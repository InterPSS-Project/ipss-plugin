package org.interpss.core.dstab;

import static org.junit.jupiter.api.Assertions.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.security.MessageDigest;
import java.util.*;
import java.util.stream.Stream;
import java.util.zip.ZipFile;
import com.fasterxml.jackson.databind.*;
import org.interpss.core.dstab.reference.BenchmarkOutputGuard;
import org.interpss.dstab.hvdc.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.*;

/** Independent prescribed-boundary qualification, distinct from coupled HVDC attachment. */
public class Chvdc2OrderControlsReferenceTest {
    private static final Path ROOT=Path.of("testData/private/verification-artifacts");
    private static final Map<String,String> ARCHIVES=Map.of(
            "chvdc2-controller-20261102/captures.zip","75d8b6a733eb20426dbc8c8fe7c4efdab854c8e11a82da99ee98f6ce76377511",
            "chvdc2-orders-20261102/captures.zip","e62a9f7e3944adbeaafc98ace40d428a8007b071ac1cd5c3d6f3552ba1480d05",
            "chvdc2-orders-20261102/zero-captures.zip","435478d8faa3b727cab778bf93d2e5c06bb97df8a5322f8f5576c2dfb70d6178");
    private static final ObjectMapper JSON=new ObjectMapper();
    private record Capture(Path archive,JsonNode manifest,JsonNode equipment){}
    private static final Map<String,Capture> CAPTURES=new TreeMap<>();
    private static final int[] STATES={0,1,2,3,8,9,10,11,12,13};
    private static final double[] LIMITS={1e-4,1e-4,1e-4,1e-4,5e-4,5e-6,1e-4,1e-4,1e-4,1e-4,1.,1.,.002};
    private static String sha(byte[] b)throws Exception{return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(b));}
    @BeforeAll static void setup()throws Exception {
        CAPTURES.clear();
        for(var archive:ARCHIVES.entrySet()) {
            Path path=ROOT.resolve(archive.getKey());Assumptions.assumeTrue(Files.isRegularFile(path),"Retain independent order-control captures");
            assertEquals(archive.getValue(),sha(Files.readAllBytes(path)));
            try(var z=new ZipFile(path.toFile())) {
                var matrix=JSON.readTree(z.getInputStream(z.getEntry("manifest.json")));
                for(var file:matrix.path("files").properties())assertEquals(file.getValue().asText(),sha(z.getInputStream(z.getEntry(file.getKey())).readAllBytes()));
                for(var profile:matrix.path("profiles")) {
                    if(!profile.path("repeat").asText().equals("a"))continue;String tag=profile.path("tag").asText();
                    var m=JSON.readTree(z.getInputStream(z.getEntry(tag+"/manifest.json")));
                    assertEquals("complete",m.path("status").asText());assertEquals(m.path("entered_constants"),m.path("initialized_constants"));
                    assertTrue(m.path("initialization_and_network_valid").asBoolean());assertTrue(m.path("excitation_observed").asBoolean());
                    assertArrayEquals(z.getInputStream(z.getEntry(tag+"/native.csv")).readAllBytes(),z.getInputStream(z.getEntry(tag.substring(0,tag.length()-1)+"b/native.csv")).readAllBytes());
                    var equipment=JSON.readTree(z.getInputStream(z.getEntry(tag+"/solved-equipment.json"))).path("9");
                    assertNull(CAPTURES.put(tag,new Capture(path,m,equipment)));
                }
            }
        }
        assertEquals(22,CAPTURES.size());
    }
    static Stream<Arguments> profiles(){return CAPTURES.keySet().stream().flatMap(tag->Stream.of(false,true).map(shared->Arguments.of(tag,shared)));}
    static Stream<String> limiterCaptures(){return CAPTURES.keySet().stream().filter(tag->tag.contains("dc-limit")||tag.contains("ac-limit"));}
    private static List<Map<String,Double>> rows(byte[] bytes){
        String[] lines=new String(bytes,StandardCharsets.UTF_8).strip().split("\\R"),header=lines[0].split(",");List<Map<String,Double>> result=new ArrayList<>();
        for(int i=1;i<lines.length;i++){String[] cells=lines[i].split(",");assertEquals(header.length,cells.length);Map<String,Double> row=new HashMap<>();for(int j=0;j<header.length;j++){double v=Double.parseDouble(cells[j]);assertTrue(Double.isFinite(v));row.put(header[j],v);}result.add(row);}return result;
    }
    private static void inputs(Chvdc2OrderControls m,Map<String,Double> r,double rated){
        m.setInput(r.get("VAR5"),r.get("VAR7"),r.get("VAR6"),r.get("VAR8"),r.get("V7"),r.get("V9"),r.get("VAR15")*rated,r.get("VAR0"));
    }
    @ParameterizedTest @MethodSource("limiterCaptures")
    void sameRowStatesDoNotReconstructRecordedCurrentOrder(String tag)throws Exception {
        var capture=CAPTURES.get(tag);double[] c=JSON.convertValue(capture.manifest().path("entered_constants"),double[].class);
        boolean ac=capture.manifest().path("settings").path("flags").get(0).asInt()==1;
        List<Map<String,Double>> rows;try(var z=new ZipFile(capture.archive().toFile())){rows=rows(z.getInputStream(z.getEntry(tag+"/native.csv")).readAllBytes());}
        double maximum=0,at=0;int terminal=0;
        for(int row=0;row<rows.size();row++) {
            var r=rows.get(row);
            for(int side=0;side<2;side++) {
                double voltage=r.get("STATE"+(2+side));
                double curve=c[20]+(c[21]-c[20])*Math.max(0,Math.min(1,(voltage-c[22])/(c[23]-c[22])));
                double order=Math.min(r.get("VAR15"),curve);if(ac)order=Math.min(order,r.get("STATE"+(12+side)));
                double error=Math.abs(order*c[28]-r.get("VAR"+(13+side)));
                if(row==0)assertTrue(error<.001,"Initialization must establish the static current/voltage bases");
                if(error>maximum){maximum=error;at=r.get("time_s");terminal=side;}
            }
        }
        Path out=Files.createTempDirectory(Path.of("target"),"chvdc2-orders-observation-");
        try(var w=BenchmarkOutputGuard.newWriter(out.resolve("observation.json"),20000)){
            JSON.writerWithDefaultPrettyPrinter().writeValue(w,Map.of("profile",tag,"maximum_same_row_current_order_discrepancy_A",maximum,
                    "time_s",at,"terminal_index",terminal,"samples",rows.size(),"production_time_shift_applied",false,"coupled_acceptance_is_separate",true));
        }
        assertTrue(maximum>(ac?.05:.5),"Retain the independently observed state/output distinction");
    }
    @ParameterizedTest @MethodSource("profiles")
    void replayAllMeasurementLimiterAndAngleBoundStates(String tag,boolean shared)throws Exception {
        var capture=CAPTURES.get(tag);var manifest=capture.manifest();double h=manifest.path("settings").path("step").asDouble();
        double[] c=JSON.convertValue(manifest.path("entered_constants"),double[].class);int[] flags=JSON.convertValue(manifest.path("settings").path("flags"),int[].class);
        var m=new Chvdc2Parameters("link",Chvdc2Parameters.Dialect.CHVDC2U1,flags,c).prepareOrderControls(capture.equipment().path("TR").asDouble(),capture.equipment().path("TAP").asDouble());
        List<Map<String,Double>> rows;try(var z=new ZipFile(capture.archive().toFile())){rows=rows(z.getInputStream(z.getEntry(tag+"/native.csv")).readAllBytes());}
        assertEquals(h==.0005?1005:2005,rows.size());inputs(m,rows.getFirst(),c[28]);m.initialize(rows.getFirst().get("STATE7"));
        assertEquals(rows.getFirst().get("VAR17"),m.acReferencePu(),1e-6);assertEquals(rows.getFirst().get("VAR16"),m.cosineReference(),1e-6);
        double[] x=new double[10],f=new double[10],old=new double[10],left=new double[10],trial=new double[10],values=new double[13],maximum=new double[13],times=new double[13];
        boolean started=false,event=false;int duplicates=0;double excitation=0;
        Path out=Files.createTempDirectory(Path.of("target"),"chvdc2-orders-"+(shared?"staggered-":"euler-"));
        try(var w=BenchmarkOutputGuard.newWriter(out.resolve("replay.csv"),BenchmarkOutputGuard.estimateCsvBytes(rows.size(),40))) {
            w.write("source_time_s");for(int i=0;i<13;i++)w.write(",value_"+i+",reference_"+i+",error_"+i);w.write("\n");
            for(int index=0;index<rows.size();index++) {
                var r=rows.get(index);double time=r.get("time_s");
                if(index>0) {
                    var before=rows.get(index-1);inputs(m,before,c[28]);
                    if(time==before.get("time_s")){m.evaluateStaggered(h,left,0);event=true;duplicates++;}
                    else if(shared) {
                        m.readStaggeredState(x,0);m.evaluateStaggered(h,f,0);if(!started)System.arraycopy(f,0,old,0,10);
                        if(event){for(int i=0;i<10;i++)f[i]=.5*(f[i]+left[i]);event=false;}
                        for(int i=0;i<10;i++)trial[i]=x[i]+h*(1.5*f[i]-.5*old[i]);m.applyStaggeredState(trial,0,h);m.readStaggeredState(x,0);
                        for(int i=0;i<10;i++)old[i]=x[i]==trial[i]?f[i]:0.;started=true;
                    }else{m.step(h,0);inputs(m,r,c[28]);m.step(h,1);}
                }
                inputs(m,r,c[28]);m.readPublishedStates(values,0);values[10]=m.rectifierCurrentOrderAmperes();values[11]=m.inverterCurrentOrderAmperes();values[12]=m.inverterMaximumDegrees();
                excitation=Math.max(excitation,Math.max(rows.getFirst().get("VAR13")-r.get("VAR13"),rows.getFirst().get("VAR14")-r.get("VAR14")));
                w.write(Double.toString(time));
                for(int i=0;i<13;i++) {
                    double expected=r.get(i<10?"STATE"+STATES[i]:i==10?"VAR13":i==11?"VAR14":"VAR20"),error=values[i]-expected;
                    if(Math.abs(error)>maximum[i]){maximum[i]=Math.abs(error);times[i]=time;}w.write(","+values[i]+","+expected+","+error);
                }
                w.write("\n");
            }
        }
        try(var w=BenchmarkOutputGuard.newWriter(out.resolve("comparison.json"),20000)){
            JSON.writerWithDefaultPrettyPrinter().writeValue(w,Map.of("profile",tag,"shared_staggered",shared,"maximum_absolute_errors",maximum,"maximum_error_time_s",times,"limits",LIMITS,"samples",rows.size(),"event_duplicates",duplicates,"maximum_order_reduction_A",excitation,"coupled_acceptance_is_separate",true));
        }
        assertEquals(2,duplicates);if(tag.contains("dc-limit")||tag.contains("ac-limit"))assertTrue(excitation>1,"Limiter must be exercised");
        List<org.junit.jupiter.api.function.Executable> gates=new ArrayList<>();
        for(int i=0;i<13;i++){int channel=i;gates.add(()->assertTrue(maximum[channel]<LIMITS[channel],"Channel "+channel+" error "+maximum[channel]+" at "+times[channel]));}
        assertAll(tag+" / "+shared,gates);
    }
}
