package org.interpss.dstab.dynLoad;

/** Native PSS/E IEEL algebraic load-characteristic parameters. */
public record IeelLoadData(
        double a1, double a2, double a3,
        double a4, double a5, double a6,
        double a7, double a8,
        double n1, double n2, double n3,
        double n4, double n5, double n6) {

    public IeelLoadData {
        double[] values = {a1, a2, a3, a4, a5, a6, a7, a8,
                n1, n2, n3, n4, n5, n6};
        for (double value : values) {
            if (!Double.isFinite(value)) {
                throw new IllegalArgumentException("IEEL parameters must be finite");
            }
        }
    }

    private static final double LOW_VOLTAGE_BREAKPOINT = 0.5;

    public double activeVoltageFactor(double voltage) {
        return a1 * voltagePower(voltage, n1)
                + a2 * voltagePower(voltage, n2)
                + a3 * voltagePower(voltage, n3);
    }

    public double reactiveVoltageFactor(double voltage) {
        return a4 * voltagePower(voltage, n4)
                + a5 * voltagePower(voltage, n5)
                + a6 * voltagePower(voltage, n6);
    }

    /**
     * Below 0.5 pu, a term with exponent below two uses a current ellipse.
     * The squared current after adding one is quadratic in V / 0.5. Its
     * coefficients impose zero current at zero voltage and match both the
     * value and slope of I = V^(n - 1) at the breakpoint. This keeps P and Q
     * continuous while bringing the low-voltage current to zero.
     * IEEL owns this fixed breakpoint independently of the static ZIP setting.
     */
    private static double voltagePower(double voltage, double exponent) {
        if (voltage >= LOW_VOLTAGE_BREAKPOINT || exponent >= 2.0) {
            return Math.pow(voltage, exponent);
        }
        if (voltage <= 0.0) {
            return 0.0;
        }
        double boundaryCurrent = Math.pow(LOW_VOLTAGE_BREAKPOINT, exponent - 1.0);
        double boundaryIncrement = boundaryCurrent * (boundaryCurrent + 2.0);
        double boundarySlope = 2.0 * (boundaryCurrent + 1.0)
                * (exponent - 1.0) * boundaryCurrent;
        double ratio = voltage / LOW_VOLTAGE_BREAKPOINT;
        double increment = ratio * (boundaryIncrement
                + (1.0 - ratio) * (boundaryIncrement - boundarySlope));
        // Rationalize sqrt(1 + increment) - 1 to retain precision near zero.
        return voltage * increment / (Math.sqrt(1.0 + increment) + 1.0);
    }
}
