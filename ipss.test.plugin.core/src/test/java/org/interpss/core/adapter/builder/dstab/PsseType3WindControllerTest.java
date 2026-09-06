package org.interpss.core.adapter.builder.dstab;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;

import org.interpss.CorePluginTestSetup;
import org.interpss.dstab.renewable.Reeca1Model;
import org.interpss.dstab.renewable.Regca1Model;
import org.interpss.dstab.renewable.WindControlStack;
import org.interpss.dstab.renewable.Wtara1Data;
import org.interpss.dstab.renewable.Wtara1Model;
import org.interpss.dstab.renewable.Wtpta1Data;
import org.interpss.dstab.renewable.Wtpta1Model;
import org.interpss.dstab.renewable.Wttqa1Data;
import org.interpss.dstab.renewable.Wttqa1Model;
import org.interpss.fadapter.builder.DStabNetworkBuilder;
import org.interpss.fadapter.psse.PSSEDStabDirectParser;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.interpss.dstab.DStabGen;

public class PsseType3WindControllerTest extends CorePluginTestSetup {

    @Test
    void parserMapsAndWiresCompleteWindStackInSourceOrder(@TempDir Path tempDir) throws Exception {
        DStabNetworkBuilder builder = DStabBuilderTestFixture.createBuilder();
        Path dyr = tempDir.resolve("type3.dyr");
        Files.writeString(dyr,
                "1 'REGCA1' 1 1 .02 10 .9 .4 1.22 1.2 .9 .5 -1.3 .02 0 100 -100 .7 /\n"
                + "1 'REECA1' 1 0 0 1 1 0 0 .85 1.15 .02 0 0 5 1.1 -1.1 0 0 0 .5 .02 .436 -.436 1.1 .9 1.3 2.4 .6 1.5 0 .02 99 -99 1 0 1.3 .02 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 /\n"
                + "1 'WTPTA1' 1 28 137 22.03 1.17 .27 .3 27 0 10 -10 /\n"
                + "1 'WTTQA1' 1 1 1.9 .5 .04 60 1.002 0 .2 .58 .4 .72 .6 .86 .8 1 0 /\n"
                + "1 'WTARA1' 1 .007 0 /\n");

        PSSEDStabDirectParser parser = new PSSEDStabDirectParser(builder).setStrictImport(true);
        parser.parseDynFile(dyr.toString());
        assertEquals(5, parser.getLastImportReport().totalRecordCount());

        Regca1Model converter = (Regca1Model) ((DStabGen) builder.getDStabNetwork()
                .getDStabBus("Bus1").getContributeGen("1")).getDynamicGenDevice();
        Reeca1Model electrical = converter.getReeca1Controller();
        WindControlStack stack = electrical.getWindControlStack();
        assertNotNull(stack);
        assertTrue(stack.isComplete());
        assertEquals(new Wtara1Data(.007, 0), stack.getAerodynamics().getData());
        assertEquals(new Wtpta1Data(28, 137, 22.03, 1.17, .27, .3, 27, 0, 10, -10),
                stack.getPitchController().getData());
        assertEquals(new Wttqa1Data(1, 1.9, .5, .04, 60, 1.002, 0,
                .2, .58, .4, .72, .6, .86, .8, 1, 0),
                stack.getTorqueController().getData());
    }

    @Test
    void noDriveTrainPathInitializesWithoutPowerReferenceJump() {
        WindControlStack stack = new WindControlStack();
        stack.setAerodynamics(new org.interpss.dstab.renewable.Wtara1Model(
                new Wtara1Data(.007, 0)));
        stack.setPitchController(new org.interpss.dstab.renewable.Wtpta1Model(
                new Wtpta1Data(25, 150, 30, 3, 0, .3, 27, 0, 10, -10)));
        stack.setTorqueController(new org.interpss.dstab.renewable.Wttqa1Model(
                new Wttqa1Data(0, 1, .5, 0, 60, 1.002, 0,
                        .2, .58, .4, .72, .6, .86, .8, 1, 0)));
        stack.initialize(.5);
        assertEquals(.5, stack.getPref(), 1.0e-12);
        assertEquals(.79, stack.getGeneratorSpeed(), 1.0e-12);
        for (int i = 0; i < 20; i++) stack.step(.005, .5, .5);
        assertEquals(.5, stack.getPref(), 1.0e-9);
        assertEquals(0, stack.getPitchController().getPitch(), 1.0e-9);
        assertEquals(.5, stack.getAerodynamics().getMechanicalPower(), 1.0e-9);
    }

    @Test
    void aerodynamicPowerUsesTheDocumentedNonlinearPitchProduct() {
        Wtara1Model model = new Wtara1Model(new Wtara1Data(.007, 0));
        model.initialize(1.0);

        model.step(5.0);
        assertEquals(.825, model.getMechanicalPower(), 1.0e-12);
        model.step(10.0);
        assertEquals(.3, model.getMechanicalPower(), 1.0e-12);
    }

    @Test
    void nonzeroInitialPitchPreservesTheOperatingPointAndBothDirections() {
        Wtara1Model model = new Wtara1Model(new Wtara1Data(.01, 2.0));
        model.initialize(.8);
        assertEquals(.8, model.getMechanicalPower(), 1.0e-12);

        model.step(5.0);
        assertEquals(.65, model.getMechanicalPower(), 1.0e-12);
        model.step(1.0);
        assertEquals(.81, model.getMechanicalPower(), 1.0e-12);
    }

