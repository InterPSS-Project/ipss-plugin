package org.interpss.fadapter.psse.dyr;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/** Lossless legacy shaft-record decoding only; does not establish runtime equivalence. */
public final class Gewtt1NativeRecord {
    private static final int[] ALLOCATION = {5, 0, 1, 5, 4, 3};
    private final PsseDyrRecord source;
    private final int integerSlot;
    private final List<Double> constants;

    private Gewtt1NativeRecord(PsseDyrRecord source, int integerSlot, List<Double> constants) {
        this.source = source;
        this.integerSlot = integerSlot;
        this.constants = List.copyOf(constants);
    }

    public static Gewtt1NativeRecord decode(PsseDyrRecord source) {
        Objects.requireNonNull(source, "source");
        try {
            if (!source.sourceModelName().equals("GEWTT1")
                    || !source.canonicalModelName().equals("GEWTT1")
                    || source.parameterOffset() != 4 || source.parameterCount() != 12
                    || !source.fields().get(1).equalsIgnoreCase("USRMDL")
                    || source.busNumber() <= 0
                    || Integer.parseInt(source.fields().getFirst()) != source.busNumber()
                    || source.deviceId().isBlank() || source.deviceId().length() > 2) {
                throw new IllegalArgumentException("Exact GEWTT1 USRMDL owner and allocation required");
            }
            for (int i = 0; i < ALLOCATION.length; i++) {
                if (source.intParameter(i) != ALLOCATION[i]) {
                    throw new IllegalArgumentException("Incorrect GEWTT1 allocation at position " + (i + 1));
                }
            }
            int integerSlot = source.intParameter(6);
            List<Double> constants = new ArrayList<>(5);
            for (int i = 0; i < 5; i++) {
                double value = source.doubleParameter(7 + i);
                if (!Double.isFinite(value)) {
                    throw new IllegalArgumentException("GEWTT1 constant " + (i + 1) + " must be finite");
                }
                constants.add(value);
            }
            return new Gewtt1NativeRecord(source, integerSlot, constants);
        } catch (IllegalArgumentException error) {
            throw new IllegalArgumentException(error.getMessage() + " at "
                    + source.source() + ":" + source.startLine(), error);
        }
    }

    public PsseDyrRecord source() { return source; }
    public int integerSlot() { return integerSlot; }
    public List<Double> constants() { return constants; }
}
