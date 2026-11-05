package org.interpss.core.dstab;

import static org.junit.jupiter.api.Assertions.*;
import java.lang.reflect.Proxy;
import java.util.Arrays;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import org.apache.commons.math3.complex.Complex;
import org.interpss.dstab.renewable.*;
import org.interpss.core.dstab.reference.ReecaNativeReference;
import org.interpss.core.dstab.reference.RepcaNativeReference;
import org.junit.jupiter.api.Test;
import com.interpss.dstab.*;
import com.interpss.dstab.algo.defaultImpl.DStabSolverImpl;
import com.interpss.dstab.device.NetworkIterationAware;

/** Algebraic memory is advanced by solver callbacks, never by observation. */
public class RegcaNetworkIterationTest {
    private static final NetworkIterationAware.Context CONTEXT =
            new NetworkIterationAware.Context("DIRECT", 0, 0, 100, 1e-4);

    @Test void savedVoltageAdvancesOnceAndAcceptedVoltageGetsOneFinalEvaluation() {
        var f = fixture();var model = f.model();
        var states = model.getIntegrationStates();
        var derivatives = model.getStateDerivativeSnapshot();
        f.bus().setVoltage(new Complex(1.4));
        model.setNetworkIterationHighVoltageEnabled(true);
        model.beginNetworkSolution(CONTEXT);
        assertEquals(1.34, model.getNetworkHighVoltageState().savedVoltage(), 1e-15);
        model.beforeNetworkIteration(0);
        assertEquals(1.364, model.getNetworkHighVoltageState().deceleratedVoltage(), 1e-15);
        assertEquals(.1148, model.getNetworkHighVoltageState().correction(), 1e-15);
        assertEquals(.5-.1148/1.364, model.getIq(), 1e-15);
        f.bus().setVoltage(new Complex(1.2));
        model.afterNetworkConvergence();
        model.endNetworkSolution(true);
        var memory = model.getNetworkHighVoltageState();
        assertEquals(1.34, memory.savedVoltage(), 1e-15);
        assertEquals(1.284, memory.deceleratedVoltage(), 1e-15);
        assertEquals(.1736, memory.correction(), 1e-15);
        assertEquals(.5-.1736/1.284, model.getIq(), 1e-15);
        for (int i=0;i<10;i++) {
            model.getIq();model.getOutputObject();model.getStates(null);model.getNamedStates();
            model.getIntegrationStates();model.getStateDerivativeSnapshot();model.updateAttributes(false);
        }
        assertEquals(memory, model.getNetworkHighVoltageState());
        assertEquals(states, model.getIntegrationStates());
        assertEquals(derivatives, model.getStateDerivativeSnapshot());
        f.control().iq = -.6;
        assertEquals(.6-.1736/1.284, model.getIq(), 1e-15,
                "Retained correction must be applied to the current regulator output");
        model.beginNetworkSolution(CONTEXT);
        assertEquals(1.284, model.getNetworkHighVoltageState().savedVoltage(), 1e-15,
                "A fresh solve at the same time still advances algebraic memory");
        assertEquals(0, model.getNetworkHighVoltageState().correction(), 0);
        model.endNetworkSolution(false);
        assertEquals(memory, model.getNetworkHighVoltageState());
    }

    @Test void abortedSolveRestoresMemoryAndReinitializationClearsIt() {
        var f = fixture();var model = f.model();
        model.setNetworkIterationHighVoltageEnabled(true);
        var before = model.getNetworkHighVoltageState();
        model.beginNetworkSolution(CONTEXT);model.beforeNetworkIteration(0);
        assertThrows(IllegalStateException.class, () -> model.setNetworkIterationHighVoltageEnabled(false));
        model.endNetworkSolution(false);
        assertEquals(before, model.getNetworkHighVoltageState());
        model.beginNetworkSolution(CONTEXT);model.beforeNetworkIteration(0);
        model.afterNetworkConvergence();model.endNetworkSolution(true);
        assertTrue(model.getNetworkHighVoltageState().outputAvailable());
        assertTrue(model.initStates(f.bus()));
        assertEquals(before, model.getNetworkHighVoltageState());
    }

