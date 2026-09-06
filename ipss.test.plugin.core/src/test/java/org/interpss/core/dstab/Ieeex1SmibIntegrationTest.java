package org.interpss.core.dstab;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;

import org.interpss.IpssCorePlugin;
import org.interpss.fadapter.psse.PSSEMultiFileLoader;
import org.junit.jupiter.api.Test;

import com.interpss.dstab.algo.DynamicSimuMethod;
import com.interpss.dstab.cache.StateMonitor;

/** End-to-end RAW/DYR initialization and steady-state gate for IEEEX1. */
public class Ieeex1SmibIntegrationTest {
    private static final Path CASE = Path.of(
            "testData", "adpter", "psse", "v33", "SMIB");

    @Test
    void rawDyrCaseInitializesAndRemainsAtEquilibrium() throws Exception {
        IpssCorePlugin.init();
        var context = new PSSEMultiFileLoader().loadDStab(
                CASE.resolve("SMIB_v33.raw").toString(),
                CASE.resolve("SMIB_v33_genrou_ieeex1.dyr").toString());
        var algorithm = context.getDynSimuAlgorithm();
        assertTrue(algorithm.getAclfAlgorithm().loadflow(), "SMIB load flow");
        algorithm.setSimuMethod(DynamicSimuMethod.MODIFIED_EULER);
        algorithm.setSimuStepSec(0.001);
        algorithm.setTotalSimuTimeSec(0.1);
        algorithm.setOutPutPerSteps(1);
        StateMonitor monitor = new StateMonitor();
        monitor.addGeneratorStdMonitor(new String[] {"Bus1-mach1"});
        algorithm.setSimuOutputHandler(monitor);
        assertTrue(algorithm.initialization(), "IEEEX1 dynamic initialization");
        assertTrue(algorithm.performSimulation(), "IEEEX1 steady-state simulation");

        var records = monitor.getMachEfdTable().get("Bus1-mach1");
        double initial = records.get(0).getValue();
        double maximumDrift = records.values().stream()
                .mapToDouble(record -> Math.abs(record.getValue() - initial))
                .max().orElseThrow();
        assertTrue(maximumDrift < 1.0e-8, "IEEEX1 Efd drift=" + maximumDrift);
    }
}
