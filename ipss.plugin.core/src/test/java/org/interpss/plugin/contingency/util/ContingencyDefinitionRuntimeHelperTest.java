package org.interpss.plugin.contingency.util;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import org.apache.commons.math3.complex.Complex;
import com.interpss.core.contingency.definition.ContingencyAction;
import com.interpss.core.contingency.definition.ContingencyActionType;
import com.interpss.core.contingency.definition.ContingencyDefinition;
import com.interpss.core.contingency.definition.ContingencyObjectType;
import org.interpss.numeric.sparse.ISparseEqnDouble;
import org.interpss.CorePluginFactory;
import org.junit.jupiter.api.Test;
import org.interpss.numeric.datatype.Unit.UnitType;

import com.interpss.common.exp.InterpssException;
import com.interpss.core.DclfAlgoObjectFactory;
import com.interpss.core.aclf.AclfBranch;
import com.interpss.core.aclf.AclfNetwork;
import com.interpss.core.algo.dclf.ContingencyAnalysisAlgorithm;
import com.interpss.core.algo.dclf.DclfContingencyConfig;
import com.interpss.core.algo.dclf.DclfMethod;
import com.interpss.core.algo.dclf.solver.ParallelDclfContingencyAnalyzer;
import com.interpss.core.algo.dclf.solver.DclfContingencySolutionMethod;
import com.interpss.core.contingency.ContingencyBranchOutageType;
import com.interpss.core.contingency.aclf.AclfBranchOutage;
import com.interpss.core.contingency.aclf.AclfMultiOutage;
import com.interpss.core.contingency.dclf.DclfMultiOutage;
import org.interpss.fadapter.IpssFileAdapter;
import org.interpss.plugin.contingency.util.DclfMultiOutageContingencyHelper.DclfMultiOutageContingencyPlan;

import org.interpss.fadapter.psse.PSSEDirectParser;
public class ContingencyDefinitionRuntimeHelperTest {

    @Test
    public void createsDclfMultiOutageFromGroupedDefinition() throws Exception {
        AclfNetwork net = importIeee9Labeled();
        ContingencyAnalysisAlgorithm dclfAlgo =
                DclfAlgoObjectFactory.createContingencyAnalysisAlgorithm(net);
        assertTrue(dclfAlgo.calculateDclf(), "IEEE9 base DCLF should converge");

        ContingencyDefinition definition = definition(
                "GROUPED_DCLF",
                "Bus4->Bus5(0)",
                "Bus7->Bus8(0)");

        List<DclfMultiOutage> outages =
                new DclfMultiOutageContingencyHelper(dclfAlgo)
                        .createDclfMultiOutageContListFromDefinitions(List.of(definition));

        assertEquals(1, outages.size());
        assertEquals("GROUPED_DCLF", outages.get(0).getId());
        assertEquals(ContingencyBranchOutageType.OPEN, outages.get(0).getOutageType());
        assertEquals(2, outages.get(0).getOutageEquips().size());
        assertEquals("Bus4->Bus5(0)", outages.get(0).getOutageEquips().get(0).getBranch().getId());
        assertEquals(
                dclfAlgo.getDclfAlgoBranch("Bus4->Bus5(0)").getDclfFlow(),
                outages.get(0).getOutageEquips().get(0).getDclfFlow(),
                1.0e-12);
    }

    @Test
    public void createsDclfCloseMultiOutageFromGroupedDefinition() throws Exception {
        AclfNetwork net = importIeee9Labeled();
        ContingencyAnalysisAlgorithm dclfAlgo =
                DclfAlgoObjectFactory.createContingencyAnalysisAlgorithm(net);
        assertTrue(dclfAlgo.calculateDclf(), "IEEE9 base DCLF should converge");

        ContingencyDefinition definition = definition(
                "GROUPED_DCLF_CLOSE",
                ContingencyActionType.CLOSE,
                "Bus4->Bus5(0)",
                "Bus7->Bus8(0)");

        List<DclfMultiOutage> outages =
                new DclfMultiOutageContingencyHelper(dclfAlgo)
                        .createDclfMultiOutageContListFromDefinitions(List.of(definition));

        assertEquals(1, outages.size());
        assertEquals(ContingencyBranchOutageType.CLOSE, outages.get(0).getOutageType());
        assertEquals(ContingencyBranchOutageType.CLOSE,
                outages.get(0).getOutageEquips().get(0).getOutageType());
    }

