package org.interpss.core.adapter.builder.dstab;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;

import org.interpss.IpssCorePlugin;
import org.interpss.dstab.mach.Wt2e1Model;
import org.interpss.dstab.mach.Wt2g1Machine;
import org.interpss.fadapter.psse.PSSEMultiFileLoader;
import org.interpss.fadapter.psse.dyr.DynamicModelCatalog;
import org.junit.jupiter.api.Test;

import com.interpss.dstab.algo.DynamicSimuMethod;
import com.interpss.dstab.cache.StateMonitor;

/** Parser, initialization, and named-state checks for WT2E1. */
public class PsseWt2e1ModelTest {
    private static final Path CASE = Path.of("testData", "adpter", "psse", "v33", "SMIB");

    @Test
    void exactPublishedRecordAttachesAndInitializes() throws Exception {
        IpssCorePlugin.init();
        var context = new PSSEMultiFileLoader().loadDStab(
                CASE.resolve("SMIB_v33_wt2g1_psse36.raw").toString(),
                CASE.resolve("SMIB_v33_wt2e1_psse36.dyr").toString());
        var algorithm = context.getDynSimuAlgorithm();
        assertTrue(algorithm.getAclfAlgorithm().loadflow());
        algorithm.setSimuMethod(DynamicSimuMethod.MODIFIED_EULER);
        algorithm.setSimuStepSec(0.0005);
        algorithm.setSimuOutputHandler(new StateMonitor());
        assertTrue(algorithm.initialization());

        Wt2g1Machine machine = assertInstanceOf(Wt2g1Machine.class,
                context.getDStabilityNet().getMachine("Bus1-mach1"));
        Wt2e1Model controller = machine.getRotorResistanceController();
        assertInstanceOf(Wt2e1Model.class, controller);
        assertEquals(6, DynamicModelCatalog.find("WT2E1").orElseThrow().parameterCount());
        assertEquals(3, controller.getNamedStates().size());
        assertEquals(0.03, controller.getNamedStates().get("Rotor speed filter"), 1e-12);
        assertEquals(0.5, controller.getNamedStates().get("Power filter"), 2e-4);
        assertEquals(machine.getRotorControlVoltage(),
                controller.getNamedStates().get("PI integrator"), 1e-12);
    }
}
