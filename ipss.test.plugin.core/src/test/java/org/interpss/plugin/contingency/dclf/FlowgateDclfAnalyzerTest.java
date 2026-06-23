package org.interpss.plugin.contingency.dclf;

import static com.interpss.core.DclfAlgoObjectFactory.createCaOutageBranch;
import static com.interpss.core.DclfAlgoObjectFactory.createContingency;
import static com.interpss.core.DclfAlgoObjectFactory.createContingencyAnalysisAlgorithm;
import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;
import java.util.concurrent.ConcurrentLinkedQueue;

import org.interpss.CorePluginTestSetup;
import org.interpss.plugin.contingency.DclfContingencyConfig;
import org.interpss.plugin.contingency.definition.MonitoredBranchRecord;
import org.interpss.plugin.optadj.IEEE14_SensHelper_Test;
import org.junit.jupiter.api.Test;

import com.interpss.common.exp.InterpssException;
import com.interpss.core.aclf.AclfBranch;
import com.interpss.core.aclf.AclfNetwork;
import com.interpss.core.algo.dclf.ContingencyAnalysisAlgorithm;
import com.interpss.core.algo.dclf.solver.FlowgateDclfAnalyzer;
import com.interpss.core.algo.dclf.check.MonitoringExceptionRecord;
import com.interpss.core.algo.dclf.check.MonitoringExceptionStatus;
import com.interpss.core.algo.dclf.check.MonitoringObjectType;
import com.interpss.core.algo.dclf.definition.FlowgateConstraintRecord;
import com.interpss.core.algo.dclf.definition.FlowgateContingencyRef;
import com.interpss.core.algo.dclf.definition.FlowgateLimitSelection;
import com.interpss.core.algo.dclf.definition.FlowgateLimitSet;
import com.interpss.core.algo.dclf.result.FlowgateViolationResult;
import com.interpss.core.contingency.ContingencyBranchOutageType;
import com.interpss.core.contingency.dclf.DclfBranchOutage;
import com.interpss.core.contingency.dclf.DclfOutageBranch;

public class FlowgateDclfAnalyzerTest extends CorePluginTestSetup {
    @Test
    public void baseFlowgateUsesSelectedRealtimeEffectiveLimit() throws InterpssException {
        AclfNetwork net = IEEE14_SensHelper_Test.createSenTestCase();
        ContingencyAnalysisAlgorithm algo = createContingencyAnalysisAlgorithm(net);
        algo.calculateDclf();

        String monitorBranchId = "Bus2->Bus4(1)";
        double baseFlowMW = algo.getDclfAlgoBranch(monitorBranchId).getDclfFlow() * net.getBaseMva();
        double coefficient = baseFlowMW >= 0.0 ? 1.0 : -1.0;
        double expectedValueMW = coefficient * baseFlowMW;

        FlowgateLimitSet limits = new FlowgateLimitSet(
                expectedValueMW + 100.0,
                expectedValueMW - 0.01,
                expectedValueMW + 50.0);
        limits.setSelectionPolicy(FlowgateLimitSelection.REALTIME_EFFECTIVE_LIMIT);
        FlowgateConstraintRecord flowgate =
                FlowgateConstraintRecord.of("GGS", FlowgateContingencyRef.base(), limits);
        flowgate.setConstraintType("FG");
        flowgate.setMonitoredFacilityName("Multi-Element Constraint");
        flowgate.setContingentFacilityName("BASE");
        flowgate.addBranch(new MonitoredBranchRecord(monitorBranchId, coefficient));

        ConcurrentLinkedQueue<FlowgateViolationResult> results =
                FlowgateDclfAnalyzer.executeFlowgateAnalysis(
                        net,
                        List.of(flowgate),
                        config(),
                        1);

        assertEquals(1, results.size());
        FlowgateViolationResult result = results.peek();
        assertEquals("GGS", result.getFlowgateId());
        assertEquals("BASE", result.getContingencyId());
        assertEquals(expectedValueMW, result.getPostValueMW(), 1.0e-8);
        assertEquals(expectedValueMW - 0.01, result.getLimitMW(), 1.0e-8);
        assertEquals(FlowgateLimitSelection.REALTIME_EFFECTIVE_LIMIT, result.getLimitSelection());
    }

