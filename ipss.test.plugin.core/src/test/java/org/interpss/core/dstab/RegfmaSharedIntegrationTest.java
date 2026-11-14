package org.interpss.core.dstab;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.file.Files;
import java.nio.file.Path;
import org.apache.commons.math3.complex.Complex;
import org.interpss.IpssCorePlugin;
import org.interpss.core.dstab.reference.Regfma1NativeReferenceCheckpoints;
import org.interpss.dstab.algo.PsseStaggeredEulerSolver;
import com.interpss.dstab.controller.runtime.IntegrationStateMonitor.Stage;
import org.interpss.dstab.renewable.Regfma1Model;
import org.interpss.fadapter.psse.PSSEMultiFileLoader;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import com.interpss.dstab.DStabGen;
import com.interpss.dstab.algo.DynamicSimuAlgorithm;
import com.interpss.dstab.algo.DynamicSimuMethod;
import com.interpss.dstab.cache.StateMonitor;

/** State observation, recurrence, projection and lifecycle checks for grid-forming integration. */
public class RegfmaSharedIntegrationTest {
    private static final double STEP=.0005;
    private record Fixture(DynamicSimuAlgorithm algorithm, Regfma1Model model,
            PsseStaggeredEulerSolver solver) { }

    private Fixture load(Path directory, boolean shared, String profile) throws Exception {
        IpssCorePlugin.init();
        var reference=Regfma1NativeReferenceCheckpoints.get(profile);
        Path dyr=directory.resolve(profile+".dyr");
        Files.writeString(dyr,"1 'REGFMA1' '1' "+reference.vflag()+" "+reference.qvflag()+" "
                +String.join(" ",reference.constants().stream().map(Object::toString).toList())+" /\n"
                +"3 'GENCLS' '1' 9999999 0 /\n");
        var context=new PSSEMultiFileLoader().loadDStab(
                "testData/adpter/psse/v33/renewable/regfma1_native.raw",dyr.toString());
        var algorithm=context.getDynSimuAlgorithm();
        context.getDStabilityNet().setAllowGenWithoutMach(true);
        algorithm.getAclfAlgorithm().getDataCheckConfig().setAllowGenWithoutMachine(true);
        algorithm.getAclfAlgorithm().setTolerance(1e-10);
        assertTrue(algorithm.getAclfAlgorithm().loadflow());
        var model=(Regfma1Model)((DStabGen)context.getDStabilityNet().getBus("Bus1")
                .getContributeGen("1")).getDynamicGenDevice();
        var solver=new PsseStaggeredEulerSolver(algorithm);
        if (shared) algorithm.setSolver(solver);
        algorithm.setSimuMethod(DynamicSimuMethod.MODIFIED_EULER);
        algorithm.setSimuStepSec(STEP);
        algorithm.setTotalSimuTimeSec(.01);
        algorithm.setSimuOutputHandler(new StateMonitor());
        assertTrue(algorithm.initialization());
        return new Fixture(algorithm,model,solver);
    }

    @Test void defaultStagesExposeDetachedEvaluationCoordinates(@TempDir Path directory) throws Exception {
        var f=load(directory,false,"flat11");var m=f.model;
        assertEquals(Stage.NOT_EVALUATED,m.getStateDerivativeSnapshot().stage());
        assertTrue(m.getStateDerivativeSnapshot().rates().isEmpty());
        m.getDStabBus().setVoltage(new Complex(.98,0));m.getOutputObject();
        var before=m.getIntegrationStates();
        assertTrue(m.nextStep(STEP,DynamicSimuMethod.MODIFIED_EULER,0));
        var first=m.getStateDerivativeSnapshot();
        assertEquals(Stage.PREDICTOR,first.stage());
        assertEquals(before,first.evaluationStates());
        assertEquals(9,first.rates().size());
        var predicted=m.getIntegrationStates();
        assertTrue(m.nextStep(STEP,DynamicSimuMethod.MODIFIED_EULER,1));
        assertEquals(Stage.CORRECTOR,m.getStateDerivativeSnapshot().stage());
        assertEquals(predicted,m.getStateDerivativeSnapshot().evaluationStates());
        assertEquals(before,first.evaluationStates());
        assertThrows(UnsupportedOperationException.class,()->first.rates().clear());
        assertThrows(UnsupportedOperationException.class,()->before.clear());
        assertTrue(m.initStates(m.getDStabBus()));
        assertEquals(Stage.NOT_EVALUATED,m.getStateDerivativeSnapshot().stage());
    }

