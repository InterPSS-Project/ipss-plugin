package org.interpss.core.adapter.builder.dstab;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;

import org.interpss.IpssCorePlugin;
import org.interpss.dstab.mach.Wt3g2Model;
import org.interpss.dstab.mach.Wt3t1Data;
import org.interpss.dstab.mach.Wt3t1Model;
import org.interpss.fadapter.psse.PSSEMultiFileLoader;
import org.interpss.fadapter.psse.dyr.DynamicModelCatalog;
import org.junit.jupiter.api.Test;

import com.interpss.dstab.DStabGen;
import com.interpss.dstab.algo.DynamicSimuMethod;
import com.interpss.dstab.cache.StateMonitor;

/** Exact schema, equilibrium, attachment, and named-state checks for WT3T1. */
public class PsseWt3t1ModelTest {
    private static final Path CASE = Path.of("testData", "adpter", "psse", "v33", "SMIB");

    @Test
    void exactRecordAttachesToType3HostAndInitializesAtMechanicalEquilibrium()
            throws Exception {
        IpssCorePlugin.init();
        var context = new PSSEMultiFileLoader().loadDStab(
                CASE.resolve("SMIB_v33_wt3g2_psse36.raw").toString(),
                CASE.resolve("SMIB_v33_wt3t1_psse36.dyr").toString());
        var network = context.getDStabilityNet();
        var algorithm = context.getDynSimuAlgorithm();
        DStabGen gen = (DStabGen) network.getBus("Bus1").getContributeGen("1");
        Wt3g2Model generator = assertInstanceOf(Wt3g2Model.class,
                gen.getDynamicGenDevice());
        Wt3t1Model model = generator.getDriveTrain();
        assertEquals(new Wt3t1Data(1.08, 4.8, .15, .009, 22.5,
                .72, 1.9, 1.3), model.getData());
        assertEquals(8, DynamicModelCatalog.find("WT3T1").orElseThrow().parameterCount());
        algorithm.getAclfAlgorithm().getDataCheckConfig().setAllowGenWithoutMachine(true);
        assertTrue(algorithm.getAclfAlgorithm().loadflow());
        algorithm.setSimuMethod(DynamicSimuMethod.MODIFIED_EULER);
        algorithm.setSimuStepSec(.0005);
        algorithm.setSimuOutputHandler(new StateMonitor());
        assertTrue(algorithm.initialization());
        assertEquals(4, model.getNamedStates().size());
        assertEquals(.88, model.getGeneratorSpeed(), 1.0e-12);
        assertEquals(.48416, model.getAerodynamicPower(), 2.0e-6);
        assertEquals(0.0, model.getInitialPitch(), 0.0);
        assertEquals(model.getNamedStates().get("Generator speed deviation"),
                generator.getNamedStates().get("Generator speed deviation"));
    }
}
