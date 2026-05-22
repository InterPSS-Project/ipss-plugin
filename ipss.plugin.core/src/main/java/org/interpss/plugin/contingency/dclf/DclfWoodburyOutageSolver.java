package org.interpss.plugin.contingency.dclf;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.ForkJoinPool;
import java.util.stream.IntStream;

import com.interpss.common.exp.InterpssException;
import com.interpss.core.aclf.AclfBranch;
import com.interpss.core.algo.dclf.ContingencyAnalysisAlgorithm;
import com.interpss.core.algo.dclf.adapter.DclfAlgoBranch;
import com.interpss.core.contingency.ContingencyBranchOutageType;
import com.interpss.core.contingency.dclf.DclfOutageBranch;

/**
 * Woodbury-style DCLF outage solver for fixed-topology sensitivity studies.
 */
public final class DclfWoodburyOutageSolver {
    private final ContingencyAnalysisAlgorithm dclfAlgorithm;

    public DclfWoodburyOutageSolver(ContingencyAnalysisAlgorithm dclfAlgorithm) {
        if (dclfAlgorithm == null) {
            throw new IllegalArgumentException("dclfAlgorithm cannot be null");
        }
        this.dclfAlgorithm = dclfAlgorithm;
    }

    public double singleOpenLodf(DclfOutageBranch outageBranch, AclfBranch monitorBranch)
            throws InterpssException {
        validateOpenOutage(outageBranch);
        return dclfAlgorithm.lineOutageDFactor(outageBranch, monitorBranch);
    }

    public double singleOpenPostFlow(DclfOutageBranch outageBranch, DclfAlgoBranch monitorBranch)
            throws InterpssException {
        validateOpenOutage(outageBranch);
        return dclfAlgorithm.calPostOutageFlow(outageBranch, monitorBranch);
    }

    public MultiOpenResult solveMultiOpen(DclfOutageBranch[] outageBranches, AclfBranch[] monitorBranches)
            throws InterpssException {
        return solveMultiOpen(outageBranches, monitorBranches, 1);
    }

    public MultiOpenResult solveMultiOpen(
            DclfOutageBranch[] outageBranches,
            AclfBranch[] monitorBranches,
            int parallelism)
            throws InterpssException {
        if (outageBranches == null || outageBranches.length == 0) {
            throw new IllegalArgumentException("outageBranches cannot be null or empty");
        }
        if (monitorBranches == null) {
            throw new IllegalArgumentException("monitorBranches cannot be null");
        }
        for (DclfOutageBranch outageBranch : outageBranches) {
            validateOpenOutage(outageBranch);
        }

        int[] originalSortNumbers = saveSortNumbers(outageBranches);
        List<DclfOutageBranch> originalOutageList = new ArrayList<>(dclfAlgorithm.getOutageBranchList());
        dclfAlgorithm.getOutageBranchList().clear();
        dclfAlgorithm.getOutageBranchList().addAll(Arrays.asList(outageBranches));

        try {
            Object invEptdf = dclfAlgorithm.calMultiOutageInvE_PTDF("woodbury");
            double[][] lodfByMonitor = new double[monitorBranches.length][];
            double[] shiftedFlowPu = new double[monitorBranches.length];
            double[] preFlowPu = new double[monitorBranches.length];
            double[] postFlowPu = new double[monitorBranches.length];
            double[] outagePreFlowPu = new double[outageBranches.length];
            for (int outageIndex = 0; outageIndex < outageBranches.length; outageIndex++) {
                DclfAlgoBranch outageDclfBranch =
                        dclfAlgorithm.getDclfAlgoBranch(outageBranches[outageIndex].getBranch().getId());
                outagePreFlowPu[outageIndex] = outageDclfBranch.getDclfFlow();
                outageBranches[outageIndex].setDclfFlow(outagePreFlowPu[outageIndex]);
            }

            runMonitorSolve(
                    monitorBranches,
                    invEptdf,
                    outageBranches.length,
                    outagePreFlowPu,
                    lodfByMonitor,
                    preFlowPu,
                    shiftedFlowPu,
                    postFlowPu,
                    parallelism);

            return new MultiOpenResult(
                    Arrays.copyOf(outageBranches, outageBranches.length),
                    Arrays.copyOf(monitorBranches, monitorBranches.length),
                    lodfByMonitor,
                    preFlowPu,
                    shiftedFlowPu,
                    postFlowPu,
                    dclfAlgorithm.getAclfNet().getBaseMva());
        } finally {
            restoreSortNumbers(outageBranches, originalSortNumbers);
            dclfAlgorithm.getOutageBranchList().clear();
            dclfAlgorithm.getOutageBranchList().addAll(originalOutageList);
        }
    }

