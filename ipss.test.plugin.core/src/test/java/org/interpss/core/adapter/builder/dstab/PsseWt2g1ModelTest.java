package org.interpss.core.adapter.builder.dstab;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;

import org.interpss.IpssCorePlugin;
import org.interpss.dstab.mach.Wt2g1Data;
import org.interpss.dstab.mach.Wt2g1Machine;
import org.interpss.fadapter.psse.PSSEMultiFileLoader;
import org.interpss.fadapter.psse.dyr.DynamicModelCatalog;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.interpss.dstab.algo.DynamicSimuMethod;
import com.interpss.dstab.cache.StateMonitor;

/** Exact parser, validation, initialization, and named-state checks for WT2G1. */
public class PsseWt2g1ModelTest {
    private static final Path CASE = Path.of("testData", "adpter", "psse", "v33", "SMIB");

    @Test
    void exactNativeRecordAttachesAndInitializes() throws Exception {
        IpssCorePlugin.init();
        var context = new PSSEMultiFileLoader().loadDStab(
                CASE.resolve("SMIB_v33_wt2g1_psse36.raw").toString(),
                CASE.resolve("SMIB_v33_wt2g1_psse36.dyr").toString());
        var algorithm = context.getDynSimuAlgorithm();
        assertTrue(algorithm.getAclfAlgorithm().loadflow());
        algorithm.setSimuMethod(DynamicSimuMethod.MODIFIED_EULER);
        algorithm.setSimuStepSec(0.0005);
        algorithm.setSimuOutputHandler(new StateMonitor());
        assertTrue(algorithm.initialization());

        Wt2g1Machine machine = assertInstanceOf(Wt2g1Machine.class,
                context.getDStabilityNet().getMachine("Bus1-mach1"));
        assertEquals(0.140, machine.getWt2g1Data().xa(), 0.0);
        assertEquals(0.3340298507462687, machine.getWt2g1Data().transientReactance(), 1e-14);
        assertEquals(19, DynamicModelCatalog.find("WT2G1").orElseThrow().parameterCount());
        assertEquals(3, machine.getNamedStates().size());
        assertEquals(0.03, machine.getSpeed() - 1.0, 1e-12);
        assertTrue(machine.getRotorResistance() > machine.getWt2g1Data().rotorResistance());
        assertTrue(machine.getRotorResistance() < machine.getWt2g1Data().maximumRotorResistance());
    }

    @Test
    void rejectsExtraNativeField(@TempDir Path tempDir) throws Exception {
        Path dyr = tempDir.resolve("extra.dyr");
        Files.writeString(dyr,
                "1 'WT2G1' '1' .14 6.5 .2 .005 .12 1.02 0 1.22 0 "
                        + "0 .15 .45 .75 .95 0 .006 .025 .055 .11 99 /\n"
                        + "2 'GENCLS' '1' 99999 0 /\n");
        var context = new PSSEMultiFileLoader().loadDStab(
                CASE.resolve("SMIB_v33_wt2g1_psse36.raw").toString(), dyr.toString());
        assertTrue(!(context.getDStabilityNet().getMachine("Bus1-mach1")
                instanceof Wt2g1Machine));
    }

    @Test
    void validatesAndInterpolatesPowerSlipCurve() {
        Wt2g1Data data = data();
        assertEquals(0.03, data.speedDeviation(0.50), 1e-12);
        assertEquals(0.0, data.speedDeviation(-1.0), 0.0);
        assertEquals(0.11, data.speedDeviation(2.0), 0.0);
        assertThrows(IllegalArgumentException.class, () -> new Wt2g1Data(
                .14, 6.5, .2, .12, .005, 1.02, 0, 1.22, 0,
                new double[] {0, .15, .45, .75, .95},
                new double[] {0, .006, .025, .055, .11}));
    }

    private static Wt2g1Data data() {
        return new Wt2g1Data(.14, 6.5, .2, .005, .12, 1.02, 0, 1.22, 0,
                new double[] {0, .15, .45, .75, .95},
                new double[] {0, .006, .025, .055, .11});
    }
}
