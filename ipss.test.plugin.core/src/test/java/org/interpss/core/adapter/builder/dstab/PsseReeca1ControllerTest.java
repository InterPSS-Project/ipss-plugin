package org.interpss.core.adapter.builder.dstab;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;

import com.interpss.dstab.DStabGen;

import org.apache.commons.math3.complex.Complex;
import org.interpss.CorePluginTestSetup;
import org.interpss.dstab.renewable.Regca1Data;
import org.interpss.dstab.renewable.Reeca1Data;
import org.interpss.dstab.renewable.Reeca1Model;
import org.interpss.dstab.renewable.Regca1Model;
import org.interpss.dstab.renewable.Repca1Data;
import org.interpss.dstab.renewable.Repca1Model;
import org.interpss.dstab.renewable.WindControlStack;
import org.interpss.dstab.renewable.Wttqa1Data;
import org.interpss.dstab.renewable.Wttqa1Model;
import org.interpss.fadapter.builder.DStabNetworkBuilder;
import org.interpss.fadapter.builder.AclfNetworkBuilder;
import org.interpss.fadapter.psse.PSSEDStabDirectParser;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

public class PsseReeca1ControllerTest extends CorePluginTestSetup {
    private static final double CONTROL_STEP = 1.0 / 960.0;

    @Test
    void converterAndElectricalTwoStepRatesExposeHoldsAndInitialSelector() throws Exception {
        var builder = DStabBuilderTestFixture.createBuilder();
        var converter = builder.addRegca1("Bus1", "1", new Regca1Data(
                0, .02, 10, .9, .4, 1.22, 10, .8, .4, -1.3, .02, 0, 0, 0, .8));
        converter.getParentGen().setGen(new Complex(.8, 1.e-6));
        converter.getDStabBus().setVoltage(Complex.ONE);
        var controller = new Reeca1Model(texasData(1, 1, 1), converter);
        converter.setReeca1Controller(controller);
        converter.setIntegrationScheme(Regca1Model.IntegrationScheme.TWO_STEP);
        assertTrue(converter.initStates(converter.getDStabBus()));
        assertEquals(1, converter.getReactiveRecoveryDirection());
        assertTrue(converter.getStateDerivativeSnapshot().rates().isEmpty());
        converter.getDStabBus().setVoltage(new Complex(.5, 0));
        converter.nextStep(.001, com.interpss.dstab.algo.DynamicSimuMethod.MODIFIED_EULER, 0);
        var converterSnapshot = converter.getStateDerivativeSnapshot();
        var controlSnapshot = controller.getStateDerivativeSnapshot();
        assertEquals(com.interpss.dstab.controller.runtime.IntegrationStateMonitor.Stage.TWO_STEP, controlSnapshot.stage());
        assertEquals(-25.0, converterSnapshot.rates().get("Filtered Voltage"), 1.e-12);
        assertEquals(0.0, controlSnapshot.rates().get("Active Power Order"), 0.0);
        assertEquals(0.0, controlSnapshot.rates().get("Reactive Control Integral"), 0.0);
        assertEquals(0.0, controlSnapshot.rates().get("Voltage Control Integral"), 0.0);
        converter.nextStep(.001, com.interpss.dstab.algo.DynamicSimuMethod.MODIFIED_EULER, 1);
        assertEquals(converterSnapshot, converter.getStateDerivativeSnapshot());
        assertEquals(controlSnapshot, controller.getStateDerivativeSnapshot());
        converter.getParentGen().setGen(new Complex(.8, 0));
        assertTrue(converter.initStates(converter.getDStabBus()));
        assertEquals(0, converter.getReactiveRecoveryDirection());
        assertTrue(converter.getStateDerivativeSnapshot().rates().isEmpty());
    }

    @Test
    void electricalStateAccessIsDetachedAndReportsAppliedRateSemantics() {
        var controller = new Reeca1Model(texasData(1, 1, 1), null);
        controller.initialize(.8, .2, 1.0);
        assertTrue(controller.getStateDerivativeSnapshot().rates().isEmpty());
        double initial = controller.getMeasuredVoltage();
        controller.step(.001, .8, .2, .95, 1.0, 0);
        var snapshot = controller.getStateDerivativeSnapshot();
        assertEquals(com.interpss.dstab.controller.runtime.IntegrationStateMonitor.Stage.APPLIED_STEP, snapshot.stage());
        assertEquals(initial, snapshot.evaluationStates().get("Measured Voltage"), 0.0);
        assertEquals((controller.getMeasuredVoltage()-initial)/.001,
                snapshot.rates().get("Measured Voltage"), 0.0);
        var states = controller.getNamedStates();
        var memory = controller.getControlMemory();
        for (int i=0;i<20;i++) {
            assertEquals(snapshot, controller.getStateDerivativeSnapshot());
            assertEquals(states, controller.getNamedStates());
            assertEquals(memory, controller.getControlMemory());
        }
        controller.step(.001, .8, .2, .94, 1.0, 1);
        assertEquals(snapshot, controller.getStateDerivativeSnapshot(), "Algebraic refresh retains the last actual rate evaluation");
        controller.initialize(.8, .2, 1.0);
        assertTrue(controller.getStateDerivativeSnapshot().rates().isEmpty());
    }

    @Test
    void directParserMapsCompleteFiftyOneParameterRecord(@TempDir Path tempDir) throws Exception {
        DStabNetworkBuilder builder = DStabBuilderTestFixture.createBuilder();
        Path dyr = tempDir.resolve("reeca1.dyr");
        Files.writeString(dyr,
                "1 'REGCA1' 1 1 .02 10 .9 .4 1.22 1.2 .9 .5 -1.3 .02 0 100 -100 .7 /\n"
                + "1 'REECA1' 1 0 1 0 1 0 1 .7 1.3 .01 -.02 .03 2 .9 -.8 1.01 "
                + ".1 .2 .3 .04 .7 -.6 .5 -.4 1.1 1.2 1.3 1.4 .05 .06 2 -2 1 0 1.2 .07 "
                + ".1 .2 .3 .4 .5 .6 .7 .8 .11 .21 .31 .41 .51 .61 .71 .81 /\n");

        PSSEDStabDirectParser parser = new PSSEDStabDirectParser(builder).setStrictImport(true);
        parser.parseDynFile(dyr.toString());
        Regca1Model converter = (Regca1Model) ((DStabGen) builder.getDStabNetwork().getDStabBus("Bus1")
                .getContributeGen("1")).getDynamicGenDevice();
        Reeca1Model controller = converter.getReeca1Controller();
        assertNotNull(controller);
        assertSame(controller, converter.getActiveElectricalController());
        assertEquals(expectedData(), controller.getData());
        assertEquals(Reeca1Model.WindSpeedConvention.PSSE_GENERATOR_SPEED,
                controller.getWindSpeedConvention());
        assertEquals(Set.of("Filtered Voltage", "Active Current Regulator",
                "Reactive Current Regulator", "Active Power", "Reactive Power"),
                converter.getNamedStates().keySet());
        assertEquals(Set.of("Measured Voltage", "Measured Active Power",
                "Active Power Filter", "Active Power Order", "Reactive Current",
                "Reactive Control Integral", "Voltage Control Integral",
                "Reactive Control Output", "Voltage Control Output",
                "Active Current Command", "Reactive Current Command"),
                controller.getNamedStates().keySet());
        assertEquals(controller.getMeasuredVoltage(),
                controller.getNamedState("Measured Voltage"), 0.0);
    }

