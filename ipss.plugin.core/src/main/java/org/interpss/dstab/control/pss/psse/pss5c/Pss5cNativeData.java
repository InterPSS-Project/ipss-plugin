package org.interpss.dstab.control.pss.psse.pss5c;

/** Exact thirty-constant native PSS5C record, without an input selector. */
public final class Pss5cNativeData {
    public static final int PARAMETER_COUNT = 30;
    private final double[] parameters;

    public Pss5cNativeData(double[] values) {
        if (values == null || values.length != PARAMETER_COUNT)
            throw new IllegalArgumentException("Native PSS5C requires exactly 30 constants");
        parameters = values.clone();
        for (double value : parameters)
            if (!Double.isFinite(value)) throw new IllegalArgumentException("PSS5C constants must be finite");
    }

    public double parameter(int index) { return parameters[index]; }
    public double[] parameters() { return parameters.clone(); }

    /** This realization supports the published full-order transducers. */
    public boolean hasFullOrderTransducers() {
        return parameters[1] > 0 && parameters[4] > 0 && parameters[5] > 0 && parameters[7] > 0;
    }

    /** Exact first-order low transducer, not a timestep-dependent approximation. */
    public boolean hasFirstOrderLowTransducer() {
        return parameters[1] == 0 && parameters[2] > 0;
    }

    public boolean hasSupportedTransducers() {
        return hasFullOrderTransducers() || (hasFirstOrderLowTransducer()
                && parameters[4] > 0 && parameters[5] > 0 && parameters[7] > 0);
    }

    public boolean isBypassed() {
        if (parameters[25] == 0 || parameters[26] == 0 || parameters[27] == 0) return true;
        for (int band = 0; band < 4; band++) {
            int offset = 9 + 4 * band;
            double inputGain = parameters[band == 3 ? 8 : 3];
            if (inputGain != 0 && parameters[offset] != 0
                    && parameters[offset + 2] != 0 && parameters[offset + 3] != 0
                    && parameters[offset + 1] <= 0) return true;
        }
        return false;
    }

    public double upper(int index) { return Math.max(parameters[index], parameters[index + 1]); }
    public double lower(int index) { return Math.min(parameters[index], parameters[index + 1]); }
}
