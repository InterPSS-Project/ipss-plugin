package org.interpss.core.dstab;

import static org.junit.jupiter.api.Assertions.*;
import java.nio.file.*;
import java.security.MessageDigest;
import java.util.*;
import java.util.zip.ZipFile;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

/** Two distinct transfer targets and a duplicated target retain one accepted network/load action. */
public class Tiocr1MultipleTransferReferenceTest {
    private static final Path ARCHIVE=Path.of("testData/private/verification-artifacts/tiocr-multiple-transfer-20261104/captures.zip");
    @ParameterizedTest @CsvSource({"false,1_2","true,1_2","false,1_2_1","true,1_2_1"})
    void completeMultipleTransfer(boolean staggered,String terminal,@TempDir Path staged)throws Exception {
        Assumptions.assumeTrue(Files.isRegularFile(ARCHIVE),"Retain paired independent action references");
        assertEquals("05760ac11b534f31068cd01d5ad7d71c75a53add35c19b24d54537b747c7b483",
                HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(ARCHIVE))));
        List<Map<String,Double>> reference;
        try(var zip=new ZipFile(ARCHIVE.toFile())) {
            byte[] first=zip.getInputStream(zip.getEntry(terminal+"/a/native.csv")).readAllBytes();
            assertArrayEquals(first,zip.getInputStream(zip.getEntry(terminal+"/b/native.csv")).readAllBytes());
            reference=Tiocr1ResetReferenceTest.csv(first).stream().filter(r->r.get("time_s")>=0).toList();
            for(String file:List.of("network.raw","dynamic.dyr"))
                Files.write(staged.resolve(file),zip.getInputStream(zip.getEntry(terminal+"/a/"+file)).readAllBytes());
        }
        HspUsageRankedModelTest.setup();
        Path out=Files.createTempDirectory(Path.of("target"),"tiocr-multiple-"+terminal+"-");
        HspUsageRankedModelTest.run("tiocr1-action",false,staggered,false,staged.resolve("network.raw"),staged.resolve("dynamic.dyr"),out);
        var actual=Tiocr1ResetReferenceTest.csv(Files.readAllBytes(out.resolve("tiocr1-action"+(staggered?"-staggered":"")+"-fault.csv")));
        assertEquals(reference.size(),actual.size(),"All grid rows and event-side duplicates");
        double positionError=0,rawCurrentDifference=0;int action=-1,duplicates=0;
        for(int i=0;i<reference.size();i++) {
            var r=reference.get(i);var a=actual.get(i);
            assertEquals(r.get("time_s"),a.get("time_s"),5e-6);
            positionError=Math.max(positionError,Math.abs(a.get("RELAY.Position")-r.get("VAR1")));
            rawCurrentDifference=Math.max(rawCurrentDifference,Math.abs(a.get("RELAY.Current")-r.get("VAR0")));
            if(i>0&&r.get("time_s").equals(reference.get(i-1).get("time_s"))) {
                duplicates++;
                if(r.get("VAR1")==0.&&reference.get(i-1).get("VAR1")==1.)action=i;
            }
        }
        assertEquals(3,duplicates,"Fault application, relay trip, fault clearing");assertTrue(action>0);
        var left=actual.get(action-1);var right=actual.get(action);
        assertEquals(1.,left.get("RELAY.Position"));assertTrue(left.get("RELAY.Current")>.4);
        assertEquals(1.,left.get("RELAY.Operated"),"Internal operation latch precedes physical commit");
        assertEquals(0.,left.get("RELAY.Revision"),"Physical revision remains pre-action");
        assertEquals(0.,right.get("RELAY.Position"));assertEquals(0.,right.get("RELAY.Current"));
        assertEquals(1.,right.get("RELAY.Operated"));assertEquals(.1,right.get("RELAY.ShedFraction"),1e-12);
        for(String circuit:List.of("1","2")){assertEquals(1.,left.get("BRANCH.Bus1->Bus2("+circuit+")"));assertEquals(0.,right.get("BRANCH.Bus1->Bus2("+circuit+")"));}
        assertEquals(1.,left.get("BRANCH.Bus1->Bus2(3)"));assertEquals(1.,right.get("BRANCH.Bus1->Bus2(3)"));
        for(int i=action;i<actual.size();i++) {
            assertEquals(0.,actual.get(i).get("RELAY.Current"),"Opened physical branch carries no current");
            assertEquals(1.,actual.get(i).get("RELAY.Revision"),"Transfer and shedding commit once");
            assertEquals(.1,actual.get(i).get("RELAY.ShedFraction"),1e-12);
            assertEquals(0.,actual.get(i).get("BRANCH.Bus1->Bus2(1)"));assertEquals(0.,actual.get(i).get("BRANCH.Bus1->Bus2(2)"));
            assertEquals(1.,actual.get(i).get("BRANCH.Bus1->Bus2(3)"));
        }
        assertTrue(positionError<(staggered?3.3e-7:2.2e-5),"Unchanged terminal position envelope: "+positionError);
        // Diagnostic memory is not the physical elapsed timer/current. Retain and expose this distinction.
        assertEquals(9999.,reference.get(0).get("VAR2"));
        assertEquals(reference.get(action-1).get("time_s"),reference.get(action-1).get("VAR2"),5e-6);
        assertEquals(0.,reference.get(action).get("VAR2"));assertTrue(rawCurrentDifference>1.);
        var errors=new LinkedHashMap<String,Double>();
        for(String channel:List.of("V1","V2","P_PU","Q_PU","ANGLE_DEG")) {
            double sum=0,min=Double.POSITIVE_INFINITY,max=Double.NEGATIVE_INFINITY;
            for(int i=0;i<reference.size();i++) {
                double value=reference.get(i).get(channel),delta=actual.get(i).get(channel)-value;
                sum+=delta*delta;min=Math.min(min,value);max=Math.max(max,value);
            }
            double rmse=Math.sqrt(sum/reference.size());
            double error=channel.equals("ANGLE_DEG")?rmse:rmse/(max-min);errors.put(channel,error);
            assertTrue(error<(channel.equals("ANGLE_DEG")?5e-5:staggered?8e-6:5e-5),channel+" error="+error);
        }
        System.out.println("TIOCR physical terminal="+terminal+" staggered="+staggered+" errors="+errors+" position="+positionError);
    }
}
