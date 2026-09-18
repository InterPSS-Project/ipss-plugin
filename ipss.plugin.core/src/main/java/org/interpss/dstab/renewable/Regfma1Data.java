package org.interpss.dstab.renewable;

/**
 * WECC REGFM_A1 parameters on the inverter MVA base.
 *
 * <p>The 19-value PSS/E REGFMA1 record contains {@code Vflag} followed by
 * eighteen control parameters.  Its voltage-source interface uses the WECC
 * defaults {@code Re=0} and {@code Xe=0.15}; {@code QVflag=0} selects the
 * reactive-power plant-controller input.  The full constructor also represents
 * the complete PowerWorld REGFM_A1 data record.</p>
 */
public record Regfma1Data(
        double tpf, double tqf, double tvf, double re, double xe, double imax,
        double emax, double emin, double pmax, double pmin, double qmax, double qmin,
        double mp, double mq, double kpv, double kiv,
        double kppmax, double kipmax, double kpqmax, double kiqmax,
        int vflag, int qvflag) {

    /** PSS/E REGFMA1 exchange order (one flag and 18 constants after the machine ID). */
    public Regfma1Data(int vflag, double tpf, double tqf, double tvf, double imax,
            double emax, double emin, double pmax, double pmin, double qmax, double qmin,
            double mp, double mq, double kppmax, double kipmax,
            double kpqmax, double kiqmax, double kpv, double kiv) {
        this(tpf, tqf, tvf, 0.0, 0.15, imax, emax, emin, pmax, pmin, qmax, qmin,
                mp, mq, kpv, kiv, kppmax, kipmax, kpqmax, kiqmax, vflag, 0);
    }

    public Regfma1Data {
        if (tpf < 0.0 || tqf < 0.0 || tvf < 0.0) {
            throw new IllegalArgumentException("REGFMA1 filter time constants must be non-negative");
        }
        // PowerWorld's documented validation floor.  Applying it here keeps
        // the network admittance nonsingular for every import path.
        xe = Math.max(0.0001, xe);
        if (emax < emin) { double value = emax; emax = emin; emin = value; }
        if (pmax < pmin) { double value = pmax; pmax = pmin; pmin = value; }
        if (qmax < qmin) { double value = qmax; qmax = qmin; qmin = value; }
        if (kiv <= 0.0) throw new IllegalArgumentException("REGFMA1 Kiv must be positive");
    }
}
