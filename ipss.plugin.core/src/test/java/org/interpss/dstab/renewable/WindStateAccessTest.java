package org.interpss.dstab.renewable;

import static org.junit.jupiter.api.Assertions.*;
import com.interpss.dstab.controller.runtime.IntegrationStateMonitor.Stage;
import org.junit.jupiter.api.Test;

class WindStateAccessTest {
    @Test void torqueRatesRemainDetachedAcrossRefreshAndReset() {
        var model = new Wttqa1Model(new Wttqa1Data(1, 1, 2, .1, .2, 2, -2,
                0, 0, 0, 0, 0, 0, 0, 0, 100));
        model.initialize(.8, 1);
        assertTrue(model.getStateDerivativeSnapshot().rates().isEmpty());
        model.stepTwoStep(.001, .7, 1, .8, false);
        var snapshot = model.getStateDerivativeSnapshot();
        assertEquals(Stage.TWO_STEP, snapshot.stage());
        assertEquals(-1, snapshot.rates().get("Filtered Power"), 1.e-12);
        var current = model.getIntegrationStates();
        for (int i=0;i<10;i++) {
            assertEquals(snapshot, model.getStateDerivativeSnapshot());
            assertEquals(current, model.getIntegrationStates());
        }
        assertThrows(UnsupportedOperationException.class, () -> snapshot.evaluationStates().clear());
        model.stepTwoStep(0, .6, 1, .8, false);
        assertEquals(snapshot, model.getStateDerivativeSnapshot());
        model.initialize(.8, 1);
        assertTrue(model.getStateDerivativeSnapshot().rates().isEmpty());
    }

    @Test void pitchExposesClampedRateAndAllThreeCoordinates() {
        var model = new Wtpta1Model(new Wtpta1Data(2, 1, 2, 1, 0, .1, 30, 0, .5, -.5));
        model.initialize(1, 1);
        assertTrue(model.getStateDerivativeSnapshot().rates().isEmpty());
        model.stepTwoStep(.001, .8, .8, 1.1);
        var snapshot = model.getStateDerivativeSnapshot();
        assertEquals(3, snapshot.rates().size());
        assertEquals(.5, snapshot.rates().get("Pitch"), 1.e-12);
        assertEquals(.2, snapshot.rates().get("Speed Integral"), 1.e-12);
        assertEquals(1, snapshot.evaluationStates().get("Pitch"), 0.0);
        var current = model.getIntegrationStates();
        assertEquals(snapshot, model.getStateDerivativeSnapshot());
        assertEquals(current, model.getIntegrationStates());
        model.initialize(1, 1);
        assertTrue(model.getStateDerivativeSnapshot().rates().isEmpty());
    }

    @Test void driveTrainExposesOnlyDifferentialCoordinatesForEachMassMode() {
        for (double fraction : new double[]{0, .5}) {
            var model = new Wtdta1Model(new Wtdta1Data(4, 0, fraction, 1, 0));
            model.initialize(.8, 1);
            assertTrue(model.getStateDerivativeSnapshot().rates().isEmpty());
            model.stepTwoStep(.001, .9, .8);
            var snapshot = model.getStateDerivativeSnapshot();
            assertEquals(fraction==0 ? 1 : 3, snapshot.rates().size());
            assertEquals(snapshot.rates().keySet(), model.getIntegrationStates().keySet());
            assertEquals(fraction==0 ? .1/8 : 0,
                    snapshot.rates().get("Generator Speed"), 1.e-12);
            if (fraction>0) assertEquals(.1/4, snapshot.rates().get("Turbine Speed"), 1.e-12);
            var current = model.getIntegrationStates();
            assertEquals(snapshot, model.getStateDerivativeSnapshot());
            assertEquals(current, model.getIntegrationStates());
            model.initialize(.8, 1);
            assertTrue(model.getStateDerivativeSnapshot().rates().isEmpty());
        }
    }
}
