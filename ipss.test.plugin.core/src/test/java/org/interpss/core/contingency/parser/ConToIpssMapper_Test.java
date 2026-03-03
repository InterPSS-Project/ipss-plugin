package org.interpss.core.contingency.parser;

import java.util.List;

import org.interpss.CorePluginTestSetup;
import org.interpss.plugin.contingency.con_fmt.ConContainer;
import org.interpss.plugin.contingency.con_fmt.bean.ConBranchAction;
import org.interpss.plugin.contingency.con_fmt.bean.ConBranchEvent;
import org.interpss.plugin.contingency.con_fmt.bean.ConBusEvent;
import org.interpss.plugin.contingency.con_fmt.bean.ConCase;
import org.interpss.plugin.contingency.con_fmt.bean.ConEquipAction;
import org.interpss.plugin.contingency.con_fmt.bean.ConEquipEvent;
import org.interpss.plugin.contingency.con_fmt.bean.ConEquipType;
import org.interpss.plugin.contingency.con_fmt.mapper.ConToIpssMapper;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import org.junit.BeforeClass;
import org.junit.Test;

import com.interpss.common.exp.InterpssException;
import com.interpss.core.CoreObjectFactory;
import com.interpss.core.aclf.AclfBranch;
import com.interpss.core.aclf.AclfNetwork;
import com.interpss.core.contingency.ContingencyBranchOutageType;
import com.interpss.core.contingency.ContingencyBusDeviceType;
import com.interpss.core.contingency.aclf.AclfBranchOutage;
import com.interpss.core.contingency.aclf.AclfBusDeviceOutage;
import com.interpss.core.contingency.aclf.AclfMultiOutage;

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
        CoreObjectFactory.createAclfBus("Bus1001", net).get().setNumber(1001);
        CoreObjectFactory.createAclfBus("Bus1002", net).get().setNumber(1002);
        CoreObjectFactory.createAclfBus("Bus1003", net).get().setNumber(1003);

        // --- branches ---
        // 1001 -> 1002, circuit "1", active
        AclfBranch bra12 = CoreObjectFactory.createAclfBranch();
        bra12.setCircuitNumber("1");
        bra12.setStatus(true);
        net.addBranch(bra12, "Bus1001", "Bus1002");

        // 1002 -> 1003, circuit "1", active
        AclfBranch bra23 = CoreObjectFactory.createAclfBranch();
        bra23.setCircuitNumber("1");
        bra23.setStatus(true);
        net.addBranch(bra23, "Bus1002", "Bus1003");

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

    /**
     * Duplicate branch events in one case should be deduplicated, and all mapped
     * outage items must keep a non-null outage equipment reference.
     */
    @Test
    public void testMapCase_duplicateBranchEvent_dedupAndNoNullOutageEquip() {
        ConCase cas = new ConCase("253686_DUP");
        cas.addBranchEvent(new ConBranchEvent(ConBranchAction.DISCONNECT, 1001, 1002, "1"));
        cas.addBranchEvent(new ConBranchEvent(ConBranchAction.DISCONNECT, 1001, 1002, "1")); // duplicate
        cas.addBranchEvent(new ConBranchEvent(ConBranchAction.DISCONNECT, 1002, 1003, "1"));

        AclfMultiOutage outage = mapper.mapCase(cas);

        // duplicate 1001-1002 event is skipped, leaving two unique outages
        assertEquals(2, outage.getOutageEquips().size());

        outage.getOutageEquips().forEach(item -> {
            assertTrue(item instanceof AclfBranchOutage);
            assertNotNull(((AclfBranchOutage) item).getOutageEquip());
            assertNotNull(((AclfBranchOutage) item).getOutageEquip().getId());
            assertEquals(ContingencyBranchOutageType.OPEN, item.getOutageType());
        });
    }

    /**
     * A case can contain both branch and generator outages. Generator REMOVE
     * events are mapped to {@link AclfBusDeviceOutage} of type GEN.
     */
    @Test
    public void testMapCase_branchAndGeneratorOutage_mixedTypes() {
        ConCase cas = new ConCase("MIXED_BRANCH_GEN");
        cas.addBranchEvent(new ConBranchEvent(ConBranchAction.DISCONNECT, 1001, 1002, "1"));
        cas.addEquipEvent(new ConEquipEvent(ConEquipAction.REMOVE, ConEquipType.MACHINE, "G1", 1002));

        AclfMultiOutage outage = mapper.mapCase(cas);

        assertEquals(2, outage.getOutageEquips().size());
        assertTrue(outage.getOutageEquips().stream().anyMatch(item -> item instanceof AclfBranchOutage));
        assertTrue(outage.getOutageEquips().stream().anyMatch(item -> item instanceof AclfBusDeviceOutage));

        AclfBusDeviceOutage genOutage = outage.getOutageEquips().stream()
                .filter(item -> item instanceof AclfBusDeviceOutage)
                .map(item -> (AclfBusDeviceOutage) item)
                .findFirst()
                .orElseThrow(() -> new AssertionError("Generator outage not mapped"));

        assertEquals(ContingencyBusDeviceType.GEN, genOutage.getBusDeviceOutageType());
        assertEquals("G1", genOutage.getBusDeviceId());
        assertNotNull(genOutage.getOutageEquip());
        assertEquals("Bus1002", genOutage.getOutageEquip().getId());
    }

    /**
     * Empty equipment id in REMOVE MACHINE should be skipped gracefully
     * instead of throwing an InterPSS runtime exception.
     */
    @Test
    public void testMapCase_machineOutage_blankId_isSkipped() {
        ConCase cas = new ConCase("BLANK_GEN_ID");
        cas.addEquipEvent(new ConEquipEvent(ConEquipAction.REMOVE, ConEquipType.MACHINE, "", 1002));

        AclfMultiOutage outage = mapper.mapCase(cas);

        assertNotNull(outage);
        assertEquals(0, outage.getOutageEquips().size());
    }

    /**
     * SWSHUNT id is optional in .con input; blank id should default to "1".
     */
    @Test
    public void testMapCase_swshuntOutage_blankId_defaultsToOne() {
        ConCase cas = new ConCase("BLANK_SWSHUNT_ID");
        cas.addEquipEvent(new ConEquipEvent(ConEquipAction.REMOVE, ConEquipType.SWSHUNT, "", 1002));

        AclfMultiOutage outage = mapper.mapCase(cas);

        assertNotNull(outage);
        assertEquals(1, outage.getOutageEquips().size());
        assertTrue(outage.getOutageEquips().get(0) instanceof AclfBusDeviceOutage);

        AclfBusDeviceOutage swshuntOutage = (AclfBusDeviceOutage) outage.getOutageEquips().get(0);
        assertEquals(ContingencyBusDeviceType.SWITCHED_SHUNT, swshuntOutage.getBusDeviceOutageType());
        assertEquals("1", swshuntOutage.getBusDeviceId());
        assertNotNull(swshuntOutage.getOutageEquip());
        assertEquals("Bus1002", swshuntOutage.getOutageEquip().getId());
    }

    /**
     * When DISCONNECT_BUS and DISCONNECT_BRANCH overlap on the same branch,
     * branch outages should be deduplicated and all outage equip refs remain non-null.
     */
    @Test
    public void testMapCase_busAndBranchOverlap_dedupBranchOutage() {
        ConCase cas = new ConCase("BUS_BRANCH_OVERLAP");
        cas.addBranchEvent(new ConBranchEvent(ConBranchAction.DISCONNECT, 1001, 1002, "1"));
        cas.addBusEvent(new ConBusEvent(1002));

        AclfMultiOutage outage = mapper.mapCase(cas);

        // Bus1002 has two incident branches total; explicit 1001-1002 should not duplicate it
        assertEquals(2, outage.getOutageEquips().size());
        outage.getOutageEquips().forEach(item -> {
            assertTrue(item instanceof AclfBranchOutage);
            assertNotNull(((AclfBranchOutage) item).getOutageEquip());
            assertNotNull(((AclfBranchOutage) item).getOutageEquip().getId());
        });
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
