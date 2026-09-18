package org.interpss.core.adapter.builder.dstab;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;

import org.interpss.IpssCorePlugin;
import org.interpss.dstab.mach.Wt1g1Machine;
import org.interpss.dstab.mach.Wt12t1Data;
import org.interpss.dstab.mach.Wt12t1Model;
import org.interpss.fadapter.psse.PSSEMultiFileLoader;
import org.interpss.fadapter.psse.dyr.DynamicModelCatalog;
import org.junit.jupiter.api.Test;

import com.interpss.dstab.algo.DynamicSimuMethod;
import com.interpss.dstab.cache.StateMonitor;

/** Exact parser, initialization, and state-coordinate checks for WT12T1. */
public class PsseWt12t1ModelTest {
    private static final Path CASE = Path.of("testData", "adpter", "psse", "v33", "SMIB");

    @Test
    void nativeTwoMassRecordAttachesToWt1g1() throws Exception {
        IpssCorePlugin.init();
        var context = new PSSEMultiFileLoader().loadDStab(
                CASE.resolve("SMIB_v33_wt1g1_psse36.raw").toString(),
                CASE.resolve("SMIB_v33_wt12t1_psse36.dyr").toString());
        var algorithm = context.getDynSimuAlgorithm();
        assertTrue(algorithm.getAclfAlgorithm().loadflow());
        algorithm.setSimuMethod(DynamicSimuMethod.MODIFIED_EULER);
        algorithm.setSimuStepSec(0.0005);
        algorithm.setSimuOutputHandler(new StateMonitor());
        assertTrue(algorithm.initialization());

        Wt1g1Machine machine = (Wt1g1Machine) context.getDStabilityNet()
                .getMachine("Bus1-mach1");
        var model = machine.getDriveTrain();
        assertNotNull(model);
        assertEquals(5, DynamicModelCatalog.find("WT12T1").orElseThrow().parameterCount());
        assertEquals(4, model.getNamedStates().size());
        assertEquals(0.13515957, model.getShaftAngle(), 2.0e-5);
        assertEquals(0.00356328,
                model.getNamedStates().get("Generator speed deviation"), 2.0e-5);
        assertEquals(0.0, model.getGeneratorAngleDeviation(), 1.0e-12);
        assertEquals(0.500179, model.getAerodynamicPower(), 2.0e-5);
    }

    @Test
    void oneMassModeInitializesAtExactDampedEquilibrium() {
        Wt12t1Model model = new Wt12t1Model(new Wt12t1Data(4.8, .12, 0, 0, 0));
        model.initialize(.6, 1.01, 60.0);
        double initializedSpeed = model.getGeneratorSpeed();
        model.step(.01, .6, 0);
        model.step(.01, .6, 1);
        assertEquals(initializedSpeed, model.getGeneratorSpeed(), 1.0e-12);
        assertEquals(initializedSpeed, model.getTurbineSpeed(), 1.0e-12);
    }
}
