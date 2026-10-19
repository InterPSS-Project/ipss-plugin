package org.interpss.core.adapter.builder.dstab;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;

import org.interpss.CorePluginTestSetup;
import org.interpss.dstab.control.gov.psse.ggov1.PsseGgov1Governor;
import org.interpss.dstab.control.gov.psse.ggov1.PsseGgov1GovernorData;
import org.interpss.fadapter.builder.DStabNetworkBuilder;
import org.interpss.fadapter.psse.PSSEDStabDirectParser;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import com.interpss.dstab.algo.DynamicSimuMethod;
import com.interpss.dstab.mach.Machine;

public class PsseGgov1GovernorTest extends CorePluginTestSetup {
    private static final double TOL = 1.0e-9;

    @Test
    void stateAccessRetainsExactSolverRatesAndResetsWithoutAdvancing() throws Exception {
        var builder = DStabBuilderTestFixture.createWithMachine();
        var machine = builder.getDStabNetwork().getMachine("Bus1-mach1");
        machine.setSpeed(1.0); machine.setPm(.4); machine.setPe(.4);
        var data = texasData(); data.setTrate(0.0); data.setTeng(.05);
        var governor = builder.addGovGgov1("Bus1", "1", data);
        governor.configureIntegrationStep(.001);
        assertTrue(governor.initStates(machine.getDStabBus(), machine));
        assertTrue(governor.getStateDerivativeSnapshot().rates().isEmpty());
        machine.setPe(.2);
        double[] rates = new double[10];
        governor.evaluateStaggered(.001, rates, 0);
        var snapshot = governor.getStateDerivativeSnapshot();
        assertEquals(org.interpss.dstab.control.util.IntegrationStateMonitor.Stage.STAGGERED, snapshot.stage());
        assertEquals(rates[0], snapshot.rates().get("Electrical Power"), 0.0);
        assertEquals(-.2 / data.getTpelec(), rates[0], TOL);
        var states = governor.getIntegrationStates();
        double output = governor.getOutput(machine), selector = governor.getFsr();
        for (int i=0;i<20;i++) {
            assertEquals(snapshot, governor.getStateDerivativeSnapshot());
            assertEquals(states, governor.getIntegrationStates());
            assertEquals(output, governor.getOutput(machine), 0.0);
            assertEquals(selector, governor.getFsr(), 0.0);
        }
        org.junit.jupiter.api.Assertions.assertThrows(UnsupportedOperationException.class,
                () -> snapshot.rates().put("Electrical Power", 0.0));
        governor.nextStep(.001, DynamicSimuMethod.MODIFIED_EULER, machine, 0);
        assertEquals(org.interpss.dstab.control.util.IntegrationStateMonitor.Stage.PREDICTOR,
                governor.getStateDerivativeSnapshot().stage());
        governor.nextStep(.001, DynamicSimuMethod.MODIFIED_EULER, machine, 1);
        assertEquals(org.interpss.dstab.control.util.IntegrationStateMonitor.Stage.CORRECTOR,
                governor.getStateDerivativeSnapshot().stage());
        assertEquals(rates[0], snapshot.rates().get("Electrical Power"), 0.0);
        assertTrue(governor.initStates(machine.getDStabBus(), machine));
        assertTrue(governor.getStateDerivativeSnapshot().rates().isEmpty());
    }

    @Test
    void sharedIntegratorSamplesAllTenStatesWithoutAdvancingTransportHistory() throws Exception {
        var builder = DStabBuilderTestFixture.createWithMachine();
        Machine machine = builder.getDStabNetwork().getMachine("Bus1-mach1");
        machine.setSpeed(1.0);machine.setPm(.4);machine.setPe(.4);
        var data = texasData();data.setTrate(0.0);data.setTeng(.05);
        var governor = builder.addGovGgov1("Bus1", "1", data);
        governor.configureIntegrationStep(.01);
        assertTrue(governor.initStates(builder.getDStabNetwork().getDStabBus("Bus1"),machine));
        assertEquals(10,governor.staggeredStateCount());
        double[] initial = new double[10], observed = new double[10], rates = new double[10];
        governor.readStaggeredState(initial,0);
        machine.setPe(.2);machine.setSpeed(.99);
        governor.evaluateStaggered(.01,rates,0);
        double[] firstRates = rates.clone();
        assertEquals(-.2/data.getTpelec(),rates[0],TOL);
        for (int i=0;i<100;i++) {
            governor.evaluateStaggered(.01,rates,0);
            governor.readStaggeredState(observed,0);
            assertArrayEquals(initial,observed,0.0);
            assertArrayEquals(firstRates,rates,0.0);
        }
        double[] next = initial.clone();
        for (int i=0;i<next.length;i++) next[i]+=.015*rates[i];
        governor.applyStaggeredState(next,0,.01);
        governor.readStaggeredState(observed,0);
        assertArrayEquals(next,observed,0.0,"every state coordinate must survive the commit");
        governor.completeStaggeredStep(.01);
        governor.readStaggeredState(observed,0);
        assertArrayEquals(next,observed,0.0,"committing transport history cannot integrate again");
    }

