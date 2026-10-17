package org.interpss.core.dstab;

import static org.junit.jupiter.api.Assertions.*;

import java.util.Arrays;
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

/** Native 36.7 Iolim direction and nominal-grid checkpoints, not event-post parity. */
public class RegcaCurrentLimitNativeTest {
    @Test void nativeCurrentCeilingAtQuarterMillisecond() throws Exception {
        run(.00025, QUARTER, new double[]{0,.014,.0033,.00085,.0036});
    }

    @Test void nativeCurrentCeilingAtEighthMillisecond() throws Exception {
        run(.000125, EIGHTH, new double[]{0,.007,.0017,.00043,.0018});
    }

    private void run(double step, String checkpoints, double[] tolerances) throws Exception {
        IpssCorePlugin.init();
        String root="testData/adpter/psse/v33/renewable/";
        var context=new PSSEMultiFileLoader().loadDStab(root+"regca_zero_q_lossless.raw",
                root+"regca_iolim_native.dyr");
        var network=context.getDStabilityNet();
        var algorithm=context.getDynSimuAlgorithm();
        network.setBypassDataCheck(true);
        network.setAllowGenWithoutMach(true);
        algorithm.getAclfAlgorithm().setTolerance(1e-10);
        assertTrue(algorithm.getAclfAlgorithm().loadflow());
        var generator=(DStabGen)network.getBus("Bus1").getContributeGen("1");
        var model=(Regca1Model)generator.getDynamicGenDevice();
        assertEquals(-.25,model.getData().iolim(),0);
        assertEquals(0,generator.getGen().getImaginary(),0);
        var monitor=new EndpointAlignedStateMonitor("Bus1");
        monitor.addBusStdMonitor(new String[]{"Bus1"});
        algorithm.setSimuOutputHandler(monitor);
        algorithm.setSimuMethod(DynamicSimuMethod.MODIFIED_EULER);
        algorithm.setSimuStepSec(step);
        algorithm.setTotalSimuTimeSec(.5);
        algorithm.setOutPutPerSteps(1);
        network.addDynamicEvent(DStabObjectFactory.createBusFaultEvent("Bus1",network,
                SimpleFaultCode.GROUND_3P,new Complex(0,.01),null,.05,.05),"IolimFault");
        assertTrue(algorithm.initialization());
        monitor.initializeStateCapture(algorithm.getSimuTime(),()->new double[]{
                model.getIpRegulatorState(),model.getIqRegulatorState(),model.getFilteredVoltage(),
                network.getBus("Bus1").getVoltageMag(),network.getBus("Bus1").getVoltageMag()*model.getIq(),
                model.getIq()});
        while(algorithm.getSimuTime()<.5-step/2) {
            monitor.beginStep(algorithm.getSimuTime(),step);
            assertTrue(algorithm.solveDEqnStep(true));
            monitor.endStep(algorithm.getSimuTime());
        }
        var frames=monitor.frames();
        assertEquals((int)Math.round(.5/step)+3,frames.size());
        int activeSamples=0;
        for(var frame:frames) {
            double[] states=frame.states();
            assertTrue(Arrays.stream(states).allMatch(Double::isFinite));
            if(frame.time()>.06 && frame.time()<.0999 && states[1]>.3) {
                assertEquals(.25,states[5],1e-10,"Native positive generated-current ceiling");
                activeSamples++;
            }
        }
        assertTrue(activeSamples>100,"Exercise the independent low-voltage active limit");
        double[] maximum=new double[5];
        for(String line:checkpoints.strip().split("\\R")) {
            double[] expected=Arrays.stream(line.split(",")).mapToDouble(Double::parseDouble).toArray();
            var frame=frames.stream().filter(f->Math.abs(f.time()-expected[0])<1e-10).findFirst().orElseThrow();
            double[] actual=frame.states();
            for(int channel=0;channel<5;channel++) {
                maximum[channel]=Math.max(maximum[channel],Math.abs(expected[channel+1]-actual[channel]));
                assertEquals(expected[channel+1],actual[channel],tolerances[channel],
                        "h="+step+" t="+expected[0]+" channel="+channel);
            }
        }
        System.out.println("REGCA1 Iolim native checkpoint max errors h="+step+": "+Arrays.toString(maximum));
    }

