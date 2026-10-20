package org.interpss.core.adapter.builder.dstab;

import static org.junit.jupiter.api.Assertions.*;
import java.util.List;
import org.interpss.dstab.control.oel.psse.basoel2.Basoel2Data;
import org.interpss.dstab.control.oel.psse.basoel2.Basoel2Kernel;
import org.interpss.dstab.control.oel.psse.basoel2.Basoel2Kernel.Inputs;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

/** Published equation/timer foundation, not imported or independently trajectory-accepted runtime. */
public class Basoel2KernelTest {
    private static Basoel2Data data(int oel,int vhz,double filter,double speedFilter){
        return new Basoel2Data(oel,vhz,1,filter,.2,.4,3,.25,2,.25,1,.25,speedFilter,1.05,.1);
    }
    private static Basoel2Kernel kernel(Basoel2Data data){
        return new Basoel2Kernel(data,Basoel2Kernel.TimerSemantics.POWERWORLD_DOCUMENTED);
    }
    private static Inputs input(double selected){return new Inputs(selected,1,1,1.2);}
    private static void advance(Basoel2Kernel k,double h,Inputs before,Inputs after){
        assertTrue(k.step(h,0,before));assertTrue(k.step(h,1,after));assertTrue(k.acceptEndpoint(h,after));
    }
    @Test void exactDataOrderAndCumulativeExposureTimesArePreserved(){
        var p=data(1,1,.02,.03);assertEquals(.2,p.proportionalGain());assertEquals(.4,p.integralGain());
        assertEquals(Basoel2Kernel.TimerSemantics.POWERWORLD_DOCUMENTED,kernel(p).timerSemantics());
        assertThrows(NoSuchMethodException.class,()->Basoel2Kernel.class.getConstructor(Basoel2Data.class));
        assertArrayEquals(new double[]{3,2,1},p.thresholds());assertArrayEquals(new double[]{.25,.5,.75},p.cumulativeDelays());
        double[] external=p.thresholds();external[0]=99;assertEquals(3,p.thresholds()[0]);
    }
    @ParameterizedTest @CsvSource({"0,0","0,1","1,0","1,1"})
    void inactiveFlatEquilibriumHasZeroRatesAndNoTimerExposure(int oel,int vhz){
        var k=kernel(data(oel,vhz,.02,.02));var f=input(.5);k.initialize(f);var state=k.state();
        for(int i=0;i<100;i++)advance(k,.001,f,f);
        assertEquals(state,k.state());assertEquals(0,k.signals(f).total());
        assertTrue(k.timers().stream().allMatch(t->!t.above()&&!t.active()&&t.elapsed()==0));
    }
    @Test void filtersUseExplicitModifiedEulerPredictorAndCorrector(){
        var k=kernel(data(1,0,.01,.02));k.initialize(input(1));
        var changed=new Inputs(2,.9,1,1.2);assertTrue(k.step(.001,0,changed));
        assertEquals(1.1,k.state().inputLag(),1e-15);assertEquals(.995,k.state().speedLag(),1e-15);
        assertTrue(k.step(.001,1,changed));assertEquals(1.095,k.state().inputLag(),1e-15);
        assertEquals(.995125,k.state().speedLag(),1e-15);assertTrue(k.acceptEndpoint(.001,changed));
    }
    @Test void bypassedFiltersRemainAlgebraicWithoutMutatingUnusedStorage(){
        var k=kernel(data(0,1,0,0));k.initialize(input(.5));var before=k.state();
        var changed=new Inputs(1,.05,1,1.2);var signals=k.signals(changed);
        assertEquals(1,signals.sensedInput());assertEquals(.05,signals.sensedFrequency());
        assertEquals(-.9,signals.vhzOutput(),1e-15);
        advance(k,.001,changed,changed);assertEquals(before.inputLag(),k.state().inputLag());assertEquals(before.speedLag(),k.state().speedLag());
    }
    @Test void cumulativeTimersLowerReferenceOnlyAtAcceptedEndpoints(){
        var k=kernel(data(1,0,0,0));var high=input(4);k.initialize(input(.5));
        assertTrue(k.step(.125,0,high));assertTrue(k.step(.125,1,high));assertEquals(0,k.timers().get(0).elapsed());
        assertTrue(k.acceptEndpoint(.125,high));assertFalse(k.timers().get(0).active());
        advance(k,.125,high,high);assertTrue(k.timers().get(0).active());assertEquals(3,k.signals(high).reference());
        advance(k,.125,high,high);advance(k,.125,high,high);
        assertTrue(k.timers().get(1).active());assertEquals(2,k.signals(high).reference());
        advance(k,.125,high,high);advance(k,.125,high,high);
        assertTrue(k.timers().get(2).active());assertEquals(1,k.signals(high).reference());
    }
    @Test void partialPreActivationExposureIsRetainedWhileBelowThreshold(){
        var k=kernel(data(1,0,0,0));k.initialize(input(.5));
        advance(k,.125,input(4),input(4));advance(k,.125,input(.5),input(.5));
        assertEquals(.125,k.timers().get(0).elapsed());assertFalse(k.timers().get(0).active());
        advance(k,.125,input(4),input(4));assertTrue(k.timers().get(0).active());
    }
    @Test void activeTimersCountDownAndResetWithoutOutputUntilCurrentAgainExceedsThreshold(){
        var k=kernel(data(1,0,0,0));k.initialize(input(.5));var high=input(4);
        advance(k,.25,high,high);assertTrue(k.signals(high).oelGate());
        var low=input(.5);assertFalse(k.signals(low).oelGate());assertEquals(0,k.signals(low).oelOutput());
        advance(k,.125,low,low);assertTrue(k.timers().get(0).active());assertEquals(.125,k.timers().get(0).elapsed());
        advance(k,.125,low,low);assertFalse(k.timers().get(0).active());assertEquals(0,k.timers().get(0).elapsed());
    }
    @Test void documentedReturnThresholdIsStrictAndDoesNotRatchetDuringObservations(){
        var k=kernel(data(1,0,0,0));k.initialize(input(.5));advance(k,.125,input(4),input(4));
        var latched=k.timers();double returnThreshold=3*.999;
        advance(k,.125,input(returnThreshold+.0001),input(returnThreshold+.0001));assertTrue(k.timers().get(0).above());
        advance(k,.125,input(returnThreshold),input(returnThreshold));assertFalse(k.timers().get(0).above());
        var before=k.timers();for(int i=0;i<10;i++)k.signals(input(3.001));assertEquals(before,k.timers());
        assertNotEquals(latched,k.timers());
    }
    @Test void zeroDelayUsesPublishedPositiveMemoryAndClearsOnTheFirstCoolingStep(){
        var p=new Basoel2Data(1,0,0,0,.2,.4,3,0,2,0,1,0,0,1.05,.1);
        var k=kernel(p);k.initialize(input(.5));var high=input(4);
        assertEquals(0,k.signals(high).oelOutput());advance(k,.001,high,high);
        for(var timer:k.timers()){assertTrue(timer.active());assertEquals(1e-6,timer.elapsed());}
        advance(k,.001,input(.5),input(.5));assertTrue(k.timers().stream().noneMatch(t->t.active()));
    }
    @ParameterizedTest @CsvSource({"0,0","0,1","1,0","1,1"})
    void enableSelectorsGateOutputsNotContinuousStorageOrTimerLogic(int oel,int vhz){
        var k=kernel(data(oel,vhz,0,0));k.initialize(input(.5));var f=new Inputs(10,1,1.2,1.2);
        advance(k,.25,f,f);assertTrue(k.state().integral()>0);assertTrue(k.timers().get(0).active());
        var s=k.signals(f);assertEquals(oel==0?0:-1.2,s.oelOutput(),1e-15);
        assertEquals(vhz==0?0:-.15,s.vhzOutput(),1e-15);assertEquals(s.oelOutput()+s.vhzOutput(),s.total());
    }
    @Test void piBoundsAndNonWindupUnwindingAreSeparateFromOutputGating(){
        var k=kernel(data(1,0,0,0));k.initialize(input(.5));var high=input(100);
        for(int i=0;i<10;i++)advance(k,.125,high,high);
        assertEquals(1.2,k.state().integral());assertEquals(0,k.rates(high).integral());
        var low=input(.5);assertTrue(k.rates(low).integral()<0);assertEquals(0,k.signals(low).oelOutput());
        advance(k,.125,low,low);assertTrue(k.state().integral()<1.2);
    }
    @Test void inputAndSpeedObservationsAndZeroTimeCallsNeverCommitTimers(){
        var k=kernel(data(1,1,.02,.02));k.initialize(input(.5));var high=input(4);
        assertTrue(k.step(.001,0,high));var state=k.state();var timers=k.timers();
        for(int i=0;i<10;i++){k.signals(high);k.rates(high);assertTrue(k.step(0,0,high));assertTrue(k.acceptEndpoint(0,high));}
        assertEquals(state,k.state());assertEquals(timers,k.timers());assertTrue(k.step(.001,1,high));
        assertTrue(k.acceptEndpoint(.001,high));assertFalse(k.acceptEndpoint(.001,high));
    }
    @Test void bothPredictedAndCorrectedUnacceptedTrialsCanBeCancelledAtomically(){
        for(boolean correct:new boolean[]{false,true}){
            var k=kernel(data(1,0,.02,0));k.initialize(input(.5));var original=k.state();var timers=k.timers();
            assertTrue(k.step(.001,0,input(4)));if(correct)assertTrue(k.step(.001,1,input(4)));
            k.cancelStep();assertEquals(original,k.state());assertEquals(timers,k.timers());
            assertFalse(k.step(.001,1,input(4)));advance(k,.001,input(4),input(4));
        }
    }
    @Test void wrongSequencesStepSizesAndNonfiniteTimesAreRejectedWithoutLosingTrialHistory(){
        var k=kernel(data(1,0,.02,0));k.initialize(input(.5));var high=input(4);
        assertFalse(k.step(.001,1,high));assertFalse(k.acceptEndpoint(.001,high));assertFalse(k.step(-1,0,high));
        assertFalse(k.step(Double.NaN,0,high));assertFalse(k.step(.001,2,high));assertTrue(k.step(.001,0,high));
        var predicted=k.state();assertFalse(k.step(.002,1,high));assertEquals(predicted,k.state());
        assertTrue(k.step(.001,1,high));assertFalse(k.step(.001,0,high));assertTrue(k.acceptEndpoint(.001,high));
    }
    @Test void stateAndTimerSnapshotsAreImmutableAndReinitializationDropsPendingHistory(){
        var k=kernel(data(1,0,0,0));k.initialize(input(.5));
        assertEquals(List.of("Input lag storage","PI integral","Speed lag storage"),List.copyOf(k.namedStates().keySet()));
        assertThrows(UnsupportedOperationException.class,()->k.namedStates().put("x",1.));
        assertThrows(UnsupportedOperationException.class,()->k.timers().clear());
        assertTrue(k.step(.1,0,input(4)));k.initialize(input(.5));assertEquals(0,k.state().integral());
        assertFalse(k.step(.1,1,input(4)));assertTrue(k.timers().stream().noneMatch(t->t.active()));
    }
    @Test void zeroVoltageReferenceAndZeroGainsDoNotInventIntegratorHeadroom(){
        var p=new Basoel2Data(1,1,0,0,0,0,0,0,0,0,0,0,0,0,0);
        var k=kernel(p);var f=new Inputs(4,0,0,0);k.initialize(f);advance(k,.001,f,f);
        assertEquals(0,k.state().integral());assertEquals(0,k.signals(f).total());
    }
    @Test void invalidTypedDataInputsAndArithmeticOverflowCannotBeHiddenByClipping(){
        assertThrows(IllegalArgumentException.class,()->new Inputs(0,1,1,-1));
        assertThrows(IllegalArgumentException.class,()->new Inputs(Double.NaN,1,1,1));
        assertThrows(IllegalArgumentException.class,()->new Basoel2Data(1,0,3,0,1,1,1,0,1,0,1,0,0,1,.1));
        assertThrows(IllegalArgumentException.class,()->new Basoel2Data(1,0,0,-1,1,1,1,0,1,0,1,0,0,1,.1));
        assertThrows(IllegalArgumentException.class,()->new Basoel2Data(1,0,0,0,1,1,1,Double.MAX_VALUE,1,Double.MAX_VALUE,1,0,0,1,.1));
        var k=kernel(data(1,0,.02,0));k.initialize(input(.5));var before=k.state();
        assertFalse(k.step(Double.MAX_VALUE,0,input(Double.MAX_VALUE)));assertEquals(before,k.state());
    }
    @ParameterizedTest @CsvSource({"-5,2","2,-5","42,1","1,42"})
    void everyNonzeroEnableFlagRetainsItsExactValueAndEnablesTheOutput(int oel,int vhz){
        var p=data(oel,vhz,0,0);assertEquals(oel,p.oelFlag());assertEquals(vhz,p.vhzFlag());
        assertTrue(p.oelEnabled());assertTrue(p.vhzEnabled());
        var k=kernel(p);k.initialize(input(.5));var f=new Inputs(10,1,1.2,1.2);
        advance(k,.25,f,f);assertEquals(-1.2,k.signals(f).oelOutput(),1e-15);
        assertEquals(-.15,k.signals(f).vhzOutput(),1e-15);
    }
    @Test void weccAppendixAndCurrentPowerWorldZeroDelayMemoryRemainDistinct(){
        var p=new Basoel2Data(1,0,0,0,.2,.4,3,0,2,0,1,0,0,1.05,.1);
        var wecc=new Basoel2Kernel(p,Basoel2Kernel.TimerSemantics.WECC_2026_DOCUMENTED);
        var pw=kernel(p);wecc.initialize(input(.5));pw.initialize(input(.5));
        var high=input(4);advance(wecc,.001,high,high);advance(pw,.001,high,high);
        assertTrue(wecc.timers().get(0).active());assertTrue(pw.timers().get(0).active());
        assertEquals(0,wecc.timers().get(0).elapsed());assertEquals(1e-6,pw.timers().get(0).elapsed());
        // Literal appendix ordering resets the zero-valued active timer on the next high-input step.
        advance(wecc,.001,high,high);advance(pw,.001,high,high);
        assertFalse(wecc.timers().get(0).active());assertTrue(pw.timers().get(0).active());
    }
}
