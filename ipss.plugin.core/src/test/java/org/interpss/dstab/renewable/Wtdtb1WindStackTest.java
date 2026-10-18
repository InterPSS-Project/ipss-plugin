package org.interpss.dstab.renewable;

import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Test;

class Wtdtb1WindStackTest {
    private static WindControlStack stack(boolean aero, boolean controllers) {
        var stack = new WindControlStack();
        stack.setTorsionalEmulation(new Wtdtb1Model(
                new Wtdtb1Data(4.5, 1.5, .6, 2, .4, .1), 60));
        if (aero) stack.setAerodynamics(new Wtara1Model(new Wtara1Data(.007, 0)));
        if (controllers) {
            stack.setTorqueController(new Wttqa1Model(new Wttqa1Data(1, 2, 1, .02, .1,
                    2, 0, 0, .8, .3, .9, .6, 1, 1, 1.1, 100)));
            stack.setPitchController(new Wtpta1Model(new Wtpta1Data(1, 2, 1, 2, 0,
                    .1, 30, 0, 5, -5)));
        }
        return stack;
    }

    @Test void emulationIgnoresConfiguredPitchAndTorqueControllersWithoutAerodynamics() {
        var configured = stack(false, true);
        var bare = stack(false, false);
        configured.initialize(.24);
        bare.initialize(.24);
        assertNotNull(configured.getTorqueController());
        assertFalse(configured.isTorqueControllerActive());
        assertEquals(1, configured.getGeneratorSpeed(), 0);
        for (int i = 0; i < 20; i++) {
            configured.step(.001, .12, .8, .9, false);
            bare.step(.001, .12, .8, .9, false);
        }
        assertArrayEquals(bare.getTorsionalEmulation().getNativeStates(),
                configured.getTorsionalEmulation().getNativeStates(), 0);
        assertEquals(bare.getPref(), configured.getPref(), 0);
        assertEquals(configured.getGeneratorSpeed(), configured.getElectricalControllerSpeed(), 0);
    }

    @Test void aerodynamicFallbackReactivatesConfiguredMechanicalControllers() {
        var stack = stack(true, true);
        stack.initialize(.24);
        assertTrue(stack.isTorqueControllerActive());
        assertTrue(stack.getTorsionalEmulation().isAerodynamicPresent());
        assertEquals(0, stack.getTorsionalEmulation().getFilteredElectricalPower(), 0);
        assertEquals(1, stack.getElectricalControllerSpeed(), 0);
        stack.step(.001, .12, .24, .24, false, 0);
        stack.step(.001, .12, .24, .24, false, 1);
        assertTrue(Double.isFinite(stack.getGeneratorSpeed()));
        assertEquals(0, stack.getTorsionalEmulation().getFilteredElectricalPower(), 0);
    }

    @Test void stagedAndCompleteEmulationHaveIdenticalMechanicalStates() {
        var complete = stack(false, true);
        var staged = stack(false, true);
        complete.initialize(.24);
        staged.initialize(.24);
        for (int i = 0; i < 20; i++) {
            complete.step(.001, .12, .24, .24, false);
            staged.step(.001, .12, .24, .24, false, 0);
            staged.step(.001, .12, .24, .24, false, 1);
        }
        assertArrayEquals(complete.getTorsionalEmulation().getNativeStates(),
                staged.getTorsionalEmulation().getNativeStates(), 0);
    }

    @Test void duplicateOrMixedDriveTrainAttachmentsAreRejected() {
        var emulation = stack(false, false);
        var legacy = new Wtdta1Model(new Wtdta1Data(4.5, 1.5, .6, .02, .4));
        assertThrows(IllegalStateException.class, () -> emulation.setDriveTrain(legacy));
        assertThrows(IllegalStateException.class,
                () -> emulation.setTorsionalEmulation(emulation.getTorsionalEmulation()));
        var stack = new WindControlStack();
        stack.setDriveTrain(legacy);
        assertThrows(IllegalStateException.class,
                () -> stack.setTorsionalEmulation(emulation.getTorsionalEmulation()));
    }
}
