package org.interpss.core.dstab;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;
import java.nio.file.*;
import java.util.*;
import org.interpss.IpssCorePlugin;
import org.interpss.core.dstab.reference.RepcaNativeVerification;
import com.interpss.dstab.controller.exc.exac2.Exac2Exciter;
import org.interpss.fadapter.psse.PSSEMultiFileLoader;
import org.junit.jupiter.api.*;

/** Independent field-current-base witness and full-solver equilibrium regression. */
public class Exac2FieldBaseVerificationTest {
    private static final Path ROOT=Path.of("testData/private/exac2-field-base");
    @BeforeAll static void initialize(){IpssCorePlugin.init();IpssCorePlugin.configureSparseSolver("java-klu");}

    @Test void exactNativeInitialStatesAndFullSolverFlatRunAgree()throws Exception {
        assumeTrue(Files.isDirectory(ROOT),"Private field-current references unavailable");
        for(String grid:List.of("coarse","fine")) {
            var directory=ROOT.resolve("flat-"+grid+"-a");
            var rows=RepcaNativeVerification.rows(Files.readString(directory.resolve("native.csv")));
            var first=rows.getFirst();var loader=new PSSEMultiFileLoader();
            var context=loader.loadDStab(directory.resolve("network.raw").toString(),directory.resolve("dynamic.dyr").toString());
            assertTrue(loader.getLastDynamicImportReport().isStrictlyComplete());
            var algorithm=context.getDynSimuAlgorithm();assertTrue(algorithm.getAclfAlgorithm().loadflow());
            algorithm.setSimuStepSec(grid.equals("coarse")?.0005:.00025);
            algorithm.setSimuOutputHandler(new com.interpss.dstab.cache.StateMonitor());
            assertTrue(algorithm.initialization());var machine=context.getDStabilityNet().getMachine("Bus1-mach1");
            var exciter=assertInstanceOf(Exac2Exciter.class,machine.getExciter());
            double[] initial=exciter.getStateSnapshot();
            // The existing API exposes a normalized washout low-pass state;
            // native storage includes Kf. This is an observation conversion.
            for(int i=0;i<5;i++)assertEquals(first.get("EXC_STATE"+i),
                    i==4?exciter.kf*initial[i]:initial[i],1e-6,"Native state "+i);
            assertEquals(first.get("EFD"),exciter.getOutput(machine),1e-6);
            algorithm.setTotalSimuTimeSec(.6);assertTrue(algorithm.performSimulation());
            assertArrayEquals(initial,exciter.getStateSnapshot(),1e-10);
            assertEquals(first.get("EFD"),exciter.getOutput(machine),1e-6);assertEquals(1,machine.getSpeed(),1e-12);
            for(var row:rows)assertEquals(first.get("EFD"),row.get("EFD"),0,"Native equilibrium witness");
        }
    }

    @Test void allRawReferenceChannelsRepeatAndEveryManifestHashIsIntact()throws Exception {
        assumeTrue(Files.isDirectory(ROOT),"Private field-current references unavailable");
        for(String mode:List.of("flat","step-up","fault"))for(String grid:List.of("coarse","fine")) {
            Path a=ROOT.resolve(mode+"-"+grid+"-a"),b=ROOT.resolve(mode+"-"+grid+"-b");
            for(Path directory:List.of(a,b)) {
                var manifest=com.google.gson.JsonParser.parseString(Files.readString(directory.resolve("manifest.json"))).getAsJsonObject();
                assertEquals("complete",manifest.get("status").getAsString());assertEquals("EXAC2",manifest.get("model").getAsString());
                for(var entry:manifest.getAsJsonObject("files").entrySet())assertEquals(entry.getValue().getAsString(),
                        HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(directory.resolve(entry.getKey())))));
            }
            assertEquals(-1,Files.mismatch(a.resolve("native.csv"),b.resolve("native.csv")),mode+"-"+grid);
        }
    }
}
