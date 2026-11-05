package org.interpss.dstab.control.gov.wecc.urgs3t;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.zip.ZipFile;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.interpss.IpssCorePlugin;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Independent fault-on boundary discriminator; it does not relax trajectory gates. */
public class Urgs3tElectricalBoundaryDiscriminatorTest {
    private static final Path ARCHIVE=Path.of("testData/private/verification-artifacts/urgs3t-electrical-boundary-20261105/captures.zip");
    private static final String SHA="3b920c8cd7d5f5607ca31a7ec8b34265604d1768640b5161236929dcb7f42e2e";
    private static final String SUFFIX="-h0p0005-t0p5-fault-machines-";

    @Test void firstFaultOnDifferencePrecedesTheGovernorAndIsMethodDependent(@TempDir Path staged)throws Exception {
        Assumptions.assumeTrue(Files.isRegularFile(ARCHIVE),"Retain independent complete machine-state captures");
        assertEquals(SHA,hash(Files.readAllBytes(ARCHIVE)));
        IpssCorePlugin.init();IpssCorePlugin.configureSparseSolver("java-klu");
        try(var zip=new ZipFile(ARCHIVE.toFile())) {
            Map<String,Double> priorReference=null;
            var priorErrors=new java.util.HashMap<Boolean,double[]>();
            for(String profile:List.of("standard","temperature-limits")) {
            String tag=profile+SUFFIX;
            byte[] first=zip.getInputStream(zip.getEntry(tag+"a/native.csv")).readAllBytes();
            assertArrayEquals(first,zip.getInputStream(zip.getEntry(tag+"b/native.csv")).readAllBytes());
            for(String repeat:List.of("a","b")) {
                var sourceManifest=new ObjectMapper().readTree(zip.getInputStream(zip.getEntry(tag+repeat+"/manifest.json")));
                assertEquals("complete",sourceManifest.path("status").asText());
                for(var file:sourceManifest.path("files").properties()) {
                    byte[] bytes=zip.getInputStream(zip.getEntry(tag+repeat+"/"+file.getKey())).readAllBytes();
                    assertEquals(file.getValue().asText(),hash(bytes),repeat+"/"+file.getKey());
                }
            }
            Path input=Files.createDirectory(staged.resolve(profile));
            for(String name:List.of("manifest.json","network.raw","dynamic.dyr","native.csv")) {
                byte[] contents=zip.getInputStream(zip.getEntry(tag+"a/"+name)).readAllBytes();
                Files.write(input.resolve(name),contents);
            }
            var manifest=new ObjectMapper().readTree(input.resolve("manifest.json").toFile());
            assertEquals("complete",manifest.path("status").asText());
            assertEquals(.0005,manifest.path("settings").path("step").asDouble(),0.);
            assertTrue(manifest.path("settings").path("machine_states").asBoolean());
            for(String name:List.of("network.raw","dynamic.dyr","native.csv"))
                assertEquals(manifest.path("files").path(name).asText(),hash(Files.readAllBytes(input.resolve(name))));
            var reference=Urgs3tReferenceTest.rows(input.resolve("native.csv"));
            assertEquals(1005,reference.size());
            assertEquals(reference.size(),manifest.path("samples").asInt());
            assertEquals(reference.getFirst().size()-1,manifest.path("channels").asInt());
            for(boolean shared:List.of(false,true)) {
                var simulation=Urgs3tReferenceTest.simulate(input,shared,1e-6);
                assertEquals(reference.size()-2,simulation.frames().size());
                int index=0,eventSides=0,postFault=0;
                Map<String,Double> observed=null,expected=null;
                double last=Double.NaN,lastActual=Double.NaN;
                for(var row:reference) {
                    double time=row.get("time_s");if(time<0)continue;
                    var actual=simulation.frames().get(index++);
                    assertEquals(time,actual.get("time_s"),2e-5,
                            "Retain the indexed numerical interval despite serialized clock drift");
                    if(time==last&&(Math.abs(time-.05)<2e-6||Math.abs(time-.1)<2e-6)) {
                        assertEquals(lastActual,actual.get("time_s"),0.,"Preserve the event's second observation");
                        eventSides++;
                    }
                    if(Math.abs(time-.0505000129)<2e-6){expected=row;observed=actual;postFault++;}
                    last=time;lastActual=actual.get("time_s");
                }
                assertEquals(2,eventSides,"Preserve both event-side duplicates");
                assertEquals(1,postFault,"Compare the first accepted fault-on interval");
                if(priorReference!=null)for(String key:List.of("V1","V2","P_PU","Q_PU","GEN1_STATE2","STATE0","PMECH"))
                    assertEquals(priorReference.get(key),expected.get(key),0.,
                            "Changing only the turbine temperature limit must preserve the first fault-on input: "+key);
                double voltage=Math.abs(observed.get("V1")-expected.get("V1"));
                double reactive=Math.abs(observed.get("Q_PU")-expected.get("Q_PU"));
                double damper=Math.abs(observed.get("GEN1_STATE2")-expected.get("GEN1_STATE2"));
                double governor=Math.abs(observed.get("STATE0")-expected.get("STATE0"));
                double mechanical=Math.abs(observed.get("PMECH")-expected.get("PMECH"));
                double[] errors={voltage,reactive,damper,governor,mechanical};
                if(priorErrors.containsKey(shared))assertArrayEquals(priorErrors.get(shared),errors,1e-9,
                        "The first electrical discrepancy must not depend on the inactive temperature limit");
                else priorErrors.put(shared,errors);
                assertTrue(governor<1e-6&&mechanical<1e-6,"Governor output has not diverged at the fault boundary");
                if(shared) {
                    assertTrue(voltage<1e-6&&reactive<1e-5&&damper<1e-6,
                            "Shared staggered electrical boundary agrees with the independent reference");
                } else {
                    assertTrue(voltage>1e-4&&reactive>1e-3&&damper>5e-4,
                            "Modified Euler's first discrepancy is in the electrical boundary");
                    assertTrue(damper>500*governor,"Electrical-state error must dominate valve-state error");
                }
                priorReference=expected;
            }
            }
        }
    }

    private static String hash(byte[] bytes)throws Exception {
        return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
    }
}
