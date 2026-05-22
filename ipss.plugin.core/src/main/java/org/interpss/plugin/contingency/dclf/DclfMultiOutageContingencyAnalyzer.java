package org.interpss.plugin.contingency.dclf;

import static com.interpss.core.DclfAlgoObjectFactory.createContingencyAnalysisAlgorithm;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentLinkedQueue;

import com.interpss.algo.parallel.BranchCAResultRec;
import com.interpss.common.exp.InterpssException;
import com.interpss.core.aclf.AclfBranch;
import com.interpss.core.aclf.AclfNetwork;
import com.interpss.core.algo.dclf.ContingencyAnalysisAlgorithm;
import com.interpss.core.algo.dclf.DclfWoodburyOutageSolver;
import com.interpss.core.algo.dclf.DclfMethod;
import com.interpss.core.algo.dclf.adapter.DclfAlgoBranch;
import com.interpss.core.contingency.ContingencyBranchOutageType;
import com.interpss.core.contingency.dclf.DclfMultiOutage;
import com.interpss.core.contingency.dclf.DclfOutageBranch;

/**
 * Applies the Woodbury multi-open DCLF solve to multi-branch contingencies.
 */
public final class DclfMultiOutageContingencyAnalyzer {
    private final ContingencyAnalysisAlgorithm dclfAlgorithm;
    private final AclfBranch[] monitoredBranches;
    private final double overloadThreshold;
    private final double shiftThresholdMw;

    public DclfMultiOutageContingencyAnalyzer(
            ContingencyAnalysisAlgorithm dclfAlgorithm,
            AclfBranch[] monitoredBranches,
            double overloadThreshold,
            double shiftThresholdMw) {
        if (dclfAlgorithm == null) {
            throw new IllegalArgumentException("dclfAlgorithm cannot be null");
        }
        if (monitoredBranches == null) {
            throw new IllegalArgumentException("monitoredBranches cannot be null");
        }
        this.dclfAlgorithm = dclfAlgorithm;
        this.monitoredBranches = Arrays.copyOf(monitoredBranches, monitoredBranches.length);
        this.overloadThreshold = overloadThreshold;
        this.shiftThresholdMw = shiftThresholdMw;
    }

    public static ConcurrentLinkedQueue<BranchCAResultRec> performContingencyAnalysis(
            AclfNetwork aclfNet,
            List<DclfMultiOutage> contingencyList,
            Set<String> monitoredBranchIds,
            double overloadThreshold,
            boolean dclfInclLoss,
            int parallelismLevel)
            throws InterpssException {
        ContingencyAnalysisAlgorithm dclfAlgorithm = createContingencyAnalysisAlgorithm(aclfNet);
        DclfMethod method = dclfInclLoss ? DclfMethod.INC_LOSS : DclfMethod.STD;
        dclfAlgorithm.calculateDclf(method);

        AclfBranch[] monitoredBranches = resolveMonitoredBranches(dclfAlgorithm, monitoredBranchIds);
        DclfMultiOutageContingencyAnalyzer analyzer =
                new DclfMultiOutageContingencyAnalyzer(
                        dclfAlgorithm,
                        monitoredBranches,
                        overloadThreshold,
                        BranchCAResultRec.ContingencyShiftThreshold);
        return analyzer.analyze(contingencyList, parallelismLevel);
    }

    public ConcurrentLinkedQueue<BranchCAResultRec> analyze(
            List<DclfMultiOutage> contingencyList,
            int parallelism)
            throws InterpssException {
        if (contingencyList == null) {
            throw new IllegalArgumentException("contingencyList cannot be null");
        }

        ConcurrentLinkedQueue<BranchCAResultRec> results = new ConcurrentLinkedQueue<>();
        DclfWoodburyOutageSolver solver = new DclfWoodburyOutageSolver(dclfAlgorithm);

        for (DclfMultiOutage contingency : contingencyList) {
            DclfOutageBranch[] outageBranches = outageBranches(contingency);
            refreshOutagePreFlows(outageBranches);

            DclfWoodburyOutageSolver.MultiOpenResult solved =
                    solver.solveMultiOpen(outageBranches, monitoredBranches, parallelism);
            for (int monitorIndex = 0; monitorIndex < solved.getMonitorCount(); monitorIndex++) {
                double shiftedFlowMw = solved.getShiftedFlowMw(monitorIndex);
                if (Math.abs(shiftedFlowMw) <= shiftThresholdMw) {
                    continue;
                }

                BranchCAResultRec result =
                        new BranchCAResultRec(
                                contingency,
                                monitoredBranches[monitorIndex],
                                solved.getPreFlowMw(monitorIndex),
                                shiftedFlowMw,
                                solved.getLodfs(monitorIndex));
                if (result.calLoadingPercent() >= overloadThreshold) {
                    results.add(result);
                }
            }
        }

        return results;
    }

    private DclfOutageBranch[] outageBranches(DclfMultiOutage contingency) throws InterpssException {
        if (contingency == null) {
            throw new InterpssException("DCLF multi-outage contingency cannot be null");
        }
        if (contingency.getOutageType() != ContingencyBranchOutageType.OPEN) {
            throw new InterpssException(
                    "DclfMultiOutageContingencyAnalyzer currently supports OPEN branch outages only: "
                            + contingency.getId());
        }
        if (contingency.getOutageEquips().isEmpty()) {
            throw new InterpssException("DCLF multi-outage contingency has no outage branches: "
                    + contingency.getId());
        }

        DclfOutageBranch[] outageBranches = new DclfOutageBranch[contingency.getOutageEquips().size()];
        for (int i = 0; i < contingency.getOutageEquips().size(); i++) {
            DclfOutageBranch outageBranch = contingency.getOutageEquips().get(i);
            if (outageBranch == null || outageBranch.getBranch() == null) {
                throw new InterpssException("DCLF multi-outage contingency has an invalid outage branch: "
                        + contingency.getId());
            }
            outageBranches[i] = outageBranch;
        }
        return outageBranches;
    }

    private void refreshOutagePreFlows(DclfOutageBranch[] outageBranches) {
        for (DclfOutageBranch outageBranch : outageBranches) {
            DclfAlgoBranch dclfBranch =
                    dclfAlgorithm.getDclfAlgoBranch(outageBranch.getBranch().getId());
            outageBranch.setDclfFlow(dclfBranch != null ? dclfBranch.getDclfFlow() : 0.0);
        }
    }

    private static AclfBranch[] resolveMonitoredBranches(
            ContingencyAnalysisAlgorithm dclfAlgorithm,
            Set<String> monitoredBranchIds) {
        List<AclfBranch> branches = new ArrayList<>();
        for (DclfAlgoBranch dclfBranch : dclfAlgorithm.getDclfAlgoBranchList()) {
            AclfBranch branch = dclfBranch.getBranch();
            if (branch != null
                    && branch.isActive()
                    && (monitoredBranchIds == null || monitoredBranchIds.contains(branch.getId()))) {
                branches.add(branch);
            }
        }
        return branches.toArray(new AclfBranch[0]);
    }
}
