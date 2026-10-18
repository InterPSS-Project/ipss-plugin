package org.interpss.dstab.renewable;

import static org.junit.jupiter.api.Assertions.*;
import static org.interpss.dstab.renewable.RepccData.Parameter.*;
import java.util.ArrayList;
import java.util.Collections;
import org.apache.commons.math3.complex.Complex;
import org.junit.jupiter.api.Test;

class RepccDataTest {
    private static double[] constants() {
        return new double[]{.02,1,5,0,.05,.7,0,.05,.02,.05,-.05,0,0,1,-1,1,.1,.02,-.0006,.0006,
                99,-99,2,-1,.1,20,20,1.08,.95,1,-1,99,-99,.5,-.5,99,-99,.9,-.9,1,-1,1,-1,
                .02,-.25,-.5,.3,.4,.5,.1,.06,120,1,.02,.8,.015,-.015};
    }
    private static RepccData data(double[] c, int ref, int feedback, int feedforward) {
        return new RepccData(3, new RepccData.Branch(-2,3,"1"),1,ref,1,feedback,feedforward,0,
                new RepccData.Branch(0,0,"0"),Collections.nCopies(10,new RepccData.Shunt(0,"0")),3,1,c);
    }
    @Test void exactLayoutAndImmutableStorage() {
        assertEquals(35,RepccData.ICON_COUNT);assertEquals(57,RepccData.Parameter.values().length);
        assertEquals(13,RepccData.STATE_COUNT);assertEquals(36,RepccData.VAR_COUNT);
        double[] c=constants();var d=data(c,1,1,1);c[0]=9;d.constants()[0]=8;
        assertEquals(.02,d.value(TFLTR));assertTrue(d.measurementBranch().arrivingFlow());
        assertEquals(2,d.measurementBranch().measurementBus());
        assertThrows(UnsupportedOperationException.class,()->d.shunts().clear());
    }
    @Test void thresholdOrderingDisablesSwitchingWithoutRewritingInputs() {
        double[] c=constants();assertTrue(data(c,1,1,1).switchingThresholdsValid());
        c[QDN2.ordinal()]=-.1;var d=data(c,1,1,1);assertFalse(d.switchingThresholdsValid());
        assertEquals(-.1,d.value(QDN2));
    }
    @Test void flagsChoosePiLimitsAndLowPowerFallbackAtStrictBoundary() {
        assertEquals(-1,data(constants(),1,1,0).activePiMinimum());
        assertEquals(2,data(constants(),1,1,0).activePiMaximum());
        assertEquals(1,data(constants(),1,1,1).activePiMaximum());
        var d=data(constants(),2,1,1);
        for(double p:new double[]{0,.009999,-.009999})assertEquals(0,d.effectiveReferenceFlag(p));
        for(double p:new double[]{.01,-.01,.5})assertEquals(2,d.effectiveReferenceFlag(p));
        assertEquals(.001,d.referenceSlewTime(.0005));assertEquals(.0005,d.referenceSlewTime(.00025));
    }
    @Test void invalidDataAreRejected() {
        assertThrows(IllegalArgumentException.class,()->data(new double[56],1,1,1));
        for(double value:new double[]{Double.NaN,Double.POSITIVE_INFINITY}) {
            double[] c=constants();c[0]=value;assertThrows(IllegalArgumentException.class,()->data(c,1,1,1));
        }
        double[] c=constants();c[TP.ordinal()]=-.1;assertThrows(IllegalArgumentException.class,()->data(c,1,1,1));
        assertThrows(IllegalArgumentException.class,()->data(constants(),3,1,1));
        assertThrows(IllegalArgumentException.class,()->data(constants(),1,2,1));
        assertThrows(IllegalArgumentException.class,()->new RepccData.Branch(Integer.MIN_VALUE,3,"1"));
        assertThrows(IllegalArgumentException.class,()->new RepccData.Shunt(-1,"1"));
        assertThrows(IllegalArgumentException.class,()->new RepccData.Branch(2,3,"bad"));
        assertThrows(IllegalArgumentException.class,()->data(constants(),1,1,1).referenceSlewTime(0));
    }
    @Test void algebraicInitializationUsesPhysicalCurrentAndDistinctReferenceCoordinates() {
        var d=data(constants(),1,1,1);
        var m=new RepccInitialization.Measurements(new Complex(1,0),new Complex(.4,-.2),.4,.2,.41,.22);
        var init=RepccInitialization.initialize(d,m);double[] state=init.states();
        assertEquals(new Complex(.99,-.02).abs(),init.voltageReference(),1e-14);
        assertEquals(.22,state[2]);assertEquals(.4,state[4]);assertEquals(.01,state[5],1e-14);
        assertEquals(.41,state[6]);assertEquals(.4,init.plantReference());
        assertEquals(13,init.namedStates().size());state[2]=0;assertEquals(.22,init.states()[2]);
        assertThrows(UnsupportedOperationException.class,()->init.namedStates().clear());
        var noFeedforward=RepccInitialization.initialize(data(constants(),1,1,0),m);
        assertEquals(.41,noFeedforward.states()[5]);
        var noFeedback=RepccInitialization.initialize(data(constants(),1,0,1),m);
        assertEquals(0,noFeedback.states()[4]);assertEquals(0,noFeedback.plantReference());
        assertEquals(.41,noFeedback.states()[5]);
    }
    @Test void reactiveAndPowerFactorModesPreserveChargeDischargeSign() {
        var m=new RepccInitialization.Measurements(Complex.ONE,Complex.ZERO,-.4,-.2,-.4,-.2);
        var q=RepccInitialization.initialize(data(constants(),0,1,1),m);
        assertEquals(-.2,q.reactiveReference());assertEquals(-.2,q.states()[11]);assertEquals(0,q.voltageReference());
        double[] feasible=constants();feasible[PFMAX.ordinal()]=.8;feasible[PFMIN.ordinal()]=-.8;
        var pf=RepccInitialization.initialize(data(feasible,2,1,1),m);
        assertEquals(-.4/Math.hypot(.4,.2),pf.powerFactorReference(),1e-15);
        assertEquals(0,pf.states()[11]);assertEquals(-.2,pf.states()[1]);
        double[] c=constants();c[KI.ordinal()]=0;
        assertThrows(IllegalArgumentException.class,()->RepccInitialization.initialize(data(c,1,1,1),m));
    }
    @Test void infeasibleEquilibriumIsRejectedRatherThanClampedOrFitted() {
        var m=new RepccInitialization.Measurements(Complex.ONE,Complex.ZERO,.4,.2,.4,.2);
        assertThrows(IllegalArgumentException.class,()->RepccInitialization.initialize(data(constants(),2,1,1),m));
        assertThrows(IllegalArgumentException.class,()->RepccInitialization.initialize(data(constants(),2,0,1),m));
        double[] qLimited=constants();qLimited[QVMAX.ordinal()]=.1;
        assertThrows(IllegalArgumentException.class,()->RepccInitialization.initialize(data(qLimited,0,1,1),m));
        double[] limited=constants();limited[PIMAX.ordinal()]=.1;
        assertThrows(IllegalArgumentException.class,()->RepccInitialization.initialize(data(limited,1,0,1),m));
    }
}