    @Test
    public void createsMixedDclfOpenCloseActionsFromGroupedDefinition() throws Exception {
        AclfNetwork net = importIeee9Labeled();
        ContingencyAnalysisAlgorithm dclfAlgo =
                DclfAlgoObjectFactory.createContingencyAnalysisAlgorithm(net);
        assertTrue(dclfAlgo.calculateDclf(), "IEEE9 base DCLF should converge");

        ContingencyDefinition definition = new ContingencyDefinition("MIXED_DCLF");
        definition.addAction(new ContingencyAction(
                ContingencyObjectType.BRANCH,
                ContingencyActionType.OPEN,
                "Bus4->Bus5(0)"));
        definition.addAction(new ContingencyAction(
                ContingencyObjectType.BRANCH,
                ContingencyActionType.CLOSE,
                "Bus7->Bus8(0)"));

        DclfMultiOutage outage =
                new DclfMultiOutageContingencyHelper(dclfAlgo).createDclfMultiOutage(definition);

        assertEquals(ContingencyBranchOutageType.OPEN, outage.getOutageEquips().get(0).getOutageType());
        assertEquals(ContingencyBranchOutageType.CLOSE, outage.getOutageEquips().get(1).getOutageType());
    }

    @Test
    public void createsPreScreenedDclfPlanWithNormalAndReplayBuckets() throws Exception {
        AclfNetwork net = importIeee9Labeled();
        ContingencyAnalysisAlgorithm dclfAlgo =
                DclfAlgoObjectFactory.createContingencyAnalysisAlgorithm(net);
        assertTrue(dclfAlgo.calculateDclf(), "IEEE9 base DCLF should converge");

        ContingencyDefinition normal = definition("NORMAL", "Bus4->Bus5(0)");
        ContingencyDefinition duplicate = definition(
                "DUPLICATE_REPLAY",
                "Bus4->Bus5(0)",
                "Bus4->Bus5(0)");

        DclfMultiOutageContingencyPlan plan =
                new DclfMultiOutageContingencyHelper(dclfAlgo)
                        .createPreScreenedDclfMultiOutagePlanFromDefinitions(List.of(normal, duplicate));

        assertEquals(2, plan.totalContingencies());
        assertEquals(1, plan.normalContingencies().size());
        assertEquals("NORMAL", plan.normalContingencies().get(0).getId());
        assertEquals(1, plan.replayRequiredDefinitions().size());
        assertEquals("DUPLICATE_REPLAY", plan.replayRequiredDefinitions().get(0).name);
        assertEquals(1, plan.preScreenReport().normalContingencies());
        assertEquals(1, plan.preScreenReport().ePtdfSingularContingencies());
    }

    @Test
    public void parallelPreScreenedDclfPlanMatchesSerialBuckets() throws Exception {
        AclfNetwork net = importIeee9Labeled();
        ContingencyAnalysisAlgorithm dclfAlgo =
                DclfAlgoObjectFactory.createContingencyAnalysisAlgorithm(net);
        assertTrue(dclfAlgo.calculateDclf(DclfMethod.STD), "IEEE9 base DCLF should converge");

        List<ContingencyDefinition> definitions = List.of(
                definition("NORMAL_1", "Bus4->Bus5(0)"),
                definition("DUPLICATE_REPLAY_1", "Bus4->Bus5(0)", "Bus4->Bus5(0)"),
                definition("NORMAL_2", "Bus7->Bus8(0)"),
                definition("DUPLICATE_REPLAY_2", "Bus7->Bus8(0)", "Bus7->Bus8(0)"));

        DclfMultiOutageContingencyHelper helper = new DclfMultiOutageContingencyHelper(dclfAlgo);
        DclfMultiOutageContingencyPlan serial =
                helper.createPreScreenedDclfMultiOutagePlanFromDefinitions(definitions);
        DclfMultiOutageContingencyPlan parallel =
                helper.createPreScreenedDclfMultiOutagePlanFromDefinitions(definitions, 3, DclfMethod.STD);

        assertEquals(serial.totalContingencies(), parallel.totalContingencies());
        assertEquals(serial.normalContingencies().size(), parallel.normalContingencies().size());
        assertEquals(serial.replayRequiredDefinitions().size(), parallel.replayRequiredDefinitions().size());
        assertEquals(serial.preScreenReport().normalContingencies(),
                parallel.preScreenReport().normalContingencies());
        assertEquals(serial.preScreenReport().ePtdfSingularContingencies(),
                parallel.preScreenReport().ePtdfSingularContingencies());
        assertEquals("NORMAL_1", parallel.normalContingencies().get(0).getId());
        assertEquals("NORMAL_2", parallel.normalContingencies().get(1).getId());
    }

