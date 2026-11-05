package org.interpss.core.dstab;

import org.interpss.dstab.algo.StaggeredEulerSolver;

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
    @Test void acceptedEndpointInputChangeRetainsFirstLeftRateAndOlderHistory() throws Exception {
        var algo=loadSubtransient("SMIB_v33_genrou.dyr");
        var solver=(StaggeredEulerSolver)algo.getSolver();solver.setReuseNetworkEndpoint(true);
        var machine=(com.interpss.dstab.mach.impl.MachineImpl)algo.getNetwork().getMachine("Bus1-mach1");
        machine.setPm(machine.getPm()+.1);double older=0.;
        for(int i=0;i<20;i++) {
            older=machine.captureMechanicalIntegrationState().speedDerivative();
            assertTrue(algo.solveDEqnStep(true));
        }
        double left=machine.captureMechanicalIntegrationState().speedDerivative(),speed=machine.getSpeed(),power=machine.getPm(),h=algo.getSimuStepSec();
        solver.notifyAcceptedEndpointDiscontinuity();
        machine.setPm(power+.04);assertTrue(machine.nextStep(0.,DynamicSimuMethod.MODIFIED_EULER,0));
        solver.notifyAcceptedEndpointDiscontinuity();machine.setPm(power+.1);
        double right=left+.1/(2.*machine.getH()*speed);
        double expected=speed+h*(.75*(left+right)-.5*older);
        double previousOnly=speed+h*(.75*right+.25*older);
        assertTrue(Math.abs(expected-previousOnly)>1e-12,"Moving endpoint distinguishes accepted rates from retained older history");
        assertTrue(algo.solveDEqnStep(true));
        assertEquals(expected,machine.getSpeed(),1e-12,"Repeated notification and beforeStep preserve the first accepted left rate");
        double nextLeft=machine.captureMechanicalIntegrationState().speedDerivative();speed=machine.getSpeed();
        assertTrue(algo.solveDEqnStep(true));
        assertEquals(speed+h*(1.5*nextLeft-.25*(left+right)),machine.getSpeed(),1e-12,"Event blend is consumed once, retaining the averaged interval rate");
        var uninitialized=new StaggeredEulerSolver(algo);
        assertThrows(IllegalStateException.class,uninitialized::notifyAcceptedEndpointDiscontinuity);
    }
    @Test void acceptedEventRefreshesRoundRotorElectricalRatesWithoutAdvancingStates() throws Exception {
        assertAcceptedEventRefresh("SMIB_v33_genrou.dyr");
    }

    @Test void acceptedEventRefreshesSalientElectricalRatesWithoutAdvancingStates() throws Exception {
        assertAcceptedEventRefresh("SMIB_v33_gensal.dyr");
    }

    private void assertAcceptedEventRefresh(String file) throws Exception {
        var plain=loadSubtransient(file); var observed=loadSubtransient(file);
        for (var algo:java.util.List.of(plain,observed)) {
            var machine=(com.interpss.dstab.mach.impl.DynamicMachineImpl)algo.getNetwork().getMachine("Bus1-mach1");
            double[] states=differentialStates(machine);
            var mechanicalHistory=machine.captureMechanicalIntegrationState();
            double[] predictor=electricalPredictorCoordinates(machine);
            // Emulate acceptance of the final network correction without a new
            // injection request. This intentionally leaves the ordinary cache stale.
            machine.getDStabBus().setVoltage(machine.getDStabBus().getVoltage().multiply(.85));
            double[] stale=statorCache(machine);
            if(algo==observed)for(int i=0;i<3;i++)machine.getIgen();
            var solver=(StaggeredEulerSolver)algo.getSolver();
            solver.notifyAcceptedEndpointDiscontinuity();
            double[] accepted=statorCache(machine);
            assertTrue(Math.abs(stale[0]-accepted[0])+Math.abs(stale[1]-accepted[1])>1e-3);
            assertArrayEquals(states,differentialStates(machine),0.,"No state advances at the accepted event boundary");
            assertEquals(mechanicalHistory,machine.captureMechanicalIntegrationState(),"Mechanical predictor history is outside the electrical refresh");
            assertArrayEquals(predictor,electricalPredictorCoordinates(machine),0.,"Electrical predictor coordinates do not advance");
            if(machine instanceof com.interpss.dstab.mach.impl.RoundRotorMachineImpl round) {
                var e=round.captureRoundElectricalState();
                assertEquals((-round.getPsikd()-(round.getXd1()-round.getXl())*e.Id()+round.getEq1())/round.getTd011(),e.dx4Psid1(),1e-12);
            } else {
                var salient=(com.interpss.dstab.mach.impl.SalientPoleMachineImpl)machine;
                var e=salient.captureSalientElectricalState();
                assertEquals((-salient.getPsiq11()+(salient.getXq11()-salient.getXq())*e.Iq())/salient.getTq011(),e.dx5Psiq11(),1e-12);
            }
            // Idempotent notification must preserve the first accepted left rates.
            if(algo==observed)for(int i=0;i<3;i++){machine.getIgen();solver.notifyAcceptedEndpointDiscontinuity();}
            assertArrayEquals(accepted,statorCache(machine),0.);
            assertEquals(1,solver.getMachineEventElectricalRefreshCount(),"One electrical refresh per machine per event");
        }
        for(int i=0;i<40;i++) {
            assertTrue(plain.solveDEqnStep(true));assertTrue(observed.solveDEqnStep(true));
            var a=(com.interpss.dstab.mach.impl.DynamicMachineImpl)plain.getNetwork().getMachine("Bus1-mach1");
            var b=(com.interpss.dstab.mach.impl.DynamicMachineImpl)observed.getNetwork().getMachine("Bus1-mach1");
            assertArrayEquals(differentialStates(a),differentialStates(b),0.,"Observation cannot choose the event trajectory");
            assertEquals(a.getDStabBus().getVoltage(),b.getDStabBus().getVoltage());
        }
        assertEquals(1,((StaggeredEulerSolver)plain.getSolver()).getMachineEventElectricalRefreshCount(),"No event refresh work in ordinary steps");
        assertEquals(1,((StaggeredEulerSolver)observed.getSolver()).getMachineEventElectricalRefreshCount());
        assertEquals(2,((StaggeredEulerSolver)plain.getSolver()).getMachineEventMechanicalRefreshCount(),"No mechanical event work in ordinary intervals");
        assertEquals(2,((StaggeredEulerSolver)observed.getSolver()).getMachineEventMechanicalRefreshCount());
    }

    @Test void acceptedMachineRefreshDoesNotEvaluateExciterHistory() throws Exception {
        var algo=load();assertTrue(algo.initialization());
        for(int i=0;i<20;i++)assertTrue(algo.solveDEqnStep(true));
        var machine=algo.getNetwork().getMachine("Bus36-mach1");
        var exc=(Ieee1968Type1Exciter)machine.getExciter();
        double[] before={exc.getSensedVoltage(),exc.getRegulatorOutput(),exc.getFieldVoltageState(),exc.getRateFeedback(),machine.getEfd()};
        machine.getDStabBus().setVoltage(machine.getDStabBus().getVoltage().multiply(.9));
        solver.notifyAcceptedEndpointDiscontinuity();solver.notifyAcceptedEndpointDiscontinuity();
        double[] after={exc.getSensedVoltage(),exc.getRegulatorOutput(),exc.getFieldVoltageState(),exc.getRateFeedback(),machine.getEfd()};
        assertArrayEquals(before,after,0.,"Accepted machine refresh cannot evaluate controller equations/history");
    }

    @Test void acceptedMechanicalRatesUsePhysicalTorqueAndPreservePredictorHistory() throws Exception {
        for(String file:java.util.List.of("SMIB_v33_genrou.dyr","SMIB_v33_gensal.dyr"))
            for(boolean active:new boolean[]{true,false}) {
                var algo=loadSubtransient(file);var solver=(StaggeredEulerSolver)algo.getSolver();
                var dynamic=(com.interpss.dstab.mach.impl.MachineImpl)algo.getNetwork().getMachine("Bus1-mach1");
                var classical=(com.interpss.dstab.mach.impl.MachineImpl)algo.getNetwork().getMachine("Bus2-mach1");
                var history=new java.util.IdentityHashMap<com.interpss.dstab.mach.impl.MachineImpl,com.interpss.dstab.mach.impl.MachineImpl.MechanicalIntegrationState>();
                for(var m:java.util.List.of(dynamic,classical)) {
                    m.setStatus(active);m.setSpeed(1.003);m.setPm(m.getPm()+.1);
                    m.getDStabBus().setVoltage(m.getDStabBus().getVoltage().multiply(.85));
                    history.put(m,m.captureMechanicalIntegrationState());
                }
                solver.notifyAcceptedEndpointDiscontinuity();
                var owners=(java.util.List<?>)solverField(solver,"machines");
                int[] slots=(int[])solverField(solver,"machineMechanicalStateSlots");
                double[] left=(double[])solverField(solver,"machineEventLeft");
                boolean[] valid=(boolean[])solverField(solver,"machineEventValid");
                for(var m:java.util.List.of(dynamic,classical)) {
                    int slot=slots[owners.indexOf(m)];assertEquals(active,valid[slot]);
                    assertEquals(history.get(m),m.captureMechanicalIntegrationState(),"No mechanical predictor/history mutation");
                    assertEquals(1.003,m.getSpeed(),0.);
                    if(active) {
                        double damping=m.getD()*.01*algo.getNetwork().getFrequency();
                        double expected=m instanceof com.interpss.dstab.mach.impl.DynamicMachineImpl
                                ? ((m.getPm()-damping*(m.getSpeed()-1))/m.getSpeed()-m.getPe()-m.getPe_NegSeq())/(2*m.getH())
                                : (m.getPm()-m.getPe()-damping*(m.getSpeed()-1)-m.getPe_NegSeq())/(m.getSpeed()*2*m.getH());
                        assertEquals(expected,left[slot],1e-14,"Accepted physical torque balance");
                        assertEquals(2*Math.PI*algo.getNetwork().getFrequency()*(m.getSpeed()-1),left[slot+1],1e-14);
                    }
                }
                double[] captured=left.clone();
                for(var m:java.util.List.of(dynamic,classical))if(active){m.getIgen();m.getIgen();}
                solver.notifyAcceptedEndpointDiscontinuity();
                assertEquals(active?2:0,solver.getMachineEventMechanicalRefreshCount(),"One mechanical refresh per active source at first capture");
                assertArrayEquals(captured,(double[])solverField(solver,"machineEventLeft"),0.,"First event rate survives repeated observation/notification");
            }
    }

    private static Object solverField(StaggeredEulerSolver solver,String name) throws Exception {
        var field=PsseStaggeredEulerSolver.class.getDeclaredField(name);field.setAccessible(true);return field.get(solver);
    }

    @Test void roundRotorRetainsLastInjectionMeasurementsWhenSelected() throws Exception {
        assertLastInjectionBoundary("SMIB_v33_genrou.dyr");
    }

    @Test void salientPoleRetainsLastInjectionMeasurementsWhenSelected() throws Exception {
        assertLastInjectionBoundary("SMIB_v33_gensal.dyr");
    }

    private void assertLastInjectionBoundary(String dynamicFile) throws Exception {
        var algo = loadSubtransient(dynamicFile);
        var solver = new StaggeredEulerSolver(algo);
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

    private double[] electricalPredictorCoordinates(com.interpss.dstab.mach.impl.DynamicMachineImpl machine) {
        if(machine instanceof com.interpss.dstab.mach.impl.RoundRotorMachineImpl round) {
            var s=round.captureRoundElectricalState();
            return new double[]{s.x3Eq1(),s.x4Psid1(),s.x5Psiq1(),s.x6Ed1()};
        }
        var s=((com.interpss.dstab.mach.impl.SalientPoleMachineImpl)machine).captureSalientElectricalState();
        return new double[]{s.x3Eq1(),s.x4Psid1(),s.x5Psiq11()};
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
        var solver = new StaggeredEulerSolver(algo);
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
        var shared = new StaggeredEulerSolver(algo) {
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
        var shared=new StaggeredEulerSolver(algo) {
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
        var shared=new StaggeredEulerSolver(algo);
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
        var shared=new StaggeredEulerSolver(algo);
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
        solver=new StaggeredEulerSolver(algo);
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
