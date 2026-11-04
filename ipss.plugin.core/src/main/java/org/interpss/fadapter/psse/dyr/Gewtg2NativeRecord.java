package org.interpss.fadapter.psse.dyr;

import java.util.List;
import java.util.Objects;

/** Exact legacy wrapper data only; this does not establish converter equations
 * or attach a dynamic model. Slot meanings remain subject to the legacy source. */
public record Gewtg2NativeRecord(int generatorBus, String generatorId,
        List<Integer> integers, List<Double> constants) {
    public static final int INTEGER_COUNT = 4;
    public static final int CONSTANT_COUNT = 18;
    public static final int STATE_COUNT = 3;
    public static final int VARIABLE_COUNT = 5;

    public Gewtg2NativeRecord {
        generatorId = Objects.requireNonNull(generatorId, "generatorId").trim();
        integers = List.copyOf(integers);
        constants = List.copyOf(constants);
        if (generatorBus <= 0 || generatorId.isEmpty() || generatorId.length() > 2
                || generatorId.contains("'")) {
            throw new IllegalArgumentException("Positive GEWTG2 bus and one-/two-character unit ID required");
        }
        if (integers.size() != INTEGER_COUNT || constants.size() != CONSTANT_COUNT) {
            throw new IllegalArgumentException("Exact GEWTG2 integer and constant counts required");
        }
        for (double value : constants) {
            if (!Double.isFinite(value)) throw new IllegalArgumentException("GEWTG2 constants must be finite");
        }
    }

    /** Decode only the independently published 1/1/4/18/3/5 legacy layout. */
    public static Gewtg2NativeRecord decode(PsseDyrRecord record) {
        Objects.requireNonNull(record, "record");
        try {
            if (!record.sourceModelName().equals("GEWTG2") || record.parameterOffset() != 4
                    || record.parameterCount() != 6 + INTEGER_COUNT + CONSTANT_COUNT
                    || !record.fields().get(1).equalsIgnoreCase("USRMDL")
                    || !record.fields().get(3).equalsIgnoreCase("GEWTG2")
                    || Integer.parseInt(record.fields().getFirst()) != record.busNumber()
                    || !record.fields().get(2).trim().equals(record.deviceId())) {
                throw new IllegalArgumentException("Exact GEWTG2 USRMDL wrapper and cardinality required");
            }
            int[] allocation = {1, 1, INTEGER_COUNT, CONSTANT_COUNT, STATE_COUNT, VARIABLE_COUNT};
            for (int i = 0; i < allocation.length; i++) {
                if (record.intParameter(i) != allocation[i]) {
                    throw new IllegalArgumentException("Incorrect GEWTG2 allocation at position " + (i + 1));
                }
            }
            var integers = new java.util.ArrayList<Integer>(INTEGER_COUNT);
            var constants = new java.util.ArrayList<Double>(CONSTANT_COUNT);
            for (int i = 0; i < INTEGER_COUNT; i++) integers.add(record.intParameter(6 + i));
            for (int i = 0; i < CONSTANT_COUNT; i++) constants.add(record.doubleParameter(6 + INTEGER_COUNT + i));
            return new Gewtg2NativeRecord(record.busNumber(), record.deviceId(), integers, constants);
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException(e.getMessage() + " at " + record.source() + ":"
                    + record.startLine(), e);
        }
    }
}
