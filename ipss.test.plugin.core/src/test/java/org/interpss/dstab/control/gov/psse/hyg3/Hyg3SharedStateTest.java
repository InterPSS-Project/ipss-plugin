package org.interpss.dstab.control.gov.psse.hyg3;

import static org.junit.jupiter.api.Assertions.*;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import org.interpss.IpssCorePlugin;
import org.interpss.fadapter.psse.PSSEMultiFileLoader;
import org.interpss.dstab.control.util.IntegrationStateMonitor.Stage;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import com.interpss.dstab.algo.DynamicSimuMethod;

/** Shared equations and numerical storage retain accepted backlash and hysteresis histories. */
public class Hyg3SharedStateTest {
    private static final double H = .0005;
    private static final String[] NAMES = {"Input filter", "Integral", "Derivative lag", "Valve", "Gate",
            "Electrical power", "Water flow", "Double derivative 1", "Double derivative 2"};
    private static volatile double sink;
    @BeforeAll static void setup() { IpssCorePlugin.init(); }

    private static PsseHyg3Governor model(int mode, boolean bypass) throws Exception {
        Path root = Path.of("testData/adpter/psse/v33/HspModels");
        var context = new PSSEMultiFileLoader().loadDStab(root.resolve("network.raw").toString(),
                root.resolve("hyg3u1-" + mode + ".dyr").toString());
        var machine = context.getDStabilityNet().getMachine("Bus1-mach1");
        machine.setPm(.4); machine.setPe(.4); machine.setSpeed(1.);
        var governor = (PsseHyg3Governor) machine.getGovernor();
        if (bypass) {
            governor.getData().setTd(0.); governor.getData().setTf(0.);
            governor.getData().setTp(0.); governor.getData().setTt(0.); governor.getData().setTw(0.);
        }
        governor.configureIntegrationStep(H);
        assertTrue(governor.initStates(machine.getDStabBus(), machine));
        return governor;
    }

    @Test void pureRhsMatchesOrdinaryEquationsAndPreservesHistoryAcrossBothModesAndBypasses() throws Exception {
        for (int mode : new int[]{0, 1}) for (boolean bypass : new boolean[]{false, true}) {
            var shared = model(mode, bypass); var ordinary = model(mode, bypass);
            double[] values = new double[13], rates = new double[13];
            Arrays.fill(values, 71.); Arrays.fill(rates, 93.);
            shared.readStaggeredState(values, 2);
            for (int i = 0; i < 9; i++) values[i+2] += .00001 * Math.sin(i+1);
            for (var governor : List.of(shared, ordinary)) {
                governor.applyStaggeredState(values, 2, H);
                governor.getMachine().setSpeed(1.002); governor.getMachine().setPe(.43);
            }
            var states = shared.getIntegrationStates(); var histories = shared.getIntegrationHistories();
            shared.evaluateStaggered(H, rates, 2);
            assertTrue(ordinary.nextStep(0., DynamicSimuMethod.MODIFIED_EULER, ordinary.getMachine(), 0));
            var expected = ordinary.getStateDerivativeSnapshot();
            for (int i = 0; i < 9; i++) {
                assertEquals(expected.rates().get(NAMES[i]), rates[i+2], 0., NAMES[i]);
                assertEquals(states.get(NAMES[i]), values[i+2], 1e-15);
            }
            assertEquals(ordinary.getOutput(ordinary.getMachine()), shared.getOutput(shared.getMachine()), 0.);
            for (int repeat = 0; repeat < 10; repeat++) {
                shared.evaluateStaggered(H, rates, 2); shared.getK1Output(); shared.getK2SecondOutput();
                assertEquals(states, shared.getIntegrationStates()); assertEquals(histories, shared.getIntegrationHistories());
            }
            assertEquals(71., values[0]); assertEquals(71., values[12]);
            assertEquals(93., rates[0]); assertEquals(93., rates[12]);
            if (bypass) for (int index : new int[]{0, 2, 3, 5, 6, 7, 8}) assertEquals(0., rates[index+2], 0.);
        }
    }

