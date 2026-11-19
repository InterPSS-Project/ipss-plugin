package org.interpss.plugin.aclf.ecf;

import java.util.Objects;
import org.apache.commons.math3.complex.Complex;

/** Immutable two-port operator. Currents enter the branch at each terminal. */
public record EcfBranchModel(String id, int from, int to, Complex yff, Complex yft,
        Complex ytf, Complex ytt) {
    public EcfBranchModel {
        Objects.requireNonNull(id);
        if (from < 0 || to < 0 || from == to) throw new IllegalArgumentException("Invalid branch endpoints: " + id);
        for (var y : new Complex[] { yff, yft, ytf, ytt })
            if (y == null || y.isNaN() || y.isInfinite())
                throw new IllegalArgumentException("Nonfinite branch admittance: " + id);
    }
    public Complex fromCurrent(Complex vf, Complex vt) { return yff.multiply(vf).add(yft.multiply(vt)); }
    public Complex toCurrent(Complex vf, Complex vt) { return ytf.multiply(vf).add(ytt.multiply(vt)); }
    public Complex fromPower(Complex vf, Complex vt) { return vf.multiply(fromCurrent(vf, vt).conjugate()); }
    public Complex toPower(Complex vf, Complex vt) { return vt.multiply(toCurrent(vf, vt).conjugate()); }

    /** Squared terminal current or apparent power, with exact derivatives in
     * [Vr_from, Vi_from, Vr_to, Vi_to]. Derivative arrays belong to this result. */
    public record ThermalEvaluation(double value, double[] gradient, double[][] hessian) { }
    public record ThermalFirstOrder(double value,double[] gradient) { }
    /** Trial/stationarity path: do not construct the second derivatives. */
    public ThermalFirstOrder thermalFirstOrder(boolean fromTerminal,boolean apparentPower,Complex vf,Complex vt) {
        Complex a=fromTerminal?yff:ytf,b=fromTerminal?yft:ytt;
        Complex current=a.multiply(vf).add(b.multiply(vt));
        Complex[] partials={a,a.multiply(Complex.I),b,b.multiply(Complex.I)};
        double squared=current.abs()*current.abs();double[] gradient=new double[4];
        for(int i=0;i<4;i++)gradient[i]=2*current.conjugate().multiply(partials[i]).getReal();
        if(!apparentPower)return new ThermalFirstOrder(squared,gradient);
        Complex terminal=fromTerminal?vf:vt;int start=fromTerminal?0:2;
        double d=terminal.abs()*terminal.abs();
        for(int i=0;i<4;i++) {
            double derivative=i==start?2*terminal.getReal():i==start+1?2*terminal.getImaginary():0;
            gradient[i]=d*gradient[i]+squared*derivative;
        }
        return new ThermalFirstOrder(d*squared,gradient);
    }
    /** Physical verification path with no derivative allocation. */
    public double thermalValue(boolean fromTerminal,boolean apparentPower,Complex vf,Complex vt) {
        Complex current=fromTerminal?fromCurrent(vf,vt):toCurrent(vf,vt);
        double squared=current.abs()*current.abs();
        if(!apparentPower)return squared;
        Complex terminal=fromTerminal?vf:vt;return terminal.abs()*terminal.abs()*squared;
    }
    public ThermalEvaluation thermal(boolean fromTerminal, boolean apparentPower, Complex vf, Complex vt) {
        Complex a = fromTerminal ? yff : ytf, b = fromTerminal ? yft : ytt;
        Complex current = a.multiply(vf).add(b.multiply(vt));
        Complex[] partials = {a, a.multiply(Complex.I), b, b.multiply(Complex.I)};
        double currentSquared = current.abs()*current.abs();
        double[] gradient = new double[4]; double[][] hessian = new double[4][4];
        for (int i=0;i<4;i++) {
            gradient[i]=2*current.conjugate().multiply(partials[i]).getReal();
            for (int j=0;j<4;j++) hessian[i][j]=2*partials[i].conjugate().multiply(partials[j]).getReal();
        }
        if (!apparentPower) return new ThermalEvaluation(currentSquared,gradient,hessian);
        Complex terminal = fromTerminal ? vf : vt;
        int start = fromTerminal ? 0 : 2;
        double d = terminal.abs()*terminal.abs();
        double[] magnitudeGradient = new double[4];
        magnitudeGradient[start]=2*terminal.getReal(); magnitudeGradient[start+1]=2*terminal.getImaginary();
        for (int i=0;i<4;i++) for (int j=0;j<4;j++)
            hessian[i][j]=d*hessian[i][j]+magnitudeGradient[i]*gradient[j]+gradient[i]*magnitudeGradient[j]
                    +(i==j && i>=start && i<start+2 ? 2*currentSquared : 0);
        for (int i=0;i<4;i++) gradient[i]=d*gradient[i]+currentSquared*magnitudeGradient[i];
        return new ThermalEvaluation(d*currentSquared,gradient,hessian);
    }
}
