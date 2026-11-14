package org.interpss.core.dstab;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.file.Files;
import java.nio.file.Path;

import org.apache.commons.math3.complex.Complex;
import org.interpss.IpssCorePlugin;
import org.interpss.dstab.control.pss.ieee.y1992.pss2b.Ieee1992PSS2BStabilizer;
import org.interpss.fadapter.psse.PSSEMultiFileLoader;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.interpss.core.acsc.fault.SimpleFaultCode;
import com.interpss.dstab.DStabObjectFactory;
import com.interpss.dstab.algo.DynamicSimuAlgorithm;
import com.interpss.dstab.algo.DynamicSimuMethod;
import com.interpss.dstab.cache.StateMonitor;

class GenrouPss2bLocalStaggeredTest {
    private static final double STEP = .0033333;
    private static final DynamicSimuMethod METHOD = DynamicSimuMethod.MODIFIED_EULER;
    private static final Path SMIB = Path.of("testData/adpter/psse/v33/SMIB");
    @TempDir Path temp;

    @Test void jsonSelectionAdvancesOnlyTheRequestedPathThroughSplitEventIntervals() throws Exception {
        for (boolean enabled : new boolean[]{false, true}) {
            var algorithm = load(enabled, true);
            var model = stabilizer(algorithm);
            assertEquals(enabled, model.requiresPhysicalStepTrial());
            assertTrue(algorithm.performSimulation());
            assertEquals(.17, algorithm.getSimuTime(), 1e-10);
            if (enabled) assertTrue(model.getStaggeredEvaluationCount() >= 50);
            else assertEquals(0, model.getStaggeredEvaluationCount());
        }
    }

    @Test void discardingAfterEndpointObservationRestoresControllerHistoryAndCoordinates() throws Exception {
        var algorithm = load(true, false);
        var model = stabilizer(algorithm);
        var machine = model.getMachine();
        var before = model.getNamedStates();
        double output = model.getOutput(machine);
        model.beginPhysicalStepTrial(0., STEP);
        machine.setSpeed(1.0002);
        assertTrue(model.nextStep(STEP, METHOD, machine, 0));
        assertThrows(IllegalStateException.class, () -> model.acceptVoltageDerivativeEndpoint(STEP, STEP));
        assertTrue(model.nextStep(STEP, METHOD, machine, 1));
        model.acceptVoltageDerivativeEndpoint(STEP, STEP);
        assertEquals(STEP, model.getNamedState("inputPathAcceptedTime"));
        model.discardPhysicalStepTrial();
        machine.setSpeed(1.);
        assertEquals(before, model.getNamedStates());
        assertEquals(output, model.getOutput(machine), 1e-14);
        assertEquals(0, model.getStaggeredEvaluationCount());
        assertTrue(algorithm.solveDEqnStep(true));
        assertEquals(STEP, model.getNamedState("inputPathAcceptedTime"));
    }

    private static Ieee1992PSS2BStabilizer stabilizer(DynamicSimuAlgorithm algorithm) {
        return assertInstanceOf(Ieee1992PSS2BStabilizer.class,
                algorithm.getNetwork().getMachine("Bus1-mach1").getStabilizer());
    }

    private DynamicSimuAlgorithm load(boolean enabled, boolean withEvent) throws Exception {
        IpssCorePlugin.init();
        var source = Files.readString(SMIB.resolve("SMIB_v33_genrou_esst1a_pss2b_independent.dyr"));
        var dyr = temp.resolve("standard-pss2b.dyr");
        // The independent fixture uses the same standard fields plus four
        // default-valued extension fields. Exercise the native DYR path.
        Files.writeString(dyr, source.replace(" 1.0 0.0 0.0 1.0 /", " /"));
        var context = new PSSEMultiFileLoader().setAutoDiscoverCasePreparationFiles(false)
                .setDynamicRunConfigJson("{\"genrouPss2bLocalStaggeredEnabled\":" + enabled + "}")
                .loadDStab(SMIB.resolve("SMIB_v33.raw").toString(), dyr.toString());
        var algorithm = context.getDynSimuAlgorithm();
        algorithm.setSimuMethod(METHOD);
        algorithm.setSimuStepSec(STEP);
        algorithm.setTotalSimuTimeSec(.17);
        algorithm.setSimuOutputHandler(new StateMonitor());
        assertTrue(algorithm.getAclfAlgorithm().loadflow());
        if (withEvent) {
            var network = algorithm.getNetwork();
            network.addDynamicEvent(DStabObjectFactory.createBusFaultEvent("Bus1", network,
                    SimpleFaultCode.GROUND_3P, new Complex(0., .2), null, .1017, .0333), "Fault");
        }
        assertTrue(algorithm.initialization());
        return algorithm;
    }
}
