package org.interpss.dstab.mach;

/** Published GEWTGCU1 generator/converter data in native record order. */
public record Gewtgcu1Data(
        int turbineCount, int fullConverterFlag,
        double turbineRatedMw, double equivalentReactance,
        double lvplVoltage1, double lvplVoltage2, double lvplGain,
        double highVoltageReactiveVoltage2, double highVoltageReactiveCurrent2,
        double lowVoltageActiveVoltage1, double lowVoltageActiveVoltage2,
        double activeCurrentRecoveryRate, double voltageSensorTime,
        double curveVoltage1, double curvePower1,
        double curveVoltage2, double curvePower2,
        double curveVoltage3, double curvePower3,
        double compensationReactance) {

    public Gewtgcu1Data {
        if (turbineCount <= 0) throw new IllegalArgumentException("GEWTGCU1 turbine count must be positive");
        if (fullConverterFlag != 0 && fullConverterFlag != 1) {
            throw new IllegalArgumentException("GEWTGCU1 full-converter flag must be zero or one");
        }
        double[] values = {turbineRatedMw, equivalentReactance, lvplVoltage1,
                lvplVoltage2, lvplGain, highVoltageReactiveVoltage2,
                highVoltageReactiveCurrent2, lowVoltageActiveVoltage1,
                lowVoltageActiveVoltage2, activeCurrentRecoveryRate,
                voltageSensorTime, curveVoltage1, curvePower1, curveVoltage2,
                curvePower2, curveVoltage3, curvePower3, compensationReactance};
        for (double value : values) {
            if (!Double.isFinite(value)) throw new IllegalArgumentException("GEWTGCU1 values must be finite");
        }
        if (turbineRatedMw <= 0.0 || equivalentReactance <= 0.0) {
            throw new IllegalArgumentException("GEWTGCU1 rating and Xeq must be positive");
        }
        if (lvplVoltage2 <= lvplVoltage1
                || lowVoltageActiveVoltage2 <= lowVoltageActiveVoltage1
                || curveVoltage2 <= curveVoltage1 || curveVoltage3 <= curveVoltage2) {
            throw new IllegalArgumentException("GEWTGCU1 voltage breakpoints must increase");
        }
        if (activeCurrentRecoveryRate < 0.0 || voltageSensorTime < 0.0
                || highVoltageReactiveCurrent2 < 0.0) {
            throw new IllegalArgumentException("GEWTGCU1 rates and time constants must be nonnegative");
        }
    }

    public boolean fullConverter() { return fullConverterFlag == 1; }
    public double aggregateRatedMw() { return turbineCount * turbineRatedMw; }
}
