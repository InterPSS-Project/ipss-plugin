package org.interpss.core.adapter.builder.dstab;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.interpss.dstab.renewable.Reecb1Data;
import org.interpss.dstab.renewable.Reecb1Model;
import org.junit.jupiter.api.Test;

/** Independent voltage-reference and feedback discriminators for the electrical cascade. */
public class Reecb1VoltageControlTest {
    private static final double STEP = .001;

    private static Reecb1Data data(int voltageFlag, double qProportional) {
        // Zero measurement lags and a single small integration step isolate the
        // two PI summing junctions. Voltage limits deliberately exclude zero;
        // Q limits exclude the initialized voltage reference in direct-V mode.
        return new Reecb1Data(0, 0, voltageFlag, 1, 0,
                -99, 99, 0, 0, 0, 0, 10, -10, 0, 0,
                .44, -.44, 1.1, .9, qProportional, .1, 0, 2, 0,
                99, -99, 1, 0, 10, 0);
    }

    @Test
    void coordinatedReactiveControlInitializesAnAbsoluteVoltageReference() {
        var model = new Reecb1Model(data(1, 0));
        model.initialize(.8, .2, 1.03);
        assertEquals(1.03, model.getQControlIntegral(), 1e-12);
        model.step(STEP, .8, .2, 1.03, 1);
        assertEquals(.2 / 1.03, model.getReactiveCurrentState(), 1e-12);
    }

    @Test
    void measuredVoltageFeedbackRespondsInBothDirectionsWithoutChangingQTarget() {
        var model = new Reecb1Model(data(1, 0));
        model.initialize(.8, .2, 1);
        model.step(STEP, .8, .2, 1.01, 1);
        assertEquals(.2 - 2 * .01 * STEP, model.getReactiveCurrentState(), 1e-12);
        model.step(STEP, .8, .2, .99, 1);
        assertEquals(.2, model.getReactiveCurrentState(), 1e-12);
    }

    @Test
    void directVoltageReferenceBypassesReactivePowerLimits() {
        var model = new Reecb1Model(data(0, 0));
        model.initialize(.8, .2, 1.03);
        model.step(STEP, .8, .2, 1.04, 1);
        assertEquals(.2 / 1.03 - 2 * .01 * STEP, model.getReactiveCurrentState(), 1e-12);
        assertEquals(0, model.getQControlIntegral(), 1e-12,
                "The bypassed outer PI is not integrated");
    }

    @Test
    void positiveMinimumVoltageIsNotReinterpretedAsAZeroBasedBias() {
        var source = data(1, 1);
        var model = new Reecb1Model(source);
        model.initialize(.8, .2, 1);
        model.step(STEP, .8, .4, 1, 1);
        assertEquals(.2 - 2 * .1 * STEP, model.getReactiveCurrentState(), 1e-12);
        assertEquals(.9, source.vmin(), 0, "Entered physical voltage bounds remain unchanged");
    }
}
