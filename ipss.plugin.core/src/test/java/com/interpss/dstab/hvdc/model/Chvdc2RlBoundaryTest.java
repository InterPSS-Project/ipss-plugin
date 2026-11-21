package com.interpss.dstab.hvdc.model;

import static org.junit.jupiter.api.Assertions.*;
import java.util.*;
import org.junit.jupiter.api.Test;

/** Public synthetic current-zero, stage-domain and ownership regressions. */
public class Chvdc2RlBoundaryTest {
    private static double[] constants(){return new double[]{50,150,90,5,.0005,50,150,110,.0005,.01,5,.005,.01,.005,.02,-.05,
        .95,.01,10,-10,.3,1.1,.4,.8,.05,.02,.1,.01,3500,500,10,.9,.035,.5,-.5,1.05,.85,.25,90,10,.85,.1,.9,.1,1500,500,500,0};}
    private static Chvdc2CoupledRuntime model(int[] flags,double enteredC,double preparedC) {
        double[] c=constants();c[47]=enteredC;
        var p=new Chvdc2Parameters("public",Chvdc2Parameters.Dialect.CHVDC2U1,flags,c);
        var r=new LccConverterBoundary(true,2,230000,1,1,5,1e8);
        var i=new LccConverterBoundary(false,2,230000,1,1,5,1e8);
        var m=new Chvdc2CoupledRuntime(p,r,i,new Chvdc2DcLine(5,2.5,preparedC,true),1,1);
        double vr=r.dcVoltageAtFiringAngle(1.03,1050,Math.toRadians(32)),vi=vr-5*1050;
        m.setBoundary(1.03,0,1.03,0,1050,0);m.initialize(vr,vi,0);return m;
    }
    private static Chvdc2CoupledRuntime model(){return model(new int[]{1,1,1},0,0);}
    private static double[] state(Chvdc2CoupledRuntime m){double[] x=new double[m.staggeredStateCount()];m.readStaggeredState(x,0);return x;}
    private static double current(Chvdc2CoupledRuntime m){return state(m)[15];}
    private static void advance(Chvdc2CoupledRuntime m,int local) {
        m.beginIntegrationInterval(.001);m.step(.001,0,local);m.step(.001,1,local);m.completeIntegrationInterval();
    }
    @Test void rawHeunQuadratureAvoidsProjectedPredictorRebound() {
        var m=model();m.setBoundary(.07,0,1.03,0,1050,0);
        double h=.0001;double[] x=state(m),f=new double[16];m.evaluateStaggered(0,f,0);
        x[15]=-.75*h*f[15];m.applyStaggeredState(x,0,0);m.evaluateStaggered(0,f,0);
        assertTrue(x[15]+h*f[15]<0);assertTrue(x[15]+.5*h*f[15]>0,
                "Zeroing the right quadrature rate would create the rejected rebound");
        m.step(h,0);assertEquals(0,current(m));
        m.step(h,1);assertEquals(0,current(m),"Retain the free right rate, then project the corrected coordinate");
    }
    @Test void entryHoldReleaseWithBothSingleAndLocalModifiedEuler() {
        for(int local:new int[]{1,10,20}) {
            var m=model();var before=state(m);
            m.setBoundary(.07,0,1.03,0,1050,0);
            for(int k=0;k<20;k++)advance(m,local);
            assertEquals(0,current(m));
            var held=state(m);assertNotEquals(before[0],held[0]);assertNotEquals(before[4],held[4]);
            double[] boundary=new double[10],f=new double[16];m.readBoundary(boundary,0);
            for(int k=2;k<8;k++)assertEquals(0,boundary[k],0.);
            assertFalse(m.inverterShorted(),"A numerical boundary cannot commit protection");
            m.evaluateStaggered(0,f,0);assertEquals(0,f[15]);
            m.setBoundary(1.03,0,1.03,0,1050,0);
            for(int k=0;k<10;k++)advance(m,local);
            assertTrue(current(m)>0,"Release follows the restored driving voltage");
        }
    }
    @Test void predictorCancellationAndOuterRollbackRestoreAllCoordinatesAndHistory() {
        var m=model();m.acceptBoundary(0,0);
        var states=m.getIntegrationStates();var history=m.getIntegrationHistories();
        m.setBoundary(.07,0,1.03,0,1050,0);
        m.beginTransaction();m.beginIntegrationInterval(.001);m.step(.001,0,10);
        m.cancelPrediction();assertEquals(states,m.getIntegrationStates());
        m.step(.001,0,10);m.step(.001,1,10);m.completeIntegrationInterval();m.rollbackTransaction();
        assertEquals(states,m.getIntegrationStates());assertEquals(history,m.getIntegrationHistories());
    }
    @Test void zeroDurationAndRepeatedObservationDoNotAcceptHistory() {
        var m=model();var x=state(m);x[15]=0;m.applyStaggeredState(x,0,0);
        m.setBoundary(.07,0,1.03,0,1050,0);
        var states=m.getIntegrationStates();var history=m.getIntegrationHistories();
        for(int k=0;k<3;k++){m.step(0,0);m.step(0,1);m.evaluateStaggered(0,new double[16],0);m.readBoundary(new double[10],0);}
        assertEquals(states,m.getIntegrationStates());assertEquals(history,m.getIntegrationHistories());
    }
    @Test void otherModesAndSharedNegativeProposalsRetainTheirFailGuards() {
        for(var m:List.of(model(new int[]{0,0,1},0,0),model(new int[]{1,1,1},1e-4,0),
                model(new int[]{1,1,1},1,1e-6))) {
            var before=m.getIntegrationStates();m.setBoundary(.07,0,1.03,0,1050,0);
            assertThrows(IllegalArgumentException.class,()->m.step(.01,0));assertEquals(before,m.getIntegrationStates());
        }
        var m=model();double[] x=state(m),before=x.clone();x[15]=-1;
        assertThrows(IllegalArgumentException.class,()->m.applyStaggeredState(x,0,.001));
        assertEquals(-1,x[15]);assertArrayEquals(before,state(m));
    }
    @Test void bareRlEquationIsStillSigned() {
        var line=new Chvdc2DcLine(5,2.5,0,true);line.initialize(505000,500000);
        line.setInput(1000,2000);line.applyStaggeredState(new double[]{-7},0,0);
        double[] rate=new double[1];line.evaluateStaggered(0,rate,0);
        assertEquals(-386,rate[0]);assertEquals(-7,line.rectifierAmperes());
    }
}
