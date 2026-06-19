package org.interpss.plugin.contingency.dclf;

import static com.interpss.core.DclfAlgoObjectFactory.createCaOutageBranch;
import static com.interpss.core.DclfAlgoObjectFactory.createContingency;
import static com.interpss.core.DclfAlgoObjectFactory.createContingencyAnalysisAlgorithm;
import static org.junit.jupiter.api.Assertions.assertEquals;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentLinkedQueue;

import org.interpss.CorePluginTestSetup;
import com.interpss.core.algo.dclf.DclfContingencyLimitStudy;
import com.interpss.core.algo.dclf.DclfContingencyConfig;
import com.interpss.core.algo.dclf.ParallelDclfContingencyAnalyzer;
import com.interpss.core.algo.dclf.check.BranchMwLimitCheck;
import com.interpss.core.algo.dclf.check.DclfLimitCheckCompileContext;
import com.interpss.core.algo.dclf.check.DclfLimitCheckContext;
import com.interpss.core.algo.dclf.check.DclfMwLimitViolationResult;
import com.interpss.core.algo.dclf.check.MonitoringExceptionRecord;
import com.interpss.core.algo.dclf.check.MonitoringExceptionStatus;
import com.interpss.core.algo.dclf.check.MonitoringObjectType;
import com.interpss.core.algo.dclf.check.MonitoringExceptionPolicy;
import com.interpss.core.algo.dclf.check.NomogramMwBoundaryCheck;
import com.interpss.core.algo.dclf.definition.DclfMonitoringConfigRecord;
import com.interpss.core.algo.dclf.definition.NomogramConstraintRecord;
import com.interpss.core.algo.dclf.definition.NomogramRecord;
import org.interpss.plugin.contingency.definition.MonitoredBranchRecord;
import org.interpss.plugin.contingency.definition.MonitoredInterfaceRecord;
import com.interpss.core.algo.dclf.result.DclfMonitoredConstraintResult;
import org.interpss.plugin.contingency.util.ContingencyFileUtil;
import org.interpss.plugin.optadj.IEEE14_SensHelper_Test;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.interpss.common.exp.InterpssException;
import com.interpss.core.aclf.AclfBranch;
import com.interpss.core.aclf.AclfNetwork;
import com.interpss.core.algo.dclf.ContingencyAnalysisAlgorithm;
import com.interpss.core.contingency.ContingencyBranchOutageType;
import com.interpss.core.contingency.dclf.DclfBranchOutage;
import com.interpss.core.contingency.dclf.DclfOutageBranch;

public class DclfMonitoredConstraintTest extends CorePluginTestSetup {
    @TempDir
    Path tempDir;

    @Test
    public void monitoredConstraintUsesWeightedPostContingencyFlows() throws InterpssException {
        AclfNetwork net = IEEE14_SensHelper_Test.createSenTestCase();
        ContingencyAnalysisAlgorithm algo = createContingencyAnalysisAlgorithm(net);
        algo.calculateDclf();

        DclfBranchOutage contingency = branchOutage(algo, "Bus2->Bus3(1)");
        AclfBranch monitor1 = net.getBranch("Bus2->Bus4(1)");
        AclfBranch monitor2 = net.getBranch("Bus3->Bus4(1)");

        double rawPostValue = postFlowMw(algo, contingency.getOutageEquip(), monitor1)
                + postFlowMw(algo, contingency.getOutageEquip(), monitor2);
        double coefficient = rawPostValue >= 0.0 ? 1.0 : -1.0;
        double expectedPostValue = coefficient * rawPostValue;
        double limitMW = expectedPostValue - 0.01;

        MonitoredInterfaceRecord constraint =
                new MonitoredInterfaceRecord("IEEE14_BG", limitMW);
        constraint.addBranch(new MonitoredBranchRecord(monitor1.getId(), coefficient));
        constraint.addBranch(new MonitoredBranchRecord(monitor2.getId(), coefficient));

        ConcurrentLinkedQueue<DclfMonitoredConstraintResult> results =
                ParallelDclfContingencyAnalyzer.performMonitoredConstraintAnalysis(
                        net,
                        List.of(contingency),
                        List.of(constraint),
                        100.0,
                        false,
                        1);

        assertEquals(1, results.size());
        DclfMonitoredConstraintResult result = results.peek();
        assertEquals(contingency.getId(), result.getContingencyId());
        assertEquals("IEEE14_BG", result.getConstraintId());
        assertEquals(expectedPostValue, result.getPostValueMW(), 1.0e-8);
        assertEquals(limitMW, result.getLimitMW(), 1.0e-8);
    }

