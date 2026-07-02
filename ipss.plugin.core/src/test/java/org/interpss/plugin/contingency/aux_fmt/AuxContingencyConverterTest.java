package org.interpss.plugin.contingency.aux_fmt;

import static com.interpss.core.DclfAlgoObjectFactory.createCaMonitoringBranch;
import static com.interpss.core.DclfAlgoObjectFactory.createContingencyAnalysisAlgorithm;
import static org.interpss.plugin.pssl.plugin.IpssAdapter.FileFormat.PSSE;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.stream.Collectors;

import org.interpss.plugin.contingency.definition.BranchContingencyRecord;
import org.interpss.plugin.contingency.util.ContingencyFileUtil;
import org.interpss.plugin.contingency.util.DclfMultiOutageContingencyHelper;
import org.interpss.plugin.pssl.plugin.IpssAdapter;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.interpss.algo.parallel.BranchCAResultRec;
import com.interpss.common.exp.InterpssException;
import com.interpss.core.aclf.AclfBranch;
import com.interpss.core.aclf.AclfNetwork;
import com.interpss.core.algo.dclf.ContingencyAnalysisAlgorithm;
import com.interpss.core.algo.dclf.solver.DclfContingencySolutionMethod;
import com.interpss.core.algo.dclf.solver.ParallelDclfContingencyAnalyzer;
import com.interpss.core.contingency.dclf.DclfMonitoringBranch;
import com.interpss.core.contingency.dclf.DclfMultiOutage;
import com.interpss.monitor.definition.MonitoredBranchRecord;

public class AuxContingencyConverterTest {
    private static final Path TEXAS7K_DIR = resolveTexas7kDir();
    private static final Path TEXAS7K_RAW = TEXAS7K_DIR.resolve("Texas7k_20210804.RAW");
    private static final Path TEXAS7K_LABELED_RAW = TEXAS7K_DIR.resolve("Texas7k_20210804_labeled.RAW");
    private static final Path TEXAS7K_MONITORS = TEXAS7K_DIR.resolve("Texas7k_20210804_monitored_branches.json");
    private static final Path TEXAS7K_AUX_SAMPLE =
            Path.of("testData/powerworld/texas7k/Texas7k_20210804_aux_contingencies_100.aux");
    private static final Path TEXAS7K_CONVERTED_JSON =
            Path.of("testData/powerworld/texas7k/Texas7k_20210804_aux_contingencies_100_converted.json");
    private static final Path TEXAS7K_LABELED_AUX_SAMPLE =
            Path.of("testData/powerworld/texas7k/Texas7k_20210804_labeled_aux_contingencies_100.aux");
    private static final Path TEXAS7K_LABELED_CONVERTED_JSON =
            Path.of("testData/powerworld/texas7k/Texas7k_20210804_labeled_aux_contingencies_100_converted.json");

    @TempDir
    Path tempDir;

    @Test
    public void convertsAndGroupsSimpleAuxFile() throws Exception {
        Path input = tempDir.resolve("sample.aux");
        Path output = tempDir.resolve("sample.json");
        Files.writeString(input, """
                DATA (Contingency, [Name])
                {
                  "CTG_A"
                  "CTG_B"
                }
                DATA (CTGElement, [Contingency, ElementType, Action, BusNumFrom, BusNumTo, Circuit])
                {
                  "CTG_A", "Branch", "Open", 1001, 1064, "1"
                  "CTG_A", "Line", "Trip", 1002, 1007, "2"
                  "CTG_B", "Branch", "Close", "Bus5", "Bus6", "1"
                }
                """, StandardCharsets.UTF_8);

        AuxConversionReport report = new AuxContingencyConverter(importIeee9Labeled())
                .convert(input.toFile(), output.toFile(), AuxConversionOptions.defaultOptions());
        List<BranchContingencyRecord> records = ContingencyFileUtil.importContingenciesFromJson(output.toFile());

        assertEquals(2, report.getContingencyCount());
        assertEquals(3, report.getCtgElementCount());
        assertEquals(3, report.getEmittedBranchRecordCount());
        assertEquals(3, records.size());
        assertEquals("CTG_A", records.get(0).name);
        assertEquals("CTG_A", records.get(1).name);
        assertEquals("OPEN", records.get(0).actionType);
        assertEquals("OPEN", records.get(1).actionType);
        assertEquals("CLOSE", records.get(2).actionType);
        assertEquals("Bus1001", records.get(0).fromBus);
        assertEquals("Bus1007", records.get(1).toBus);
        assertEquals("Bus5", records.get(2).fromBus);
    }

