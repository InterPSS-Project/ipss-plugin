package org.interpss.core.adapter.builder.dstab;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;

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
    @Test void nativePositiveLowerDeadbandIsAMagnitudeAndKeepsTheInitializedEquilibrium() {
        var data=new Repca1Data(0,0,0,"0",1,1,0,.066667,18,5,0,.05,0,0,0,.02,
                .1,-.1,.025,0,.493,-.493,.1,.05,.25,0,0,999,-999,999,-999,.1,20,0,1);
        var model=Repca1Model.fromNativeRecord(data);
        var boundary=new Repca1Model.Measurement(.24,.047,new Complex(1.01,0),
                new Complex(.24,-.047).divide(1.01),1);
        model.initializeMeasured(.24,.047,boundary);var initial=model.getNamedStates();
        for(int i=0;i<100;i++) {
            model.stepMeasured(.0005,boundary,0);model.stepMeasured(.0005,boundary,1);
        }
        for(var entry:initial.entrySet())assertEquals(entry.getValue(),model.getNamedStates().get(entry.getKey()),1e-12,entry.getKey());
        assertEquals(.025,model.getData().dbd1(),"Entered data must remain unchanged");
    }

    @Test void nativeFallbackProfilesReplayAllSevenStates() throws Exception {
        org.interpss.core.dstab.reference.RepcaNativeVerification.verifyProfiles(
                org.interpss.core.dstab.reference.RepcaFallbackReference.profiles(),org.interpss.core.dstab.reference.RepcaFallbackReference::branchFeedback);
    }
    @Test void nativeFallbackProfilesHaveExactImportAndMeasurementSourceDiagnostics(@TempDir Path staged) throws Exception {
        org.interpss.core.dstab.reference.RepcaNativeVerification.importAndRunProfiles(
                org.interpss.core.dstab.reference.RepcaFallbackReference.profiles(),staged,(profile,model)->{
                    var before=model.getNamedStates();
                    var expected=org.interpss.core.dstab.reference.RepcaFallbackReference.source(profile);
                    assertEquals(expected,model.getMeasurementSource());
                    assertEquals(expected!=Repca1Model.MeasurementSource.MONITORED_BRANCH,model.isUsingGeneratorPowerFallback());
                    assertEquals(expected==Repca1Model.MeasurementSource.GENERATOR_POWER_ZERO_IDENTIFIERS,model.isUsingZeroBranchFallback());
                    assertEquals(before,model.getNamedStates());
                });
    }
    @Test void nativeMissingBranchUsesGeneratorFeedbackAndRetainsTheRegulatedBus() throws Exception {
        var fixture=measurementFixture();var data=plantData(2,1,3,1,1,.02,.08,0,1);
        var model=fixture.builder().addRepca1("Bus1","1",data);model.initialize(.24,.047,1);
        Complex remote=fixture.builder().getDStabNetwork().getDStabBus("Bus2").getVoltage();
        Complex current=new Complex(.24,.047).divide(remote).conjugate();
        assertEquals(.24,model.getMeasuredActivePower(),1e-12);
        assertEquals(remote.subtract(new Complex(.02,.08).multiply(current)).abs(),model.getMeasuredReactiveOrVoltage(),1e-12);
        assertEquals(data,model.getData(),"Missing-branch fallback must not rewrite entered identifiers");
        assertTrue(model.isUsingGeneratorPowerFallback());
        assertEquals(Repca1Model.MeasurementSource.GENERATOR_POWER_MISSING_BRANCH,model.getMeasurementSource());
    }

    @Test void nativeMissingCircuitIsResolvedAgainOnReinitializationAfterItAppears() throws Exception {
        var fixture=measurementFixture();var data=withBranch(plantData(2,1,2,1,1,.02,.08,0,1),1,2,"Z");
        var model=fixture.builder().addRepca1("Bus1","1",data);model.initialize(.24,.047,1);
        assertEquals(.24,model.getMeasuredActivePower(),1e-12);
        assertEquals(Repca1Model.MeasurementSource.GENERATOR_POWER_MISSING_BRANCH,model.getMeasurementSource());
        var topology=new AclfNetworkBuilder(fixture.builder().getDStabNetwork());
        var branch=topology.addLine("Bus1","Bus2","Z",new Complex(.04,.12),Complex.ZERO,null,null,0,0,0,true);
        model.initialize(.24,.047,1);
        assertEquals(branch.powerFrom2To(UnitType.PU).getReal(),model.getMeasuredActivePower(),1e-12);
        assertEquals(Repca1Model.MeasurementSource.MONITORED_BRANCH,model.getMeasurementSource());
        assertTrue(!model.isUsingGeneratorPowerFallback());
    }

    @Test void nativeNegativeTargetKeepsTheSameOriginMeasurementInsteadOfFallingBack() throws Exception {
        var fixture=measurementFixture();var data=plantData(2,1,-2,1,1,.02,.08,0,1);
        var model=fixture.builder().addRepca1("Bus1","1",data);model.initialize(.24,.047,1);
        assertEquals(fixture.branch().powerFrom2To(UnitType.PU).getReal(),model.getMeasuredActivePower(),1e-12);
        assertEquals(Repca1Model.MeasurementSource.MONITORED_BRANCH,model.getMeasurementSource());
        assertEquals(data,model.getData());
    }

    @Test void historicalTypedMissingBranchRemainsStrictAndUnknownRegulatedBusIsNotSilentlyReplaced() throws Exception {
        var fixture=measurementFixture();var data=plantData(2,1,3,1,1,.02,.08,0,1);
        assertThrows(IllegalStateException.class,()->new Repca1Model(data,fixture.converter()).initialize(.24,.047,1));
        var invalid=fixture.builder().addRepca1("Bus1","1",plantData(99,1,3,1,1,.02,.08,0,1));
        assertThrows(IllegalStateException.class,()->invalid.initialize(.24,.047,1));
    }

    @Test void nativeColdPairedProfilesReplayAllSevenAllocatedStates() throws Exception {
        org.interpss.core.dstab.reference.RepcaNativeVerification.replayAllStates();
    }
    @Test void nativeProfilesHaveStrictImportAndCoupledSmallNetworkDiagnostics(@TempDir Path staged) throws Exception {
        org.interpss.core.dstab.reference.RepcaNativeVerification.importAndRunLocalProfiles(staged);
    }
    @Test void nativeLineDropInitializationRejectsTheBranchOriginCurrentSubstitution() {
        org.interpss.core.dstab.reference.RepcaNativeVerification.regulatedVoltageHasAnIndependentNativeInitializationWitness();
    }
    @Test
    void nativeZeroCompensationDoesNotInferTheMonitoredBranchImpedance() throws Exception {
        var fixture=measurementFixture();
        var model=fixture.builder().addRepca1("Bus1","1",plantData(2,1,2,1,1,0,0,0,1));
        model.initialize(.24,.0474445894361,1.0);
        assertEquals(fixture.builder().getDStabNetwork().getDStabBus("Bus2").getVoltageMag(),
                model.getMeasuredReactiveOrVoltage(),1e-12);
    }

    @Test void nativeNegativeOriginReversesPowerAtTheSameTerminalRatherThanSelectingTheOtherTerminal() throws Exception {
        var fixture=measurementFixture();
        var model=fixture.builder().addRepca1("Bus1","1",plantData(1,-1,2,0,0,0,0,0,1));
        model.initialize(.24,.0474445894361,1);
        Complex origin=fixture.branch().powerFrom2To(UnitType.PU);
        assertEquals(-origin.getReal(),model.getMeasuredActivePower(),1e-12);
        assertEquals(-origin.getImaginary(),model.getMeasuredReactiveOrVoltage(),1e-12);
    }

    @Test void nativeLineDropCurrentUsesTheRegulatedVoltageRatherThanTheBranchOriginVoltage() throws Exception {
        var fixture=measurementFixture();
        var model=fixture.builder().addRepca1("Bus1","1",plantData(2,1,2,1,1,.02,.08,0,1));
        model.initialize(.24,.0474445894361,1);
        Complex remote=fixture.builder().getDStabNetwork().getDStabBus("Bus2").getVoltage();
        Complex power=fixture.branch().powerFrom2To(UnitType.PU);
        double expected=remote.subtract(new Complex(.02,.08).multiply(power.divide(remote).conjugate())).abs();
        assertEquals(expected,model.getMeasuredReactiveOrVoltage(),1e-12);
    }

    @Test void nativeMeasuredBoundaryRejectsNonfiniteValuesAndObservationsArePure() {
        var data=dynamicData(0,0,.02,.5,1,.03,.1,.7,1,-1,.5,1,.1,20,20);
        var model=Repca1Model.fromNativeRecord(data);
        var input=new Repca1Model.Measurement(.24,.047,Complex.ONE,Complex.ZERO,1);
        model.initializeMeasured(.24,.047,input);var named=model.getNamedStates();var active=model.getIntegrationStates();
        double[] states=model.getNativeStates();states[1]=99;
        assertEquals(.047,model.getNativeStates()[1],0);assertEquals(0,model.getNativeStates()[0],0);
        assertEquals(0,model.getNativeStates()[4],0);assertEquals(0,model.getNativeStates()[5],0);assertEquals(0,model.getNativeStates()[6],0);
        assertEquals(Set.of("Measured Reactive Or Voltage","Reactive Control Integral","Lead Lag"),active.keySet());
        assertThrows(UnsupportedOperationException.class,()->active.put("Lead Lag",99.));
        assertEquals(named,model.getNamedStates());assertEquals(active,model.getIntegrationStates());
        assertThrows(IllegalArgumentException.class,()->new Repca1Model.Measurement(Double.NaN,.047,Complex.ONE,Complex.ZERO,1));
        assertThrows(IllegalArgumentException.class,()->model.initializeMeasured(.24,Double.NaN,input));assertEquals(named,model.getNamedStates());
        assertThrows(IllegalStateException.class,()->new Repca1Model(data).getNativeStates());
    }

    @Test
    void nativeAbsolutePowerLimitsAreTranslatedAroundTheInitialElectricalReference() throws Exception {
        var builder=DStabBuilderTestFixture.createBuilder();addRenewableChain(builder);
        var data=new Repca1Data(0,0,0,"0",0,0,1,.02,.5,1,.03,.1,.7,0,0,0,.2,-.2,0,0,
                1,-1,.5,1,.02,-.0006,.0006,.2,-.2,.245,.235,.1,20,20,1);
        var model=builder.addRepca1("Bus1","1",data);model.initialize(.24,.0474445894361,1);
        // The network fixture owns frequency, not this method's fallback argument.
        builder.getDStabNetwork().getDStabBus("Bus1").setFreq(1.02);
        for(int i=0;i<500;i++){model.step(.001,.24,.0474445894361,1,1.02,0);model.step(.001,.24,.0474445894361,1,1.02,1);}
        assertTrue(model.getPref()<-.004,"Positive absolute lower power limit must allow a negative increment");
        assertTrue(model.getPref()>=-.005-1e-12);
    }

    @Test
    void plantRateSnapshotDistinguishesPredictorFromCorrectedState() {
        var plant = new Repca1Model(dynamicData(1, 1, .1, 1, 2, 0, .1, .7, 1, -1, 1, 2, .1, 1, 1));
        plant.initialize(.8, .2, 1.0);
        assertTrue(plant.getStateDerivativeSnapshot().rates().isEmpty());
        plant.step(.001, .8, .2, .95, 1.0, 0);
        var predictor = plant.getStateDerivativeSnapshot();
        assertEquals(-.5, predictor.rates().get("Measured Reactive Or Voltage"), 1.e-12);
        assertEquals(1.0, predictor.evaluationStates().get("Measured Reactive Or Voltage"), 0.0);
        assertEquals(com.interpss.dstab.controller.runtime.IntegrationStateMonitor.Stage.PREDICTOR, predictor.stage());
        var state = plant.getIntegrationStates();
        for (int i=0;i<20;i++) {
            assertEquals(predictor, plant.getStateDerivativeSnapshot());
            assertEquals(state, plant.getIntegrationStates());
        }
        plant.step(.001, .8, .2, .94, 1.0, 1);
        assertEquals(com.interpss.dstab.controller.runtime.IntegrationStateMonitor.Stage.CORRECTOR,
                plant.getStateDerivativeSnapshot().stage());
        assertEquals(1.0, predictor.evaluationStates().get("Measured Reactive Or Voltage"), 0.0);
        plant.initialize(.8, .2, 1.0);
        assertTrue(plant.getStateDerivativeSnapshot().rates().isEmpty());
    }

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
        assertEquals(Set.of("Measured Active Power", "Measured Reactive Or Voltage",
                "Active Control Integral", "Reactive Control Integral",
                "Reactive Control Output", "Lead Lag", "Active Power Lag",
                "Active Power Command", "Reactive Command"),
                plant.getNamedStates().keySet());
        assertEquals(plant.getPref(), plant.getNamedState("Active Power Command"), 0.0);
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
    void zeroBranchFallbackUsesGeneratorPowerAndSelectedBusVoltage() throws Exception {
        MeasurementFixture fixture = measurementFixture();
        Repca1Model plant = new Repca1Model(
                plantData(2, 0, 0, 0, 1, 0, 0, 0, 0), fixture.converter());

        plant.initialize(.8, .2, 1.0);

        assertEquals(.8, plant.getMeasuredActivePower(), 1.0e-12);
        assertEquals(.96, plant.getMeasuredReactiveOrVoltage(), 1.0e-12);
        assertEquals(true, plant.isUsingZeroBranchFallback());

        Repca1Model compensated = new Repca1Model(
                plantData(2, 0, 0, 0, 1, 0, 0, .5, 0), fixture.converter());
        compensated.initialize(.8, .2, 1.0);
        assertEquals(.96 + .5 * .2, compensated.getMeasuredReactiveOrVoltage(), 1.0e-12,
                "Kc compensation must use generator reactive power");
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
    void numericalPartitionResidualDoesNotMoveInitializedPlantStates() {
        Repca1Model plant = new Repca1Model(dynamicData(0, 1, .1, 2, 1, .05, .1,
                .7, 1, -1, 2, 1, .1, 10, 20));
        plant.initialize(.8, .2, 1.0);

        plant.step(.01, .8 + 4.0e-9, .2 - 3.0e-9, 1.0 - 5.0e-9,
                1.0 + 2.0e-9);

        assertEquals(.8, plant.getMeasuredActivePower(), 0.0);
        assertEquals(.2, plant.getMeasuredReactiveOrVoltage(), 0.0);
        assertEquals(0.0, plant.getPref(), 0.0);
        assertEquals(0.0, plant.getQref(), 0.0);

        plant.step(.01, .8, .2 - 2.0e-8, 1.0, 1.0);
        assertTrue(plant.getMeasuredReactiveOrVoltage() < .2,
                "finite disturbances above numerical resolution must remain observable");
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

    private static Repca1Data withBranch(Repca1Data d,int from,int to,String id) {
        return new Repca1Data(d.remoteBus(),from,to,id,d.vcFlag(),d.refFlag(),d.fFlag(),
                d.tfltr(),d.kp(),d.ki(),d.tft(),d.tfv(),d.vfrz(),d.rc(),d.xc(),d.kc(),
                d.emax(),d.emin(),d.dbd1(),d.dbd2(),d.qmax(),d.qmin(),d.kpg(),d.kig(),d.tp(),
                d.fdbd1(),d.fdbd2(),d.femax(),d.femin(),d.pmax(),d.pmin(),d.tg(),d.ddn(),d.dup(),d.puFlag());
    }
}