    @ParameterizedTest(name = "selector endpoint recurrence at step {0}")
    @CsvSource({"0.0005", "0.001"})
    void staggeredAccelerationSelectorUsesThePrecedingDerivativeEndpoint(double dt) throws Exception {
        var builder = DStabBuilderTestFixture.createWithMachine();
        Machine machine = builder.getDStabNetwork().getMachine("Bus1-mach1");
        machine.setSpeed(1.0); machine.setPm(.4); machine.setPe(.4);
        var data = texasData();
        data.setTrate(0.0); data.setRselect(0); data.setR(0.0);
        data.setKpgov(.01); data.setKigov(.01); data.setLdref(10.0);
        var governor = builder.addGovGgov1("Bus1", "1", data);
        governor.configureIntegrationStep(dt);
        assertTrue(governor.initStates(machine.getDStabBus(), machine));
        machine.setSpeed(1.005);
        double[] values = new double[10], rates = new double[10], history = new double[10];
        governor.evaluateStaggered(dt, rates, 0);
        for (int step = 0; step < 20; step++) {
            double precedingSelector = governor.getFsr();
            governor.readStaggeredState(values, 0);
            governor.evaluateStaggered(dt, rates, 0);
            for (int i = 0; i < values.length; i++) {
                values[i] += dt * (1.5 * rates[i] - .5 * history[i]);
                history[i] = rates[i];
            }
            governor.applyStaggeredState(values, 0, dt);
            governor.completeStaggeredStep(dt);
            double acceleration = (machine.getSpeed() - 1.0 - values[7]) / data.getTa();
            double expectedSelector = precedingSelector
                    + data.getKa() * dt * (data.getAset() - acceleration);
            assertTrue(expectedSelector > governor.getEffectiveVmin()
                    && expectedSelector < governor.getEffectiveVmax());
            for (int observation = 0; observation < 3; observation++) {
                governor.evaluateStaggered(dt, rates, 0);
                assertEquals(expectedSelector, governor.getFsr(), 1.e-12,
                        "a state update must not recompute the preceding selector reference");
            }
        }
    }

    @Test
    void builderAttachesExactDataIncludingTransportDelay() throws Exception {
        DStabNetworkBuilder builder = DStabBuilderTestFixture.createWithMachine();
        PsseGgov1GovernorData data = texasData();
        PsseGgov1Governor governor = builder.addGovGgov1("Bus1", "1", data);

        assertNotNull(governor);
        assertSame(governor, builder.getDStabNetwork().getMachine("Bus1-mach1").getGovernor());
        assertEquals(1, governor.getData().getRselect());
        assertEquals(390.24, governor.getData().getTrate(), TOL);
        assertEquals(4.0, governor.getData().getTsa(), TOL);
        assertEquals(-99.0, governor.getData().getRdown(), TOL);

        data.setTeng(0.1);
        DStabNetworkBuilder delayBuilder = DStabBuilderTestFixture.createWithMachine();
        assertNotNull(delayBuilder.addGovGgov1("Bus1", "1", data));
    }

