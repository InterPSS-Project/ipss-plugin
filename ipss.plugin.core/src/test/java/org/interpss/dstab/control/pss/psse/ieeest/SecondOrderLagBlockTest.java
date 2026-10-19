package org.interpss.dstab.control.pss.psse.ieeest;

import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Test;

/** The two endpoint slopes must use one simultaneous predicted state vector. */
class SecondOrderLagBlockTest {
    @Test void correctorUsesPredictedOutputAndRateBeforeUpdatingEither() {
        var block=new SecondOrderLagBlock(2,.4,.2);assertTrue(block.initStateY0(.3));
        double h=.01,u0=.4,u1=-.2,y0=.3,r0=0;
        double dy0=r0,dr0=(2*u0-y0-.4*r0)/.2;
        double yp=y0+h*dy0,rp=r0+h*dr0;
        block.eulerStep1(u0,h);
        assertEquals(yp,block.getY(),0);assertEquals(rp,block.getRate(),0);
        double dy1=rp,dr1=(2*u1-yp-.4*rp)/.2;
        block.eulerStep2(u1,h);
        assertEquals(y0+.5*h*(dy0+dy1),block.getY(),1e-15);
        assertEquals(r0+.5*h*(dr0+dr1),block.getRate(),1e-15,
                "Rate correction must not read an already-corrected output");
    }

    @Test void everyStageMatchesAnIndependentTwoStateRecurrence() {
        for(double h:new double[]{.001,.0005})for(double a:new double[]{0,.061,.4}) {
            double k=1.7,b=.0017,y=.2,rate=0;
            var block=new SecondOrderLagBlock(k,a,b);assertTrue(block.initStateY0(y));
            for(int tick=0;tick<200;tick++) {
                double u0=.3*Math.sin(tick*.13),u1=.2*Math.cos(tick*.17);
                double d0=(k*u0-y-a*rate)/b,yp=y+h*rate,rp=rate+h*d0;
                block.eulerStep1(u0,h);
                assertEquals(yp,block.getY(),1e-13);assertEquals(rp,block.getRate(),1e-13);
                double d1=(k*u1-yp-a*rp)/b;
                y+=.5*h*(rate+rp);rate+=.5*h*(d0+d1);
                block.eulerStep2(u1,h);
                assertEquals(y,block.getY(),1e-13);assertEquals(rate,block.getRate(),1e-13);
            }
        }
    }

    @Test void firstOrderLimitRemainsBitIdentical() {
        for(double b:new double[]{0,1e-13,1e-12}) {
            var block=new SecondOrderLagBlock(2,.02,b);double expected=.4;
            assertTrue(block.initStateY0(expected));
            for(int tick=0;tick<200;tick++) {
                double h=.0005,u0=Math.sin(tick*.1),u1=Math.cos(tick*.07);
                double old=(2*u0-expected)/.02,predicted=expected+old*h;
                block.eulerStep1(u0,h);
                assertEquals(Double.doubleToLongBits(predicted),Double.doubleToLongBits(block.getY()));
                expected=predicted+.5*((2*u1-predicted)/.02-old)*h;
                block.eulerStep2(u1,h);
                assertEquals(Double.doubleToLongBits(expected),Double.doubleToLongBits(block.getY()));
                assertEquals(0,block.getRate(),0);
            }
        }
    }

    @Test void algebraicLimitStillReturnsTheCurrentInputWithoutDynamicAdvance() {
        var block=new SecondOrderLagBlock(2,0,0);assertTrue(block.initStateU0(.3));
        block.eulerStep1(-.2,.01);assertEquals(-.4,block.getY(),0);
        block.eulerStep2(.7,.01);assertEquals(1.4,block.getY(),0);assertEquals(0,block.getRate(),0);
    }

    @Test void zeroDurationCallsDoNotAdvanceOrPoisonTheNextStep() {
        var a=new SecondOrderLagBlock(1,.061,.0017);var b=new SecondOrderLagBlock(1,.061,.0017);
        assertTrue(a.initStateY0(.4));assertTrue(b.initStateY0(.4));
        a.eulerStep1(.9,0);a.eulerStep2(-.3,0);
        assertEquals(.4,a.getY(),0);assertEquals(0,a.getRate(),0);
        a.eulerStep1(.2,.001);a.eulerStep2(.3,.001);
        b.eulerStep1(.2,.001);b.eulerStep2(.3,.001);
        assertEquals(b.getY(),a.getY(),0);assertEquals(b.getRate(),a.getRate(),0);
    }

    @Test void initializedEquilibriumAndPureRateReadsRemainStationary() {
        var block=new SecondOrderLagBlock(2,.061,.0017);assertTrue(block.initStateY0(.3));
        for(int i=0;i<100;i++) {
            assertEquals(0,block.getRate(),0);block.eulerStep1(.15,.0005);block.eulerStep2(.15,.0005);
            assertEquals(.3,block.getY(),0);assertEquals(0,block.getRate(),0);
        }
    }

    @Test void undampedOscillatorRefinesAgainstTheAnalyticSolution() {
        double coarse=oscillatorError(.02),fine=oscillatorError(.01);
        assertTrue(fine<.27*coarse,"The vector method must retain second-order convergence");
    }
    private static double oscillatorError(double h) {
        var block=new SecondOrderLagBlock(1,0,1);assertTrue(block.initStateY0(1));
        double maximum=0;
        for(int i=1;i<=Math.round(1/h);i++) {
            block.eulerStep1(0,h);block.eulerStep2(0,h);double time=i*h;
            double error=Math.hypot(block.getY()-Math.cos(time),block.getRate()+Math.sin(time));
            maximum=Math.max(maximum,error);
            // Taylor remainder and ||I+h*A+h^2*A^2/2|| <= exp(h), ||A||=1.
            assertTrue(error<=time*h*h*Math.exp(time+h)/6+1e-14);
        }
        return maximum;
    }
}
