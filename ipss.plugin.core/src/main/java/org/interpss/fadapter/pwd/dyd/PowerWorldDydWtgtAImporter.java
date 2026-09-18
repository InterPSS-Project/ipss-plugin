package org.interpss.fadapter.pwd.dyd;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.charset.Charset;
import java.nio.charset.MalformedInputException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.interpss.dstab.renewable.Wtdta1Model;
import org.interpss.dstab.renewable.WtgtAData;
import org.interpss.fadapter.builder.DStabNetworkBuilder;
import org.interpss.fadapter.psse.PsseGnetIdvProcessor.GeneratorKey;

import com.interpss.common.exp.InterpssException;

/** Reads and attaches PowerWorld {@code WTGT_A} records from a DYD source. */
public final class PowerWorldDydWtgtAImporter {
    private static final Pattern TOKEN = Pattern.compile("\"([^\"]*)\"|\\S+");
    private static final String BUS_PREFIX = "Bus";

    public Result importFile(Path source, DStabNetworkBuilder builder,
            Collection<GeneratorKey> gnetRemoved,
            Collection<GeneratorKey> modelRemoved) throws InterpssException {
        List<Record> records;
        try {
            records = read(source);
        } catch (IOException | IllegalArgumentException e) {
            throw new InterpssException("Invalid PowerWorld DYD file " + source + ": "
                    + e.getMessage());
        }
        Collection<GeneratorKey> gnet = gnetRemoved == null ? List.of() : gnetRemoved;
        Collection<GeneratorKey> removed = modelRemoved == null ? List.of() : modelRemoved;
        List<Entry> entries = new ArrayList<>();
        for (Record record : records) {
            GeneratorKey key = new GeneratorKey(BUS_PREFIX + record.busNumber(),
                    record.deviceId());
            if (gnet.contains(key)) {
                entries.add(new Entry(record, Status.SKIPPED_GNET,
                        "generator intentionally removed by GNET preprocessing"));
                continue;
            }
            if (removed.contains(key)) {
                entries.add(new Entry(record, Status.SKIPPED_MODEL_REMOVE,
                        "dynamic stack intentionally removed by BAT_PLMOD_REMOVE type 1"));
                continue;
            }
            try {
                Wtdta1Model attached = builder.addWtgtA(key.busId(), key.generatorId(),
                        record.data());
                entries.add(new Entry(record,
                        attached == null ? Status.REJECTED : Status.ATTACHED,
                        attached == null
                                ? "REECA1 target is missing or already has a drive train" : ""));
            } catch (RuntimeException e) {
                entries.add(new Entry(record, Status.ERROR, e.getMessage()));
            }
        }
        return new Result(source.toString(), entries);
    }

    /** Location-preserving reader for the WTGT_A subset of PowerWorld DYD. */
    public static List<Record> read(Path source) throws IOException {
        List<String> lines = readLines(source);
        List<Record> records = new ArrayList<>();
        for (int index = 0; index < lines.size(); index++) {
            String line = lines.get(index).trim();
            if (line.isEmpty() || line.startsWith("#")) continue;
            List<String> tokens = tokenize(line);
            if (tokens.isEmpty() || !tokens.get(0).equalsIgnoreCase("WTGT_A")) continue;
            int lineNumber = index + 1;
            if (tokens.size() != 13 || !tokens.get(5).equals(":")
                    || !tokens.get(6).equalsIgnoreCase("#9")) {
                throw new IllegalArgumentException("Malformed WTGT_A record at " + source
                        + ":" + lineNumber + "; expected six parameters after ': #9'");
            }
            int bus = parseBus(tokens.get(1), source, lineNumber);
            WtgtAData data = new WtgtAData(
                    parseDouble(tokens.get(8), "Ht", source, lineNumber),
                    parseDouble(tokens.get(9), "Hg", source, lineNumber),
                    parseDouble(tokens.get(10), "DShaft", source, lineNumber),
                    parseDouble(tokens.get(11), "KShaft", source, lineNumber),
                    parseDouble(tokens.get(7), "MWCap", source, lineNumber),
                    parseDouble(tokens.get(12), "W0", source, lineNumber));
            records.add(new Record(source.toString(), lineNumber, line, bus,
                    tokens.get(4), data));
        }
        return List.copyOf(records);
    }

    private static List<String> readLines(Path source) throws IOException {
        try {
            return Files.readAllLines(source, StandardCharsets.UTF_8);
        } catch (MalformedInputException e) {
            // PowerWorld DYD exports commonly retain Windows-1252 comments and names.
            return Files.readAllLines(source, Charset.forName("windows-1252"));
        }
    }

    private static List<String> tokenize(String line) {
        List<String> tokens = new ArrayList<>();
        Matcher matcher = TOKEN.matcher(line);
        while (matcher.find()) {
            tokens.add(matcher.group(1) == null ? matcher.group() : matcher.group(1));
        }
        return tokens;
    }

    private static int parseBus(String value, Path source, int line) {
        try {
            return Math.abs(Integer.parseInt(value));
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("Invalid WTGT_A bus number at " + source
                    + ":" + line, e);
        }
    }

    private static double parseDouble(String value, String field, Path source, int line) {
        try {
            return Double.parseDouble(value);
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("Invalid WTGT_A " + field + " at " + source
                    + ":" + line + ": " + value, e);
        }
    }

    public enum Status {
        ATTACHED,
        SKIPPED_GNET,
        SKIPPED_MODEL_REMOVE,
        REJECTED,
        ERROR
    }

    public record Record(String source, int lineNumber, String rawText,
            int busNumber, String deviceId, WtgtAData data) { }

    public record Entry(Record record, Status status, String message) { }

    public static final class Result {
        private final String source;
        private final List<Entry> entries;
        private final Map<Status, Integer> counts;

        private Result(String source, List<Entry> entries) {
            this.source = source;
            this.entries = List.copyOf(entries);
            EnumMap<Status, Integer> collected = new EnumMap<>(Status.class);
            for (Entry entry : entries) collected.merge(entry.status(), 1, Integer::sum);
            this.counts = Map.copyOf(collected);
        }

        public static Result empty() { return new Result("", List.of()); }
        public String source() { return source; }
        public List<Entry> entries() { return entries; }
        public int totalRecordCount() { return entries.size(); }
        public int count(Status status) { return counts.getOrDefault(status, 0); }
        public boolean isStrictlyComplete() {
            return totalRecordCount() > 0
                    && count(Status.ATTACHED) + count(Status.SKIPPED_GNET)
                    + count(Status.SKIPPED_MODEL_REMOVE) == totalRecordCount();
        }
    }
}