    @Test
    void initializesOnTrateBaseWithoutDriftAndRespondsToFrequency() throws Exception {
        DStabNetworkBuilder builder = DStabBuilderTestFixture.createWithMachine();
        Machine machine = builder.getDStabNetwork().getMachine("Bus1-mach1");
        machine.setSpeed(1.0);
        machine.setPm(0.4);
        machine.setPe(0.4);
        PsseGgov1GovernorData data = texasData();
        data.setTrate(50.0);
        PsseGgov1Governor governor = builder.addGovGgov1("Bus1", "1", data);

        assertTrue(governor.initStates(builder.getDStabNetwork().getDStabBus("Bus1"), machine));
        assertEquals(50.0, governor.getGovernorBaseMva(machine), TOL);
        assertEquals(0.4, governor.getOutput(machine), TOL);
        double initialValve = governor.getValveStroke();
        for (int i = 0; i < 200; i++) step(governor, machine, 0.005);
        assertEquals(0.4, governor.getOutput(machine), 2.0e-8);
        assertEquals(initialValve, governor.getValveStroke(), 2.0e-8);

        machine.setSpeed(0.99);
        for (int i = 0; i < 400; i++) step(governor, machine, 0.005);
        assertTrue(governor.getValveStroke() > initialValve + 1.0e-3);
        assertTrue(governor.getOutput(machine) > 0.4);
    }

    @Test
    void directParserUsesPsseFieldOrder(@TempDir Path tempDir) throws Exception {
        DStabNetworkBuilder builder = DStabBuilderTestFixture.createWithMachine();
        Path dyr = tempDir.resolve("ggov1.dyr");
        Files.writeString(dyr, "1 'GGOV1' 1 1 0 0.03 1 0.05 -0.05 4.18 0.71 0 1 "
                + "1 0.15 0.4 1.22 0.18 0.16 0 0 3 2.56 0.85 1 0 1 -1 0 "
                + "0.01 10 0.1 390.24 0 4 5 99 -99 /\n");

        PSSEDStabDirectParser parser = new PSSEDStabDirectParser(builder).setStrictImport(true);
        parser.parseDynFile(dyr.toString());
        PsseGgov1Governor governor = (PsseGgov1Governor) builder.getDStabNetwork()
                .getMachine("Bus1-mach1").getGovernor();
        assertNotNull(governor);
        assertArrayEquals(new double[] { 1, 0, .03, 1, .05, -.05, 4.18, .71, 0, 1,
                1, .15, .4, 1.22, .18, .16, 0, 0, 3, 2.56, .85, 1, 0, 1, -1,
                0, .01, 10, .1, 390.24, 0, 4, 5, 99, -99 }, values(governor.getData()), TOL);
        assertTrue(parser.getLastImportReport().isStrictlyComplete());
    }

    @Test
    void ggov1duParserMapsFinalDeadbandFieldsAndDeadbandsOnlyFrequency(@TempDir Path tempDir)
            throws Exception {
        DStabNetworkBuilder builder = DStabBuilderTestFixture.createWithMachine();
        Path dyr = tempDir.resolve("ggov1du.dyr");
        Files.writeString(dyr, "1 'GGOV1DU' 1 0 0 0.0 1 0.05 -0.05 4.18 0.71 0 1 "
                + "1 0.15 0.4 1.22 0.18 0.16 0 0 3 0 0 1 0 1 -1 0 "
                + "0.01 0 0.1 50.0 0 4 5 99 -99 0.002 -0.003 /\n");

        PSSEDStabDirectParser parser = new PSSEDStabDirectParser(builder).setStrictImport(true);
        parser.parseDynFile(dyr.toString());
        Machine machine = builder.getDStabNetwork().getMachine("Bus1-mach1");
        machine.setSpeed(1.0);
        machine.setPm(0.4);
        machine.setPe(0.4);
        PsseGgov1Governor governor = (PsseGgov1Governor) machine.getGovernor();

        assertNotNull(governor);
        assertEquals("GGOV1D", governor.getName());
        assertEquals(0.002, governor.getData().getDbH(), TOL);
        assertEquals(-0.003, governor.getData().getDbL(), TOL);
        assertTrue(governor.initStates(builder.getDStabNetwork().getDStabBus("Bus1"), machine));
        assertEquals(10, governor.getNamedStates().size());
        assertEquals(governor.getValveStroke(), governor.getNamedState("Valve Stroke"), TOL);
        assertEquals(governor.getMeasuredElectricalPower(),
                governor.getNamedState("Electrical Power"), TOL);
        assertEquals(0.0, governor.applyFrequencyDeadband(-0.002), TOL);
        assertEquals(-0.007, governor.applyFrequencyDeadband(-0.010), TOL);
        assertEquals(0.008, governor.applyFrequencyDeadband(0.010), TOL);
        assertTrue(parser.getLastImportReport().isStrictlyComplete());
    }

