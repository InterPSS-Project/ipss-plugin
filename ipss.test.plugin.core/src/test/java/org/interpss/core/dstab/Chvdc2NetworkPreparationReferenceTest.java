package org.interpss.core.dstab;

import static org.junit.jupiter.api.Assertions.*;
import java.nio.file.*;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.*;
import java.util.zip.ZipFile;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.interpss.CorePluginTestSetup;
import org.interpss.core.dstab.reference.BenchmarkOutputGuard;
import org.interpss.dstab.hvdc.*;
import org.interpss.fadapter.psse.PSSEMultiFileLoader;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/** Independent initialization only: solve the captured RAW without importing dynamic DC states. */
public class Chvdc2NetworkPreparationReferenceTest extends CorePluginTestSetup {
    private static final Path ARCHIVE=Path.of("testData/private/verification-artifacts/chvdc2-controller-20261102/captures.zip");
    private static final ObjectMapper JSON=new ObjectMapper();
    @TempDir Path folder;
    @BeforeAll static void sourceIntegrity()throws Exception {
        Assumptions.assumeTrue(Files.isRegularFile(ARCHIVE),"Retain independent initialized reference captures");
        assertEquals("75d8b6a733eb20426dbc8c8fe7c4efdab854c8e11a82da99ee98f6ce76377511",
                HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(ARCHIVE))));
    }
    @ParameterizedTest @ValueSource(strings={"0p00025","0p0005"})
    void solvedRawPreparationMatchesIndependentInitialStatesAndBoundary(String step)throws Exception {
        String tag="stiff-legacy-inverter-fault-flags001-h"+step+"-a";
        try(var z=new ZipFile(ARCHIVE.toFile())) {
            var manifest=JSON.readTree(z.getInputStream(z.getEntry(tag+"/manifest.json")));
            assertEquals("complete",manifest.path("status").asText());
            assertTrue(manifest.path("initialization_and_network_valid").asBoolean());
            assertEquals(manifest.path("entered_constants"),manifest.path("initialized_constants"));
            byte[] csv=z.getInputStream(z.getEntry(tag+"/native.csv")).readAllBytes();
            assertArrayEquals(csv,z.getInputStream(z.getEntry(tag.substring(0,tag.length()-1)+"b/native.csv")).readAllBytes());
            var reference=Tiocr1ResetReferenceTest.csv(csv).getFirst();
            Path raw=folder.resolve("network.raw"),dyr=folder.resolve("machines.dyr");
            Files.write(raw,z.getInputStream(z.getEntry(tag+"/network.raw")).readAllBytes());
            String source=new String(z.getInputStream(z.getEntry(tag+"/dynamic.dyr")).readAllBytes(),StandardCharsets.UTF_8);
            var machines=source.lines().filter(line->line.contains("'GENCLS'")).toList();assertEquals(4,machines.size());
            Files.write(dyr,machines);
            var context=new PSSEMultiFileLoader().loadDStab(raw.toString(),dyr.toString());
            var n=context.getDStabilityNet();var flow=context.getDynSimuAlgorithm().getAclfAlgorithm();
            flow.setTolerance(1e-10);flow.setMaxIterations(80);flow.getLfAdjAlgo().setApplyAdjustAlgo(false);
            assertTrue(flow.loadflow());
            var p=new Chvdc2Parameters("1",Chvdc2Parameters.Dialect.CHVDC2U1,
                    JSON.convertValue(manifest.path("settings").path("flags"),int[].class),
                    JSON.convertValue(manifest.path("entered_constants"),double[].class));
            var b=Chvdc2NetworkPreparation.prepare(n,p,manifest.path("settings").path("step").asDouble(),reference.get("time_s"));
            var m=b.runtime();double[] values=new double[10],x=new double[m.staggeredStateCount()];
            m.readBoundary(values,0);m.readPublishedStates(x,0);
            assertEquals(reference.get("VAR15")*p.value(Chvdc2Parameters.Constant.I_DC_RATED_A),b.operatingPoint().currentAmperes(),.01);
            var errors=new LinkedHashMap<String,Double>();
            var checks=new ArrayList<org.junit.jupiter.api.function.Executable>();
            for(String bus:new String[]{"7","9"}) {
                double error=n.getBus("Bus"+bus).getVoltageMag()-reference.get("V"+bus);
                errors.put("V"+bus+"_pu",error);checks.add(()->assertEquals(0,error,1e-5,"AC voltage "+bus));
            }
            for(int k=0;k<6;k++) {
                int index=k;String channel="VAR"+new int[]{5,7,9,10,11,12}[k];
                double expected=reference.get(channel)/(k<2?1:100),error=values[k+2]-expected,limit=k<2?.01:1e-4;
                errors.put(channel+(k<2?"_A":"_pu"),error);checks.add(()->assertEquals(0,error,limit,"Boundary "+index));
            }
            double[] limits={1e-5,1e-5,1e-5,1e-5,.002,.002,.002,.002,1e-5,1e-5,1e-5,1e-5,1e-5,1e-5,.002,.01};
            for(int k=0;k<x.length;k++) {
                int index=k;double error=x[k]-reference.get("STATE"+k);errors.put("STATE"+k,error);
                checks.add(()->assertEquals(0,error,limits[index],"Initial state "+index));
            }
            Path output=Files.createTempDirectory(Path.of("target"),"chvdc2-network-initialization-");
            try(var writer=BenchmarkOutputGuard.newWriter(output.resolve("comparison.json"),20000)) {
                JSON.writerWithDefaultPrettyPrinter().writeValue(writer,Map.of("profile",tag,"initial_errors",errors,
                        "recorded_states_used_for_initialization",false,"full_trajectory_qualification",false));
            }
            assertAll(tag,checks);
        }
    }
}
