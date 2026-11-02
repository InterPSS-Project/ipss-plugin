package org.interpss.dstab.control.gov.wecc.wsieg1;

import static org.junit.jupiter.api.Assertions.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.zip.GZIPOutputStream;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.commons.math3.complex.Complex;
import org.interpss.test.TestConfig;
import org.interpss.core.dstab.reference.BenchmarkOutputGuard;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/** Independent machine-state event discrimination and coupled timestep refinement. */
public class Wsieg1MachineEventConformanceTest {
    private static final TestConfig CONFIG=TestConfig.load("/test-config/ipss.test.plugin.core.json","Wsieg1MachineEventConformanceTest");
    private static final String[] STATES={"GEN_EP_Q","GEN_EP_D","GEN_PSIK_D","GEN_PSIK_Q","GEN_SPEED_DEV","GEN_ANGLE"};
    @BeforeAll static void setup(){Wsieg1SharedSmibTest.setup();}

    @Test void coldCapturesSeparateEventLeftRatesFromMultistepHistory(@TempDir Path staged)throws Exception {
        var fixture=new Wsieg1ReferenceFixture(staged,CONFIG);
        var pairs=new HashMap<Double,Map<String,String>>();int events=0;double discriminator=0.;
        for(var entry:fixture.matrix()) {
            Path input=fixture.stage(entry.path("tag").asText());double h=entry.path("step_s").asDouble();
            var manifest=new ObjectMapper().readTree(input.resolve("manifest.json").toFile());
            assertEquals(manifest.path("entered_constants"),manifest.path("initialized_constants"));
            assertEquals(6,manifest.path("machine_layouts").path("1").path("NSTATE").asInt());
            assertEquals(6,manifest.path("machine_layouts").path("2").path("NSTATE").asInt());
            byte[] data=Files.readAllBytes(input.resolve("native.csv"));
            pairs.computeIfAbsent(h,k->new HashMap<>()).put(entry.path("repeat").asText(),Wsieg1ReferenceFixture.sha(data));
            var rows=Wsieg1StateConformanceTest.rows(data);
            // This discriminator uses the exact unsaturated, undamped synthetic
            // round-rotor equations; guard their parameters and machine bases.
            var dynamic=Files.readAllLines(input.resolve("dynamic.dyr"));
            String constants="6.0 0.033 0.54 0.078 6.4 0.0 0.8958 0.8645 0.1189 0.1969 0.089 0.0521 0.0 0.0 /";
            assertEquals("1 'GENROU' 1 "+constants,dynamic.get(0));
            assertEquals("2 'GENROU' 1 "+constants,dynamic.get(1));
            var raw=Files.readAllLines(input.resolve("network.raw"));
            assertEquals(100.,Double.parseDouble(raw.get(0).split(",")[1]));
            boolean generators=false;var bases=new HashMap<Integer,Double>();
            for(String line:raw) {
                if(line.contains("BEGIN GENERATOR DATA")){generators=true;continue;}
                if(line.contains("END OF GENERATOR DATA"))break;
                if(generators){var fields=line.split(",");bases.put(Integer.parseInt(fields[0]),Double.parseDouble(fields[8]));assertEquals(0.,Double.parseDouble(fields[9]));}
            }
            assertEquals(50.,bases.get(1));assertEquals(200.,bases.get(2));
            for(int j=2;j<rows.size()-1;j++) {
                var post=rows.get(j);var pre=rows.get(j-1);
                if(post.get("time_s")<=0.||!post.get("time_s").equals(pre.get("time_s")))continue;
                events++;
                for(int bus=1;bus<=2;bus++) {
                    String suffix=bus==1?"":"_LP";
                    double[] right=rates(post,bus),left=rates(pre,bus),old=rates(rows.get(j-2),bus);
                    for(int k=0;k<STATES.length;k++) {
                        String key=STATES[k]+suffix;assertEquals(pre.get(key),post.get(key),0.,"Continuous machine state at the event");
                        double increment=rows.get(j+1).get(key)-post.get(key);
                        double expected=h*(.75*(right[k]+left[k])-.5*old[k]);
                        assertEquals(expected,increment,1.5e-7,key+" fresh left derivative with retained multistep history");
                        discriminator=Math.max(discriminator,Math.abs(increment-h*(.75*right[k]+.25*old[k])));
                    }
                }
            }
        }
        assertEquals(12,events);assertEquals(3,pairs.size());
        for(var pair:pairs.values()){assertEquals(Set.of("a","b"),pair.keySet());assertEquals(pair.get("a"),pair.get("b"));}
        assertTrue(discriminator>4e-4,"Fault clearing distinguishes old history from the fresh left endpoint");
    }

