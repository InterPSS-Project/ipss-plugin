package org.interpss.dstab.svc;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.apache.commons.math3.complex.Complex;
import org.interpss.CorePluginTestSetup;
import org.interpss.core.dstab.Svsmo3t2MssKernelTest;
import org.interpss.fadapter.builder.FixedShuntAdmittance;
import org.interpss.fadapter.psse.PSSEMultiFileLoader;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.interpss.dstab.BaseDStabNetwork;

/** Exact physical stamps, including off-state conductance and unrelated faults. */
public class Svsmo3t2MssBoundaryTest extends CorePluginTestSetup {
    private static com.interpss.simu.SimuContext context(Path temporary, int bus, double b, boolean active) throws Exception {
        Path fixture = Path.of("testData/psse/v33/SVSMO3T2");
        String raw = Files.readString(fixture.resolve("smib.raw"));
        raw = raw.replace("0 / END OF FIXED SHUNT DATA", bus + ",'S0'," + (active ? 1 : 0)
                + ",.5," + (b * 100) + "\n0 / END OF FIXED SHUNT DATA");
        Path input = temporary.resolve("network.raw");
        Files.writeString(input, raw);
        var context = new PSSEMultiFileLoader().loadDStab(input.toString(), fixture.resolve("smib.dyr").toString());
        var lf = context.getDynSimuAlgorithm().getAclfAlgorithm();
        lf.getLfAdjAlgo().setLocalContinuousSvcAsPv(true);
        assertTrue(lf.loadflow());
        return context;
    }

    private static BaseDStabNetwork<?, ?> network(Path temporary, int bus, double b, boolean active) throws Exception {
        return context(temporary, bus, b, active).getDStabilityNet();
    }

    private static Svsmo3t2Data data(int bus) throws Exception {
        var devices = new ArrayList<Svsmo3t2Data.MssDevice>();
        for (int i = 0; i < 8; i++) devices.add(new Svsmo3t2Data.MssDevice(i == 0 ? bus : 0, "S" + i));
        return Svsmo3t2MssKernelTest.data(Map.of("mssSwitchingFlag", 1, "mssDevices", devices,
                "mssBreakerTime", 0., "mssSwitchingTime", 0., "capacitorDischargeTime", .25,
                "mssUpperCurrent", .5, "mssLowerCurrent", -.5), 1);
    }

    @Test void individualGAndBChangeOnceAndSurviveRebuildWithoutErasingAFault(@TempDir Path temporary) throws Exception {
        for (int busNumber : new int[]{2, 3}) for (double nominalB : new double[]{.1, -.1}) {
            Path dir = Files.createDirectory(temporary.resolve(busNumber + "-" + nominalB));
            var net = network(dir, busNumber, nominalB, false);
            var bus = net.getDStabBus("Bus" + busNumber);
            var boundary = new Svsmo3t2MssBoundary(net, data(busNumber));
            boundary.initialize();
            assertThrows(IllegalStateException.class, () -> boundary.advance(0, nominalB > 0 ? 1 : -1));
            net.formYMatrix4DStab();
            int index = bus.getSortNumber();
            var base = net.getYMatrix().getA(index, index);
            var originalG = bus.getShuntY();
            var fault = new Complex(.02, -3.);
            net.getYMatrix().addToA(fault, index, index);
            var bank = new Complex(.005, nominalB);
            boundary.advance(0, nominalB > 0 ? 1 : -1);
            assertTrue(bus.getCompensator("S0").isActive());
            assertEquals(0, net.getYMatrix().getA(index, index).subtract(base.add(fault).add(bank)).abs(), 1e-12);
            assertEquals(originalG.add(new Complex(.005, 0)), bus.getShuntY());
            boundary.advance(0, nominalB > 0 ? 1 : -1);
            assertEquals(1, boundary.controller().completionCount());
            assertEquals(0, net.getYMatrix().getA(index, index).subtract(base.add(fault).add(bank)).abs(), 1e-12);
            net.formYMatrix4DStab();
            assertEquals(0, net.getYMatrix().getA(index, index).subtract(base.add(bank)).abs(), 1e-12);
            boundary.advance(.125, nominalB > 0 ? -1 : 1);
            assertFalse(bus.getCompensator("S0").isActive());
            assertEquals(originalG, bus.getShuntY());
            net.formYMatrix4DStab();
            assertEquals(0, net.getYMatrix().getA(index, index).subtract(base).abs(), 1e-12);
        }
    }

    @Test void changedMetadataAndExternalStatusRequireExplicitReinitialization(@TempDir Path temporary) throws Exception {
        var net = network(temporary, 3, .1, true);
        var bus = net.getDStabBus("Bus3");
        var device = bus.getCompensator("S0");
        var boundary = new Svsmo3t2MssBoundary(net, data(3));
        boundary.initialize();
        net.formYMatrix4DStab();
        device.setStatus(false);
        assertThrows(IllegalStateException.class, () -> boundary.advance(0, 1));
        boundary.initialize();
        assertFalse(boundary.controller().isInService(0));
        device.setB(.2);
        assertThrows(IllegalStateException.class, () -> boundary.advance(0, 1));
        assertThrows(IllegalArgumentException.class, () -> new Svsmo3t2MssBoundary(net, data(3)));
        device.setB(.1);
        device.eAdapters().removeIf(FixedShuntAdmittance.class::isInstance);
        assertThrows(IllegalArgumentException.class, () -> new Svsmo3t2MssBoundary(net, data(3)));
    }

