package org.interpss.core.dstab.mach;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.file.Path;
import java.nio.file.Files;
import org.apache.commons.math3.complex.Complex;
import org.interpss.IpssCorePlugin;
import org.interpss.dstab.algo.DefaultDynamicSimulationSolver;
import org.interpss.dstab.algo.StaggeredEulerSolver;
import org.interpss.dstab.mach.Wt3g2Model;
import org.interpss.fadapter.psse.PSSEMultiFileLoader;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import com.interpss.dstab.DStabGen;
import com.interpss.dstab.algo.DynamicSimuMethod;
import com.interpss.dstab.algo.DynamicSimuAlgorithm;
import com.interpss.dstab.cache.StateMonitor;
import com.interpss.dstab.DStabObjectFactory;
import com.interpss.core.acsc.fault.SimpleFaultCode;

/** Common-equation and full-network checks for the bare converter boundary. */
public class Wt3g2SharedStateTest {
    private static final Path CASE = Path.of("testData/adpter/psse/v33/SMIB");
    private record Fixture(DynamicSimuAlgorithm algorithm, Wt3g2Model model) {}

    private static Fixture fixture(boolean shared, boolean keepController, double step) throws Exception {
        return fixture(shared, keepController, step, CASE.resolve("SMIB_v33_wt3g2.dyr"));
    }

    private static Fixture fixture(boolean shared, boolean keepController, double step,
            Path dyr) throws Exception {
        IpssCorePlugin.init();
        var context = new PSSEMultiFileLoader().loadDStab(
                CASE.resolve("SMIB_v33_wt3g2.raw").toString(),
                dyr.toString());
        var a = context.getDynSimuAlgorithm();
        var gen = (DStabGen) context.getDStabilityNet().getBus("Bus1").getContributeGen("1");
        var model = (Wt3g2Model) gen.getDynamicGenDevice();
        if (!keepController) model.setElectricalController(null);
        a.getAclfAlgorithm().getDataCheckConfig().setAllowGenWithoutMachine(true);
        assertTrue(a.getAclfAlgorithm().loadflow());
        a.setSimuMethod(DynamicSimuMethod.MODIFIED_EULER);
        a.setSimuStepSec(step);
        a.setTotalSimuTimeSec(.2);
        a.setSimuOutputHandler(new StateMonitor());
        a.setSolver(shared ? new StaggeredEulerSolver(a) : new DefaultDynamicSimulationSolver(a));
        return new Fixture(a, model);
    }

    private static double[] state(Wt3g2Model model) {
        double[] result = new double[5];
        model.readStaggeredState(result, 0);
        return result;
    }

    @Test void sharedRhsReproducesOrdinaryPredictorCorrector() throws Exception {
        var ordinary = fixture(false, false, .0005);
        var shared = fixture(false, false, .0005);
        assertTrue(ordinary.algorithm.initialization());
        assertTrue(shared.algorithm.initialization());
        double h = .0005;
        for (var f : new Fixture[]{ordinary, shared})
            f.model.setCommands(f.model.getActiveCurrentState() + .03,
                    f.model.getInternalVoltageState() + .01);
        for (int n = 0; n < 20; n++) {
            double[] left = state(shared.model), first = new double[5], last = new double[5];
            shared.model.evaluateStaggered(h, first, 0);
            double[] trial = left.clone();
            for (int i = 0; i < 5; i++) trial[i] += h * first[i];
            shared.model.applyStaggeredState(trial, 0, h);
            assertTrue(ordinary.model.nextStep(h, DynamicSimuMethod.MODIFIED_EULER, 0));
            assertArrayEquals(state(ordinary.model), state(shared.model), 0.0);
            shared.model.evaluateStaggered(h, last, 0);
            for (int i = 0; i < 5; i++) trial[i] = left[i] + .5 * h * (first[i] + last[i]);
            shared.model.applyStaggeredState(trial, 0, h);
            assertTrue(ordinary.model.nextStep(h, DynamicSimuMethod.MODIFIED_EULER, 1));
            assertArrayEquals(state(ordinary.model), state(shared.model), 0.0);
        }
    }

    @Test void trialsArePureAtomicAndRestorable() throws Exception {
        var f = fixture(false, false, .0005);
        assertTrue(f.algorithm.initialization());
        double[] left = state(f.model), rates = new double[5];
        var output = f.model.getOutputObject();
        f.model.evaluateStaggered(.0005, rates, 0);
        f.model.evaluateStaggered(.0005, rates, 0);
        assertArrayEquals(left, state(f.model), 0.0);
        double[] trial = left.clone(); trial[0] += .02;
        f.model.applyStaggeredState(trial, 0, .0005);
        f.model.applyStaggeredState(left, 0, .0005);
        assertArrayEquals(left, state(f.model), 0.0);
        assertEquals(output, f.model.getOutputObject());
        trial[4] = Double.NaN;
        assertThrows(IllegalArgumentException.class, () -> f.model.applyStaggeredState(trial, 0, .0005));
        assertArrayEquals(left, state(f.model), 0.0);
        assertTrue(f.model.nextStep(.0005, DynamicSimuMethod.MODIFIED_EULER, 0));
        assertThrows(IllegalStateException.class, () -> f.model.evaluateStaggered(.0005, rates, 0));
        assertFalse(f.model.nextStep(.001, DynamicSimuMethod.MODIFIED_EULER, 1));
        assertTrue(f.model.nextStep(.0005, DynamicSimuMethod.MODIFIED_EULER, 1));
        assertFalse(f.model.nextStep(.0005, DynamicSimuMethod.MODIFIED_EULER, 1));
    }

