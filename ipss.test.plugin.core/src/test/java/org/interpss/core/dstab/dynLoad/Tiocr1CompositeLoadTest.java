package org.interpss.core.dstab.dynLoad;

import static org.junit.jupiter.api.Assertions.*;
import java.nio.file.*;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.*;
import java.util.zip.ZipFile;
import org.interpss.IpssCorePlugin;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

/** Complete relay-driven composite demand and equivalent-network comparisons. */
public class Tiocr1CompositeLoadTest {
    private static final Path ARCHIVE=Path.of("testData/private/verification-artifacts/cmldblu1-shedding-20261104/captures.zip");

    @ParameterizedTest @CsvSource({"false,0.0","true,0.0","false,0.25","true,0.25","false,1.0","true,1.0"})
    void completeCompositeSheddingTrajectory(boolean staggered,String fraction,@TempDir Path temporary)throws Exception {
        Path archive=fraction.equals("1.0")?ARCHIVE.resolveSibling("fulltrip.zip"):ARCHIVE;
        Assumptions.assumeTrue(Files.isRegularFile(archive),"Retain independent composite shedding captures");
        assertEquals(fraction.equals("1.0")?"dada624db14a401ddda2fa66c6fa9b6f5b320df013c95fa9fd3579bc8e0761b6"
                        :"09a82dd77febe6dceb050ef837b24da9f82c3def9a80d5ac726d947a22feadf9",
                HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(archive))));
        List<Map<String,Double>> reference;
        String prefix="target/cmld-shedding-"+fraction+"-20261104/cmldblu1-restart-fault-h0.0005-";
        Path dynamic=temporary.resolve("dynamic.dyr");
        try(var zip=new ZipFile(archive.toFile())) {
            byte[] raw=zip.getInputStream(zip.getEntry(prefix+"a-part0/native.csv")).readAllBytes();
            assertArrayEquals(raw,zip.getInputStream(zip.getEntry(prefix+"b-part0/native.csv")).readAllBytes());
            reference=csv(raw).stream().filter(r->r.get("time_s")>=0).toList();
            Files.write(dynamic,zip.getInputStream(zip.getEntry(prefix+"a-part0/dynamic.dyr")).readAllBytes());
            assertArrayEquals(Files.readAllBytes(Cmldblu1SharedSmibTest.CASE.resolve("load-network.raw")),
                    zip.getInputStream(zip.getEntry(prefix+"a-part0/network.raw")).readAllBytes());
        }
        IpssCorePlugin.init();IpssCorePlugin.configureSparseSolver("java-klu");
        double end=Math.round(reference.getLast().get("time_s")/.0005)*.0005;
        var recording=Cmldblu1SharedRecording.simulateStaticControl(dynamic,staggered,.0005,.2,0,end);
        Path output=Files.createTempDirectory(Path.of("target"),"tiocr-composite-");
        recording.write(output.resolve("complete.csv.gz"));
        assertEquals(reference.size(),recording.frames.size(),"Every grid row and both event sides");
        var maxima=new TreeMap<String,Double>();var squares=new TreeMap<String,Double>();
        for(String key:List.of("V1","V2","V3","Low.V","Load.PreparedV","P_PU","Q_PU","ANGLE_DEG")) {maxima.put(key,0.);squares.put(key,0.);}
        double positionError=0.;int duplicates=0;
        for(int i=0;i<reference.size();i++) {
            var r=reference.get(i);var a=recording.frames.get(i);
            assertEquals(r.get("time_s"),a.time(),5e-6);
            if(i>0&&r.get("time_s").equals(reference.get(i-1).get("time_s")))duplicates++;
            for(String key:maxima.keySet()) {
                double error=a.values().get(key)-r.get(key);
                maxima.put(key,Math.max(maxima.get(key),Math.abs(error)));
                squares.put(key,squares.get(key)+error*error);
            }
            positionError=Math.max(positionError,Math.abs(a.values().get("RELAY.Position")-r.get("RELAY_VAR1")));
            if (fraction.equals("0.25") && a.kind().equals("RELAY_EVENT_PRE"))
                assertTrue(Math.abs(a.values().get("Load.V")-a.values().get("Load.PreparedV"))>.005,
                        "Retain the physical voltage separately from the updated-parameter diagnostic");
        }
        assertEquals(3,duplicates,"Fault application, relay operation and fault clearing");
        assertEquals(1.-Double.parseDouble(fraction),recording.model.getRemainingCapacity(),1e-12);
        var normalized=new TreeMap<String,Double>();
        for(String key:List.of("V1","V2","Low.V","Load.PreparedV","P_PU","Q_PU")) {
            double range=reference.stream().mapToDouble(r->r.get(key)).max().orElseThrow()
                    -reference.stream().mapToDouble(r->r.get(key)).min().orElseThrow();
            normalized.put(key,Math.sqrt(squares.get(key)/reference.size())/range);
        }
        System.out.println("TIOCR composite staggered="+staggered+" fraction="+fraction+" peaks="+maxima
                +" NRMSE="+normalized+" position="+positionError+" recording="+output.getFileName());
        for(var entry:normalized.entrySet())assertTrue(entry.getValue()<.001,entry.toString());
        for(var entry:normalized.entrySet())assertTrue(entry.getValue()<(staggered?8e-6:5e-5),
                "Qualified regression envelope: "+entry);
        assertTrue(Math.sqrt(squares.get("ANGLE_DEG")/reference.size())<.01,"Rotor angle RMSE");
        assertTrue(maxima.get("ANGLE_DEG")<6e-5,"Qualified rotor-angle peak envelope");
        assertTrue(maxima.get("V3")<1e-5,"Constant-voltage boundary");
        assertTrue(positionError<(staggered?5e-7:5e-5),"Moving-element position");
    }

    private static List<Map<String,Double>> csv(byte[] raw) {
        String[] lines=new String(raw,StandardCharsets.UTF_8).strip().split("\\R"),keys=lines[0].split(",");
        var result=new ArrayList<Map<String,Double>>();
        for(int i=1;i<lines.length;i++) {
            String[] fields=lines[i].split(",");assertEquals(keys.length,fields.length);var row=new LinkedHashMap<String,Double>();
            for(int j=0;j<keys.length;j++) {double value=Double.parseDouble(fields[j]);assertTrue(Double.isFinite(value));row.put(keys[j],value);}
            row.put("Low.V",row.get("VAR30"));row.put("Load.PreparedV",row.get("VAR29"));
            result.add(row);
        }
        return result;
    }
}
