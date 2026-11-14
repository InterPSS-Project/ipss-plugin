package org.interpss.core.dstab;

import static org.junit.jupiter.api.Assertions.*;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.interpss.dstab.relay.*;
import com.interpss.dstab.DStabGen;
import com.interpss.dstab.algo.DynamicSimuMethod;

/** Accepted-step protection timing, independent stage rounding and event-side invariance. */
class GeneratorTripRelayTimingTest {
    static Stream<Arguments> recordedTiming() {
        // Independent small-network onset experiments: source values are kept
        // unchanged; the expected integer is the observed number of accepted
        // intervals after the imposed violation, not a recomputed rounding rule.
        double[][] cases = {
            {.0033333,.02,0,6}, {.0033333,.02001,0,6},
            {.0033333,.021,0,6}, {.0033333,.0217,0,7},
            {.0033333,.15,0,45}, {.001,.02,0,20},
            {.001,.0204,0,20}, {.001,.0206,0,21},
            {.001,.0204,.0204,40}, {.001,.0206,.0206,42},
            {.001,.0004,0,1}, {.001,.0006,0,1},
            {.001,.02,.0004,21}, {.001,0,.02,20}
        };
        return Stream.of(false,true).flatMap(frequency -> Stream.of(cases)
                .map(row -> Arguments.of(frequency,row[0],row[1],row[2],(int)row[3])));
    }

    private AbstractGeneratorTripRelayModel relay(boolean frequency, double pickup, double breaker) throws Exception {
        var net=com.interpss.simu.util.sample.SampleDStabTestingCases.sample3BusDStab_StaticLoads();
        net.setFrequency(60.0);
        assertTrue(com.interpss.core.LoadflowAlgoObjectFactory.createLoadflowAlgorithm(net).loadflow());
        net.setStaticLoadIncludedInYMatrix(true);
        net.formYMatrix4DStab();
        var monitored=net.getDStabBus("Bus-1");
        var target=net.getDStabBus("Bus-3");
        var gen=(DStabGen)target.getContributeGen("G1");
        var data=frequency?new GeneratorTripRelayData(59.9,60.1,pickup,breaker)
                :new GeneratorTripRelayData(.9,1.1,pickup,breaker);
        AbstractGeneratorTripRelayModel relay=frequency
                ?new FrqtpatRelayModel(904,monitored,target,gen,data)
                :new VtgtpatRelayModel(904,monitored,target,gen,data);
        monitored.setVoltageMag(1.0);monitored.setFreq(1.0);
        assertTrue(relay.initStates(monitored));
        return relay;
    }

    private void outside(AbstractGeneratorTripRelayModel relay) {
        relay.getMonitoredBus().setVoltageMag(.7);
        relay.getMonitoredBus().setFreq(1.01);
    }

    @ParameterizedTest @MethodSource("recordedTiming")
    void positiveStagesRetainTheirOwnRecordedTick(boolean frequency,double dt,double pickup,
            double breaker,int expectedSteps) throws Exception {
        var relay=relay(frequency,pickup,breaker);
        // Include a genuine prefault interval: initial outside-band warm-up is
        // a separate experiment and must not contaminate onset timing.
        assertTrue(relay.afterStep(.05));outside(relay);
        for(int k=1;k<=expectedSteps;k++) {
            double prior=relay.getTimerMemory();
            assertTrue(relay.nextStep(dt,DynamicSimuMethod.MODIFIED_EULER,0));
            assertTrue(relay.nextStep(dt,DynamicSimuMethod.MODIFIED_EULER,1));
            assertTrue(relay.afterStep(0.0));
            assertEquals(prior,relay.getTimerMemory(),0.0);
            assertTrue(relay.afterStep(dt));
            assertEquals(k==expectedSteps,relay.isTripped(),"accepted interval "+k);
        }
        assertEquals(.05+expectedSteps*dt,relay.getActionTime(),1e-12);
        assertTrue(relay.didDisconnectTarget());assertFalse(relay.getTargetGenerator().isActive());
        assertEquals(pickup,relay.getData().pickupTime(),0.0);
        assertEquals(breaker,relay.getData().breakerTime(),0.0);
    }

    static Stream<Arguments> recordedEndpointCrossings() {
        return Stream.of(
                Arguments.of(true, .0033333, .008334, 0.0, 3),
                Arguments.of(true, .00166665, .008334, 0.0, 5),
                Arguments.of(true, .0033333, .007, 0.0, 2),
                Arguments.of(true, .0033333, .008334, .008334, 6),
                Arguments.of(false, .0033333, .008334, 0.0, 3),
                Arguments.of(false, .00166665, .008334, 0.0, 5));
    }

    @ParameterizedTest @MethodSource("recordedEndpointCrossings")
    void startsTimingAtFirstOutsideEndpoint(boolean frequency, double dt, double pickup,
            double breaker, int expectedIntervalsAfterCrossing) throws Exception {
        var relay = relay(frequency, pickup, breaker);
        assertTrue(relay.nextStep(dt, DynamicSimuMethod.MODIFIED_EULER, 0));
        outside(relay);
        assertTrue(relay.nextStep(dt, DynamicSimuMethod.MODIFIED_EULER, 1));
        assertTrue(relay.afterStep(dt));
        assertEquals(0.0, relay.getTimerMemory(), 0.0);
        assertFalse(relay.isPickedUp());

        for (int interval = 1; interval <= expectedIntervalsAfterCrossing; interval++) {
            assertTrue(relay.nextStep(dt, DynamicSimuMethod.MODIFIED_EULER, 0));
            assertTrue(relay.nextStep(dt, DynamicSimuMethod.MODIFIED_EULER, 1));
            assertTrue(relay.afterStep(dt));
            assertEquals(interval == expectedIntervalsAfterCrossing, relay.isTripped());
        }
        assertEquals((1 + expectedIntervalsAfterCrossing) * dt, relay.getActionTime(), 1e-12);
    }

    @Test void resetBeforePickupDoesNotLeakIntoLatchedBreaker() throws Exception {
        var relay=relay(false,.0204,.0204);outside(relay);
        for(int i=0;i<19;i++)assertTrue(relay.afterStep(.001));
        assertFalse(relay.isPickedUp());relay.getMonitoredBus().setVoltageMag(1.0);
        assertTrue(relay.afterStep(.001));assertEquals(0,relay.getTimerMemory(),0);
        outside(relay);for(int i=0;i<20;i++)assertTrue(relay.afterStep(.001));
        assertTrue(relay.isPickedUp());assertFalse(relay.isTripped());
        relay.getMonitoredBus().setVoltageMag(1.0);
        for(int i=0;i<19;i++)assertTrue(relay.afterStep(.001));
        assertFalse(relay.isTripped());assertTrue(relay.afterStep(.001));
        assertEquals(.06,relay.getActionTime(),1e-12);
        assertTrue(relay.reset());assertFalse(relay.isPickedUp());assertFalse(relay.isTripped());
        assertEquals(0,relay.getTimerMemory(),0);assertTrue(Double.isNaN(relay.getActionTime()));
    }

    @Test void clippedAcceptedIntervalsUseElapsedTimeRatherThanStepCounts() throws Exception {
        var relay=relay(false,.02,0);outside(relay);
        assertTrue(relay.afterStep(.006));assertTrue(relay.afterStep(.006));
        assertTrue(relay.afterStep(.001));assertFalse(relay.isTripped());
        assertTrue(relay.afterStep(.004));assertFalse(relay.isTripped());
        assertTrue(relay.afterStep(.003));assertTrue(relay.isTripped());
        assertEquals(.02,relay.getActionTime(),1e-12);
    }
}