    @Test
    public void structuralPreScreenMatchesNumericalClassification() throws Exception {
        AclfNetwork net = importIeee9Labeled();
        ContingencyAnalysisAlgorithm dclfAlgo =
                DclfAlgoObjectFactory.createContingencyAnalysisAlgorithm(net);
        assertTrue(dclfAlgo.calculateDclf(DclfMethod.STD), "IEEE9 base DCLF should converge");

        List<ContingencyDefinition> definitions = List.of(
                definition("NORMAL", "Bus4->Bus5(0)"),
                definition("DUPLICATE", "Bus4->Bus5(0)", "Bus4->Bus5(0)"),
                definition("ISLAND", "Bus1->Bus4(1)"));

        var numerical = DclfContingencyPreScreenUtil.scan(dclfAlgo, definitions);
        var structural = DclfContingencyPreScreenUtil.scanStructurally(net, definitions, 3);

        assertEquals(
                numerical.results().stream().map(result -> result.classification()).toList(),
                structural.results().stream().map(result -> result.classification()).toList());
    }

    @Test
    public void coreStructuralPreScreenDetectsTwoLineSeriesCutsetAsIslanding() throws Exception {
        AclfNetwork net = importIeee14();
        ContingencyAnalysisAlgorithm dclfAlgo =
                DclfAlgoObjectFactory.createContingencyAnalysisAlgorithm(net);
        assertTrue(dclfAlgo.calculateDclf(DclfMethod.STD), "IEEE14 base DCLF should converge");

        ContingencyDefinition seriesCutset = definition(
                "SERIES_CUTSET",
                "Bus9->Bus10(1)",
                "Bus10->Bus11(1)");

        com.interpss.core.algo.dclf.solver.DclfMultiOutageContingencyHelper.Plan plan =
                new com.interpss.core.algo.dclf.solver.DclfMultiOutageContingencyHelper(dclfAlgo)
                        .createPreScreenedPlanFromDefinitions(List.of(seriesCutset), 2);

        assertEquals(1, plan.islandingContingencies().size());
        assertEquals(0, plan.ePtdfSingularContingencies().size());
        assertEquals(
                com.interpss.core.algo.dclf.solver.DclfMultiOutageContingencyHelper.Classification.ISLANDING,
                plan.results().get(0).classification());
        assertEquals(List.of(1), plan.results().get(0).islandBusCounts());
        assertEquals(List.of("Bus10"), plan.results().get(0).islandBusIds());
    }

