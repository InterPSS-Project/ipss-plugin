package org.interpss.plugin.subsystem;

import java.io.FileWriter;
import java.nio.file.Path;
import java.nio.file.Paths;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

/**
 * Converts a PSS/E Subsystem Definition (.sub) file to JSON.
 *
 * Usage:
 *   mvn compile exec:java -Dexec.mainClass="org.interpss.plugin.subsystem.SubFileConverter" \
 *       -Dexec.args="path/to/input.sub path/to/output.json"
 *
 * If no output path is provided, output is written next to the input file
 * with the same name but a .json extension.
 */
public class SubFileConverter {

    private static final Logger log = LoggerFactory.getLogger(SubFileConverter.class);

    public static void main(String[] args) throws Exception {
        if (args.length < 1) {
            System.err.println("Usage: SubFileConverter <input.sub> [output.json]");
            System.exit(1);
        }

        Path inputPath  = Paths.get(args[0]);
        Path outputPath = args.length >= 2
                ? Paths.get(args[1])
                : inputPath.resolveSibling(
                        inputPath.getFileName().toString().replaceAll("\\.sub$", "") + ".json");

        log.info("Parsing: {}", inputPath.toAbsolutePath());

        SubsystemContainer subFile = new SubFileParser().parse(inputPath);

        log.info("Parsed {} subsystems:", subFile.subsystems().size());
        for (Subsystem s : subFile.subsystems()) {
            int joinCount   = s.getJoinGroups().size();
            int directAreas = s.getDirectSelection().getAreas().size();
            int skipCount   = s.getSkipBuses().size();
            log.info(String.format("  %-15s  joinGroups=%d  directAreas=%d  skipBuses=%d",
                    "'" + s.getLabel() + "'", joinCount, directAreas, skipCount));
        }

        Gson gson = new GsonBuilder().setPrettyPrinting().create();
        try (FileWriter writer = new FileWriter(outputPath.toFile())) {
            gson.toJson(subFile, writer);
        }
        log.info("JSON written to: {}", outputPath.toAbsolutePath());
    }
}
