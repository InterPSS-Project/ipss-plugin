package org.interpss.core.dstab;

import static org.junit.jupiter.api.Assertions.*;
import java.lang.management.ManagementFactory;
import com.interpss.dstab.hvdc.model.*;
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
    @ParameterizedTest @ValueSource(ints={1,10})
    void scheduledEventRetainsLeftLineRateForTheFirstOuterInterval(int localSteps) {
        var event=rig(true,0).model();var ordinary=rig(true,0).model();double h=.0005;
        event.acceptBoundary(0,0);ordinary.acceptBoundary(0,0);
        double[] initial=new double[event.staggeredStateCount()],after=new double[initial.length];
        event.readStaggeredState(initial,0);event.captureScheduledNetworkEvent();
        for(var model:new Chvdc2CoupledRuntime[]{event,ordinary}) {
            model.setBoundary(1.02,0,1.03,0,1050,0);
            model.beginTransaction();model.beginIntegrationInterval(h);
            model.step(h,0,localSteps);model.step(h,1,localSteps);model.completeIntegrationInterval();
        }
        event.readStaggeredState(after,0);double eventChange=after[15]-initial[15];
        ordinary.readStaggeredState(after,0);double ordinaryChange=after[15]-initial[15];
        assertTrue(ordinaryChange<-.01,"The changed terminal voltage must excite the line");
        assertEquals(.5,eventChange/ordinaryChange,.03,"The first interval retains both event-side rates");
        event.rollbackTransaction();event.readStaggeredState(after,0);assertArrayEquals(initial,after,0.);
        event.setBoundary(1.02,0,1.03,0,1050,0);event.beginIntegrationInterval(h);
        event.step(h,0,localSteps);event.step(h,1,localSteps);event.completeIntegrationInterval();
        event.readStaggeredState(after,0);
        assertEquals(eventChange,after[15]-initial[15],1e-9,"Rollback must retain the pending event stage");
    }
    @ParameterizedTest @ValueSource(booleans={false,true})
    void localModifiedEulerReplaysEndpointInputsAndRestoresOuterSamples(boolean dynamic) {
        for(int steps:new int[]{2,4,10}) {
            var m=rig(dynamic,0).model();var manual=rig(dynamic,0).model();int count=m.staggeredStateCount();
            double H=.0001,h=H/steps;double[] initial=new double[count],actual=new double[count],expected=new double[count];
            m.readStaggeredState(initial,0);m.beginTransaction();m.beginIntegrationInterval(H);
            m.step(H,0,steps);
            assertEquals(com.interpss.dstab.controller.runtime.IntegrationStateMonitor.Stage.PREDICTOR,m.getStateDerivativeSnapshot().stage());
            assertThrows(IllegalStateException.class,()->m.step(H,1));
            m.setBoundary(1.029,0,1.031,0,1051,0);m.step(H,1,steps);m.completeIntegrationInterval();
            manual.beginIntegrationInterval(H);
            for(int k=0;k<steps;k++) {
                double a=k/(double)steps,b=(k+1)/(double)steps;
                manual.setBoundary(1.03+a*(1.029-1.03),0,1.03+a*(1.031-1.03),0,1050+a,0);manual.step(h,0);
                manual.setBoundary(1.03+b*(1.029-1.03),0,1.03+b*(1.031-1.03),0,1050+b,0);manual.step(h,1);
            }
            manual.completeIntegrationInterval();m.readStaggeredState(actual,0);manual.readStaggeredState(expected,0);
            assertArrayEquals(expected,actual,1e-10,"Local corrector follows the same equations and endpoint input interpolation");
            m.rollbackTransaction();m.readStaggeredState(actual,0);assertArrayEquals(initial,actual);
            m.beginIntegrationInterval(H);m.step(H,0,steps);m.cancelPrediction();
            m.step(H,0,steps);m.step(H,1,steps);m.completeIntegrationInterval();
            m.readStaggeredState(actual,0);assertArrayEquals(initial,actual,1e-9,"Cancellation preserves flat equilibrium");
        }
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
        m.readAcceptedBoundary(accepted,0);m.beginTransaction();m.beginIntegrationInterval(.00001);
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
            m.evaluateStaggered(.00001,rates,0);for(int n=0;n<count;n++)raw[n]+=.00001*rates[n];m.applyStaggeredState(raw,0,.00001);m.completeStaggeredStep(.00001);
        }else{
            m.step(.00001,0);m.readStaggeredState(raw,0);m.readPublishedStates(published,1);m.readStaggeredState(check,0);assertArrayEquals(raw,check);
            m.step(.00001,1);
        }
        m.readBoundary(power,0);m.readPublishedStates(published,1);assertEquals(power[2]/3500,published[1],1e-15);m.completeIntegrationInterval();
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
    @ParameterizedTest @ValueSource(booleans={false,true})
    void outerIntervalSamplesBothCurrentTransducersButKeepsTheCircuitLive(boolean staggered) {
        var m=rig(true,.0001).model();int count=m.staggeredStateCount();double h=.0000025;
        double[] initial=new double[count],x=new double[count],f=new double[count],b=new double[10],injection=new double[4],changed=new double[4];
        m.readStaggeredState(initial,0);m.acceptBoundary(0,0);m.beginTransaction();m.beginIntegrationInterval(4*h);
        m.evaluateInjections(injection,0);
        for(int local=0;local<4;local++) {
            // Distinct capacitor-terminal currents catch accidental reuse of one sample.
            m.readStaggeredState(x,0);x[15]=1060+local;x[17]=1040-local;
            m.applyStaggeredState(x,0,0);m.setBoundary(1.02,0,1.03,0,1050,0);
            m.evaluateStaggered(h,f,0);m.readBoundary(b,0);m.evaluateInjections(changed,0);
            assertEquals(0,f[0],1e-10);assertEquals(0,f[1],1e-10);
            assertEquals(x[15],b[2]);assertEquals(x[17],b[3]);assertNotEquals(injection[0],changed[0]);
            assertEquals((x[15]-x[17])/.0001,f[16],1e-7);
            if(staggered){for(int k=0;k<count;k++)x[k]+=h*f[k];m.applyStaggeredState(x,0,h);m.completeStaggeredStep(h);}
            else{m.step(h,0);m.step(h,1);}
        }
        m.completeIntegrationInterval();m.readStaggeredState(x,0);m.evaluateStaggered(h,f,0);
        assertEquals((x[15]/3500-x[0])/.01,f[0],1e-10);
        assertEquals((x[17]/3500-x[1])/.01,f[1],1e-10);
        assertNotEquals(0,f[0]);assertNotEquals(0,f[1]);
        m.beginIntegrationInterval(h);double sampledR=x[15],sampledI=x[17];
        x[15]+=2;x[17]-=2;m.applyStaggeredState(x,0,0);m.evaluateStaggered(h,f,0);
        assertEquals((sampledR/3500-x[0])/.01,f[0],1e-10);
        assertEquals((sampledI/3500-x[1])/.01,f[1],1e-10);
        m.rollbackTransaction();m.readStaggeredState(x,0);assertArrayEquals(initial,x);
        // Rollback must discard the new interval and its current samples.
        m.beginIntegrationInterval(h);m.step(h,0);m.cancelPrediction();m.step(h,0);m.step(h,1);m.completeIntegrationInterval();
        m.readStaggeredState(x,0);assertArrayEquals(initial,x,1e-10);
    }
    @ParameterizedTest @ValueSource(booleans={false,true})
    void lineIntegrationSamplesCommutationCurrentsButKeepsOtherInputsLive(boolean staggered) {
        for(double capacitance:new double[]{0,.0001})for(double measurementTime:new double[]{0,.01}) {
            var r=rig(true,capacitance,measurementTime);var m=r.model();int count=m.staggeredStateCount();
            double h=.000001;double[] initial=new double[count],sample=new double[count],x=new double[count],rates=new double[count],b=new double[10];
            m.readStaggeredState(initial,0);m.beginTransaction();
            if(count==18) {m.readStaggeredState(x,0);x[15]+=2;x[17]-=2;m.applyStaggeredState(x,0,0);}
            m.readStaggeredState(sample,0);m.beginIntegrationInterval(h);
            m.readStaggeredState(x,0);x[15]+=4;if(count==18)x[17]-=3;
            x[6]+=.1;x[7]-=.1;m.applyStaggeredState(x,0,0);m.readStaggeredState(x,0);
            m.setBoundary(1.02,0,1.03,0,1050,0);m.evaluateStaggered(h,rates,0);m.readBoundary(b,0);
            double lineR=r.rectifier().dcVoltageAtFiringAngle(1.02,sample[15],Math.toRadians(x[6]));
            double lineI=r.inverter().dcVoltageAtFiringAngle(1.03,sample[count==18?17:15],Math.toRadians(x[7]));
            assertEquals(r.rectifier().dcVoltageAtFiringAngle(1.02,x[15],Math.toRadians(x[6])),b[0],1e-8);
            assertEquals(r.inverter().dcVoltageAtFiringAngle(1.03,x[count==18?17:15],Math.toRadians(x[7])),b[1],1e-8);
            if(count==16)assertEquals((lineR-lineI-5*x[15])/2.5,rates[15],1e-8);
            else {
                assertEquals((lineR-x[16]-2.5*x[15])/1.25,rates[15],1e-8);
                assertEquals((x[15]-x[17])/capacitance,rates[16],1e-7);
                assertEquals((x[16]-lineI-2.5*x[17])/1.25,rates[17],1e-8);
            }
            if(staggered) {for(int k=0;k<count;k++)x[k]+=h*rates[k];m.applyStaggeredState(x,0,h);m.completeStaggeredStep(h);}
            else {m.step(h,0);m.step(h,1);}
            m.completeIntegrationInterval();m.readStaggeredState(x,0);m.readBoundary(b,0);m.evaluateStaggered(0,rates,0);
            double expected=count==16?(b[0]-b[1]-5*x[15])/2.5:(b[0]-x[16]-2.5*x[15])/1.25;
            assertEquals(expected,rates[15],1e-8,"Completed interval resumes live commutation current");
            m.rollbackTransaction();m.readStaggeredState(x,0);assertArrayEquals(initial,x);
            m.beginIntegrationInterval(h);m.step(h,0);m.cancelPrediction();m.step(h,0);m.step(h,1);m.completeIntegrationInterval();
            m.readStaggeredState(x,0);assertArrayEquals(initial,x,1e-10,"Rollback restores sampled current and interval ownership");
        }
    }
    @Test void capacitorCircuitRetainsBothCurrentsAndItsEnergyBalance() {
        var r=rig(true,.0001);var m=r.model();assertEquals(18,m.staggeredStateCount());double[] x=new double[18],f=new double[18],b=new double[10];
        m.readStaggeredState(x,0);x[15]=1060;x[17]=1040;m.applyStaggeredState(x,0,0);m.readBoundary(b,0);m.evaluateStaggered(0,f,0);
        assertEquals((1060-1040)/.0001,f[16],1e-8);
        double storedRate=1.25*x[15]*f[15]+.0001*x[16]*f[16]+1.25*x[17]*f[17];
        double terminalPower=b[0]*x[15]-b[1]*x[17]-2.5*(x[15]*x[15]+x[17]*x[17]);
        assertEquals(terminalPower,storedRate,1e-6);
    }
    @ParameterizedTest @ValueSource(booleans={false,true})
    void algebraicCurrentSamplesTheIntegrationInputWhileAcInjectionsStayLive(boolean staggered) {
        var rig=rig(false,0);var m=rig.model();double[] b=new double[10],x=new double[15],f=new double[15],i=new double[4],oldInjection=new double[4];
        m.acceptBoundary(0,0);m.evaluateInjections(oldInjection,0);m.beginTransaction();
        m.setBoundary(1.02,0,1.03,0,1050,0);var history=m.getIntegrationHistories();
        for(int n=0;n<3;n++) {
            m.readBoundary(b,0);assertEquals(1050,b[2],1e-8);m.evaluateInjections(i,0);m.readPublishedStates(x,0);
            assertNotEquals(oldInjection[0],i[0]);assertEquals(history,m.getIntegrationHistories());
        }
        if(staggered) {
            m.readStaggeredState(x,0);m.evaluateStaggered(.00001,f,0);for(int k=0;k<15;k++)x[k]+=.00001*f[k];m.applyStaggeredState(x,0,.00001);m.completeStaggeredStep(.00001);
        }else {
            m.step(.00001,0);m.readBoundary(b,0);assertEquals(1050,b[2],1e-8);
            // Predictor network voltage must not replace the interval's sampled voltage.
            m.setBoundary(1.019,0,1.031,0,1050,0);m.step(.00001,1);
        }
        m.readStaggeredState(x,0);m.readBoundary(b,0);
        double expected=(rig.rectifier().dcVoltageAtFiringAngle(1.02,0,Math.toRadians(x[6]))
                -rig.inverter().dcVoltageAtFiringAngle(1.03,0,Math.toRadians(x[7])))
                /(5+2*(3/Math.PI*2*5));
        assertEquals(expected,b[2],1e-10);assertNotEquals(1050,b[2]);
        m.acceptBoundary(.00001,1);m.rollbackTransaction();m.readBoundary(b,0);assertEquals(1050,b[2],1e-8);
        m.acceptBoundary(0,0);assertEquals(1050,m.getIntegrationHistories().get("Algebraic line current (A)"),1e-8);
    }
    @Test void algebraicPredictorCancellationAndFailedAcceptanceRestoreCurrentHistory() {
        var r=rig(false,0);var m=r.model();m.acceptBoundary(0,0);var initial=m.getIntegrationHistories();var states=m.getIntegrationStates();
        m.setBoundary(1.02,0,1.03,0,1050,0);m.step(.00001,0);m.cancelPrediction();
        assertEquals(initial,m.getIntegrationHistories());assertEquals(states,m.getIntegrationStates());
        double[] x=new double[15];m.readStaggeredState(x,0);x[6]=90;
        m.beginTransaction();m.applyStaggeredState(x,0,.001);
        assertThrows(IllegalArgumentException.class,()->m.completeStaggeredStep(.001));assertEquals(initial,m.getIntegrationHistories());m.rollbackTransaction();
        assertEquals(initial,m.getIntegrationHistories());assertEquals(states,m.getIntegrationStates());
        m.setBoundary(1.03,0,1.03,0,1050,0);m.initialize(r.vr(),r.vi(),0);
        assertEquals(1050,m.getIntegrationHistories().get("Algebraic line current (A)"),1e-8);
    }
    @Test void repeatedAcceptanceChecksAlgebraicHistoryEvenWhenStatesAndInputsMatch() {
        var m=rig(false,0).model();double[] x=new double[15],before=new double[10],after=new double[10];
        m.acceptBoundary(0,0);m.readStaggeredState(x,0);m.readAcceptedBoundary(before,0);m.beginTransaction();
        m.setBoundary(1.02,0,1.03,0,1050,0);m.applyStaggeredState(x,0,.00001);m.completeStaggeredStep(.00001);m.setBoundary(1.03,0,1.03,0,1050,0);
        assertThrows(IllegalStateException.class,()->m.acceptBoundary(0,0));m.readAcceptedBoundary(after,0);assertArrayEquals(before,after);
        m.rollbackTransaction();m.acceptBoundary(0,0);m.readAcceptedBoundary(after,0);assertArrayEquals(before,after);
    }
    @ParameterizedTest @ValueSource(booleans={false,true})
    void localStepsKeepOneOuterAlgebraicInputAndRollbackItsProgress(boolean staggered) {
        var r=rig(false,0);var m=r.model();double[] x=new double[15],f=new double[15],b=new double[10];double h=.0000025;
        m.acceptBoundary(0,0);m.beginTransaction();m.setBoundary(1.02,0,1.03,0,1050,0);m.beginIntegrationInterval(4*h);
        assertThrows(IllegalStateException.class,()->m.beginIntegrationInterval(h));assertThrows(IllegalStateException.class,m::completeIntegrationInterval);
        assertThrows(IllegalStateException.class,m::commitTransaction);assertThrows(IllegalStateException.class,()->m.initialize(r.vr(),r.vi(),0));
        for(int n=0;n<4;n++) {
            m.setBoundary(1.02-n*.0001,0,1.03+n*.0001,0,1050,0);
            if(staggered){m.readStaggeredState(x,0);m.evaluateStaggered(h,f,0);for(int k=0;k<15;k++)x[k]+=h*f[k];m.applyStaggeredState(x,0,h);m.completeStaggeredStep(h);}
            else{m.step(h,0);assertThrows(IllegalStateException.class,m::completeIntegrationInterval);m.step(h,1);}
            m.readBoundary(b,0);assertEquals(1050,b[2],1e-8);
        }
        var states=m.getIntegrationStates();m.readStaggeredState(x,0);
        if(staggered)assertThrows(IllegalArgumentException.class,()->m.applyStaggeredState(x,0,h));else assertThrows(IllegalArgumentException.class,()->m.step(h,0));
        assertEquals(states,m.getIntegrationStates());m.completeIntegrationInterval();m.readBoundary(b,0);
        double expected=(r.rectifier().dcVoltageAtFiringAngle(1.02,0,Math.toRadians(x[6]))-r.inverter().dcVoltageAtFiringAngle(1.03,0,Math.toRadians(x[7])))/(5+2*(3/Math.PI*2*5));
        assertEquals(expected,b[2],1e-10);assertThrows(IllegalStateException.class,m::completeIntegrationInterval);
        m.rollbackTransaction();m.acceptBoundary(0,0);m.readBoundary(b,0);assertEquals(1050,b[2],1e-8);
        m.beginIntegrationInterval(2*h);m.step(h,0);m.cancelPrediction();assertThrows(IllegalStateException.class,m::completeIntegrationInterval);
        m.step(h,0);m.step(h,1);assertThrows(IllegalStateException.class,m::completeIntegrationInterval);m.step(h,0);m.step(h,1);m.completeIntegrationInterval();
    }
    @Test void sharedStateProposalsDoNotResampleAlgebraicCurrentOrConsumeTheInterval() {
        var m=rig(false,0).model();double[] initial=new double[15],trial=new double[15],rate=new double[15];
        m.readStaggeredState(initial,0);m.beginTransaction();m.setBoundary(1.02,0,1.03,0,1050,0);m.beginIntegrationInterval(.00001);var histories=m.getIntegrationHistories();
        for(int n=0;n<3;n++) {
            System.arraycopy(initial,0,trial,0,15);trial[6]+=.001*n;
            m.applyStaggeredState(trial,0,.00001);m.evaluateStaggered(.00001,rate,0);assertEquals(histories,m.getIntegrationHistories());
        }
        m.completeStaggeredStep(0);assertEquals(histories,m.getIntegrationHistories());
        assertThrows(IllegalStateException.class,()->m.acceptBoundary(.00001,0));assertThrows(IllegalStateException.class,m::completeIntegrationInterval);
        assertThrows(IllegalStateException.class,()->m.completeStaggeredStep(.00002));
        m.completeStaggeredStep(.00001);assertEquals(histories,m.getIntegrationHistories());assertThrows(IllegalStateException.class,()->m.completeStaggeredStep(.00001));
        m.completeIntegrationInterval();assertNotEquals(histories,m.getIntegrationHistories());m.rollbackTransaction();m.readStaggeredState(trial,0);assertArrayEquals(initial,trial);
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
        checkAllocation(staggered,true);
    }
    @ParameterizedTest @ValueSource(booleans={false,true})
    void warmedAlgebraicLineSamplingAllocatesNoObjects(boolean staggered) {
        checkAllocation(staggered,false);
    }
    @ParameterizedTest @ValueSource(booleans={false,true})
    void warmedLocalModifiedEulerStagesAllocateNoObjects(boolean dynamic) {
        var bean=ManagementFactory.getThreadMXBean();Assumptions.assumeTrue(bean instanceof com.sun.management.ThreadMXBean);
        var memory=(com.sun.management.ThreadMXBean)bean;Assumptions.assumeTrue(memory.isThreadAllocatedMemorySupported());
        memory.setThreadAllocatedMemoryEnabled(true);long thread=Thread.currentThread().threadId();var m=rig(dynamic,0).model();
        for(int k=0;k<5;k++)localEulerBatch(m,5000);
        long before=memory.getThreadAllocatedBytes(thread);localEulerBatch(m,5000);
        assertEquals(0,memory.getThreadAllocatedBytes(thread)-before);
    }
    private static void localEulerBatch(Chvdc2CoupledRuntime m,int count) {
        for(int k=0;k<count;k++) {m.beginTransaction();m.beginIntegrationInterval(.0001);
            m.step(.0001,0,10);m.step(.0001,1,10);m.completeIntegrationInterval();m.rollbackTransaction();}
    }
    private static void checkAllocation(boolean staggered,boolean dynamic) {
        var bean=ManagementFactory.getThreadMXBean();Assumptions.assumeTrue(bean instanceof com.sun.management.ThreadMXBean);var memory=(com.sun.management.ThreadMXBean)bean;
        Assumptions.assumeTrue(memory.isThreadAllocatedMemorySupported());memory.setThreadAllocatedMemoryEnabled(true);long thread=Thread.currentThread().threadId();
        var m=rig(dynamic,0).model();double[] x=new double[m.staggeredStateCount()],f=new double[x.length],i=new double[4];
        for(int n=0;n<20;n++)batch(m,staggered,x,f,i,20000);long before=memory.getThreadAllocatedBytes(thread);batch(m,staggered,x,f,i,20000);assertEquals(0,memory.getThreadAllocatedBytes(thread)-before);
    }
    private static void batch(Chvdc2CoupledRuntime m,boolean staggered,double[] x,double[] f,double[] i,int count) {
        for(int n=0;n<count;n++){m.beginTransaction();m.evaluateInjections(i,0);m.readPublishedStates(x,0);m.beginIntegrationInterval(.00001);if(staggered){m.readStaggeredState(x,0);m.evaluateStaggered(.00001,f,0);for(int j=0;j<x.length;j++)x[j]+=.00001*f[j];m.applyStaggeredState(x,0,.00001);m.completeStaggeredStep(.00001);}else{m.step(.00001,0);m.step(.00001,1);}m.completeIntegrationInterval();m.commitTransaction();}
    }
}
