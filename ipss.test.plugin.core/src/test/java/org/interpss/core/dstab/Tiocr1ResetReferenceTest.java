package org.interpss.core.dstab;

import static org.junit.jupiter.api.Assertions.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.security.MessageDigest;
import java.util.*;
import java.util.zip.ZipFile;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

/** Complete independent reset/re-pickup boundaries and accepted protection milestones. */
public class Tiocr1ResetReferenceTest {
    private static final Path ARCHIVE=Path.of("testData/private/verification-artifacts/tiocr-reset-20261101/captures.zip");

    @ParameterizedTest
    @CsvSource({"false,tiocr1-0","true,tiocr1-0","false,tiocr1-1","true,tiocr1-1"})
    void shortFaultResetsBeforeSecondPickup(boolean staggered,String fixture) throws Exception {
        Assumptions.assumeTrue(Files.isRegularFile(ARCHIVE),"Retain independent reset/re-pickup captures");
        assertEquals("e4d5abbe60840cfcd2f62601749b90218a618ecc5c2302687a98aa194afc025d",
                HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(ARCHIVE))));
        List<Map<String,Double>> reference;
        try(var zip=new ZipFile(ARCHIVE.toFile())) {
            reference=csv(zip.getInputStream(zip.getEntry(fixture+"/a/native.csv")).readAllBytes())
                    .stream().filter(row->row.get("time_s")>=0).toList();
        }
        HspUsageRankedModelTest.run(fixture,false,staggered,true);
        var actual=csv(Files.readAllBytes(Path.of("target/hsp-model-actual-20261030",
                fixture+"-reset"+(staggered?"-staggered":"")+"-fault.csv")));
        assertEquals(reference.size(),actual.size(),"Every grid and event-side sample is retained");
        var bounds=new LinkedHashMap<String,Double>();
        // Profile-specific bounds tightened after independent runs; base-profile bounds stay unchanged.
        bounds.put("V1",staggered?6.7e-6:.00026);bounds.put("V2",staggered?2.3e-6:.00007);
        bounds.put("P_PU",staggered?7.5e-6:.00014);bounds.put("Q_PU",staggered?4.4e-5:.00185);
        bounds.put("ANGLE_DEG",staggered?9.4e-5:7.1e-5);bounds.put("SPEED",staggered?3.7e-8:1.5e-6);
        var errors=new TreeMap<String,Double>();bounds.keySet().forEach(k->errors.put(k,0.));
        var normalized=new TreeMap<String,double[]>();
        for(String key:List.of("V1","V2","P_PU","Q_PU"))
            normalized.put(key,new double[]{0,Double.POSITIVE_INFINITY,Double.NEGATIVE_INFINITY});
        double firstMaximum=0,referenceMaximum=0,actualPickup=Double.NaN,referencePickup=Double.NaN;
        int resetSamples=0;
        for(int i=0;i<actual.size();i++) {
            var a=actual.get(i);var b=reference.get(i);double time=a.get("time_s");
            assertEquals(b.get("time_s"),time,5e-6,"Reference clock and event-side alignment");
            for(String key:bounds.keySet())errors.put(key,Math.max(errors.get(key),Math.abs(a.get(key)-b.get(key))));
            for(var entry:normalized.entrySet()) {
                double value=b.get(entry.getKey()),delta=a.get(entry.getKey())-value;var metric=entry.getValue();
                metric[0]+=delta*delta;metric[1]=Math.min(metric[1],value);metric[2]=Math.max(metric[2],value);
            }
            if(time>=.05 && time<.15) {
                firstMaximum=Math.max(firstMaximum,a.get("RELAY.Position"));
                referenceMaximum=Math.max(referenceMaximum,b.get("VAR1"));
            }
            if(time>=.15 && time<.19) {
                assertEquals(0,a.get("RELAY.Position"),1e-12);assertEquals(0,b.get("VAR1"),1e-12);resetSamples++;
            }
            if(time>=.2 && a.get("RELAY.Position")>=1 && Double.isNaN(actualPickup))actualPickup=time;
            if(b.get("time_s")>=.2 && b.get("VAR1")>=1 && Double.isNaN(referencePickup))referencePickup=b.get("time_s");
        }
        assertTrue(firstMaximum>.4 && firstMaximum<1);assertTrue(referenceMaximum>.4 && referenceMaximum<1);
        assertTrue(resetSamples>=79);assertTrue(Double.isFinite(actualPickup));
        assertEquals(referencePickup,actualPickup,.00051,"Second pickup agrees within one integration interval");
        if(fixture.equals("tiocr1-1")) {
            assertEquals(tripTime(reference),tripTime(actual),5e-6,"Accepted physical-action event time");
            assertEquals(0,actual.getLast().get("RELAY.Position"));
        } else assertEquals(1,actual.getLast().get("RELAY.Position"));
        System.out.println("TIOCR reset boundary "+staggered+" "+fixture+" "+errors);
        for(var entry:normalized.entrySet()) {
            var metric=entry.getValue();assertTrue(metric[2]>metric[1]);
            double nrmse=Math.sqrt(metric[0]/actual.size())/(metric[2]-metric[1]);
            System.out.println("TIOCR reset NRMSE "+staggered+" "+fixture+" "+entry.getKey()+"="+nrmse);
            assertTrue(nrmse<.001,entry.getKey()+" range-normalized RMSE "+nrmse);
        }
        for(var entry:bounds.entrySet())assertTrue(errors.get(entry.getKey())<=entry.getValue(),
                entry.getKey()+" error "+errors.get(entry.getKey()));
    }

    private static double tripTime(List<Map<String,Double>> rows) {
        for(int i=1;i<rows.size();i++) {
            double t=rows.get(i).get("time_s");
            if(t>.21 && t<.249 && t==rows.get(i-1).get("time_s"))return t;
        }
        fail("Missing physical-action event-side duplicate");return Double.NaN;
    }
    static List<Map<String,Double>> csv(byte[] bytes) {
        String[] lines=new String(bytes,StandardCharsets.UTF_8).strip().split("\\R"),keys=lines[0].split(",");
        var rows=new ArrayList<Map<String,Double>>();
        for(int i=1;i<lines.length;i++) {
            String[] fields=lines[i].split(",");assertEquals(keys.length,fields.length);
            var row=new LinkedHashMap<String,Double>();
            for(int k=0;k<keys.length;k++)if(!keys[k].equals("sample_kind")) {
                double value=Double.parseDouble(fields[k]);assertTrue(Double.isFinite(value));row.put(keys[k],value);
            }
            rows.add(row);
        }
        return rows;
    }
}
