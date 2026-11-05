package org.interpss.core.dstab;

import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Test;
import com.interpss.dstab.algo.DynamicSimuMethod;
import org.interpss.dstab.svc.Svsmo3t2Model;

/** Physical publication is separate from the zero-time algebraic observation. */
public class Svsmo3t2PhysicalPublicationTest extends org.interpss.CorePluginTestSetup {
    private static double[] states(Svsmo3t2Model m){double[] x=new double[6];m.readStaggeredState(x,0);return x;}
    @Test void eventObservationPublishesOnlyOnNextRealStepAndDiscardRestoresBothMethods() throws Exception {
        var root=Svsmo3t2DeadbandRelockTest.reference();
        for(boolean shared:new boolean[]{false,true}) {
            var m=Svsmo3t2DeadbandRelockTest.model(root.resolve("bypass-a"));
            double original=m.getCurrent();m.setScheduledVoltage(1.007);
            assertTrue(m.nextStep(0,DynamicSimuMethod.MODIFIED_EULER,0));
            double observation=states(m)[2];assertEquals(.14,observation-original,1e-10);
            assertEquals(original,m.getCurrent(),0,"Event observation does not publish current");
            double[] before=states(m);var history=m.getIntegrationHistories();
            for(int attempt=0;attempt<2;attempt++) {
                m.beginPhysicalStepTrial(.05,.0005);
                if(shared){double[] f=new double[6];m.evaluateStaggered(.0005,f,0);double[] x=states(m);for(int i=0;i<6;i++)x[i]+=.0005*f[i];m.applyStaggeredState(x,0,.0005);m.completeStaggeredStep(.0005);}
                else {assertTrue(m.nextStep(.0005,DynamicSimuMethod.MODIFIED_EULER,0));assertTrue(m.nextStep(.0005,DynamicSimuMethod.MODIFIED_EULER,1));}
                assertEquals(original,m.getCurrent(),0);
                m.publishPhysicalStepTrial();assertEquals(observation,m.getCurrent(),0);
                m.evaluateStaggered(.0005,new double[6],0);m.publishPhysicalStepTrial();
                assertEquals(observation,m.getCurrent(),0,"Repeated solves/publication cannot resample");
                if(attempt==0){m.discardPhysicalStepTrial();m.discardPhysicalStepTrial();assertArrayEquals(before,states(m),0);assertEquals(history,m.getIntegrationHistories());assertEquals(original,m.getCurrent(),0);}
                else {m.validatePhysicalStepAcceptance();m.acceptPhysicalStepTrial();m.acceptPhysicalStepTrial();assertEquals(observation,m.getCurrent(),0);}
            }
            m.setScheduledVoltage(1.0);
        }
    }
    @Test void actualSolversDiscardAfterPublishedNetworkFailure() throws Exception {
        var root=Svsmo3t2DeadbandRelockTest.reference().resolve("bypass-a");
        for(boolean shared:new boolean[]{false,true}) {
            var c=new org.interpss.fadapter.psse.PSSEMultiFileLoader().loadDStab(root.resolve("smib.raw").toString(),root.resolve("smib.dyr").toString());
            var a=c.getDynSimuAlgorithm();var net=c.getDStabilityNet();
            net.setStaticLoadIncludedInYMatrix(true);net.setStaticLoadModel(com.interpss.dstab.StaticLoadModel.CONST_Z);net.setReactiveStaticLoadModel(com.interpss.dstab.StaticLoadModel.CONST_Z);
            a.getAclfAlgorithm().getLfAdjAlgo().setLocalContinuousSvcAsPv(true);a.getAclfAlgorithm().setTolerance(1e-10);assertTrue(a.getAclfAlgorithm().loadflow());
            a.setSimuStepSec(.0005);a.setSimuMethod(DynamicSimuMethod.MODIFIED_EULER);a.setSimuOutputHandler(new com.interpss.dstab.cache.StateMonitor());
            Svsmo3t2Model[] owner={null};boolean[] armed={false},observed={false};double[] old={0};
            Runnable fail=()->{if(armed[0]&&Math.abs(owner[0].getCurrent()-old[0])>.1){observed[0]=true;throw new IllegalStateException("Synthetic network failure after publication");}};
            var solver=shared?new org.interpss.dstab.algo.StaggeredEulerSolver(a){
                @Override public boolean networkSolutionStep() throws com.interpss.dstab.common.DStabSimuException {fail.run();return super.networkSolutionStep();}
            }:new org.interpss.dstab.algo.DefaultDynamicSimulationSolver(a){
                @Override public boolean networkSolutionStep() throws com.interpss.dstab.common.DStabSimuException {fail.run();return super.networkSolutionStep();}
            };
            a.setSolver(solver);assertTrue(a.initialization());
            owner[0]=(Svsmo3t2Model)net.getDStabBus("Bus3").getDynamicBusDeviceList().stream().filter(Svsmo3t2Model.class::isInstance).findFirst().orElseThrow();
            var m=owner[0];old[0]=m.getCurrent();m.setScheduledVoltage(m.getScheduledVoltage()+.007);assertTrue(m.nextStep(0,DynamicSimuMethod.MODIFIED_EULER,0));
            double[] before=states(m);var history=m.getIntegrationHistories();armed[0]=true;
            var failure=assertThrows(Exception.class,()->solver.nextStep(0,.0005,DynamicSimuMethod.MODIFIED_EULER));assertTrue(failure.toString().contains("Synthetic network failure") || (failure.getCause()!=null&&failure.getCause().toString().contains("Synthetic network failure")));assertTrue(observed[0]);
            assertArrayEquals(before,states(m),0);assertEquals(history,m.getIntegrationHistories());assertEquals(old[0],m.getCurrent(),0);
            // Retry of the model boundary is supported; other solver/network owners are not rolled back by this contract.
            m.beginPhysicalStepTrial(0,.0005);m.discardPhysicalStepTrial();m.discardPhysicalStepTrial();
        }
    }
    @Test void standalonePredictorCorrectorPublishesWithoutOptionalSolverWrapper() throws Exception {
        var m=Svsmo3t2DeadbandRelockTest.model(Svsmo3t2DeadbandRelockTest.reference().resolve("bypass-a"));
        double initial=m.getCurrent();m.setScheduledVoltage(1.007);assertTrue(m.nextStep(0,DynamicSimuMethod.MODIFIED_EULER,0));
        double command=states(m)[2];assertEquals(initial,m.getCurrent(),0);
        assertTrue(m.nextStep(.0005,DynamicSimuMethod.MODIFIED_EULER,0));assertEquals(command,m.getCurrent(),0);
        assertTrue(m.nextStep(.0005,DynamicSimuMethod.MODIFIED_EULER,1));assertEquals(command,m.getCurrent(),0);
        m.setScheduledVoltage(1.0);assertTrue(m.nextStep(0,DynamicSimuMethod.MODIFIED_EULER,0));
        double next=states(m)[2];assertNotEquals(command,next);assertTrue(m.nextStep(.0005,DynamicSimuMethod.MODIFIED_EULER,0));assertEquals(next,m.getCurrent(),0);
        m.discardPhysicalStepTrial();assertEquals(command,m.getCurrent(),0);
    }
    @Test void rejectedBeginDoesNotChangeCheckpointAndFiniteLagNeedsNoTrial() throws Exception {
        var root=Svsmo3t2DeadbandRelockTest.reference();
        var m=Svsmo3t2DeadbandRelockTest.model(root.resolve("bypass-a"));double[] x=states(m);double current=m.getCurrent();
        assertThrows(IllegalStateException.class,()->m.beginPhysicalStepTrial(.05,Double.NaN));
        m.discardPhysicalStepTrial();assertArrayEquals(x,states(m),0);assertEquals(current,m.getCurrent(),0);
        m.beginPhysicalStepTrial(.05,.0005);assertThrows(IllegalStateException.class,m::validatePhysicalStepAcceptance);
        m.discardPhysicalStepTrial();assertArrayEquals(x,states(m),0);
        var lag=Svsmo3t2DeadbandRelockTest.model(root.resolve("enabled-a"));assertFalse(lag.requiresPhysicalStepTrial());
        lag.beginPhysicalStepTrial(.05,.0005);lag.publishPhysicalStepTrial();lag.validatePhysicalStepAcceptance();lag.acceptPhysicalStepTrial();lag.discardPhysicalStepTrial();
    }
}