    @Test
    public void convertsPowerWorldObjectSyntaxFromBranchExtUid() throws Exception {
        AclfNetwork net = importIeee9Labeled();
        AclfBranch branch = branch(net, "Bus4", "Bus5", "0");
        branch.setExtUID("powerworld_custom_branch_uid");

        Path input = tempDir.resolve("powerworld-object.aux");
        Path output = tempDir.resolve("powerworld-object.json");
        Files.writeString(input, """
                Contingency (Name,Skip,Category,Processed,Solved,RemedialActionInclude,ScreenAllow,
                   PostCTGAuxFile,LoadMWIslanded,GenMWIslanded,OccurredGlobalCount,
                   OccurredTransientCount,OccurredRemedialCount,ViolCustom,Violations,
                   BranchMaxPerc,MinVolt,MaxVolt,InterfaceMaxPerc,BusPairAngleMax,Memo)
                {
                "TX7KLBL001" "NO " "" "NO " "NO" "YES" "NO " "none" "" "" "" "" "" "" "" "" "" "" "" "" ""
                }

                ContingencyElement (Contingency,Object,Action,Criteria,CriteriaStatus,TimeDelay,Persistent,Comment)
                {
                "TX7KLBL001" "BRANCH 'powerworld_custom_branch_uid'" "OPEN" "" "CHECK"     0 "NO " ""
                }
                """, StandardCharsets.UTF_8);

        AuxConversionReport report = new AuxContingencyConverter(net)
                .convert(input.toFile(), output.toFile(), AuxConversionOptions.defaultOptions());
        List<BranchContingencyRecord> records = ContingencyFileUtil.importContingenciesFromJson(output.toFile());

        assertEquals(1, report.getContingencyCount());
        assertEquals(1, report.getCtgElementCount());
        assertEquals(1, report.getEmittedBranchRecordCount());
        assertEquals(0, report.getSkippedElementCount());
        assertEquals(0, report.getUnsupportedElementCount());
        assertEquals(1, records.size());

        BranchContingencyRecord record = records.get(0);
        assertEquals("TX7KLBL001", record.name);
        assertEquals("OPEN", record.actionType);
        assertEquals("Bus4", record.fromBus);
        assertEquals("Bus5", record.toBus);
        assertEquals("0", record.ckt);
        assertEquals(230.0, record.baseKv, 1.0e-6);
    }

    @Test
    public void importsAuxDirectlyToDclfMultiOutageList() throws Exception {
        AclfNetwork net = importIeee9Labeled();
        AclfBranch branch = branch(net, "Bus4", "Bus5", "0");
        branch.setExtUID("direct_import_branch_uid");

        Path input = tempDir.resolve("direct-dclf.aux");
        Files.writeString(input, """
                Contingency (Name)
                {
                "DIRECT_A"
                }

                ContingencyElement (Contingency,Object,Action)
                {
                "DIRECT_A" "BRANCH 'direct_import_branch_uid'" "OPEN"
                }
                """, StandardCharsets.UTF_8);

        ContingencyAnalysisAlgorithm dclfAlgo = createContingencyAnalysisAlgorithm(net);
        assertTrue(dclfAlgo.calculateDclf(), "IEEE9 base DCLF should converge");

        List<DclfMultiOutage> outages = new AuxContingencyConverter(net)
                .importDclfMultiOutages(input.toFile(), dclfAlgo, AuxConversionOptions.defaultOptions());

        assertEquals(1, outages.size());
        assertEquals("DIRECT_A", outages.get(0).getId());
        assertEquals(1, outages.get(0).getOutageEquips().size());
    }

