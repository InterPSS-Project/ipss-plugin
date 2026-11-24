package org.interpss.fadapter.psse.monitor;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import org.interpss.fadapter.psse.subsystem.SubsystemFilter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.interpss.core.aclf.Aclf3WBranch;
import com.interpss.core.aclf.AclfBranch;
import com.interpss.core.aclf.AclfBus;
import com.interpss.core.aclf.AclfNetwork;

/**
 * Resolves a {@link MonElementContainer} against a live {@link AclfNetwork},
 * producing a {@link MonitoredElements} result that contains actual network
 * branch/bus IDs.
 *
 * <h3>Resolution rules</h3>
 * <ol>
 *   <li><b>BRANCHES / BREAKERS directive</b> — both terminals must belong to the named
 *       subsystem (internal branches).</li>
 *   <li><b>TIES directive</b> — exactly one terminal belongs to the named subsystem
 *       (cross-boundary branches).</li>
 *   <li><b>Voltage directive (SYSTEM scope)</b> — every active bus inside the named
 *       subsystem is added to the voltage-monitoring set.</li>
 *   <li><b>Voltage directive (BUS scope)</b> — the single bus with the specified number
 *       is added to the voltage-monitoring set.</li>
 *   <li><b>Interface branches</b> — each {@code MONITOR BRANCH FROM BUS n TO BUS m CKT c}
 *       entry is looked up by bus numbers and circuit ID; the resolved network branch ID
 *       is stored under the interface name.</li>
 * </ol>
 *
 * <h3>Usage</h3>
 * <pre>{@code
 * SubsystemFilter subFilter = new SubsystemFilter(
 *         new SubFileParser().parse(Paths.get("private_data/hal/sub_input.sub")));
 *
 * MonElementContainer mon = new MonFileParser()
 *         .parse(Paths.get("private_data/hal/mon_input.mon"));
 *
 * MonitoredElements result = new MonElementHelper(subFilter, net).resolve(mon);
 *
 * Set<String> thermalBranches = result.getThermalBranchIds();
 * Set<String> tieBranches     = result.getTieBranchIds();
 * Set<String> voltageBuses    = result.getVoltageBusIds();
 * Map<String,Set<String>> ifaces = result.getInterfaceBranchIds();
 * }</pre>
 */
public class MonElementHelper {

    private static final Logger log = LoggerFactory.getLogger(MonElementHelper.class);

    private final SubsystemFilter subFilter;
    private final AclfNetwork     net;

    public MonElementHelper(SubsystemFilter subFilter, AclfNetwork net) {
        this.subFilter = subFilter;
        this.net       = net;
    }

    // -----------------------------------------------------------------------
    // Main entry point
    // -----------------------------------------------------------------------

    public MonitoredElements resolve(MonElementContainer mon) {
        MonitoredElements result = new MonitoredElements();

        resolveFlowDirectives(mon.getMonitoredFlowDirectives(), result);
        resolveVoltageDirectives(mon.getMonitoredBusVoltageDirectives(), result);
        resolveInterfaces(mon.getInterfaces(), result);

        return result;
    }

    // -----------------------------------------------------------------------
    // Flow directives (BRANCHES, BREAKERS, TIES)
    // -----------------------------------------------------------------------

    private void resolveFlowDirectives(List<MonFlowDirective> directives,
                                       MonitoredElements result) {
        for (MonFlowDirective d : directives) {
            Set<String> sysbusBusIds = subFilter.getBusIds(net, d.getSystem());
            if (sysbusBusIds.isEmpty()) {
                log.warn("Subsystem '{}' resolved to 0 buses", d.getSystem());
                continue;
            }

            String type = d.getType().toUpperCase();
            switch (type) {
                case "BRANCHES", "BREAKERS" ->
                    // internal branches: both terminals inside the system
                    collectInternalBranches(sysbusBusIds, result.thermalBranchIds());

                case "TIES" ->
                    // tie branches: exactly one terminal inside the system
                    collectTieBranches(sysbusBusIds, result.tieBranchIds());

                default ->
                    log.warn("Unknown flow directive type: {}", type);
            }
        }
    }

    /** Both from-bus AND to-bus must be inside {@code sysIds}. */
    private void collectInternalBranches(Set<String> sysIds, Set<String> out) {
        for (AclfBranch bra : net.getBranchList()) {
            if (!bra.isActive()) continue;
            if (sysIds.contains(bra.getFromBus().getId())
                    && sysIds.contains(bra.getToBus().getId())) {
                out.add(bra.getId());
            }
        }
    }