    @Test void externalModifiedEulerMatchesOrdinaryIntegrationWithLiveInputsAndCommittedHistories() throws Exception {
        for (int mode : new int[]{0, 1}) for (boolean bypass : new boolean[]{false, true}) {
            var shared = model(mode, bypass); var ordinary = model(mode, bypass);
            double[] start = new double[9], a = new double[9], b = new double[9], trial = new double[9];
            for (int tick = 0; tick < 800; tick++) {
                for (var governor : List.of(shared, ordinary)) {
                    governor.getMachine().setSpeed(1. + .001 * Math.sin(tick * .017));
                    governor.getMachine().setPe(.4 + .01 * Math.cos(tick * .021));
                    governor.setAuxiliaryInput(.0001 * Math.sin(tick * .031));
                }
                shared.readStaggeredState(start, 0); shared.evaluateStaggered(H, a, 0);
                for (int i = 0; i < 9; i++) trial[i] = start[i] + a[i]*H;
                shared.applyStaggeredState(trial, 0, H); shared.evaluateStaggered(H, b, 0);
                for (int i = 0; i < 9; i++) trial[i] = start[i] + (a[i]+b[i])/2.*H;
                shared.applyStaggeredState(trial, 0, H); shared.completeStaggeredStep(H);
                ordinary.nextStep(H, DynamicSimuMethod.MODIFIED_EULER, ordinary.getMachine(), 0);
                ordinary.nextStep(H, DynamicSimuMethod.MODIFIED_EULER, ordinary.getMachine(), 1);
                for (String name : NAMES) assertEquals(ordinary.getIntegrationStates().get(name),
                        shared.getIntegrationStates().get(name), 2e-14, name);
                assertEquals(ordinary.getOutput(ordinary.getMachine()), shared.getOutput(shared.getMachine()), 2e-14);
                assertEquals(ordinary.getIntegrationHistories(), shared.getIntegrationHistories());
            }
        }
    }

    @Test void trialProjectionAndObservationCannotCommitBacklashOrHysteresis() throws Exception {
        var governor = model(1, false); var data = governor.getData();
        data.setDb2(10.); data.setFrequencyDeadbandConvention(PsseHyg3GovernorData.FrequencyDeadbandConvention.HZ_HYSTERESIS);
        data.setDbH(.036); data.setDbL(-.024); data.setEps(.012);
        double[] initial = new double[9], trial = new double[9], rates = new double[9];
        governor.readStaggeredState(initial, 0); System.arraycopy(initial, 0, trial, 0, 9);
        var histories = governor.getIntegrationHistories();
        governor.getMachine().setSpeed(1.002);
        trial[1] = 2.; trial[3] = 5.; trial[4] = 2.;
        governor.applyStaggeredState(trial, 0, H); governor.evaluateStaggered(H, rates, 0);
        assertEquals(data.getPmax(), governor.getIntegralState(), 0.);
        assertEquals(data.getPmax(), governor.getGatePosition(), 0.);
        assertEquals(data.getVelopen(), governor.getValveState(), 0.);
        assertTrue(rates[1] <= 0.); assertTrue(rates[3] <= 0.); assertTrue(rates[4] <= 0.);
        assertEquals(histories, governor.getIntegrationHistories());
        governor.applyStaggeredState(initial, 0, H);
        assertEquals(histories, governor.getIntegrationHistories());
        governor.applyStaggeredState(trial, 0, H); governor.completeStaggeredStep(H);
        var committed = governor.getIntegrationHistories();
        assertNotEquals(histories, committed); assertEquals(governor.getGateOutput(), committed.get("Committed gate output"), 0.);
        assertEquals(2., committed.get("Committed deadband mode"), 0.);
        assertThrows(UnsupportedOperationException.class, () -> committed.put("x", 1.));
        governor.getMachine().setSpeed(1.0005);
        governor.evaluateStaggered(H, rates, 0); assertEquals(committed, governor.getIntegrationHistories());
        governor.completeStaggeredStep(H); assertEquals(2., governor.getIntegrationHistories().get("Committed deadband mode"), 0.);
        governor.getMachine().setSpeed(1.0003); governor.completeStaggeredStep(H);
        assertEquals(1., governor.getIntegrationHistories().get("Committed deadband mode"), 0.);
        assertEquals(2., committed.get("Committed deadband mode"), 0., "Detached snapshot");
    }

    @Test void invalidTrialsAndUnfinishedPredictorRejectSharedAccessBeforeMutation() throws Exception {
        var governor = model(0, false); double[] values = new double[9], rates = new double[9];
        governor.readStaggeredState(values, 0); var original = governor.getIntegrationStates();
        values[0] = .5; values[8] = Double.NaN;
        assertThrows(IllegalArgumentException.class, () -> governor.applyStaggeredState(values, 0, H));
        assertEquals(original, governor.getIntegrationStates());
        assertThrows(IndexOutOfBoundsException.class, () -> governor.readStaggeredState(new double[8], 0));
        for (double step : new double[]{0., -.1, Double.NaN, Double.POSITIVE_INFINITY})
            assertThrows(IllegalArgumentException.class, () -> governor.evaluateStaggered(step, rates, 0));
        var machine = governor.getMachine(); governor.nextStep(H, DynamicSimuMethod.MODIFIED_EULER, machine, 0);
        assertThrows(IllegalStateException.class, () -> governor.evaluateStaggered(H, rates, 0));
        assertThrows(IllegalStateException.class, () -> governor.readStaggeredState(values, 0));
        governor.nextStep(H, DynamicSimuMethod.MODIFIED_EULER, machine, 1); governor.evaluateStaggered(H, rates, 0);
        governor.getData().setKg(-1.); assertFalse(governor.initStates(machine.getDStabBus(), machine));
        assertEquals(Stage.NOT_EVALUATED, governor.getStateDerivativeSnapshot().stage());
        assertThrows(IllegalStateException.class, () -> governor.evaluateStaggered(H, rates, 0));
        governor.getData().setKg(5.); assertTrue(governor.initStates(machine.getDStabBus(), machine));
        assertEquals(Stage.INITIALIZATION, governor.getStateDerivativeSnapshot().stage());
    }

