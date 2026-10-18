package org.interpss.dstab.renewable;

import static org.junit.jupiter.api.Assertions.*;
import static org.interpss.dstab.renewable.Reece1Data.Parameter.*;
import org.junit.jupiter.api.Test;
import org.interpss.dstab.renewable.Reece1LocalPi.Input;

class Reece1LocalPiTest {
    private static Reece1Data data(int qflag,int pflag,double kpq,double kiq,double kpp,double kip) {
        double[] c=new double[79];c[VDIP.ordinal()]=.7;c[VUP.ordinal()]=1.3;c[IQH1.ordinal()]=1;c[IQL1.ordinal()]=-1;
        c[QMAX.ordinal()]=1;c[QMIN.ordinal()]=-1;c[VMAX.ordinal()]=1.2;c[VMIN.ordinal()]=.8;
        c[DPMAX.ordinal()]=99;c[DPMIN.ordinal()]=-99;c[PMAX.ordinal()]=1;c[PMIN.ordinal()]=-1;
        c[VBLKH.ordinal()]=2;c[VBLKL.ordinal()]=.2;c[IMAX.ordinal()]=1.3;
        c[KQP.ordinal()]=kpq;c[KQI.ordinal()]=kiq;c[KPP.ordinal()]=kpp;c[KPI.ordinal()]=kip;
        return Reece1Data.fromNativeParameters(new int[]{0,0,qflag,0,1,0,pflag,0},c);
    }
    private static Reece1Data data(){return data(2,1,2,5,3,10);}
    private static Input input(double p,double q,boolean dip){return new Input(p,.3,q,.1,-1,1,-1,1,dip);}
    private static Reece1LocalPi initialized(){var m=new Reece1LocalPi(data());m.initialize(.1,.3,input(.3,.1,false));return m;}

