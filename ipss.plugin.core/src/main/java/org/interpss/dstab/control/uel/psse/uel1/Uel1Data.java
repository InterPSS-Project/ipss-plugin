package org.interpss.dstab.control.uel.psse.uel1;

/** Native PSS/E UEL1 parameter record in DYR order. */
public record Uel1Data(double kur, double kuc, double kuf,
        double vurmax, double vucmax, double kui, double kul,
        double vuimax, double vuimin, double tu1, double tu2,
        double tu3, double tu4, double vulmax, double vulmin) {

    public Uel1Data {
        if (!finite(kur, kuc, kuf, vurmax, vucmax, kui, kul,
                vuimax, vuimin, tu1, tu2, tu3, tu4, vulmax, vulmin)) {
            throw new IllegalArgumentException("UEL1 parameters must be finite");
        }
        if (vurmax < 0.0 || vucmax < 0.0 || tu1 < 0.0 || tu2 < 0.0
                || tu3 < 0.0 || tu4 < 0.0 || vuimax < vuimin
                || vulmax < vulmin) {
            throw new IllegalArgumentException("UEL1 limits/time constants are invalid");
        }
        if (Math.abs(kui) < 1.0e-12 && Math.abs(kul) < 1.0e-12) {
            throw new IllegalArgumentException("UEL1 requires nonzero KUI or KUL");
        }
    }

    private static boolean finite(double... values) {
        for (double value : values) if (!Double.isFinite(value)) return false;
        return true;
    }
}
