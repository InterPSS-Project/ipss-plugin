package org.interpss.dstab.renewable;

import static org.junit.jupiter.api.Assertions.*;
import static org.interpss.dstab.renewable.RepccData.Parameter.*;
import java.util.*;
import org.apache.commons.math3.complex.Complex;
import org.junit.jupiter.api.Test;

class RepccKernelTest {
    private static double[] constants(){return new double[]{.02,1,5,0,.05,.7,0,0,0,.05,-.05,0,0,1,-1,1,.1,.02,
            -.0006,.0006,99,-99,2,-1,.1,20,20,1.08,.95,1,-1,.2,-.2,.5,-.5,.3,-.3,.8,-.8,1,-1,1,-1,.02,-.25,-.5,.3,.4,.5,.1,.06,120,.003,.02,.8,.015,-.015};}
    private static RepccData data(double[] c,int mode){return new RepccData(3,new RepccData.Branch(2,3,"1"),1,mode,1,1,1,0,
            new RepccData.Branch(0,0,"0"),Collections.nCopies(10,new RepccData.Shunt(0,"0")),3,1,c);}
    private static RepccKernel kernel(double[] c,int mode,boolean qcmd){var k=new RepccKernel(data(c,mode),qcmd);
        k.initialize(new RepccInitialization.Measurements(Complex.ONE,new Complex(.4,-.2),.4,.2,.4,.2));return k;}
    private static RepccKernel.Input in(double voltage){return new RepccKernel.Input(new Complex(voltage,0),new Complex(.4,-.2),.4,.2,0,1);}
    private static RepccKernel.References refs(RepccKernel k,double v,double pAux){var r=k.references();return new RepccKernel.References(v,r.reactive(),r.frequency(),r.active(),r.powerFactor(),r.voltageAux(),pAux);}
    @Test void equilibriumAndImmutableMonitoring(){
        var k=kernel(constants(),1,true);var initial=k.states();
        for(int i=0;i<1000;i++)k.step(.0005,in(1));
        assertArrayEquals(initial,k.states(),1e-14);assertEquals(.5,k.time(),1e-14);assertEquals(13,k.getNamedStates().size());
        initial[0]=0;assertEquals(1,k.states()[0]);
        assertThrows(UnsupportedOperationException.class,()->k.getNamedStates().clear());
        double time=k.time();for(int i=0;i<10;i++){k.output(in(1));k.getNamedStates();k.step(0,in(1),0);}assertEquals(time,k.time());
    }
    @Test void predictorAndCorrectorAreExplicitModifiedEuler(){
        var k=kernel(constants(),1,true);k.step(.001,in(.98),0);
        assertEquals(.999,k.states()[7],1e-15);assertEquals(.2,k.states()[2],1e-15);assertEquals(0,k.time());
        k.step(.001,in(.98),1);
        assertEquals(.999025,k.states()[7],1e-15);assertEquals(.2000025,k.states()[2],1e-15);
        assertEquals(.20001,k.states()[3],1e-15);assertEquals(.001,k.time());
    }
    @Test void proportionalLimitCrossingDoesNotRefitIntegralState(){
        double[] c=constants();c[PIMAX.ordinal()]=.005;c[PIMIN.ordinal()]=-.005;c[QVMAX.ordinal()]=.21;
        var k=kernel(c,1,true);k.setReferences(refs(k,1.05,.04));
        assertEquals(.005,k.output(in(1)).activePi(),1e-15);assertEquals(.21,k.output(in(1)).reactivePi(),1e-15);
        k.step(.001,in(1));assertEquals(0,k.states()[5]);assertEquals(.2,k.states()[2]);
        k.setReferences(refs(k,.99,-.04));k.step(.001,in(1));assertTrue(k.states()[2]<.2,"Reversal releases the reactive integrator");
    }
    @Test void inactivePiBoundsLeaveTheLocalDerivativeUnchanged(){
        var k=kernel(constants(),1,true);k.setReferences(refs(k,1,.04));k.step(.001,in(1),0);
        assertEquals(.000004,k.states()[5],1e-15);
    }
    @Test void activeErrorLimitIsAfterFeedbackAndNotOnFeedforward(){
        double[] c=constants();c[PFREMAX.ordinal()]=.02;c[PFREMIN.ordinal()]=-.02;
        var k=kernel(c,1,true);k.setReferences(refs(k,1,.05));
        assertEquals(.02,k.output(in(1)).activePi(),1e-15);
        k.step(.001,in(1),0);assertEquals(.4007,k.states()[6],1e-15);
    }
    @Test void freezeHoldsOnlyTheFourSpecifiedStatesAndRecoveryIsTimed(){
        double[] c=constants();c[TFLTR.ordinal()]=0;
        var k=kernel(c,0,true);var r=k.references();k.setReferences(new RepccKernel.References(r.voltage(),.4,0,.5,0,0,0));
        double[] initial=k.states();k.step(.001,in(.5));
        assertTrue(k.output(in(.5)).frozen());for(int i:new int[]{2,3,5,6})assertEquals(initial[i],k.states()[i]);
        assertTrue(k.states()[10]>.4);assertTrue(k.states()[11]>.2);
        k.step(.001,in(1));assertEquals(.002,k.output(in(1)).recoveryStart(),1e-15);
        for(int n=0;n<2;n++)k.step(.001,in(1));for(int i:new int[]{2,3,5,6})assertEquals(initial[i],k.states()[i]);
        k.step(.001,in(.5));assertEquals(0,k.output(in(.5)).recoveryStart());
        k.step(.001,in(1));assertEquals(.006,k.output(in(1)).recoveryStart(),1e-15);
        for(int n=0;n<5;n++)k.step(.001,in(1));assertFalse(k.output(in(1)).frozen());assertEquals(0,k.output(in(1)).recoveryStart());
    }
    @Test void frequencyVoltageGateUsesDeviationZeroAndErrorPolarity(){
        double[] c=constants();c[TFRQ.ordinal()]=0;
        var k=kernel(c,1,true);var low=new RepccKernel.Input(Complex.ONE,Complex.ZERO,.4,.2,-.02,.5);
        k.step(0,low,0);assertEquals(0,k.states()[9]);assertEquals(0,k.output(low).frequencyError());
        var normal=new RepccKernel.Input(Complex.ONE,Complex.ZERO,.4,.2,-.02,1);
        k.step(0,normal,0);assertEquals(-.02,k.states()[9]);assertEquals(.015,k.output(normal).frequencyError());
        assertEquals(.0194,k.output(normal).frequencyDeadband(),1e-15);
        assertEquals(.3,k.output(normal).activePi(),1e-15);
    }
    @Test void reactiveOutputRateLimitIsNotAppliedToVoltageCommand(){
        double[] c=constants();c[TFT.ordinal()]=.025;
        var q=kernel(c,1,true);var v=kernel(c,1,false);
        q.setReferences(refs(q,1.05,0));v.setReferences(refs(v,1.05,0));
        assertEquals(.2,q.output(in(1)).reactive());assertEquals(.225,v.output(in(1)).reactive(),1e-15);
    }
    @Test void zeroTimeBypassesAndStageValidation(){
        double[] c=constants();c[TFLTR.ordinal()]=c[TP.ordinal()]=c[TC.ordinal()]=c[TFRQ.ordinal()]=0;
        var k=kernel(c,1,true);k.step(0,in(.99),0);assertEquals(.99,k.states()[0]);assertEquals(.99,k.states()[7]);assertEquals(0,k.time());
        assertThrows(IllegalStateException.class,()->k.step(.001,in(1),1));
        k.step(.001,in(1),0);assertThrows(IllegalStateException.class,()->k.step(.001,in(1),0));
        assertThrows(IllegalStateException.class,()->k.step(.002,in(1),1));k.step(.001,in(1),1);
        assertThrows(IllegalArgumentException.class,()->k.step(-.001,in(1),0));
        assertThrows(IllegalArgumentException.class,()->k.step(.001,in(1),2));
    }
}
