package org.interpss.core.dstab;

import static org.junit.jupiter.api.Assertions.*;
import java.lang.management.ManagementFactory;
import com.interpss.dstab.hvdc.model.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/** Signed bridge power and shared electrical RHS checks; complete model qualification is separate. */
public class Chvdc2ConverterBoundaryTest {
    static LccConverterBoundary converter(boolean rectifier) {
        return new LccConverterBoundary(rectifier,2,230000,rectifier?.9263:.9097,rectifier?.9375:.94375,
                rectifier?13.5939:13.3174,1e8);
    }
    @ParameterizedTest @ValueSource(booleans={false,true})
    void firingAndVoltageInterfacesHaveTheSameNormalPower(boolean rectifier) {
        var m=converter(rectifier);double[] byAngle=new double[6],byVoltage=new double[3];
        for(double voltage:new double[]{.85,1.,1.15})for(double current:new double[]{0.,100.,1000.,2000.}) {
            double alpha=Math.toRadians(rectifier?32:130);
            byAngle[0]=17;byAngle[5]=19;m.evaluateFiringAngle(voltage,current,alpha,byAngle,1);
            m.evaluateDcVoltage(voltage,byAngle[1],current,byVoltage,0);
            assertEquals(byVoltage[0],byAngle[2],1e-12);assertEquals(byVoltage[1],byAngle[3],1e-12);
            assertEquals(rectifier?alpha:byAngle[4],byVoltage[2],1e-12);
            assertEquals(17,byAngle[0]);assertEquals(19,byAngle[5]);
        }
    }
    @Test void signedRectifierVoltageAndZeroPowerRetainCommutatingReactivePower() {
        var m=converter(true);double[] out=new double[4];double rc=6/Math.PI*13.5939;
        m.evaluateFiringAngle(1,1000,Math.PI/2,out,0);
        assertEquals(-rc*1000,out[0],1e-9);assertEquals(-rc*.01,out[1],1e-12);assertTrue(out[2]>0);
        double e=6*Math.sqrt(2)/Math.PI*230000*.9263/.9375;
        m.evaluateFiringAngle(1,1000,Math.acos(rc*1000/e),out,0);
        assertEquals(0,out[0],1e-9);assertEquals(0,out[1],1e-12);assertTrue(out[2]>0);
        double q=out[2];m.evaluateFiringAngle(1,1000,Math.acos((rc*1000+.001)/e),out,0);
        assertEquals(q,out[2],1e-8);
    }
    @Test void losslessBridgeHasTheAnalyticPowerFactorAndNoOverlap() {
        var m=new LccConverterBoundary(true,1,100000,1,1,0,1e8);double[] out=new double[4];
        double e=3*Math.sqrt(2)/Math.PI*100000;
        for(double alpha:new double[]{.01,.4,1.,Math.PI/2,2.,3.}) {
            m.evaluateFiringAngle(.9,200,alpha,out,0);
            assertEquals(.9*e*Math.cos(alpha),out[0],1e-9);
            assertEquals(.9*e*200*Math.sin(alpha)/1e8,out[2],1e-12);
            assertEquals(Math.PI-alpha,out[3],1e-12);
        }
    }
    @Test void invalidOverlapAndInputsDoNotPublishPartialResults() {
        var m=converter(false);double[] out={3,4,5,6};var original=out.clone();
        assertThrows(IllegalArgumentException.class,()->m.evaluateFiringAngle(1,1000,Math.PI,out,0));assertArrayEquals(original,out);
        assertThrows(IllegalArgumentException.class,()->m.evaluateFiringAngle(0,1000,2,out,0));assertArrayEquals(original,out);
        assertThrows(IllegalArgumentException.class,()->m.evaluateFiringAngle(1,-1,2,out,0));assertArrayEquals(original,out);
        assertThrows(IllegalArgumentException.class,()->m.evaluateFiringAngle(1,1000,Double.NaN,out,0));assertArrayEquals(original,out);
        assertThrows(IllegalArgumentException.class,()->m.evaluateFiringAngle(Double.MAX_VALUE,1000,2,out,0));assertArrayEquals(original,out);
        assertThrows(IndexOutOfBoundsException.class,()->m.evaluateFiringAngle(1,1000,2,out,1));assertArrayEquals(original,out);
        m.evaluateFiringAngle(1,0,Math.PI,out,0);assertEquals(0,out[1],0);assertEquals(0,out[2],0);assertEquals(0,out[3],0);
    }
    @ParameterizedTest @ValueSource(booleans={false,true})
    void coupledBridgeAndLineUseOneRhsAndConvergeAtSecondOrder(boolean staggered) {
        double coarse=integrateLine(.0004,staggered),fine=integrateLine(.0002,staggered);
        assertTrue(coarse/fine>3.8&&coarse/fine<4.3,"Independent RL analytic response");assertTrue(fine<.01);
    }
    private static double integrateLine(double h,boolean staggered) {
        var r=converter(true);var i=converter(false);var line=new Chvdc2DcLine(5,3,0,true);
        double er=6*Math.sqrt(2)/Math.PI*230000*.9263/.9375,ei=6*Math.sqrt(2)/Math.PI*230000*.9097/.94375;
        double rc=6/Math.PI*13.5939,ic=6/Math.PI*13.3174,ar=Math.toRadians(32.7),ai=Math.toRadians(140.4);
        double initial=(er*Math.cos(ar)+ei*Math.cos(ai))/(5+rc+ic),old=0;
        double[] b=new double[8],x=new double[1],f=new double[1];
        r.evaluateFiringAngle(1,initial,ar,b,0);i.evaluateFiringAngle(1,initial,ai,b,4);line.initialize(b[0],b[4]);
        ar+=.002;double equilibrium=(er*Math.cos(ar)+ei*Math.cos(ai))/(5+rc+ic);
        for(int n=0;n<Math.round(.04/h);n++) {
            double current=line.rectifierAmperes();r.evaluateFiringAngle(1,current,ar,b,0);i.evaluateFiringAngle(1,current,ai,b,4);
            line.setInput(b[0],b[4]);line.evaluateStaggered(h,f,0);
            assertEquals((5*current*current+3*current*f[0])/1e8,b[1]+b[5],1e-11,"Instantaneous power includes stored magnetic energy");
            if(staggered){line.readStaggeredState(x,0);x[0]+=h*(n==0?f[0]:1.5*f[0]-.5*old);old=f[0];line.applyStaggeredState(x,0,h);}
            else {line.step(h,0);current=line.rectifierAmperes();r.evaluateFiringAngle(1,current,ar,b,0);i.evaluateFiringAngle(1,current,ai,b,4);line.setInput(b[0],b[4]);line.step(h,1);}
        }
        return Math.abs(line.rectifierAmperes()-(equilibrium+(initial-equilibrium)*Math.exp(-(5+rc+ic)*.04/3)));
    }
    @Test void warmedLiveVoltageBoundaryAllocatesNoPerCallObjects() {
        var bean=ManagementFactory.getThreadMXBean();Assumptions.assumeTrue(bean instanceof com.sun.management.ThreadMXBean);
        var memory=(com.sun.management.ThreadMXBean)bean;Assumptions.assumeTrue(memory.isThreadAllocatedMemorySupported());
        memory.setThreadAllocatedMemoryEnabled(true);long id=Thread.currentThread().threadId();
        var m=converter(true);double[] out=new double[4];
        for(int n=0;n<5;n++)allocationBatch(memory,id,m,out);
        long allocated=allocationBatch(memory,id,m,out);
        assertEquals(0,allocated);double voltage=out[0];
        m.evaluateFiringAngle(1,1000,.6,out,0);assertNotEquals(voltage,out[0]);
    }
    private static long allocationBatch(com.sun.management.ThreadMXBean memory,long id,LccConverterBoundary m,double[] out) {
        long before=memory.getThreadAllocatedBytes(id);
        for(int n=0;n<100000;n++)m.evaluateFiringAngle(1+(n%2)*.001,1000,.6,out,0);
        return memory.getThreadAllocatedBytes(id)-before;
    }
}
