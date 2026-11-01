package org.interpss.dstab.control.gov.wecc.wsieg1;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.zip.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.interpss.IpssCorePlugin;
import org.interpss.core.dstab.reference.BenchmarkOutputGuard;
import org.interpss.fadapter.psse.PSSEMultiFileLoader;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/** Independent complete-state, boundary and shaft-event evidence retains inactive diagnostics explicitly. */
public class Wsieg1StateConformanceTest {
    @BeforeAll static void setup(){IpssCorePlugin.init();IpssCorePlugin.configureSparseSolver("java-klu");}
    @Test void coldPairsAndPublishedCompensationRecurrencesAreIndependentlyReproducible(@TempDir Path staged)throws Exception {
        var fixture=new Wsieg1ReferenceFixture(staged);var pairs=new LinkedHashMap<String,Map<String,String>>();
        try(var zip=new ZipFile(fixture.archive().toFile())) {
            for(var entry:fixture.matrix()) {
                String tag=entry.path("tag").asText();Path root=fixture.stage(tag);
                var manifest=new ObjectMapper().readTree(root.resolve("manifest.json").toFile());
                assertTrue(manifest.path("diagnostic_storage_initialization").asText().startsWith("All VAR slots zeroed"));
                byte[] body=zip.getInputStream(zip.getEntry(tag+"/native.csv")).readAllBytes();
                pairs.computeIfAbsent(tag.substring(0,tag.lastIndexOf('-')),key->new HashMap<>()).put(entry.path("repeat").asText(),Wsieg1ReferenceFixture.sha(body));
                if(!entry.path("repeat").asText().equals("a"))continue;
                double h=entry.path("step_s").asDouble();var c=manifest.path("initialized_constants");var rows=rows(body);
                var grid=new TreeMap<Long,Map<String,Double>>();for(var row:rows)if(row.get("time_s")>=0)grid.put(Math.round(row.get("time_s")/h),row);
                double maximum=0.,wrongCoordinate=0.;int checked=0;
                for(var point:grid.entrySet()) {
                    long tick=point.getKey();if(tick<2||!grid.containsKey(tick+1))continue;
                    if(entry.path("event").asText().equals("primary-trip")&&tick*h>=.05-1e-10)continue;
                    if(Math.abs(tick*h-.05)<h*1.1||Math.abs(tick*h-.1)<h*1.1)continue;
                    var row=point.getValue();var previous=grid.get(tick-1);double gain=c.get(0).asDouble(),t1=c.get(1).asDouble(),t2=c.get(2).asDouble();
                    double alpha=1-t2/t1;
                    double rate=(alpha*gain*row.get("VAR3")-row.get("STATE0"))/t1;
                    double prior=(alpha*gain*previous.get("VAR3")-previous.get("STATE0"))/t1;
                    double expected=row.get("STATE0")+h*(1.5*rate-.5*prior);
                    maximum=Math.max(maximum,Math.abs(expected-grid.get(tick+1).get("STATE0")));
                    double wrong=row.get("STATE0")+h*(1.5*(gain*row.get("VAR3")-row.get("STATE0"))/t1
                            -.5*(gain*previous.get("VAR3")-previous.get("STATE0"))/t1);
                    wrongCoordinate=Math.max(wrongCoordinate,Math.abs(wrong-grid.get(tick+1).get("STATE0")));checked++;
                }
                assertTrue(checked>5,tag+" active regular-interval coverage");assertTrue(maximum<2e-8,tag+" compensation recurrence "+maximum);
                if(entry.path("event").asText().equals("fault"))assertTrue(wrongCoordinate>1e-7,tag+" nontrivial coordinate discriminator");
                assertTrue(rows.stream().allMatch(row->row.get("VAR2")==0.),"Retain the unused allocated deadband-input slot separately");
                if(entry.path("event").asText().equals("primary-trip")) {
                    var off=rows.stream().filter(row->row.get("time_s")>.05+h).toList();
                    assertTrue(off.getLast().get("STATE1")-off.getFirst().get("STATE1")>.05,"Allocated off-model states continue with stale rates");
                    assertEquals(0.,off.getLast().get("PMECH"),0.);
                    for(String key:List.of("VAR3","VAR7","PMECH_LP"))for(var row:off)assertEquals(off.getFirst().get(key),row.get(key),0.,"Off-owner physical/output boundary is held");
                }
            }
        }
        assertEquals(12,pairs.size());for(var pair:pairs.values()){assertEquals(Set.of("a","b"),pair.keySet());assertEquals(pair.get("a"),pair.get("b"));}
    }