    @Test void activePllKeepsStoredAngleWhenTerminalAngleChanges() throws Exception {
        var f = fixture(false, false, .0005);
        assertTrue(f.algorithm.initialization());
        assertTrue(f.model.getData().pllGain() > 0);
        assertTrue(f.model.getData().pllIntegratorGain() > 0);
        var bus = f.model.getDStabBus();
        Complex voltage = bus.getVoltage();
        Complex source = (Complex) f.model.getOutputObject();
        bus.setVoltage(voltage.abs(), voltage.getArgument() + .03);
        Complex changed = (Complex) f.model.getOutputObject();
        assertEquals(source.getReal(), changed.getReal(), 1e-12);
        assertEquals(source.getImaginary(), changed.getImaginary(), 1e-12);
        assertEquals(voltage.getArgument(), f.model.getPllAngleState(), 1e-12);
    }

    @Test void bypassedPllFollowsTerminalAngleWithoutChangingStoredState(@TempDir Path temp)
            throws Exception {
        String source = Files.readString(CASE.resolve("SMIB_v33_wt3g2.dyr"));
        String zeroPll = source.replace("20.0 0.55 0.12", "0.0 0.0 0.12");
        assertNotEquals(source, zeroPll, "The test must change both PLL gains");
        Path dyr = temp.resolve("zero-pll.dyr");
        Files.writeString(dyr, zeroPll);
        var f = fixture(false, false, .0005, dyr);
        assertTrue(f.algorithm.initialization());
        assertEquals(0.0, f.model.getData().pllGain());
        assertEquals(0.0, f.model.getData().pllIntegratorGain());
        var bus = f.model.getDStabBus();
        Complex voltage = bus.getVoltage();
        Complex sourceCurrent = (Complex) f.model.getOutputObject();
        double stateAngle = f.model.getPllAngleState();
        double rotation = .03;
        bus.setVoltage(voltage.abs(), voltage.getArgument() + rotation);
        Complex changed = (Complex) f.model.getOutputObject();
        Complex expected = sourceCurrent.multiply(new Complex(Math.cos(rotation), Math.sin(rotation)));
        assertEquals(expected.getReal(), changed.getReal(), 1e-12);
        assertEquals(expected.getImaginary(), changed.getImaginary(), 1e-12);
        assertEquals(stateAngle, f.model.getPllAngleState(), 0.0);
    }

    @Test void foreignAttachedControllerIsRejectedBeforeIntegration() throws Exception {
        var f = fixture(true, true, .0005);
        var other = fixture(false, true, .0005);
        f.model.setElectricalController(other.model.getElectricalController());
        assertThrows(IllegalStateException.class, () -> f.algorithm.initialization());
    }

    @Test void projectionPreservesLimiterDirectionsAndReinitializationClearsPendingStage() throws Exception {
        var f = fixture(false, false, .0005);
        assertThrows(IllegalStateException.class, () -> f.model.evaluateStaggered(.0005, new double[5], 0));
        assertTrue(f.algorithm.initialization());
        double[] trial = state(f.model);
        trial[0] = 20; trial[2] = 20; trial[4] = .3;
        f.model.applyStaggeredState(trial, 0, .0005);
        assertEquals(0, f.model.getActiveCurrentState(), 0);
        assertEquals(f.model.getData().pllMaximum(), f.model.getPllIntegralState(), 0);
        trial[2] = -20;
        f.model.applyStaggeredState(trial, 0, .0005);
        assertEquals(-f.model.getData().pllMaximum(), f.model.getPllIntegralState(), 0);
        assertTrue(f.model.nextStep(.0005, DynamicSimuMethod.MODIFIED_EULER, 0));
        assertTrue(f.model.initStates(f.model.getDStabBus()));
        assertFalse(f.model.nextStep(.0005, DynamicSimuMethod.MODIFIED_EULER, 1));
        f.model.evaluateStaggered(.0005, new double[5], 0);
        f.model.getParentGen().setDynamicGenDevice(null);
        assertThrows(IllegalStateException.class, () -> f.model.evaluateStaggered(.0005, new double[5], 0));
    }

    @Test void bothMethodsRunBareConverterFaultAndRefine() throws Exception {
        double[] euler = run(false, .0005);
        double[] staggered = run(true, .0005);
        double[] eulerFine = run(false, .00025);
        double[] staggeredFine = run(true, .00025);
        double coarse = distance(euler, staggered), fine = distance(eulerFine, staggeredFine);
        System.out.println("WT3G2 bare endpoint method difference coarse=" + coarse + " refined=" + fine);
        assertTrue(fine < coarse, "method difference must reduce under refinement");
        assertTrue(fine < 2e-6, "bare converter refined endpoint discrepancy");
    }

    private static double distance(double[] a, double[] b) {
        double result = 0;
        for (int i = 0; i < a.length; i++) result = Math.max(result, Math.abs(a[i] - b[i]));
        return result;
    }

    private static double[] run(boolean shared, double step) throws Exception {
        var f = fixture(shared, false, step);
        var network = f.algorithm.getNetwork();
        network.addDynamicEvent(DStabObjectFactory.createBusFaultEvent("Bus1", network,
                SimpleFaultCode.GROUND_3P, new Complex(0, .1), null, .05, .05), "Fault");
        assertTrue(f.algorithm.initialization());
        double initial = f.model.getActiveCurrentState();
        while (f.algorithm.getSimuTime() < .2 - step / 2) {
            assertTrue(f.algorithm.solveDEqnStep(true));
            for (double value : state(f.model)) assertTrue(Double.isFinite(value));
        }
        assertTrue(Math.abs(initial - f.model.getActiveCurrentState()) > 1e-5,
                "converter states must advance through the fault");
        return state(f.model);
    }
}
