package org.interpss.dstab.dynLoad.impl;

/** Reusable finite-input complex division with the established scaled arithmetic order. */
final class MotorElectricalKernel {
    private MotorElectricalKernel() { }
    static void divide(double a, double b, double c, double d, double[] output) {
        if (Math.abs(c) < Math.abs(d)) {
            double ratio = c / d;
            double denominator = c * ratio + d;
            output[0] = (a * ratio + b) / denominator;
            output[1] = (b * ratio - a) / denominator;
        } else {
            double ratio = d / c;
            double denominator = d * ratio + c;
            output[0] = (b * ratio + a) / denominator;
            output[1] = (b - a * ratio) / denominator;
        }
    }
}
