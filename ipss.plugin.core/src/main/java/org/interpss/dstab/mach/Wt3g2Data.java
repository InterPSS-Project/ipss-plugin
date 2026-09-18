package org.interpss.dstab.mach;

/** Native WT3G2 parameters in published DYR order. */
public record Wt3g2Data(
        int turbineCount, double reactiveCommandTime, double activeCommandTime,
        double pllGain, double pllIntegratorGain, double pllMaximum,
        double turbineRatedMw, double lvplVoltage1, double lvplVoltage2,
        double lvplGain, double highVoltageThreshold,
        double highVoltageReactiveCurrentGain, double activeCurrentRecoveryRate,
        double lvplVoltageFilterTime) {

    private static final double EPS = 1.0e-10;

    public Wt3g2Data {
        if (turbineCount <= 0) throw new IllegalArgumentException("WT3G2 turbine count must be positive");
        requireNonnegative(reactiveCommandTime, "TIQcmd");
        requireNonnegative(activeCommandTime, "TIPcmd");
        requireNonnegative(pllGain, "KPLL");
        requireNonnegative(pllIntegratorGain, "KIPLL");
        requireNonnegative(pllMaximum, "PLLMAX");
        requirePositive(turbineRatedMw, "Prated");
        requireFinite(lvplVoltage1, "VLVPL1");
        requireFinite(lvplVoltage2, "VLVPL2");
        requireNonnegative(lvplGain, "GLVPL");
        requireFinite(highVoltageThreshold, "VHVRCR");
        requireNonnegative(highVoltageReactiveCurrentGain, "CURHVRCR");
        requireNonnegative(activeCurrentRecoveryRate, "RIp_LVPL");
        requireNonnegative(lvplVoltageFilterTime, "T_LVPL");
        if (lvplVoltage2 <= lvplVoltage1 + EPS) {
            throw new IllegalArgumentException("WT3G2 VLVPL2 must exceed VLVPL1");
        }
    }

    public double aggregateRatedMw() { return turbineCount * turbineRatedMw; }

    private static void requirePositive(double value, String name) {
        requireFinite(value, name);
        if (value <= EPS) throw new IllegalArgumentException("WT3G2 " + name + " must be positive");
    }

    private static void requireNonnegative(double value, String name) {
        requireFinite(value, name);
        if (value < 0.0) throw new IllegalArgumentException("WT3G2 " + name + " must be nonnegative");
    }

    private static void requireFinite(double value, String name) {
        if (!Double.isFinite(value)) throw new IllegalArgumentException("WT3G2 " + name + " must be finite");
    }
}
