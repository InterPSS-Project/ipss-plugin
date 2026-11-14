package org.interpss.dstab.renewable;

import static org.junit.jupiter.api.Assertions.*;

import com.interpss.dstab.controller.runtime.IntegrationStateMonitor;
import com.interpss.dstab.controller.runtime.IntegrationStateMonitor.Stage;
import org.junit.jupiter.api.Test;

class RenewableEventFreezeHistoryTest {
    @Test
    void eventFreezeResetsStorageButPreservesTheRateForEventAveraging() {
        for (double offset : new double[]{0., .3}) {
            var history = new TwoStepHistory(1);
            history.setSinglePrecision(true);
            history.setOffset(0, offset);
            history.begin(.001);
            double x = history.advance(0, 1. - offset, -2.);
            assertEquals(.997 - offset, x, 1.e-7);

            history.rebaseNextStep();
            history.rebaseNextStep();
            history.begin(.001);
            var monitor = new IntegrationStateMonitor("coordinate");
            monitor.record(0, x, 0.);
            double frozen = history.hold(0, x);
            history.observeRates(monitor);

            // STORE is anchored at the current state. The half old/half zero
            // event rate then gives a 1.5*h*(-1) first-step change.
            assertEquals(.9955 - offset, frozen, 1.e-7);
            var event = monitor.snapshot();
            assertEquals(Stage.TWO_STEP_EVENT, event.stage());
            assertEquals(-1., event.rates().get("coordinate"), 0.);
            assertEquals(x, event.evaluationStates().get("coordinate"), 0.);

            history.begin(.001);
            assertEquals(frozen, history.hold(0, frozen), 0.);
            history.observeRates(monitor);
            assertEquals(Stage.TWO_STEP, monitor.snapshot().stage());
            assertEquals(0., monitor.snapshot().rates().get("coordinate"), 0.);
            assertEquals(-1., event.rates().get("coordinate"), 0.);

            history.begin(.001);
            assertEquals(.9985 - offset, history.advance(0, frozen, 2.), 1.e-7);
        }
    }

    @Test
    void ordinaryFreezeStillHoldsTheStateAndClearsTheRate() {
        var history = new TwoStepHistory(1);
        history.setSinglePrecision(true);
        history.begin(.001);
        double x = history.advance(0, 1., -2.);
        history.begin(.001);
        assertEquals(x, history.hold(0, x), 0.);
        history.rebaseNextStep();
        history.begin(.001);
        assertEquals(x, history.hold(0, x), 0.,
                "a preceding ordinary freeze leaves no rate to average");
    }

    @Test
    void eventHoldAveragesOnlyTheRateBelongingToItsCoordinate() {
        var history = new TwoStepHistory(2);
        history.setSinglePrecision(true);
        history.begin(.001);
        double x = history.advance(0, 1., -2.);
        double y = history.advance(1, 2., 4.);
        history.rebaseNextStep();
        history.begin(.001);
        assertEquals(.9955, history.hold(0, x), 1.e-7);
        assertEquals(2.009, history.hold(1, y), 3.e-7);
    }

    @Test
    void resetDiscardsPendingEventsAndTheirPreviousRates() {
        var history = new TwoStepHistory(1);
        history.setSinglePrecision(true);
        history.begin(.001);
        history.advance(0, 1., -2.);
        history.rebaseNextStep();
        history.reset();
        history.begin(.001);
        assertEquals(.5, history.hold(0, .5), 0.);
    }
}