    @Test
    void allTexasFlagProfilesInitializeWithoutCommandDiscontinuity() {
        int[][] profiles = {{1, 1, 1}, {1, 1, 0}, {0, 0, 0}};
        for (int[] profile : profiles) {
            Reeca1Model controller = new Reeca1Model(texasData(profile[0], profile[1], profile[2]), null);
            controller.initialize(.8, .2, 1.0);
            assertEquals(.8, controller.getIpcmd(), 1.0e-12);
            assertEquals(-.2, controller.getIqcmd(), 1.0e-12);
            for (int i = 0; i < 20; i++) controller.step(.005, .8, .2, 1.0, 1.0);
            assertEquals(.8, controller.getIpcmd(), 1.0e-9);
            assertEquals(-.2, controller.getIqcmd(), 1.0e-9);
        }
    }

    @Test
    void numericalPartitionResidualDoesNotExciteInitializedEquilibrium() {
        Reeca1Model controller = new Reeca1Model(texasData(1, 1, 1), null);
        controller.initialize(.8, .2, 1.0);

        controller.step(CONTROL_STEP, .8 + 4.0e-9, .2 - 3.0e-9,
                1.0 - 5.0e-9, 1.0 + 2.0e-9);

        assertEquals(.8, controller.getIpcmd(), 0.0);
        assertEquals(-.2, controller.getIqcmd(), 0.0);
        assertEquals(1.0, controller.getMeasuredVoltage(), 0.0);

        controller.step(CONTROL_STEP, .8, .2, 1.0 - 2.0e-8, 1.0);
        assertTrue(controller.getMeasuredVoltage() < 1.0,
                "finite disturbances above numerical resolution must remain observable");
    }

    @Test
    void voltageDipInjectionHonorsQPriorityCircularCurrentLimit() {
        Reeca1Model controller = new Reeca1Model(texasData(1, 1, 1), null);
        controller.initialize(1.0, 0.0, 1.0);
        for (int i = 0; i < 40; i++) controller.step(.005, 1.0, 0.0, .5, 1.0);
        assertTrue(Math.abs(controller.getIqcmd()) > .1);
        assertTrue(Math.hypot(controller.getIpcmd(), controller.getIqcmd()) <= 1.3 + 1.0e-9);
    }

    @Test
    void terminalVoltageFilterUsesRawVoltageForDipDetection() {
        Reeca1Model controller = new Reeca1Model(sensingData(0, 0, .1), null);
        controller.initialize(.8, .2, 1.0);

        controller.step(CONTROL_STEP, .8, .2, .5, 1.0);

        assertTrue(controller.isVoltageDip());
        assertEquals(modifiedEulerLag(1.0, .5, .1, CONTROL_STEP),
                controller.getMeasuredVoltage(), 1.0e-12);
    }

    @Test
    void dipFreezesReactiveCurrentWhilePowerMeasurementContinues() {
        Reeca1Model controller = new Reeca1Model(sensingData(0, 1, .1), null);
        controller.initialize(.8, .2, 1.0);
        double initialIqState = controller.getReactiveCurrentState();

        controller.step(.01, .4, .2, .5, 1.0);
        assertTrue(controller.isVoltageDip());
        assertEquals(.4, controller.getMeasuredActivePower(), 1.0e-12);
        assertEquals(initialIqState, controller.getReactiveCurrentState(), 1.0e-12);

        controller.step(.01, .4, .2, 1.0, 1.0);
        assertTrue(!controller.isVoltageDip());
        assertTrue(controller.getReactiveCurrentState() < initialIqState);
    }

    @Test
    void remoteBusVoltageDrivesFilterAndDipComparatorInsteadOfLocalVoltage() throws Exception {
        DStabNetworkBuilder builder = DStabBuilderTestFixture.createBuilder();
        AclfNetworkBuilder topology = new AclfNetworkBuilder(builder.getDStabNetwork());
        topology.addBus("Bus2", "Remote", 2L, 16500.0, .95, 0.0,
                null, null, null);
        Regca1Model converter = builder.addRegca1("Bus1", "1",
                new Regca1Data(0, .02, 10, .9, .4, 1.22, 1.2, .8,
                        .4, -1.3, .02, .7, 0, 0, .8));
        Reeca1Model controller = new Reeca1Model(sensingData(2, 0, .1), converter);
        converter.setReeca1Controller(controller);
        assertTrue(converter.initStates(converter.getDStabBus()));
        assertEquals(.95, controller.getMeasuredVoltage(), 1.0e-12);

        converter.getDStabBus().setVoltage(new Complex(.5, 0.0));
        builder.getDStabNetwork().getDStabBus("Bus2").setVoltage(new Complex(.9, 0.0));
        controller.step(CONTROL_STEP, .8, .2, .5, 1.0);

        assertTrue(!controller.isVoltageDip());
        assertEquals(modifiedEulerLag(.95, .9, .1, CONTROL_STEP),
                controller.getMeasuredVoltage(), 1.0e-12);
    }

    @Test
    void activeReferenceRampPflagTpordAndDipFreezeFollowTheWeccPath() {
        for (int pFlag : new int[] {0, 1}) {
            Reeca1Model controller = new Reeca1Model(activePathData(pFlag), null);
            controller.setPlantController(frequencyPlantController());
            controller.initialize(.8, .2, 1.0);

            controller.step(CONTROL_STEP, .8, .2, 1.0, .9);
            double expectedFilter = .8 + .05 * CONTROL_STEP;
            assertEquals(expectedFilter, controller.getActivePowerFilter(), 1.0e-12);
            // Network frequency is not the REEC_A turbine-generator-speed input.
            // Without WTDTA1, wg defaults to 1.0 for either PFLAG setting.
            double selected = expectedFilter;
            double expectedOrder = modifiedEulerLag(.8, selected, .2, CONTROL_STEP);
            assertEquals(expectedOrder, controller.getActivePowerOrder(), 1.0e-12,
                    "PFLAG=" + pFlag);

            controller.step(CONTROL_STEP, .8, .2, .5, .9);
            assertEquals(.8 + .1 * CONTROL_STEP, controller.getActivePowerFilter(), 1.0e-12);
            assertEquals(expectedOrder, controller.getActivePowerOrder(), 1.0e-12,
                    "Pord must freeze during a voltage dip");
        }
    }

