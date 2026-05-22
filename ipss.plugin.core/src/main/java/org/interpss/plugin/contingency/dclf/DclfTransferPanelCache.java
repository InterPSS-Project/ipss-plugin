package org.interpss.plugin.contingency.dclf;

import java.util.Arrays;

import com.interpss.core.aclf.AclfBranch;
import com.interpss.core.algo.dclf.ContingencyAnalysisAlgorithm;
import com.interpss.core.contingency.dclf.DclfBranchOutage;

/**
 * Immutable monitor-by-outage LODF panel for a fixed InterPSS DCLF topology.
 */
public final class DclfTransferPanelCache {
    private final DclfContingencyStudySpec spec;
    private final ContingencyAnalysisAlgorithm dclfAlgorithm;
    private final AclfBranch[] monitoredBranches;
    private final DclfBranchOutage[] contingencies;
    private final double[][] lodfPanel;
    private final double[] denominator;
    private final boolean[] validOutage;

    DclfTransferPanelCache(
            DclfContingencyStudySpec spec,
            ContingencyAnalysisAlgorithm dclfAlgorithm,
            AclfBranch[] monitoredBranches,
            DclfBranchOutage[] contingencies,
            double[][] lodfPanel,
            double[] denominator,
            boolean[] validOutage) {
        this.spec = spec;
        this.dclfAlgorithm = dclfAlgorithm;
        this.monitoredBranches = Arrays.copyOf(monitoredBranches, monitoredBranches.length);
        this.contingencies = Arrays.copyOf(contingencies, contingencies.length);
        this.lodfPanel = copy(lodfPanel);
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
        return lodfPanel[monitorIndex][outageIndex];
    }

    public double getDenominator(int outageIndex) {
        return denominator[outageIndex];
    }

    public boolean isValidOutage(int outageIndex) {
        return validOutage[outageIndex];
    }

    public long estimatePanelBytes() {
        return (long) getMonitorCount() * (long) getOutageCount() * Double.BYTES;
    }

    private static double[][] copy(double[][] data) {
        double[][] copied = new double[data.length][];
        for (int i = 0; i < data.length; i++) {
            copied[i] = Arrays.copyOf(data[i], data[i].length);
        }
        return copied;
    }
}
