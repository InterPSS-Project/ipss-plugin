package org.interpss.dstab.renewable;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class RepcaReactiveReferenceLimitTest {
    private static final double INITIAL_Q = .1145341694355011;

    @Test
    void absoluteUpperLimitIncludesInitialReferenceAndFreezesOutwardIntegral() {
        for (int method=0;method<3;method++) {
            Repca1Model model=model();
            model.initialize(.625,INITIAL_Q,1.0);
            model.setReactiveStateOffset(INITIAL_Q);
            for (int step=0;step<200;step++) {
                advance(model,method,.87);
                assertTrue(INITIAL_Q+model.getQref()<=.12+1.e-14,"method "+method);
            }
            assertEquals(.12,INITIAL_Q+model.getReactiveControlOutput(),1.e-14,"method "+method);
            assertEquals(0.,model.getReactiveControlIntegral(),1.e-14,"method "+method);
        }
    }

    @Test
    void absoluteLowerLimitUsesTheSameCoordinateAndInitializesAtEquilibrium() {
        for (int method=0;method<3;method++) {
            Repca1Model model=model();
            model.initialize(.625,INITIAL_Q,1.0);
            model.setReactiveStateOffset(INITIAL_Q);
            advance(model,method,1.0);
            assertEquals(0.,model.getQref(),0.);
            assertEquals(0.,model.getReactiveControlIntegral(),0.);
            for (int step=0;step<200;step++) advance(model,method,1.13);
            assertEquals(-.12,INITIAL_Q+model.getReactiveControlOutput(),1.e-14,"method "+method);
            double integralAtBound=model.getReactiveControlIntegral();
            for (int step=0;step<20;step++) advance(model,method,1.13);
            assertEquals(integralAtBound,model.getReactiveControlIntegral(),0.,"method "+method);
        }
    }

    private static void advance(Repca1Model model,int method,double voltage) {
        if (method==0) model.step(.0005,.625,INITIAL_Q,voltage,1.0);
        else if (method==1) {
            model.step(.0005,.625,INITIAL_Q,voltage,1.0,0);
            model.step(.0005,.625,INITIAL_Q,voltage,1.0,1);
        } else model.stepTwoStep(.0005,.625,INITIAL_Q,voltage,1.0);
    }

    private static Repca1Model model() {
        return new Repca1Model(new Repca1Data(0,0,0,"1",0,1,0,
                .02,12.,3.,0.,1.03,.7,0.,0.,1.,1.,-1.,0.,0.,.12,-.12,
                .3,.08,.02,0.,0.,1.,-1.,2.,0.,.1,20.,0.,1));
    }
}
