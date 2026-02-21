package org.interpss.plugin.monitor;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Parser for PSS/E Monitored Element (.mon) files.
 *
 * <p>Recognised line patterns (case-insensitive):
 * <ul>
 *   <li>{@code monitor branches in system <name>}
 *   <li>{@code monitor breakers in system <name>}
 *   <li>{@code monitor ties from system <name>}
 *   <li>{@code monitor voltage range system <name> <vMin> <vMax>}
 *   <li>{@code monitor voltage range bus <busNum> <vMin> <vMax>}
 *   <li>{@code MONITOR INTERFACE '<name>' RATING <mw> MW}
 *   <li>{@code MONITOR BRANCH FROM BUS <n> TO BUS <n> CKT <id>}
 *   <li>{@code END} — closes an open INTERFACE block
 * </ul>
 * Lines beginning with {@code /} are treated as comments and ignored.
 */
public class MonFileParser {

    // MONITOR INTERFACE 'name' RATING 1645.0 MW
    private static final Pattern IFACE_PAT = Pattern.compile(
            "MONITOR\\s+INTERFACE\\s+['\"]([^'\"]+)['\"]\\s+RATING\\s+([\\d.]+)\\s+MW",
            Pattern.CASE_INSENSITIVE);

    // MONITOR BRANCH FROM BUS 524415 TO BUS 525213 CKT 1
    private static final Pattern BRANCH_PAT = Pattern.compile(
            "MONITOR\\s+BRANCH\\s+FROM\\s+BUS\\s+(\\d+)\\s+TO\\s+BUS\\s+(\\d+)\\s+CKT\\s+(\\S+)",
            Pattern.CASE_INSENSITIVE);

    // monitor branches/breakers in system <name>
    private static final Pattern SYS_PAT = Pattern.compile(
            "(?:MONITOR)\\s+(BRANCHES|BREAKERS)\\s+IN\\s+SYSTEM\\s+(\\S+)",
            Pattern.CASE_INSENSITIVE);

    // monitor ties from system <name>
    private static final Pattern TIES_PAT = Pattern.compile(
            "(?:MONITOR)\\s+TIES\\s+FROM\\s+SYSTEM\\s+(\\S+)",
            Pattern.CASE_INSENSITIVE);

    // monitor voltage range system <name> <min> <max>
    private static final Pattern VOLT_SYS_PAT = Pattern.compile(
            "(?:MONITOR)\\s+VOLTAGE\\s+RANGE\\s+SYSTEM\\s+(\\S+)\\s+([\\d.]+)\\s+([\\d.]+)",
            Pattern.CASE_INSENSITIVE);

    // monitor voltage range bus <num> <min> <max>
    private static final Pattern VOLT_BUS_PAT = Pattern.compile(
            "(?:MONITOR)\\s+VOLTAGE\\s+RANGE\\s+BUS\\s+(\\d+)\\s+([\\d.]+)\\s+([\\d.]+)",
            Pattern.CASE_INSENSITIVE);

    public MonElementContainer parse(Path filePath) throws IOException {
        List<MonFlowDirective>    sysDirectives  = new ArrayList<>();
        List<MonVoltageDirective> voltDirectives = new ArrayList<>();
        List<MonInterface>        interfaces     = new ArrayList<>();

        MonInterface currentIface = null;

        try (BufferedReader reader = new BufferedReader(new FileReader(filePath.toFile()))) {
            String line;
            while ((line = reader.readLine()) != null) {
                String trimmed = line.trim();

                // Skip pure comment lines (start with /)
                if (trimmed.startsWith("/")) continue;
                if (trimmed.isEmpty()) continue;

                // Separate inline comment from branch comments (after the last /)
                String comment = null;
                int slashIdx = trimmed.indexOf('/');
                String data  = slashIdx >= 0 ? trimmed.substring(0, slashIdx).trim() : trimmed;
                if (slashIdx >= 0) comment = trimmed.substring(slashIdx + 1).trim();

                if (data.isEmpty()) continue;

                String upper = data.toUpperCase();

                // END closes an interface block (ignore extra END tokens)
                if (upper.equals("END")) {
                    if (currentIface != null) {
                        interfaces.add(currentIface);
                        currentIface = null;
                    }
                    continue;
                }

                // MONITOR BRANCH  (must be checked before generic MONITOR patterns)
                Matcher m = BRANCH_PAT.matcher(data);
                if (m.find()) {
                    if (currentIface != null) {
                        int    from = Integer.parseInt(m.group(1));
                        int    to   = Integer.parseInt(m.group(2));
                        String ckt  = m.group(3);
                        currentIface.addBranch(new MonBranchEntry(from, to, ckt, comment));
                    }
                    continue;
                }

                // MONITOR INTERFACE
                m = IFACE_PAT.matcher(data);
                if (m.find()) {
                    // close any previously open interface (safety)
                    if (currentIface != null) interfaces.add(currentIface);
                    currentIface = new MonInterface(m.group(1), Double.parseDouble(m.group(2)));
                    continue;
                }

                // monitor voltage range bus
                m = VOLT_BUS_PAT.matcher(data);
                if (m.find()) {
                    voltDirectives.add(MonVoltageDirective.forBus(
                            Integer.parseInt(m.group(1)),
                            Double.parseDouble(m.group(2)),
                            Double.parseDouble(m.group(3))));
                    continue;
                }

                // monitor voltage range system
                m = VOLT_SYS_PAT.matcher(data);
                if (m.find()) {
                    voltDirectives.add(MonVoltageDirective.forSystem(
                            m.group(1),
                            Double.parseDouble(m.group(2)),
                            Double.parseDouble(m.group(3))));
                    continue;
                }

                // monitor branches/breakers in system
                m = SYS_PAT.matcher(data);
                if (m.find()) {
                    sysDirectives.add(new MonFlowDirective(
                            m.group(1).toUpperCase(), m.group(2)));
                    continue;
                }

                // monitor ties from system
                m = TIES_PAT.matcher(data);
                if (m.find()) {
                    sysDirectives.add(new MonFlowDirective("TIES", m.group(1)));
                    continue;
                }
                // unrecognised lines are silently skipped
            }
        }
        // close any unterminated interface block at EOF
        if (currentIface != null) interfaces.add(currentIface);

        return new MonElementContainer(filePath.toAbsolutePath().toString(),
                sysDirectives, voltDirectives, interfaces);
    }
}
