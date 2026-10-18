package org.interpss.core.dstab;

import static org.junit.jupiter.api.Assertions.*;
import java.nio.file.*;
import java.util.*;
import org.interpss.dstab.renewable.*;
import org.interpss.core.dstab.reference.Repcgfmc1KernelNativeReference;
import org.junit.jupiter.api.Test;

/** Recorded physical boundary/manual references only; no native state injection. */
public class Repcgfmc1KernelNativeConformanceTest {
    private static List<Map<String,Double>> rows(String csv){
        var lines=csv.lines().toList();String[] names=lines.getFirst().split(",");var rows=new ArrayList<Map<String,Double>>();
        for(String line:lines.subList(1,lines.size())){String[] values=line.split(",");assertEquals(names.length,values.length);Map<String,Double> row=new HashMap<>();
            for(int i=0;i<names.length;i++){double v=Double.parseDouble(values[i]);assertTrue(Double.isFinite(v));row.put(names[i],v);}rows.add(row);}return rows;
    }
    private static Repcgfmc1Kernel.Input input(Map<String,Double> row){
        return new Repcgfmc1Kernel.Input(row.get("V1"),row.get("V2"),row.get("V3"),1+row.get("FREQ3_DEV"),row.get("PLANT_VAR12")/100,row.get("PLANT_VAR13")/100);
    }
    private static void references(Repcgfmc1Kernel m,Repcgfmc1Data data,Map<String,Double> row){
        var r=m.references();m.setReferences(new Repcgfmc1Kernel.References(row.get("PLANT_VAR0"),row.get("PLANT_VAR6"),
                data.voltageFlag()==0?row.get("PLANT_VAR7"):r.reactiveBranch(),row.get("PLANT_VAR14"),row.get("PLANT_VAR16"),row.get("PLANT_VAR17")));
    }
    @Test void replayAllFifteenStatesFromIndependentPhysicalInputs() throws Exception {
        var required=Set.of("reference-coarse","reference-fine","limits-coarse","limits-fine","voltage-coarse","voltage-fine",
                "ffr-coarse","ffr-fine","voltage-limits-coarse","voltage-limits-fine");
        assertEquals(10,Repcgfmc1KernelNativeReference.profiles().size());
        assertEquals(required,Repcgfmc1KernelNativeReference.profiles().stream().map(p->p.name()).collect(java.util.stream.Collectors.toSet()));
        Map<String,double[]> coarseByMode=new HashMap<>();
        for(var profile:Repcgfmc1KernelNativeReference.profiles()){
            var all=rows(profile.body());assertEquals(profile.samples(),all.size());assertEquals(85,all.getFirst().size());
            assertEquals(2,all.stream().filter(r->r.get("time_s")<0).count());var samples=all.stream().filter(r->r.get("time_s")>=0).toList();
            var initial=samples.getFirst();var data=profile.parameters();var m=new Repcgfmc1Kernel(data);
            m.initialize(new Repcgfmc1Initialization.Measurements(100,50,initial.get("V1"),initial.get("V2"),initial.get("P_PU"),initial.get("Q_PU"),
                    initial.get("PLANT_VAR12")/100,initial.get("PLANT_VAR13")/100,true),profile.step());
            double[] max=new double[15],at=new double[15],outputs=new double[4],outputAt=new double[4];int duplicates=0,timerBoundaryDifferences=0;
            StringBuilder trace=new StringBuilder("time_s");for(int j=0;j<15;j++)trace.append(",IPSS_STATE").append(j);for(int j=0;j<15;j++)trace.append(",NATIVE_STATE").append(j);
            trace.append(",IPSS_TARGET_P,IPSS_TARGET_Q,NATIVE_TARGET_P,NATIVE_TARGET_Q,IPSS_FFR_START,NATIVE_FFR_START\n");
            for(int i=0;i<samples.size();i++){
                var row=samples.get(i);boolean duplicate=i>0&&row.get("time_s").equals(samples.get(i-1).get("time_s"));
                if(duplicate&&profile.name().startsWith("ffr")){
                    double time=row.get("time_s");
                    if(Math.abs(time-.05)<1e-5)m.setFastFrequencyThresholds(1.001,1.05);
                    if(Math.abs(time-.06)<1e-5)m.setFastFrequencyThresholds(.98,1.05);
                    if(Math.abs(time-.15)<1e-5)m.setFastFrequencyThresholds(.98,.999);
                    if(Math.abs(time-.25)<1e-5)m.setFastFrequencyThresholds(.98,1.05);
                }
                if(i==0||duplicate){references(m,data,row);m.step(0,input(row));if(duplicate)duplicates++;}
                else {references(m,data,samples.get(i-1));m.step(profile.step(),input(samples.get(i-1)),0);references(m,data,row);m.step(profile.step(),input(row),1);}
                double[] state=m.states();for(int j=0;j<15;j++){double error=Math.abs(state[j]-row.get("PLANT_STATE"+j));if(error>max[j]){max[j]=error;at[j]=row.get("time_s");}}
                var out=m.output(input(row));double[] actual={out.activeTarget(),out.reactiveTarget(),out.ffrPower(),out.ffrStartTime()};String[] channels={"PLANT_VAR1","PLANT_VAR2","PLANT_VAR15","PLANT_VAR19"};
                for(int j=0;j<4;j++){double error=Math.abs(actual[j]-row.get(channels[j]));if(error>outputs[j]){outputs[j]=error;outputAt[j]=row.get("time_s");}}
                boolean nativeActive=row.get("PLANT_VAR19")!=0,modelActive=out.ffrStartTime()!=0;
                if(nativeActive&&modelActive)assertEquals(row.get("PLANT_VAR19"),out.ffrStartTime(),1e-5,"Absolute activation clock, not elapsed time");
                else if(nativeActive!=modelActive){
                    timerBoundaryDifferences++;
                    double rampWindow=2*data.value(Repcgfmc1Data.Parameter.FFRR)*profile.step();
                    assertTrue(Math.abs(out.ffrPower())<rampWindow&&Math.abs(row.get("PLANT_STATE14"))<rampWindow,"Timer sentinel mismatch must be confined to the ramp-to-zero boundary");
                }
                trace.append(row.get("time_s"));for(double v:state)trace.append(',').append(v);for(int j=0;j<15;j++)trace.append(',').append(row.get("PLANT_STATE"+j));
                trace.append(',').append(out.activeTarget()).append(',').append(out.reactiveTarget()).append(',').append(row.get("PLANT_VAR1")).append(',').append(row.get("PLANT_VAR2"))
                        .append(',').append(out.ffrStartTime()).append(',').append(row.get("PLANT_VAR19")).append('\n');
            }
            assertEquals(5,duplicates);assertEquals(.3,m.time(),1e-12);
            System.out.println("REPCGFMC1U state replay "+profile.name()+" errors="+Arrays.toString(max)+" times="+Arrays.toString(at));
            System.out.println("REPCGFMC1U output replay "+profile.name()+" errors="+Arrays.toString(outputs)+" times="+Arrays.toString(outputAt));
            Path directory=Path.of("target","repcgfmc-kernel-replay");Files.createDirectories(directory);Files.writeString(directory.resolve(profile.name()+".csv"),trace.toString());
            assertTrue(timerBoundaryDifferences<=2,"Preserve and bound every timer boundary sample, not a broad timestamp tolerance");
            double[] stateLimits=stateBounds(profile.name()),outputLimits=outputBounds(profile.name());
            for(int j=0;j<15;j++)assertTrue(max[j]<stateLimits[j],profile.name()+" STATE"+j+"="+max[j]+" at "+at[j]);
            for(int j=0;j<3;j++)assertTrue(outputs[j]<outputLimits[j],profile.name()+" output"+j+"="+outputs[j]+" at "+outputAt[j]);
            String mode=profile.name().replace("-coarse","").replace("-fine","");
            if(profile.step()==.0005)coarseByMode.put(mode,max);
            else {
                int[] states=mode.startsWith("voltage")?new int[]{8,9,13}:mode.equals("ffr")?new int[]{11,13,14}:new int[]{11,13};
                for(int state:states)assertTrue(max[state]<.7*coarseByMode.get(mode)[state],"Step-halving must reduce truncation-dominated STATE"+state+" in "+mode);
            }
        }
    }
    private static double[] stateBounds(String name){return switch(name){
        case "reference-coarse" -> new double[]{2.511e-06,1.296e-06,1e-12,1.886e-06,1.015e-06,1.196e-06,3.285e-05,1.736e-05,1e-12,1e-12,2.251e-06,0.0004403,1.249e-06,0.000194,1e-12};
        case "reference-fine" -> new double[]{5.492e-06,1.296e-06,1e-12,7.802e-07,1.865e-06,3.266e-06,8.444e-06,4.256e-06,1e-12,1e-12,1.72e-06,0.0002851,6.247e-07,8.636e-05,1e-12};
        case "limits-coarse" -> new double[]{2.475e-06,1.287e-06,1e-12,2.243e-06,1.351e-06,1.105e-06,3.264e-05,2.02e-05,1e-12,1e-12,2.698e-06,0.0004382,1.782e-06,0.0002327,1e-12};
        case "limits-fine" -> new double[]{5.456e-06,1.287e-06,1e-12,5.498e-07,2.354e-06,4.604e-06,8.412e-06,4.937e-06,1e-12,1e-12,1.349e-06,0.0002834,8.908e-07,0.0001145,1e-12};
        case "voltage-coarse" -> new double[]{4.791e-08,1.459e-08,5.312e-07,1e-12,1e-12,9.715e-07,1.993e-07,5.388e-06,0.0001874,0.0008902,6.263e-09,2.184e-07,1.062e-07,2.124e-05,1e-12};
        case "voltage-fine" -> new double[]{4.791e-08,1.458e-08,8.864e-07,1e-12,1e-12,2e-06,2.09e-07,1.346e-06,9.341e-05,0.0004569,6.575e-09,5.636e-07,3.09e-08,5.289e-06,1e-12};
        case "ffr-coarse" -> new double[]{2.268e-06,5.415e-07,7.587e-07,1e-12,1e-12,2.479e-06,7.491e-06,1.291e-06,1.854e-07,1.517e-06,2.183e-06,0.0002076,1.899e-08,3.245e-05,0.0001875};
        case "ffr-fine" -> new double[]{2.27e-06,5.434e-07,2.2e-06,1e-12,1e-12,1.306e-05,1.945e-06,3.465e-07,5.01e-07,4.4e-06,4.764e-07,4.662e-05,2.96e-08,8.877e-06,9.375e-05};
        case "voltage-limits-coarse" -> new double[]{8.499e-08,1.219e-08,8.889e-07,1e-12,1e-12,1.626e-06,2.229e-07,1.017e-05,0.0009906,0.0008902,6.398e-09,1.398e-07,2.42e-07,5.22e-05,1e-12};
        case "voltage-limits-fine" -> new double[]{8.581e-08,1.224e-08,1.084e-06,1e-12,1e-12,1.213e-06,2.003e-07,2.504e-06,0.0004952,0.0004569,6.681e-09,1.674e-07,1.814e-07,1.318e-05,1e-12};
        default -> throw new IllegalArgumentException("Missing native profile bounds: "+name);
    };}
    private static double[] outputBounds(String name){return switch(name){
        case "reference-coarse" -> new double[]{5.023e-05,9.314e-09,1e-12};
        case "reference-fine" -> new double[]{0.0001098,9.314e-09,1e-12};
        case "limits-coarse" -> new double[]{4.952e-05,9.314e-09,1e-12};
        case "limits-fine" -> new double[]{0.0001091,9.314e-09,1e-12};
        case "voltage-coarse" -> new double[]{1e-12,0.0005391,1e-12};
        case "voltage-fine" -> new double[]{1e-12,0.0002754,1e-12};
        case "ffr-coarse" -> new double[]{0.0001875,1.404e-06,0.0001875};
        case "ffr-fine" -> new double[]{9.376e-05,4.463e-06,9.375e-05};
        case "voltage-limits-coarse" -> new double[]{1e-12,0.001383,1e-12};
        case "voltage-limits-fine" -> new double[]{1e-12,0.0006973,1e-12};
        default -> throw new IllegalArgumentException("Missing native profile bounds: "+name);
    };}
    @Test void nativeDisabledVoltagePathsRetainUnusedRemoteStorage(){
        for(var p:Repcgfmc1KernelNativeReference.profiles())if(p.parameters().voltageFlag()==0&&p.parameters().voltageReferenceFlag()==0){
            var samples=rows(p.body());double initial=samples.getFirst().get("PLANT_STATE2"),remote=samples.getFirst().get("V2");
            for(var r:samples)assertEquals(initial,r.get("PLANT_STATE2"));
            assertTrue(samples.stream().anyMatch(r->Math.abs(r.get("V2")-remote)>.001));
        }
    }
    @Test void nativeFineStepFrequencyFilterHasAnAbsoluteFloatLockingFloor(){
        for(var p:Repcgfmc1KernelNativeReference.profiles())if(p.name().equals("reference-fine")||p.name().equals("limits-fine")){
            int locked=0;
            for(var r:rows(p.body())){
                double increment=p.step()*r.get("FREQ3_DEV")/.02;
                if(r.get("PLANT_STATE0")==1&&r.get("FREQ3_DEV")>4e-6&&Math.abs(increment)<.5*Math.ulp(1.0f)){
                    assertEquals(1,(float)(1+increment));assertEquals(1,r.get("PLANT_VAR18"));locked++;
                }
            }assertTrue(locked>20,"Nonzero physical frequency input cannot move the native absolute float state");
        }
    }
    @Test void nativePowerAndVoltageIntegralsReachBothBounds(){
        for(var p:Repcgfmc1KernelNativeReference.profiles()){
            int[] indices=p.name().startsWith("limits-")?new int[]{10,12}:p.name().startsWith("voltage-limits-")?new int[]{8}:new int[0];
            for(int index:indices){var samples=rows(p.body());double bound=index==8?.25:.0001;
                assertTrue(samples.stream().filter(r->Math.abs(r.get("PLANT_STATE"+index)-bound)<4e-12).count()>2);
                assertTrue(samples.stream().filter(r->Math.abs(r.get("PLANT_STATE"+index)+bound)<4e-12).count()>2);
            }
        }
    }
    @Test void nativeFfrHoldOppositeEventAndStoredDerivativeHistoryAreExplicit(){
        for(var p:Repcgfmc1KernelNativeReference.profiles())if(p.name().startsWith("ffr-")){
            var samples=rows(p.body());boolean ramp=false,oppositeHeld=false,rearmed=false;
            for(int i=1;i<samples.size();i++){
                var r=samples.get(i);var previous=samples.get(i-1);double time=r.get("time_s"),power=r.get("PLANT_STATE14");
                assertEquals(power,r.get("PLANT_VAR15"));
                if(time>.15&&time<.18){assertTrue(power>0);assertEquals(.05,r.get("PLANT_VAR19"),1e-5);oppositeHeld=true;}
                if(time>.20&&time<.22){assertTrue(power<0);assertTrue(r.get("PLANT_VAR19")>.189&&r.get("PLANT_VAR19")<.192);rearmed=true;}
                if(!ramp&&previous.get("PLANT_STATE14")>.0299&&power<previous.get("PLANT_STATE14")){
                    assertEquals(-1.5,(power-previous.get("PLANT_STATE14"))/(.3*p.step()),2e-5);ramp=true;
                }
            }assertTrue(ramp&&oppositeHeld&&rearmed);assertEquals(0,samples.getLast().get("PLANT_STATE14"));assertEquals(0,samples.getLast().get("PLANT_VAR19"));
        }
    }
}