    @Test
    void pitchControllerCombinesSpeedAndPowerCompensationPiPaths() {
        Wtpta1Model model = new Wtpta1Model(
                new Wtpta1Data(2, 1, 4, 3, .5, 1, 20, 0, 100, -100));
        model.initialize(2.0, 1.0);

        model.step(.1, .9, .8, 1.05);

        // Power error=.1: PIc=.3+.04=.34. Speed error=.1: PIw=.1+(2+.02)=2.12.
        // Target=2.46 and Tp=1 gives pitch=2+.1*(2.46-2)=2.046.
        assertEquals(.02, model.getSpeedIntegral() - 2.0, 1.0e-12);
        assertEquals(.04, model.getCompensationIntegral(), 1.0e-12);
        assertEquals(2.046, model.getPitch(), 1.0e-12);
    }

    @Test
    void pitchRateAndAngleLimitsPreventWindupAndPermitRecovery() {
        Wtpta1Model model = new Wtpta1Model(
                new Wtpta1Data(10, 0, 0, 0, 0, 0, 1, 0, .5, -.5));
        model.initialize(0.0, 1.0);

        for (int i = 0; i < 40; i++) model.step(.1, .8, .8, 2.0);
        assertEquals(1.0, model.getPitch(), 1.0e-12);
        assertTrue(model.getSpeedIntegral() <= 1.0 + 1.0e-12);

        for (int i = 0; i < 10; i++) model.step(.1, .8, .8, 0.0);
        assertEquals(.5, model.getPitch(), 1.0e-12);
        assertTrue(model.getSpeedIntegral() < 1.0);
    }

    @Test
    void torqueControllerInterpolatesAndClampsThePowerSpeedCurve() {
        Wttqa1Model model = torqueController(0, 0, 0, 0, 0, 10, 0);

        assertEquals(.58, model.speedForPower(.1), 1.0e-12);
        assertEquals(.65, model.speedForPower(.3), 1.0e-12);
        assertEquals(.79, model.speedForPower(.5), 1.0e-12);
        assertEquals(.93, model.speedForPower(.7), 1.0e-12);
        assertEquals(1.0, model.speedForPower(.9), 1.0e-12);
    }

    @Test
    void torqueControllerFiltersPowerBeforeTheSpeedCurve() {
        Wttqa1Model model = torqueController(0, 0, 0, .2, .4, 10, 0);
        model.initialize(.5);

        model.step(.1, .7, .79);

        assertEquals(.6, model.getFilteredPower(), 1.0e-12);
        assertEquals(.8075, model.getSpeedReference(), 1.0e-12);
        assertEquals(.5, model.getPref(), 1.0e-12);
    }

    @Test
    void torqueControllerPowerErrorModeUsesPowerworldNegativeFeedback() {
        Wttqa1Model model = torqueController(1, 1, 0, 0, 0, 10, 0);
        model.initialize(.5);

        model.step(.1, .6, 1.0);

        double initialTorque = .5 / .79;
        assertEquals(initialTorque - .1, model.getTorque(), 1.0e-12);
        assertEquals(initialTorque - .1, model.getPref(), 1.0e-12);
    }

    @Test
    void torquePowerErrorUsesFilteredPowerAndFreezesIntegratorDuringDip() {
        Wttqa1Model model = torqueController(1, 0, 2, .2, 0, 10, 0);
        model.initialize(.5);
        double initialIntegral = model.getTorqueIntegral();

        model.step(.1, .7, 1.0, true);

        assertEquals(.6, model.getFilteredPower(), 1.0e-12);
        assertEquals(initialIntegral, model.getTorqueIntegral(), 1.0e-12);

        model.step(.1, .7, 1.0, false);
        assertTrue(model.getTorqueIntegral() < initialIntegral);
    }

    @Test
    void torqueControllerDoesNotAmplifyPartitionedSolverRoundoffAtEquilibrium() {
        Wttqa1Model model = torqueController(1, 2.7, .5, .08, 60, 1.002, 0);
        model.initialize(.625);
        double initialTorque = model.getTorque();

        for (int i = 0; i < 240; i++) {
            model.step(1.0 / 240.0, .625 - 5.0e-10, model.getSpeedReference());
        }

        assertEquals(initialTorque, model.getTorque(), 1.0e-12);
        assertEquals(.625, model.getPref(), 1.0e-11);
    }

    @Test
    void torqueControllerSpeedErrorModeMatchesAndesEquations() {
        Wttqa1Model model = torqueController(0, 1, 0, 0, 0, 10, 0);
        model.initialize(.5);

        model.step(.1, .5, .7);

        double expectedTorque = .5 / .79 + .09;
        assertEquals(expectedTorque, model.getTorque(), 1.0e-12);
        assertEquals(expectedTorque * .7, model.getPref(), 1.0e-12);
    }

    @Test
    void torqueControllerLimitStopsWindupAndAllowsRecovery() {
        Wttqa1Model model = torqueController(1, 0, 1, 0, 0, .7, 0);
        model.initialize(.5);

        for (int i = 0; i < 20; i++) model.step(.01, 0.0, 1.0);
        double saturatedIntegral = model.getTorqueIntegral();
        assertEquals(.7, model.getTorque(), 1.0e-12);

        for (int i = 0; i < 10; i++) model.step(.01, 1.5, 1.0);
        assertTrue(model.getTorqueIntegral() < saturatedIntegral);
        assertTrue(model.getTorque() < .7);
    }

    private static Wttqa1Model torqueController(int tFlag, double kpp, double kip,
            double tp, double twref, double teMax, double teMin) {
        return new Wttqa1Model(new Wttqa1Data(tFlag, kpp, kip, tp, twref, teMax, teMin,
                .2, .58, .4, .72, .6, .86, .8, 1.0, 0));
    }
}