    @Test void disabledModeRetainsInstantaneousEquationAndPureCallbacks() {
        var f = fixture();var model = f.model();
        var before = model.getNetworkHighVoltageState();
        double iq = model.getIq();
        model.beginNetworkSolution(CONTEXT);model.beforeNetworkIteration(0);
        model.afterNetworkConvergence();model.endNetworkSolution(true);
        assertEquals(before, model.getNetworkHighVoltageState());
        assertEquals(iq, model.getIq(), 0);
        f.bus().setVoltage(new Complex(1.1));
        assertEquals(.5, model.getIq(), 0);
        f.control().ip = .3;
        f.bus().setVoltage(new Complex(.6));
        assertEquals(.15, model.getIp(), 1e-15);
        model.setNetworkIterationHighVoltageEnabled(true);
        assertEquals(.15, model.getIp(), 1e-15,
                "Until a network evaluation is available, use the instantaneous boundary");
    }

    @Test void activeGainUsesDeceleratedVoltageAndObservationDoesNotAdvanceMemory() {
        var f = fixture();var model = f.model();
        f.control().ip = .3;
        model.setNetworkIterationHighVoltageEnabled(true);
        model.beginNetworkSolution(CONTEXT);
        // The retained 1.3 pu endpoint keeps the decelerated input at 0.94 pu.
        f.bus().setVoltage(new Complex(.4));
        model.beforeNetworkIteration(0);model.afterNetworkConvergence();model.endNetworkSolution(true);
        assertEquals(.94, model.getNetworkHighVoltageState().deceleratedVoltage(), 1e-15);
        assertEquals(.3, model.getIp(), 1e-15);
        model.beginNetworkSolution(CONTEXT);
        model.beforeNetworkIteration(0);model.afterNetworkConvergence();model.endNetworkSolution(true);
        assertEquals(.724, model.getNetworkHighVoltageState().deceleratedVoltage(), 1e-15);
        assertEquals(.243, model.getIp(), 1e-15);
        var memory = model.getNetworkHighVoltageState();
        var states = model.getIntegrationStates();
        var derivatives = model.getStateDerivativeSnapshot();
        // The current source must use the same gain while rotating by terminal angle.
        f.bus().setVoltage(new Complex(.4*Math.cos(.27), .4*Math.sin(.27)));
        for (int i=0;i<10;i++) {
            Complex current = (Complex)model.getOutputObject();
            assertEquals(.4*.243, f.bus().getVoltage().multiply(current.conjugate()).getReal(), 1e-15);
            assertEquals(.243, model.getIp(), 1e-15);
            model.getStates(null);model.getNamedStates();model.updateAttributes(false);
        }
        assertEquals(memory, model.getNetworkHighVoltageState());
        assertEquals(states, model.getIntegrationStates());
        assertEquals(derivatives, model.getStateDerivativeSnapshot());
        // A rejected solve must restore the accepted gain as well as HV correction.
        model.beginNetworkSolution(CONTEXT);
        f.bus().setVoltage(new Complex(.01));model.beforeNetworkIteration(0);
        assertEquals(.0288, model.getIp(), 1e-15);
        model.endNetworkSolution(false);
        assertEquals(memory, model.getNetworkHighVoltageState());
        assertEquals(.243, model.getIp(), 1e-15);
        model.setNetworkIterationHighVoltageEnabled(false);
        assertEquals(0, model.getIp(), 0);
    }

    @Test void eventMemoryUsesLeftVoltageBeforeThePreliminaryNetworkSolve() {
        var f = fixture();var model = f.model();f.control().ip = .3;
        model.setNetworkIterationHighVoltageEnabled(true);
        model.captureNetworkEventLeftBoundary();
        // Event processing changes voltage before the iterative solve begins.
        f.bus().setVoltage(new Complex(.8));
        model.beginNetworkSolution(CONTEXT);
        assertEquals(1.3, model.getNetworkHighVoltageState().savedVoltage(), 1e-15);
        assertThrows(IllegalStateException.class, model::captureNetworkEventLeftBoundary);
        model.beforeNetworkIteration(0);
        f.bus().setVoltage(new Complex(.4));
        model.afterNetworkConvergence();model.endNetworkSolution(true);
        assertEquals(.94, model.getNetworkHighVoltageState().deceleratedVoltage(), 1e-15);
        assertEquals(.3, model.getIp(), 1e-15);
        // The captured endpoint applies to only one accepted solve.
        model.beginNetworkSolution(CONTEXT);
        assertEquals(.94, model.getNetworkHighVoltageState().savedVoltage(), 1e-15);
        model.beforeNetworkIteration(0);model.endNetworkSolution(true);
        assertEquals(.243, model.getIp(), 1e-15);
    }

