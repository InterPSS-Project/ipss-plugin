package org.interpss.plugin.contingency.dclf;

import static com.interpss.core.DclfAlgoObjectFactory.createCaMonitoringBranch;
import static com.interpss.core.DclfAlgoObjectFactory.createCaOutageBranch;
import static com.interpss.core.DclfAlgoObjectFactory.createContingencyAnalysisAlgorithm;
import static com.interpss.core.DclfAlgoObjectFactory.createMultiOutageContingency;

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
import com.interpss.core.algo.dclf.DclfContingencySolutionMethod;
import com.interpss.core.algo.dclf.DclfMethod;
import com.interpss.core.algo.dclf.adapter.DclfAlgoBranch;
import com.interpss.core.contingency.ContingencyBranchOutageType;
import com.interpss.core.contingency.dclf.DclfMonitoringBranch;
import com.interpss.core.contingency.dclf.DclfMultiOutage;
import com.interpss.core.contingency.dclf.DclfOutageBranch;
import org.interpss.plugin.contingency.DclfContingencyConfig;

/**
 * Applies core DCLF CA solution-method selection to multi-branch contingencies.
 */
public final class DclfMultiOutageContingencyAnalyzer {
    private final ContingencyAnalysisAlgorithm dclfAlgorithm;
    private final AclfBranch[] monitoredBranches;
    private final double overloadThreshold;
    private final double shiftThresholdMw;
    private final DclfContingencySolutionMethod solutionMethod;

    public DclfMultiOutageContingencyAnalyzer(
            ContingencyAnalysisAlgorithm dclfAlgorithm,
            AclfBranch[] monitoredBranches,
            double overloadThreshold,
            double shiftThresholdMw) {
        this(
                dclfAlgorithm,
                monitoredBranches,
                overloadThreshold,
                shiftThresholdMw,
                DclfContingencySolutionMethod.WoodburyMatrixUpdate);
    }

    public DclfMultiOutageContingencyAnalyzer(
            ContingencyAnalysisAlgorithm dclfAlgorithm,
            AclfBranch[] monitoredBranches,
            double overloadThreshold,
            double shiftThresholdMw,
            DclfContingencySolutionMethod solutionMethod) {
        if (dclfAlgorithm == null) {
            throw new IllegalArgumentException("dclfAlgorithm cannot be null");
        }
        if (monitoredBranches == null) {
            throw new IllegalArgumentException("monitoredBranches cannot be null");
        }
        if (solutionMethod == null) {
            throw new IllegalArgumentException("solutionMethod cannot be null");
        }
        this.dclfAlgorithm = dclfAlgorithm;
        this.monitoredBranches = Arrays.copyOf(monitoredBranches, monitoredBranches.length);
        this.overloadThreshold = overloadThreshold;
        this.shiftThresholdMw = shiftThresholdMw;
        this.solutionMethod = solutionMethod;
    }

    public static ConcurrentLinkedQueue<BranchCAResultRec> performContingencyAnalysis(
            AclfNetwork aclfNet,
            List<DclfMultiOutage> contingencyList,
            Set<String> monitoredBranchIds,
            DclfContingencyConfig config,
            int parallelismLevel)
            throws InterpssException {
        if (config == null) {
            throw new IllegalArgumentException("config cannot be null");
        }
        return performContingencyAnalysis(
                aclfNet,
                contingencyList,
                monitoredBranchIds,
                config.getOverloadThreshold(),
                config.isDclfInclLoss(),
                parallelismLevel,
                config.getSolutionMethod());
    }

    public static ConcurrentLinkedQueue<BranchCAResultRec> performContingencyAnalysis(
            AclfNetwork aclfNet,
            List<DclfMultiOutage> contingencyList,
            Set<String> monitoredBranchIds,
            double overloadThreshold,
            boolean dclfInclLoss,
            int parallelismLevel)
            throws InterpssException {
        return performContingencyAnalysis(
                aclfNet,
                contingencyList,
                monitoredBranchIds,
                overloadThreshold,
                dclfInclLoss,
                parallelismLevel,
                DclfContingencySolutionMethod.WoodburyMatrixUpdate);
    }

