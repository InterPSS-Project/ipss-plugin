package org.interpss.core.dstab;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Arrays;
import java.util.HexFormat;

import org.apache.commons.math3.complex.Complex;
import org.interpss.IpssCorePlugin;
import org.interpss.core.dstab.reference.Pss1aPowerWorldActiveReference;
import org.interpss.fadapter.builder.DStabNetworkBuilder;
import org.interpss.fadapter.psse.PSSEMultiFileLoader;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import com.interpss.dstab.algo.DynamicSimuMethod;
import com.interpss.dstab.cache.StateMonitor;

/** Exact typed PowerWorld speed/voltage playback with independently active branches. */
public class Pss1aPowerWorldActiveConformanceTest {
    private static final double[] COARSE={5e-7,9e-7,5e-6,2e-8,1.5e-6,4e-8,5e-6};
    private static final double[] FINE={1.5e-7,2.5e-7,1.2e-6,1e-8,9e-7,2e-8,1.2e-6};

    @ParameterizedTest
    @ValueSource(strings={"limits","upper-gate","lower-window"})
    void activeBranchesMatchExactPowerWorldAtTwoSteps(String profile) throws Exception {
        double[] coarse=replay(profile,"step05",.0005,COARSE);
        double[] fine=replay(profile,"step025",.00025,FINE);
        for(int i=0;i<coarse.length;i++)
            assertTrue(fine[i]<coarse[i]*.7,profile+" channel "+i+" must improve under step halving");
    }

    @Test
    void fingerprintsAreFrozenAndRejectMutationsAndUnknownNames() throws Exception {
        for(String profile:new String[]{"limits","upper-gate","lower-window"})
            for(String step:new String[]{"step05","step025"}) {
                String id=profile+"-"+step;
                assertEquals(64,Pss1aPowerWorldActiveReference.rawSourceDigest(id).length());
                for(String suffix:new String[]{"inputs","checkpoints"}) {
                    String name=id+"-"+suffix;
                    byte[] bytes=Pss1aPowerWorldActiveReference.text(name).getBytes(StandardCharsets.UTF_8);
                    String expected=Pss1aPowerWorldActiveReference.expectedDigest(name);
                    assertEquals(expected,digest(bytes),name);
                    bytes[0]^=1;
                    assertNotEquals(expected,digest(bytes),name+" must reject a changed value");
                }
            }
        assertThrows(IllegalArgumentException.class,()->Pss1aPowerWorldActiveReference.text("unknown"));
        assertThrows(IllegalArgumentException.class,()->Pss1aPowerWorldActiveReference.expectedDigest("unknown"));
        assertThrows(IllegalArgumentException.class,()->Pss1aPowerWorldActiveReference.rawSourceDigest("unknown"));
    }

    @Test
    void cutoffThresholdEqualityIsInclusiveAndReadsDoNotChangeStates() throws Exception {
        IpssCorePlugin.init();
        var context=new PSSEMultiFileLoader().loadDStab(
                "testData/adpter/psse/v33/SMIB/SMIB_v33.raw",
                "testData/adpter/psse/v33/SMIB/SMIB_v33_genrou_esst1a.dyr");
        var network=context.getDStabilityNet();
        var pss=new DStabNetworkBuilder(network).addPss1a("Bus1","1",
                1,.061,.0017,.30,.03,.30,.03,5,.020,5,.05,-.05,1.002,.99);
        var algorithm=context.getDynSimuAlgorithm();
        assertTrue(algorithm.getAclfAlgorithm().loadflow());
        algorithm.setSimuOutputHandler(new StateMonitor());
        assertTrue(algorithm.initialization());
        var machine=network.getMachine("Bus1-mach1");
        machine.setSpeed(1.001);
        for(int i=0;i<200;i++) {
            assertTrue(pss.nextStep(.0005,DynamicSimuMethod.MODIFIED_EULER,machine,0));
            assertTrue(pss.nextStep(.0005,DynamicSimuMethod.MODIFIED_EULER,machine,1));
        }
        machine.getDStabBus().setVoltage(new Complex(1,0));
        double enabled=pss.getOutput(machine);
        assertTrue(Math.abs(enabled)>1e-6);
        var states=pss.getNamedStates();
        for(double voltage:new double[]{.99,1.002}) {
            machine.getDStabBus().setVoltage(new Complex(voltage,0));
            assertEquals(enabled,pss.getOutput(machine),0,"Equality must not trigger cutoff");
        }
        for(double voltage:new double[]{Math.nextDown(.99),Math.nextUp(1.002)}) {
            machine.getDStabBus().setVoltage(new Complex(voltage,0));
            assertEquals(0,pss.getOutput(machine),0,"Outside threshold must cut out");
        }
        assertEquals(states,pss.getNamedStates(),"Algebraic cutout cannot mutate dynamic states");
        pss.vcu=0;pss.vcl=0;
        machine.getDStabBus().setVoltage(new Complex(.8,0));
        assertEquals(enabled,pss.getOutput(machine),0,"Zero thresholds disable the cutouts");
        assertEquals(states,pss.getNamedStates());
    }

