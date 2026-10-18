package org.interpss.dstab.renewable;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

class RenewableTwoStepIntegrationTest {
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
