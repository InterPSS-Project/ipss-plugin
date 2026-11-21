package org.interpss.core.dstab;

import static org.junit.jupiter.api.Assertions.*;

import java.lang.management.ManagementFactory;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Arrays;
import java.util.Map;
import java.util.zip.ZipInputStream;

import org.interpss.CorePluginTestSetup;
import org.interpss.dstab.algo.StaggeredEulerSolver;
import com.interpss.dstab.controller.runtime.IntegrationStateMonitor.Stage;
import com.interpss.dstab.controller.runtime.StaggeredEulerParticipant;
import com.interpss.dstab.svc.Svsmo3t2Model;
import org.interpss.fadapter.psse.PSSEMultiFileLoader;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.interpss.dstab.StaticLoadModel;
import com.interpss.dstab.algo.DynamicSimuMethod;
import com.interpss.dstab.cache.StateMonitor;
import com.interpss.simu.SimuContext;

/** Direct shared-state integration, without an alternate set of controller equations. */
public class Svsmo3t2StaggeredSmibTest extends CorePluginTestSetup {
    private static SimuContext load(Path folder) throws Exception {
        Files.createDirectories(folder);
        try (var stream = Svsmo3t2StaggeredSmibTest.class.getResourceAsStream("/dynamic-models/svsmo3t2/mss-reactors.zip")) {
            assertNotNull(stream);
            try (var zip = new ZipInputStream(stream)) {
                java.util.zip.ZipEntry entry;
                while ((entry = zip.getNextEntry()) != null) {
                    if (entry.getName().equals("close-reactor-pair/smib.raw") || entry.getName().equals("close-reactor-pair/smib.dyr"))
                        Files.copy(zip, folder.resolve(Path.of(entry.getName()).getFileName()));
                }
            }
        }
        var context = new PSSEMultiFileLoader().loadDStab(folder.resolve("smib.raw").toString(), folder.resolve("smib.dyr").toString());
        var net = context.getDStabilityNet();
        var algo = context.getDynSimuAlgorithm();
        net.setStaticLoadIncludedInYMatrix(true);
        net.setStaticLoadModel(StaticLoadModel.CONST_Z);
        net.setReactiveStaticLoadModel(StaticLoadModel.CONST_Z);
        algo.getAclfAlgorithm().setTolerance(1e-10);
        algo.getAclfAlgorithm().getLfAdjAlgo().setLocalContinuousSvcAsPv(true);
        algo.getAclfAlgorithm().getLfAdjAlgo().getVoltAdjConfig().setAdjTolerance(1e-9);
        assertTrue(algo.getAclfAlgorithm().loadflow());
        algo.setSimuStepSec(.0005);
        algo.setSimuMethod(DynamicSimuMethod.MODIFIED_EULER);
        algo.setSimuOutputHandler(new StateMonitor());
        return context;
    }

    private static Svsmo3t2Model model(SimuContext context) {
        return (Svsmo3t2Model) context.getDStabilityNet().getDStabBus("Bus3").getDynamicBusDeviceList().stream()
                .filter(Svsmo3t2Model.class::isInstance).findFirst().orElseThrow();
    }

    @Test void sixStoredCoordinatesBindAndFlatStartStaysAtEquilibrium(@TempDir Path folder) throws Exception {
        var context = load(folder);
        var model = model(context);
        assertThrows(IllegalStateException.class, () -> model.evaluateStaggered(.0005, new double[6], 0));
        var algo = context.getDynSimuAlgorithm();
        var solver = new StaggeredEulerSolver(algo);
        assertEquals(StaggeredEulerSolver.EndpointTimePrecision.DOUBLE, solver.getEndpointTimePrecision());
        assertThrows(NullPointerException.class, () -> solver.setEndpointTimePrecision(null));
        algo.setSolver(solver);
        assertTrue(algo.initialization());
        assertEquals(2, solver.getBoundMachineCount());
        assertEquals(10, solver.getBoundDifferentialStateCount());
        assertEquals(Stage.STAGGERED, model.getStateDerivativeSnapshot().stage());
        var before = model.getNamedStates();
        for (int i = 0; i < 40; i++) assertTrue(algo.solveDEqnStep(true));
        for (var state : before.entrySet()) assertEquals(state.getValue(), model.getNamedStates().get(state.getKey()), 1e-9, state.getKey());
        assertEquals(algo.getSimuTime(), model.getIntegrationTime(), 0.);
        assertEquals(0, model.getMssController().completionCount());
        assertThrows(IllegalStateException.class, () -> solver.setEndpointTimePrecision(StaggeredEulerSolver.EndpointTimePrecision.SINGLE));
        model.setDStabBus(null);
        assertThrows(IllegalStateException.class, () -> solver.nextStep(algo.getSimuTime(), .0005, DynamicSimuMethod.MODIFIED_EULER));
    }

