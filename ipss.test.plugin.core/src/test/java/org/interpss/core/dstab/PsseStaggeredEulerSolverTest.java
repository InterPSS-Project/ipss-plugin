package org.interpss.core.dstab;

import static org.junit.jupiter.api.Assertions.*;

import org.interpss.IpssCorePlugin;
import org.interpss.dstab.algo.PsseStaggeredEulerSolver;
import org.interpss.dstab.control.exc.ieee.y1968.type1.Ieee1968Type1Exciter;
import org.interpss.dstab.control.exc.ieee.y1968.type2.Ieee1968Type2Exciter;
import org.interpss.fadapter.psse.PSSEMultiFileLoader;
import org.junit.jupiter.api.Test;

import com.interpss.dstab.StaticLoadModel;
import com.interpss.dstab.algo.DynamicSimuAlgorithm;
import com.interpss.dstab.algo.DynamicSimuMethod;
import com.interpss.dstab.cache.StateMonitor;

class PsseStaggeredEulerSolverTest {
    @Test void roundRotorRetainsLastInjectionMeasurementsWhenSelected() throws Exception {
        assertLastInjectionBoundary("SMIB_v33_genrou.dyr");
    }

    @Test void salientPoleRetainsLastInjectionMeasurementsWhenSelected() throws Exception {
        assertLastInjectionBoundary("SMIB_v33_gensal.dyr");
    }

    private void assertLastInjectionBoundary(String dynamicFile) throws Exception {
        var algo = loadSubtransient(dynamicFile);
        var solver = new PsseStaggeredEulerSolver(algo);
        assertEquals(PsseStaggeredEulerSolver.MachineMeasurementStage.LAST_NETWORK_INJECTION,
                solver.getMachineMeasurementStage());
        assertThrows(NullPointerException.class, () -> solver.setMachineMeasurementStage(null));
        solver.setMachineMeasurementStage(PsseStaggeredEulerSolver.MachineMeasurementStage.LAST_NETWORK_INJECTION);
        algo.setSolver(solver);
        assertTrue(algo.initialization());
        assertThrows(IllegalStateException.class, () -> solver.setMachineMeasurementStage(
                PsseStaggeredEulerSolver.MachineMeasurementStage.ACCEPTED_VOLTAGE));
        var machine = (com.interpss.dstab.mach.impl.DynamicMachineImpl)
                algo.getNetwork().getMachine("Bus1-mach1");
        machine.getDStabBus().setVoltage(machine.getDStabBus().getVoltage().multiply(.85));
        machine.getIxy();
        double[] injection = statorCache(machine), initial = differentialStates(machine);
        solver.setNetworkSolutionTolerance(.5);
        solver.setMinimumNetworkIterations(1);
        solver.setReuseNetworkEndpoint(true);
        assertTrue(solver.networkSolutionStep());
        assertEquals(1, solver.getLastNetworkSolutionDiagnostics().iterations());
        assertArrayEquals(injection, statorCache(machine), 1.e-12,
                "the explicit injection boundary must survive a converged network solve");
        assertArrayEquals(initial, differentialStates(machine), 0.);
        machine.getIxy();
        double[] accepted = statorCache(machine);
        double largest = 0.;
        for (int i = 0; i < accepted.length; i++) largest = Math.max(largest, Math.abs(accepted[i] - injection[i]));
        assertTrue(largest > .01, "the fixture must discriminate the two measurement boundaries");
    }

    @Test void roundRotorCurrentsMatchTheAcceptedNetworkVoltage() throws Exception {
        assertStatorCacheMatchesAcceptedVoltage(loadSubtransient("SMIB_v33_genrou.dyr",
                PsseStaggeredEulerSolver.MachineMeasurementStage.ACCEPTED_VOLTAGE));
    }

    @Test void salientPoleCurrentsMatchTheAcceptedNetworkVoltage() throws Exception {
        assertStatorCacheMatchesAcceptedVoltage(loadSubtransient("SMIB_v33_gensal.dyr",
                PsseStaggeredEulerSolver.MachineMeasurementStage.ACCEPTED_VOLTAGE));
    }

