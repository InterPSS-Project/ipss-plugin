package org.interpss.core.dstab;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import org.interpss.IpssCorePlugin;
import org.interpss.fadapter.builder.DStabNetworkBuilder;
import org.interpss.fadapter.psse.PSSEDStabDirectParser;
import org.interpss.fadapter.psse.PSSEMultiFileLoader;
import org.interpss.fadapter.psse.PsseGnetIdvProcessor;
import org.interpss.fadapter.psse.PsseModelRemoveIdvProcessor;
import org.interpss.fadapter.psse.dyr.DynamicModelImportEntry;
import org.interpss.fadapter.psse.dyr.DynamicModelImportReport;
import org.interpss.fadapter.psse.dyr.DynamicModelImportStatus;
import org.interpss.fadapter.psse.dyr.DynamicModelCatalog;
import org.interpss.fadapter.psse.dyr.DynamicModelCategory;
import org.interpss.dstab.renewable.Reeca1Model;
import org.interpss.dstab.renewable.Regca1Model;
import org.interpss.dstab.renewable.Regfma1Model;
import org.interpss.dstab.renewable.WindControlStack;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.interpss.dstab.BaseDStabNetwork;
import com.interpss.dstab.DStabGen;

/** Whole-file attachment audit for all six Texas2k Series 24 cases. */
public class Texas2kFullDynamicCoverageTest {
    private static final Set<String> MODEL_REMOVED_STACKS = Set.of(
            "5045:1", "7099:2");
    private static final Set<String> GNET_REMOVED_STACKS = Set.of(
            "5394:1", "5395:1", "7095:1");
    private static final Set<String> REVIEWED_DEPENDENCY_MODELS = Set.of(
            "REECA1", "REPCA1", "WTARA1", "WTPTA1", "WTTQA1");
    private static final Path ROOT = Path.of(System.getProperty("texas2k.case.root",
            Path.of(System.getProperty("user.home"), "OneDrive", "Documents", "qiuhua",
                    "private_cases", "Texas2k_series24_cases_with_dynamics",
                    "Texas2k_series24_cases_with_dynamics").toString()));
    private static final List<CaseFile> CASES = List.of(
            new CaseFile("Texas2k_series24_case1_2016summerpeak",
                    "Texas2k_series24_case1_2016summerPeak_v36.RAW", "dynamic_models_case1.dyr",
                    "dynamic_models_case1_gnet.idv", "dynamic_models_case1_MODREMOVE.idv",
                    2223, 0, 0),
            new CaseFile("Texas2k_series24_case2_2016lowload",
                    "Texas2k_series24_case2_2016lowload.RAW", "dynamic_models_case2.dyr",
                    "dynamic_models_case2_gnet.idv", "dynamic_models_case2_MODREMOVE.idv",
                    2223, 0, 0),
            new CaseFile("Texas2k_series24_case3_2024summerpeak",
                    "Texas2k_series24_case3_2024summerpeak_v30.RAW", "dynamic_models_case3.dyr",
                    "dynamic_models_case3_gnet.idv", "dynamic_models_case3_MODREMOVE.idv",
                    2965, 10, 15),
            new CaseFile("Texas2k_series24_case4_2024lowload",
                    "Texas2k_series24_case4_2024lowload.RAW", "dynamic_models_case4.dyr",
                    "dynamic_models_case4_gnet.idv", "dynamic_models_case4_MODREMOVE.idv",
                    2965, 10, 15),
            new CaseFile("Texas2k_series24_case5_2024highrenewables",
                    "Texas2k_series24_case5_2024highrenewables.RAW", "dynamic_models_case5.dyr",
                    "dynamic_models_case5_gnet.idv", "dynamic_models_case5_MODREMOVE.idv",
                    2965, 10, 15),
            new CaseFile("Texas2k_series24_case6_2024lowloadwithgfm",
                    "Texas2k_series24_case6_2024lowloadwithgfm.RAW", "dynamic_models_case6.dyr",
                    "dynamic_models_case6_gnet.idv", "dynamic_models_case6_MODREMOVE.idv",
                    2970, 10, 15));

    @BeforeAll
    static void initializePlugin() {
        IpssCorePlugin.init();
    }

