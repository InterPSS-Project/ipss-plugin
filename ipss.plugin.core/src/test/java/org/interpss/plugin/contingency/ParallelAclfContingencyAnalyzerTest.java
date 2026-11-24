package org.interpss.plugin.contingency;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentLinkedQueue;

import org.apache.commons.math3.complex.Complex;
import org.interpss.IpssCorePlugin;
import org.interpss.plugin.aclf.config.AclfRunConfigRec;
import org.interpss.plugin.contingency.result.AclfContingencyResultContainer;
import org.interpss.plugin.contingency.result.AclfContingencyResultRec;
import org.interpss.plugin.contingency.aux_fmt.mapper.AclfNetworkAuxBranchResolver;
import com.interpss.core.contingency.definition.ContingencyAction;
import com.interpss.core.contingency.definition.ContingencyActionType;
import com.interpss.core.contingency.definition.ContingencyDefinition;
import com.interpss.core.contingency.definition.ContingencyObjectType;
import org.interpss.plugin.contingency.util.AclfContingencyDefinitionHelper;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import com.interpss.common.exp.InterpssException;
import com.interpss.core.AclfContingencyObjectFactory;
import com.interpss.core.CoreObjectFactory;
import com.interpss.core.LoadflowAlgoObjectFactory;
import com.interpss.core.aclf.AclfBranch;
import com.interpss.core.aclf.AclfBus;
import com.interpss.core.aclf.AclfGenCode;
import com.interpss.core.aclf.AclfLoadCode;
import com.interpss.core.aclf.AclfNetwork;
import com.interpss.core.algo.AcPowerMismatchSummary;
import com.interpss.core.algo.LoadflowTerminationReport;
import com.interpss.core.algo.LoadflowTerminationReport.Status;
import com.interpss.core.algo.LoadflowAlgorithm;
import com.interpss.core.algo.LoadflowAlgorithmInitializer;
import com.interpss.core.algo.contingency.aclf.AcContingencyReportStatus;
import com.interpss.opf.OpfBranch;
import com.interpss.opf.OpfBus;
import com.interpss.opf.OpfNetwork;
import com.interpss.opf.OpfObjectFactory;

class ParallelAclfContingencyAnalyzerTest {
    @BeforeAll
    static void initializeCore() {
        IpssCorePlugin.init();
    }

    @Test
    void reportsUnsettledAndEligibleNearSolutionsForPlanning() {
        var unsettled = new AclfContingencyResultRec(false, new LoadflowTerminationReport(
                Status.CONTROLS_UNSETTLED, 6, 0.003, 0.001, "load", 0.002, "load",
                0, 1, List.of("SHUNT|load")));
        var near = new AclfContingencyResultRec(false, new LoadflowTerminationReport(
                Status.MAX_ITERATIONS, 6, 0.003, 0.004, "load", 0.002, "load",
                0, 0, List.of(), new AcPowerMismatchSummary(0.4, 0.2, 1.1)));
        var excessiveSystemMismatch = new AclfContingencyResultRec(false, new LoadflowTerminationReport(
                Status.MAX_ITERATIONS, 6, 0.003, 0.004, "load", 0.002, "load",
                0, 0, List.of(), new AcPowerMismatchSummary(0.4, 0.2, 5.1)));
        var results = new AclfContingencyResultContainer<>(
                Map.of("unsettled", unsettled, "near", near, "non-converged", excessiveSystemMismatch),
                0, 3, 0);

        assertEquals(AcContingencyReportStatus.CONTROLS_UNSETTLED, unsettled.reportStatus());
        assertEquals(List.of("SHUNT|load"), unsettled.getTerminationReport().unsettledControlDetails());
        assertEquals(AcContingencyReportStatus.TREATED_AS_CONVERGED, near.reportStatus());
        assertEquals(Status.MAX_ITERATIONS, near.getTerminationReport().status());
        assertFalse(excessiveSystemMismatch.isReportable());
        assertEquals(2, results.getReportableCaseCount());
        assertEquals(0, results.getTotalSuccessCount());
    }

