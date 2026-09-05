package org.interpss.dstab.control.pss.psse.ieeest;

import com.interpss.dstab.controller.cml.field.adapt.CMLControlBlockAdapter;

/** K/(1 + a*s + b*s^2), including its first-order and algebraic limits. */
public final class SecondOrderLagBlock extends CMLControlBlockAdapter {
    private static final double EPS = 1.0e-12;

    private final double k;
    private final double a;
    private final double b;
    private double rate;
    private double oldOutputDerivative;
    private double oldRateDerivative;

    public SecondOrderLagBlock(double k, double a, double b) {
        this.k = k;
        this.a = a;
        this.b = b;
    }

    @Override
    public boolean initStateY0(double y0) {
        if (Math.abs(k) <= EPS) return Math.abs(y0) <= EPS;
        u = y0 / k;
        stateX = y0;
        rate = 0.0;
        return true;
    }

    @Override
    public boolean initStateU0(double u0) {
        u = u0;
        stateX = k * u0;
        rate = 0.0;
        return true;
    }

    @Override public double getU0() { return u; }
    @Override public double getU0(double y0) { return Math.abs(k) <= EPS ? 0.0 : y0 / k; }

    @Override
    public void eulerStep1(double input, double dt) {
        u = input;
        oldOutputDerivative = outputDerivative(input);
        oldRateDerivative = rateDerivative(input);
        stateX += oldOutputDerivative * dt;
        rate += oldRateDerivative * dt;
    }

    @Override
    public void eulerStep2(double input, double dt) {
        u = input;
        stateX += 0.5 * (outputDerivative(input) - oldOutputDerivative) * dt;
        rate += 0.5 * (rateDerivative(input) - oldRateDerivative) * dt;
    }

    private double outputDerivative(double input) {
        if (b > EPS) return rate;
        if (a > EPS) return (k * input - stateX) / a;
        return 0.0;
    }

    private double rateDerivative(double input) {
        return b > EPS ? (k * input - stateX - a * rate) / b : 0.0;
    }

    @Override public double getY() { return a <= EPS && b <= EPS ? k * u : stateX; }
    @Override public void setDX_dt(double value) { oldOutputDerivative = value; }
    @Override public void setParameter(String name, double value) { }
}
