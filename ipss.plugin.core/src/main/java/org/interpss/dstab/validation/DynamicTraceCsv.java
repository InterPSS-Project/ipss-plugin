package org.interpss.dstab.validation;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

/** Reader/writer for the tool-neutral long-form dynamic trace CSV. */
public final class DynamicTraceCsv {
    public static final String HEADER = "time_s,device_id,signal,value,unit,base";

    private DynamicTraceCsv() {
    }

    public static void write(Path path, Collection<DynamicTraceSample> samples) throws IOException {
        List<DynamicTraceSample> ordered = samples.stream()
                .sorted(Comparator.comparing(DynamicTraceSample::key)
                        .thenComparingDouble(DynamicTraceSample::timeSeconds))
                .toList();
        StringBuilder csv = new StringBuilder(HEADER).append('\n');
        for (DynamicTraceSample sample : ordered) {
            csv.append(format(sample.timeSeconds())).append(',')
                    .append(quote(sample.deviceId())).append(',')
                    .append(quote(sample.signal())).append(',')
                    .append(format(sample.value())).append(',')
                    .append(quote(sample.unit())).append(',')
                    .append(quote(sample.base())).append('\n');
        }
        Path parent = path.toAbsolutePath().getParent();
        if (parent != null) Files.createDirectories(parent);
        Files.writeString(path, csv, StandardCharsets.UTF_8);
    }

    public static List<DynamicTraceSample> read(Path path) throws IOException {
        List<String> lines = Files.readAllLines(path, StandardCharsets.UTF_8);
        if (lines.isEmpty() || !HEADER.equals(stripBom(lines.get(0)))) {
            throw new IllegalArgumentException("Expected dynamic trace header: " + HEADER);
        }
        List<DynamicTraceSample> samples = new ArrayList<>();
        for (int lineNumber = 2; lineNumber <= lines.size(); lineNumber++) {
            String line = lines.get(lineNumber - 1);
            if (line.isBlank()) continue;
            List<String> fields = parseRow(line);
            if (fields.size() != 6) {
                throw new IllegalArgumentException("Expected 6 fields at " + path + ':' + lineNumber);
            }
            try {
                samples.add(new DynamicTraceSample(
                        Double.parseDouble(fields.get(0)), fields.get(1), fields.get(2),
                        Double.parseDouble(fields.get(3)), fields.get(4), fields.get(5)));
            } catch (RuntimeException ex) {
                throw new IllegalArgumentException("Invalid trace row at " + path + ':' + lineNumber,
                        ex);
            }
        }
        return List.copyOf(samples);
    }

    private static String format(double value) {
        return String.format(Locale.ROOT, "%.17g", value);
    }

    private static String quote(String value) {
        if (value.indexOf(',') < 0 && value.indexOf('"') < 0
                && value.indexOf('\r') < 0 && value.indexOf('\n') < 0) return value;
        return '"' + value.replace("\"", "\"\"") + '"';
    }

    private static List<String> parseRow(String row) {
        List<String> fields = new ArrayList<>();
        StringBuilder field = new StringBuilder();
        boolean quoted = false;
        for (int index = 0; index < row.length(); index++) {
            char ch = row.charAt(index);
            if (ch == '"') {
                if (quoted && index + 1 < row.length() && row.charAt(index + 1) == '"') {
                    field.append('"');
                    index++;
                } else {
                    quoted = !quoted;
                }
            } else if (ch == ',' && !quoted) {
                fields.add(field.toString());
                field.setLength(0);
            } else {
                field.append(ch);
            }
        }
        if (quoted) throw new IllegalArgumentException("Unterminated quoted CSV field");
        fields.add(field.toString());
        return fields;
    }

    private static String stripBom(String value) {
        return value.startsWith("\ufeff") ? value.substring(1) : value;
    }
}