    @Test
    void dieselTransportDelayHoldsTurbineInputForItsPhysicalDuration() throws Exception {
        DStabNetworkBuilder builder = DStabBuilderTestFixture.createWithMachine();
        Machine machine = builder.getDStabNetwork().getMachine("Bus1-mach1");
        machine.setSpeed(1.0);
        machine.setPm(0.4);
        machine.setPe(0.4);
        PsseGgov1GovernorData data = texasData();
        data.setTeng(.05);
        data.setTb(0.0);
        data.setTc(0.0);
        PsseGgov1Governor governor = builder.addGovGgov1("Bus1", "1", data);
        assertTrue(governor.initStates(builder.getDStabNetwork().getDStabBus("Bus1"), machine));

        machine.setSpeed(.99);
        for (int i = 0; i < 9; i++) step(governor, machine, .005);
        assertEquals(.4, governor.getOutput(machine), 1.0e-9);
        for (int i = 0; i < 20; i++) step(governor, machine, .005);
        assertTrue(governor.getOutput(machine) > .4);
    }

    @ParameterizedTest(name = "GGOV1 Rselect={0}, Flag={1}")
    @CsvSource({"1,0", "1,1", "0,0", "0,1", "-1,0", "-1,1", "-2,0", "-2,1"})
    void supportsEveryDocumentedDroopAndFuelSourceCombination(int rselect, int flag)
            throws Exception {
        DStabNetworkBuilder builder = DStabBuilderTestFixture.createWithMachine();
        Machine machine = builder.getDStabNetwork().getMachine("Bus1-mach1");
        machine.setSpeed(1.0);
        machine.setPm(0.4);
        machine.setPe(0.4);
        PsseGgov1GovernorData data = texasData();
        data.setRselect(rselect);
        data.setFlag(flag);
        if (rselect == 0) data.setR(0.0);
        PsseGgov1Governor governor = builder.addGovGgov1("Bus1", "1", data);

        assertNotNull(governor);
        assertTrue(governor.initStates(machine.getDStabBus(), machine));
        machine.setSpeed(0.995);
        for (int i = 0; i < 20; i++) step(governor, machine, 0.005);
        assertTrue(Double.isFinite(governor.getOutput(machine)));
        assertTrue(Double.isFinite(governor.getDroopFeedback()));
        assertTrue(Double.isFinite(governor.getFuelFlow()));
    }

    @Test
    void rselectUsesPublishedElectricalValveAndGovernorOutputSignals() throws Exception {
        PsseGgov1Governor electrical = initializedGovernor(1, 0);
        assertEquals(0.4 / 3.9024, electrical.getDroopFeedback(), TOL,
                "Electrical-power droop feedback is expressed on the Trate governor base");

        PsseGgov1Governor valve = initializedGovernor(-1, 0);
        valve.getMachine().setSpeed(0.99);
        for (int i = 0; i < 20; i++) step(valve, valve.getMachine(), 0.005);
        assertEquals(valve.getValveStroke(), valve.getDroopFeedback(), TOL);

        PsseGgov1Governor governorOutput = initializedGovernor(-2, 0);
        governorOutput.getMachine().setSpeed(0.99);
        for (int i = 0; i < 20; i++) step(governorOutput, governorOutput.getMachine(), 0.005);
        assertEquals(governorOutput.getFsr(), governorOutput.getDroopFeedback(), TOL);
        assertTrue(Math.abs(governorOutput.getFsr() - governorOutput.getValveStroke()) > 1.0e-5,
                "Requested and true valve stroke must diverge to distinguish Rselect -2 from -1");

        PsseGgov1Governor isochronous = initializedGovernor(0, 0);
        assertEquals(0.0, isochronous.getDroopFeedback(), TOL);
    }