    @Test
    void typedOutagesInheritTheImportedProfileAndPreserveSourceAndOrder() throws Exception {
        AclfNetwork network = createAclfTriangle();
        assertTrue(LoadflowAlgoObjectFactory.createLoadflowAlgorithm(network).loadflow());
        network.getExtraInfo().put(LoadflowAlgorithmInitializer.NETWORK_EXTRA_INFO_KEY,
                (LoadflowAlgorithmInitializer) (algorithm, workingCase) -> {
                    algorithm.setInitBusVoltage(false);
                    algorithm.setMaxIterations(40);
                    algorithm.setTolerance(0.002);
                });
        var first = AclfContingencyObjectFactory.createAclfMultiOutage("z-first");
        first.getOutageEquips().add(AclfContingencyObjectFactory.createAclfBranchOutage(
                network.getBranchList().get(0)));
        var second = AclfContingencyObjectFactory.createAclfMultiOutage("a-second");
        second.getOutageEquips().add(AclfContingencyObjectFactory.createAclfBranchOutage(
                network.getBranchList().get(1)));
        var config = new AclfContingencyConfig();
        config.setParallelism(2);
        var analyzer = new ParallelAclfContingencyAnalyzer<AclfContingencyResultRec>(network);
        for (boolean parallel : List.of(false, true)) {
            var result = analyzer.analyzeOutages(List.of(first, second), config, parallel, true);
            assertEquals(List.of("z-first", "a-second"), new ArrayList<>(result.getCAResults().keySet()));
            assertEquals(2, result.getTotalSuccessCount(), () -> result.getDiagnostics().toString());
            for (var entry : result.getCAResults().values()) {
                assertEquals(0.002, entry.getTerminationReport().tolerancePu(), 1e-12);
            }
        }
        assertTrue(network.isLfConverged());
        assertTrue(network.getBranchList().stream().allMatch(AclfBranch::isActive));
    }

    @Test
    void sharedLoadflowProfileReachesTypedOutagesAndPreservesExplicitStudyOverrides() throws Exception {
        AclfNetwork network = createAclfTriangle();
        assertTrue(LoadflowAlgoObjectFactory.createLoadflowAlgorithm(network).loadflow());
        network.getExtraInfo().put(LoadflowAlgorithmInitializer.NETWORK_EXTRA_INFO_KEY,
                (LoadflowAlgorithmInitializer) (algorithm, workingCase) -> {
                    algorithm.setInitBusVoltage(false);
                    algorithm.setMaxIterations(40);
                    algorithm.setTolerance(0.002);
                });
        var outage = AclfContingencyObjectFactory.createAclfMultiOutage("shared-profile");
        outage.getOutageEquips().add(AclfContingencyObjectFactory.createAclfBranchOutage(
                network.getBranchList().getFirst()));
        var analyzer = new ParallelAclfContingencyAnalyzer<AclfContingencyResultRec>(network);
        var config = new AclfContingencyConfig();

        config.setLoadflowProfile(AclfRunConfigRec.fromJson("{\"schemaVersion\":1}"));
        var inherited = analyzer.analyzeOutages(List.of(outage), config, false, true);
        assertEquals(1, inherited.getTotalSuccessCount(), () -> inherited.getDiagnostics().toString());
        assertEquals(0.002, inherited.getCAResults().get("shared-profile").getTerminationReport().tolerancePu(), 1e-12);

        var profile = AclfRunConfigRec.fromJson("{\"schemaVersion\":1,\"tolerance\":0.1,\"tolUnitType\":\"mVA\"}");
        config.setLoadflowProfile(profile);
        profile.tolerance = 8.0;
        var configured = analyzer.analyzeOutages(List.of(outage), config, false, true);
        assertEquals(1, configured.getTotalSuccessCount(), () -> configured.getDiagnostics().toString());
        assertEquals(0.001, configured.getCAResults().get("shared-profile").getTerminationReport().tolerancePu(), 1e-12);

        config.setTolerance(0.004);
        var overridden = analyzer.analyzeOutages(List.of(outage), config, false, true);
        assertEquals(1, overridden.getTotalSuccessCount(), () -> overridden.getDiagnostics().toString());
        assertEquals(0.004, overridden.getCAResults().get("shared-profile").getTerminationReport().tolerancePu(), 1e-12);
        assertTrue(network.getBranchList().stream().allMatch(AclfBranch::isActive));
    }

    @Test
    void aclfParallelResultsMatchSequentialAndPreserveInputOrder() throws Exception {
        AclfNetwork network = createAclfTriangle();
        assertEquivalentAndUnchanged(network);
    }

    @Test
    void opfParallelResultsMatchSequentialAndPreserveSourceNetwork() throws Exception {
        OpfNetwork network = createOpfTriangle();
        assertEquivalentAndUnchanged(network);

        String branchId = network.getBranchList().get(0).getId();
        assertTrue(new AclfNetworkAuxBranchResolver(network).resolve(branchId).isPresent());
        ContingencyDefinition definition = new ContingencyDefinition(
                "opf-outage",
                List.of(new ContingencyAction(
                        ContingencyObjectType.BRANCH,
                        ContingencyActionType.OPEN,
                        branchId)));
        assertEquals(branchId, new AclfContingencyDefinitionHelper(network)
                .createAclfMultiOutage(definition)
                .getOutageEquips().get(0)
                .getOutageEquip().getId());
    }

