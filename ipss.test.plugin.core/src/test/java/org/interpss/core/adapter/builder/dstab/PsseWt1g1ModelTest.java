package org.interpss.core.adapter.builder.dstab;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;

import org.interpss.IpssCorePlugin;
import org.interpss.dstab.mach.Wt1g1Data;
import org.interpss.dstab.mach.Wt1g1Machine;
import org.interpss.fadapter.psse.PSSEMultiFileLoader;
import org.interpss.fadapter.psse.dyr.DynamicModelCatalog;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.interpss.dstab.algo.DynamicSimuMethod;
import com.interpss.dstab.cache.StateMonitor;

/** Exact parser, validation, initialization, and named-state checks for WT1G1. */
public class PsseWt1g1ModelTest {
    private static final Path CASE = Path.of("testData", "adpter", "psse", "v33", "SMIB");

    @Test
    void exactNativeRecordAttachesAndInitializes() throws Exception {
        IpssCorePlugin.init();
        var context = new PSSEMultiFileLoader().loadDStab(
                CASE.resolve("SMIB_v33_wt1g1.raw").toString(),
                CASE.resolve("SMIB_v33_wt1g1.dyr").toString());
        var network = context.getDStabilityNet();
        var algorithm = context.getDynSimuAlgorithm();
        assertTrue(algorithm.getAclfAlgorithm().loadflow());
        algorithm.setSimuMethod(DynamicSimuMethod.MODIFIED_EULER);
        algorithm.setSimuStepSec(0.0005);
        algorithm.setSimuOutputHandler(new StateMonitor());
        assertTrue(algorithm.initialization());

        Wt1g1Machine machine = assertInstanceOf(Wt1g1Machine.class,
                network.getMachine("Bus1-mach1"));
        assertEquals(0.810, machine.getWt1g1Data().tp(), 0.0);
        assertEquals(0.035, machine.getWt1g1Data().tpp(), 0.0);
        assertEquals(10, DynamicModelCatalog.find("WT1G1").orElseThrow().parameterCount());
        assertEquals(4, machine.getNamedStates().size());
        assertTrue(machine.getSlip() < 0.0, "induction generator must initialize above sync speed");
        assertEquals(-0.00356336, machine.getSlip(), 2.0e-5);
    }

    @Test
    void rejectsExtraNativeField(@TempDir Path tempDir) throws Exception {
        Path dyr = tempDir.resolve("extra.dyr");
        Files.writeString(dyr,
                "1 'WT1G1' '1' .81 .035 3.6 .21 .15 .1 1 .025 1.2 .14 99 /\n"
                        + "2 'GENCLS' '1' 99999 0 /\n");
        var context = new PSSEMultiFileLoader().loadDStab(
                CASE.resolve("SMIB_v33_wt1g1.raw").toString(), dyr.toString());
        assertTrue(!(context.getDStabilityNet().getMachine("Bus1-mach1")
                instanceof Wt1g1Machine));
    }

    @Test
    void validatesSingleAndDoubleCageBoundaries() {
        Wt1g1Data single = new Wt1g1Data(.81, 0, 3.6, .21, 0, .1,
                1, .025, 1.2, .14);
        assertTrue(!single.twoCage());
        assertEquals(.21, single.xpp(), 0.0);
        assertThrows(IllegalArgumentException.class, () -> new Wt1g1Data(
                .81, .035, 3.6, .21, .25, .1, 1, .025, 1.2, .14));
        assertThrows(IllegalArgumentException.class, () -> new Wt1g1Data(
                0, 0, 3.6, .21, 0, .1, 1, .025, 1.2, .14));
    }
}
