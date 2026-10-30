package org.interpss.core.aclf;

import java.io.BufferedReader;
import java.io.StringReader;
import org.apache.commons.math3.complex.Complex;
import org.interpss.fadapter.matpower.MatpowerDirectParser;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import com.interpss.core.LoadflowAlgoObjectFactory;
import com.interpss.core.aclf.*;
import com.interpss.core.algo.LoadflowAlgorithm;
import com.interpss.core.aclf.impl.func.net.AclfNetJMatrixHelper;
import com.interpss.core.sparse.solver.SparseEqnSolverProvider;

/** Checks the public prepared path, structural invalidation, and generic fallback. */
class PreparedNewtonLoadflowTest {
    private static final String CASE = """
        function mpc = prepared_test
        mpc.baseMVA = 100;
        mpc.bus = [
        1 3 0 0 0 0 1 1 0 230 1 1.1 .9;
        2 2 0 0 0 0 1 1.02 0 230 1 1.1 .9;
        3 1 50 20 0 0 1 1 0 230 1 1.1 .9;
        4 2 0 0 0 0 1 1.01 0 230 1 1.1 .9;
        5 1 40 10 0 0 1 1 0 230 1 1.1 .9;
        ];
        mpc.gen = [
        1 10 0 999 -999 1 100 1 999 -999;
        2 50 0 100 -100 1.02 100 1 999 -999;
        3 10 0 100 -100 1 100 1 999 -999;
        4 30 0 100 -100 1.01 100 1 999 -999;
        ];
        mpc.branch = [
        1 2 .02 .15 0 0 0 0 0 0 1;
        2 3 .015 .12 0 0 0 0 0 0 1;
        3 4 .02 .1 0 0 0 0 0 0 1;
        4 5 .03 .15 0 0 0 0 0 0 1;
        5 1 .01 .1 0 0 0 0 0 0 1;
        2 5 .02 .2 0 0 0 0 0 0 1;
        1 4 .01 .15 0 0 0 0 0 0 1;
        ];
        """;

    private int previousBackend;
    @BeforeEach void selectJavaKlu() {
        previousBackend = AclfNetJMatrixHelper.sparseEqnSolverType;
        SparseEqnSolverProvider.useJavaKlu();
    }
    @AfterEach void restoreSparseBackend() {
        if (previousBackend == AclfNetJMatrixHelper.SparseEqnSolverKLU) SparseEqnSolverProvider.useJavaKlu();
        else SparseEqnSolverProvider.useCSJ();
    }

    private LoadflowAlgorithm create(boolean prepared) throws Exception {
        return create(prepared, CASE);
    }

    private LoadflowAlgorithm create(boolean prepared, String text) throws Exception {
        AclfNetwork net = new MatpowerDirectParser().parseFromReader(new BufferedReader(new StringReader(text)));
        var algorithm = LoadflowAlgoObjectFactory.createLoadflowAlgorithm(net);
        algorithm.setPreparedNewtonEnabled(prepared);
        algorithm.setInitBusVoltage(false); algorithm.setTolerance(1e-10); algorithm.setMaxIterations(30);
        algorithm.getLfAdjAlgo().setApplyAdjustAlgo(false);
        algorithm.getNrMethodConfig().setStepSizeOptimization(false);
        algorithm.getNrMethodConfig().setVariableUpdateLimit(false);
        net.getBusLoadLowVoltConfig().setApplyVoltAdjust(false);
        assertTrue(algorithm.initializeAclfNet());
        reset(algorithm);
        return algorithm;
    }

    private void reset(LoadflowAlgorithm algorithm) {
        for (var bus : algorithm.getNetwork().getBusList()) bus.setVoltage(
                bus.isGenPV() || bus.isSwing() ? bus.getDesiredVoltMag() : 1, 0);
    }

    private void compare(LoadflowAlgorithm fast, LoadflowAlgorithm reference) throws Exception {
        reset(fast); reset(reference);
        assertTrue(fast.loadflow(false)); assertTrue(reference.loadflow(false));
        assertTrue(fast.wasPreparedNewtonUsed(), "Positive fast-path coverage is required");
        assertFalse(reference.wasPreparedNewtonUsed());
        for (var bus : fast.getNetwork().getBusList()) {
            var expected = reference.getNetwork().getBus(bus.getId());
            assertEquals(expected.getVoltageMag(), bus.getVoltageMag(), 2e-9, bus.getId());
            assertEquals(expected.getVoltageAng(), bus.getVoltageAng(), 2e-9, bus.getId());
            for (int g = 0; g < bus.getContributeGenList().size(); g++) {
                assertEquals(expected.getContributeGenList().get(g).getGen().getReal(),
                        bus.getContributeGenList().get(g).getGen().getReal(), 2e-8);
                assertEquals(expected.getContributeGenList().get(g).getGen().getImaginary(),
                        bus.getContributeGenList().get(g).getGen().getImaginary(), 2e-8);
            }
        }
        assertTrue(fast.getTerminationReport().maxEquationMismatchPu() < 1e-10);
    }

