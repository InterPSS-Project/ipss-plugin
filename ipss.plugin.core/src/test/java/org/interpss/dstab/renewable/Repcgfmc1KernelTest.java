package org.interpss.dstab.renewable;

import static org.junit.jupiter.api.Assertions.*;
import static org.interpss.dstab.renewable.Repcgfmc1Data.Parameter.*;
import org.junit.jupiter.api.Test;

class Repcgfmc1KernelTest {
    private static Repcgfmc1Data data(int v,int vr,int f,double[] c){return new Repcgfmc1Data(3,2,3,"1",v,vr,f,c);}
    private static Repcgfmc1Initialization.Measurements initial(){return new Repcgfmc1Initialization.Measurements(100,50,1.02,1.01,.2,.1,.2,.095,true);}
    private static Repcgfmc1Kernel.Input input(){return new Repcgfmc1Kernel.Input(1.02,1.01,1,1,.2,.095);}
    private static Repcgfmc1Kernel kernel(Repcgfmc1Data data){var m=new Repcgfmc1Kernel(data);m.initialize(initial(),.001);return m;}
    @Test void equilibriumAndNamedStatesUseTheCorrectPlantBase(){
        var m=kernel(data(1,1,0,Repcgfmc1FoundationTest.defaults()));double[] first=m.states();
        for(int i=0;i<100;i++)m.step(.001,input());assertArrayEquals(first,m.states(),2e-14);
        assertEquals(15,m.getNamedStates().size());assertEquals(.4,m.output(input()).active(),1e-14);assertEquals(.2,m.output(input()).reactive(),1e-14);
        assertEquals(1,m.output(input()).frequency(),1e-14);assertEquals(1.02,m.output(input()).voltage(),1e-14);
        assertThrows(UnsupportedOperationException.class,()->m.getNamedStates().put("bad",1.));
        double[] copy=m.states();copy[0]=9;assertEquals(1,m.states()[0]);
    }
    @Test void transportLagUsesExplicitModifiedEulerPredictorAndCorrector(){
        double[] c=Repcgfmc1FoundationTest.defaults();c[RLOSS.ordinal()]=c[XLOSS.ordinal()]=c[KIP.ordinal()]=0;
        var m=kernel(data(0,0,0,c));var r=m.references();m.setReferences(new Repcgfmc1Kernel.References(1,r.activeBranch()+.04,r.reactiveBranch(),0,0,0));
        m.step(.001,input(),0);assertEquals(.4008,m.states()[11],1e-14);
        m.step(.001,input(),1);assertEquals(.400792,m.states()[11],1e-14);
        assertEquals(.001,m.time(),0);
    }
    @Test void powerIntegralsHitBothBoundsAndReleaseInward(){
        double[] c=Repcgfmc1FoundationTest.defaults();c[RLOSS.ordinal()]=c[XLOSS.ordinal()]=0;
        c[PERRMAX.ordinal()]=c[QERRMAX.ordinal()]=.0001;c[PERRMIN.ordinal()]=c[QERRMIN.ordinal()]=-.0001;
        var m=kernel(data(0,0,0,c));var r=m.references();
        m.setReferences(new Repcgfmc1Kernel.References(1,r.activeBranch(),r.reactiveBranch(),0,.2,.2));
        for(int i=0;i<20;i++)m.step(.001,input());assertEquals(.0001,m.states()[10]);assertEquals(.0001,m.states()[12]);
        m.setReferences(new Repcgfmc1Kernel.References(1,r.activeBranch(),r.reactiveBranch(),0,-.2,-.2));m.step(.001,input());
        assertTrue(m.states()[10]<.0001);assertTrue(m.states()[12]<.0001);
        for(int i=0;i<40;i++)m.step(.001,input());assertEquals(-.0001,m.states()[10]);assertEquals(-.0001,m.states()[12]);
    }
    @Test void voltageOutputClippingDoesNotFreezeAnInteriorIntegral(){
        double[] c=Repcgfmc1FoundationTest.defaults();c[KPVC.ordinal()]=10;
        var m=kernel(data(1,1,0,c));var r=m.references();double before=m.states()[8];
        m.setReferences(new Repcgfmc1Kernel.References(1,r.activeBranch(),r.reactiveBranch(),r.voltage()+.05,0,0));
        m.step(0,input());assertEquals(.3,m.output(input()).voltageControl());m.step(.001,input());assertTrue(m.states()[8]>before);
    }
    @Test void frequencyRateLimitAndLowVoltageSelectionAreSeparateFromTheLag(){
        double[] c=Repcgfmc1FoundationTest.defaults();c[TFREFD.ordinal()]=0;c[VFTH.ordinal()]=.9;c[FREQMAX.ordinal()]=1.0001;
        var m=kernel(data(0,0,0,c));var high=new Repcgfmc1Kernel.Input(1,1,1,1.2,.2,.095);
        m.step(.01,high);assertEquals(1.0005,m.states()[0],1e-14);assertEquals(1.0001,m.output(high).frequency());
        var r=m.references();m.setReferences(new Repcgfmc1Kernel.References(1.00002,r.activeBranch(),r.reactiveBranch(),0,0,0));
        var low=new Repcgfmc1Kernel.Input(1,1,.1,1.2,.2,.095);m.step(0,low);
        assertEquals(1.00002,m.output(low).filteredFrequency());assertEquals(1.00002,m.output(low).frequency());assertEquals(1.0005,m.states()[0],1e-14);
    }
    @Test void ffrHoldsRampsBothSignsAndCannotRetriggerWhileActive(){
        double[] c=Repcgfmc1FoundationTest.defaults();c[TFFR.ordinal()]=.004;c[PFFRLOW.ordinal()]=.003;c[PFFRHIGH.ordinal()]=-.002;c[FFRR.ordinal()]=1;
        var m=kernel(data(0,0,1,c));m.setFastFrequencyThresholds(1.001,1.05);m.step(0,input());assertEquals(.003,m.states()[14]);
        m.setFastFrequencyThresholds(.98,.999);
        for(int i=0;i<4;i++){m.step(.001,input());assertEquals(.003,m.states()[14]);}
        for(int i=0;i<3;i++)m.step(.001,input());assertEquals(0,m.states()[14]);
        m.step(.001,input());assertEquals(-.002,m.states()[14]);assertEquals(.008,m.output(input()).ffrStartTime(),1e-14);
        m.setFastFrequencyThresholds(.98,1.05);
        for(int i=0;i<8;i++)m.step(.001,input());assertEquals(0,m.states()[14]);assertEquals(0,m.output(input()).ffrStartTime());
    }
    @Test void zeroTimeBypassesAndQueriesNeverAdvanceDynamicStatesOrClock(){
        double[] c=Repcgfmc1FoundationTest.defaults();for(var p:new Repcgfmc1Data.Parameter[]{TFREFD,TVF,TVREFD,TVD,TPF,TPD,TQF,TQD,TVC})c[p.ordinal()]=0;
        var m=kernel(data(0,0,0,c));var changed=new Repcgfmc1Kernel.Input(.9,.95,1,1,.19,.08);
        double integral=m.states()[10];m.step(0,changed);assertEquals(.9,m.states()[3]);assertEquals(.9,m.states()[4]);assertEquals(.9,m.states()[5]);
        assertEquals(.38,m.states()[6]);assertEquals(.16,m.states()[7]);assertEquals(integral,m.states()[10]);assertEquals(0,m.time());
        var before=m.getNamedStates();for(int i=0;i<50;i++)m.output(changed);assertEquals(before,m.getNamedStates());
    }
    @Test void invalidStagesInputsAndUninitializedUseAreRejected(){
        var d=data(1,1,0,Repcgfmc1FoundationTest.defaults());var fresh=new Repcgfmc1Kernel(d);
        assertThrows(IllegalStateException.class,fresh::states);assertThrows(IllegalStateException.class,()->fresh.step(.001,input()));
        var m=kernel(d);assertThrows(IllegalStateException.class,()->m.step(.001,input(),1));
        assertThrows(IllegalArgumentException.class,()->m.step(-.001,input(),0));assertThrows(IllegalArgumentException.class,()->m.step(.001,input(),2));
        m.step(.001,input(),0);assertThrows(IllegalStateException.class,()->m.step(.002,input(),1));
        assertThrows(IllegalArgumentException.class,()->new Repcgfmc1Kernel.Input(Double.NaN,1,1,1,0,0));
        assertThrows(IllegalArgumentException.class,()->m.setFastFrequencyThresholds(1.1,.9));
    }
    @Test void unusedRemoteVoltageStorageIsRetainedWhenBothVoltagePathsAreOff(){
        var m=kernel(data(0,0,0,Repcgfmc1FoundationTest.defaults()));double first=m.states()[2];
        var changed=new Repcgfmc1Kernel.Input(1.02,.9,1,1,.2,.095);
        for(int i=0;i<100;i++)m.step(.001,changed);assertEquals(first,m.states()[2]);
    }
    @Test void deferredTimestepCorrectionMatchesAnExplicitActualStep(){
        double[] c=Repcgfmc1FoundationTest.defaults();c[TFF.ordinal()]=0;var d=data(0,0,0,c);
        var explicit=new Repcgfmc1Kernel(d);explicit.initialize(initial(),.0005);
        var deferred=new Repcgfmc1Kernel(d);deferred.initialize(initial());
        var changed=new Repcgfmc1Kernel.Input(1.02,1.01,1,1.01,.2,.095);
        explicit.step(.0005,changed);deferred.step(.0005,changed);assertArrayEquals(explicit.states(),deferred.states(),0);
    }
    @Test void predictorNetworkRefreshCannotLoseADiscreteFfrJump(){
        var m=kernel(data(0,0,1,Repcgfmc1FoundationTest.defaults()));
        m.step(.001,input(),0);m.setFastFrequencyThresholds(1.001,1.05);m.step(0,input());assertEquals(0,m.states()[14]);
        m.step(.001,input(),1);assertEquals(.3,m.states()[14]);assertEquals(.001,m.output(input()).ffrStartTime());
    }
}
