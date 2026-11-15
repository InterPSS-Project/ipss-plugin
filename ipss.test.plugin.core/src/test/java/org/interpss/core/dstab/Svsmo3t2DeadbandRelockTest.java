package org.interpss.core.dstab;

import static org.junit.jupiter.api.Assertions.*;
import java.nio.file.*;
import java.util.*;
import java.security.MessageDigest;
import java.util.zip.ZipInputStream;
import org.apache.commons.math3.complex.Complex;
import org.interpss.CorePluginTestSetup;
import org.interpss.dstab.svc.Svsmo3t2Model;
import org.interpss.fadapter.psse.PSSEMultiFileLoader;
import com.interpss.dstab.algo.DynamicSimuMethod;
import com.interpss.dstab.cache.StateMonitor;
import org.junit.jupiter.api.Test;

/** Independent deadband input/reset evidence, distinct from complete trajectory acceptance. */
public class Svsmo3t2DeadbandRelockTest extends CorePluginTestSetup {
    private static final double H=.0005;
    @Test void smallPulseSensorCrossingFollowsTheOrdinaryTwoStepRate() throws Exception {
        var samples=rows(reference().resolve("middle-a/reference.csv"));
        int checked=0, crossings=0;
        for(int i=2;i<samples.size();i++) {
            var previous=samples.get(i-2);var left=samples.get(i-1);var right=samples.get(i);
            double time=right.get("time_s"),h=time-left.get("time_s");
            if(time<.185||time>.1881||h<=0||left.get("time_s")<=previous.get("time_s"))continue;
            double rate=(left.get("V_3")-left.get("A_STATE_0"))/.05;
            double oldRate=(previous.get("V_3")-previous.get("A_STATE_0"))/.05;
            assertEquals(left.get("A_STATE_0")+h*(1.5*rate-.5*oldRate),
                    right.get("A_STATE_0"),4e-8,"Sensor recurrence at "+time);
            checked++;
            if(Math.abs(time-.187999352813)<2e-6) {
                assertTrue(left.get("A_STATE_0")<.995);
                assertTrue(right.get("A_STATE_0")>.995);
                crossings++;
            }
        }
        assertTrue(checked>=5);
        assertEquals(1,crossings,"The captured sensor crosses the exact return-band boundary once");
    }
    @Test void everyHeldLagEndpointOmitsPriorSlopeWhileLeadLagHistoryRemainsLive() throws Exception {
        Path root=reference();
        for(String profile:List.of("enabled","middle","timer","unequal")) {
            var samples=rows(root.resolve(profile+"-a/reference.csv"));
            int heldIntervals=0, repeatedNonzeroIntervals=0;
            double worstRetainedHistoryError=0;
            for(int i=2;i<samples.size()-1;i++) {
                var old=samples.get(i-1);var left=samples.get(i);var right=samples.get(i+1);
                // Exclude duplicate event boundaries; no sample is removed from the fixture.
                if(left.get("time_s")<=.05 || !(right.get("time_s")>left.get("time_s")
                        && left.get("time_s")>old.get("time_s"))
                        || left.get("A_VAR_5")!=0 || right.get("A_VAR_5")!=0) continue;
                double slope=(left.get("A_VAR_3")-left.get("A_STATE_2"))/.01;
                double previous=(old.get("A_VAR_3")-old.get("A_STATE_2"))/.01;
                double increment=right.get("A_STATE_2")-left.get("A_STATE_2");
                assertEquals(1.5*H*slope,increment,5e-8,
                        profile+" held lag recurrence at "+left.get("time_s"));
                worstRetainedHistoryError=Math.max(worstRetainedHistoryError,
                        Math.abs(increment-H*(1.5*slope-.5*previous)));
                if(old.get("A_VAR_5")==0 && Math.abs(previous)>1e-3) repeatedNonzeroIntervals++;
                heldIntervals++;
            }
            assertTrue(heldIntervals>40,profile+" must exercise held recurrence repeatedly");
            assertTrue(worstRetainedHistoryError>5e-4,profile+" must distinguish retained lag history");
            if(profile.equals("unequal")) assertTrue(repeatedNonzeroIntervals>100,
                    "A transition-only reset cannot explain repeated unequal-lead-lag increments");
            System.out.println("SVS_HELD_LAG "+profile+" intervals="+heldIntervals
                    +" repeatedNonzero="+repeatedNonzeroIntervals
                    +" retainedSlopeMaximumError="+worstRetainedHistoryError);
        }
    }
    @Test void sourceRepeatsDiscriminateRelockAndStoredLeadLagDecay() throws Exception {
        Path root=reference();
        for(String profile:List.of("enabled","disabled","middle","timer","unequal","bypass")) {
            var a=rows(root.resolve(profile+"-a/reference.csv"));
            var b=rows(root.resolve(profile+"-b/reference.csv"));
            assertEquals(2005,a.size()); assertEquals(a.size(),b.size());
            for(int i=0;i<a.size();i++) for(String key:a.get(i).keySet()) {
                assertTrue(Double.isFinite(a.get(i).get(key)));
                // This unused, disabled MSS slot is retained but is not assigned by the source.
                if(!key.equals("A_VAR_18")) assertEquals(a.get(i).get(key),b.get(i).get(key),0,key);
            }
            if(profile.equals("disabled")) continue;
            int resets=0,decays=0;
            for(int i=2;i<a.size();i++) {
                var old=a.get(i-2);var left=a.get(i-1);var right=a.get(i);
                if(right.get("time_s")<=.05) continue;
                if(Math.abs(right.get("A_STATE_1")-left.get("A_STATE_1"))>.01) {
                    resets++;
                    assertEquals(right.get("A_STATE_2"),right.get("A_STATE_1"),0);
                    assertEquals(0,right.get("A_VAR_5"),0);
                    assertTrue(Math.abs(right.get("A_VAR_6")-right.get("A_VAR_8"))<.005);
                    assertTrue(Math.abs(left.get("A_VAR_6")-left.get("A_VAR_8"))>=.005);
                }
                if(right.get("time_s")>left.get("time_s") && left.get("time_s")>old.get("time_s")
                        && right.get("A_VAR_5")==0 && left.get("A_VAR_5")==0
                        && Math.abs(left.get("A_STATE_5"))>1e-5) {
                    double f=(left.get("A_VAR_5")-left.get("A_STATE_5"))/.01;
                    double previous=(old.get("A_VAR_5")-old.get("A_STATE_5"))/.01;
                    assertEquals(left.get("A_STATE_5")+H*(1.5*f-.5*previous),right.get("A_STATE_5"),4e-8);
                    decays++;
                }
            }
            assertTrue(resets>0);assertTrue(decays>10);
        }
        assertEquals(rows(root.resolve("middle-a/reference.csv")),rows(root.resolve("timer-a/reference.csv")),
                "Changing only deadband time from .01 to .04 leaves this control exactly unchanged");
    }
    @Test void bothIntegrationContractsResetAcceptedCommandAndRetainDecayingStorage() throws Exception {
        Path root=reference().resolve("enabled-a");
        for(boolean shared:List.of(false,true)) {
            var model=model(root);
            model.setScheduledVoltage(1.02);model.completeStaggeredStep(H);
            model.setScheduledVoltage(1.0);
            double[] x={1,.3,.48,0,0,.008};model.applyStaggeredState(x,0,H);
            double[] f=new double[6];model.evaluateStaggered(H,f,0);
            assertEquals(-.8,f[5],1e-12,"Held input must not freeze lead-lag storage");
            assertEquals(0,f[1],0);assertEquals(-18,f[2],1e-12);
            var history=model.getIntegrationHistories();
            model.evaluateStaggered(H,new double[6],0);assertEquals(history,model.getIntegrationHistories());
            if(shared) {
                double[] trial=x.clone();for(int i=0;i<6;i++) trial[i]+=H*f[i];
                model.applyStaggeredState(trial,0,H);model.completeStaggeredStep(H);
                assertEquals(.008*(1-H/.01),numerical(model)[5],1e-14);
            } else {
                assertTrue(model.nextStep(H,DynamicSimuMethod.MODIFIED_EULER,0));
                assertFalse(model.nextStep(H,DynamicSimuMethod.MODIFIED_EULER,0));
                assertTrue(model.nextStep(H,DynamicSimuMethod.MODIFIED_EULER,1));
                assertEquals(.008*(1-H/.01+.5*Math.pow(H/.01,2)),numerical(model)[5],1e-14);
            }
            var accepted=numerical(model);assertEquals(accepted[2],accepted[1],0);
            var acceptedHistory=model.getIntegrationHistories();
            if(shared) {
                double[] invalid=accepted.clone();invalid[5]=Double.NaN;
                assertThrows(IllegalArgumentException.class,()->model.applyStaggeredState(invalid,0,H));
            } else {
                model.getDStabBus().setVoltage(new Complex(Double.NaN,0));
                assertFalse(model.nextStep(H,DynamicSimuMethod.MODIFIED_EULER,0));
                model.getDStabBus().setVoltage(Complex.ONE);
            }
            assertArrayEquals(accepted,numerical(model),0);assertEquals(acceptedHistory,model.getIntegrationHistories());

            model.setScheduledVoltage(1.007);model.evaluateStaggered(H,f,0);
            assertEquals(.035,f[1],1e-12,"Reference pulse reopens input without an extra accepted step");
        }
    }
    @Test void unequalLeadLagRetainsProportionalOutputAndBypassReopensImmediately() throws Exception {
        Path root=reference();
        var data=rows(root.resolve("unequal-a/reference.csv"));int held=0;
        for(var row:data) if(row.get("time_s")>.05 && row.get("A_VAR_5")==0) {
            assertEquals(row.get("A_STATE_2"),row.get("A_STATE_1"),0);
            assertEquals(.5*row.get("A_STATE_5"),row.get("A_VAR_4"),2e-9);
            assertEquals(row.get("A_STATE_1")+20*row.get("A_VAR_4"),row.get("A_VAR_3"),8e-8);held++;
        }
        assertTrue(held>100);
        var model=model(root.resolve("unequal-a"));
        model.applyStaggeredState(new double[]{1,.3,.48,0,0,.008},0,H);
        model.completeStaggeredStep(H);
        var first=numerical(model);assertEquals(first[2],first[1],0);
        double[] f=new double[6];model.evaluateStaggered(H,f,0);
        assertEquals(.02,f[1],1e-12);assertEquals(8,f[2],1e-12);assertEquals(-.8,f[5],1e-12);
        first[2]+=.001;model.applyStaggeredState(first,0,H);model.completeStaggeredStep(H);
        assertEquals(numerical(model)[2],numerical(model)[1],0,"Every held endpoint resets, not only transition");
        var bypass=model(root.resolve("bypass-a"));double initial=numerical(bypass)[2];
        bypass.setScheduledVoltage(1.007);var before=bypass.getIntegrationHistories();
        assertTrue(bypass.nextStep(0,DynamicSimuMethod.MODIFIED_EULER,0));
        assertEquals(initial+.14,numerical(bypass)[2],2e-7);
        assertEquals(before,bypass.getIntegrationHistories(),"Algebraic reopening cannot consume history");
        var shared=model(root.resolve("bypass-a"));double[] x=numerical(shared);shared.setScheduledVoltage(1.007);
        shared.applyStaggeredState(x,0,0);assertEquals(x[2]+.14,numerical(shared)[2],2e-7);
    }
    private static double[] numerical(Svsmo3t2Model m){double[] x=new double[6];m.readStaggeredState(x,0);return x;}
    static Svsmo3t2Model model(Path root)throws Exception {
        var c=new PSSEMultiFileLoader().loadDStab(root.resolve("smib.raw").toString(),root.resolve("smib.dyr").toString());
        var a=c.getDynSimuAlgorithm();
        // The recorded fixture and the manual stages below use this same physical interval.
        a.setSimuStepSec(H);
        a.getAclfAlgorithm().getLfAdjAlgo().setLocalContinuousSvcAsPv(true);
        a.getAclfAlgorithm().setTolerance(1e-10);assertTrue(a.getAclfAlgorithm().loadflow());a.setSimuOutputHandler(new StateMonitor());assertTrue(a.initialization());
        var b=c.getDStabilityNet().getDStabBus("Bus3");b.setVoltage(Complex.ONE);
        return (Svsmo3t2Model)b.getDynamicBusDeviceList().stream().filter(Svsmo3t2Model.class::isInstance).findFirst().orElseThrow();
    }
    private static List<Map<String,Double>> rows(Path p)throws Exception {
        var lines=Files.readAllLines(p);String[] names=lines.getFirst().split(",");var result=new ArrayList<Map<String,Double>>();
        for(String line:lines.subList(1,lines.size())){String[] v=line.split(",");var row=new LinkedHashMap<String,Double>();for(int i=0;i<v.length;i++)row.put(names[i],Double.parseDouble(v[i]));result.add(row);}return result;
    }
    static Path reference()throws Exception {
        byte[] bytes;try(var in=Svsmo3t2DeadbandRelockTest.class.getResourceAsStream("/dynamic-models/svsmo3t2/deadband-relock.zip")){assertNotNull(in);bytes=in.readAllBytes();}
        assertEquals("b11a6d7d8b8ff49808e8091a8414723688815aab261fb9eefa543f62965a815b",HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes)));
        Path root=Files.createTempDirectory(Path.of("target"),"svs-deadband-reference-").toAbsolutePath();
        try(var z=new ZipInputStream(new java.io.ByteArrayInputStream(bytes))){java.util.zip.ZipEntry e;while((e=z.getNextEntry())!=null){Path p=root.resolve(e.getName()).normalize();assertTrue(p.startsWith(root));Files.createDirectories(p.getParent());Files.copy(z,p);}}
        return root;
    }
}
