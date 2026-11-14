package org.interpss.core.dstab;

import static org.junit.jupiter.api.Assertions.*;
import java.lang.management.ManagementFactory;
import com.interpss.dstab.hvdc.model.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/** Physical units, independent equations and common integration ownership for order controls. */
public class Chvdc2OrderControlsTest {
    static Chvdc2OrderControls model(double[] c,boolean ac){return new Chvdc2Parameters("link",Chvdc2Parameters.Dialect.CHVDC2U1,new int[]{ac?1:0,0,1},c).prepareOrderControls(.9,.95);}
    static void input(Chvdc2OrderControls m,double current,double vr,double vi,double ar,double ai,double reference){m.setInput(current,current,vr,vi,ar,ai,reference,0);}
    @ParameterizedTest @ValueSource(booleans={false,true})
    void independentExponentialMeasurementConvergesAtSecondOrder(boolean shared){
        double coarse=exponential(.0001,shared),fine=exponential(.00005,shared);
        assertTrue(coarse/fine>3.9&&coarse/fine<4.2);assertTrue(fine<1e-6);
    }
    private static double exponential(double h,boolean shared){
        var m=model(Chvdc2ParametersTest.constants(),false);input(m,1050,505,500,1.03,1.02,1050);m.initialize(140);
        assertEquals(140,m.inverterMaximumDegrees(),1e-12);input(m,1400,505,500,1.04,1.01,1050);
        double[] x=new double[10],f=new double[10],old=new double[10];
        for(int n=0;n<Math.round(.04/h);n++){
            if(shared){m.readStaggeredState(x,0);m.evaluateStaggered(h,f,0);for(int i=0;i<10;i++)x[i]+=h*(n==0?f[i]:1.5*f[i]-.5*old[i]);m.applyStaggeredState(x,0,h);System.arraycopy(f,0,old,0,10);}
            else{m.step(h,0);m.step(h,1);}
        }
        m.readStaggeredState(x,0);double e=Math.exp(-4);assertEquals(1.04-.01*e,x[6],2e-7);assertEquals(1.01+.01*e,x[7],2e-7);
        assertEquals(1050,m.rectifierCurrentOrderAmperes(),1e-10);assertEquals(1050,m.inverterCurrentOrderAmperes(),1e-10);
        return Math.abs(x[0]-(.4-.1*e));
    }
    @Test void asymmetricVoltageLagAndPiecewiseCurrentCurveHaveExplicitUnits(){
        var c=Chvdc2ParametersTest.constants();var m=model(c,false);input(m,1050,500,500,1,1,3500);m.initialize(140);
        input(m,1050,300,550,1,1,3500);double[] f=new double[10];m.evaluateStaggered(.001,f,0);
        assertEquals(-.4/c[25],f[2],1e-12);assertEquals(.1/c[26],f[3],1e-12);
        double[] x=new double[10];m.readStaggeredState(x,0);x[2]=.6;x[3]=.8;m.applyStaggeredState(x,0,0);
        assertEquals(.7*3500,m.rectifierCurrentOrderAmperes(),1e-9);assertEquals(3500,m.inverterCurrentOrderAmperes(),1e-9);
        x[2]=.2;x[3]=.4;m.applyStaggeredState(x,0,0);assertEquals(.3*3500,m.rectifierCurrentOrderAmperes(),1e-9);assertEquals(.3*3500,m.inverterCurrentOrderAmperes(),1e-9);
    }
    @ParameterizedTest @ValueSource(booleans={false,true})
    void acLimitsInitializeAtUpperBoundAndReleaseWithoutAGetterCommit(boolean shared){
        var c=Chvdc2ParametersTest.constants();c[17]=0;c[42]=1.03;
        var m=model(c,true);input(m,1050,500,500,1,1.01,3500);m.initialize(140);
        assertEquals(1,m.acReferencePu());double[] x=new double[10],f=new double[10];m.readStaggeredState(x,0);assertEquals(c[35],x[8]);assertEquals(c[35],x[9]);
        input(m,1050,500,500,0,0,3500);var before=m.getIntegrationStates();
        m.evaluateStaggered(.001,f,0);assertEquals(c[34]/c[37],f[8]);assertEquals(c[34]/c[37],f[9]);
        for(int i=0;i<5;i++){m.rectifierCurrentOrderAmperes();m.inverterMaximumDegrees();m.readPublishedStates(f,0);}assertEquals(before,m.getIntegrationStates());
        if(shared){m.evaluateStaggered(.001,f,0);for(int i=0;i<10;i++)x[i]+=.001*f[i];m.applyStaggeredState(x,0,.001);}else{m.step(.001,0);m.step(.001,1);}
        m.readStaggeredState(x,0);assertEquals(c[35]+.001*c[34]/c[37],x[8],1e-12);
        x[8]=x[9]=c[36];m.applyStaggeredState(x,0,0);m.evaluateStaggered(.001,f,0);assertEquals(0,f[8]);assertEquals(c[36]*3500,m.rectifierCurrentOrderAmperes(),1e-10);
        input(m,1050,500,500,2,2,3500);m.evaluateStaggered(.001,f,0);assertEquals(c[33]/c[37],f[8]);
    }
    @Test void algebraicBypassesAndInputChangesPreserveNumericalOwnership(){
        var c=Chvdc2ParametersTest.constants();c[11]=c[13]=c[17]=c[25]=c[27]=0;
        var m=model(c,false);input(m,1050,500,500,1,1,1050);m.initialize(140);var old=m.getIntegrationStates();
        m.setInput(1400,1750,250,300,.8,.9,1000,350);double[] x=new double[10];m.readPublishedStates(x,0);
        assertEquals(.4,x[0],1e-12);assertEquals(.5,x[1],1e-12);assertEquals(1,x[2]);assertEquals(1,x[3]);
        assertEquals(5*1350/3500.,x[4],1e-12);assertEquals(.01*(.5-1350/3500.),x[5],1e-12);assertEquals(old,m.getIntegrationStates());
        m.step(.0001,0);assertThrows(IllegalStateException.class,()->m.applyStaggeredState(x,0,.0001));m.cancelPrediction();assertEquals(old,m.getIntegrationStates());
        assertThrows(IllegalArgumentException.class,()->m.setInput(1,2,3,4,5,Double.NaN,6,7));m.readPublishedStates(x,0);assertEquals(.9,x[7]);
        m.applyStaggeredState(x,0,0);m.readStaggeredState(x,0);assertEquals(.4,x[0],1e-12);assertEquals(.5,x[2]);assertEquals(.6,x[3]);
        var accepted=m.getIntegrationStates();x[9]=Double.NaN;assertThrows(IllegalArgumentException.class,()->m.applyStaggeredState(x,0,.001));assertEquals(accepted,m.getIntegrationStates());
    }
    @ParameterizedTest @ValueSource(booleans={false,true})
    void warmedStateAndOutputKernelsAllocateNoPerStepObjects(boolean shared){
        var b=ManagementFactory.getThreadMXBean();Assumptions.assumeTrue(b instanceof com.sun.management.ThreadMXBean);
        var memory=(com.sun.management.ThreadMXBean)b;Assumptions.assumeTrue(memory.isThreadAllocatedMemorySupported());memory.setThreadAllocatedMemoryEnabled(true);
        var m=model(Chvdc2ParametersTest.constants(),true);input(m,1050,505,500,1.03,1.02,1050);m.initialize(140);
        double[] x=new double[10],f=new double[10];long id=Thread.currentThread().threadId();
        run(m,shared,x,f,100000);long start=memory.getThreadAllocatedBytes(id);run(m,shared,x,f,100000);long allocated=memory.getThreadAllocatedBytes(id)-start;
        assertEquals(0,allocated);
    }
    private static void run(Chvdc2OrderControls m,boolean shared,double[] x,double[] f,int count){
        for(int n=0;n<count;n++){
            if(shared){m.readStaggeredState(x,0);m.evaluateStaggered(.0001,f,0);for(int i=0;i<10;i++)x[i]+=.0001*f[i];m.applyStaggeredState(x,0,.0001);}
            else{m.step(.0001,0);m.step(.0001,1);}
            m.rectifierCurrentOrderAmperes();m.inverterCurrentOrderAmperes();m.inverterMaximumDegrees();
        }
    }
}