    @Test void derivativeSnapshotsRetainEvaluationCoordinatesAndDoNotAdvanceHistory() throws Exception {
        var governor = model(0, false); var snapshot = governor.getStateDerivativeSnapshot();
        assertEquals(Stage.INITIALIZATION, snapshot.stage()); assertEquals(9, snapshot.rates().size());
        snapshot.rates().values().forEach(rate -> assertEquals(0., rate, 1e-14));
        var before = governor.getIntegrationStates(); var history = governor.getIntegrationHistories();
        governor.getMachine().setSpeed(1.001); double[] rates = new double[9]; governor.evaluateStaggered(H, rates, 0);
        var evaluated = governor.getStateDerivativeSnapshot();
        assertEquals(Stage.STAGGERED, evaluated.stage()); assertEquals(before, evaluated.evaluationStates());
        assertEquals(history, governor.getIntegrationHistories()); assertEquals(before, snapshot.evaluationStates());
        for (int i = 0; i < 9; i++) assertEquals(rates[i], evaluated.rates().get(NAMES[i]), 0.);
        assertThrows(UnsupportedOperationException.class, () -> evaluated.rates().clear());
    }

    @Test void replacingDataOwnerRequiresCoherentReinitialization() throws Exception {
        var governor = model(1, false); double[] rates = new double[9];
        java.lang.reflect.Field owner = null;
        for (Class<?> type = governor.getClass(); type != null && owner == null; type = type.getSuperclass()) {
            try { owner = type.getDeclaredField("_data"); } catch (NoSuchFieldException inherited) { }
        }
        assertNotNull(owner); owner.setAccessible(true); owner.set(governor, new PsseHyg3GovernorData());
        assertThrows(IllegalStateException.class, () -> governor.evaluateStaggered(H, rates, 0));
        var machine = governor.getMachine(); assertTrue(governor.initStates(machine.getDStabBus(), machine));
        governor.evaluateStaggered(H, rates, 0);
        assertEquals(1., governor.getTurbineHead(), 1e-14);
    }

    @Test void warmedBothMethodUpdatesReuseNumericalBuffers() throws Exception {
        var bean = java.lang.management.ManagementFactory.getThreadMXBean();
        org.junit.jupiter.api.Assumptions.assumeTrue(bean instanceof com.sun.management.ThreadMXBean);
        var allocations = (com.sun.management.ThreadMXBean) bean;
        org.junit.jupiter.api.Assumptions.assumeTrue(allocations.isThreadAllocatedMemorySupported());
        allocations.setThreadAllocatedMemoryEnabled(true);
        for (boolean staggered : new boolean[]{false, true}) for (int mode : new int[]{0, 1}) {
            var governor = model(mode, false); double[] states = new double[9], rates = new double[9];
            for (int i = 0; i < 80000; i++) update(governor, staggered, states, rates);
            long thread = Thread.currentThread().threadId(), start = allocations.getThreadAllocatedBytes(thread);
            for (int i = 0; i < 100000; i++) update(governor, staggered, states, rates);
            long bytes = allocations.getThreadAllocatedBytes(thread)-start;
            System.out.println("HYG3 reusable updates mode="+mode+" staggered="+staggered+" allocated="+bytes);
            assertTrue(bytes <= 1024, "Reusable numerical loop allocated "+bytes);
            assertTrue(Double.isFinite(sink));
        }
    }

    private static void update(PsseHyg3Governor governor, boolean staggered, double[] states, double[] rates) {
        if (staggered) {
            governor.readStaggeredState(states, 0); governor.evaluateStaggered(H, rates, 0);
            governor.applyStaggeredState(states, 0, H); governor.completeStaggeredStep(H);
        } else {
            governor.nextStep(H, DynamicSimuMethod.MODIFIED_EULER, governor.getMachine(), 0);
            governor.nextStep(H, DynamicSimuMethod.MODIFIED_EULER, governor.getMachine(), 1);
        }
        sink = governor.getOutput(governor.getMachine());
    }
}