    private void assertStatorCacheMatchesAcceptedVoltage(DynamicSimuAlgorithm algo) throws Exception {
        var machine = (com.interpss.dstab.mach.impl.DynamicMachineImpl)
                algo.getNetwork().getMachine("Bus1-mach1");
        machine.getDStabBus().setVoltage(machine.getDStabBus().getVoltage().multiply(.85));
        machine.getIxy();
        double[] initial = differentialStates(machine);
        var shared = (PsseStaggeredEulerSolver) algo.getSolver();
        // Accept a visible voltage update in one iteration to expose a stale
        // pre-solve cache; this tolerance is local to the endpoint-contract test.
        shared.setNetworkSolutionTolerance(.5);
        shared.setMinimumNetworkIterations(1);
        assertTrue(shared.networkSolutionStep());
        assertTrue(shared.getLastNetworkSolutionDiagnostics().converged());
        assertEquals(1, shared.getLastNetworkSolutionDiagnostics().iterations());
        double[] cached = statorCache(machine);
        machine.getIxy();
        assertArrayEquals(statorCache(machine), cached, 1.e-12,
                "derivatives must see currents and air-gap power at the accepted voltage");
        assertArrayEquals(initial, differentialStates(machine), 0.,
                "an algebraic endpoint refresh must not advance differential states");
    }

    private double[] statorCache(com.interpss.dstab.mach.impl.DynamicMachineImpl machine) {
        if (machine instanceof com.interpss.dstab.mach.impl.RoundRotorMachineImpl round) {
            var state = round.captureRoundElectricalState();
            return new double[]{state.Id(), state.Iq(), machine.getPe()};
        }
        var state = ((com.interpss.dstab.mach.impl.SalientPoleMachineImpl) machine)
                .captureSalientElectricalState();
        return new double[]{state.Id(), state.Iq(), machine.getPe()};
    }

    private double[] differentialStates(com.interpss.dstab.mach.impl.DynamicMachineImpl machine) {
        if (machine instanceof com.interpss.dstab.mach.impl.RoundRotorMachineImpl round)
            return new double[]{round.getEq1(), round.getPsikd(), round.getPsikq(),
                    round.getEd1(), round.getAngle(), round.getSpeed()};
        var salient = (com.interpss.dstab.mach.impl.SalientPoleMachineImpl) machine;
        return new double[]{salient.getEq1(), salient.getPsikd(), salient.getPsiq11(),
                salient.getAngle(), salient.getSpeed()};
    }

    @Test void salientElectricalRefreshPreservesAnExternallyAdvancedQState() throws Exception {
        var algo = loadSalient();
        var machine = (com.interpss.dstab.mach.impl.SalientPoleMachineImpl)
                algo.getNetwork().getMachine("Bus1-mach1");
        double accepted = machine.getPsiq11() + .01;
        machine.setPsiq11(accepted);
        for (int i = 0; i < 3; i++) {
            assertTrue(machine.nextStepElectrical(0., DynamicSimuMethod.MODIFIED_EULER,
                    algo.getNetwork(), 0));
            assertEquals(accepted, machine.getPsiq11(), 0.,
                    "algebraic refresh must retain the shared integrator's q-axis state");
        }
        machine.getIxy();
        assertTrue(machine.nextStepElectrical(0., DynamicSimuMethod.MODIFIED_EULER,
                algo.getNetwork(), 0));
        var state = machine.captureSalientElectricalState();
        assertEquals(accepted, state.x5Psiq11(), 0.);
        assertEquals((-accepted + (machine.getXq11() - machine.getXq()) * state.Iq())
                / machine.getTq011(), state.dx5Psiq11(), 1.e-12);
    }

