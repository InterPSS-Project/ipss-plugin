package org.interpss.dstab.mach;

/**
 * Parameters for the WECC GENQEJ synchronous-machine model.
 *
 * <p>The record order matches GENQEC, with {@code Kis} replacing {@code Kw}.
 * Reactance and time-constant corrections are delegated to {@link GenqecData}
 * so GENQEC and GENQEJ cannot drift apart.</p>
 */
public record GenqejData(
        double h, double d, double ra,
        double xd, double xq, double xdp, double xqp,
        double xdpp, double xqpp, double xl,
        double tdop, double tqop, double tdopp, double tqopp,
        double s1, double s12,
        double rcomp, double xcomp,
        double accel, double kis, int satFunc) {

    public GenqejData {
        if (!Double.isFinite(kis) || kis < 0.0) {
            throw new IllegalArgumentException("GENQEJ Kis must be finite and non-negative");
        }
        GenqecData normalized = normalize(h, d, ra, xd, xq, xdp, xqp, xdpp, xqpp, xl,
                tdop, tqop, tdopp, tqopp, s1, s12, rcomp, xcomp, accel, satFunc);
        h = normalized.h();
        d = normalized.d();
        ra = normalized.ra();
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
        rcomp = normalized.rcomp();
        xcomp = normalized.xcomp();
        accel = normalized.accel();
        satFunc = normalized.satFunc();
    }

    GenqecData asGenqecData() {
        return normalize(h, d, ra, xd, xq, xdp, xqp, xdpp, xqpp, xl,
                tdop, tqop, tdopp, tqopp, s1, s12, rcomp, xcomp, accel, satFunc);
    }

    private static GenqecData normalize(double h, double d, double ra,
            double xd, double xq, double xdp, double xqp, double xdpp, double xqpp, double xl,
            double tdop, double tqop, double tdopp, double tqopp, double s1, double s12,
            double rcomp, double xcomp, double accel, int satFunc) {
        return new GenqecData(h, d, ra, xd, xq, xdp, xqp, xdpp, xqpp, xl,
                tdop, tqop, tdopp, tqopp, s1, s12, rcomp, xcomp, accel, 0.0, satFunc);
    }
}
