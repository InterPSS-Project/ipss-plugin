package org.interpss.core.dstab;

import static org.junit.jupiter.api.Assertions.*;
import java.lang.reflect.Proxy;
import java.util.concurrent.atomic.AtomicInteger;
import org.apache.commons.math3.complex.Complex;
import org.interpss.dstab.renewable.*;
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

    private record Fixture(DStabilityNetwork network, DStabBus bus, Regca1Model model, Control control) {}
    private Fixture fixture() {
        var network = DStabObjectFactory.createDStabilityNetwork();network.setBaseKva(100000);
        var bus = (DStabBus)DStabObjectFactory.createDStabBus("B", network).orElseThrow();
        bus.setVoltage(new Complex(1.3));
        var gen = DStabObjectFactory.createDStabGen("1");
        gen.setGen(new Complex(0,.65));gen.setMvaBase(100);gen.setStatus(true);
        bus.getContributeGenList().add(gen);
        var data = new Regca1Data(0,0,10, .9,.4,1.2,1.2,.8,.4,-1.3,.02,.7,999,-999,.4);
        var model = new Regca1Model(gen,bus,"1",data);model.setStatus(true);
        var control = new Control();model.setActiveElectricalController(control);
        assertTrue(model.initStates(bus));
        return new Fixture(network,bus,model,control);
    }
    private static class Control implements RenewableElectricalController {
        double iq = -.5;
        public void initialize(double p,double q,double v) {}
        public void step(double dt,double p,double q,double v,double f) {}
        public double getIpcmd() {return 0;}
        public double getIqcmd() {return iq;}
        public Repca1Model getPlantController() {return null;}
        public void setPlantController(Repca1Model controller) {}
    }
}
