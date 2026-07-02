package org.interpss.plugin.contingency.aux_fmt.parser;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.interpss.plugin.contingency.aux_fmt.bean.AuxContingency;
import org.interpss.plugin.contingency.aux_fmt.bean.AuxCtgElement;
import org.interpss.plugin.contingency.aux_fmt.bean.AuxParsedData;

public class AuxContingencyParser {
    private static final Pattern DATA_HEADER = Pattern.compile("(?i)^\\s*DATA\\s*\\((.*)\\)\\s*$");
    private static final Pattern OBJECT_HEADER =
            Pattern.compile("(?i)^\\s*([A-Za-z][A-Za-z0-9_]*)\\s*\\((.*)\\)\\s*$");
    private static final Pattern HEADER_START =
            Pattern.compile("(?i)^\\s*(DATA\\s*)?[A-Za-z][A-Za-z0-9_]*\\s*\\(.*$");

    public AuxParsedData parse(File file) throws IOException {
        List<String> lines = Files.readAllLines(file.toPath(), StandardCharsets.UTF_8);
        List<AuxContingency> contingencies = new ArrayList<>();
        List<AuxCtgElement> ctgElements = new ArrayList<>();

        Block activeBlock = null;
        StringBuilder pendingHeader = null;
        int pendingHeaderLineNumber = 0;
        for (int i = 0; i < lines.size(); i++) {
            int lineNumber = i + 1;
            String rawLine = lines.get(i);
            String line = stripComment(rawLine).trim();
            if (line.isEmpty()) {
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
            if (line.endsWith(";")) {
                line = line.substring(0, line.length() - 1).trim();
            }
            if (line.endsWith(",")) {
                line = line.substring(0, line.length() - 1).trim();
            }
            if (line.isEmpty()) {
                continue;
            }

            Map<String, String> fields = mapFields(activeBlock.fields, tokenize(line));
            if ("contingency".equals(activeBlock.objectType)) {
                String name = firstPresent(fields, "name", "contingency", "ctgname", "ctglabel", "contingencyname");
                if (name == null || name.isBlank()) {
                    throw new IOException("Missing contingency name at line " + lineNumber);
                }
                contingencies.add(new AuxContingency(name, lineNumber));
            } else if ("ctgelement".equals(activeBlock.objectType)) {
                ctgElements.add(new AuxCtgElement(fields, lineNumber));
            }
        }

        return new AuxParsedData(contingencies, ctgElements);
    }

    private static Block parseHeader(String header, int lineNumber) throws IOException {
        Matcher dataMatcher = DATA_HEADER.matcher(header);
        if (dataMatcher.matches()) {
            return parseDataHeader(dataMatcher.group(1), lineNumber);
        }

        Matcher objectMatcher = OBJECT_HEADER.matcher(header);
        if (objectMatcher.matches()) {
            return new Block(
                    normalizeObjectType(objectMatcher.group(1)),
                    normalizeFields(tokenize(objectMatcher.group(2))));
        }

        throw new IOException("Unsupported AUX object header at line " + lineNumber + ": " + header);
    }

    private static Block parseDataHeader(String header, int lineNumber) throws IOException {
        int commaIndex = header.indexOf(',');
        String objectType = normalizeObjectType(commaIndex >= 0 ? header.substring(0, commaIndex) : header);
        int start = header.indexOf('[');
        int end = header.lastIndexOf(']');
        if (start < 0 || end <= start) {
            throw new IOException("Missing AUX DATA field list at line " + lineNumber);
        }
        List<String> fields = tokenize(header.substring(start + 1, end));
        return new Block(objectType, normalizeFields(fields));
    }

    private static List<String> normalizeFields(List<String> fields) {
        List<String> normalizedFields = new ArrayList<>();
        for (String field : fields) {
            normalizedFields.add(AuxCtgElement.normalize(field));
        }
        return normalizedFields;
    }

    private static String normalizeObjectType(String objectType) {
        String normalized = AuxCtgElement.normalize(objectType);
        if ("contingencyelement".equals(normalized)) {
            return "ctgelement";
        }
        return normalized;
    }

    private static Map<String, String> mapFields(List<String> fields, List<String> values) {
        Map<String, String> mapped = new LinkedHashMap<>();
        for (int i = 0; i < fields.size(); i++) {
            String value = i < values.size() ? values.get(i) : "";
            mapped.put(fields.get(i), value);
        }
        return mapped;
    }

    private static List<String> tokenize(String line) {
        List<String> tokens = new ArrayList<>();
        StringBuilder token = new StringBuilder();
        boolean inQuote = false;
        boolean tokenStarted = false;
        for (int i = 0; i < line.length(); i++) {
            char ch = line.charAt(i);
            if (ch == '"') {
                if (inQuote && i + 1 < line.length() && line.charAt(i + 1) == '"') {
                    token.append('"');
                    tokenStarted = true;
                    i++;
                } else {
                    inQuote = !inQuote;
                    tokenStarted = true;
                }
            } else if (!inQuote && (ch == ',' || Character.isWhitespace(ch))) {
                if (tokenStarted || ch == ',') {
                    tokens.add(token.toString().trim());
                    token.setLength(0);
                    tokenStarted = false;
                }
            } else {
                token.append(ch);
                tokenStarted = true;
            }
        }
        if (tokenStarted) {
            tokens.add(token.toString().trim());
        }
        return tokens;
    }

    private static String firstPresent(Map<String, String> fields, String... names) {
        for (String name : names) {
            String value = fields.get(AuxCtgElement.normalize(name));
            if (value != null && !value.isBlank()) {
                return value;
            }
        }
        return null;
    }

    private static String stripComment(String line) {
        int index = line.indexOf("//");
        return index >= 0 ? line.substring(0, index) : line;
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