    @Test void localSampledFiringRetainsEventRatesAcrossDiscardAndShortInterval(@TempDir Path folder) throws Exception {
        var retried = load(folder.resolve("retried"));
        var clean = load(folder.resolve("clean"));
        double nominalStep = .005;
        double fragment = .00001;
        double[] expected = new double[2];
        int trial = 0;
        for (var context : List.of(retried, clean)) {
            var algo = context.getDynSimuAlgorithm();
            algo.setSimuStepSec(nominalStep);
            assertTrue(algo.initialization());
            var device = model(context);
            assertEquals(nominalStep * 2, device.getData().firingTime(), 0);
            assertTrue(device.requiresPhysicalStepTrial(), "Positive short lag uses sampled firing in the ordinary solver");

            double[] left = new double[device.staggeredStateCount()];
            double[] right = left.clone();
            double[] initial = left.clone();
            device.evaluateStaggered(nominalStep, left, 0);
            double previousSensorRate = left[0];
            assertTrue(algo.solveDEqnStep(true));
            device.acceptNetworkEvent();
            device.evaluateStaggered(nominalStep, left, 0);
            var remote = device.getRemoteBus();
            remote.setVoltage(remote.getVoltage().multiply(.97));
            device.evaluateStaggered(nominalStep, right, 0);
            device.readStaggeredState(initial, 0);
            double eventSensorRate = .5 * (left[0] + right[0]);
            double expectedSensor = initial[0] + fragment * (eventSensorRate
                    + .5 * fragment / nominalStep * (eventSensorRate - previousSensorRate));

            if (trial == 0) {
                var before = device.getIntegrationHistories();
                double time = device.getIntegrationTime();
                device.beginPhysicalStepTrial(nominalStep, fragment);
                assertTrue(device.nextStep(fragment, DynamicSimuMethod.MODIFIED_EULER, 0));
                device.publishPhysicalStepTrial();
                device.discardPhysicalStepTrial();
                double[] restored = new double[initial.length];
                device.readStaggeredState(restored, 0);
                assertArrayEquals(initial, restored, 0);
                assertEquals(before, device.getIntegrationHistories());
                assertEquals(time, device.getIntegrationTime(), 0);
            }
            assertTrue(device.nextStep(fragment, DynamicSimuMethod.MODIFIED_EULER, 0));
            assertTrue(device.nextStep(fragment, DynamicSimuMethod.MODIFIED_EULER, 1));
            double[] after = new double[initial.length];
            device.readStaggeredState(after, 0);
            assertEquals(expectedSensor, after[0], 1e-12);
            expected[trial++] = after[0];
        }
        assertEquals(expected[0], expected[1], 0);
    }

    @Test void shortStaggeredLagProjectsTheNextCommandAtTheSolvedEndpoint(@TempDir Path folder) throws Exception {
        var context = load(folder.resolve("staggered"));
        var algo = context.getDynSimuAlgorithm();
        var device = model(context);
        double step = device.getData().firingTime() / 2;
        var solver = new StaggeredEulerSolver(algo);
        algo.setSolver(solver);
        algo.setSimuStepSec(step);
        assertTrue(algo.initialization());
        assertTrue(device.requiresPhysicalStepTrial());
        assertEquals(.01, device.getData().firingTime(), 0);

        double heldCurrent = device.getCurrent();
        device.setScheduledVoltage(device.getScheduledVoltage() + .005);
        assertTrue(solver.networkSolutionStep());
        double[] state = new double[device.staggeredStateCount()];
        device.readStaggeredState(state, 0);
        // The fixture's sensor has zero direct feedthrough and its gain block
        // has unit feedthrough. The small reference change leaves limits clear.
        var data = device.getData();
        assertEquals(0, data.tc1(), 0);
        assertEquals(data.tb2(), data.tc2(), 0);
        double error = device.getVoltageReference() - state[0] - data.xc0() * heldCurrent;
        assertEquals(state[1] + data.kp() * error, state[2], 1e-12);
        assertNotEquals(heldCurrent, state[2], 1e-5);
        assertEquals(heldCurrent, device.getCurrent(), 0);
        assertTrue(algo.solveDEqnStep(true));
        assertEquals(state[2], device.getCurrent(), 1e-10);

        context.getDStabilityNet().setDStabNetInitialized(false);
        algo.setSimuStepSec(step / 2);
        assertTrue(algo.initialization());
        assertFalse(device.requiresPhysicalStepTrial());

        var modified = load(folder.resolve("modified"));
        modified.getDynSimuAlgorithm().setSimuStepSec(step);
        assertTrue(modified.getDynSimuAlgorithm().initialization());
        assertTrue(model(modified).requiresPhysicalStepTrial());
        modified.getDStabilityNet().setDStabNetInitialized(false);
        modified.getDynSimuAlgorithm().setSimuStepSec(step / 2);
        assertTrue(modified.getDynSimuAlgorithm().initialization());
        assertFalse(model(modified).requiresPhysicalStepTrial());
    }