    @Test void repeatedRateEvaluationDoesNotAdvanceCoordinates(@TempDir Path directory) throws Exception {
        var f=load(directory,true,"flat11");var m=f.model;
        assertEquals(1,f.solver.getBoundRenewableCount());
        assertEquals(1,f.solver.getBoundMachineCount());
        assertEquals(11,f.solver.getBoundDifferentialStateCount());
        m.setReferenceOffsets(.2,0);
        var before=m.getIntegrationStates();double[] rates=new double[11];
        rates[0]=rates[10]=123;
        m.evaluateStaggered(STEP,rates,1);
        assertEquals(2*Math.PI*60*m.getData().mp()*.2,rates[1],1e-10);
        double[] first=rates.clone();
        for(int i=0;i<10;i++)m.evaluateStaggered(STEP,rates,1);
        assertArrayEquals(first,rates,0.);
        assertEquals(before,m.getIntegrationStates());
        assertEquals(Stage.STAGGERED,m.getStateDerivativeSnapshot().stage());
        assertEquals(123,rates[0]);assertEquals(123,rates[10]);
    }

    @Test void externalEventAndOrdinaryStepUseRetainedDerivativeHistory(@TempDir Path directory) throws Exception {
        var f=load(directory,true,"flat00");var m=f.model;
        m.setReferenceOffsets(.2,0);
        double[] rates=new double[9];m.evaluateStaggered(STEP,rates,0);
        double firstRate=rates[0],start=m.getAngle();
        f.solver.notifyDiscontinuity();f.solver.notifyDiscontinuity();
        assertTrue(f.algorithm.solveDEqnStep(true));
        assertEquals(start+.75*STEP*firstRate,m.getAngle(),1e-10);
        double secondRate=m.getStateDerivativeSnapshot().rates().get("Angle");
        start=m.getAngle();
        assertTrue(f.algorithm.solveDEqnStep(true));
        assertEquals(start+STEP*(1.5*secondRate-.25*firstRate),m.getAngle(),1e-10);
    }

    @Test void projectionPreservesIndependentBoundsAndRejectsNonfiniteTrials(@TempDir Path directory) throws Exception {
        var f=load(directory,true,"voltage11");var m=f.model;
        double[] trial=new double[9];m.readStaggeredState(trial,0);
        trial[4]=2;trial[5]=1;trial[6]=-1;trial[7]=1;trial[8]=-1;
        m.applyStaggeredState(trial,0,STEP);
        assertEquals(1.08,m.getVoltageIntegral(),0.);
        assertEquals(0,m.getActiveUpperLimitIntegral(),0.);
        assertEquals(0,m.getActiveLowerLimitIntegral(),0.);
        assertEquals(0,m.getReactiveUpperLimitIntegral(),0.);
        assertEquals(0,m.getReactiveLowerLimitIntegral(),0.);
        trial[4]=0;m.applyStaggeredState(trial,0,STEP);
        assertEquals(1.07,m.getVoltageIntegral(),0.);
        var before=m.getIntegrationStates();trial[8]=Double.NaN;
        assertThrows(IllegalArgumentException.class,()->m.applyStaggeredState(trial,0,STEP));
        assertEquals(before,m.getIntegrationStates());
    }

    @Test void inactiveUnitHoldsItsStatesAndAttachmentReplacementIsRejected(@TempDir Path directory) throws Exception {
        var f=load(directory,true,"flat00");var m=f.model;
        m.setStatus(false);var before=m.getIntegrationStates();
        assertTrue(f.algorithm.solveDEqnStep(true));
        assertEquals(before,m.getIntegrationStates());
        m.setStatus(true);
        m.getParentGen().setDynamicGenDevice(null);
        assertThrows(IllegalStateException.class,()->f.solver.nextStep(
                f.algorithm.getSimuTime(),STEP,DynamicSimuMethod.MODIFIED_EULER));
        assertEquals(before,m.getIntegrationStates());
    }
}
