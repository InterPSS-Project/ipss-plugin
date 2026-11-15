package org.interpss.core.adapter.builder.dstab;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;

import org.apache.commons.math3.complex.Complex;
import org.interpss.IpssCorePlugin;
import com.interpss.dstab.mach.model.Gewtecu1Model;
import com.interpss.dstab.mach.model.Gewtgcu1Model;
import org.interpss.fadapter.psse.PSSEMultiFileLoader;
import org.interpss.fadapter.psse.dyr.DynamicModelCatalog;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.interpss.dstab.BaseDStabBus;
import com.interpss.dstab.DStabGen;
import com.interpss.dstab.algo.DynamicSimuMethod;
import com.interpss.dstab.cache.StateMonitor;

/** Exact wrapper, initialization, curve, limit, and named-state checks. */
public class Gewtecu1ModelTest {
    private static final Path CASE = Path.of("testData", "adpter", "psse", "v33", "SMIB");
    private static final Path MODEL_DYR = CASE.resolve(
            "SMIB_v33_gewtgcu1_gewtecu1.dyr");

    @Test
    void exactNativeWrapperAttachesAndInitializesAllPublishedStates(@TempDir Path tempDir)
            throws Exception {
        Fixture fixture = load(tempDir);
        assertEquals(82, DynamicModelCatalog.find("GEWTECU1").orElseThrow().parameterCount());
        assertEquals(18, fixture.controller.getNamedStates().size());
        assertTrue(fixture.controller.getNamedStates().containsKey("Filter in Voltage regulator"));
        assertTrue(fixture.controller.getNamedStates().containsKey("Washout in WindInertia"));
        assertNotNull(fixture.generator.getElectricalController());
        assertTrue(fixture.generator.getNamedStates().keySet()
                .containsAll(fixture.controller.getNamedStates().keySet()));
    }

    @Test
    void remoteVoltageInitializesWindVarAndLvplWhileInnerLoopUsesTerminal(
            @TempDir Path tempDir) throws Exception {
        var fixture = loadWithVoltageBus(tempDir, 2);
        var terminal = fixture.generator.getDStabBus();
        var remote = assertInstanceOf(BaseDStabBus.class, terminal.getNetwork().getBus("Bus2"));
        terminal.setVoltage(new Complex(.99, 0));
        remote.setVoltage(new Complex(.95, 0));
        assertTrue(fixture.generator.initStates(terminal));

        var controller = fixture.controller;
        assertEquals(.95, controller.getNamedState("Voltage sensor"), 1.0e-12);
        assertEquals(.95, controller.getNamedState("LVPL limit"), 1.0e-12);
        assertEquals(.99, controller.getNamedState("Mvar/Volt integrator"), 1.0e-12);
        double initialCommand = controller.getNamedState("Volt/Mvar integrator");
        assertTrue(controller.step(.001, 0));
        assertEquals(.95, controller.getNamedState("Voltage sensor"), 1.0e-12);
        assertEquals(0, controller.getNamedState("Lag of the WindVar controller"), 1.0e-12);
        assertEquals(initialCommand, controller.getNamedState("Volt/Mvar integrator"), 1.0e-12);

        remote.setVoltage(new Complex(.90, 0));
        assertTrue(controller.step(.001, 1));
        assertEquals(.95 + .0005 * (.90 - .95) / controller.getData().voltageSensorTime(),
                controller.getNamedState("Voltage sensor"), 1.0e-12);
        assertTrue(controller.getNamedState("LVPL limit") < .95);
        assertEquals(initialCommand, controller.getNamedState("Volt/Mvar integrator"), 1.0e-12);
    }

