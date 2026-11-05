package org.interpss.fadapter.psse.dyr;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/** Lossless decoding of the legacy pitch-controller record, without runtime attachment.
 * Constants retain their source order until an exact equation contract is available.
 */
public final class Gewtp2NativeRecord {
    private static final int[] ALLOCATION = {505, 0, 1, 10, 3, 3};
    private final PsseDyrRecord source;
    private final List<Double> constants;
    private final int integerSlot;

    private Gewtp2NativeRecord(PsseDyrRecord source, int integerSlot, List<Double> constants) {
        this.source = source;
        this.integerSlot = integerSlot;
        this.constants = List.copyOf(constants);
    }

    public static Gewtp2NativeRecord decode(PsseDyrRecord source) {
        Objects.requireNonNull(source, "source");
        try {
            if (!source.sourceModelName().equals("GEWTP2")
                    || !source.canonicalModelName().equals("GEWTP2")
                    || source.parameterOffset() != 4 || source.parameterCount() != 17
                    || !source.fields().get(1).equalsIgnoreCase("USRMDL")
                    || source.busNumber() <= 0
                    || Integer.parseInt(source.fields().getFirst()) != source.busNumber()
                    || source.deviceId().isBlank() || source.deviceId().length() > 2) {
                throw new IllegalArgumentException("Exact GEWTP2 USRMDL owner and allocation required");
            }
            for (int i = 0; i < ALLOCATION.length; i++) {
                if (source.intParameter(i) != ALLOCATION[i]) {
                    throw new IllegalArgumentException("Incorrect GEWTP2 allocation at position " + (i + 1));
                }
            }
            List<Double> values = new ArrayList<>(10);
            for (int i = 0; i < 10; i++) {
                double value = source.doubleParameter(7 + i);
                if (!Double.isFinite(value)) {
                    throw new IllegalArgumentException("GEWTP2 constant " + (i + 1) + " must be finite");
                }
                values.add(value);
            }
            return new Gewtp2NativeRecord(source, source.intParameter(6), values);
        } catch (IllegalArgumentException error) {
            throw new IllegalArgumentException(error.getMessage() + " at "
                    + source.source() + ":" + source.startLine(), error);
        }
    }

    public PsseDyrRecord source() { return source; }
    public int integerSlot() { return integerSlot; }
    public List<Double> constants() { return constants; }
}
