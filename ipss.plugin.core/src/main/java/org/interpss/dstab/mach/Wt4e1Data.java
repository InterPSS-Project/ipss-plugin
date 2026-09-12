package org.interpss.dstab.mach;

/** WT4E1 flags and continuous parameters in published record order. */
public record Wt4e1Data(
        int remoteBus, int powerFactorFlag, int varFlag, int pqPriorityFlag,
        double voltageFilterTime, double voltageProportionalGain,
        double voltageIntegralGain, double powerProportionalGain,
        double powerIntegralGain, double rateFeedbackGain,
        double rateFeedbackTime, double reactiveMaximum, double reactiveMinimum,
        double activeCurrentMaximum, double voltageSensorTime,
        double powerRateMaximum, double powerRateMinimum, double powerFilterTime,
        double reactiveVoltageGain, double voltageMinimum, double voltageMaximum,
        double voltageErrorGain, double windVarTime, double powerFactorFilterTime,
        double converterCurrentMaximum, double hardActiveCurrentMaximum,
        double hardReactiveCurrentMaximum) {

    public Wt4e1Data {
        if ((powerFactorFlag != 0 && powerFactorFlag != 1)
                || (varFlag != 0 && varFlag != 1)
                || (pqPriorityFlag != 0 && pqPriorityFlag != 1)) fail("flags must be 0 or 1");
        double[] values = {voltageFilterTime, voltageProportionalGain,
                voltageIntegralGain, powerProportionalGain, powerIntegralGain,
                rateFeedbackGain, rateFeedbackTime, reactiveMaximum, reactiveMinimum,
                activeCurrentMaximum, voltageSensorTime, powerRateMaximum,
                powerRateMinimum, powerFilterTime, reactiveVoltageGain, voltageMinimum,
                voltageMaximum, voltageErrorGain, windVarTime, powerFactorFilterTime,
                converterCurrentMaximum, hardActiveCurrentMaximum,
                hardReactiveCurrentMaximum};
        for (double value : values) if (!Double.isFinite(value)) fail("parameters must be finite");
        if (voltageFilterTime < 0 || rateFeedbackTime < 0 || voltageSensorTime < 0
                || powerFilterTime < 0 || windVarTime < 0 || powerFactorFilterTime < 0) {
            fail("time constants must be nonnegative");
        }
        if (reactiveMinimum > reactiveMaximum || powerRateMinimum > powerRateMaximum
                || voltageMinimum > voltageMaximum) fail("minimum exceeds maximum");
        if (activeCurrentMaximum <= 0 || converterCurrentMaximum <= 0
                || hardActiveCurrentMaximum <= 0 || hardReactiveCurrentMaximum <= 0) {
            fail("current limits must be positive");
        }
    }

    private static void fail(String message) { throw new IllegalArgumentException("WT4E1 " + message); }
}