    @Test void salientQStateAdvancesDuringTheSharedNetworkIntegration() throws Exception {
        var algo = loadSalient();
        var machine = (com.interpss.dstab.mach.impl.SalientPoleMachineImpl)
                algo.getNetwork().getMachine("Bus1-mach1");
        double initial = machine.getPsiq11();
        machine.setPm(machine.getPm() + .1);
        for (int i = 0; i < 200; i++) assertTrue(algo.solveDEqnStep(true));
        assertTrue(Math.abs(machine.getPsiq11() - initial) > 1.e-6,
                "the damper state must respond to the changing rotor/current boundary");
        assertTrue(Double.isFinite(machine.getPe()));
    }

    private DynamicSimuAlgorithm loadSalient() throws Exception {
        return loadSubtransient("SMIB_v33_gensal.dyr");
    }

    private DynamicSimuAlgorithm loadSubtransient(String dynamicFile) throws Exception {
        return loadSubtransient(dynamicFile, PsseStaggeredEulerSolver.MachineMeasurementStage.LAST_NETWORK_INJECTION);
    }

    private DynamicSimuAlgorithm loadSubtransient(String dynamicFile,
            PsseStaggeredEulerSolver.MachineMeasurementStage stage) throws Exception {
        IpssCorePlugin.init();
        String base = "testData/adpter/psse/v33/SMIB/";
        var context = new PSSEMultiFileLoader().loadDStab(
                base + "SMIB_v33.raw", base + dynamicFile);
        var algo = context.getDynSimuAlgorithm();
        assertTrue(algo.getAclfAlgorithm().loadflow());
        var solver = new PsseStaggeredEulerSolver(algo);
        solver.setMachineMeasurementStage(stage);
        algo.setSolver(solver);
        algo.setSimuMethod(DynamicSimuMethod.MODIFIED_EULER);
        algo.setSimuStepSec(.0005);
        algo.setTotalSimuTimeSec(.1);
        algo.setSimuOutputHandler(new StateMonitor());
        assertTrue(algo.initialization());
        return algo;
    }

    @Test void endpointReuseSkipsOnlyOrdinaryInitialSolve() throws Exception {
        var algo = load();
        var calls = new java.util.concurrent.atomic.AtomicInteger();
        var stages = new java.util.ArrayList<String>();
        var shared = new PsseStaggeredEulerSolver(algo) {
            @Override public boolean networkSolutionStep() throws com.interpss.dstab.common.DStabSimuException {
                stages.add(networkSolutionStage);
                calls.incrementAndGet();return super.networkSolutionStep();
            }
        };
        algo.setSolver(shared);
        assertFalse(shared.isReuseNetworkEndpoint());
        assertTrue(algo.initialization());
        stages.clear();calls.set(0);assertTrue(algo.solveDEqnStep(true));assertEquals(2,calls.get());
        assertEquals(java.util.List.of("INITIAL", "DIRECT"), stages);
        shared.setReuseNetworkEndpoint(true);
        stages.clear();calls.set(0);assertTrue(algo.solveDEqnStep(true));assertEquals(1,calls.get());
        assertEquals(java.util.List.of("DIRECT"), stages);
        shared.notifyDiscontinuity();
        stages.clear();calls.set(0);assertTrue(algo.solveDEqnStep(true));assertEquals(2,calls.get());
        assertEquals(java.util.List.of("INITIAL", "DIRECT"), stages);
        algo.getNetwork().setYMatrixDirty(true);
        calls.set(0);assertTrue(algo.solveDEqnStep(true));assertEquals(2,calls.get());
        calls.set(0);assertTrue(algo.solveDEqnStep(true));assertEquals(1,calls.get());
    }

    @Test void initialSolveRestoresStageWhenTheNetworkThrows() throws Exception {
        var algo = load();
        class FailingSolver extends PsseStaggeredEulerSolver {
            boolean fail;
            FailingSolver() { super(algo); }
            String stage() { return networkSolutionStage; }
            @Override public boolean networkSolutionStep() throws com.interpss.dstab.common.DStabSimuException {
                if (fail) {
                    assertEquals("INITIAL", networkSolutionStage);
                    throw new com.interpss.dstab.common.DStabSimuException("Initial network failure");
                }
                return super.networkSolutionStep();
            }
        }
        var shared = new FailingSolver();algo.setSolver(shared);
        assertTrue(algo.initialization());shared.fail = true;
        String before = shared.stage();
        assertThrows(com.interpss.dstab.common.DStabSimuException.class,
                () -> shared.nextStep(0., algo.getSimuStepSec(), DynamicSimuMethod.MODIFIED_EULER));
        assertEquals(before, shared.stage());
    }