    @Test void publicApiReusesOneOperatorAndReadsFreshInjections() throws Exception {
        var fast = create(true); var reference = create(false);
        compare(fast, reference); long builds = fast.getPreparedNewtonBuildCount();
        for (int repeat = 0; repeat < 5; repeat++) compare(fast, reference);
        assertEquals(builds, fast.getPreparedNewtonBuildCount());
        for (var algorithm : new LoadflowAlgorithm[]{fast, reference}) algorithm.getNetwork().getBus("Bus5")
                .getContributeLoadList().get(0).setLoadCP(new Complex(.6, .1));
        compare(fast, reference);
        assertEquals(builds, fast.getPreparedNewtonBuildCount(), "Only the RHS changed");
        for (var algorithm : new LoadflowAlgorithm[]{fast, reference}) algorithm.getNetwork().getBus("Bus5").setLoadP(.9);
        compare(fast, reference);
        assertEquals(.6, fast.getNetwork().getBus("Bus5").getLoadP(), 1e-12, "Contributing records remain authoritative");
        for (var algorithm : new LoadflowAlgorithm[]{fast, reference}) {
            var load = algorithm.getNetwork().getBus("Bus5").getContributeLoadList().get(0);
            load.setDistGenPower(new Complex(.1, .02)); load.setDistGenStatus(true);
        }
        compare(fast, reference);
        assertEquals(.5, fast.getNetwork().getBus("Bus5").getLoadP(), 1e-12);
        for (var algorithm : new LoadflowAlgorithm[]{fast, reference}) algorithm.getNetwork().getBus("Bus5")
                .getContributeLoadList().get(0).setStatus(false);
        compare(fast, reference);
        assertEquals(0, fast.getNetwork().getBus("Bus5").getLoadP(), 1e-12);
        assertEquals(builds, fast.getPreparedNewtonBuildCount());
    }

    @Test void changesPvPqIdentitiesEvenWhenTheCountsAreUnchanged() throws Exception {
        var fast = create(true); var reference = create(false); compare(fast, reference);
        long builds = fast.getPreparedNewtonBuildCount();
        for (var algorithm : new LoadflowAlgorithm[]{fast, reference}) {
            var oldPv = algorithm.getNetwork().getBus("Bus2"); oldPv.setGenCode(AclfGenCode.GEN_PQ); oldPv.setGenQ(.1);
            var newPv = algorithm.getNetwork().getBus("Bus3"); newPv.setGenCode(AclfGenCode.GEN_PV); newPv.setDesiredVoltMag(1);
        }
        compare(fast, reference);
        assertEquals(builds + 1, fast.getPreparedNewtonBuildCount());
    }

    @Test void refreshesCoefficientsAndRebuildsOnlyForTopology() throws Exception {
        var fast = create(true); var reference = create(false); compare(fast, reference);
        long builds = fast.getPreparedNewtonBuildCount();
        for (var algorithm : new LoadflowAlgorithm[]{fast, reference}) algorithm.getNetwork().getBus("Bus5").setShuntY(new Complex(0, .04));
        compare(fast, reference); assertEquals(builds, fast.getPreparedNewtonBuildCount());
        for (var algorithm : new LoadflowAlgorithm[]{fast, reference}) algorithm.getNetwork().getBranchList().get(0).setZ(new Complex(.025, .17));
        compare(fast, reference); assertEquals(builds, fast.getPreparedNewtonBuildCount());
        for (var algorithm : new LoadflowAlgorithm[]{fast, reference}) algorithm.getNetwork().getBranchList().get(0).setStatus(false);
        compare(fast, reference); assertEquals(++builds, fast.getPreparedNewtonBuildCount());
    }

