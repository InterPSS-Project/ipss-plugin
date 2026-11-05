package org.interpss.fadapter.psse.dyr;

import java.util.Objects;

/** Observation-wrapper identity only. Decoding does not attach a controller,
 * establish the meaning of the output slots, or qualify a runtime alias. */
public record Gewplt2NativeRecord(int recordIdentifier, int generatorBus, String generatorId) {
    public static final int VARIABLE_COUNT = 17;

    public Gewplt2NativeRecord {
        Objects.requireNonNull(generatorId, "generatorId");
        generatorId = generatorId.trim();
        if (recordIdentifier <= 0 || generatorBus <= 0 || generatorId.isEmpty()
                || generatorId.length() > 2 || generatorId.contains("'")) {
            throw new IllegalArgumentException("Positive GEWPLT2 record/target identities and one-/two-character unit ID required");
        }
    }

    /** Decode the published 512/0/2/0/0/17 wrapper. Its numeric record identifier
     * is deliberately separate from the generator bus in its first ICON. */
    public static Gewplt2NativeRecord decode(PsseDyrRecord record) {
        Objects.requireNonNull(record, "record");
        try {
            if (!record.sourceModelName().equals("GEWPLT2") || record.parameterOffset() != 3
                    || record.parameterCount() != 8 || !record.fields().get(1).equalsIgnoreCase("USRMSC")
                    || !record.fields().get(2).equalsIgnoreCase("GEWPLT2")
                    || !record.deviceId().equals("*")) {
                throw new IllegalArgumentException("Exact GEWPLT2 USRMSC wrapper and cardinality required");
            }
            int identity = Integer.parseInt(record.fields().getFirst());
            if (identity != record.busNumber()) {
                throw new IllegalArgumentException("GEWPLT2 record identifier must be positive and preserved");
            }
            int[] allocation = {512, 0, 2, 0, 0, VARIABLE_COUNT};
            for (int i = 0; i < allocation.length; i++) {
                if (record.intParameter(i) != allocation[i]) {
                    throw new IllegalArgumentException("Incorrect GEWPLT2 allocation at position " + (i + 1));
                }
            }
            requireQuoted(record, 1);
            requireQuoted(record, 2);
            requireQuoted(record, 10);
            return new Gewplt2NativeRecord(identity, record.intParameter(6), record.parameter(7));
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException(e.getMessage() + " at " + record.source() + ":"
                    + record.startLine(), e);
        }
    }

    private static void requireQuoted(PsseDyrRecord record, int field) {
        var matcher = java.util.regex.Pattern.compile("'(?:[^']|'')*'|[^\\s,]+").matcher(record.rawText());
        for (int i = 0; i <= field; i++) {
            if (!matcher.find()) throw new IllegalArgumentException("Missing GEWPLT2 character field");
        }
        if (!matcher.group().startsWith("'")) {
            throw new IllegalArgumentException("Quoted GEWPLT2 character field required at position " + field);
        }
    }
}
