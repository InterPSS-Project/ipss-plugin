package org.interpss.plugin.aclf.ecf;

import org.apache.commons.math3.complex.Complex;

/** Immutable physical parameters needed for Tx stepping. Series admittances
 * grow by 1+eta*scale; taps/phase shifts approach unity/zero. Shunts open at
 * eta=1 and recover continuously, following the Tx-stepping construction. */
public record EcfTxBranch(EcfBranchModel original,Complex series,Complex fromShunt,
        Complex toShunt,Complex fromTap,Complex toTap) {
    public EcfTxBranch {
        java.util.Objects.requireNonNull(original);
        for(var value:new Complex[]{series,fromShunt,toShunt,fromTap,toTap})
            if(value==null || value.isNaN() || value.isInfinite())throw new IllegalArgumentException("Nonfinite Tx parameter");
        if(!(fromTap.abs()>0) || !(toTap.abs()>0))throw new IllegalArgumentException("Positive tap magnitude required");
    }
    public EcfBranchModel at(double eta,double scale) {
        if(!(eta>=0 && eta<=1) || !(scale>0) || !Double.isFinite(scale))throw new IllegalArgumentException("Invalid Tx parameters");
        if(eta==0)return original;
        var y=series.multiply(1+eta*scale);
        var tf=tap(fromTap,eta);var tt=tap(toTap,eta);
        return new EcfBranchModel(original.id(),original.from(),original.to(),
                y.add(fromShunt.multiply(1-eta)).divide(tf.abs()*tf.abs()),
                y.negate().divide(tf.conjugate().multiply(tt)),
                y.negate().divide(tf.multiply(tt.conjugate())),
                y.add(toShunt.multiply(1-eta)).divide(tt.abs()*tt.abs()));
    }
    private static Complex tap(Complex original,double eta) {
        double magnitude=original.abs()+(1-original.abs())*eta,angle=(1-eta)*original.getArgument();
        return new Complex(magnitude*Math.cos(angle),magnitude*Math.sin(angle));
    }
}
