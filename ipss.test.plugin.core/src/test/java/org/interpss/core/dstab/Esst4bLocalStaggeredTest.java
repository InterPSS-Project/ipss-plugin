package org.interpss.core.dstab;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import org.apache.commons.math3.complex.Complex;
import org.interpss.IpssCorePlugin;
import org.interpss.dstab.algo.DefaultDynamicSimulationSolver;
import com.interpss.dstab.controller.exc.ieee2005st4b.IEEE2005ST4BExciter;
import org.interpss.fadapter.psse.PSSEMultiFileLoader;
import org.junit.jupiter.api.Test;
import com.interpss.core.acsc.fault.SimpleFaultCode;
import com.interpss.dstab.DStabObjectFactory;
import com.interpss.dstab.algo.DynamicSimuAlgorithm;
import com.interpss.dstab.algo.DynamicSimuMethod;
import com.interpss.dstab.cache.StateMonitor;
import com.interpss.dstab.common.DStabSimuException;
import com.interpss.dstab.controller.cml.field.adapt.CMLControlBlock1stOrderAdapter;
import com.interpss.dstab.mach.Machine;

class Esst4bLocalStaggeredTest {
    private static final Path SMIB = Path.of("testData/adpter/psse/v33/SMIB");
    private static final double STEP = 1. / 300.;
    private static final DynamicSimuMethod METHOD = DynamicSimuMethod.MODIFIED_EULER;
    private static final String MACHINE_ID = "Bus1-mach1";

    @Test void selectedIntegrationRunsThroughScheduledFaultAndClearing() throws Exception {
        var ordinary = load(Set.of());
        var staggered = load(Set.of(MACHINE_ID));
        assertFalse(ordinary.exciter.requiresPhysicalStepTrial());
        assertTrue(staggered.exciter.requiresPhysicalStepTrial());
        assertEquals(snapshot(ordinary), snapshot(staggered));
        double largestDifference = 0.;
        for (int i = 0; i < 90; i++) {
            assertTrue(ordinary.algorithm.solveDEqnStep(true));
            assertTrue(staggered.algorithm.solveDEqnStep(true));
            largestDifference = Math.max(largestDifference,
                    Math.abs(ordinary.machine.getEfd() - staggered.machine.getEfd()));
            assertTrue(Double.isFinite(staggered.machine.getEfd()));
        }
        assertEquals(.3, staggered.algorithm.getSimuTime(), 1e-10);
        assertTrue(largestDifference > 1e-5);
    }

    @Test void discardedTrialPreservesEventSampleAndControllerHistory() throws Exception {
        for (double interval : new double[] {STEP, STEP / 4.}) {
            var retry = load(Set.of(MACHINE_ID));
            var clean = load(Set.of(MACHINE_ID));
            for (var f : List.of(retry, clean)) {
                advance(f, 0., STEP);
                f.exciter.captureLocalStaggeredPreEvent(STEP);
                f.exciter.setRefPoint(f.exciter.getRefPoint() + .02);
                f.exciter.confirmLocalStaggeredPostEvent(STEP);
            }
            var accepted = snapshot(retry);
            retry.exciter.beginPhysicalStepTrial(STEP, interval);
            stage(retry, interval, 0);
            assertNotEquals(accepted, snapshot(retry));
            stage(retry, interval, 1);
            retry.exciter.discardPhysicalStepTrial();
            retry.exciter.discardPhysicalStepTrial();
            assertEquals(accepted, snapshot(retry));
            for (var f : List.of(retry, clean)) advance(f, STEP, interval);
            assertEquals(snapshot(clean), snapshot(retry));
            for (var f : List.of(retry, clean)) advance(f, STEP + interval, STEP);
            assertEquals(snapshot(clean), snapshot(retry));
        }
    }

    @Test void dynamicInnerPiUsesAcceptedRatesAndRestoresRejectedTrial() throws Exception {
        var algorithm = prepare(Set.of(MACHINE_ID));
        var exciter = (IEEE2005ST4BExciter) algorithm.getNetwork().getMachine(MACHINE_ID).getExciter();
        exciter.getData().setKim(.5);
        assertTrue(algorithm.initialization());
        var f = fixture(algorithm);
        assertTrue(exciter.requiresPhysicalStepTrial());
        var inner = (CMLControlBlock1stOrderAdapter) exciter.getFieldWrapperList().stream()
                .filter(wrapper -> wrapper.getFieldName().equals("vmPIBlock"))
                .findFirst().orElseThrow().getField();
        var amplifier = (CMLControlBlock1stOrderAdapter) exciter.getFieldWrapperList().stream()
                .filter(wrapper -> wrapper.getFieldName().equals("taDelayBlock"))
                .findFirst().orElseThrow().getField();
        double initialInnerState = inner.getStateX();
        double initialInnerRate = .5 * amplifier.getStateX();
        exciter.setRefPoint(exciter.getRefPoint() + .02);
        advance(f, 0., STEP);
        assertEquals(initialInnerState + STEP * initialInnerRate, inner.getStateX(), 1e-13);
        double currentInnerRate = .5 * amplifier.getStateX();
        assertNotEquals(initialInnerRate, currentInnerRate);
        double acceptedInner = inner.getStateX();
        var accepted = snapshot(f);
        exciter.beginPhysicalStepTrial(STEP, STEP);
        stage(f, STEP, 0);
        double expected = acceptedInner + STEP * (1.5 * currentInnerRate - .5 * initialInnerRate);
        assertEquals(expected, inner.getStateX(), 1e-13);
        stage(f, STEP, 1);
        assertEquals(expected, inner.getStateX(), 1e-13);
        exciter.discardPhysicalStepTrial();
        assertEquals(accepted, snapshot(f));
        advance(f, STEP, STEP);
        assertEquals(expected, inner.getStateX(), 1e-13);
    }

