package org.interpss.plugin.subsystem;

import java.io.FileReader;
import java.nio.file.Path;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.gson.Gson;
import com.interpss.core.aclf.AclfBranch;
import com.interpss.core.aclf.AclfBus;
import com.interpss.core.aclf.AclfNetwork;

/**
 * Resolves a PSS/E subsystem definition against a live {@link AclfNetwork},
 * returning the sets of bus/branch IDs that belong to selected subsystems.
 *
 * <p>Matching logic per JOIN group (all criteria are AND-ed):
 * <ul>
 *   <li>If the group has areas, the bus must belong to one of them.</li>
 *   <li>If the group has zones, the bus must belong to one of them.</li>
 *   <li>If the group has buses, the bus number must be in the list.</li>
 *   <li>If kvMin/kvMax is set, the bus base-kV must be within [kvMin, kvMax].</li>
 * </ul>
 * JOIN groups within a subsystem are OR-ed; directSelection is an additional OR-path.
 * skipBuses are excluded after all positive matches.
 *
 * <p>Usage:
 * <pre>{@code
 * SubsystemContainer sub = new SubFileParser().parse(Paths.get("input.sub"));
 * SubsystemFilter filter = new SubsystemFilter(sub);
 * Set<String> busIds = filter.getBusIds(net, "Internal");
 * }</pre>
 */
public class SubsystemFilter {

    private static final Logger log = LoggerFactory.getLogger(SubsystemFilter.class);

    private final SubsystemContainer subFile;

    public SubsystemFilter(SubsystemContainer subFile) {
        this.subFile = subFile;
    }

    // -----------------------------------------------------------------------
    // Factory methods
    // -----------------------------------------------------------------------

    /**
     * Loads a subsystem JSON file produced by {@link SubFileConverter}.
     *
     * @param jsonPath path to the JSON file
     * @return a ready-to-use filter instance
     */
    public static SubsystemFilter load(Path jsonPath) throws Exception {
        try (FileReader reader = new FileReader(jsonPath.toFile())) {
            SubsystemContainer sf = new Gson().fromJson(reader, SubsystemContainer.class);
            return new SubsystemFilter(sf);
        }
    }

    /**
     * Parses a PSS/E Subsystem Definition (.sub) file directly.
     *
     * @param subPath path to the .sub file
     * @return a ready-to-use filter instance
     */
    public static SubsystemFilter parse(Path subPath) throws Exception {
        SubsystemContainer sf = new SubFileParser().parse(subPath);
        return new SubsystemFilter(sf);
    }

    // -----------------------------------------------------------------------
    // Public API
    // -----------------------------------------------------------------------

    /**
     * Returns all subsystem labels available in the parsed file.
     */
    public List<String> getSubsystemLabels() {
        return subFile.subsystems().stream()
                .map(Subsystem::getLabel)
                .toList();
    }

    /**
     * Returns the set of bus IDs that belong to a single named subsystem.
     *
     * @param net   the network to match against
     * @param label one subsystem label
     * @return mutable set of matching bus IDs (empty if label not found)
     */
    public Set<String> getBusIds(AclfNetwork net, String label) {
        Set<String> busIds    = new HashSet<>();
        Set<String> branchIds = new HashSet<>();
        populate(net, List.of(label), busIds, branchIds);
        return busIds;
    }

    /**
     * Populates {@code busIds} and {@code branchIds} for all buses/branches
     * that match the selected subsystems.
     *
     * @param net        the AC load-flow network
     * @param labels     subsystem labels to include
     * @param busIds     output set — matched bus IDs are added here
     * @param branchIds  output set — matched branch IDs are added here
     */
    public void populate(AclfNetwork net,
                         Collection<String> labels,
                         Set<String> busIds,
                         Set<String> branchIds) {

        Set<String> labelSet = new HashSet<>(labels);
        List<Subsystem> selected = subFile.subsystems().stream()
                .filter(s -> labelSet.contains(s.getLabel()))
                .toList();

        if (selected.isEmpty()) {
            log.warn("None of the requested labels found: {}", labels);
            return;
        }

        // Build skip-bus set across all selected subsystems
        Set<Integer> skipNums = new HashSet<>();
        for (Subsystem s : selected)
            skipNums.addAll(s.getSkipBuses());

        // Match buses
        for (AclfBus bus : net.getBusList()) {
            if (!bus.isActive()) continue;
            if (skipNums.contains((int) bus.getNumber())) continue;
            for (Subsystem s : selected) {
                if (busMatchesSubsystem(bus, s)) {
                    busIds.add(bus.getId());
                    break;
                }
            }
        }

        // Match branches — include if either terminal is in the bus set
        for (AclfBranch bra : net.getBranchList()) {
            if (!bra.isActive()) continue;
            if (busIds.contains(bra.getFromBus().getId())
                    || busIds.contains(bra.getToBus().getId())) {
                branchIds.add(bra.getId());
            }
        }
    }

    // -----------------------------------------------------------------------
    // Internal matching helpers
    // -----------------------------------------------------------------------

    private boolean busMatchesSubsystem(AclfBus bus, Subsystem s) {
        for (JoinGroup jg : s.getJoinGroups()) {
            if (busMatchesGroup(bus, jg.getSelection())) return true;
        }
        if (!s.getDirectSelection().isEmpty()) {
            if (busMatchesGroup(bus, s.getDirectSelection())) return true;
        }
        return false;
    }

    private boolean busMatchesGroup(AclfBus bus, SelectionGroup g) {
        // Area filter
        if (!g.getAreas().isEmpty()) {
            if (bus.getArea() == null) return false;
            if (!g.getAreas().contains((int) bus.getArea().getNumber())) return false;
        }
        // Zone filter
        if (!g.getZones().isEmpty()) {
            if (bus.getZone() == null) return false;
            if (!g.getZones().contains((int) bus.getZone().getNumber())) return false;
        }
        // Explicit bus-number filter
        if (!g.getBuses().isEmpty()) {
            if (!g.getBuses().contains((int) bus.getNumber())) return false;
        }
        // KV filter: explicit list and/or range — bus must satisfy at least one
        boolean hasKvs   = !g.getKvs().isEmpty();
        boolean hasRange = g.getKvMin() != null;
        if (hasKvs || hasRange) {
            double baseKv      = bus.getBaseVoltage() / 1000.0;
            boolean matchKvs   = hasKvs  && g.getKvs().contains(baseKv);
            boolean matchRange = hasRange && baseKv >= g.getKvMin() && baseKv <= g.getKvMax();
            if (!matchKvs && !matchRange) return false;
        }
        return true;
    }
}
