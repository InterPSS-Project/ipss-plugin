package org.interpss.core.adapter.builder.dstab;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;

import org.interpss.CorePluginTestSetup;
import org.interpss.dstab.control.gov.psse.hygovr.PsseHygovrGovernorData;
import org.interpss.dstab.control.gov.psse.lcfb1.Lcfb1Data;
import org.interpss.dstab.control.gov.psse.lcfb1.Lcfb1PrefController;
import org.interpss.dstab.mach.GenqecData;
import org.interpss.dstab.mach.Gentpj1Data;
import org.interpss.dstab.mach.IeeeVcRoundRotorMachine;
import org.interpss.numeric.datatype.Unit.UnitType;
import org.interpss.fadapter.builder.DStabNetworkBuilder;
import org.interpss.fadapter.psse.PSSEDStabDirectParser;
import org.interpss.fadapter.psse.dyr.DynamicModelCatalog;
import org.interpss.fadapter.psse.dyr.DynamicModelImportStatus;
import org.interpss.fadapter.psse.dyr.DynamicModelSupportStatus;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.interpss.dstab.algo.DynamicSimuMethod;
import com.interpss.dstab.algo.DynamicSimuAlgorithm;
import com.interpss.dstab.DStabObjectFactory;
import com.interpss.dstab.cache.StateMonitor;
import com.interpss.dstab.mach.Machine;

public class Lcfb1PrefControllerTest extends CorePluginTestSetup {
    private static final double TOL = 1.0e-9;

    @Test
    void mapsNativeRecordAfterGovernorRegardlessOfRecordOrder(@TempDir Path tempDir)
            throws Exception {
        DStabNetworkBuilder builder = DStabBuilderTestFixture.createWithMachine();
        Path dyr = tempDir.resolve("lcfb1.dyr");
        Files.writeString(dyr,
                "1 'LCFB1' 1 0 1 0.0 1.0 0.0 0.1 0.0 .00015 .025 /\n"
                + "1 'TGOV1' 1 .05 .1 1.0 0.0 .2 .5 0.0 /\n");

        PSSEDStabDirectParser parser = new PSSEDStabDirectParser(builder).setStrictImport(true);
        parser.parseDynFile(dyr.toString());
        Machine machine = builder.getDStabNetwork().getMachine("Bus1-mach1");
        Lcfb1PrefController controller = find(builder);

        assertNotNull(controller);
        assertEquals(Set.of("Pelec Sensed", "Integral"),
                controller.getNamedStates().keySet());
        assertSame(machine.getGovernor(), controller.getGovernor());
        assertEquals(0, controller.getData().frequencyBiasFlag());
        assertEquals(1, controller.getData().powerControlFlag());
        assertEquals(1.0, controller.getData().powerTransducerTime(), TOL);
        assertEquals(.00015, controller.getData().integralGain(), TOL);
        assertEquals(.025, controller.getData().maximumReferenceBias(), TOL);
        assertEquals(2, parser.getLastImportReport().count(DynamicModelImportStatus.ATTACHED));
        assertTrue(parser.getLastImportReport().isStrictlyComplete());
    }

    @Test
    void catalogDefinesNativeSchemaAndPowerWorldAlias() {
        var descriptor = DynamicModelCatalog.find("LCFB1_PTI").orElseThrow();
        assertEquals("LCFB1", descriptor.canonicalName());
        assertEquals(9, descriptor.parameterCount());
        assertEquals(DynamicModelSupportStatus.LOADABLE, descriptor.supportStatus());
    }

    @Test
    void initializesAtSolvedPowerWithoutReferenceStep() throws Exception {
        Fixture f = fixture(new Lcfb1Data(1, 1, 10, .1, 0, .1, 1, 1, .05));
        assertEquals(.4, f.controller.getPowerSetpoint(), TOL);
        assertEquals(.4, f.controller.getSensedPower(), TOL);
        assertEquals(0.0, f.controller.getReferenceBias(), TOL);
        assertEquals(.4, f.controller.getGovernorReference(), TOL);
        for (int i = 0; i < 100; i++) step(f.controller, .01);
        assertEquals(0.0, f.controller.getReferenceBias(), TOL);
    }