    @Test void trialsAndDerivativeReadsDoNotCommitDiscreteHistories(@TempDir Path folder) throws Exception {
        var context = load(folder);
        var algo = context.getDynSimuAlgorithm();
        algo.setSolver(new StaggeredEulerSolver(algo));
        assertTrue(algo.initialization());
        var model = model(context);
        model.setScheduledVoltage(model.getScheduledVoltage() - .08);
        double[] values = new double[10], rates = new double[11];
        Arrays.fill(values, 77); Arrays.fill(rates, 99);
        model.readStaggeredState(values, 2);
        var states = model.getNamedStates();
        var switching = model.getSwitchingNamedStates();
        var histories = model.getIntegrationHistories();
        assertThrows(UnsupportedOperationException.class, () -> histories.put("Tripped", 1.));
        for (int i = 0; i < 3; i++) model.evaluateStaggered(.0005, rates, 3);
        assertEquals(states, model.getNamedStates());
        assertEquals(switching, model.getSwitchingNamedStates());
        assertEquals(histories, model.getIntegrationHistories());
        assertEquals(77, values[0]); assertEquals(77, values[9]);
        assertEquals(99, rates[0]); assertEquals(99, rates[10]);
        model.applyStaggeredState(values, 2, .0005);
        assertEquals(switching, model.getSwitchingNamedStates());
        assertEquals(histories, model.getIntegrationHistories());
        assertEquals(0, model.getIntegrationTime());
        var projected = model.getNamedStates();
        var invalid = values.clone(); invalid[5] = Double.NaN;
        assertThrows(IllegalArgumentException.class, () -> model.applyStaggeredState(invalid, 2, .0005));
        assertEquals(projected, model.getNamedStates());
        assertThrows(IllegalArgumentException.class, () -> model.completeStaggeredStep(.0005, Double.NaN));
        model.completeStaggeredStep(0, 0);
        assertEquals(switching, model.getSwitchingNamedStates());
        assertEquals(histories, model.getIntegrationHistories());
        assertEquals(0, model.getIntegrationTime());
        assertTrue(model.nextStep(.0005, DynamicSimuMethod.MODIFIED_EULER, 0));
        assertThrows(IllegalStateException.class, model::acceptNetworkEvent);
        assertThrows(IllegalStateException.class, () -> model.readStaggeredState(values, 2));
        assertThrows(IllegalStateException.class, () -> model.completeStaggeredStep(.0005));
        assertTrue(model.nextStep(.0005, DynamicSimuMethod.MODIFIED_EULER, 1));
    }

    @Test void explicitEndpointPrecisionDoesNotChangeContinuousIntegrationWeights(@TempDir Path folder) throws Exception {
        Map<String, Double> expected = null;
        for (var precision : StaggeredEulerSolver.EndpointTimePrecision.values()) {
            var context = load(folder.resolve(precision.name()));
            var algo = context.getDynSimuAlgorithm();
            var solver = new StaggeredEulerSolver(algo);
            solver.setEndpointTimePrecision(precision);
            algo.setSolver(solver);
            assertTrue(algo.initialization());
            float clock = 0;
            for (int i = 0; i < 100; i++) { assertTrue(algo.solveDEqnStep(true)); clock += (float) .0005; }
            var model = model(context);
            assertEquals(precision == StaggeredEulerSolver.EndpointTimePrecision.SINGLE ? (double) clock : algo.getSimuTime(), model.getIntegrationTime(), 0.);
            if (expected == null) expected = model.getNamedStates(); else assertEquals(expected, model.getNamedStates());
        }
    }

