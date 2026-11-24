package org.interpss.core.adapter.builder.dstab;

import static org.junit.jupiter.api.Assertions.*;
import java.nio.file.Path;
import org.apache.commons.math3.complex.Complex;
import org.interpss.IpssCorePlugin;
import org.interpss.fadapter.psse.PSSEMultiFileLoader;
import org.junit.jupiter.api.Test;
import com.interpss.core.acsc.fault.SimpleFaultCode;
import com.interpss.dstab.DStabGen;
import com.interpss.dstab.DStabObjectFactory;
import com.interpss.dstab.algo.DynamicSimuAlgorithm;
import com.interpss.dstab.algo.DynamicSimuMethod;
import com.interpss.dstab.cache.StateMonitor;
import com.interpss.dstab.mach.model.*;

class Gewtgcu1StaggeredGroupTest {
    private static final double STEP = 1.0 / 240;
    private static final Path CASE = Path.of("testData", "adpter", "psse", "v33", "SMIB");

    private record Fixture(Gewtgcu1Model model, DynamicSimuAlgorithm algorithm) { }
    private static Fixture load(boolean staggered) throws Exception {
        IpssCorePlugin.init();
        var context = new PSSEMultiFileLoader().loadDStab(CASE.resolve("SMIB_v33_wt2g1.raw").toString(),
                CASE.resolve("SMIB_v33_gewtgcu1_gewtecu1.dyr").toString());
        var gen = (DStabGen) context.getDStabilityNet().getBus("Bus1").getContributeGen("1");
        var model = assertInstanceOf(Gewtgcu1Model.class, gen.getDynamicGenDevice());
        model.setDriveTrain(new Gewt2mu1Model(new Gewt2mu1Data(4.61, .09, .68, 1.37, .19)));
        model.setAerodynamicModel(new Gewtaru1Model(new Gewtaru1Data(19.1, 2.2, 26, -1.2, .047, 1.16, 52, 97, 1180)));
        model.setPitchController(new Gewtptu1Model(new Gewtptu1Data(.21, 11.7, 3.2, 1.9, 1.4, -1.2, 25, -4.8, 4.2, .73)));
        model.setWindModel(new Gewtgdu1Model(new Gewtgdu1Data(.02, .04, .1, .01, .09, .05)));
        var algorithm = context.getDynSimuAlgorithm();
        algorithm.getAclfAlgorithm().getDataCheckConfig().setAllowGenWithoutMachine(true);
        assertTrue(algorithm.getAclfAlgorithm().loadflow());
        algorithm.setSimuMethod(DynamicSimuMethod.MODIFIED_EULER);
        algorithm.setSimuStepSec(STEP); algorithm.setTotalSimuTimeSec(.1);
        algorithm.setSimuOutputHandler(new StateMonitor());
        if (staggered) algorithm.setSolver(new org.interpss.dstab.algo.StaggeredEulerSolver(algorithm));
        return new Fixture(model, algorithm);
    }

    @Test void completeGroupRatesArePureAndPolePreparationDoesNotAdvanceTheWindClock() throws Exception {
        var fixture = load(false); var model = fixture.model;
        assertTrue(model.initStates(model.getDStabBus()));
        model.prepareStaggeredIntegration(STEP);
        assertEquals(29, model.staggeredStateCount());
        assertEquals(0, model.getWindModel().getTime(), 0);
        model.beginStaggeredStep(STEP);
        double[] initial = new double[29], after = new double[29], first = new double[29], next = new double[29];
        model.readStaggeredState(initial, 0); model.evaluateStaggered(STEP, first, 0);
        for (int i = 0; i < 100; i++) model.evaluateStaggered(STEP, next, 0);
        model.readStaggeredState(after, 0);
        assertArrayEquals(initial, after, 0);
        assertArrayEquals(first, next, 0);
        assertEquals(0, model.getWindModel().getTime(), 0);
        assertThrows(IllegalStateException.class, () -> model.completeStaggeredStep(STEP / 2));
        assertEquals(0, model.getWindModel().getTime(), 0);
        model.completeStaggeredStep(STEP);
        assertEquals(STEP, model.getWindModel().getTime(), 0);
        assertThrows(IllegalStateException.class, () -> model.completeStaggeredStep(STEP));
        assertEquals(STEP, model.getWindModel().getTime(), 0);
    }

