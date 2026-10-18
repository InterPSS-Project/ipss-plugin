package org.interpss.core.dstab;

import static org.junit.jupiter.api.Assertions.*;
import java.nio.file.*;
import java.util.*;
import org.apache.commons.math3.complex.Complex;
import org.interpss.core.dstab.reference.RepccKernelNativeReference;
import org.interpss.dstab.renewable.*;
import org.junit.jupiter.api.Test;

/** Replay physical measurements and external references, never native state vectors. */
public class RepccKernelNativeConformanceTest {
    private static List<Map<String,Double>> rows(String text){
        var lines=text.lines().toList();String[] headers=lines.getFirst().split(",");var result=new ArrayList<Map<String,Double>>();
        for(String line:lines.subList(1,lines.size())){String[] fields=line.split(",");assertEquals(headers.length,fields.length);var row=new HashMap<String,Double>();
            for(int i=0;i<headers.length;i++){double value=Double.parseDouble(fields[i]);assertTrue(Double.isFinite(value));row.put(headers[i],value);}result.add(row);}
        return result;
    }
    private static Complex voltage(Map<String,Double> r,int bus){
        double a=Math.toRadians(r.get("A"+bus+"_DEG"));return new Complex(Math.cos(a),Math.sin(a)).multiply(r.get("V"+bus));
    }
    private static RepccKernel.Input input(Map<String,Double> r,RepccData d){
        double p=r.get("PLANT_VAR4")/50,q=r.get("PLANT_VAR5")/50;
        return new RepccKernel.Input(voltage(r,d.remoteBus()),new Complex(p,q).divide(voltage(r,2)).conjugate(),p,q,r.get("FREQ3_DEV"),r.get("V3"));
    }
    private static RepccKernel.References references(Map<String,Double> r){return new RepccKernel.References(r.get("PLANT_VAR0"),r.get("PLANT_VAR1"),
            r.get("PLANT_VAR2"),r.get("PLANT_VAR3"),r.get("PLANT_VAR9"),r.get("PLANT_VAR10"),r.get("PLANT_VAR11"));}
    private static double[] bounds(String name){return switch(name){
        case "reference-coarse" -> new double[]{3.393e-07,1e-12,7.976e-06,3.086e-05,7.951e-07,2.569e-07,5.049e-05,1.21e-06,1e-12,1.696e-10,9.254e-05,1e-12,3.131e-05,5.049e-05,3.393e-07,2.331e-10};
        case "reference-fine" -> new double[]{9.462e-07,1e-12,2.855e-06,1.456e-05,2.955e-07,1.632e-07,2.998e-05,2.212e-06,1e-12,4.254e-11,5.307e-05,1e-12,1.468e-05,2.998e-05,9.462e-07,1.233e-10};
        case "q-reference-coarse" -> new double[]{1e-12,9.461e-08,4.058e-07,2.107e-06,7.951e-07,2.569e-07,5.049e-05,1.21e-06,1e-12,1.696e-10,9.254e-05,6.291e-05,1.713e-06,5.049e-05,6.285e-05,2.331e-10};
        case "q-reference-fine" -> new double[]{1e-12,7.394e-08,1.561e-06,2.959e-06,2.955e-07,1.632e-07,2.998e-05,2.212e-06,1e-12,4.254e-11,5.307e-05,2.796e-05,2.967e-06,2.998e-05,2.794e-05,1.233e-10};
        case "pf-reference-coarse" -> new double[]{1e-12,9.461e-08,7.834e-05,0.0003081,7.951e-07,2.569e-07,5.049e-05,1.21e-06,1e-12,1.696e-10,9.254e-05,1e-12,0.0001762,5.049e-05,2.695e-07,2.331e-10};
        case "pf-reference-fine" -> new double[]{1e-12,7.394e-08,3.847e-05,0.0001549,2.955e-07,1.632e-07,2.998e-05,2.212e-06,1e-12,4.254e-11,5.307e-05,1e-12,9.372e-05,2.998e-05,1.129e-07,1.233e-10};
        case "limits-coarse" -> new double[]{1e-12,7.12e-08,3.748e-07,2.09e-06,6.343e-07,1.499e-07,2.526e-05,1.055e-06,1e-12,1.053e-10,9.254e-05,6.291e-05,1.708e-06,2.526e-05,6.291e-05,1.341e-10};
        case "limits-fine" -> new double[]{1e-12,5.799e-08,1.67e-06,2.936e-06,3.222e-07,7.159e-08,1.499e-05,2.547e-06,1e-12,2.626e-11,5.307e-05,2.796e-05,2.948e-06,1.499e-05,2.795e-05,1.15e-10};
        case "voltage-coarse" -> new double[]{1e-12,1e-12,8.325e-06,3.113e-05,5.961e-09,6.14e-09,1.145e-08,1e-12,3.856e-10,1e-12,5.961e-09,1e-12,8.34e-05,1.145e-08,1.554e-09,1e-12};
        case "voltage-fine" -> new double[]{1e-12,1e-12,3.187e-06,1.383e-05,5.961e-09,6.141e-09,1.145e-08,1e-12,3.856e-10,1e-12,5.961e-09,1e-12,3.922e-05,1.145e-08,1.554e-09,1e-12};
        case "freeze-coarse" -> new double[]{6.771e-05,1e-12,4.141e-06,1.573e-05,0.0003133,1.461e-06,0.0001481,0.000744,1e-12,1.153e-09,5.961e-09,1e-12,1.574e-05,0.0001481,6.771e-05,1e-12};
        case "freeze-fine" -> new double[]{3.49e-05,1e-12,2.09e-06,7.979e-06,0.0001611,7.358e-07,7.428e-05,0.0003815,1e-12,2.878e-10,5.961e-09,1e-12,7.98e-06,7.428e-05,3.49e-05,1e-12};
        case "frequency-coarse" -> new double[]{6.526e-07,1e-12,3.129e-07,3.57e-07,1.337e-06,6.34e-07,0.0001242,1.088e-06,1e-12,3.197e-10,5.961e-09,1e-12,3.562e-07,0.0001242,6.526e-07,5.938e-11};
        case "frequency-fine" -> new double[]{1.556e-06,1e-12,3.149e-07,7.055e-07,4.345e-07,3.194e-07,6.231e-05,4.033e-06,1e-12,8.034e-11,5.961e-09,1e-12,7.046e-07,6.231e-05,1.556e-06,5.938e-11};
        default -> throw new IllegalArgumentException("Unreviewed profile "+name);
    };}

