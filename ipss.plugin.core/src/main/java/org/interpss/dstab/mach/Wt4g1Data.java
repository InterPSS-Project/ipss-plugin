package org.interpss.dstab.mach;

/** WT4G1 parameters in published dynamic-record order. */
public record Wt4g1Data(
        double reactiveCommandTime, double activeCommandTime,
        double lvplVoltage1, double lvplVoltage2, double lvplGain,
        double highVoltageThreshold, double highVoltageReactiveCurrentGain,
        double activeCurrentRecoveryRate, double lvplVoltageFilterTime) {

    private static final double EPS = 1.0e-10;

    public Wt4g1Data {
        requireNonnegative(reactiveCommandTime, "TIQcmd");
        requireNonnegative(activeCommandTime, "TIPcmd");
        requireFinite(lvplVoltage1, "VLVPL1");
        requireFinite(lvplVoltage2, "VLVPL2");
        requireNonnegative(lvplGain, "GLVPL");
        requireFinite(highVoltageThreshold, "VHVRC");
        requireNonnegative(highVoltageReactiveCurrentGain, "CURHVRC");
        requireNonnegative(activeCurrentRecoveryRate, "RIp_LVPL");
        requireNonnegative(lvplVoltageFilterTime, "T_LVPL");
        if (lvplVoltage2 <= lvplVoltage1 + EPS) {
            throw new IllegalArgumentException("WT4G1 VLVPL2 must exceed VLVPL1");
        }
    }

    private static void requireNonnegative(double value, String name) {
        requireFinite(value, name);
        if (value < 0.0) throw new IllegalArgumentException("WT4G1 " + name + " must be nonnegative");
    }

    private static void requireFinite(double value, String name) {
        if (!Double.isFinite(value)) throw new IllegalArgumentException("WT4G1 " + name + " must be finite");
    }
}