    /** Exactly one of from-bus / to-bus is inside {@code sysIds}. */
    private void collectTieBranches(Set<String> sysIds, Set<String> out) {
        for (AclfBranch bra : net.getBranchList()) {
            if (!bra.isActive()) continue;
            boolean fromIn = sysIds.contains(bra.getFromBus().getId());
            boolean toIn   = sysIds.contains(bra.getToBus().getId());
            if (fromIn ^ toIn) {          // XOR: exactly one end inside
                out.add(bra.getId());
            }
        }
    }

    // -----------------------------------------------------------------------
    // Voltage directives
    // -----------------------------------------------------------------------

    private void resolveVoltageDirectives(List<MonVoltageDirective> directives,
                                          MonitoredElements result) {
        for (MonVoltageDirective d : directives) {
            if ("SYSTEM".equalsIgnoreCase(d.getScope())) {
                // add every bus that belongs to this subsystem
                Set<String> busIds = subFilter.getBusIds(net, d.getSystem());
                busIds.forEach(busId -> result.addVoltageRange(busId, d));

            } else if ("BUS".equalsIgnoreCase(d.getScope())) {
                // find a single bus by number
                int targetNum = d.getBusNum();
                for (AclfBus bus : net.getBusList()) {
                    if (bus.isActive() && (int) bus.getNumber() == targetNum) {
                        result.addVoltageRange(bus.getId(), d);
                        break;
                    }
                }
            }
        }
    }

    // -----------------------------------------------------------------------
    // Interface branch resolution
    // -----------------------------------------------------------------------

    private void resolveInterfaces(List<MonInterface> interfaces,
                                   MonitoredElements result) {
        for (MonInterface iface : interfaces) {
            Set<String> resolved = new LinkedHashSet<>();
            var members = new LinkedHashSet<MonitoredElements.InterfaceMember>();
            var unresolved = new ArrayList<String>();

            for (MonBranchEntry entry : iface.getBranches()) {
                AclfBranch branch = entry.getThirdBusNum() == null
                        ? findBranch(entry.getFromBusNum(), entry.getToBusNum(), entry.getCkt())
                        : findThreeWindingMember(entry);
                if (branch != null) {
                    resolved.add(branch.getId());
                    members.add(new MonitoredElements.InterfaceMember(branch.getId(),
                            branch.getFromBus().getNumber() == entry.getFromBusNum()));
                } else {
                    String member = entry.getFromBusNum() + " to " + entry.getToBusNum()
                            + (entry.getThirdBusNum() == null ? "" : " to " + entry.getThirdBusNum())
                            + " circuit " + entry.getCkt();
                    unresolved.add(member);
                    log.warn("Interface '{}': branch {} not found", iface.getId(), member);
                }
            }
            result.interfaceBranchIds().put(iface.getId(), resolved);
            result.addInterface(iface.getId(), new MonitoredElements.InterfaceDefinition(
                    iface.getRatingMW(), List.copyOf(members), unresolved.isEmpty() && !members.isEmpty(), unresolved));
        }
    }

    private AclfBranch findThreeWindingMember(MonBranchEntry entry) {
        Set<Long> terminals = new LinkedHashSet<>();
        terminals.add((long) entry.getFromBusNum());
        terminals.add((long) entry.getToBusNum());
        terminals.add(entry.getThirdBusNum().longValue());
        if (terminals.size() != 3) return null;
        for (var branch : net.getSpecialBranchList()) {
            if (!(branch instanceof Aclf3WBranch transformer)
                    || !entry.getCkt().equals(transformer.getCircuitNumber())) continue;
            long from = transformer.getFromBus().getNumber();
            long to = transformer.getToBus().getNumber();
            long third = transformer.getTertiaryBus().getNumber();
            if (!terminals.containsAll(List.of(from, to, third))) continue;
            if (from == entry.getFromBusNum()) return transformer.getFromAclfBranch();
            if (to == entry.getFromBusNum()) return transformer.getToAclfBranch();
            return transformer.getTertAclfBranch();
        }
        return null;
    }

    /**
     * Looks up a network branch by from/to bus number and circuit ID.
     * Checks both orientations (from->to and to->from) because PSS/E may store
     * the branch in either direction.
     *
     * @return the network branch, or {@code null} if not found
     */
    private AclfBranch findBranch(int fromNum, int toNum, String ckt) {
        for (AclfBranch bra : net.getBranchList()) {
            int bFrom = (int) bra.getFromBus().getNumber();
            int bTo   = (int) bra.getToBus().getNumber();
            String bCkt = bra.getCircuitNumber();

            if (bCkt == null) bCkt = "1";    // default circuit

            boolean cktMatch = bCkt.trim().equals(ckt.trim());
            if (!cktMatch) continue;

            if ((bFrom == fromNum && bTo == toNum)
                    || (bFrom == toNum && bTo == fromNum)) {
                return bra;
            }
        }
        return null;
    }
}
