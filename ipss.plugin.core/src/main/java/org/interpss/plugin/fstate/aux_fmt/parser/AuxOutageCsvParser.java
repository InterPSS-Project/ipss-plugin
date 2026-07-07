package org.interpss.plugin.fstate.aux_fmt.parser;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import org.interpss.plugin.fstate.aux_fmt.bean.AuxOutageRecord;

/**
 * Parses PowerWorld scheduled-outage PWCSV files.
 */
public class AuxOutageCsvParser {

    public List<AuxOutageRecord> parse(Path path) throws IOException {
        List<String> lines = Files.readAllLines(path, StandardCharsets.UTF_8);
        if (lines.isEmpty()) {
            throw new IOException("Empty outages CSV: " + path);
        }

        List<AuxOutageRecord> outages = new ArrayList<>();
        for (int i = 1; i < lines.size(); i++) {
            int lineNumber = i + 1;
            String line = lines.get(i).trim();
            if (line.isEmpty()) {
                continue;
            }
            String[] columns = line.split(",", -1);
            if (columns.length < 6) {
                throw new IOException("Invalid outages CSV row at line " + lineNumber + ": " + line);
            }
            String branchLabel = columns[2].trim();
            outages.add(new AuxOutageRecord(
                    branchLabel,
                    AuxDateTimeParser.parseOutageDateTime(columns[4].trim(), lineNumber),
                    AuxDateTimeParser.parseOutageDateTime(columns[5].trim(), lineNumber)));
        }
        return outages;
    }
}