    @Test void missingSelectionFailsInitialization() throws Exception {
        var algorithm = prepare(Set.of("Bus999-mach1"));
        assertFalse(algorithm.initialization());
    }

    @Test void solverDiscardsTheControllerTrialWhenTheCorrectorSolveFails() throws Exception {
        var algorithm = prepare(Set.of(MACHINE_ID), true);
        assertTrue(algorithm.initialization());
        var solver = (FailingSolver) algorithm.getSolver();
        var f = fixture(algorithm);
        assertTrue(algorithm.solveDEqnStep(true));
        double acceptedTime = algorithm.getSimuTime();
        f.exciter.setRefPoint(f.exciter.getRefPoint() + .02);
        solver.failCorrector = true;
        assertFalse(algorithm.solveDEqnStep(true));
        assertNotNull(solver.predictorStart);
        assertNotNull(solver.trialEndpoint);
        assertNotEquals(solver.predictorStart, solver.trialEndpoint);
        assertEquals(solver.predictorStart, snapshot(f));
        assertEquals(acceptedTime, algorithm.getSimuTime(), 0.);
    }

    private static void advance(Fixture f, double time, double interval) {
        f.exciter.beginPhysicalStepTrial(time, interval);
        stage(f, interval, 0);
        stage(f, interval, 1);
        f.exciter.validatePhysicalStepAcceptance();
        f.exciter.acceptPhysicalStepTrial();
    }

    private static void stage(Fixture f, double interval, int stage) {
        assertTrue(f.exciter.nextStep(interval, METHOD, f.machine, stage));
        f.machine.setEfd(f.exciter.getOutput(f.machine));
    }

    private static List<Double> snapshot(Fixture f) {
        var values = new ArrayList<Double>();
        values.add(f.machine.getEfd());
        for (var wrapper : f.exciter.getFieldWrapperList()) {
            if (wrapper.getField() instanceof CMLControlBlock1stOrderAdapter block) {
                values.add(block.getU());
                values.add(block.getStateX());
                values.add(block.getDX_dt());
            }
        }
        values.add(f.exciter.customBlock.getU());
        return values;
    }

    private static Fixture load(Set<String> ids) throws Exception {
        var algorithm = prepare(ids);
        assertTrue(algorithm.initialization());
        return fixture(algorithm);
    }

    private static Fixture fixture(DynamicSimuAlgorithm algorithm) {
        var machine = algorithm.getNetwork().getMachine(MACHINE_ID);
        return new Fixture(algorithm, machine, (IEEE2005ST4BExciter) machine.getExciter());
    }

    private static DynamicSimuAlgorithm prepare(Set<String> ids) throws Exception {
        return prepare(ids, false);
    }

    private static DynamicSimuAlgorithm prepare(Set<String> ids, boolean injectFailure) throws Exception {
        IpssCorePlugin.init();
        var context = new PSSEMultiFileLoader().loadDStab(
                SMIB.resolve("SMIB_v33.raw").toString(),
                SMIB.resolve("SMIB_v33_genrou_esst4b.dyr").toString());
        var algorithm = context.getDynSimuAlgorithm();
        assertTrue(algorithm.getAclfAlgorithm().loadflow());
        if (injectFailure) algorithm.setSolver(new FailingSolver(algorithm));
        var solver = assertInstanceOf(DefaultDynamicSimulationSolver.class, algorithm.getSolver());
        solver.setEsst4bLocalStaggeredMachineIds(ids);
        algorithm.setSimuMethod(METHOD);
        algorithm.setSimuStepSec(STEP);
        algorithm.setTotalSimuTimeSec(.3);
        algorithm.setSimuOutputHandler(new StateMonitor());
        var network = algorithm.getNetwork();
        network.addDynamicEvent(DStabObjectFactory.createBusFaultEvent("Bus1", network,
                SimpleFaultCode.GROUND_3P, new Complex(0., .2), null, .1, .05), "Fault");
        return algorithm;
    }

    private record Fixture(DynamicSimuAlgorithm algorithm, Machine machine, IEEE2005ST4BExciter exciter) {}

    private static final class FailingSolver extends DefaultDynamicSimulationSolver {
        private final DynamicSimuAlgorithm algorithm;
        boolean failCorrector;
        List<Double> predictorStart, trialEndpoint;

        FailingSolver(DynamicSimuAlgorithm algorithm) {
            super(algorithm);
            this.algorithm = algorithm;
        }

        @Override public void diffEqnIntegrationStep(double time, double dt, DynamicSimuMethod method,
                int flag) throws DStabSimuException {
            if (failCorrector && flag == 0) predictorStart = snapshot(fixture(algorithm));
            super.diffEqnIntegrationStep(time, dt, method, flag);
        }

        @Override protected void requireNetworkSolution(double start, double endpoint, String stage)
                throws DStabSimuException {
            if (failCorrector && "CORRECTOR".equals(stage)) {
                trialEndpoint = snapshot(fixture(algorithm));
                throw new DStabSimuException("Injected corrector failure");
            }
            super.requireNetworkSolution(start, endpoint, stage);
        }
    }
}
