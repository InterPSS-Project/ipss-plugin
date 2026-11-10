package org.interpss.dstab.mach;

import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Test;

/** Zero weights disconnect incremental commands without destroying the host operating point. */
class ReaxZeroParticipationTest {
    private Reaxbu1Model model(double time,double qGain,double pGain) {
        return new Reaxbu1Model("REAX4BU1",new Reaxbu1Data(1,time,qGain,pGain,2,-2,2,-2));
    }

    @Test void zeroActiveWeightHoldsNonzeroPowerDuringReactiveAndPlantCommandChanges() {
        var m=model(.02,.8,0);m.initializeOutputs(.1,.5,2);
        assertEquals(0,m.getData().activeGain(),0);assertEquals(0,m.getActiveReference(),0);
        for(double rate:m.getStateDerivativeSnapshot().rates().values())assertEquals(0,rate,0);
        m.setExternalSignals(.05,100);
        m.step(.001,0);assertEquals(.5,m.getActiveOutput(),0);
        m.step(.001,1);assertEquals(.5,m.getActiveOutput(),0);
        // Heun step of dQ/dt=(.18-Q)/.02: .1 + .001/2*(4+3.8).
        assertEquals(.1039,m.getReactiveOutput(),1e-14);
        m.initializeOutputs(.2,.7,2);assertEquals(.7,m.getActiveOutput(),0);
        for(double rate:m.getStateDerivativeSnapshot().rates().values())assertEquals(0,rate,1e-14);
    }

    @Test void zeroReactiveWeightHoldsOutputAndStaggeredRatesRemainFinite() {
        var m=model(.02,0,.6);m.initializeOutputs(.2,.5,2);
        m.setExternalSignals(100,.05);
        double[] rates=new double[2];m.evaluateStaggered(.001,rates,0);
        assertEquals(0,rates[0],0);assertEquals(3,rates[1],1e-12);
        m.applyStaggeredState(new double[]{.2,.503},0,.001);
        assertEquals(.2,m.getReactiveOutput(),0);assertEquals(.503,m.getActiveOutput(),0);
    }

    @Test void algebraicAndLegacyIncrementalInitializationHandleTwoZeroWeights() {
        var m=model(0,0,0);m.initializeOutputs(.2,.5);
        m.setReferences(10,-10);m.setExternalSignals(-100,100);
        m.step(.001,0);m.step(.001,1);
        assertEquals(.2,m.getReactiveOutput(),0);assertEquals(.5,m.getActiveOutput(),0);
        m.initialize(5,6);m.step(.001,0);m.step(.001,1);
        assertEquals(0,m.getReactiveOutput(),0);assertEquals(0,m.getActiveOutput(),0);
    }

    @Test void nonzeroSignedGainsKeepExistingReferenceAndLimiterEquations() {
        var m=model(0,-.8,.6);m.initializeOutputs(.2,.5,2);
        assertEquals(-.25,m.getReactiveReference(),1e-14);
        assertEquals(.5/.6,m.getActiveReference(),1e-14);
        m.setExternalSignals(.1,.2);m.step(.001,0);m.step(.001,1);
        assertEquals(.04,m.getReactiveOutput(),1e-14);assertEquals(.74,m.getActiveOutput(),1e-14);
        m.setExternalSignals(-100,100);m.step(.001,0);m.step(.001,1);
        assertEquals(2,m.getReactiveOutput(),0);assertEquals(2,m.getActiveOutput(),0);
        assertThrows(IllegalArgumentException.class,()->model(.02,Double.NaN,0));
    }
}
