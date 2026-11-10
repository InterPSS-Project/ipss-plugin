package org.interpss.core.adapter.builder.dstab;

import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Test;
import org.interpss.CorePluginTestSetup;
import org.interpss.dstab.control.exc.ieee.y1981.st1.IEEE1981ST1Exciter;
import org.interpss.dstab.algo.DefaultDynamicSimulationSolver;
import com.interpss.dstab.algo.DynamicSimuMethod;
import com.interpss.dstab.controller.cml.field.block.DelayControlBlock;

/** Local transducer integration must be identical through generic and prepared wiring. */
public class Esst1aLocalSubstepTest extends CorePluginTestSetup {
    private record Fixture(com.interpss.dstab.mach.Machine machine,IEEE1981ST1Exciter exciter) { }
    private Fixture fixture() throws Exception {
        var b=DStabBuilderTestFixture.createWithMachine();var net=b.getDStabNetwork();
        assertTrue(com.interpss.core.LoadflowAlgoObjectFactory.createLoadflowAlgorithm(net).loadflow());
        var m=net.getMachine("Bus1-mach1");assertTrue(m.initStates(net.getDStabBus("Bus1")));
        var e=b.addExcEsst1a("Bus1","1",1,1,.001,.8,-.8,.2,.8,.1,.4,80,.04,99,-99,7.3,-7.3,0,.1,1,0,1.3);
        assertTrue(e.initStates(net.getDStabBus("Bus1"),m));return new Fixture(m,e);
    }
    private static DelayControlBlock transducer(Fixture f){return (DelayControlBlock)f.exciter().getNamedStateBlocks().get("trDelayBlock");}
    @Test void compatibleAndPreparedStagesPreserveSameLocalHistory() throws Exception {
        var a=fixture();var b=fixture();transducer(a).setLocalIntegrationSteps(4);transducer(b).setLocalIntegrationSteps(4);
        b.exciter().compileRuntimePlan();b.exciter().enableModifiedEulerEvaluation();
        for(int n=0;n<60;n++) {
            double v=n<10?1.04:n<35?1.02:1.04;
            a.machine().getDStabBus().setVoltageMag(v);b.machine().getDStabBus().setVoltageMag(v);
            for(int stage=0;stage<2;stage++) {
                assertTrue(a.exciter().nextStep(.0033333,DynamicSimuMethod.MODIFIED_EULER,a.machine(),stage));
                assertTrue(b.exciter().nextStep(.0033333,DynamicSimuMethod.MODIFIED_EULER,b.machine(),stage));
                for(var item:a.exciter().getNativeStateSnapshot().entrySet())
                    assertEquals(item.getValue(),b.exciter().getNativeStateSnapshot().get(item.getKey()),1e-11,item.getKey());
            }
        }
        assertTrue(b.exciter().getModifiedEulerStageCount()>0);
        assertEquals(.001,a.exciter().getData().getTr(),0.);assertEquals(.001,transducer(a).getT(),0.);
    }
    @Test void solverPolicySelectsFinalInitializedBlockAndReportsItsCount() throws Exception {
        var f=fixture();var net=(com.interpss.dstab.DStabilityNetwork)f.machine().getDStabBus().getNetwork();
        var algorithm=com.interpss.dstab.DStabObjectFactory.createDynamicSimuAlgorithm(net);
        algorithm.setSimuMethod(DynamicSimuMethod.MODIFIED_EULER);algorithm.setSimuStepSec(.0033333);
        var solver=new DefaultDynamicSimulationSolver(algorithm);algorithm.setSolver(solver);
        solver.setStepSensitiveModelSubstepsEnabled(true);
        algorithm.setSimuOutputHandler(new com.interpss.dstab.cache.StateMonitor());assertTrue(algorithm.initialization());
        assertEquals(1,transducer(f).getLocalIntegrationSteps());
        assertEquals(4,f.exciter().getModifiedEulerLocalIntegrationSteps());
        assertEquals(1,solver.getLocalSubstepPreparationReport().size());
        var entry=solver.getLocalSubstepPreparationReport().get(0);assertEquals(4,entry.localSteps());
        assertEquals(.001,entry.enteredTimeConstantSec(),0.);assertEquals(.0033333/4,entry.localStepSec(),0.);
    }
    @Test void solverResolvesFeedbackEvenWhenIndividualLagsExceedOuterStep() throws Exception {
        var f=fixture();var data=f.exciter().getData();
        data.setTr(.02);data.setKa(360);data.setTa(.06);data.setKf(.06);data.setTf(.5);
        var net=(com.interpss.dstab.DStabilityNetwork)f.machine().getDStabBus().getNetwork();
        var algorithm=com.interpss.dstab.DStabObjectFactory.createDynamicSimuAlgorithm(net);
        algorithm.setSimuMethod(DynamicSimuMethod.MODIFIED_EULER);algorithm.setSimuStepSec(.0033333);
        var solver=new DefaultDynamicSimulationSolver(algorithm);algorithm.setSolver(solver);
        solver.setStepSensitiveModelSubstepsEnabled(true);
        algorithm.setSimuOutputHandler(new com.interpss.dstab.cache.StateMonitor());assertTrue(algorithm.initialization());
        assertEquals(.02,transducer(f).getT(),0.);assertEquals(.06,f.exciter().ta,0.);assertEquals(.5,f.exciter().tf,0.);
        assertEquals(4,f.exciter().getModifiedEulerLocalIntegrationSteps());
        assertEquals(1,transducer(f).getLocalIntegrationSteps(),"Whole graph excludes nested block steps");
        assertTrue(solver.getLocalSubstepPreparationReport().get(0).coordinate().contains("feedback"));
    }

    @Test void inactiveUninitializedMotorMetadataDoesNotRequireElectricalSubsteps() throws Exception {
        var f=fixture();var net=(com.interpss.dstab.DStabilityNetwork)f.machine().getDStabBus().getNetwork();
        var unused=new org.interpss.dstab.dynLoad.impl.InductionMotorImpl();
        unused.setId("inactive");unused.setDStabBus(f.machine().getDStabBus());
        f.machine().getDStabBus().getDynamicBusDeviceList().add(unused);
        unused.setTwoAxisModel(true);unused.setStatus(false);assertEquals(0.,unused.getTp0(),0.);
        var algorithm=com.interpss.dstab.DStabObjectFactory.createDynamicSimuAlgorithm(net);
        algorithm.setSimuMethod(DynamicSimuMethod.MODIFIED_EULER);algorithm.setSimuStepSec(.0033333);
        var solver=new DefaultDynamicSimulationSolver(algorithm);algorithm.setSolver(solver);
        solver.setStepSensitiveModelSubstepsEnabled(true);
        algorithm.setSimuOutputHandler(new com.interpss.dstab.cache.StateMonitor());assertTrue(algorithm.initialization());
        assertEquals(0.,unused.getLocalIntegrationMaximumStepSec(),0.);
        assertFalse(solver.getLocalSubstepPreparationReport().stream().anyMatch(e->e.modelId().contains("inactive")));
    }

}