    @Test void allThirteenStatesAndThreeObservableOutputsAtEveryRawSample() throws Exception {
        assertEquals(14,RepccKernelNativeReference.profiles().size());
        for(var profile:RepccKernelNativeReference.profiles()){
            var samples=rows(profile.body());var d=profile.parameters();var initial=samples.stream().filter(r->r.get("time_s")==0).findFirst().orElseThrow();
            var in=input(initial,d);var k=new RepccKernel(d,true);
            k.initialize(new RepccInitialization.Measurements(in.regulatedVoltage(),in.branchCurrent(),in.active(),in.reactive(),2*initial.get("P_PU"),2*initial.get("Q_PU")));
            var previous=initial;double[] max=new double[16],at=new double[16];int history=0,duplicates=0,timerMismatch=0;
            double lastTime=-Double.MAX_VALUE;boolean lastNativeTimer=false;var nativeTransitions=new ArrayList<Double>();var mismatchedTimes=new ArrayList<Double>();
            double maximumCommonTimerError=0;
            StringBuilder trace=new StringBuilder("time_s");for(int i=0;i<13;i++)trace.append(",IPSS_STATE").append(i).append(",NATIVE_STATE").append(i);
            trace.append(",IPSS_TIMER,NATIVE_TIMER\n");
            for(var row:samples){double time=row.get("time_s");k.setReferences(references(row));
                if(time<0)history++;
                if(time==lastTime)duplicates++;lastTime=time;
                if(time>previous.get("time_s")){k.step(profile.step(),input(previous,d),0);k.step(profile.step(),input(row,d),1);previous=row;}
                else if(time>=0){k.step(0,input(row,d),0);previous=row;}
                double[] actual=Arrays.copyOf(k.states(),16);var out=k.output(input(row,d));actual[13]=out.active();actual[14]=out.reactiveError();actual[15]=out.frequencyError();
                double[] nativeValues=new double[16];for(int i=0;i<13;i++)nativeValues[i]=row.get("PLANT_STATE"+i);
                nativeValues[13]=row.get("PLANT_VAR6");nativeValues[14]=row.get("PLANT_VAR7");nativeValues[15]=row.get("PLANT_VAR8");
                for(int i=0;i<16;i++){double error=Math.abs(actual[i]-nativeValues[i]);if(error>max[i]){max[i]=error;at[i]=time;}}
                double timer=row.get("PLANT_VAR12");boolean nativeActive=Math.abs(timer)>1e-15,active=out.recoveryStart()>0;
                if(time>=0&&nativeActive!=lastNativeTimer){nativeTransitions.add(time);lastNativeTimer=nativeActive;}
                if(nativeActive!=active){timerMismatch++;mismatchedTimes.add(time);}
                if(nativeActive&&active)maximumCommonTimerError=Math.max(maximumCommonTimerError,Math.abs(timer-out.recoveryStart()));
                trace.append(time);for(int i=0;i<13;i++)trace.append(',').append(actual[i]).append(',').append(nativeValues[i]);
                trace.append(',').append(out.recoveryStart()).append(',').append(timer).append('\n');
            }
            Path path=Path.of("target/repcc-kernel-replay",profile.name()+".csv");Files.createDirectories(path.getParent());Files.writeString(path,trace.toString());
            System.out.println("REPCC replay "+profile.name()+" maxima="+Arrays.toString(max)+" times="+Arrays.toString(at));
            double[] limits=bounds(profile.name());for(int i=0;i<16;i++)assertTrue(max[i]<limits[i],profile.name()+" channel "+i+" error="+max[i]);
            assertEquals(profile.samples(),samples.size());assertEquals(2,history);assertEquals(5,duplicates);
            assertEquals(.3,k.time(),1e-12);assertEquals(13,k.getNamedStates().size());
            if(profile.name().startsWith("freeze")){
                assertEquals(2,nativeTransitions.size());assertTrue(timerMismatch<=4,"At most two one-step transition windows, including duplicate rows");
                for(double time:mismatchedTimes)assertTrue(nativeTransitions.stream().anyMatch(t->Math.abs(t-time)<=profile.step()+3e-6));
                assertTrue(maximumCommonTimerError<profile.step()+3e-6);
            } else {assertEquals(0,timerMismatch);assertTrue(nativeTransitions.isEmpty());}
        }
    }
    @Test void limiterAndFreezeDiscriminatorsActuallyActivateTheIntendedPaths(){
        for(var p:RepccKernelNativeReference.profiles()){
            var samples=rows(p.body());
            if(p.name().startsWith("limits")){
                assertTrue(samples.stream().anyMatch(r->Math.abs(r.get("PLANT_VAR2"))>.0009));
                double drift=samples.stream().mapToDouble(r->Math.abs(r.get("PLANT_STATE5"))).max().orElseThrow();assertTrue(drift<.0001);
                assertTrue(samples.stream().mapToDouble(r->r.get("PLANT_STATE6")).max().orElseThrow()-.4>.001);
            }
            if(p.name().startsWith("freeze")){
                var frozen=samples.stream().filter(r->r.get("time_s")>.065&&r.get("time_s")<.15).toList();assertTrue(frozen.size()>100);
                for(int state:new int[]{2,3,5,6}){double value=frozen.getFirst().get("PLANT_STATE"+state);for(var row:frozen)assertEquals(value,row.get("PLANT_STATE"+state));}
                assertTrue(samples.stream().anyMatch(r->r.get("PLANT_STATE7")<.98));
                assertTrue(samples.stream().anyMatch(r->r.get("PLANT_VAR12")>.12));
            }
            if(p.name().startsWith("frequency")){
                assertTrue(samples.stream().anyMatch(r->Math.abs(r.get("PLANT_VAR2"))>.02));
                assertTrue(samples.stream().mapToDouble(r->Math.abs(r.get("PLANT_VAR8"))).max().orElseThrow()<.001001);
                assertTrue(samples.stream().mapToDouble(r->Math.abs(r.get("PLANT_VAR8"))).max().orElseThrow()>.000999);
            }
        }
    }
}
