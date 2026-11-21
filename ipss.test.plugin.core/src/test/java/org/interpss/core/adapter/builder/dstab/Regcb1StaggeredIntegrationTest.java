package org.interpss.core.adapter.builder.dstab;

import static org.junit.jupiter.api.Assertions.*;
import org.apache.commons.math3.complex.Complex;
import org.junit.jupiter.api.Test;
import com.interpss.dstab.algo.DynamicSimuMethod;
import com.interpss.dstab.renewable.*;

class Regcb1StaggeredIntegrationTest {
    private static Regcb1Model model() throws Exception {
        var model = DStabBuilderTestFixture.createBuilder().addRegcb1("Bus1", "1",
                new Regcb1Data(0, 1, .02, .02, 99, -99, 99, .01, 1.3));
        model.getDStabBus().setVoltage(1, 0);
        model.initializeWithSignals(1, .5, .2);
        return model;
    }

    @Test void ratesUseExistingEquationsAndDoNotAdvanceNativePredictorHistory() throws Exception {
        var probed = model(); var direct = model();
        for (var value : new Regcb1Model[]{probed, direct})
            assertTrue(value.nextStepWithSignals(.001, DynamicSimuMethod.MODIFIED_EULER, 0, .9, .6, -.1));
        double[] active = probed.getStateSnapshot();
        double[] first = new double[5], second = new double[5];
        probed.evaluateStaggered(.001, first, 0);
        probed.evaluateStaggered(.001, second, 0);
        assertArrayEquals(first, second, 0);
        assertArrayEquals(active, probed.getStateSnapshot(), 0);
        for (var value : new Regcb1Model[]{probed, direct})
            assertTrue(value.nextStepWithSignals(.001, DynamicSimuMethod.MODIFIED_EULER, 1, .9, .6, -.1));
        assertArrayEquals(direct.getStateSnapshot(), probed.getStateSnapshot(), 0);
    }

    @Test void explicitCoordinatesUseOldCurrentStatesAndPhysicalProjection() throws Exception {
        var model = model(); model.prepareStaggeredIntegration(.001); model.setCommands(.6, -.1);
        double[] state = new double[5], rates = new double[5];
        model.readStaggeredState(state, 0); model.evaluateStaggered(.001, rates, 0);
        assertArrayEquals(new double[]{5, -5, 0, 0, 0}, rates, 1e-12);
        for (int i = 0; i < state.length; i++) state[i] += .001 * 1.5 * rates[i];
        model.applyStaggeredState(state, 0, .001);
        assertEquals(.5075, model.getStateSnapshot()[0], 1e-14);
        assertEquals(.1925, model.getStateSnapshot()[1], 1e-14);
        var before = model.getStateSnapshot(); state[0] = Double.NaN;
        assertThrows(IllegalArgumentException.class, () -> model.applyStaggeredState(state, 0, .001));
        assertArrayEquals(before, model.getStateSnapshot(), 0);
    }

    @Test void electricalControlHasExactlyOnePositiveIntervalOwner() throws Exception {
        var model = model(); var electrical = new CounterController();
        model.setActiveElectricalController(electrical);
        electrical.initialize(.5, .2, 1);
        model.prepareStaggeredIntegration(1.0 / 240);
        assertEquals(0, electrical.advances);
        model.beginStaggeredStep(1.0 / 240);
        for (int i = 0; i < 20; i++) model.evaluateStaggered(1.0 / 240, new double[5], 0);
        assertEquals(0, electrical.advances);
        model.advanceStaggeredControls(1.0 / 240);
        assertEquals(1, electrical.advances);
        assertThrows(IllegalStateException.class, () -> model.advanceStaggeredControls(1.0 / 240));
        model.completeStaggeredStep(1.0 / 240);
        model.rebaseStaggeredGeneratorHistory();
        assertEquals(1, electrical.rebases);
        model.beginStaggeredStep(1.0 / 240);
        assertThrows(IllegalStateException.class, () -> model.completeStaggeredStep(1.0 / 240));
        model.advanceStaggeredControls(1.0 / 240);
        model.completeStaggeredStep(1.0 / 240);
        assertEquals(2, electrical.advances);
    }

    @Test void preparedElectricalAttachmentCannotBeReplacedSilently() throws Exception {
        var model = model(); var electrical = new CounterController();
        model.setActiveElectricalController(electrical); electrical.initialize(.5, .2, 1);
        model.prepareStaggeredIntegration(.001);
        model.setActiveElectricalController(new CounterController());
        assertFalse(model.isStaggeredAttachmentIntact());
        assertThrows(IllegalStateException.class, () -> model.evaluateStaggered(.001, new double[5], 0));
    }

    @Test void nestedReecdBlockingDisconnectsPhysicalCurrentWithoutResettingTheConverter() throws Exception {
        var model = model(); double[] unused = new double[10];
        var electrical = new Reecd1Model(new Reecd1Data(
                0, 0, 0, 0, 1, 0, .85, 1.2, 0, 0, 0, 0, 1, -1, 1,
                0, 0, 0, .02, 2, -2, 2, -2, 0, 0, 0, 0, 0, .02,
                10, -10, 2, -2, 1.3, .02, 0, 0, 0, 0, 0, .1, 2, .02,
                unused, unused, unused, unused), model);
        model.setActiveElectricalController(electrical);
        assertTrue(model.initStates(model.getDStabBus()));
        double[] before = model.getStateSnapshot();
        model.getDStabBus().setVoltage(.05, 0);
        electrical.stepTwoStep(.001, .5, .2, .05, 1);
        assertTrue(electrical.isConverterBlocked());
        Complex voltage = model.getDStabBus().getVoltage();
        Complex impedance = model.getParentGen().getPosGenZ().multiply(model.getParentGen().getZMultiFactor());
        Complex physical = ((Complex) model.getOutputObject()).subtract(voltage.divide(impedance));
        assertEquals(0, physical.abs(), 1e-14);
        assertArrayEquals(before, model.getStateSnapshot(), 0);
    }

    private static final class CounterController implements TwoStepElectricalController {
        int advances, rebases; double ip, iq;
        @Override public void initialize(double p, double q, double v) { ip = p / v; iq = -q / v; }
        @Override public void refreshTwoStep(double p, double q, double v, double f) { }
        @Override public void stepTwoStep(double dt, double p, double q, double v, double f) { if (dt > 0) advances++; }
        @Override public void rebaseNextTwoStep() { rebases++; }
        @Override public double getTwoStepIpcmd() { return ip; }
        @Override public double getTwoStepIqcmd() { return iq; }
        @Override public double getIpcmd() { return ip; }
        @Override public double getIqcmd() { return iq; }
        @Override public Repca1Model getPlantController() { return null; }
        @Override public void setPlantController(Repca1Model plant) { }
        @Override public void step(double dt, double p, double q, double v, double f) { throw new AssertionError("Ordinary control dispatch in staggered mode"); }
    }
}
