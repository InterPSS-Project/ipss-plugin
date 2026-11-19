package com.interpss.dstab.controller.pss.ieee1992pss2b;

import static org.junit.jupiter.api.Assertions.*;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.apache.commons.math3.complex.Complex;
import com.interpss.dstab.controller.pss.ieee1992pss2a.Pss2aLeadLagBlock;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import com.interpss.dstab.algo.DynamicSimuMethod;
import com.interpss.dstab.controller.cml.field.adapt.CMLControlBlockAdapter;
import com.interpss.dstab.controller.cml.field.adapt.CMLControlBlock1stOrderAdapter;
import com.interpss.dstab.controller.cml.field.block.DelayControlBlock;
import com.interpss.dstab.controller.cml.wrapper.ControlBlockFieldAnWrapper;
import com.interpss.dstab.controller.pss.ieee1992pss2b.Ieee1992PSS2BStabilizer;
import com.interpss.dstab.controller.pss.ieee1992pss2b.Pss2bCompiledStage;

/** Shared integration uses the existing blocks without advancing predictor or measurement history. */
public class Pss2bSharedStateTest {
    @BeforeAll static void setup() { org.interpss.IpssCorePlugin.init(); }
    @Test void pureRatesMatchExistingBlockEquationsAcrossBypassesAndRampOrders() throws Exception {
        for (boolean bypass : new boolean[]{false, true})
            for (int[] order : new int[][]{{0, 0}, {2, 0}, {1, 2}, {2, 2}, {5, 1}}) {
                var shared = Pss2bCompiledStageTest.model(bypass, order[0], order[1]);
                var compatible = Pss2bCompiledStageTest.model(bypass, order[0], order[1]);
                int count = shared.staggeredStateCount();
                assertEquals(10 + order[0] * order[1], count);
                double[] states = new double[count + 4], rates = new double[count + 4];
                Arrays.fill(states, 77); Arrays.fill(rates, 99);
                shared.readStaggeredState(states, 2);
                for (int i = 0; i < count; i++) states[i + 2] += .0001 * Math.sin(i + 1);
                shared.applyStaggeredState(states, 2, .0005);
                compatible.applyStaggeredState(states, 2, .0005);
                for (var model : List.of(shared, compatible)) {
                    model.getMachine().setSpeed(1.003);
                    model.getMachine().setPe(.7);
                }
                var before = shared.getNamedStates();
                var history = storedRates(shared);
                shared.evaluateStaggered(.0005, rates, 2);
                assertTrue(compatible.nextStep(0, DynamicSimuMethod.MODIFIED_EULER, compatible.getMachine(), 0));
                assertArrayEquals(storedRates(compatible), Arrays.copyOfRange(rates, 2, 2 + count), 1e-12);
                assertEquals(compatible.getOutput(compatible.getMachine()), shared.getOutput(shared.getMachine()), 1e-12);
                assertEquals(before, shared.getNamedStates());
                assertArrayEquals(history, storedRates(shared), "Pure evaluation cannot replace predictor slopes");
                for (int repeat = 0; repeat < 3; repeat++) {
                    shared.evaluateStaggered(.0005, rates, 2);
                    shared.getOutput(shared.getMachine());
                    assertEquals(before, shared.getNamedStates());
                    assertArrayEquals(history, storedRates(shared));
                }
                assertEquals(77, states[0]); assertEquals(77, states[count + 3]);
                assertEquals(99, rates[0]); assertEquals(99, rates[count + 3]);
                var invalid = states.clone(); invalid[count] = Double.NaN;
                assertThrows(IllegalArgumentException.class, () -> shared.applyStaggeredState(invalid, 2, .0005));
                assertEquals(before, shared.getNamedStates(), "Reject the entire invalid trial before publishing any state");
            }
    }

