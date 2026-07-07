package org.interpss.plugin.fstate.aux_fmt.parser;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;

import org.interpss.plugin.fstate.aux_fmt.bean.AuxTimepointsHorizon;

/**
 * Parses PowerWorld TSS timepoints CSV files.
 */
public class AuxTimepointsCsvParser {

    private static final DateTimeFormatter ISO_FORMAT = DateTimeFormatter.ISO_LOCAL_DATE_TIME;

    public AuxTimepointsHorizon parse(Path path) throws IOException {
        List<String> lines = Files.readAllLines(path, StandardCharsets.UTF_8);
        if (lines.isEmpty()) {
            throw new IOException("Empty timepoints CSV: " + path);
        }

        List<LocalDateTime> times = new ArrayList<>();
        for (int i = 1; i < lines.size(); i++) {
            String line = lines.get(i).trim();
            if (line.isEmpty()) {
                continue;
            }
            String[] parts = line.split(",", 2);
            if (parts.length < 2) {
                throw new IOException("Invalid timepoints CSV row at line " + (i + 1) + ": " + line);
            }
            String[] columns = line.split(",", -1);
            if (columns.length < 2) {
                throw new IOException("Invalid timepoints CSV row at line " + (i + 1) + ": " + line);
            }
            times.add(LocalDateTime.parse(columns[1].trim(), ISO_FORMAT));
        }

        if (times.isEmpty()) {
            throw new IOException("No timepoints found in CSV: " + path);
        }

        int intervalMin = 15;
        if (times.size() > 1) {
            intervalMin = (int) ChronoUnit.MINUTES.between(times.get(0), times.get(1));
            if (intervalMin <= 0) {
                throw new IOException("Unable to infer interval from timepoints CSV: " + path);
            }
        }

        return new AuxTimepointsHorizon(times.size(), times.get(0), intervalMin, List.copyOf(times));
    }
}
