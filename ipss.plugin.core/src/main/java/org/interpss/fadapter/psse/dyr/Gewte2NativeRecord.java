package org.interpss.fadapter.psse.dyr;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/** Lossless legacy electrical-control record; no dynamic equations or attachment. */
public record Gewte2NativeRecord(int generatorBus, String generatorId,
        List<Integer> integers, List<Double> constants) {
    public static final int INTEGER_COUNT = 12;
    public static final int CONSTANT_COUNT = 67;
    public static final int STATE_COUNT = 18;
    public static final int VARIABLE_COUNT = 9;
    private static final int[] ALLOCATION = {4, 0, INTEGER_COUNT, CONSTANT_COUNT,
            STATE_COUNT, VARIABLE_COUNT};

    public Gewte2NativeRecord {
        generatorId = Objects.requireNonNull(generatorId, "generatorId").trim();
        integers = List.copyOf(integers);
        constants = List.copyOf(constants);
        if (generatorBus <= 0 || generatorId.isEmpty() || generatorId.length() > 2
                || generatorId.contains("'")) {
            throw new IllegalArgumentException("Positive GEWTE2 bus and one-/two-character unit ID required");
        }
        if (integers.size() != INTEGER_COUNT || constants.size() != CONSTANT_COUNT) {
            throw new IllegalArgumentException("Exact GEWTE2 integer and constant counts required");
        }
        for (double value : constants) {
            if (!Double.isFinite(value)) {
                throw new IllegalArgumentException("GEWTE2 constants must be finite");
            }
        }
    }

    /** Decode only the independently published 4/0/12/67/18/9 legacy layout. */
    public static Gewte2NativeRecord decode(PsseDyrRecord record) {
        Objects.requireNonNull(record, "record");
        try {
            if (!record.sourceModelName().equals("GEWTE2")
                    || !record.canonicalModelName().equals("GEWTE2")
                    || record.parameterOffset() != 4
                    || record.parameterCount() != 6 + INTEGER_COUNT + CONSTANT_COUNT
                    || !record.fields().get(1).equalsIgnoreCase("USRMDL")
                    || record.busNumber() <= 0
                    || Integer.parseInt(record.fields().getFirst()) != record.busNumber()
                    || !record.fields().get(2).trim().equals(record.deviceId())) {
                throw new IllegalArgumentException("Exact GEWTE2 USRMDL wrapper and cardinality required");
            }
            for (int i = 0; i < ALLOCATION.length; i++) {
                if (record.intParameter(i) != ALLOCATION[i]) {
                    throw new IllegalArgumentException("Incorrect GEWTE2 allocation at position " + (i + 1));
                }
            }
            var integers = new ArrayList<Integer>(INTEGER_COUNT);
            var constants = new ArrayList<Double>(CONSTANT_COUNT);
            for (int i = 0; i < INTEGER_COUNT; i++) integers.add(record.intParameter(6 + i));
            for (int i = 0; i < CONSTANT_COUNT; i++) {
                double value = record.doubleParameter(6 + INTEGER_COUNT + i);
                if (!Double.isFinite(value)) {
                    throw new IllegalArgumentException("GEWTE2 constant " + (i + 1) + " must be finite");
                }
                constants.add(value);
            }
            return new Gewte2NativeRecord(record.busNumber(), record.deviceId(), integers, constants);
        } catch (IllegalArgumentException error) {
            throw new IllegalArgumentException(error.getMessage() + " at "
                    + record.source() + ":" + record.startLine(), error);
        }
    }
}
