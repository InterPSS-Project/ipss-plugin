package org.interpss.dstab.control.pss.ieee.y1992.pss2a;

import com.interpss.dstab.controller.cml.field.block.WashoutControlBlock;

/**
 * PSS2A washout stage with the model-defined zero-time-constant bypass.
 *
 * <p>The optional Tw2/Tw4 stages are bypassed when their time constant is
 * zero.  The generic washout block represents {@code sT/(1+sT)} literally,
 * so using it at T=0 would divide by zero during the predictor step.</p>
 */
public class Pss2aWashoutBlock extends WashoutControlBlock implements
        com.interpss.dstab.controller.cml.runtime.CmlPureOutputRefresh,
        com.interpss.dstab.controller.cml.runtime.CmlDiagnosticSource {
    @Override public boolean supportsPureOutputRefresh() { return getClass() == Pss2aWashoutBlock.class; }

    @Override public java.util.List<com.interpss.dstab.controller.cml.runtime.CmlRuntimeDiagnostics.Binding>
            diagnosticBindings() {
        return java.util.List.of(new com.interpss.dstab.controller.cml.runtime.CmlRuntimeDiagnostics.Binding(
                "x", this::getStateX, this::getDX_dt,
                () -> getClass() == Pss2aWashoutBlock.class ? (isBypassed() ? 0. : dX_dt(getU())) : Double.NaN,
                () -> com.interpss.dstab.controller.cml.runtime.CmlRuntimeDiagnostics.LimiterStatus.UNAVAILABLE));
    }
    private static final double EPS = 1.0e-12;

    public Pss2aWashoutBlock(double k, double t) {
        super(k, t);
    }

    private boolean isBypassed() {
        return getT() <= EPS;
    }

    @Override
    public boolean initStateU0(double u0) {
        if (!isBypassed()) return super.initStateU0(u0);
        setU(u0);
        setStateX(0.0);
        return true;
    }

    @Override
    public boolean initStateY0(double y0) {
        if (!isBypassed()) return super.initStateY0(y0);
        if (Math.abs(getK()) <= EPS) {
            if (Math.abs(y0) > EPS) return false;
            setU(0.0);
        }
        else {
            setU(y0 / getK());
        }
        setStateX(0.0);
        return true;
    }

    @Override
    public double getY() {
        return isBypassed() ? getK() * getU() : super.getY();
    }

    @Override
    public void eulerStep1(double u, double dt) {
        if (isBypassed()) {
            setU(u);
            return;
        }
        super.eulerStep1(u, dt);
    }

    @Override
    public void eulerStep2(double u, double dt) {
        if (isBypassed()) {
            setU(u);
            return;
        }
        super.eulerStep2(u, dt);
    }
}