    @Test
    public void texas7kLabeledPowerWorldAuxRoundTripsToJson() throws Exception {
        assumeTrue(Files.isRegularFile(TEXAS7K_LABELED_RAW),
                "Texas7k labeled RAW is not available: " + TEXAS7K_LABELED_RAW);
        assumeTrue(Files.isRegularFile(TEXAS7K_LABELED_AUX_SAMPLE),
                "Texas7k labeled AUX sample is not available: " + TEXAS7K_LABELED_AUX_SAMPLE);
        assumeTrue(Files.isRegularFile(TEXAS7K_LABELED_CONVERTED_JSON),
                "Texas7k labeled converted JSON is not available: " + TEXAS7K_LABELED_CONVERTED_JSON);

        Path reconverted = tempDir.resolve("texas7k-labeled-reconverted.json");
        AuxConversionReport report = new AuxContingencyConverter(importPsse(TEXAS7K_LABELED_RAW)).convert(
                TEXAS7K_LABELED_AUX_SAMPLE.toFile(),
                reconverted.toFile(),
                AuxConversionOptions.defaultOptions());
        List<BranchContingencyRecord> reconvertedRecords =
                ContingencyFileUtil.importContingenciesFromJson(reconverted.toFile());
        List<BranchContingencyRecord> fixtureRecords =
                ContingencyFileUtil.importContingenciesFromJson(TEXAS7K_LABELED_CONVERTED_JSON.toFile());

        assertEquals(100, report.getContingencyCount());
        assertEquals(250, report.getCtgElementCount());
        assertEquals(250, report.getEmittedBranchRecordCount());
        assertEquals(0, report.getSkippedElementCount());
        assertEquals(0, report.getUnsupportedElementCount());
        assertEquals(signature(fixtureRecords), signature(reconvertedRecords));

        Map<String, List<BranchContingencyRecord>> groups = recordsByName(reconvertedRecords);
        assertEquals(100, groups.size());
        assertTrue(groups.values().stream().allMatch(group -> group.size() >= 1 && group.size() <= 4));

        BranchContingencyRecord first = reconvertedRecords.get(0);
        assertEquals("TX7KLBL001", first.name);
        assertEquals("OPEN", first.actionType);
        assertEquals("Bus110126", first.fromBus);
        assertEquals("Bus111216", first.toBus);
        assertEquals("1", first.ckt);
        assertEquals(345.0, first.baseKv, 1.0e-6);
    }

    @Test
    public void texas7kConvertedAuxJsonRunsDcContingencyAndDirectApiCheck() throws Exception {
        assumeTrue(Files.isRegularFile(TEXAS7K_RAW), "Texas7k RAW is not available: " + TEXAS7K_RAW);
        assumeTrue(Files.isRegularFile(TEXAS7K_AUX_SAMPLE), "Texas7k AUX sample is not available: " + TEXAS7K_AUX_SAMPLE);
        assumeTrue(Files.isRegularFile(TEXAS7K_CONVERTED_JSON),
                "Texas7k converted JSON is not available: " + TEXAS7K_CONVERTED_JSON);
        assumeTrue(Files.isRegularFile(TEXAS7K_MONITORS), "Texas7k monitor JSON is not available: " + TEXAS7K_MONITORS);

        Path reconverted = tempDir.resolve("texas7k-reconverted.json");
        AuxConversionReport report = new AuxContingencyConverter(importPsse(TEXAS7K_RAW))
                .convert(TEXAS7K_AUX_SAMPLE.toFile(), reconverted.toFile(), AuxConversionOptions.defaultOptions());
        List<BranchContingencyRecord> reconvertedRecords =
                ContingencyFileUtil.importContingenciesFromJson(reconverted.toFile());
        List<BranchContingencyRecord> fixtureRecords =
                ContingencyFileUtil.importContingenciesFromJson(TEXAS7K_CONVERTED_JSON.toFile());

        assertEquals(100, report.getContingencyCount());
        assertEquals(fixtureRecords.size(), report.getEmittedBranchRecordCount());
        assertEquals(signature(fixtureRecords), signature(reconvertedRecords));
        assertEquals(100, recordsByName(fixtureRecords).size());
        assertTrue(recordsByName(fixtureRecords).values().stream().allMatch(group ->
                group.size() >= 1 && group.size() <= 4));

        AclfNetwork net = importPsse(TEXAS7K_RAW);
        ContingencyAnalysisAlgorithm sourceAlgo = createContingencyAnalysisAlgorithm(net);
        assertTrue(sourceAlgo.calculateDclf(), "Texas7k base DCLF should converge");

        List<DclfMultiOutage> multiOutages =
                new DclfMultiOutageContingencyHelper(sourceAlgo).createDclfMultiOutageContList(fixtureRecords);
        assertEquals(100, multiOutages.size());
        assertTrue(multiOutages.stream().anyMatch(outage -> outage.getOutageEquips().size() == 4));

        Set<String> monitorIds = monitoredBranchIds(net,
                ContingencyFileUtil.importMonitoredBranchRecordsFromJson(TEXAS7K_MONITORS.toFile()),
                80);
        assertFalse(monitorIds.isEmpty(), "Texas7k monitor set should not be empty");

        List<DclfMultiOutage> runnableOutages = runnableMultiOutages(net, fixtureRecords, 20);
        assertFalse(runnableOutages.isEmpty(), "Converted JSON should contain runnable DC contingencies");
        assertTrue(runnableOutages.stream().anyMatch(outage -> outage.getOutageEquips().size() == 4),
                "Runnable converted contingencies should include a four-line outage group");

        ConcurrentLinkedQueue<BranchCAResultRec> batchResults =
                ParallelDclfContingencyAnalyzer.performContingencyAnalysis(
                        net,
                        runnableOutages,
                        monitorIds,
                        0.0,
                        false,
                        4,
                        DclfContingencySolutionMethod.WoodburyMatrixUpdate);
        assertFalse(batchResults.isEmpty(), "Converted Texas7k contingencies should produce DC CA results");

        DclfMultiOutage selected = runnableOutages.stream()
                .filter(outage -> outage.getOutageEquips().size() == 4)
                .findFirst()
                .orElseThrow();
        DirectApiResult directResult = runDirectApi(net, fixtureRecords, selected.getId(), monitorIds);
        assertEquals(selected.getOutageEquips().size(), directResult.outageCount());
        assertEquals(monitorIds.size(), directResult.monitorShiftMwByBranch().size());

        Map<String, Double> batchSelected = batchResults.stream()
                .filter(result -> selected.getId().equals(result.contingency.getId()))
                .collect(Collectors.toMap(
                        result -> result.aclfBranch.getId(),
                        result -> result.shiftedFlowMW,
                        (left, right) -> left,
                        LinkedHashMap::new));
        assertFalse(batchSelected.isEmpty(), "Batch DC CA should include the selected contingency");

        String commonMonitor = batchSelected.keySet().iterator().next();
        assertEquals(
                directResult.monitorShiftMwByBranch().get(commonMonitor),
                batchSelected.get(commonMonitor),
                1.0e-6);
    }