    @Test
    public void parallelAnalyzerAutomaticallyDispatchesMixedDefinitions() throws Exception {
        AclfNetwork net = importIeee14();
        List<ContingencyDefinition> definitions = List.of(
                definition("FAST_N1", "Bus1->Bus5(1)"),
                definition("NORMAL_N2", "Bus2->Bus3(1)", "Bus2->Bus4(1)"),
                definition("SERIES_ISLAND", "Bus9->Bus10(1)", "Bus10->Bus11(1)"),
                definition("DUPLICATE_SINGULAR", "Bus2->Bus3(1)", "Bus2->Bus3(1)"));
        DclfContingencyConfig config = new DclfContingencyConfig();
        config.setDclfInclLoss(false);
        config.setOverloadThreshold(0.0);
        config.setMultiOutageReplayMode(
                com.interpss.core.algo.dclf.DclfMultiOutageReplayMode.HIGH_IMPEDANCE);

        ParallelDclfContingencyAnalyzer.DefinitionAnalysisResult result =
                ParallelDclfContingencyAnalyzer.executeContingencyDefinitions(
                        net,
                        definitions,
                        java.util.Set.of("Bus4->Bus5(1)"),
                        config,
                        3);

        assertEquals(4, result.contingencyCount());
        assertEquals(1, result.fastN1Count());
        assertEquals(1, result.normalMultiOutageCount());
        assertEquals(1, result.islandingCount());
        assertEquals(1, result.ePtdfSingularCount());
        assertEquals(4, result.results().size());
        assertEquals(
                java.util.Set.of("FAST_N1", "NORMAL_N2", "SERIES_ISLAND", "DUPLICATE_SINGULAR"),
                result.results().stream()
                        .map(com.interpss.algo.parallel.BranchCAResultRec::getContingencyId)
                        .collect(java.util.stream.Collectors.toSet()));
    }

    @Test
    public void highImpedanceReplayWorkspaceSolvesByUpdatingCachedB1() throws Exception {
        AclfNetwork net = importIeee9Labeled();
        ContingencyAnalysisAlgorithm dclfAlgo =
                DclfAlgoObjectFactory.createContingencyAnalysisAlgorithm(net);
        DclfMultiOutageContingencyHelper helper = new DclfMultiOutageContingencyHelper(dclfAlgo);
        AclfBranch outageBranch = net.getBranch("Bus4->Bus5(0)");
        AclfBranch monitorBranch = net.getBranch("Bus7->Bus8(0)");
        var originalZ = outageBranch.getZ();

        try (DclfMultiOutageContingencyHelper.HighImpedanceDclfReplayWorkspace workspace =
                helper.createHighImpedanceDclfReplayWorkspace(DclfMethod.STD)) {
            double baseFlow = dclfAlgo.getBranchFlow(outageBranch, UnitType.mW);

            assertTrue(workspace.solve(definition("HIGH_Z_DCLF", "Bus4->Bus5(0)"), 10000.0));

            double highZOutageFlow = workspace.getBranchFlow(outageBranch, UnitType.mW);
            double monitorFlow = workspace.getBranchFlow(monitorBranch, UnitType.mW);
            assertTrue(Math.abs(highZOutageFlow) < Math.abs(baseFlow) * 0.01 + 1.0e-6);
            assertTrue(Double.isFinite(monitorFlow));
        }

        assertEquals(originalZ, outageBranch.getZ());
    }

    @Test
    public void highImpedanceReplayRejectsCloseActionsForExactFallback() throws Exception {
        AclfNetwork net = importIeee9Labeled();
        ContingencyAnalysisAlgorithm dclfAlgo =
                DclfAlgoObjectFactory.createContingencyAnalysisAlgorithm(net);
        DclfMultiOutageContingencyHelper helper = new DclfMultiOutageContingencyHelper(dclfAlgo);

        try (DclfMultiOutageContingencyHelper.HighImpedanceDclfReplayWorkspace workspace =
                helper.createHighImpedanceDclfReplayWorkspace(DclfMethod.STD)) {
            ContingencyDefinition close = definition(
                    "HIGH_Z_CLOSE",
                    ContingencyActionType.CLOSE,
                    "Bus4->Bus5(0)");
            assertThrows(InterpssException.class, () -> workspace.solve(close, 10000.0));
        }
    }