    @Test
    void plantFrequencyControllerFeedsTorqueControllerPref0() {
        Reeca1Model controller = new Reeca1Model(activePathData(0), null);
        controller.setPlantController(frequencyPlantController());
        WindControlStack stack = new WindControlStack();
        stack.setTorqueController(new Wttqa1Model(new Wttqa1Data(
                1, 1, 0, 0, 0, 2, 0,
                .2, .58, .4, .72, .6, .86, 1.0, 1.0, 0)));
        controller.setWindControlStack(stack);
        controller.initialize(.8, .2, 1.0);

        controller.step(CONTROL_STEP, .8, .2, 1.0, .9);

        double plantIncrement = controller.getPlantController().getPref();
        assertTrue(plantIncrement > 0.0);
        assertEquals(.8 + plantIncrement, stack.getPref(), 1.0e-12);
        assertEquals(.8 + .05 * CONTROL_STEP,
                controller.getActivePowerFilter(), 1.0e-12);
    }

    @Test
    void nativeWindPflagRetainsTheGeneratorSpeedMultiplierWithAndWithoutDriveTrain() {
        // PSS/E 36.7 Model Library pp. 840-843 and native Bus-2089 discriminator:
        // P0=.625, wg0=.8775, Tpord=.03 gives dPord/dt=-2.5520833333.
        // The same native profile with PFLAG=0 has exactly zero initial rate.
        for (boolean withDrive : new boolean[] {false, true}) {
            for (int flag : new int[] {0, 1}) {
                Reeca1Model controller = new Reeca1Model(nativeWindData(flag), null);
                controller.setWindSpeedConvention(Reeca1Model.WindSpeedConvention.PSSE_GENERATOR_SPEED);
                WindControlStack stack = new WindControlStack();
                stack.setTorqueController(new Wttqa1Model(new Wttqa1Data(
                        1, 2.4, .5, .04, 60, 1.002, 0,
                        .2, .58, .4, .72, .6, .86, .8, 1, 0)));
                if (withDrive) stack.setDriveTrain(new org.interpss.dstab.renewable.Wtdta1Model(
                        new org.interpss.dstab.renewable.Wtdta1Data(5, .1, .8, 1.5, .2)));
                controller.setWindControlStack(stack);
                controller.initialize(.625, .11453417, 1.0);
                assertEquals(.8775, controller.getActivePowerSpeedMultiplier(), 1.e-12);
                assertEquals(.625, stack.getPref(), 0.0);
                double target = flag == 1 ? .625 * .8775 : .625;
                assertEquals(flag == 1 ? -2.552083333333333 : 0.0,
                        (target - .625) / .03, 1.e-12);
                controller.step(.0005, .625, .11453417, 1.0, .8, 0);
                double expected = modifiedEulerLag(.625, target, .03, .0005);
                assertEquals(expected, controller.getActivePowerOrder(), 1.e-12);
                controller.step(.0005, .625, .11453417, 1.0, .8, 1);
                assertEquals(expected, controller.getActivePowerOrder(), 1.e-12,
                        "The corrector must not integrate the controller a second time");
            }
        }
    }

    @Test
    void explicitPowerReferenceConventionRetainsTypedClientBehavior() {
        Reeca1Model controller = new Reeca1Model(nativeWindData(1), null);
        WindControlStack stack = new WindControlStack();
        stack.setTorqueController(new Wttqa1Model(new Wttqa1Data(
                1, 2.4, .5, .04, 60, 1.002, 0,
                .2, .58, .4, .72, .6, .86, .8, 1, 0)));
        controller.setWindControlStack(stack);
        controller.setWindSpeedConvention(Reeca1Model.WindSpeedConvention.WTTQA_POWER_REFERENCE);
        controller.initialize(.625, .1, 1.0);
        controller.step(.0005, .625, .1, 1.0, 1.0);
        assertEquals(1.0, controller.getActivePowerSpeedMultiplier(), 0.0);
        assertEquals(.625, controller.getActivePowerOrder(), 1.e-12);
    }

    @Test
    void nativeUnmodeledShaftSpeedDoesNotFollowTheTorqueSpeedReference() {
        for (boolean withDrive : new boolean[] {false, true}) {
            Reeca1Model controller = new Reeca1Model(nativeWindData(1), null);
            WindControlStack stack = new WindControlStack();
            stack.setTorqueController(new Wttqa1Model(new Wttqa1Data(
                    1, 2.4, .5, .04, 60, 1.002, 0,
                    .2, .58, .4, .72, .6, .86, .8, 1, 0)));
            if (withDrive) stack.setDriveTrain(new org.interpss.dstab.renewable.Wtdta1Model(
                    new org.interpss.dstab.renewable.Wtdta1Data(5, .1, .8, 1.5, .2)));
            controller.setWindControlStack(stack);
            controller.setWindSpeedConvention(Reeca1Model.WindSpeedConvention.PSSE_GENERATOR_SPEED);
            controller.initialize(.625, .1, 1.0);
            double initialSpeed = stack.getGeneratorSpeed();
            for (int index = 0; index < 100; index++) {
                stack.step(.005, .4, .625, .625, false, 0);
                stack.step(.005, .4, .625, .625, false, 1);
            }
            assertTrue(stack.getTorqueController().getSpeedReference() < initialSpeed);
            if (withDrive) assertTrue(stack.getGeneratorSpeed() > initialSpeed,
                    "An attached mechanical model must still supply changing shaft speed");
            else assertEquals(initialSpeed, stack.getGeneratorSpeed(), 0.0,
                    "PSS/E retains SPEED while the WTTQA1 reference state moves independently");
        }
    }