    @Test void localStagesHoldPhysicalVoltageButRetainInternalShaftControllerCoupling() throws Exception {
        var fixture = load(false); var model = fixture.model;
        assertTrue(model.initStates(model.getDStabBus())); model.prepareStaggeredIntegration(STEP);
        model.beginStaggeredStep(STEP);
        double[] first = new double[29], held = new double[29], coupled = new double[29], state = new double[29];
        model.evaluateStaggered(STEP, first, 0);
        model.getDStabBus().setVoltage(.8, 0);
        model.evaluateStaggered(STEP, held, 0);
        assertArrayEquals(first, held, 0);
        model.readStaggeredState(state, 0);
        state[3 + 18 + 2] += .005;
        model.applyStaggeredState(state, 0, STEP);
        model.evaluateStaggered(STEP, coupled, 0);
        assertTrue(Math.abs(coupled[3 + 3] - held[3 + 3]) > 1e-9,
                "the electrical torque controller must see the group's live generator speed");
        model.completeStaggeredStep(STEP);
    }

    @Test void invalidGroupProposalsAreRejectedBeforeAnyChildCoordinateChanges() throws Exception {
        var fixture = load(false); var model = fixture.model;
        assertTrue(model.initStates(model.getDStabBus())); model.prepareStaggeredIntegration(STEP);
        double[] original = new double[29], trial = new double[29], after = new double[29];
        model.readStaggeredState(original, 0);
        System.arraycopy(original, 0, trial, 0, 29); trial[28] = Double.NaN;
        assertThrows(IllegalArgumentException.class, () -> model.applyStaggeredState(trial, 0, STEP));
        model.readStaggeredState(after, 0); assertArrayEquals(original, after, 0);
        System.arraycopy(original, 0, trial, 0, 29); trial[3 + 18 + 1] = 0;
        assertThrows(IllegalArgumentException.class, () -> model.applyStaggeredState(trial, 0, STEP));
        model.readStaggeredState(after, 0); assertArrayEquals(original, after, 0);
    }

    @Test void fullGroupRunsAnActualQuarterCycleFaultWithOneAcceptedWindTick() throws Exception {
        var fixture = load(true); var model = fixture.model; var algorithm = fixture.algorithm;
        var network = algorithm.getNetwork();
        network.addDynamicEvent(DStabObjectFactory.createBusFaultEvent("Bus1", network,
                SimpleFaultCode.GROUND_3P, new Complex(0, .05), null, .05, .025), "ge-group-fault");
        assertTrue(algorithm.initialization());
        assertEquals(29, model.staggeredStateCount());
        assertFalse(model.getElectricalController().isTripped(), "the test must exercise an active electrical controller");
        double minimumVoltage = Double.POSITIVE_INFINITY;
        int steps = 0;
        while (algorithm.getSimuTime() < .1 - STEP / 2) {
            assertTrue(algorithm.solveDEqnStep(true)); steps++;
            assertEquals(steps * STEP, model.getWindModel().getTime(), 1e-12);
            minimumVoltage = Math.min(minimumVoltage, model.getDStabBus().getVoltageMag());
            double[] state = new double[29]; model.readStaggeredState(state, 0);
            for (double value : state) assertTrue(Double.isFinite(value));
        }
        assertEquals(24, steps);
        assertTrue(minimumVoltage < .95, "the configured fault must actually depress terminal voltage");
        assertTrue(model.getDStabBus().getVoltageMag() > minimumVoltage);
        assertEquals(model.getWindModel().getInitialWindSpeed() + .05, model.getWindModel().getWindSpeed(), 1e-12);
    }

    @Test void changedNestedAttachmentRequiresReinitialization() throws Exception {
        var fixture = load(false); var model = fixture.model;
        assertTrue(model.initStates(model.getDStabBus())); model.prepareStaggeredIntegration(STEP);
        model.setWindModel(new Gewtgdu1Model(new Gewtgdu1Data(0, 0, 0, 0, 0, 0)));
        assertFalse(model.isStaggeredAttachmentIntact());
        assertThrows(IllegalStateException.class, () -> model.evaluateStaggered(STEP, new double[29], 0));
    }
}
