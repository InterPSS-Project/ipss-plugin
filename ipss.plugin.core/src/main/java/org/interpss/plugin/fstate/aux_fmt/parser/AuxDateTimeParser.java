package org.interpss.plugin.fstate.aux_fmt.parser;

import java.io.IOException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeFormatterBuilder;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.Locale;

import org.interpss.plugin.aux_fmt.util.AuxParseUtil;

/**
 * Parses PowerWorld display date/time strings from AUX and CSV files.
 */
public final class AuxDateTimeParser {

    private static final DateTimeFormatter SCHEDULE_DATE_TIME = new DateTimeFormatterBuilder()
            .parseCaseInsensitive()
            .appendPattern("MM/dd/yyyy hh:mm:ss a")
            .toFormatter(Locale.US);

    private static final DateTimeFormatter OUTAGE_DATE_TIME = DateTimeFormatter.ofPattern(
            "MM/dd/yyyy HH:mm", Locale.US);

    private AuxDateTimeParser() {
    }

    public static LocalDateTime parseSchedulePointLine(String line, int lineNumber) throws IOException {
        List<String> tokens = AuxParseUtil.tokenize(AuxParseUtil.stripComment(line).trim());
        if (tokens.size() < 3) {
            throw new IOException("Invalid SchedPoint row at line " + lineNumber + ": " + line);
        }

        String datePart = tokens.get(0);
        StringBuilder timePart = new StringBuilder(tokens.get(1));
        int index = 2;
        if (index < tokens.size() && isAmPm(tokens.get(index))) {
            timePart.append(' ').append(tokens.get(index));
        }

        String text = datePart + " " + timePart;
        try {
            return LocalDateTime.parse(text, SCHEDULE_DATE_TIME);
        } catch (DateTimeParseException ex) {
            throw new IOException("Invalid SchedPoint date/time at line " + lineNumber + ": " + text, ex);
        }
    }

    public static LocalDateTime parseOutageDateTime(String text, int lineNumber) throws IOException {
        try {
            return LocalDateTime.parse(text.trim(), OUTAGE_DATE_TIME);
        } catch (DateTimeParseException ex) {
            throw new IOException("Invalid outage date/time at line " + lineNumber + ": " + text, ex);
        }
    }

    private static boolean isAmPm(String token) {
        String normalized = token.trim().toUpperCase(Locale.ROOT);
        return "AM".equals(normalized) || "PM".equals(normalized);
    }
}