    @Test
    void everyRecordEitherAttachesOrIsIntentionallyRemovedByCasePreparation(@TempDir Path tempDir)
            throws Exception {
        assumeTrue(Files.isDirectory(ROOT), "Missing private Texas2k root: " + ROOT);
        Path emptyDyr = tempDir.resolve("empty.dyr");
        Files.writeString(emptyDyr, "");

        for (CaseFile source : CASES) {
            Path directory = ROOT.resolve(source.directory());
            Path raw = directory.resolve(source.raw());
            Path dyr = directory.resolve(source.dyr());
            Path gnet = directory.resolve(source.gnet());
            Path modelRemove = directory.resolve(source.modelRemove());
            assumeTrue(Files.isRegularFile(raw), "Missing Texas2k RAW: " + raw);
            assumeTrue(Files.isRegularFile(dyr), "Missing Texas2k DYR: " + dyr);
            assumeTrue(Files.isRegularFile(gnet), "Missing Texas2k GNET: " + gnet);
            assumeTrue(Files.isRegularFile(modelRemove),
                    "Missing Texas2k model-removal IDV: " + modelRemove);

            BaseDStabNetwork<?, ?> network = new PSSEMultiFileLoader()
                    .loadDStab(raw.toString(), emptyDyr.toString()).getDStabilityNet();
            var gnetResult = PsseGnetIdvProcessor.apply(network, gnet.toString());
            var modelRemoveResult = PsseModelRemoveIdvProcessor.apply(network,
                    modelRemove.toString());
            DStabNetworkBuilder builder = new DStabNetworkBuilder(network);
            PSSEDStabDirectParser parser = new PSSEDStabDirectParser(builder)
                    .setGnetRemovedGenerators(gnetResult.convertedGeneratorKeys())
                    .setModelRemovedGenerators(modelRemoveResult.removedGeneratorKeys());
            parser.parseDynFile(dyr.toString());
            DynamicModelImportReport report = parser.getLastImportReport();
            assertGnetRemovedDetailedModels(network, gnetResult, source.directory());
            assertExactAttachedRuntimeClasses(network, report, source.directory());
            assertNoDuplicateControllerSlots(report, source.directory());
            assertEquals(source.records(), report.totalRecordCount(), source.directory());
            assertEquals(0, report.failures().size(),
                    () -> source.directory() + ": " + summarize(report.failures()));
            assertEquals(source.gnetSkippedRecords(),
                    report.count(DynamicModelImportStatus.SKIPPED_GNET), source.directory());
            assertEquals(source.modelRemovedRecords(),
                    report.count(DynamicModelImportStatus.SKIPPED_MODEL_REMOVE),
                    source.directory());
            if (source.gnetSkippedRecords() > 0) {
                List<DynamicModelImportEntry> skipped = report.entries().stream()
                        .filter(entry -> entry.status() == DynamicModelImportStatus.SKIPPED_GNET)
                        .toList();
                assertEquals(GNET_REMOVED_STACKS,
                        skipped.stream().map(entry -> entry.busNumber() + ":" + entry.deviceId())
                                .collect(Collectors.toSet()),
                        source.directory() + " GNET-removed generator keys");
                assertEquals(REVIEWED_DEPENDENCY_MODELS,
                        skipped.stream().map(DynamicModelImportEntry::canonicalModelName)
                                .collect(Collectors.toSet()),
                        source.directory() + " GNET-skipped controller models");
                assertEquals(15, skipped.stream()
                        .map(entry -> entry.canonicalModelName() + "@" + entry.busNumber()
                                + ":" + entry.deviceId())
                        .collect(Collectors.toSet()).size(),
                        source.directory() + " unique GNET skips");
            }
            if (source.modelRemovedRecords() > 0) {
                List<DynamicModelImportEntry> removed = report.entries().stream()
                        .filter(entry -> entry.status()
                                == DynamicModelImportStatus.SKIPPED_MODEL_REMOVE)
                        .toList();
                assertEquals(MODEL_REMOVED_STACKS,
                        removed.stream().map(entry -> entry.busNumber() + ":"
                                + entry.deviceId()).collect(Collectors.toSet()),
                        source.directory() + " model-removed generator keys");
                assertEquals(REVIEWED_DEPENDENCY_MODELS,
                        removed.stream().map(DynamicModelImportEntry::canonicalModelName)
                                .collect(Collectors.toSet()),
                        source.directory() + " model-removed controller types");
            }
            assertTrue(report.isStrictlyComplete(), source.directory());
        }
    }