    private static double[] rates(Map<String,Double> row,int bus) {
        String s=bus==1?"":"_LP";double eq=row.get(STATES[0]+s),ed=row.get(STATES[1]+s),pd=row.get(STATES[2]+s),pq=row.get(STATES[3]+s);
        double angle=row.get(STATES[5]+s),speed=row.get(STATES[4]+s),v=row.get("V"+bus),a=Math.toRadians(row.get("A"+bus));
        var voltage=new Complex(v*Math.cos(a),v*Math.sin(a));
        var current=new Complex(row.get("P_PU"+s),row.get("Q_PU"+s)).divide(voltage).conjugate().multiply(bus==1?2.:.5);
        double id=current.getReal()*Math.sin(angle)-current.getImaginary()*Math.cos(angle);
        double iq=current.getReal()*Math.cos(angle)+current.getImaginary()*Math.sin(angle);
        double xd=.8958,xq=.8645,xdp=.1189,xqp=.1969,xpp=.089,xl=.0521;
        double td=(xdp-xpp)/Math.pow(xdp-xl,2)*(-pd-(xdp-xl)*id+eq);
        double tq=(xqp-xpp)/Math.pow(xqp-xl,2)*(-pq+(xqp-xl)*iq+ed);
        return new double[]{(row.get("EFD"+s)-eq-(xd-xdp)*(id+td))/6.,(-ed+(xq-xqp)*(iq-tq))/.54,
                (-pd-(xdp-xl)*id+eq)/.033,(-pq+(xqp-xl)*iq+ed)/.078,
                (row.get("PMECH"+s)/(1.+speed)-row.get("P_PU"+s)*(bus==1?2.:.5))/(2.*6.4),2.*Math.PI*60.*speed};
    }

