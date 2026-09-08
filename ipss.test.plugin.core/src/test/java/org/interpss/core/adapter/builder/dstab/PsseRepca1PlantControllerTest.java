package org.interpss.core.adapter.builder.dstab;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.nio.file.Files;
import java.nio.file.Path;

import org.apache.commons.math3.complex.Complex;
import org.interpss.CorePluginTestSetup;
import org.interpss.dstab.renewable.Reeca1Data;
import org.interpss.dstab.renewable.Reeca1Model;
import org.interpss.dstab.renewable.Regca1Data;
import org.interpss.dstab.renewable.Regca1Model;
import org.interpss.dstab.renewable.Repca1Data;
import org.interpss.dstab.renewable.Repca1Model;
import org.interpss.fadapter.builder.DStabNetworkBuilder;
import org.interpss.fadapter.builder.AclfNetworkBuilder;
import org.interpss.fadapter.psse.PSSEDStabDirectParser;
import org.interpss.numeric.datatype.Unit.UnitType;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.interpss.core.aclf.AclfBranch;

public class PsseRepca1PlantControllerTest extends CorePluginTestSetup {

    @Test
    void directParserRetainsAllParametersAndAttachesToReeca1(@TempDir Path tempDir)
            throws Exception {
        DStabNetworkBuilder builder = DStabBuilderTestFixture.createBuilder();
        Regca1Model converter = addRenewableChain(builder);
        Path dyr = tempDir.resolve("repca1.dyr");
        Files.writeString(dyr,
                "1 'REPCA1' 1 2 3 4 'X' 1 0 1 .01 .02 .03 .04 .05 .06 .07 .08 .09 "
                + "2 -2 -.1 .1 1 -1 .11 .12 .13 -.01 .01 .2 -.2 3 -3 .14 4 5 1 /\n");

        new PSSEDStabDirectParser(builder).setStrictImport(true).parseDynFile(dyr.toString());

        Reeca1Model electrical = converter.getReeca1Controller();
        Repca1Model plant = electrical.getPlantController();
        assertNotNull(plant);
        assertSame(plant, converter.getActiveElectricalController().getPlantController());
        assertEquals(expectedData(), plant.getData());
    }

    @Test
    void optionalPuFlagDefaultsToPowerWorldModelBase(@TempDir Path tempDir) throws Exception {
        DStabNetworkBuilder builder = DStabBuilderTestFixture.createBuilder();
        Regca1Model converter = addRenewableChain(builder);
        Path dyr = tempDir.resolve("repca1-default-puflag.dyr");
        Files.writeString(dyr,
                "1 'REPCA1' 1 2 3 4 'X' 1 0 1 .01 .02 .03 .04 .05 .06 .07 .08 .09 "
                + "2 -2 -.1 .1 1 -1 .11 .12 .13 -.01 .01 .2 -.2 3 -3 .14 4 5 /\n");

        new PSSEDStabDirectParser(builder).setStrictImport(true).parseDynFile(dyr.toString());

        assertEquals(1, converter.getReeca1Controller().getPlantController().getData().puFlag());
    }

    @Test
    void rejectsInvalidControlFlags() {
        Repca1Data valid = expectedData();
        assertThrows(IllegalArgumentException.class, () -> copyWithFlags(valid, 2, 0, 1));
        assertThrows(IllegalArgumentException.class, () -> copyWithFlags(valid, 1, -1, 1));
        assertThrows(IllegalArgumentException.class, () -> copyWithFlags(valid, 1, 0, 3));
    }