    @Test
    void solvedPowerFallbackConvertsSystemToMachineBase() throws Exception {
        var builder = classicalMachineBuilder();
        var machine = builder.getDStabNetwork().getMachine("Bus1-mach1");
        machine.setRating(200, org.interpss.numeric.datatype.Unit.UnitType.mVA,
                builder.getDStabNetwork().getBaseKva());
        machine.setPe(0); machine.setPm(0); machine.setSpeed(1);
        builder.addGovTgov1("Bus1", "1", .05, .1, 1, 0, .2, .5, 0);
        var controller = builder.addLcfb1("Bus1", "1",
                new Lcfb1Data(0, 1, 0, 1, 0, .1, 0, .01, .025));
        assertTrue(controller.initStates(machine.getDStabBus()));
        assertEquals(.7164 / 2, controller.getSensedPower(), TOL);
        assertEquals(.7164 / 2, controller.getPowerSetpoint(), TOL);
    }

    @Test
    void typeJTransducerUsesTerminalPowerWithNonzeroStatorResistance() throws Exception {
        for (double transducerTime : new double[]{0.0, 0.1}) {
            var builder = DStabBuilderTestFixture.createBuilder();
            var bus = builder.getDStabNetwork().getDStabBus("Bus1");
            var machine = builder.addGentpj1("Bus1", "1", 100.0, 16.5,
                    new Gentpj1Data(5., .04, .5, .03, 3., .1, 1.8, 1., .6, .5, .3, .3, .1, .1, .3, 0.));
            machine.setRa(.02);
            var governor = builder.addGovTgov1("Bus1", "1", .05, .1, 1, 0, .2, .5, 0);
            var controller = builder.addLcfb1("Bus1", "1",
                    new Lcfb1Data(0, 1, 0, transducerTime, 0, .1, 0, .1, .5));
            double dispatch = machine.getParentGen().getGen().getReal();
            assertTrue(controller.initStates(bus));
            assertTrue(machine.initStates(bus));
            assertTrue(governor.initStates(bus, machine));
            assertTrue(machine.getPe() - dispatch > .005, "The fixture must have measurable stator loss");
            for (int i = 0; i < 10; i++) step(controller, .01);
            assertEquals(dispatch, controller.getSensedPower(), TOL);
            assertEquals(0.0, controller.getReferenceBias(), TOL);

            bus.setVoltage(.98, .025);
            var voltage = bus.getVoltage();
            var current = machine.getIgen().subtract(voltage.multiply(machine.getYgen()));
            double terminalPower = voltage.multiply(current.conjugate()).getReal();
            double airGapPower = machine.getPe();
            assertTrue(Math.abs(terminalPower - dispatch) > 1e-4);
            step(controller, .01);
            double coefficient = .01 / transducerTime;
            double expected = transducerTime == 0 ? terminalPower
                    : dispatch + (coefficient - .5 * coefficient * coefficient) * (terminalPower - dispatch);
            assertEquals(expected, controller.getSensedPower(), TOL);
            assertEquals(airGapPower, machine.getPe(), 0.0, "Reading terminal MW must leave Pe untouched");

            assertTrue(controller.initStates(bus));
            assertEquals(dispatch, controller.getPowerSetpoint(), TOL, "Reinitialization uses solved dispatch");
        }
    }