    // Every source CSV has two exact cold repeats and clean native initialization/network logs.
    // Quarter: 80e092f8bc0c3a523d1a0d301ca6b9067b1713ac128918f604683866641cd13a
    // Eighth:  a0171d86330529e48d406363ab747754d20ff2c61b06c92f2fe209b7d599c96d
    // Native single-precision clocks are mapped to nominal ticks; instantaneous event-post
    // samples, initialization prehistory and one eighth-step overshoot sample stay in raw
    // artifacts. Public checkpoints cover Ip/Iq lag states, voltage filter, terminal V and Q.
    private static final String QUARTER = """
            0,0,0,1,1,-0
            0.025,0,0,1,1,-0
            0.05,0,0,1,1,-0
            0.05025,0,0,0.991051197052,0.0454549640417,-0
            0.051,0,0.00528162904084,0.953342080116,0.0455027259886,0.000240328517975
            0.055,0,0.126214832067,0.788875460625,0.0466584935784,0.00588899385184
            0.06,0,0.341621041298,0.624904751778,0.047840911895,0.0119602279738
            0.075,0,0.741749644279,0.320439994335,0.047840911895,0.0119602279738
            0.09,0,0.930766165257,0.176613926888,0.047840911895,0.0119602279738
            0.1,0,0.997351229191,0.125948265195,0.047840911895,0.0119602279738
            0.10025,0,0.998626291752,0.134394958615,1.05237066746,0.263092666864
            0.101,0,0.95560669899,0.171242341399,1.05245983601,0.263114959002
            0.105,0,0.793408036232,0.330976843834,1.05249989033,0.263124972582
            0.11,0,0.647692739964,0.490568041801,1.05249989033,0.263124972582
            0.12,0,0.481131911278,0.71165984869,1.05249989033,0.263124972582
            0.15,0,0.367096334696,0.976440489292,1.05249989033,0.263124972582
            0.2,0,0.334502220154,1.04625582695,1.05249989033,0.263124972582
            0.25,0,0.26614227891,1.05198705196,1.05249989033,0.263124972582
            0.2625,0,0.246035158634,1.05219256878,1.05184364319,0.258790522814
            0.27,0,0.23361608386,1.05165314674,1.04924333096,0.245120123029
            0.3,0,0.18276527524,1.04464113712,1.03856730461,0.189814046025
            0.4,0,0.0371577739716,1.01333475113,1.00793218613,0.0374525152147
            0.5,0,-0.0525981895626,0.992178738117,0.989026963711,-0.0520210266113
            """;
    private static final String EIGHTH = """
            0,0,0,1,1,-0
            0.025,0,0,1,1,-0
            0.05,0,0,1,1,-0
            0.05025,0,0.000209737569094,0.988110184669,0.0454564131796,9.53391736402e-06
            0.051,0,0.00564995128661,0.953421056271,0.0455072745681,0.000257113890257
            0.055,0,0.126386582851,0.788933873177,0.0466557331383,0.00589665863663
            0.06,0,0.341589748859,0.624942541122,0.047840911895,0.0119602279738
            0.075,0,0.741747975349,0.320447802544,0.047840911895,0.0119602279738
            0.09,0,0.930771708488,0.176612824202,0.047840911895,0.0119602279738
            0.1,0,0.997356891632,0.125945657492,0.047840911895,0.0119602279738
            0.100125,0,0.997996389866,0.130167469382,1.05237066746,0.263092666864
            0.101,0,0.952467560768,0.171158820391,1.05249154568,0.263122886419
            0.105,0,0.790817797184,0.330916404724,1.05249989033,0.263124972582
            0.11,0,0.645657598972,0.490527778864,1.05249989033,0.263124972582
            0.12,0,0.479879081249,0.711643815041,1.05249989033,0.263124972582
            0.15,0,0.366811126471,0.976442754269,1.05249989033,0.263124972582
            0.2,0,0.334481447935,1.04625618458,1.05249989033,0.263124972582
            0.25,0,0.266143530607,1.05198740959,1.05249989033,0.263124972582
            0.2625,0,0.246037423611,1.0521863699,1.05175817013,0.25877186656
            0.27,0,0.233619257808,1.05162072182,1.04915201664,0.245102122426
            0.3,0,0.182791754603,1.04456365108,1.03847944736,0.189825475216
            0.4,0,0.0373488813639,1.01329791546,1.00790762901,0.0376442223787
            0.5,0,-0.0523622110486,0.992186427116,0.989040136337,-0.0517883300781
            """;
}