    @Test void failedEventSolveRetainsLeftVoltageAndResetDiscardsIt() {
        var f = fixture();var model = f.model();
        model.setNetworkIterationHighVoltageEnabled(true);
        model.captureNetworkEventLeftBoundary();
        f.bus().setVoltage(new Complex(.8));
        var before = model.getNetworkHighVoltageState();
        model.beginNetworkSolution(CONTEXT);model.beforeNetworkIteration(0);
        model.endNetworkSolution(false);
        assertEquals(before, model.getNetworkHighVoltageState());
        f.bus().setVoltage(new Complex(.4));
        model.beginNetworkSolution(CONTEXT);
        assertEquals(1.3, model.getNetworkHighVoltageState().savedVoltage(), 1e-15);
        model.beforeNetworkIteration(0);model.endNetworkSolution(true);
        model.captureNetworkEventLeftBoundary();
        f.bus().setVoltage(new Complex(.6));
        assertTrue(model.initStates(f.bus()));
        model.beginNetworkSolution(CONTEXT);
        assertEquals(.6, model.getNetworkHighVoltageState().savedVoltage(), 1e-15);
        model.endNetworkSolution(false);
        model.captureNetworkEventLeftBoundary();
        f.bus().setVoltage(new Complex(.7));
        model.setNetworkIterationHighVoltageEnabled(false);
        model.setNetworkIterationHighVoltageEnabled(true);
        model.beginNetworkSolution(CONTEXT);
        assertEquals(.7, model.getNetworkHighVoltageState().savedVoltage(), 1e-15);
        model.endNetworkSolution(false);
    }

    @Test void retainedReferenceFaultBoundariesVerifyGainAndSavedVoltage() {
        var electrical = Set.of("q-coarse", "q-fine", "qv-coarse", "qv-fine");
        var plant = Set.of("v-coarse", "v-fine", "frequency-off-coarse", "frequency-off-fine");
        int profiles = 0;
        for (var profile : ReecaNativeReference.profiles())
            if (electrical.contains(profile.name())) { verifyRecordedBoundary(profile.body());profiles++; }
        for (var profile : RepcaNativeReference.profiles())
            if (plant.contains(profile.name())) { verifyRecordedBoundary(profile.body());profiles++; }
        assertEquals(8, profiles);
    }

    private void verifyRecordedBoundary(String csv) {
        var lines = csv.lines().toList();var header = Arrays.asList(lines.getFirst().split(","));
        int voltage = header.indexOf("V1"), current = header.indexOf("CONVERTER_STATE0"),
                power = header.indexOf("P_PU"), saved = header.indexOf("CONVERTER_VAR0");
        int event = 2;
        while (event < lines.size() && !lines.get(event).split(",")[0]
                .equals(lines.get(event-1).split(",")[0])) event++;
        assertTrue(event+1 < lines.size());
        var left = lines.get(event-1).split(",");
        var f = fixture(Double.parseDouble(left[voltage]), (double)(float).8);
        var model = f.model();model.setNetworkIterationHighVoltageEnabled(true);
        model.captureNetworkEventLeftBoundary();
        // This is an algebraic boundary replay: the recorded regulator output
        // is supplied to a zero-time regulator; no differential state is fitted.
        for (int row=event;row<=event+1;row++) {
            var sample = lines.get(row).split(",");
            if (row==event) f.bus().setVoltage(new Complex(.91));
            model.beginNetworkSolution(CONTEXT);
            f.bus().setVoltage(new Complex(Double.parseDouble(sample[voltage])));
            f.control().ip = Double.parseDouble(sample[current]);
            model.beforeNetworkIteration(0);model.afterNetworkConvergence();model.endNetworkSolution(true);
            assertEquals(Double.parseDouble(sample[saved]),
                    model.getNetworkHighVoltageState().savedVoltage(), 1e-7);
            assertEquals(Double.parseDouble(sample[power]),
                    f.bus().getVoltageMag()*model.getIp(), 6e-8);
        }
    }