    @Test
    void coordinatedReactiveControlInitializesBothPisAtEquilibrium() {
        Reeca1Model controller = new Reeca1Model(
                texasData(1, 1, 1), null);
        controller.initialize(.36, -.005, 1.0);
        double initialIntegral = controller.getVoltageControlIntegral();

        assertEquals(1.0, controller.getReactiveControlIntegral(), 0.0,
                "PIQ stores the absolute voltage reference in WECC REEC_A");
        assertEquals(1.0, controller.getReactiveControlOutput(), 0.0);

        controller.step(1.0 / 240.0, .36, -.005, 1.0, .999);

        assertEquals(initialIntegral, controller.getVoltageControlIntegral(), 1.0e-12);
        assertEquals(.36, controller.getActivePowerOrder(), 1.0e-12);
    }

    @Test
    void directQAndActiveCurrentUseLowerCappedFilteredTerminalVoltage() {
        Reeca1Model controller = new Reeca1Model(controlData(0, 0, 0, .1, 0, .1), null);
        controller.initialize(.8, .2, 1.0);

        controller.step(CONTROL_STEP, .8, .2, .9, 1.0);

        double expectedVoltage = modifiedEulerLag(1.0, .9, .1, CONTROL_STEP);
        double expectedQCurrent = modifiedEulerLag(
                .2, .2 / expectedVoltage, .1, CONTROL_STEP);
        assertEquals(expectedVoltage, controller.getMeasuredVoltage(), 1.0e-12);
        assertEquals(expectedQCurrent, controller.getReactiveCurrentState(), 1.0e-12);
        assertEquals(.8 / expectedVoltage, controller.getIpcmd(), 1.0e-12);
        assertEquals(-expectedQCurrent, controller.getIqcmd(), 1.0e-12);
    }

    @Test
    void powerFactorModeTracksFilteredActivePower() {
        Reeca1Model controller = new Reeca1Model(controlData(1, 0, 0, 0, .1, .1), null);
        controller.initialize(.8, .2, 1.0);

        controller.step(CONTROL_STEP, .4, .2, 1.0, 1.0);

        double expectedP = modifiedEulerLag(.8, .4, .1, CONTROL_STEP);
        double expectedQCurrent = modifiedEulerLag(.2, expectedP * .25, .1, CONTROL_STEP);
        assertEquals(expectedP, controller.getMeasuredActivePower(), 1.0e-12);
        assertEquals(expectedQCurrent, controller.getReactiveCurrentState(), 1.0e-12);
        assertEquals(-expectedQCurrent, controller.getIqcmd(), 1.0e-12);
    }

    @Test
    void coordinatedQControlFormsAbsoluteVoltageReferenceBeforeInnerPi() {
        Reeca1Model controller = new Reeca1Model(controlData(0, 1, 1, 0, 0, .1), null);
        controller.initialize(.8, .2, 1.0);
        assertEquals(1.0, controller.getReactiveControlIntegral(), 0.0,
                "WECC Figure 3-2 PIQ initializes to the terminal-voltage reference");

        controller.step(CONTROL_STEP, .8, .3, 1.0, 1.0);

        double qError = -.1;
        double qIntegral = 1.0 + qError * CONTROL_STEP;
        double voltageReference = 2.0 * qError + qIntegral;
        double voltageError = voltageReference - 1.0;
        double vIntegral = .2 + 2.0 * voltageError * CONTROL_STEP;
        double expectedIqcmd = -(voltageError + vIntegral);
        assertEquals(.2, controller.getReactivePowerTarget(), 1.0e-12);
        assertEquals(qError, controller.getReactiveControlError(), 1.0e-12);
        assertEquals(voltageReference,
                controller.getReactiveControlPreLimitOutput(), 1.0e-12);
        assertEquals(voltageReference, controller.getReactiveControlOutput(), 1.0e-12);
        assertEquals(voltageError, controller.getVoltageControlError(), 1.0e-12);
        assertEquals(voltageError + vIntegral,
                controller.getVoltageControlPreLimitOutput(), 1.0e-12);
        assertEquals(voltageError + vIntegral,
                controller.getVoltageControlOutput(), 1.0e-12);
        assertTrue(controller.getPreliminaryReactiveCurrentLimit()
                >= Math.abs(voltageError + vIntegral));
        assertEquals(0.0, controller.getReactiveCurrentInjection(), 1.0e-12);
        assertEquals(expectedIqcmd, controller.getIqcmd(), 1.0e-12);
    }

    @Test
    void correctorStageRefreshesCommandsWithoutAdvancingStatesTwice() {
        Reeca1Model controller = new Reeca1Model(controlData(0, 1, 1, 0, 0, .1), null);
        controller.initialize(.8, .2, 1.0);

        controller.step(CONTROL_STEP, .8, .3, 1.0, 1.0, 0);
        double predictorIntegral = controller.getReactiveControlIntegral();
        double predictorOutput = controller.getReactiveControlOutput();
        controller.step(CONTROL_STEP, .8, .25, 1.0, 1.0, 1);

        assertEquals(predictorIntegral, controller.getReactiveControlIntegral(), 0.0,
                "flag 1 must not integrate REECA1 states a second time");
        assertTrue(controller.getReactiveControlOutput() > predictorOutput,
                "flag 1 must evaluate the corrected reactive-power endpoint");
    }

    private static double modifiedEulerLag(double state, double input,
            double timeConstant, double dt) {
        double initialDerivative = (input - state) / timeConstant;
        double predicted = state + dt * initialDerivative;
        return state + .5 * dt * (initialDerivative
                + (input - predicted) / timeConstant);
    }

    @Test
    void largeExternalStepMatchesRepeatedBoundedControlSteps() {
        Reeca1Model internallySubstepped = new Reeca1Model(
                controlData(0, 1, 1, .1, .1, .1), null);
        Reeca1Model explicitlyStepped = new Reeca1Model(
                controlData(0, 1, 1, .1, .1, .1), null);
        internallySubstepped.initialize(.8, .2, 1.0);
        explicitlyStepped.initialize(.8, .2, 1.0);

        internallySubstepped.step(.1, .7, .25, .97, 1.0);
        for (int i = 0; i < 96; i++) {
            explicitlyStepped.step(CONTROL_STEP, .7, .25, .97, 1.0);
        }

        assertEquals(explicitlyStepped.getMeasuredVoltage(),
                internallySubstepped.getMeasuredVoltage(), 1.0e-12);
        assertEquals(explicitlyStepped.getMeasuredActivePower(),
                internallySubstepped.getMeasuredActivePower(), 1.0e-12);
        assertEquals(explicitlyStepped.getActivePowerOrder(),
                internallySubstepped.getActivePowerOrder(), 1.0e-12);
        assertEquals(explicitlyStepped.getVoltageControlIntegral(),
                internallySubstepped.getVoltageControlIntegral(), 1.0e-12);
        assertEquals(explicitlyStepped.getIpcmd(), internallySubstepped.getIpcmd(), 1.0e-12);
        assertEquals(explicitlyStepped.getIqcmd(), internallySubstepped.getIqcmd(), 1.0e-12);
    }