    @Test
    void flagOneMakesFuelFlowProportionalToShaftSpeed() throws Exception {
        PsseGgov1Governor independent = initializedGovernor(1, 0);
        PsseGgov1Governor proportional = initializedGovernor(1, 1);
        independent.getMachine().setSpeed(0.98);
        proportional.getMachine().setSpeed(0.98);

        assertEquals(independent.getValveStroke(), independent.getFuelFlow(), TOL);
        assertEquals(0.98 * proportional.getValveStroke(), proportional.getFuelFlow(), TOL);
    }

    @ParameterizedTest
    @CsvSource({"0,0,.98", "0,0,1.02", "0,1,.98", "0,1,1.02",
            ".2,0,.98", ".2,1,1.02", "-1,0,.98", "-1,1,1.02"})
    void temperatureDetectorKeepsFuelFlagSeparateFromNegativeDampingCharacteristic(
            double damping, int flag, double speed) throws Exception {
        var builder = DStabBuilderTestFixture.createWithMachine();
        var machine = builder.getDStabNetwork().getMachine("Bus1-mach1");
        machine.setSpeed(1.0); machine.setPm(.4); machine.setPe(.4);
        var data = texasData();
        data.setTrate(0.0); data.setDm(damping); data.setFlag(flag);
        data.setTsa(2.0); data.setTsb(4.0); data.setTfload(3.0);
        var governor = builder.addGovGgov1("Bus1", "1", data);
        governor.configureIntegrationStep(.0005);
        assertTrue(governor.initStates(machine.getDStabBus(), machine));
        double[] initial = new double[10], rates = new double[10];
        governor.readStaggeredState(initial, 0);
        machine.setSpeed(speed);

        // The measured fuel has one Flag-selected speed factor. The separate
        // maximum-power characteristic is active only for negative Dm.
        double fuel = initial[3] * (flag == 1 ? speed : 1.0);
        double temperatureInput = fuel * (damping < 0.0 ? Math.pow(speed, damping) : 1.0);
        double leadOutput = .5 * initial[8] + .5 * temperatureInput;
        governor.evaluateStaggered(.0005, rates, 0);
        assertEquals(fuel, governor.getFuelFlow(), TOL);
        assertEquals(leadOutput, governor.getTemperatureDetectionLeadLagOutput(), TOL);
        assertEquals((temperatureInput - initial[8]) / 4.0, rates[8], TOL);
        assertEquals((leadOutput - initial[9]) / 3.0, rates[9], TOL);

        double[] next = initial.clone();
        next[8] += .0005 * rates[8]; next[9] += .0005 * rates[9];
        governor.applyStaggeredState(next, 0, .0005);
        governor.evaluateStaggered(.0005, rates, 0);
        assertEquals((temperatureInput - next[8]) / 4.0, rates[8], TOL);
        assertEquals((.5 * next[8] + .5 * temperatureInput - next[9]) / 3.0,
                rates[9], TOL);
    }

    @Test
    void normalizesPowerWorldLimitsWithoutOverwritingImportedData() throws Exception {
        DStabNetworkBuilder builder = DStabBuilderTestFixture.createWithMachine();
        Machine machine = builder.getDStabNetwork().getMachine("Bus1-mach1");
        machine.setPm(0.4);
        machine.setPe(0.4);
        PsseGgov1GovernorData data = texasData();
        data.setMaxerr(-0.08);
        data.setMinerr(0.04);
        data.setVmax(0.1);
        data.setVmin(1.2);
        data.setRopen(1.0);
        data.setRclose(-0.05);
        PsseGgov1Governor governor = builder.addGovGgov1("Bus1", "1", data);

        assertTrue(governor.initStates(machine.getDStabBus(), machine));
        assertEquals(0.04, governor.getEffectiveMaxerr(), TOL);
        assertEquals(-0.08, governor.getEffectiveMinerr(), TOL);
        assertEquals(1.0, governor.getEffectiveVmax(), TOL);
        assertTrue(governor.getEffectiveVmin() <= governor.getValveStroke());
        assertEquals(0.1, governor.getEffectiveRopen(), TOL);
        assertEquals(-0.1, governor.getEffectiveRclose(), TOL);

        assertEquals(-0.08, governor.getData().getMaxerr(), TOL);
        assertEquals(0.04, governor.getData().getMinerr(), TOL);
        assertEquals(0.1, governor.getData().getVmax(), TOL);
        assertEquals(1.2, governor.getData().getVmin(), TOL);
        assertEquals(1.0, governor.getData().getRopen(), TOL);
        assertEquals(-0.05, governor.getData().getRclose(), TOL);
    }

