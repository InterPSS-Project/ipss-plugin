package org.interpss.core.dstab;

import static org.junit.jupiter.api.Assertions.*;
import java.nio.file.*;
import java.security.MessageDigest;
import java.util.*;
import java.util.stream.Stream;
import java.util.zip.ZipFile;
import com.fasterxml.jackson.databind.*;
import org.interpss.core.dstab.reference.BenchmarkOutputGuard;
import org.interpss.dstab.hvdc.LccConverterBoundary;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

/** Independent initial/electrical boundary checks; inverter publication and coupled trajectories remain open. */
public class Chvdc2ConverterReferenceTest {
    private static final ObjectMapper JSON=new ObjectMapper();
    private record Capture(Path archive,JsonNode manifest,JsonNode equipment){}
    private static final Map<String,Capture> CAPTURES=new TreeMap<>();
    @BeforeAll static void setup()throws Exception {
        CAPTURES.clear();
        var archives=Map.of("chvdc2-controller-20261102/captures.zip","75d8b6a733eb20426dbc8c8fe7c4efdab854c8e11a82da99ee98f6ce76377511",
                "chvdc2-orders-20261102/captures.zip","e62a9f7e3944adbeaafc98ace40d428a8007b071ac1cd5c3d6f3552ba1480d05",
                "chvdc2-orders-20261102/zero-captures.zip","435478d8faa3b727cab778bf93d2e5c06bb97df8a5322f8f5576c2dfb70d6178");
        for(var a:archives.entrySet()) {
            var path=Path.of("testData/private/verification-artifacts").resolve(a.getKey());
            Assumptions.assumeTrue(Files.isRegularFile(path),"Retain independent converter captures");assertEquals(a.getValue(),sha(Files.readAllBytes(path)));
            try(var z=new ZipFile(path.toFile())) {
                var matrix=JSON.readTree(z.getInputStream(z.getEntry("manifest.json")));
                for(var file:matrix.path("files").properties())assertEquals(file.getValue().asText(),sha(z.getInputStream(z.getEntry(file.getKey())).readAllBytes()));
                for(var p:matrix.path("profiles")) {
                    if(!p.path("repeat").asText().equals("a"))continue;String tag=p.path("tag").asText();
                    var m=JSON.readTree(z.getInputStream(z.getEntry(tag+"/manifest.json")));
                    assertEquals("complete",m.path("status").asText());assertEquals(m.path("entered_constants"),m.path("initialized_constants"));
                    assertTrue(m.path("initialization_and_network_valid").asBoolean());assertTrue(m.path("excitation_observed").asBoolean());
                    assertArrayEquals(z.getInputStream(z.getEntry(tag+"/native.csv")).readAllBytes(),z.getInputStream(z.getEntry(tag.substring(0,tag.length()-1)+"b/native.csv")).readAllBytes());
                    assertNull(CAPTURES.put(tag,new Capture(path,m,JSON.readTree(z.getInputStream(z.getEntry(tag+"/solved-equipment.json"))))));
                }
            }
        }
        assertEquals(22,CAPTURES.size());
    }
    static Stream<String> profiles(){return CAPTURES.keySet().stream();}
    private static String sha(byte[] b)throws Exception{return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(b));}
    private static LccConverterBoundary converter(JsonNode equipment,boolean rectifier) {
        return new LccConverterBoundary(rectifier,2,equipment.path("EBASE").asDouble()*1000,equipment.path("TR").asDouble(),
                equipment.path("TAP").asDouble(),equipment.path("XC").asDouble(),1e8);
    }
    @ParameterizedTest @MethodSource("profiles")
    void initialConditionsAndRectifierPowersMatchWhileInverterPublicationRemainsDiagnostic(String tag)throws Exception {
        var c=CAPTURES.get(tag);var rectifier=converter(c.equipment().path("7"),true);var inverter=converter(c.equipment().path("9"),false);
        List<Map<String,Double>> rows;try(var z=new ZipFile(c.archive().toFile())){rows=Tiocr1ResetReferenceTest.csv(z.getInputStream(z.getEntry(tag+"/native.csv")).readAllBytes());}
        assertEquals(c.manifest().path("samples").asInt(),rows.size());assertTrue(rows.getFirst().get("time_s")<0);
        double[] values=new double[8],maximum=new double[7],times=new double[7];double closure=0,closureTime=0,prior=Double.NaN;int duplicates=0;
        String[] names={"rectifier_dc_V","rectifier_ac_P_pu","rectifier_ac_Q_pu","inverter_dc_V","inverter_ac_P_pu","inverter_ac_Q_pu","extinction_rad"};
        Path out=Files.createTempDirectory(Path.of("target"),"chvdc2-converter-");
        try(var w=BenchmarkOutputGuard.newWriter(out.resolve("comparison.csv"),BenchmarkOutputGuard.estimateCsvBytes(rows.size(),23))) {
            w.write("time_s");for(String name:names)w.write(",value_"+name+",reference_"+name+",error_"+name);w.write(",source_inverter_power_closure_pu\n");
            for(int n=0;n<rows.size();n++) {
                var row=rows.get(n);double time=row.get("time_s");if(time==prior)duplicates++;prior=time;
                rectifier.evaluateFiringAngle(row.get("V7"),row.get("VAR5"),Math.toRadians(row.get("VAR2")),values,0);
                inverter.evaluateFiringAngle(row.get("V9"),row.get("VAR7"),Math.toRadians(row.get("VAR3")),values,4);
                double[] reference={row.get("VAR6")*1000,row.get("VAR9")/100,row.get("VAR10")/100,
                        row.get("VAR8")*1000,row.get("VAR11")/100,row.get("VAR12")/100,Math.toRadians(row.get("VAR4"))};
                w.write(Double.toString(time));
                for(int channel=0;channel<7;channel++) {
                    double value=values[channel<3?channel:channel+1],error=value-reference[channel];
                    if(Math.abs(error)>maximum[channel]){maximum[channel]=Math.abs(error);times[channel]=time;}
                    if(n==0)assertEquals(reference[channel],value,channel==0||channel==3?1.:channel==6?Math.toRadians(.002):1e-5,"Initial boundary "+names[channel]);
                    w.write(","+value+","+reference[channel]+","+error);
                }
                double residual=-row.get("VAR8")*1000*row.get("VAR7")/1e8-row.get("VAR11")/100;
                if(Math.abs(residual)>closure){closure=Math.abs(residual);closureTime=time;}w.write(","+residual+"\n");
            }
        }
        var report=new LinkedHashMap<String,Object>();report.put("profile",tag);report.put("channels",names);report.put("maximum_absolute_errors",maximum);report.put("maximum_error_time_s",times);
        report.put("samples",rows.size());report.put("event_duplicates",duplicates);report.put("source_inverter_power_closure_pu",closure);report.put("source_closure_time_s",closureTime);
        report.put("qualified_scope","initial boundaries and measured-input rectifier P/Q only");report.put("full_inverter_trajectory_accepted",false);report.put("coupled_acceptance_is_separate",true);
        try(var w=BenchmarkOutputGuard.newWriter(out.resolve("comparison.json"),20000)){JSON.writerWithDefaultPrettyPrinter().writeValue(w,report);}
        assertEquals(2,duplicates);assertTrue(maximum[1]<1e-5,"Rectifier P");assertTrue(maximum[2]<1e-5,"Rectifier Q");
        // The source itself does not publish a simultaneous inverter P=VI boundary.
        // Retain every sample and this independent discriminator rather than fit a delay.
        assertTrue(closure>1e-4,"Preserve the source-only publication discrepancy");
    }
}
