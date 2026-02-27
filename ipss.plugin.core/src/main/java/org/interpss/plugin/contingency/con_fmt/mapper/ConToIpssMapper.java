package org.interpss.plugin.contingency.con_fmt.mapper;

import static com.interpss.common.util.NetUtilFunc.ToBranchId;

import java.util.ArrayList;
import java.util.List;

import org.interpss.plugin.contingency.con_fmt.ConContainer;
import org.interpss.plugin.contingency.con_fmt.bean.ConBranchEvent;
import org.interpss.plugin.contingency.con_fmt.bean.ConBusEvent;
import org.interpss.plugin.contingency.con_fmt.bean.ConCase;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.interpss.core.AclfContingencyObjectFactory;
import com.interpss.core.aclf.Aclf3WBranch;
import com.interpss.core.aclf.AclfBranch;
import com.interpss.core.aclf.AclfBus;
import com.interpss.core.aclf.AclfNetwork;
import com.interpss.core.contingency.ContingencyBranchOutageType;
import com.interpss.core.contingency.aclf.AclfMultiOutage;
import com.interpss.core.net.Branch;

/**
 * Maps a parsed {@link ConContainer} to a list of InterPSS
 * {@link AclfMultiOutage} objects, one per {@link ConCase}.
 *
 * <h3>Mapping rules</h3>
 * <ol>
 *   <li><b>{@code DISCONNECT BUS n}</b> — opens every active branch whose
 *       from-bus or to-bus matches bus number {@code n}.</li>
 *   <li><b>{@code DISCONNECT BRANCH FROM i TO j CKT c}</b> and
 *       <b>{@code OPEN / TRIP LINE}</b> — creates an
 *       {@link AclfContingencyObjectFactory#createAclfBranchOutage(AclfBranch, ContingencyBranchOutageType) AclfBranchOutage}
 *       with outage type {@code OPEN}.</li>
 *   <li><b>{@code CLOSE BRANCH FROM i TO j CKT c}</b> — creates an
 *       {@code AclfBranchOutage} with outage type {@code CLOSE}.</li>
 *   <li><b>{@code DISCONNECT/CLOSE BRANCH 3W}</b> and
 *       <b>{@code DISCONNECT THREEWINDING}</b> — looks up the matching
 *       {@link Aclf3WBranch} in {@code net.getSpecialBranchList()} by
 *       matching all three bus numbers, then creates an
 *       {@link AclfContingencyObjectFactory#createAclfXfr3WOutage(Aclf3WBranch) AclfXfr3WOutage}.</li>
 * </ol>
 *
 * <p>Cases where no network element can be resolved are skipped with a WARN
 * log message; they do not prevent other contingencies from being mapped.
 *
 * <h3>Usage</h3>
 * <pre>{@code
 * ConContainer con     = new ConFileParser().parse(Paths.get("input.con"));
 * ConToIpssMapper mapper = new ConToIpssMapper(net);
 * List<AclfMultiOutage> outages = mapper.map(con);
 * }</pre>
 */
public class ConToIpssMapper {

    private static final Logger log = LoggerFactory.getLogger(ConToIpssMapper.class);

    private final AclfNetwork net;

    public ConToIpssMapper(AclfNetwork net) {
        this.net = net;
    }

    // -----------------------------------------------------------------------
    // Public API
    // -----------------------------------------------------------------------

    /**
     * Maps all cases in the container to {@link AclfMultiOutage} objects.
     *
     * @param container the parsed {@code .con} container
     * @return list of multi-outage objects in the same order as the source cases;
     *         cases with no resolved events are included as empty outages
     */
    public List<AclfMultiOutage> map(ConContainer container) {
        List<AclfMultiOutage> result = new ArrayList<>();
        for (ConCase cas : container.getCases()) {
            AclfMultiOutage outage = mapCase(cas);
            result.add(outage);
        }
        log.info("Mapped {} contingency cases to AclfMultiOutage objects", result.size());
        return result;
    }

    /**
     * Maps a single {@link ConCase} to an {@link AclfMultiOutage}.
     *
     * @param cas the contingency case to map
     * @return the corresponding multi-outage object (may have zero outage items
     *         if no network elements could be resolved)
     */
    public AclfMultiOutage mapCase(ConCase cas) {
        AclfMultiOutage multiOutage = AclfContingencyObjectFactory.createAclfMultiOutage(cas.getLabel());

        // ---- bus-disconnect events ----
        for (ConBusEvent be : cas.getBusEvents()) {
            mapBusDisconnect(be, multiOutage, cas.getLabel());
        }

        // ---- branch/transformer events ----
        for (ConBranchEvent be : cas.getBranchEvents()) {
            mapBranchEvent(be, multiOutage, cas.getLabel());
        }

        return multiOutage;
    }