    private static double[] replay(String profile,String grid,double step,double[] bounds) throws Exception {
        IpssCorePlugin.init();
        double maximum=profile.equals("limits")?.01:.05;
        double upper=profile.equals("upper-gate")?1.002:profile.equals("lower-window")?2:0;
        double lower=profile.equals("lower-window")?.99:0;
        var context=new PSSEMultiFileLoader().loadDStab(
                "testData/adpter/psse/v33/SMIB/SMIB_v33.raw",
                "testData/adpter/psse/v33/SMIB/SMIB_v33_genrou_esst1a.dyr");
        var network=context.getDStabilityNet();
        var pss=new DStabNetworkBuilder(network).addPss1a("Bus1","1",
                1,.061,.0017,.30,.03,.30,.03,5,.020,5,maximum,-maximum,upper,lower);
        assertNotNull(pss);
        var algorithm=context.getDynSimuAlgorithm();
        assertTrue(algorithm.getAclfAlgorithm().loadflow());
        algorithm.setSimuMethod(DynamicSimuMethod.MODIFIED_EULER);
        algorithm.setSimuStepSec(step);
        algorithm.setSimuOutputHandler(new StateMonitor());
        assertTrue(algorithm.initialization());
        var machine=network.getMachine("Bus1-mach1");
        String id=profile+"-"+grid;
        var input=Pss1aPowerWorldActiveReference.text(id+"-inputs").lines()
                .map(Pss1aPowerWorldActiveConformanceTest::parse).toList();
        var expected=Pss1aPowerWorldActiveReference.text(id+"-checkpoints").lines()
                .map(Pss1aPowerWorldActiveConformanceTest::parse).toList();
        assertEquals((int)Math.round(1/step)+1,input.size());
        assertEquals(201,expected.size());
        double[] errors=new double[7];
        int next=0,atUpper=0,atLower=0,gated=0,changingWhileGated=0,enabled=0;
        boolean upperReleased=false,lowerReleased=false;
        double previous=0;
        for(int tick=0;tick<input.size();tick++) {
            if(tick>0) {
                machine.setSpeed(input.get(tick-1)[0]);
                machine.getDStabBus().setVoltage(new Complex(input.get(tick-1)[1],0));
                assertTrue(pss.nextStep(step,DynamicSimuMethod.MODIFIED_EULER,machine,0));
                machine.setSpeed(input.get(tick)[0]);
                machine.getDStabBus().setVoltage(new Complex(input.get(tick)[1],0));
                assertTrue(pss.nextStep(step,DynamicSimuMethod.MODIFIED_EULER,machine,1));
            } else machine.getDStabBus().setVoltage(new Complex(input.get(0)[1],0));
            double output=pss.getOutput(machine);
            var before=pss.getNamedStates();
            double[] actual=Pss1aPowerWorldPlaybackConformanceTest.publishedSignals(pss,output);
            assertArrayEquals(actual,Pss1aPowerWorldPlaybackConformanceTest.publishedSignals(pss,output),0);
            assertEquals(before,pss.getNamedStates());
            double voltage=input.get(tick)[1];
            boolean cutoff=(upper!=0&&voltage>upper)||(lower!=0&&voltage<lower);
            assertEquals(cutoff?0:Math.max(-maximum,Math.min(maximum,actual[2])),output,1e-15,
                    "Only the algebraic output is clipped/gated");
            if(Math.abs(actual[2])>1e-6) {
                if(cutoff){gated++;if(Math.abs(actual[2]-previous)>1e-12)changingWhileGated++;}
                else enabled++;
            }
            if(actual[2]>maximum+1e-6)atUpper++;
            if(actual[2]<-maximum-1e-6)atLower++;
            if(atUpper>0&&actual[2]<maximum-1e-5)upperReleased=true;
            if(atLower>0&&actual[2]>-maximum+1e-5)lowerReleased=true;
            previous=actual[2];
            if(next<expected.size()&&Math.abs(expected.get(next)[0]-tick*step)<1e-10) {
                var checkpoint=expected.get(next++);
                for(int i=0;i<errors.length;i++) {
                    assertTrue(Double.isFinite(actual[i]));
                    errors[i]=Math.max(errors[i],Math.abs(actual[i]-checkpoint[i+1]));
                }
            }
        }
        assertEquals(expected.size(),next,"Every checkpoint including event-pre must be scored");
        if(profile.equals("limits")) {
            assertTrue(atUpper>100&&atLower>100,"Both physical limits must be active");
            assertTrue(upperReleased&&lowerReleased,"Both bounds must release without freezing filter states");
        } else {
            assertTrue(gated>50&&enabled>50,"The profile must exercise cutoff and enabled output");
            assertTrue(changingWhileGated>20,"Dynamic states must continue while output is cut out");
        }
        for(int i=0;i<errors.length;i++)
            assertTrue(errors[i]<=bounds[i],id+" channel "+i+" max="+errors[i]+" bound="+bounds[i]);
        System.out.println("PSS1A active "+id+" errors="+Arrays.toString(errors)
                +" upper="+atUpper+" lower="+atLower+" cutout="+gated);
        return errors;
    }

    private static double[] parse(String row) {
        return Arrays.stream(row.split(",")).mapToDouble(Double::parseDouble).toArray();
    }

    private static String digest(byte[] bytes) throws Exception {
        return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
    }
}