    private void runMonitorSolve(
            AclfBranch[] monitorBranches,
            Object invEptdf,
            int outageCount,
            double[] outagePreFlowPu,
            double[][] lodfByMonitor,
            double[] preFlowPu,
            double[] shiftedFlowPu,
            double[] postFlowPu,
            int parallelism)
            throws InterpssException {
        if (parallelism <= 1 || monitorBranches.length <= 1) {
            for (int monitorIndex = 0; monitorIndex < monitorBranches.length; monitorIndex++) {
                solveMonitor(
                        monitorIndex,
                        monitorBranches,
                        invEptdf,
                        outageCount,
                        outagePreFlowPu,
                        lodfByMonitor,
                        preFlowPu,
                        shiftedFlowPu,
                        postFlowPu);
            }
            return;
        }

        ForkJoinPool pool = new ForkJoinPool(parallelism);
        try {
            pool.submit(() -> IntStream.range(0, monitorBranches.length).parallel().forEach(monitorIndex -> {
                try {
                    solveMonitor(
                            monitorIndex,
                            monitorBranches,
                            invEptdf,
                            outageCount,
                            outagePreFlowPu,
                            lodfByMonitor,
                            preFlowPu,
                            shiftedFlowPu,
                            postFlowPu);
                } catch (InterpssException e) {
                    throw new RuntimeException(e);
                }
            })).get();
        } catch (Exception e) {
            Throwable cause = e.getCause() != null ? e.getCause() : e;
            if (cause instanceof RuntimeException && cause.getCause() instanceof InterpssException) {
                throw (InterpssException) cause.getCause();
            }
            throw new InterpssException(cause.toString());
        } finally {
            pool.close();
            pool.shutdown();
        }
    }

    private void solveMonitor(
            int monitorIndex,
            AclfBranch[] monitorBranches,
            Object invEptdf,
            int outageCount,
            double[] outagePreFlowPu,
            double[][] lodfByMonitor,
            double[] preFlowPu,
            double[] shiftedFlowPu,
            double[] postFlowPu)
            throws InterpssException {
        AclfBranch monitorBranch = monitorBranches[monitorIndex];
        DclfAlgoBranch monitorDclfBranch = dclfAlgorithm.getDclfAlgoBranch(monitorBranch.getId());
        preFlowPu[monitorIndex] = monitorDclfBranch != null ? monitorDclfBranch.getDclfFlow() : 0.0;

        double[] lodfs = dclfAlgorithm.calMultiOutageLODFs(monitorBranch, invEptdf);
        lodfByMonitor[monitorIndex] = lodfs;
        if (lodfs == null) {
            shiftedFlowPu[monitorIndex] = -preFlowPu[monitorIndex];
        } else {
            if (lodfs.length != outageCount) {
                throw new InterpssException(
                        "Linearly dependent multi-outage rows are not supported by this Woodbury wrapper yet");
            }
            for (int outageIndex = 0; outageIndex < outageCount; outageIndex++) {
                shiftedFlowPu[monitorIndex] += outagePreFlowPu[outageIndex] * lodfs[outageIndex];
            }
        }
        postFlowPu[monitorIndex] = preFlowPu[monitorIndex] + shiftedFlowPu[monitorIndex];
    }

