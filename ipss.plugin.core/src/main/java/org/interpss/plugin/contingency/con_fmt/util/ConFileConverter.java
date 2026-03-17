package org.interpss.plugin.contingency.con_fmt.util;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Objects;

import org.interpss.plugin.contingency.con_fmt.ConContainer;
import org.interpss.plugin.contingency.con_fmt.bean.ConBranchAction;
import org.interpss.plugin.contingency.con_fmt.parser.ConFileParser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

/**
 * Utility methods for converting a PSS/E contingency definition ({@code .con})
 * into pretty-printed JSON.
 *
 * <h3>Usage</h3>
 * <pre>
 *   mvn -pl ipss.plugin.core compile exec:java \
 *       -Dexec.mainClass="org.interpss.plugin.contingency.con_fmt.util.ConFileConverter" \
 *       -Dexec.args="path/to/input.con [path/to/output.json]"
 * </pre>
 *
 * <p>If the output path is omitted the JSON is written next to the input file
 * with the {@code .con} extension replaced by {@code .json}.
 */
public final class ConFileConverter {

    private static final Logger log = LoggerFactory.getLogger(ConFileConverter.class);
        private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

        private ConFileConverter() {
        }

        public static ConContainer parse(Path inputPath) throws Exception {
                Objects.requireNonNull(inputPath, "inputPath must not be null");
                log.info("Parsing: {}", inputPath.toAbsolutePath());

                ConContainer container = new ConFileParser().parse(inputPath);
                logSummary(container);
                return container;
        }

        public static String toJson(Path inputPath) throws Exception {
                return toJson(parse(inputPath));
        }

        public static String toJson(ConContainer container) {
                Objects.requireNonNull(container, "container must not be null");
                return GSON.toJson(container);
        }

        public static Path writeJson(ConContainer container,Path outputPath) {
                Objects.requireNonNull(container, "container must not be null");
                Objects.requireNonNull(outputPath, "outputPath must not be null");
                try {
                        Files.writeString(outputPath, toJson(container));
                        log.info("JSON written to: {}", outputPath.toAbsolutePath());
                        return outputPath;
                } catch (Exception e) {
                        throw new RuntimeException("Error writing JSON to file: " + outputPath, e);
                }
        }

        public static Path writeJson(Path inputPath) throws Exception {
                return writeJson(inputPath, defaultOutputPath(inputPath));
        }

        public static Path writeJson(Path inputPath, Path outputPath) throws Exception {
                Objects.requireNonNull(inputPath, "inputPath must not be null");
                Objects.requireNonNull(outputPath, "outputPath must not be null");

                Files.writeString(outputPath, toJson(inputPath));
                log.info("JSON written to: {}", outputPath.toAbsolutePath());
                return outputPath;
        }

        public static Path defaultOutputPath(Path inputPath) {
                Objects.requireNonNull(inputPath, "inputPath must not be null");
                return inputPath.resolveSibling(
                                inputPath.getFileName().toString().replaceAll("(?i)\\.con$", "") + ".json");
        }

    public static void main(String[] args) throws Exception {
        if (args.length < 1) {
            System.err.println("Usage: ConFileConverter <input.con> [output.json]");
            System.exit(1);
        }

        Path inputPath = Paths.get(args[0]);
        Path outputPath = args.length >= 2
                ? Paths.get(args[1])
                : defaultOutputPath(inputPath);
        writeJson(inputPath, outputPath);
    }

    private static void logSummary(ConContainer container) {
        log.info("  categories  : {}", container.getCategories().size());
        log.info("  cases       : {}", container.getCases().size());

        long branchOutages = container.getCases().stream()
                .flatMap(c -> c.getBranchEvents().stream())
                .filter(e -> e.getAction() != ConBranchAction.CLOSE)
                .count();
        long busOutages = container.getCases().stream()
                .mapToLong(c -> c.getBusEvents().size())
                .sum();
        long equipEvents = container.getCases().stream()
                .mapToLong(c -> c.getEquipEvents().size())
                .sum();
        long busModEvents = container.getCases().stream()
                .mapToLong(c -> c.getBusModEvents().size())
                .sum();
        long equipMoveEvents = container.getCases().stream()
                .mapToLong(c -> c.getEquipMoveEvents().size())
                .sum();
        log.info("  branch outage events : {}", branchOutages);
        log.info("  bus outage events    : {}", busOutages);
        log.info("  equip remove/add     : {}", equipEvents);
        log.info("  bus mod events       : {}", busModEvents);
        log.info("  equip move events    : {}", equipMoveEvents);
    }
}
