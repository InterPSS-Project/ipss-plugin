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
import org.interpss.core.dstab.reference.PowerWorldCsvRepeatability;
import org.interpss.core.dstab.reference.EmbeddedTrajectoryReference;
import com.interpss.dstab.controller.oel.basoel2.Basoel2Data;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

/** Frozen whole-reference integrity and algebraic identity, not coupled trajectory acceptance. */
public class Basoel2PowerWorldArtifactConformanceTest {
    private static final String BASE="/basoel2-powerworld-reference/";
    record Profile(String name,double step,int samples,String rawSha,String canonicalSha,int oelFlag) { }
    @TempDir Path directory;
    private static byte[] resource(String name)throws Exception{
        try(var input=Basoel2PowerWorldArtifactConformanceTest.class.getResourceAsStream(BASE+name)){
            assertNotNull(input,name);return input.readAllBytes();
        }
    }
    static Stream<Profile> profiles()throws Exception{
        return new String(resource("profiles.tsv"),StandardCharsets.UTF_8).lines().skip(1).map(line->{
            String[] fields=line.split("\t");assertEquals(6,fields.length);
            return new Profile(fields[0],Double.parseDouble(fields[1]),Integer.parseInt(fields[2]),fields[3],fields[4],Integer.parseInt(fields[5]));
        });
    }
    static JsonObject readback(Profile profile,String model)throws Exception{
        var profiles=JsonParser.parseString(new String(resource("index.json"),StandardCharsets.UTF_8)).getAsJsonObject().getAsJsonArray("profiles");
        JsonObject found=null;
        for(var item:profiles)if(item.getAsJsonObject().get("name").getAsString().equals(profile.name())){
            assertNull(found,"Duplicate fixture profile");found=item.getAsJsonObject();
        }
        assertNotNull(found);assertEquals(profile.rawSha(),found.get("raw_sha256").getAsString());
        return found.getAsJsonObject("model_readback").getAsJsonObject("records").getAsJsonObject("initialized").getAsJsonObject(model);
    }
    static Basoel2Data data(Profile profile)throws Exception{
        var p=readback(profile,"limiter");
        return new Basoel2Data(p.get("OELFlag").getAsInt(),p.get("VHzFlag").getAsInt(),p.get("OELIn").getAsInt(),
            p.get("TRoel").getAsDouble(),p.get("Kp").getAsDouble(),p.get("Ki").getAsDouble(),
            p.get("OEL1").getAsDouble(),p.get("OET1").getAsDouble(),p.get("OEL2").getAsDouble(),p.get("OET2").getAsDouble(),
            p.get("OEL3").getAsDouble(),p.get("OET3").getAsDouble(),p.get("TRVHz").getAsDouble(),p.get("KVHz").getAsDouble(),p.get("VHzmin").getAsDouble());
    }
    static EmbeddedTrajectoryReference load(Profile profile,Path directory)throws Exception{
        byte[] body;
        try(var input=new GZIPInputStream(new ByteArrayInputStream(Base64.getMimeDecoder().decode(resource(profile.name()+".csv.gz.b64"))))){body=input.readAllBytes();}
        assertEquals(profile.rawSha(),HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(body)));
        Path path=directory.resolve(profile.name()+".csv");Files.write(path,body);
        return PowerWorldCsvRepeatability.trajectory(path,profile.canonicalSha());
    }
    @ParameterizedTest @MethodSource("profiles")
    void retainsEveryRawFieldAndEventSideWithoutFittedReferenceStates(Profile profile)throws Exception{
        var reference=load(profile,directory);
        assertEquals(31,reference.fields().size());assertEquals(profile.samples(),reference.samples().size());
        int sensed=reference.fieldIndex("Generator","1 1","TSOELState:1"),integral=reference.fieldIndex("Generator","1 1","TSOELState:2");
        int target=reference.fieldIndex("Generator","1 1","TSOELOther:1"),output=reference.fieldIndex("Generator","1 1","TSOELOther:2");
        int vt=reference.fieldIndex("Generator","1 1","TSExciterState:2"),va=reference.fieldIndex("Generator","1 1","TSExciterState:3");
        double vref=reference.samples().getFirst().value(vt)+reference.samples().getFirst().value(va)/10.;
        int duplicates=0;double maximum=0,previous=-1;
        for(var sample:reference.samples()){
            assertTrue(sample.time()>=previous);if(sample.time()==previous){duplicates++;assertTrue(sample.time()==.05||sample.time()==.1);}
            previous=sample.time();for(double value:sample.values())assertTrue(Double.isFinite(value));
            boolean gate=false;
            for(int timer=0;timer<3;timer++){
                double active=sample.value(reference.fieldIndex("Generator","1 1","TSOELOther:"+(4+timer*3)));
                double above=sample.value(reference.fieldIndex("Generator","1 1","TSOELOther:"+(5+timer*3)));
                assertTrue(active==0||active==1);assertTrue(above==0||above==1);gate|=active==1&&above==1;
            }
            double pi=Math.max(0,Math.min(vref,.15*(sample.value(sensed)-sample.value(target))+sample.value(integral)));
            double expected=profile.oelFlag()!=0&&gate?-pi:0;
            maximum=Math.max(maximum,Math.abs(expected-sample.value(output)));
        }
        assertEquals(2,duplicates);assertEquals(0,reference.samples().getFirst().time());assertEquals(.5,previous);
        assertEquals(Math.round(.5/profile.step())+3,profile.samples());
        // Independent serialized-channel bound, not the coupled-trajectory RMSE allowance.
        assertTrue(maximum<1e-6,"Published output identity residual="+maximum);
        System.out.println("BASOEL2 full reference "+profile.name()+" rows="+profile.samples()+" algebraicMaximum="+maximum);
    }
}