    @Test
    public void dclfLimitStudyMatchesExistingMonitoredConstraintPath() throws InterpssException {
        AclfNetwork net = IEEE14_SensHelper_Test.createSenTestCase();
        ContingencyAnalysisAlgorithm algo = createContingencyAnalysisAlgorithm(net);
        algo.calculateDclf();

        DclfBranchOutage contingency = branchOutage(algo, "Bus2->Bus3(1)");
        MonitoredInterfaceRecord constraint = monitoredExpression(
                "MATCH_EXISTING",
                net,
                algo,
                contingency,
                "Bus2->Bus4(1)",
                "Bus3->Bus4(1)");

        ConcurrentLinkedQueue<DclfMonitoredConstraintResult> oldResults =
                ParallelDclfContingencyAnalyzer.performMonitoredConstraintAnalysis(
                        net,
                        List.of(contingency),
                        List.of(constraint),
                        100.0,
                        false,
                        1);
        ConcurrentLinkedQueue<DclfMonitoredConstraintResult> newResults =
                DclfContingencyLimitStudy.performMonitoredExpressionAnalysis(
                        net,
                        List.of(contingency),
                        List.of(constraint),
                        100.0,
                        false,
                        1);

        assertEquals(oldResults.size(), newResults.size());
        assertEquals(oldResults.peek().getPostValueMW(), newResults.peek().getPostValueMW(), 1.0e-8);
        assertEquals(oldResults.peek().getLimitMW(), newResults.peek().getLimitMW(), 1.0e-8);
    }

    @Test
    public void dclfLimitStudyAppliesMonitoringExceptions() throws InterpssException {
        AclfNetwork net = IEEE14_SensHelper_Test.createSenTestCase();
        ContingencyAnalysisAlgorithm algo = createContingencyAnalysisAlgorithm(net);
        algo.calculateDclf();

        DclfBranchOutage contingency = branchOutage(algo, "Bus2->Bus3(1)");
        MonitoredInterfaceRecord include = monitoredExpression(
                "INCLUDE_IF_CONFIGURED",
                net,
                algo,
                contingency,
                "Bus2->Bus4(1)",
                "Bus3->Bus4(1)");
        MonitoredInterfaceRecord exclude = monitoredExpression(
                "EXCLUDE_FOR_THIS_CONTINGENCY",
                net,
                algo,
                contingency,
                "Bus2->Bus4(1)",
                "Bus3->Bus4(1)");
        MonitoredInterfaceRecord defaulted = monitoredExpression(
                "DEFAULT_MONITORING",
                net,
                algo,
                contingency,
                "Bus2->Bus4(1)",
                "Bus3->Bus4(1)");

        ConcurrentLinkedQueue<DclfMonitoredConstraintResult> results =
                DclfContingencyLimitStudy.performMonitoredExpressionAnalysis(
                        net,
                        List.of(contingency),
                        List.of(include, exclude, defaulted),
                        List.of(
                                new MonitoringExceptionRecord(
                                        contingency.getId(),
                                        MonitoringObjectType.INTERFACE,
                                        include.getId(),
                                        MonitoringExceptionStatus.INCLUDE),
                                new MonitoringExceptionRecord(
                                        contingency.getId(),
                                        MonitoringObjectType.INTERFACE,
                                        exclude.getId(),
                                        MonitoringExceptionStatus.EXCLUDE),
                                new MonitoringExceptionRecord(
                                        contingency.getId(),
                                        MonitoringObjectType.INTERFACE,
                                        defaulted.getId(),
                                        MonitoringExceptionStatus.DEFAULT)),
                        100.0,
                        false,
                        1,
                        com.interpss.core.algo.dclf.DclfContingencySolutionMethod.SparseEqnSolve,
                        0);

        assertEquals(2, results.size());
        List<String> constraintIds = results.stream()
                .map(DclfMonitoredConstraintResult::getConstraintId)
                .sorted()
                .toList();
        assertEquals(List.of("DEFAULT_MONITORING", "INCLUDE_IF_CONFIGURED"), constraintIds);
    }

