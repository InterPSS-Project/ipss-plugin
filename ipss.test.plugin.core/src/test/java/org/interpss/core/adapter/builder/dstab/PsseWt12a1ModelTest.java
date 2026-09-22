package org.interpss.core.adapter.builder.dstab;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;

import org.interpss.IpssCorePlugin;
import org.interpss.dstab.mach.Wt1g1Machine;
import org.interpss.fadapter.psse.PSSEMultiFileLoader;
import org.interpss.fadapter.psse.dyr.DynamicModelCatalog;
import org.junit.jupiter.api.Test;

import com.interpss.dstab.algo.DynamicSimuMethod;
import com.interpss.dstab.cache.StateMonitor;

/** Parser, attachment, initialization, and named-state checks for WT12A1. */
public class PsseWt12a1ModelTest {
    private static final Path CASE = Path.of("testData", "adpter", "psse", "v33", "SMIB");

    @Test
    void nativeRecordAttachesAndInitializes() throws Exception {
        IpssCorePlugin.init();
        var context = new PSSEMultiFileLoader().loadDStab(
                CASE.resolve("SMIB_v33_wt1g1.raw").toString(),
                CASE.resolve("SMIB_v33_wt12a1.dyr").toString());
        var algorithm = context.getDynSimuAlgorithm();
        assertTrue(algorithm.getAclfAlgorithm().loadflow());
        algorithm.setSimuMethod(DynamicSimuMethod.MODIFIED_EULER);
        algorithm.setSimuStepSec(.0005);
        algorithm.setSimuOutputHandler(new StateMonitor());
        assertTrue(algorithm.initialization());
        Wt1g1Machine machine = (Wt1g1Machine) context.getDStabilityNet()
                .getMachine("Bus1-mach1");
        var model = machine.getDriveTrain().getAerodynamicController();
        assertNotNull(model);
        assertEquals(8, DynamicModelCatalog.find("WT12A1").orElseThrow().parameterCount());
        assertEquals(4, model.getNamedStates().size());
        assertEquals(.50000006, model.getNamedStates().get("Power filter"), 2e-5);
        assertEquals(.50017887, model.getNamedStates().get("PI integrator"), 2e-5);
        assertEquals(.00356328, model.getSpeedReference(), 2e-5);
        assertEquals(.50000006, model.getPowerReference(), 2e-5);
    }
}
