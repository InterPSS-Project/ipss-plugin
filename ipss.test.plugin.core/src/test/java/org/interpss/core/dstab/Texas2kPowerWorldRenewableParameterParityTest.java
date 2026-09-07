package org.interpss.core.dstab;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.interpss.IpssCorePlugin;
import org.interpss.dstab.renewable.Reeca1Data;
import org.interpss.dstab.renewable.Reeca1Model;
import org.interpss.dstab.renewable.Regca1Data;
import org.interpss.dstab.renewable.Regca1Model;
import org.interpss.dstab.renewable.Repca1Data;
import org.interpss.fadapter.psse.PSSEMultiFileLoader;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import com.interpss.dstab.DStabGen;

/**
 * Independent parameter-oracle gate against PowerWorld's AUX export of the
 * same six Texas2k PSS/E cases. PowerWorld reorders several fields in its AUX
 * tables, so these comparisons are semantic rather than positional copies of
 * the source DYR records.
 */
public class Texas2kPowerWorldRenewableParameterParityTest {
    private static final double TOLERANCE = 1.0e-9;
    private static final Pattern AUX_TOKEN = Pattern.compile("\"[^\"]*\"|\\S+");
    private static final Path ROOT = Path.of(System.getProperty("texas2k.case.root",
            Path.of(System.getProperty("user.home"), "OneDrive", "Documents", "qiuhua",
                    "private_cases", "Texas2k_series24_cases_with_dynamics",
                    "Texas2k_series24_cases_with_dynamics").toString()));
    private static final List<CaseFile> CASES = List.of(
            new CaseFile("Texas2k_series24_case1_2016summerpeak",
                    "Texas2k_series24_case1_2016summerPeak_v36.RAW",
                    "dynamic_models_case1.dyr", "Texas2k_series24_case1_2016summerPeak.AUX", 107),
            new CaseFile("Texas2k_series24_case2_2016lowload",
                    "Texas2k_series24_case2_2016lowload.RAW",
                    "dynamic_models_case2.dyr", "Texas2k_series24_case2_2016lowload.AUX", 107),
            new CaseFile("Texas2k_series24_case3_2024summerpeak",
                    "Texas2k_series24_case3_2024summerpeak_v30.RAW",
                    "dynamic_models_case3.dyr", "Texas2k_series24_case3_2024summerpeak.AUX", 301),
            new CaseFile("Texas2k_series24_case4_2024lowload",
                    "Texas2k_series24_case4_2024lowload.RAW",
                    "dynamic_models_case4.dyr", "Texas2k_series24_case4_2024lowload.AUX", 301),
            new CaseFile("Texas2k_series24_case5_2024highrenewables",
                    "Texas2k_series24_case5_2024highrenewables.RAW",
                    "dynamic_models_case5.dyr", "Texas2k_series24_case5_2024highrenewables.AUX", 301),
            new CaseFile("Texas2k_series24_case6_2024lowloadwithgfm",
                    "Texas2k_series24_case6_2024lowloadwithgfm.RAW",
                    "dynamic_models_case6.dyr", "Texas2k_series24_case6_2024lowloadwithgfm.AUX", 301));

    @BeforeAll
    static void initializePlugin() {
        IpssCorePlugin.init();
    }

    @Test
    void allAttachedRegcaReecaRepcaParametersMatchPowerWorldAux() throws Exception {
        assumeTrue(Files.isDirectory(ROOT), "Missing private Texas2k root: " + ROOT);
        for (CaseFile source : CASES) verify(source);
    }

