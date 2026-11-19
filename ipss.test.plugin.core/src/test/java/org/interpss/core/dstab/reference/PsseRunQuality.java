package org.interpss.core.dstab.reference;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

/**
 * Necessary run-quality checks for native PSS/E English logs and time_s CSV
 * exports. This is not a model-parity, provenance, or mathematical convergence
 * proof: callers must additionally verify hashes, inputs, settings and channels.
 * A zero API exit code and an exported CSV do not imply a converged run.
 */
public final class PsseRunQuality {
    private PsseRunQuality() {}

    private static final Pattern INITIALIZED = Pattern.compile(
            "initial\\s+conditions\\s+check\\s+o\\.k\\.", Pattern.CASE_INSENSITIVE);
    private static final Pattern FAILURE = Pattern.compile(
            "network\\s+not\\s+converged|network\\s+(?:solution\\s+)?(?:did\\s+not|failed\\s+to)\\s+converge"
            + "|initial\\s+conditions?\\s+(?:check\\s+)?suspect|suspect\\s+initial\\s+conditions?"
            + "|initial\\s+conditions?\\s+check\\s+failed|traceback\\s*\\(most\\s+recent\\s+call\\s+last\\)",
            Pattern.CASE_INSENSITIVE);

    public record Summary(int samples, int channels, int repeatedTimeSamples,
            int initializationHistorySamples, double finalTime) {}

    public static Summary audit(Path log, Path csv, double endTime, double clockTolerance)
            throws IOException {
        require(Double.isFinite(endTime) && endTime > 0, "Expected end time must be positive and finite");
        require(Double.isFinite(clockTolerance) && clockTolerance >= 0 && clockTolerance < endTime,
                "Clock tolerance must be finite, nonnegative and smaller than end time");
        List<String> logLines = Files.readAllLines(log);
        boolean initialized = false;
        for (int line = 0; line < logLines.size(); line++) {
            String text = logLines.get(line);
            require(!FAILURE.matcher(text).find(), "Native run failure at log line " + (line + 1)
                    + ": " + text.strip());
            initialized |= INITIALIZED.matcher(text).find();
        }
        require(initialized, "Missing native initial-conditions check O.K. (English log required)");

        List<String> lines = Files.readAllLines(csv);
        require(lines.size() >= 2, "Missing native CSV samples");
        String[] headers = lines.get(0).split(",", -1);
        require(headers.length > 1 && headers[0].equals("time_s"), "Expected time_s native CSV header");
        var names = new HashSet<String>();
        for (String header : headers) {
            require(!header.isBlank() && names.add(header.strip().toLowerCase(Locale.ROOT)),
                    "Blank or duplicate native channel name");
        }
        int repeated = 0;
        int history = 0;
        boolean hasZero = false;
        double previous = Double.NEGATIVE_INFINITY;
        for (int row = 1; row < lines.size(); row++) {
            String[] fields = lines.get(row).split(",", -1);
            require(fields.length == headers.length, "Wrong native CSV width at line " + (row + 1));
            double time = Double.NaN;
            for (int column = 0; column < fields.length; column++) {
                double value;
                try {
                    value = Double.parseDouble(fields[column]);
                } catch (NumberFormatException ex) {
                    throw new IllegalArgumentException("Invalid native number at line " + (row + 1)
                            + ", column " + column, ex);
                }
                require(Double.isFinite(value), "Nonfinite native number at line " + (row + 1));
                if (column == 0) time = value;
            }
            require(time >= previous, "Native CSV time decreases at line " + (row + 1));
            if (time == previous) repeated++;
            if (time < 0) history++;
            if (time == 0) hasZero = true;
            previous = time;
        }
        require(hasZero, "Native CSV is missing the time-zero sample");
        require(Math.abs(previous - endTime) <= clockTolerance,
                "Native final time " + previous + " does not reach declared end " + endTime
                        + " within clock tolerance " + clockTolerance);
        return new Summary(lines.size() - 1, headers.length - 1, repeated, history, previous);
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new IllegalArgumentException(message);
    }

    public static void main(String[] args) throws IOException {
        require(args.length == 4, "Usage: PsseRunQuality native.log native.csv end_s clock_tolerance_s");
        System.out.println(audit(Path.of(args[0]), Path.of(args[1]),
                Double.parseDouble(args[2]), Double.parseDouble(args[3])));
    }
}