    @Test void externalModifiedEulerUsingSharedEquationsMatchesExistingIntegration() throws Exception {
        for (boolean bypass : new boolean[]{false, true}) {
            var shared = Pss2bCompiledStageTest.model(bypass, 2, 2);
            var compatible = Pss2bCompiledStageTest.model(bypass, 2, 2);
            int count = shared.staggeredStateCount();
            double[] start = new double[count], first = new double[count], second = new double[count], trial = new double[count];
            double step = .0001;
            for (int tick = 0; tick < 80; tick++) {
                for (var model : List.of(shared, compatible)) model.getMachine().setSpeed(1 + .003 * Math.sin(tick * .17));
                shared.readStaggeredState(start, 0);
                shared.evaluateStaggered(step, first, 0);
                for (int i = 0; i < count; i++) trial[i] = start[i] + step * first[i];
                shared.applyStaggeredState(trial, 0, step);
                assertTrue(compatible.nextStep(step, DynamicSimuMethod.MODIFIED_EULER, compatible.getMachine(), 0));
                for (var model : List.of(shared, compatible)) model.getMachine().setSpeed(1 + .003 * Math.sin((tick + .5) * .17));
                shared.evaluateStaggered(step, second, 0);
                for (int i = 0; i < count; i++) trial[i] = start[i] + .5 * step * (first[i] + second[i]);
                shared.applyStaggeredState(trial, 0, step);
                assertTrue(compatible.nextStep(step, DynamicSimuMethod.MODIFIED_EULER, compatible.getMachine(), 1));
                compatible.readStaggeredState(second, 0);
                assertArrayEquals(second, trial, 2e-13, "One state owner and the same equations under both methods");
                assertEquals(compatible.getOutput(compatible.getMachine()), shared.getOutput(shared.getMachine()), 2e-12);
            }
        }
    }

    @Test void acceptedMeasurementHistoriesRemainSeparateFromTrialsAndDuplicateEndpoints() throws Exception {
        var model = Pss2bCompiledStageTest.model(false, 2, 1);
        model.getData().setIcs1(6); model.getData().setIcs2(6);
        var bus = model.getMachine().getDStabBus(); bus.setVoltage(Complex.ONE);
        assertTrue(model.initStates(bus, model.getMachine()));
        int count = model.staggeredStateCount();
        var state = new double[count]; var rates = new double[count];
        bus.setVoltage(new Complex(.99, 0)); model.acceptVoltageDerivativeEndpoint(.001, .001);
        assertEquals(-2., model.getInput1VoltageDerivative(), 1e-12);
        var histories = model.getNamedStates();
        model.readStaggeredState(state, 0);
        bus.setVoltage(new Complex(.8, 0));
        for (int i = 0; i < 3; i++) { model.evaluateStaggered(.001, rates, 0); model.applyStaggeredState(state, 0, .001); }
        assertEquals(histories, model.getNamedStates());
        model.synchronizeVoltageDerivativeMemory();
        assertEquals(.8, model.getInput1VoltageMemory()); assertEquals(-2., model.getInput2VoltageDerivative(), 1e-12);
        bus.setVoltage(new Complex(.81, 0)); model.acceptVoltageDerivativeEndpoint(.002, .001);
        assertEquals(.4, model.getInput1VoltageDerivative(), 1e-12);
        var accepted = model.getNamedStates();
        model.acceptVoltageDerivativeEndpoint(.002, .001);
        assertEquals(accepted, model.getNamedStates());
        assertFalse(model.averageStaggeredRatesAtDiscontinuity());
    }

    @Test void pendingPredictorsAndChangedGraphsRequireCoherentReinitialization() throws Exception {
        var model = Pss2bCompiledStageTest.model(false, 2, 1);
        int count = model.staggeredStateCount(); var values = new double[count];
        assertTrue(model.nextStep(.0005, DynamicSimuMethod.MODIFIED_EULER, model.getMachine(), 0));
        assertThrows(IllegalStateException.class, () -> model.readStaggeredState(values, 0));
        assertTrue(model.nextStep(.0005, DynamicSimuMethod.MODIFIED_EULER, model.getMachine(), 1));
        model.readStaggeredState(values, 0);
        model.invalidateRuntimePlan();
        assertThrows(IllegalStateException.class, () -> model.evaluateStaggered(.0005, values, 0));
        assertTrue(model.initStates(model.getMachine().getDStabBus(), model.getMachine()));
        model.readStaggeredState(values, 0);
        assertThrows(IllegalArgumentException.class, () -> model.evaluateStaggered(Double.NaN, values, 0));
        var wrapper = (ControlBlockFieldAnWrapper) model.inputPath1.getFieldWrapperList().stream()
                .filter(w -> w.getFieldName().equals("delayBlock")).findFirst().orElseThrow();
        var custom = new DelayControlBlock(1, .07) { };
        assertTrue(custom.initStateY0(0));
        model.invalidateRuntimePlan(); wrapper.setField(custom);
        assertThrows(IllegalStateException.class, model::staggeredStateCount);
    }

