package org.interpss.core.dstab;

import static org.junit.jupiter.api.Assertions.*;
import java.nio.file.*;
import java.security.MessageDigest;
import java.util.*;
import java.util.stream.Stream;
import java.util.zip.ZipFile;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.interpss.core.dstab.reference.BenchmarkOutputGuard;
import org.interpss.dstab.control.util.StaggeredEulerParticipant;
import org.interpss.dstab.hvdc.*;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.*;

/** Independent coarse-step event discriminator through production component RHSs.
 * Recorded terminal inputs isolate integration policy; this is not full-network acceptance. */
public class Chvdc2EventIntervalReferenceTest {
    private static final ObjectMapper JSON=new ObjectMapper();
    private static final Path ARCHIVE=Path.of("testData/private/verification-artifacts/chvdc2-coarse-line-event-20261104/captures.zip");
    static Stream<Arguments> profiles(){return Stream.of("0p00416666666667","0p00208333333333")
            .flatMap(h->Stream.of(false,true).flatMap(s->Stream.of(false,true).map(line->Arguments.of(h,s,line))));}
    @ParameterizedTest @MethodSource("profiles")
    void wholeIntervalEventReplay(String interval,boolean staggered,boolean line)throws Exception {
        Assumptions.assumeTrue(Files.isRegularFile(ARCHIVE),"Retain cold coarse-step controls");
        assertEquals("ae3e8619df97ad2fb85b6d4704959faf60c057c87a6a8b2ef21d23e0efdd839c",
                HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(ARCHIVE))));
        String tag="stiff-legacy-inverter-fault-flags001-h"+interval+"-fixed-angle-line-gamma30-netiter50-fixed-dc-taps-";
        try(var z=new ZipFile(ARCHIVE.toFile())) {
            var m=JSON.readTree(z.getInputStream(z.getEntry(tag+"a/manifest.json")));
            assertEquals("complete",m.path("status").asText());assertTrue(m.path("initialization_and_network_valid").asBoolean());
            assertTrue(m.path("finite_measurements").asBoolean());assertEquals(m.path("entered_constants"),m.path("initialized_constants"));
            byte[] csv=z.getInputStream(z.getEntry(tag+"a/native.csv")).readAllBytes();
            assertArrayEquals(csv,z.getInputStream(z.getEntry(tag+"b/native.csv")).readAllBytes());
            var rows=Tiocr1ResetReferenceTest.csv(csv);double H=m.path("settings").path("step").asDouble();
            assertEquals(interval.startsWith("0p004")?125:245,rows.size());
            var equipment=JSON.readTree(z.getInputStream(z.getEntry(tag+"a/converted-equipment.json")));
            double[] constants=JSON.convertValue(m.path("entered_constants"),double[].class);
            var parameters=new Chvdc2Parameters("link",Chvdc2Parameters.Dialect.CHVDC2U1,
                    JSON.convertValue(m.path("settings").path("flags"),int[].class),constants);
            var first=rows.getFirst();
            for(var row:rows) {assertEquals(first.get("STATE6"),row.get("STATE6"));assertEquals(first.get("STATE7"),row.get("STATE7"));}
            Path output=Files.createTempDirectory(Files.createDirectories(Path.of("../target/chvdc2-event-interval-reference")),"replay-");
            Map<String,Double> errors=new LinkedHashMap<>();
            for(String policy:List.of("first-local","whole-interval")) {
                StaggeredEulerParticipant model;
                if(line) {
                    double inductance=(constants[44]+constants[45]+constants[46])/1000
                            +3.5*(equipment.path("7").path("XC").asDouble()+equipment.path("9").path("XC").asDouble())/(2*Math.PI*60);
                    var dc=new Chvdc2DcLine(5,inductance,0,true);
                    dc.initialize(5*first.get("VAR5"),0);model=dc;
                } else {
                    var dc=new Chvdc2OrderControls(parameters,equipment.path("9").path("TR").asDouble(),equipment.path("9").path("TAP").asDouble());
                    input(dc,first,constants[28]);dc.initialize(first.get("STATE7"));model=dc;
                }
                int size=model.staggeredStateCount();double h=H/10,maximum=0;
                double[] x=new double[size],old=new double[size],rate=new double[size],left=new double[size],trial=new double[size],second=new double[size];
                input(model,first,constants[28]);model.evaluateStaggered(h,old,0);
                boolean event=false;int duplicates=0;
                try(var writer=BenchmarkOutputGuard.newWriter(output.resolve(policy+".csv"),2*1024*1024)) {
                    writer.write("time_s,value,reference,error\n");
                    for(int k=0;k<rows.size();k++) {
                        var row=rows.get(k);
                        if(k>0&&row.get("time_s").equals(rows.get(k-1).get("time_s"))) {
                            input(model,rows.get(k-1),constants[28]);model.evaluateStaggered(h,left,0);event=true;duplicates++;
                        } else if(k>0) {
                            input(model,rows.get(k-1),constants[28]);
                            for(int local=0;local<10;local++) {
                                model.readStaggeredState(x,0);model.evaluateStaggered(h,rate,0);
                                boolean blend=event&&(local==0||policy.equals("whole-interval"));
                                for(int c=0;c<size;c++) {
                                    if(blend)rate[c]=.5*(rate[c]+left[c]);
                                    trial[c]=x[c]+h*(staggered?1.5*rate[c]-.5*old[c]:rate[c]);
                                }
                                model.applyStaggeredState(trial,0,h);
                                if(!staggered) {
                                    model.evaluateStaggered(h,second,0);
                                    for(int c=0;c<size;c++){if(blend)second[c]=.5*(second[c]+left[c]);trial[c]=x[c]+.5*h*(rate[c]+second[c]);}
                                    model.applyStaggeredState(trial,0,h);
                                }
                                System.arraycopy(rate,0,old,0,size);
                            }
                            event=false;
                        }
                        model.readStaggeredState(x,0);double value=x[line?0:7],reference=row.get(line?"VAR5":"STATE11");
                        maximum=Math.max(maximum,Math.abs(value-reference));
                        writer.write(row.get("time_s")+","+value+","+reference+","+(value-reference)+"\n");
                    }
                }
                assertEquals(2,duplicates);errors.put(policy,maximum);
            }
            try(var writer=BenchmarkOutputGuard.newWriter(output.resolve("comparison.json"),20000)) {
                JSON.writerWithDefaultPrettyPrinter().writeValue(writer,Map.of("profile",tag,"staggered",staggered,"line",line,
                        "maximum_errors",errors,"samples",rows.size(),"event_duplicates",2,"local_steps",10,
                        "precision","double","complete_network_qualified",false));
            }
            double limit=line?.0055:1e-5;
            assertTrue(errors.get("first-local")>10*limit,"The reference must distinguish the old event scope");
            assertTrue(errors.get("whole-interval")<limit,"Whole-interval error: "+errors.get("whole-interval"));
        }
    }
    private static void input(StaggeredEulerParticipant model,Map<String,Double> row,double rated) {
        if(model instanceof Chvdc2DcLine line)line.setInput(1000*row.get("VAR6"),1000*row.get("VAR8"));
        else ((Chvdc2OrderControls)model).setInput(row.get("VAR5"),row.get("VAR7"),row.get("VAR6"),row.get("VAR8"),
                row.get("V7"),row.get("V9"),row.get("VAR15")*rated,row.get("VAR0"));
    }
}
