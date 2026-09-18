package org.interpss.core.dstab;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;

import org.apache.commons.math3.complex.Complex;
import org.interpss.IpssCorePlugin;
import org.interpss.dstab.control.gov.psse.hygov.PsseHygovGovernor;
import org.interpss.fadapter.psse.PSSEMultiFileLoader;
import org.junit.jupiter.api.Test;

import com.interpss.core.acsc.fault.SimpleFaultCode;
import com.interpss.dstab.DStabObjectFactory;
import com.interpss.dstab.algo.DynamicSimuMethod;
import com.interpss.dstab.cache.StateMonitor;

/** Full-solver smoke test for the native PSS/E HYGOVD record. */
public class HygovdSmibIntegrationTest {
    private static final double STEP = 0.0005;
    private static final Path CASE = Path.of("testData", "adpter", "psse", "v33", "SMIB");

    @Test
    void nativeRecordInitializesAndRespondsToThreeCycleFault() throws Exception {
        IpssCorePlugin.init();
        var loader = new PSSEMultiFileLoader();
        var context = loader.loadDStab(
                CASE.resolve("SMIB_v33.raw").toString(),
                CASE.resolve("SMIB_v33_genrou_hygovd_independent.dyr").toString());
        var network = context.getDStabilityNet();
        var algorithm = context.getDynSimuAlgorithm();
        assertTrue(algorithm.getAclfAlgorithm().loadflow(), "GENROU + HYGOVD load flow");
        algorithm.setSimuMethod(DynamicSimuMethod.MODIFIED_EULER);
        algorithm.setSimuStepSec(STEP);
        algorithm.setTotalSimuTimeSec(1.0);
        algorithm.setSimuOutputHandler(new StateMonitor());
        network.addDynamicEvent(DStabObjectFactory.createBusFaultEvent(
                "Bus1", network, SimpleFaultCode.GROUND_3P,
                new Complex(0.0, 0.2), null, 0.05, 0.05), "SmibFault");
        assertTrue(algorithm.initialization(), "GENROU + HYGOVD initialization");

        var machine = network.getMachine("Bus1-mach1");
        var governor = (PsseHygovGovernor) machine.getGovernor();
        assertEquals("HYGOVD", governor.getName());
        assertEquals(0.00002, governor.getData().getDbH(), 0.0);
        assertEquals(-0.00003, governor.getData().getDbL(), 0.0);
        assertEquals(100.0, governor.getGovernorBaseMva(machine), 1.0e-12);
        double initialGate = governor.getGatePosition();
        double initialFlow = governor.getWaterFlow();
        double maximumGateMovement = 0.0;
        double maximumFlowMovement = 0.0;
        while (algorithm.getSimuTime() < 1.0 - STEP / 2.0) {
            assertTrue(algorithm.solveDEqnStep(true),
                    "HYGOVD solve at " + algorithm.getSimuTime());
            maximumGateMovement = Math.max(maximumGateMovement,
                    Math.abs(governor.getGatePosition() - initialGate));
            maximumFlowMovement = Math.max(maximumFlowMovement,
                    Math.abs(governor.getWaterFlow() - initialFlow));
        }

        assertTrue(Double.isFinite(network.getBus("Bus1").getVoltageMag()));
        assertTrue(Double.isFinite(machine.getSpeed()));
        assertTrue(Double.isFinite(governor.getOutput(machine)));
        assertTrue(maximumGateMovement > 1.0e-6,
                "three-cycle fault must exercise the HYGOVD gate state");
        assertTrue(maximumFlowMovement > 1.0e-6,
                "three-cycle fault must exercise the HYGOVD penstock state");
    }
}
