package org.interpss.dstab.mach;

/** Seven ICONs and 28 CONs for the PLNTBU1 plant controller. */
public record Plntbu1Data(
        int voltageControlBus, int branchFromBus, int branchToBus, String branchId,
        int vcFlag, int refFlag, int frequencyFlag,
        double voltageFilterTime, double reactiveProportionalGain,
        double reactiveIntegralGain, double reactiveLeadTime,
        double reactiveLagTime, double freezeVoltage, double resistanceCompensation,
        double reactanceCompensation, double reactiveDroop,
        double reactiveErrorMaximum, double reactiveErrorMinimum,
        double reactiveDeadbandLower, double reactiveDeadbandUpper,
        double reactiveOutputMaximum, double reactiveOutputMinimum,
        double activeProportionalGain, double activeIntegralGain,
        double activePowerFilterTime, double frequencyDeadbandLower,
        double frequencyDeadbandUpper, double activeErrorMaximum,
        double activeErrorMinimum, double activeOutputMaximum,
        double activeOutputMinimum, double activeOutputLagTime,
        double overFrequencyDroop, double underFrequencyDroop,
        double plantBaseMva) {

    public Plntbu1Data {
        branchId = branchId == null ? "" : branchId.trim();
        flag(vcFlag, 0, 1, "VCFlag");
        flag(refFlag, 0, 2, "RefFlag");
        // The native selector activates the whole P-control path only at 1.
        // Other integers retain their entered value and select the inactive
        // branch; they are not coerced to an inferred enabled setting.
        if (!finite(voltageFilterTime, reactiveProportionalGain,
                reactiveIntegralGain, reactiveLeadTime, reactiveLagTime,
                freezeVoltage, resistanceCompensation, reactanceCompensation,
                reactiveDroop, reactiveErrorMaximum, reactiveErrorMinimum,
                reactiveDeadbandLower, reactiveDeadbandUpper,
                reactiveOutputMaximum, reactiveOutputMinimum,
                activeProportionalGain, activeIntegralGain, activePowerFilterTime,
                frequencyDeadbandLower, frequencyDeadbandUpper,
                activeErrorMaximum, activeErrorMinimum, activeOutputMaximum,
                activeOutputMinimum, activeOutputLagTime, overFrequencyDroop,
                underFrequencyDroop, plantBaseMva)) {
            throw new IllegalArgumentException("PLNTBU1 constants must be finite");
        }
        if (voltageFilterTime < 0.0 || reactiveLagTime < 0.0
                || activePowerFilterTime < 0.0 || activeOutputLagTime < 0.0
                || plantBaseMva < 0.0) {
            throw new IllegalArgumentException("PLNTBU1 time constants and MVA base must be non-negative");
        }
        if (reactiveErrorMaximum < reactiveErrorMinimum
                || reactiveOutputMaximum < reactiveOutputMinimum
                || frequencyFlag == 1 && (activeErrorMaximum < activeErrorMinimum
                || activeOutputMaximum < activeOutputMinimum)) {
            throw new IllegalArgumentException("PLNTBU1 upper limits must be >= lower limits");
        }
        if (reactiveDeadbandLower > 0.0 || reactiveDeadbandUpper < 0.0
                || frequencyFlag == 1 && (frequencyDeadbandLower > 0.0 || frequencyDeadbandUpper < 0.0)) {
            throw new IllegalArgumentException("PLNTBU1 deadbands must enclose zero");
        }
    }

    public boolean activePowerControlEnabled() { return frequencyFlag == 1; }

    /** Power-factor regulation still needs measured P when the P-control path is off. */
    public boolean activePowerMeasurementEnabled() { return activePowerControlEnabled() || refFlag == 2; }

    /** Initialization retains measured P for a nonzero selector even if its path will not advance. */
    public boolean activePowerMeasurementInitialized() { return frequencyFlag != 0 || refFlag == 2; }

    private static void flag(int value, int low, int high, String name) {
        if (value < low || value > high) {
            throw new IllegalArgumentException("PLNTBU1 " + name + " is invalid: " + value);
        }
    }

    private static boolean finite(double... values) {
        for (double value : values) if (!Double.isFinite(value)) return false;
        return true;
    }
}