    @Test
    void correctedOpeningRateBoundsValveMotion() throws Exception {
        PsseGgov1Governor governor = initializedGovernor(1, 0);
        Machine machine = governor.getMachine();
        double initialValve = governor.getValveStroke();
        machine.setSpeed(0.90);

        double elapsed = 0.2;
        for (int i = 0; i < 40; i++) step(governor, machine, 0.005);

        assertTrue(governor.getValveStroke() > initialValve);
        assertTrue(governor.getValveStroke() <= initialValve
                + governor.getEffectiveRopen() * elapsed + 1.0e-9);
    }

    @ParameterizedTest
    @CsvSource({"1.0,-0.03", "0.04,-0.03"})
    void declaredValveRatesBoundBothActuatorDirections(double opening, double closing) throws Exception {
        var builder = DStabBuilderTestFixture.createWithMachine();
        var machine = builder.getDStabNetwork().getMachine("Bus1-mach1");
        machine.setSpeed(1.0); machine.setPm(.4); machine.setPe(.4);
        var data = texasData();
        data.setUseDeclaredValveRates(true);
        data.setRopen(opening); data.setRclose(closing);
        data.setKpgov(20.0); data.setTact(.01); data.setAset(99.0);
        var governor = builder.addGovGgov1("Bus1", "1", data);
        assertTrue(governor.initStates(machine.getDStabBus(), machine));
        assertTrue(governor.getData().isUseDeclaredValveRates());
        assertEquals(opening, governor.getEffectiveRopen(), TOL);
        assertEquals(closing, governor.getEffectiveRclose(), TOL);
        double[] rates = new double[governor.staggeredStateCount()];
        machine.setSpeed(.9); governor.evaluateStaggered(.001, rates, 0);
        assertEquals(opening, rates[3], TOL);
        machine.setSpeed(1.1); governor.evaluateStaggered(.001, rates, 0);
        assertEquals(closing, rates[3], TOL);
    }

    @ParameterizedTest
    @CsvSource({"GGOV1,false", "GGOV1D,true", "GGOV1DU,true"})
    void nativeImportsSelectDeclaredValveRates(String model, boolean deadband, @TempDir Path tempDir)
            throws Exception {
        var builder = DStabBuilderTestFixture.createWithMachine();
        Path dyr = tempDir.resolve("declared-rates.dyr");
        Files.writeString(dyr, "1 '" + model + "' 1 1 0 .03 1 .05 -.05 4.18 .71 0 1 "
                + "1 .15 .4 1.22 .18 .16 0 0 3 2.56 .85 1 0 1 -.03 0 "
                + ".01 10 .1 390.24 0 4 5 99 -99" + (deadband ? " 0 0" : "") + " /\n");
        var parser = new PSSEDStabDirectParser(builder).setStrictImport(true);
        parser.parseDynFile(dyr.toString());
        var machine = builder.getDStabNetwork().getMachine("Bus1-mach1");
        machine.setSpeed(1.0); machine.setPm(.4); machine.setPe(.4);
        var governor = (PsseGgov1Governor)machine.getGovernor();
        assertNotNull(governor);
        assertTrue(governor.getData().isUseDeclaredValveRates());
        assertTrue(governor.initStates(machine.getDStabBus(), machine));
        assertEquals(1.0, governor.getEffectiveRopen(), TOL);
        assertEquals(-.03, governor.getEffectiveRclose(), TOL);
        assertTrue(parser.getLastImportReport().isStrictlyComplete());
    }

