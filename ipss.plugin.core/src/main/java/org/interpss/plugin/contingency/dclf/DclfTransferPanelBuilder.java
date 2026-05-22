package org.interpss.plugin.contingency.dclf;

import static com.interpss.core.DclfAlgoObjectFactory.createContingencyAnalysisAlgorithm;

import java.util.ArrayList;
import java.util.Arrays;
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

        double[] denominator = new double[contingencies.length];
        boolean[] validOutage = new boolean[contingencies.length];
        ChunkBuilder[] chunkBuilders = createChunkBuilders(monitors, contingencies.length, options);

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
            validOutage[outageIndex] =
                    Math.abs(denominator[outageIndex]) > options.getDenominatorTolerance()
                            || Math.abs(Math.abs(outagePtdf) - 1.0) <= options.getDenominatorTolerance();

            double[] lodfVector = dclfAlgo.lineOutageDFactors(outageBranch);
            for (ChunkBuilder chunkBuilder : chunkBuilders) {
                chunkBuilder.setOutageColumn(outageIndex, lodfVector);
            }
        }

        DclfTransferPanelChunk[] chunks = buildChunks(chunkBuilders);

        return new DclfTransferPanelCache(
                spec,
                dclfAlgo,
                monitors,
                contingencies,
                chunks,
                denominator,
                validOutage);
    }

    private static ChunkBuilder[] createChunkBuilders(
            AclfBranch[] monitors,
            int outageCount,
            PanelBuildOptions options) {
        int chunkSize = options.getMonitorChunkSize() > 0 ? options.getMonitorChunkSize() : monitors.length;
        if (chunkSize == 0) {
            return new ChunkBuilder[0];
        }

        List<ChunkBuilder> chunks = new ArrayList<>();
        for (int monitorOffset = 0; monitorOffset < monitors.length; monitorOffset += chunkSize) {
            int end = Math.min(monitors.length, monitorOffset + chunkSize);
            AclfBranch[] chunkMonitors = Arrays.copyOfRange(monitors, monitorOffset, end);
            chunks.add(new ChunkBuilder(monitorOffset, chunkMonitors, outageCount));
        }

        return chunks.toArray(new ChunkBuilder[0]);
    }

    private static DclfTransferPanelChunk[] buildChunks(ChunkBuilder[] builders) {
        DclfTransferPanelChunk[] chunks = new DclfTransferPanelChunk[builders.length];
        for (int i = 0; i < builders.length; i++) {
            chunks[i] = builders[i].build();
        }
        return chunks;
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

    private static final class ChunkBuilder {
        private final int monitorOffset;
        private final AclfBranch[] monitors;
        private final double[][] lodfPanel;

        private ChunkBuilder(int monitorOffset, AclfBranch[] monitors, int outageCount) {
            this.monitorOffset = monitorOffset;
            this.monitors = monitors;
            this.lodfPanel = new double[monitors.length][outageCount];
        }

        private void setOutageColumn(int outageIndex, double[] lodfVector) {
            for (int monitorIndex = 0; monitorIndex < monitors.length; monitorIndex++) {
                lodfPanel[monitorIndex][outageIndex] = lodfVector[monitors[monitorIndex].getSortNumber()];
            }
        }

        private DclfTransferPanelChunk build() {
            return new DclfTransferPanelChunk(monitorOffset, monitors, lodfPanel);
        }
    }
}