    @Test
    public void branchOutageFlowgateUsesContingencyAwarePostFlow() throws InterpssException {
        AclfNetwork net = IEEE14_SensHelper_Test.createSenTestCase();
        ContingencyAnalysisAlgorithm algo = createContingencyAnalysisAlgorithm(net);
        algo.calculateDclf();

        String outageBranchId = "Bus2->Bus3(1)";
        AclfBranch monitor1 = net.getBranch("Bus2->Bus4(1)");
        AclfBranch monitor2 = net.getBranch("Bus3->Bus4(1)");
        DclfBranchOutage contingency = branchOutage(algo, outageBranchId);

        double rawPostValue = postFlowMw(algo, contingency.getOutageEquip(), monitor1)
                + postFlowMw(algo, contingency.getOutageEquip(), monitor2);
        double coefficient = rawPostValue >= 0.0 ? 1.0 : -1.0;
        double expectedPostValue = coefficient * rawPostValue;

        FlowgateLimitSet limits = new FlowgateLimitSet(
                expectedPostValue + 10.0,
                expectedPostValue - 0.01,
                expectedPostValue + 5.0);
        FlowgateConstraintRecord flowgate =
                FlowgateConstraintRecord.of(
                        "CORNAPTERSUN",
                        FlowgateContingencyRef.singleBranchOpen(outageBranchId),
                        limits);
        flowgate.setConstraintType("FG");
        flowgate.setNercId("5107");
        flowgate.setTlrLevel("CME");
        flowgate.setMarketState("BINDING");
        flowgate.setMonitoredFacilityName("LN CORNTP4 - NAPLES1");
        flowgate.setContingentFacilityName("OKGE CSWS:SUNNYSDE TERRY_RD:345:2:");
        flowgate.addBranch(new MonitoredBranchRecord(monitor1.getId(), coefficient));
        flowgate.addBranch(new MonitoredBranchRecord(monitor2.getId(), coefficient));

        ConcurrentLinkedQueue<FlowgateViolationResult> results =
                FlowgateDclfAnalyzer.executeFlowgateAnalysis(
                        net,
                        List.of(flowgate),
                        config(),
                        1);

        assertEquals(1, results.size());
        FlowgateViolationResult result = results.peek();
        assertEquals("CORNAPTERSUN", result.getFlowgateId());
        assertEquals("OPEN:" + outageBranchId, result.getContingencyId());
        assertEquals(expectedPostValue, result.getPostValueMW(), 1.0e-8);
        assertEquals(expectedPostValue - 0.01, result.getLimitMW(), 1.0e-8);
        assertEquals("FG", result.getFlowgate().getConstraintType());
        assertEquals("5107", result.getFlowgate().getNercId());
    }

    @Test
    public void flowgateAnalysisAppliesMonitoringExceptionExclusion() throws InterpssException {
        AclfNetwork net = IEEE14_SensHelper_Test.createSenTestCase();
        ContingencyAnalysisAlgorithm algo = createContingencyAnalysisAlgorithm(net);
        algo.calculateDclf();

        String outageBranchId = "Bus2->Bus3(1)";
        AclfBranch monitor1 = net.getBranch("Bus2->Bus4(1)");
        AclfBranch monitor2 = net.getBranch("Bus3->Bus4(1)");
        DclfBranchOutage contingency = branchOutage(algo, outageBranchId);

        double rawPostValue = postFlowMw(algo, contingency.getOutageEquip(), monitor1)
                + postFlowMw(algo, contingency.getOutageEquip(), monitor2);
        double coefficient = rawPostValue >= 0.0 ? 1.0 : -1.0;
        double expectedPostValue = coefficient * rawPostValue;

        FlowgateLimitSet limits = new FlowgateLimitSet(
                expectedPostValue + 10.0,
                expectedPostValue - 0.01,
                expectedPostValue + 5.0);
        FlowgateConstraintRecord flowgate =
                FlowgateConstraintRecord.of(
                        "SUPPRESSED_FLOWGATE",
                        FlowgateContingencyRef.singleBranchOpen(outageBranchId),
                        limits);
        flowgate.addBranch(new MonitoredBranchRecord(monitor1.getId(), coefficient));
        flowgate.addBranch(new MonitoredBranchRecord(monitor2.getId(), coefficient));

        ConcurrentLinkedQueue<FlowgateViolationResult> results =
                FlowgateDclfAnalyzer.executeFlowgateAnalysis(
                        net,
                        List.of(flowgate),
                        List.of(new MonitoringExceptionRecord(
                                FlowgateContingencyRef.singleBranchOpen(outageBranchId).getId(),
                                MonitoringObjectType.FLOWGATE,
                                flowgate.getId(),
                                MonitoringExceptionStatus.EXCLUDE)),
                        config(),
                        1);

        assertEquals(0, results.size());
    }

    private static DclfContingencyConfig config() {
        DclfContingencyConfig config = new DclfContingencyConfig();
        config.setDclfInclLoss(false);
        config.setOverloadThreshold(100.0);
        return config;
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
