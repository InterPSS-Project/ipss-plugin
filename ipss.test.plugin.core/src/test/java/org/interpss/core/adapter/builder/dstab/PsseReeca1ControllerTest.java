package org.interpss.core.adapter.builder.dstab;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;

import com.interpss.dstab.DStabGen;

import org.apache.commons.math3.complex.Complex;
import org.interpss.CorePluginTestSetup;
import org.interpss.dstab.renewable.Regca1Data;
import org.interpss.dstab.renewable.Reeca1Data;
import org.interpss.dstab.renewable.Reeca1Model;
import org.interpss.dstab.renewable.Regca1Model;
import org.interpss.dstab.renewable.Repca1Data;
import org.interpss.dstab.renewable.Repca1Model;
import org.interpss.fadapter.builder.DStabNetworkBuilder;
import org.interpss.fadapter.builder.AclfNetworkBuilder;
import org.interpss.fadapter.psse.PSSEDStabDirectParser;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

public class PsseReeca1ControllerTest extends CorePluginTestSetup {
    private static final double CONTROL_STEP = 1.0 / 960.0;

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
        assertEquals(1.0 + CONTROL_STEP * (.5 - 1.0) / .1,
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
        assertEquals(.95 + CONTROL_STEP * (.9 - .95) / .1,
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
            double expectedOrder = .8 + CONTROL_STEP * (selected - .8) / .2;
            assertEquals(expectedOrder, controller.getActivePowerOrder(), 1.0e-12,
                    "PFLAG=" + pFlag);

            controller.step(CONTROL_STEP, .8, .2, .5, .9);
            assertEquals(.8 + .1 * CONTROL_STEP, controller.getActivePowerFilter(), 1.0e-12);
            assertEquals(expectedOrder, controller.getActivePowerOrder(), 1.0e-12,
                    "Pord must freeze during a voltage dip");
        }
    }

    @Test
    void coordinatedReactiveControlInitializesBothPisAtEquilibrium() {
        Reeca1Model controller = new Reeca1Model(
                texasData(1, 1, 1), null);
        controller.initialize(.36, -.005, 1.0);
        double initialIntegral = controller.getVoltageControlIntegral();

        controller.step(1.0 / 240.0, .36, -.005, 1.0, .999);

        assertEquals(initialIntegral, controller.getVoltageControlIntegral(), 1.0e-12);
        assertEquals(.36, controller.getActivePowerOrder(), 1.0e-12);
    }

    @Test
    void directQAndActiveCurrentUseLowerCappedFilteredTerminalVoltage() {
        Reeca1Model controller = new Reeca1Model(controlData(0, 0, 0, .1, 0, .1), null);
        controller.initialize(.8, .2, 1.0);

        controller.step(CONTROL_STEP, .8, .2, .9, 1.0);

        double expectedVoltage = 1.0 + CONTROL_STEP * (.9 - 1.0) / .1;
        double expectedQCurrent = .2
                + CONTROL_STEP * (.2 / expectedVoltage - .2) / .1;
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

        double expectedP = .8 + CONTROL_STEP * (.4 - .8) / .1;
        double expectedQCurrent = .2 + CONTROL_STEP * (expectedP * .25 - .2) / .1;
        assertEquals(expectedP, controller.getMeasuredActivePower(), 1.0e-12);
        assertEquals(expectedQCurrent, controller.getReactiveCurrentState(), 1.0e-12);
        assertEquals(-expectedQCurrent, controller.getIqcmd(), 1.0e-12);
    }

    @Test
    void coordinatedQControlRoutesReactivePiDirectlyIntoInnerPi() {
        Reeca1Model controller = new Reeca1Model(controlData(0, 1, 1, 0, 0, .1), null);
        controller.initialize(.8, .2, 1.0);

        controller.step(CONTROL_STEP, .8, .3, 1.0, 1.0);

        double qError = -.1;
        double qIntegral = qError * CONTROL_STEP;
        double voltageError = 2.0 * qError + qIntegral;
        double vIntegral = .2 + 2.0 * voltageError * CONTROL_STEP;
        double expectedIqcmd = -(voltageError + vIntegral);
        assertEquals(expectedIqcmd, controller.getIqcmd(), 1.0e-12);
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

    private static Repca1Model frequencyPlantController() {
        return new Repca1Model(new Repca1Data(
                0, 0, 0, "1", 0, 0, 1,
                0, 0, 0, 0, 0, 0, 0, 0, 0,
                1, -1, -.001, .001, 1, -1,
                1, 0, 0, -.001, .001, 1, -1,
                1, -1, 0, 1, 1, 1));
    }
}
