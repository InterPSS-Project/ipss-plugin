package org.interpss.core.dstab;

import static org.junit.jupiter.api.Assertions.*;
import java.nio.file.*;
import org.apache.commons.math3.complex.Complex;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

/** Full-sample independent discrimination of voltage gain, frame and saved input. */
@Tag("extended")
public class RegcaReferenceGainMemoryTest {
    @Test void recordedPhasorsAndStatesIdentifyGainAndAcceptedBoundaryRecurrence() throws Exception {
        ReeccPlantReplayConformanceTest.verifyCaptures();
        Path folder=Path.of("testData/private/verification-artifacts/reecc1-plant-remote-20261104/plant-fault-a");
        var rows=org.interpss.core.dstab.reference.RepcaNativeVerification.rows(Files.readString(folder.resolve("native.csv")));
        var manifest=com.google.gson.JsonParser.parseString(Files.readString(folder.resolve("manifest.json"))).getAsJsonObject();
        var constants=manifest.getAsJsonObject("settings").getAsJsonArray("converter");
        // Reconstruct the declared single-precision reference constants only;
        // this does not select a different production arithmetic policy.
        double acceleration=(float)constants.get(14).getAsDouble();
        double low=(float)constants.get(8).getAsDouble(),high=(float)constants.get(7).getAsDouble();
        double zero=(float)constants.get(4).getAsDouble(),breakpoint=(float)constants.get(3).getAsDouble(),limitGain=(float)constants.get(5).getAsDouble();
        String branch=Files.readAllLines(folder.resolve("network.raw")).stream().filter(line->line.startsWith("1,2,'1',")).findFirst().orElseThrow();
        String[] fields=branch.split(",");Complex impedance=new Complex(Double.parseDouble(fields[3]),Double.parseDouble(fields[4]));
        assertEquals(0.,Double.parseDouble(fields[5]),0.,"No line-charging term in this synthetic terminal branch");
        double maxInstantaneous=0,maxFilteredRecurrence=0,maxCurrentRecurrence=0;
        double maxAlgebraic=0,maxRecurrence=0,maxFrame=0,maxBranch=0;
        int duplicates=0;var previous=rows.getFirst();
        for(var row:rows) {
            double v=row.get("V1"),theta=Math.toRadians(row.get("A1_DEG"));
            Complex terminal=phasor(v,theta),remote=phasor(row.get("V2"),Math.toRadians(row.get("A2_DEG")));
            double ip=row.get("CONVERTER_STATE0"),iq=row.get("CONVERTER_STATE1");
            double saved=row.get("CONVERTER_VAR0"),filtered=row.get("CONVERTER_STATE2");
            double decelerated=(1.-acceleration)*saved+acceleration*v;
            double gain=clip((decelerated-low)/(high-low));
            double error=Math.abs(v*ip*gain-row.get("P_PU"));maxAlgebraic=Math.max(maxAlgebraic,error);
            assertTrue(error<6.e-8,"Decelerated-voltage power equation at "+row.get("time_s"));
            assertEquals(v*iq,row.get("Q_PU"),2.e-8);
            maxInstantaneous=Math.max(maxInstantaneous,Math.abs(v*ip*clip((v-low)/(high-low))-row.get("P_PU")));
            double modelAngle=row.get("CONVERTER_VAR1");maxFrame=Math.max(maxFrame,Math.abs(modelAngle-theta));
            assertEquals(theta,modelAngle,5.e-8,"Recorded current frame follows terminal angle");
            Complex predicted=new Complex(ip*gain,-iq).multiply(phasor(1.,modelAngle));
            Complex reconstructed=terminal.subtract(remote).divide(impedance);
            double residual=reconstructed.subtract(predicted).abs();maxBranch=Math.max(maxBranch,residual);
            assertTrue(residual<.0011,"Retained finite reference network residual");
            // The separate filtered-voltage active-current limiter stays inactive.
            if(filtered<breakpoint)assertTrue((filtered-zero)*limitGain/(breakpoint-zero)-ip>.6);
            if(row!=rows.getFirst()) {
                double expected=(1.-acceleration)*previous.get("CONVERTER_VAR0")+acceleration*previous.get("V1");
                maxRecurrence=Math.max(maxRecurrence,Math.abs(expected-saved));assertEquals(expected,saved,1.e-7);
                maxFilteredRecurrence=Math.max(maxFilteredRecurrence,Math.abs((1.-acceleration)*previous.get("CONVERTER_VAR0")+acceleration*previous.get("CONVERTER_STATE2")-saved));
                maxCurrentRecurrence=Math.max(maxCurrentRecurrence,Math.abs((1.-acceleration)*previous.get("CONVERTER_VAR0")+acceleration*v-saved));
                if(row.get("time_s").equals(previous.get("time_s"))) {
                    duplicates++;
                    assertEquals(previous.get("CONVERTER_STATE0"),row.get("CONVERTER_STATE0"));
                    assertEquals(previous.get("CONVERTER_STATE1"),row.get("CONVERTER_STATE1"));
                    assertEquals(previous.get("ELEC_VAR2"),row.get("ELEC_VAR2"));
                }
            }
            previous=row;
        }
        assertEquals(1205,rows.size());assertEquals(2,duplicates);
        assertTrue(maxInstantaneous>.015);assertTrue(maxFilteredRecurrence>.19);assertTrue(maxCurrentRecurrence>.19);
        System.out.println("Reference gain audit: algebraic="+maxAlgebraic+", accepted recurrence="+maxRecurrence+", frame="+maxFrame+", branch residual="+maxBranch+", instantaneous counterfactual="+maxInstantaneous);
    }
    private static double clip(double value){return Math.max(0.,Math.min(1.,value));}
    private static Complex phasor(double magnitude,double angle){return new Complex(magnitude*Math.cos(angle),magnitude*Math.sin(angle));}
}