    @Test
    void roundRotorTransducerReadsMachineBasePowerFromTheStatorBoundary() throws Exception {
        var builder = DStabBuilderTestFixture.createWithMachine();
        var bus = builder.getDStabNetwork().getDStabBus("Bus1");
        var machine = (IeeeVcRoundRotorMachine) builder.getDStabNetwork().getMachine("Bus1-mach1");
        machine.setRating(200.0, UnitType.mVA, builder.getDStabNetwork().getBaseKva());
        machine.calMultiFactors();
        machine.setRa(.04);
        var governor = builder.addGovTgov1("Bus1", "1", .05, .1, 1, 0, .2, .5, 0);
        var controller = builder.addLcfb1("Bus1", "1",
                new Lcfb1Data(0, 1, 0, .1, 0, .1, 0, .1, .5));
        double dispatch = machine.getParentGen().getGen().getReal() / machine.getRating();

        assertTrue(controller.initStates(bus));
        assertTrue(machine.initStates(bus));
        assertTrue(governor.initStates(bus, machine));
        machine.getIgen();
        assertTrue(machine.getPe() - dispatch > .004, "The fixture must have measurable stator loss");
        var initialBoundary = machine.captureRoundElectricalState();
        for (int i = 0; i < 10; i++) step(controller, .01);
        assertEquals(dispatch, controller.getSensedPower(), TOL);
        assertEquals(0.0, controller.getReferenceBias(), TOL);

        bus.setVoltage(.98, .025);
        assertEquals(dispatch, machine.getTerminalActivePower(), TOL, "The read must not recalculate current");
        var voltage = bus.getVoltage();
        var current = machine.getIgen().subtract(voltage.multiply(machine.getYgen()));
        double terminalPower = voltage.multiply(current.conjugate()).getReal() / machine.getRating();
        double swingInput = machine.getPe();
        assertTrue(Math.abs(terminalPower - dispatch) > 1e-4);
        assertEquals(terminalPower, machine.getTerminalActivePower(), TOL);
        step(controller, .01);
        assertEquals(dispatch + .095 * (terminalPower - dispatch), controller.getSensedPower(), TOL);
        assertEquals(swingInput, machine.getPe(), 0.0);

        assertTrue(controller.initStates(bus));
        assertEquals(dispatch, controller.getPowerSetpoint(), TOL, "Reinitialization uses solved dispatch");
        machine.restoreRoundElectricalState(initialBoundary);
        assertEquals(dispatch, machine.getTerminalActivePower(), TOL, "Rollback restores the terminal sample");
    }

    @Test
    void genqecHydroTransducerMeasuresTerminalPowerOnTurbineBase() throws Exception {
        var builder = DStabBuilderTestFixture.createBuilder();
        var network = builder.getDStabNetwork();
        var bus = network.getDStabBus("Bus1");
        var machine = builder.addGenqec("Bus1", "1", 100.0, 16.5,
                new GenqecData(3.17, 0.0, .025,
                        2.37, 1.87, .32, .52, .28, .20, .19,
                        6.81, .85, .02, .02, .233, .797,
                        0.0, 0.0, 0.0, .1, 1));
        machine.setRa(.025);
        var hydro = new PsseHygovrGovernorData();
        hydro.setR(.05);
        hydro.setAt(1.0);
        hydro.setTw(1.0);
        hydro.setTrate(80.0);
        var governor = builder.addGovHygovr1("Bus1", "1", hydro);
        var controller = builder.addLcfb1("Bus1", "1",
                new Lcfb1Data(0, 1, 0, .1, 0, .1, 0, .1, .5));

        double scale = 100.0 / 80.0;
        double dispatch = machine.getParentGen().getGen().getReal();
        assertTrue(controller.initStates(bus));
        assertEquals(dispatch * scale, controller.getPowerSetpoint(), TOL);
        assertTrue(machine.initStates(bus));
        assertTrue(governor.initStates(bus, machine));
        machine.getIgen();
        assertEquals(dispatch, machine.getTerminalActivePower(), 1.0e-7);
        assertTrue(machine.getPe() - dispatch > .005);
        step(controller, .01);
        assertEquals(dispatch * scale, controller.getSensedPower(), 1.0e-7);

        bus.setVoltage(.98, .025);
        assertEquals(dispatch, machine.getTerminalActivePower(), 1.0e-7);
        var voltage = bus.getVoltage();
        var current = machine.getIgen().subtract(voltage.multiply(machine.getYgen()));
        double terminalPower = voltage.multiply(current.conjugate()).getReal() / machine.getRating();
        double swingInput = machine.getPe();
        assertTrue(Math.abs(terminalPower - dispatch) > 1.0e-4);
        assertEquals(terminalPower, machine.getTerminalActivePower(), TOL);
        step(controller, .01);
        assertEquals(scale * (dispatch + .095 * (terminalPower - dispatch)),
                controller.getSensedPower(), 1.0e-7);
        assertEquals(swingInput, machine.getPe(), 0.0);
    }

    @Test
    void powerDeficitRaisesReferenceAndHonorsBiasLimit() throws Exception {
        Fixture f = fixture(new Lcfb1Data(0, 1, 0, 0, 0, 1, 2, 1, .05));
        f.machine.setPe(.3);
        for (int i = 0; i < 100; i++) step(f.controller, .01);
        assertEquals(.05, f.controller.getReferenceBias(), TOL);
        assertEquals(.45, f.controller.getGovernorReference(), TOL);
    }