    @Test
    public void monitoredInterfaceJsonImportsBranchCoefficients() throws Exception {
        File file = tempDir.resolve("monitored-interfaces.json").toFile();
        Files.writeString(file.toPath(), """
                {
                  "monitored_branches": [
                    {
                      "from_bus": "Bus2",
                      "to_bus": "Bus4",
                      "circuit": "1",
                      "from_bus_area": "A",
                      "to_bus_area": "B",
                      "base_kv": 138.0,
                      "pre_contingency_flow_mw": 50.0
                    }
                  ],
                  "monitored_interfaces": [
                    {
                      "id": "PATH26_N-S",
                      "limit_mw": 1400.0,
                      "branches": [
                        {
                          "branch_id": "Bus2->Bus4(1)",
                          "coefficient": 0.75
                        },
                        {
                          "from_bus": "Bus3",
                          "to_bus": "Bus4",
                          "circuit": "1",
                          "coefficient": -0.25
                        }
                      ]
                    }
                  ]
                }
                """, StandardCharsets.UTF_8);

        List<MonitoredInterfaceRecord> interfaces =
                ContingencyFileUtil.importMonitoredInterfaceRecordsFromJson(file);

        assertEquals(1, interfaces.size());
        MonitoredInterfaceRecord record = interfaces.get(0);
        assertEquals("PATH26_N-S", record.getId());
        assertEquals(1400.0, record.getLimitMW(), 1.0e-8);
        assertEquals(2, record.getBranches().size());
        assertEquals(0.75, record.getBranches().iterator().next().getCoefficient(), 1.0e-8);
    }

    @Test
    public void dclfMonitoringConfigJsonImportsInterfaceFlowgateNomogramAndExceptions() throws Exception {
        File file = tempDir.resolve("dclf-monitoring-config.json").toFile();
        Files.writeString(file.toPath(), """
                {
                  "monitored_branches": [
                    {
                      "from_bus": "Bus2",
                      "to_bus": "Bus4",
                      "circuit": "1",
                      "from_bus_area": "A",
                      "to_bus_area": "B",
                      "base_kv": 138.0,
                      "pre_contingency_flow_mw": 50.0
                    }
                  ],
                  "monitored_interfaces": [
                    {
                      "id": "PATH26_N-S",
                      "limit_mw": 1400.0,
                      "branches": [
                        {
                          "branch_id": "Bus2->Bus4(1)",
                          "coefficient": 0.75
                        }
                      ]
                    },
                    {
                      "id": "PATH26_S-N",
                      "limit_mw": 1300.0,
                      "branches": [
                        {
                          "branch_id": "Bus3->Bus4(1)",
                          "coefficient": -0.25
                        }
                      ]
                    }
                  ],
                  "flowgates": [
                    {
                      "id": "CORNAPTERSUN",
                      "constraint_type": "FG",
                      "nerc_id": "5107",
                      "tlr_level": "CME",
                      "market_state": "BINDING",
                      "monitored_facility_name": "LN CORNTP4 - NAPLES1",
                      "contingent_facility_name": "OKGE CSWS:SUNNYSDE TERRY_RD:345:2:",
                      "contingency_ref": {
                        "type": "SINGLE_BRANCH_OPEN",
                        "outage_branch_id": "Bus2->Bus5(1)"
                      },
                      "limits": {
                        "source_limit_mw": 201.0,
                        "realtime_effective_limit_mw": 190.95,
                        "initial_effective_limit_mw": 194.97,
                        "selection_policy": "REALTIME_EFFECTIVE_LIMIT"
                      },
                      "branches": [
                        {
                          "branch_id": "Bus2->Bus4(1)",
                          "coefficient": 1.0
                        }
                      ]
                    }
                  ],
                  "nomograms": [
                    {
                      "id": "GGS",
                      "axis_a_id": "PATH26_N-S",
                      "axis_b_id": "PATH26_S-N",
                      "constraints": [
                        {
                          "id": "GGS_LIMIT_01",
                          "coefficient_a": 0.6,
                          "coefficient_b": 0.4,
                          "limit_mw": 900.0
                        }
                      ]
                    }
                  ],
                  "monitoring_exceptions": [
                    {
                      "contingency_id": "OPEN:Bus2->Bus5(1)",
                      "object_type": "FLOWGATE",
                      "object_id": "CORNAPTERSUN",
                      "check_id": "FLOWGATE_EFFECTIVE_LIMIT",
                      "status": "EXCLUDE"
                    }
                  ]
                }
                """, StandardCharsets.UTF_8);

        DclfMonitoringConfigRecord config =
                ContingencyFileUtil.importDclfMonitoringConfigFromJson(file);

        assertEquals(1, config.getMonitoredBranches().size());
        assertEquals("Bus2->Bus4(1)", config.getMonitoredBranches().get(0).getBranchId());
        assertEquals(138.0, config.getMonitoredBranches().get(0).baseKv, 1.0e-8);
        assertEquals(2, config.getMonitoredInterfaces().size());
        assertEquals("PATH26_N-S", config.getMonitoredInterfaces().get(0).getId());
        assertEquals(1, config.getFlowgates().size());
        assertEquals("CORNAPTERSUN", config.getFlowgates().get(0).getId());
        assertEquals("5107", config.getFlowgates().get(0).getNercId());
        assertEquals("OPEN:Bus2->Bus5(1)", config.getFlowgates().get(0).getContingencyRef().getId());
        assertEquals(190.95, config.getFlowgates().get(0).getLimitMW(), 1.0e-8);
        assertEquals(1, config.getNomograms().size());
        assertEquals("GGS", config.getNomograms().get(0).id());
        assertEquals(1, config.getNomograms().get(0).constraints().size());
        assertEquals(1, config.getMonitoringExceptions().size());
        assertEquals(MonitoringObjectType.FLOWGATE,
                config.getMonitoringExceptions().get(0).getMonitoredObjectType());
        assertEquals(MonitoringExceptionStatus.EXCLUDE,
                config.getMonitoringExceptions().get(0).getStatus());
        assertEquals("FLOWGATE_EFFECTIVE_LIMIT",
                config.getMonitoringExceptions().get(0).getCheckId());
    }

