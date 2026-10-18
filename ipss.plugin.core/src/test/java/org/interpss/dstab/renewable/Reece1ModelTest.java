package org.interpss.dstab.renewable;

import static org.junit.jupiter.api.Assertions.*;
import static org.interpss.dstab.renewable.Reece1Data.Parameter.*;
import java.util.Map;
import org.junit.jupiter.api.Test;

class Reece1ModelTest {
    private static Reece1Data data(int qflag,int vflag,int peflag) {
        double[] c=new double[79];c[0]=.7;c[1]=1.3;c[2]=.02;c[3]=-.05;c[4]=.05;c[6]=1;c[7]=-1;
        c[12]=.02;c[13]=1;c[14]=-1;c[15]=1.2;c[16]=.8;c[17]=2;c[18]=5;c[19]=2;c[20]=5;
        c[22]=.02;c[23]=99;c[24]=-99;c[25]=1;c[26]=-1;c[27]=1.3;c[28]=.02;c[74]=.2;c[75]=2;c[77]=2;c[78]=10;
        return Reece1Data.fromNativeParameters(new int[]{0,vflag,qflag,0,1,0,peflag,0},c);
    }
    private static Reece1Data change(Reece1Data d,Map<Reece1Data.Parameter,Double> values) {
        double[] c=d.constants();values.forEach((k,v)->c[k.ordinal()]=v);return Reece1Data.fromNativeParameters(d.toNativeIcons(),c);
    }
    private static Reece1Model model(Reece1Data d){var m=new Reece1Model(d);m.initialize(.3,.1,1);return m;}
    @Test void equilibriumAcrossEveryReactiveVoltageAndActiveSelector() {
        for(int q=0;q<3;q++)for(int v=0;v<2;v++)for(int pe=0;pe<2;pe++) {
            var m=model(data(q,v,pe));var before=m.getNamedStates();
            for(int i=0;i<100;i++)m.step(.0005,.3,.1,1,1);
            for(var s:before.entrySet())assertEquals(s.getValue(),m.getNamedStates().get(s.getKey()),1e-12,s.getKey());
            assertEquals(.3,m.getIpcmd(),1e-12);assertEquals(-.1,m.getIqcmd(),1e-12);
        }
    }
    @Test void powerMeasurementRunsForActivePiEvenWithoutPowerFactorControl() {
        var m=model(data(2,0,1));m.step(.001,.4,.1,1,1,0);
        assertEquals(.305,m.getNamedStates().get("Pmeas"),1e-15);
        m.step(.001,.4,.1,1,1,1);assertEquals(.304875,m.getNamedStates().get("Pmeas"),1e-15);
        var disabled=model(data(2,0,0));disabled.step(.001,.4,.1,1,1);assertEquals(0,disabled.getNamedStates().get("Pmeas"));
    }
    @Test void referenceRateLimiterPrecedesPowerPiAndIsNotThePordTimeConstant() {
        var m=model(change(data(2,0,1),Map.of(DPMAX,.1,DPMIN,-.2)));
        m.setReferenceSteps(.2,0);m.step(.001,.3,.1,1,1);
        assertEquals(.3001,m.getPreparedPowerReference(),1e-15);
        assertEquals(.300001,m.getNamedStates().get("PIP"),1e-15);
        m.setReferenceSteps(-.2,0);m.step(.001,.3,.1,1,1);assertEquals(.2999,m.getPreparedPowerReference(),1e-15);
    }
    @Test void nativeDipFreezesReactiveStatesButActivePiAndFRTLimitsStillAct() {
        var m=model(change(data(2,0,1),Map.of(TRV,0.0,TPORD,0.0,KQV,1.0,DPMAX,1000.0)));
        var before=m.getNamedStates();m.setReferenceSteps(.1,.2);m.step(.001,.3,.1,.5,1);
        for(String key:new String[]{"PIQ","PIV","Q_V"})assertEquals(before.get(key),m.getNamedStates().get(key),0,key);
        assertEquals(.301,m.getNamedStates().get("PIP"),1e-12);
        assertTrue(m.isVoltageDip());assertTrue(-m.getIqcmd()>.5);
        assertEquals(.501,m.getNamedStates().get("Pord"),1e-12,"zero Tpord bypasses freezing");
        assertTrue(Math.hypot(m.getIpcmd(),m.getIqcmd())<=1.3+1e-12);
    }
    @Test void nonzeroPordFreezesDuringDipWhileMeasurementStatesContinue() {
        var m=model(change(data(2,0,1),Map.of(TRV,0.0)));
        m.setReferenceSteps(.2,0);m.step(.001,.4,.1,.5,1);
        assertEquals(.3,m.getNamedStates().get("Pord"),0);assertTrue(m.getNamedStates().get("Pmeas")>.3);
    }
    @Test void reactiveLagAndCompensationUseTheirOwnStatesAndTimes() {
        var m=model(change(data(0,0,0),Map.of(TR1,.04,KC,.2)));
        m.setReferenceSteps(0,.1);m.step(.001,.3,.2,1,1);
        assertTrue(m.getNamedStates().get("Q_V")>.1);assertEquals(0,m.getNamedStates().get("Vcomp"));
        var voltage=model(change(data(1,1,0),Map.of(TR1,.04,KC,.2)));
        voltage.step(.001,.3,.2,1,1);assertTrue(voltage.getNamedStates().get("Vcomp")>1.02);
    }
    @Test void negativeReactiveVdlForcesAbsorptionAndChargingRespectsKe() {
        var m=model(change(data(2,0,0),Map.of(VQ1,1.0,IQ1,-.2,KE,.5,TPORD,0.0,DPMIN,-2000.0)));
        m.setReferenceSteps(-1,0);m.step(.001,.3,.1,1,1);assertEquals(.2,m.getIqcmd(),0);
        assertEquals(-.65,m.getIpcmd(),1e-15);
    }
    @Test void blockingAndDelayedReleaseDoNotAdvanceTwicePerCorrector() {
        var m=model(change(data(2,0,0),Map.of(TRV,0.0,TBLK,.002)));
        m.step(.001,.3,.1,.1,1);assertTrue(m.isBlocked());assertEquals(0,m.getIpcmd(),0);assertEquals(0,m.getIqcmd(),0);
        m.step(.001,.3,.1,1,1);assertTrue(m.isBlocked());m.step(.001,.3,.1,1,1);assertTrue(m.isBlocked());
        m.step(.001,.3,.1,1,1);assertFalse(m.isBlocked());assertTrue(m.getIpcmd()>0);
    }
    @Test void compensationModesAreDistinctAndBasedOnMachineBasePAndQ() {
        var d=data(1,0,0);int[] flags=d.toNativeIcons();flags[5]=1;
        var m=model(Reece1Data.fromNativeParameters(flags,change(d,Map.of(RC,.1,XC,.2)).constants()));
        assertEquals(Math.hypot(1-.05,.05),m.getNamedStates().get("Vcomp"),1e-15);
    }
    @Test void voltageModeLimitsVextBeforeAddingTheConfiguredBias() {
        var m=new Reece1Model(change(data(1,0,0),Map.of(VBIAS,.03,KC,.1,TR1,.03,KVP,2.0,KVI,10.0)));
        double p=.240000009537,q=.0474445894361,v=1.00999999046;
        m.initialize(p,q,v);m.setReferenceSteps(0,.02);m.step(0,p,q,v,1);
        assertEquals(q/v+2*(1+.03-(v+.1*q)),-m.getIqcmd(),1e-12);
    }
    @Test void aBlockedUnitCannotRestartExactlyOnTheBlockingThreshold() {
        var m=model(change(data(2,0,0),Map.of(TRV,0.0)));
        m.step(.001,.3,.1,.1,1);m.step(.001,.3,.1,.2,1);assertTrue(m.isBlocked());
        m.step(.001,.3,.1,.21,1);assertFalse(m.isBlocked());
    }
    @Test void signedReactiveHoldAndActiveCommandHoldAreMeasuredOncePerStep() {
        for(double sign:new double[]{-1,1}) {
            var m=model(change(data(2,0,0),Map.of(TRV,0.0,THLD,sign*.002,THLD2,.002,IQFRZ,.25,KQV,1.0)));
            m.step(.001,.3,.1,.5,1);double ip=m.getIpcmd(),iq=-m.getIqcmd();
            m.step(.001,.3,.1,1,1);assertEquals(ip,m.getIpcmd(),1e-15);assertEquals(sign>0?iq:.25,-m.getIqcmd(),1e-15);
            m.step(.001,.3,.1,1,1);assertEquals(ip,m.getIpcmd(),1e-15);
            m.step(.001,.3,.1,1,1);assertEquals(.3,m.getIpcmd(),1e-15);assertEquals(.1,-m.getIqcmd(),1e-15);
        }
    }
    @Test void nonUnitInitialSpeedUsesThePreMultiplierReferenceCoordinate() {
        var d=data(2,0,1);int[] icons=d.toNativeIcons();icons[3]=1;
        var m=new Reece1Model(Reece1Data.fromNativeParameters(icons,d.constants()));m.setGeneratorSpeed(.95);m.initialize(.3,.1,1);
        assertEquals(.3/.95,m.getPreparedPowerReference(),1e-15);
        for(int i=0;i<20;i++)m.step(.001,.3,.1,1,1);
        assertEquals(.3,m.getIpcmd(),1e-15);assertEquals(.3,m.getNamedStates().get("PIP"),1e-15);
    }
    @Test void auxiliaryPowerIsAddedAfterPordAndBeforeVoltageDivision() {
        var m=model(data(2,0,0));m.setAuxiliaryPower(.05);m.step(0,.3,.1,1,1);
        assertEquals(.35,m.getIpcmd(),1e-15);assertEquals(.3,m.getNamedStates().get("Pord"));
    }
    @Test void zeroDurationRefreshAndObservationPreserveCorrectorHistory() {
        var a=model(data(2,0,1));var b=model(data(2,0,1));
        a.step(.001,.4,.2,1.01,1,0);b.step(.001,.4,.2,1.01,1,0);
        for(int i=0;i<10;i++){a.step(0,.4,.2,1.01,1);a.getNamedStates();}
        a.step(.001,.4,.2,1.01,1,1);b.step(.001,.4,.2,1.01,1,1);assertEquals(a.getNamedStates(),b.getNamedStates());
        assertThrows(UnsupportedOperationException.class,()->a.getNamedStates().clear());
    }
    @Test void localPowerPiStopsAtBothLimitsAndReleasesInward() {
        for(double sign:new double[]{-1,1}) {
            var m=model(change(data(2,0,1),Map.of(KPP,0.0,KPI,10.0)));
            m.setReferenceSteps(2*sign,0);
            for(int i=0;i<1000;i++)m.step(.001,.3,.1,1,1);
            assertEquals(sign,m.getNamedStates().get("PIP"),1e-12);
            m.setReferenceSteps(-2*sign,0);
            for(int i=0;i<50;i++)m.step(.001,.3,.1,1,1);
            assertTrue(sign*m.getNamedStates().get("PIP")<.9);
        }
    }
    @Test void nativeCascadeReactivePiLimitsItsIntegralNotTheSumWithProportionalGain() {
        var m=model(change(data(1,1,0),Map.of(VMAX,1.1,VMIN,.9)));
        m.setReferenceSteps(0,.02);for(int i=0;i<1500;i++)m.step(.001,.3,.1,1,1);
        assertEquals(1.1,m.getNamedStates().get("PIQ"),1e-12);
    }
    @Test void nativeActivePiKeepsItsBoundsAndReleasesWhilePowerOrderIsFrozen() {
        for(double sign:new double[]{-1,1}) {
            var m=model(change(data(2,0,1),Map.of(TRV,0.0,KPP,0.0,KPI,10.0)));
            m.setReferenceSteps(2*sign,0);
            for(int i=0;i<1000;i++)m.step(.001,.3,.1,.5,1);
            assertTrue(m.isVoltageDip());assertEquals(sign,m.getNamedStates().get("PIP"),1e-12);
            assertEquals(.3,m.getNamedStates().get("Pord"),0);
            m.setReferenceSteps(-2*sign,0);
            for(int i=0;i<50;i++)m.step(.001,.3,.1,.5,1);
            assertTrue(sign*m.getNamedStates().get("PIP")<.9);
            assertEquals(.3,m.getNamedStates().get("Pord"),0);
        }
    }
    @Test void restartClearsReferenceEventsAndStageHistoryAndRejectsInvalidInputs() {
        var m=model(data(2,0,1));m.setReferenceSteps(.1,.1);m.step(.001,.3,.1,1,1,0);m.initialize(.3,.1,1);
        assertThrows(IllegalStateException.class,()->m.step(.001,.3,.1,1,1,1));assertEquals(.3,m.getIpcmd(),1e-15);
        assertThrows(IllegalArgumentException.class,()->m.step(-1,.3,.1,1,1));
        assertThrows(IllegalArgumentException.class,()->m.setReferenceSteps(Double.NaN,0));
        assertThrows(IllegalArgumentException.class,()->m.setGeneratorSpeed(0));
    }
}