    @Test void legacyCompletionCallbackDelegatesExactlyOnce() {
        int[] calls = {0};
        StaggeredEulerParticipant legacy = new StaggeredEulerParticipant() {
            @Override public int staggeredStateCount() { return 0; }
            @Override public void readStaggeredState(double[] values, int offset) { }
            @Override public void evaluateStaggered(double stepSec, double[] derivatives, int offset) { }
            @Override public void applyStaggeredState(double[] values, int offset, double stepSec) { }
            @Override public void completeStaggeredStep(double stepSec) { assertEquals(.1, stepSec); calls[0]++; }
        };
        legacy.completeStaggeredStep(.1, .3);
        assertEquals(1, calls[0]);
    }

    @Test void rejectedOrdinaryStagesPreserveTrialAndAcceptedHistories(@TempDir Path folder) throws Exception {
        var context = load(folder);
        var model = model(context);
        var method = DynamicSimuMethod.MODIFIED_EULER;
        assertFalse(model.nextStep(.0005, method, 0));
        assertTrue(context.getDynSimuAlgorithm().initialization());
        model.setScheduledVoltage(model.getScheduledVoltage() - .02);
        var initial = model.getNamedStates();
        var histories = model.getIntegrationHistories();
        assertFalse(model.nextStep(.0005, method, 1));
        assertEquals(initial, model.getNamedStates());
        var bus = model.getRemoteBus();
        var voltage = bus.getVoltage();
        bus.setVoltage(org.apache.commons.math3.complex.Complex.NaN);
        assertFalse(model.nextStep(.0005, method, 0));
        assertEquals(initial, model.getNamedStates());
        assertEquals(histories, model.getIntegrationHistories());
        bus.setVoltage(voltage);
        assertTrue(model.nextStep(.0005, method, 0));
        var predicted = model.getNamedStates();
        assertFalse(model.nextStep(.001, method, 1));
        assertFalse(model.nextStep(0, method, 0));
        assertFalse(model.nextStep(.0005, method, 0));
        assertEquals(predicted, model.getNamedStates());
        bus.setVoltage(org.apache.commons.math3.complex.Complex.NaN);
        assertFalse(model.nextStep(.0005, method, 1));
        assertEquals(predicted, model.getNamedStates());
        assertEquals(histories, model.getIntegrationHistories());
        assertEquals(0, model.getIntegrationTime());
        bus.setVoltage(voltage);
        assertTrue(model.nextStep(.0005, method, 1));
        assertEquals(.0005, model.getIntegrationTime());
        double[] trial = new double[6];
        model.readStaggeredState(trial, 0);
        var accepted = model.getNamedStates();
        histories = model.getIntegrationHistories();
        trial[0] += .1;
        bus.setVoltage(org.apache.commons.math3.complex.Complex.NaN);
        assertThrows(IllegalStateException.class, () -> model.applyStaggeredState(trial, 0, .0005));
        assertEquals(accepted, model.getNamedStates());
        assertEquals(histories, model.getIntegrationHistories());
        bus.setVoltage(voltage);
    }

    @Test void initiallyInactiveOrReactivatedBusDevicesRequireCoherentInitialization(@TempDir Path folder) throws Exception {
        var context = load(folder);
        var algo = context.getDynSimuAlgorithm();
        var model = model(context);
        model.setStatus(false);
        assertNotNull(model.getStates(null).get(com.interpss.dstab.common.DStabOutSymbol.OUT_SYMBOL_BUS_DEVICE_ID));
        var solver = new StaggeredEulerSolver(algo);
        algo.setSolver(solver);
        assertTrue(algo.initialization());
        assertEquals(4, solver.getBoundDifferentialStateCount());
        assertTrue(algo.solveDEqnStep(true));
        model.setStatus(true);
        assertThrows(IllegalStateException.class, () -> solver.nextStep(algo.getSimuTime(), .0005, DynamicSimuMethod.MODIFIED_EULER));
        // Core initialization is cached independently of the solver's buffers.
        // Invalidate it when changing which dynamic devices need initialization.
        context.getDStabilityNet().setDStabNetInitialized(false);
        assertTrue(algo.initialization());
        assertEquals(10, solver.getBoundDifferentialStateCount());
        assertTrue(algo.solveDEqnStep(true));
        model.setStatus(false);
        assertTrue(algo.solveDEqnStep(true));
        model.setStatus(true);
        assertThrows(IllegalStateException.class, () -> solver.nextStep(algo.getSimuTime(), .0005, DynamicSimuMethod.MODIFIED_EULER));
    }