    // -----------------------------------------------------------------------
    // Bus disconnect
    // -----------------------------------------------------------------------

    /**
     * Opens every active branch incident to the specified bus.
     * This simulates a complete bus isolation.
     */
    private void mapBusDisconnect(ConBusEvent event, AclfMultiOutage target, String caseLabel) {
        int busNum = event.getBusNum();
        AclfBus bus = findBusByNumber(busNum);

        if (bus == null) {
            log.warn("Contingency '{}': bus {} not found in network", caseLabel, busNum);
            return;
        }

        int addedCount = 0;
        for (AclfBranch bra : net.getBranchList()) {
            if (!bra.isActive()) continue;
            boolean fromMatch = bra.getFromBus() == bus;
            boolean toMatch   = bra.getToBus()   == bus;
            if (fromMatch || toMatch) {
                target.getOutageEquips().add(
                        AclfContingencyObjectFactory.createAclfBranchOutage(
                                bra, ContingencyBranchOutageType.OPEN));
                addedCount++;
            }
        }

        if (addedCount == 0) {
            log.warn("Contingency '{}': bus {} found but has no active incident branches", caseLabel, busNum);
        } else {
            log.debug("Contingency '{}': DISCONNECT BUS {} -> {} branch outages", caseLabel, busNum, addedCount);
        }
    }

    // -----------------------------------------------------------------------
    // Branch / transformer events
    // -----------------------------------------------------------------------

    private void mapBranchEvent(ConBranchEvent event, AclfMultiOutage target, String caseLabel) {
        switch (event.getAction()) {
            case DISCONNECT -> {
                if (event.isThreeWinding())
                    map3WBranchOutage(event, target, caseLabel, ContingencyBranchOutageType.OPEN);
                else
                    mapTwoTerminalBranchOutage(event, target, caseLabel, ContingencyBranchOutageType.OPEN);
            }
            case CLOSE -> {
                if (event.isThreeWinding())
                    map3WBranchOutage(event, target, caseLabel, ContingencyBranchOutageType.CLOSE);
                else
                    mapTwoTerminalBranchOutage(event, target, caseLabel, ContingencyBranchOutageType.CLOSE);
            }
            case DISCONNECT_3W_WINDING ->
            		// TODO: this might be disconnecting a single winding instead of the whole 3W transformer; 
            		// future enhancement: support single-winding outages when the source data and contingency 
            		// object model support it
                    map3WBranchOutage(event, target, caseLabel, ContingencyBranchOutageType.OPEN);
            default ->
                    log.warn("Contingency '{}': unhandled branch action {}", caseLabel, event.getAction());
        }
    }

    // ---- two-terminal ----

    private void mapTwoTerminalBranchOutage(ConBranchEvent event, AclfMultiOutage target,
                                            String caseLabel, ContingencyBranchOutageType outageType) {
        AclfBranch branch = findBranchByBusNumbers(
                event.getFromBusNum(), event.getToBusNum(), event.getCkt());

        if (branch == null) {
            log.warn("Contingency '{}': branch FROM {} TO {} CKT {} not found",
                    caseLabel, event.getFromBusNum(), event.getToBusNum(), event.getCkt());
            return;
        }

        target.getOutageEquips().add(
                AclfContingencyObjectFactory.createAclfBranchOutage(branch, outageType));

        log.debug("Contingency '{}': {} branch {} ({}->{})", caseLabel, outageType,
                branch.getId(), event.getFromBusNum(), event.getToBusNum());
    }

    // ---- three-winding ----

    private void map3WBranchOutage(ConBranchEvent event, AclfMultiOutage target,
                                   String caseLabel, ContingencyBranchOutageType outageType) {
        Aclf3WBranch xfr3W = find3WBranchByBusNumbers(
                event.getFromBusNum(), event.getToBusNum(), event.getThirdBusNum());

        if (xfr3W == null) {
            log.warn("Contingency '{}': 3W transformer BUS {} / {} / {} not found",
                    caseLabel, event.getFromBusNum(), event.getToBusNum(), event.getThirdBusNum());
            return;
        }

        var outageObj = AclfContingencyObjectFactory.createAclfXfr3WOutage(xfr3W, outageType);
        // this issue has been fixed.
        // AclfContingencyObjectFactory only creates an OPEN-type Xfr3WOutage;
        // for a CLOSE event we still use the same factory method but note the
        // desired type for awareness. Future enhancement: set CLOSE type when
        // the object supports it.
        /*
        if (outageType == ContingencyBranchOutageType.CLOSE) {
            outageObj.setOutageType(ContingencyBranchOutageType.CLOSE);
            log.debug("Contingency '{}': CLOSE 3W transformer {}", caseLabel, xfr3W.getId());
        } else {
            log.debug("Contingency '{}': DISCONNECT 3W transformer {}", caseLabel, xfr3W.getId());
        }
        */

        target.getOutageEquips().add(outageObj);
    }

