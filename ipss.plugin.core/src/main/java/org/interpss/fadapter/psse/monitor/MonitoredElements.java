package org.interpss.fadapter.psse.monitor;

import java.util.ArrayList;
import java.util.List;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

/**
 * The resolved result of applying a {@link MonElementContainer} to an actual network.
 *
 * <ul>
 *   <li>{@link #thermalBranchIds} — internal branches/breakers to monitor for thermal limits.
 *       Both terminals belong to at least one of the subsystems listed in the
 *       {@code BRANCHES}/{@code BREAKERS} flow directives.</li>
 *   <li>{@link #tieBranchIds} — tie branches, where exactly one terminal is inside the
 *       subsystem that owns the {@code TIES} directive.</li>
 *   <li>{@link #voltageBusIds} — buses to monitor for voltage violations, coming from
 *       {@code SYSTEM} and {@code BUS} voltage directives.</li>
 *   <li>{@link #interfaceBranchIds} — map from interface ID to the set of resolved
 *       network branch IDs defined in the {@code MONITOR INTERFACE} blocks.</li>
 * </ul>
 */
public class MonitoredElements {
    public record VoltageRange(double minimumPu, double maximumPu) { }
    public record InterfaceMember(String branchId, boolean fromTerminal) { }
    public record InterfaceDefinition(double ratingMw, List<InterfaceMember> members, boolean complete,
            List<String> unresolvedMembers) {
        public InterfaceDefinition(double ratingMw, List<InterfaceMember> members, boolean complete) {
            this(ratingMw, members, complete, List.of());
        }

        public InterfaceDefinition {
            members = List.copyOf(members);
            unresolvedMembers = List.copyOf(unresolvedMembers);
        }
    }

    private final Map<String, List<VoltageRange>> voltageRanges = new LinkedHashMap<>();
    private final Map<String, InterfaceDefinition> interfaceDefinitions = new LinkedHashMap<>();

    void addVoltageRange(String busId, MonVoltageDirective directive) {
        voltageBusIds.add(busId);
        var range = new VoltageRange(directive.getVMin(), directive.getVMax());
        var ranges = voltageRanges.computeIfAbsent(busId, id -> new ArrayList<>());
        if (!ranges.contains(range)) ranges.add(range);
    }

    void addInterface(String id, InterfaceDefinition definition) {
        interfaceDefinitions.put(id, definition);
    }

    public List<VoltageRange> getVoltageRanges(String busId) {
        return List.copyOf(voltageRanges.getOrDefault(busId, List.of()));
    }

    public Map<String, InterfaceDefinition> getInterfaceDefinitions() {
        return java.util.Collections.unmodifiableMap(interfaceDefinitions);
    }

    /** Branch IDs for thermal monitoring (both ends inside a monitored system). */
    private final Set<String> thermalBranchIds  = new LinkedHashSet<>();

    /** Branch IDs for tie monitoring (one end inside, one end outside a monitored system). */
    private final Set<String> tieBranchIds      = new LinkedHashSet<>();

    /** Bus IDs for voltage monitoring. */
    private final Set<String> voltageBusIds     = new LinkedHashSet<>();

    /**
     * Interface ID -> set of network branch IDs.
     * Ordered by the sequence in the .mon file.
     */
    private final Map<String, Set<String>> interfaceBranchIds = new LinkedHashMap<>();

    // package-private so only MonElementHelper writes to these
    Set<String> thermalBranchIds()                { return thermalBranchIds;   }
    Set<String> tieBranchIds()                    { return tieBranchIds;       }
    Set<String> voltageBusIds()                   { return voltageBusIds;      }
    Map<String, Set<String>> interfaceBranchIds() { return interfaceBranchIds; }

    // ---- read-only public accessors ----
    public Set<String>              getThermalBranchIds()   { return thermalBranchIds;   }
    public Set<String>              getTieBranchIds()       { return tieBranchIds;       }
    public Set<String>              getVoltageBusIds()      { return voltageBusIds;      }
    public Map<String, Set<String>> getInterfaceBranchIds() { return interfaceBranchIds; }

    @Override
    public String toString() {
        return String.format(
                "MonitoredElements{thermalBranches=%d, tieBranches=%d, voltageBuses=%d, interfaces=%d}",
                thermalBranchIds.size(), tieBranchIds.size(),
                voltageBusIds.size(), interfaceBranchIds.size());
    }
}