    @Test
    public void branchMwLimitCheckUsesCompiledBranchIndexAndExceptions() throws InterpssException {
        AclfNetwork net = IEEE14_SensHelper_Test.createSenTestCase();
        ContingencyAnalysisAlgorithm algo = createContingencyAnalysisAlgorithm(net);
        algo.calculateDclf();

        String branchId = "Bus2->Bus4(1)";
        AclfBranch branch = net.getBranch(branchId);
        double preFlowMW = Math.abs(algo.getDclfAlgoBranch(branchId).getDclfFlow() * net.getBaseMva());
        branch.setRatingMva2(preFlowMW - 0.01);

        DclfContingencyConfig config = new DclfContingencyConfig();
        BranchMwLimitCheck check = new BranchMwLimitCheck(List.of(branchId), 100.0);
        check.compile(new DclfLimitCheckCompileContext(
                net,
                algo,
                Map.of(branchId, 0),
                net.getBaseMva(),
                config));

        List<DclfMwLimitViolationResult> results = new java.util.ArrayList<>();
        check.evaluateBase(new DclfLimitCheckContext(
                net,
                algo,
                null,
                "BASE",
                new double[] { preFlowMW },
                new double[] { preFlowMW },
                net.getBaseMva(),
                config,
                null), results);

        assertEquals(1, results.size());
        assertEquals(BranchMwLimitCheck.CHECK_ID, results.get(0).getCheckId());
        assertEquals(branchId, results.get(0).getMonitoredObjectId());

        MonitoringExceptionRecord exception = new MonitoringExceptionRecord(
                "BASE",
                MonitoringObjectType.BRANCH,
                branchId,
                MonitoringExceptionStatus.EXCLUDE);
        List<DclfMwLimitViolationResult> suppressed = new java.util.ArrayList<>();
        check.evaluateBase(new DclfLimitCheckContext(
                net,
                algo,
                null,
                "BASE",
                new double[] { preFlowMW },
                new double[] { preFlowMW },
                net.getBaseMva(),
                config,
                MonitoringExceptionPolicy.compile(List.of(exception))),
                suppressed);

        assertEquals(0, suppressed.size());
    }