    @Test
    void missingVoltageBusFallsBackToTerminalAtInitializationAndDuringStepping(
            @TempDir Path tempDir) throws Exception {
        var fixture = loadWithVoltageBus(tempDir, 999);
        var terminal = fixture.generator.getDStabBus();
        terminal.setVoltage(new Complex(.97, 0));
        assertTrue(fixture.generator.initStates(terminal));
        var controller = fixture.controller;
        assertEquals(.97, controller.getNamedState("Voltage sensor"), 1.0e-12);
        assertEquals(.97, controller.getNamedState("LVPL limit"), 1.0e-12);
        assertTrue(controller.step(.001, 0));
        terminal.setVoltage(new Complex(.93, 0));
        assertTrue(controller.step(.001, 1));
        assertEquals(.97 + .0005 * (.93 - .97) / controller.getData().voltageSensorTime(),
                controller.getNamedState("Voltage sensor"), 1.0e-12);
    }

    @Test
    void bypassedLvplUsesLiveRemoteVoltageOnTheFirstPredictor(@TempDir Path tempDir)
            throws Exception {
        Path dyr = tempDir.resolve("bypassed-lvpl.dyr");
        Files.writeString(dyr, Files.readString(MODEL_DYR)
                .replace("  0 0 1 0 0 0 0 ", "  2 0 1 0 0 0 0 ")
                .replace("1.2 0.0 0.10 0.02 0.70", "1.2 0.0 0.10 0.0 0.70"));
        var fixture = loadDyr(dyr);
        var remote = assertInstanceOf(BaseDStabBus.class,
                fixture.generator.getDStabBus().getNetwork().getBus("Bus2"));
        var controller = fixture.controller;
        assertEquals(0.0, controller.getData().lvplSensorTime(), 0.0);
        controller.setMeasuredPower(.4, 0.0);
        remote.setVoltage(new Complex(.35, 0));

        assertTrue(controller.step(.001, 0));
        assertEquals(.35, controller.getNamedState("LVPL limit"), 1.0e-12);
        assertEquals(0.0, controller.getNamedState("Braking resistor integrator"), 0.0);
        assertTrue(controller.step(.001, 1));
        assertEquals(0.0, controller.getNamedState("Braking resistor integrator"), 0.0);
    }

    private static Fixture loadWithVoltageBus(Path directory, int remoteBus) throws Exception {
        Path dyr = directory.resolve("voltage-sensing.dyr");
        Files.writeString(dyr, Files.readString(MODEL_DYR)
                .replace("  0 0 1 0 0 0 0 ", "  " + remoteBus + " 0 1 0 0 0 0 "));
        return loadDyr(dyr);
    }

    @Test
    void frequencyCurveInterpolatesPublishedFourPointCharacteristic(@TempDir Path tempDir)
            throws Exception {
        Gewtecu1Model model = load(tempDir).controller;
        assertEquals(.8, model.frequencyPower(.94), 0.0);
        assertEquals(.85, model.frequencyPower(.965), 1.0e-12);
        assertEquals(.95, model.frequencyPower(1.0), 1.0e-12);
        assertEquals(1.05, model.frequencyPower(1.035), 1.0e-12);
        assertEquals(1.1, model.frequencyPower(1.06), 0.0);
    }

    @Test
    void nonzeroReactivePowerAndAbsoluteShaftSpeedRemainAtEquilibrium(@TempDir Path tempDir)
            throws Exception {
        var fixture = load(tempDir);
        var generator = fixture.generator;
        var controller = fixture.controller;
        var bus = generator.getDStabBus();
        bus.setVoltage(new org.apache.commons.math3.complex.Complex(.99, 0));
        generator.getParentGen().setGen(new org.apache.commons.math3.complex.Complex(.5, -.1));
        assertTrue(generator.initStates(bus));
        controller.setRotorSpeed(controller.getInitialRotorSpeed());
        var initial = controller.getNamedStates();
        assertEquals(0, initial.get("Lag of the WindVar controller"), 0);
        for (int i = 0; i < 100; i++) {
            assertTrue(generator.nextStep(.001, DynamicSimuMethod.MODIFIED_EULER, 0));
            assertTrue(generator.nextStep(.001, DynamicSimuMethod.MODIFIED_EULER, 1));
            assertTrue(generator.updateAttributes(false));
        }
        assertEquals(.5, generator.getP(), 1e-10);
        assertEquals(-.1, generator.getQ(), 1e-10);
        initial.forEach((name, value) -> assertEquals(value,
                controller.getNamedState(name), 1e-10, name));
    }