    @Test
    void appliesPowerWorldTimeStepAutocorrectionsBeforeInitialization() throws Exception {
        DStabNetworkBuilder builder = DStabBuilderTestFixture.createWithMachine();
        Machine machine = builder.getDStabNetwork().getMachine("Bus1-mach1");
        machine.setPm(0.4);
        machine.setPe(0.4);
        PsseGgov1GovernorData data = texasData();
        data.setTpelec(0.004);
        data.setTb(0.006);
        data.setTa(0.004);
        data.setTsb(0.006);
        data.setTfload(0.004);
        data.setKturb(0.004);
        data.setKpgov(0.004);
        data.setKigov(1.0);
        data.setRup(120.0);
        data.setRdown(-20.0);
        PsseGgov1Governor governor = builder.addGovGgov1("Bus1", "1", data);
        governor.configureIntegrationStep(0.01);

        assertTrue(governor.initStates(machine.getDStabBus(), machine));
        assertEquals(0.0, governor.getEffectiveTpelec(), TOL);
        assertEquals(0.01, governor.getEffectiveTb(), TOL);
        assertEquals(0.0, governor.getEffectiveTa(), TOL);
        assertEquals(0.01, governor.getEffectiveTsb(), TOL);
        assertEquals(0.0, governor.getEffectiveTfload(), TOL);
        assertEquals(0.01, governor.getEffectiveKturb(), TOL);
        assertEquals(0.0, governor.getEffectiveKpgov(), TOL);
        assertEquals(99.0, governor.getEffectiveRup(), TOL);
        assertEquals(-99.0, governor.getEffectiveRdown(), TOL);

        assertEquals(0.004, data.getTpelec(), TOL);
        assertEquals(0.006, data.getTb(), TOL);
        assertEquals(0.004, data.getKturb(), TOL);
        assertEquals(0.004, data.getKpgov(), TOL);
    }

    @Test
    void feasibleTimeStepCorrectionsPreserveInitializedEquilibrium() throws Exception {
        DStabNetworkBuilder builder = DStabBuilderTestFixture.createWithMachine();
        Machine machine = builder.getDStabNetwork().getMachine("Bus1-mach1");
        machine.setPm(0.4);
        machine.setPe(0.4);
        PsseGgov1GovernorData data = texasData();
        data.setTpelec(0.004);
        data.setTb(0.006);
        data.setTa(0.004);
        data.setTsb(0.006);
        data.setTfload(0.004);
        data.setKpgov(0.004);
        data.setKigov(1.0);
        PsseGgov1Governor governor = builder.addGovGgov1("Bus1", "1", data);
        governor.configureIntegrationStep(0.01);
        assertTrue(governor.initStates(machine.getDStabBus(), machine));

        double initialOutput = governor.getOutput(machine);
        for (int i = 0; i < 100; i++) step(governor, machine, 0.01);
        assertEquals(initialOutput, governor.getOutput(machine), 1.0e-8,
                "Corrected parameters must preserve the initialized equilibrium");
    }

    @Test
    void honorsPowerWorldTimeStepAutocorrectionBoundaryValues() throws Exception {
        DStabNetworkBuilder builder = DStabBuilderTestFixture.createWithMachine();
        Machine machine = builder.getDStabNetwork().getMachine("Bus1-mach1");
        machine.setPm(0.4);
        machine.setPe(0.4);
        PsseGgov1GovernorData data = texasData();
        data.setTpelec(0.005);
        data.setTb(0.01);
        PsseGgov1Governor governor = builder.addGovGgov1("Bus1", "1", data);
        governor.configureIntegrationStep(0.01);

        assertTrue(governor.initStates(machine.getDStabBus(), machine));
        assertEquals(0.005, governor.getEffectiveTpelec(), TOL);
        assertEquals(0.01, governor.getEffectiveTb(), TOL);
    }

    @Test
    void supervisoryLoadControlRaisesFuelRequestWhenElectricalPowerFalls() throws Exception {
        DStabNetworkBuilder builder = DStabBuilderTestFixture.createWithMachine();
        Machine machine = builder.getDStabNetwork().getMachine("Bus1-mach1");
        machine.setPm(0.4);
        machine.setPe(0.4);
        PsseGgov1GovernorData data = texasData();
        data.setRselect(0);
        // GGOV1 limits the supervisory MW integrator to +/-1.1R, even when
        // Rselect=0 disables the primary droop-feedback signal.
        data.setR(0.05);
        data.setKimw(0.5);
        PsseGgov1Governor governor = builder.addGovGgov1("Bus1", "1", data);
        assertTrue(governor.initStates(machine.getDStabBus(), machine));
        double initialValve = governor.getValveStroke();

        machine.setPe(0.3);
        for (int i = 0; i < 200; i++) step(governor, machine, 0.005);

        assertTrue(governor.getValveStroke() > initialValve);
        assertTrue(governor.getOutput(machine) > 0.4);
    }