    private static void verify(CaseFile source) throws Exception {
        Path directory = ROOT.resolve(source.directory());
        Path raw = directory.resolve(source.raw());
        Path dyr = directory.resolve(source.dyr());
        Path aux = directory.resolve(source.aux());
        assumeTrue(Files.isRegularFile(raw), "Missing Texas2k RAW: " + raw);
        assumeTrue(Files.isRegularFile(dyr), "Missing Texas2k DYR: " + dyr);
        assumeTrue(Files.isRegularFile(aux), "Missing PowerWorld AUX: " + aux);

        var network = new PSSEMultiFileLoader().loadDStab(raw.toString(), dyr.toString())
                .getDStabilityNet();
        Map<String, List<String>> regcaRows = readAuxRows(aux, "MachineModel_REGC_A");
        Map<String, List<String>> reecaRows = readAuxRows(aux, "Exciter_REECA1");
        Map<String, List<String>> repcaRows = readAuxRows(aux, "PlantController_REPCA1");

        int compared = 0;
        for (var bus : network.getBusList()) {
            for (var sourceGen : bus.getContributeGenList()) {
                if (!(sourceGen instanceof DStabGen gen)
                        || !(gen.getDynamicGenDevice() instanceof Regca1Model converter)) {
                    continue;
                }
                Reeca1Model electrical = converter.getReeca1Controller();
                if (electrical == null) continue;
                Repca1Data plant = electrical.getPlantController() == null
                        ? null : electrical.getPlantController().getData();
                assertNotNull(plant, source.directory() + " missing REPCA1 at "
                        + bus.getId() + ":" + gen.getId());
                String key = bus.getNumber() + ":" + gen.getId().trim();
                assertRegca(source.directory(), key, converter.getData(), regcaRows.get(key));
                assertReeca(source.directory(), key, electrical.getData(), reecaRows.get(key));
                assertRepca(source.directory(), key, plant, repcaRows.get(key));
                compared++;
            }
        }
        assertEquals(source.expectedChains(), compared,
                source.directory() + " PowerWorld renewable-chain comparisons");
    }

    private static void assertRegca(String caseName, String key, Regca1Data data,
            List<String> row) {
        String label = caseName + " REGCA1 " + key;
        requireActiveModelRow(label, row);
        assertEquals(data.lvplsw(), integer(row, 3), label + " LVPLSW");
        assertDoubles(label, row,
                new int[] {11, 4, 5, 6, 7, 8, 9, 10, 15, 12, 16, 13, 14},
                new double[] {data.tg(), data.rrpwr(), data.brkpt(), data.zerox(),
                        data.lvpl1(), data.volim(), data.lvpnt1(), data.lvpnt0(),
                        data.iolim(), data.tfltr(), data.khv(), data.iqrmax(),
                        data.iqrmin()});
    }

    private static void assertReeca(String caseName, String key, Reeca1Data data,
            List<String> row) {
        String label = caseName + " REECA1 " + key;
        requireActiveModelRow(label, row);
        assertEquals(data.pfFlag(), integer(row, 4), label + " PFFLAG");
        assertEquals(data.vFlag(), integer(row, 5), label + " VFLAG");
        assertEquals(data.qFlag(), integer(row, 6), label + " QFLAG");
        assertEquals(data.pFlag(), integer(row, 8), label + " PFLAG");
        assertEquals(data.pqFlag(), integer(row, 7), label + " PQFLAG");
        assertDoubles(label, row,
                new int[] {9, 10, 11, 12, 13, 14, 15, 16, 17, 18, 19, 20,
                        21, 22, 23, 24, 25, 26, 27, 28, 29, 30, 31, 32, 33,
                        34, 35, 37, 36, 38, 39, 40, 41, 42, 43, 44, 45, 46,
                        47, 48, 49, 50, 51, 52, 53},
                new double[] {data.vdip(), data.vup(), data.trv(), data.dbd1(),
                        data.dbd2(), data.kqv(), data.iqh1(), data.iql1(),
                        data.vref0(), data.iqfrz(), data.thld(), data.thld2(),
                        data.tp(), data.qmax(), data.qmin(), data.vmax(), data.vmin(),
                        data.kqp(), data.kqi(), data.kvp(), data.kvi(), data.vref1(),
                        data.tiq(), data.dpmax(), data.dpmin(), data.pmax(), data.pmin(),
                        data.imax(), data.tpord(), data.vq1(), data.iq1(), data.vq2(),
                        data.iq2(), data.vq3(), data.iq3(), data.vq4(), data.iq4(),
                        data.vp1(), data.ip1(), data.vp2(), data.ip2(), data.vp3(),
                        data.ip3(), data.vp4(), data.ip4()});
    }