    @Test
    void monitoredBranchDirectionAndReactivePowerControlUseRequestedTerminal() throws Exception {
        MeasurementFixture fixture = measurementFixture();
        Repca1Model forward = new Repca1Model(
                plantData(1, 1, 2, 0, 0, 0, 0, 0, 0), fixture.converter());
        forward.initialize(.8, .2, 1.0);
        Complex sf = fixture.branch().powerFrom2To(UnitType.PU);
        assertEquals(sf.getReal(), forward.getMeasuredActivePower(), 1.0e-12);
        assertEquals(sf.getImaginary(), forward.getMeasuredReactiveOrVoltage(), 1.0e-12);

        Repca1Model reverse = new Repca1Model(
                plantData(2, 2, 1, 0, 0, 0, 0, 0, 0), fixture.converter());
        reverse.initialize(.8, .2, 1.0);
        Complex sr = fixture.branch().powerTo2From(UnitType.PU);
        assertEquals(sr.getReal(), reverse.getMeasuredActivePower(), 1.0e-12);
        assertEquals(sr.getImaginary(), reverse.getMeasuredReactiveOrVoltage(), 1.0e-12);
    }

    @Test
    void remoteVoltageSupportsDroopAndLineDropCompensation() throws Exception {
        MeasurementFixture fixture = measurementFixture();
        Complex s = fixture.branch().powerFrom2To(UnitType.PU);
        Complex remoteVoltage = fixture.builder().getDStabNetwork().getDStabBus("Bus2").getVoltage();

        Repca1Model droop = new Repca1Model(
                plantData(2, 1, 2, 0, 1, 0, 0, .4, 0), fixture.converter());
        droop.initialize(.8, .2, 1.0);
        assertEquals(remoteVoltage.abs() + .4 * s.getImaginary(),
                droop.getMeasuredReactiveOrVoltage(), 1.0e-12);

        Repca1Model lineDrop = new Repca1Model(
                plantData(2, 1, 2, 1, 1, .02, .08, 0, 0), fixture.converter());
        lineDrop.initialize(.8, .2, 1.0);
        Complex current = s.divide(fixture.builder().getDStabNetwork()
                .getDStabBus("Bus1").getVoltage()).conjugate();
        double expected = remoteVoltage.subtract(new Complex(.02, .08).multiply(current)).abs();
        assertEquals(expected, lineDrop.getMeasuredReactiveOrVoltage(), 1.0e-12);
    }

    @Test
    void zeroBranchFallbackUsesSelectedBusVoltageAndZeroPower() throws Exception {
        MeasurementFixture fixture = measurementFixture();
        Repca1Model plant = new Repca1Model(
                plantData(2, 0, 0, 0, 1, 0, 0, 0, 0), fixture.converter());

        plant.initialize(.8, .2, 1.0);

        assertEquals(0.0, plant.getMeasuredActivePower(), 1.0e-12);
        assertEquals(.96, plant.getMeasuredReactiveOrVoltage(), 1.0e-12);
        assertEquals(true, plant.isUsingZeroBranchFallback());
    }

    @Test
    void branchPowerIsNormalizedToTheGeneratorModelBaseForBothPuDeclarations()
            throws Exception {
        MeasurementFixture fixture = measurementFixture();
        fixture.converter().getParentGen().setMvaBase(50.0);
        double expected = 2.0 * fixture.branch().powerFrom2To(UnitType.PU).getReal();

        for (int puFlag : new int[] {0, 1}) {
            Repca1Model plant = new Repca1Model(
                    plantData(1, 1, 2, 0, 0, 0, 0, 0, puFlag), fixture.converter());
            plant.initialize(.8, .2, 1.0);
            assertEquals(50.0, plant.getDeviceBaseMva(), 1.0e-12);
            assertEquals(expected, plant.getMeasuredActivePower(), 1.0e-12,
                    "PUflag=" + puFlag);
        }
    }