    @Test void duplicateOrMissingTargetsFailBeforeAttachingAController(@TempDir Path temporary) throws Exception {
        var net = network(temporary, 3, .1, false);
        var bus = net.getDStabBus("Bus3");
        var before = List.copyOf(bus.getDynamicBusDeviceList());
        var keys = new ArrayList<>(data(3).mssDevices());
        keys.set(1, keys.getFirst());
        var duplicate = Svsmo3t2MssKernelTest.data(Map.of("mssSwitchingFlag", 1, "mssDevices", keys), 2);
        assertThrows(IllegalArgumentException.class, () -> new Svsmo3t2Model(bus, bus,
                bus.getStaticVarCompensatorList().getFirst(), duplicate));
        assertEquals(before, bus.getDynamicBusDeviceList());
        assertThrows(IllegalArgumentException.class, () -> new Svsmo3t2Model(bus, bus,
                bus.getStaticVarCompensatorList().getFirst(), data(99)));
        assertEquals(before, bus.getDynamicBusDeviceList());
        assertFalse(bus.getCompensator("S0").isActive());
    }

    @Test void zeroDelayOriginRunsAfterCompensationAndTrialStagesCannotDispatch(@TempDir Path temporary) throws Exception {
        Complex baselineY = null;
        java.util.Hashtable<String, Complex> baselineCurrents = null;
        for (boolean enabled : new boolean[]{false, true}) {
            var context = context(Files.createDirectory(temporary.resolve(Boolean.toString(enabled))), 3, .1, false);
            var net = context.getDStabilityNet();
            var bus = net.getDStabBus("Bus3");
            var old = (Svsmo3t2Model) bus.getDynamicBusDeviceList().stream().filter(Svsmo3t2Model.class::isInstance).findFirst().orElseThrow();
            old.setDStabBus(null);
            var params = Svsmo3t2MssKernelTest.data(Map.of("mssSwitchingFlag", enabled ? 1 : 0,
                    "mssDevices", data(3).mssDevices(), "mssBreakerTime", 0., "mssSwitchingTime", 0.,
                    "mssUpperCurrent", .2, "mssLowerCurrent", -.5), 1);
            var model = new Svsmo3t2Model(bus, bus, bus.getStaticVarCompensatorList().getFirst(), params);
            var algo = context.getDynSimuAlgorithm();
            net.setStaticLoadIncludedInYMatrix(true);
            net.setStaticLoadModel(com.interpss.dstab.StaticLoadModel.CONST_Z);
            net.setReactiveStaticLoadModel(com.interpss.dstab.StaticLoadModel.CONST_Z);
            algo.setSolver(new org.interpss.dstab.algo.DefaultDynamicSimulationSolver(algo));
            algo.setSimuOutputHandler(new com.interpss.dstab.cache.StateMonitor());
            algo.setSimuStepSec(.0005);
            algo.setSimuMethod(com.interpss.dstab.algo.DynamicSimuMethod.MODIFIED_EULER);
            assertTrue(algo.initialization());
            int index = bus.getSortNumber();
            var y = net.getYMatrix().getA(index, index);
            if (!enabled) {
                baselineY = y;
                baselineCurrents = new java.util.Hashtable<>(net.getCustomBusCurrInjHashtable());
                assertFalse(bus.getCompensator("S0").isActive());
            } else {
                assertTrue(bus.getCompensator("S0").isActive());
                assertEquals(1, model.getMssController().completionCount());
                assertEquals(0, y.subtract(baselineY.add(new Complex(.005, .1))).abs(), 1e-12);
                assertEquals(baselineCurrents, net.getCustomBusCurrInjHashtable(), "The new bank must not be absorbed into initial compensation");
                assertTrue(model.afterStep(0));
                assertTrue(model.acceptSwitchingEndpoint());
                assertEquals(y, net.getYMatrix().getA(index, index));
                assertTrue(model.nextStep(.0005, com.interpss.dstab.algo.DynamicSimuMethod.MODIFIED_EULER, 0));
                assertFalse(model.afterStep(.0005), "An unaccepted predictor cannot dispatch switching");
                assertEquals(0, model.getMssController().time());
                assertTrue(model.nextStep(.0005, com.interpss.dstab.algo.DynamicSimuMethod.MODIFIED_EULER, 1));
                assertEquals(0, model.getMssController().time(), "Correcting continuous states alone does not dispatch");
                assertTrue(model.afterStep(.0005));
                assertEquals(.0005, model.getMssController().time());
            }
        }
    }
}
