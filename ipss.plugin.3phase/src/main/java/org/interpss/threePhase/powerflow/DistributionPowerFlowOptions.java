package org.interpss.threePhase.powerflow;

/** Per-algorithm fixed-point options, also usable as deterministic test fixtures. */
public record DistributionPowerFlowOptions(
        boolean symbolReuseEnabled,
        boolean valueUpdateEnabled,
        boolean zeroDiagonalFillEnabled,
        boolean transformerAntiFloatEnabled,
        boolean floatingComponentAntiFloatEnabled,
        boolean nonSwingBusAntiFloatEnabled,
        boolean floatingComponentDeactivationEnabled) {
    public static final DistributionPowerFlowOptions DEFAULTS =
            new DistributionPowerFlowOptions(true, true, true, true, true, false, true);

    public static DistributionPowerFlowOptions withMatrixReuse(boolean symbols, boolean values) {
        return new DistributionPowerFlowOptions(symbols, values, true, true, true, false, true);
    }
}
