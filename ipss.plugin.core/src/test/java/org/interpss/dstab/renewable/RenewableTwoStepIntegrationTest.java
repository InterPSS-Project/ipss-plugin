package org.interpss.dstab.renewable;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

class RenewableTwoStepIntegrationTest {
    @Test
    void discontinuityAveragesOnceAndRetainsTheResultForTheFollowingStep() {
        for (boolean single : new boolean[]{false, true}) {
            var history = new TwoStepHistory(1);
            history.setSinglePrecision(single);
            history.begin(.1);
            double x = history.advance(0, 1., 2.);
            history.rebaseNextStep();
            history.rebaseNextStep();
            assertThrows(IllegalArgumentException.class, () -> history.begin(0.));
            history.begin(.1);
            x = history.advance(0, x, 6.);
            assertEquals(1.8, x, single ? 3.e-7 : 1.e-14);
            history.begin(.1);
            assertEquals(2.5, history.advance(0, x, 6.), single ? 3.e-7 : 1.e-14);
            history.rebaseNextStep();
            history.reset();
            history.begin(.1);
            assertEquals(1.3, history.advance(0, 1., 2.), single ? 1.e-7 : 1.e-14);
        }
    }

    @Test
    void eventFilterMatchesTheArchivedFirstPositiveStateWithoutStateForcing() {
        // Independent archived state-step profile, including its observed terminal
        // voltage. Evidence: 2026-10-18-case2-converter-boundaries, eight cold runs.
        var history = new TwoStepHistory(1);
        history.setSinglePrecision(true);
        history.rebaseNextStep();
        history.begin(.00025);
        assertEquals(1.0019669532775879,
                history.advance(0, 1., (1.20981049538-1.)/.02), 0.);
    }

    @Test
    void heldStateDiscardsHistoryWithoutInjectingAnAveragedRate() {
        var history = new TwoStepHistory(1);
        var monitor = new org.interpss.dstab.control.util.IntegrationStateMonitor("state");
        history.begin(.1);
        double x = history.advance(0, 1., 2.);
        history.rebaseNextStep();
        history.begin(.1);
        monitor.record(0, x, 9.);
        assertEquals(x, history.hold(0, x), 0.);
        history.observeRates(monitor);
        var snapshot = monitor.snapshot();
        assertEquals(org.interpss.dstab.control.util.IntegrationStateMonitor.Stage.TWO_STEP_EVENT,
                snapshot.stage());
        assertEquals(0., snapshot.rates().get("state"), 0.);
        assertEquals(x, snapshot.evaluationStates().get("state"), 0.);
        history.begin(.1);
        assertEquals(x+.9, history.advance(0, x, 6.), 1.e-14);
        assertEquals(0., snapshot.rates().get("state"), 0.);
    }

    @Test
    void activeCommandUsesTheCurrentLimitComputedBeforeTheStateAdvance() {
        Reeca1Model electrical=limitController(1,0.,2.,1.,0.,.1);
        electrical.initialize(.8,.5,1.);
        electrical.stepTwoStep(.01,.8,.5,.5,1.);

        assertEquals(.515,electrical.getVoltageControlIntegral(),1.e-14);
        assertEquals(Math.sqrt(1.-.5*.5),electrical.getTwoStepIpcmd(),1.e-14,
                "the converter consumes the pre-advance current limit with advanced Pord/V");
        assertEquals(Math.sqrt(1.-.515*.515),electrical.getIpcmd(),1.e-14);
        electrical.refreshTwoStep(.8,.5,.5,1.);
        assertEquals(Math.sqrt(.75),electrical.getTwoStepIpcmd(),1.e-14);
    }

    @Test
    void downstreamPiSaturationKeepsTheUpstreamIntegralFrozen() {
        for(double voltage:new double[]{.5,1.5}) {
            Reeca1Model electrical=limitController(1,1.,50.,1.3,0.,.1);
            electrical.initialize(.5,0.,1.);
            double measuredQ=voltage>1. ? .2 : -.2;
            for(int i=0;i<200;i++) electrical.stepTwoStep(.001,.5,measuredQ,voltage,1.);
            assertTrue(Math.abs(electrical.getVoltageControlIntegral())>=1.3);
            double integral=electrical.getReactiveControlIntegral();
            assertNotEquals(1.,integral);
            for(int i=0;i<200;i++) electrical.stepTwoStep(.001,.5,measuredQ,voltage,1.);
            assertEquals(integral,electrical.getReactiveControlIntegral(),1.e-14,
                    "zeroing the downstream limited derivative must not release upstream anti-windup");
        }
    }

