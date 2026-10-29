package org.interpss.threePhase.basic;

import java.util.List;
import org.apache.commons.math3.complex.Complex;
import org.apache.commons.math3.linear.Array2DRowFieldMatrix;
import org.apache.commons.math3.linear.FieldLUDecomposition;

/** 60-Hz shielded cable constants in SI units, simple Carson earth return (100 ohm m).
 * Concentric neutrals/tape shields and optional bare neutrals are grounded and
 * eliminated by Kron reduction. Skin-effect corrections are supplied through Rac.
 */
public final class CableLineConstants {
    private CableLineConstants() { }

    /** Dimensions are metres and resistances ohms/metre. strands=0 denotes tape shield;
     * strands=-1 denotes an additional bare neutral (no insulation or shield).
     */
    public record Conductor(double x, double y, double rac, double gmr, double radius,
            double diameterInsulation, double insulationThickness, double epsR,
            double diameterCable, int strands, double strandGmr, double strandRadius,
            double strandResistance, double diameterShield, double tapeThickness, double tapeLap) {
        public Conductor {
            if (!Double.isFinite(x + y + rac + gmr + radius) || rac <= 0 || gmr <= 0 || radius <= 0)
                throw new IllegalArgumentException("Invalid cable conductor resistance or dimensions");
            if (strands < -1) throw new IllegalArgumentException("Invalid neutral strand count");
            if (strands >= 0) {
                if (!Double.isFinite(diameterInsulation + insulationThickness + epsR + diameterCable)
                        || insulationThickness <= 0 || diameterInsulation <= 2 * insulationThickness
                        || epsR <= 1 || diameterCable < diameterInsulation)
                    throw new IllegalArgumentException("Invalid cable insulation dimensions or permittivity");
                if (strands > 0 && (!Double.isFinite(strandGmr + strandRadius + strandResistance)
                        || strandGmr <= 0 || strandRadius <= 0 || strandResistance <= 0
                        || diameterCable <= 2 * strandRadius))
                    throw new IllegalArgumentException("Invalid concentric neutral data");
                if (strands == 0 && (!Double.isFinite(diameterShield + tapeThickness + tapeLap)
                        || tapeThickness <= 0 || diameterShield <= tapeThickness || tapeLap < 0 || tapeLap >= 100))
                    throw new IllegalArgumentException("Invalid tape shield data");
            }
        }
        double shieldRadius() { return strands > 0 ? diameterCable / 2 - strandRadius : (diameterShield - tapeThickness) / 2; }
        double shieldGmr() { return strands > 0
                ? Math.exp((Math.log(strandGmr * strands) + (strands - 1) * Math.log(shieldRadius())) / strands)
                : shieldRadius(); }
        double shieldResistance() { return strands > 0 ? strandResistance / strands
                : 0.3183 * 2.3718e-8 / (diameterShield * tapeThickness * Math.sqrt(50 / (100 - tapeLap))); }
    }

    public record Result(Complex[][] impedanceOhmPerMetre, double[][] capacitanceNfPerMetre) { }

    /** Phase conductors precede any bare neutrals; supports one, two or three phases. */
    public static Result calculate(List<Conductor> conductors, int phases) {
        int n = conductors.size(), size = n + phases;
        if (phases < 1 || phases > 3 || n < phases) throw new IllegalArgumentException("Invalid cable phase count");
        Complex[][] z = new Complex[size][size];
        for (int i = 0; i < n; i++) {
            var a = conductors.get(i);
            if ((i < phases) == (a.strands < 0)) throw new IllegalArgumentException("Cable phases must precede bare neutrals");
            z[i][i] = self(a.rac, a.gmr);
            for (int j = 0; j < i; j++) {
                var b = conductors.get(j);
                double d = Math.hypot(a.x - b.x, a.y - b.y);
                if (d <= (a.strands >= 0 ? a.diameterCable / 2 : a.radius)
                        + (b.strands >= 0 ? b.diameterCable / 2 : b.radius))
                    throw new IllegalArgumentException("Cable conductors overlap");
                z[i][j] = z[j][i] = mutual(d);
            }
        }
        for (int i = 0; i < phases; i++) {
            var a = conductors.get(i);
            z[n + i][n + i] = self(a.shieldResistance(), a.shieldGmr());
            for (int j = 0; j < i; j++) {
                var b = conductors.get(j);
                z[n + i][n + j] = z[n + j][n + i] = mutual(Math.hypot(a.x - b.x, a.y - b.y));
            }
            for (int j = 0; j < n; j++) {
                var b = conductors.get(j);
                double d = i == j ? a.shieldRadius() : Math.hypot(a.x - b.x, a.y - b.y);
                if (i != j && a.strands > 0)
                    d *= Math.pow(1 - Math.pow(a.shieldRadius() / d, a.strands), 1.0 / a.strands);
                z[n + i][j] = z[j][n + i] = mutual(d);
            }
        }
        var matrix = new Array2DRowFieldMatrix<Complex>(z, false);
        var zn = matrix.getSubMatrix(phases, size - 1, phases, size - 1);
        var zp = matrix.getSubMatrix(0, phases - 1, phases, size - 1);
        var reduced = matrix.getSubMatrix(0, phases - 1, 0, phases - 1)
                .subtract(zp.multiply(new FieldLUDecomposition<>(zn).getSolver()
                        .solve(matrix.getSubMatrix(phases, size - 1, 0, phases - 1))));
        double[][] c = new double[phases][phases];
        for (int i = 0; i < phases; i++) {
            var a = conductors.get(i);
            double outer = a.diameterInsulation / 2;
            // Retain OpenDSS's published engineering constants for reproducible interchange.
            c[i][i] = 2 * Math.PI * 8.854e-12 * a.epsR * 1e9
                    / Math.log(outer / (outer - a.insulationThickness));
        }
        return new Result(reduced.getData(), c);
    }

    private static Complex self(double resistance, double gmr) { return mutual(gmr).add(resistance); }
    private static Complex mutual(double distance) {
        double omegaMu = 2 * Math.PI * 60 * 12.56637e-7;
        return new Complex(omegaMu / 8, omegaMu / (2 * Math.PI) * Math.log(658.5 * Math.sqrt(100.0 / 60) / distance));
    }
}
