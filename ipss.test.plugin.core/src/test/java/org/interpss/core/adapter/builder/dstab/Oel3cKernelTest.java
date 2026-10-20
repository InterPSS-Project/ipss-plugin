package org.interpss.core.adapter.builder.dstab;

import static org.junit.jupiter.api.Assertions.*;
import java.util.Map;
import org.interpss.dstab.control.oel.psse.oel3c.Oel3cData;
import org.interpss.dstab.control.oel.psse.oel3c.Oel3cKernel;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/** Published source equations and stage invariants, not native trajectory acceptance. */
public class Oel3cKernelTest {
    private static Oel3cData data(int exponent,double tf) {
        return new Oel3cData(1,exponent,1.,.5,tf,.2,.1,.7,0.,-1.,0.,-2.);
    }
    @ParameterizedTest @ValueSource(ints={1,2})
    void inactiveEquilibriumDoesNotIntegrateOutwardOrEnableProportionalPath(int exponent) {
        var k=new Oel3cKernel(data(exponent,.02));k.initialize(1.);
        var initial=k.state();for(int i=0;i<100;i++){assertTrue(k.step(.001,0,1.));assertTrue(k.step(.001,1,1.));}
        assertEquals(initial,k.state());assertEquals(0,k.signals(1).proportional(),0);assertEquals(0,k.signals(1).output(),0);
    }
    @ParameterizedTest @ValueSource(ints={1,2})
    void bothStagesUseTheirOwnInputAndTheWholePredictedVector(int exponent) {
        var p=data(exponent,.02);var k=new Oel3cKernel(p);k.initialize(2.4);double h=.001;
        double x=.5*2.4,i=0,r0=(.5*3-x)/.02;
        double i0=.2*(1-Math.pow(x,exponent))/.1;
        assertTrue(k.step(h,0,3));assertEquals(x+h*r0,k.state().inputLag(),1e-15);
        assertEquals(i+h*i0,k.state().integral(),1e-15);
        double r1=(.5*2.8-(x+h*r0))/.02;
        double i1=.2*(1-Math.pow(x+h*r0,exponent))/.1;
        assertTrue(k.step(h,1,2.8));assertEquals(x+.5*h*(r0+r1),k.state().inputLag(),1e-15);
        assertEquals(i+.5*h*(i0+i1),k.state().integral(),1e-15);
    }
    @ParameterizedTest @ValueSource(ints={1,2})
    void negativeIntegralEnablesTheProportionalGainOnTheGainedError(int exponent) {
        var k=new Oel3cKernel(data(exponent,0));k.initialize(3.);
        assertEquals(0,k.signals(3).proportional(),0);assertTrue(k.step(.001,0,3.));
        double gained=.2*(1-Math.pow(1.5,exponent));
        assertEquals(.7*gained,k.signals(3).proportional(),1e-15);
        assertEquals(k.state().integral()+.7*gained,k.signals(3).output(),1e-15);
    }
    @ParameterizedTest @ValueSource(ints={1,2})
    void boundedIntegralFreezesOutwardAndUnwindsInward(int exponent) {
        var k=new Oel3cKernel(data(exponent,0));k.initialize(4);
        for(int i=0;i<3000;i++){assertTrue(k.step(.001,0,4));assertTrue(k.step(.001,1,4));}
        assertEquals(-1,k.state().integral(),0);assertEquals(0,k.rates(4).integral(),0);
        assertTrue(k.rates(1).integral()>0);assertTrue(k.step(.001,0,1));assertTrue(k.step(.001,1,1));
        assertTrue(k.state().integral()>-1);
    }
    @Test void outputSaturationDoesNotFreezeAnInteriorIntegrator() {
        var p=new Oel3cData(1,1,1,.5,0,.2,.1,20,0,-1,0,-.01);
        var k=new Oel3cKernel(p);k.initialize(3);assertTrue(k.step(.001,0,3));
        assertEquals(-.01,k.signals(3).output(),0);assertTrue(k.rates(3).integral()<0);
        double first=k.state().integral();assertTrue(k.step(.001,1,3));assertTrue(k.state().integral()<0);
        assertEquals(first,k.state().integral(),1e-15);
    }
    @Test void zeroFilterHasAlgebraicFeedthroughWithoutChangingUnusedStorage() {
        var k=new Oel3cKernel(data(1,0));k.initialize(1);var before=k.state();
        assertEquals(1.5,k.signals(3).scaledInput(),0);assertEquals(0,k.rates(3).inputLag(),0);
        assertEquals(before,k.state());assertTrue(k.step(.001,0,3));assertTrue(k.step(.001,1,3));
        assertEquals(before.inputLag(),k.state().inputLag(),0);
    }
    @Test void zeroTimeAndObservationCannotAdvanceOrClearPendingHistory() {
        var k=new Oel3cKernel(data(1,.02));k.initialize(2.4);assertTrue(k.step(.001,0,3));
        var before=k.state();var map=k.namedStates();assertThrows(UnsupportedOperationException.class,()->map.put("x",1.));
        assertEquals(java.util.List.of("Input lag storage","OEL integral"),new java.util.ArrayList<>(map.keySet()));
        for(int i=0;i<10;i++){k.signals(3);k.rates(3);assertTrue(k.step(0,0,3));}
        assertEquals(before,k.state());assertTrue(k.step(.001,1,3));
    }
    @Test void invalidStageSequencesAndNonfiniteInputsLeaveStateAndHistoryIntact() {
        var k=new Oel3cKernel(data(1,.02));k.initialize(2.4);var before=k.state();
        assertFalse(k.step(.001,1,3));assertFalse(k.step(-1,0,3));assertFalse(k.step(.001,2,3));
        assertFalse(k.step(.001,0,Double.NaN));assertEquals(before,k.state());
        assertTrue(k.step(.001,0,3));var predicted=k.state();assertFalse(k.step(.001,0,3));
        assertFalse(k.step(.002,1,3));assertEquals(predicted,k.state());assertTrue(k.step(.001,1,3));
    }
    @Test void initializationResetAndRejectionDoNotCarryOldIntegratorHistory() {
        var k=new Oel3cKernel(data(1,0));k.initialize(3);assertTrue(k.step(.001,0,3));
        k.initialize(1);assertEquals(new Oel3cKernel.State(.5,0),k.state());assertFalse(k.step(.001,1,3));
        var before=k.state();assertThrows(IllegalArgumentException.class,()->k.initialize(Double.NaN));assertEquals(before,k.state());
    }
    @Test void uninitializedAccessIsExplicitlyRejected() {
        var k=new Oel3cKernel(data(1,.02));assertThrows(IllegalStateException.class,k::state);
        assertThrows(IllegalStateException.class,()->k.signals(1));assertThrows(IllegalStateException.class,()->k.step(.001,0,1));
    }
    @Test void enteredInvertedBoundsArePreservedAndEffectivePairsAreSwapped() {
        var p=new Oel3cData(1,1,1,1,.02,.2,.1,.7,-1,0,-2,0);
        assertEquals(-1,p.integralMax(),0);assertEquals(0,p.integralMin(),0);
        assertEquals(-1,p.lowerIntegral(),0);assertEquals(0,p.upperIntegral(),0);
        assertEquals(-2,p.lowerOutput(),0);assertEquals(0,p.upperOutput(),0);
    }
    @ParameterizedTest @ValueSource(ints={-9,0,1,2,3,99})
    void inputFallbackDoesNotAliasTheThreeDistinctPhysicalSignals(int entered) {
        var p=new Oel3cData(entered,1,1,1,.02,.2,.1,.7,0,-1,0,-1);
        assertEquals(entered,p.inputFlag());assertEquals(entered==2||entered==3?entered:1,p.effectiveInputFlag());
    }
    @Test void invalidDataAndOverflowCannotSilentlyProduceAFiniteClippedOutput() {
        assertThrows(IllegalArgumentException.class,()->new Oel3cData(1,0,1,1,0,.2,.1,1,0,-1,0,-1));
        assertThrows(IllegalArgumentException.class,()->new Oel3cData(1,1,1,1,-1,.2,.1,1,0,-1,0,-1));
        assertThrows(IllegalArgumentException.class,()->new Oel3cData(1,1,1,1,0,.2,0,1,0,-1,0,-1));
        var k=new Oel3cKernel(data(2,.02));k.initialize(1);var before=k.state();
        assertFalse(k.step(1,0,Double.MAX_VALUE));assertEquals(before,k.state());
        assertThrows(IllegalArgumentException.class,()->k.signals(Double.NaN));
        var bypassed=new Oel3cKernel(data(2,0));bypassed.initialize(4);var start=bypassed.state();
        assertFalse(bypassed.step(Double.MAX_VALUE,0,4));assertEquals(start,bypassed.state());
    }
}
