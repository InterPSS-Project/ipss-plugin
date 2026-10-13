package org.interpss.test;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;

/** Keeps configuration changes in test fixtures instead of JVM-wide state. */
class RepositoryTestConfigurationTest {
    private static final Pattern GLOBAL_SETTING = Pattern.compile(
            "\\bSystem\\s*(?:\\.\\s*|::\\s*)(?:getProperty|setProperty|clearProperty|getProperties|setProperties)\\b"
            + "|\\b(?:Boolean|Integer|Long)\\s*(?:\\.\\s*|::\\s*)(?:getBoolean|getInteger|getLong)\\b"
            + "|import\\s+static\\s+java\\.lang\\.(?:System|Boolean|Integer|Long)\\."
            + "(?:getProperty|setProperty|clearProperty|getProperties|setProperties|getBoolean|getInteger|getLong)"
            + "|(?:Enabled|Disabled)IfSystemProperty"
            + "|configureSparseSolverFromSystemProperties\\s*\\(");

    @Test
    void repositoryTestsDoNotUseGlobalSettings() throws Exception {
        Path root = Path.of("").toAbsolutePath();
        while (root != null && !Files.isDirectory(root.resolve("ipss.plugin.core"))) root = root.getParent();
        assertTrue(root != null, "Cannot locate plugin repository for configuration audit");
        var violations = new ArrayList<String>();
        try (var modules = Files.list(root)) {
            for (Path module : modules.filter(Files::isDirectory).toList()) {
                Path tests = module.resolve("src/test");
                if (!Files.isDirectory(tests)) continue;
                try (var sources = Files.walk(tests)) {
                    for (Path file : sources.filter(Files::isRegularFile)
                            .filter(p -> p.toString().matches(".*\\.(java|kt|groovy)$")).toList()) {
                        if (GLOBAL_SETTING.matcher(Files.readString(file)).find()) {
                            violations.add(root.relativize(file).toString());
                        }
                    }
                }
            }
        }
        assertTrue(violations.isEmpty(), "Move test settings to JSON or fixtures: " + violations);
    }
}
