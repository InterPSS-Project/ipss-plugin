package org.interpss.core.contingency.parser;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.nio.file.Paths;
import java.util.List;

import org.interpss.plugin.contingency.con_format.ConContainer;
import org.interpss.plugin.contingency.con_format.bean.ConBranchAction;
import org.interpss.plugin.contingency.con_format.bean.ConBranchEvent;
import org.interpss.plugin.contingency.con_format.bean.ConBusEvent;
import org.interpss.plugin.contingency.con_format.bean.ConBusModAction;
import org.interpss.plugin.contingency.con_format.bean.ConBusModEvent;
import org.interpss.plugin.contingency.con_format.bean.ConCase;
import org.interpss.plugin.contingency.con_format.bean.ConEquipAction;
import org.interpss.plugin.contingency.con_format.bean.ConEquipEvent;
import org.interpss.plugin.contingency.con_format.bean.ConEquipMoveEvent;
import org.interpss.plugin.contingency.con_format.bean.ConEquipType;
import org.interpss.plugin.contingency.con_format.parser.ConFileParser;
import org.junit.BeforeClass;
import org.junit.Test;

/**
 * Unit tests for {@link ConFileParser}.
 *
 * <p>Uses the synthetic fixture at
 * {@code testData/psse/contingency/sample.con}, which covers every
 * event type without containing any real or sensitive network data.
 */
public class ConFileParser_Test {

    private static final String FIXTURE =
            "testData/psse/contingency/sample.con";

    private static ConContainer container;

    @BeforeClass
    public static void parseFixture() throws Exception {
        container = new ConFileParser().parse(Paths.get(FIXTURE));
    }

    // -----------------------------------------------------------------------
    // Top-level container
    // -----------------------------------------------------------------------

    @Test
    public void testCategoryCount() {
        // CATEGORY 'CAT_N1' with VALUE 'CAT_N2' → 2 entries
        assertEquals(2L, container.getCategories().size());
        assertTrue(container.getCategories().contains("CAT_N1"));
        assertTrue(container.getCategories().contains("CAT_N2"));
    }

    @Test
    public void testTotalCaseCount() {
        // 26 CONTINGENCY blocks in the fixture
        assertEquals(26L, container.getCases().size());
    }

    // -----------------------------------------------------------------------
    // Branch outage variants
    // -----------------------------------------------------------------------

    @Test
    public void testDisconnectBranch2T() {
        ConCase c = findCase("N1_LINE_A");
        assertEquals(1L, c.getBranchEvents().size());
        ConBranchEvent e = c.getBranchEvents().get(0);
        assertEquals(ConBranchAction.DISCONNECT, e.getAction());
        assertEquals(1001L, e.getFromBusNum());
        assertEquals(1002L, e.getToBusNum());
        assertEquals("1", e.getCkt());
    }

    @Test
    public void testOpenLineKeyword() {
        ConCase c = findCase("N1_LINE_B");
        ConBranchEvent e = c.getBranchEvents().get(0);
        assertEquals(ConBranchAction.DISCONNECT, e.getAction());
        assertEquals(1001L, e.getFromBusNum());
        assertEquals(1003L, e.getToBusNum());
    }

    @Test
    public void testTripBranchKeyword() {
        ConCase c = findCase("N1_LINE_C");
        ConBranchEvent e = c.getBranchEvents().get(0);
        assertEquals(ConBranchAction.DISCONNECT, e.getAction());
        assertEquals(1002L, e.getFromBusNum());
        assertEquals(1003L, e.getToBusNum());
    }

    @Test
    public void testDisconnect3WBranch_allWindings() {
        ConCase c = findCase("N1_3W_XFR");
        assertEquals(1L, c.getBranchEvents().size());
        ConBranchEvent e = c.getBranchEvents().get(0);
        assertEquals(ConBranchAction.DISCONNECT, e.getAction());
        assertEquals(2001L, e.getFromBusNum());
        assertEquals(2002L, e.getToBusNum());
        assertEquals(2003L, e.getThirdBusNum());
        assertEquals("1", e.getCkt());
    }