    @Test
    public void highImpedanceCachedB1MatchesDirectRebuildForLineAndTransformer() throws Exception {
        for (DclfMethod method : List.of(DclfMethod.STD, DclfMethod.INC_LOSS)) {
            AclfNetwork workspaceNet = importIeee9Labeled();
            ContingencyAnalysisAlgorithm workspaceAlgo =
                    DclfAlgoObjectFactory.createContingencyAnalysisAlgorithm(workspaceNet);
            AclfBranch lineOutage = workspaceNet.getBranch("Bus4->Bus5(0)");
            assertNotNull(lineOutage);
            Complex originalLineZ = lineOutage.getZ();
            DclfMultiOutageContingencyHelper.HighImpedanceDclfReplayWorkspace workspace =
                    new DclfMultiOutageContingencyHelper(workspaceAlgo)
                            .createHighImpedanceDclfReplayWorkspace(method);
            double[][] baseB1 = snapshotB1(workspace.dclfAlgo().getB1Matrix());
            try {
                assertCachedHighZMatchesDirectRebuild(
                        workspace, lineOutage.getId(), importIeee9Labeled(), method);
            } finally {
                workspace.close();
            }
            assertEquals(originalLineZ, lineOutage.getZ());
            assertB1Matches(baseB1, workspaceAlgo.getB1Matrix());

            AclfNetwork transformerNet = importIeee14();
            assertTrue(DclfAlgoObjectFactory.createContingencyAnalysisAlgorithm(transformerNet)
                    .calculateDclf(method));
            AclfBranch transformerOutage = firstNonIslandingTransformer(transformerNet);
            assertNotNull(transformerOutage);
            assertTrue(transformerOutage.isXfr() || transformerOutage.isPSXfr());
            Complex originalTransformerZ = transformerOutage.getZ();
            ContingencyAnalysisAlgorithm transformerAlgo =
                    DclfAlgoObjectFactory.createContingencyAnalysisAlgorithm(transformerNet);
            DclfMultiOutageContingencyHelper.HighImpedanceDclfReplayWorkspace transformerWorkspace =
                    new DclfMultiOutageContingencyHelper(transformerAlgo)
                            .createHighImpedanceDclfReplayWorkspace(method);
            double[][] transformerBaseB1 = snapshotB1(transformerWorkspace.dclfAlgo().getB1Matrix());
            try {
                assertCachedHighZMatchesDirectRebuild(
                        transformerWorkspace, transformerOutage.getId(), importIeee14(), method);
            } finally {
                transformerWorkspace.close();
            }
            assertEquals(originalTransformerZ, transformerOutage.getZ());
            assertB1Matches(transformerBaseB1, transformerAlgo.getB1Matrix());
        }
    }

    @Test
    public void highImpedanceReplayCompensatesOneBusIslandInjection() throws Exception {
        AclfNetwork workspaceNet = importIeee14();
        ContingencyAnalysisAlgorithm workspaceAlgo =
                DclfAlgoObjectFactory.createContingencyAnalysisAlgorithm(workspaceNet);
        ContingencyDefinition islanding =
                definition("BUS14_ISLAND", "Bus9->Bus14(1)", "Bus13->Bus14(1)");
        assertTrue(DclfContingencyPreScreenUtil.isTopologyIslanding(workspaceNet, islanding));

        try (DclfMultiOutageContingencyHelper.HighImpedanceDclfReplayWorkspace workspace =
                new DclfMultiOutageContingencyHelper(workspaceAlgo)
                        .createHighImpedanceDclfReplayWorkspace(DclfMethod.STD)) {
            assertTrue(workspace.solve(islanding, 10000.0));

            AclfNetwork directNet = importIeee14();
            directNet.getBranch("Bus9->Bus14(1)").setStatus(false);
            directNet.getBranch("Bus13->Bus14(1)").setStatus(false);
            directNet.getBus("Bus14").setStatus(false);
            ContingencyAnalysisAlgorithm directAlgo =
                    DclfAlgoObjectFactory.createContingencyAnalysisAlgorithm(directNet);
            directAlgo.setSolutionMethod(DclfContingencySolutionMethod.SparseEqnSolve);
            assertTrue(directAlgo.calculateDclf(DclfMethod.STD));

            for (AclfBranch directBranch : directNet.getBranchList()) {
                if (directBranch.isGroundBranch() || !directBranch.isActive()) {
                    continue;
                }
                AclfBranch workspaceBranch = workspaceNet.getBranch(directBranch.getId());
                assertNotNull(workspaceBranch);
                assertEquals(
                        directAlgo.getBranchFlow(directBranch, UnitType.mW),
                        workspace.getBranchFlow(workspaceBranch, UnitType.mW),
                        5.0e-2,
                        "Compensated high-Z island replay should match direct island DCLF for "
                                + directBranch.getId());
            }
        }
    }

