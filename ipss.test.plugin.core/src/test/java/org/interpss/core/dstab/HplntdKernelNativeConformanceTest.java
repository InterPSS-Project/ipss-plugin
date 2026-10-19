package org.interpss.core.dstab;

import java.nio.file.*;
import java.util.*;
import org.apache.commons.math3.complex.Complex;
import org.junit.jupiter.api.Test;
import org.interpss.dstab.renewable.*;
import org.interpss.core.dstab.reference.HplntdAggregationNativeReference;
import org.interpss.core.dstab.reference.HplntdAggregationNativeReference.Profile;
import static org.junit.jupiter.api.Assertions.*;

/** Matched staggered-method plant-base replay, not network attachment acceptance.
 * The default kernel modified-Euler path remains separate and unchanged. */
public class HplntdKernelNativeConformanceTest {
    static List<Profile> reviewedProfiles(){return List.copyOf(profiles());}
    private static List<Profile> profiles(){var result=new ArrayList<Profile>();for(var p:HplntdAggregationNativeReference.profiles())if(p.name().startsWith("commands")||p.name().equals("flat-auto-coarse")||p.name().equals("fixed-continuous-coarse"))result.add(p);result.addAll(List.of(
        new Profile("plant-low-window-coarse",.0005,405,"4c1413ee008f5723c9dfc367ef1b5c911694523f8dc3a3ae008d4a5b832d0093","1a6bd67dbd0c25557697d469a0e7a738536f0a88d68034592eb3bae16063d77c"),
        new Profile("plant-low-window-fine",.00025,805,"15426437f89a366d1ece11a9f124b8ad770f0395918275377d01e4e82bb4abaa","1a6bd67dbd0c25557697d469a0e7a738536f0a88d68034592eb3bae16063d77c"),
        new Profile("plant-high-window-coarse",.0005,405,"060c6b4425983cd77f27e55e049ce536d54800db6c8715ade22f16f3c2d693d5","aa50536438e618b0c11a57e622e211105acbf29d080b9da900ec3d7e099bfeb0"),
        new Profile("plant-high-window-fine",.00025,805,"0489be8fde1107d64697a12af760fa4a7314aaca63b1c3ea514a9afc2e4869c4","aa50536438e618b0c11a57e622e211105acbf29d080b9da900ec3d7e099bfeb0"),
        new Profile("plant-reference-full-coarse",.0005,405,"1c9a6a78adbf40e99731fce140c6e094f04b497bb5fb1bc2615087901c2359a1","9539112d700b5cf2d8f929bf345471bc5737dc2ba1474310979c445ff5dfea2e"),
        new Profile("plant-reference-full-fine",.00025,805,"1908ae87c2b8ba6bae19acaab92875822a4a2983c4b22d7a2634d845ebc9db37","9539112d700b5cf2d8f929bf345471bc5737dc2ba1474310979c445ff5dfea2e")
    ));return result;}
    private static double[] bounds(String name){return switch(name){
        case "flat-auto-coarse" -> new double[]{1e-12,2.5691e-07,2.5594e-07,4.4504e-07,4.677e-08,4.7702e-08,8.1312e-08,1e-12,1e-12,4.9642e-12,4.677e-08,2.5691e-07,4.4368e-07,8.1312e-08,4.4368e-07,8.1313e-08,4.4368e-07};
        case "commands-coarse" -> new double[]{1e-12,3.3523e-07,1.0319e-07,1.9899e-07,2.0383e-06,4.7062e-08,1.9623e-07,4.3201e-07,1e-12,1.6703e-05,4.677e-08,2.5691e-07,1.9941e-07,1.9623e-07,1.9941e-07,1.9623e-07,1.9941e-07};
        case "commands-fine" -> new double[]{1e-12,2.5691e-07,1.9061e-07,2.3013e-07,5.2351e-07,4.7163e-08,2.1918e-07,1.5812e-06,1e-12,1.6689e-05,4.677e-08,2.5691e-07,2.3689e-07,2.1918e-07,2.3689e-07,2.1918e-07,2.3689e-07};
        case "fixed-continuous-coarse" -> new double[]{1e-12,1.9473e-07,1.94e-07,3.3733e-07,4.3273e-08,4.4135e-08,7.5233e-08,1e-12,1e-12,4.9642e-12,4.3273e-08,1.9473e-07,3.363e-07,7.5233e-08,3.363e-07,7.5233e-08,3.363e-07};
        case "plant-low-window-coarse" -> new double[]{1e-12,6.8472e-05,1.9156e-06,4.4601e-06,1.531e-05,5.5355e-08,7.0987e-07,2.2715e-05,1e-12,1.5849e-10,4.677e-08,2.5691e-07,4.4703e-06,7.0987e-07,4.4703e-06,7.0987e-07,4.4703e-06};
        case "plant-low-window-fine" -> new double[]{1e-12,1.7184e-05,4.8568e-07,1.3083e-06,3.2124e-06,4.8822e-08,1.4422e-07,5.5975e-06,1e-12,2.1904e-10,4.677e-08,2.5691e-07,1.3123e-06,1.4422e-07,1.3123e-06,1.4422e-07,1.3123e-06};
        case "plant-high-window-coarse" -> new double[]{1e-12,2.5691e-07,1e-12,1e-12,4.677e-08,4.677e-08,1e-12,1e-12,1e-12,1e-12,4.677e-08,2.5691e-07,1e-12,1e-12,1e-12,1e-12,1e-12};
        case "plant-high-window-fine" -> new double[]{1e-12,2.5691e-07,1e-12,1e-12,4.677e-08,4.677e-08,1e-12,1e-12,1e-12,1e-12,4.677e-08,2.5691e-07,1e-12,1e-12,1e-12,1e-12,1e-12};
        case "plant-reference-full-coarse" -> new double[]{1e-12,2.5691e-07,8.7753e-08,5.9082e-07,1.7093e-06,5.2519e-08,1.7751e-06,4.6641e-07,1e-12,9.8718e-09,4.677e-08,2.5691e-07,6.0079e-07,1.7751e-06,6.0079e-07,1.7751e-06,6.0079e-07};
        case "plant-reference-full-fine" -> new double[]{1e-12,2.5691e-07,1.9061e-07,2.3013e-07,5.2351e-07,4.7163e-08,2.1918e-07,8.8506e-07,1e-12,6.5276e-11,4.677e-08,2.5691e-07,2.3689e-07,2.1918e-07,2.3689e-07,2.1918e-07,2.3689e-07};
        default -> throw new IllegalArgumentException("Unknown plant reference profile "+name);
    };}
    private record Row(double time,Map<String,Double> values){double v(String name){return values.get(name);}Complex voltage(int bus){return new Complex(v("V"+bus)*Math.cos(Math.toRadians(v("A"+bus+"_DEG"))),v("V"+bus)*Math.sin(Math.toRadians(v("A"+bus+"_DEG"))));}}
    private static List<Row> rows(Profile p){String[] lines=p.body().strip().split("\\R"),header=lines[0].split(",");var rows=new ArrayList<Row>();for(int i=1;i<lines.length;i++){String[] values=lines[i].split(",");var map=new HashMap<String,Double>();for(int j=0;j<header.length;j++)map.put(header[j],Double.parseDouble(values[j]));rows.add(new Row(map.get("time_s"),map));}return rows;}
    private static HplntdKernel.Input input(Row r,HplntdData data){double base=r.v("PLANT_VAR6");var power=new Complex(r.v("PLANT_VAR4")/base,r.v("PLANT_VAR5")/base);return new HplntdKernel.Input(r.voltage(data.remoteBus()),power.divide(r.voltage(2)).conjugate(),power.getReal(),power.getImaginary(),r.values().getOrDefault("FREQ2",0.),r.v("V"+data.frequencyBus()));}
    private static HplntdKernel.References references(Row r){return new HplntdKernel.References(r.v("PLANT_VAR0"),r.v("PLANT_VAR1"),r.v("PLANT_VAR2"),r.v("PLANT_VAR3"),r.v("PLANT_VAR9"),r.v("PLANT_VAR10"),r.v("PLANT_VAR11"));}
    @Test void nativeReferenceSlewHasIndependentScalarStoreDiscriminator(){
        for(var p:profiles())for(int axis=0;axis<2;axis++){
            var samples=rows(p);var data=p.plant();int reference=axis==0?3:1,state=axis==0?10:11;
            double y=samples.get(0).v("PLANT_VAR"+reference),history=0,maximum=0;
            var previous=samples.get(0);boolean event=false;
            for(var row:samples){
                if(previous.time()>=0&&row.time()-previous.time()>p.step()*.5){
                    double rate=(previous.v("PLANT_VAR"+reference)-y)/data.referenceSlewTime(p.step());
                    double lo=data.value(axis==0?HplntdData.Parameter.DPRMIN:HplntdData.Parameter.DQREFMIN),hi=data.value(axis==0?HplntdData.Parameter.DPRMAX:HplntdData.Parameter.DQREFMAX);
                    rate=Math.max(lo,Math.min(hi,rate));if(event)rate=.5*(rate+history);
                    y+=p.step()*(1.5*rate-.5*history);history=rate;event=false;
                }else if(row.time()>=0&&row.time()==previous.time()&&row.v("PLANT_VAR"+reference)!=previous.v("PLANT_VAR"+reference))event=true;
                maximum=Math.max(maximum,Math.abs(y-row.v("PLANT_STATE"+state)));previous=row;
            }
            assertTrue(maximum<1e-7,p.name()+" scalar reference state "+state+"="+maximum);
        }
    }
    @Test void compareEveryPlantStateAndIncrement()throws Exception {
        compareProfiles(profiles(),p->bounds(p.name()));
    }
    static void compareProfiles(List<Profile> profiles,java.util.function.Function<Profile,double[]> bounds)throws Exception {
        var failures=new ArrayList<String>();
        for(var p:profiles) {
            var data=p.plant();var rows=rows(p);var first=rows.get(0);var in=input(first,data);double base=first.v("PLANT_VAR6");
            boolean measuredFrequency=first.values().containsKey("FREQ2");
            boolean sawFreeze=false,sawRecovery=false,sawRelease=false,sawFrequencyRateLimit=false;
            // Historical profiles do not expose frequency. They remain
            // comparisons of the other sixteen quantities only, and their
            // recorded deadband must prove that omitted signal is inactive.
            if(!measuredFrequency)for(var row:rows)assertEquals(0,row.v("PLANT_VAR8"),1e-12,"Unmeasured frequency must not drive the active control loop");
            double initialP=(50*first.v("UNIT1_VAR0")+100*first.v("UNIT4_VAR0"))/base,initialQ=(50*first.v("UNIT1_VAR1")+100*first.v("UNIT4_VAR1"))/base;
            var k=new HplntdKernel(data);k.initialize(new HplntdInitialization.Measurements(in.regulatedVoltage(),in.branchCurrent(),in.active(),in.reactive(),initialP,initialQ));
            double[] maximum=new double[17],at=new double[17],history=new double[13],rates=new double[13],values=new double[13],trial=new double[13];Row previous=first;
            boolean discontinuity=false;
            var trace=new StringBuilder("time_s");for(int i=0;i<17;i++)trace.append(",native_").append(i).append(",interpss_").append(i);trace.append('\n');
            for(var row:rows){
                if(previous.time()>=0&&row.time()-previous.time()>p.step()*.5){
                    k.setReferences(references(previous));k.setStaggeredInput(input(previous,data));k.evaluateStaggered(p.step(),rates,0);k.readStaggeredState(values,0);
                    // Exactly the shared opt-in solver's STORE/Adams-Bashforth
                    // rule and first-post-event half-old/half-new derivative.
                    for(int i=0;i<13;i++){if(discontinuity)rates[i]=.5*(rates[i]+history[i]);trial[i]=values[i]+p.step()*(1.5*rates[i]-.5*history[i]);}
                    k.applyStaggeredState(trial,0,p.step());k.readStaggeredState(values,0);
                    for(int i=0;i<13;i++)history[i]=values[i]!=trial[i]?0:rates[i];
                    k.completeStaggeredStep(p.step(),row.time());discontinuity=false;
                }else if(row.time()>=0&&row.time()==previous.time()){
                    if(!references(row).equals(references(previous))||row.voltage(data.remoteBus()).subtract(previous.voltage(data.remoteBus())).abs()>1e-5)discontinuity=true;
                }
                k.setReferences(references(row));k.setStaggeredInput(input(row,data));k.evaluateStaggered(p.step(),new double[13],0);
                var state=k.states();var out=k.output(input(row,data));double[] actual=Arrays.copyOf(state,17);actual[13]=out.activeCommand();actual[14]=out.reactiveCommand();actual[15]=out.active();actual[16]=out.reactive();
                if(p.name().contains("window")&&row.time()>0){
                    double timer=row.v("PLANT_VAR12");assertEquals(timer!=0,out.frozen(),p.name()+" freeze at "+row.time());
                    sawFreeze|=timer<0;sawRecovery|=timer>0;sawRelease|=row.time()>.15&&timer==0;
                    if(timer>0)assertEquals(timer,out.recoveryStart(),1e-10,"Recovery uses accepted simulation time");
                    double frequency=row.v("V2")<data.value(HplntdData.Parameter.VFRQ)?0:row.v("FREQ2");
                    sawFrequencyRateLimit|=Math.abs((frequency-row.v("PLANT_STATE9"))/data.value(HplntdData.Parameter.TFRQ))>2*data.value(HplntdData.Parameter.FRMAX);
                }
                trace.append(row.time());
                for(int i=0;i<17;i++){double expected=row.v(i<13?"PLANT_STATE"+i:"PLANT_VAR"+new int[]{39,40,43,44}[i-13]);double error=Math.abs(actual[i]-expected);if(error>maximum[i]){maximum[i]=error;at[i]=row.time();}trace.append(',').append(expected).append(',').append(actual[i]);}
                trace.append('\n');previous=row;
            }
            System.out.println("HPLNTDU "+p.name()+" errors="+Arrays.toString(maximum)+" times="+Arrays.toString(at));
            var output=Path.of("target/hplntd-kernel-comparisons",p.name()+".csv");Files.createDirectories(output.getParent());Files.writeString(output,trace);
            if(p.name().contains("window"))assertTrue(sawFreeze,"Voltage freeze must activate");
            if(p.name().contains("low-window")){assertTrue(sawRecovery&&sawRelease,"Low-voltage freeze must recover and release");assertTrue(sawFrequencyRateLimit,"Frequency derivative limits must activate");}
            var limits=bounds.apply(p);
            for(int i=0;i<17;i++)if((i!=9||measuredFrequency)&&maximum[i]>limits[i])failures.add(p.name()+" channel "+i+"="+maximum[i]+" limit="+limits[i]);
        }
        assertTrue(failures.isEmpty(),String.join("; ",failures));
    }
}
