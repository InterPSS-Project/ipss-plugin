package org.interpss.core.dstab;

import static org.junit.jupiter.api.Assertions.*;
import java.nio.file.*;
import java.util.*;
import org.interpss.core.dstab.reference.HplntdAggregationNativeReference;
import org.interpss.dstab.renewable.*;
import org.interpss.fadapter.psse.dyr.DynamicModelCatalog;
import org.junit.jupiter.api.Test;

/** Algebraic interface play-in, not the hybrid plant or downstream lag runtime. */
public class HplntdAggregationNativeConformanceTest {
    private static final HplntdAggregation.Key A=new HplntdAggregation.Key("Bus1","1"),B=new HplntdAggregation.Key("Bus4","1");
    private static List<Map<String,Double>> rows(String text) {
        var lines=text.lines().toList();var header=lines.getFirst().split(",");var result=new ArrayList<Map<String,Double>>();
        for(String line:lines.subList(1,lines.size())) {
            var fields=line.split(",");assertEquals(header.length,fields.length);var row=new HashMap<String,Double>();
            for(int i=0;i<fields.length;i++){double v=Double.parseDouble(fields[i]);assertTrue(Double.isFinite(v));row.put(header[i],v);}result.add(row);
        }return result;
    }
    private static List<HplntdAggregation.Device> devices(HplntdAggregationNativeReference.Profile p,Map<String,Double> initial,boolean second) {
        var a=p.downstream(1);var b=p.downstream(4);
        return List.of(new HplntdAggregation.Device(A,50,a.value(ReaxdData.Parameter.KZ),a.value(ReaxdData.Parameter.KW),
                        50*initial.get("UNIT1_VAR0"),50*initial.get("UNIT1_VAR1"),true),
                new HplntdAggregation.Device(B,100,b.value(ReaxdData.Parameter.KZ),b.value(ReaxdData.Parameter.KW),
                        100*initial.get("UNIT4_VAR0"),100*initial.get("UNIT4_VAR1"),second));
    }
    private static double[] limits(String profile) {
        double[] result=new double[16];Arrays.fill(result,1e-12);result[8]=3.279e-9;result[13]=1.312e-8;
        double[] raw=switch(profile) {
            case "flat-auto-coarse","fixed-continuous-coarse" -> new double[]{1e-12,1e-12,1e-12,1e-12};
            case "commands-coarse","commands-fine" -> new double[]{1.64e-8,8.197e-9,8.197e-9,4.099e-9};
            case "trip-coarse" -> new double[]{8.197e-9,2.05e-9,4.099e-9,1.025e-9};
            case "trip-fine" -> new double[]{8.197e-9,4.099e-9,4.099e-9,2.05e-9};
            default -> throw new IllegalArgumentException("No accepted hybrid interface bounds for "+profile);
        };
        result[6]=raw[0];result[7]=raw[1];result[11]=raw[2];result[12]=raw[3];return result;
    }
    @Test void nativeEnvelopesAreExactAndDoNotCreateRuntimePresence() {
        assertEquals(7,HplntdAggregationNativeReference.profiles().size());
        for(var p:HplntdAggregationNativeReference.profiles()) {
            var d=p.plant();assertEquals(61,d.constants().length);assertEquals(3,d.remoteBus());assertEquals(10,d.shunts().size());
            assertEquals(2,d.measurementBranch().from());assertEquals("1",d.measurementBranch().circuit());
            assertEquals(p.name().contains("fixed")?200:0,d.value(HplntdData.Parameter.PLANT_MVA));
            for(int bus:new int[]{1,4}) {
                var unit=p.downstream(bus);assertEquals(2,unit.plantBus());assertEquals("HPLNTDU",unit.plantModel());
                assertArrayEquals(new int[]{37,38,39,42,43,44,45},unit.relativeVariables());assertTrue(unit.validRelativeVariables(45));
                assertEquals(bus==1?1:2,unit.value(ReaxdData.Parameter.KZ));assertEquals(bus==1?2:1,unit.value(ReaxdData.Parameter.KW));
            }
        }
        for(String name:List.of("HPLNTD","HPLNTDU","REAXDU"))assertTrue(DynamicModelCatalog.find(name).isEmpty(),"A foundation must not invent a runtime descriptor");
    }
    @Test void everyRawAggregationAndDispatchSampleIncludingPauseAndTripSides()throws Exception {
        int profiles=0;
        for(var p:HplntdAggregationNativeReference.profiles()) {
            if(p.name().equals("flat-fixed-coarse"))continue; // retained counterexample below
            boolean continuous=p.name().startsWith("fixed-continuous");
            var table=rows(p.body());assertEquals(p.samples(),table.size());assertEquals(89,table.getFirst().size());var initial=table.getFirst();
            var aggregate=new HplntdAggregation(p.plant().value(HplntdData.Parameter.PLANT_MVA),devices(p,initial,true));
            double[] max=new double[16],at=new double[16],heldUnit4=null;double largestP=0,largestQ=0;int histories=0,duplicates=0,pauses=0,tripped=0;
            Double previous=null;StringBuilder trace=new StringBuilder("native_time_s,solver_grid_s,side,paused");
            String[] columns={"PLANT_VAR6","PLANT_VAR36","PLANT_VAR37","PLANT_VAR38","PLANT_VAR41","PLANT_VAR42",
                    "UNIT1_VAR2","UNIT1_VAR3","UNIT1_VAR4","UNIT1_VAR5","UNIT1_VAR6","UNIT4_VAR2","UNIT4_VAR3","UNIT4_VAR4","UNIT4_VAR5","UNIT4_VAR6"};
            for(String name:columns)trace.append(",IPSS_").append(name).append(",NATIVE_").append(name);trace.append('\n');
            var heldInput=new HashMap<String,Double>();
            for(var row:table) {
                double time=row.get("time_s");int tick=(int)Math.round(time/p.step());boolean duplicate=previous!=null&&time==previous;previous=time;
                boolean second=!p.name().startsWith("trip")||tick<(int)Math.round(.1/p.step())||tick==(int)Math.round(.1/p.step())&&!duplicate;
                boolean pause=time>=0&&!duplicate&&(tick==(int)Math.round(.2/p.step())||!continuous&&(tick==(int)Math.round(.05/p.step())||tick==(int)Math.round(.1/p.step())));
                if(time<0)histories++;if(duplicate)duplicates++;if(pause)pauses++;if(!second)tripped++;
                var live=aggregate.refresh(devices(p,initial,second));var observed=pause?live.pausedView():live;
                double dp=row.get("PLANT_VAR43"),dq=row.get("PLANT_VAR44");largestP=Math.max(largestP,Math.abs(dp));largestQ=Math.max(largestQ,Math.abs(dq));
                var a=live.dispatch(A,dp,dq);var b=live.dispatch(B,dp,dq);assertTrue(a.enabled());assertEquals(second,b.enabled());
                assertEquals(dp*live.plantMva(),50*a.activeIncrement()+(b.enabled()?100*b.activeIncrement():0),5e-12);
                assertEquals(dq*live.plantMva(),50*a.reactiveIncrement()+(b.enabled()?100*b.reactiveIncrement():0),5e-12);
                double[] unit4;
                if(second) {
                    unit4=new double[]{b.rawActive(),b.rawReactive(),live.shares().get(B).activeWeight(),live.shares().get(B).reactiveWeight(),live.plantMva()};heldUnit4=unit4.clone();
                    for(int i=0;i<7;i++)heldInput.put("UNIT4_VAR"+i,row.get("UNIT4_VAR"+i));
                } else {
                    assertNotNull(heldUnit4);unit4=heldUnit4;
                    // The algebraic port emits no new command. Reported input
                    // VARs hold; this does not assert that unused lag states freeze.
                    for(var entry:heldInput.entrySet())assertEquals(entry.getValue(),row.get(entry.getKey()),entry.getKey()+" holds after trip");
                }
                // The native allocator initializes its calculated-base plot
                // slot to the configured base. The actual device sum remains
                // separately observable through the aggregation snapshot.
                double reportedBase=pause?0:continuous?live.plantMva():live.calculatedMva();
                double[] actual={observed.plantMva(),reportedBase,observed.weightedKz(),observed.weightedKw(),observed.initialActive(),observed.initialReactive(),
                        a.rawActive(),a.rawReactive(),live.shares().get(A).activeWeight(),live.shares().get(A).reactiveWeight(),live.plantMva(),
                        unit4[0],unit4[1],unit4[2],unit4[3],unit4[4]};
                trace.append(time).append(',').append(tick*p.step()).append(',').append(time<0?-1:duplicate?1:0).append(',').append(pause?1:0);
                for(int i=0;i<16;i++) {
                    double expected=row.get(columns[i]),error=Math.abs(actual[i]-expected);if(error>max[i]){max[i]=error;at[i]=time;}
                    trace.append(',').append(actual[i]).append(',').append(expected);
                }trace.append('\n');
            }
            Path out=Path.of("target/hplntd-foundation-comparisons",p.name()+".csv");Files.createDirectories(out.getParent());Files.writeString(out,trace.toString());
            System.out.println("Hybrid aggregation "+p.name()+" maxima="+Arrays.toString(max)+" times="+Arrays.toString(at)+" command maxima="+largestP+","+largestQ+" tripped rows="+tripped);
            assertEquals(2,histories);assertEquals(continuous?0:2,duplicates);assertEquals(continuous?1:3,pauses);
            if(p.name().startsWith("commands")||p.name().startsWith("trip")){assertTrue(largestP>.02);assertTrue(largestQ>.01);}
            else {assertEquals(0,largestP);assertEquals(0,largestQ);}
            assertEquals(p.name().startsWith("trip"),tripped>0);
            for(int i=0;i<16;i++)assertTrue(max[i]<limits(p.name())[i],p.name()+" "+columns[i]+" error="+max[i]);
            profiles++;
        }
        assertEquals(6,profiles);
    }
    @Test void explicitBasePauseCounterexampleIsNotFittedIntoTheAggregation()throws Exception {
        var p=HplntdAggregationNativeReference.profiles().stream().filter(x->x.name().equals("flat-fixed-coarse")).findFirst().orElseThrow();
        var table=rows(p.body());var initial=table.getFirst();var aggregate=new HplntdAggregation(200,devices(p,initial,true));
        boolean resumed=false;Double previous=null;double largest=0;int shifted=0;
        StringBuilder trace=new StringBuilder("native_time_s,side,IPSS_PLANT_MVA,NATIVE_PLANT_MVA,IPSS_DEVICE_MVA_SUM,NATIVE_CALC_SLOT,DELTA_P,DELTA_Q\n");
        for(var row:table) {
            double time=row.get("time_s");int tick=(int)Math.round(time/p.step());boolean duplicate=previous!=null&&time==previous;previous=time;
            if(tick==(int)Math.round(.05/p.step())&&duplicate)resumed=true;
            var own=aggregate.refresh(devices(p,initial,true));assertEquals(200,own.plantMva());assertEquals(150,own.calculatedMva());
            assertEquals(resumed?150:200,row.get("PLANT_VAR6"));
            assertEquals(initial.get("PLANT_VAR41"),row.get("PLANT_VAR41"));assertEquals(initial.get("PLANT_VAR42"),row.get("PLANT_VAR42"));
            assertEquals(resumed?150:200,row.get("UNIT1_VAR6"));if(resumed)shifted++;
            largest=Math.max(largest,Math.abs(row.get("PLANT_VAR43")));
            trace.append(time).append(',').append(time<0?-1:duplicate?1:0).append(',').append(own.plantMva()).append(',').append(row.get("PLANT_VAR6"))
                    .append(',').append(own.calculatedMva()).append(',').append(row.get("PLANT_VAR36")).append(',').append(row.get("PLANT_VAR43")).append(',').append(row.get("PLANT_VAR44")).append('\n');
        }
        assertTrue(shifted>0);assertTrue(largest>.04,"The base reset causes a no-event command drift");
        var control=HplntdAggregationNativeReference.profiles().stream().filter(x->x.name().equals("fixed-continuous-coarse")).findFirst().orElseThrow();
        assertEquals(p.input(),control.input(),"Only pause scheduling changes; no model data is tuned");
        for(var row:rows(control.body())) {assertEquals(200,row.get("PLANT_VAR6"));assertEquals(0,row.get("PLANT_VAR43"));assertEquals(0,row.get("PLANT_VAR44"));}
        Path out=Path.of("target/hplntd-foundation-comparisons/flat-fixed-coarse-diagnostic.csv");Files.createDirectories(out.getParent());Files.writeString(out,trace.toString());
    }
}