    private static void assertRepca(String caseName, String key, Repca1Data data,
            List<String> row) {
        String label = caseName + " REPCA1 " + key;
        requireActiveModelRow(label, row);
        assertEquals(0, data.remoteBus(), label + " remote bus");
        assertEquals(0, data.branchFromBus(), label + " branch from bus");
        assertEquals(0, data.branchToBus(), label + " branch to bus");
        assertEquals(data.refFlag(), integer(row, 5), label + " RefFlag");
        assertEquals(data.vcFlag(), integer(row, 6), label + " VcompFlag");
        assertEquals(data.fFlag(), integer(row, 7), label + " FreqFlag");
        assertEquals(data.puFlag(), integer(row, 35), label + " PUflag");
        assertDoubles(label, row,
                new int[] {8, 9, 10, 11, 12, 13, 14, 15, 16, 17, 18, 19, 34,
                        20, 21, 22, 23, 24, 25, 26, 27, 28, 29, 30, 31, 32, 33},
                new double[] {data.tfltr(), data.kp(), data.ki(), data.tft(),
                        data.tfv(), data.vfrz(), data.rc(), data.xc(), data.kc(),
                        data.emax(), data.emin(), data.dbd1(), data.dbd2(), data.qmax(),
                        data.qmin(), data.kpg(), data.kig(), data.tp(), data.fdbd1(),
                        data.fdbd2(), data.femax(), data.femin(), data.pmax(),
                        data.pmin(), data.tg(), data.ddn(), data.dup()});
    }

    private static void requireActiveModelRow(String label, List<String> row) {
        assertNotNull(row, label + " missing from PowerWorld AUX");
        assertEquals("Active", row.get(2), label + " model status");
    }

    private static void assertDoubles(String label, List<String> row,
            int[] indices, double[] actual) {
        assertEquals(indices.length, actual.length, label + " test definition");
        for (int i = 0; i < indices.length; i++) {
            assertEquals(number(row, indices[i]), actual[i], TOLERANCE,
                    label + " AUX field " + indices[i]);
        }
    }

    private static int integer(List<String> row, int index) {
        return Integer.parseInt(row.get(index));
    }

    private static double number(List<String> row, int index) {
        return Double.parseDouble(row.get(index));
    }

    private static Map<String, List<String>> readAuxRows(Path aux, String table)
            throws Exception {
        List<String> lines = Files.readAllLines(aux);
        boolean tableFound = false;
        boolean inRows = false;
        Map<String, List<String>> rows = new LinkedHashMap<>();
        for (String raw : lines) {
            String line = raw.trim();
            if (!tableFound) {
                tableFound = line.startsWith("DATA (" + table + ",");
                continue;
            }
            if (!inRows) {
                if (line.equals("{")) inRows = true;
                continue;
            }
            if (line.equals("}")) break;
            if (line.isEmpty() || line.startsWith("//")) continue;
            List<String> tokens = tokenize(line);
            rows.put(tokens.get(0) + ":" + tokens.get(1), tokens);
        }
        assertEquals(true, tableFound, "Missing PowerWorld AUX table " + table);
        assertEquals(false, rows.isEmpty(), "Empty PowerWorld AUX table " + table);
        return rows;
    }

    private static List<String> tokenize(String line) {
        Matcher matcher = AUX_TOKEN.matcher(line);
        java.util.ArrayList<String> tokens = new java.util.ArrayList<>();
        while (matcher.find()) {
            String token = matcher.group();
            if (token.length() >= 2 && token.startsWith("\"") && token.endsWith("\"")) {
                token = token.substring(1, token.length() - 1);
            }
            tokens.add(token);
        }
        return List.copyOf(tokens);
    }

    private record CaseFile(String directory, String raw, String dyr, String aux,
            int expectedChains) { }
}