    private static List<DclfMultiOutage> runnableMultiOutages(
            AclfNetwork net,
            List<BranchContingencyRecord> records,
            int targetCount)
            throws InterpssException {
        ContingencyAnalysisAlgorithm dclfAlgo = createContingencyAnalysisAlgorithm(net);
        assertTrue(dclfAlgo.calculateDclf(), "Screening API base DCLF should converge");
        DclfMultiOutageContingencyHelper helper = new DclfMultiOutageContingencyHelper(dclfAlgo);

        List<DclfMultiOutage> runnable = new java.util.ArrayList<>();
        for (Map.Entry<String, List<BranchContingencyRecord>> entry : recordsByName(records).entrySet()) {
            DclfMultiOutage contingency = helper.createDclfMultiOutage(entry.getKey(), entry.getValue());
            try {
                dclfAlgo.ca(contingency);
                runnable.add(contingency);
            } catch (InterpssException | RuntimeException e) {
                // Some validly converted Texas7k multi-line outages island the network.
                // Use solver-admissible groups for the DC CA comparison.
            }
            boolean hasFourLineGroup = runnable.stream().anyMatch(outage -> outage.getOutageEquips().size() == 4);
            if (runnable.size() >= targetCount && hasFourLineGroup) {
                break;
            }
        }
        return runnable;
    }

    private static DirectApiResult runDirectApi(
            AclfNetwork net,
            List<BranchContingencyRecord> records,
            String contingencyName,
            Set<String> monitorIds)
            throws InterpssException {
        ContingencyAnalysisAlgorithm dclfAlgo = createContingencyAnalysisAlgorithm(net);
        assertTrue(dclfAlgo.calculateDclf(), "Direct API base DCLF should converge");

        List<BranchContingencyRecord> selectedRecords = recordsByName(records).get(contingencyName);
        DclfMultiOutage contingency =
                new DclfMultiOutageContingencyHelper(dclfAlgo).createDclfMultiOutage(contingencyName, selectedRecords);
        for (String monitorId : monitorIds) {
            contingency.addMonitoringBranch(createCaMonitoringBranch(dclfAlgo.getDclfAlgoBranch(monitorId)));
        }
        dclfAlgo.ca(contingency);

        Map<String, Double> shiftedByBranch = new LinkedHashMap<>();
        for (DclfMonitoringBranch monitoringBranch : contingency.getMonitoringBranches()) {
            shiftedByBranch.put(
                    monitoringBranch.getBranch().getId(),
                    monitoringBranch.getShiftedFlow() * net.getBaseMva());
        }
        return new DirectApiResult(contingency.getOutageEquips().size(), shiftedByBranch);
    }

