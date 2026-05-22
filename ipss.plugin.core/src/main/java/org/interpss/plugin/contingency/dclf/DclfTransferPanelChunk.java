package org.interpss.plugin.contingency.dclf;

import java.util.Arrays;

import com.interpss.core.aclf.AclfBranch;

/**
 * A contiguous monitor slice of a monitor-by-outage LODF panel.
 */
public final class DclfTransferPanelChunk {
    private final int monitorOffset;
    private final AclfBranch[] monitoredBranches;
    private final double[][] lodfPanel;

    DclfTransferPanelChunk(int monitorOffset, AclfBranch[] monitoredBranches, double[][] lodfPanel) {
        this.monitorOffset = monitorOffset;
        this.monitoredBranches = Arrays.copyOf(monitoredBranches, monitoredBranches.length);
        this.lodfPanel = copy(lodfPanel);
    }

    public int getMonitorOffset() {
        return monitorOffset;
    }

    public int getMonitorCount() {
        return monitoredBranches.length;
    }

    public AclfBranch[] getMonitoredBranches() {
        return Arrays.copyOf(monitoredBranches, monitoredBranches.length);
    }

    public AclfBranch getMonitoredBranch(int localMonitorIndex) {
        return monitoredBranches[localMonitorIndex];
    }

    public double getLodf(int localMonitorIndex, int outageIndex) {
        return lodfPanel[localMonitorIndex][outageIndex];
    }

    public long estimatePanelBytes() {
        if (lodfPanel.length == 0) {
            return 0;
        }
        return (long) lodfPanel.length * (long) lodfPanel[0].length * Double.BYTES;
    }

    private static double[][] copy(double[][] data) {
        double[][] copied = new double[data.length][];
        for (int i = 0; i < data.length; i++) {
            copied[i] = Arrays.copyOf(data[i], data[i].length);
        }
        return copied;
    }
}
