package org.interpss.plugin.fstate.aux_fmt.parser;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.interpss.plugin.aux_fmt.util.AuxParseUtil;
import org.interpss.plugin.fstate.aux_fmt.bean.AuxSchedPoint;
import org.interpss.plugin.fstate.aux_fmt.bean.AuxTssParsedData;
import org.interpss.plugin.fstate.aux_fmt.bean.AuxTssSchedule;
import org.interpss.plugin.fstate.aux_fmt.bean.AuxTssScheduleSub;

/**
 * Parses PowerWorld TSS schedule AUX files containing {@code TSSchedule} and
 * {@code TSScheduleSub} blocks with {@code SchedPoint} SUBDATA.
 */
public class AuxTssScheduleAuxParser {

    private static final Pattern OBJECT_HEADER =
            Pattern.compile("(?i)^\\s*([A-Za-z][A-Za-z0-9_]*)\\s*\\((.*)\\)\\s*$");
    private static final Pattern HEADER_START =
            Pattern.compile("(?i)^\\s*[A-Za-z][A-Za-z0-9_]*\\s*\\(.*$");
    private static final Pattern SUBDATA_START =
            Pattern.compile("(?i)^\\s*<\\s*SUBDATA\\s+([A-Za-z][A-Za-z0-9_]*)\\s*>\\s*,?\\s*$");
    private static final Pattern SUBDATA_END =
            Pattern.compile("(?i)^\\s*</\\s*SUBDATA\\s*>\\s*,?\\s*$");

    public AuxTssParsedData parse(File file) throws IOException {
        return parse(file.toPath());
    }

    public AuxTssParsedData parse(Path path) throws IOException {
        List<String> lines = Files.readAllLines(path, StandardCharsets.UTF_8);
        List<AuxTssSchedule> schedules = new ArrayList<>();
        List<AuxTssScheduleSub> subscriptions = new ArrayList<>();

        Block activeBlock = null;
        StringBuilder pendingHeader = null;
        int pendingHeaderLineNumber = 0;

        AuxTssSchedule pendingSchedule = null;
        List<AuxSchedPoint> pendingPoints = null;
        boolean inSubdata = false;

        for (int i = 0; i < lines.size(); i++) {
            int lineNumber = i + 1;
            String rawLine = lines.get(i);
            String line = AuxParseUtil.stripComment(rawLine).trim();
            if (line.isEmpty()) {
                continue;
            }

            if (inSubdata) {
                if (SUBDATA_END.matcher(line).matches()) {
                    if (pendingSchedule != null && pendingPoints != null) {
                        schedules.add(new AuxTssSchedule(
                                pendingSchedule.scheduleName(),
                                pendingSchedule.valueType(),
                                List.copyOf(pendingPoints),
                                pendingSchedule.lineNumber()));
                        pendingSchedule = null;
                        pendingPoints = null;
                    }
                    inSubdata = false;
                    continue;
                }
                pendingPoints.add(parseSchedPoint(line, lineNumber));
                continue;
            }

            if (activeBlock == null) {
                if (pendingHeader != null) {
                    pendingHeader.append(' ').append(line);
                    if (line.contains(")")) {
                        activeBlock = parseHeader(pendingHeader.toString(), pendingHeaderLineNumber);
                        pendingHeader = null;
                    }
                    continue;
                }

                if (HEADER_START.matcher(line).matches()) {
                    if (line.contains(")")) {
                        activeBlock = parseHeader(line, lineNumber);
                    } else {
                        pendingHeader = new StringBuilder(line);
                        pendingHeaderLineNumber = lineNumber;
                    }
                }
                continue;
            }

            if ("{".equals(line)) {
                continue;
            }
            if ("}".equals(line)) {
                activeBlock = null;
                continue;
            }

            if (SUBDATA_START.matcher(line).matches()) {
                if (pendingSchedule == null) {
                    throw new IOException("SUBDATA without schedule header at line " + lineNumber);
                }
                inSubdata = true;
                pendingPoints = new ArrayList<>();
                continue;
            }

            line = AuxParseUtil.trimTrailingComma(line);
            if (line.isEmpty()) {
                continue;
            }

            if ("tsschedule".equals(activeBlock.objectType)) {
                pendingSchedule = parseScheduleHeader(line, lineNumber);
            } else if ("tsschedulesub".equals(activeBlock.objectType)) {
                subscriptions.add(parseScheduleSub(activeBlock.fields, line, lineNumber));
            }
        }

        return new AuxTssParsedData(schedules, subscriptions);
    }

