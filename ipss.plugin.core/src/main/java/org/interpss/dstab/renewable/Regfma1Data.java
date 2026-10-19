package org.interpss.dstab.renewable;

/**
 * WECC REGFM_A1 parameters on the inverter MVA base.
 *
 * <p>The supported legacy 19-value exchange used by Texas2k contains
 * {@code Vflag} followed by eighteen control parameters. Its compatibility
 * voltage-source interface uses compatibility constants {@code Re=0} and
 * {@code Xe=0.15}, rather than the generator RAW source impedance. The WECC
 * specification lists 0.15 as an example reactance, not a mandatory default.
 * {@code QVflag=0} selects the
 * reactive-power plant-controller input.  The full constructor also represents
 * the complete PowerWorld REGFM_A1 data record. This legacy exchange is not
 * the current PSS/E 36.7 two-ICON, 18-CON layout, whose coupling impedance is
 * supplied by the generator RAW record. Native records use {@link #nativePsse}
 * and do not apply the PowerWorld-only reactance floor or limit swaps.</p>
 */
public record Regfma1Data(
        double tpf, double tqf, double tvf, double re, double xe, double imax,
        double emax, double emin, double pmax, double pmin, double qmax, double qmin,
        double mp, double mq, double kpv, double kiv,
        double kppmax, double kipmax, double kpqmax, double kiqmax,
        int vflag, int qvflag, ParameterRules parameterRules) {

    /** Keep simulator-specific automatic corrections out of native PSS/E input. */
    public enum ParameterRules { POWERWORLD_COMPATIBLE, NATIVE_PSSE_36_7 }

    /** Source-compatible full typed/PowerWorld constructor. */
    public Regfma1Data(double tpf, double tqf, double tvf, double re, double xe, double imax,
            double emax, double emin, double pmax, double pmin, double qmax, double qmin,
            double mp, double mq, double kpv, double kiv,
            double kppmax, double kipmax, double kpqmax, double kiqmax,
            int vflag, int qvflag) {
        this(tpf,tqf,tvf,re,xe,imax,emax,emin,pmax,pmin,qmax,qmin,mp,mq,kpv,kiv,
                kppmax,kipmax,kpqmax,kiqmax,vflag,qvflag,ParameterRules.POWERWORLD_COMPATIBLE);
    }

    /** PSS/E 36.7: two ICONs, eighteen CONs, and machine-base RAW ZSOURCE. */
    public static Regfma1Data nativePsse(int vflag, int qvflag,
            org.apache.commons.math3.complex.Complex source, double... constants) {
        if (source == null) throw new IllegalArgumentException("Native REGFMA1 requires RAW ZSOURCE");
        if (constants.length != 18)
            throw new IllegalArgumentException("Native REGFMA1 requires eighteen constants");
        return new Regfma1Data(constants[0],constants[1],constants[2],source.getReal(),source.getImaginary(),
                constants[3],constants[4],constants[5],constants[6],constants[7],constants[8],constants[9],
                constants[10],constants[11],constants[16],constants[17],
                constants[12],constants[13],constants[14],constants[15],vflag,qvflag,
                ParameterRules.NATIVE_PSSE_36_7);
    }

    /** Legacy exchange order (one flag and 18 constants after the machine ID). */
    public Regfma1Data(int vflag, double tpf, double tqf, double tvf, double imax,
            double emax, double emin, double pmax, double pmin, double qmax, double qmin,
            double mp, double mq, double kppmax, double kipmax,
            double kpqmax, double kiqmax, double kpv, double kiv) {
        this(tpf, tqf, tvf, 0.0, 0.15, imax, emax, emin, pmax, pmin, qmax, qmin,
                mp, mq, kpv, kiv, kppmax, kipmax, kpqmax, kiqmax, vflag, 0);
    }

    public Regfma1Data {
        java.util.Objects.requireNonNull(parameterRules,"parameterRules");
        if (parameterRules == ParameterRules.NATIVE_PSSE_36_7) {
            for (double value : new double[]{tpf,tqf,tvf,re,xe,imax,emax,emin,pmax,pmin,qmax,qmin,
                    mp,mq,kpv,kiv,kppmax,kipmax,kpqmax,kiqmax})
                if (!Double.isFinite(value))
                    throw new IllegalArgumentException("Native REGFMA1 parameters and RAW ZSOURCE must be finite");
            if ((vflag != 0 && vflag != 1) || (qvflag != 0 && qvflag != 1))
                throw new IllegalArgumentException("Native REGFMA1 Vflag/QVflag must be zero or one");
            if (Math.hypot(re,xe) == 0.0)
                throw new IllegalArgumentException("Native REGFMA1 requires nonzero RAW ZSOURCE");
            if (emax < emin || pmax < pmin || qmax < qmin)
                throw new IllegalArgumentException("Native REGFMA1 limits are inverted; no vendor auto-correction is applied");
            if (mp <= 0 || mq <= 0 || kipmax <= 0 || kiqmax <= 0)
                throw new IllegalArgumentException("Native REGFMA1 mp, mq, Kip and Kiq must be positive");
        }
        if (tpf < 0.0 || tqf < 0.0 || tvf < 0.0) {
            throw new IllegalArgumentException("REGFMA1 filter time constants must be non-negative");
        }
        if (parameterRules == ParameterRules.POWERWORLD_COMPATIBLE) {
            xe = Math.max(0.0001, xe);
            if (emax < emin) { double value = emax; emax = emin; emin = value; }
            if (pmax < pmin) { double value = pmax; pmax = pmin; pmin = value; }
            if (qmax < qmin) { double value = qmax; qmax = qmin; qmin = value; }
        }
        if (kiv <= 0.0) throw new IllegalArgumentException("REGFMA1 Kiv must be positive");
    }
}
