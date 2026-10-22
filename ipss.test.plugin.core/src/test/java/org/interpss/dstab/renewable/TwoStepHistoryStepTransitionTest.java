package org.interpss.dstab.renewable;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

/** Independent scalar-lag evidence for the binary32 retained-base recurrence. */
class TwoStepHistoryStepTransitionTest {
    @Test
    void stepChangesRetainTheIntegrationBaseAndResetStartsFresh() {
        // Two cold reference runs: T=0.02 s, fixed input 1 pu, forced initial
        // STATE/STORE=0.9 pu and zero initial rate. The private evidence index is
        // reference-startup-transition-2026-10-22.json. These are state values,
        // not fitted tolerances or a claim about full-network transition accuracy.
        double[] steps = {.001, .001, 1. / 240, 1. / 240, 1. / 240, .001, .001, .001};
        double[] expected = {
            0.9074999690055847, 0.9119374752044678, 0.9371445178985596,
            0.9476137161254883, 0.9574369788169861, 0.9551722407341003,
            0.9574702382087708, 0.9595392942428589
        };
        var history = new TwoStepHistory(1);
        history.setSinglePrecision(true);
        for (int repeat = 0; repeat < 2; repeat++) {
            history.reset();
            double state = (float) .9;
            for (int i = 0; i < steps.length; i++) {
                float rate = (1.0f - (float) state) / .02f;
                history.begin((float) steps[i]);
                state = history.advance(0, state, rate);
                assertEquals(expected[i], state, 0., "step " + i + ", repeat " + repeat);
            }
        }
    }
}
