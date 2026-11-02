package org.interpss.dstab.hvdc;

/** Immutable observations on both converged sides of one accepted internal network action.
 * The physical time is shared by both sides; continuous states are not advanced between them. */
public record Cdc6tNetworkTransition(double time,
        Cdc6tNormalRuntime.OperatingMode beforeMode,Cdc6tNormalRuntime.OperatingMode afterMode,
        Boundary before,Boundary after) {
    /** Terminal phasors in pu, measurement states in V/A, powers in pu,
     * converter angles in radians and DC boundary voltages in V. Differential
     * states retain storage; algebraic measurement coordinates evaluate each side. */
    public record Boundary(double rectifierReal,double rectifierImaginary,
            double inverterReal,double inverterImaginary,double measuredInverterVolts,
            double measuredAmperes,double measuredRectifierVolts,double rectifierP,double rectifierQ,
            double alphaRadians,double inverterP,double inverterQ,double gammaRadians,
            double rectifierVolts,double inverterVolts) {}
}