    @Test
    void algebraicRefreshDoesNotStartThePostDipHoldEarly() {
        Reeca1Model electrical=limitController(0,0.,0.,1.,.02,.85);
        electrical.initialize(.8,0.,1.);
        electrical.stepTwoStep(.01,.8,0.,.7,1.);
        assertEquals(0.,electrical.getActiveCurrentLimit(),0.);
        for(int i=0;i<10;i++) electrical.refreshTwoStep(.8,0.,1.,1.);

        electrical.stepTwoStep(.01,.8,0.,1.,1.);
        electrical.stepTwoStep(.01,.8,0.,1.,1.);
        assertEquals(0.,electrical.getActiveCurrentLimit(),0.,
                "the hold starts when integration observes clearing, regardless of refresh count");
        electrical.stepTwoStep(.01,.8,0.,1.,1.);
        assertEquals(1.,electrical.getActiveCurrentLimit(),0.);
    }

    private static Reeca1Model limitController(int qFlag,double kqi,double kvi,
            double imax,double holdTime,double vdip) {
        return new Reeca1Model(new Reeca1Data(
                0,0,1,qFlag,0,0,
                vdip,2.,0.,0.,0.,5.,1.1,-1.1,0.,0.,0.,holdTime,.5,2.,-2.,
                10.,-10.,0.,kqi,0.,kvi,0.,.02,99.,-99.,1.,0.,imax,.02,
                0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0),null);
    }

    @Test
    void converterCommandRetainsTheCompletedStepsProportionalInputs() {
        Reeca1Model electrical=new Reeca1Model(new Reeca1Data(
                0,0,1,1,0,0,
                .85,1.15,.02,0,0,5,1.1,-1.1,0,0,0,0,.5,.436,-.436,
                1.1,.9,1,2,.5,3,0,.02,99,-99,1,0,1.3,.02,
                0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0),null);
        electrical.initialize(.5,0.,1.);
        electrical.stepTwoStep(.001,.5,.05,1.,1.);

        assertEquals(.99985,electrical.getReactiveControlIntegral(),1.e-14);
        assertEquals(-.000225,electrical.getVoltageControlIntegral(),1.e-14);
        assertEquals(.0253,electrical.getTwoStepIqcmd(),1.e-14);
        electrical.refreshTwoStep(.5,0.,1.,1.);
        assertEquals(.0003,electrical.getIqcmd(),1.e-14);
        assertEquals(.0253,electrical.getTwoStepIqcmd(),1.e-14,
                "a network refresh must not replace the command consumed by the next converter step");
        assertEquals(.5,electrical.getTwoStepIpcmd(),1.e-14);
    }

    @Test
    void singlePrecisionStoreDoesNotAccumulateSubResolutionIntegralMotion() {
        TwoStepHistory nativeStorage=new TwoStepHistory(1);
        TwoStepHistory doubleStorage=new TwoStepHistory(1);
        nativeStorage.setSinglePrecision(true);
        double nativeState=1.,doubleState=1.;
        for (int i=0;i<500;i++) {
            nativeStorage.begin(.0005);doubleStorage.begin(.0005);
            nativeState=nativeStorage.advance(0,nativeState,1.e-5);
            doubleState=doubleStorage.advance(0,doubleState,1.e-5);
        }
        assertEquals(1.,nativeState,0.);
        assertEquals(1.+500.5*.0005*1.e-5,doubleState,2.e-14);
    }

