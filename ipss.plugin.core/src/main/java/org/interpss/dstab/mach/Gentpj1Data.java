package org.interpss.dstab.mach;

/**
 * GENTPJ1 parameters in the reference model-library record order.
 *
 * <p>Type-J uses current-dependent, scaled quadratic saturation and obtains
 * stator resistance from the RAW generator source impedance. The compatibility
 * GENQEC parameter view is not an electrical solver or a state realization.</p>
 */
public record Gentpj1Data(
        double tdop, double tdopp, double tqop, double tqopp,
        double h, double d,
        double xd, double xq, double xdp, double xqp,
        double xdpp, double xqpp, double xl,
        double s1, double s12, double kis) {

    public Gentpj1Data {
        // A zero subtransient time constant requires a distinct algebraic
        // reduction. Do not borrow GENQEC's reactance-collapse normalization:
        // the Type-J stator/current terms remain in that algebraic equation.
        // Until that boundary is independently qualified, reject it explicitly.
        if (!(tdopp > 0) || !(tqopp > 0)) {
            throw new IllegalArgumentException("Type-J requires positive subtransient time constants; zero-time reduction is not qualified");
        }
        // Zero is the current-independent saturation boundary, not an invalid
        // machine: the runtime then evaluates saturation at air-gap flux alone.
        // Reference import/initialization retains zero unchanged, and the
        // PowerWorld GENTPJ default is also zero. Rejecting that boundary hid
        // valid machines (and their dependent controls) during combined import.
        // Preserve the supplied value; do not replace it with a positive epsilon
        // or substitute a different machine. Other domain bounds are unchanged.
        if (!Double.isFinite(kis) || kis < 0.0 || kis >= 1.0) {
            throw new IllegalArgumentException("GENTPJ1 Kis must satisfy 0 <= Kis < 1");
        }
        // Type-J has its own native schema and voltage-state equations. The
        // compatibility compound-circuit data type performs model-specific
        // corrections (notably Xq'' <= Xq/4 when Tq0'=0, then Xl reduction).
        // Native Type-J retains those entered reactances, so using that view
        // here silently changed valid stator/saturation and fault-current data.
        // Validate this realization's domain without rewriting source values.
        // The voltage-state formulas require nonzero winding denominators,
        // not the compound circuit's stricter subtransient ordering. Retain
        // finite nonsingular source values; diagnostics can report their order.
        for(double value:new double[]{tdop,tdopp,tqop,tqopp,h,d,xd,xq,xdp,xqp,
                xdpp,xqpp,xl,s1,s12}) {
            if(!Double.isFinite(value))throw new IllegalArgumentException("Type-J constants must be finite");
        }
        if(!(h>0 && tdop>0 && tqop>=0 && xd>xdp && xq>=xqp
                && xdp>xl && xqp>xl && xl>=0
                && xdpp>=xl && xdpp!=xdp && xqpp>=xl && xqpp!=xqp
                && s1>=0 && s12>=0)) {
            throw new IllegalArgumentException("Type-J constants are outside the supported voltage-state domain; source values are not auto-corrected");
        }

    }

    GenqecData asGenqecData() {
        return asGenqecData(h, d, xd, xq, xdp, xqp, xdpp, xqpp, xl,
                tdop, tqop, tdopp, tqopp, s1, s12);
    }

    private static GenqecData asGenqecData(double h, double d,
            double xd, double xq, double xdp, double xqp,
            double xdpp, double xqpp, double xl,
            double tdop, double tqop, double tdopp, double tqopp,
            double s1, double s12) {
        return new GenqecData(h, d, 0.0, xd, xq, xdp, xqp, xdpp, xqpp, xl,
                tdop, tqop, tdopp, tqopp, s1, s12,
                0.0, 0.0, 0.0, 0.0, 1);
    }
}