    @Test void eventConvergenceRebasesTheAcceptedAngleBeforeFrequencyIntegration() throws Exception {
        IpssCorePlugin.init();
        String base="testData/adpter/psse/v33/renewable/";
        var context=new PSSEMultiFileLoader().loadDStab(
                base+"regca_reeca_repca_bus1062.raw",base+"regca_reeca_repca_bus1062.dyr");
        var algo=context.getDynSimuAlgorithm();var network=context.getDStabilityNet();
        network.setBypassDataCheck(true);network.setAllowGenWithoutMach(true);
        assertTrue(algo.getAclfAlgorithm().loadflow());
        var events = new java.util.concurrent.atomic.AtomicInteger();
        var shared=new PsseStaggeredEulerSolver(algo) {
            @Override protected void rebaseDynamicModelInputs(DynamicSimuMethod method)
                    throws com.interpss.dstab.common.DStabSimuException {
                super.rebaseDynamicModelInputs(method);
                events.incrementAndGet();
                for (var bus : network.getBusList()) {
                    if (bus.getBusFreqMeasureBlock() instanceof
                            com.interpss.dstab.controller.deqn.measure.BusFreqMeasurementImpl frequency) {
                        var state=frequency.captureModelState();
                        double mismatch=Math.IEEEremainder(
                                state.angle0()+state.lastInput()-bus.getVoltageAng(), 2*Math.PI);
                        assertEquals(0., mismatch, 1e-12,
                                "Rebase after nonlinear convergence, before frequency integration: "+bus.getId());
                    }
                }
            }
        };
        shared.setReuseNetworkEndpoint(true);algo.setSolver(shared);
        algo.setSimuMethod(DynamicSimuMethod.MODIFIED_EULER);algo.setSimuStepSec(.00025);
        algo.setTotalSimuTimeSec(.006);algo.setSimuOutputHandler(new StateMonitor());
        network.addDynamicEvent(com.interpss.dstab.DStabObjectFactory.createBusFaultEvent(
                "Bus2",network,com.interpss.core.acsc.fault.SimpleFaultCode.GROUND_3P,
                new org.apache.commons.math3.complex.Complex(0,.2),null,.002,.002),"FrequencyBoundary");
        assertTrue(algo.initialization());
        var converter=(org.interpss.dstab.renewable.Regca1Model)
                ((com.interpss.dstab.DStabGen)network.getBus("Bus1").getContributeGen("1")).getDynamicGenDevice();
        converter.setNetworkIterationHighVoltageEnabled(true);
        for (int i=0;i<24;i++) assertTrue(algo.solveDEqnStep(true));
        assertEquals(2, events.get());
    }

    @Test void explicitDiscontinuityRebasesRenewableBundleOnlyOnTheNextStep() throws Exception {
        IpssCorePlugin.init();
        String base="testData/adpter/psse/v33/renewable/";
        var context=new PSSEMultiFileLoader().loadDStab(
                base+"regca_reeca_repca_bus1062.raw",base+"regca_reeca_repca_bus1062.dyr");
        var algo=context.getDynSimuAlgorithm();
        context.getDStabilityNet().setBypassDataCheck(true);
        context.getDStabilityNet().setAllowGenWithoutMach(true);
        assertTrue(algo.getAclfAlgorithm().loadflow());
        var shared=new PsseStaggeredEulerSolver(algo);
        algo.setSolver(shared);
        algo.setSimuMethod(DynamicSimuMethod.MODIFIED_EULER);
        algo.setSimuStepSec(.00025);
        algo.setTotalSimuTimeSec(.002);
        algo.setSimuOutputHandler(new StateMonitor());
        assertTrue(algo.initialization());
        var converter=(org.interpss.dstab.renewable.Regca1Model)
                ((com.interpss.dstab.DStabGen)algo.getNetwork().getBus("Bus1").getContributeGen("1")).getDynamicGenDevice();
        var electrical=converter.getReeca1Controller();
        var models=java.util.List.of(converter,electrical,electrical.getPlantController());
        assertTrue(algo.solveDEqnStep(true));
        shared.notifyDiscontinuity();
        shared.notifyDiscontinuity();
        assertTrue(algo.solveDEqnStep(true));
        for(var model:models) assertEquals(
                org.interpss.dstab.control.util.IntegrationStateMonitor.Stage.TWO_STEP_EVENT,
                ((org.interpss.dstab.control.util.IntegrationStateAccess)model).getStateDerivativeSnapshot().stage());
        assertTrue(algo.solveDEqnStep(true));
        for(var model:models) assertEquals(
                org.interpss.dstab.control.util.IntegrationStateMonitor.Stage.TWO_STEP,
                ((org.interpss.dstab.control.util.IntegrationStateAccess)model).getStateDerivativeSnapshot().stage());
    }