    @ParameterizedTest @ValueSource(booleans={false,true})
    void completeStateTracesAndRefinedCoupledBoundaries(boolean staggered,@TempDir Path staged)throws Exception {
        var fixture=new Wsieg1ReferenceFixture(staged,CONFIG);var gates=new ArrayList<Executable>();int profiles=0;
        Path output=Files.createTempDirectory(Path.of("target"),"wsieg1-machine-events-"+(staggered?"staggered-":"euler-"));
        System.out.println("WSIEG1 machine-event comparison "+output.getFileName());
        for(var entry:fixture.matrix()) {
            if(!entry.path("repeat").asText().equals("a"))continue;profiles++;
            String tag=entry.path("tag").asText();Path input=fixture.stage(tag);double h=entry.path("step_s").asDouble();
            var reference=Wsieg1StateConformanceTest.rows(Files.readAllBytes(input.resolve("native.csv")));
            double duration=Math.round(reference.getLast().get("time_s")/h)*h;
            var actual=Wsieg1SharedSmibTest.simulate(input.resolve("network.raw"),input.resolve("dynamic.dyr"),staggered,"fault",h,duration,List.of(),a->{},0,true);
            var maxima=new LinkedHashMap<String,Double>();var squares=new LinkedHashMap<String,Double>();var at=new LinkedHashMap<String,Double>();
            for(String key:reference.getFirst().keySet())if(!key.equals("time_s")&&!key.equals("VAR2")){maxima.put(key,0.);squares.put(key,0.);at.put(key,0.);}
            int frame=0,startups=0;long tick=0;float clock=0f;
            try(var stream=new GZIPOutputStream(BenchmarkOutputGuard.newOutputStream(output.resolve(tag+".csv.gz"),16L*1024*1024));
                    var writer=new BufferedWriter(new OutputStreamWriter(stream,StandardCharsets.UTF_8))) {
            writer.write("reference_time_s,actual_time_s,sample_kind,"+String.join(",",actual.initial.values().keySet()));
            for(String key:reference.getFirst().keySet())writer.write(",REFERENCE."+key);writer.write("\n");
            for(var expected:reference) {
                double t=expected.get("time_s");var observed=t<0.?actual.initial:actual.frames.get(frame++);
                writer.write(t+","+observed.time()+","+(t<0.?"STARTUP_HELD_INITIAL":observed.kind()));
                for(double value:observed.values().values())writer.write(","+value);
                for(double value:expected.values())writer.write(","+value);writer.write("\n");
                if(t<0.)startups++;else {
                    long next=Math.round(observed.time()/h);assertTrue(next>=tick&&next-tick<=1);assertEquals(next*h,observed.time(),1e-10);
                    while(tick<next){clock+=(float)h;tick++;}assertEquals((double)clock,t,1e-10,"Explicit reference clock, including both event sides");
                }
                for(String key:maxima.keySet()) {
                    String name=key.startsWith("STATE")?"PUBLISHED"+key.substring(5):key;
                    double error=observed.values().get(name)-expected.get(key);assertTrue(Double.isFinite(error));
                    if(Math.abs(error)>maxima.get(key)){maxima.put(key,Math.abs(error));at.put(key,t);}squares.put(key,squares.get(key)+error*error);
                    if(t<=0.&&key.startsWith("GEN_"))assertEquals(expected.get(key),observed.values().get(name),3e-6,"Initial machine state");
                }
            }
            }
            assertEquals(reference.size(),actual.frames.size()+startups);
            var normalized=new LinkedHashMap<String,Double>();var pass=new LinkedHashMap<String,Boolean>();
            for(String key:maxima.keySet()) {
                if(staggered&&key.startsWith("GEN_"))gates.add(()->assertTrue(maxima.get(key)<3e-6,tag+" "+key+" machine state error "+maxima.get(key)));
                if(key.matches("V[0-9]+")||key.startsWith("P_PU")||key.startsWith("Q_PU")||key.startsWith("ANGLE_DEG")) {
                    double rmse=Math.sqrt(squares.get(key)/reference.size());
                    double range=reference.stream().mapToDouble(r->r.get(key)).max().orElseThrow()-reference.stream().mapToDouble(r->r.get(key)).min().orElseThrow();
                    assertTrue(range>0.);double nrmse=rmse/range;normalized.put(key,nrmse);
                    boolean accepted=key.startsWith("ANGLE_DEG")?rmse<.01:nrmse<.001;pass.put(key,accepted);
                    // The existing quarter-cycle modified-Euler conformance test
                    // retains its failed gate. This test additionally qualifies
                    // refinement, without turning a coarse failure into a pass.
                    if(staggered||h<1./240.)gates.add(()->assertTrue(accepted,tag+" "+key+" refined boundary"));
                    if(staggered&&!key.startsWith("ANGLE_DEG"))gates.add(()->assertTrue(nrmse<1e-5,tag+" "+key+" event-corrected boundary "+nrmse));
                }
            }
            var report=new LinkedHashMap<String,Object>();report.put("tag",tag);report.put("staggered",staggered);report.put("samples",reference.size());report.put("step_s",h);
            report.put("maximum_absolute_error",maxima);report.put("time_of_maximum_error",at);report.put("range_nrmse",normalized);report.put("unchanged_coupled_gates",pass);
            report.put("purpose","Machine event-state qualification and method refinement; coarse modified-Euler failures remain open");
            report.put("reference_archive_sha256",CONFIG.string("archiveSha256"));report.put("startup_rows_held_at_initial_state",startups);
            new ObjectMapper().writerWithDefaultPrettyPrinter().writeValue(output.resolve(tag+".json").toFile(),report);
        }
        assertEquals(3,profiles);assertAll(gates);
    }
}
