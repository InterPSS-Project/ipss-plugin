package org.interpss.plugin.monitor;

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
