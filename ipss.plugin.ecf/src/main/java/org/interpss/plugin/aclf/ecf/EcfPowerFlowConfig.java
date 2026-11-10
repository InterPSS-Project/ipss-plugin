package org.interpss.plugin.aclf.ecf;

/** Immutable controls for the opt-in constant-P/Q current-balance solver.
 * Generator reactive bounds are not enforced in this initial PF formulation. */
public record EcfPowerFlowConfig(double tolerance, int maximumIterations, double voltageStepLimit,
        int maximumBacktracks, Initialization initialization, Backend backend, boolean freezeAutomaticControls) {
    public enum Initialization { FLAT, SUPPLIED }
    public enum Backend { KLU, CSJ }
    public EcfPowerFlowConfig {
        if (!(tolerance > 0) || !Double.isFinite(tolerance) || maximumIterations < 0
                || !(voltageStepLimit > 0) || !Double.isFinite(voltageStepLimit)
                || maximumBacktracks < 1 || initialization == null || backend == null)
            throw new IllegalArgumentException("Invalid ECF power flow configuration");
    }
    public static EcfPowerFlowConfig defaults() {
        return new EcfPowerFlowConfig(1e-9, 50, .2, 20, Initialization.FLAT, Backend.KLU, false);
    }
}