    @Test
    void tripEnvelopeAndPredictorCorrectorAreActive(@TempDir Path tempDir) throws Exception {
        Fixture fixture = load(tempDir);
        fixture.controller.setExternalSignals(1.0, 10.0, .9, .97,
                .01, 0.0, 0.0);
        assertTrue(fixture.controller.step(.0005, 0));
        assertTrue(fixture.controller.step(.0005, 1));
        assertTrue(!fixture.controller.isTripped());
        fixture.controller.setExternalSignals(-2.1, 10.0, .9, 1.0,
                0.0, 0.0, 0.0);
        assertTrue(fixture.controller.step(.0005, 0));
        assertTrue(fixture.controller.step(.0005, 1));
        assertTrue(fixture.controller.isTripped());
    }

    @Test
    void highWindProtectionIntegratesExcessAndRetainsExposure(@TempDir Path tempDir)
            throws Exception {
        Path dyr = tempDir.resolve("wind-protection.dyr");
        String original = Files.readString(MODEL_DYR);
        String adjusted = original.replace("10.0 100.0 -100.0 -1.0 1000.0",
                "14.0 25.0 3.0 -0.9 8.0");
        assertTrue(!original.equals(adjusted));
        Files.writeString(dyr, adjusted);
        var controller = loadDyr(dyr).controller;
        for (int i = 0; i < 2000; i++) protectionStep(controller, 14);
        assertEquals(0, controller.getNamedState("High wind speed trip integrator"), 0);
        assertTrue(!controller.isTripped());
        for (int i = 0; i < 500; i++) protectionStep(controller, 34);
        assertEquals(4.5, controller.getNamedState("High wind speed trip integrator"), 1e-10);
        assertTrue(!controller.isTripped());
        for (int i = 0; i < 500; i++) protectionStep(controller, 14);
        assertEquals(4.5, controller.getNamedState("High wind speed trip integrator"), 1e-10);
        for (int i = 0; i < 500; i++) protectionStep(controller, 34);
        assertEquals(9, controller.getNamedState("High wind speed trip integrator"), 1e-10);
        assertTrue(controller.isTripped());
        controller.setWindSpeed(14);
        assertTrue(controller.initialize());
        assertEquals(0, controller.getNamedState("High wind speed trip integrator"), 0);
        assertTrue(!controller.isTripped());
    }

    private static void protectionStep(Gewtecu1Model controller, double wind) {
        controller.setWindSpeed(wind);
        assertTrue(controller.step(.001, 0));
        assertTrue(controller.step(.001, 1));
    }

    @Test
    void terminalReactivePowerBypassesBranchSynthesizingImpedance(@TempDir Path tempDir)
            throws Exception {
        Gewtecu1Model model = load(tempDir).controller;
        model.setMeasuredPower(0.5, -0.4);
        model.setExternalSignals(0.2, 10.0, 0.9, 1.0, 0.0, 0.0, -0.4);
        assertTrue(model.step(0.0005, 0));
        assertTrue(model.step(0.0005, 1));
        assertEquals(-0.00398,
                model.getNamedState("Filter in Reactive Droop"), 1.0e-12);
    }

    @Test
    void downstreamVoltageLimitCoordinatesUpstreamAntiWindup(@TempDir Path tempDir)
            throws Exception {
        Gewtecu1Model model = load(tempDir).controller;
        model.setMeasuredPower(0.5, -2.0);
        model.setExternalSignals(0.2, 10.0, 0.9, 1.0, 0.0, 0.0, -2.0);
        for (int index = 0; index < 200; index++) {
            assertTrue(model.step(0.0005, 0));
            assertTrue(model.step(0.0005, 1));
        }
        assertEquals(1.0, model.getNamedState("Volt/Mvar integrator"), 0.0);
        double upstreamAtLimit = model.getNamedState("Mvar/Volt integrator");
        for (int index = 0; index < 20; index++) {
            assertTrue(model.step(0.0005, 0));
            assertTrue(model.step(0.0005, 1));
        }
        assertEquals(upstreamAtLimit,
                model.getNamedState("Mvar/Volt integrator"), 0.0);
    }

