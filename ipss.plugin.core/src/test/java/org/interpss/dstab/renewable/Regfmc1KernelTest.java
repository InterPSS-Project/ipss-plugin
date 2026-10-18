package org.interpss.dstab.renewable;

import static org.junit.jupiter.api.Assertions.*;
import static org.interpss.dstab.renewable.Regfmc1Data.Parameter.*;
import java.util.*;
import org.apache.commons.math3.complex.Complex;
import org.junit.jupiter.api.Test;

class Regfmc1KernelTest {
    private static Regfmc1Data data(int... flags){return Regfmc1Data.nativePsse(new Complex(.015,.23),flags,
            new double[]{.4,.35,3,.2,-.2,.025,.35,.02,.01,.02,1.5,0,100,3,.05,1.35,-1.35,1.5,-1.5,.2,-.35,.025,.3,.8,-.8,.6,-.6,.9,0,2,.15,-.15,.9,1.2});}
    private static Regfmc1Kernel model(Regfmc1Data data){var m=new Regfmc1Kernel(data,60);m.initialize(Complex.ONE,new Complex(.4,.2),.0005);return m;}
    @Test void equilibriumAcrossAllEightSelectorCombinationsAndNonzeroTerminalAngle() {
        Complex v=new Complex(1,.2),power=new Complex(.4,.2);
        for(int f=0;f<2;f++)for(int pq=0;pq<2;pq++)for(int flag=0;flag<2;flag++) {
            var m=new Regfmc1Kernel(data(f,pq,flag),60);m.initialize(v,power,.0005);var initial=m.getNamedStates();
            for(int i=0;i<100;i++)m.step(.0005,v);
            for(var entry:initial.entrySet())assertEquals(entry.getValue(),m.getNamedStates().get(entry.getKey()),1e-13,entry.getKey());
            assertEquals(0,m.boundary(v).rawGfmCurrent().abs(),1e-13);
            assertEquals(0,v.multiply(m.boundary(v).totalCurrent().conjugate()).subtract(power).abs(),1e-13);
        }
    }
    @Test void predictorAndCorrectorPreserveModifiedEulerAndScaledDampingCoordinate() {
        var d=data(1,0,1);var m=model(d);m.setReferenceSteps(0,0,0,0,.06,.1);double h=.001;
        m.step(h,Complex.ONE,0);double[] predicted=m.stateSnapshot();
        assertEquals(.0003,predicted[3],1e-15);assertEquals(1.0001,predicted[4],1e-15);
        assertEquals(.00002,predicted[8],1e-15);assertEquals(1,predicted[5]);assertEquals(0,predicted[9]);assertEquals(0,predicted[10]);
        Complex current=new Complex(.0001,0).divide(d.sourceImpedance());
        m.step(h,Complex.ONE,1);double[] corrected=m.stateSnapshot();
        assertEquals(.0003,corrected[3],1e-15);
        assertEquals(1+.5*h*(.1+(.0003+.035-.0001)/.35),corrected[4],1e-15);
        assertEquals(1+.5*h*.00002/.3,corrected[5],1e-15);
        assertEquals(.5*h*current.getReal()/.02,corrected[7],1e-15);
        assertEquals(.5*h*(-current.getImaginary())/.01,corrected[2],1e-15);
        assertEquals(.5*h*(.06/3+(.06-100*.00002)/3),corrected[8],1e-15);
        assertEquals(.5*h*3*100*.00002,corrected[9],1e-15);
        assertEquals(.5*h*2*Math.PI*60*.00002,corrected[10],1e-15);
    }
    @Test void observationsAndZeroTimeRefreshArePureAndKeepPredictorHistory() {
        var a=model(data(1,0,1));var b=model(data(1,0,1));
        a.setReferenceSteps(.1,.01,.01,.001,.03,.02);b.setReferenceSteps(.1,.01,.01,.001,.03,.02);
        a.step(.0005,Complex.ONE,0);b.step(.0005,Complex.ONE,0);var states=a.getNamedStates();
        for(int i=0;i<20;i++){a.boundary(new Complex(.9,.1));a.rawInternalVoltage();a.reactiveCommand();a.step(0,Complex.ONE);assertEquals(states,a.getNamedStates());}
        a.step(.0005,Complex.ONE,1);b.step(.0005,Complex.ONE,1);assertEquals(b.getNamedStates(),a.getNamedStates());
        assertEquals(11,states.size());assertThrows(UnsupportedOperationException.class,()->states.clear());
        double[] copy=a.stateSnapshot();copy[0]=99;assertNotEquals(99,a.stateSnapshot()[0]);
    }
    @Test void zeroTimeMeasurementsBypassWithoutPhantomStatesAndTeUsesNominalStep() {
        var d=data(1,0,1);double[] c=d.constants();for(var p:new Regfmc1Data.Parameter[]{TVF,TVR,TIF,TPF,TWM,TWREF})c[p.ordinal()]=0;
        c[TE.ordinal()]=.0001;var m=model(Regfmc1Data.nativePsse(d.sourceImpedance(),d.toNativeIcons(),c));
        m.setReferenceSteps(0,0,0,.001,0,.01);m.step(.0005,Complex.ONE,0);
        assertEquals(1+.0005*.0035/.001,m.stateSnapshot()[4],1e-15);
        m.step(.0005,Complex.ONE,1);m.step(0,new Complex(.9,0));
        var s=m.getNamedStates();Complex raw=m.rawInternalVoltage().subtract(new Complex(.9,0)).divide(d.sourceImpedance());
        assertEquals(.9,s.get("Vmeas"));assertEquals(1,s.get("GfmVrefMeas"));assertEquals(1.001,s.get("SpeedReferenceMeas"));
        assertEquals(1+m.speedDeviation(),s.get("SpeedMeas"));assertEquals(.9*raw.getReal(),s.get("GfmPmeas"),1e-13);
        assertEquals(-raw.getImaginary(),s.get("GfmIqMeas"),1e-13);assertEquals(11,s.size());
    }
    @Test void independentlyClippedPiOutputDoesNotFreezeAnInteriorIntegral() {
        var m=model(data(1,0,1));m.setReferenceSteps(0,0,0,0,0,100);m.step(.0001,Complex.ONE,0);
        assertEquals(.03,m.stateSnapshot()[3],1e-15);
        assertEquals(1+.0001*.2/.35,m.stateSnapshot()[4],1e-15,"PI output clips, but interior integral still advances");
    }
    @Test void activePiAndSpeedBoundsHoldOnlyOutwardMotionAndReleaseInward() {
        var m=model(data(1,0,1));m.setReferenceSteps(0,0,0,0,1000,100);
        for(int i=0;i<20;i++)m.step(.0005,Complex.ONE);
        assertEquals(.2,m.stateSnapshot()[3]);assertEquals(.2,m.speedDeviation());double oldDamping=m.stateSnapshot()[9];
        m.step(.0005,Complex.ONE);assertEquals(.2,m.stateSnapshot()[3]);assertEquals(.2,m.speedDeviation());assertNotEquals(oldDamping,m.stateSnapshot()[9]);
        m.setReferenceSteps(0,0,0,0,-1000,-100);for(int i=0;i<30;i++)m.step(.0005,Complex.ONE);
        assertEquals(-.2,m.stateSnapshot()[3]);assertEquals(-.35,m.speedDeviation());
        m.setReferenceSteps(0,0,0,0,1000,100);m.step(.0005,Complex.ONE,0);
        assertTrue(m.stateSnapshot()[3]>-.2);assertTrue(m.speedDeviation()>-.35);
    }
    @Test void gflAndGfmReferencesStayIndependentAndSelectorsRemoveOnlyTheirPaths() {
        var a=model(data(1,0,1));var b=model(data(1,0,1));a.setReferenceSteps(.1,.02,0,0,0,0);
        assertEquals(.5,a.boundary(Complex.ONE).totalCurrent().getReal(),1e-14);
        assertEquals(-.22,a.boundary(Complex.ONE).totalCurrent().getImaginary(),1e-14);
        a.step(.0005,Complex.ONE);b.step(.0005,Complex.ONE);assertEquals(b.getNamedStates(),a.getNamedStates());
        var off=model(data(0,0,0));var on=model(data(1,0,1));
        off.setReferenceSteps(0,0,.01,.01,0,0);on.setReferenceSteps(0,0,.01,.01,0,0);
        for(int i=0;i<10;i++){off.step(.0005,Complex.ONE);on.step(.0005,Complex.ONE);}
        assertEquals(0,off.reactiveCommand());assertTrue(on.reactiveCommand()>0);
        assertEquals(0,off.stateSnapshot()[5]);assertEquals(0,off.stateSnapshot()[6],"Native Fflag=0 leaves its unused measurement states at zero");
        var offFrequency=model(data(0,0,0));var onFrequency=model(data(1,0,0));
        offFrequency.setReferenceSteps(0,0,0,.01,0,0);onFrequency.setReferenceSteps(0,0,0,.01,0,0);
        for(int i=0;i<10;i++){offFrequency.step(.0005,Complex.ONE);onFrequency.step(.0005,Complex.ONE);}
        assertEquals(0,offFrequency.speedDeviation(),1e-13);assertTrue(onFrequency.speedDeviation()>0);
    }
    @Test void restartClearsReferencesAndHistoryAndRejectsUndefinedInputs() {
        var d=data(1,0,1);var m=new Regfmc1Kernel(d,60);assertThrows(IllegalStateException.class,m::getNamedStates);
        m.initialize(Complex.ONE,new Complex(.4,.2),.0005);var before=m.getNamedStates();m.setReferenceSteps(.1,.1,.1,.01,.1,.1);m.step(.0005,Complex.ONE,0);
        m.initialize(Complex.ONE,new Complex(.4,.2),.0005);assertEquals(before,m.getNamedStates());assertEquals(.4,m.boundary(Complex.ONE).totalCurrent().getReal(),1e-14);
        assertThrows(IllegalStateException.class,()->m.step(.0005,Complex.ONE,1));assertThrows(IllegalArgumentException.class,()->m.step(-1,Complex.ONE));
        assertThrows(IllegalArgumentException.class,()->m.step(.001,Complex.ONE,2));assertThrows(IllegalArgumentException.class,()->m.setReferenceSteps(Double.NaN,0,0,0,0,0));
        assertThrows(IllegalArgumentException.class,()->m.initialize(Complex.ZERO,new Complex(.4,.2),.0005));
        assertThrows(IllegalArgumentException.class,()->m.initialize(Complex.ONE,new Complex(2,0),.0005));
        assertThrows(IllegalArgumentException.class,()->m.initialize(Complex.ONE,new Complex(.4,.2),0));
        assertThrows(IllegalArgumentException.class,()->new Regfmc1Kernel(d,0));
        m.step(.0005,Complex.ONE,0);assertThrows(IllegalArgumentException.class,()->m.step(.001,Complex.ONE,1));m.step(.0005,Complex.ONE,1);
        double[] c=d.constants();c[VREF0.ordinal()]=1.5;
        assertThrows(IllegalArgumentException.class,()->model(Regfmc1Data.nativePsse(d.sourceImpedance(),d.toNativeIcons(),c)));
    }
    @Test void networkInitializationDefersTeCorrectionToTheActualFirstStepWithoutResettingReferences() {
        var d=data(1,0,1);double[] c=d.constants();c[TE.ordinal()]=.0001;d=Regfmc1Data.nativePsse(d.sourceImpedance(),d.toNativeIcons(),c);
        var deferred=new Regfmc1Kernel(d,60);var explicit=new Regfmc1Kernel(d,60);
        deferred.initialize(Complex.ONE,new Complex(.4,.2));explicit.initialize(Complex.ONE,new Complex(.4,.2),.0005);
        deferred.setReferenceSteps(0,0,0,0,.01,.01);explicit.setReferenceSteps(0,0,0,0,.01,.01);
        deferred.step(0,Complex.ONE);assertEquals(explicit.getNamedStates(),deferred.getNamedStates());
        deferred.step(.0005,Complex.ONE,0);explicit.step(.0005,Complex.ONE,0);assertEquals(explicit.getNamedStates(),deferred.getNamedStates());
        deferred.step(.0005,Complex.ONE,1);explicit.step(.0005,Complex.ONE,1);assertEquals(explicit.getNamedStates(),deferred.getNamedStates());
    }
}
