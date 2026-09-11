package org.interpss.dstab.control.pss.psse.st2cut;

import com.interpss.dstab.controller.cml.field.block.WashoutControlBlock;

/** Implements ST2CUT's documented T3=0 substitution for the washout numerator. */
public final class St2cutWashoutBlock extends WashoutControlBlock {
    private static final double EPS = 1.0e-12;
    private final double numeratorTimeConstant;

    public St2cutWashoutBlock(double t3, double t4) {
        super(t4 > EPS ? t3 / t4 : 0.0, t4);
        numeratorTimeConstant = t3;
    }

    private boolean isSubstitutedLag() {
        return Math.abs(numeratorTimeConstant) <= EPS;
    }

    @Override
    public boolean initStateU0(double u0) {
        if (!isSubstitutedLag()) return super.initStateU0(u0);
        setU(u0);
        setStateX(u0);
        return true;
    }

    @Override
    public boolean initStateY0(double y0) {
        if (!isSubstitutedLag()) return super.initStateY0(y0);
        setU(y0);
        setStateX(y0);
        return true;
    }

    @Override
    public double getU0() {
        return isSubstitutedLag() ? getStateX() : super.getU0();
    }

    @Override
    public double getY() {
        return isSubstitutedLag() ? getStateX() : super.getY();
    }

    @Override
    protected double dX_dt(double u) {
        return isSubstitutedLag() ? (u - getStateX()) / getT() : super.dX_dt(u);
    }
}
