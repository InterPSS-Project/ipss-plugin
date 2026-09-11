package org.interpss.dstab.control.pss.psse.st2cut;

import com.interpss.dstab.controller.cml.field.block.DelayControlBlock;

/** First-order transducer with ST2CUT's zero-time-constant algebraic behavior. */
public final class St2cutTransducerBlock extends DelayControlBlock {
    private static final double EPS = 1.0e-12;

    public St2cutTransducerBlock(double k, double t) {
        super(k, t);
    }

    private boolean isAlgebraic() {
        return getT() <= EPS;
    }

    @Override
    public boolean initStateU0(double u0) {
        if (!isAlgebraic()) return super.initStateU0(u0);
        setU(u0);
        setStateX(getK() * u0);
        return true;
    }

    @Override
    public boolean initStateY0(double y0) {
        if (!isAlgebraic()) return super.initStateY0(y0);
        if (Math.abs(getK()) <= EPS) {
            if (Math.abs(y0) > EPS) return false;
            setU(0.0);
        }
        else {
            setU(y0 / getK());
        }
        setStateX(y0);
        return true;
    }

    @Override
    public double getY() {
        return isAlgebraic() ? getK() * getU() : super.getY();
    }

    @Override
    public void eulerStep1(double u, double dt) {
        if (isAlgebraic()) {
            setU(u);
            setStateX(getK() * u);
            return;
        }
        super.eulerStep1(u, dt);
    }

    @Override
    public void eulerStep2(double u, double dt) {
        if (isAlgebraic()) {
            setU(u);
            setStateX(getK() * u);
            return;
        }
        super.eulerStep2(u, dt);
    }
}