    @Test void recordedActiveValveInputsReproduceBacklashOutputAndAcceptedMemory(@TempDir Path staged)throws Exception {
        var fixture=new Wsieg1ReferenceFixture(staged);
        for(var entry:fixture.matrix()) {
            if(!entry.path("repeat").asText().equals("a"))continue;
            String tag=entry.path("tag").asText();Path root=fixture.stage(tag);var loader=new PSSEMultiFileLoader();
            var context=loader.loadDStab(root.resolve("network.raw").toString(),root.resolve("dynamic.dyr").toString());var algorithm=context.getDynSimuAlgorithm();
            assertTrue(algorithm.getAclfAlgorithm().loadflow());algorithm.setSimuStepSec(entry.path("step_s").asDouble());algorithm.setSimuOutputHandler(new com.interpss.dstab.cache.StateMonitor());assertTrue(algorithm.initialization());
            var g=(Wsieg1Governor)context.getDStabilityNet().getMachine("Bus1-mach1").getGovernor();double[] trial=new double[6];g.readStaggeredState(trial,0);
            double h=entry.path("step_s").asDouble(),last=0.,maximum=0.;
            for(var row:rows(Files.readAllBytes(root.resolve("native.csv")))) {
                double time=row.get("time_s");boolean active=!entry.path("event").asText().equals("primary-trip")||Math.round(time/h)<=Math.round(.05/h);
                var history=g.getIntegrationHistories();
                if(active){trial[1]=row.get("STATE1");g.applyStaggeredState(trial,0,h);assertEquals(history,g.getIntegrationHistories());}
                maximum=Math.max(maximum,Math.abs(g.getValveOutput()-row.get("VAR7")));
                if(time>last&&active){g.completeStaggeredStep(h);last=time;}
                assertEquals(row.get("VAR6"),g.getBacklashInputHistory(),1e-7,tag+" accepted valve input reference");
            }
            assertTrue(maximum<1e-7,tag+" recorded-valve output "+maximum);
        }
    }

    @Test void everyRetainedOrdinaryStateAndBoundaryValueRemainsBitIdentical()throws Exception {
        Path path=Path.of(Wsieg1ReferenceFixture.CONFIG.string("beforeArchive"));assumeTrue(Files.isRegularFile(path),"Retained pre-change executable traces are unavailable");
        assertEquals(Wsieg1ReferenceFixture.CONFIG.string("beforeArchiveSha256"),Wsieg1ReferenceFixture.sha(Files.readAllBytes(path)));
        try(var zip=new ZipFile(path.toFile())) {
            for(String profile:List.of("standard","curve","dual","unused-lp"))for(String event:List.of("flat","fault")) {
                String name=profile.equals("standard")?"wsieg1":"wsieg1-"+profile;
                var actual=Wsieg1SharedSmibTest.simulate(Wsieg1SharedSmibTest.CASE.resolve(profile.equals("dual")?"dual-network.raw":"network.raw"),
                        Wsieg1SharedSmibTest.CASE.resolve(name+".dyr"),false,event,.0005,.5,List.of());
                var entry=zip.getEntry("ipss.test.plugin.core/target/wsieg1-shared-actual-20261031/"+profile+"-"+event+"-euler.csv.gz");assertNotNull(entry);
                String[] lines;try(var gzip=new GZIPInputStream(zip.getInputStream(entry))){lines=new String(gzip.readAllBytes(),StandardCharsets.UTF_8).strip().split("\\R");}
                String[] keys=lines[0].split(",");assertEquals(actual.frames.size(),lines.length-1);
                for(int i=1;i<lines.length;i++) {
                    String[] cells=lines[i].split(",");var row=actual.frames.get(i-1);assertEquals(row.kind(),cells[1]);
                    assertEquals(Double.doubleToLongBits(Double.parseDouble(cells[0])),Double.doubleToLongBits(row.time()));
                    for(int k=2;k<keys.length;k++)assertEquals(Double.doubleToLongBits(Double.parseDouble(cells[k])),Double.doubleToLongBits(row.values().get(keys[k])),profile+" "+event+" "+keys[k]);
                }
            }
        }
    }