    @Test
    void preservesInitializedGgovDroopReferenceOnUnequalBases() throws Exception {
        var builder = classicalMachineBuilder();
        Machine machine = builder.getDStabNetwork().getMachine("Bus1-mach1");
        machine.setRating(100, org.interpss.numeric.datatype.Unit.UnitType.mVA,
                builder.getDStabNetwork().getBaseKva());
        machine.setPm(.4); machine.setPe(.4); machine.setSpeed(1.0);
        var data = new org.interpss.dstab.control.gov.psse.ggov1.PsseGgov1GovernorData();
        data.setRselect(1); data.setR(.04); data.setTrate(55);
        data.setDm(0); data.setKimw(0); data.setAset(99);
        var governor = builder.addGovGgov1("Bus1", "1", data);
        assertTrue(governor.initStates(machine.getDStabBus(), machine));
        double reference = governor.getRefPoint();
        assertEquals(.04 * .4, reference, TOL);
        var controller = builder.addLcfb1("Bus1", "1",
                new Lcfb1Data(0, 1, 0, 0, 0, 1, .1, 0, .025));
        assertTrue(controller.initStates(machine.getDStabBus()));
        assertEquals(reference, governor.getRefPoint(), 0.0);
        for (int i = 0; i < 100; i++) {
            step(controller, .001);
            governor.nextStep(.001, DynamicSimuMethod.MODIFIED_EULER, machine, 0);
            governor.nextStep(.001, DynamicSimuMethod.MODIFIED_EULER, machine, 1);
        }
        assertEquals(reference, governor.getRefPoint(), TOL);
        assertEquals(.4, governor.getOutput(machine), TOL);
        machine.setPe(.3);
        step(controller, .001);
        assertEquals(.01, controller.getReferenceBias(), TOL);
        assertEquals(reference + .01 * .55, governor.getRefPoint(), TOL);

        // Exercise the actual network lifecycle too: the bus auxiliary is
        // initialized before the host governor. The first predictor must use
        // the host's subsequently initialized Pref0, not its old/default value.
        governor.setRefPoint(.3);
        machine.setPe(.4);
        assertTrue(controller.initStates(machine.getDStabBus()));
        assertTrue(governor.initStates(machine.getDStabBus(), machine));
        step(controller, .001);
        assertEquals(reference, governor.getRefPoint(), TOL);
    }

    @Test
    void integralPathContinuesUntilItsOwnLimitWhileFinalSumIsSaturated() throws Exception {
        Fixture f = fixture(new Lcfb1Data(0, 1, 0, 0, 0, 1, 1, 1, .05));
        f.machine.setPe(.3);
        for (int i = 0; i < 10; i++) step(f.controller, .01);

        // The first modified-Euler predictor still sees the initialized power;
        // its corrector contributes half a step before nine full increments.
        assertEquals(.0095, f.controller.getNamedState("Integral"), TOL);
        assertEquals(.05, f.controller.getReferenceBias(), TOL);
    }

    @Test
    void preservesHydroDigitalGovernorSpeedReference() throws Exception {
        var builder = classicalMachineBuilder();
        var machine = builder.getDStabNetwork().getMachine("Bus1-mach1");
        machine.setPm(.4); machine.setPe(.4); machine.setSpeed(1);
        var data = new org.interpss.dstab.control.gov.psse.hygovr.PsseHygovrGovernorData();
        data.setR(.05); data.setAt(1); data.setTrate(0); data.setTw(1);
        var governor = builder.addGovHygovr1("Bus1", "1", data);
        assertTrue(governor.initStates(machine.getDStabBus(), machine));
        double reference = governor.getRefPoint();
        assertEquals(.02, reference, TOL);
        var controller = builder.addLcfb1("Bus1", "1",
                new Lcfb1Data(0, 1, 0, 0, 0, 1, .1, 0, .025));
        assertTrue(controller.initStates(machine.getDStabBus()));
        step(controller, .001);
        assertEquals(reference, governor.getRefPoint(), TOL);
        machine.setPe(.3);step(controller, .001);
        assertEquals(reference + .01, governor.getRefPoint(), TOL);
    }

