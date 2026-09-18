package org.interpss.dstab.mach;

/** GEWTGCU1 electrical-control data in the published GEWTECU1 record order. */
public record Gewtecu1Data(
        int remoteBus, int powerFactorFlag, int varFlag, int activePowerControlFlag,
        int pqPriorityFlag, int qDroopFromBus, int qDroopToBus,
        String qDroopCircuitId, int windFreeFlag,
        double voltageFilterTime, double voltageProportionalGain,
        double voltageIntegralGain, double lineDropResistance,
        double lineDropReactance, double torqueFilterTime,
        double torqueProportionalGain, double torqueIntegralGain,
        double powerMaximum, double powerMinimum, double reactiveMaximum,
        double reactiveMinimum, double activeCurrentMaximum,
        double voltageSensorTime, double powerRateMaximum,
        double powerRateMinimum, double powerReferenceFilterTime,
        double reactiveVoltageGain, double voltageMinimum,
        double voltageMaximum, double internalVoltageGain,
        double internalVoltageMinimum, double internalVoltageMaximum,
        double windVarTime, double fastPowerFactorFilterTime,
        double onlineFraction, double availablePowerFilterTime,
        double frequencyA, double frequencyB, double frequencyC,
        double frequencyD, double powerAtFrequencyA, double powerAtFrequencyB,
        double powerAtFrequencyC, double powerAtFrequencyD,
        double frequencyPowerMaximum, double frequencyPowerMinimum,
        double powerCommandRateTime, double lvplSensorTime,
        double lvplBreakpoint, double initialWindSpeed, double maximumWindSpeed,
        double minimumWindSpeed, double lowRotorSpeedTrip,
        double highWindTripThreshold, double brakingEnergyThreshold,
        double brakingControllerGain, double brakingPowerMaximum,
        double converterCurrentLimit, double hardActiveCurrentLimit,
        double hardReactiveCurrentLimit, double reactiveDroopFilterTime,
        double reactiveDroopGain, double reactiveDroopReactance,
        double windInertiaGain, double windInertiaDeadband,
        double windInertiaFilterTime, double windInertiaWashoutTime,
        double windInertiaUpRate, double windInertiaDownRate,
        double windInertiaPowerMaximum, double windInertiaPowerMinimum,
        double reactiveErrorMaximum, double reactiveErrorMinimum,
        double reactiveFreezeVoltage, double zeroPowerReactiveMaximum,
        double zeroPowerReactiveMinimum) {

    public Gewtecu1Data {
        qDroopCircuitId = qDroopCircuitId == null ? "" : qDroopCircuitId;
        int[] flags = {powerFactorFlag, varFlag, activePowerControlFlag,
                pqPriorityFlag, windFreeFlag};
        for (int flag : flags) {
            if (flag != 0 && flag != 1) fail("flags must be zero or one");
        }
        double[] values = {voltageFilterTime, voltageProportionalGain,
                voltageIntegralGain, lineDropResistance, lineDropReactance,
                torqueFilterTime, torqueProportionalGain, torqueIntegralGain,
                powerMaximum, powerMinimum, reactiveMaximum, reactiveMinimum,
                activeCurrentMaximum, voltageSensorTime, powerRateMaximum,
                powerRateMinimum, powerReferenceFilterTime, reactiveVoltageGain,
                voltageMinimum, voltageMaximum, internalVoltageGain,
                internalVoltageMinimum, internalVoltageMaximum, windVarTime,
                fastPowerFactorFilterTime, onlineFraction,
                availablePowerFilterTime, frequencyA, frequencyB, frequencyC,
                frequencyD, powerAtFrequencyA, powerAtFrequencyB,
                powerAtFrequencyC, powerAtFrequencyD, frequencyPowerMaximum,
                frequencyPowerMinimum, powerCommandRateTime, lvplSensorTime,
                lvplBreakpoint, initialWindSpeed, maximumWindSpeed,
                minimumWindSpeed, lowRotorSpeedTrip, highWindTripThreshold,
                brakingEnergyThreshold, brakingControllerGain,
                brakingPowerMaximum, converterCurrentLimit,
                hardActiveCurrentLimit, hardReactiveCurrentLimit,
                reactiveDroopFilterTime, reactiveDroopGain,
                reactiveDroopReactance, windInertiaGain, windInertiaDeadband,
                windInertiaFilterTime, windInertiaWashoutTime,
                windInertiaUpRate, windInertiaDownRate,
                windInertiaPowerMaximum, windInertiaPowerMinimum,
                reactiveErrorMaximum, reactiveErrorMinimum,
                reactiveFreezeVoltage, zeroPowerReactiveMaximum,
                zeroPowerReactiveMinimum};
        for (double value : values) {
            if (!Double.isFinite(value)) fail("parameters must be finite");
        }
        if (powerMinimum > powerMaximum || reactiveMinimum > reactiveMaximum
                || powerRateMinimum > powerRateMaximum
                || voltageMinimum > voltageMaximum
                || internalVoltageMinimum > internalVoltageMaximum
                || frequencyPowerMinimum > frequencyPowerMaximum
                || minimumWindSpeed > maximumWindSpeed
                || windInertiaDownRate > windInertiaUpRate
                || windInertiaPowerMinimum > windInertiaPowerMaximum
                || reactiveErrorMinimum > reactiveErrorMaximum
                || zeroPowerReactiveMinimum > zeroPowerReactiveMaximum) {
            fail("minimum exceeds maximum");
        }
        if (activeCurrentMaximum <= 0.0 || converterCurrentLimit <= 0.0
                || hardActiveCurrentLimit <= 0.0
                || hardReactiveCurrentLimit <= 0.0) {
            fail("current limits must be positive");
        }
        double[] times = {voltageFilterTime, torqueFilterTime,
                voltageSensorTime, powerReferenceFilterTime, windVarTime,
                fastPowerFactorFilterTime, availablePowerFilterTime,
                powerCommandRateTime, lvplSensorTime,
                reactiveDroopFilterTime, windInertiaFilterTime,
                windInertiaWashoutTime};
        for (double time : times) if (time < 0.0) fail("time constants must be nonnegative");
        if (onlineFraction < 0.0 || onlineFraction > 1.0) {
            fail("online fraction must be within zero and one");
        }
    }

    public boolean qDroopEnabled() {
        return qDroopFromBus != 0 && qDroopToBus != 0 && !qDroopCircuitId.isBlank();
    }

    private static void fail(String message) {
        throw new IllegalArgumentException("GEWTECU1 " + message);
    }
}
