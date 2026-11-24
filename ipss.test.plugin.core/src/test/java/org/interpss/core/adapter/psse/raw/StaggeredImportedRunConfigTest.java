package org.interpss.core.adapter.psse.raw;

import static org.junit.jupiter.api.Assertions.*;
import java.nio.file.Files;
import java.nio.file.Path;
import com.interpss.dstab.algo.DynamicSimuMethod;
import com.interpss.dstab.algo.dynamic.DynamicSimulationSolverBase;
import com.interpss.dstab.cache.StateMonitor;
import org.interpss.IpssCorePlugin;
import org.interpss.dstab.algo.ImportedModelPolicySolver;
import org.interpss.dstab.algo.StaggeredEulerSolver;
import org.interpss.fadapter.psse.PSSEMultiFileLoader;
import org.interpss.fadapter.psse.PsseGovernorBsgProcessor.Status;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class StaggeredImportedRunConfigTest {
    @TempDir Path directory;
    @Test void theSameJsonAndLoadPoliciesApplyBeforeEitherRuntimeInitializes() throws Exception {
        IpssCorePlugin.init();
        var fixture = Path.of("testData/adpter/psse/v33/SMIB");
        String json = """
                {"schemaVersion":1,"stepSec":0.0005,"totalTimeSec":0.1,
                 "networkTolerance":0.000002,"maxNetworkIterations":37,
                 "activeStaticLoadModel":"CONST_P","reactiveStaticLoadModel":"CONST_P",
                 "loadModelThresholds":{"minimumMw":0.2,"minimumAbsPOverQ":0.3,"minimumVoltagePu":0.4}}
                """;
        for (boolean staggered : new boolean[]{false, true}) {
            var loader = new PSSEMultiFileLoader().setAutoDiscoverCasePreparationFiles(false)
                    .setStaggeredIntegrationEnabled(staggered).setDynamicRunConfigJson(json);
            var context = loader.loadDStab(fixture.resolve("SMIB_v33_regcb1.raw").toString(),
                    fixture.resolve("SMIB_v33_regcb1.dyr").toString());
            var algorithm = context.getDynSimuAlgorithm();
            assertEquals(staggered, algorithm.getSolver() instanceof StaggeredEulerSolver);
            var numeric = assertInstanceOf(DynamicSimulationSolverBase.class, algorithm.getSolver());
            var policy = assertInstanceOf(ImportedModelPolicySolver.class, algorithm.getSolver());
            assertEquals(.0005, algorithm.getSimuStepSec(), 0);
            assertEquals(.1, algorithm.getTotalSimuTimeSec(), 0);
            assertEquals(.000002, numeric.getNetworkVoltageTolerance(), 0);
            assertEquals(.2, policy.getLoadModelThresholds().minimumMw(), 0);
            assertEquals(.3, policy.getLoadModelThresholds().minimumAbsPOverQ(), 0);
            assertEquals(.4, policy.getLoadModelThresholds().minimumVoltagePu(), 0);
            assertTrue(loader.getLastRunConfigApplicationGaps().isEmpty());
            algorithm.getAclfAlgorithm().getDataCheckConfig().setAllowGenWithoutMachine(true);
            assertTrue(algorithm.getAclfAlgorithm().loadflow());
            algorithm.setSimuMethod(DynamicSimuMethod.MODIFIED_EULER);
            algorithm.setSimuOutputHandler(new StateMonitor());
            assertTrue(algorithm.initialization());
            assertTrue(algorithm.solveDEqnStep(true));
            assertFalse(policy.getLoadModelAssignmentReport().isEmpty());
        }
    }

    @Test void governorBlockingIsRegisteredAndReplayedOnTheChosenRuntime() throws Exception {
        IpssCorePlugin.init();
        var fixture = Path.of("testData/adpter/psse/v33/HspModels");
        Path bsg = directory.resolve("governor.bsg");
        Files.writeString(bsg, "1, 'GEN', 13.8, '1', 2\n999, 'missing', 13.8, '1', 1\n");
        for (boolean staggered : new boolean[]{false, true}) {
            var loader = new PSSEMultiFileLoader().setAutoDiscoverCasePreparationFiles(false)
                    .setStaggeredIntegrationEnabled(staggered);
            var context = loader.loadDStab(fixture.resolve("network.raw").toString(),
                    fixture.resolve("wsieg1.dyr").toString(), bsg.toString());
            var algorithm = context.getDynSimuAlgorithm();
            var policy = assertInstanceOf(ImportedModelPolicySolver.class, algorithm.getSolver());
            assertEquals(2, loader.getLastGovernorBlockingEntries().size());
            assertEquals(Status.REGISTERED, policy.getGovernorBlockingReport().getFirst().status());
            assertTrue(algorithm.getAclfAlgorithm().loadflow());
            algorithm.setSimuMethod(DynamicSimuMethod.MODIFIED_EULER);
            algorithm.setSimuStepSec(.0005); algorithm.setSimuOutputHandler(new StateMonitor());
            assertTrue(algorithm.initialization());
            assertEquals(Status.APPLIED, policy.getGovernorBlockingReport().getFirst().status());
            assertEquals(Status.MISSING_TARGET, policy.getGovernorBlockingReport().get(1).status());
            assertEquals(staggered, algorithm.getSolver() instanceof StaggeredEulerSolver);
        }
    }
}
