package org.interpss.plugin.monitor;

import java.nio.file.Path;
import java.nio.file.Paths;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

/**
 * Converts a PSS/E Monitored Element (.mon) file to JSON.
 *
 * Usage:
 * <pre>
 *   mvn compile exec:java -Dexec.mainClass="org.interpss.plugin.monitor.MonFileConverter" \
 *       -Dexec.args="path/to/input.mon path/to/output.json"
 * </pre>
 */
public class MonFileConverter {

    private static final Logger log = LoggerFactory.getLogger(MonFileConverter.class);

    public static void main(String[] args) throws Exception {
        if (args.length < 1) {
            System.err.println("Usage: MonFileConverter <input.mon> [output.json]");
            System.exit(1);
        }

        Path inputPath  = Paths.get(args[0]);
        Path outputPath = args.length >= 2
                ? Paths.get(args[1])
                : inputPath.resolveSibling(
                        inputPath.getFileName().toString().replaceAll("\\.mon$", "") + ".json");

        log.info("Parsing: {}", inputPath.toAbsolutePath());

        MonElementContainer monFile = new MonFileParser().parse(inputPath);

        log.info("  monitored flow directives          : {}", monFile.getMonitoredFlowDirectives().size());
        log.info("  monitored bus voltage directives   : {}", monFile.getMonitoredBusVoltageDirectives().size());
        log.info("  interfaces                         : {}", monFile.getInterfaces().size());
        for (MonInterface iface : monFile.getInterfaces()) {
            log.info(String.format("    %-30s  rating=%7.1f MW  branches=%d",
                    "'" + iface.getId() + "'", iface.getRatingMW(), iface.getBranches().size()));
        }

        Gson gson = new GsonBuilder().setPrettyPrinting().create();
        java.nio.file.Files.writeString(outputPath, gson.toJson(monFile));
        log.info("JSON written to: {}", outputPath.toAbsolutePath());
    }
}
