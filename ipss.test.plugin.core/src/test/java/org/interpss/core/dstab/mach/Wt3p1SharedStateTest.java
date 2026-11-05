package org.interpss.core.dstab.mach;

import static org.junit.jupiter.api.Assertions.*;

import org.interpss.dstab.mach.Wt3p1Data;
import org.interpss.dstab.mach.Wt3p1Model;
import org.junit.jupiter.api.Test;

/** Shared pitch states retain the ordinary controller equations and limits. */
public class Wt3p1SharedStateTest {
    private static Wt3p1Data data(double rate, Wt3p1Data.RateLimitPolicy policy) {
        return new Wt3p1Data(.012, .8, 1.7, .4, 1.1, -3, 25,
                rate, 1.0, policy);
    }

    @Test void sharedPredictorCorrectorMatchesOrdinaryAcrossInputAndLimitChanges() {
        for (double signedRate : new double[]{.75, -.75}) {
            var policy=signedRate<0?Wt3p1Data.RateLimitPolicy.SIGNED_MAGNITUDE:
                    Wt3p1Data.RateLimitPolicy.NONNEGATIVE;
            var ordinary=new Wt3p1Model(data(signedRate,policy));
            var shared=new Wt3p1Model(data(signedRate,policy));
            ordinary.initialize(1.5,0,1.0);shared.initialize(1.5,0,1.0);
            double h=.0005;
            double[] start=new double[3],predictor=new double[3],corrector=new double[3],rate0=new double[3],rate1=new double[3],actual=new double[3];
            for(int step=0;step<2000;step++) {
                double t=step*h, speed=.13*Math.sin(17*t), reference=.04*Math.cos(9*t),
                        power=1.0+.33*Math.sin(11*t);
                double speedEnd=.13*Math.sin(17*(t+h)),referenceEnd=.04*Math.cos(9*(t+h)),
                        powerEnd=1.0+.33*Math.sin(11*(t+h));
                shared.readStaggeredState(start,0);
                shared.evaluateStaggered(h,speed,reference,power,rate0,0);
                ordinary.step(h,speed,reference,power,0);
                for(int i=0;i<3;i++)predictor[i]=start[i]+h*rate0[i];
                shared.applyStaggeredState(predictor,0,h);
                shared.readStaggeredState(actual,0);
                assertEquals(ordinary.getPitch(),actual[0],0,"predictor pitch step="+step);
                assertEquals(ordinary.getPitchControlState(),actual[1],0,"predictor PI step="+step);
                assertEquals(ordinary.getPitchCompensationState(),actual[2],0,"predictor compensation step="+step);
                shared.evaluateStaggered(h,speedEnd,referenceEnd,powerEnd,rate1,0);
                ordinary.step(h,speedEnd,referenceEnd,powerEnd,1);
                for(int i=0;i<3;i++)corrector[i]=start[i]+.5*h*(rate0[i]+rate1[i]);
                shared.applyStaggeredState(corrector,0,h);
                shared.readStaggeredState(actual,0);
                assertEquals(ordinary.getPitch(),actual[0],0,"corrector pitch step="+step);
                assertEquals(ordinary.getPitchControlState(),actual[1],0,"corrector PI step="+step);
                assertEquals(ordinary.getPitchCompensationState(),actual[2],0,"corrector compensation step="+step);
            }
            assertTrue(Double.isFinite(shared.getPitch()));
        }
    }

    @Test void trialRatesArePureAndInvalidProjectionLeavesAllStatesUnchanged() {
        var model=new Wt3p1Model(data(.75,Wt3p1Data.RateLimitPolicy.NONNEGATIVE));
        double[] values=new double[6], rates=new double[6];
        assertThrows(IllegalStateException.class,()->model.readStaggeredState(values,2));
        model.initialize(1.5,0,1.0);
        model.readStaggeredState(values,2);
        double[] original=values.clone();
        model.evaluateStaggered(.0005,.1,0,1.2,rates,2);
        double[] first=rates.clone();
        model.evaluateStaggered(.0005,.1,0,1.2,rates,2);
        assertArrayEquals(first,rates,0);
        model.readStaggeredState(values,2);assertArrayEquals(original,values,0);
        values[4]=Double.NaN;
        assertThrows(IllegalArgumentException.class,()->model.applyStaggeredState(values,2,.0005));
        model.readStaggeredState(values,2);assertArrayEquals(original,values,0);
        assertThrows(IllegalArgumentException.class,()->model.evaluateStaggered(.0005,Double.NaN,0,1.2,rates,2));
        assertArrayEquals(first,rates,0);
        model.applyStaggeredState(original,2,0);
        model.readStaggeredState(values,2);assertArrayEquals(original,values,0);
    }

    @Test void limitsAndOrdinaryStageOwnershipArePreserved() {
        var model=new Wt3p1Model(data(.75,Wt3p1Data.RateLimitPolicy.NONNEGATIVE));
        model.initialize(25,0,1.0);
        double[] v=new double[3],rate=new double[3];
        model.evaluateStaggered(.0005,10,0,10,rate,0);
        assertEquals(0,rate[0],0,"outward pitch rate at upper limit");
        v[0]=100;v[1]=-100;v[2]=-10;
        model.applyStaggeredState(v,0,.0005);
        model.readStaggeredState(v,0);
        assertEquals(25,v[0],0);assertEquals(0,v[2],0);assertEquals(-100,v[1],0);
        model.step(.0005,0,0,1,0);
        assertThrows(IllegalStateException.class,()->model.readStaggeredState(v,0));
        assertThrows(IllegalStateException.class,()->model.evaluateStaggered(.0005,0,0,1,rate,0));
        model.initialize(1.5,0,1);
        model.readStaggeredState(v,0);
        assertEquals(1.5,v[0],0,"reinitialization clears an ordinary pending trial");
    }
}