    @Test void warmedSharedExecutionReusesAllNumericalBuffers() throws Exception {
        var model = Pss2bCompiledStageTest.model(false, 2, 2);
        int count = model.staggeredStateCount(); var states = new double[count]; var rates = new double[count];
        for (int i = 0; i < 20000; i++) group(model, states, rates);
        var bean = java.lang.management.ManagementFactory.getThreadMXBean();
        if (bean instanceof com.sun.management.ThreadMXBean allocation && allocation.isThreadAllocatedMemorySupported()) {
            allocation.setThreadAllocatedMemoryEnabled(true);
            long thread = Thread.currentThread().threadId(), before = allocation.getThreadAllocatedBytes(thread);
            for (int i = 0; i < 100000; i++) group(model, states, rates);
            long bytes = allocation.getThreadAllocatedBytes(thread) - before;
            System.out.println("PSS2B warmed shared-state allocation bytes=" + bytes);
            assertTrue(bytes < 4096, "Shared numerical execution must not allocate per evaluation");
        }
    }

    @Test void signalBusAndSampledSelectorChangesRequireReinitialization() throws Exception {
        var model = Pss2bCompiledStageTest.model(false, 2, 1);
        int count = model.staggeredStateCount(); var values = new double[count];
        model.getData().setIcs1(6);
        assertThrows(IllegalStateException.class, () -> model.readStaggeredState(values, 0));
        assertTrue(model.initStates(model.getMachine().getDStabBus(), model.getMachine()));
        model.readStaggeredState(values, 0);
        var local = model.getMachine().getDStabBus();
        var remote = (com.interpss.dstab.BaseDStabBus<?, ?>) local.getNetwork().getBus("Bus2");
        model.setInputSignalBuses(remote, local);
        assertThrows(IllegalStateException.class, () -> model.evaluateStaggered(.0005, values, 0));
        assertTrue(model.initStates(local, model.getMachine()));
        model.evaluateStaggered(.0005, values, 0);
        assertEquals(remote.getVoltageMag(), model.getInput1VoltageMemory());
    }

    @Test void customBlocksAndChangedOwnersCannotEnterTheSharedPlan() throws Exception {
        var model = Pss2bCompiledStageTest.model(false, 2, 1);
        var wrapper = (ControlBlockFieldAnWrapper) model.inputPath1.getFieldWrapperList().stream()
                .filter(w -> w.getFieldName().equals("delayBlock")).findFirst().orElseThrow();
        var custom = new DelayControlBlock(1, .07) { };
        assertTrue(custom.initStateY0(0)); wrapper.setField(custom);
        assertThrows(IllegalArgumentException.class, model::staggeredStateCount);
        var standard = Pss2bCompiledStageTest.model(false, 2, 1);
        int count = standard.staggeredStateCount(); var values = new double[count];
        standard.readStaggeredState(values, 0);
        var before = standard.getNamedStates();
        standard.inputPath1 = standard.inputPath2;
        assertThrows(IllegalStateException.class, () -> standard.applyStaggeredState(values, 0, .0005));
        assertEquals(before, standard.getNamedStates(), "Invalid ownership must fail before any trial write");
    }

    @Test void preparedModifiedEulerDispatchAlsoProtectsItsPendingPredictor() throws Exception {
        var model = Pss2bCompiledStageTest.model(false, 2, 1);
        int count = model.staggeredStateCount(); var states = new double[count];
        var stage = new Pss2bCompiledStage(model);
        stage.prepare(true, .0005, true); stage.advance(true, .0005); stage.release();
        assertThrows(IllegalStateException.class, () -> model.readStaggeredState(states, 0));
        stage.prepare(false, .0005, true); stage.advance(false, .0005); stage.release();
        model.readStaggeredState(states, 0);
    }

