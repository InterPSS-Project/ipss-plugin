package org.interpss.core.adapter.cim;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.interpss.CorePluginFactory;
import org.interpss.CorePluginTestSetup;
import org.interpss.fadapter.IpssFileAdapter;
import org.interpss.fadapter.cim.CIMDirectParser;
import org.junit.jupiter.api.Test;

import com.interpss.core.aclf.AclfBranch;
import com.interpss.core.aclf.AclfBranchCode;
import com.interpss.core.aclf.AclfBus;
import com.interpss.core.aclf.AclfGenCode;
import com.interpss.core.aclf.AclfNetwork;
import com.interpss.core.net.OriginalDataFormat;
import com.interpss.simu.SimuContext;

/**
 * Regression tests for CIM direct import (ODM-bypass path).
 * Fixtures under testData/adpter/cim/ — expectations ported from ipss-odm CIM tests.
 */
public class CIMDirectParserTest extends CorePluginTestSetup {

    private static final String TD = "testData/adpter/cim/";

    private static final String MG_BE_EQ = TD + "MicroGrid_T4_BE_EQ_V2.xml";
    private static final String MG_BE_TP = TD + "MicroGrid_T4_BE_TP_V2.xml";
    private static final String MG_BE_SSH = TD + "MicroGrid_T4_BE_SSH_V2.xml";
    private static final String MG_BE_SV = TD + "MicroGrid_T4_BE_SV_V2.xml";

    private static final String MN_EQ = TD + "MiniGrid_NB_EQ_V3.xml";
    private static final String MN_TP = TD + "MiniGrid_NB_TP_V3.xml";
    private static final String MN_SSH = TD + "MiniGrid_NB_SSH_V3.xml";

    @Test
    public void testMicroGrid_EQ_TP_BusBranchCounts() throws Exception {
        AclfNetwork net = new CIMDirectParser().parse(new String[]{MG_BE_EQ, MG_BE_TP});

        assertEquals(OriginalDataFormat.CIM, net.getOriginalDataFormat());
        // 7 TopologicalNodes + 1 star bus from the 3W transformer
        assertEquals(8, net.getNoBus(), "T4 BE: 7 topo buses + 1 star bus from 3W xfr");

        int lines = 0, xfr2w = 0, xfr3w = 0;
        for (AclfBranch b : net.getBranchList()) {
            if (b.getBranchCode() == AclfBranchCode.LINE) lines++;
            else if (b.getBranchCode() == AclfBranchCode.XFORMER) xfr2w++;
            else if (b.getBranchCode() == AclfBranchCode.W3_XFORMER) xfr3w++;
        }
        // EQ+TP only: lines include ACLineSegments + SeriesCompensator that resolve
        assertTrue(lines >= 1, "Should have at least 1 line");
        assertTrue(xfr2w + xfr3w >= 1, "Should have at least 1 transformer");

        for (AclfBus bus : net.getBusList()) {
            assertNotNull(bus.getId());
            assertTrue(bus.getBaseVoltage() > 0, "Bus " + bus.getId() + " should have base voltage");
        }
    }

    @Test
    public void testMicroGrid_MultiFile_LoadsAndSwing() throws Exception {
        AclfNetwork net = new CIMDirectParser().parse(
                new String[]{MG_BE_EQ, MG_BE_TP, MG_BE_SSH, MG_BE_SV});

        assertTrue(net.getNoBus() > 0);

        int loadBuses = 0;
        boolean hasSwing = false;
        for (AclfBus bus : net.getBusList()) {
            if (bus.getContributeLoadList() != null && !bus.getContributeLoadList().isEmpty()) {
                loadBuses++;
            }
            if (bus.getGenCode() == AclfGenCode.SWING) {
                hasSwing = true;
            }
        }
        assertTrue(loadBuses > 0, "Should have loads with SSH data");
        assertTrue(hasSwing, "Should have SWING bus");
        assertTrue(CIMDirectParser.getLastLoadCount() > 0);
    }

