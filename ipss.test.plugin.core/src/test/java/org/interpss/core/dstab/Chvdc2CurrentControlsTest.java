package org.interpss.core.dstab;

import static org.junit.jupiter.api.Assertions.*;
import java.lang.management.ManagementFactory;
import org.interpss.dstab.hvdc.*;
import org.interpss.dstab.hvdc.Chvdc2Parameters.Dialect;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/** Independent analytic and explicit stage/limiter contracts for the shared current-control kernel. */
public class Chvdc2CurrentControlsTest {
    static Chvdc2CurrentControls model(double[] c){return new Chvdc2Parameters("link",Dialect.CHVDC2U1,new int[]{0,0,1},c).prepareCurrentControls();}
    @ParameterizedTest @ValueSource(booleans={false,true})
    void bothMethodsConvergeToRampDrivenAngleLag(boolean shared){
        double coarse=analytic(.0002,shared),fine=analytic(.0001,shared);
        assertTrue(coarse/fine>3.8&&coarse/fine<4.2,"Second-order independent analytic convergence");assertTrue(fine<1e-5);
    }
    private static double analytic(double h,boolean shared){
        double[] c=Chvdc2ParametersTest.constants();c[4]=.02;c[8]=.03;
        var m=model(c);m.setInput(.3,.3,1050,1400,0,0,5,150);m.initialize(35,130);
        m.setInput(.32,.3,1050,1400,0,0,5,150);double[] x=new double[4],f=new double[4],old=new double[4],trial=new double[4];
        for(int n=0;n<Math.round(.05/h);n++){
            if(shared){
                m.readStaggeredState(x,0);m.evaluateStaggered(h,f,0);
                for(int i=0;i<4;i++)trial[i]=x[i]+h*(n==0?f[i]:1.5*f[i]-.5*old[i]);
                m.applyStaggeredState(trial,0,h);System.arraycopy(f,0,old,0,4);
            }else{m.step(h,0);m.step(h,1);}
        }
        m.readStaggeredState(x,0);assertEquals(35+150*.02*.05,x[0],1e-10);assertEquals(130,x[3],1e-10);
        double expected=35+150*.02*(.05-.02)+50*.02+(150*.02*.02-50*.02)*Math.exp(-.05/.02);
        return Math.abs(x[1]-expected);
    }
    @ParameterizedTest @ValueSource(booleans={false,true})
    void positionLimitsBlockOutwardMotionAndReleaseInward(boolean shared){
        var m=model(Chvdc2ParametersTest.constants());m.setInput(.3,.3,1050,1400,0,0,5,150);m.initialize(90,110);
        m.setInput(.32,.28,1050,1400,0,0,5,150);double[] f=new double[4],x=new double[4];m.evaluateStaggered(.001,f,0);
        assertArrayEquals(new double[4],f,1e-10);
        m.setInput(.28,.32,1050,1400,0,0,5,150);m.evaluateStaggered(.0001,f,0);
        assertEquals(-3,f[0],1e-10);assertTrue(f[1]<0);assertEquals(3,f[2],1e-10);assertTrue(f[3]>0);
        if(shared){m.readStaggeredState(x,0);for(int i=0;i<4;i++)x[i]+=.0001*f[i];m.applyStaggeredState(x,0,.0001);}
        else{m.step(.0001,0);m.step(.0001,1);}
        assertTrue(m.rectifierDegrees()<90);assertTrue(m.inverterDegrees()>110);
    }
    @Test void amplitudeLimitPrecedesAngleTimeConstantAndUnitsAreExplicit(){
        var c=Chvdc2ParametersTest.constants();c[18]=.2;c[19]=-.3;c[4]=.02;c[8]=.03;
        var m=model(c);m.setInput(.3,.3,1050,1400,0,0,5,150);m.initialize(35,130);
        m.setInput(.3,.3,1050,1400,-350,350,5,150);double[] f=new double[6];m.evaluateStaggered(.001,f,1);
        assertEquals(15,f[1],1e-10);assertEquals(10,f[2],1e-10);assertEquals(-15,f[3],1e-10);assertEquals(-10,f[4],1e-10);
        assertEquals(0,f[0]);assertEquals(0,f[5]);
    }
    @Test void piOutputIsBoundedBeforeTheAngleLagEvenWhileBothStatesAreInsideLimits(){
        var m=model(Chvdc2ParametersTest.constants());
        m.setInput(.3,.3,1050,1400,0,0,5,150);m.initialize(89,111);
        m.setInput(.5,.1,1050,1400,0,0,5,150);double[] f=new double[4];m.evaluateStaggered(.0001,f,0);
        assertEquals(30,f[0],1e-10);assertEquals(2000,f[1],1e-10);
        assertEquals(-30,f[2],1e-10);assertEquals(-2000,f[3],1e-10);
        // Without the sum clamp the two lag rates would be +/-20000 degrees/s.
        assertEquals(89,m.rectifierDegrees());assertEquals(111,m.inverterDegrees());
    }
    @Test void refreshedBoundsDoNotCommitStateAndInvalidWritesAreAtomic(){
        var m=model(Chvdc2ParametersTest.constants());double[] f=new double[4];
        assertThrows(IllegalStateException.class,()->m.initialize(35,130));
        m.setInput(.3,.3,1050,1400,0,0,5,150);m.initialize(35,140);var initial=m.getIntegrationStates();
        m.setInput(.3,.3,1050,1400,0,0,36,139);assertEquals(initial,m.getIntegrationStates());
        assertThrows(IllegalArgumentException.class,()->m.setInput(.3,.3,1050,1400,0,0,91,139));
        m.applyStaggeredState(new double[]{35,35,140,140},0,0);assertEquals(36,m.rectifierDegrees());assertEquals(139,m.inverterDegrees());
        var bounded=m.getIntegrationStates();m.step(.0001,0);var trial=m.getIntegrationStates();
        assertThrows(IllegalStateException.class,()->m.evaluateStaggered(.0001,f,0));
        assertThrows(IllegalStateException.class,()->m.step(.0002,1));m.step(0,1);assertEquals(trial,m.getIntegrationStates());
        m.cancelPrediction();assertEquals(bounded,m.getIntegrationStates());
        assertThrows(IllegalArgumentException.class,()->m.applyStaggeredState(new double[]{1,2,3,Double.NaN},0,.001));assertEquals(bounded,m.getIntegrationStates());
        m.setInput(.3,.3,1050,1400,0,0,5,150);m.initialize(35,140);assertEquals(initial,m.getIntegrationStates());
    }
    @ParameterizedTest @ValueSource(booleans={false,true})
    void warmedNumericalStagesAllocateNoPerStepObjects(boolean shared){
        var bean=ManagementFactory.getThreadMXBean();Assumptions.assumeTrue(bean instanceof com.sun.management.ThreadMXBean);
        var memory=(com.sun.management.ThreadMXBean)bean;Assumptions.assumeTrue(memory.isThreadAllocatedMemorySupported());
        memory.setThreadAllocatedMemoryEnabled(true);long id=Thread.currentThread().threadId();
        var m=model(Chvdc2ParametersTest.constants());m.setInput(.3,.3,1050,1400,0,0,5,150);m.initialize(35,130);
        double[] x=new double[4],f=new double[4];
        for(int i=0;i<100000;i++)numericalStep(m,shared,x,f);
        long start=memory.getThreadAllocatedBytes(id);
        for(int i=0;i<100000;i++)numericalStep(m,shared,x,f);
        assertEquals(0,memory.getThreadAllocatedBytes(id)-start);assertEquals(35,m.rectifierDegrees(),1e-8);
    }
    private static void numericalStep(Chvdc2CurrentControls m,boolean shared,double[] x,double[] f){
        if(shared){m.readStaggeredState(x,0);m.evaluateStaggered(.0001,f,0);for(int i=0;i<4;i++)x[i]+=.0001*f[i];m.applyStaggeredState(x,0,.0001);}
        else{m.step(.0001,0);m.step(.0001,1);}
    }
}
