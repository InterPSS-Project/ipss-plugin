package org.interpss.core.dstab;

import static org.junit.jupiter.api.Assertions.*;
import java.lang.management.ManagementFactory;
import com.interpss.dstab.hvdc.model.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/** Coupled measurement/current-control stages and transaction ownership, independent of a network adapter. */
public class Chvdc2ControlAssemblyTest {
    static Chvdc2ControlAssembly model(boolean raml) {
        var c=Chvdc2ParametersTest.constants();c[30]=10;c[38]=40;c[39]=.03;c[40]=1.;
        var p=new Chvdc2Parameters("link",Chvdc2Parameters.Dialect.CHVDC2U1,new int[]{0,raml?1:0,1},c);
        var m=p.prepareControls(.9,.95);input(m,1050,1.03,30);m.initialize(32,140,0);return m;
    }
    static void input(Chvdc2ControlAssembly m,double current,double voltage,double gamma) {
        m.setInput(current,1050,505,500,voltage,1.03,1050,0,gamma);
    }
    @Test void simultaneousCorrectorSeesPredictedMeasurements() {
        var m=model(false);input(m,1400,1.03,30);double h=.0001;double[] x=new double[15];m.step(h,0);m.readStaggeredState(x,0);
        assertEquals(.301,x[0],1e-12);assertEquals(32,x[4]);assertEquals(32,x[6]);
        m.step(h,1);m.readStaggeredState(x,0);
        assertEquals(.300995,x[0],1e-12);assertEquals(32+.5*h*150*.001,x[4],1e-12);
        assertEquals(32+.5*h*(50*.001)/.0005,x[6],1e-12);
        assertEquals(15,m.getStateDerivativeSnapshot().rates().size());
    }
    @ParameterizedTest @ValueSource(booleans={false,true})
    void coupledMeasurementAndCurrentIntegralConvergeToAnalyticSolution(boolean staggered) {
        double coarse=trajectory(.0001,staggered),fine=trajectory(.00005,staggered);
        assertTrue(coarse/fine>3.7&&coarse/fine<4.3,"Both shared equations must retain second order: "+coarse/fine);
        assertTrue(fine<1e-6);
    }
    private static double trajectory(double h,boolean staggered) {
        var m=model(false);input(m,1400,1.03,30);double[] x=new double[15],f=new double[15],old=new double[15];
        for(int n=0;n<Math.round(.02/h);n++) {
            if(staggered){m.readStaggeredState(x,0);m.evaluateStaggered(h,f,0);for(int j=0;j<15;j++)x[j]+=h*(n==0?f[j]:1.5*f[j]-.5*old[j]);m.applyStaggeredState(x,0,h);System.arraycopy(f,0,old,0,15);}
            else{m.step(h,0);m.step(h,1);}
        }
        m.readStaggeredState(x,0);double exact=32+150*.1*(.02-.01*(1-Math.exp(-2)));
        assertEquals(.4-.1*Math.exp(-2),x[0],2e-6);return Math.abs(x[4]-exact);
    }
    @ParameterizedTest @ValueSource(booleans={false,true})
    void flatInitialStateAndPureObservationsDoNotCommitHistories(boolean staggered) {
        var m=model(true);m.acceptBoundary(0,0);var histories=m.getIntegrationHistories();double[] initial=new double[15],x=new double[15],f=new double[15];m.readStaggeredState(initial,0);
        for(int n=0;n<20;n++) {
            if(staggered){m.readStaggeredState(x,0);m.evaluateStaggered(.0001,f,0);for(int j=0;j<15;j++)x[j]+=.0001*f[j];m.applyStaggeredState(x,0,.0001);}
            else{m.step(.0001,0);m.step(.0001,1);}
            m.rectifierCurrentOrderAmperes();m.inverterCurrentOrderAmperes();m.getIntegrationStates();m.getIntegrationHistories();
        }
        m.readStaggeredState(x,0);assertArrayEquals(initial,x,1e-12);assertEquals(histories,m.getIntegrationHistories());
        input(m,1050,.1,0);for(int n=0;n<10;n++){m.evaluateStaggered(0,f,0);m.step(0,0);m.step(0,1);}
        assertFalse(m.ramlActive());assertFalse(m.inverterShorted());assertEquals(histories,m.getIntegrationHistories());
    }
    @Test void predictedVoltageCanTriggerRamlAndCancelRestoresAllControllerHistories() {
        var m=model(true);m.acceptBoundary(0,0);double[] x=new double[15];m.readStaggeredState(x,0);x[10]=1.00001;m.applyStaggeredState(x,0,0);
        input(m,1050,.9,0);var before=m.getIntegrationStates();var histories=m.getIntegrationHistories();
        m.step(.00001,0);assertFalse(m.ramlActive());m.evaluatePredictorProtection(.00001,1);assertTrue(m.ramlActive());assertTrue(m.inverterShorted());
        assertEquals(0,m.staggeredInputDiscontinuityVersion(),"Do not publish a trial discontinuity");
        assertEquals(30,m.acceptedInverterExtinctionDegrees(),"The electrical boundary must not consume an unaccepted predictor angle");
        assertThrows(IllegalStateException.class,()->m.acceptBoundary(.00001,2));m.cancelPrediction();
        assertEquals(before,m.getIntegrationStates());assertEquals(histories,m.getIntegrationHistories());assertEquals(0,m.staggeredInputDiscontinuityVersion());
        m.step(.00001,0);m.evaluatePredictorProtection(.00001,1);m.step(.00001,1);
        assertEquals(1,m.staggeredInputDiscontinuityVersion());assertTrue(m.ramlActive());assertTrue(m.inverterShorted());
        assertEquals(30,m.acceptedInverterExtinctionDegrees(),"Correcting a controller state does not accept a new electrical reference");
        m.acceptBoundary(.00001,2);assertEquals(0,m.acceptedInverterExtinctionDegrees());
        m.readStaggeredState(x,0);assertEquals(.5*.00001*40/.03,x[14],1e-12);assertEquals(32,x[4],1e-12,"RAML must not rebase PI memory");
        assertEquals(40-x[14],x[6],1e-12,"Apply the new firing-angle bound with the corrected RAML coordinate");
    }
    @Test void outerRollbackRestoresCommittedSubstepsAndAPendingInnerPrediction() {
        var m=model(true);m.acceptBoundary(0,0);var before=m.getIntegrationStates();var histories=m.getIntegrationHistories();
        m.beginTransaction();assertThrows(IllegalStateException.class,m::beginTransaction);input(m,1400,.1,0);
        m.step(.001,0);m.evaluatePredictorProtection(.001,1);m.step(.001,1);m.step(.001,0);
        assertThrows(IllegalStateException.class,m::commitTransaction);m.rollbackTransaction();
        assertEquals(before,m.getIntegrationStates());assertEquals(histories,m.getIntegrationHistories());m.acceptBoundary(0,0);
        assertEquals(0,m.staggeredInputDiscontinuityVersion());assertTrue(m.getStateDerivativeSnapshot().rates().isEmpty());
        m.beginTransaction();m.step(.0001,0);m.step(.0001,1);m.acceptBoundary(.0001,1);m.commitTransaction();
        assertThrows(IllegalStateException.class,m::rollbackTransaction);
    }
    @Test void externalStateWritesAndInvalidInputsAreAtomic() {
        var m=model(true);var before=m.getIntegrationStates();double[] x=new double[15];m.readStaggeredState(x,0);x[0]=.5;x[14]=Double.NaN;
        assertThrows(IllegalArgumentException.class,()->m.applyStaggeredState(x,0,.001));assertEquals(before,m.getIntegrationStates());
        m.readStaggeredState(x,0);x[8]=Double.MAX_VALUE;x[14]=10;
        assertThrows(IllegalArgumentException.class,()->m.applyStaggeredState(x,0,.001),"A later dependent angle-bound failure must roll back earlier state writes");
        assertEquals(before,m.getIntegrationStates());
        assertThrows(IllegalArgumentException.class,()->input(m,1050,Double.NaN,0));assertThrows(IllegalArgumentException.class,()->input(m,1050,1,Double.NaN));
        m.acceptBoundary(0,0);assertFalse(m.inverterShorted());m.step(.0001,0);assertThrows(IllegalStateException.class,()->m.step(.0002,1));m.cancelPrediction();assertEquals(before,m.getIntegrationStates());
    }
    @Test void rollbackDoesNotProjectAStateSavedImmediatelyBeforeANewBoundTakesEffect() {
        var m=model(true);double[] x=new double[15];m.readStaggeredState(x,0);x[10]=.9;m.applyStaggeredState(x,0,0);m.acceptBoundary(0,0);
        assertTrue(m.ramlActive());assertEquals(32,m.rectifierDegrees());var before=m.getIntegrationStates();
        m.beginTransaction();m.step(.0001,0);m.step(.0001,1);assertTrue(m.rectifierDegrees()>39);m.rollbackTransaction();
        assertEquals(before,m.getIntegrationStates());assertEquals(32,m.rectifierDegrees());assertTrue(m.ramlActive());
    }
    @ParameterizedTest @ValueSource(booleans={false,true})
    void warmedAssemblyStagesAndTransactionsAllocateNoPerStepObjects(boolean staggered) {
        var bean=ManagementFactory.getThreadMXBean();Assumptions.assumeTrue(bean instanceof com.sun.management.ThreadMXBean);
        var memory=(com.sun.management.ThreadMXBean)bean;Assumptions.assumeTrue(memory.isThreadAllocatedMemorySupported());memory.setThreadAllocatedMemoryEnabled(true);
        var m=model(true);double[] x=new double[15],f=new double[15];long id=Thread.currentThread().threadId();
        for(int n=0;n<5;n++)batch(m,staggered,x,f,100000);long start=memory.getThreadAllocatedBytes(id);batch(m,staggered,x,f,100000);assertEquals(0,memory.getThreadAllocatedBytes(id)-start);
    }
    private static void batch(Chvdc2ControlAssembly m,boolean staggered,double[] x,double[] f,int count) {
        for(int n=0;n<count;n++) {
            m.beginTransaction();
            if(staggered){m.readStaggeredState(x,0);m.evaluateStaggered(.0001,f,0);for(int i=0;i<15;i++)x[i]+=.0001*f[i];m.applyStaggeredState(x,0,.0001);}
            else{m.step(.0001,0);m.step(.0001,1);}
            m.commitTransaction();
        }
    }
}
