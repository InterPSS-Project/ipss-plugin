package org.interpss.core.dstab;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import org.interpss.IpssCorePlugin;
import org.interpss.fadapter.psse.PSSEMultiFileLoader;
import org.interpss.fadapter.psse.dyr.PsseDyrRecord;
import org.interpss.fadapter.psse.dyr.PsseDyrRecordReader;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import com.interpss.dstab.mach.RoundRotorMachine;
import com.interpss.dstab.mach.SalientPoleMachine;

/** Exact shared-core runtime attachment gate for all Texas2k synchronous machines. */
public class Texas2kSynchronousMachineCoverageTest {
    private static final Path ROOT = Path.of(System.getProperty("texas2k.case.root",
            Path.of("testData", "private", "texas2k").toString()));
    private static final List<CaseFile> CASES = List.of(
            new CaseFile("Texas2k_series24_case1_2016summerpeak",
                    "Texas2k_series24_case1_2016summerPeak_v36.RAW", "dynamic_models_case1.dyr",
                    "dynamic_models_case1_gnet.idv"),
            new CaseFile("Texas2k_series24_case2_2016lowload",
                    "Texas2k_series24_case2_2016lowload.RAW", "dynamic_models_case2.dyr",
                    "dynamic_models_case2_gnet.idv"),
            new CaseFile("Texas2k_series24_case3_2024summerpeak",
                    "Texas2k_series24_case3_2024summerpeak_v30.RAW", "dynamic_models_case3.dyr",
                    "dynamic_models_case3_gnet.idv"),
            new CaseFile("Texas2k_series24_case4_2024lowload",
                    "Texas2k_series24_case4_2024lowload.RAW", "dynamic_models_case4.dyr",
                    "dynamic_models_case4_gnet.idv"),
            new CaseFile("Texas2k_series24_case5_2024highrenewables",
                    "Texas2k_series24_case5_2024highrenewables.RAW", "dynamic_models_case5.dyr",
                    "dynamic_models_case5_gnet.idv"),
            new CaseFile("Texas2k_series24_case6_2024lowloadwithgfm",
                    "Texas2k_series24_case6_2024lowloadwithgfm.RAW", "dynamic_models_case6.dyr",
                    "dynamic_models_case6_gnet.idv"));

    @BeforeAll
    static void initializePlugin() {
        IpssCorePlugin.init();
    }

    @Test
    void allSixCasesAttachEveryGenrouAndGensalToTheExpectedCoreMachine() throws Exception {
        assumeTrue(Files.isDirectory(ROOT), "Missing private Texas2k root: " + ROOT);
        for (CaseFile source : CASES) verify(source);
    }

    private static void verify(CaseFile source) throws Exception {
        Path directory = ROOT.resolve(source.directory());
        Path raw = directory.resolve(source.raw());
        Path dyr = directory.resolve(source.dyr());
        Path gnet = directory.resolve(source.gnet());
        assumeTrue(Files.isRegularFile(raw), "Missing Texas2k RAW: " + raw);
        assumeTrue(Files.isRegularFile(dyr), "Missing Texas2k DYR: " + dyr);
        assumeTrue(Files.isRegularFile(gnet), "Missing Texas2k GNET IDV: " + gnet);

        var network = new PSSEMultiFileLoader().loadDStab(
                raw.toString(), dyr.toString(), gnet.toString()).getDStabilityNet();
        List<PsseDyrRecord> records = PsseDyrRecordReader.read(dyr).stream()
                .filter(record -> record.canonicalModelName().equals("GENROU")
                        || record.canonicalModelName().equals("GENSAL"))
                .toList();
        assertEquals(435, records.size(), source.directory() + " synchronous-machine records");
        assertEquals(410, records.stream()
                .filter(record -> record.canonicalModelName().equals("GENROU")).count(),
                source.directory() + " GENROU records");
        assertEquals(25, records.stream()
                .filter(record -> record.canonicalModelName().equals("GENSAL")).count(),
                source.directory() + " GENSAL records");

        for (PsseDyrRecord record : records) {
            String id = "Bus" + record.busNumber() + "-mach" + record.deviceId();
            var machine = network.getMachine(id);
            assertNotNull(machine, source.directory() + " missing "
                    + record.canonicalModelName() + " " + id);
            if (record.canonicalModelName().equals("GENROU")) {
                assertTrue(machine instanceof RoundRotorMachine,
                        source.directory() + " GENROU runtime for " + id + ": "
                                + machine.getClass().getName());
            } else {
                assertTrue(machine instanceof SalientPoleMachine
                                && !(machine instanceof RoundRotorMachine),
                        source.directory() + " GENSAL runtime for " + id + ": "
                                + machine.getClass().getName());
            }
        }
    }

    private record CaseFile(String directory, String raw, String dyr, String gnet) { }
}
