package org.interpss.core.contingency.parser;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

import java.util.List;

import org.interpss.CorePluginTestSetup;
import org.interpss.plugin.contingency.con_frmt.ConContainer;
import org.interpss.plugin.contingency.con_frmt.bean.ConBranchAction;
import org.interpss.plugin.contingency.con_frmt.bean.ConBranchEvent;
import org.interpss.plugin.contingency.con_frmt.bean.ConBusEvent;
import org.interpss.plugin.contingency.con_frmt.bean.ConCase;
import org.interpss.plugin.contingency.con_frmt.mapper.ConToIpssMapper;
import org.junit.BeforeClass;
import org.junit.Test;

import com.interpss.common.exp.InterpssException;
import com.interpss.core.CoreObjectFactory;
import com.interpss.core.aclf.AclfBranch;
import com.interpss.core.aclf.AclfNetwork;
import com.interpss.core.aclf.contingency.ContingencyBranchOutageType;
import com.interpss.core.aclf.contingency.aclf.AclfMultiOutage;

/**
 * Unit tests for {@link ConToIpssMapper}.
 *
 * <p>Uses a small hand-built network with three buses and two branches:
 * <pre>
 *   1001 ---[1001->1002(1)]--- 1002 ---[1002->1003(1)]--- 1003
 * </pre>
 * Bus 1001 is incident to one branch; bus 1002 is incident to two branches.
 */
public class ConToIpssMapper_Test extends CorePluginTestSetup {

    private static AclfNetwork net;
    private static ConToIpssMapper mapper;

    // -----------------------------------------------------------------------
    // Network fixture
    // -----------------------------------------------------------------------

    @BeforeClass
    public static void buildNetwork() throws InterpssException {
        net = CoreObjectFactory.createAclfNetwork();

        // --- buses ---
        CoreObjectFactory.createAclfBus("1001", net).get().setNumber(1001);
        CoreObjectFactory.createAclfBus("1002", net).get().setNumber(1002);
        CoreObjectFactory.createAclfBus("1003", net).get().setNumber(1003);

        // --- branches ---
        // 1001 -> 1002, circuit "1", active
        AclfBranch bra12 = CoreObjectFactory.createAclfBranch();
        bra12.setCircuitNumber("1");
        bra12.setStatus(true);
        net.addBranch(bra12, "1001", "1002");

        // 1002 -> 1003, circuit "1", active
        AclfBranch bra23 = CoreObjectFactory.createAclfBranch();
        bra23.setCircuitNumber("1");
        bra23.setStatus(true);
        net.addBranch(bra23, "1002", "1003");

        mapper = new ConToIpssMapper(net);
    }

    // -----------------------------------------------------------------------
    // Helper: build a minimal ConCase with one branch event
    // -----------------------------------------------------------------------

    private static ConCase branchCase(String label, ConBranchAction action,
                                       int fromBus, int toBus, String ckt) {
        ConCase cas = new ConCase(label);
        cas.addBranchEvent(new ConBranchEvent(action, fromBus, toBus, ckt));
        return cas;
    }

    // -----------------------------------------------------------------------
    // Tests: DISCONNECT_BRANCH
    // -----------------------------------------------------------------------

    /** Normal open: known branch found, one OPEN outage created. */
    @Test
    public void testDisconnectBranch_createsOpenOutage() {
        ConCase cas = branchCase("OPEN_12", ConBranchAction.DISCONNECT, 1001, 1002, "1");
        AclfMultiOutage outage = mapper.mapCase(cas);

        assertNotNull(outage);
        assertEquals("OPEN_12", outage.getId());
        assertEquals(1, outage.getOutageEquips().size());
        assertEquals(ContingencyBranchOutageType.OPEN,
                outage.getOutageEquips().get(0).getOutageType());
    }

    /** Reversed orientation (toBus, fromBus) should still match. */
    @Test
    public void testDisconnectBranch_reverseOrientation_createsOpenOutage() {
        ConCase cas = branchCase("OPEN_21", ConBranchAction.DISCONNECT, 1002, 1001, "1");
        AclfMultiOutage outage = mapper.mapCase(cas);

        assertEquals(1, outage.getOutageEquips().size());
        assertEquals(ContingencyBranchOutageType.OPEN,
                outage.getOutageEquips().get(0).getOutageType());
    }

    /** Second branch in the network is also found correctly. */
    @Test
    public void testDisconnectBranch_secondBranch() {
        ConCase cas = branchCase("OPEN_23", ConBranchAction.DISCONNECT, 1002, 1003, "1");
        AclfMultiOutage outage = mapper.mapCase(cas);

        assertEquals(1, outage.getOutageEquips().size());
        assertEquals(ContingencyBranchOutageType.OPEN,
                outage.getOutageEquips().get(0).getOutageType());
    }

    // -----------------------------------------------------------------------
    // Tests: CLOSE_BRANCH
    // -----------------------------------------------------------------------

