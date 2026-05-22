package org.interpss.plugin.contingency.dclf;

import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ForkJoinPool;

import com.interpss.algo.parallel.BranchCAResultRec;
import com.interpss.core.aclf.AclfBranch;
import com.interpss.core.algo.dclf.ContingencyAnalysisAlgorithm;
import com.interpss.core.contingency.dclf.DclfBranchOutage;

/**
 * Applies a precomputed DCLF LODF transfer panel to the current network profile.
 */
final class CachedDclfContingencyAnalyzer {
    private final DclfTransferPanelCache cache;

    public CachedDclfContingencyAnalyzer(DclfTransferPanelCache cache) {
        if (cache == null) {
            throw new IllegalArgumentException("cache cannot be null");
        }
        this.cache = cache;
    }

    public ConcurrentLinkedQueue<BranchCAResultRec> analyzeCurrentProfile() {
        return analyzeCurrentProfile(1);
    }

    public ConcurrentLinkedQueue<BranchCAResultRec> analyzeCurrentProfile(int parallelism) {
        ContingencyAnalysisAlgorithm dclfAlgo = cache.getDclfAlgorithm();
        DclfContingencyStudySpec spec = cache.getSpec();
        dclfAlgo.calculateDclf(spec.getMethod());

        DclfBranchOutage[] contingencies = cache.getContingencies();
        DclfTransferPanelChunk[] chunks = cache.getChunks();
        double baseMva = spec.getAclfNetwork().getBaseMva();
        ConcurrentLinkedQueue<BranchCAResultRec> results = new ConcurrentLinkedQueue<>();
        double[] outagePreFlowMw = snapshotOutageFlows(dclfAlgo, contingencies, baseMva);
        double[][] monitorPreFlowMw = snapshotMonitorFlows(dclfAlgo, chunks, baseMva);

        if (parallelism <= 1 || chunks.length <= 1) {
            for (int chunkIndex = 0; chunkIndex < chunks.length; chunkIndex++) {
                scanChunk(
                        chunkIndex,
                        chunks[chunkIndex],
                        contingencies,
                        outagePreFlowMw,
                        monitorPreFlowMw[chunkIndex],
                        spec,
                        results);
            }
            return results;
        }

        ForkJoinPool pool = new ForkJoinPool(parallelism);
        try {
            pool.submit(() -> java.util.stream.IntStream.range(0, chunks.length).parallel().forEach(chunkIndex -> {
                scanChunk(
                        chunkIndex,
                        chunks[chunkIndex],
                        contingencies,
                        outagePreFlowMw,
                        monitorPreFlowMw[chunkIndex],
                        spec,
                        results);
            })).get();
        } catch (Exception e) {
            throw new RuntimeException("Cached DCLF contingency scan failed", e);
        } finally {
            pool.close();
            pool.shutdown();
        }

        return results;
    }

    private double[] snapshotOutageFlows(
            ContingencyAnalysisAlgorithm dclfAlgo,
            DclfBranchOutage[] contingencies,
            double baseMva) {
        double[] outagePreFlowMw = new double[contingencies.length];

        for (int outageIndex = 0; outageIndex < contingencies.length; outageIndex++) {
            DclfBranchOutage contingency = contingencies[outageIndex];
            double outagePreFlowPu =
                    dclfAlgo.getDclfAlgoBranch(contingency.getOutageEquip().getBranch().getId()).getDclfFlow();
            contingency.getOutageEquip().setDclfFlow(outagePreFlowPu);
            outagePreFlowMw[outageIndex] = outagePreFlowPu * baseMva;
        }

        return outagePreFlowMw;
    }

    private static double[][] snapshotMonitorFlows(
            ContingencyAnalysisAlgorithm dclfAlgo,
            DclfTransferPanelChunk[] chunks,
            double baseMva) {
        double[][] monitorPreFlowMw = new double[chunks.length][];
        for (int chunkIndex = 0; chunkIndex < chunks.length; chunkIndex++) {
            DclfTransferPanelChunk chunk = chunks[chunkIndex];
            monitorPreFlowMw[chunkIndex] = new double[chunk.getMonitorCount()];
            for (int localMonitorIndex = 0; localMonitorIndex < chunk.getMonitorCount(); localMonitorIndex++) {
                AclfBranch monitor = chunk.getMonitoredBranch(localMonitorIndex);
                monitorPreFlowMw[chunkIndex][localMonitorIndex] =
                        dclfAlgo.getDclfAlgoBranch(monitor.getId()).getDclfFlow() * baseMva;
            }
        }
        return monitorPreFlowMw;
    }

    private void scanChunk(
            int chunkIndex,
            DclfTransferPanelChunk chunk,
            DclfBranchOutage[] contingencies,
            double[] outagePreFlowMw,
            double[] monitorPreFlowMw,
            DclfContingencyStudySpec spec,
            ConcurrentLinkedQueue<BranchCAResultRec> results) {
        for (int outageIndex = 0; outageIndex < contingencies.length; outageIndex++) {
            if (!cache.isValidOutage(outageIndex)) {
                continue;
            }

            DclfBranchOutage contingency = contingencies[outageIndex];
            double outageFlowMw = outagePreFlowMw[outageIndex];

            for (int localMonitorIndex = 0; localMonitorIndex < chunk.getMonitorCount(); localMonitorIndex++) {
                double shiftedFlowMW = outageFlowMw * chunk.getLodf(localMonitorIndex, outageIndex);

                if (Math.abs(shiftedFlowMW) <= spec.getShiftThresholdMw()) {
                    continue;
                }

                BranchCAResultRec result =
                        new BranchCAResultRec(
                                contingency,
                                chunk.getMonitoredBranch(localMonitorIndex),
                                monitorPreFlowMw[localMonitorIndex],
                                shiftedFlowMW);
                if (result.calLoadingPercent() >= spec.getOverloadThreshold()) {
                    results.add(result);
                }
            }
        }
    }

    public DclfTransferPanelCache getCache() {
        return cache;
    }
}
