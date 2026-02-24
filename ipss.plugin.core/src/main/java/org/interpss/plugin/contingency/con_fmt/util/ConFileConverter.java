package org.interpss.plugin.contingency.con_fmt.util;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import org.interpss.plugin.contingency.con_fmt.ConContainer;
import org.interpss.plugin.contingency.con_fmt.bean.ConBranchAction;
import org.interpss.plugin.contingency.con_fmt.parser.ConFileParser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

/**
 * CLI utility that converts a PSS/E Contingency Definition ({@code .con}) file
 * to a pretty-printed JSON file.
 *
 * <h3>Usage</h3>
 * <pre>
 *   mvn -pl ipss.plugin.core compile exec:java \
 *       -Dexec.mainClass="org.interpss.plugin.contingency.parser.ConFileConverter" \
 *       -Dexec.args="path/to/input.con [path/to/output.json]"
 * </pre>
 *
 * <p>If the output path is omitted the JSON is written next to the input file
 * with the {@code .con} extension replaced by {@code .json}.
 */
public class ConFileConverter {

    private static final Logger log = LoggerFactory.getLogger(ConFileConverter.class);

    public static void main(String[] args) throws Exception {
        if (args.length < 1) {
            System.err.println("Usage: ConFileConverter <input.con> [output.json]");
            System.exit(1);
        }

        Path inputPath = Paths.get(args[0]);
        Path outputPath = args.length >= 2
                ? Paths.get(args[1])
                : inputPath.resolveSibling(
                        inputPath.getFileName().toString().replaceAll("(?i)\\.con$", "") + ".json");

        log.info("Parsing: {}", inputPath.toAbsolutePath());

        ConContainer container = new ConFileParser().parse(inputPath);

        log.info("  categories  : {}", container.getCategories().size());
        log.info("  cases       : {}", container.getCases().size());

        // summary by event type counts
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

        Gson gson = new GsonBuilder().setPrettyPrinting().create();
        Files.writeString(outputPath, gson.toJson(container));
        log.info("JSON written to: {}", outputPath.toAbsolutePath());
    }
}
