package org.interpss.core.dstab;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.zip.ZipFile;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/** A source machine and passive side bus remain one island after relay transfer. */
public class Tiocr1SeparatedIslandReferenceTest {
    private static final Path ARCHIVE=Path.of("testData/private/verification-artifacts/tiocr-separated-island-20261105/captures.zip");
    private static final String SHA="2a67415347de5e6b6f05a8a971c4c0d6febba076e31049d933b2a0301defb838";
    private static final Path QUARTER_ARCHIVE=Path.of("testData/private/verification-artifacts/tiocr-separated-quarter-20261105/captures.zip");
    private static final String QUARTER_SHA="5c0a8702fb1aa046dd6d8a1f435069dcfb56ad29b6110f94a82fe311d61baef2";
    private static final Path DELAY_ARCHIVE=Path.of("testData/private/verification-artifacts/tiocr-quarter-delay-20261105/captures.zip");
    private static final String DELAY_SHA="daaaca846107779000611e6d6bf7805bfb736d7ea45ac0762b44fe41a2ff03dd";

    @ParameterizedTest @ValueSource(booleans={false,true})
    void completeSeparatedIslandTrajectory(boolean staggered,@TempDir Path staged)throws Exception {
        compare(staggered,staged,ARCHIVE,SHA,.0005);
    }
    @ParameterizedTest @ValueSource(booleans={false,true})
    void quarterCycleSeparatedIslandTrajectory(boolean staggered,@TempDir Path staged)throws Exception {
        compare(staggered,staged,QUARTER_ARCHIVE,QUARTER_SHA,1./240.);
    }
    @ParameterizedTest @ValueSource(booleans={false,true})
    void quarterCycleBreakerDelaySelectsTheNearestPostPickupEndpoint(boolean staggered,
            @TempDir Path staged)throws Exception {
        Assumptions.assumeTrue(Files.isRegularFile(DELAY_ARCHIVE));
        assertEquals(DELAY_SHA,HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                .digest(Files.readAllBytes(DELAY_ARCHIVE))));
        var expectedTimes=Map.of("0p001",.08333333333333333,"0p009",.0875,
                "0p011",.09166666666666666,"0p015",.09583333333333334);
        try(var zip=new ZipFile(DELAY_ARCHIVE.toFile())) {
            for(var entry:expectedTimes.entrySet()) {
                String tag=entry.getKey();
                byte[] first=zip.getInputStream(zip.getEntry(tag+"/a/native.csv")).readAllBytes();
                assertArrayEquals(first,zip.getInputStream(zip.getEntry(tag+"/b/native.csv")).readAllBytes());
                var source=Tiocr1ResetReferenceTest.csv(first).stream().filter(r->r.get("time_s")>=0).toList();
                var folder=Files.createDirectory(staged.resolve(tag));
                for(String file:List.of("network.raw","dynamic.dyr")) {
                    byte[] input=zip.getInputStream(zip.getEntry(tag+"/a/"+file)).readAllBytes();
                    assertArrayEquals(input,zip.getInputStream(zip.getEntry(tag+"/b/"+file)).readAllBytes());
                    Files.write(folder.resolve(file),input);
                }
                HspUsageRankedModelTest.setup();
                Path output=Files.createDirectory(folder.resolve("output-"+(staggered?"staggered":"euler")));
                HspUsageRankedModelTest.run("tiocr1-action",false,staggered,false,
                        folder.resolve("network.raw"),folder.resolve("dynamic.dyr"),output,.2,"Bus1",1./240.);
                var actual=Tiocr1ResetReferenceTest.csv(Files.readAllBytes(output.resolve("tiocr1-action"
                        +(staggered?"-staggered":"")+"-fault.csv")));
                assertEquals(source.size(),actual.size(),"Complete event grid for "+tag);
                int referenceAction=actionIndex(source,"VAR1"),actualAction=actionIndex(actual,"RELAY.Position");
                assertEquals(referenceAction,actualAction,"Breaker operation row for "+tag);
                assertEquals(entry.getValue(),source.get(referenceAction).get("time_s"),5e-6);
                assertEquals(source.get(referenceAction).get("time_s"),
                        actual.get(actualAction).get("time_s"),5e-6);
            }
        }
    }
    private static int actionIndex(List<Map<String,Double>> rows,String position) {
        int action=-1;
        for(int i=1;i<rows.size();i++)
            if(rows.get(i).get("time_s").equals(rows.get(i-1).get("time_s"))
                    &&rows.get(i-1).get(position)==1.&&rows.get(i).get(position)==0.) {
                assertEquals(-1,action,"Only one accepted relay action");action=i;
            }
        assertTrue(action>=0,"One accepted relay action required");
        return action;
    }
    private static void compare(boolean staggered,Path staged,Path archive,String sha,double step)throws Exception {
        Assumptions.assumeTrue(Files.isRegularFile(archive));
        assertEquals(sha,HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                .digest(Files.readAllBytes(archive))));
        List<Map<String,Double>> reference;
        try(var zip=new ZipFile(archive.toFile())) {
            byte[] first=zip.getInputStream(zip.getEntry("a/native.csv")).readAllBytes();
            assertArrayEquals(first,zip.getInputStream(zip.getEntry("b/native.csv")).readAllBytes());
            reference=Tiocr1ResetReferenceTest.csv(first).stream().filter(r->r.get("time_s")>=0).toList();
            for(String file:List.of("network.raw","dynamic.dyr")) {
                byte[] source=zip.getInputStream(zip.getEntry("a/"+file)).readAllBytes();
                assertArrayEquals(source,zip.getInputStream(zip.getEntry("b/"+file)).readAllBytes());
                Files.write(staged.resolve(file),source);
            }
        }
        HspUsageRankedModelTest.setup();
        Path output=Files.createDirectory(staged.resolve("output"));
        HspUsageRankedModelTest.run("tiocr1-action",false,staggered,false,
                staged.resolve("network.raw"),staged.resolve("dynamic.dyr"),output,.2,"Bus1",step);
        var actual=Tiocr1ResetReferenceTest.csv(Files.readAllBytes(output.resolve("tiocr1-action"
                +(staggered?"-staggered":"")+"-fault.csv")));
        assertEquals(reference.size(),actual.size(),"Complete grid and every event-side duplicate");
        int action=-1,duplicates=0;
        for(int i=0;i<actual.size();i++) {
            var expected=reference.get(i);var observed=actual.get(i);
            assertEquals(expected.get("time_s"),observed.get("time_s"),5e-6);
            if(i>0&&expected.get("time_s").equals(reference.get(i-1).get("time_s"))) {
                duplicates++;
                if(expected.get("VAR1")==0.&&reference.get(i-1).get("VAR1")==1.)action=i;
            }
        }
        assertEquals(3,duplicates);assertTrue(action>0);
        var sourceRight=reference.get(action);var actualRight=actual.get(action);
        assertTrue(sourceRight.get("V1")>.5&&sourceRight.get("V3")>.5);
        assertEquals(0.,sourceRight.get("V2"),1e-12);
        assertTrue(actualRight.get("V1")>.5&&actualRight.get("V3")>.5);
        assertEquals(0.,actualRight.get("V2"),1e-8);
        assertEquals(sourceRight.get("V1"),actualRight.get("V1"),.001);
        assertEquals(sourceRight.get("V3"),actualRight.get("V3"),.001);
        assertEquals(actualRight.get("V1"),actualRight.get("V3"),1e-8,
                "The passive side bus shares its source island voltage");
        for(int i=action;i<actual.size();i++) {
            var row=actual.get(i);
            assertEquals(0.,row.get("V2"),1e-8);
            assertEquals(1.,row.get("BRANCH.Bus1->Bus3(1)"));
            for(String circuit:List.of("1","2","3"))
                assertEquals(0.,row.get("BRANCH.Bus1->Bus2("+circuit+")"));
        }
        for(String channel:List.of("V1","V2","V3","P_PU","Q_PU","ANGLE_DEG")) {
            double sum=0,min=Double.POSITIVE_INFINITY,max=Double.NEGATIVE_INFINITY;
            for(int i=0;i<reference.size();i++) {
                double value=reference.get(i).get(channel);
                double delta=actual.get(i).get(channel)-value;
                sum+=delta*delta;min=Math.min(min,value);max=Math.max(max,value);
            }
            double rmse=Math.sqrt(sum/reference.size());
            double error=channel.equals("ANGLE_DEG")?rmse:rmse/(max-min);
            double limit=step>.001?(channel.equals("ANGLE_DEG")?.01:.001)
                    :channel.equals("ANGLE_DEG")?1e-4:staggered?8e-6:5e-5;
            assertTrue(error<limit,
                    channel+" full-trajectory error="+error);
        }
    }
}
