package org.interpss.fadapter.psse;

import static org.junit.jupiter.api.Assertions.*;
import java.util.List;
import java.util.Hashtable;
import org.apache.commons.math3.complex.Complex;
import com.interpss.dstab.renewable.Regca1Data;
import com.interpss.dstab.renewable.Regca1Model;
import org.junit.jupiter.api.Test;
import com.interpss.dstab.DStabObjectFactory;
import com.interpss.dstab.algo.DynamicSimuAlgorithm;
import com.interpss.dstab.device.DynamicDevice;
import com.interpss.dstab.device.impl.DynamicBusDeviceImpl;

class PsseNetworkAccelerationTest {
    private static class ProbeSolver extends PsseDStabSolver {
        ProbeSolver(DynamicSimuAlgorithm algorithm) { super(algorithm); }
        boolean allowed(DynamicDevice... devices) { return canAccelerateNetworkIterations(List.of(devices)); }
    }

    @Test void mutableIterationMemoryAndUnknownDevicesDisableAcceleration() {
        var network = DStabObjectFactory.createDStabilityNetwork();
        var bus = (com.interpss.dstab.DStabBus) DStabObjectFactory.createDStabBus("B", network).orElseThrow();
        var gen = DStabObjectFactory.createDStabGen("1");
        bus.getContributeGenList().add(gen);
        var converter = new Regca1Model(gen, bus, "1", data(.02));
        gen.setDynamicGenDevice(converter);
        var solver = new ProbeSolver(DStabObjectFactory.createDynamicSimuAlgorithm(network));
        assertFalse(solver.isNetworkAccelerationEnabled());
        solver.setNetworkAccelerationEnabled(true);
        assertTrue(solver.isNetworkAccelerationEnabled());
        assertTrue(solver.allowed(converter));
        converter.setNetworkIterationHighVoltageEnabled(true);
        assertFalse(solver.allowed(converter));
        converter.setNetworkIterationHighVoltageEnabled(false);
        assertTrue(solver.allowed(converter));
        var unknown = new DynamicBusDeviceImpl() {};
        unknown.setStatus(true);
        bus.getDynamicBusDeviceList().add(unknown);
        assertFalse(solver.allowed(converter));
        unknown.setStatus(false);
        assertTrue(solver.allowed(converter));
    }

    @Test void algebraicControllerBypassRetainsOrdinaryIteration() {
        var network = DStabObjectFactory.createDStabilityNetwork();
        var bus = (com.interpss.dstab.DStabBus) DStabObjectFactory.createDStabBus("B", network).orElseThrow();
        var gen = DStabObjectFactory.createDStabGen("1");
        var converter = new Regca1Model(gen, bus, "1", data(0.));
        var solver = new ProbeSolver(DStabObjectFactory.createDynamicSimuAlgorithm(network));
        assertFalse(solver.allowed(converter));
    }

    @Test void customCurrentGettersRetainOrdinaryIteration() {
        var network = DStabObjectFactory.createDStabilityNetwork();
        var solver = new ProbeSolver(DStabObjectFactory.createDynamicSimuAlgorithm(network));
        assertTrue(solver.allowed());
        network.setCustomBusCurrInjHashtable(new Hashtable<String, Complex>() {});
        assertFalse(solver.allowed());
        network.setCustomBusCurrInjHashtable(new Hashtable<>());
        assertTrue(solver.allowed());
    }

    @Test void defaultAndStaggeredSolversRequireExplicitMixingSelection() {
        var network=DStabObjectFactory.createDStabilityNetwork();
        var algorithm=DStabObjectFactory.createDynamicSimuAlgorithm(network);
        for(var solver:List.of(
                new org.interpss.dstab.algo.DefaultDynamicSimulationSolver(algorithm),
                new org.interpss.dstab.algo.StaggeredEulerSolver(algorithm))) {
            assertFalse(solver.isNetworkAccelerationEnabled());
            assertEquals(50,solver.getMaxIterationTimes());
            assertEquals(0,solver.getNetworkAccelerationApplications());
            solver.setNetworkAccelerationEnabled(true);
            assertTrue(solver.isNetworkAccelerationEnabled());
            solver.setNetworkAccelerationEnabled(false);
            assertFalse(solver.isNetworkAccelerationEnabled());
        }
    }

    private Regca1Data data(double lag) {
        return new Regca1Data(1, lag, 10., .9, .4, 1.2, 1.2, .8, .4, -1.3, .02, 0., 999., -999., .3);
    }
}
