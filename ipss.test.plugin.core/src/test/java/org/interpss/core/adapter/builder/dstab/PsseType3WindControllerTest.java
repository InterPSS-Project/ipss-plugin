package org.interpss.core.adapter.builder.dstab;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;

import org.interpss.CorePluginTestSetup;
import org.interpss.dstab.renewable.Reeca1Model;
import org.interpss.dstab.renewable.Regca1Model;
import org.interpss.dstab.renewable.WindControlStack;
import org.interpss.dstab.renewable.Wtara1Data;
import org.interpss.dstab.renewable.Wtara1Model;
import org.interpss.dstab.renewable.Wtdta1Data;
import org.interpss.dstab.renewable.Wtdta1Model;
import org.interpss.dstab.renewable.Wtpta1Data;
import org.interpss.dstab.renewable.Wtpta1Model;
import org.interpss.dstab.renewable.WtgtAData;
import org.interpss.dstab.renewable.Wttqa1Data;
import org.interpss.dstab.renewable.Wttqa1Model;
import org.interpss.fadapter.builder.DStabNetworkBuilder;
import org.interpss.fadapter.psse.PSSEDStabDirectParser;
import org.interpss.fadapter.psse.PSSEMultiFileLoader;
import org.interpss.fadapter.pwd.dyd.PowerWorldDydWtgtAImporter;
import org.interpss.fadapter.pwd.dyd.PowerWorldDydWtgtAImporter.Status;
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
                + "1 'WTDTA1' 1 5.0 .1 .8 1.5 .2 /\n"
                + "1 'WTPTA1' 1 28 137 22.03 1.17 .27 .3 27 0 10 -10 /\n"
                + "1 'WTTQA1' 1 1 1.9 .5 .04 60 1.002 0 .2 .58 .4 .72 .6 .86 .8 1 0 /\n"
                + "1 'WTARA1' 1 .007 0 /\n");

        PSSEDStabDirectParser parser = new PSSEDStabDirectParser(builder).setStrictImport(true);
        parser.parseDynFile(dyr.toString());
        assertEquals(6, parser.getLastImportReport().totalRecordCount());

        Regca1Model converter = (Regca1Model) ((DStabGen) builder.getDStabNetwork()
                .getDStabBus("Bus1").getContributeGen("1")).getDynamicGenDevice();
        Reeca1Model electrical = converter.getReeca1Controller();
        WindControlStack stack = electrical.getWindControlStack();
        assertNotNull(stack);
        assertTrue(stack.isComplete());
        assertEquals(new Wtdta1Data(5.0, .1, .8, 1.5, .2),
                stack.getDriveTrain().getData());
        assertEquals(new Wtara1Data(.007, 0), stack.getAerodynamics().getData());
        assertEquals(new Wtpta1Data(28, 137, 22.03, 1.17, .27, .3, 27, 0, 10, -10),
                stack.getPitchController().getData());
        assertEquals(new Wttqa1Data(1, 1.9, .5, .04, 60, 1.002, 0,
                .2, .58, .4, .72, .6, .86, .8, 1, 0),
                stack.getTorqueController().getData());
    }

    @Test
    void supplementalWtgtADoesNotReplaceAnExplicitDyrDriveTrain(@TempDir Path tempDir)
            throws Exception {
        DStabNetworkBuilder builder = DStabBuilderTestFixture.createBuilder();
        Path dyr = tempDir.resolve("type3.dyr");
        Files.writeString(dyr,
                "1 'REGCA1' 1 1 .02 10 .9 .4 1.22 1.2 .9 .5 -1.3 .02 0 100 -100 .7 /\n"
                + "1 'REECA1' 1 0 0 1 1 0 0 .85 1.15 .02 0 0 5 1.1 -1.1 0 0 0 .5 .02 .436 -.436 1.1 .9 1.3 2.4 .6 1.5 0 .02 99 -99 1 0 1.3 .02 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 /\n"
                + "1 'WTDTA1' 1 5.0 .1 .8 1.5 .2 /\n");
        new PSSEDStabDirectParser(builder).setStrictImport(true)
                .parseDynFile(dyr.toString());
        Regca1Model converter = (Regca1Model) ((DStabGen) builder.getDStabNetwork()
                .getDStabBus("Bus1").getContributeGen("1")).getDynamicGenDevice();
        Wtdta1Model original = converter.getReeca1Controller().getWindControlStack()
                .getDriveTrain();
        Path dyd = tempDir.resolve("type3.dyd");
        Files.writeString(dyd,
                "wtgt_a 1 \"BUS 1\" 230.00 \"1\" : #9 0 4 1 .2 3.2 1\n");

        var result = new PowerWorldDydWtgtAImporter().importFile(dyd, builder,
                java.util.List.of(), java.util.List.of());

        assertEquals(1, result.count(Status.REJECTED));
        assertSame(original, converter.getReeca1Controller().getWindControlStack()
                .getDriveTrain());
    }

    @Test
    void multiFileLoaderDoesNotConsumeSiblingPslfDyd(@TempDir Path tempDir)
            throws Exception {
        Path raw = tempDir.resolve("type3.raw");
        Files.copy(Path.of("testData", "adpter", "psse", "v33", "SMIB",
                "SMIB_v33.raw"), raw);
        Path dyr = tempDir.resolve("type3.dyr");
        Files.writeString(dyr,
                "1 'REGCA1' 1 1 .02 10 .9 .4 1.22 1.2 .9 .5 -1.3 .02 0 100 -100 .7 /\n"
                + "1 'REECA1' 1 0 0 1 1 0 0 .85 1.15 .02 0 0 5 1.1 -1.1 0 0 0 .5 .02 .436 -.436 1.1 .9 1.3 2.4 .6 1.5 0 .02 99 -99 1 0 1.3 .02 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 /\n");
        Files.writeString(tempDir.resolve("type3.dyd"),
                "wtgt_a 1 \"BUS 1\" 230.00 \"1\" : #9 0 4 1 .2 3.2 1\n");

        PSSEMultiFileLoader pssELoader = new PSSEMultiFileLoader();
        var pssEContext = pssELoader.loadDStab(raw.toString(), dyr.toString());
        Regca1Model pssEConverter = (Regca1Model) ((DStabGen) pssEContext
                .getDStabilityNet().getDStabBus("Bus1").getContributeGen("1"))
                        .getDynamicGenDevice();
        assertNull(pssEConverter.getReeca1Controller().getWindControlStack());
        assertThrows(com.interpss.common.exp.InterpssException.class,
                () -> new PSSEMultiFileLoader().loadDStab(raw.toString(),
                        tempDir.resolve("type3.dyd").toString()));
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
    void driveTrainInitializesAtTheTorqueControllersPowerSpeedOperatingPoint() {
        WindControlStack stack = new WindControlStack();
        stack.setDriveTrain(new Wtdta1Model(new Wtdta1Data(5, .1, .8, 1.5, .2)));
        stack.setAerodynamics(new Wtara1Model(new Wtara1Data(.007, 0)));
        stack.setTorqueController(torqueController(0, 1, .5, .04, 60, 1.2, 0));

        stack.initialize(.5);

        assertEquals(.79, stack.getGeneratorSpeed(), 1.0e-12);
        assertEquals(.79, stack.getTurbineSpeed(), 1.0e-12);
        assertEquals(.5 / .79, stack.getDriveTrain().getShaftTorque(), 1.0e-12);
        for (int i = 0; i < 20; i++) stack.step(.005, .5, .5);
        assertEquals(.79, stack.getGeneratorSpeed(), 1.0e-12);
        assertEquals(.5, stack.getPref(), 1.0e-12);
        // WTTQA1 already forms Pref=torque*wg, so REECA1 must not multiply by wg again.
        assertEquals(1.0, stack.getElectricalControllerSpeed(), 1.0e-12);
    }

    @Test
    void driveTrainUsesAndesTwoMassEquationsWithModifiedEuler() {
        Wtdta1Model model = new Wtdta1Model(new Wtdta1Data(5, .1, .8, 1.5, .2));
        model.initialize(.8, 1.0);

        model.step(.1, .8, 1.0);

        // Predictor derivatives: dwt=0, dwg=-0.1, dTshaft=0.
        // At the predicted state (wt=1, wg=.99, T=.8), the ANDES equations give
        // dwt=-.00025, dwg=-.103550505..., dTshaft=.036.
        assertEquals(.9999875, model.getTurbineSpeed(), 1.0e-12);
        assertEquals(.9898224747474748, model.getGeneratorSpeed(), 1.0e-12);
        assertEquals(.8018, model.getShaftTorque(), 1.0e-12);
        assertEquals(3.6, model.shaftStiffness(), 1.0e-12);
    }

    @Test
    void zeroTurbineInertiaFractionUsesTheDocumentedSingleMassModel() {
        Wtdta1Model model = new Wtdta1Model(new Wtdta1Data(4, 0, 0, 0, 0));
        model.initialize(.8, 1.0);

        model.step(.1, .8, 1.0);

        double d0 = -.2 / 8.0;
        double predicted = 1.0 + .1 * d0;
        double d1 = (.8 / predicted - 1.0 / predicted) / 8.0;
        double expected = 1.0 + .05 * (d0 + d1);
        assertTrue(model.isSingleMass());
        assertEquals(expected, model.getGeneratorSpeed(), 1.0e-12);
        assertEquals(expected, model.getTurbineSpeed(), 1.0e-12);
    }

    @Test
    void powerWorldWtgtAConversionPreservesMassesStiffnessBaseAndInitialSpeed() {
        WtgtAData source = new WtgtAData(4.0, 1.0, .2, 3.2, 100.0, 1.05);
        Wtdta1Model model = new Wtdta1Model(source, 200.0);

        model.initialize(.4, .8);

        assertEquals(5.0, model.getData().h(), 0.0);
        assertEquals(.8, model.getData().htfrac(), 0.0);
        assertEquals(3.2, model.shaftStiffness(), 1.0e-12);
        assertEquals(2.0, model.getInputPowerScale(), 0.0);
        assertEquals(.8, model.getInitialPower(), 0.0);
        assertEquals(1.05, model.getInitialSpeed(), 0.0);
        model.step(.01, .4, .4);
        assertEquals(1.05, model.getTurbineSpeed(), 1.0e-12);
        assertEquals(1.05, model.getGeneratorSpeed(), 1.0e-12);
    }

    @Test
    void powerWorldDriveTrainInitializesTorqueControllerAtW0WithoutPrefJump() {
        WindControlStack stack = new WindControlStack();
        stack.setDriveTrain(new Wtdta1Model(
                new WtgtAData(4.0, 1.0, .2, 3.2, 0.0, 1.0), 100.0));
        stack.setAerodynamics(new Wtara1Model(new Wtara1Data(.007, 0)));
        stack.setTorqueController(torqueController(1, 2.6, .5, .04, 60, 1.002, 0));

        stack.initialize(.625);
        stack.step(1.0 / 240.0, .625, .625);

        assertEquals(1.0, stack.getGeneratorSpeed(), 1.0e-12);
        assertEquals(.625, stack.getTorqueController().getTorque(), 1.0e-12);
        assertEquals(.625, stack.getPref(), 1.0e-12);
    }

    @Test
    void driveTrainWithoutTorqueControllerExposesGeneratorSpeedToReeca() {
        WindControlStack stack = new WindControlStack();
        stack.setDriveTrain(new Wtdta1Model(new Wtdta1Data(4, 0, 0, 0, 0)));
        stack.initialize(.8);

        stack.step(.1, 1.0, .8);

        assertEquals(stack.getGeneratorSpeed(), stack.getElectricalControllerSpeed(), 0.0);
        assertTrue(stack.getElectricalControllerSpeed() < 1.0);
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

        double filteredPower = modifiedEulerLag(.5, .7, .2, .1);
        double speedReference = modifiedEulerLag(.79,
                model.speedForPower(filteredPower), .4, .1);
        assertEquals(filteredPower, model.getFilteredPower(), 1.0e-12);
        assertEquals(speedReference, model.getSpeedReference(), 1.0e-12);
        assertEquals(.5, model.getPref(), 1.0e-12);
    }

    @Test
    void torqueControllerPowerErrorModeMatchesWeccFigure() {
        Wttqa1Model model = torqueController(1, 1, 0, 0, 0, 10, 0);
        model.initialize(.5);

        model.step(.1, .6, 1.0);

        double initialTorque = .5 / .79;
        assertEquals(initialTorque - .1, model.getTorque(), 1.0e-12);
        assertEquals(initialTorque - .1, model.getPref(), 1.0e-12);
    }

    @Test
    void torqueControllerUsesPlantControllerPref0InPowerErrorBranch() {
        Wttqa1Model model = torqueController(1, 1, 0, 0, 0, 10, 0);
        model.initialize(.5, 1.0);

        model.step(.1, .5, 1.0, .6, false);

        assertEquals(.6, model.getTorque(), 1.0e-12);
        assertEquals(.6, model.getPref(), 1.0e-12);
    }

    @Test
    void windStackForwardsAbsolutePlantReferenceToTorqueController() {
        WindControlStack stack = new WindControlStack();
        stack.setTorqueController(torqueController(1, 1, 0, 0, 0, 10, 0));
        stack.initialize(.5);

        stack.step(.1, .5, .5, .6, false);

        double generatorSpeed = stack.getGeneratorSpeed();
        assertEquals(.5 / generatorSpeed + .1 / generatorSpeed,
                stack.getTorqueController().getTorque(), 1.0e-12);
        assertEquals(.6, stack.getPref(), 1.0e-12);
    }

    @Test
    void torquePowerErrorUsesFilteredPowerAndFreezesIntegratorDuringDip() {
        Wttqa1Model model = torqueController(1, 0, 2, .2, 0, 10, 0);
        model.initialize(.5);
        double initialIntegral = model.getTorqueIntegral();

        model.step(.1, .7, 1.0, true);

        assertEquals(modifiedEulerLag(.5, .7, .2, .1),
                model.getFilteredPower(), 1.0e-12);
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

    private static double modifiedEulerLag(double state, double input,
            double timeConstant, double dt) {
        double initialDerivative = (input - state) / timeConstant;
        double predicted = state + dt * initialDerivative;
        return state + .5 * dt * (initialDerivative
                + (input - predicted) / timeConstant);
    }

    @Test
    void torqueControllerSpeedErrorModeMatchesPowerworldFlagDefinition() {
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
