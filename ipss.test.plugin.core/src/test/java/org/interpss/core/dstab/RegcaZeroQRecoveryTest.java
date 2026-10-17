package org.interpss.core.dstab;

import static org.junit.jupiter.api.Assertions.*;

import org.apache.commons.math3.complex.Complex;
import org.interpss.IpssCorePlugin;
import org.interpss.core.dstab.reference.EndpointAlignedStateMonitor;
import org.interpss.dstab.renewable.Regca1Model;
import org.interpss.fadapter.psse.PSSEMultiFileLoader;
import org.junit.jupiter.api.Test;
import com.interpss.core.acsc.fault.SimpleFaultCode;
import com.interpss.dstab.DStabGen;
import com.interpss.dstab.DStabObjectFactory;
import com.interpss.dstab.algo.DynamicSimuMethod;

/** Exact-zero selector and final recovery checkpoint, not full-profile acceptance. */
public class RegcaZeroQRecoveryTest {
    private static final double STEP=.00025;

    @Test void zeroInitialQDoesNotLatchFaultReactiveCurrentAndMatchesNativeFinalRecovery() throws Exception {
        IpssCorePlugin.init();
        String root="testData/adpter/psse/v33/renewable/";
        var context=new PSSEMultiFileLoader().loadDStab(root+"regca_zero_q_lossless.raw",
                root+"regca_reeca_repca_bus1062.dyr");
        var network=context.getDStabilityNet();
        var algorithm=context.getDynSimuAlgorithm();
        network.setBypassDataCheck(true);
        network.setAllowGenWithoutMach(true);
        algorithm.getAclfAlgorithm().setTolerance(1.e-10);
        assertTrue(algorithm.getAclfAlgorithm().loadflow());
        var generator=(DStabGen)network.getBus("Bus1").getContributeGen("1");
        var converter=(Regca1Model)generator.getDynamicGenDevice();
        assertEquals(0,generator.getGen().getImaginary(),0,"The selector fixture must initialize at exact zero");
        var monitor=new EndpointAlignedStateMonitor("Bus1");
        monitor.addBusStdMonitor(new String[]{"Bus1"});
        algorithm.setSimuOutputHandler(monitor);
        algorithm.setSimuMethod(DynamicSimuMethod.MODIFIED_EULER);
        algorithm.setSimuStepSec(STEP);
        algorithm.setTotalSimuTimeSec(.5);
        algorithm.setOutPutPerSteps(1);
        network.addDynamicEvent(DStabObjectFactory.createBusFaultEvent("Bus1",network,
                SimpleFaultCode.GROUND_3P,new Complex(0,.01),null,.05,.05),"ZeroQRecoveryFault");
        assertTrue(algorithm.initialization());
        assertEquals(0,converter.getIqRegulatorState(),0);
        monitor.initializeStateCapture(algorithm.getSimuTime(),()->new double[]{
                converter.getIpRegulatorState(),converter.getIqRegulatorState(),converter.getFilteredVoltage(),
                network.getBus("Bus1").getVoltageMag(),
                network.getBus("Bus1").getVoltageMag()*converter.getIq()});
        while(algorithm.getSimuTime()<.5-STEP/2) {
            monitor.beginStep(algorithm.getSimuTime(),STEP);
            assertTrue(algorithm.solveDEqnStep(true));
            monitor.endStep(algorithm.getSimuTime());
        }
        var frames=monitor.frames();
        assertEquals(2003,frames.size());
        double maximumIq=frames.stream().mapToDouble(frame->frame.states()[1]).max().orElseThrow();
        double[] actual=frames.get(frames.size()-1).states();
        assertTrue(maximumIq>1,"Fault must exercise the upward current excursion");
        assertTrue(actual[1]<0,"Exactly zero Q must allow downward return, not latch the fault current");

        // PSS/E 36.7, network iteration limit 200, two identical cold exports;
        // initialization O.K. and no recognized network-convergence failures.
        // Raw CSV SHA-256:
        // 5a1d1a0724556c861c10f00e4a0693cfdc4fec13fa0eb32f92cd8fe73384d345
        // Native last sample is 0.500009357929 s (single-precision clock drift).
        // These five values score final recovery only; early high-voltage-boundary
        // differences remain explicitly open in the benchmark report.
        double[] expected={0,-.00694300374016,.99753087759,.998517394066,-.00693270983174};
        assertEquals(expected[0],actual[0],0);
        for(int channel=1;channel<expected.length;channel++)
            assertEquals(expected[channel],actual[channel],1.e-4,"Final recovery channel "+channel);
        System.out.println("REGCA1 zero-Q native final-recovery errors: "+java.util.Arrays.toString(
                java.util.stream.IntStream.range(0,expected.length).mapToDouble(i->Math.abs(actual[i]-expected[i])).toArray()));
    }
}