    @Test
    void underfrequencyBiasRaisesGovernorReference() throws Exception {
        Fixture f = fixture(new Lcfb1Data(1, 0, 10, 0, 0, 1, .5, 0, .2));
        f.machine.setSpeed(.99);
        step(f.controller, .01);
        assertEquals(.05, f.controller.getReferenceBias(), 1.0e-12);
    }

    @Test
    void continuousDeadbandAndErrorClampPrecedePi() throws Exception {
        Fixture f = fixture(new Lcfb1Data(0, 1, 0, 0, .02, .03, 2, 0, 1));
        f.machine.setPe(.30);
        step(f.controller, .01);
        assertEquals(.06, f.controller.getReferenceBias(), TOL);
    }

    @Test
    void powerTransducerMatchesFirstOrderResponse() throws Exception {
        Fixture f = fixture(new Lcfb1Data(0, 1, 0, .5, 0, 1, 0, 0, 1));
        f.machine.setPe(.3);
        double dt = .0005;
        for (int i = 0; i < 2000; i++) step(f.controller, dt);
        double expected = .3 + .1 * Math.exp(-1.0 / .5);
        assertEquals(expected, f.controller.getSensedPower(), 2.0e-8);
    }

    @Test
    void participatesInFullDynamicAlgorithmWithoutReplacingGovernor() throws Exception {
        DStabNetworkBuilder builder = DStabBuilderTestFixture.createWithMachine();
        Machine machine = builder.getDStabNetwork().getMachine("Bus1-mach1");
        var governor = builder.addGovTgov1("Bus1", "1", .05, .1, 1, 0, .2, .5, 0);
        Lcfb1PrefController controller = builder.addLcfb1("Bus1", "1",
                new Lcfb1Data(1, 1, 10, .1, 0, .1, 1, 1, .05));
        DynamicSimuAlgorithm algorithm = DStabObjectFactory
                .createDynamicSimuAlgorithm(builder.getDStabNetwork());
        algorithm.setSimuMethod(DynamicSimuMethod.MODIFIED_EULER);
        algorithm.setSimuStepSec(.005);
        algorithm.setTotalSimuTimeSec(.02);
        algorithm.setSimuOutputHandler(new StateMonitor());

        assertTrue(algorithm.getAclfAlgorithm().loadflow());
        assertTrue(algorithm.initialization());
        assertTrue(algorithm.performSimulation());
        assertSame(governor, machine.getGovernor());
        assertTrue(Math.abs(controller.getReferenceBias()) <= .05 + TOL);
        assertTrue(Double.isFinite(controller.getGovernorReference()));
    }

    private static DStabNetworkBuilder classicalMachineBuilder() throws Exception {
        var builder = DStabBuilderTestFixture.createBuilder();
        builder.addGencls("Bus1", "1", 100.0, 16.5, 5.0, 0.0, 0.0, .25);
        return builder;
    }

    private static Fixture fixture(Lcfb1Data data) throws Exception {
        DStabNetworkBuilder builder = classicalMachineBuilder();
        Machine machine = builder.getDStabNetwork().getMachine("Bus1-mach1");
        machine.setPm(.4); machine.setPe(.4); machine.setSpeed(1.0);
        assertNotNull(builder.addGovTgov1("Bus1", "1", .05, .1, 1, 0, .2, .5, 0));
        Lcfb1PrefController controller = builder.addLcfb1("Bus1", "1", data);
        assertNotNull(controller);
        assertTrue(controller.initStates(machine.getDStabBus()));
        return new Fixture(machine, controller);
    }

    private static Lcfb1PrefController find(DStabNetworkBuilder builder) {
        return builder.getDStabNetwork().getDStabBus("Bus1").getDynamicBusDeviceList()
                .stream().filter(Lcfb1PrefController.class::isInstance)
                .map(Lcfb1PrefController.class::cast).findFirst().orElse(null);
    }

    private static void step(Lcfb1PrefController controller, double dt) {
        controller.nextStep(dt, DynamicSimuMethod.MODIFIED_EULER, 0);
        controller.nextStep(dt, DynamicSimuMethod.MODIFIED_EULER, 1);
    }

    private record Fixture(Machine machine, Lcfb1PrefController controller) { }
}