    public static ConcurrentLinkedQueue<BranchCAResultRec> performContingencyAnalysis(
            AclfNetwork aclfNet,
            List<DclfMultiOutage> contingencyList,
            Set<String> monitoredBranchIds,
            double overloadThreshold,
            boolean dclfInclLoss,
            int parallelismLevel,
            DclfContingencySolutionMethod solutionMethod)
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
                        BranchCAResultRec.ContingencyShiftThreshold,
                        solutionMethod);
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
        dclfAlgorithm.setSolutionMethod(solutionMethod);
        double baseMva = dclfAlgorithm.getAclfNet().getBaseMva();

        for (DclfMultiOutage contingency : contingencyList) {
            DclfMultiOutage workingContingency = createWorkingContingency(contingency);
            dclfAlgorithm.ca(workingContingency);

            for (DclfMonitoringBranch monitoringBranch : workingContingency.getMonitoringBranches()) {
                AclfBranch monitor = monitoringBranch.getBranch();
                DclfAlgoBranch dclfBranch = dclfAlgorithm.getDclfAlgoBranch(monitor.getId());
                double preFlowMw = dclfBranch.getDclfFlow() * baseMva;
                double shiftedFlowMw = monitoringBranch.getShiftedFlow() * baseMva;
                if (Math.abs(shiftedFlowMw) <= shiftThresholdMw) {
                    continue;
                }

                BranchCAResultRec result =
                        new BranchCAResultRec(
                                workingContingency,
                                monitor,
                                preFlowMw,
                                shiftedFlowMw);
                if (result.calLoadingPercent() >= overloadThreshold) {
                    results.add(result);
                }
            }
        }

        return results;
    }

    private DclfMultiOutage createWorkingContingency(DclfMultiOutage contingency) throws InterpssException {
        if (contingency == null) {
            throw new InterpssException("DCLF multi-outage contingency cannot be null");
        }
        if (contingency.getOutageType() != ContingencyBranchOutageType.OPEN
                && contingency.getOutageType() != ContingencyBranchOutageType.CLOSE) {
            throw new InterpssException(
                    "DclfMultiOutageContingencyAnalyzer supports OPEN and CLOSE branch outages only: "
                            + contingency.getId());
        }
        if (contingency.getOutageEquips().isEmpty()) {
            throw new InterpssException("DCLF multi-outage contingency has no outage branches: "
                    + contingency.getId());
        }

        DclfMultiOutage workingContingency =
                createMultiOutageContingency(contingency.getId(), contingency.getOutageType());
        for (int i = 0; i < contingency.getOutageEquips().size(); i++) {
            DclfOutageBranch outageBranch = contingency.getOutageEquips().get(i);
            if (outageBranch == null || outageBranch.getBranch() == null) {
                throw new InterpssException("DCLF multi-outage contingency has an invalid outage branch: "
                        + contingency.getId());
            }
            DclfAlgoBranch dclfBranch =
                    dclfAlgorithm.getDclfAlgoBranch(outageBranch.getBranch().getId());
            if (dclfBranch == null) {
                throw new InterpssException("DCLF outage branch is not available in the analysis algorithm: "
                        + outageBranch.getBranch().getId());
            }
            DclfOutageBranch workingOutage = createCaOutageBranch(dclfBranch, contingency.getOutageType());
            workingOutage.setDclfFlow(dclfBranch.getDclfFlow());
            workingContingency.getOutageEquips().add(workingOutage);
        }

        for (AclfBranch monitor : monitoredBranches) {
            DclfAlgoBranch dclfBranch = dclfAlgorithm.getDclfAlgoBranch(monitor.getId());
            if (dclfBranch != null) {
                workingContingency.addMonitoringBranch(createCaMonitoringBranch(dclfBranch));
            }
        }
        return workingContingency;
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