    private static AuxTssSchedule parseScheduleHeader(String line, int lineNumber) throws IOException {
        List<String> tokens = AuxParseUtil.tokenize(line);
        if (tokens.isEmpty()) {
            throw new IOException("Empty TSSchedule row at line " + lineNumber);
        }
        String scheduleName = tokens.get(0);
        String valueType = tokens.size() > 1 ? tokens.get(1) : "Numeric";
        return new AuxTssSchedule(scheduleName, valueType, List.of(), lineNumber);
    }

    private static AuxTssScheduleSub parseScheduleSub(List<String> fields, String line, int lineNumber)
            throws IOException {
        Map<String, String> mapped = AuxParseUtil.mapFields(fields, AuxParseUtil.tokenize(line));
        String objectType = AuxParseUtil.firstPresent(mapped, "objecttype");
        String objectIdentifier = AuxParseUtil.firstPresent(mapped, "objectidentifier");
        String objectField = AuxParseUtil.firstPresent(mapped, "objectfield");
        String scheduleName = AuxParseUtil.firstPresent(mapped, "schedulename");
        if (scheduleName == null || scheduleName.isBlank()) {
            throw new IOException("Missing ScheduleName in TSScheduleSub at line " + lineNumber);
        }
        return new AuxTssScheduleSub(objectType, objectIdentifier, objectField, scheduleName, lineNumber);
    }

    private static AuxSchedPoint parseSchedPoint(String line, int lineNumber) throws IOException {
        List<String> tokens = AuxParseUtil.tokenize(AuxParseUtil.stripComment(line).trim());
        if (tokens.size() < 6) {
            throw new IOException("Invalid SchedPoint row at line " + lineNumber + ": " + line);
        }

        int pointType;
        double nValue;
        String bValue;
        try {
            pointType = Integer.parseInt(tokens.get(tokens.size() - 5));
            nValue = Double.parseDouble(tokens.get(tokens.size() - 4));
            bValue = tokens.get(tokens.size() - 3);
        } catch (NumberFormatException ex) {
            throw new IOException("Invalid SchedPoint numeric fields at line " + lineNumber + ": " + line, ex);
        }

        String dateTimeLine = String.join(" ", tokens.subList(0, tokens.size() - 5));
        return new AuxSchedPoint(
                AuxDateTimeParser.parseSchedulePointLine(dateTimeLine, lineNumber),
                pointType,
                nValue,
                bValue);
    }

    private static Block parseHeader(String header, int lineNumber) throws IOException {
        Matcher objectMatcher = OBJECT_HEADER.matcher(header);
        if (objectMatcher.matches()) {
            return new Block(
                    AuxParseUtil.normalize(objectMatcher.group(1)),
                    normalizeFields(AuxParseUtil.tokenize(objectMatcher.group(2))));
        }
        throw new IOException("Unsupported AUX object header at line " + lineNumber + ": " + header);
    }

    private static List<String> normalizeFields(List<String> fields) {
        List<String> normalizedFields = new ArrayList<>();
        for (String field : fields) {
            normalizedFields.add(AuxParseUtil.normalize(field));
        }
        return normalizedFields;
    }

    private static final class Block {
        private final String objectType;
        private final List<String> fields;

        private Block(String objectType, List<String> fields) {
            this.objectType = objectType;
            this.fields = fields;
        }
    }
}
