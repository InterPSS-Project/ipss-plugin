package org.interpss.core.dstab;

import static org.junit.jupiter.api.Assertions.*;
import java.lang.management.ManagementFactory;
import com.interpss.dstab.hvdc.model.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/** Explicit acceptance, rollback and shared protection-state equation contracts. */
public class Chvdc2ProtectionTest {
    static Chvdc2Protection model(boolean enabled,double failureAngle) {
        double[] c=Chvdc2ParametersTest.constants();c[30]=failureAngle;c[32]=.125;c[38]=40;c[39]=.03;c[40]=1.;c[41]=.125;
        return new Chvdc2Parameters("link",Chvdc2Parameters.Dialect.CHVDC2U1,new int[]{0,enabled?1:0,1},c).prepareProtection();
    }
    @ParameterizedTest @ValueSource(booleans={false,true})
    void sharedWashoutConvergesToItsIndependentExponential(boolean staggered) {
        double coarse=washout(.0005,staggered),fine=washout(.00025,staggered);
        assertTrue(coarse/fine>3.8&&coarse/fine<4.2);assertTrue(fine<.001);
    }
    private static double washout(double h,boolean staggered) {
        var m=model(true,0);m.initialize(1.03,1.03,30,0);m.setInput(.8,1.03,30);m.acceptBoundary(0,0);
        double[] x=new double[1],f=new double[1];double old=0;
        for(int n=0;n<Math.round(.03/h);n++) {
            if(staggered){m.readStaggeredState(x,0);m.evaluateStaggered(h,f,0);x[0]+=h*(n==0?f[0]:1.5*f[0]-.5*old);old=f[0];m.applyStaggeredState(x,0,h);}
            else{m.step(h,0);m.step(h,1);}
            m.acceptBoundary((n+1)*h,n+1);
        }
        m.readStaggeredState(x,0);assertTrue(m.ramlActive());assertEquals(40-x[0],m.rectifierMinimumDegrees(),1e-12);
        return Math.abs(x[0]-40*(1-Math.exp(-1)));
    }
    @Test void ramlUsesStrictVoltageAndHoldBoundariesAndKeepsItsOwnClock() {
        var m=model(true,0);m.initialize(1.03,1.03,30,-.01);m.setInput(1.,1.03,30);m.acceptBoundary(0,0);assertFalse(m.ramlActive());
        m.setInput(.9,1.03,30);m.acceptBoundary(.25,1);assertTrue(m.ramlActive());assertEquals(40,m.rectifierMinimumDegrees());
        assertEquals(.25,m.ramlTriggerTime());assertEquals(.25,m.ramlTimerMarker());assertEquals(1,m.staggeredInputDiscontinuityVersion());
        double[] left={7};assertTrue(m.readStaggeredInputDiscontinuityLeftRates(left,0));assertEquals(0,left[0]);
        m.setInput(1.1,1.03,30);m.acceptBoundary(.375,2);assertTrue(m.ramlActive(),"Hold expiration is strict");
        m.setInput(1.,1.03,30);m.acceptBoundary(.5,3);assertTrue(m.ramlActive(),"Recovery voltage is strict");
        assertEquals(.5,m.ramlTimerMarker());assertEquals(1,m.staggeredInputDiscontinuityVersion(),"Timer phase alone does not change the RHS");
        m.applyStaggeredState(new double[]{30},0,0);m.setInput(1.1,1.03,30);m.acceptBoundary(.5,4);assertFalse(m.ramlActive());
        assertEquals(5,m.rectifierMinimumDegrees());assertEquals(2,m.staggeredInputDiscontinuityVersion());
        assertTrue(m.readStaggeredInputDiscontinuityLeftRates(left,0));assertEquals(10/.03,left[0],1e-12);
        m.evaluateStaggered(0,left,0);assertEquals(-1000,left[0],1e-12,"Lag memory decays after releasing the output switch");
    }
    @Test void commutationFailureHasStrictTimeInclusiveVoltageAndOneTransitionPerAcceptance() {
        var m=model(false,10);m.initialize(1.03,1.03,30,0);m.setInput(1.03,.9,10);m.acceptBoundary(0,0);assertFalse(m.inverterShorted());
        m.setInput(1.03,.9,9);m.acceptBoundary(.25,1);assertTrue(m.inverterShorted());assertEquals(.25,m.commutationTransitionTime());
        m.acceptBoundary(.375,2);assertTrue(m.inverterShorted(),"Minimum failure duration is strict");
        m.setInput(1.03,Math.nextDown(.9),0);m.acceptBoundary(.5,3);assertTrue(m.inverterShorted());assertEquals(.25,m.commutationTransitionTime());
        m.setInput(1.03,.9,0);m.acceptBoundary(.5,4);assertFalse(m.inverterShorted(),"Voltage recovery includes equality");
        assertEquals(.5,m.commutationTransitionTime());assertTrue(m.hasCommutationTransition());
        long revision=m.staggeredInputDiscontinuityVersion();m.acceptBoundary(.5,4);assertFalse(m.inverterShorted());assertEquals(revision,m.staggeredInputDiscontinuityVersion());
        m.acceptBoundary(.5,5);assertTrue(m.inverterShorted(),"A distinct accepted event may retrigger at the same timestamp");
    }
    @Test void disabledFeaturesAndPreparedThresholdDoNotModifyEnteredParameters() {
        var c=Chvdc2ParametersTest.constants();c[40]=1.05;var p=new Chvdc2Parameters("link",Chvdc2Parameters.Dialect.CHVDC2,new int[]{0,1,1},c);
        var m=p.prepareProtection();m.initialize(1.01,1.03,30,0);assertEquals(1.01,m.preparedRamVoltagePu());assertEquals(1.05,p.copySourceConstants()[40]);
        m.acceptBoundary(0,0);assertFalse(m.ramlActive());m.setInput(1.,1.03,30);m.acceptBoundary(.1,1);assertTrue(m.ramlActive());
        var off=model(false,0);off.initialize(1.03,1.03,30,0);off.setInput(0,0,-10);off.acceptBoundary(1,0);off.step(.01,0);off.step(.01,1);
        assertFalse(off.ramlActive());assertFalse(off.inverterShorted());assertEquals(0,off.staggeredInputDiscontinuityVersion());
        assertEquals(0,off.getIntegrationStates().values().iterator().next());assertEquals(5,off.rectifierMinimumDegrees());
    }
    @Test void trialsAndZeroDurationCallsCannotCommitProtectionHistory() {
        var m=model(true,10);m.initialize(1.03,1.03,30,0);m.acceptBoundary(0,0);var states=m.getIntegrationStates();var histories=m.getIntegrationHistories();
        m.setInput(.1,.1,0);double[] f=new double[1];
        assertEquals(30,m.acceptedInverterExtinctionDegrees());
        for(int n=0;n<20;n++){m.evaluateStaggered(0,f,0);m.step(0,0);m.step(0,1);m.getStateDerivativeSnapshot();m.rectifierMinimumDegrees();m.inverterShorted();}
        assertEquals(states,m.getIntegrationStates());assertEquals(histories,m.getIntegrationHistories());assertEquals(0,m.staggeredInputDiscontinuityVersion());
        assertThrows(IllegalArgumentException.class,()->m.acceptBoundary(0,0));assertEquals(histories,m.getIntegrationHistories());
        m.acceptBoundary(0,1);assertTrue(m.ramlActive());assertTrue(m.inverterShorted());assertEquals(1,m.staggeredInputDiscontinuityVersion());
        assertEquals(0,m.acceptedInverterExtinctionDegrees());
    }
    @Test void transactionRollbackRestoresStatesInputsClocksAndEventRates() {
        var m=model(true,10);m.initialize(1.03,1.03,30,0);m.acceptBoundary(0,0);var states=m.getIntegrationStates();var histories=m.getIntegrationHistories();
        m.beginTransaction();assertThrows(IllegalStateException.class,m::beginTransaction);
        assertThrows(IllegalStateException.class,()->m.initialize(1.03,1.03,30,0));
        m.setInput(.1,.1,0);m.acceptBoundary(.1,1);m.step(.001,0);
        assertThrows(IllegalStateException.class,m::commitTransaction);assertThrows(IllegalStateException.class,()->m.acceptBoundary(.2,2));
        m.rollbackTransaction();assertEquals(states,m.getIntegrationStates());assertEquals(histories,m.getIntegrationHistories());assertEquals(0,m.staggeredInputDiscontinuityVersion());
        assertFalse(m.readStaggeredInputDiscontinuityLeftRates(new double[1],0));m.acceptBoundary(0,0);
        assertTrue(m.getStateDerivativeSnapshot().rates().isEmpty());assertThrows(IllegalStateException.class,m::rollbackTransaction);
        m.beginTransaction();m.setInput(.1,.1,0);m.acceptBoundary(.1,1);m.step(.001,0);m.step(.001,1);m.commitTransaction();
        assertTrue(m.ramlActive());assertTrue(m.inverterShorted());assertEquals(1,m.staggeredInputDiscontinuityVersion());
    }
    @Test void invalidWritesAreAtomicAndIntegrationOwnershipIsExplicit() {
        var m=model(true,10);m.initialize(1.03,1.03,30,0);m.acceptBoundary(0,0);var histories=m.getIntegrationHistories();
        assertThrows(IllegalArgumentException.class,()->m.setInput(.1,Double.NaN,0));m.acceptBoundary(0,0);
        assertThrows(IllegalArgumentException.class,()->m.acceptBoundary(-1,1));assertThrows(IllegalArgumentException.class,()->m.acceptBoundary(1,-1));
        assertEquals(histories,m.getIntegrationHistories());var states=m.getIntegrationStates();
        assertThrows(IllegalArgumentException.class,()->m.applyStaggeredState(new double[]{Double.NaN},0,0));assertEquals(states,m.getIntegrationStates());
        m.setInput(.1,1.03,30);m.acceptBoundary(.1,1);m.step(.001,0);var trial=m.getIntegrationStates();m.step(0,1);assertEquals(trial,m.getIntegrationStates());
        assertThrows(IllegalStateException.class,()->m.evaluateStaggered(.001,new double[1],0));assertThrows(IllegalStateException.class,()->m.step(.002,1));
        m.cancelPrediction();assertEquals(states,m.getIntegrationStates());assertTrue(m.ramlActive());
        m.initialize(1.03,1.03,30,-.01);assertFalse(m.ramlActive());assertEquals(0,m.staggeredInputDiscontinuityVersion());assertFalse(m.hasCommutationTransition());
    }
    @ParameterizedTest @ValueSource(booleans={false,true})
    void warmedStateAndTransactionalHistoryUpdatesAllocateNoPerStepObjects(boolean staggered) {
        var bean=ManagementFactory.getThreadMXBean();Assumptions.assumeTrue(bean instanceof com.sun.management.ThreadMXBean);
        var memory=(com.sun.management.ThreadMXBean)bean;Assumptions.assumeTrue(memory.isThreadAllocatedMemorySupported());memory.setThreadAllocatedMemoryEnabled(true);
        long thread=Thread.currentThread().threadId();var m=model(true,10);m.initialize(1.03,1.03,30,0);double[] x=new double[1],f=new double[1];
        for(int n=0;n<5;n++)allocationBatch(memory,thread,m,staggered,x,f,n*100000);
        long allocated=allocationBatch(memory,thread,m,staggered,x,f,500000);assertEquals(0,allocated);
    }
    private static long allocationBatch(com.sun.management.ThreadMXBean memory,long thread,Chvdc2Protection m,boolean staggered,double[] x,double[] f,int base) {
        long before=memory.getThreadAllocatedBytes(thread);
        for(int n=1;n<=100000;n++) {
            m.beginTransaction();m.setInput((n%100)<50?.9:1.1,1.03,(n%100)<50?0:30);m.acceptBoundary((base+n)*.0001,base+n);
            if(staggered){m.readStaggeredState(x,0);m.evaluateStaggered(.0001,f,0);x[0]+=.0001*f[0];m.applyStaggeredState(x,0,.0001);}
            else{m.step(.0001,0);m.step(.0001,1);}
            m.commitTransaction();
        }
        return memory.getThreadAllocatedBytes(thread)-before;
    }
}