    @Test void actualSolverFinalizesOutputWithoutAnotherNetworkSolve() throws Exception {
        var f = fixture();var model = f.model();
        model.setNetworkIterationHighVoltageEnabled(true);
        var calls = new AtomicInteger();
        var network = (DStabilityNetwork) Proxy.newProxyInstance(DStabilityNetwork.class.getClassLoader(),
                new Class<?>[]{DStabilityNetwork.class}, (proxy, method, args) -> {
                    if (method.getName().equals("solveNetEqn")) {
                        calls.incrementAndGet();model.getOutputObject();model.getOutputObject();
                        f.bus().setVoltage(new Complex(1.25));return true;
                    }
                    return method.invoke(f.network(), args);
                });
        var solver = new DStabSolverImpl(DStabObjectFactory.createDynamicSimuAlgorithm(network));
        assertTrue(solver.networkSolutionStep());
        assertEquals(2, calls.get());
        assertEquals(.182, model.getNetworkHighVoltageState().correction(), 1e-15);
        assertEquals(.5-.182/1.28, model.getIq(), 1e-15);
        assertFalse(model.getNetworkHighVoltageState().solutionInProgress());
        var accepted = model.getNetworkHighVoltageState();
        solver.setMaxIterationTimes(1);
        assertFalse(solver.networkSolutionStep());
        assertEquals(accepted, model.getNetworkHighVoltageState());
    }

    @Test void acceptedEventPowerIsPureIdempotentAndAtomicForBypassedCurrent() {
        var f=fixture();var model=f.model();
        model.setNetworkIterationHighVoltageEnabled(true);
        model.beginNetworkSolution(CONTEXT);
        assertThrows(IllegalStateException.class,()->model.acceptNetworkEventInput(.1));
        model.beforeNetworkIteration(0);model.afterNetworkConvergence();model.endNetworkSolution(true);
        var memory=model.getNetworkHighVoltageState();var states=model.getIntegrationStates();
        var rates=model.getStateDerivativeSnapshot();
        f.bus().setVoltage(new Complex(1.21,.03));
        Complex expected=f.bus().getVoltage().multiply(model.uncompensatedNetworkCurrent().conjugate());
        model.acceptNetworkEventInput(.1);
        assertEquals(expected.getReal(),((Number)model.getStates(null).get("REGCA1_P")).doubleValue(),1.e-15);
        assertEquals(expected.getImaginary(),((Number)model.getStates(null).get("REGCA1_Q")).doubleValue(),1.e-15);
        var accepted=new java.util.HashMap<>(model.getStates(null));
        model.acceptNetworkEventInput(.1);
        assertEquals(accepted,model.getStates(null));assertEquals(memory,model.getNetworkHighVoltageState());
        assertEquals(states,model.getIntegrationStates());assertEquals(rates,model.getStateDerivativeSnapshot());
        assertThrows(IllegalArgumentException.class,()->model.acceptNetworkEventInput(Double.NaN));
        f.bus().setVoltage(new Complex(Double.NaN,0.));
        assertThrows(IllegalStateException.class,()->model.acceptNetworkEventInput(.2));
        assertEquals(accepted,model.getStates(null),"Rejected refresh cannot partially publish P/Q");
        f.bus().setVoltage(new Complex(.7));model.setStatus(false);model.acceptNetworkEventInput(.2);
        assertEquals(accepted,model.getStates(null),"Tripped converter is not refreshed");
        model.setStatus(true);model.getParentGen().setStatus(false);model.acceptNetworkEventInput(.2);
        assertEquals(accepted,model.getStates(null),"Inactive parent is not refreshed");
        model.getParentGen().setStatus(true);f.bus().setStatus(false);model.acceptNetworkEventInput(.2);
        assertEquals(accepted,model.getStates(null),"Inactive bus is not refreshed");
    }

