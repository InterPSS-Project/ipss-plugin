package org.interpss.dstab.renewable;

import java.util.Arrays;

/**
 * Public WECC REEC_D selectors and constants.
 *
 * <p>The native wrapper carries six selectors followed by 77 constants. Forty
 * of those constants define the ten-point reactive and active
 * voltage-dependent current-limit tables.</p>
 */
public record Reecd1Data(
        int powerFactorFlag, int voltageFlag, int reactiveControlFlag,
        int powerFlag, int pqPriorityFlag, int voltageCompensationFlag,
        double voltageDip, double voltageUp, double voltageMeasurementTime,
        double deadbandLow, double deadbandHigh,
        double reactiveInjectionGain, double reactiveInjectionMaximum,
        double reactiveInjectionMinimum, double voltageReference,
        double frozenReactiveCurrent, double reactiveHoldTime,
        double activeLimitHoldTime, double powerMeasurementTime,
        double externalReactiveMaximum, double externalReactiveMinimum,
        double voltageControlMaximum, double voltageControlMinimum,
        double reactiveProportionalGain, double reactiveIntegralGain,
        double voltageProportionalGain, double voltageIntegralGain,
        double innerVoltageReference, double reactiveLagTime,
        double powerRampMaximum, double powerRampMinimum,
        double powerMaximum, double powerMinimum, double currentMaximum,
        double powerOrderTime, double compensationResistance,
        double compensationReactance, double compensationFilterTime,
        double reactiveDroopGain, double chargingCurrentFactor,
        double blockingVoltageLow, double blockingVoltageHigh,
        double unblockDelay,
        double[] reactiveVoltagePoints, double[] reactiveCurrentPoints,
        double[] activeVoltagePoints, double[] activeCurrentPoints) {

    private static final int TABLE_POINTS = 10;

    public Reecd1Data {
        requireFlag(powerFactorFlag, "PFFLAG");
        requireFlag(voltageFlag, "VFLAG");
        requireFlag(reactiveControlFlag, "QFLAG");
        requireFlag(powerFlag, "PFLAG");
        requireFlag(pqPriorityFlag, "PQFLAG");
        requireFlag(voltageCompensationFlag, "VCOMPFLAG");
        reactiveVoltagePoints = copyTable(reactiveVoltagePoints, "VDLQ voltage");
        reactiveCurrentPoints = copyTable(reactiveCurrentPoints, "VDLQ current");
        activeVoltagePoints = copyTable(activeVoltagePoints, "VDLP voltage");
        activeCurrentPoints = copyTable(activeCurrentPoints, "VDLP current");

        double[] values = {voltageDip, voltageUp, voltageMeasurementTime,
                deadbandLow, deadbandHigh, reactiveInjectionGain,
                reactiveInjectionMaximum, reactiveInjectionMinimum,
                voltageReference, frozenReactiveCurrent, reactiveHoldTime,
                activeLimitHoldTime, powerMeasurementTime,
                externalReactiveMaximum, externalReactiveMinimum,
                voltageControlMaximum, voltageControlMinimum,
                reactiveProportionalGain, reactiveIntegralGain,
                voltageProportionalGain, voltageIntegralGain,
                innerVoltageReference, reactiveLagTime, powerRampMaximum,
                powerRampMinimum, powerMaximum, powerMinimum, currentMaximum,
                powerOrderTime, compensationResistance, compensationReactance,
                compensationFilterTime, reactiveDroopGain,
                chargingCurrentFactor, blockingVoltageHigh,
                blockingVoltageLow, unblockDelay};
        for (double value : values) requireFinite(value);
        for (double value : reactiveVoltagePoints) requireFinite(value);
        for (double value : reactiveCurrentPoints) requireFinite(value);
        for (double value : activeVoltagePoints) requireFinite(value);
        for (double value : activeCurrentPoints) requireFinite(value);

        if (voltageUp < voltageDip
                || reactiveInjectionMaximum < reactiveInjectionMinimum
                || externalReactiveMaximum < externalReactiveMinimum
                || voltageControlMaximum < voltageControlMinimum
                || powerRampMaximum < powerRampMinimum
                || powerMaximum < powerMinimum
                || blockingVoltageHigh < blockingVoltageLow) {
            throw new IllegalArgumentException("REEC_D upper limit is below lower limit");
        }
        if (chargingCurrentFactor < 0.0 || chargingCurrentFactor > 1.0) {
            throw new IllegalArgumentException("REEC_D Ke must be between zero and one");
        }
        if (voltageMeasurementTime < 0.0 || activeLimitHoldTime < 0.0
                || powerMeasurementTime < 0.0 || reactiveLagTime < 0.0
                || powerOrderTime < 0.0 || compensationFilterTime < 0.0
                || unblockDelay < 0.0) {
            throw new IllegalArgumentException("REEC_D time constants must be non-negative");
        }
        validateTable(reactiveVoltagePoints, reactiveCurrentPoints, "VDLQ", false);
        validateTable(activeVoltagePoints, activeCurrentPoints, "VDLP", true);
    }

    @Override public double[] reactiveVoltagePoints() {
        return reactiveVoltagePoints.clone();
    }
    @Override public double[] reactiveCurrentPoints() {
        return reactiveCurrentPoints.clone();
    }
    @Override public double[] activeVoltagePoints() {
        return activeVoltagePoints.clone();
    }
    @Override public double[] activeCurrentPoints() {
        return activeCurrentPoints.clone();
    }

    private static double[] copyTable(double[] values, String name) {
        if (values == null || values.length != TABLE_POINTS) {
            throw new IllegalArgumentException("REEC_D " + name
                    + " table must contain exactly " + TABLE_POINTS + " points");
        }
        return Arrays.copyOf(values, values.length);
    }

    private static void validateTable(double[] voltage, double[] current,
            String name, boolean nonNegativeCurrent) {
        int count = tablePointCount(voltage, current);
        if (count == 0) return;
        if (count < 2) {
            throw new IllegalArgumentException("REEC_D " + name
                    + " must contain at least two active points");
        }
        for (int index = 0; index < count; index++) {
            if (nonNegativeCurrent && current[index] < 0.0) {
                throw new IllegalArgumentException("REEC_D VDLP current must be non-negative");
            }
        }
    }

    static int tablePointCount(double[] voltage, double[] current) {
        boolean enabled = false;
        for (int index = 0; index < voltage.length; index++) {
            enabled |= voltage[index] != 0.0 || current[index] != 0.0;
        }
        if (!enabled) return 0;
        for (int index = 1; index < voltage.length; index++) {
            if (voltage[index] == 0.0 || voltage[index] <= voltage[index - 1]) {
                return index;
            }
        }
        return voltage.length;
    }

    private static void requireFlag(int value, String name) {
        if (value != 0 && value != 1) {
            throw new IllegalArgumentException("REEC_D " + name + " must be 0 or 1");
        }
    }

    private static void requireFinite(double value) {
        if (!Double.isFinite(value)) {
            throw new IllegalArgumentException("REEC_D constants must be finite");
        }
    }
}
