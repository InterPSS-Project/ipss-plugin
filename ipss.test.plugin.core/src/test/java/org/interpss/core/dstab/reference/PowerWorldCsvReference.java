package org.interpss.core.dstab.reference;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;

/** Parser for metadata-prefixed CSV files written by PowerWorld TSGetResults. */
public final class PowerWorldCsvReference {
    private final List<Field> fields;
    private final List<Sample> samples;

    private PowerWorldCsvReference(List<Field> fields, List<Sample> samples) {
        this.fields = List.copyOf(fields);
        this.samples = List.copyOf(samples);
    }

    public static PowerWorldCsvReference read(Path path) throws Exception {
        List<String> lines = Files.readAllLines(path, StandardCharsets.UTF_8);
        if (lines.size() < 5 || !"ObjectFields".equals(lines.get(0))) {
            throw new IllegalArgumentException("not a PowerWorld TSGetResults CSV: " + path);
        }
        List<Field> fields = new ArrayList<>();
        int index = 2;
        while (index < lines.size() && !"END".equals(lines.get(index))) {
            List<String> row = csvRow(lines.get(index++));
            if (row.size() < 6) {
                throw new IllegalArgumentException("invalid PowerWorld field row at line " + index);
            }
            fields.add(new Field(row.get(0), row.get(1), row.get(2), row.get(4), row.get(5)));
        }
        if (index + 1 >= lines.size() || !lines.get(++index).startsWith("Results,")) {
            throw new IllegalArgumentException("PowerWorld CSV has no Results section: " + path);
        }
        List<Sample> samples = new ArrayList<>();
        int occurrence = 0;
        double previousTime = Double.NaN;
        while (++index < lines.size() && !"END".equals(lines.get(index))) {
            if (lines.get(index).isBlank()) continue;
            List<String> row = csvRow(lines.get(index));
            if (!row.isEmpty() && row.get(row.size() - 1).isEmpty()) row.remove(row.size() - 1);
            if (row.size() != fields.size() + 1) {
                throw new IllegalArgumentException("PowerWorld value count mismatch at line "
                        + (index + 1) + ": " + (row.size() - 1) + " != " + fields.size());
            }
            double time = Double.parseDouble(row.get(0));
            occurrence = Double.compare(time, previousTime) == 0 ? occurrence + 1 : 0;
            double[] values = new double[fields.size()];
            for (int column = 0; column < values.length; column++) {
                values[column] = Double.parseDouble(row.get(column + 1));
            }
            samples.add(new Sample(time, occurrence, values));
            previousTime = time;
        }
        if (samples.isEmpty()) throw new IllegalArgumentException("PowerWorld trajectory is empty");
        return new PowerWorldCsvReference(fields, samples);
    }

    public List<Field> fields() { return fields; }
    public List<Sample> samples() { return samples; }

    /** Returns one sample per timestamp, retaining PowerWorld's last (post-event) occurrence. */
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
        throw new IllegalArgumentException("PowerWorld field not found: " + objectType + " "
                + primaryKey + " | " + variableName);
    }

    private static List<String> csvRow(String line) {
        List<String> result = new ArrayList<>();
        StringBuilder value = new StringBuilder();
        boolean quoted = false;
        for (int index = 0; index < line.length(); index++) {
            char ch = line.charAt(index);
            if (ch == '"') {
                if (quoted && index + 1 < line.length() && line.charAt(index + 1) == '"') {
                    value.append('"');
                    index++;
                } else {
                    quoted = !quoted;
                }
            } else if (ch == ',' && !quoted) {
                result.add(value.toString());
                value.setLength(0);
            } else {
                value.append(ch);
            }
        }
        if (quoted) throw new IllegalArgumentException("unterminated CSV quote: " + line);
        result.add(value.toString());
        return result;
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
