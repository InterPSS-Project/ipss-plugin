package org.interpss.dstab.control.util;

import com.interpss.dstab.controller.cml.field.ICMLStaticBlock.StaticBlockType;
import com.interpss.dstab.controller.cml.field.block.PIControlBlock;

/**
 * PI block whose integrator freezes at an output limit and resumes for an
 * inward-driving input. A zero integral gain is initialized as a pure
 * proportional path instead of storing the requested output as a false state.
 */
public final class FreezeNonWindupPIControlBlock extends PIControlBlock {
    private static final double EPS = 1.0e-12;

    public FreezeNonWindupPIControlBlock(double kp, double ki, double max, double min) {
        super(StaticBlockType.NonWindup, kp, ki, max, min);
    }

    @Override
    public boolean initStateY0(double y0) {
        if (getLimit().isViolated(y0)) return false;
        if (Math.abs(getKi()) <= EPS) {
            if (Math.abs(getKp()) <= EPS) return Math.abs(y0) <= EPS;
            setStateX(0.0);
            setU(y0 / getKp());
        } else {
            setStateX(y0);
            setU(0.0);
        }
        setDX_dt(0.0);
        return true;
    }

    @Override
    public double getU0() {
        return getU();
    }

    @Override
    public void eulerStep1(double u, double dt) {
        setU(u);
        this.dX_dt = limitedDerivative(u);
        this.stateX += this.dX_dt * dt;
    }

    @Override
    public void eulerStep2(double u, double dt) {
        setU(u);
        double correctedDerivative = limitedDerivative(u);
        this.stateX += 0.5 * (correctedDerivative - this.dX_dt) * dt;
    }

    @Override
    public double getY() {
        return getLimit().limit(getStateX() + getKp() * getU());
    }

    private double limitedDerivative(double input) {
        double derivative = getKi() * input;
        double rawOutput = getStateX() + getKp() * input;
        if (derivative > 0.0 && rawOutput >= getLimit().getMax()) return 0.0;
        if (derivative < 0.0 && rawOutput <= getLimit().getMin()) return 0.0;
        return derivative;
    }
}
