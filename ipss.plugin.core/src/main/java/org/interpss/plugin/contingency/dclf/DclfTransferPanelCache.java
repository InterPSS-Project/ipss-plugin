package org.interpss.plugin.contingency.dclf;

import java.util.Arrays;

import com.interpss.core.aclf.AclfBranch;
import com.interpss.core.algo.dclf.ContingencyAnalysisAlgorithm;
import com.interpss.core.contingency.dclf.DclfBranchOutage;

/**
 * Immutable monitor-by-outage LODF panel for a fixed InterPSS DCLF topology.
 */
final class DclfTransferPanelCache {
    private final DclfContingencyStudySpec spec;
    private final ContingencyAnalysisAlgorithm dclfAlgorithm;
    private final AclfBranch[] monitoredBranches;
    private final DclfBranchOutage[] contingencies;
    private final DclfTransferPanelChunk[] chunks;
    private final double[] denominator;
    private final boolean[] validOutage;

    DclfTransferPanelCache(
            DclfContingencyStudySpec spec,
            ContingencyAnalysisAlgorithm dclfAlgorithm,
            AclfBranch[] monitoredBranches,
            DclfBranchOutage[] contingencies,
            DclfTransferPanelChunk[] chunks,
            double[] denominator,
            boolean[] validOutage) {
        this.spec = spec;
        this.dclfAlgorithm = dclfAlgorithm;
        this.monitoredBranches = Arrays.copyOf(monitoredBranches, monitoredBranches.length);
        this.contingencies = Arrays.copyOf(contingencies, contingencies.length);
        this.chunks = Arrays.copyOf(chunks, chunks.length);
        this.denominator = Arrays.copyOf(denominator, denominator.length);
        this.validOutage = Arrays.copyOf(validOutage, validOutage.length);
    }

    public DclfContingencyStudySpec getSpec() {
        return spec;
    }

    public ContingencyAnalysisAlgorithm getDclfAlgorithm() {
        return dclfAlgorithm;
    }

    public AclfBranch[] getMonitoredBranches() {
        return Arrays.copyOf(monitoredBranches, monitoredBranches.length);
    }

    public DclfBranchOutage[] getContingencies() {
        return Arrays.copyOf(contingencies, contingencies.length);
    }

    public int getMonitorCount() {
        return monitoredBranches.length;
    }

    public int getOutageCount() {
        return contingencies.length;
    }

    public double getLodf(int monitorIndex, int outageIndex) {
        for (DclfTransferPanelChunk chunk : chunks) {
            int localIndex = monitorIndex - chunk.getMonitorOffset();
            if (localIndex >= 0 && localIndex < chunk.getMonitorCount()) {
                return chunk.getLodf(localIndex, outageIndex);
            }
        }
        throw new IndexOutOfBoundsException("monitorIndex out of range: " + monitorIndex);
    }

    public double getDenominator(int outageIndex) {
        return denominator[outageIndex];
    }

    public boolean isValidOutage(int outageIndex) {
        return validOutage[outageIndex];
    }

    public long estimatePanelBytes() {
        long bytes = 0;
        for (DclfTransferPanelChunk chunk : chunks) {
            bytes += chunk.estimatePanelBytes();
        }
        return bytes;
    }

    public int getChunkCount() {
        return chunks.length;
    }

    public DclfTransferPanelChunk getChunk(int chunkIndex) {
        return chunks[chunkIndex];
    }

    public DclfTransferPanelChunk[] getChunks() {
        return Arrays.copyOf(chunks, chunks.length);
    }
}