    @Test
    void voltageControlUsesVref1PlusQReferenceAgainstFilteredVoltage() {
        Reeca1Model controller = new Reeca1Model(controlData(0, 0, 1, 0, 0, .1), null);
        controller.initialize(.8, .2, 1.0);

        controller.step(.1, .8, .2, .9, 1.0);

        // Vref1=.8 and Qref=.2 establish the 1.0 pu initial reference. At .9 pu,
        // the voltage PI output is 1*.1 + [.2 + integral(2*.1)] = .32.
        assertEquals(-.32, controller.getIqcmd(), 1.0e-12);
    }

    @Test
    void circularCurrentLimitHonorsQAndPSelectedPriority() {
        Reeca1Model qPriority = new Reeca1Model(currentLimitData(0, 0, 1, 0, false), null);
        qPriority.initialize(1.0, .8, 1.0);
        qPriority.step(.01, 1.0, .8, 1.0, 1.0);
        assertEquals(.8, Math.abs(qPriority.getIqcmd()), 1.0e-12);
        assertEquals(.6, qPriority.getIpcmd(), 1.0e-12);
        assertEquals(.6, qPriority.getActiveCurrentLimit(), 1.0e-12);

        Reeca1Model pPriority = new Reeca1Model(currentLimitData(1, 0, 1, 0, false), null);
        pPriority.initialize(1.0, .8, 1.0);
        pPriority.step(.01, 1.0, .8, 1.0, 1.0);
        assertEquals(1.0, pPriority.getIpcmd(), 1.0e-12);
        assertEquals(0.0, pPriority.getIqcmd(), 1.0e-12);
        assertEquals(0.0, pPriority.getReactiveCurrentLimit(), 1.0e-12);
    }

    @Test
    void zeroImaxUsesIndependentRectangularVdlLimits() {
        Reeca1Model controller = new Reeca1Model(currentLimitData(0, 0, 0, 0, true), null);
        controller.initialize(1.0, 1.0, 1.0);
        controller.step(.01, 1.0, 1.0, 1.0, 1.0);

        assertEquals(.7, controller.getIpcmd(), 1.0e-12);
        assertEquals(-.6, controller.getIqcmd(), 1.0e-12);
        assertTrue(Math.hypot(controller.getIpcmd(), controller.getIqcmd()) > .9);
    }

    @Test
    void thld2HoldsTheFaultActiveCurrentLimitAfterVoltageRecovery() {
        Reeca1Model controller = new Reeca1Model(currentLimitData(1, 0, 0, .1, true), null);
        controller.initialize(1.0, 0.0, 1.0);

        controller.step(.02, 1.0, 0.0, .5, 1.0);
        assertEquals(.4, controller.getIpcmd(), 1.0e-12);
        controller.step(.02, 1.0, 0.0, 1.0, 1.0);
        assertEquals(.4, controller.getIpcmd(), 1.0e-12);
        for (int i = 0; i < 5; i++) controller.step(.02, 1.0, 0.0, 1.0, 1.0);
        assertEquals(.7, controller.getIpcmd(), 1.0e-12);
    }

    @Test
    void networkEventPreservesAnExcursionBetweenIntegrationSamples() throws Exception {
        for (double leftVoltage : new double[]{.5, 1.5}) {
            var builder = DStabBuilderTestFixture.createBuilder();
            var converter = builder.addRegca1("Bus1", "1", new Regca1Data(
                    0, .02, 10, .9, .4, 1.22, 10, .8, .4, -1.3, .02, 0, 100, -100, .8));
            converter.getParentGen().setGen(new Complex(.8, 0));
            converter.getDStabBus().setVoltage(Complex.ONE);
            var controller = new Reeca1Model(currentLimitData(1, 0, 2, .5, true), converter);
            converter.setReeca1Controller(controller);
            converter.setIntegrationScheme(Regca1Model.IntegrationScheme.TWO_STEP);
            assertTrue(converter.initStates(converter.getDStabBus()));
            var method = com.interpss.dstab.algo.DynamicSimuMethod.MODIFIED_EULER;
            assertTrue(converter.nextStep(.001, method, 0));
            converter.getDStabBus().setVoltage(new Complex(leftVoltage, 0));
            assertTrue(converter.nextStep(0, method, 1));
            assertEquals(0., controller.getControlMemory().get("Previous Dip"));
            var converterStates = converter.getIntegrationStates();
            var controlStates = controller.getIntegrationStates();
            var converterRates = converter.getStateDerivativeSnapshot();
            var controlRates = controller.getStateDerivativeSnapshot();
            double held = controller.getControlMemory().get("Held Active Current Limit");
            for (int i = 0; i < 3; i++) converter.captureNetworkEventLeftBoundary();
            assertEquals(converterStates, converter.getIntegrationStates());
            assertEquals(controlStates, controller.getIntegrationStates());
            assertEquals(converterRates, converter.getStateDerivativeSnapshot());
            assertEquals(controlRates, controller.getStateDerivativeSnapshot());
            assertEquals(1., controller.getControlMemory().get("Previous Dip"));
            assertEquals(0., controller.getControlMemory().get("Post Dip Timer"));
            assertEquals(held, controller.getControlMemory().get("Held Active Current Limit"));

            // Both event sides have the same physical time. Recovery starts
            // when the next positive integration step sees the right side.
            converter.getDStabBus().setVoltage(Complex.ONE);
            assertTrue(converter.nextStep(0, method, 1));
            assertEquals(0., controller.getControlMemory().get("Post Dip Timer"));
            assertTrue(converter.nextStep(.001, method, 0));
            assertEquals(.5, controller.getControlMemory().get("Post Dip Timer"), 0.);
            assertEquals(held, controller.getActiveCurrentLimit(), 1.e-12);
            assertTrue(converter.nextStep(.001, method, 0));
            assertEquals(.499, controller.getControlMemory().get("Post Dip Timer"), 1.e-12);
            assertTrue(converter.initStates(converter.getDStabBus()));
            assertEquals(0., controller.getControlMemory().get("Previous Dip"));
            assertEquals(0., controller.getControlMemory().get("Post Dip Timer"));
        }
    }

