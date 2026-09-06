package org.interpss.dstab.control.pss.ieee.y1992.pss2a;

import org.interpss.numeric.datatype.LimitType;

import com.interpss.dstab.controller.cml.field.adapt.CMLControlBlockAdapter;

/** Implements PowerWorld's optional PSS2A block (A + s*Ta)/(1 + s*Tb). */
public final class Pss2aLeadLagBlock extends CMLControlBlockAdapter {
    private static final double EPS = 1.0e-12;

    private final double a;
    private final double ta;
    private final double tb;
    private final LimitType limit;
    private double predictorStartState;
    private double predictorDerivative;

    public Pss2aLeadLagBlock(double a, double ta, double tb, double max, double min) {
        this.a = a;
        this.ta = ta;
        this.tb = tb;
        this.limit = new LimitType(max, min);
    }

    @Override
    public boolean initStateY0(double y0) {
        if (Math.abs(a) <= EPS) {
            if (Math.abs(y0) > EPS) return false;
            u = 0.0;
            stateX = 0.0;
            return true;
        }
        u = y0 / a;
        stateX = u;
        return !limit.isViolated(y0);
    }

    @Override
    public boolean initStateU0(double u0) {
        u = u0;
        stateX = u0;
        return !limit.isViolated(a * u0);
    }

    @Override public double getU0() { return u; }

    @Override
    public double getU0(double y0) {
        return Math.abs(a) <= EPS ? 0.0 : y0 / a;
    }

    @Override
    public void eulerStep1(double input, double dt) {
        u = input;
        predictorStartState = stateX;
        predictorDerivative = derivative(input, stateX);
        stateX += predictorDerivative * dt;
    }

    @Override
    public void eulerStep2(double input, double dt) {
        u = input;
        stateX = predictorStartState
                + 0.5 * (predictorDerivative + derivative(input, stateX)) * dt;
    }

    private double derivative(double input, double state) {
        return tb > EPS ? (input - state) / tb : 0.0;
    }

    @Override
    public double getY() {
        double output;
        if (tb > EPS) {
            double directGain = ta / tb;
            output = directGain * u + (a - directGain) * stateX;
        } else {
            // The standard PSS/E form is A=1, Ta=Tb=0. The builder rejects
            // the undefined improper case Tb=0 with nonzero Ta.
            output = a * u;
        }
        return limit.limit(output);
    }

    @Override public void setDX_dt(double value) { predictorDerivative = value; }
    @Override public void setParameter(String name, double value) { }
}