    @ParameterizedTest @ValueSource(booleans={false,true})
    void bothMethodsRetainCompleteStateBoundaryAndShaftTripComparisons(boolean staggered,@TempDir Path staged)throws Exception {
        var fixture=new Wsieg1ReferenceFixture(staged);Path output=Files.createTempDirectory(Path.of("target"),staggered?"wsieg1-state-staggered-":"wsieg1-state-euler-");
        System.out.println("WSIEG1 state comparison "+output.getFileName());var diagnostics=new LinkedHashMap<String,Object>();
        for(var entry:fixture.matrix()) {
            if(!entry.path("repeat").asText().equals("a"))continue;
            String tag=entry.path("tag").asText();Path root=fixture.stage(tag);double h=entry.path("step_s").asDouble();
            var manifest=new ObjectMapper().readTree(root.resolve("manifest.json").toFile());var events=new ArrayList<Wsieg1SharedSmibTest.ReferenceEvent>();
            for(var point:manifest.path("reference_events"))events.add(new Wsieg1SharedSmibTest.ReferenceEvent(point.get(0).asDouble(),point.get(1).asDouble()));
            var actual=Wsieg1SharedSmibTest.simulate(root.resolve("network.raw"),root.resolve("dynamic.dyr"),staggered,entry.path("event").asText(),h,entry.path("duration_s").asDouble(),events);
            var reference=rows(Files.readAllBytes(root.resolve("native.csv")));var maximum=new LinkedHashMap<String,Double>();var at=new LinkedHashMap<String,Double>();
            var activeState=new LinkedHashMap<String,Double>();var square=new LinkedHashMap<String,Double>();var low=new LinkedHashMap<String,Double>();var high=new LinkedHashMap<String,Double>();
            for(String key:reference.getFirst().keySet())if(!key.equals("time_s")&&!key.equals("VAR2")){maximum.put(key,0.);at.put(key,0.);square.put(key,0.);low.put(key,Double.POSITIVE_INFINITY);high.put(key,Double.NEGATIVE_INFINITY);}
            for(int i=0;i<6;i++)activeState.put("STATE"+i,0.);
            int frame=0,startups=0,inactive=0;long clockTick=0;float clock=0.f;
            try(var raw=BenchmarkOutputGuard.newOutputStream(output.resolve(tag+".csv.gz"),BenchmarkOutputGuard.estimateCsvBytes(reference.size(),actual.initial.values().size()+reference.getFirst().size()+2));
                    var gzip=new GZIPOutputStream(raw);var writer=new BufferedWriter(new OutputStreamWriter(gzip,StandardCharsets.UTF_8))) {
                writer.write("reference_time_s,actual_time_s,sample_kind,"+String.join(",",actual.initial.values().keySet()));for(String key:reference.getFirst().keySet())writer.write(",REFERENCE."+key);writer.write("\n");
                for(var expected:reference) {
                    double time=expected.get("time_s");var row=time<0.?actual.initial:actual.frames.get(frame++);
                    if(time<0.)startups++;else {
                        long tick=Math.round(row.time()/h);assertEquals(tick*h,row.time(),1e-10);assertTrue(tick>=clockTick&&tick-clockTick<=1);
                        while(clockTick<tick){clock+=(float)h;clockTick++;}assertEquals((double)clock,time,1e-10,"Explicit binary32 reference clock");
                    }
                    boolean ownerActive=row.values().get("ACTIVE")!=0.;if(!ownerActive)inactive++;
                    writer.write(time+","+row.time()+","+(time<0.?"STARTUP_HELD_INITIAL":row.kind()));
                    for(double value:row.values().values())writer.write(","+value);for(double value:expected.values())writer.write(","+value);writer.write("\n");
                    for(String key:maximum.keySet()) {
                        String observed=key.startsWith("STATE")?"PUBLISHED"+key.substring(5):key;assertTrue(row.values().containsKey(observed),key);
                        double value=expected.get(key),error=row.values().get(observed)-value;assertTrue(Double.isFinite(error));
                        if(Math.abs(error)>maximum.get(key)){maximum.put(key,Math.abs(error));at.put(key,time);}
                        if(key.startsWith("STATE")&&ownerActive)activeState.put(key,Math.max(activeState.get(key),Math.abs(error)));
                        square.put(key,square.get(key)+error*error);low.put(key,Math.min(low.get(key),value));high.put(key,Math.max(high.get(key),value));
                    }
                }
            }
            assertEquals(reference.size(),actual.frames.size()+startups);var norms=new LinkedHashMap<String,Object>();
            for(String key:maximum.keySet())if(key.matches("V[0-9]+")||key.startsWith("ANGLE_DEG")||key.startsWith("P_PU")||key.startsWith("Q_PU")) {
                double range=high.get(key)-low.get(key),rmse=Math.sqrt(square.get(key)/reference.size());var result=new LinkedHashMap<String,Object>();result.put("rmse",rmse);result.put("reference_range",range);result.put("range_nrmse",range==0.?null:rmse/range);norms.put(key,result);
            }
            var result=new LinkedHashMap<String,Object>();result.put("maximum_absolute_error",maximum);result.put("time_of_maximum",at);result.put("active_owner_state_maximum",activeState);
            result.put("inactive_owner_rows",inactive);result.put("norms",norms);result.put("unmapped_reference_diagnostics",List.of("VAR2"));
            result.put("inactive_state_policy","Retain raw stale allocated slots; qualify active owner states and held physical outputs separately");result.put("step_s",h);result.put("event",entry.path("event").asText());
            diagnostics.put(tag,result);new ObjectMapper().writerWithDefaultPrettyPrinter().writeValue(output.resolve("diagnostic.json").toFile(),diagnostics);
            var stateBounds=stateBounds(staggered,h);for(int i=0;i<6;i++)assertTrue(activeState.get("STATE"+i)<stateBounds[i],tag+" active STATE"+i+" "+activeState.get("STATE"+i));
            var boundaryBounds=boundaryBounds(staggered,h);
            for(var bound:boundaryBounds.entrySet())if(maximum.containsKey(bound.getKey()))assertTrue(maximum.get(bound.getKey())<bound.getValue(),tag+" "+bound.getKey()+" "+maximum.get(bound.getKey()));
            for(var norm:norms.entrySet()) {
                var values=(Map<?,?>)norm.getValue();double rmse=(double)values.get("rmse");
                if(norm.getKey().startsWith("ANGLE_DEG"))assertTrue(rmse<.01,tag+" rotor-angle RMSE "+rmse);
                if(norm.getKey().matches("V[0-9]+")) {
                    Object nrmse=values.get("range_nrmse");if(nrmse!=null)assertTrue((double)nrmse<.001,tag+" "+norm.getKey()+" voltage NRMSE "+nrmse);
                }
            }
        }
    }

