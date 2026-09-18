package org.interpss.core.dstab.reference;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;

/** Immutable trajectory checkpoints stored directly as typed test values. */
public final class EmbeddedTrajectoryReference {
    private final List<Field> fields;
    private final List<Sample> samples;

    private EmbeddedTrajectoryReference(List<Field> fields, List<Sample> samples) {
        this.fields = List.copyOf(fields);
        this.samples = List.copyOf(samples);
    }

    public static EmbeddedTrajectoryReference embedded(String datasetId) {
        return EmbeddedTrajectoryValues.dataset(datasetId);
    }

    static EmbeddedTrajectoryReference of(Field[] fields, double[][] rows) {
        List<Sample> samples = new ArrayList<>(rows.length);
        double previousTime = Double.NaN;
        int occurrence = 0;
        for (double[] row : rows) {
            if (row.length != fields.length + 1) {
                throw new IllegalArgumentException("checkpoint value count mismatch: "
                        + (row.length - 1) + " != " + fields.length);
            }
            double time = row[0];
            occurrence = Double.compare(time, previousTime) == 0 ? occurrence + 1 : 0;
            double[] values = new double[fields.length];
            System.arraycopy(row, 1, values, 0, values.length);
            samples.add(new Sample(time, occurrence, values));
            previousTime = time;
        }
        if (samples.isEmpty()) {
            throw new IllegalArgumentException("trajectory checkpoint set is empty");
        }
        return new EmbeddedTrajectoryReference(List.of(fields), samples);
    }

    public List<Field> fields() { return fields; }
    public List<Sample> samples() { return samples; }

    /** Returns one sample per timestamp, retaining the last event-side occurrence. */
    public List<Sample> postEventSamples() {
        LinkedHashMap<Double, Sample> byTime = new LinkedHashMap<>();
        for (Sample sample : samples) byTime.put(sample.time(), sample);
        return List.copyOf(byTime.values());
    }

    public int fieldIndex(String objectType, String primaryKey, String variableName) {
        for (int index = 0; index < fields.size(); index++) {
            Field field = fields.get(index);
            if (field.objectType().equals(objectType)
                    && field.primaryKey().equals(primaryKey)
                    && field.variableName().equals(variableName)) return index;
        }
        throw new IllegalArgumentException("checkpoint field not found: " + objectType + " "
                + primaryKey + " | " + variableName);
    }

    public record Field(String objectType, String primaryKey, String secondaryKey,
                        String variableName, String columnHeader) {}

    public record Sample(double time, int occurrence, double[] values) {
        public Sample {
            values = values.clone();
        }

        @Override
        public double[] values() { return values.clone(); }

        public double value(int fieldIndex) { return values[fieldIndex]; }
    }
}