    @Test void nativeShapeAndImmutableRoundTripPreserveEightSelectorsAndSeventyNineConstants() {
        var d=data();assertEquals(79,Reece1Data.Parameter.values().length);assertEquals(d,Reece1Data.fromNativeParameters(d.toNativeIcons(),d.toNativeConstants()));
        assertEquals(d.hashCode(),Reece1Data.fromNativeParameters(d.toNativeIcons(),d.toNativeConstants()).hashCode());
        double[] values=d.constants();values[77]=99;int[] flags=d.toNativeIcons();flags[2]=0;
        assertEquals(3,d.value(KPP));assertEquals(2,d.selectors().reactiveControlFlag());
        assertThrows(IllegalArgumentException.class,()->Reece1Data.fromNativeParameters(new int[7],new double[79]));
        assertThrows(IllegalArgumentException.class,()->Reece1Data.fromNativeParameters(new int[8],new double[78]));
    }
    @Test void nativeParameterOrderSeparatesNewPowerGainsFromReactiveAndVoltageGains() {
        assertEquals(17,KQP.ordinal());assertEquals(18,KQI.ordinal());assertEquals(19,KVP.ordinal());assertEquals(20,KVI.ordinal());
        assertEquals(69,RC.ordinal());assertEquals(70,XC.ordinal());assertEquals(76,TBLK.ordinal());assertEquals(77,KPP.ordinal());assertEquals(78,KPI.ordinal());
        for(int i=1;i<=10;i++) {
            assertEquals(29+2*(i-1),Reece1Data.Parameter.valueOf("VQ"+i).ordinal());
            assertEquals(30+2*(i-1),Reece1Data.Parameter.valueOf("IQ"+i).ordinal());
            assertEquals(49+2*(i-1),Reece1Data.Parameter.valueOf("VP"+i).ordinal());
            assertEquals(50+2*(i-1),Reece1Data.Parameter.valueOf("IP"+i).ordinal());
        }
        assertEquals(2,data().value(KQP));assertEquals(3,data().value(KPP));
    }
    @Test void selectorsPermitThreeReactiveModesButRequirePositiveActiveIntegralGainWhenEnabled() {
        for(int q=0;q<3;q++)assertEquals(q,data(q,0,0,0,0,0).selectors().reactiveControlFlag());
        assertThrows(IllegalArgumentException.class,()->data(3,0,0,0,0,0));
        assertThrows(IllegalArgumentException.class,()->data(2,1,0,0,3,0));
        assertThrows(IllegalArgumentException.class,()->data(2,2,0,0,3,10));
        assertEquals(1,data().selectors().currentPriority(false));assertEquals(0,data().selectors().currentPriority(true));
    }
    @Test void finiteTimesAndOrderedLimitsAreValidatedWithoutDiscardingSignedReactiveHold() {
        double[] c=data().constants();c[THLD.ordinal()]=-.2;assertEquals(-.2,Reece1Data.fromNativeParameters(data().toNativeIcons(),c).value(THLD));
        c[TRV.ordinal()]=-.1;assertThrows(IllegalArgumentException.class,()->Reece1Data.fromNativeParameters(data().toNativeIcons(),c));
        c[TRV.ordinal()]=0;c[PMIN.ordinal()]=2;assertThrows(IllegalArgumentException.class,()->Reece1Data.fromNativeParameters(data().toNativeIcons(),c));
        c[PMIN.ordinal()]=0;c[KPP.ordinal()]=Double.NaN;assertThrows(IllegalArgumentException.class,()->Reece1Data.fromNativeParameters(data().toNativeIcons(),c));
    }
    @Test void activeVdlRejectsNegativeCurrentWhileReactiveNegativeLimitsRemainRepresentable() {
        double[] c=data().constants();c[VQ1.ordinal()]=1;c[IQ1.ordinal()]=-.1;c[VP1.ordinal()]=1;c[IP1.ordinal()]=-.2;
        assertThrows(IllegalArgumentException.class,()->Reece1Data.fromNativeParameters(data().toNativeIcons(),c));
        c[IP1.ordinal()]=.2;c[IP3.ordinal()]=-99; // beyond the Vp2=0 terminator
        assertEquals(-.1,Reece1Data.fromNativeParameters(data().toNativeIcons(),c).value(IQ1));
    }
    @Test void equilibriumAndStateObservationArePureAndImmutable() {
        var m=initialized();for(int i=0;i<200;i++)m.step(.001,input(.3,.1,false));
        assertArrayEquals(new double[]{.1,.3},m.getIntegralContributions(),0);assertEquals(.1,m.reactiveOutput());assertEquals(.3,m.activeOutput());
        double[] copy=m.getIntegralContributions();copy[0]=99;assertEquals(.1,m.getIntegralContributions()[0]);
        assertThrows(UnsupportedOperationException.class,()->m.getNamedStates().put("x",1.));
    }
    @Test void modifiedEulerUsesBothEndpointErrorsAndDistinctLocalPiGains() {
        var m=initialized();m.step(.01,input(.4,.15,false),0);
        assertArrayEquals(new double[]{.1025,.31},m.getIntegralContributions(),1e-15);assertEquals(.2025,m.reactiveOutput(),1e-15);assertEquals(.61,m.activeOutput(),1e-15);
        m.step(.01,input(.5,.2,false),1);
        assertArrayEquals(new double[]{.10375,.315},m.getIntegralContributions(),1e-15);assertEquals(.30375,m.reactiveOutput(),1e-15);assertEquals(.915,m.activeOutput(),1e-15);
    }
    @Test void voltageDipFreezesIntegralButNotProportionalResponseAndSelectsFaultPriority() {
        var m=initialized();m.step(.01,input(.4,.2,true));assertArrayEquals(new double[]{.1,.3},m.getIntegralContributions(),0);
        assertEquals(.3,m.reactiveOutput(),1e-15);assertEquals(.6,m.activeOutput(),1e-15);assertEquals(0,m.currentPriority());
        m.step(0,input(.4,.2,false));assertEquals(1,m.currentPriority());assertArrayEquals(new double[]{.1,.3},m.getIntegralContributions(),0);
    }
    @Test void conditionalIntegrationStopsAtBothBoundsAndRecoversInward() {
        for(double sign:new double[]{-1,1}) {
            var m=new Reece1LocalPi(data(2,1,0,1,0,1));m.initialize(.9*sign,.9*sign,input(.3,.1,false));
            Input outward=input(.3+sign,.1+sign,false);m.step(1,outward);
            for(int i=0;i<20;i++)m.step(.1,outward);
            assertArrayEquals(new double[]{sign,sign},m.getIntegralContributions(),0);
            m.step(.1,input(.3-sign,.1-sign,false));
            assertTrue(sign*m.getIntegralContributions()[0]<1);assertTrue(sign*m.getIntegralContributions()[1]<1);
        }
    }
    @Test void proportionalOutputSaturationDoesNotPrematurelyFreezeTheNativeIntegral() {
        var m=initialized();m.step(.01,input(.31,.11,false),0);m.step(.01,input(1.3,1.1,false),1);
        assertArrayEquals(new double[]{.12525,.3505},m.getIntegralContributions(),1e-15);assertEquals(1,m.reactiveOutput());assertEquals(1,m.activeOutput());
    }
    @Test void movingNegativeReactiveCurrentLimitsDoNotRewriteStateDuringAlgebraicRefresh() {
        var m=initialized();m.step(0,new Input(.3,.3,.1,.1,-.2,-.2,-1,1,false));
        assertEquals(-.2,m.reactiveOutput());assertArrayEquals(new double[]{.1,.3},m.getIntegralContributions(),0);
    }
    @Test void zeroDurationRefreshDoesNotConsumePredictorCorrectorHistory() {
        var a=initialized();var b=initialized();a.step(.001,input(.4,.2,false),0);b.step(.001,input(.4,.2,false),0);
        for(int i=0;i<10;i++)a.step(0,input(.5,.3,false));
        a.step(.001,input(.4,.2,false),1);b.step(.001,input(.4,.2,false),1);assertArrayEquals(a.getIntegralContributions(),b.getIntegralContributions(),0);
    }
    @Test void bypassModesDoNotPretendToImplementTheOuterVoltageOrReactiveLagPaths() {
        var m=new Reece1LocalPi(data(1,0,2,5,0,0));m.initialize(.1,.3,input(.3,.1,false));m.step(.01,input(.4,.2,false));
        assertArrayEquals(new double[]{0,0},m.getIntegralContributions(),0);assertEquals(.4,m.activeOutput());assertThrows(IllegalStateException.class,m::reactiveOutput);
    }
    @Test void initializationAndInputGuardsRejectInvalidStagesAndClearHistoryOnRestart() {
        var m=new Reece1LocalPi(data());assertThrows(IllegalStateException.class,m::activeOutput);m.initialize(.1,.3,input(.3,.1,false));
        m.step(.001,input(.4,.2,false),0);m.initialize(.2,.4,input(.3,.1,false));
        assertThrows(IllegalStateException.class,()->m.step(.001,input(.4,.2,false),1));assertEquals(.2,m.reactiveOutput());
        assertThrows(IllegalArgumentException.class,()->m.step(-1,input(.3,.1,false)));
        assertThrows(IllegalArgumentException.class,()->m.step(.1,input(.3,.1,false),2));
        assertThrows(IllegalArgumentException.class,()->new Input(Double.NaN,0,0,0,-1,1,0,1,false));
    }
}