    private static double[] stateBounds(boolean staggered,double h) {
        if(staggered)return h<.001?new double[]{3.6e-7,1e-5,7.5e-6,3.7e-6,2e-6,2e-6}:new double[]{8.2e-6,7.2e-6,2.8e-5,4.5e-6,3.2e-6,3.2e-6};
        return h<.001?new double[]{5.1e-6,3.3e-5,7.4e-6,3.7e-6,2.1e-6,2.1e-6}:new double[]{4.8e-5,2.8e-4,3.8e-5,6.9e-6,5e-6,5e-6};
    }
    private static Map<String,Double> boundaryBounds(boolean staggered,double h) {
        String[] keys={"V1","V2","V3","A1","A2","A3","P_PU","Q_PU","PMECH","SPEED","ANGLE_DEG","P_PU_LP","Q_PU_LP","PMECH_LP","SPEED_LP","ANGLE_DEG_LP","VAR3","VAR6","VAR7"};
        double[] values=staggered?(h<.001
                ?new double[]{2.2e-6,8e-7,1.6e-7,5.5e-5,2.1e-5,3.5e-6,6.1e-6,1.5e-5,4e-6,3.1e-8,.00014,1.3e-6,4.5e-6,1.1e-7,1.5e-8,2.6e-5,3.1e-8,5e-7,1e-5}
                :new double[]{.00016,6.1e-5,1e-5,.0017,.0005,7.1e-5,.00017,.001,9.2e-6,2.1e-6,.0021,3.9e-5,8.5e-5,9.1e-7,9.8e-7,.0012,2.1e-6,3.8e-6,6.7e-6}):(h<.001
                ?new double[]{.00024,9.2e-5,1.6e-5,.003,.0013,6.3e-5,.00016,.0023,4e-6,1.8e-6,.00013,.00012,.00024,1.7e-7,1.5e-6,6e-5,1.8e-6,7.5e-7,3.3e-5}
                :new double[]{.00165,.00062,.00011,.030,.011,.00075,.0021,.0147,1.2e-5,1.65e-5,.0043,.00092,.0016,1.2e-5,1.1e-5,.0037,1.65e-5,1.8e-5,.00028});
        var bounds=new LinkedHashMap<String,Double>();for(int i=0;i<keys.length;i++)bounds.put(keys[i],values[i]);
        bounds.put("VAR0",3.3e-8);bounds.put("VAR1",3.2e-7);bounds.put("VAR4",6.3e-8);bounds.put("VAR5",1e-12);return bounds;
    }

    static List<Map<String,Double>> rows(byte[] bytes) {
        String[] lines=new String(bytes,StandardCharsets.UTF_8).strip().split("\\R"),names=lines[0].split(",");var rows=new ArrayList<Map<String,Double>>();
        for(int i=1;i<lines.length;i++){String[] fields=lines[i].split(",");assertEquals(names.length,fields.length);var row=new LinkedHashMap<String,Double>();
            for(int k=0;k<names.length;k++){double value=Double.parseDouble(fields[k]);assertTrue(Double.isFinite(value));row.put(names[k],value);}rows.add(row);}return rows;
    }
}