    @Test
    void normalEventBoundaryDoesNotEraseAnUnconsumedRecoveryTransition() throws Exception {
        var builder = DStabBuilderTestFixture.createBuilder();
        var converter = builder.addRegca1("Bus1", "1", new Regca1Data(
                0, .02, 10, .9, .4, 1.22, 10, .8, .4, -1.3, .02, 0, 100, -100, .8));
        converter.getParentGen().setGen(new Complex(.8, 0));
        converter.getDStabBus().setVoltage(Complex.ONE);
        var controller = new Reeca1Model(currentLimitData(1, 0, 2, .5, true), converter);
        converter.setReeca1Controller(controller);
        converter.setIntegrationScheme(Regca1Model.IntegrationScheme.TWO_STEP);
        assertTrue(converter.initStates(converter.getDStabBus()));
        converter.captureNetworkEventLeftBoundary();
        assertEquals(0., controller.getControlMemory().get("Previous Dip"));
        converter.getDStabBus().setVoltage(new Complex(.5, 0));
        assertTrue(converter.nextStep(.001, com.interpss.dstab.algo.DynamicSimuMethod.MODIFIED_EULER, 0));
        converter.getDStabBus().setVoltage(Complex.ONE);
        converter.captureNetworkEventLeftBoundary();
        assertEquals(1., controller.getControlMemory().get("Previous Dip"));
        assertTrue(converter.nextStep(.001, com.interpss.dstab.algo.DynamicSimuMethod.MODIFIED_EULER, 0));
        assertEquals(.5, controller.getControlMemory().get("Post Dip Timer"), 0.);
    }

    @Test
    void repeatedVoltageExcursionDoesNotRestartActiveCurrentRecovery() {
        Reeca1Model controller = new Reeca1Model(currentLimitData(1, 0, 2, .5, true), null);
        controller.initialize(1.0, 0.0, 1.0);
        controller.step(.125, 1.0, 0.0, .55, 1.0);
        controller.step(.125, 1.0, 0.0, 1.0, 1.0);
        double heldLimit = .4 + (.55 - .5) * (.6 - .4) / (.8 - .5);
        assertEquals(heldLimit, controller.getIpcmd(), 1.0e-12);

        controller.step(.125, 1.0, 0.0, .5, 1.0);
        assertEquals(heldLimit, controller.getIpcmd(), 1.0e-12,
                "a second excursion cannot overwrite the held fault limit");
        controller.step(.125, 1.0, 0.0, 1.0, 1.0);
        controller.step(.125, 1.0, 0.0, 1.0, 1.0);
        assertEquals(heldLimit, controller.getIpcmd(), 1.0e-12);
        controller.step(.125, 1.0, 0.0, 1.0, 1.0);
        assertEquals(.7, controller.getIpcmd(), 1.0e-12,
                "the first recovery deadline includes the second excursion");

        controller.step(.125, 1.0, 0.0, .5, 1.0);
        controller.step(.125, 1.0, 0.0, 1.0, 1.0);
        assertEquals(.4, controller.getIpcmd(), 1.0e-12,
                "a later independent excursion starts a fresh recovery interval");
        controller.initialize(1.0, 0.0, 1.0);
        controller.step(.125, 1.0, 0.0, 1.0, 1.0);
        assertEquals(.7, controller.getIpcmd(), 1.0e-12,
                "initialization clears the previous recovery interval");
    }

    @Test
    void activeCurrentRecoveryUsesSelectedStoragePrecisionAtBoundary() throws Exception {
        for (boolean singlePrecision : new boolean[]{false, true}) {
            DStabNetworkBuilder builder = DStabBuilderTestFixture.createBuilder();
            Regca1Model converter = builder.addRegca1("Bus1", "1", new Regca1Data(
                    1, .02, 10, .9, .4, 1.22, 1.2, .9, .5, -1.3, .02, 0, 100, -100, .7));
            Reeca1Model controller = new Reeca1Model(currentLimitData(1, 0, 2, .5, true), null);
            converter.setReeca1Controller(controller);
            converter.setSinglePrecisionStates(singlePrecision);
            controller.initialize(1.0, 0.0, 1.0);
            controller.step(.001, 1.0, 0.0, .5, 1.0);
            controller.step(.001, 1.0, 0.0, 1.0, 1.0);
            for (int i = 0; i < 499; i++) controller.step(.001, 1.0, 0.0, 1.0, 1.0);
            assertEquals(.4, controller.getIpcmd(), 1.0e-12);
            controller.step(.001, 1.0, 0.0, 1.0, 1.0);
            assertEquals(singlePrecision ? .4 : .7, controller.getIpcmd(), 1.0e-12,
                    "the recorded binary32 recovery interval expires one step later");
            controller.step(.001, 1.0, 0.0, 1.0, 1.0);
            assertEquals(.7, controller.getIpcmd(), 1.0e-12);
        }
    }

    @Test
    void twoStepCurrentHeadroomUsesThePreviouslyPublishedReactiveCommand() throws Exception {
        DStabNetworkBuilder builder = DStabBuilderTestFixture.createBuilder();
        Regca1Model converter = builder.addRegca1("Bus1", "1", new Regca1Data(
                0, .02, 10, .9, .4, 1.22, 10, .8, .4, -1.3, .02, 0, 100, -100, .8));
        converter.getParentGen().setGen(new Complex(.8, 0.0));
        converter.getDStabBus().setVoltage(Complex.ONE);
        Reeca1Model controller = new Reeca1Model(new Reeca1Data(
                0, 0, 0, 0, 0, 0, .85, 1.15, 0, 0, 0, 10, 1, -1,
                1, 0, 0, 0, .02, 2, -2, 2, 0, 0, 0, 0, 0, 0, .02,
                99, -99, 2, 0, 1, .02,
                0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0), converter);
        converter.setReeca1Controller(controller);
        converter.setIntegrationScheme(Regca1Model.IntegrationScheme.TWO_STEP);
        assertTrue(converter.initStates(converter.getDStabBus()));
        assertEquals(.8, converter.getIpRegulatorState(), 1.0e-12);

        converter.getDStabBus().setVoltage(new Complex(1.2, 0.0));
        for (int i = 0; i < 3; i++) {
            assertTrue(converter.nextStep(0.0,
                    com.interpss.dstab.algo.DynamicSimuMethod.MODIFIED_EULER, 1));
        }
        assertEquals(1.0, controller.getIqcmd(), 1.0e-12);
        assertEquals(0.0, controller.getActiveCurrentLimit(), 1.0e-12);
        assertTrue(converter.nextStep(.001,
                com.interpss.dstab.algo.DynamicSimuMethod.MODIFIED_EULER, 0));
        assertEquals(.8, converter.getIpRegulatorState(), 1.0e-12);
        assertTrue(converter.nextStep(.001,
                com.interpss.dstab.algo.DynamicSimuMethod.MODIFIED_EULER, 0));
        double previousCommand = .8 / 1.2;
        double expectedCurrent = .8 + 1.5 * .001 * (previousCommand - .8) / .02;
        assertEquals(expectedCurrent, converter.getIpRegulatorState(), 1.0e-12,
                "refreshes cannot make the new reactive command restrict an earlier active-current stage");
    }