    /** Close event creates a CLOSE-type outage item. */
    @Test
    public void testCloseBranch_createsCloseOutage() {
        ConCase cas = branchCase("CLOSE_12", ConBranchAction.CLOSE, 1001, 1002, "1");
        AclfMultiOutage outage = mapper.mapCase(cas);

        assertEquals(1, outage.getOutageEquips().size());
        assertEquals(ContingencyBranchOutageType.CLOSE,
                outage.getOutageEquips().get(0).getOutageType());
    }

    // -----------------------------------------------------------------------
    // Tests: unknown branch → graceful skip
    // -----------------------------------------------------------------------

    /** No match: bus 9999 does not exist → zero outage items, no exception. */
    @Test
    public void testDisconnectBranch_unknownBus_noOutage() {
        ConCase cas = branchCase("MISS_99", ConBranchAction.DISCONNECT, 9999, 1001, "1");
        AclfMultiOutage outage = mapper.mapCase(cas);

        assertNotNull(outage);
        assertEquals(0, outage.getOutageEquips().size());
    }

    /** Wrong circuit number → no match → zero outage items. */
    @Test
    public void testDisconnectBranch_wrongCkt_noOutage() {
        ConCase cas = branchCase("MISS_CKT", ConBranchAction.DISCONNECT, 1001, 1002, "2");
        AclfMultiOutage outage = mapper.mapCase(cas);

        assertEquals(0, outage.getOutageEquips().size());
    }

    // -----------------------------------------------------------------------
    // Tests: DISCONNECT_BUS
    // -----------------------------------------------------------------------

    /**
     * Bus 1001 is the from-bus of one active branch (1001->1002).
     * Disconnecting it should produce exactly one OPEN outage.
     */
    @Test
    public void testDisconnectBus_opensAllIncidentBranches_oneBranch() {
        ConCase cas = new ConCase("DBUS_1001");
        cas.addBusEvent(new ConBusEvent(1001));
        AclfMultiOutage outage = mapper.mapCase(cas);

        assertNotNull(outage);
        assertEquals(1, outage.getOutageEquips().size());
        assertEquals(ContingencyBranchOutageType.OPEN,
                outage.getOutageEquips().get(0).getOutageType());
    }

    /**
     * Bus 1002 is incident to both branches (1001->1002 and 1002->1003).
     * Disconnecting it should produce two OPEN outages.
     */
    @Test
    public void testDisconnectBus_opensAllIncidentBranches_twoBranches() {
        ConCase cas = new ConCase("DBUS_1002");
        cas.addBusEvent(new ConBusEvent(1002));
        AclfMultiOutage outage = mapper.mapCase(cas);

        assertEquals(2, outage.getOutageEquips().size());
        // both outages must be OPEN
        outage.getOutageEquips().forEach(item ->
                assertEquals(ContingencyBranchOutageType.OPEN, item.getOutageType()));
    }

    /** Disconnecting an unknown bus → zero outages, no exception. */
    @Test
    public void testDisconnectBus_unknownBus_noOutage() {
        ConCase cas = new ConCase("DBUS_9999");
        cas.addBusEvent(new ConBusEvent(9999));
        AclfMultiOutage outage = mapper.mapCase(cas);

        assertEquals(0, outage.getOutageEquips().size());
    }

    // -----------------------------------------------------------------------
    // Tests: multi-event case
    // -----------------------------------------------------------------------

    /**
     * A single case with two branch events produces two outage items.
     */
    @Test
    public void testMapCase_twoEvents_twoOutages() {
        ConCase cas = new ConCase("MULTI");
        cas.addBranchEvent(new ConBranchEvent(ConBranchAction.DISCONNECT, 1001, 1002, "1"));
        cas.addBranchEvent(new ConBranchEvent(ConBranchAction.DISCONNECT, 1002, 1003, "1"));
        AclfMultiOutage outage = mapper.mapCase(cas);

        assertEquals(2, outage.getOutageEquips().size());
    }

    // -----------------------------------------------------------------------
    // Tests: map(ConContainer)
    // -----------------------------------------------------------------------

    /**
     * When a container holds three cases, {@link ConToIpssMapper#map(ConContainer)}
     * returns exactly three {@link AclfMultiOutage} objects in the same order,
     * one per case.
     */
    @Test
    public void testMap_container_returnsOneOutagePerCase() {
        ConContainer container = new ConContainer();
        container.addCase(branchCase("C1", ConBranchAction.DISCONNECT, 1001, 1002, "1"));
        container.addCase(branchCase("C2", ConBranchAction.DISCONNECT, 1002, 1003, "1"));
        container.addCase(branchCase("C3", ConBranchAction.CLOSE,      1001, 1002, "1"));

        List<AclfMultiOutage> outages = mapper.map(container);

        assertEquals(3, outages.size());
        assertEquals("C1", outages.get(0).getId());
        assertEquals("C2", outages.get(1).getId());
        assertEquals("C3", outages.get(2).getId());

        // spot-check outage types
        assertEquals(ContingencyBranchOutageType.OPEN,
                outages.get(0).getOutageEquips().get(0).getOutageType());
        assertEquals(ContingencyBranchOutageType.CLOSE,
                outages.get(2).getOutageEquips().get(0).getOutageType());
    }
}