    @Test
    void reactivePiFilterAndLeadLagFollowDocumentedBlockOrder() {
        Repca1Model plant = new Repca1Model(dynamicData(0, 0, .1, 2, 1, .05, .1,
                .7, 1, -1, 0, 0, 0, 0, 0));
        plant.initialize(.8, .2, 1.0);

        plant.step(.05, .8, 0.0, 1.0, 1.0);

        double measuredQ = modifiedEulerLag(.2, 0.0, .1, .05);
        double error = .2 - measuredQ;
        double integral = error * .05;
        double piOutput = 2.0 * error + integral;
        double leadLagState = modifiedEulerLag(0.0, piOutput, .1, .05);
        double leadLagOutput = .5 * piOutput + .5 * leadLagState;
        assertEquals(measuredQ, plant.getMeasuredReactiveOrVoltage(), 1.0e-12);
        assertEquals(.2, plant.getReactiveReference(), 1.0e-12);
        assertEquals(error, plant.getReactiveControlRawError(), 1.0e-12);
        assertEquals(error, plant.getReactiveControlDeadbandOutput(), 1.0e-12);
        assertEquals(error, plant.getReactiveControlLimitedError(), 1.0e-12);
        assertEquals(piOutput, plant.getReactiveControlPreLimitOutput(), 1.0e-12);
        assertEquals(integral, plant.getReactiveControlIntegral(), 1.0e-12);
        assertEquals(leadLagState, plant.getLeadLagState(), 1.0e-12);
        assertEquals(leadLagOutput, plant.getQref(), 1.0e-12);
    }

    @Test
    void stageAwareReactivePathUsesTheCorrectedEndpoint() {
        Repca1Model plant = new Repca1Model(dynamicData(0, 0, .1, 2, 1, .05, .1,
                .7, 1, -1, 0, 0, 0, 0, 0));
        plant.initialize(.8, .2, 1.0);

        plant.step(.05, .8, 0.0, 1.0, 1.0, 0);
        assertEquals(.1, plant.getMeasuredReactiveOrVoltage(), 1.0e-12);
        assertEquals(.2, plant.getReactiveControlOutput(), 1.0e-12);
        assertEquals(.1, plant.getQref(), 1.0e-12);

        plant.step(.05, .8, 0.0, 1.0, 1.0, 1);
        assertEquals(.125, plant.getMeasuredReactiveOrVoltage(), 1.0e-12);
        assertEquals(.0025, plant.getReactiveControlIntegral(), 1.0e-12);
        assertEquals(.05, plant.getLeadLagState(), 1.0e-12);
        assertEquals(.1525, plant.getReactiveControlOutput(), 1.0e-12);
        assertEquals(.10125, plant.getQref(), 1.0e-12);
    }

    @Test
    void stageAwareCorrectorRequiresItsPredictor() {
        Repca1Model plant = new Repca1Model(dynamicData(0, 0, .1, 2, 1, .05, .1,
                .7, 1, -1, 0, 0, 0, 0, 0));
        plant.initialize(.8, .2, 1.0);

        assertThrows(IllegalStateException.class,
                () -> plant.step(.05, .8, 0.0, 1.0, 1.0, 1));
    }

    @Test
    void stageAwareActivePathCorrectsThePiAndOutputLag() {
        Repca1Model plant = new Repca1Model(dynamicData(0, 1, 0, 0, 0, 0, 0,
                .7, 1, -1, 2, 1, .1, 10, 20));
        plant.initialize(.8, .2, 1.0);

        plant.step(.05, .7, .2, 1.0, .99, 0);
        assertEquals(.01, plant.getActiveControlIntegral(), 1.0e-12);
        assertEquals(.2, plant.getActiveLagState(), 1.0e-12);

        plant.step(.05, .7, .2, 1.0, .99, 1);
        assertEquals(.0125, plant.getActiveControlIntegral(), 1.0e-12);
        assertEquals(.2025, plant.getActiveLagState(), 1.0e-12);
        assertEquals(.2025, plant.getPref(), 1.0e-12);
    }