    private static int[] saveSortNumbers(DclfOutageBranch[] outageBranches) {
        int[] sortNumbers = new int[outageBranches.length];
        for (int i = 0; i < outageBranches.length; i++) {
            sortNumbers[i] = outageBranches[i].getBranch().getSortNumber();
        }
        return sortNumbers;
    }

    private static void restoreSortNumbers(DclfOutageBranch[] outageBranches, int[] sortNumbers) {
        for (int i = 0; i < outageBranches.length; i++) {
            outageBranches[i].getBranch().setSortNumber(sortNumbers[i]);
        }
    }

    private static void validateOpenOutage(DclfOutageBranch outageBranch) throws InterpssException {
        if (outageBranch == null || outageBranch.getBranch() == null) {
            throw new InterpssException("DCLF outage branch cannot be null");
        }
        if (outageBranch.getOutageType() != ContingencyBranchOutageType.OPEN) {
            throw new InterpssException(
                    "DclfWoodburyOutageSolver currently supports OPEN branch outages only: "
                            + outageBranch.getId());
        }
    }

    public static final class MultiOpenResult {
        private final DclfOutageBranch[] outageBranches;
        private final AclfBranch[] monitorBranches;
        private final double[][] lodfByMonitor;
        private final double[] preFlowPu;
        private final double[] shiftedFlowPu;
        private final double[] postFlowPu;
        private final double baseMva;

        private MultiOpenResult(
                DclfOutageBranch[] outageBranches,
                AclfBranch[] monitorBranches,
                double[][] lodfByMonitor,
                double[] preFlowPu,
                double[] shiftedFlowPu,
                double[] postFlowPu,
                double baseMva) {
            this.outageBranches = outageBranches;
            this.monitorBranches = monitorBranches;
            this.lodfByMonitor = copy(lodfByMonitor);
            this.preFlowPu = Arrays.copyOf(preFlowPu, preFlowPu.length);
            this.shiftedFlowPu = Arrays.copyOf(shiftedFlowPu, shiftedFlowPu.length);
            this.postFlowPu = Arrays.copyOf(postFlowPu, postFlowPu.length);
            this.baseMva = baseMva;
        }

        public int getOutageCount() {
            return outageBranches.length;
        }

        public int getMonitorCount() {
            return monitorBranches.length;
        }

        public DclfOutageBranch[] getOutageBranches() {
            return Arrays.copyOf(outageBranches, outageBranches.length);
        }

        public AclfBranch[] getMonitorBranches() {
            return Arrays.copyOf(monitorBranches, monitorBranches.length);
        }

        public double[] getLodfs(int monitorIndex) {
            double[] lodfs = lodfByMonitor[monitorIndex];
            return lodfs == null ? null : Arrays.copyOf(lodfs, lodfs.length);
        }

        public double getPreFlowPu(int monitorIndex) {
            return preFlowPu[monitorIndex];
        }

        public double getShiftedFlowPu(int monitorIndex) {
            return shiftedFlowPu[monitorIndex];
        }

        public double getPostFlowPu(int monitorIndex) {
            return postFlowPu[monitorIndex];
        }

        public double getPreFlowMw(int monitorIndex) {
            return preFlowPu[monitorIndex] * baseMva;
        }

        public double getShiftedFlowMw(int monitorIndex) {
            return shiftedFlowPu[monitorIndex] * baseMva;
        }

        public double getPostFlowMw(int monitorIndex) {
            return postFlowPu[monitorIndex] * baseMva;
        }

        private static double[][] copy(double[][] data) {
            double[][] copied = new double[data.length][];
            for (int i = 0; i < data.length; i++) {
                copied[i] = data[i] == null ? null : Arrays.copyOf(data[i], data[i].length);
            }
            return copied;
        }
    }
}
