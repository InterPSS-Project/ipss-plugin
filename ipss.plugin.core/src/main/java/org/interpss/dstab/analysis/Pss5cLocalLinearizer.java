package org.interpss.dstab.analysis;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import org.apache.commons.math3.complex.Complex;
import org.apache.commons.math3.linear.Array2DRowRealMatrix;
import org.apache.commons.math3.linear.EigenDecomposition;
import com.interpss.dstab.controller.pss.pss5c.Pss5cNativeData;

/** Read-only continuous-time A/B diagnostic for the supported PSS5C orders.
 * These are controller internal-state modes, including potentially unobservable
 * modes. Exciter, machine, output-limiter sensitivity and network feedback are
 * not closed around this matrix. This is not a system-stability assessment or
 * certification of a reference simulator's implementation.
 */
public final class Pss5cLocalLinearizer {
    private Pss5cLocalLinearizer() {}
    public static final List<String> STATE_NAMES = List.of("lowPosition", "lowRate",
            "highPosition", "highRate", "highAcceleration", "veryLowUpperLag", "veryLowLowerLag",
            "lowUpperLag", "lowLowerLag", "intermediateUpperLag", "intermediateLowerLag",
            "highUpperLag", "highLowerLag");

    public static Snapshot analyze(Pss5cNativeData data) {
        Objects.requireNonNull(data, "data");
        double[][] a = new double[13][13];double[] b = new double[13];
        boolean bypassed = data.isBypassed();
        if (!bypassed) {
            if (!data.hasSupportedTransducers())
                throw new IllegalArgumentException("Unsupported PSS5C transducer order");
            double[] p = data.parameters();
            if (data.hasFirstOrderLowTransducer()) {
                a[0][0] = -1 / p[2];b[0] = 1 / p[2];
            } else if (!data.hasStaticLowTransducer()) {
                a[0][1] = 1;a[1][0] = -1 / p[1];a[1][1] = -p[2] / p[1];b[1] = 1 / p[1];
            }
            a[2][3] = 1;
            double highQuadratic = (p[5] + p[4] * p[6]) / p[7];
            double highLinear = p[4] + p[6] / p[7];
            if (data.hasSecondOrderHighTransducer()) {
                a[3][2] = -1 / highQuadratic;a[3][3] = -highLinear / highQuadratic;
                b[3] = 1 / highQuadratic;
            } else {
                a[3][4] = 1;
                double denominator = p[4] * p[5];
                a[4][2] = -p[7] / denominator;
                a[4][3] = -(p[6] + p[4] * p[7]) / denominator;
                a[4][4] = -(p[5] + p[4] * p[6]) / denominator;
                b[4] = p[7] / denominator;
            }
            for (int band = 0; band < 4; band++) {
                double frequency = p[10 + 4 * band];
                if (frequency <= 0) continue; // Frozen disabled memories, as in the runtime.
                for (int branch = 0; branch < 2; branch++) {
                    int row = 5 + 2 * band + branch;
                    double rate = p[26 - branch] * frequency;
                    a[row][row] = -rate;
                    if (band == 3 && data.hasSecondOrderHighTransducer()) {
                        a[row][2] = -rate * p[8] / highQuadratic;
                        a[row][3] = -rate * p[8] * highLinear / highQuadratic;
                        b[row] = rate * p[8] / highQuadratic;
                    } else if (band == 3) a[row][4] = rate * p[8];
                    else if (data.hasFirstOrderLowTransducer()) {
                        a[row][0] = rate * p[3] * (1 - p[0] / p[2]);
                        b[row] = rate * p[3] * p[0] / p[2];
                    } else if (data.hasStaticLowTransducer()) b[row] = rate * p[3];
                    else {a[row][0] = rate * p[3];a[row][1] = rate * p[3] * p[0];}
                }
            }
        }
        for (double[] row : a) for (double value : row)
            if (!Double.isFinite(value)) throw new IllegalArgumentException("Nonfinite PSS5C Jacobian");
        for (double value : b) if (!Double.isFinite(value)) throw new IllegalArgumentException("Nonfinite PSS5C input vector");
        var decomposition = new EigenDecomposition(new Array2DRowRealMatrix(a, true));
        List<Complex> modes = new ArrayList<>();
        for (int i = 0; i < 13; i++) modes.add(new Complex(
                decomposition.getRealEigenvalue(i), decomposition.getImagEigenvalue(i)));
        return new Snapshot(a, b, modes, bypassed);
    }

    public static final class Snapshot {
        private final double[][] a;
        private final double[] b;
        private final List<Complex> modes;
        private final boolean bypassed;
        private Snapshot(double[][] a, double[] b, List<Complex> modes, boolean bypassed) {
            this.a = copy(a);this.b = b.clone();this.modes = List.copyOf(modes);this.bypassed = bypassed;
        }
        public List<String> stateNames() { return STATE_NAMES; }
        public double[][] stateMatrix() { return copy(a); }
        public double[] inputVector() { return b.clone(); }
        public List<Complex> eigenvalues() { return modes; }
        public boolean bypassed() { return bypassed; }
        /** Poles associated with the eight independent band memories, in state order. */
        public double[] bandPoles() {
            double[] values = new double[8];for (int i = 0; i < 8; i++) values[i] = a[5+i][5+i];return values;
        }
        private static double[][] copy(double[][] matrix) {
            double[][] values = new double[matrix.length][];
            for (int i = 0; i < matrix.length; i++) values[i] = matrix[i].clone();return values;
        }
    }
}