    private static void assertExactAttachedRuntimeClasses(BaseDStabNetwork<?, ?> network,
            DynamicModelImportReport report, String caseName) throws ClassNotFoundException {
        for (DynamicModelImportEntry entry : report.entries()) {
            if (entry.status() != DynamicModelImportStatus.ATTACHED) {
                continue;
            }
            var descriptor = DynamicModelCatalog.find(entry.canonicalModelName())
                    .orElseThrow(() -> new AssertionError(caseName
                            + " attached an uncataloged model " + entry.canonicalModelName()));
            Object actual = runtimeModelFor(network, entry, descriptor.category());
            assertNotNull(actual, caseName + " missing attached runtime model "
                    + entry.canonicalModelName() + "@" + entry.busNumber() + ":"
                    + entry.deviceId());
            Class<?> expected = Class.forName(descriptor.runtimeClassName());
            assertTrue(expected.isInstance(actual), () -> caseName + " mapped "
                    + entry.canonicalModelName() + "@" + entry.busNumber() + ":"
                    + entry.deviceId() + " to " + actual.getClass().getName()
                    + " instead of " + descriptor.runtimeClassName());
        }
    }

    private static Object runtimeModelFor(BaseDStabNetwork<?, ?> network,
            DynamicModelImportEntry entry, DynamicModelCategory category) {
        var bus = network.getDStabBus("Bus" + entry.busNumber());
        if (category == DynamicModelCategory.LOAD_CHARACTERISTIC) {
            return bus.getDynLoadModelList().stream()
                    .filter(model -> model.getId().equals(entry.deviceId()))
                    .findFirst().orElse(null);
        }
        if (category == DynamicModelCategory.LOAD_PROTECTION) {
            return bus.getDynamicBusDeviceList().stream()
                    .filter(device -> device.getName().equals(entry.canonicalModelName())
                            && device.getId().endsWith("_" + entry.deviceId()))
                    .findFirst().orElse(null);
        }
        if (category == DynamicModelCategory.GENERATOR_PROTECTION) {
            for (var candidateBus : network.getBusList()) {
                for (var device : candidateBus.getDynamicBusDeviceList()) {
                    if (device.getName().equals(entry.canonicalModelName())
                            && device.getId().contains("_" + entry.busNumber() + "_")) {
                        return device;
                    }
                }
            }
            return null;
        }
        if (category == DynamicModelCategory.SWITCHED_SHUNT) {
            return bus.getDynamicBusDeviceList().stream()
                    .filter(device -> device.getName().equals(entry.canonicalModelName())
                            && (entry.sourceModelName().equals("SVSMO1T2")
                                    || device.getId().equals(entry.deviceId())))
                    .findFirst().orElse(null);
        }
        DStabGen generator = (DStabGen) bus
                .getContributeGen(entry.deviceId());
        assertNotNull(generator, "Missing generator for attached record " + entry);
        var machine = generator.getMach();
        return switch (category) {
            case SYNCHRONOUS_MACHINE -> machine;
            case COMPENSATOR -> machine instanceof org.interpss.dstab.mach.IeeeVoltageCompensatedMachine
                    compensated && compensated.getIeeeVcData() != null ? compensated : null;
            case EXCITER -> machine == null ? null : machine.getExciter();
            case GOVERNOR -> machine == null ? null : machine.getGovernor();
            case STABILIZER -> machine == null ? null : machine.getStabilizer();
            case UNDER_EXCITATION_LIMITER -> bus.getDynamicBusDeviceList().stream()
                    .filter(device -> device.getName().equals(entry.canonicalModelName())
                            && device.getId().equals(entry.deviceId()))
                    .findFirst().orElse(null);
            case CONVERTER_MACHINE -> generator.getDynamicGenDevice();
            case ELECTRICAL_CONTROLLER -> regca(generator) == null ? null
                    : regca(generator).getActiveElectricalController();
            case PLANT_CONTROLLER -> plantController(generator);
            case DRIVE_TRAIN -> windStack(generator) == null ? null
                    : windStack(generator).getDriveTrain();
            case AERODYNAMIC_CONTROLLER -> windStack(generator) == null ? null
                    : windStack(generator).getAerodynamics();
            case PITCH_CONTROLLER -> windStack(generator) == null ? null
                    : windStack(generator).getPitchController();
            case TORQUE_CONTROLLER -> windStack(generator) == null ? null
                    : windStack(generator).getTorqueController();
            case LOAD_CHARACTERISTIC -> throw new IllegalStateException(
                    "load characteristics are resolved before generator models");
            case LOAD_PROTECTION -> throw new IllegalStateException(
                    "load protection is resolved before generator models");
            case GENERATOR_PROTECTION -> throw new IllegalStateException(
                    "generator protection is resolved before generator controllers");
            case SWITCHED_SHUNT -> throw new IllegalStateException(
                    "switched shunts are resolved before generator models");
        };
    }

