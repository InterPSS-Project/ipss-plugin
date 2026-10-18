package org.interpss.dstab.renewable;

import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Test;

class WtgwgoaModelTest {
    private static WtgwgoaData data(double filter,double hold){return new WtgwgoaData(.7,.1,.2,.3,.1,hold,.05,filter);}
    private static WtgwgoaModel initialized(){var model=new WtgwgoaModel(data(0,.5));model.initialize(1,.5);return model;}
    private static void beginRecovery(WtgwgoaModel model){model.step(0,.6,.5);model.step(0,1,.9);}
    @Test void initializedBypassAndNativeVarStorageAreDistinctAndObservationsArePure(){
        var m=initialized();assertEquals(.5,m.getReference());assertArrayEquals(new double[]{1,0,0},m.getNativeStates());
        assertArrayEquals(new double[]{.5,999,0},m.getNativeVars());
        for(int i=0;i<100;i++){m.step(.001,1,.6);m.getNamedStates();}
        assertEquals(.6,m.getReference());assertEquals(0,m.getNativeVars()[2]);
        assertThrows(UnsupportedOperationException.class,()->m.getNamedStates().put("extra",0.));
        double[] copy=m.getNativeStates();copy[0]=99;assertEquals(1,m.getNativeStates()[0]);
    }
    @Test void triggerAndRecoveryUseStrictBoundariesAndHysteresis(){
        var m=initialized();m.step(0,.7,.5);assertEquals(WtgwgoaModel.Mode.BYPASS,m.getMode());
        m.step(0,.699,.5);assertEquals(WtgwgoaModel.Mode.FAULT_HOLD,m.getMode());assertEquals(.1,m.getReference());
        m.step(0,.75,.8);assertEquals(WtgwgoaModel.Mode.FAULT_HOLD,m.getMode());
        m.step(0,.7500001,.8);assertEquals(WtgwgoaModel.Mode.FIRST_RAMP,m.getMode());
        assertEquals(.5,m.getNativeVars()[0],"Do not replace the captured target while the live input changes");
    }
    @Test void twoRampsAndTimedHoldRecoverToCapturedTargetThenBypassTheLiveInput(){
        var m=initialized();beginRecovery(m);m.step(1,1,.9);
        assertEquals(WtgwgoaModel.Mode.TIMED_HOLD,m.getMode());assertEquals(.3,m.getReference(),1e-14);
        assertEquals(1,m.getNativeVars()[1]);m.step(.5,1,.9);
        assertEquals(WtgwgoaModel.Mode.SECOND_RAMP,m.getMode());assertEquals(999,m.getNativeVars()[1]);
        m.step(2,1,.9);assertEquals(WtgwgoaModel.Mode.BYPASS,m.getMode());assertEquals(.9,m.getReference());
        assertArrayEquals(new double[]{1,0,0},m.getNativeStates());assertEquals(.5,m.getNativeVars()[2]);
    }
    @Test void zeroHoldBeginsSecondRampWithoutAnExtraArtificialHoldInterval(){
        var m=new WtgwgoaModel(data(0,0));m.initialize(1,.5);beginRecovery(m);m.step(1,1,.8);
        assertEquals(WtgwgoaModel.Mode.SECOND_RAMP,m.getMode());assertEquals(.3,m.getReference(),1e-14);
    }
    @Test void targetBelowIntermediatePowerCompletesInFirstRamp(){
        var m=initialized();m.initialize(1,.2);m.step(0,.6,.2);m.step(0,1,.8);m.step(.5,1,.8);
        assertEquals(WtgwgoaModel.Mode.BYPASS,m.getMode());assertEquals(.8,m.getReference());assertEquals(.2,m.getNativeVars()[2]);
    }
    @Test void faultPowerAbovePlantReferenceNeverIncreasesTheReference(){
        var m=initialized();m.step(0,.6,.08);assertEquals(.08,m.getReference());
        assertEquals(WtgwgoaModel.Mode.BYPASS,m.getMode());assertArrayEquals(new double[]{.6,0,0},m.getNativeStates());
    }
    @Test void repeatedFaultResetsEachRecoveryPhaseAndCapturesANewTarget(){
        for(int phase=0;phase<3;phase++){
            var m=initialized();beginRecovery(m);
            if(phase==0)m.step(.2,1,.7);else{m.step(1,1,.7);if(phase==2)m.step(.5,1,.7);}
            m.step(0,.6,.65);assertEquals(WtgwgoaModel.Mode.FAULT_HOLD,m.getMode());
            assertArrayEquals(new double[]{.6,0,0},m.getNativeStates());assertArrayEquals(new double[]{.65,999,.1},m.getNativeVars());
        }
    }
    @Test void disabledSelectionDoesNotInventARecoveryProfile(){
        var m=new WtgwgoaModel(new WtgwgoaData(.7,.3,.2,.1,.1,.5,.05,0));m.initialize(1,.5);m.step(.1,.2,.6);
        assertEquals(.6,m.getReference());assertEquals(WtgwgoaModel.Mode.BYPASS,m.getMode());assertEquals(0,m.getNativeVars()[2]);
        assertArrayEquals(new double[]{1,0,0},m.getNativeStates());assertArrayEquals(new double[]{0,0,0},m.getNativeVars());
    }
    @Test void nativeSecondFaultConventionIsExplicitAndDoesNotReplacePublishedRestart(){
        var nativeModel=WtgwgoaModel.forNativePsse367(data(0,.5));nativeModel.initialize(1,.5);beginRecovery(nativeModel);
        nativeModel.step(.2,1,.7);double ramp=nativeModel.getNativeStates()[1];
        nativeModel.step(0,.6,.65);assertEquals(WtgwgoaModel.Mode.FIRST_RAMP,nativeModel.getMode());
        assertEquals(ramp,nativeModel.getNativeStates()[1]);assertEquals(.5,nativeModel.getNativeVars()[0]);
        assertEquals(WtgwgoaModel.EquationConvention.PSSE_36_7,nativeModel.getEquationConvention());
        assertEquals(WtgwgoaModel.EquationConvention.PUBLISHED_RESTART,initialized().getEquationConvention());
    }
    @Test void voltageFilterUsesBothEulerStagesAndZeroTimeDoesNotAdvanceIt(){
        var m=new WtgwgoaModel(data(.1,.5));m.initialize(1,.5);
        m.step(.01,.5,.5,0);assertEquals(.95,m.getNativeStates()[0],1e-14);
        m.step(.01,.4,.5,1);assertEquals(.9475,m.getNativeStates()[0],1e-14);
        double[] initial=m.getNativeStates();m.step(0,0,.5);assertArrayEquals(initial,m.getNativeStates(),0);
    }
    @Test void predictorOnlyThresholdCrossingIsNotCommittedWhenCorrectedFilterStaysAboveThreshold(){
        var m=new WtgwgoaModel(new WtgwgoaData(.97,.1,.2,.3,.1,.5,.05,.1));m.initialize(1,.5);
        m.step(.01,.5,.5,0);assertEquals(WtgwgoaModel.Mode.FAULT_HOLD,m.getMode());
        m.step(.01,1,.5,1);assertEquals(WtgwgoaModel.Mode.BYPASS,m.getMode());assertEquals(.5,m.getReference());
        assertEquals(.9775,m.getNativeStates()[0],1e-14);
    }
    @Test void reinitializationResetsPredictorClockAndHistory(){
        var m=initialized();beginRecovery(m);m.step(.1,1,.7,0);m.initialize(1,.8,42);
        assertThrows(IllegalStateException.class,()->m.step(.1,1,.8,1));m.step(0,.6,.8);m.step(0,1,.9);m.step(1,1,.9);
        assertEquals(43,m.getNativeVars()[1]);
    }
    @Test void twoStepZeroRefreshPreservesHistoryAndInactiveRampCoordinatesDoNotDrift(){
        var a=initialized();var b=initialized();beginRecovery(a);beginRecovery(b);
        a.stepTwoStep(.01,1,.8);b.stepTwoStep(.01,1,.8);a.stepTwoStep(0,1,.8);
        a.stepTwoStep(.01,1,.8);b.stepTwoStep(.01,1,.8);assertArrayEquals(a.getNativeStates(),b.getNativeStates(),0);
        assertEquals(0,a.getNativeStates()[2]);
    }
    @Test void invalidInputsAndParametersAreExplicit(){
        assertThrows(IllegalArgumentException.class,()->new WtgwgoaData(.7,-.1,.2,.3,.1,.5,0,.1));
        assertThrows(IllegalArgumentException.class,()->new WtgwgoaData(.7,.1,0,.3,.1,.5,0,.1));
        assertThrows(IllegalArgumentException.class,()->new WtgwgoaData(.7,.1,.2,.3,.1,.5,Double.NaN,.1));
        var m=new WtgwgoaModel(data(0,.5));assertThrows(IllegalStateException.class,()->m.step(.1,1,.5));
        m.initialize(1,.5);assertThrows(IllegalArgumentException.class,()->m.step(-1,1,.5));
        assertThrows(IllegalArgumentException.class,()->m.step(.1,-1,.5));assertThrows(IllegalArgumentException.class,()->m.step(.1,1,.5,2));
    }
}
