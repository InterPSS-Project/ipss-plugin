package org.interpss.fadapter.psse.dyr;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/** Location-preserving reader and tokenizer for PSS/E DYR records. */
public final class PsseDyrRecordReader {
    private PsseDyrRecordReader() {
    }

    public static List<PsseDyrRecord> read(Path path) throws IOException {
        try (BufferedReader reader = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
            return read(reader, path.toString());
        }
    }

    public static List<PsseDyrRecord> read(Reader sourceReader, String sourceName) throws IOException {
        BufferedReader reader = sourceReader instanceof BufferedReader buffered
                ? buffered : new BufferedReader(sourceReader);
        List<PsseDyrRecord> records = new ArrayList<>();
        StringBuilder pending = new StringBuilder();
        int startLine = -1;
        int lineNo = 0;
        String line;
        while ((line = reader.readLine()) != null) {
            lineNo++;
            if (lineNo == 1 && !line.isEmpty() && line.charAt(0) == '\uFEFF') {
                line = line.substring(1);
            }
            String useful = stripDoubleSlashComment(line);
            if (pending.isEmpty() && isCommentOrBlank(useful)) continue;
            if (startLine < 0) startLine = lineNo;
            if (!pending.isEmpty()) pending.append(' ');
            pending.append(useful.trim());

            int slash;
            while ((slash = indexOfUnquotedSlash(pending)) >= 0) {
                String raw = pending.substring(0, slash).trim();
                pending.delete(0, slash + 1);
                if (!raw.isEmpty()) records.add(toRecord(raw, sourceName, startLine, lineNo));
                String remainder = pending.toString().trim();
                pending.setLength(0);
                if (startsWithBusNumber(remainder)) {
                    pending.append(remainder);
                    startLine = lineNo;
                } else {
                    startLine = -1;
                }
            }
        }
        if (!pending.toString().trim().isEmpty()) {
            throw new IOException("Unterminated DYR record at " + sourceName + ":" + startLine);
        }
        return List.copyOf(records);
    }

    public static List<String> tokenize(String recordText) {
        List<String> fields = new ArrayList<>();
        StringBuilder field = new StringBuilder();
        boolean quoted = false;
        for (int i = 0; i < recordText.length(); i++) {
            char c = recordText.charAt(i);
            if (c == '\'') {
                if (quoted && i + 1 < recordText.length() && recordText.charAt(i + 1) == '\'') {
                    field.append('\'');
                    i++;
                } else {
                    quoted = !quoted;
                }
            } else if (!quoted && (c == ',' || Character.isWhitespace(c))) {
                if (!field.isEmpty()) {
                    fields.add(field.toString());
                    field.setLength(0);
                }
            } else {
                field.append(c);
            }
        }
        if (quoted) throw new IllegalArgumentException("Unterminated quoted DYR field: " + recordText);
        if (!field.isEmpty()) fields.add(field.toString());
        return List.copyOf(fields);
    }

    private static PsseDyrRecord toRecord(String raw, String source, int startLine, int endLine) {
        List<String> fields = tokenize(raw);
        if (fields.size() < 3) {
            throw new IllegalArgumentException("DYR record has fewer than three fields at "
                    + source + ":" + startLine);
        }
        int bus;
        try {
            bus = Math.abs(Integer.parseInt(fields.get(0)));
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("Invalid DYR bus number at " + source + ":" + startLine, e);
        }
        String wrapper = fields.get(1).toUpperCase(java.util.Locale.ROOT);
        boolean idlessUserModel = wrapper.equals("USRMDL") && fields.size() > 2
                && DynamicModelCatalog.canonicalName(fields.get(2)).equals("WT12A1B");
        boolean userDeviceModel = (wrapper.equals("USRLOD") || wrapper.equals("USRMDL"))
                && !idlessUserModel;
        boolean userBusModel = wrapper.equals("USRBUS");
        int modelIndex = userDeviceModel ? 3 : userBusModel || idlessUserModel ? 2 : 1;
        if (fields.size() <= modelIndex) {
            throw new IllegalArgumentException("Missing user-model name at " + source + ":" + startLine);
        }
        String sourceModel = fields.get(modelIndex);
        String deviceId = userBusModel || idlessUserModel ? "*" : fields.get(2);
        int parameterOffset = userDeviceModel ? 4 : 3;
        return new PsseDyrRecord(source, startLine, endLine, raw, bus, sourceModel,
                DynamicModelCatalog.canonicalName(sourceModel), deviceId, fields,
                parameterOffset);
    }

    private static boolean isCommentOrBlank(CharSequence line) {
        String value = line.toString().trim();
        return value.isEmpty() || value.startsWith("/");
    }

    /**
     * A second record may follow a slash on the same line. Legacy PSS/E files
     * also commonly put an unmarked comment there, so retain the remainder only
     * when it starts with a signed decimal bus number.
     */
    private static boolean startsWithBusNumber(String value) {
        if (value.isEmpty() || isCommentOrBlank(value)) return false;
        int index = value.charAt(0) == '+' || value.charAt(0) == '-' ? 1 : 0;
        int firstDigit = index;
        while (index < value.length() && Character.isDigit(value.charAt(index))) index++;
        return index > firstDigit && (index == value.length()
                || Character.isWhitespace(value.charAt(index)) || value.charAt(index) == ',');
    }

    private static String stripDoubleSlashComment(String line) {
        boolean quoted = false;
        for (int i = 0; i + 1 < line.length(); i++) {
            char c = line.charAt(i);
            if (c == '\'') quoted = !quoted;
            if (!quoted && c == '/' && line.charAt(i + 1) == '/') return line.substring(0, i);
        }
        return line;
    }

    private static int indexOfUnquotedSlash(CharSequence text) {
        boolean quoted = false;
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (c == '\'') quoted = !quoted;
            else if (!quoted && c == '/') return i;
        }
        return -1;
    }
}