    @Test
    public void highImpedanceReplayCompensatesOneBusIslandInjectionWithLoss() throws Exception {
        AclfNetwork workspaceNet = importIeee14();
        ContingencyAnalysisAlgorithm workspaceAlgo =
                DclfAlgoObjectFactory.createContingencyAnalysisAlgorithm(workspaceNet);
        ContingencyDefinition islanding =
                definition("BUS14_ISLAND_WITH_LOSS", "Bus9->Bus14(1)", "Bus13->Bus14(1)");

        try (DclfMultiOutageContingencyHelper.HighImpedanceDclfReplayWorkspace workspace =
                new DclfMultiOutageContingencyHelper(workspaceAlgo)
                        .createHighImpedanceDclfReplayWorkspace(DclfMethod.INC_LOSS)) {
            assertTrue(workspace.solve(islanding, 10000.0));

            AclfNetwork directNet = importIeee14();
            directNet.getBranch("Bus9->Bus14(1)").setStatus(false);
            directNet.getBranch("Bus13->Bus14(1)").setStatus(false);
            directNet.getBus("Bus14").setStatus(false);
            ContingencyAnalysisAlgorithm directAlgo =
                    DclfAlgoObjectFactory.createContingencyAnalysisAlgorithm(directNet);
            directAlgo.setSolutionMethod(DclfContingencySolutionMethod.SparseEqnSolve);
            assertTrue(directAlgo.calculateDclf(DclfMethod.INC_LOSS));

            for (AclfBranch directBranch : directNet.getBranchList()) {
                if (directBranch.isGroundBranch() || !directBranch.isActive()) {
                    continue;
                }
                AclfBranch workspaceBranch = workspaceNet.getBranch(directBranch.getId());
                assertNotNull(workspaceBranch);
                assertEquals(
                        directAlgo.getBranchFlow(directBranch, UnitType.mW),
                        workspace.getBranchFlow(workspaceBranch, UnitType.mW),
                        5.0e-2,
                        "Compensated loss-inclusive high-Z island replay should match direct DCLF for "
                                + directBranch.getId());
            }
        }
    }

    @Test
    public void createsAclfMultiOutageFromGroupedDefinition() throws Exception {
        AclfNetwork net = importIeee9Labeled();
        ContingencyDefinition definition = definition(
                "GROUPED_ACLF",
                "Bus4->Bus5(0)",
                "Bus7->Bus8(0)");

        List<AclfMultiOutage> outages =
                new AclfContingencyDefinitionHelper(net)
                        .createAclfMultiOutageList(List.of(definition));

        assertEquals(1, outages.size());
        assertEquals("GROUPED_ACLF", outages.get(0).getId());
        assertEquals(2, outages.get(0).getOutageEquips().size());
        assertTrue(outages.get(0).getOutageEquips().get(0) instanceof AclfBranchOutage);

        AclfBranchOutage first =
                (AclfBranchOutage) outages.get(0).getOutageEquips().get(0);
        assertEquals("Bus4->Bus5(0)", first.getOutageEquip().getId());
    }

    @Test
    public void createsAclfCloseMultiOutageFromGroupedDefinition() throws Exception {
        AclfNetwork net = importIeee9Labeled();
        ContingencyDefinition definition = definition(
                "GROUPED_ACLF_CLOSE",
                ContingencyActionType.CLOSE,
                "Bus4->Bus5(0)");

        List<AclfMultiOutage> outages =
                new AclfContingencyDefinitionHelper(net)
                        .createAclfMultiOutageList(List.of(definition));

        AclfBranchOutage first =
                (AclfBranchOutage) outages.get(0).getOutageEquips().get(0);
        assertEquals(ContingencyBranchOutageType.CLOSE, first.getOutageType());
    }

    private static ContingencyDefinition definition(String name, String... objectIds) {
        return definition(name, ContingencyActionType.OPEN, objectIds);
    }

    private static ContingencyDefinition definition(
            String name,
            ContingencyActionType actionType,
            String... objectIds) {
        ContingencyDefinition definition = new ContingencyDefinition(name);
        for (String objectId : objectIds) {
            definition.addAction(new ContingencyAction(
                    ContingencyObjectType.BRANCH,
                    actionType,
                    objectId));
        }
        return definition;
    }