    private static Regca1Model regca(DStabGen generator) {
        return generator.getDynamicGenDevice() instanceof Regca1Model model ? model : null;
    }

    private static Object plantController(DStabGen generator) {
        if (generator.getDynamicGenDevice() instanceof Regca1Model model) {
            return model.getActiveElectricalController() == null ? null
                    : model.getActiveElectricalController().getPlantController();
        }
        if (generator.getDynamicGenDevice() instanceof Regfma1Model model) {
            return model.getPlantController();
        }
        return null;
    }

    private static WindControlStack windStack(DStabGen generator) {
        Regca1Model converter = regca(generator);
        if (converter == null || !(converter.getActiveElectricalController()
                instanceof Reeca1Model controller)) {
            return null;
        }
        return controller.getWindControlStack();
    }

    private static void assertNoDuplicateControllerSlots(DynamicModelImportReport report,
            String caseName) {
        Map<String, Long> occupiedSlots = report.entries().stream()
                .filter(entry -> entry.status() == DynamicModelImportStatus.ATTACHED)
                .collect(Collectors.groupingBy(entry -> {
                    DynamicModelCategory category = DynamicModelCatalog
                            .find(entry.canonicalModelName()).orElseThrow().category();
                    return entry.busNumber() + ":" + entry.deviceId() + ":" + category;
                }, Collectors.counting()));
        Map<String, Long> duplicates = occupiedSlots.entrySet().stream()
                .filter(entry -> entry.getValue() > 1L)
                .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
        assertTrue(duplicates.isEmpty(), caseName + " duplicate dynamic-model slots " + duplicates);
    }

    private static void assertGnetRemovedDetailedModels(BaseDStabNetwork<?, ?> network,
            PsseGnetIdvProcessor.Result result, String caseName) {
        for (PsseGnetIdvProcessor.GeneratorKey key : result.convertedGeneratorKeys()) {
            DStabGen generator = (DStabGen) network.getDStabBus(key.busId())
                    .getContributeGen(key.generatorId());
            assertFalse(generator.isActive(), caseName + " active GNET generator " + key);
            assertNull(generator.getMach(), caseName + " retained GNET machine " + key);
            assertNull(generator.getDynamicGenDevice(),
                    caseName + " retained GNET dynamic generator device " + key);
            assertTrue(network.getDStabBus(key.busId())
                            .getContributeLoad("GNET-" + key.generatorId()).isActive(),
                    caseName + " missing active GNET replacement load " + key);
        }
    }

    private static String summarize(List<DynamicModelImportEntry> failures) {
        return failures.stream().limit(20)
                .map(entry -> entry.canonicalModelName() + "@" + entry.busNumber()
                        + ":" + entry.deviceId() + "=" + entry.message())
                .collect(Collectors.joining(", "));
    }

    private record CaseFile(String directory, String raw, String dyr, String gnet,
            String modelRemove, int records, int modelRemovedRecords,
            int gnetSkippedRecords) { }
}