    @Test
    void stageAwareAlgebraicBypassesAndAntiWindupDoNotCreateStates() {
        Repca1Model plant = new Repca1Model(dynamicData(0, 1, 0, 2, 1, 0, 0,
                .7, .1, -.1, 2, 1, 0, 10, 20));
        plant.initialize(.8, .2, 1.0);

        plant.step(.05, .7, .1, 1.0, .99, 0);
        plant.step(.05, .7, .1, 1.0, .99, 1);

        assertEquals(.1, plant.getMeasuredReactiveOrVoltage(), 1.0e-12);
        assertEquals(0.0, plant.getReactiveControlIntegral(), 1.0e-12);
        assertEquals(.1, plant.getReactiveControlOutput(), 1.0e-12);
        assertEquals(.1, plant.getLeadLagState(), 1.0e-12);
        assertEquals(.1, plant.getQref(), 1.0e-12);
        assertEquals(.6125, plant.getActiveLagState(), 1.0e-12);
        assertEquals(.6125, plant.getPref(), 1.0e-12);
    }

    @Test
    void lowVoltageFreezesOnlyTheReactiveIntegratorAndLimitsRemainLive() {
        Repca1Model plant = new Repca1Model(dynamicData(0, 0, 0, 2, 1, 0, 0,
                .95, .15, -.15, 0, 0, 0, 0, 0));
        plant.initialize(.8, .2, 1.0);

        plant.step(.1, .8, .1, .9, 1.0);

        assertEquals(0.0, plant.getReactiveControlIntegral(), 1.0e-12);
        assertEquals(.15, plant.getQref(), 1.0e-12,
                "the proportional path remains live and is output-limited");
    }

    @Test
    void reactivePiDoesNotWindUpAgainstItsOutputLimit() {
        Repca1Model plant = new Repca1Model(dynamicData(0, 0, 0, 2, 1, 0, 0,
                .7, .1, -.1, 0, 0, 0, 0, 0));
        plant.initialize(.8, .2, 1.0);

        plant.step(.1, .8, .1, 1.0, 1.0);

        assertEquals(0.0, plant.getReactiveControlIntegral(), 1.0e-12);
        assertEquals(.1, plant.getQref(), 1.0e-12);
    }

    @Test
    void initializationExpandsPiLimitsToIncludeZeroIncrementalOutput() {
        Repca1Model plant = new Repca1Model(dynamicData(0, 0, 0, 2, 1, 0, 0,
                .7, -.1, -.2, 0, 0, 0, 0, 0));
        plant.initialize(.8, .2, 1.0);

        plant.step(.1, .8, .1, 1.0, 1.0);

        assertEquals(0.0, plant.getQref(), 1.0e-12);
        assertEquals(0.0, plant.getReactiveControlIntegral(), 1.0e-12);
    }

    @Test
    void frequencyPowerPiAndOutputLagUseDirectionalDroop() {
        Repca1Model plant = new Repca1Model(dynamicData(0, 1, 0, 0, 0, 0, 0,
                .7, 1, -1, 2, 1, .1, 10, 20));
        plant.initialize(.8, .2, 1.0);

        plant.step(.05, .7, .2, 1.0, .99);

        // Tp=0 in this fixture, so Perr=(.8-.7)+Dup*(1-.99)=.3.
        // Active PI=2*.3 + integral(1*.3*.05)=.615; Tg=.1 is integrated
        // with the renewable stack's modified-Euler corrector.
        assertEquals(.015, plant.getActiveControlIntegral(), 1.0e-12);
        double expectedOutput = modifiedEulerLag(0.0, .615, .1, .05);
        assertEquals(expectedOutput, plant.getActiveLagState(), 1.0e-12);
        assertEquals(expectedOutput, plant.getPref(), 1.0e-12);

        Repca1Model disabled = new Repca1Model(dynamicData(0, 0, 0, 0, 0, 0, 0,
                .7, 1, -1, 2, 1, .1, 10, 20));
        disabled.initialize(.8, .2, 1.0);
        disabled.step(.05, .7, .2, 1.0, .99);
        assertEquals(0.0, disabled.getPref(), 1.0e-12);
    }