    @Test
    void pPriorityReactiveHeadroomPreventsVoltagePiWindup() {
        Reeca1Model controller = new Reeca1Model(currentLimitData(1, 1, 1, 0, false), null);
        controller.initialize(.8, .2, 1.0);
        double initialIntegral = controller.getVoltageControlIntegral();

        controller.step(.1, .8, .2, .8, 1.0);

        assertEquals(1.0, controller.getIpcmd(), 1.0e-12);
        assertEquals(0.0, controller.getReactiveCurrentLimit(), 1.0e-12);
        assertEquals(0.0, controller.getIqcmd(), 1.0e-12);
        assertEquals(initialIntegral, controller.getVoltageControlIntegral(), 1.0e-12);
    }

    @Test
    void cascadedQAndVoltageIntegratorsUseLinkedNonWindupAndDirectionalRelease() {
        Reeca1Model controller = new Reeca1Model(linkedIntegratorData(), null);
        controller.initialize(.8, .2, 1.0);

        controller.step(CONTROL_STEP, .8, .1, 1.0, 1.0);

        assertEquals(1.0, controller.getReactiveControlIntegral(), 0.0,
                "upstream Q integrator must freeze when both derivatives push "
                        + "the downstream voltage PI farther above Iqmax");
        assertEquals(.2, controller.getVoltageControlIntegral(), 0.0,
                "downstream voltage integrator must apply its own upper-limit freeze");

        controller.step(CONTROL_STEP, .8, .3, 1.0, 1.0);

        assertTrue(controller.getReactiveControlIntegral() < 1.0,
                "the linked Q integrator must release for motion away from the upper limit");
        assertTrue(controller.getVoltageControlIntegral() < .2,
                "the downstream voltage integrator must release toward its admissible range");

        Reeca1Model lower = new Reeca1Model(linkedIntegratorData(), null);
        lower.initialize(.8, -.2, 1.0);
        lower.step(CONTROL_STEP, .8, -.1, 1.0, 1.0);

        assertEquals(1.0, lower.getReactiveControlIntegral(), 0.0,
                "upstream Q integrator must freeze when both derivatives push "
                        + "the downstream voltage PI farther below Iqmin");
        assertEquals(-.2, lower.getVoltageControlIntegral(), 0.0,
                "downstream voltage integrator must apply its own lower-limit freeze");

        lower.step(CONTROL_STEP, .8, -.3, 1.0, 1.0);

        assertTrue(lower.getReactiveControlIntegral() > 1.0,
                "the linked Q integrator must release for motion away from the lower limit");
        assertTrue(lower.getVoltageControlIntegral() > -.2,
                "the downstream voltage integrator must release toward its admissible range");
    }

    @Test
    void activeAndReactivePowerLimitsClampBothDirectionsAndRecover() {
        Reeca1Model active = new Reeca1Model(outerLimitData(0, 0, 0,
                2.0, -2.0, 2.0, -2.0, .9, .7, 10.0, 0.0), null);
        Repca1Model plant = frequencyPlantController();
        active.setPlantController(plant);
        active.initialize(.8, .2, 1.0);
        active.step(CONTROL_STEP, .8, .2, 1.0, .5);
        assertTrue(plant.getPref() > .4);
        assertEquals(.9, active.getActivePowerOrder(), 1.0e-12);
        active.step(CONTROL_STEP, .8, .2, 1.0, 1.5);
        assertTrue(plant.getPref() < -.4);
        assertEquals(.8 + plant.getPref(), active.getActivePowerFilter(), 1.0e-12);
        assertTrue(!active.isVoltageDip());
        assertEquals(.7, active.getActivePowerOrder(), 1.0e-12);
        active.step(CONTROL_STEP, .8, .2, 1.0, 1.0);
        assertEquals(.8, active.getActivePowerOrder(), 1.0e-12);

        Reeca1Model reactive = new Reeca1Model(outerLimitData(1, 0, 0,
                .3, -.1, 2.0, -2.0, 2.0, -2.0, 10.0, 0.0), null);
        reactive.initialize(.8, .2, 1.0);
        reactive.step(CONTROL_STEP, 2.0, .2, 1.0, 1.0);
        assertEquals(-.3, reactive.getIqcmd(), 1.0e-12);
        reactive.step(CONTROL_STEP, -2.0, .2, 1.0, 1.0);
        assertEquals(.1, reactive.getIqcmd(), 1.0e-12);
        reactive.step(CONTROL_STEP, .8, .2, 1.0, 1.0);
        assertEquals(-.2, reactive.getIqcmd(), 1.0e-12);
    }

    @Test
    void directVoltageReferencePathHonorsVLimitsAndRecovers() {
        Reeca1Model controller = new Reeca1Model(outerLimitData(1, 0, 1,
                2.0, -2.0, 1.1, .9, 2.0, -2.0, 10.0, 0.0), null);
        controller.initialize(.8, .2, 1.0);

        controller.step(CONTROL_STEP, 2.0, .2, 1.0, 1.0);
        assertEquals(-.3, controller.getIqcmd(), 1.0e-12,
                "Vref1+Qcpf must be capped by Vmax before the inner voltage PI");
        controller.step(CONTROL_STEP, -2.0, .2, 1.0, 1.0);
        assertEquals(-.1, controller.getIqcmd(), 1.0e-12,
                "Vref1+Qcpf must be floored by Vmin before the inner voltage PI");
        controller.step(CONTROL_STEP, .8, .2, 1.0, 1.0);
        assertEquals(-.2, controller.getIqcmd(), 1.0e-12);
    }

    @Test
    void reactiveInjectionLimitsAndPositiveHoldRecoverAfterDip() {
        Reeca1Model controller = new Reeca1Model(injectionRecoveryData(), null);
        controller.initialize(.8, 0.0, 1.0);

        controller.step(.01, .8, 0.0, .5, 1.0);
        assertEquals(-.3, controller.getIqcmd(), 1.0e-12,
                "undervoltage injection must stop at Iqh1");
        controller.step(.01, .8, 0.0, 1.5, 1.0);
        assertEquals(.2, controller.getIqcmd(), 1.0e-12,
                "overvoltage injection must stop at Iql1");
        controller.step(.01, .8, 0.0, 1.0, 1.0);
        assertEquals(-.1, controller.getIqcmd(), 1.0e-12,
                "positive Thld must hold Iqfrz after voltage recovery");
        for (int i = 0; i < 5; i++) controller.step(.01, .8, 0.0, 1.0, 1.0);
        assertEquals(0.0, controller.getIqcmd(), 1.0e-12);
    }