    @Test void classicalSourceAdvancesAlongsideTheExtendedControllerStack() throws Exception {
        IpssCorePlugin.init();
        String base="testData/adpter/psse/v33/SMIB/";
        var context=new PSSEMultiFileLoader().loadDStab(
                base+"SMIB_v33.raw",base+"SMIB_v33_genrou_esst4b.dyr");
        var algo=context.getDynSimuAlgorithm();
        var source=context.getDStabilityNet().getMachine("Bus2-mach1");
        source.setH(2.);
        assertTrue(algo.getAclfAlgorithm().loadflow());
        var shared=new PsseStaggeredEulerSolver(algo);
        algo.setSolver(shared);
        algo.setSimuMethod(DynamicSimuMethod.MODIFIED_EULER);
        algo.setSimuStepSec(.0005);
        algo.setTotalSimuTimeSec(.01);
        algo.setSimuOutputHandler(new StateMonitor());
        assertTrue(algo.initialization());
        assertEquals(2,shared.getBoundMachineCount());
        double initialAngle=source.getAngle();
        source.setPm(source.getPm()+.01);
        for (int i=0;i<20;i++) assertTrue(algo.solveDEqnStep(true));
        assertTrue(source.getSpeed()>1.,"classical source responds to mechanical-power input");
        assertTrue(source.getAngle()>initialAngle,"classical rotor angle advances");
        assertTrue(Double.isFinite(source.getPe()));
    }

    @Test void compoundFilterExposesEveryActualStateAtTheCommonEndpoint() {
        var block=new com.interpss.dstab.controller.cml.field.block.FilterNthOrderBlock(.2,.5,2,2);
        assertTrue(block.initStateU0(1.));
        var components=block.getIntegrationBlocks();
        assertEquals(4,components.size());
        assertThrows(UnsupportedOperationException.class,components::clear);
        double[] before=components.stream().mapToDouble(b->b.getStateX()).toArray();
        block.eulerStep1(2.,0.);
        assertArrayEquals(before,components.stream().mapToDouble(b->b.getStateX()).toArray(),0.);
        assertArrayEquals(new double[]{1.2,.48,.32,0.},
                components.stream().mapToDouble(b->b.getDX_dt()).toArray(),1.e-14);
    }
    private PsseStaggeredEulerSolver solver;
    private DynamicSimuAlgorithm load() throws Exception {
        IpssCorePlugin.init();
        String base="testData/adpter/psse/v30/IEEE39Bus/";
        var context=new PSSEMultiFileLoader().loadDStab(base+"IEEE39bus_v30.raw",base+"IEEE39bus.dyr");
        var algo=context.getDynSimuAlgorithm();
        var net=context.getDStabilityNet();
        net.setStaticLoadModel(StaticLoadModel.CONST_Z);
        net.setReactiveStaticLoadModel(StaticLoadModel.CONST_Z);
        algo.getAclfAlgorithm().setTolerance(1.e-10);
        algo.getAclfAlgorithm().setMaxIterations(50);
        assertTrue(algo.getAclfAlgorithm().loadflow());
        solver=new PsseStaggeredEulerSolver(algo);
        algo.setSolver(solver);
        algo.setSimuMethod(DynamicSimuMethod.MODIFIED_EULER);
        algo.setSimuStepSec(.001);
        algo.setTotalSimuTimeSec(2.);
        algo.setSimuOutputHandler(new StateMonitor());
        algo.setOutPutPerSteps(1000);
        return algo;
    }