    @Test
    void copiedContingenciesInheritImportedSolverOptionsUnlessExplicitlyOverridden() throws Exception {
        AclfNetwork network = createAclfTriangle();
        String key = LoadflowAlgorithmInitializer.NETWORK_EXTRA_INFO_KEY;
        network.getExtraInfo().put(key, (LoadflowAlgorithmInitializer) (algorithm, workingCase) -> {
            algorithm.setMaxIterations(1);
            algorithm.setTolerance(1.0e-10);
        });
        var analyzer = new ParallelAclfContingencyAnalyzer<AclfContingencyResultRec>(network);
        var inherited = new AclfContingencyConfig();
        inherited.setParallelism(2);
        for (boolean parallel : List.of(false, true)) {
            var result = analyzer.analyzeContingencies(3, inherited, parallel);
            assertEquals(0, result.getTotalSuccessCount());
            assertEquals(3, result.getCAResults().size());
            assertTrue(result.getDiagnostics().isEmpty());
        }
        inherited.setMaxIterations(30);
        for (boolean parallel : List.of(false, true)) {
            var result = analyzer.analyzeContingencies(3, inherited, parallel);
            assertEquals(3, result.getTotalSuccessCount());
            for (var outage : result.getCAResults().values()) {
                assertEquals(AcContingencyReportStatus.CONVERGED, outage.reportStatus());
                assertNotNull(outage.getTerminationReport().acPowerMismatch());
            }
            assertTrue(result.getDiagnostics().isEmpty());
        }
        assertTrue(network.getBranchList().stream().allMatch(AclfBranch::isActive));
    }

    @Test
    void contingencyAdjustmentRequestHonorsImportedCategoriesAndExplicitLock() throws Exception {
        AclfNetwork network = createAclfTriangle();
        var algorithms = new ConcurrentLinkedQueue<LoadflowAlgorithm>();
        network.getExtraInfo().put(LoadflowAlgorithmInitializer.NETWORK_EXTRA_INFO_KEY,
                (LoadflowAlgorithmInitializer) (algorithm, workingCase) -> {
                    var voltage = algorithm.getLfAdjAlgo().getVoltAdjConfig();
                    voltage.setSwitchedShuntAdjust(true);
                    voltage.setXfrTapControl(false);
                    algorithms.add(algorithm);
                });
        var analyzer = new ParallelAclfContingencyAnalyzer<AclfContingencyResultRec>(network);
        var inherited = new AclfContingencyConfig();
        inherited.setParallelism(2);
        var allowed = new AclfContingencyConfig();
        allowed.setApplyAdjustAlgo(true);
        allowed.setParallelism(2);
        var locked = new AclfContingencyConfig();
        locked.setApplyAdjustAlgo(false);
        locked.setParallelism(2);
        for (boolean parallel : List.of(false, true)) {
            for (var config : List.of(allowed, locked, inherited)) {
                algorithms.clear();
                var result = analyzer.analyzeContingencies(3, config, parallel);
                assertEquals(3, result.getCAResults().size());
                assertTrue(result.getDiagnostics().isEmpty());
                assertEquals(3, algorithms.size());
                for (var algorithm : algorithms) {
                    assertNotSame(network, algorithm.getNetwork());
                    var adjust = algorithm.getLfAdjAlgo();
                    assertEquals(config.isApplyAdjustAlgo(), adjust.isApplyAdjustAlgo());
                    assertTrue(adjust.getVoltAdjConfig().isSwitchedShuntAdjust());
                    assertFalse(adjust.getVoltAdjConfig().isXfrTapControl());
                }
            }
        }
        assertTrue(network.getBranchList().stream().allMatch(AclfBranch::isActive));
    }