    private static Reeca1Data expectedData() {
        return new Reeca1Data(0, 1, 0, 1, 0, 1,
                .7, 1.3, .01, -.02, .03, 2, .9, -.8, 1.01, .1, .2, .3,
                .04, .7, -.6, .5, -.4, 1.1, 1.2, 1.3, 1.4, .05, .06,
                2, -2, 1, 0, 1.2, .07,
                .1, .2, .3, .4, .5, .6, .7, .8,
                .11, .21, .31, .41, .51, .61, .71, .81);
    }

    private static Reeca1Data nativeWindData(int pFlag) {
        return new Reeca1Data(0, 0, 1, 1, pFlag, 0,
                .85, 1.15, .02, 0, 0, 5, 1.1, -1.1, 0, 0, 0, .5,
                .02, .436, -.436, 1.1, .9, 0, .1, 0, 39.5, 0, .02,
                99, -99, 1, 0, 1.3, .03,
                0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0);
    }

    private static Reeca1Data texasData(int vFlag, int qFlag, int pFlag) {
        return new Reeca1Data(0, 0, vFlag, qFlag, pFlag, 0,
                .85, 1.15, .02, 0, 0, 5, 1.1, -1.1, 0, 0, 0, .5,
                .02, .436, -.436, 1.1, .9, 1.2, 1.9, 1, 1.1, 0, .02,
                99, -99, 1, 0, 1.3, .02,
                0, 0, 0, 0, 0, 0, 0, 0,
                0, 0, 0, 0, 0, 0, 0, 0);
    }

    private static Reeca1Data sensingData(int remoteBus, int pfFlag, double trv) {
        return new Reeca1Data(remoteBus, pfFlag, 0, 0, 0, 0,
                .8, 1.2, trv, -.02, .02, 0, 1, -1, 0, 0, 0, 0,
                0, 1, -1, 1, -1, 0, 0, 0, 0, 1, .1,
                99, -99, 2, -2, 10, .1,
                0, 0, 0, 0, 0, 0, 0, 0,
                0, 0, 0, 0, 0, 0, 0, 0);
    }

    private static Reeca1Data activePathData(int pFlag) {
        return new Reeca1Data(0, 0, 0, 0, pFlag, 0,
                .8, 1.2, 0, -.02, .02, 0, 1, -1, 0, 0, 0, 0,
                0, 1, -1, 1, -1, 0, 0, 0, 0, 1, 0,
                .05, -.05, 2, -2, 10, .2,
                0, 0, 0, 0, 0, 0, 0, 0,
                0, 0, 0, 0, 0, 0, 0, 0);
    }

    private static Reeca1Data controlData(int pfFlag, int vFlag, int qFlag,
            double trv, double tp, double tiq) {
        return new Reeca1Data(0, pfFlag, vFlag, qFlag, 0, 0,
                .8, 1.2, trv, 0, 0, 0, 1, -1, 1, 0, 0, 0,
                tp, 2, -2, .5, -.5, 2, 1, 1, 2, .8, tiq,
                99, -99, 2, -2, 10, 0,
                0, 0, 0, 0, 0, 0, 0, 0,
                0, 0, 0, 0, 0, 0, 0, 0);
    }

    private static Reeca1Data currentLimitData(int pqFlag, int qFlag,
            double imax, double thld2, boolean vdlEnabled) {
        double scale = vdlEnabled ? 1.0 : 0.0;
        return new Reeca1Data(0, 0, 0, qFlag, 0, pqFlag,
                .6, 1.4, 0, 0, 0, 0, 1, -1, 1, 0, 0, thld2,
                0, 2, -2, 2, -2, 0, 0, 1, 2, .8, 0,
                99, -99, 2, -2, imax, 0,
                .5 * scale, .3 * scale, .8 * scale, .5 * scale,
                1.0 * scale, .6 * scale, 1.2 * scale, .7 * scale,
                .5 * scale, .4 * scale, .8 * scale, .6 * scale,
                1.0 * scale, .7 * scale, 1.2 * scale, .8 * scale);
    }

    private static Reeca1Data outerLimitData(int pfFlag, int vFlag, int qFlag,
            double qmax, double qmin, double vmax, double vmin,
            double pmax, double pmin, double imax, double tiq) {
        return new Reeca1Data(0, pfFlag, vFlag, qFlag, 0, 0,
                .8, 1.2, 0, 0, 0, 0, 1, -1, 1, 0, 0, 0,
                0, qmax, qmin, vmax, vmin, 1, 0, 1, 0, .8, tiq,
                1.0e9, -1.0e9, pmax, pmin, imax, 0,
                0, 0, 0, 0, 0, 0, 0, 0,
                0, 0, 0, 0, 0, 0, 0, 0);
    }

    private static Reeca1Data injectionRecoveryData() {
        return new Reeca1Data(0, 0, 0, 0, 0, 0,
                .8, 1.2, 0, 0, 0, 10, .3, -.2, 1, .1, .05, 0,
                0, 2, -2, 2, -2, 0, 0, 0, 0, 1, 0,
                99, -99, 2, -2, 10, 0,
                0, 0, 0, 0, 0, 0, 0, 0,
                0, 0, 0, 0, 0, 0, 0, 0);
    }

    private static Reeca1Data linkedIntegratorData() {
        return new Reeca1Data(0, 0, 1, 1, 0, 0,
                .8, 1.2, 0, 0, 0, 0, 1, -1, 1, 0, 0, 0,
                0, 2, -2, 2, 0, 1, 1, 1, 1, 0, 0,
                99, -99, 2, -2, .2, 0,
                0, 0, 0, 0, 0, 0, 0, 0,
                0, 0, 0, 0, 0, 0, 0, 0);
    }

    private static Repca1Model frequencyPlantController() {
        return new Repca1Model(new Repca1Data(
                0, 0, 0, "1", 0, 0, 1,
                0, 0, 0, 0, 0, 0, 0, 0, 0,
                1, -1, -.001, .001, 1, -1,
                1, 0, 0, -.001, .001, 1, -1,
                1, -1, 0, 1, 1, 1));
    }
}