    private static Regca1Model addRenewableChain(DStabNetworkBuilder builder) {
        Regca1Model converter = builder.addRegca1("Bus1", "1",
                new Regca1Data(0, .02, 10, .9, .4, 1.22, 1.2, .8,
                        .4, -1.3, .02, .7, 0, 0, .8));
        builder.addReeca1("Bus1", "1", new Reeca1Data(0, 0, 0, 0, 0, 0,
                .8, 1.2, 0, 0, 0, 0, 1, -1, 1, 0, 0, 0,
                0, 1, -1, 1, -1, 0, 0, 0, 0, 0, 0,
                1, -1, 2, -2, 10, 0,
                0, 0, 0, 0, 0, 0, 0, 0,
                0, 0, 0, 0, 0, 0, 0, 0));
        return converter;
    }

    private static double modifiedEulerLag(double state, double input,
            double timeConstant, double dt) {
        double initialDerivative = (input - state) / timeConstant;
        double predicted = state + dt * initialDerivative;
        return state + .5 * dt * (initialDerivative
                + (input - predicted) / timeConstant);
    }

    private static MeasurementFixture measurementFixture() throws Exception {
        DStabNetworkBuilder builder = DStabBuilderTestFixture.createBuilder();
        AclfNetworkBuilder topology = new AclfNetworkBuilder(builder.getDStabNetwork());
        topology.addBus("Bus2", "Remote", 2L, 16500.0, .96, -.04, null, null, null);
        AclfBranch branch = topology.addLine("Bus1", "Bus2", "X",
                new Complex(.02, .08), Complex.ZERO, null, null, 0, 0, 0, true);
        return new MeasurementFixture(builder, addRenewableChain(builder), branch);
    }

    private static Repca1Data plantData(int remote, int from, int to, int vcFlag,
            int refFlag, double rc, double xc, double kc, int puFlag) {
        return new Repca1Data(remote, from, to, "X", vcFlag, refFlag, 0,
                0, 0, 0, 0, 0, .7, rc, xc, kc,
                1, -1, 0, 0, 1, -1, 0, 0, 0,
                0, 0, 1, -1, 1, -1, 0, 0, 0, puFlag);
    }

    private static Repca1Data dynamicData(int refFlag, int fFlag, double tfltr,
            double kp, double ki, double tft, double tfv, double vfrz,
            double qmax, double qmin, double kpg, double kig, double tg,
            double ddn, double dup) {
        return new Repca1Data(0, 0, 0, "1", 0, refFlag, fFlag,
                tfltr, kp, ki, tft, tfv, vfrz, 0, 0, 0,
                1, -1, 0, 0, qmax, qmin, kpg, kig, 0,
                0, 0, 1, -1, 1, -1, tg, ddn, dup, 0);
    }

    private record MeasurementFixture(DStabNetworkBuilder builder,
            Regca1Model converter, AclfBranch branch) {}

    private static Repca1Data expectedData() {
        return new Repca1Data(2, 3, 4, "X", 1, 0, 1,
                .01, .02, .03, .04, .05, .06, .07, .08, .09,
                2, -2, -.1, .1, 1, -1, .11, .12, .13,
                -.01, .01, .2, -.2, 3, -3, .14, 4, 5, 1);
    }

    private static Repca1Data copyWithFlags(Repca1Data d, int vc, int ref, int freq) {
        return new Repca1Data(d.remoteBus(), d.branchFromBus(), d.branchToBus(), d.branchId(),
                vc, ref, freq, d.tfltr(), d.kp(), d.ki(), d.tft(), d.tfv(), d.vfrz(),
                d.rc(), d.xc(), d.kc(), d.emax(), d.emin(), d.dbd1(), d.dbd2(),
                d.qmax(), d.qmin(), d.kpg(), d.kig(), d.tp(), d.fdbd1(), d.fdbd2(),
                d.femax(), d.femin(), d.pmax(), d.pmin(), d.tg(), d.ddn(), d.dup(),
                d.puFlag());
    }
}