    @Test void controlWorkflowAndRequestedLoggingRetainTheExistingNewtonLoop() throws Exception {
        var algorithm = create(true);
        algorithm.getLfAdjAlgo().setApplyAdjustAlgo(true);
        assertTrue(algorithm.loadflow(false));
        algorithm.getLfAdjAlgo().setApplyAdjustAlgo(false); reset(algorithm);
        algorithm.getMessageLog().setEnabled(true);
        assertTrue(algorithm.loadflow(false)); assertTrue(algorithm.wasPreparedNewtonUsed());
        assertFalse(algorithm.getMessageLog().getFormattedLines().isEmpty());
    }

    @Test void iterationFailureKeepsHonestStatusAndCanRecover() throws Exception {
        var algorithm = create(true); algorithm.setMaxIterations(0);
        assertFalse(algorithm.loadflow(false)); assertFalse(algorithm.getNetwork().isLfConverged());
        assertFalse(algorithm.getTerminationReport().converged());
        algorithm.setMaxIterations(30); reset(algorithm);
        assertTrue(algorithm.loadflow(false)); assertTrue(algorithm.wasPreparedNewtonUsed());
    }

    @Test void respectsTheExplicitSparseBackend() throws Exception {
        var algorithm = create(true); SparseEqnSolverProvider.useCSJ();
        assertTrue(algorithm.loadflow(false)); assertFalse(algorithm.wasPreparedNewtonUsed());
    }

    @Test void customBusObserversRetainTheIterationWorkflow() throws Exception {
        var algorithm = create(true);
        var notifications = new java.util.concurrent.atomic.AtomicInteger();
        algorithm.getNetwork().getBus("Bus3").eAdapters().add(new org.eclipse.emf.common.notify.impl.AdapterImpl() {
            @Override public void notifyChanged(org.eclipse.emf.common.notify.Notification notification) {
                notifications.incrementAndGet();
            }
        });
        assertTrue(algorithm.loadflow(false)); assertTrue(algorithm.wasPreparedNewtonUsed());
        assertTrue(notifications.get() > 0);
    }

    @Test void fullInitializationRefreshesAfterEditsWithNotificationsSuppressed() throws Exception {
        var fast = create(true); var reference = create(false); compare(fast, reference);
        long builds = fast.getPreparedNewtonBuildCount();
        for (var algorithm : new LoadflowAlgorithm[]{fast, reference}) {
            var branch = algorithm.getNetwork().getBranchList().get(0);
            branch.eSetDeliver(false);
            branch.setZ(new Complex(.03, .18));
            branch.eSetDeliver(true);
            reset(algorithm);
            assertTrue(algorithm.loadflow());
        }
        compare(fast, reference);
        assertEquals(builds, fast.getPreparedNewtonBuildCount(), "Muted numerical edits do not change the CSC pattern");
    }

    @Test void customNetworkObserversRetainConvergenceNotifications() throws Exception {
        var algorithm = create(true);
        var states = new java.util.ArrayList<Boolean>();
        algorithm.getNetwork().eAdapters().add(new org.eclipse.emf.common.notify.impl.AdapterImpl() {
            @Override public void notifyChanged(org.eclipse.emf.common.notify.Notification notification) {
                if (notification.getFeature() instanceof org.eclipse.emf.ecore.EStructuralFeature feature
                        && feature.getName().equals("lfConverged")) states.add(notification.getNewBooleanValue());
            }
        });
        assertTrue(algorithm.loadflow(false)); assertTrue(algorithm.wasPreparedNewtonUsed());
        assertTrue(states.contains(false)); assertEquals(true, states.get(states.size() - 1));
    }

    @Test void multiGeneratorAllocationAndLimitsMatchTheGenericWorkflow() throws Exception {
        String multiple = CASE.replace("2 50 0 100 -100 1.02 100 1 999 -999;", """
                2 25 0 3 -3 1.02 100 1 999 -999;
                2 25 0 100 -100 1.02 100 1 999 -999;
                2 30 0 100 -100 1.02 100 0 999 -999;
                """);
        var fast = create(true, multiple); var reference = create(false, multiple);
        compare(fast, reference); compare(fast, reference);
    }

    @Test void actualReactiveLimitTransitionKeepsTheConfiguredControlWorkflow() throws Exception {
        String limited = CASE.replace("2 50 0 100 -100 1.02", "2 50 0 2 -2 1.08");
        var algorithm = create(true, limited);
        algorithm.getLfAdjAlgo().setApplyAdjustAlgo(true);
        assertTrue(algorithm.loadflow());
        var bus = algorithm.getNetwork().getBus("Bus2");
        assertTrue(bus.getVoltageMag() < 1.07, "The Q limit must release the voltage target");
        assertEquals(.02, bus.getContributeGenList().get(0).getGen().getImaginary(), 1e-7);
    }
}