    @Test
    void brakingStateIntegratesLimitedExcessPower(@TempDir Path tempDir)
            throws Exception {
        Gewtecu1Model model = load(tempDir).controller;
        model.setMeasuredPower(0.4, 0.0);
        model.setExternalSignals(0.2, 10.0, 0.9, 1.0,
                0.0, 0.0, 0.0);
        assertTrue(model.step(0.001, 0));
        assertTrue(model.step(0.001, 1));

        // Initial WPCMD is 5/6 pu on turbine base.  Measured power is
        // 0.4 * 100/60 = 2/3 pu, so the published braking-power input is 1/6 pu.
        assertEquals(1.0 / 6000.0,
                model.getNamedState("Braking resistor integrator"), 1.0e-12);
    }

    @Test
    void rejectsIncorrectAllocation(@TempDir Path tempDir) throws Exception {
        Path invalid = tempDir.resolve("invalid.dyr");
        Files.writeString(invalid, Files.readString(MODEL_DYR)
                .replace("102 0 9 67 18 16", "102 0 9 66 18 16"));
        var context = new PSSEMultiFileLoader().loadDStab(
                CASE.resolve("SMIB_v33_wt2g1.raw").toString(), invalid.toString());
        DStabGen gen = (DStabGen) context.getDStabilityNet().getBus("Bus1")
                .getContributeGen("1");
        Gewtgcu1Model generator = assertInstanceOf(Gewtgcu1Model.class,
                gen.getDynamicGenDevice());
        assertTrue(generator.getElectricalController() == null);
    }

    private static Fixture load(Path tempDir) throws Exception {
        return loadDyr(MODEL_DYR);
    }

    private static Fixture loadDyr(Path modelDyr) throws Exception {
        IpssCorePlugin.init();
        var context = new PSSEMultiFileLoader().loadDStab(
                CASE.resolve("SMIB_v33_wt2g1.raw").toString(),
                modelDyr.toString());
        var network = context.getDStabilityNet();
        var algorithm = context.getDynSimuAlgorithm();
        DStabGen gen = (DStabGen) network.getBus("Bus1").getContributeGen("1");
        Gewtgcu1Model generator = assertInstanceOf(Gewtgcu1Model.class,
                gen.getDynamicGenDevice());
        Gewtecu1Model controller = generator.getElectricalController();
        assertNotNull(controller);
        algorithm.getAclfAlgorithm().getDataCheckConfig().setAllowGenWithoutMachine(true);
        assertTrue(algorithm.getAclfAlgorithm().loadflow());
        algorithm.setSimuMethod(DynamicSimuMethod.MODIFIED_EULER);
        algorithm.setSimuStepSec(.0005);
        algorithm.setSimuOutputHandler(new StateMonitor());
        assertTrue(algorithm.initialization());
        return new Fixture(generator, controller);
    }

    private record Fixture(Gewtgcu1Model generator, Gewtecu1Model controller) { }

    @Test void reinitializationDiscardsPendingConverterAndControllerHistory(@TempDir Path directory)
            throws Exception {
        var fixture = load(directory);
        var generator = fixture.generator;
        var controller = fixture.controller;
        var method = com.interpss.dstab.algo.DynamicSimuMethod.MODIFIED_EULER;
        assertFalse(generator.nextStep(.001, method, 1));
        assertTrue(generator.nextStep(.001, method, 0));
        assertTrue(generator.initStates(generator.getDStabBus()));
        var converterState = generator.getNamedStates();
        var controlState = controller.getNamedStates();
        assertFalse(generator.nextStep(.001, method, 1));
        assertFalse(controller.step(.001, 1));
        assertEquals(converterState, generator.getNamedStates());
        assertEquals(controlState, controller.getNamedStates());
        assertTrue(generator.nextStep(.001, method, 0));
        assertTrue(generator.nextStep(.001, method, 1));
    }
}
