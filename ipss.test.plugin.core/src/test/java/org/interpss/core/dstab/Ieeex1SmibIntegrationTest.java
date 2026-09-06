package org.interpss.core.dstab;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;

import org.interpss.IpssCorePlugin;
import org.interpss.fadapter.psse.PSSEMultiFileLoader;
import org.junit.jupiter.api.Test;

import com.interpss.dstab.algo.DynamicSimuMethod;
import com.interpss.dstab.cache.StateMonitor;

/** End-to-end RAW/DYR initialization and steady-state gates for DC exciters. */
public class Ieeex1SmibIntegrationTest {
    private static final Path CASE = Path.of(
            "testData", "adpter", "psse", "v33", "SMIB");

    @Test
    void rawDyrCaseInitializesAndRemainsAtEquilibrium() throws Exception {
        assertSteadyState("IEEEX1", "SMIB_v33_genrou_ieeex1.dyr");
    }

    @Test
    void exdc2RawDyrCaseInitializesAndRemainsAtEquilibrium() throws Exception {
        assertSteadyState("EXDC2", "SMIB_v33_genrou_exdc2.dyr");
    }

    @Test
    void exdc2aRawDyrCaseInitializesAndRemainsAtEquilibrium() throws Exception {
        assertSteadyState("EXDC2A", "SMIB_v33_genrou_exdc2a.dyr");
    }

    private static void assertSteadyState(String model, String dyrName) throws Exception {
        IpssCorePlugin.init();
        var context = new PSSEMultiFileLoader().loadDStab(
                CASE.resolve("SMIB_v33.raw").toString(),
                CASE.resolve(dyrName).toString());
        var algorithm = context.getDynSimuAlgorithm();
        assertTrue(algorithm.getAclfAlgorithm().loadflow(), "SMIB load flow");
        algorithm.setSimuMethod(DynamicSimuMethod.MODIFIED_EULER);
        algorithm.setSimuStepSec(0.001);
        algorithm.setTotalSimuTimeSec(0.1);
        algorithm.setOutPutPerSteps(1);
        StateMonitor monitor = new StateMonitor();
        monitor.addGeneratorStdMonitor(new String[] {"Bus1-mach1"});
        algorithm.setSimuOutputHandler(monitor);
        assertTrue(algorithm.initialization(), model + " dynamic initialization");
        assertTrue(algorithm.performSimulation(), model + " steady-state simulation");

        var records = monitor.getMachEfdTable().get("Bus1-mach1");
        double initial = records.get(0).getValue();
        double maximumDrift = records.values().stream()
                .mapToDouble(record -> Math.abs(record.getValue() - initial))
                .max().orElseThrow();
        assertTrue(maximumDrift < 1.0e-8, model + " Efd drift=" + maximumDrift);
    }
}
