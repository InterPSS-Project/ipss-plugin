package org.interpss.dstab.renewable;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

/** Recovery headroom belongs to the published command's integration boundary. */
class ReecaRecoveryHoldTest {
    @Test
    void endpointRefreshCannotReplaceThePublishedRecoveryHeadroom() {
        for (boolean single : new boolean[]{false, true}) {
            var model = controller(single);
            model.stepTwoStep(.001, .625, .024, .8, 1.);
            var states = model.getIntegrationStates();
            var memory = model.getControlMemory();
            double iq = memory.get("Published Reactive Command");
            assertEquals(Math.sqrt(1.3 * 1.3 - iq * iq),
                    memory.get("Held Active Current Limit"), 1e-14);

            for (double q : new double[]{.2, -.1, .3, .024}) {
                model.refreshTwoStep(.625, q, .8, 1.);
                assertEquals(states, model.getIntegrationStates());
                assertEquals(memory, model.getControlMemory(),
                        "Network refresh may update outputs, but not the retained recovery command");
            }
        }
    }

    @Test
    void recoveryUsesTheLastPublishedHeadroomAcrossAnotherDip() {
        var model = controller(true);
        model.stepTwoStep(.001, .625, .024, .8, 1.);
        double held = model.getControlMemory().get("Held Active Current Limit");
        model.refreshTwoStep(.625, .2, .8, 1.);
        model.refreshTwoStep(.625, -.1, 1., 1.);

        model.stepTwoStep(.001, .625, -.1, 1., 1.);
        assertEquals(held, model.getActiveCurrentLimit(), 0.);
        assertEquals((double) (float) .5, model.getControlMemory().get("Post Dip Timer"), 0.);

        model.stepTwoStep(.001, .625, .3, .8, 1.);
        assertEquals(held, model.getControlMemory().get("Held Active Current Limit"), 0.);
        double remaining = model.getControlMemory().get("Post Dip Timer");
        assertTrue(remaining < .5);
        model.refreshTwoStep(.625, -.2, 1., 1.);
        model.stepTwoStep(.001, .625, -.2, 1., 1.);
        assertEquals(held, model.getActiveCurrentLimit(), 0.);
        assertTrue(model.getControlMemory().get("Post Dip Timer") < remaining,
                "A second recovery must not restart an active hold");
    }

    @Test
    void reinitializationDiscardsThePriorRecoveryHold() {
        var model = controller(false);
        model.stepTwoStep(.001, .625, .024, .8, 1.);
        model.stepTwoStep(.001, .625, .024, 1., 1.);
        assertTrue(model.getControlMemory().get("Post Dip Timer") > 0.);
        model.initialize(.625, .024, 1.);
        assertEquals(0., model.getControlMemory().get("Post Dip Timer"), 0.);
        assertEquals(1.3, model.getControlMemory().get("Held Active Current Limit"), 0.);
    }

    @Test
    void defaultStagedPathStillCapturesItsOwnAlgebraicCurrentLimit() {
        var model = controller(false);
        model.step(.001, .625, .024, .8, 1., 0);
        double old = model.getControlMemory().get("Held Active Current Limit");
        model.step(0., .625, .2, .8, 1., 1);
        assertNotEquals(old, model.getControlMemory().get("Held Active Current Limit"));
        assertEquals(model.getActiveCurrentLimit(),
                model.getControlMemory().get("Held Active Current Limit"), 0.);
    }

    private static Reeca1Model controller(boolean single) {
        var model = Reeca1Model.fromNativeRecord(new Reeca1Data(
                0, 0, 1, 1, 1, 0,
                .85, 1.15, .02, 0, 0, 5, 1.1, -1.1, 0, 0, 0, .5, .02,
                .436, -.436, 1.1, .9, 2.4, 2.8, 2.4, 2.9, 0, .02,
                99, -99, 1, 0, 1.3, .03,
                0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0), null);
        model.setSinglePrecisionStates(single);
        model.initialize(.625, .024, 1.);
        return model;
    }
}