    @Test void importedConvertersReceiveOnlyConvergedEventInputsInBothSolvers() throws Exception {
        for(boolean history:new boolean[]{false,true})for(boolean fail:new boolean[]{false,true}) {
            String base="testData/adpter/psse/v33/renewable/";
            var context=new org.interpss.fadapter.psse.PSSEMultiFileLoader().loadDStab(
                    base+"regca_reeca_repca_bus1062.raw",base+"regca_reecc1_bus1062.dyr");
            var network=context.getDStabilityNet();var algorithm=context.getDynSimuAlgorithm();
            var device=(Regca1Model)((DStabGen)network.getBus("Bus1").getContributeGen("1")).getDynamicGenDevice();
            assertTrue(network.getBus("Bus1").getDynamicBusDeviceList().contains(device),"Actual imported-device event registry");
            var cold=new java.util.HashMap<>(device.getStates(null));device.acceptNetworkEventInput(0.);
            assertEquals(cold,device.getStates(null),"Before initialization the event input is ignored");
            AtomicInteger callbacks=new AtomicInteger();
            Runnable check=()->{
                Complex expected=network.getBus("Bus1").getVoltage().multiply(device.uncompensatedNetworkCurrent().conjugate());
                assertEquals(expected.getReal(),((Number)device.getStates(null).get("REGCA1_P")).doubleValue(),1.e-13);
                assertEquals(expected.getImaginary(),((Number)device.getStates(null).get("REGCA1_Q")).doubleValue(),1.e-13);
                callbacks.incrementAndGet();
            };
            org.interpss.dstab.algo.DefaultDynamicSimulationSolver solver=history?
                new org.interpss.dstab.algo.StaggeredEulerSolver(algorithm){
                    @Override public boolean networkSolutionStep() throws com.interpss.dstab.common.DStabSimuException {
                        boolean ok=super.networkSolutionStep();if(ok&&eventBoundaryThisStep&&"INITIAL".equals(networkSolutionStage))check.run();return ok;
                    }
                }:new org.interpss.dstab.algo.DefaultDynamicSimulationSolver(algorithm){
                    @Override public boolean networkSolutionStep() throws com.interpss.dstab.common.DStabSimuException {
                        boolean ok=super.networkSolutionStep();if(ok&&eventBoundaryThisStep&&"INITIAL".equals(networkSolutionStage))check.run();return ok;
                    }
                };
            algorithm.setSolver(solver);network.setBypassDataCheck(true);network.setAllowGenWithoutMach(true);
            assertTrue(algorithm.getAclfAlgorithm().loadflow());
            algorithm.setSimuMethod(com.interpss.dstab.algo.DynamicSimuMethod.MODIFIED_EULER);
            algorithm.setSimuStepSec(.001);algorithm.setTotalSimuTimeSec(.006);
            algorithm.setSimuOutputHandler(new com.interpss.dstab.cache.StateMonitor());
            network.addDynamicEvent(DStabObjectFactory.createBusFaultEvent("Bus2",network,
                    com.interpss.core.acsc.fault.SimpleFaultCode.GROUND_3P,new Complex(0.,.2),null,.002,.002),"AcceptedPower");
            assertTrue(algorithm.initialization());device.setNetworkIterationHighVoltageEnabled(true);
            for(int i=0;i<2;i++)assertTrue(algorithm.solveDEqnStep(true));
            assertEquals(0,callbacks.get(),"Ordinary steps never dispatch an event input");
            if(fail) {
                var before=new java.util.HashMap<>(device.getStates(null));
                var states=device.getIntegrationStates();var memory=device.getNetworkHighVoltageState();
                solver.setMaxIterationTimes(1);boolean accepted=algorithm.solveDEqnStep(true);
                assertFalse(accepted,"Deliberately under-iterated fault must fail");assertEquals(0,callbacks.get());
                var after=new java.util.HashMap<>(device.getStates(null));before.remove("Time");after.remove("Time");
                assertEquals(before,after,"Failed event does not publish inputs; recorder time is metadata");
                assertEquals(states,device.getIntegrationStates());assertEquals(memory,device.getNetworkHighVoltageState());
            } else {
                for(int i=0;i<4;i++)assertTrue(algorithm.solveDEqnStep(true));
                assertEquals(2,callbacks.get(),"Application and clearing use real solver dispatch");
            }
        }
    }

    private record Fixture(DStabilityNetwork network, DStabBus bus, Regca1Model model, Control control) {}
    private Fixture fixture() {
        return fixture(1.3, .4);
    }
    private Fixture fixture(double voltage, double acceleration) {
        var network = DStabObjectFactory.createDStabilityNetwork();network.setBaseKva(100000);
        var bus = (DStabBus)DStabObjectFactory.createDStabBus("B", network).orElseThrow();
        bus.setVoltage(new Complex(voltage));
        var gen = DStabObjectFactory.createDStabGen("1");
        gen.setGen(new Complex(0,.65));gen.setMvaBase(100);gen.setStatus(true);
        gen.setPosGenZ(Complex.ZERO);
        bus.getContributeGenList().add(gen);
        var data = new Regca1Data(0,0,10, .9,.4,1.2,1.2,.8,.4,-1.3,.02,.7,999,-999,acceleration);
        var model = new Regca1Model(gen,bus,"1",data);model.setStatus(true);
        var control = new Control();model.setActiveElectricalController(control);
        assertTrue(model.initStates(bus));
        return new Fixture(network,bus,model,control);
    }
    private static class Control implements RenewableElectricalController {
        double ip, iq = -.5;
        public void initialize(double p,double q,double v) {}
        public void step(double dt,double p,double q,double v,double f) {}
        public double getIpcmd() {return ip;}
        public double getIqcmd() {return iq;}
        public Repca1Model getPlantController() {return null;}
        public void setPlantController(Repca1Model controller) {}
    }
}