    // -----------------------------------------------------------------------
    // Network lookup helpers
    // -----------------------------------------------------------------------

    /**
     * Looks up a bus by PSS/E integer bus number via a lookup ( instead of linear network scan).
     * Returns the bus object (whose {@link AclfBus#getId()} gives the
     * InterPSS String bus ID, e.g. {@code "Bus1001"} or {@code "1001"}
     * depending on how the network was loaded).
     *
     * @return the matching bus, or {@code null} if not found
     */
    private AclfBus findBusByNumber(int busNum) {
    	// PSS/E busid = "Bus"+busNum in InterPSS implementation.
    	return net.getBus("Bus"+busNum);
    	/*
        for (AclfBus bus : net.getBusList()) {
            if ((int) bus.getNumber() == busNum) {
                return bus;
            }
        }
        return null;
        */
    }

    /**
     * Looks up a two-terminal branch using the InterPSS branch ID
     * ({@code "fromBusId->toBusId(ckt)"}) derived from the resolved bus objects.
     * Both orientations (A→B and B→A) are tried.
     *
     * @return the matching branch, or {@code null} if not found
     */
    private AclfBranch findBranchByBusNumbers(int fromNum, int toNum, String ckt) {
        AclfBus fromBus = findBusByNumber(fromNum);
        AclfBus toBus   = findBusByNumber(toNum);
        if (fromBus == null || toBus == null) return null;
        AclfBranch bra = net.getBranch(ToBranchId.f(fromBus.getId(), toBus.getId(), ckt));
        if (bra == null)
            bra = net.getBranch(ToBranchId.f(toBus.getId(), fromBus.getId(), ckt)); // reversed
        return bra;
    }

    /**
     * Finds a three-winding transformer by its three bus numbers in any order.
     *
     * <p>In InterPSS the 3W transformer is accessed via
     * {@link AclfNetwork#getSpecialBranchList()}. Each {@link Aclf3WBranch} has:
     * <ul>
     *   <li>{@code getFromBus()} → primary from-bus</li>
     *   <li>{@code getToBus()}   → primary to-bus</li>
     *   <li>{@code getFromAclfBranch().getFromBus()} → secondary from-bus</li>
     *   <li>{@code getTertAclfBranch().getFromBus()} → tertiary from-bus</li>
     * </ul>
     *
     * @param busA first bus number
     * @param busB second bus number
     * @param busC third bus number (0 if unknown — not matched against network)
     * @return the matching {@link Aclf3WBranch}, or {@code null} if not found
     */
    private Aclf3WBranch find3WBranchByBusNumbers(int busA, int busB, int busC) {
        for (Branch bra : net.getSpecialBranchList()) {
            if (!(bra instanceof Aclf3WBranch)) continue;
            Aclf3WBranch w3 = (Aclf3WBranch) bra;

            String idA = (findBusByNumber(busA) != null) ? findBusByNumber(busA).getId() : null;
            String idB = (findBusByNumber(busB) != null) ? findBusByNumber(busB).getId() : null;
            AclfBus bc = busC != 0 ? findBusByNumber(busC) : null;
            String idC = bc != null ? bc.getId() : null;
            if (idA == null || idB == null) continue;

            // Build a set of the 3W bus IDs and check whether the event buses match
            java.util.Set<String> netBuses = new java.util.HashSet<>();
            netBuses.add(w3.getFromBus().getId());
            netBuses.add(w3.getToBus().getId());
            netBuses.add(w3.getTertiaryBus().getId());

            java.util.Set<String> evtBuses = new java.util.HashSet<>();
            evtBuses.add(idA);
            evtBuses.add(idB);
            if (idC != null) evtBuses.add(idC);

            if (netBuses.containsAll(evtBuses) && evtBuses.containsAll(netBuses)) {
                return w3;
            }
        }
        return null;
    }
}
