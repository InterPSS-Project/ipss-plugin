package org.interpss.dstab.control.pss.psse.ieeest;

import com.interpss.dstab.controller.cml.field.adapt.CMLControlBlockAdapter;

/** (1 + c*s + d*s^2)/(1 + a*s + b*s^2). */
public final class SecondOrderLeadLagBlock extends CMLControlBlockAdapter {
    private static final double EPS = 1.0e-12;

    private final double a;
    private final double b;
    private final double c;
    private final double d;
    private double x2;
    private double oldX1Derivative;
    private double oldX2Derivative;

    public SecondOrderLeadLagBlock(double a, double b, double c, double d) {
        this.a = a;
        this.b = b;
        this.c = c;
        this.d = d;
    }

    @Override
    public boolean initStateY0(double y0) {
        u = y0;
        stateX = b > EPS ? 0.0 : y0;
        x2 = y0;
        return true;
    }

    @Override
    public boolean initStateU0(double u0) {
        u = u0;
        stateX = b > EPS ? 0.0 : u0;
        x2 = u0;
        return true;
    }

    @Override public double getU0() { return u; }
    @Override public double getU0(double y0) { return y0; }

    @Override
    public void eulerStep1(double input, double dt) {
        u = input;
        if (b <= EPS) {
            oldX1Derivative = firstOrderDerivative(input);
            stateX += oldX1Derivative * dt;
            return;
        }
        oldX1Derivative = x1Derivative(input);
        oldX2Derivative = x2Derivative();
        stateX += oldX1Derivative * dt;
        x2 += oldX2Derivative * dt;
    }

    @Override
    public void eulerStep2(double input, double dt) {
        u = input;
        if (b <= EPS) {
            stateX += 0.5 * (firstOrderDerivative(input) - oldX1Derivative) * dt;
            return;
        }
        stateX += 0.5 * (x1Derivative(input) - oldX1Derivative) * dt;
        x2 += 0.5 * (x2Derivative() - oldX2Derivative) * dt;
    }

    private double x1Derivative(double input) {
        return b > EPS ? (input - x2 - a * stateX) / b : 0.0;
    }

    private double firstOrderDerivative(double input) {
        return a > EPS ? (input - stateX) / a : 0.0;
    }

    private double x2Derivative() { return b > EPS ? stateX : 0.0; }

    @Override
    public double getY() {
        if (b <= EPS) {
            return a > EPS ? stateX + c * firstOrderDerivative(u) : u;
        }
        return x2 + c * stateX + d * x1Derivative(u);
    }

    @Override public void setDX_dt(double value) { oldX1Derivative = value; }
    @Override public void setParameter(String name, double value) { }
}
