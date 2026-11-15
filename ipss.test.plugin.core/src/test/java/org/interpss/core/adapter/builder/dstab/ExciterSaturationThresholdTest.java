package org.interpss.core.adapter.builder.dstab;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.interpss.dstab.controller.exc.exac1.Exac1Exciter;
import org.junit.jupiter.api.Test;

/** Independent synthetic curve and cutoff checks; no private case parameters. */
public class ExciterSaturationThresholdTest {
    @Test
    void equalFactorsRetainSignedFitAndIntroduceQualifiedCutoff() {
        // Equal .12 factors at E=2 and E=4 imply A=-sqrt(8).
        double cutoff=Math.sqrt(8.0);
        assertEquals(0.0, value(cutoff-1e-4,2,.12,4,.12,true));
        assertTrue(value(cutoff+1e-4,2,.12,4,.12,true)>.116);
        assertEquals(.12,value(4,2,.12,4,.12,true),2e-16);
        assertEquals(.12,value(4,2,.12,4,.12,false),2e-16);
        assertTrue(value(1,2,.12,4,.12,false)>.12);
    }

    @Test
    void unequalFactorsUseTheirOwnNegativeIntercept() {
        // Independent algebra gives A=(15-3*sqrt(20))/(3-sqrt(20)).
        double a=(15.0-3.0*Math.sqrt(20.0))/(3.0-Math.sqrt(20.0));
        assertTrue(a<0);
        assertEquals(0.0,value(-a-1e-4,3,.15,5,.20,true));
        assertTrue(value(-a+1e-4,3,.15,5,.20,true)>.116);
        assertEquals(.15,value(3,3,.15,5,.20,true),2e-16);
        assertEquals(.20,value(5,3,.15,5,.20,true),2e-16);
    }

    @Test
    void exactNegativeInterceptShowsClosedCutoffAndUnsmoothJump() {
        // These coefficients give exactly A=-2 and B=1/32.
        assertEquals(0.0,value(2,2,.25,8,.390625,true));
        assertEquals(0.0,value(Math.nextDown(2.0),2,.25,8,.390625,true));
        assertTrue(value(Math.nextUp(2.0),2,.25,8,.390625,true)>.249);
        assertEquals(.25,value(2,2,.25,8,.390625,false),0.0);
        assertEquals(.390625,value(8,2,.25,8,.390625,true),0.0);
        // Taking abs(A) inside the polynomial would incorrectly make this tiny.
        assertTrue(value(2.001,2,.25,8,.390625,true)>.25);
    }

    @Test
    void positiveInterceptIsBitwiseIdenticalInBothConventions() {
        // E1=2, S1=.1; E2=3, S2=.6 give A=1.5.
        for(double ve:new double[]{-1,0,.5,1.5,Math.nextUp(1.5),1.7,2,3,8}) {
            assertEquals(Double.doubleToRawLongBits(value(ve,2,.1,3,.6,false)),
                    Double.doubleToRawLongBits(value(ve,2,.1,3,.6,true)));
        }
        assertEquals(.1,value(2,2,.1,3,.6,true),2e-16);
        assertEquals(.6,value(3,2,.1,3,.6,true),4*Math.ulp(.6));
    }

    @Test
    void historicalEntryPointRetainsOriginalArithmeticAcrossCurveRegions() {
        double[][] profiles={{2,.12,4,.12},{3,.15,5,.2},{2,.1,3,.6},
                {2,.25,8,.390625},{0,.1,3,.2},{1,0,2,.2},{1,.2,2,.1}};
        for(double[] p:profiles)for(double ve:new double[]{-2,0,.01,.5,1,2,3,4,8,100}) {
            double before=historical(ve,p[0],p[1],p[2],p[3]);
            assertEquals(Double.doubleToRawLongBits(before),Double.doubleToRawLongBits(
                    Exac1Exciter.saturation(ve,p[0],p[1],p[2],p[3])));
            assertEquals(Double.doubleToRawLongBits(before),Double.doubleToRawLongBits(
                    value(ve,p[0],p[1],p[2],p[3],false)));
        }
    }

    @Test
    void nonpositiveInputsAndDegenerateFitRetainZeroBehavior() {
        for(boolean absolute:new boolean[]{false,true}) {
            assertEquals(0.0,value(0,2,.12,4,.12,absolute));
            assertEquals(0.0,value(-1,2,.12,4,.12,absolute));
            assertEquals(0.0,value(2,0,.12,4,.12,absolute));
            assertEquals(0.0,value(2,2,0,4,.12,absolute));
            assertEquals(0.0,value(2,2,.12,0,.12,absolute));
            assertEquals(0.0,value(2,2,.12,4,0,absolute));
            assertEquals(0.0,value(2,1,.2,2,.1,absolute));
        }
    }

    private static double value(double v,double e1,double s1,double e2,double s2,boolean absolute) {
        return Exac1Exciter.saturation(v,e1,s1,e2,s2,absolute);
    }

    /** Frozen pre-overload arithmetic solely guards compatibility, not the new convention. */
    private static double historical(double ve,double e1,double se1,double e2,double se2) {
        if(e1<=0||e2<=0||se1<=0||se2<=0)return 0.0;
        double ratio=Math.sqrt(e2*se2/(e1*se1));
        if(!Double.isFinite(ratio)||Math.abs(1-ratio)<1e-12)return 0.0;
        double a=(e2-e1*ratio)/(1-ratio);
        if(ve<=a||ve<=0)return 0.0;
        double d=(e1-a)*(e1-a);
        return d>0?e1*se1*(ve-a)*(ve-a)/(d*ve):0.0;
    }
}
