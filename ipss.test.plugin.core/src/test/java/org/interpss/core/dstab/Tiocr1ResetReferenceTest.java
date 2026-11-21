package org.interpss.core.dstab;

import static org.junit.jupiter.api.Assertions.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.security.MessageDigest;
import java.util.*;
import java.util.zip.ZipFile;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import com.interpss.dstab.relay.model.Tiocr1Data;

/** Complete independent reset/re-pickup boundaries and accepted protection milestones. */
public class Tiocr1ResetReferenceTest {
    private static final Path ARCHIVE=Path.of("testData/private/verification-artifacts/tiocr-reset-20261101/captures.zip");
    private static final Path ALTERNATE=Path.of("testData/private/verification-artifacts/tiocr-reset03-20261105/captures.zip");
    private static final String ALTERNATE_SHA="301eb34c56e7fbd735ab2160235e2748b11b879591ccb7d15c820d953024a57d";
    private static final Path LONG_RESET=Path.of("testData/private/verification-artifacts/tiocr-reset20-20261105/captures.zip");
    private static final String LONG_SHA="8974e0f1a6204bfb010b4211d9be3a930f8cd00ae6f8e015e76416bbde3863f7";

    @Test void longResetSourceCarriesMovingElementIntoSecondFault() throws Exception {
        Assumptions.assumeTrue(Files.isRegularFile(LONG_RESET));
        assertEquals(LONG_SHA,HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                .digest(Files.readAllBytes(LONG_RESET))));
        try(var zip=new ZipFile(LONG_RESET.toFile())) {
            var first=csv(zip.getInputStream(zip.getEntry("a/native.csv")).readAllBytes());
            var repeat=csv(zip.getInputStream(zip.getEntry("b/native.csv")).readAllBytes());
            assertEquals(1008,first.size());assertEquals(first,repeat);
            var active=first.stream().filter(row->row.get("time_s")>=0).toList();
            assertEquals(.2185,active.stream().filter(row->row.get("time_s")>=.2 && row.get("VAR1")>=1)
                    .findFirst().orElseThrow().get("time_s"),.00051);
            assertEquals(.2285,tripTime(active),.00051);
            var recovery=active.stream().filter(row->row.get("time_s")>=.09 && row.get("time_s")<.19)
                    .mapToDouble(row->row.get("VAR1")).toArray();
            assertTrue(recovery.length>100);
            assertTrue(Arrays.stream(recovery).min().orElseThrow()>.07);
            assertTrue(Arrays.stream(recovery).max().orElseThrow()<.4);
            // The source advances the moving element on the right side of both
            // later network events using the newly solved terminal current.
            var curve=new Tiocr1Data(new double[]{.4,.2,1.1,.04,1.5,.03,2,.02,3,.01,5,.005,.01,.1});
            for(double eventTime:new double[]{.06,.20}) {
                int right=eventRightIndex(active,eventTime);
                double measured=active.get(right).get("VAR1")-active.get(right-1).get("VAR1");
                double expected=.0005*curve.rate(active.get(right).get("VAR0"));
                assertEquals(expected,measured,2e-8,"Event-right relay recurrence at "+eventTime);
            }
            int firstFaultRight=eventRightIndex(active,.05);
            assertEquals(0.,active.get(firstFaultRight).get("VAR1")
                    -active.get(firstFaultRight-1).get("VAR1"),1e-12);
        }
    }

    private static int eventRightIndex(List<Map<String,Double>> rows,double targetTime) {
        int found=-1;
        for(int i=1;i<rows.size();i++)
            if(rows.get(i).get("time_s").equals(rows.get(i-1).get("time_s"))
                    &&Math.abs(rows.get(i).get("time_s")-targetTime)<5e-6) {
                assertEquals(-1,found,"One event-side pair at "+targetTime);found=i;
            }
        assertTrue(found>0,"Event-side pair exists at "+targetTime);
        return found;
    }

    @ParameterizedTest @CsvSource({"false","true"})
    void alternateResetRetainsCompleteTripTrajectory(boolean staggered,@TempDir Path staged) throws Exception {
        Assumptions.assumeTrue(Files.isRegularFile(ALTERNATE));
        assertEquals(ALTERNATE_SHA,HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                .digest(Files.readAllBytes(ALTERNATE))));
        List<Map<String,Double>> reference;
        try(var zip=new ZipFile(ALTERNATE.toFile())) {
            var first=csv(zip.getInputStream(zip.getEntry("a/native.csv")).readAllBytes());
            var repeat=csv(zip.getInputStream(zip.getEntry("b/native.csv")).readAllBytes());
            assertEquals(1008,first.size());assertEquals(first,repeat);
            reference=first.stream().filter(row->row.get("time_s")>=0).toList();
            Files.write(staged.resolve("network.raw"),zip.getInputStream(zip.getEntry("a/network.raw")).readAllBytes());
            Files.write(staged.resolve("dynamic.dyr"),zip.getInputStream(zip.getEntry("a/dynamic.dyr")).readAllBytes());
        }
        Path output=staged.resolve("actual");
        HspUsageRankedModelTest.run("tiocr1-1",false,staggered,true,staged.resolve("network.raw"),
                staged.resolve("dynamic.dyr"),output);
        var actual=csv(Files.readAllBytes(output.resolve("tiocr1-1-reset"
                +(staggered?"-staggered":"")+"-fault.csv")));
        assertEquals(reference.size(),actual.size());
        assertEquals(tripTime(reference),tripTime(actual),.00051);
        assertEquals(.23,tripTime(reference),.00051);
        assertEquals(0,actual.getLast().get("RELAY.Position"));
        for(var rows:List.of(reference,actual)) {
            String position=rows==reference?"VAR1":"RELAY.Position";
            assertTrue(rows.stream().filter(row->row.get("time_s")>=.05 && row.get("time_s")<.09)
                    .mapToDouble(row->row.get(position)).max().orElseThrow()>.4);
            var recovery=rows.stream().filter(row->row.get("time_s")>=.09 && row.get("time_s")<.19)
                    .mapToDouble(row->row.get(position)).toArray();
            assertTrue(recovery.length>100);
            assertTrue(Arrays.stream(recovery).allMatch(value->value==0));
            assertTrue(rows.stream().filter(row->row.get("time_s")>=.2 && row.get("time_s")<.229)
                    .mapToDouble(row->row.get(position)).max().orElseThrow()>=1);
        }
        for(String channel:List.of("V1","V2","P_PU","Q_PU","ANGLE_DEG")) {
            double sum=0,min=Double.POSITIVE_INFINITY,max=Double.NEGATIVE_INFINITY;
            for(int i=0;i<reference.size();i++) {
                var expected=reference.get(i);var observed=actual.get(i);
                assertEquals(expected.get("time_s"),observed.get("time_s"),5e-6);
                double value=expected.get(channel),error=observed.get(channel)-value;
                sum+=error*error;min=Math.min(min,value);max=Math.max(max,value);
            }
            double rmse=Math.sqrt(sum/reference.size()),nrmse=rmse/(max-min);
            System.out.printf("TIOCR_RESET03 %s %s rmse=%.12g nrmse=%.12g%n",
                    staggered,channel,rmse,nrmse);
            if(channel.equals("ANGLE_DEG"))assertTrue(rmse<.01,channel+" RMSE="+rmse);
            else assertTrue(nrmse<.001,channel+" NRMSE="+nrmse);
        }
    }

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
