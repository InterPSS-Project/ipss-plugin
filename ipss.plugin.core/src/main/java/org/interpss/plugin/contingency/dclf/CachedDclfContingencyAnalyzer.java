package org.interpss.plugin.contingency.dclf;

import java.util.concurrent.ConcurrentLinkedQueue;

import com.interpss.algo.parallel.BranchCAResultRec;
import com.interpss.core.aclf.AclfBranch;
import com.interpss.core.algo.dclf.ContingencyAnalysisAlgorithm;
import com.interpss.core.contingency.dclf.DclfBranchOutage;

/**
 * Applies a precomputed DCLF LODF transfer panel to the current network profile.
 */
public final class CachedDclfContingencyAnalyzer {
    private final DclfTransferPanelCache cache;

    public CachedDclfContingencyAnalyzer(DclfTransferPanelCache cache) {
        if (cache == null) {
            throw new IllegalArgumentException("cache cannot be null");
        }
        this.cache = cache;
    }

    public ConcurrentLinkedQueue<BranchCAResultRec> analyzeCurrentProfile() {
        ContingencyAnalysisAlgorithm dclfAlgo = cache.getDclfAlgorithm();
        DclfContingencyStudySpec spec = cache.getSpec();
        dclfAlgo.calculateDclf(spec.getMethod());

        DclfBranchOutage[] contingencies = cache.getContingencies();
        double baseMva = spec.getAclfNetwork().getBaseMva();
        ConcurrentLinkedQueue<BranchCAResultRec> results = new ConcurrentLinkedQueue<>();

        for (int outageIndex = 0; outageIndex < contingencies.length; outageIndex++) {
            if (!cache.isValidOutage(outageIndex)) {
                continue;
            }

            DclfBranchOutage contingency = contingencies[outageIndex];
            double outagePreFlowMW =
                    dclfAlgo.getDclfAlgoBranch(contingency.getOutageEquip().getBranch().getId()).getDclfFlow()
                            * baseMva;

            for (DclfTransferPanelChunk chunk : cache.getChunks()) {
                for (int localMonitorIndex = 0; localMonitorIndex < chunk.getMonitorCount(); localMonitorIndex++) {
                    AclfBranch monitor = chunk.getMonitoredBranch(localMonitorIndex);
                    double preFlowMW = dclfAlgo.getDclfAlgoBranch(monitor.getId()).getDclfFlow() * baseMva;
                    double shiftedFlowMW = outagePreFlowMW * chunk.getLodf(localMonitorIndex, outageIndex);

                    if (Math.abs(shiftedFlowMW) <= spec.getShiftThresholdMw()) {
                        continue;
                    }

                    BranchCAResultRec result =
                            new BranchCAResultRec(contingency, monitor, preFlowMW, shiftedFlowMW);
                    if (result.calLoadingPercent() >= spec.getOverloadThreshold()) {
                        results.add(result);
                    }
                }
            }
        }

        return results;
    }

    public DclfTransferPanelCache getCache() {
        return cache;
    }
}