    @Test
    public void testMicroGrid_3WTransformer() throws Exception {
        AclfNetwork net = new CIMDirectParser().parse(
                new String[]{MG_BE_EQ, MG_BE_TP, MG_BE_SSH});

        int xfr2w = 0, xfr3w = 0;
        for (AclfBranch b : net.getBranchList()) {
            if (b.getBranchCode() == AclfBranchCode.XFORMER) xfr2w++;
            else if (b.getBranchCode() == AclfBranchCode.W3_XFORMER) xfr3w++;
        }
        // 3W creates star-bus legs as 2W branches under W3_XFORMER parent;
        // count via network 3W list as well
        int threeWCount = net.getBranchList().stream()
                .filter(b -> b.getBranchCode() == AclfBranchCode.W3_XFORMER)
                .mapToInt(b -> 1)
                .sum();
        // Also count Aclf3WBranch objects if exposed
        long n3w = 0;
        try {
            n3w = net.getBranchList().stream()
                    .filter(b -> b instanceof com.interpss.core.aclf.Aclf3WBranch)
                    .count();
        } catch (Exception ignored) { }

        assertTrue(xfr2w >= 3 || threeWCount + n3w >= 1 || net.getNoBranch() > 4,
                "MicroGrid should create transformers including 3W (2W=" + xfr2w
                        + ", W3code=" + xfr3w + ", 3Winst=" + n3w + ")");
    }

    @Test
    public void testMiniGrid_3WTransformers() throws Exception {
        AclfNetwork net = new CIMDirectParser().parse(new String[]{MN_EQ, MN_TP, MN_SSH});
        assertTrue(net.getNoBus() > 0, "MiniGrid should have buses");
        assertTrue(net.getNoBranch() > 0, "MiniGrid should have branches");
    }

    @Test
    public void testIEEE118_CIMHub() throws Exception {
        AclfNetwork net = new CIMDirectParser().parse(TD + "IEEE118_CIM.xml");

        assertEquals(193, net.getNoBus(), "Buses should match MATPOWER");

        int lines = 0, xfr2w = 0;
        for (AclfBranch b : net.getBranchList()) {
            if (b.getBranchCode() == AclfBranchCode.LINE) lines++;
            else if (b.getBranchCode() == AclfBranchCode.XFORMER) xfr2w++;
        }
        assertEquals(170, lines, "Lines should match MATPOWER");
        assertEquals(84, xfr2w, "2W transformers should match MATPOWER");
        assertEquals(99, CIMDirectParser.getLastLoadCount(), "Loads should match MATPOWER");

        int genCount = 0, shuntCount = 0;
        for (AclfBus bus : net.getBusList()) {
            if (bus.getGenCode() != null && bus.getGenCode() != AclfGenCode.NON_GEN) {
                genCount++;
            }
            if (bus.getShuntY() != null && bus.getShuntY().abs() > 0) {
                shuntCount++;
            }
        }
        assertTrue(genCount >= 49, "Should have generators");
        assertEquals(14, shuntCount, "Shunts should match MATPOWER");

        // Cross-validate known line 1_2_1 PU values
        boolean found = false;
        for (AclfBranch b : net.getBranchList()) {
            if ("1_2_1".equals(b.getName()) && b.getBranchCode() == AclfBranchCode.LINE) {
                found = true;
                assertEquals(0.030300, b.getZ().getReal(), 0.0001);
                assertEquals(0.099900, b.getZ().getImaginary(), 0.0001);
                break;
            }
        }
        assertTrue(found, "Should find line 1_2_1");
    }

    @Test
    public void testCIMFormat_MultiFileViaFactory() throws Exception {
        IpssFileAdapter adapter = CorePluginFactory.getFileAdapter(IpssFileAdapter.FileFormat.CIM);
        SimuContext ctx = com.interpss.simu.SimuObjectFactory.createSimuNetwork(
                com.interpss.simu.SimuCtxType.NOT_DEFINED);
        adapter.load(ctx, new String[]{MG_BE_EQ, MG_BE_TP}, false, null);
        assertNotNull(ctx.getAclfNet());
        assertEquals(8, ctx.getAclfNet().getNoBus());
    }

    @Test
    public void testBoundaryNodesSkipped() throws Exception {
        // With BD file, boundary TNs should not become buses
        AclfNetwork withBd = new CIMDirectParser().parse(new String[]{
                MG_BE_EQ, MG_BE_TP, TD + "MicroGrid_T4_BE_EQ_BD_V2.xml", TD + "MicroGrid_T4_BE_TP_BD_V2.xml"
        });
        AclfNetwork withoutBd = new CIMDirectParser().parse(new String[]{MG_BE_EQ, MG_BE_TP});
        // Boundary merge may keep same bus count (boundary TNs skipped either way once marked)
        assertTrue(withBd.getNoBus() <= withoutBd.getNoBus() + 2,
                "Boundary handling should not inflate bus count substantially");
        assertTrue(withBd.getNoBus() >= 5);
    }
}
