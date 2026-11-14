package org.interpss.fadapter.psse.dyr;

import com.interpss.dstab.hvdc.model.Chvdc2Parameters;
import com.interpss.dstab.hvdc.model.Chvdc2Parameters.Dialect;

/** Strict standard/legacy named-link decoding. Neither form is automatically attached by this mapper. */
public final class Chvdc2RecordMapper {
    private static final int[] LEGACY_ALLOCATION = {18, 1, 3, 48, 18, 24};
    private Chvdc2RecordMapper() { }

    public static Chvdc2Parameters prepare(PsseDyrRecord record) {
        java.util.Objects.requireNonNull(record);
        String location = record.source() + ":" + record.startLine();
        try {
            if (!record.hasNamedTarget()) throw new IllegalArgumentException("Named CHVDC2 link required");
            boolean legacy = record.fields().get(1).equalsIgnoreCase("USRDCL");
            String expected = legacy ? "CHVDC2U1" : "CHVDC2";
            if (!expected.equals(record.sourceModelName()) || !expected.equals(record.canonicalModelName()))
                throw new IllegalArgumentException("Exact " + expected + " source identity required");
            int offset = legacy ? LEGACY_ALLOCATION.length : 0;
            if (record.parameterCount() != offset + Chvdc2Parameters.ICON_COUNT + Chvdc2Parameters.CONSTANT_COUNT)
                throw new IllegalArgumentException("Incorrect " + expected + " parameter count");
            for (int i = 0; i < offset; i++) if (record.intParameter(i) != LEGACY_ALLOCATION[i])
                throw new IllegalArgumentException("Incorrect CHVDC2U1 allocation field " + (i + 1));
            int[] flags = new int[Chvdc2Parameters.ICON_COUNT];
            double[] constants = new double[Chvdc2Parameters.CONSTANT_COUNT];
            for (int i = 0; i < flags.length; i++) flags[i] = record.intParameter(offset + i);
            for (int i = 0; i < constants.length; i++) constants[i] = record.doubleParameter(offset + flags.length + i);
            return new Chvdc2Parameters(record.deviceId(), legacy ? Dialect.CHVDC2U1 : Dialect.CHVDC2, flags, constants);
        } catch (IllegalArgumentException failure) {
            throw new IllegalArgumentException("Invalid named-link data at " + location + ": " + failure.getMessage(), failure);
        }
    }
}