    @Test
    public void testDisconnect3WBranch_singleWinding() {
        ConCase c = findCase("N1_3W_WINDING");
        ConBranchEvent e = c.getBranchEvents().get(0);
        assertEquals(ConBranchAction.DISCONNECT_3W_WINDING, e.getAction());
        assertEquals(2001L, e.getFromBusNum());
        assertEquals(2002L, e.getToBusNum());
        assertEquals(2003L, e.getThirdBusNum());
    }

    @Test
    public void testCloseBranch2T() {
        ConCase c = findCase("CLOSE_LINE");
        ConBranchEvent e = c.getBranchEvents().get(0);
        assertEquals(ConBranchAction.CLOSE, e.getAction());
        assertEquals(1001L, e.getFromBusNum());
        assertEquals(1002L, e.getToBusNum());
    }

    @Test
    public void testCloseBranch3W() {
        ConCase c = findCase("CLOSE_3W_XFR");
        ConBranchEvent e = c.getBranchEvents().get(0);
        assertEquals(ConBranchAction.CLOSE, e.getAction());
        assertEquals(2001L, e.getFromBusNum());
        assertEquals(2002L, e.getToBusNum());
        assertEquals(2003L, e.getThirdBusNum());
    }

    // -----------------------------------------------------------------------
    // Bus events
    // -----------------------------------------------------------------------

    @Test
    public void testDisconnectBus() {
        ConCase c = findCase("DISC_BUS");
        assertEquals(1L, c.getBusEvents().size());
        ConBusEvent e = c.getBusEvents().get(0);
        assertEquals(3001L, e.getBusNum());
    }

    @Test
    public void testTripBusKeyword() {
        ConCase c = findCase("TRIP_BUS");
        ConBusEvent e = c.getBusEvents().get(0);
        assertEquals(3002L, e.getBusNum());
    }

    // -----------------------------------------------------------------------
    // Equipment events
    // -----------------------------------------------------------------------

    @Test
    public void testRemoveMachine() {
        ConCase c = findCase("REM_MACHINE");
        ConEquipEvent e = c.getEquipEvents().get(0);
        assertEquals(ConEquipAction.REMOVE, e.getAction());
        assertEquals(ConEquipType.MACHINE, e.getEquipType());
        assertEquals("G1", e.getEquipId());
        assertEquals(4001L, e.getBusNum());
    }

    @Test
    public void testRemoveUnit_mappedToMachine() {
        ConCase c = findCase("REM_UNIT");
        ConEquipEvent e = c.getEquipEvents().get(0);
        // UNIT is treated as a synonym for MACHINE
        assertEquals(ConEquipAction.REMOVE, e.getAction());
        assertEquals(ConEquipType.MACHINE, e.getEquipType());
        assertEquals("1", e.getEquipId());
        assertEquals(4002L, e.getBusNum());
    }

    @Test
    public void testRemoveLoad() {
        ConCase c = findCase("REM_LOAD");
        ConEquipEvent e = c.getEquipEvents().get(0);
        assertEquals(ConEquipAction.REMOVE, e.getAction());
        assertEquals(ConEquipType.LOAD, e.getEquipType());
        assertEquals("1", e.getEquipId());
        assertEquals(5001L, e.getBusNum());
    }

    @Test
    public void testRemoveShunt() {
        ConCase c = findCase("REM_SHUNT");
        ConEquipEvent e = c.getEquipEvents().get(0);
        assertEquals(ConEquipAction.REMOVE, e.getAction());
        assertEquals(ConEquipType.SHUNT, e.getEquipType());
        assertEquals(5002L, e.getBusNum());
    }

    @Test
    public void testRemoveSwshunt_noId() {
        ConCase c = findCase("REM_SWSHUNT");
        ConEquipEvent e = c.getEquipEvents().get(0);
        assertEquals(ConEquipAction.REMOVE, e.getAction());
        assertEquals(ConEquipType.SWSHUNT, e.getEquipType());
        assertNull(e.getEquipId());
        assertEquals(5003L, e.getBusNum());
    }

