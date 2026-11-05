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

/** Prescribed-input qualification of current controls; complete network coupling remains separate. */
public class Chvdc2CurrentControlsReferenceTest {
    private static final Path ARCHIVE=Path.of("testData/private/verification-artifacts/chvdc2-controller-20261102/captures.zip");
    private static final String SHA="75d8b6a733eb20426dbc8c8fe7c4efdab854c8e11a82da99ee98f6ce76377511";
    private static final ObjectMapper JSON=new ObjectMapper();
    private static final Map<String,JsonNode> PROFILES=new TreeMap<>();
    private static final int[] SOURCE_STATES={4,6,5,7};
    private static String sha(byte[] b)throws Exception{return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(b));}
    @BeforeAll static void setup()throws Exception {
        Assumptions.assumeTrue(Files.isRegularFile(ARCHIVE),"Retain independent current-controller captures");
        assertEquals(SHA,sha(Files.readAllBytes(ARCHIVE)));PROFILES.clear();
        try(var z=new ZipFile(ARCHIVE.toFile())) {
            var matrix=JSON.readTree(z.getInputStream(z.getEntry("manifest.json")));
            for(var file:matrix.path("files").properties())assertEquals(file.getValue().asText(),sha(z.getInputStream(z.getEntry(file.getKey())).readAllBytes()));
            assertEquals(24,matrix.path("profiles").size());
            for(var entry:matrix.path("profiles")) {
                String tag=entry.path("tag").asText();var m=JSON.readTree(z.getInputStream(z.getEntry(tag+"/manifest.json")));
                assertEquals("complete",m.path("status").asText());assertTrue(m.path("excitation_observed").asBoolean());
                assertTrue(m.path("initialization_and_network_valid").asBoolean());
                assertEquals(m.path("entered_constants"),m.path("initialized_constants"));
                if(entry.path("repeat").asText().equals("a")) {
                    assertArrayEquals(z.getInputStream(z.getEntry(tag+"/native.csv")).readAllBytes(),z.getInputStream(z.getEntry(tag.substring(0,tag.length()-1)+"b/native.csv")).readAllBytes());
                    PROFILES.put(tag,m);
                }
            }
        }
        assertEquals(12,PROFILES.size());
    }
    static Stream<Arguments> profiles(){return PROFILES.keySet().stream().flatMap(tag->Stream.of(false,true).map(shared->Arguments.of(tag,shared)));}
    static Stream<String> captures(){return PROFILES.keySet().stream();}
    private static List<Map<String,Double>> rows(byte[] csv) {
        String[] lines=new String(csv,StandardCharsets.UTF_8).strip().split("\\R"),header=lines[0].split(",");
        List<Map<String,Double>> rows=new ArrayList<>();
        for(int i=1;i<lines.length;i++) {
            String[] cells=lines[i].split(",");assertEquals(header.length,cells.length);Map<String,Double> row=new HashMap<>();
            for(int j=0;j<header.length;j++){double value=Double.parseDouble(cells[j]);assertTrue(Double.isFinite(value));row.put(header[j],value);}
            rows.add(row);
        }
        return rows;
    }
    private static void inputs(Chvdc2CurrentControls m,Map<String,Double> r,double minimum) {
        // RAML is disabled in these profiles: its diagnostic VAR does not replace the entered limit.
        m.setInput(r.get("STATE0"),r.get("STATE1"),r.get("VAR13"),r.get("VAR14"),r.get("VAR0"),r.get("VAR1"),minimum,r.get("VAR20"));
    }
    @ParameterizedTest @MethodSource("captures")
    void numericalPrecisionDiscriminatorDoesNotQualifyCoupledModel(String tag)throws Exception {
        var manifest=PROFILES.get(tag);double h=manifest.path("settings").path("step").asDouble();
        double[] c=JSON.convertValue(manifest.path("entered_constants"),double[].class);List<Map<String,Double>> rows;
        try(var z=new ZipFile(ARCHIVE.toFile())){rows=rows(z.getInputStream(z.getEntry(tag+"/native.csv")).readAllBytes());}
        double[] errors=new double[2];int[] subdivisions={1,10};
        for(int control=0;control<2;control++) {
            int n=subdivisions[control];float x=rows.getFirst().get("STATE4").floatValue();
            for(int index=1;index<rows.size();index++) {
                var a=rows.get(index-1);var b=rows.get(index);
                if(!a.get("time_s").equals(b.get("time_s"))) {
                    for(int j=0;j<n;j++) {
                        double fraction=(j+.5)/n;
                        float measured=(float)((1-fraction)*a.get("STATE0")+fraction*b.get("STATE0"));
                        float order=(float)(((1-fraction)*a.get("VAR13")+fraction*b.get("VAR13"))/c[28]);
                        float rate=(float)c[1]*(measured-order);
                        x=(float)Math.max(c[3],Math.min(c[2],x+(float)(h/n)*rate));
                    }
                }
                errors[control]=Math.max(errors[control],Math.abs(x-b.get("STATE4")));
            }
        }
        Path out=Files.createTempDirectory(Path.of("target"),"chvdc2-current-precision-");
        try(var w=BenchmarkOutputGuard.newWriter(out.resolve("precision.json"),20000)) {
            JSON.writerWithDefaultPrettyPrinter().writeValue(w,Map.of("profile",tag,"single_precision_subdivisions",subdivisions,
                    "maximum_rectifier_pi_error_deg",errors,"production_precision_changed",false,"coupled_acceptance_is_separate",true));
        }
        assertTrue(errors[0]>1e-4,"Single outer update must remain a distinct negative control");
        assertTrue(errors[1]<8*Math.ulp(32f),"Ten binary32 updates explain the observed PI quantization within eight ULPs: "+errors[1]);
    }
    @ParameterizedTest @MethodSource("profiles")
    void replayEveryControllerCoordinate(String tag,boolean shared)throws Exception {
        var manifest=PROFILES.get(tag);double h=manifest.path("settings").path("step").asDouble();
        double[] c=JSON.convertValue(manifest.path("entered_constants"),double[].class);
        List<Map<String,Double>> rows;
        try(var z=new ZipFile(ARCHIVE.toFile())){rows=rows(z.getInputStream(z.getEntry(tag+"/native.csv")).readAllBytes());}
        assertEquals(h==.0005?1005:2005,rows.size());
        var m=new Chvdc2Parameters("link",Chvdc2Parameters.Dialect.CHVDC2U1,new int[]{0,0,1},c).prepareCurrentControls();
        inputs(m,rows.getFirst(),c[3]);m.initialize(rows.getFirst().get("STATE6"),rows.getFirst().get("STATE7"));
        double[] x=new double[4],f=new double[4],previous=new double[4],left=new double[4],trial=new double[4],max=new double[4],at=new double[4];
        boolean started=false,event=false;int duplicates=0;
        Path out=Files.createTempDirectory(Path.of("target"),"chvdc2-current-"+(shared?"staggered-":"euler-"));
        try(var w=BenchmarkOutputGuard.newWriter(out.resolve("replay.csv"),BenchmarkOutputGuard.estimateCsvBytes(rows.size(),13))) {
            w.write("source_time_s,pi_r,reference_pi_r,error_pi_r,alpha_r,reference_alpha_r,error_alpha_r,pi_i,reference_pi_i,error_pi_i,alpha_i,reference_alpha_i,error_alpha_i\n");
            for(int row=0;row<rows.size();row++) {
                var current=rows.get(row);double time=current.get("time_s");
                if(row>0) {
                    var before=rows.get(row-1);inputs(m,before,c[3]);
                    if(time==before.get("time_s")) {
                        m.evaluateStaggered(h,left,0);event=true;duplicates++;inputs(m,current,c[3]);
                    }else if(shared) {
                        m.readStaggeredState(x,0);m.evaluateStaggered(h,f,0);
                        if(!started)System.arraycopy(f,0,previous,0,4);
                        if(event){for(int i=0;i<4;i++)f[i]=.5*(f[i]+left[i]);event=false;}
                        for(int i=0;i<4;i++)trial[i]=x[i]+h*(1.5*f[i]-.5*previous[i]);
                        m.applyStaggeredState(trial,0,h);m.readStaggeredState(x,0);
                        for(int i=0;i<4;i++)previous[i]=x[i]==trial[i]?f[i]:0.;
                        started=true;
                    }else {
                        m.step(h,0);inputs(m,current,c[3]);m.step(h,1);
                    }
                }
                m.readStaggeredState(x,0);w.write(Double.toString(time));
                for(int i=0;i<4;i++) {
                    double reference=current.get("STATE"+SOURCE_STATES[i]),error=x[i]-reference;
                    if(Math.abs(error)>max[i]){max[i]=Math.abs(error);at[i]=time;}
                    w.write(","+x[i]+","+reference+","+error);
                }
                w.write("\n");
            }
        }
        try(var w=BenchmarkOutputGuard.newWriter(out.resolve("comparison.json"),20000)) {
            JSON.writerWithDefaultPrettyPrinter().writeValue(w,Map.of("profile",tag,"shared_staggered",shared,"maximum_absolute_error_deg",max,"maximum_error_time_s",at,"samples",rows.size(),"event_duplicates",duplicates,"coupled_acceptance_is_separate",true));
        }
        assertEquals(2,duplicates);
        assertAll("Current-control replay: "+tag+" / "+shared,
                ()->assertTrue(max[0]<.002,"Rectifier PI error "+max[0]),
                ()->assertTrue(max[1]<.002,"Rectifier angle error "+max[1]),
                ()->assertTrue(max[2]<.002,"Inverter PI error "+max[2]),
                ()->assertTrue(max[3]<.002,"Inverter angle error "+max[3]));
    }
}
