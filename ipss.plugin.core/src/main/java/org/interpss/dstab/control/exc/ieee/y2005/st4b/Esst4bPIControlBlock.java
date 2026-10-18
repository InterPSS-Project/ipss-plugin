package org.interpss.dstab.control.exc.ieee.y2005.st4b;

import com.interpss.dstab.controller.cml.field.ICMLStaticBlock.StaticBlockType;
import com.interpss.dstab.controller.cml.field.block.PIControlBlock;

/**
 * ESST4B PI with separately clipped output and integral state in the native
 * model. The legacy typed convention freezes on output saturation instead.
 */
public final class Esst4bPIControlBlock extends PIControlBlock {
    private static final double EPS = 1.0e-12;
    private final boolean integralStateLimits;

    /** The numeric selector is internal to the CML constructor, not a DYR CON. */
    public Esst4bPIControlBlock(double kp, double ki, double max, double min,
            double integralStateLimits) {
        super(StaticBlockType.NonWindup, kp, ki, max, min);
        if (integralStateLimits != 0.0 && integralStateLimits != 1.0) {
            throw new IllegalArgumentException("ESST4B PI convention must be 0 or 1");
        }
        this.integralStateLimits = integralStateLimits == 1.0;
    }

    @Override
    public boolean initStateY0(double y0) {
        if (getLimit().isViolated(y0)) return false;
        if (Math.abs(getKi()) <= EPS) {
            if (Math.abs(getKp()) <= EPS && Math.abs(y0) > EPS) return false;
            setStateX(0.0);
            setU(Math.abs(getKp()) <= EPS ? 0.0 : y0 / getKp());
        } else {
            setStateX(y0);
            setU(0.0);
        }
        setDX_dt(0.0);
        return true;
    }

    @Override
    public double getU0() { return getU(); }

    @Override
    public void eulerStep1(double u, double dt) {
        setU(u);
        this.dX_dt = limitedDerivative(u);
        double trial = getStateX() + this.dX_dt * dt;
        setStateX(projectIntegralState(trial));
        if (getStateX() != trial) setDX_dt(0.0);
    }

    @Override
    public void eulerStep2(double u, double dt) {
        setU(u);
        setStateX(projectIntegralState(getStateX()
                + 0.5 * (limitedDerivative(u) - this.dX_dt) * dt));
    }

    @Override
    public double getY() {
        return getLimit().limit(getStateX() + getKp() * getU());
    }

    /** Project the integration coordinate, including in the staggered solver. */
    public double projectIntegralState(double state) {
        // A pure proportional path has no integral state to project, including
        // when its output limits do not contain zero.
        return integralStateLimits && Math.abs(getKi()) > EPS
                ? getLimit().limit(state) : state;
    }

    /** Whether an outward derivative must clear multistep integration history. */
    public boolean isIntegratorFrozen() {
        return isIntegratorFrozen(getU());
    }

    private boolean isIntegratorFrozen(double input) {
        double derivative = getKi() * input;
        double limitedCoordinate = integralStateLimits
                ? getStateX() : getStateX() + getKp() * input;
        return derivative > 0.0 && limitedCoordinate >= getLimit().getMax()
                || derivative < 0.0 && limitedCoordinate <= getLimit().getMin();
    }

    private double limitedDerivative(double input) {
        return isIntegratorFrozen(input) ? 0.0 : getKi() * input;
    }
}
