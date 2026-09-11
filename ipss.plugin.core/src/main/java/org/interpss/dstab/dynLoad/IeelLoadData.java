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

    public double activeVoltageFactor(double voltage) {
        return a1 * Math.pow(voltage, n1)
                + a2 * Math.pow(voltage, n2)
                + a3 * Math.pow(voltage, n3);
    }

    public double reactiveVoltageFactor(double voltage) {
        return a4 * Math.pow(voltage, n4)
                + a5 * Math.pow(voltage, n5)
                + a6 * Math.pow(voltage, n6);
    }
}
