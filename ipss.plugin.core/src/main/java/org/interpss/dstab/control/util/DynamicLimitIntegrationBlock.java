package org.interpss.dstab.control.util;

import com.interpss.dstab.controller.cml.field.block.IntegrationControlBlock;

/**
 * Integration block with runtime-adjustable non-windup limits.
 *
 * <p>The bounds may depend on controller algebraic signals and can therefore
 * be refreshed before each integration stage.</p>
 */
public class DynamicLimitIntegrationBlock extends IntegrationControlBlock {
    private double upper;
    private double lower;

    public DynamicLimitIntegrationBlock(double gain, double upper, double lower) {
        super(gain);
        setLimits(upper, lower);
    }

    public final void setLimits(double upper, double lower) {
        if (!Double.isFinite(upper) || !Double.isFinite(lower) || upper < lower) {
            throw new IllegalArgumentException("Dynamic integration limits require finite upper >= lower");
        }
        this.upper = upper;
        this.lower = lower;
        setStateX(limit(getStateX()));
    }

    public double getUpperLimit() {
        return upper;
    }

    public double getLowerLimit() {
        return lower;
    }

    @Override
    public boolean initStateY0(double y0) {
        if (y0 > upper || y0 < lower) return false;
        setStateX(y0);
        return true;
    }

    @Override
    public void eulerStep1(double input, double dt) {
        this.u = input;
        double derivative = permittedDerivative(input);
        double predicted = getStateX() + derivative * dt;
        if ((predicted >= upper && derivative > 0.0)
                || (predicted <= lower && derivative < 0.0)) {
            setStateX(limit(predicted));
            this.dX_dt = 0.0;
        } else {
            setStateX(predicted);
            this.dX_dt = derivative;
        }
    }

    @Override
    public void eulerStep2(double input, double dt) {
        this.u = input;
        double correctedDerivative = permittedDerivative(input);
        setStateX(limit(getStateX() + 0.5 * (correctedDerivative - this.dX_dt) * dt));
    }

    @Override
    public double getY() {
        return limit(getStateX());
    }

    private double permittedDerivative(double input) {
        double derivative = getK() * input;
        if ((getStateX() >= upper && derivative > 0.0)
                || (getStateX() <= lower && derivative < 0.0)) {
            return 0.0;
        }
        return derivative;
    }

    private double limit(double value) {
        return Math.max(lower, Math.min(upper, value));
    }
}
