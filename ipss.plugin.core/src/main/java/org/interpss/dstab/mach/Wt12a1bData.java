package org.interpss.dstab.mach;

/** One ICON and 14 CONs for the WT12A1U_B/WT12A1B controller. */
public record Wt12a1bData(int configurationFlag, double voltageTime,
        double rampUpRate, double rampDownRate, double outputTime,
        double minimumPower, double powerThreshold,
        double voltage1, double duration1, double voltage2, double duration2,
        double voltage3, double duration3, double voltage4, double duration4) {
    public Wt12a1bData {
        double[] values = {voltageTime, rampUpRate, rampDownRate, outputTime,
                minimumPower, powerThreshold, voltage1, duration1, voltage2,
                duration2, voltage3, duration3, voltage4, duration4};
        for (double value : values) if (!Double.isFinite(value)) {
            throw new IllegalArgumentException("WT12A1B constants must be finite");
        }
        if (voltageTime < 0.0 || outputTime < 0.0 || rampUpRate < 0.0
                || rampDownRate > 0.0 || minimumPower < 0.0
                || duration1 < 0.0 || duration2 < 0.0
                || duration3 < 0.0 || duration4 < 0.0) {
            throw new IllegalArgumentException("WT12A1B time, rate, or power boundary is invalid");
        }
        if (!(voltage1 <= voltage2 && voltage2 <= voltage3 && voltage3 <= voltage4)) {
            throw new IllegalArgumentException("WT12A1B voltage points must be ordered");
        }
    }
}
