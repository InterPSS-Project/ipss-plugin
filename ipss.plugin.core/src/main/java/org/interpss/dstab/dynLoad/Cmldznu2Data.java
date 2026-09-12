package org.interpss.dstab.dynLoad;

import java.util.List;

/** Immutable 133-constant data record for the zone-scoped composite-load model. */
public final class Cmldznu2Data {
    public static final int PARAMETER_COUNT = 133;

    private static final List<String> PARAMETER_NAMES = List.of(
            "Mva", "Bss", "Rfdr", "Xfdr", "Fb", "Xxf", "Tfixhs", "Tfixls",
            "Ltc", "Tmin", "Tmax", "Step", "Vmin", "Vmax", "Td", "Tc", "Rcmp", "Xcmp",
            "FmA", "FmB", "FmC", "FmD", "Fel", "Pfel", "Vd1", "Vd2", "Pfs",
            "P1e", "P1c", "P2e", "P2c", "Pfrq", "Q1e", "Q1c", "Q2e", "Q2c", "Qfrq",
            "MtypA", "LfmA", "RaA", "LsA", "LpA", "LppA", "TpoA", "TppoA", "HA", "EtrqA",
            "Vtr1A", "Ttr1A", "Ftr1A", "Vrc1A", "Trc1A", "Vtr2A", "Ttr2A", "Ftr2A", "Vrc2A", "Trc2A",
            "MtypB", "LfmB", "RaB", "LsB", "LpB", "LppB", "TpoB", "TppoB", "HB", "EtrqB",
            "Vtr1B", "Ttr1B", "Ftr1B", "Vrc1B", "Trc1B", "Vtr2B", "Ttr2B", "Ftr2B", "Vrc2B", "Trc2B",
            "MtypC", "LfmC", "RaC", "LsC", "LpC", "LppC", "TpoC", "TppoC", "HC", "EtrqC",
            "Vtr1C", "Ttr1C", "Ftr1C", "Vrc1C", "Trc1C", "Vtr2C", "Ttr2C", "Ftr2C", "Vrc2C", "Trc2C",
            "Tstall", "Trestart", "Tv", "Tf", "CompLf", "CompPf", "Vstall", "Rstall", "Xstall", "Lfadj",
            "Kp1", "Np1", "Kq1", "Nq1", "Kp2", "Np2", "Kq2", "Nq2", "Vbrk", "Frst", "Vrst",
            "CmpKpf", "CmpKqf", "Vc1off", "Vc2off", "Vc1on", "Vc2on", "Tth", "Th1t", "Th2t",
            "Fuvr", "Uvtr1", "Ttr1", "Uvtr2", "Ttr2", "FelRestart");

    private final double[] values;

    public Cmldznu2Data(double[] values) {
        if (values == null || values.length != PARAMETER_COUNT) {
            throw new IllegalArgumentException("CMLDZNU2 requires exactly 133 constants");
        }
        this.values = values.clone();
        for (int i = 0; i < this.values.length; i++) {
            if (!Double.isFinite(this.values[i])) {
                throw new IllegalArgumentException("CMLDZNU2 " + PARAMETER_NAMES.get(i) + " must be finite");
            }
        }
        if (value(0) > 0.0) {
            throw new IllegalArgumentException("CMLDZNU2 subsystem MVA setting must be non-positive");
        }
        if (value(9) > value(10) || value(12) > value(13)) {
            throw new IllegalArgumentException("CMLDZNU2 minimum limits must not exceed maximum limits");
        }
        if (value(11) < 0.0) {
            throw new IllegalArgumentException("CMLDZNU2 tap step must be non-negative");
        }
        for (int i = 18; i <= 22; i++) requireUnitInterval(i);
        requirePowerFactor(23);
        requirePowerFactor(26);
        if (value(24) < value(25)) {
            throw new IllegalArgumentException("CMLDZNU2 Vd1 must be greater than or equal to Vd2");
        }
        requireUnitInterval(49); requireUnitInterval(54);
        requireUnitInterval(69); requireUnitInterval(74);
        requireUnitInterval(89); requireUnitInterval(94);
        requirePowerFactor(102);
        requireUnitInterval(116); requireUnitInterval(127); requireUnitInterval(132);
    }

    public double value(int index) {
        if (index < 0 || index >= PARAMETER_COUNT) throw new IndexOutOfBoundsException(index);
        return values[index];
    }

    public double[] values() { return values.clone(); }
    public static List<String> parameterNames() { return PARAMETER_NAMES; }

    public double staticActiveFactor(double voltageRatio, double frequencyDeviation) {
        double p3 = 1.0 - value(28) - value(30);
        return (value(28) * Math.pow(nonnegative(voltageRatio), value(27))
                + value(30) * Math.pow(nonnegative(voltageRatio), value(29)) + p3)
                * (1.0 + value(31) * frequencyDeviation);
    }

    public double staticReactiveFactor(double voltageRatio, double frequencyDeviation) {
        double q3 = 1.0 - value(33) - value(35);
        return (value(33) * Math.pow(nonnegative(voltageRatio), value(32))
                + value(35) * Math.pow(nonnegative(voltageRatio), value(34)) + q3)
                * (1.0 + value(36) * frequencyDeviation);
    }

    /** Instantaneous electronic-load fraction before applying the restart latch. */
    public double electronicVoltageFraction(double voltage) {
        if (voltage >= value(24)) return 1.0;
        if (voltage <= value(25)) return 0.0;
        return (voltage - value(25)) / (value(24) - value(25));
    }

    private void requireUnitInterval(int index) {
        if (value(index) < 0.0 || value(index) > 1.0) {
            throw new IllegalArgumentException("CMLDZNU2 " + PARAMETER_NAMES.get(index)
                    + " must be in [0,1]");
        }
    }

    private void requirePowerFactor(int index) {
        if (Math.abs(value(index)) > 1.0) {
            throw new IllegalArgumentException("CMLDZNU2 " + PARAMETER_NAMES.get(index)
                    + " must be in [-1,1]");
        }
    }

    private static double nonnegative(double value) { return Math.max(0.0, value); }
}