    @Test void coupledReinitializationAfterTripMatchesFreshOperatingPoint(@TempDir Path folder) throws Exception {
        for (boolean staggered : new boolean[]{false, true}) {
            var used = load(folder.resolve("used-" + staggered));
            var fresh = load(folder.resolve("fresh-" + staggered));
            var algo = used.getDynSimuAlgorithm();
            var clean = fresh.getDynSimuAlgorithm();
            if (staggered) {
                algo.setSolver(new StaggeredEulerSolver(algo));
                clean.setSolver(new StaggeredEulerSolver(clean));
            }
            assertTrue(algo.initialization());
            var device = model(used);
            var bus = device.getDStabBus();
            var voltage = bus.getVoltage();
            bus.setVoltage(new org.apache.commons.math3.complex.Complex(2, 0));
            for (int i = 0; i < 4000 && !device.isTripped(); i++) {
                assertTrue(device.nextStep(.0005, DynamicSimuMethod.MODIFIED_EULER, 0));
                assertTrue(device.nextStep(.0005, DynamicSimuMethod.MODIFIED_EULER, 1));
            }
            assertTrue(device.isTripped());
            device.acceptNetworkEvent();
            assertTrue(device.isNetworkDetached());
            bus.setVoltage(voltage);
            used.getDStabilityNet().setDStabNetInitialized(false);
            assertTrue(algo.getAclfAlgorithm().loadflow());
            assertTrue(algo.initialization());
            assertTrue(clean.initialization());
            assertFalse(device.isNetworkDetached());
            assertFalse(device.isTripped());
            var cleanDevice = model(fresh);
            for (int i = 0; i < 40; i++) {
                assertTrue(algo.solveDEqnStep(true));
                assertTrue(clean.solveDEqnStep(true));
                for (var entry : cleanDevice.getIntegrationStates().entrySet())
                    assertEquals(entry.getValue(), device.getIntegrationStates().get(entry.getKey()), 1e-9, entry.getKey());
                for (var entry : cleanDevice.getIntegrationHistories().entrySet()) {
                    double tolerance = entry.getKey().equals("Accepted physical current") ? 1e-12 : 0.;
                    assertEquals(entry.getValue(), device.getIntegrationHistories().get(entry.getKey()), tolerance, entry.getKey());
                }
                assertEquals(cleanDevice.getDStabBus().getVoltageMag(), bus.getVoltageMag(), 1e-9);
            }
        }
    }

    @Test void endpointClockRequiresARepresentablePositiveStep(@TempDir Path folder) throws Exception {
        var context = load(folder);
        var algo = context.getDynSimuAlgorithm();
        var solver = new StaggeredEulerSolver(algo);
        solver.setEndpointTimePrecision(StaggeredEulerSolver.EndpointTimePrecision.SINGLE);
        algo.setSolver(solver);
        algo.setSimuStepSec(Double.MIN_VALUE);
        assertThrows(IllegalArgumentException.class, algo::initialization);
    }

    @Test void sharedStateKernelReusesItsNumericalBuffers(@TempDir Path folder) throws Exception {
        var context = load(folder);
        var algo = context.getDynSimuAlgorithm();
        algo.setSolver(new StaggeredEulerSolver(algo));
        assertTrue(algo.initialization());
        var model = model(context);
        var bean = ManagementFactory.getThreadMXBean();
        if (bean instanceof com.sun.management.ThreadMXBean allocation && allocation.isThreadAllocatedMemorySupported()) {
            allocation.setThreadAllocatedMemoryEnabled(true);
            double[] values = new double[6], rates = new double[6];
            for (int i = 0; i < 20000; i++) { model.readStaggeredState(values, 0); model.evaluateStaggered(.0005, rates, 0); model.applyStaggeredState(values, 0, .0005); }
            long thread = Thread.currentThread().threadId(), before = allocation.getThreadAllocatedBytes(thread);
            for (int i = 0; i < 100000; i++) { model.readStaggeredState(values, 0); model.evaluateStaggered(.0005, rates, 0); model.applyStaggeredState(values, 0, .0005); }
            long bytes = allocation.getThreadAllocatedBytes(thread) - before;
            System.out.println("SVSMO3T2 shared-state warmed allocation bytes=" + bytes);
            assertTrue(bytes < 16384);
        }
    }
}