    @Test
    void incrementalApiRetainsAbsoluteReactiveReferenceStorageResolution() {
        TwoStepHistory storage=new TwoStepHistory(1);
        storage.setSinglePrecision(true);
        double initialReference=.11453402366925415;
        storage.setOffset(0,initialReference);
        double increment=0.;
        for (int i=0;i<100;i++) {
            storage.begin(.0005);
            increment=storage.advance(0,increment,2.e-7);
        }
        assertEquals((double)(float)initialReference,increment+initialReference,0.);
        double held=storage.hold(0,increment);
        storage.begin(.0005);
        assertEquals(held,storage.advance(0,held,0.),0.);
    }
    @Test
    void windComponentsSampleTheSameOldSpeedReferenceAndMechanicalPower() {
        WindControlStack wind = new WindControlStack();
        Wtdta1Model drive = new Wtdta1Model(new Wtdta1Data(5, .1, .8, 1.5, .2));
        Wttqa1Model torque = new Wttqa1Model(new Wttqa1Data(
                1, 1, .5, .04, 60, 1.2, 0, .2, .58, .4, .72, .6, .86, .8, 1, 0));
        Wtpta1Model pitch = new Wtpta1Model(new Wtpta1Data(1, 2, 3, 4, .5, 1, 20, 0, 100, -100));
        wind.setDriveTrain(drive);
        wind.setTorqueController(torque);
        wind.setPitchController(pitch);
        wind.setAerodynamics(new Wtara1Model(new Wtara1Data(.007, 0)));
        wind.initialize(.5);
        double oldSpeed = wind.getGeneratorSpeed();
        double oldIntegral = torque.getTorqueIntegral();

        wind.stepTwoStep(.02, .4, .7, .6, false);

        assertEquals(.79, oldSpeed, 1.e-14);
        assertEquals(.425, torque.getFilteredPower(), 1.e-14);
        assertEquals(oldIntegral + .03 * .5 * (.6 - .5) / oldSpeed,
                torque.getTorqueIntegral(), 1.e-14,
                "torque error uses old filtered power and old drive speed");
        assertEquals(.003, pitch.getSpeedIntegral(), 1.e-14);
        assertEquals(.018, pitch.getCompensationIntegral(), 1.e-14);
        assertEquals(.03, pitch.getPitch(), 1.e-14,
                "pitch error uses old torque Pref, rather than the advanced command");
        assertEquals(oldSpeed + .03 * (.5 - .4) / oldSpeed / 2.,
                drive.getGeneratorSpeed(), 1.e-14);
        assertEquals(oldSpeed, drive.getTurbineSpeed(), 1.e-14,
                "drive uses old aerodynamic power before pitch changes it");
        assertEquals(torque.getTorque() * drive.getGeneratorSpeed(), wind.getPref(), 1.e-14);
    }

    @Test
    void bypassedTorqueFiltersRemainAlgebraicAndDipFreezesOnlyIntegral() {
        Wttqa1Model torque = new Wttqa1Model(new Wttqa1Data(
                1, 1, .5, 0, 0, 10, 0, .2, .58, .4, .72, .6, .86, .8, 1, 0));
        torque.initialize(.5, 1.0);
        torque.stepTwoStep(.01, .4, 1.0, .6, true);
        assertEquals(.4, torque.getFilteredPower(), 0.0);
        assertEquals(.72, torque.getSpeedReference(), 1.e-14);
        assertEquals(.5, torque.getTorqueIntegral(), 0.0);
        assertEquals(.7, torque.getPref(), 1.e-14,
                "frozen integrator does not freeze the live proportional path");
    }

    @Test
    void plantLagAndPiAreEvaluatedBeforeEitherStateAdvances() {
        Repca1Model plant = new Repca1Model(new Repca1Data(
                0, 0, 0, "0", 0, 0, 0,
                .1, 2, 3, 0, .2, .7, 0, 0, 0,
                1, -1, 0, 0, 2, -2, 0, 0, .1, 0, 0, 1, -1, 2, -2, .2, 20, 0, 0));
        plant.initialize(.5, .1, 1.0);
        plant.stepTwoStep(.01, .5, .2, 1.0, 1.0);
        assertEquals(.0, plant.getReactiveControlIntegral(), 1.e-14,
                "initial Q error is zero before the measurement lag advances");
        assertEquals(-.03, plant.getReactiveControlOutput(), 1.e-14);
    }
}
