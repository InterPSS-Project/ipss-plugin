package org.interpss.core.dstab;

import static org.junit.jupiter.api.Assertions.*;
import java.lang.management.ManagementFactory;
import org.interpss.dstab.hvdc.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/** Shared converter/line/control equations, power signs and atomic accepted-boundary ownership. */
public class Chvdc2CoupledRuntimeTest {
    record Rig(Chvdc2CoupledRuntime model,LccConverterBoundary rectifier,LccConverterBoundary inverter,Chvdc2DcLine line,double vr,double vi){}
    static Rig rig(boolean dynamic,double capacitance) {
        return rig(dynamic,capacitance,.01);
    }
    static Rig rig(boolean dynamic,double capacitance,double measurementTime) {
        var c=Chvdc2ParametersTest.constants();c[30]=10;c[38]=40;c[39]=.03;
        c[17]=measurementTime;
        var parameters=new Chvdc2Parameters("link",Chvdc2Parameters.Dialect.CHVDC2U1,new int[]{0,0,dynamic?1:0},c);
        var r=new LccConverterBoundary(true,2,230000,1,1,5,1e8);var i=new LccConverterBoundary(false,2,230000,1,1,5,1e8);
        var line=new Chvdc2DcLine(5,2.5,capacitance,dynamic);var m=new Chvdc2CoupledRuntime(parameters,r,i,line,1,1);
        double vr=r.dcVoltageAtFiringAngle(1.03,1050,Math.toRadians(32)),vi=vr-5*1050;
        m.setBoundary(1.03,0,1.03,0,1050,0);m.initialize(vr,vi,0);return new Rig(m,r,i,line,vr,vi);
    }
    @ParameterizedTest @ValueSource(booleans={false,true})
    void solvedInitialElectricalBoundaryIsConsistentAndFlat(boolean dynamic) {
        var r=rig(dynamic,0);var m=r.model();double[] b=new double[10],f=new double[m.staggeredStateCount()],inj=new double[4];m.readBoundary(b,0);
        assertEquals(r.vr(),b[0],1e-8);assertEquals(r.vi(),b[1],1e-8);assertEquals(1050,b[2],1e-8);assertEquals(1050,b[3],1e-8);
        assertEquals(5*1050*1050/1e8,b[4]+b[6],1e-12);assertTrue(b[5]>0&&b[7]>0);
        m.evaluateStaggered(.0001,f,0);for(double rate:f)assertEquals(0,rate,1e-8);
        m.evaluateInjections(inj,0);assertEquals(-b[4],1.03*inj[0],1e-12);assertEquals(-b[6],1.03*inj[2],1e-12);
        assertEquals(b[5],1.03*inj[1],1e-12);assertEquals(b[7],1.03*inj[3],1e-12);
        assertEquals(m.staggeredStateCount(),m.getIntegrationStates().size());
    }
    @ParameterizedTest @ValueSource(booleans={false,true})
    void bypassedObservationsStayLiveWithoutChangingEitherIntegrationHistory(boolean staggered) {
        var m=rig(true,0,0).model();m.acceptBoundary(0,0);int count=m.staggeredStateCount();
        double[] raw=new double[count],check=new double[count],published=new double[count+2],rates=new double[count],power=new double[10],accepted=new double[10];
        m.readAcceptedBoundary(accepted,0);m.beginTransaction();
        m.setBoundary(1.02,0,1.025,0,1050,0);m.readStaggeredState(raw,0);var histories=m.getIntegrationHistories();
        java.util.Arrays.fill(published,-17);
        for(int n=0;n<5;n++) {
            m.readPublishedStates(published,1);m.readStaggeredState(check,0);
            assertArrayEquals(raw,check);assertEquals(histories,m.getIntegrationHistories());
            assertEquals(1.02,published[11]);assertEquals(1.025,published[12]);
            assertEquals(raw[15]/3500,published[1],1e-15);assertEquals(raw[15]/3500,published[2],1e-15);
            assertEquals(-17,published[0]);assertEquals(-17,published[count+1]);
        }
        assertNotEquals(raw[10],published[11],"The observation must not substitute a stale numerical coordinate");
        if(staggered){
            m.evaluateStaggered(.00001,rates,0);for(int n=0;n<count;n++)raw[n]+=.00001*rates[n];m.applyStaggeredState(raw,0,.00001);
        }else{
            m.step(.00001,0);m.readStaggeredState(raw,0);m.readPublishedStates(published,1);m.readStaggeredState(check,0);assertArrayEquals(raw,check);
            m.step(.00001,1);
        }
        m.readBoundary(power,0);m.readPublishedStates(published,1);assertEquals(power[2]/3500,published[1],1e-15);
        m.readAcceptedBoundary(power,0);assertArrayEquals(accepted,power);assertEquals(histories,m.getIntegrationHistories());
        m.rollbackTransaction();m.readStaggeredState(raw,0);m.readPublishedStates(published,1);
        assertEquals(1.03,published[11]);assertEquals(1.03,published[12]);assertEquals(.3,raw[0],1e-14);
        var before=m.getIntegrationStates();assertThrows(IndexOutOfBoundsException.class,()->m.readPublishedStates(published,3));assertEquals(before,m.getIntegrationStates());
    }
    @ParameterizedTest @ValueSource(booleans={false,true})
    void liveAnglesAndVoltagesRefreshTheSameCircuitForBothMethods(boolean staggered) {
        var r=rig(true,0);var m=r.model();int count=m.staggeredStateCount();double[] x=new double[count],f=new double[count],old=new double[count],b=new double[10];
        m.acceptBoundary(0,0);m.setBoundary(1.02,0,1.03,0,1050,0);m.readBoundary(b,0);m.evaluateStaggered(.00001,f,0);
        assertEquals((b[0]-b[1]-5*b[2])/2.5,f[15],1e-10);assertTrue(f[15]<0);
        for(int n=0;n<100;n++) {
            if(staggered){m.readStaggeredState(x,0);m.evaluateStaggered(.00001,f,0);for(int j=0;j<count;j++)x[j]+=.00001*(n==0?f[j]:1.5*f[j]-.5*old[j]);m.applyStaggeredState(x,0,.00001);System.arraycopy(f,0,old,0,count);}
            else{m.step(.00001,0);m.evaluatePredictorProtection((n+1)*.00001,2*n+1);m.step(.00001,1);}
            m.acceptBoundary((n+1)*.00001,2*n+2);m.readBoundary(b,0);assertTrue(b[2]>0);assertFalse(m.inverterShorted());
        }
        m.readStaggeredState(x,0);assertTrue(x[15]<1050);assertTrue(x[0]<.3);assertTrue(x[4]<32);
    }
    @Test void capacitorCircuitRetainsBothCurrentsAndItsEnergyBalance() {
        var r=rig(true,.0001);var m=r.model();assertEquals(18,m.staggeredStateCount());double[] x=new double[18],f=new double[18],b=new double[10];
        m.readStaggeredState(x,0);x[15]=1060;x[17]=1040;m.applyStaggeredState(x,0,0);m.readBoundary(b,0);m.evaluateStaggered(0,f,0);
        assertEquals((1060-1040)/.0001,f[16],1e-8);
        double storedRate=1.25*x[15]*f[15]+.0001*x[16]*f[16]+1.25*x[17]*f[17];
        double terminalPower=b[0]*x[15]-b[1]*x[17]-2.5*(x[15]*x[15]+x[17]*x[17]);
        assertEquals(terminalPower,storedRate,1e-6);
    }
    @Test void acceptedPowerKeepsTheAngleUsedBeforeHistoryAdvances() {
        var m=rig(true,0).model();double[] b=new double[10],a=new double[10],x=new double[m.staggeredStateCount()];m.acceptBoundary(0,0);m.readAcceptedBoundary(a,0);
        m.readStaggeredState(x,0);x[7]-=1;m.applyStaggeredState(x,0,0);m.readBoundary(b,0);assertNotEquals(b[8],b[9]);
        m.acceptBoundary(.001,1);m.readAcceptedBoundary(a,0);assertArrayEquals(b,a,1e-10);
        m.readBoundary(b,0);assertEquals(a[8],b[9],1e-12);assertNotEquals(a[6],b[6]);
        m.readAcceptedBoundary(b,0);assertArrayEquals(a,b);
    }
    @Test void repeatedElectricalAcceptanceIsIdempotentAndRejectsChangedEpochInputs() {
        var m=rig(true,0).model();double[] x=new double[m.staggeredStateCount()],accepted=new double[10],check=new double[10];
        m.acceptBoundary(0,0);m.readStaggeredState(x,0);x[7]-=1;m.applyStaggeredState(x,0,0);
        m.acceptBoundary(.001,1);m.readAcceptedBoundary(accepted,0);var history=m.getIntegrationHistories();
        // Reading live injections uses the next held angle, but does not create a new accepted epoch.
        m.evaluateInjections(new double[4],0);m.acceptBoundary(.001,1);m.readAcceptedBoundary(check,0);
        assertArrayEquals(accepted,check);assertEquals(history,m.getIntegrationHistories());
        // Same magnitude but changed phase is still a changed physical boundary.
        m.setBoundary(0,1.03,1.03,0,1050,0);
        assertThrows(IllegalStateException.class,()->m.acceptBoundary(.001,1));
        m.readAcceptedBoundary(check,0);assertArrayEquals(accepted,check);assertEquals(history,m.getIntegrationHistories());
        m.setBoundary(1.03,0,1.03,0,1050,0);m.readStaggeredState(x,0);x[4]+=.001;m.applyStaggeredState(x,0,0);
        assertThrows(IllegalStateException.class,()->m.acceptBoundary(.001,1));
        assertThrows(IllegalStateException.class,()->m.acceptBoundary(.002,1));
    }
    @Test void rollbackRestoresTheElectricalAcceptanceEpoch() {
        var m=rig(true,0).model();double[] x=new double[m.staggeredStateCount()],accepted=new double[10],check=new double[10];
        m.acceptBoundary(0,0);m.readAcceptedBoundary(accepted,0);m.beginTransaction();
        m.readStaggeredState(x,0);x[7]-=1;m.applyStaggeredState(x,0,0);m.acceptBoundary(.001,1);
        m.rollbackTransaction();m.acceptBoundary(0,0);m.readAcceptedBoundary(check,0);assertArrayEquals(accepted,check);
        m.readStaggeredState(x,0);x[7]-=.5;m.applyStaggeredState(x,0,0);m.acceptBoundary(.001,1);
        m.readAcceptedBoundary(check,0);assertNotEquals(accepted[8],check[8]);
    }
    @Test void completedProtectionStageCannotReacceptAnOlderElectricalEpoch() {
        var m=rig(true,0).model();m.acceptBoundary(0,0);double[] initial=new double[m.staggeredStateCount()];m.readStaggeredState(initial,0);
        m.step(.00001,0);m.evaluatePredictorProtection(.00001,1);m.step(.00001,1);
        // Even identical numerical coordinates cannot rewind an accepted protection clock.
        m.applyStaggeredState(initial,0,0);assertThrows(IllegalStateException.class,()->m.acceptBoundary(0,0));
        m.acceptBoundary(.00001,2);
    }
    @Test void shortingUsesZeroLineVoltageWithoutResettingCurrentOrAcInjection() {
        var m=rig(true,0).model();double[] x=new double[m.staggeredStateCount()],a=new double[10],f=new double[x.length];
        m.acceptBoundary(0,0);m.readStaggeredState(x,0);x[7]=169;m.applyStaggeredState(x,0,0);
        // Change the physical inverter voltage to exhaust its commutation margin with the limited angle.
        m.setBoundary(1.03,0,.15,0,1050,0);m.readBoundary(a,0);assertTrue(Math.toDegrees(a[8])<10);
        m.acceptBoundary(.01,1);assertTrue(m.inverterShorted());m.readAcceptedBoundary(a,0);
        assertEquals(0,a[1]);assertEquals(1050,a[3],1e-8);assertTrue(Math.abs(a[6])>.1);
        m.evaluateStaggered(.0001,f,0);assertTrue(f[15]>0,"The short changes the line RHS rather than deleting stored current");
    }
    @Test void nestedPredictorAndOuterTransactionRestoreAllCoordinatesAndPublishedPower() {
        var m=rig(true,0).model();m.acceptBoundary(0,0);var states=m.getIntegrationStates();var history=m.getIntegrationHistories();double[] accepted=new double[10],check=new double[10];m.readAcceptedBoundary(accepted,0);
        m.beginTransaction();m.setBoundary(1.02,0,1.03,0,1050,0);m.step(.00001,0);m.evaluatePredictorProtection(.00001,1);m.readAcceptedBoundary(check,0);assertArrayEquals(accepted,check);
        m.step(.00001,1);m.acceptBoundary(.00001,2);m.step(.00001,0);m.rollbackTransaction();
        assertEquals(states,m.getIntegrationStates());assertEquals(history,m.getIntegrationHistories());m.readAcceptedBoundary(check,0);assertArrayEquals(accepted,check);
        m.step(.00001,0);m.cancelPrediction();assertEquals(states,m.getIntegrationStates());assertEquals(history,m.getIntegrationHistories());
    }
    @Test void invalidCircuitTrialsAreAtomicAndCannotAdvanceAHistory() {
        var m=rig(true,0).model();m.acceptBoundary(0,0);var states=m.getIntegrationStates();var histories=m.getIntegrationHistories();double[] x=new double[m.staggeredStateCount()];m.readStaggeredState(x,0);x[15]=-1;
        assertThrows(IllegalArgumentException.class,()->m.applyStaggeredState(x,0,.001));assertEquals(states,m.getIntegrationStates());assertEquals(histories,m.getIntegrationHistories());
        assertThrows(IllegalArgumentException.class,()->m.setBoundary(0,0,1,0,1050,0));assertEquals(states,m.getIntegrationStates());
        m.step(.0001,0);assertThrows(IllegalStateException.class,()->m.evaluateStaggered(.0001,x,0));m.cancelPrediction();assertEquals(states,m.getIntegrationStates());
    }
    @ParameterizedTest @ValueSource(booleans={false,true})
    void warmedElectricalAndIntegrationStagesAllocateNoObjects(boolean staggered) {
        var bean=ManagementFactory.getThreadMXBean();Assumptions.assumeTrue(bean instanceof com.sun.management.ThreadMXBean);var memory=(com.sun.management.ThreadMXBean)bean;
        Assumptions.assumeTrue(memory.isThreadAllocatedMemorySupported());memory.setThreadAllocatedMemoryEnabled(true);long thread=Thread.currentThread().threadId();
        var m=rig(true,0).model();double[] x=new double[m.staggeredStateCount()],f=new double[x.length],i=new double[4];
        for(int n=0;n<5;n++)batch(m,staggered,x,f,i,20000);long before=memory.getThreadAllocatedBytes(thread);batch(m,staggered,x,f,i,20000);assertEquals(0,memory.getThreadAllocatedBytes(thread)-before);
    }
    private static void batch(Chvdc2CoupledRuntime m,boolean staggered,double[] x,double[] f,double[] i,int count) {
        for(int n=0;n<count;n++){m.beginTransaction();m.evaluateInjections(i,0);m.readPublishedStates(x,0);if(staggered){m.readStaggeredState(x,0);m.evaluateStaggered(.00001,f,0);for(int j=0;j<x.length;j++)x[j]+=.00001*f[j];m.applyStaggeredState(x,0,.00001);}else{m.step(.00001,0);m.step(.00001,1);}m.commitTransaction();}
    }
}
