package org.interpss.core.dstab;

import static org.junit.jupiter.api.Assertions.*;
import java.nio.file.Path;
import org.apache.commons.math3.complex.Complex;
import org.interpss.IpssCorePlugin;
import org.interpss.dstab.relay.Tiocr1Data;
import org.interpss.dstab.relay.Tiocr1RelayModel;
import org.interpss.fadapter.psse.PSSEMultiFileLoader;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import com.interpss.dstab.DStabObjectFactory;
import com.interpss.dstab.algo.DynamicSimuMethod;
import com.interpss.core.acsc.fault.SimpleFaultCode;

/** Accepted relay history is independent of trial integration and observation. */
public class Tiocr1AcceptedHistoryTest {
    private static final Path CASE=Path.of("testData/adpter/psse/v33/HspModels");

    @ParameterizedTest
    @CsvSource({"false,false","false,true","true,false","true,true"})
    void zeroDurationCannotResetMotionAndCurrentMatchesBothTerminals(boolean staggered,boolean fromEnd) throws Exception {
        var a=setup(staggered);
        var branch=a.getNetwork().getBranchList().getFirst();
        var curve=new Tiocr1Data(new double[]{.01,0,1.1,1,1.5,1,2,1,3,1,5,1,.01,0});
        var relay=new Tiocr1RelayModel(branch,fromEnd,2,false,a.getNetwork().getDStabBus("Bus2"),null,
                new com.interpss.core.aclf.AclfBranch[3],curve);
        assertTrue(a.initialization());
        var vf=branch.getFromAclfBus().getVoltage();var vt=branch.getToAclfBus().getVoltage();
        for(int i=0;i<30;i++) {
            branch.getFromAclfBus().setVoltage(new Complex(.2+i*.04,-.3+i*.013));
            branch.getToAclfBus().setVoltage(new Complex(1.1-i*.02,.2-i*.021));
            var f=branch.getFromAclfBus().getVoltage();var t=branch.getToAclfBus().getVoltage();
            double expected=(fromEnd?branch.yff().multiply(f).add(branch.yft().multiply(t))
                    :branch.ytf().multiply(f).add(branch.ytt().multiply(t))).abs();
            assertEquals(expected,relay.currentMagnitude(),2e-14*Math.max(1,expected));
        }
        branch.getFromAclfBus().setVoltage(vf);branch.getToAclfBus().setVoltage(vt);
        assertTrue(relay.afterStep(.1));assertTrue(relay.getPosition()>0 && relay.getPosition()<1);
        branch.setStatus(false);var history=relay.getIntegrationHistories();
        assertTrue(relay.afterStep(0));assertEquals(history,relay.getIntegrationHistories());
        assertTrue(relay.nextStep(.01,DynamicSimuMethod.MODIFIED_EULER,0));
        assertTrue(relay.nextStep(.01,DynamicSimuMethod.MODIFIED_EULER,1));
        assertEquals(history,relay.getIntegrationHistories());
        assertFalse(relay.afterStep(Double.NaN));assertFalse(relay.afterStep(-1));
        assertEquals(history,relay.getIntegrationHistories());
        assertTrue(relay.getIntegrationStates().isEmpty());
        assertSame(relay.getStateDerivativeSnapshot(),relay.getStateDerivativeSnapshot());
        assertEquals(history,relay.getNamedStates());assertEquals(0,relay.currentMagnitude());
        assertTrue(relay.afterStep(.001));assertEquals(0,relay.getPosition());
        branch.setStatus(true);
    }

    @ParameterizedTest
    @CsvSource({"false,false","false,true","true,false","true,true"})
    void coupledFaultAdvancesAcceptedProtectionUnderBothMethods(boolean staggered,boolean fromEnd) throws Exception {
        var a=setup(staggered);var net=a.getNetwork();var branch=net.getBranchList().getFirst();
        var curve=new Tiocr1Data(new double[]{.4,.1,1.1,.04,1.5,.03,2,.02,3,.01,5,.005,.01,0});
        var relay=new Tiocr1RelayModel(branch,fromEnd,2,false,net.getDStabBus("Bus2"),null,
                new com.interpss.core.aclf.AclfBranch[3],curve);
        net.addDynamicEvent(DStabObjectFactory.createBusFaultEvent("Bus1",net,SimpleFaultCode.GROUND_3P,
                new Complex(0,.2),null,.05,.05),"Fault");
        assertTrue(a.initialization());
        while(a.getSimuTime()<.12-.00025) {
            assertTrue(a.solveDEqnStep(true));var before=relay.getIntegrationHistories();
            relay.getStates(null);relay.currentMagnitude();relay.getStateDerivativeSnapshot();
            assertEquals(before,relay.getIntegrationHistories());
        }
        assertTrue(relay.isOperated());assertTrue(branch.isActive(),"Monitoring alone cannot open a branch");
        assertEquals(0,relay.getShedFraction());
        assertEquals(0,relay.getNetworkActionVersion(),"Monitoring cannot request a network-history rebase");
    }

    private static com.interpss.dstab.algo.DynamicSimuAlgorithm setup(boolean staggered) throws Exception {
        IpssCorePlugin.init();IpssCorePlugin.configureSparseSolver("java-klu");
        var context=new PSSEMultiFileLoader().loadDStab(CASE.resolve("network.raw").toString(),CASE.resolve("tiocr1-0.dyr").toString());
        var a=context.getDynSimuAlgorithm();assertTrue(a.getAclfAlgorithm().loadflow());
        a.setSimuMethod(DynamicSimuMethod.MODIFIED_EULER);a.setSimuStepSec(.0005);a.setTotalSimuTimeSec(.12);
        a.setSimuOutputHandler(new com.interpss.dstab.cache.StateMonitor());
        if(staggered)a.setSolver(new org.interpss.dstab.algo.StaggeredEulerSolver(a));
        return a;
    }
}