    @Test void acceptedMeasurementAndLiveMixingRefreshOutputWithoutAdvancingStates() throws Exception {
        for (int slot : new int[]{1, 2}) {
            var shared = Pss2bCompiledStageTest.model(false, 1, 2);
            var compatible = Pss2bCompiledStageTest.model(false, 1, 2);
            for (var model : List.of(shared, compatible)) {
                model.getData().setIcs1(slot == 1 ? 6 : 1); model.getData().setIcs2(slot == 2 ? 6 : 3);
                model.getData().setT6(0); model.getData().setT7(0);
                model.getMachine().getDStabBus().setVoltage(Complex.ONE);
                assertTrue(model.initStates(model.getMachine().getDStabBus(), model.getMachine()));
            }
            compatible.compileRuntimePlan();
            shared.evaluateStaggered(.001, new double[shared.staggeredStateCount()], 0);
            double previousOutput = shared.getOutput(shared.getMachine());
            var slopes = storedRates(shared);
            for (var model : List.of(shared, compatible)) {
                model.getMachine().getDStabBus().setVoltage(new Complex(1 - 1e-8, 0));
                model.acceptVoltageDerivativeEndpoint(.001, .001);
            }
            var accepted = shared.getNamedStates();
            double expected = compatible.getOutput(compatible.getMachine());
            assertTrue(Math.abs(expected - previousOutput) > 1e-10, "The accepted measurement must exercise feedthrough");
            assertEquals(expected, shared.getOutput(shared.getMachine()), 2e-12,
                    "Accepted derivative memory must be visible at this endpoint, not one step later");
            for (var model : List.of(shared, compatible)) { model.setRefPoint(.00001); model.ks3 = .6; model.ks4 = .8; }
            assertEquals(compatible.getOutput(compatible.getMachine()), shared.getOutput(shared.getMachine()), 2e-12);
            assertEquals(accepted, shared.getNamedStates());
            assertArrayEquals(slopes, storedRates(shared), "Output refresh cannot replace predictor history");
        }
    }

    @Test void inputPathsRetainSeparateDiscontinuityPolicies() throws Exception {
        for (int slot : new int[]{1, 2}) {
            var model = Pss2bCompiledStageTest.model(false, 2, 1);
            model.getData().setIcs1(slot == 1 ? 6 : 3); model.getData().setIcs2(slot == 2 ? 6 : 3);
            assertTrue(model.initStates(model.getMachine().getDStabBus(), model.getMachine()));
            for (int i = 0; i < 3; i++) assertEquals(slot != 1, model.averageStaggeredRateAtDiscontinuity(i));
            for (int i = 3; i < 6; i++) assertEquals(slot != 2, model.averageStaggeredRateAtDiscontinuity(i));
            assertThrows(IndexOutOfBoundsException.class, () -> model.averageStaggeredRateAtDiscontinuity(-1));
        }
    }

    private static void group(Ieee1992PSS2BStabilizer model, double[] states, double[] rates) {
        model.readStaggeredState(states, 0); model.evaluateStaggered(.0005, rates, 0);
        model.applyStaggeredState(states, 0, .0005); model.getOutput(model.getMachine());
    }

    private static double[] storedRates(Ieee1992PSS2BStabilizer model) {
        var blocks = new ArrayList<CMLControlBlockAdapter>();
        for (var owner : List.of(model.inputPath1, model.inputPath2))
            for (String name : List.of("firstWashout", "secondWashout", "delayBlock")) blocks.add(block(owner, name));
        blocks.addAll(((com.interpss.dstab.controller.cml.field.block.FilterNthOrderBlock) block(model, "rampFilter")).getIntegrationBlocks());
        for (String name : List.of("leadLag1", "leadLag2", "leadLag3", "outputBlock")) blocks.add(block(model, name));
        return blocks.stream().mapToDouble(block -> block instanceof Pss2aLeadLagBlock terminal
                ? terminal.getStateDerivative() : ((CMLControlBlock1stOrderAdapter) block).getDX_dt()).toArray();
    }

    private static CMLControlBlockAdapter block(com.interpss.dstab.controller.cml.annotate.AbstractAnnotateController owner,
            String name) {
        return (CMLControlBlockAdapter) owner.getFieldWrapperList().stream().filter(w -> w.getFieldName().equals(name))
                .findFirst().orElseThrow().getField();
    }
}
