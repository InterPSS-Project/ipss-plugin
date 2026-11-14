package org.interpss.core.dstab;

import static org.junit.jupiter.api.Assertions.*;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.Base64;
import java.util.HexFormat;
import java.util.stream.Stream;
import java.util.zip.GZIPInputStream;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.interpss.core.dstab.reference.EmbeddedTrajectoryReference;
import org.interpss.core.dstab.reference.PowerWorldCsvRepeatability;
import com.interpss.dstab.controller.oel.oel4c.Oel4cData;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

/** Whole-reference integrity and channel identities, not PI/timer or coupled acceptance. */
public class Oel4cPowerWorldArtifactConformanceTest {
    private static final String BASE="/oel4c-powerworld-reference/";
    record Profile(String name,double step,int samples,String rawSha,String canonicalSha,String profile) { }
    @TempDir Path directory;
    static byte[] resource(String name)throws Exception {
        try(var input=Oel4cPowerWorldArtifactConformanceTest.class.getResourceAsStream(BASE+name)){
            assertNotNull(input,name);return input.readAllBytes();
        }
    }
    static JsonObject index()throws Exception {
        return JsonParser.parseString(new String(resource("index.json"),StandardCharsets.UTF_8)).getAsJsonObject();
    }
    static Stream<Profile> profiles()throws Exception {
        return new String(resource("profiles.tsv"),StandardCharsets.UTF_8).lines().skip(1).map(line->{
            String[] fields=line.split("\t");assertEquals(6,fields.length);
            return new Profile(fields[0],Double.parseDouble(fields[1]),Integer.parseInt(fields[2]),fields[3],fields[4],fields[5]);
        });
    }
    static JsonObject profile(Profile profile)throws Exception {
        JsonObject found=null;
        for(var item:index().getAsJsonArray("profiles"))if(item.getAsJsonObject().get("name").getAsString().equals(profile.name())){
            assertNull(found);found=item.getAsJsonObject();
        }
        assertNotNull(found);assertEquals(profile.rawSha(),found.get("raw_sha256").getAsString());return found;
    }
    static Oel4cData data(Profile profile)throws Exception {
        var records=profile(profile).getAsJsonObject("readback");
        var p=records.getAsJsonObject("initialized").getAsJsonObject("limiter");
        assertEquals(records.getAsJsonObject("power_flow_solved").getAsJsonObject("limiter"),p);
        return new Oel4cData(p.get("Qref").getAsDouble(),p.get("TDelay").getAsDouble(),p.get("Kp").getAsDouble(),
            p.get("Ki").getAsDouble(),p.get("Vmin").getAsDouble());
    }
    static EmbeddedTrajectoryReference load(Profile profile,Path directory)throws Exception {
        byte[] body;
        try(var input=new GZIPInputStream(new ByteArrayInputStream(Base64.getMimeDecoder().decode(resource(profile.name()+".csv.gz.b64"))))){body=input.readAllBytes();}
        assertEquals(profile.rawSha(),HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(body)));
        Path file=directory.resolve(profile.name()+".csv");Files.write(file,body);
        return PowerWorldCsvRepeatability.trajectory(file,profile.canonicalSha());
    }
    static int field(EmbeddedTrajectoryReference r,String name){return r.fieldIndex("Generator","1 1",name);}
    @ParameterizedTest @MethodSource("profiles")
    void retainsEveryOriginalRowAndDistinguishesOutputModeTimerAndControlledError(Profile profile)throws Exception {
        var r=load(profile,directory);var data=data(profile);var metadata=index();
        assertEquals(21,r.fields().size());assertEquals(profile.samples(),r.samples().size());
        assertEquals(100,metadata.get("machine_base_mva").getAsDouble());
        double pickup=data.effectivePickup(metadata.get("reactive_maximum_pu").getAsDouble());
        int q=field(r,"TSMvar"),output=field(r,"TSVOEL"),state=field(r,"TSOELState:1"),
            mode=field(r,"TSOELOther:1"),timer=field(r,"TSOELOther:2"),controlled=field(r,"TSOELOther:3");
        int duplicates=0;double maximum=0,previous=-1;
        for(var sample:r.samples()){
            assertTrue(sample.time()>=previous);
            if(sample.time()==previous){duplicates++;assertEquals(1,sample.occurrence());assertTrue(sample.time()==.05||sample.time()==.1);}
            previous=sample.time();for(double value:sample.values())assertTrue(Double.isFinite(value));
            assertEquals(sample.value(output),sample.value(state));
            assertTrue(sample.value(output)>=data.minimumOutput()-1e-6&&sample.value(output)<=1e-6);
            assertTrue(sample.value(timer)>=0);assertTrue(sample.value(mode)==0||sample.value(mode)==1||sample.value(mode)==2);
            double error=pickup-sample.value(q)/100;
            double expected=error>=0||sample.value(mode)!=0||data.delay()==0?error:0;
            maximum=Math.max(maximum,Math.abs(expected-sample.value(controlled)));
        }
        assertEquals(profile.profile().equals("flat")?0:2,duplicates);
        assertEquals(0,r.samples().getFirst().time());assertEquals(.5,previous);
        assertEquals(Math.round(.5/profile.step())+(profile.profile().equals("flat")?1:3),profile.samples());
        assertTrue(maximum<1e-6,"Independent serialized-channel identity residual="+maximum);
    }
}
