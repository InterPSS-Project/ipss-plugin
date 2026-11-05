package org.interpss.dstab.control.gov.wecc.urgs3t;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.Map;
import java.util.zip.ZipFile;

import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.ObjectMapper;

/** Source-only discriminator for the first accepted valve steps after host reactivation. */
public class Urgs3tReactivationDerivativeHistoryTest {
    private static final Path ARCHIVE=Path.of("testData/private/verification-artifacts/urgs3t-reactivation-contract-20261104/captures.zip");
    private static final String SHA="e2508035a2f7d912f6596cdc71849d986b79e1189efe7bf5956402ffdda24e18";
    private static final String TAG="reenable-standard-h0p0005-t0p5-trip1-machines-";

    @Test void acceptedLeftRateSurvivesReactivationAndEntersTwoStepHistory()throws Exception {
        Assumptions.assumeTrue(Files.isRegularFile(ARCHIVE));
        assertEquals(SHA,HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                .digest(Files.readAllBytes(ARCHIVE))));
        try(var zip=new ZipFile(ARCHIVE.toFile())) {
            byte[] first=zip.getInputStream(zip.getEntry(TAG+"a/native.csv")).readAllBytes();
            assertArrayEquals(first,zip.getInputStream(zip.getEntry(TAG+"b/native.csv")).readAllBytes());
            var manifest=new ObjectMapper().readTree(zip.getInputStream(zip.getEntry(TAG+"a/manifest.json")));
            assertEquals("complete",manifest.path("status").asText());
            double h=manifest.path("settings").path("step").asDouble();
            double[] c=new ObjectMapper().convertValue(manifest.path("entered_constants"),double[].class);
            assertEquals(.0005,h,0.);
            assertEquals(0.,c[16],0.);assertEquals(0.,c[17],0.);
            assertEquals(0.,c[29],0.); // Direct droop input, without a lag or deadband.
            Path staged=Files.createTempFile(Files.createDirectories(Path.of("target")),"urgs3t-reactivation-history-",".csv");
            java.util.List<Map<String,Double>> rows;
            try { Files.write(staged,first); rows=Urgs3tReferenceTest.rows(staged); }
            finally { Files.deleteIfExists(staged); }
            int action=-1;
            for(int i=2;i<rows.size()-2;i++)if(rows.get(i).get("time_s")>.19
                    &&rows.get(i).get("time_s").equals(rows.get(i-1).get("time_s"))){action=i;break;}
            assertTrue(action>2);
            Map<String,Double> previous=rows.get(action-2),left=rows.get(action-1),right=rows.get(action);
            Map<String,Double> one=rows.get(action+1),two=rows.get(action+2);
            assertEquals(.2,right.get("time_s"),2e-6);
            assertEquals(left.get("STATE0"),right.get("STATE0"),0.);
            assertEquals(0.,right.get("GREF"),0.);

            // The disconnected host kept advancing the controller coordinate.
            // Its accepted left rate is recoverable from the preceding interval.
            double leftRate=(left.get("STATE0")-previous.get("STATE0"))/h;
            double rightRate=valveRate(right,c);
            assertTrue(leftRate>0.7&&rightRate< -6.6);
            double eventRate=.5*(leftRate+rightRate);
            double firstExpected=right.get("STATE0")+h*(1.5*eventRate-.5*leftRate);
            double secondExpected=one.get("STATE0")+h*(1.5*valveRate(one,c)-.5*eventRate);
            assertEquals(firstExpected,one.get("STATE0"),2e-8);
            assertEquals(secondExpected,two.get("STATE0"),7e-8);
            assertTrue(Math.abs(right.get("STATE0")+h*rightRate-one.get("STATE0"))>8e-4,
                    "A full right-side first step must remain distinguishable");
        }
    }

    private static double valveRate(Map<String,Double> row,double[] c) {
        double input=row.get("GREF")-row.get("SPEED")/c[0];
        double valve=row.get("STATE0");
        double temperatureLimit=c[9]+c[4]+c[5]*(c[4]-row.get("STATE2"));
        assertTrue(input<temperatureLimit);
        assertTrue(input-valve<c[10]);
        return (input-valve)/c[1];
    }
}