    private static void assertEquivalentAndUnchanged(
            com.interpss.core.aclf.BaseAclfNetwork<?, ?> network) {
        List<String> expectedOrder = network.getBranchList().stream().map(AclfBranch::getId).toList();
        List<Boolean> originalStatuses = network.getBranchList().stream()
                .map(AclfBranch::isActive)
                .toList();
        AclfContingencyConfig config = new AclfContingencyConfig();
        config.setParallelism(2);

        ParallelAclfContingencyAnalyzer<AclfContingencyResultRec> analyzer =
                new ParallelAclfContingencyAnalyzer<>(network);
        AclfContingencyResultContainer<AclfContingencyResultRec> sequential =
                analyzer.analyzeContingencies(expectedOrder.size(), config, false);
        AclfContingencyResultContainer<AclfContingencyResultRec> parallel =
                analyzer.analyzeContingencies(expectedOrder.size(), config, true);

        assertEquals(expectedOrder, new ArrayList<>(sequential.getCAResults().keySet()));
        assertEquals(expectedOrder, new ArrayList<>(parallel.getCAResults().keySet()));
        assertEquals(sequential.getTotalSuccessCount(), parallel.getTotalSuccessCount());
        for (String branchId : expectedOrder) {
            assertEquals(sequential.getCAResults().get(branchId).isConverged(),
                    parallel.getCAResults().get(branchId).isConverged());
        }
        assertEquals(originalStatuses, network.getBranchList().stream()
                .map(AclfBranch::isActive)
                .toList());
        assertTrue(parallel.getDiagnostics().isEmpty());
    }

    private static AclfNetwork createAclfTriangle() throws InterpssException {
        AclfNetwork network = CoreObjectFactory.createAclfNetwork();
        network.setId("aclf-triangle");
        network.setBaseKva(100000.0);

        AclfBus bus1 = CoreObjectFactory.createAclfBus("Bus1", network).orElseThrow();
        bus1.setGenCode(AclfGenCode.SWING);
        bus1.setGenP(1.2);
        bus1.setVoltageMag(1.0);
        bus1.setBaseVoltage(138000.0);
        AclfBus bus2 = CoreObjectFactory.createAclfBus("Bus2", network).orElseThrow();
        bus2.setLoadCode(AclfLoadCode.CONST_P);
        bus2.setLoadP(0.5);
        bus2.setVoltageMag(1.0);
        bus2.setBaseVoltage(138000.0);
        AclfBus bus3 = CoreObjectFactory.createAclfBus("Bus3", network).orElseThrow();
        bus3.setLoadCode(AclfLoadCode.CONST_P);
        bus3.setLoadP(0.7);
        bus3.setVoltageMag(1.0);
        bus3.setBaseVoltage(138000.0);

        addAclfBranch(network, "Bus1", "Bus2", 0.10);
        addAclfBranch(network, "Bus2", "Bus3", 0.12);
        addAclfBranch(network, "Bus1", "Bus3", 0.15);
        return network;
    }

    private static void addAclfBranch(
            AclfNetwork network,
            String fromBusId,
            String toBusId,
            double reactance) throws InterpssException {
        AclfBranch branch = CoreObjectFactory.createAclfBranch();
        branch.setZ(new Complex(0.0, reactance));
        network.addBranch(branch, fromBusId, toBusId, "1");
    }

    private static OpfNetwork createOpfTriangle() throws InterpssException {
        OpfNetwork network = OpfObjectFactory.createOpfNetwork();
        network.setId("opf-triangle");
        network.setBaseKva(100000.0);

        OpfBus bus1 = OpfObjectFactory.createOpfGenBus("Bus1", network);
        bus1.setGenCode(AclfGenCode.SWING);
        bus1.setGenP(1.2);
        bus1.setVoltageMag(1.0);
        bus1.setBaseVoltage(138000.0);
        bus1.getOpfGen().setId("Gen1");
        bus1.getOpfGen().setGen(new Complex(1.2, 0.0));
        OpfBus bus2 = OpfObjectFactory.createOpfBus("Bus2", network);
        bus2.setLoadCode(AclfLoadCode.CONST_P);
        bus2.setLoadP(0.5);
        bus2.setVoltageMag(1.0);
        bus2.setBaseVoltage(138000.0);
        OpfBus bus3 = OpfObjectFactory.createOpfBus("Bus3", network);
        bus3.setLoadCode(AclfLoadCode.CONST_P);
        bus3.setLoadP(0.7);
        bus3.setVoltageMag(1.0);
        bus3.setBaseVoltage(138000.0);

        addOpfBranch(network, "Bus1", "Bus2", 0.10);
        addOpfBranch(network, "Bus2", "Bus3", 0.12);
        addOpfBranch(network, "Bus1", "Bus3", 0.15);
        return network;
    }

    private static void addOpfBranch(
            OpfNetwork network,
            String fromBusId,
            String toBusId,
            double reactance) throws InterpssException {
        OpfBranch branch = OpfObjectFactory.createOpfBranch();
        branch.setZ(new Complex(0.0, reactance));
        network.addBranch(branch, fromBusId, toBusId, "1");
    }
}
