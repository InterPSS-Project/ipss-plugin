package org.interpss.plugin.contingency.dclf;

import static com.interpss.core.DclfAlgoObjectFactory.createContingencyAnalysisAlgorithm;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import com.interpss.common.exp.InterpssException;
import com.interpss.core.aclf.AclfBranch;
import com.interpss.core.algo.dclf.ContingencyAnalysisAlgorithm;
import com.interpss.core.algo.dclf.adapter.DclfAlgoBranch;
import com.interpss.core.algo.dclf.solver.IDclfSolver.CacheType;
import com.interpss.core.contingency.ContingencyBranchOutageType;
import com.interpss.core.contingency.dclf.DclfBranchOutage;
import com.interpss.core.contingency.dclf.DclfOutageBranch;

/**
 * Builds fixed-topology DCLF LODF panels from existing InterPSS DCLF APIs.
 */
public final class DclfTransferPanelBuilder {
    private DclfTransferPanelBuilder() {
    }

    public static DclfTransferPanelCache build(DclfContingencyStudySpec spec) throws InterpssException {
        return build(spec, PanelBuildOptions.defaults());
    }

    public static DclfTransferPanelCache build(DclfContingencyStudySpec spec, PanelBuildOptions options)
            throws InterpssException {
        if (spec == null) {
            throw new IllegalArgumentException("spec cannot be null");
        }
        if (options == null) {
            throw new IllegalArgumentException("options cannot be null");
        }

        ContingencyAnalysisAlgorithm dclfAlgo =
                createContingencyAnalysisAlgorithm(spec.getAclfNetwork(), CacheType.SenCached, true);
        dclfAlgo.calculateDclf(spec.getMethod());

        AclfBranch[] monitors = resolveMonitoredBranches(spec, dclfAlgo);
        DclfBranchOutage[] contingencies = spec.getContingencies().toArray(new DclfBranchOutage[0]);

        double[][] lodfPanel = new double[monitors.length][contingencies.length];
        double[] denominator = new double[contingencies.length];
        boolean[] validOutage = new boolean[contingencies.length];

        for (int outageIndex = 0; outageIndex < contingencies.length; outageIndex++) {
            DclfBranchOutage contingency = contingencies[outageIndex];
            DclfOutageBranch outageBranch = contingency.getOutageEquip();
            validateOpenOutage(outageBranch);

            AclfBranch aclfOutageBranch = outageBranch.getBranch();
            double outagePtdf = dclfAlgo.pTransferDistFactor(
                    aclfOutageBranch.getFromBus().getId(),
                    aclfOutageBranch.getToBus().getId(),
                    aclfOutageBranch);
            denominator[outageIndex] = 1.0 - outagePtdf;
            validOutage[outageIndex] = Math.abs(denominator[outageIndex]) > options.getDenominatorTolerance();

            for (int monitorIndex = 0; monitorIndex < monitors.length; monitorIndex++) {
                lodfPanel[monitorIndex][outageIndex] =
                        dclfAlgo.lineOutageDFactor(outageBranch, monitors[monitorIndex]);
            }
        }

        return new DclfTransferPanelCache(
                spec,
                dclfAlgo,
                monitors,
                contingencies,
                lodfPanel,
                denominator,
                validOutage);
    }

    private static AclfBranch[] resolveMonitoredBranches(
            DclfContingencyStudySpec spec,
            ContingencyAnalysisAlgorithm dclfAlgo) {
        Set<String> monitoredBranchIds = spec.getMonitoredBranchIds();
        List<AclfBranch> branches = new ArrayList<>();

        for (DclfAlgoBranch dclfBranch : dclfAlgo.getDclfAlgoBranchList()) {
            AclfBranch branch = dclfBranch.getBranch();
            if (branch != null
                    && branch.isActive()
                    && (monitoredBranchIds == null || monitoredBranchIds.contains(branch.getId()))) {
                branches.add(branch);
            }
        }

        return branches.toArray(new AclfBranch[0]);
    }

    private static void validateOpenOutage(DclfOutageBranch outageBranch) throws InterpssException {
        if (outageBranch == null || outageBranch.getBranch() == null) {
            throw new InterpssException("DCLF contingency has no outage branch");
        }
        if (outageBranch.getOutageType() != ContingencyBranchOutageType.OPEN) {
            throw new InterpssException(
                    "DclfTransferPanelBuilder currently supports OPEN branch contingencies only: "
                            + outageBranch.getId());
        }
    }
}
