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
    @Test void classicalSourceAdvancesAlongsideTheExtendedControllerStack() throws Exception {
        IpssCorePlugin.init();
        String base="testData/adpter/psse/v33/SMIB/";
        var context=new PSSEMultiFileLoader().loadDStab(
                base+"SMIB_v33.raw",base+"SMIB_v33_genrou_esst4b.dyr");
        var algo=context.getDynSimuAlgorithm();
        var source=context.getDStabilityNet().getMachine("Bus2-mach1");
        source.setH(2.);
        assertTrue(algo.getAclfAlgorithm().loadflow());
        var shared=new PsseStaggeredEulerSolver(algo);
        algo.setSolver(shared);
        algo.setSimuMethod(DynamicSimuMethod.MODIFIED_EULER);
        algo.setSimuStepSec(.0005);
        algo.setTotalSimuTimeSec(.01);
        algo.setSimuOutputHandler(new StateMonitor());
        assertTrue(algo.initialization());
        assertEquals(2,shared.getBoundMachineCount());
        double initialAngle=source.getAngle();
        source.setPm(source.getPm()+.01);
        for (int i=0;i<20;i++) assertTrue(algo.solveDEqnStep(true));
        assertTrue(source.getSpeed()>1.,"classical source responds to mechanical-power input");
        assertTrue(source.getAngle()>initialAngle,"classical rotor angle advances");
        assertTrue(Double.isFinite(source.getPe()));
    }

    @Test void compoundFilterExposesEveryActualStateAtTheCommonEndpoint() {
        var block=new com.interpss.dstab.controller.cml.field.block.FilterNthOrderBlock(.2,.5,2,2);
        assertTrue(block.initStateU0(1.));
        var components=block.getIntegrationBlocks();
        assertEquals(4,components.size());
        assertThrows(UnsupportedOperationException.class,components::clear);
        double[] before=components.stream().mapToDouble(b->b.getStateX()).toArray();
        block.eulerStep1(2.,0.);
        assertArrayEquals(before,components.stream().mapToDouble(b->b.getStateX()).toArray(),0.);
        assertArrayEquals(new double[]{1.2,.48,.32,0.},
                components.stream().mapToDouble(b->b.getDX_dt()).toArray(),1.e-14);
    }
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
