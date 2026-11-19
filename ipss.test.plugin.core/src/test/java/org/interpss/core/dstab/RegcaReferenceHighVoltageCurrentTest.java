package org.interpss.core.dstab;

import static org.junit.jupiter.api.Assertions.*;
import java.nio.file.*;
import java.util.*;
import java.security.MessageDigest;
import org.apache.commons.math3.complex.Complex;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

/** Independent output equation and measured branch-current certificate discrimination. */
@Tag("extended")
public class RegcaReferenceHighVoltageCurrentTest {
    @Test void completeColdPairsPreserveOriginalChannelsAndDistinguishPublishedOutputFromBranchFlow() throws Exception {
        Path folder=Path.of("testData/private/verification-artifacts/regca-hv-phasors-20261104");
        assertEquals("62320f2ff351f96855f4a509621406a44d11a2fa98dcfa17f24b994636e19231",sha(folder.resolve("manifest.json")));
        var manifest=com.google.gson.JsonParser.parseString(Files.readString(folder.resolve("manifest.json"))).getAsJsonObject();
        for(var entry:manifest.getAsJsonObject("files").entrySet())assertEquals(entry.getValue().getAsString(),sha(folder.resolve(entry.getKey())),entry.getKey());
        Path original=folder.resolve(manifest.get("original_csv").getAsString());
        assertEquals(manifest.get("original_sha256").getAsString(),sha(original));
        assertArrayEquals(Files.readAllBytes(folder.resolve("a/psse.csv")),Files.readAllBytes(folder.resolve("b/psse.csv")));
        var rows=org.interpss.core.dstab.reference.RepcaNativeVerification.rows(Files.readString(folder.resolve("a/psse.csv")));
        var old=org.interpss.core.dstab.reference.RepcaNativeVerification.rows(Files.readString(original));
        assertEquals(2005,rows.size());assertEquals(old.size(),rows.size());
        double maxBranchPowerError=0.,maxOutputEquationError=0.,maxCurrentResidual=0.;int active=0,duplicates=0;
        double previous=Double.NaN;
        for(int index=0;index<rows.size();index++) {
            var row=rows.get(index);
            for(var entry:old.get(index).entrySet())assertEquals(entry.getValue(),row.get(entry.getKey()),"Additional observations cannot change original channels at row "+index);
            for(double value:row.values())assertTrue(Double.isFinite(value));
            double time=row.get("time_s");if(time==previous)duplicates++;previous=time;
            Complex terminal=phasor(row,"1"),remote=phasor(row,"2");
            Complex branch=terminal.subtract(remote).divide(new Complex(0.,.1));
            Complex branchPower=terminal.multiply(branch.conjugate());
            Complex measuredPower=new Complex(row.get("P12_MW"),row.get("Q12_MVAR")).divide(100.);
            // Independently recorded branch flow checks the phasor reconstruction, including fault rows.
            double branchError=branchPower.subtract(measuredPower).abs();maxBranchPowerError=Math.max(maxBranchPowerError,branchError);
            assertTrue(branchError<2.e-6,"Branch power reconstruction at "+time);
            double voltage=row.get("V1");
            double acceleration=(double)(float).8;
            double decelerated=(1.-acceleration)*row.get("REGCA_PREVIOUS_V")+acceleration*voltage;
            double generatedIq=Math.min(-(double)(float)-1.3,row.get("REGCA_IQ")-row.get("REGCA_HV_CORRECTION")/decelerated);
            double equationError=Math.abs(row.get("Q_PU")/voltage-generatedIq);
            maxOutputEquationError=Math.max(maxOutputEquationError,equationError);
            assertTrue(equationError<1.e-7,"Published current equation at "+time);
            if(time>.1&&voltage>1.2) {
                active++;
                assertEquals(0.,row.get("REGCA_IP"),0.,"This independent zero-active-power profile needs no active-current reconstruction");
                Complex source=new Complex(0.,-row.get("Q_PU")/voltage).multiply(terminal.divide(voltage));
                maxCurrentResidual=Math.max(maxCurrentResidual,source.subtract(branch).abs());
            }
        }
        assertEquals(2,duplicates);assertEquals(104,active);
        // This is a counterexample to joint residual certification, not a relaxed physical acceptance bound.
        assertTrue(maxCurrentResidual>2.e-3,"Published current is measurably distinct from the accepted branch current");
        System.out.println("REGCA high-voltage reference max output-equation="+maxOutputEquationError+" branch-power="+maxBranchPowerError+" published-current residual="+maxCurrentResidual);
    }
    @Test void retainedWeightingControlPreservesSavedInputAndOutputEquation() throws Exception {
        Path root=Path.of("testData/private/verification-artifacts/2026-10-17-regca-network-quality");
        String[] profiles={"baseline","accel-half"};
        String[] hashes={"5a1d1a0724556c861c10f00e4a0693cfdc4fec13fa0eb32f92cd8fe73384d345","d12ad530569937847762d807b4fbd779bf3880c5744d9d1353aaf9ee09b1c1b8"};
        for(int profile=0;profile<profiles.length;profile++) {
            Path a=root.resolve("regca-boundary-native-"+profiles[profile]+"-iter200-a/psse.csv");
            Path b=root.resolve("regca-boundary-native-"+profiles[profile]+"-iter200-b/psse.csv");
            assertEquals(hashes[profile],sha(a));assertArrayEquals(Files.readAllBytes(a),Files.readAllBytes(b));
            var rows=org.interpss.core.dstab.reference.RepcaNativeVerification.rows(Files.readString(a));
            float acceleration=profile==0?.8f:.4f;int active=0,duplicates=0;Map<String,Double> previous=null;
            for(var row:rows) {
                for(double value:row.values())assertTrue(Double.isFinite(value));
                if(previous!=null) {
                    // Reconstruct recorded storage arithmetic only, without changing production precision.
                    float expected=(1.f-acceleration)*previous.get("REGCA_PREVIOUS_V").floatValue()
                            +acceleration*previous.get("V_BUS1").floatValue();
                    assertEquals((double)expected,row.get("REGCA_PREVIOUS_V"),1.e-11,"Recorded storage identity at "+row.get("time_s"));
                    if(row.get("time_s").equals(previous.get("time_s")))duplicates++;
                }
                double voltage=row.get("V_BUS1");
                double decelerated=(1.-acceleration)*row.get("REGCA_PREVIOUS_V")+acceleration*voltage;
                double current=row.get("REGCA_IQ")-row.get("REGCA_HV_CORRECTION")/decelerated;
                assertEquals(current,row.get("Q_PU")/voltage,1.e-7,"Unchanged output-equation ceiling");
                if(row.get("REGCA_HV_CORRECTION")>0.)active++;previous=row;
            }
            assertEquals(2005,rows.size());assertEquals(2,duplicates);assertEquals(profile==0?105:97,active);
        }
    }
    private static Complex phasor(Map<String,Double> row,String bus) {
        double angle=Math.toRadians(row.get("A"+bus+"_DEG"));
        return new Complex(Math.cos(angle),Math.sin(angle)).multiply(row.get("V"+bus));
    }
    private static String sha(Path path) throws Exception {
        return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(path)));
    }
}