    @Test
    public void testRemoveSwshunt_withId() {
        ConCase c = findCase("REM_SWSHUNT_ID");
        ConEquipEvent e = c.getEquipEvents().get(0);
        assertEquals(ConEquipAction.REMOVE, e.getAction());
        assertEquals(ConEquipType.SWSHUNT, e.getEquipType());
        assertEquals("A1", e.getEquipId());
        assertEquals(5004L, e.getBusNum());
    }

    // -----------------------------------------------------------------------
    // Bus modification events
    // -----------------------------------------------------------------------

    @Test
    public void testDecreaseBusGeneration() {
        ConCase c = findCase("DEC_GEN");
        ConBusModEvent e = c.getBusModEvents().get(0);
        assertEquals(ConBusModAction.DECREASE, e.getAction());
        assertEquals(6001L, e.getBusNum());
        assertEquals("GENERATION", e.getAttribute());
        assertEquals(100.0, e.getValue(), 1e-9);
        assertEquals("MW", e.getUnit());
    }

    @Test
    public void testReduceKeyword_mappedToDecrease() {
        // REDUCE is a synonym for DECREASE
        ConCase c = findCase("REDUCE_LOAD");
        ConBusModEvent e = c.getBusModEvents().get(0);
        assertEquals(ConBusModAction.DECREASE, e.getAction());
        assertEquals(6002L, e.getBusNum());
        assertEquals("LOAD", e.getAttribute());
        assertEquals(50.0, e.getValue(), 1e-9);
        assertEquals("PERCENT", e.getUnit());
    }

    @Test
    public void testSetBusShunt() {
        ConCase c = findCase("SET_SHUNT");
        ConBusModEvent e = c.getBusModEvents().get(0);
        assertEquals(ConBusModAction.SET, e.getAction());
        assertEquals(6003L, e.getBusNum());
        assertEquals("SHUNT", e.getAttribute());
        assertEquals(25.0, e.getValue(), 1e-9);
        assertEquals("MVAR", e.getUnit());
    }

    // -----------------------------------------------------------------------
    // Load move events
    // -----------------------------------------------------------------------

    @Test
    public void testMoveLoad() {
        ConCase c = findCase("MOVE_LOAD");
        assertEquals(1L, c.getEquipMoveEvents().size());
        ConEquipMoveEvent e = c.getEquipMoveEvents().get(0);
        assertEquals(ConEquipType.LOAD, e.getEquipType());
        assertEquals(100.0, e.getValue(), 1e-9);
        assertEquals("PERCENT", e.getUnit());
        assertEquals("LOAD", e.getLoadType());
        assertEquals(7001L, e.getFromBusNum());
        assertEquals(7002L, e.getToBusNum());
    }

    @Test
    public void testMoveGeneration() {
        ConCase c = findCase("MOVE_GENERATION");
        assertEquals(1L, c.getEquipMoveEvents().size());
        ConEquipMoveEvent e = c.getEquipMoveEvents().get(0);
        assertEquals(ConEquipType.GENERATION, e.getEquipType());
        assertEquals(50.0, e.getValue(), 1e-9);
        assertEquals("MW", e.getUnit());
        assertEquals("GENERATION", e.getLoadType());
        assertEquals(7001L, e.getFromBusNum());
        assertEquals(7002L, e.getToBusNum());
    }

