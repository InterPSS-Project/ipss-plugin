package org.interpss.mon_interface;

import static com.interpss.core.DclfAlgoObjectFactory.createCaOutageBranch;
import static com.interpss.core.DclfAlgoObjectFactory.createContingency;
import static com.interpss.core.DclfAlgoObjectFactory.createContingencyAnalysisAlgorithm;

import java.util.List;
import java.util.concurrent.ConcurrentLinkedQueue;

import org.interpss.IEEE14_SensHelper_SampleCase;

import com.interpss.common.exp.InterpssException;
import com.interpss.core.aclf.AclfBranch;
import com.interpss.core.aclf.AclfNetwork;
import com.interpss.core.algo.dclf.ContingencyAnalysisAlgorithm;
import com.interpss.core.algo.dclf.solver.ParallelDclfContingencyAnalyzer;
import com.interpss.core.contingency.ContingencyBranchOutageType;
import com.interpss.core.contingency.dclf.DclfBranchOutage;
import com.interpss.core.contingency.dclf.DclfOutageBranch;
import com.interpss.monitor.definition.MonitoredBranchRecord;
import com.interpss.monitor.definition.MonitoredInterfaceRecord;
import com.interpss.monitor.result.DclfMonitoredConstraintResult;

public class IEEE14_MinitoredInterface_Sample {

    public static void main(String args[]) throws Exception {
        AclfNetwork net = IEEE14_SensHelper_SampleCase.createSenTestCase();
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

        DclfMonitoredConstraintResult result = results.peek();
        System.out.println("violations=" + results.size()
                + "  contingency=" + (result == null ? null : result.getContingencyId())
                + "  constraint=" + (result == null ? null : result.getConstraintId()));
        System.out.println("Expected post MW=" + expectedPostValue
                + "  limit MW=" + limitMW);
        if (result != null) {
            System.out.println("INTERFACE " + result.getConstraintId()
                    + ": post=" + result.getPostValueMW()
                    + " MW, limit=" + result.getLimitMW()
                    + " MW");
        }
    }

    private static DclfBranchOutage branchOutage(
            ContingencyAnalysisAlgorithm algo,
            String branchId) throws InterpssException {
        DclfBranchOutage contingency = createContingency("cont:" + branchId);
        DclfOutageBranch outage = createCaOutageBranch(
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
        double baseMva = algo.getBaseAclfNet().getBaseMva();
        double preFlowMw =
                algo.getDclfAlgoBranch(monitorBranch.getId()).getDclfFlow() * baseMva;
        double shiftedFlowMw = outage.getDclfFlow() * baseMva
                * algo.lineOutageDFactor(outage, monitorBranch);
        return preFlowMw + shiftedFlowMw;
    }
}
