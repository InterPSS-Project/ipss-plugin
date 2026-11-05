package org.interpss.fadapter.psse.dyr;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/** Lossless decoding of the legacy aerodynamic record, without runtime attachment.
 * Constants retain their source order until an exact equation contract is available.
 */
public final class Gewta2NativeRecord {
    private static final int[] ALLOCATION = {505, 0, 0, 9, 1, 4};
    private final PsseDyrRecord source;
    private final List<Double> constants;

    private Gewta2NativeRecord(PsseDyrRecord source, List<Double> constants) {
        this.source = source;
        this.constants = List.copyOf(constants);
    }

    public static Gewta2NativeRecord decode(PsseDyrRecord source) {
        Objects.requireNonNull(source, "source");
        try {
            if (!source.sourceModelName().equals("GEWTA2")
                    || !source.canonicalModelName().equals("GEWTA2")
                    || source.parameterOffset() != 4 || source.parameterCount() != 15
                    || !source.fields().get(1).equalsIgnoreCase("USRMDL")
                    || source.busNumber() <= 0
                    || Integer.parseInt(source.fields().getFirst()) != source.busNumber()
                    || source.deviceId().isBlank() || source.deviceId().length() > 2) {
                throw new IllegalArgumentException("Exact GEWTA2 USRMDL owner and allocation required");
            }
            for (int i = 0; i < ALLOCATION.length; i++) {
                if (source.intParameter(i) != ALLOCATION[i]) {
                    throw new IllegalArgumentException("Incorrect GEWTA2 allocation at position " + (i + 1));
                }
            }
            List<Double> values = new ArrayList<>(9);
            for (int i = 0; i < 9; i++) {
                double value = source.doubleParameter(6 + i);
                if (!Double.isFinite(value)) {
                    throw new IllegalArgumentException("GEWTA2 constant " + (i + 1) + " must be finite");
                }
                values.add(value);
            }
            return new Gewta2NativeRecord(source, values);
        } catch (IllegalArgumentException error) {
            throw new IllegalArgumentException(error.getMessage() + " at "
                    + source.source() + ":" + source.startLine(), error);
        }
    }

    public PsseDyrRecord source() { return source; }
    public List<Double> constants() { return constants; }
}
