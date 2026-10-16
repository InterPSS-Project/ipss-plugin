package org.interpss.core.dstab;

import static org.junit.jupiter.api.Assertions.*;

import org.interpss.IpssCorePlugin;
import org.interpss.dstab.algo.PsseStaggeredEulerSolver;
import org.interpss.dstab.control.exc.ieee.y1968.type1.Ieee1968Type1Exciter;
import org.interpss.dstab.control.exc.ieee.y1968.type2.Ieee1968Type2Exciter;
import org.interpss.fadapter.psse.PSSEMultiFileLoader;
import org.junit.jupiter.api.Test;

import com.interpss.dstab.StaticLoadModel;
import com.interpss.dstab.algo.DynamicSimuAlgorithm;
import com.interpss.dstab.algo.DynamicSimuMethod;
import com.interpss.dstab.cache.StateMonitor;

class PsseStaggeredEulerSolverTest {
    private PsseStaggeredEulerSolver solver;
    private DynamicSimuAlgorithm load() throws Exception {
        IpssCorePlugin.init();
        String base="testData/adpter/psse/v30/IEEE39Bus/";
        var context=new PSSEMultiFileLoader().loadDStab(base+"IEEE39bus_v30.raw",base+"IEEE39bus.dyr");
        var algo=context.getDynSimuAlgorithm();
        var net=context.getDStabilityNet();
        net.setStaticLoadModel(StaticLoadModel.CONST_Z);
        net.setReactiveStaticLoadModel(StaticLoadModel.CONST_Z);
        algo.getAclfAlgorithm().setTolerance(1.e-10);
        algo.getAclfAlgorithm().setMaxIterations(50);
        assertTrue(algo.getAclfAlgorithm().loadflow());
        solver=new PsseStaggeredEulerSolver(algo);
        algo.setSolver(solver);
        algo.setSimuMethod(DynamicSimuMethod.MODIFIED_EULER);
        algo.setSimuStepSec(.001);
        algo.setTotalSimuTimeSec(2.);
        algo.setSimuOutputHandler(new StateMonitor());
        algo.setOutPutPerSteps(1000);
        return algo;
    }

    @Test void referenceJumpMatchesNativePsseFirstThreeStateSamples() throws Exception {
        var algo=load();
        assertTrue(algo.initialization());
        for (int i=0;i<1000;i++) assertTrue(algo.solveDEqnStep(true));
        var exc=(Ieee1968Type1Exciter)algo.getNetwork().getMachine("Bus36-mach1").getExciter();
        exc.setRefPoint(exc.getRefPoint()+.001);
        solver.notifyDiscontinuity();
        // Native PSS/E 36.7, unchanged IEEE39 case, +0.001 VREF at 1 s,
        // 1 ms step and 1e-6 network tolerance. The first two increments
        // discriminate the retained-history rule from predictor/corrector Euler.
        double[] nativeVr={2.24202132225,2.24440884590,2.24615454674};
        for (double expected:nativeVr) {
            assertTrue(algo.solveDEqnStep(true));
            assertEquals(expected,exc.getRegulatorOutput(),5.e-6);
        }
    }

    @Test void fixedStepAndModelAttachmentChangesFailBeforeAdvancingStates() throws Exception {
        var algo=load(); assertTrue(algo.initialization());
        var machine=algo.getNetwork().getMachine("Bus36-mach1");
        double angle=machine.getAngle();
        assertThrows(IllegalArgumentException.class,
                () -> solver.nextStep(0.,.002,DynamicSimuMethod.MODIFIED_EULER));
        assertEquals(angle,machine.getAngle());
        machine.getControllerList().remove(machine.getExciter());
        machine.getControllerList().add(new Ieee1968Type2Exciter());
        assertThrows(IllegalStateException.class,
                () -> solver.nextStep(0.,.001,DynamicSimuMethod.MODIFIED_EULER));
        assertEquals(angle,machine.getAngle());
    }

    @Test void unsupportedControllerAndInvalidNumericalSettingsAreRejected() throws Exception {
        var algo=load();
        assertEquals(1.e-6,solver.getNetworkSolutionTolerance());
        assertThrows(IllegalArgumentException.class,()->solver.setNetworkSolutionTolerance(Double.NaN));
        assertThrows(IllegalArgumentException.class,()->solver.setNetworkSolutionTolerance(0.));
        var machine=algo.getNetwork().getMachine("Bus36-mach1");
        machine.getControllerList().remove(machine.getExciter());
        machine.getControllerList().add(new Ieee1968Type2Exciter());
        assertThrows(IllegalArgumentException.class,solver::initialization);
    }

    @Test void reinitializationResetsHistoryAndDisabledControllerDoesNotAdvance() throws Exception {
        var algo=load(); assertTrue(algo.initialization());
        var exc=(Ieee1968Type1Exciter)algo.getNetwork().getMachine("Bus36-mach1").getExciter();
        exc.setRefPoint(exc.getRefPoint()+.001); solver.notifyDiscontinuity();
        assertTrue(algo.solveDEqnStep(true));
        double first=exc.getRegulatorOutput();
        exc.setStatus(false);
        for (int i=0;i<3;i++) assertTrue(algo.solveDEqnStep(true));
        assertEquals(first,exc.getRegulatorOutput());
        exc.setStatus(true);
        algo.getNetwork().setDStabNetInitialized(false);
        assertTrue(algo.getAclfAlgorithm().loadflow());
        assertTrue(algo.initialization());
        exc.setRefPoint(exc.getRefPoint()+.001); solver.notifyDiscontinuity();
        assertTrue(algo.solveDEqnStep(true));
        assertEquals(first,exc.getRegulatorOutput(),2.e-8);
    }
}