    private static Map<String, List<BranchContingencyRecord>> recordsByName(List<BranchContingencyRecord> records) {
        Map<String, List<BranchContingencyRecord>> byName = new LinkedHashMap<>();
        for (BranchContingencyRecord record : records) {
            byName.computeIfAbsent(record.name, ignored -> new java.util.ArrayList<>()).add(record);
        }
        return byName;
    }

    private static List<String> signature(List<BranchContingencyRecord> records) {
        return records.stream()
                .map(record -> record.name + "|" + record.actionType + "|" + record.fromBus
                        + "|" + record.toBus + "|" + record.ckt)
                .collect(Collectors.toList());
    }

    private static Set<String> monitoredBranchIds(
            AclfNetwork net,
            List<MonitoredBranchRecord> monitorRecords,
            int maxCount) {
        Set<String> branchIds = new LinkedHashSet<>();
        for (MonitoredBranchRecord record : monitorRecords) {
            String branchId = resolveBranchId(net, record.fromBus, record.toBus, record.ckt);
            AclfBranch branch = branchId != null ? net.getBranch(branchId) : null;
            if (branch != null && branch.isActive() && !branch.isConnect2RefBus()) {
                branchIds.add(branchId);
                if (branchIds.size() >= maxCount) {
                    break;
                }
            }
        }
        return branchIds;
    }

    private static String resolveBranchId(AclfNetwork net, String fromBus, String toBus, String circuit) {
        String forward = fromBus + "->" + toBus + "(" + circuit + ")";
        if (net.getBranch(forward) != null) {
            return forward;
        }
        String reverse = toBus + "->" + fromBus + "(" + circuit + ")";
        if (net.getBranch(reverse) != null) {
            return reverse;
        }
        return null;
    }

    private static AclfNetwork importPsse(Path path) throws InterpssException {
        return IpssAdapter.importAclfNet(path.toString())
                .setFormat(PSSE)
                .setPsseVersion(IpssAdapter.PsseVersion.PSSE_33)
                .load()
                .getImportedObj();
    }

    private static AclfNetwork importIeee9Labeled() throws InterpssException {
        return IpssAdapter.importAclfNet(resolveTestDataPath(
                        "ipss.test.plugin.core/testData/adpter/psse/v36/ieee9_v36_labeled.raw",
                        "../ipss.test.plugin.core/testData/adpter/psse/v36/ieee9_v36_labeled.raw").toString())
                .setFormat(PSSE)
                .setPsseVersion(IpssAdapter.PsseVersion.PSSE_36)
                .load()
                .getImportedObj();
    }

    private static AclfBranch branch(AclfNetwork net, String fromBusId, String toBusId, String circuitId) {
        AclfBranch branch = net.getBranch(fromBusId, toBusId, circuitId);
        if (branch == null) {
            branch = net.getBranch(toBusId, fromBusId, circuitId);
        }
        assertTrue(branch != null, "Missing branch " + fromBusId + "->" + toBusId + "(" + circuitId + ")");
        return branch;
    }

    private static Path resolveTestDataPath(String first, String second) {
        Path firstPath = Path.of(first);
        if (Files.isRegularFile(firstPath)) {
            return firstPath;
        }
        return Path.of(second);
    }

    private static Path resolveTexas7kDir() {
        List<Path> candidates = List.of(
                Path.of("../ipss-desktop/examples/texas7k"),
                Path.of("../../ipss-desktop/examples/texas7k"),
                Path.of("/Users/ipssdev/github/ipss-desktop/examples/texas7k"));
        return candidates.stream()
                .filter(path -> Files.isRegularFile(path.resolve("Texas7k_20210804.RAW")))
                .findFirst()
                .orElse(candidates.get(0));
    }

    private record DirectApiResult(int outageCount, Map<String, Double> monitorShiftMwByBranch) {
    }
}