    @Test
    void loadRejectionClosesValveWithinRateAndPositionLimits() throws Exception {
        PsseGgov1Governor governor = initializedGovernor(1, 0);
        Machine machine = governor.getMachine();
        double initialValve = governor.getValveStroke();
        machine.setSpeed(1.10);

        double elapsed = 0.2;
        for (int i = 0; i < 40; i++) step(governor, machine, 0.005);

        assertTrue(governor.getValveStroke() < initialValve);
        assertTrue(governor.getValveStroke() >= initialValve
                + governor.getEffectiveRclose() * elapsed - 1.0e-9);
        assertTrue(governor.getValveStroke() >= governor.getEffectiveVmin() - TOL);
    }

    @Test
    void valveRemainsInsideExpandedUpperAndLowerPositionLimits() throws Exception {
        PsseGgov1Governor governor = initializedGovernor(1, 0);
        Machine machine = governor.getMachine();

        machine.setSpeed(0.8);
        for (int i = 0; i < 3000; i++) step(governor, machine, 0.005);
        assertTrue(governor.getValveStroke() <= governor.getEffectiveVmax() + TOL);

        machine.setSpeed(1.2);
        for (int i = 0; i < 3000; i++) step(governor, machine, 0.005);
        assertTrue(governor.getValveStroke() >= governor.getEffectiveVmin() - TOL);
    }

    private static PsseGgov1Governor initializedGovernor(int rselect, int flag)
            throws Exception {
        DStabNetworkBuilder builder = DStabBuilderTestFixture.createWithMachine();
        Machine machine = builder.getDStabNetwork().getMachine("Bus1-mach1");
        machine.setSpeed(1.0);
        machine.setPm(0.4);
        machine.setPe(0.4);
        PsseGgov1GovernorData data = texasData();
        data.setRselect(rselect);
        data.setFlag(flag);
        if (rselect == 0) data.setR(0.0);
        PsseGgov1Governor governor = builder.addGovGgov1("Bus1", "1", data);
        assertNotNull(governor);
        assertTrue(governor.initStates(machine.getDStabBus(), machine));
        return governor;
    }

    private static void step(PsseGgov1Governor governor, Machine machine, double dt) {
        governor.nextStep(dt, DynamicSimuMethod.MODIFIED_EULER, machine, 0);
        governor.nextStep(dt, DynamicSimuMethod.MODIFIED_EULER, machine, 1);
    }

    private static PsseGgov1GovernorData texasData() {
        PsseGgov1GovernorData d = new PsseGgov1GovernorData();
        d.setRselect(1); d.setFlag(0); d.setR(0.03); d.setTpelec(1.0);
        d.setMaxerr(0.05); d.setMinerr(-0.05); d.setKpgov(4.18); d.setKigov(0.71);
        d.setKdgov(0); d.setTdgov(1); d.setVmax(1); d.setVmin(0.15);
        d.setTact(0.4); d.setKturb(1.22); d.setWfnl(0.18); d.setTb(0.16);
        d.setTc(0); d.setTeng(0); d.setTfload(3); d.setKpload(2.56);
        d.setKiload(0.85); d.setLdref(1); d.setDm(0); d.setRopen(1); d.setRclose(-1);
        d.setKimw(0); d.setAset(0.01); d.setKa(10); d.setTa(0.1); d.setTrate(390.24);
        d.setDb(0); d.setTsa(4); d.setTsb(5); d.setRup(99); d.setRdown(-99);
        return d;
    }

    private static double[] values(PsseGgov1GovernorData d) {
        return new double[] { d.getRselect(), d.getFlag(), d.getR(), d.getTpelec(),
                d.getMaxerr(), d.getMinerr(), d.getKpgov(), d.getKigov(), d.getKdgov(),
                d.getTdgov(), d.getVmax(), d.getVmin(), d.getTact(), d.getKturb(),
                d.getWfnl(), d.getTb(), d.getTc(), d.getTeng(), d.getTfload(),
                d.getKpload(), d.getKiload(), d.getLdref(), d.getDm(), d.getRopen(),
                d.getRclose(), d.getKimw(), d.getAset(), d.getKa(), d.getTa(),
                d.getTrate(), d.getDb(), d.getTsa(), d.getTsb(), d.getRup(), d.getRdown() };
    }
}
