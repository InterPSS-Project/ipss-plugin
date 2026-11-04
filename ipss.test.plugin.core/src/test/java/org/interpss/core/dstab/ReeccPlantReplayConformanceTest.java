package org.interpss.core.dstab;

import static org.junit.jupiter.api.Assertions.*;
import java.nio.file.*;
import java.util.*;
import org.apache.commons.math3.complex.Complex;
import org.interpss.dstab.renewable.*;
import org.interpss.fadapter.psse.PSSEMultiFileLoader;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Tag;
import com.interpss.dstab.DStabGen;
import com.interpss.dstab.BaseDStabNetwork;

/** Independent attached-plant input replay; does not certify full network feedback. */
@Tag("extended")
public class ReeccPlantReplayConformanceTest {
    private static final Path CAPTURES=Path.of("testData/private/verification-artifacts/reecc1-plant-remote-20261104");
    private static final Path BOUNDS=Path.of("testData/private/verification-artifacts/reecc1-plant-qbound-20261104");
    @Test void attachedPlantStatesAndAcceptedPortsFollowAllRecordedSamples() throws Exception {
        verifyCaptures();
        for(String profile:new String[]{"plant-flat","plant-fault"})for(boolean history:new boolean[]{false,true})compare(profile,history);
    }
    @Test void activeUpstreamReactiveLimitRetainsAllStatesAndSamples() throws Exception {
        Path manifest=BOUNDS.resolve("manifest.json");
        org.junit.jupiter.api.Assumptions.assumeTrue(Files.exists(manifest),"Private active-limit reference not installed");
        assertEquals("aeacae80fd8391fbe29d56d759ba4533311d7d848afb364e88c52d0160c031ae",sha256(manifest));
        var index=com.google.gson.JsonParser.parseString(Files.readString(manifest)).getAsJsonObject();
        assertEquals(.04,index.get("QMIN").getAsDouble(),0.);
        assertEquals(.5,index.get("Kpg").getAsDouble(),0.);
        for(var entry:index.getAsJsonObject("files").entrySet()) {
            Path file=BOUNDS.resolve(entry.getKey()).normalize();assertTrue(file.startsWith(BOUNDS));
            assertEquals(entry.getValue().getAsString(),sha256(file),entry.getKey());
        }
        assertArrayEquals(Files.readAllBytes(BOUNDS.resolve("plant-qbound-fault-a/native.csv")),Files.readAllBytes(BOUNDS.resolve("plant-qbound-fault-b/native.csv")));
        for(String repeat:List.of("a","b")) {
            var capture=com.google.gson.JsonParser.parseString(Files.readString(BOUNDS.resolve("plant-qbound-fault-"+repeat+"/manifest.json"))).getAsJsonObject();
            assertEquals("complete",capture.get("status").getAsString());
            assertTrue(capture.get("selector_readback_unchanged").getAsBoolean());
            assertEquals(1205,capture.get("sample_count").getAsInt());
        }
        for(boolean history:new boolean[]{false,true})compare("plant-qbound-fault",history);
    }
    private static void compare(String profile,boolean history) throws Exception {
            boolean bounded=profile.equals("plant-qbound-fault");
            Path folder=(bounded?BOUNDS:CAPTURES).resolve(profile+"-a");
            var rows=org.interpss.core.dstab.reference.RepcaNativeVerification.rows(Files.readString(folder.resolve("native.csv")));
            assertEquals(1205,rows.size(),"Full reference row count");
            var context=new PSSEMultiFileLoader().loadDStab(folder.resolve("network.raw").toString(),folder.resolve("dynamic.dyr").toString());
            var network=context.getDStabilityNet();
            assertTrue(context.getDynSimuAlgorithm().getAclfAlgorithm().loadflow());
            var converter=(Regca1Model)((DStabGen)network.getBus("Bus1").getContributeGen("1")).getDynamicGenDevice();
            var model=(Reecc1Model)converter.getActiveElectricalController();
            var plant=model.getPlantController();assertNotNull(plant);
            var first=rows.getFirst();var previous=first;boundary(network,first);
            double p0=first.get("P_PU"),q0=first.get("Q_PU");
            model.configureIntegrationStep(.00025);model.initialize(p0,q0,first.get("V1"));
            var names=new ArrayList<String>();
            for(int i=0;i<7;i++)names.add("ELEC_STATE"+i);
            for(int i=0;i<7;i++)names.add("PLANT_STATE"+i);
            names.addAll(List.of("ELEC_VAR2","ELEC_VAR3","ELEC_VAR4","PREF_INPUT","QEXT_INPUT"));
            double[] maximum=new double[names.size()],at=new double[names.size()];
            var trace=new StringBuilder("time_s");for(String name:names)trace.append(',').append(name).append("_actual,").append(name).append("_reference");trace.append(",algebraic_ip,algebraic_iq\n");
            int events=0,boundSamples=0,heldSamples=0;double previousRaw=Double.NaN,previousIntegral=Double.NaN;
            for(var row:rows) {
                double t=row.get("time_s"),old=previous.get("time_s");
                if(t>=0.&&t>old) {
                    boundary(network,previous);
                    double p=previous.get("P_PU"),q=previous.get("Q_PU"),v=previous.get("V1"),f=1.+previous.get("F1_DEV");
                    if(history)model.stepTwoStep(.00025,p,q,v,f);else model.step(.00025,p,q,v,f);
                } else if(t>=0.&&row!=first&&t==old) {
                    events++;boundary(network,row);
                    if(history){if(profile.endsWith("fault"))model.rebaseNextTwoStep();model.refreshTwoStep(row.get("P_PU"),row.get("Q_PU"),row.get("V1"),1.+row.get("F1_DEV"));}
                }
                if(bounded) {
                    // Absolute Q-reference PI: Kp * clipped(Q0 - measuredQ) + stored integral.
                    double integral=row.get("PLANT_STATE2");
                    double raw=.5*Math.max(-.2,Math.min(.2,q0-row.get("PLANT_STATE1")))+integral;
                    if(raw<.04-1.e-6)boundSamples++;
                    if(previousRaw<.04-1.e-6&&previousIntegral==integral)heldSamples++;
                    previousRaw=raw;previousIntegral=integral;
                    assertTrue(row.get("QEXT_INPUT")>=.04-1.e-8,"Reference limited path remains above QMIN");
                    assertTrue(q0+plant.getQref()>=.04-1.e-8,"Implementation uses absolute QMIN");
                }
                var values=new ArrayList<Double>(model.getNamedStates().values());
                for(double value:plant.getNativeStates())values.add(value);
                values.addAll(List.of(history?model.getTwoStepIpcmd():model.getIpcmd(),history?-model.getTwoStepIqcmd():-model.getIqcmd(),model.getStateOfCharge(),p0+plant.getPref(),q0+plant.getQref()));
                trace.append(t);
                for(int i=0;i<names.size();i++) {
                    double value=values.get(i),reference=row.get(names.get(i)),error=Math.abs(value-reference);
                    assertTrue(Double.isFinite(value));
                    assertTrue(error<=ceiling(profile,history,i), profile+" twoStep="+history+" "+names.get(i)+" t="+t+" error="+error+" ceiling="+ceiling(profile,history,i));
                    if(error>maximum[i]){maximum[i]=error;at[i]=t;}
                    trace.append(',').append(value).append(',').append(reference);
                }
                trace.append(',').append(model.getIpcmd()).append(',').append(-model.getIqcmd()).append('\n');previous=row;
            }
            assertEquals(2,events,"Both run-segment boundary duplicates are retained");
            if(bounded){assertEquals(570,boundSamples);assertEquals(569,heldSamples,"Independent anti-windup plateau");}
            Files.writeString(Path.of("../target/reecc-plant-replay-"+profile+"-"+history+".csv"),trace);
            System.out.println("REECC plant "+profile+" history="+history+" names="+names+" max="+Arrays.toString(maximum)+" at="+Arrays.toString(at));
    }
    static void verifyCaptures() throws Exception {
        Path manifest=CAPTURES.resolve("manifest.json");
        org.junit.jupiter.api.Assumptions.assumeTrue(Files.exists(manifest), "Private independent reference not installed");
        assertEquals("e05fdfb606c947805d7d6157958b9d7c45d7bc71e4da263e2560713e075328d1",sha256(manifest),"Pinned capture index");
        var index=com.google.gson.JsonParser.parseString(Files.readString(manifest)).getAsJsonObject();
        for(var entry:index.getAsJsonObject("files").entrySet()) {
            Path file=CAPTURES.resolve(entry.getKey()).normalize();
            assertTrue(file.startsWith(CAPTURES),"Capture path must stay inside its archive");
            assertEquals(entry.getValue().getAsString(),sha256(file),entry.getKey());
        }
        for(String profile:List.of("plant-flat","plant-fault")) {
            var pair=index.getAsJsonObject("pairs").getAsJsonObject(profile);
            assertTrue(pair.get("byte_identical_csv").getAsBoolean());
            assertEquals(1205,pair.get("rows").getAsInt());
            assertEquals("complete",pair.get("status").getAsString());
            assertArrayEquals(Files.readAllBytes(CAPTURES.resolve(profile+"-a/native.csv")),
                    Files.readAllBytes(CAPTURES.resolve(profile+"-b/native.csv")),profile+" cold repeat");
            for(String repeat:List.of("a","b")) {
                var capture=com.google.gson.JsonParser.parseString(Files.readString(CAPTURES.resolve(profile+"-"+repeat+"/manifest.json"))).getAsJsonObject();
                assertEquals("complete",capture.get("status").getAsString());
                assertTrue(capture.get("selector_readback_unchanged").getAsBoolean());
                assertEquals(1205,capture.get("sample_count").getAsInt());
            }
        }
    }
    private static String sha256(Path file) throws Exception {
        return HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(file)));
    }
    // Fixed physical-unit ceilings for this independent input replay. No sample exclusions.
    private static double ceiling(String profile, boolean history, int channel) {
        if (profile.equals("plant-flat")) return 1.e-7;
        if (channel == 1 || channel == 4 || channel == 7) return 1.e-10;
        if (channel == 6) return history ? 1.e-10 : 5.e-9;
        if (channel == 16) return 6.e-8;
        if (history) return channel == 15 ? 1.5e-4 : 3.e-6;
        return switch(channel) {
            case 0 -> 8.e-4;
            case 2, 9, 12 -> 1.e-5;
            case 3 -> 3.e-4;
            case 5, 13, 17 -> 4.e-5;
            case 8 -> 6.e-4;
            case 10 -> 5.e-5;
            case 11, 14 -> 2.5e-4;
            case 15 -> 1.e-3;
            case 18 -> 1.2e-4;
            default -> throw new IllegalArgumentException("Unqualified channel " + channel);
        };
    }
    private static void boundary(BaseDStabNetwork<?,?> network,Map<String,Double> row) {
        for(int i=1;i<=3;i++) {
            double v=row.get("V"+i),angle=Math.toRadians(row.get("A"+i+"_DEG"));
            network.getDStabBus("Bus"+i).setVoltage(new Complex(v*Math.cos(angle),v*Math.sin(angle)));
            network.getDStabBus("Bus"+i).setFreq(1.+row.get("F"+i+"_DEV"));
        }
    }
}