    private static AclfNetwork importIeee9Labeled() throws InterpssException {
        return new PSSEDirectParser().parse(resolveTestDataPath(
                        "ipss.test.plugin.core/testData/adpter/psse/v36/ieee9_v36_labeled.raw",
                        "../ipss.test.plugin.core/testData/adpter/psse/v36/ieee9_v36_labeled.raw").toString());
    }

    private static AclfNetwork importIeee14() throws Exception {
        return CorePluginFactory
                .getFileAdapter(IpssFileAdapter.FileFormat.IEEECDF)
                .load(resolveTestDataPath(
                        "ipss.test.plugin.core/testData/adpter/ieee_format/ieee14.ieee",
                        "../ipss.test.plugin.core/testData/adpter/ieee_format/ieee14.ieee").toString())
                .getAclfNet();
    }

    private static Path resolveTestDataPath(String first, String second) {
        Path firstPath = Path.of(first);
        if (Files.isRegularFile(firstPath)) {
            return firstPath;
        }
        return Path.of(second);
    }

    private static void assertCachedHighZMatchesDirectRebuild(
            DclfMultiOutageContingencyHelper.HighImpedanceDclfReplayWorkspace workspace,
            String outageBranchId,
            AclfNetwork directNet,
            DclfMethod method) throws Exception {
        double highXPu = DclfMultiOutageContingencyHelper.DEFAULT_HIGH_IMPEDANCE_REACTANCE_PU;
        assertTrue(workspace.solve(definition("HIGH_Z_" + outageBranchId, outageBranchId), highXPu));

        AclfBranch directOutageBranch = directNet.getBranch(outageBranchId);
        assertNotNull(directOutageBranch);
        Complex originalZ = directOutageBranch.getZ();
        directOutageBranch.setZ(new Complex(
                0.0,
                (originalZ.getImaginary() < 0.0 ? -1.0 : 1.0) * highXPu));
        ContingencyAnalysisAlgorithm directAlgo =
                DclfAlgoObjectFactory.createContingencyAnalysisAlgorithm(directNet);
        assertTrue(directAlgo.calculateDclf(method));

        for (AclfBranch directBranch : directNet.getBranchList()) {
            if (directBranch.isGroundBranch()) {
                continue;
            }
            AclfBranch workspaceBranch = workspace.dclfAlgo().getAclfNet().getBranch(directBranch.getId());
            assertNotNull(workspaceBranch);
            assertEquals(
                    directAlgo.getBranchFlow(directBranch, UnitType.mW),
                    workspace.getBranchFlow(workspaceBranch, UnitType.mW),
                    1.0e-6,
                    "Cached high-Z B1 replay should match direct DCLF rebuild for "
                            + outageBranchId + " monitoring " + directBranch.getId());
        }
    }

    private static AclfBranch firstNonIslandingTransformer(AclfNetwork net) {
        for (AclfBranch branch : net.getBranchList()) {
            if (branch != null && branch.isActive() && (branch.isXfr() || branch.isPSXfr())) {
                ContingencyDefinition definition = definition("XFR_" + branch.getId(), branch.getId());
                if (!DclfContingencyPreScreenUtil.isTopologyIslanding(net, definition)) {
                    return branch;
                }
            }
        }
        return null;
    }

    private static double[][] snapshotB1(ISparseEqnDouble b1) {
        double[][] snapshot = new double[b1.getDimension()][b1.getDimension()];
        for (int row = 0; row < b1.getDimension(); row++) {
            for (int col = 0; col < b1.getDimension(); col++) {
                snapshot[row][col] = b1.getAij(row, col);
            }
        }
        return snapshot;
    }

    private static void assertB1Matches(double[][] expected, ISparseEqnDouble b1) {
        assertEquals(expected.length, b1.getDimension());
        for (int row = 0; row < expected.length; row++) {
            for (int col = 0; col < expected[row].length; col++) {
                assertEquals(expected[row][col], b1.getAij(row, col), 1.0e-9,
                        "B1 entry should be restored at (" + row + ", " + col + ")");
            }
        }
    }
}
