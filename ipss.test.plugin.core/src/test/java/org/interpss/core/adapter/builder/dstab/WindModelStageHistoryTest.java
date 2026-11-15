package org.interpss.core.adapter.builder.dstab;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.interpss.dstab.mach.model.Gewtptu1Data;
import com.interpss.dstab.mach.model.Gewtptu1Model;
import com.interpss.dstab.mach.model.Wt12a1Data;
import com.interpss.dstab.mach.model.Wt12a1Model;
import com.interpss.dstab.mach.model.Wt12t1Data;
import com.interpss.dstab.mach.model.Wt12t1Model;
import com.interpss.dstab.mach.model.Wt2e1Data;
import com.interpss.dstab.mach.model.Wt2e1Model;
import com.interpss.dstab.mach.model.Wt2g1Data;
import org.junit.jupiter.api.Test;

class WindModelStageHistoryTest {
    private static final double STEP = .002;

    @Test
    void shaftDiscardsPredictorWhenReinitialized() {
        var model = new Wt12t1Model(new Wt12t1Data(5.4, .05, .82, 4.7, .9));
        model.initialize(.5, 1.0, 60);
        model.step(STEP, .4, 0);
        model.initialize(.6, 1.01, 60);
        var initial = model.getNamedStates();

        assertThrows(IllegalStateException.class, () -> model.step(STEP, .6, 1));
        assertEquals(initial, model.getNamedStates());
        model.step(STEP, .6, 0);
        model.step(STEP, .6, 1);
        assertEquals(initial, model.getNamedStates());
    }

    @Test
    void aerodynamicControllerDiscardsPredictorWhenReinitialized() {
        var model = new Wt12a1Model(new Wt12a1Data(.1, 2, .2, .1, .1, .1, 1, 0));
        model.initialize(.5, 0, .5);
        model.step(STEP, .4, .03, 0);
        model.initialize(.6, .01, .6);
        var initial = model.getNamedStates();

        assertThrows(IllegalStateException.class, () -> model.step(STEP, .6, .01, 1));
        assertEquals(initial, model.getNamedStates());
        model.step(STEP, .6, .01, 0);
        model.step(STEP, .6, .01, 1);
        assertEquals(initial, model.getNamedStates());
    }

    @Test
    void pitchControllerDiscardsPredictorWhenReinitialized() {
        var model = new Gewtptu1Model(new Gewtptu1Data(.2, 12, 3, 2, 1.5, 0, 20, -5, 4, .7));
        model.initialize(4, 1.0);
        model.step(STEP, 1.04, .8, 0);
        model.initialize(5, 1.01);
        var initial = model.getNamedStates();

        assertThrows(IllegalStateException.class, () -> model.step(STEP, 1.01, .7, 1));
        assertEquals(initial, model.getNamedStates());
        model.step(STEP, 1.01, .7, 0);
        model.step(STEP, 1.01, .7, 1);
        assertEquals(initial, model.getNamedStates());
    }

    @Test
    void rotorResistanceControllerDiscardsPredictorWhenReinitialized() {
        var machine = new Wt2g1Data(.14, 6.5, .2, .005, .12, 1.02, 0, 1.22, 0,
                new double[]{0, .15, .45, .75, .95}, new double[]{0, .006, .025, .055, .11});
        var model = new Wt2e1Model(new Wt2e1Data(.05, .05, 2, .1, .15, .05), machine);
        model.initialize(.025, .45, .1);
        model.step(STEP, .035, .5, 0);
        model.initialize(.055, .75, .11);
        var initial = model.getNamedStates();

        assertThrows(IllegalStateException.class, () -> model.step(STEP, .055, .75, 1));
        assertEquals(initial, model.getNamedStates());
        model.step(STEP, .055, .75, 0);
        model.step(STEP, .055, .75, 1);
        assertEquals(initial, model.getNamedStates());
    }
}
