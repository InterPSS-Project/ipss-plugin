package org.interpss.core.adapter.builder.dstab;

import static org.junit.jupiter.api.Assertions.*;
import org.interpss.dstab.control.oel.psse.oel4c.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/** Direct equation/stage checks; no host or independent coupled acceptance is implied. */
public class Oel4cKernelTest {
    private static Oel4cKernel kernel(double delay,double kp,double ki,double min){
        var k=new Oel4cKernel(new Oel4cData(1,delay,kp,ki,min));k.initialize(Double.NaN);return k;
    }
    private static void step(Oel4cKernel k,double q,double h){assertTrue(k.step(h,0,q));assertTrue(k.step(h,1,q));}
    @Test void exactFiveConstantOrderAndZeroPickupResolution(){
        var d=new Oel4cData(.4,.02,.3,.7,-.8);assertEquals(.4,d.effectivePickup(Double.NaN));
        var zero=new Oel4cData(0,0,1,0,0);assertEquals(1.2,zero.effectivePickup(1.2));
        assertThrows(IllegalArgumentException.class,()->zero.effectivePickup(Double.NaN));
        assertThrows(IllegalArgumentException.class,()->zero.effectivePickup(-1));
    }
    @ParameterizedTest @ValueSource(doubles={Double.NaN,Double.POSITIVE_INFINITY,Double.NEGATIVE_INFINITY})
    void everyNonfiniteConstantIsRejected(double bad){
        assertThrows(IllegalArgumentException.class,()->new Oel4cData(bad,0,1,1,-1));
        assertThrows(IllegalArgumentException.class,()->new Oel4cData(1,bad,1,1,-1));
        assertThrows(IllegalArgumentException.class,()->new Oel4cData(1,0,bad,1,-1));
        assertThrows(IllegalArgumentException.class,()->new Oel4cData(1,0,1,bad,-1));
        assertThrows(IllegalArgumentException.class,()->new Oel4cData(1,0,1,1,bad));
    }
    @Test void invalidDomainAndZeroGainAutocorrectionAreRejected(){
        assertThrows(IllegalArgumentException.class,()->new Oel4cData(-1,0,1,1,-1));
        assertThrows(IllegalArgumentException.class,()->new Oel4cData(1,-1,1,1,-1));
        assertThrows(IllegalArgumentException.class,()->new Oel4cData(1,0,1,-1,-1));
        assertThrows(IllegalArgumentException.class,()->new Oel4cData(1,0,1,1,1));
        assertThrows(IllegalArgumentException.class,()->new Oel4cData(1,0,0,0,-1));
    }
    @Test void initializationAndStateObservationsArePure(){
        var k=kernel(.02,.3,.7,-1);assertEquals(0,k.signals(.5).output());
        var state=k.namedStates();for(int i=0;i<10;i++)assertFalse(k.signals(2).active());
        assertEquals(state,k.namedStates());assertEquals(0,k.signals(2).elapsed());
        assertThrows(UnsupportedOperationException.class,()->state.clear());
    }
    @Test void positiveErrorCannotWindUpAtTheUpperOutputLimit(){
        var k=kernel(0,.3,.7,-1);for(int i=0;i<100;i++)step(k,.5,.001);
        assertEquals(0,k.signals(.5).output());assertEquals(0,k.signals(.5).integral(),1e-14);
    }
    @Test void exactDelayBoundaryAndRecoveryResetUseStrictNegativeError(){
        var k=kernel(.02,.3,.7,-1);step(k,2,.01);assertFalse(k.signals(2).active());
        assertEquals(0,k.signals(2).output());step(k,2,.01);assertTrue(k.signals(2).active());
        assertEquals(-.3035,k.signals(2).output(),1e-12);
        step(k,.5,.01);assertEquals(0,k.signals(2).elapsed());assertFalse(k.signals(2).active());
    }
    @Test void zeroDelayAndIntegralOnlyFollowTheUnconstrainedEquation(){
        var k=kernel(0,0,.7,-1);assertTrue(k.signals(1.5).active());step(k,1.5,.01);
        assertEquals(-.0035,k.signals(1.5).integral(),1e-14);assertEquals(-.0035,k.signals(1.5).output(),1e-14);
    }
    @Test void proportionalOnlyDoesNotInventAnIntegralGain(){
        var k=kernel(0,.3,0,-1);step(k,1.5,.01);
        assertEquals(0,k.signals(1.5).integral());assertEquals(-.15,k.signals(1.5).output(),1e-14);
    }
    @Test void conditionalPiPreventsWindupAtBothLimitsAndCanRecover(){
        var k=kernel(0,3,4,-.02);for(int i=0;i<100;i++)step(k,2,.001);
        assertEquals(-.02,k.signals(2).output(),1e-14);double held=k.signals(2).integral();
        step(k,2,.001);assertEquals(held,k.signals(2).integral(),1e-14);
        step(k,0,.001);assertEquals(0,k.signals(0).output(),1e-14);
    }
    @Test void invalidStagesAndZeroTimeDoNotAdvanceOrLoseThePendingPrediction(){
        var k=kernel(.02,.3,.7,-1);assertFalse(k.step(.01,1,2));assertTrue(k.step(.01,0,2));
        var x=k.namedStates();double time=k.signals(2).elapsed();
        assertFalse(k.step(.01,0,2));assertFalse(k.step(.02,1,2));assertFalse(k.step(.01,1,Double.NaN));
        assertTrue(k.step(0,1,2));assertEquals(x,k.namedStates());assertEquals(time,k.signals(2).elapsed());
        assertTrue(k.step(.01,1,2));
    }
    @Test void predictionRollbackRestoresTimerAndPiAndCanRestart(){
        var k=kernel(0,.3,.7,-1);var initial=k.namedStates();assertTrue(k.step(.01,0,2));
        assertNotEquals(initial,k.namedStates());k.cancelPrediction();assertEquals(initial,k.namedStates());
        assertEquals(0,k.signals(2).elapsed());assertFalse(k.step(.01,1,2));step(k,2,.01);
        assertEquals(-.307,k.signals(2).output(),1e-12);
    }
    @Test void reinitializationClearsBothContinuousAndDiscreteHistory(){
        var k=kernel(0,.3,.7,-1);step(k,2,.01);k.initialize(Double.NaN);
        assertEquals(0,k.signals(2).integral());assertEquals(0,k.signals(2).elapsed());
        assertThrows(IllegalStateException.class,()->new Oel4cKernel(new Oel4cData(1,0,1,1,-1)).signals(2));
    }
    @Test void numericalOverflowIsRejectedBeforeContinuousOrTimerHistoryChanges(){
        var k=kernel(0,0,Double.MAX_VALUE,-Double.MAX_VALUE);var initial=k.namedStates();
        assertFalse(k.step(.001,0,Double.MAX_VALUE));assertEquals(initial,k.namedStates());
        assertEquals(0,k.signals(1).elapsed());
        var timer=kernel(Double.MAX_VALUE,1,0,-1);step(timer,2,Double.MAX_VALUE);
        var saved=timer.namedStates();assertFalse(timer.step(Double.MAX_VALUE,0,2));
        assertEquals(saved,timer.namedStates());assertEquals(Double.MAX_VALUE,timer.signals(2).elapsed());
    }
    @Test void prolongedPositiveErrorCannotCreatePrematureDelayedLimiting(){
        var k=kernel(.02,.3,.7,-1);for(int i=0;i<100;i++)step(k,.5,.001);
        assertEquals(0,k.signals(.5).integral());step(k,2,.005);
        assertEquals(0,k.signals(2).output());assertFalse(k.signals(2).active());
    }
    @Test void enforcementTimerPersistsDuringIntegralRecoveryAndShortOverloadsReset(){
        var k=kernel(.01,0,1,-1);step(k,2,.01);step(k,2,.01);
        step(k,.9,.01);assertTrue(k.signals(2).elapsed()>.02);assertTrue(k.signals(.9).output()<0);
        step(k,2,.001);assertTrue(k.signals(2).active());
        var shortExposure=kernel(.01,0,1,-1);step(shortExposure,2,.002);step(shortExposure,.9,.001);
        assertEquals(0,shortExposure.signals(2).elapsed());
    }
    @Test void recoveryExpiryUsesTheAcceptedCorrectorNotAnOvershootingPredictor(){
        var k=kernel(.01,0,1,-1);step(k,2,.01);step(k,.6,.01);
        assertEquals(-.001,k.signals(.6).integral(),1e-14);
        assertTrue(k.step(.01,0,.85));assertEquals(0,k.signals(.85).output());
        assertTrue(k.step(.01,1,.85));assertEquals(-.00025,k.signals(.85).output(),1e-14);
        assertEquals(.03,k.signals(.85).elapsed(),1e-14,
            "A rejected predictor's zero output must not expire an enforcing corrected state");
    }
    @Test void checkpointRestoresAnUnacceptedCorrectorAndRejectsForeignOrTrialSnapshots(){
        var k=kernel(0,.3,.7,-1);var initial=k.namedStates();var checkpoint=k.checkpoint();
        assertTrue(k.step(.01,0,2));assertThrows(IllegalStateException.class,k::checkpoint);
        assertTrue(k.step(.01,1,2));assertNotEquals(initial,k.namedStates());
        k.restore(checkpoint);assertEquals(initial,k.namedStates());assertEquals(0,k.signals(2).elapsed());
        assertThrows(IllegalArgumentException.class,()->kernel(0,.3,.7,-1).restore(checkpoint));
        step(k,2,.01);assertEquals(-.307,k.signals(2).output(),1e-12);
    }
    @Test void coreCorrectorDifferenceOverflowIsRejectedWithoutLosingThePrediction(){
        var k=kernel(0,0,Double.MAX_VALUE,-Double.MAX_VALUE);
        assertTrue(k.step(1e-310,0,1.75));var predicted=k.namedStates();double clock=k.signals(1.75).elapsed();
        assertFalse(k.step(1e-310,1,.25));assertEquals(predicted,k.namedStates());assertEquals(clock,k.signals(1.75).elapsed());
        k.cancelPrediction();assertEquals(0,k.signals(1).integral());
    }
}
