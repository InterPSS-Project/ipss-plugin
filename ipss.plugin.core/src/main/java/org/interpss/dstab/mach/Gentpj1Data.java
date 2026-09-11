package org.interpss.dstab.mach;

/**
 * Native PSS/E GENTPJ1 parameters in Model Library record order.
 *
 * <p>GENTPJ1 uses the GENQEJ current-dependent saturation input, but fixes the
 * saturation characteristic to PSS/E's quadratic function and obtains stator
 * resistance from the RAW generator source impedance.</p>
 */
public record Gentpj1Data(
        double tdop, double tdopp, double tqop, double tqopp,
        double h, double d,
        double xd, double xq, double xdp, double xqp,
        double xdpp, double xqpp, double xl,
        double s1, double s12, double kis) {

    public Gentpj1Data {
        if (!Double.isFinite(kis) || kis <= 0.0 || kis >= 1.0) {
            throw new IllegalArgumentException("GENTPJ1 Kis must satisfy 0 < Kis < 1");
        }
        // Apply the same documented reactance/time-constant normalization used
        // by the shared equation implementation, and expose those effective
        // values consistently through this native-data record.
        GenqecData normalized = asGenqecData(h, d, xd, xq, xdp, xqp, xdpp, xqpp, xl,
                tdop, tqop, tdopp, tqopp, s1, s12);
        h = normalized.h();
        d = normalized.d();
        xd = normalized.xd();
        xq = normalized.xq();
        xdp = normalized.xdp();
        xqp = normalized.xqp();
        xdpp = normalized.xdpp();
        xqpp = normalized.xqpp();
        xl = normalized.xl();
        tdop = normalized.tdop();
        tqop = normalized.tqop();
        tdopp = normalized.tdopp();
        tqopp = normalized.tqopp();
        s1 = normalized.s1();
        s12 = normalized.s12();
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
                0.0, 0.0, 0.0, 0.0, 2);
    }
}