    @Test
    public void testMoveShunt() {
        ConCase c = findCase("MOVE_SHUNT");
        assertEquals(1L, c.getEquipMoveEvents().size());
        ConEquipMoveEvent e = c.getEquipMoveEvents().get(0);
        assertEquals(ConEquipType.SHUNT, e.getEquipType());
        assertEquals(25.0, e.getValue(), 1e-9);
        assertEquals("MVAR", e.getUnit());
        assertEquals("SHUNT", e.getLoadType());
        assertEquals(7003L, e.getFromBusNum());
        assertEquals(7004L, e.getToBusNum());
    }
    @Test
    public void testMoveActiveLoad() {
        ConCase c = findCase("MOVE_ACTIVE_LOAD");
        assertEquals(1L, c.getEquipMoveEvents().size());
        ConEquipMoveEvent e = c.getEquipMoveEvents().get(0);
        assertEquals(ConEquipType.ACTIVE_LOAD, e.getEquipType());
        assertEquals(30.0, e.getValue(), 1e-9);
        assertEquals("MW", e.getUnit());
        assertEquals("ACTIVE LOAD", e.getLoadType());
        assertEquals(7001L, e.getFromBusNum());
        assertEquals(7002L, e.getToBusNum());
    }

    @Test
    public void testMoveReactiveLoad() {
        ConCase c = findCase("MOVE_REACTIVE_LOAD");
        assertEquals(1L, c.getEquipMoveEvents().size());
        ConEquipMoveEvent e = c.getEquipMoveEvents().get(0);
        assertEquals(ConEquipType.REACTIVE_LOAD, e.getEquipType());
        assertEquals(15.0, e.getValue(), 1e-9);
        assertEquals("MVAR", e.getUnit());
        assertEquals("REACTIVE LOAD", e.getLoadType());
        assertEquals(7003L, e.getFromBusNum());
        assertEquals(7004L, e.getToBusNum());
    }
    // -----------------------------------------------------------------------
    // Block DC event
    // -----------------------------------------------------------------------

    @Test
    public void testBlockDc() {
        ConCase c = findCase("BLOCK_DC");
        ConEquipEvent e = c.getEquipEvents().get(0);
        assertEquals(ConEquipAction.BLOCK, e.getAction());
        assertEquals(ConEquipType.DC_LINE, e.getEquipType());
        assertEquals("1", e.getEquipId());
        assertEquals(-1L, e.getBusNum()); // not bus-attached
    }

    // -----------------------------------------------------------------------
    // Multi-event case
    // -----------------------------------------------------------------------

    @Test
    public void testMultiEventCase_category() {
        ConCase c = findCase("MULTI_EVENT");
        assertEquals("CAT_N2", c.getCategory());
    }

    @Test
    public void testMultiEventCase_eventCount() {
        ConCase c = findCase("MULTI_EVENT");
        // 1 branch + 1 equip (UNIT) + 1 busmod (DECREASE) + 1 loadmove
        assertEquals(4L, c.eventCount());
    }

    @Test
    public void testMultiEventCase_branchAndEquip() {
        ConCase c = findCase("MULTI_EVENT");
        assertEquals(1L, c.getBranchEvents().size());
        assertEquals(1L, c.getEquipEvents().size());
        assertEquals(ConBranchAction.DISCONNECT,
                c.getBranchEvents().get(0).getAction());
        assertEquals(ConEquipAction.REMOVE, c.getEquipEvents().get(0).getAction());
        assertEquals(ConEquipType.MACHINE, c.getEquipEvents().get(0).getEquipType());
    }

    // -----------------------------------------------------------------------
    // Tolerance: FORM typo (instead of FROM)
    // -----------------------------------------------------------------------

    @Test
    public void testTypoFormAccepted() {
        ConCase c = findCase("TYPO_FORM");
        // Should have been parsed despite the FORM typo
        assertEquals(1, c.getBranchEvents().size());
        assertEquals(ConBranchAction.DISCONNECT,
                c.getBranchEvents().get(0).getAction());
    }

    // -----------------------------------------------------------------------
    // Helper
    // -----------------------------------------------------------------------

    private static ConCase findCase(String label) {
        List<ConCase> cases = container.getCases();
        return cases.stream()
                .filter(c -> label.equals(c.getLabel()))
                .findFirst()
                .orElseThrow(() -> new AssertionError(
                        "Contingency case not found: " + label));
    }
}