    @Test
    public void nomogramMwBoundaryCheckUsesCompiledInterfaceExpressionsAndExceptions() throws InterpssException {
        AclfNetwork net = IEEE14_SensHelper_Test.createSenTestCase();
        ContingencyAnalysisAlgorithm algo = createContingencyAnalysisAlgorithm(net);
        algo.calculateDclf();

        String branchId1 = "Bus2->Bus4(1)";
        String branchId2 = "Bus3->Bus4(1)";
        double flow1MW = algo.getDclfAlgoBranch(branchId1).getDclfFlow() * net.getBaseMva();
        double flow2MW = algo.getDclfAlgoBranch(branchId2).getDclfFlow() * net.getBaseMva();
        double signedFlow1MW = Math.abs(flow1MW);
        double signedFlow2MW = Math.abs(flow2MW);

        MonitoredInterfaceRecord axisA = new MonitoredInterfaceRecord("AXIS_A", 9999.0);
        axisA.addBranch(new MonitoredBranchRecord(branchId1, flow1MW >= 0.0 ? 1.0 : -1.0));
        MonitoredInterfaceRecord axisB = new MonitoredInterfaceRecord("AXIS_B", 9999.0);
        axisB.addBranch(new MonitoredBranchRecord(branchId2, flow2MW >= 0.0 ? 1.0 : -1.0));

        String nomogramId = "IEEE14_NOMOGRAM";
        NomogramMwBoundaryCheck check = new NomogramMwBoundaryCheck(
                List.of(new NomogramRecord(
                        nomogramId,
                        axisA,
                        axisB,
                        List.of(new NomogramConstraintRecord(
                                "LIMIT_01",
                                1.0,
                                1.0,
                                signedFlow1MW + signedFlow2MW - 0.01)))),
                100.0);
        DclfContingencyConfig config = new DclfContingencyConfig();
        check.compile(new DclfLimitCheckCompileContext(
                net,
                algo,
                Map.of(branchId1, 0, branchId2, 1),
                net.getBaseMva(),
                config));

        List<DclfMwLimitViolationResult> results = new java.util.ArrayList<>();
        check.evaluateBase(new DclfLimitCheckContext(
                net,
                algo,
                null,
                "BASE",
                new double[] { flow1MW, flow2MW },
                new double[] { flow1MW, flow2MW },
                net.getBaseMva(),
                config,
                null), results);

        assertEquals(1, results.size());
        assertEquals(NomogramMwBoundaryCheck.CHECK_ID, results.get(0).getCheckId());
        assertEquals(MonitoringObjectType.NOMOGRAM.name(), results.get(0).getMonitoredObjectType());
        assertEquals(nomogramId, results.get(0).getMonitoredObjectId());
        assertEquals(signedFlow1MW + signedFlow2MW, results.get(0).getPostValue(), 1.0e-8);

        MonitoringExceptionRecord exception = new MonitoringExceptionRecord(
                "BASE",
                MonitoringObjectType.NOMOGRAM,
                nomogramId,
                MonitoringExceptionStatus.EXCLUDE);
        List<DclfMwLimitViolationResult> suppressed = new java.util.ArrayList<>();
        check.evaluateBase(new DclfLimitCheckContext(
                net,
                algo,
                null,
                "BASE",
                new double[] { flow1MW, flow2MW },
                new double[] { flow1MW, flow2MW },
                net.getBaseMva(),
                config,
                MonitoringExceptionPolicy.compile(List.of(exception))),
                suppressed);

        assertEquals(0, suppressed.size());
    }

    private static DclfBranchOutage branchOutage(
            ContingencyAnalysisAlgorithm algo,
            String branchId) throws InterpssException {
        DclfBranchOutage contingency = createContingency("cont:" + branchId);
        DclfOutageBranch outage =
                createCaOutageBranch(
                        algo.getDclfAlgoBranch(branchId),
                        ContingencyBranchOutageType.OPEN);
        outage.setDclfFlow(algo.getDclfAlgoBranch(branchId).getDclfFlow());
        contingency.setOutageEquip(outage);
        return contingency;
    }

    private static MonitoredInterfaceRecord monitoredExpression(
            String id,
            AclfNetwork net,
            ContingencyAnalysisAlgorithm algo,
            DclfBranchOutage contingency,
            String monitorBranchId1,
            String monitorBranchId2) throws InterpssException {
        AclfBranch monitor1 = net.getBranch(monitorBranchId1);
        AclfBranch monitor2 = net.getBranch(monitorBranchId2);
        double rawPostValue = postFlowMw(algo, contingency.getOutageEquip(), monitor1)
                + postFlowMw(algo, contingency.getOutageEquip(), monitor2);
        double coefficient = rawPostValue >= 0.0 ? 1.0 : -1.0;
        double expectedPostValue = coefficient * rawPostValue;
        MonitoredInterfaceRecord constraint =
                new MonitoredInterfaceRecord(id, expectedPostValue - 0.01);
        constraint.addBranch(new MonitoredBranchRecord(monitor1.getId(), coefficient));
        constraint.addBranch(new MonitoredBranchRecord(monitor2.getId(), coefficient));
        return constraint;
    }

    private static double postFlowMw(
            ContingencyAnalysisAlgorithm algo,
            DclfOutageBranch outage,
            AclfBranch monitorBranch) throws InterpssException {
        double baseMva = algo.getAclfNet().getBaseMva();
        double preFlowMw = algo.getDclfAlgoBranch(monitorBranch.getId()).getDclfFlow() * baseMva;
        double shiftedFlowMw = outage.getDclfFlow() * baseMva
                * algo.lineOutageDFactor(outage, monitorBranch);
        return preFlowMw + shiftedFlowMw;
    }
}
