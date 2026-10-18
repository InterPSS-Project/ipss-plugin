package org.interpss.core.dstab;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Arrays;
import java.util.HexFormat;

import org.interpss.IpssCorePlugin;
import org.interpss.core.dstab.reference.Pss1aPowerWorldPlaybackReference;
import org.interpss.dstab.control.pss.ieee.y1992.pss1a.Ieee1992PSS1AStabilizer;
import org.interpss.fadapter.builder.DStabNetworkBuilder;
import org.interpss.fadapter.psse.PSSEMultiFileLoader;
import org.junit.jupiter.api.Test;

import com.interpss.dstab.algo.DynamicSimuMethod;
import com.interpss.dstab.cache.StateMonitor;
import com.interpss.dstab.controller.cml.field.block.TFunc2ndOrderBlock;

/**
 * Exact typed PowerWorld PSS1A speed playback, not native PTI PSS1A DYR evidence
 * and not acceptance of the still-discrepant complete SMIB network trajectory.
 * Public inputs are the recorded speed; checkpoints contain all six published
 * stabilizer states and Vstab every 5 ms, including both event times (pre-side).
 */
public class Pss1aPowerWorldPlaybackConformanceTest {
    private static final String[] LABELS={"Washout","LL1","LL2","Transducer",
            "Filter 1","Filter Out","Vstab"};

    @Test
    void recordedSpeedMatchesExactPowerWorldModelAtTwoSteps() throws Exception {
        double[] coarse=replay("step05",.0005,new double[]{5e-7,8e-7,5e-6,2e-8,1.5e-6,4e-8,5e-6});
        double[] fine=replay("step025",.00025,new double[]{1.4e-7,2e-7,1.1e-6,8e-9,5e-7,1.3e-8,1.1e-6});
        for(int i=0;i<coarse.length;i++)
            assertTrue(fine[i]<coarse[i]*.6,LABELS[i]+" error must decrease under step halving");
    }

    @Test
    void frozenInputsAndCheckpointsRejectMutation() throws Exception {
        String[] names={"step05-speed.txt","step05-checkpoints.txt",
                "step025-speed.txt","step025-checkpoints.txt"};
        String[] digests={"3335e495dcd938588b1e8312b1adda8d4609d2f91d52cdc891fbae0005942629",
                "7e1bc788c1c4f35888c3ff35158c57e51cd2896a1746a7ea97bd59f1ca69263b",
                "630c53537e0309e1d6df5f6d0b4d8494535af6481d0a50d5784df6e4bdfb0a3b",
                "36af95d548f72593026dbaa81bddcbd23b6a06e8c3eaf87caf4d6eba191fc484"};
        for(int i=0;i<names.length;i++) {
            byte[] bytes=text(names[i]).getBytes(StandardCharsets.UTF_8);
            assertEquals(digests[i],digest(bytes),names[i]);
            bytes[0]^=1;
            assertNotEquals(digests[i],digest(bytes),"A mutation must not self-certify");
        }
    }

    private static double[] replay(String profile,double step,double[] bounds) throws Exception {
        IpssCorePlugin.init();
        var context=new PSSEMultiFileLoader().loadDStab(
                "testData/adpter/psse/v33/SMIB/SMIB_v33.raw",
                "testData/adpter/psse/v33/SMIB/SMIB_v33_genrou_esst1a.dyr");
        var network=context.getDStabilityNet();
        var algorithm=context.getDynSimuAlgorithm();
        var pss=new DStabNetworkBuilder(network).addPss1a("Bus1","1",
                1,.061,.0017,.30,.03,.30,.03,5,.020,5,.05,-.05,0,0);
        assertNotNull(pss);
        assertTrue(algorithm.getAclfAlgorithm().loadflow());
        algorithm.setSimuMethod(DynamicSimuMethod.MODIFIED_EULER);
        algorithm.setSimuStepSec(step);
        algorithm.setSimuOutputHandler(new StateMonitor());
        assertTrue(algorithm.initialization());
        var machine=network.getMachine("Bus1-mach1");
        assertSame(pss,machine.getStabilizer());
        double[] speeds=text(profile+"-speed.txt").lines().mapToDouble(Double::parseDouble).toArray();
        var checkpoints=text(profile+"-checkpoints.txt").lines()
                .map(line->Arrays.stream(line.split(",")).mapToDouble(Double::parseDouble).toArray()).toList();
        assertEquals((int)Math.round(1/step)+1,speeds.length);
        assertEquals(201,checkpoints.size());
        assertEquals(1,speeds[0],0);
        double[] maximum=new double[7];
        double[] maximumTime=new double[7];
        int next=0;
        for(int tick=0;tick<speeds.length;tick++) {
            if(tick>0) {
                machine.setSpeed(speeds[tick-1]);
                assertTrue(pss.nextStep(step,DynamicSimuMethod.MODIFIED_EULER,machine,0));
                machine.setSpeed(speeds[tick]);
                assertTrue(pss.nextStep(step,DynamicSimuMethod.MODIFIED_EULER,machine,1));
            }
            double output=pss.getOutput(machine);
            if(next<checkpoints.size()&&Math.abs(checkpoints.get(next)[0]-tick*step)<1e-10) {
                double[] expected=checkpoints.get(next++);
                var namedBefore=pss.getNamedStates();
                double[] actual=publishedSignals(pss,output);
                assertArrayEquals(actual,publishedSignals(pss,output),0,
                        "Published-state observation must be repeatable without an integration step");
                assertEquals(namedBefore,pss.getNamedStates(),"Observation cannot mutate CML states");
                for(int i=0;i<actual.length;i++) {
                    assertTrue(Double.isFinite(actual[i]),LABELS[i]);
                    double error=Math.abs(actual[i]-expected[i+1]);
                    if(error>maximum[i]){maximum[i]=error;maximumTime[i]=tick*step;}
                }
            }
        }
        assertEquals(checkpoints.size(),next,"Every checkpoint, including event-pre samples, is scored");
        for(int i=0;i<maximum.length;i++)
            assertTrue(maximum[i]<=bounds[i],profile+" "+LABELS[i]+" max="+maximum[i]
                    +" at "+maximumTime[i]+" bound="+bounds[i]);
        System.out.println("PSS1A exact PowerWorld playback "+profile+" max errors="+Arrays.toString(maximum));
        return maximum;
    }

    private static double[] publishedSignals(Ieee1992PSS1AStabilizer pss,double output) throws Exception {
        // Test-only coordinate audit: CML owns the blocks in its state registry;
        // the model's Java block fields may remain null. Do not change the solver
        // realization to match a commercial display coordinate. The CML API has
        // no public second-state accessor, so only this audit reads it reflectively.
        var blocks=pss.getNamedStateBlocks();
        var filter=blocks.get("order2ndBlock");
        var second=TFunc2ndOrderBlock.class.getDeclaredField("stateX2");
        second.setAccessible(true);
        double y=filter.getY();
        // z=ydot+(A1/A2)y yields zdot=(u-y)/A2, ydot=z-(A1/A2)y.
        double filter1=second.getDouble(filter)+(pss.a1/pss.a2)*y;
        return new double[]{blocks.get("washoutBlock").getY(),blocks.get("filterBlock1").getY(),
                blocks.get("filterBlock2").getY(),blocks.get("delayBlock").getY(),filter1,y,output};
    }

    private static String text(String name) throws Exception {
        return Pss1aPowerWorldPlaybackReference.text(name);
    }

    private static String digest(byte[] bytes) throws Exception {
        return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
    }
}
