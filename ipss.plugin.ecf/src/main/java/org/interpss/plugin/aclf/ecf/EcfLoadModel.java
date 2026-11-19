package org.interpss.plugin.aclf.ecf;

import org.apache.commons.math3.complex.Complex;

/** Constant-power consumption in per unit. Squared voltage is an explicit input
 * so the same local equations can be used by power flow and lifted OPF. */
public record EcfLoadModel(double p, double q) {
    public EcfLoadModel {
        if (!Double.isFinite(p) || !Double.isFinite(q))
            throw new IllegalArgumentException("Nonfinite constant-power load");
    }

    /** Partial derivatives hold the other two voltage inputs fixed. */
    public record Evaluation(Complex current, Complex dReal, Complex dImaginary,
            Complex dSquaredMagnitude) { }

    public Evaluation evaluate(double real, double imaginary, double squaredMagnitude) {
        if (!Double.isFinite(real) || !Double.isFinite(imaginary)
                || !(squaredMagnitude > 0) || !Double.isFinite(squaredMagnitude))
            throw new IllegalArgumentException("Current model requires finite voltage and positive squared magnitude");
        var current = new Complex((p * real + q * imaginary) / squaredMagnitude,
                (p * imaginary - q * real) / squaredMagnitude);
        return new Evaluation(current, new Complex(p, -q).divide(squaredMagnitude),
                new Complex(q, p).divide(squaredMagnitude), current.divide(-squaredMagnitude));
    }

    /** Exact Hessian of realWeight*Ir + imaginaryWeight*Ii in [Vr, Vi, d]. */
    public double[][] weightedHessian(double real, double imaginary, double squaredMagnitude,
            double realWeight, double imaginaryWeight) {
        var evaluation = evaluate(real, imaginary, squaredMagnitude);
        if (!Double.isFinite(realWeight) || !Double.isFinite(imaginaryWeight))
            throw new IllegalArgumentException("Nonfinite load equation weight");
        double xD = -(realWeight*p-imaginaryWeight*q)/(squaredMagnitude*squaredMagnitude);
        double yD = -(realWeight*q+imaginaryWeight*p)/(squaredMagnitude*squaredMagnitude);
        double dd = 2*(realWeight*evaluation.current().getReal()+imaginaryWeight*evaluation.current().getImaginary())
                /(squaredMagnitude*squaredMagnitude);
        return new double[][]{{0,0,xD},{0,0,yD},{xD,yD,dd}};
    }
}
