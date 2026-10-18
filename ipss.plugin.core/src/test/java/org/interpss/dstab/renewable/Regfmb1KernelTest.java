package org.interpss.dstab.renewable;

import static org.junit.jupiter.api.Assertions.*;
import java.util.*;
import org.apache.commons.math3.complex.Complex;
import org.junit.jupiter.api.Test;

class Regfmb1KernelTest {
    private static Regfmb1Data data(int[] flags){return Regfmb1Data.nativePsse(new Complex(.015,.23),flags,
            new double[]{.05,0,5.86,.01,.1,-.1,.265,2.65,.1,-.1,.02,3,.001,100,50,1.2,.9,2,2,.02,.02,.02,.02,1,.05});}
    private static Regfmb1Kernel.Terminal terminal(double p,double q,double v){return Regfmb1Kernel.Terminal.fromPower(new Complex(v,0),new Complex(p,q));}
    @Test void equilibriumAcrossFrequencyPriorityStorageAndVoltageSelectors() {
        for(int w=0;w<2;w++)for(int pq=0;pq<2;pq++)for(int f=0;f<2;f++)for(int es=0;es<2;es++)for(int mode=0;mode<3;mode++) {
            var m=new Regfmb1Kernel(data(new int[]{w,mode==2?1:0,mode>0?1:0,pq,f,es}),60);var t=terminal(.4,.2,1);
            m.initialize(t);var initial=m.getNamedStates();for(int i=0;i<100;i++)m.step(.0005,t);
            for(var entry:initial.entrySet())assertEquals(entry.getValue(),m.getNamedStates().get(entry.getKey()),1e-13,entry.getKey());
            assertEquals(0,m.current(t).subtract(t.current()).abs(),1e-12);
        }
    }
    @Test void predictorAndCorrectorUseModifiedEuler() {
        var m=new Regfmb1Kernel(data(new int[]{0,0,0,0,1,0}),60);var t=terminal(.4,.2,1);m.initialize(t);m.setReferenceSteps(.1,0,0);
        m.step(.001,t,0);assertEquals(.001*.1/6,m.speedDeviation(),1e-15);
        double last=(.1-100.001*(.001*.1/6))/6;
        m.step(.001,t,1);assertEquals(.001*.5*(.1/6+last),m.speedDeviation(),1e-15);
        assertEquals(50*100*(.001*.1/6)*.001*.5,m.getNamedStates().get("DampingFilter"),1e-15);
    }
    @Test void algebraicRefreshAndReadoutPreserveStageHistoryAndNamedStates() {
        var t=terminal(.4,.2,1);var a=new Regfmb1Kernel(data(new int[]{0,0,0,0,1,0}),60);var b=new Regfmb1Kernel(data(new int[]{0,0,0,0,1,0}),60);
        a.initialize(t);b.initialize(t);a.setReferenceSteps(.1,.01,0);b.setReferenceSteps(.1,.01,0);a.step(.001,t,0);b.step(.001,t,0);
        var states=a.getNamedStates();for(int i=0;i<10;i++){a.step(0,t);a.current(t);assertEquals(states,a.getNamedStates());}
        a.step(.001,t,1);b.step(.001,t,1);assertEquals(b.getNamedStates(),a.getNamedStates());
        assertThrows(UnsupportedOperationException.class,()->states.clear());double[] copy=a.stateSnapshot();copy[0]=999;assertNotEquals(999,a.stateSnapshot()[0]);
    }
    @Test void bypassedMeasurementsTrackTerminalInputsWithoutAddingStates() {
        var d=data(new int[]{0,0,0,0,1,0});double[] c=d.constants();for(var p:new Regfmb1Data.Parameter[]{Regfmb1Data.Parameter.TP,Regfmb1Data.Parameter.TPF,Regfmb1Data.Parameter.TQF,Regfmb1Data.Parameter.TVF,Regfmb1Data.Parameter.TIF})c[p.ordinal()]=0;
        var m=new Regfmb1Kernel(Regfmb1Data.nativePsse(d.sourceImpedance(),d.toNativeIcons(),c),60);m.initialize(terminal(.4,.2,1));m.step(0,terminal(.5,.3,.9));
        var states=m.getNamedStates();assertEquals(.5,states.get("Pmeas"),1e-15);assertEquals(.3,states.get("Qmeas"),1e-15);assertEquals(.9,states.get("Vmeas"),0);assertEquals(14,states.size());
    }
    @Test void boundedSpeedAndVoltageIntegralStayFiniteDuringLargeReferenceSteps() {
        var m=new Regfmb1Kernel(data(new int[]{0,0,0,0,1,0}),60);var t=terminal(.4,.2,1);m.initialize(t);
        for(double sign:new double[]{1,-1}) {m.setReferenceSteps(sign*100,sign*100,0);for(int i=0;i<1000;i++)m.step(.0005,t);
            assertEquals(sign*.1,m.speedDeviation(),1e-12);assertTrue(m.getNamedStates().get("InternalRelativeAngle")>=0);
            assertTrue(m.current(t).abs()<=2+1e-12);for(double state:m.stateSnapshot())assertTrue(Double.isFinite(state));}
    }
    @Test void restartResetsReferencesAndCorrectorHistoryAndRejectsInvalidInputs() {
        var m=new Regfmb1Kernel(data(new int[]{0,0,0,0,1,0}),60);var t=terminal(.4,.2,1);m.initialize(t);var before=m.getNamedStates();
        m.setReferenceSteps(.1,.01,.001);m.step(.001,t,0);m.initialize(t);assertEquals(before,m.getNamedStates());
        assertThrows(IllegalStateException.class,()->m.step(.001,t,1));assertThrows(IllegalArgumentException.class,()->m.step(-1,t));
        assertThrows(IllegalArgumentException.class,()->m.setReferenceSteps(Double.NaN,0,0));
        assertThrows(IllegalArgumentException.class,()->new Regfmb1Kernel(data(new int[6]),0));
        assertThrows(IllegalArgumentException.class,()->m.initialize(terminal(3,0,1)));
    }
    @Test void lowVoltagePllDropsProportionalInputAndFreezesItsIntegralAndAngle() {
        var m=new Regfmb1Kernel(data(new int[]{0,0,0,0,1,0}),60);m.initialize(terminal(.4,.2,1));
        Complex v=new Complex(Math.cos(.02),Math.sin(.02));var excited=Regfmb1Kernel.Terminal.fromPower(v,new Complex(.4,.2));
        for(int i=0;i<20;i++)m.step(.0005,excited);
        var before=m.getNamedStates();double output=m.pllSpeedDeviation(excited);
        assertTrue(Math.abs(output-before.get("PllIntegral"))>1e-4);
        var low=new Regfmb1Kernel.Terminal(v.multiply(.005),Complex.ZERO);
        assertEquals(before.get("PllIntegral"),m.pllSpeedDeviation(low),0);
        for(int i=0;i<20;i++)m.step(.0005,low);
        assertEquals(before.get("PllIntegral"),m.getNamedStates().get("PllIntegral"),0);
        assertEquals(before.get("PllAngle"),m.getNamedStates().get("PllAngle"),0);
    }
}
