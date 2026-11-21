package org.interpss.core.dstab.dynLoad;

import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Test;
import org.apache.commons.math3.complex.Complex;
import org.interpss.dstab.dynLoad.impl.InductionMotorImpl;
import com.interpss.dstab.*;
import com.interpss.dstab.algo.DynamicSimuMethod;

/** Synthetic fixed-voltage tests separate local equations from network iterations. */
public class InductionMotorLocalSubstepTest {
    private InductionMotorImpl motor() throws Exception {
        var net=DStabObjectFactory.createDStabilityNetwork();net.setBaseKva(100000);net.setFrequency(60);
        var bus=(DStabBus)DStabObjectFactory.createDStabBus("B",net).get();
        bus.setLoadCode(com.interpss.core.aclf.AclfLoadCode.CONST_P);bus.setBaseVoltage(1000);
        bus.setVoltage(Complex.ONE);bus.setInitLoad(new Complex(.6,.2));
        var m=new InductionMotorImpl(bus,"M");m.setXm(3);m.setXl(.07);m.setRa(.032);
        m.setXr1(.2);m.setRr1(.01);m.setXr2(.15);m.setRr2(.02);m.setTwoAxisModel(true);
        m.setMvaBase(50);m.setH(1);m.setLoadPercent(50);m.setRatedVoltage(1000);
        m.setInitLoadPQ(new Complex(.3,.12));assertTrue(m.initStates());return m;
    }
    private static void step(InductionMotorImpl m,double h) {
        assertTrue(m.nextStep(h,DynamicSimuMethod.MODIFIED_EULER,0));
        assertTrue(m.nextStep(h,DynamicSimuMethod.MODIFIED_EULER,1));
    }
    private static double[] state(InductionMotorImpl m){var x=new double[5];m.readStaggeredState(x,0);return x;}
    private static double error(double[] x,double[] y){double e=0;for(int i=0;i<x.length;i++)e=Math.max(e,Math.abs(x[i]-y[i]));return e;}
    private static Complex voltage(double f){return new Complex(1-.08*f,-.02*f);}
    /** Independent RK4 quadrature of the existing pure motor RHS under a voltage ramp. */
    private static double[] oracle(InductionMotorImpl m,double h) {
        double[] x=state(m),trial=new double[5],a=new double[5],b=new double[5],c=new double[5],d=new double[5];
        int n=512;double dt=h/n;
        for(int k=0;k<n;k++) {
            rate(m,x,(double)k/n,dt,a);
            for(int i=0;i<5;i++)trial[i]=x[i]+.5*dt*a[i];rate(m,trial,(k+.5)/n,dt,b);
            for(int i=0;i<5;i++)trial[i]=x[i]+.5*dt*b[i];rate(m,trial,(k+.5)/n,dt,c);
            for(int i=0;i<5;i++)trial[i]=x[i]+dt*c[i];rate(m,trial,(k+1.)/n,dt,d);
            for(int i=0;i<5;i++)x[i]+=dt*(a[i]+2*b[i]+2*c[i]+d[i])/6;
        }
        return x;
    }
    private static void rate(InductionMotorImpl m,double[] x,double fraction,double dt,double[] rate) {
        m.getDStabBus().setVoltage(voltage(fraction));m.applyStaggeredState(x,0,dt);m.evaluateStaggered(dt,rate,0);
    }
    @Test void localCorrectorReplaysAcceptedOriginAndConvergesToIndependentRk4() throws Exception {
        double h=.0033333;double[] reference=oracle(motor(),h);double previous=Double.POSITIVE_INFINITY;
        for(int n:new int[]{4,8,16}) {
            var m=motor();m.setLocalIntegrationMaximumStepSec(h/n);m.getDStabBus().setVoltage(voltage(0));
            m.nextStep(h,DynamicSimuMethod.MODIFIED_EULER,0);m.getDStabBus().setVoltage(voltage(1));
            m.nextStep(h,DynamicSimuMethod.MODIFIED_EULER,1);
            double e=error(state(m),reference);assertTrue(e<previous*.4,"second-order refinement: "+e+" previous "+previous);
            previous=e;assertEquals(n,m.getLastLocalIntegrationSteps());
        }
        assertTrue(previous<1e-5,"resolved local RHS error "+previous);
    }
    @Test void defaultAndOneIntervalRetainOriginalArithmetic() throws Exception {
        var a=motor();var b=motor();b.setLocalIntegrationMaximumStepSec(.01);
        for(int i=0;i<25;i++) {
            a.getDStabBus().setVoltage(voltage(.4));b.getDStabBus().setVoltage(voltage(.4));step(a,.001);step(b,.001);
            assertArrayEquals(state(a),state(b),0.);
        }
    }
    @Test void pendingCorrectorSurvivesValueCopyAndZeroTimeObservation() throws Exception {
        var a=motor();double h=.0033333;a.setLocalIntegrationMaximumStepSec(.0005);
        a.nextStep(h,DynamicSimuMethod.MODIFIED_EULER,0);
        var gson=new com.google.gson.Gson();var saved=gson.fromJson(gson.toJson(new InductionMotorImpl.ModelState(a)),InductionMotorImpl.ModelState.class);
        var b=saved.create(motor().getDStabBus());
        var before=state(a);a.nextStep(0,DynamicSimuMethod.MODIFIED_EULER,0);assertArrayEquals(before,state(a),0.);
        a.getDStabBus().setVoltage(voltage(1));b.getDStabBus().setVoltage(voltage(1));
        a.nextStep(h,DynamicSimuMethod.MODIFIED_EULER,1);b.nextStep(h,DynamicSimuMethod.MODIFIED_EULER,1);
        assertArrayEquals(state(a),state(b),0.);assertEquals(a.getIntegrationHistories(),b.getIntegrationHistories());
    }
    @Test void internalSubstepsDoNotAdvanceProtectionTimers() throws Exception {
        var m=motor();double h=.0033333;m.setLocalIntegrationMaximumStepSec(.00025);
        m.setVtr1(.8);m.setTtr1(.02);m.setFtr1(.2);m.getDStabBus().setVoltage(new Complex(.7,0));
        var before=m.getIntegrationHistories();step(m,h);assertEquals(before,m.getIntegrationHistories());
        m.afterStep(h);assertEquals(h,m.getIntegrationHistories().get("TripTimer1"),1e-12);
    }
    @Test void staggeredLocalResolutionPreservesInitialCoordinatesAndProtectionHistories() throws Exception {
        var m=motor();double[] before=state(m);var histories=m.getIntegrationHistories();
        m.prepareStaggeredIntegration(1./240);
        assertTrue(m.staggeredLocalStepCount()>=1);
        assertTrue(m.staggeredLocalStepCount()<=com.interpss.dstab.algo.dynamic.ControllerStepStability.MAXIMUM_LOCAL_STEPS);
        assertArrayEquals(before,state(m),0.);assertEquals(histories,m.getIntegrationHistories());
        m.setVtr1(.8);m.setTtr1(.02);m.setFtr1(.2);m.getDStabBus().setVoltage(new Complex(.7,0));
        m.beginStaggeredLocalInterval(1./240);double[] left=new double[5],again=new double[5];
        m.evaluateStaggered(.0001,left,0);m.getDStabBus().setVoltage(Complex.ONE);
        m.evaluateStaggered(.0001,again,0);assertArrayEquals(left,again,0.,"Physical input is held through local stages");
        assertEquals(histories,m.getIntegrationHistories());m.completeStaggeredStep(1./240);
        m.evaluateStaggered(.0001,again,0);assertTrue(error(left,again)>1e-5,"Outer endpoint releases the held voltage");
    }
    @Test void invalidOrMismatchedStagesAreRejected() throws Exception {
        var m=motor();assertThrows(IllegalArgumentException.class,()->m.setLocalIntegrationMaximumStepSec(Double.NaN));
        m.setLocalIntegrationMaximumStepSec(.0005);
        assertThrows(IllegalStateException.class,()->m.nextStep(.003,DynamicSimuMethod.MODIFIED_EULER,1));
        m.nextStep(.003,DynamicSimuMethod.MODIFIED_EULER,0);
        assertThrows(IllegalStateException.class,()->m.nextStep(.002,DynamicSimuMethod.MODIFIED_EULER,1));
        assertThrows(IllegalArgumentException.class,()->m.setLocalIntegrationMaximumStepSec(0));
    }
}
