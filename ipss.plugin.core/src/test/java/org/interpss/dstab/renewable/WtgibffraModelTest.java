package org.interpss.dstab.renewable;

import static org.junit.jupiter.api.Assertions.*;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class WtgibffraModelTest {
    private static WtgibffraData data(double recovery, double filter, double cooldown) {
        var bands = new ArrayList<WtgibffraData.Band>();
        for (int i = 1; i <= 6; i++) bands.add(new WtgibffraData.Band(i * .125, .125, .0625, .125, .125, .125, recovery));
        return new WtgibffraData(0, .015625, bands, filter, cooldown);
    }
    private static WtgibffraModel initialized() {
        var model = new WtgibffraModel(data(1, 0, .25)); model.initialize(1); return model;
    }
    private static void trigger(WtgibffraModel model) { model.step(0, .5, .96875); }
    private static void cycle(WtgibffraModel model) {
        trigger(model); model.step(.125, .8, 1); model.step(.125, .8, 1);
        model.step(.125, .8, 1); model.step(1, .8, 1);
    }
    @Test void nativeGroupingRoundTripsAllFortyFiveConstantsWithoutMutableAliases() {
        var original=data(1, .02, .25);double[] constants=original.toNativeConstants();
        assertEquals(45, constants.length);assertEquals(.015625, constants[0]);
        assertEquals(.25, constants[2]);assertEquals(.0625, constants[14]);assertEquals(1, constants[38]);
        assertEquals(original, WtgibffraData.fromNativeParameters(0, constants));
        constants[1]=99;assertEquals(.125, original.bands().getFirst().power());
        assertThrows(UnsupportedOperationException.class, () -> original.bands().clear());
        assertThrows(IllegalArgumentException.class, () -> WtgibffraData.fromNativeParameters(0, new double[44]));
    }
    @Test void allSixPowerThresholdsSelectTheHighestEligibleBandWithoutInterpolation() {
        var d=data(1, 0, .25);assertEquals(-1,d.selectBand(.124));assertEquals(-1,d.selectBand(0));
        for(int i=0;i<6;i++){
            double threshold=d.bands().get(i).power();assertEquals(i,d.selectBand(threshold));
            assertEquals(i,d.selectBand(threshold+.01));
            var m=new WtgibffraModel(d);m.initialize(1);m.step(0,threshold,.96875);assertEquals(i,m.getSelectedBand());
        }
        assertEquals(5,d.selectBand(2));
    }
    @Test void nativeColumnOrderIsProvedWithDistinctValuesRatherThanOnlyARoundTrip() {
        double[] constants=new double[45];for(int i=0;i<45;i++)constants[i]=i+1;
        var d=WtgibffraData.fromNativeParameters(731,constants);assertEquals(731,d.frequencyBus());
        assertEquals(new WtgibffraData.Band(2,8,14,20,26,32,38),d.bands().getFirst());
        assertEquals(new WtgibffraData.Band(7,13,19,25,31,37,43),d.bands().getLast());
        assertEquals(44,d.frequencyFilterTime());assertEquals(45,d.cooldownTime());
    }
    @Test void selectedBandControlsTheActualRampNotOnlyTheReportedBandIndex() {
        var bands=new ArrayList<WtgibffraData.Band>();
        for(int i=0;i<6;i++)bands.add(new WtgibffraData.Band((i+1)*.125,(i+1)*.0625,.03125,
                (i+2)*.125,.125,.25,4));
        var d=new WtgibffraData(0,.015625,bands,0,.25);
        for(int i=0;i<6;i++){
            var m=new WtgibffraModel(d);m.initialize(1);double power=bands.get(i).power();m.step(0,power,.96875);
            m.step(.03125,power,1);assertEquals(power*bands.get(i).boost()/bands.get(i).riseTime()*.03125,m.getAuxiliaryPower(),1e-15);
            assertEquals(bands.get(i).boost(),m.getVariableSnapshot()[6]);assertEquals(bands.get(i).riseTime(),m.getVariableSnapshot()[8]);
        }
    }
    @Test void constantRampAndHoldEndpointsDoNotAcquireAnExtraStepFromSummationRoundoff() {
        double[] constants=data(1,0,.25).toNativeConstants();
        for(int i=19;i<37;i++)constants[i]=.1;
        var d=WtgibffraData.fromNativeParameters(0,constants);
        for(double power:new double[]{.24,.37,.83,1.15}){
            var small=new WtgibffraModel(d);var single=new WtgibffraModel(d);small.initialize(1);single.initialize(1);
            small.step(0,power,.9);single.step(0,power,.9);
            for(int phase=0;phase<3;phase++){
                for(int tick=0;tick<200;tick++)small.step(.0005,power,1);
                single.step(.1,power,1);
                assertEquals(single.getMode(),small.getMode(),"Constant-rate phase "+phase+" at power "+power);
                assertArrayEquals(single.getNativeStates(),small.getNativeStates(),1e-14);
            }
        }
    }
    @Test void roundoffEqualityDoesNotTurnIntoATimestepSizedEarlyTransition() {
        var m=initialized();trigger(m);m.step(.125-1e-10,.5,1);
        assertEquals(WtgibffraModel.Mode.RISE,m.getMode());
        m.step(1e-10,.5,1);assertEquals(WtgibffraModel.Mode.PEAK,m.getMode());
        m.step(.125-1e-10,.5,1);assertEquals(WtgibffraModel.Mode.PEAK,m.getMode());
        m.step(1e-10,.5,1);assertEquals(WtgibffraModel.Mode.FALL,m.getMode());
    }
    @Test void predictorRollbackRestoresCompensatedRampAndClockAtPhaseBoundaries() {
        var a=initialized();var b=initialized();trigger(a);trigger(b);
        for(int i=0;i<125;i++) {
            a.step(.001,.5,1,0);a.step(.001,.5,1,1);
            b.step(.001,.5,1);
            assertEquals(b.getMode(),a.getMode());assertArrayEquals(b.getNativeStates(),a.getNativeStates(),0);
        }
        assertEquals(WtgibffraModel.Mode.PEAK,a.getMode());
        a.initialize(1,7);b.initialize(1,7);trigger(a);trigger(b);
        a.step(.125,.5,1);b.step(.125,.5,1);
        assertEquals(7.125,a.getVariableSnapshot()[14],0);assertArrayEquals(b.getNativeStates(),a.getNativeStates(),0);
    }
    @Test void equilibriumAndObservationArePureAndImmutable() {
        var m=initialized();for(int i=0;i<100;i++)m.step(.001,.5,1);
        assertEquals(WtgibffraModel.Mode.READY,m.getMode());assertEquals(0,m.getAuxiliaryPower());
        assertArrayEquals(new double[]{0,0,0,1},m.getNativeStates(),0);
        assertThrows(UnsupportedOperationException.class,()->m.getNamedStates().put("extra",1.));
        double[] copy=m.getNativeStates();copy[0]=99;assertEquals(0,m.getNativeStates()[0]);
        double[] variables=m.getVariableSnapshot();variables[16]=99;assertEquals(-1,m.getVariableSnapshot()[16]);
    }
    @Test void triggerUsesTheFilteredUnderfrequencyBoundaryAndLatchesTheCycle() {
        var m=initialized();m.step(0,.5,1.1);assertEquals(WtgibffraModel.Mode.READY,m.getMode());
        m.step(0,.5,1-.015625);assertEquals(WtgibffraModel.Mode.RISE,m.getMode());
        m.step(.0625,.8,1.1);assertEquals(.03125,m.getAuxiliaryPower(),0);
        assertEquals(.5,m.getCapturedPower(),0);assertEquals(1,m.getVariableSnapshot()[2]);
    }
    @Test void completeRisePeakFallRecoveryAndCooldownUseTheCapturedParameters() {
        var m=initialized();trigger(m);assertEquals(3,m.getSelectedBand());
        m.step(.125,.9,1);assertEquals(WtgibffraModel.Mode.PEAK,m.getMode());assertEquals(.0625,m.getAuxiliaryPower(),0);
        assertEquals(.125,m.getVariableSnapshot()[14],0);
        m.step(.125,.9,1);assertEquals(WtgibffraModel.Mode.FALL,m.getMode());assertEquals(0,m.getVariableSnapshot()[14]);
        m.step(.125,.9,1);assertEquals(WtgibffraModel.Mode.RECOVERY,m.getMode());assertEquals(-.03125,m.getAuxiliaryPower(),0);
        m.step(1,.9,1);assertEquals(WtgibffraModel.Mode.COOLDOWN,m.getMode());
        assertArrayEquals(new double[]{0,0,0,1},m.getNativeStates(),0);assertEquals(.5,m.getCapturedPower());
        assertEquals(1.375,m.getVariableSnapshot()[15]);
        m.step(.25,.9,1);assertEquals(WtgibffraModel.Mode.READY,m.getMode());assertEquals(0,m.getCapturedPower());
    }
    @Test void automaticRecoveryBalancesInjectedAndRecoveredEnergyAndShorterUserTimeCannotOverrideIt() {
        var b=data(0,0,.25).bands().getFirst();assertEquals(.875,b.energyBalancedRecoveryTime(),0);
        assertEquals(.875,b.effectiveRecoveryTime(),0);
        var d=data(.25,0,.25);assertEquals(.875,d.bands().getFirst().effectiveRecoveryTime(),0);
        assertEquals(1,data(1,0,.25).bands().getFirst().effectiveRecoveryTime(),0);
        double ta=b.fallTime()*b.boost()/(b.boost()+b.deficit()),tb=b.fallTime()-ta,p0=.5;
        double injected=.5*b.boost()*p0*b.riseTime()+b.boost()*p0*b.peakTime()+.5*b.boost()*p0*ta;
        double recovered=.5*b.deficit()*p0*(tb+b.effectiveRecoveryTime());assertEquals(injected,recovered,1e-17);
    }
    @Test void zeroPeakBeginsFallAtTheRiseEndpointWithoutAnArtificialHold() {
        var constants=data(1,0,.25).toNativeConstants();for(int i=25;i<31;i++)constants[i]=0;
        var m=new WtgibffraModel(WtgibffraData.fromNativeParameters(0,constants));m.initialize(1);trigger(m);m.step(.125,.5,1);
        assertEquals(WtgibffraModel.Mode.FALL,m.getMode());assertEquals(.0625,m.getAuxiliaryPower());
    }
    @Test void eachActivePhaseIgnoresFurtherFrequencyEventsAndMovingPowerTargets() {
        for(int phase=0;phase<4;phase++){
            var m=initialized();trigger(m);
            for(int i=0;i<phase;i++)m.step(.125,.8,1);
            var mode=m.getMode();double[] old=m.getNativeStates();m.step(0,2,.8);
            assertEquals(mode,m.getMode());assertEquals(.5,m.getCapturedPower());assertEquals(3,m.getSelectedBand());
            for(int i=0;i<3;i++)assertEquals(old[i],m.getNativeStates()[i],0);
        }
    }
    @Test void cooldownPreventsReinitiationAndRequiresAnInactiveCallbackBeforeAnotherCycle() {
        var m=initialized();cycle(m);m.step(.125,.75,.9);assertEquals(WtgibffraModel.Mode.COOLDOWN,m.getMode());
        m.step(.125,.75,.9);assertEquals(WtgibffraModel.Mode.READY,m.getMode());
        m.step(0,.75,.9);assertEquals(WtgibffraModel.Mode.RISE,m.getMode());assertEquals(.75,m.getCapturedPower());assertEquals(5,m.getSelectedBand());
    }
    @Test void nonpositiveCooldownMakesTheFunctionUnavailableForTheRestOfTheRun() {
        for(double cooldown:new double[]{0,-1}){
            var m=new WtgibffraModel(data(1,0,cooldown));m.initialize(1);cycle(m);m.step(100,.8,.9);
            assertEquals(WtgibffraModel.Mode.COOLDOWN,m.getMode());assertEquals(0,m.getAuxiliaryPower());
        }
    }
    @Test void invalidOrderingDeactivatesTheModelButAnUnavailableBandDoesNotDisableOtherBands() {
        var constants=data(1,0,.25).toNativeConstants();constants[2]=constants[1];
        var d=WtgibffraData.fromNativeParameters(0,constants);assertFalse(d.enabled());var disabled=new WtgibffraModel(d);disabled.initialize(1);
        disabled.step(.1,.5,.9);assertEquals(WtgibffraModel.Mode.DISABLED,disabled.getMode());assertArrayEquals(new double[4],disabled.getNativeStates(),0);
        for(int column:new int[]{7,13,19,31}){
            double[] unavailable=data(1,0,.25).toNativeConstants();for(int i=column;i<column+6;i++)unavailable[i]=0;
            assertFalse(WtgibffraData.fromNativeParameters(0,unavailable).enabled());
        }
        constants=data(1,0,.25).toNativeConstants();constants[10]=0; // boost in the fourth band
        var m=new WtgibffraModel(WtgibffraData.fromNativeParameters(0,constants));m.initialize(1);trigger(m);
        assertEquals(WtgibffraModel.Mode.READY,m.getMode());m.step(0,.75,.9);assertEquals(WtgibffraModel.Mode.RISE,m.getMode());
    }
    @Test void belowThresholdAndZeroDeficitBandProduceNoAuxiliaryPower() {
        var m=initialized();m.step(0,.1,.9);assertEquals(WtgibffraModel.Mode.READY,m.getMode());
        var constants=data(1,0,.25).toNativeConstants();constants[16]=0;
        m=new WtgibffraModel(WtgibffraData.fromNativeParameters(0,constants));m.initialize(1);trigger(m);
        assertEquals(WtgibffraModel.Mode.READY,m.getMode());assertEquals(0,m.getAuxiliaryPower());
    }
    @Test void frequencyFilterUsesBothEulerEndpointsAndZeroTimeDoesNotAdvanceIt() {
        var m=new WtgibffraModel(data(1,.1,.25));m.initialize(1);
        m.step(.01,.5,.9,0);assertEquals(.99,m.getNativeStates()[3],1e-14);
        m.step(.01,.5,1,1);assertEquals(.9955,m.getNativeStates()[3],1e-14);
        double[] old=m.getNativeStates();m.step(0,.5,.8);assertArrayEquals(old,m.getNativeStates(),0);
    }
    @Test void predictorOnlyThresholdCrossingDoesNotCommitAFictitiousResponse() {
        var d=data(1,.1,.25);var m=new WtgibffraModel(new WtgibffraData(0,.005,d.bands(),.1,.25));m.initialize(1);
        m.step(.01,.5,.9,0);assertEquals(WtgibffraModel.Mode.RISE,m.getMode());
        m.step(.01,.5,1,1);assertEquals(WtgibffraModel.Mode.READY,m.getMode());assertEquals(0,m.getCapturedPower());
        assertEquals(0,m.getAuxiliaryPower());
    }
    @Test void reinitializationClearsCycleTimersPredictorAndTwoStepHistory() {
        var m=initialized();cycle(m);m.step(.01,.5,.9,0);m.initialize(1,42);
        assertEquals(WtgibffraModel.Mode.READY,m.getMode());assertEquals(0,m.getCapturedPower());
        assertThrows(IllegalStateException.class,()->m.step(.01,.5,1,1));trigger(m);m.step(.125,.5,1);
        assertEquals(42.125,m.getVariableSnapshot()[14],0);
    }
    @Test void repeatedAlgebraicRefreshDoesNotConsumeTwoStepHistory() {
        var a=initialized();var b=initialized();trigger(a);trigger(b);a.stepTwoStep(.001,.5,1);b.stepTwoStep(.001,.5,1);
        for(int i=0;i<10;i++)a.stepTwoStep(0,.5,1);
        a.stepTwoStep(.001,.5,1);b.stepTwoStep(.001,.5,1);
        assertArrayEquals(a.getNativeStates(),b.getNativeStates(),0);
    }
    @Test void invalidInputsAndNonfiniteParametersAreRejectedExplicitly() {
        assertThrows(IllegalArgumentException.class,()->new WtgibffraData(0,-.1,data(1,0,.25).bands(),0,.25));
        assertThrows(IllegalArgumentException.class,()->new WtgibffraData(0,.01,List.of(),0,.25));
        assertThrows(IllegalArgumentException.class,()->new WtgibffraData.Band(.1,Double.NaN,.1,.1,.1,.1,0));
        var m=initialized();assertThrows(IllegalArgumentException.class,()->m.step(-1,.5,1));
        assertThrows(IllegalArgumentException.class,()->m.step(.1,Double.NaN,1));
        assertThrows(IllegalArgumentException.class,()->m.step(.1,.5,-1));
        assertThrows(IllegalArgumentException.class,()->m.step(.1,.5,1,2));
        double[] extreme=data(1,0,.25).toNativeConstants();for(int i=7;i<13;i++)extreme[i]=Double.MAX_VALUE;
        var overflow=new WtgibffraModel(WtgibffraData.fromNativeParameters(0,extreme));overflow.initialize(1);
        assertThrows(IllegalArgumentException.class,()->trigger(overflow));assertEquals(WtgibffraModel.Mode.READY,overflow.getMode());
    }
}