    @Test void referenceJumpMatchesNativePsseFirstThreeStateSamples() throws Exception {
        var algo=load();
        assertTrue(algo.initialization());
        for (int i=0;i<1000;i++) assertTrue(algo.solveDEqnStep(true));
        var exc=(Ieee1968Type1Exciter)algo.getNetwork().getMachine("Bus36-mach1").getExciter();
        exc.setRefPoint(exc.getRefPoint()+.001);
        solver.notifyDiscontinuity();
        // Native PSS/E 36.7, unchanged IEEE39 case, +0.001 VREF at 1 s,
        // 1 ms step and 1e-6 network tolerance. The first two increments
        // discriminate the retained-history rule from predictor/corrector Euler.
        double[] nativeVr={2.24202132225,2.24440884590,2.24615454674};
        for (double expected:nativeVr) {
            assertTrue(algo.solveDEqnStep(true));
            assertEquals(expected,exc.getRegulatorOutput(),5.e-6);
        }
    }

    @Test void fixedStepAndModelAttachmentChangesFailBeforeAdvancingStates() throws Exception {
        var algo=load(); assertTrue(algo.initialization());
        var machine=algo.getNetwork().getMachine("Bus36-mach1");
        double angle=machine.getAngle();
        assertThrows(IllegalArgumentException.class,
                () -> solver.nextStep(0.,.002,DynamicSimuMethod.MODIFIED_EULER));
        assertEquals(angle,machine.getAngle());
        machine.getControllerList().remove(machine.getExciter());
        machine.getControllerList().add(new Ieee1968Type2Exciter());
        assertThrows(IllegalStateException.class,
                () -> solver.nextStep(0.,.001,DynamicSimuMethod.MODIFIED_EULER));
        assertEquals(angle,machine.getAngle());
    }

    @Test void unsupportedControllerAndInvalidNumericalSettingsAreRejected() throws Exception {
        var algo=load();
        assertEquals(1.e-6,solver.getNetworkSolutionTolerance());
        assertThrows(IllegalArgumentException.class,()->solver.setNetworkSolutionTolerance(Double.NaN));
        assertThrows(IllegalArgumentException.class,()->solver.setNetworkSolutionTolerance(0.));
        var machine=algo.getNetwork().getMachine("Bus36-mach1");
        machine.getControllerList().remove(machine.getExciter());
        machine.getControllerList().add(new Ieee1968Type2Exciter());
        assertThrows(IllegalArgumentException.class,solver::initialization);
    }

    @Test void reinitializationResetsHistoryAndDisabledControllerDoesNotAdvance() throws Exception {
        var algo=load(); assertTrue(algo.initialization());
        var exc=(Ieee1968Type1Exciter)algo.getNetwork().getMachine("Bus36-mach1").getExciter();
        exc.setRefPoint(exc.getRefPoint()+.001); solver.notifyDiscontinuity();
        assertTrue(algo.solveDEqnStep(true));
        double first=exc.getRegulatorOutput();
        exc.setStatus(false);
        for (int i=0;i<3;i++) assertTrue(algo.solveDEqnStep(true));
        assertEquals(first,exc.getRegulatorOutput());
        exc.setStatus(true);
        algo.getNetwork().setDStabNetInitialized(false);
        assertTrue(algo.getAclfAlgorithm().loadflow());
        assertTrue(algo.initialization());
        exc.setRefPoint(exc.getRefPoint()+.001); solver.notifyDiscontinuity();
        assertTrue(algo.solveDEqnStep(true));
        assertEquals(first,exc.getRegulatorOutput(),2.e-8);
    }
}
