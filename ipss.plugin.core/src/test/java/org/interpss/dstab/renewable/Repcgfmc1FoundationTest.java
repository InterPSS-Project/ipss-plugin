package org.interpss.dstab.renewable;

import static org.junit.jupiter.api.Assertions.*;
import static org.interpss.dstab.renewable.Repcgfmc1Data.Parameter.*;
import org.junit.jupiter.api.Test;

class Repcgfmc1FoundationTest {
    static double[] defaults(){return new double[]{1.05,.95,.05,-.05,0,.02,.3,.006,.2,1.1,.85,.01,.3,.2,.0005,-.0005,20,20,2,-2,.95,-.95,.01,.1,-.1,.1,.95,-.95,.05,.3,-.3,.01,.1,.1,-.1,.1,-.1,.05,.05,-.05,0,0,2,.3,-.3,0,5,.98,1.05,.3,-.3,.01,10,0};}
    private static Repcgfmc1Data data(double[] c){return new Repcgfmc1Data(3,-2,3,"A1",1,1,1,c);}
    private static Repcgfmc1Initialization.Measurements measurements(boolean found){
        return new Repcgfmc1Initialization.Measurements(100,50,1.02,.99,.2,.1,.19,.08,found);
    }
    @Test void exactSchemaAndDefensiveStorage(){
        double[] c=defaults();var d=data(c);assertEquals(54,Repcgfmc1Data.Parameter.values().length);
        assertEquals(7,Repcgfmc1Data.ICON_COUNT);assertEquals(15,Repcgfmc1Data.STATE_COUNT);assertEquals(20,Repcgfmc1Data.VAR_COUNT);
        assertEquals(.006,d.value(RLOSS));assertEquals(5,d.value(KIVC));assertEquals("A1",d.circuit());
        assertTrue(d.arrivingFlow());assertEquals(2,d.measurementBus());assertEquals(3,d.frequencyBus());assertEquals(3,d.branchTo());
        c[0]=9;double[] copy=d.constants();copy[0]=10;assertEquals(1.05,d.value(FREQMAX));
        assertThrows(IllegalArgumentException.class,()->data(new double[53]));
        assertThrows(IllegalArgumentException.class,()->data(new double[55]));
    }
    @Test void plantBaseSelectionAndDocumentedCorrectionsRetainRawData(){
        double[] c=defaults();c[TFF.ordinal()]=.0001;c[KIVC.ordinal()]=c[KPVC.ordinal()]=0;
        var d=data(c);assertEquals(.001,d.frequencyFilterTime(.0005));assertEquals(.0001,d.value(TFF));
        assertEquals(.01,d.effectiveVoltageGain());assertEquals(0,d.value(KPVC));assertEquals(50,d.plantMva(50));
        c[PLNTMVA.ordinal()]=75;assertEquals(75,data(c).plantMva(50));
        for(double step:new double[]{0,-1,Double.NaN})assertThrows(IllegalArgumentException.class,()->d.frequencyFilterTime(step));
        assertThrows(IllegalArgumentException.class,()->d.plantMva(0));
    }
    @Test void nativeFastFrequencyFlagIsNotANonzeroAlias(){
        for(int flag:new int[]{-1,0,1,2}){
            var d=new Repcgfmc1Data(0,0,0,"1",0,0,flag,defaults());
            assertEquals(flag,d.fastFrequencyFlag());assertEquals(flag==1,d.fastFrequencyEnabled());
        }
    }
    @Test void knownBranchUsesPlantBaseAndUnityVoltageLossConvention(){
        var r=Repcgfmc1Initialization.initialize(data(defaults()),measurements(true));
        assertEquals(.38,r.pTarget(),1e-15);assertEquals(.16,r.qTarget(),1e-15);
        assertEquals(.4,r.activeReference(),1e-15);assertEquals(.2,r.reactiveReference(),1e-15);
        assertEquals(.006*.17,r.activeLoss(),1e-15);assertEquals(.2*.17,r.reactiveLoss(),1e-15);
        assertEquals(.4-.38-.006*.17,r.activeIntegral(),1e-15);
        assertEquals(.2-.16-.2*.17,r.reactiveIntegral(),1e-15);assertEquals(.16,r.voltageIntegral(),1e-15);
        double compensated=Math.hypot(.99+.006*.38+.2*.16,.2*.38-.006*.16);
        assertEquals(compensated,r.compensatedVoltage(),1e-15);assertEquals(1.02-compensated,r.voltageCorrection(),1e-15);
    }
    @Test void automaticLossEstimationRequiresBothNonpositiveCoefficients(){
        double[] c=defaults();c[RLOSS.ordinal()]=c[XLOSS.ordinal()]=0;
        var r=Repcgfmc1Initialization.initialize(data(c),measurements(true));
        assertEquals((.4-.38)/.17,r.resistance(),1e-15);assertEquals((.2-.16)/.17,r.reactance(),1e-15);
        assertEquals(0,r.activeIntegral(),1e-15);assertEquals(0,r.reactiveIntegral(),1e-15);
        c[XLOSS.ordinal()]=.2;r=Repcgfmc1Initialization.initialize(data(c),measurements(true));
        assertEquals(0,r.resistance());assertEquals(.2,r.reactance());
    }
    @Test void missingBranchDisablesLossesAndFallsBackToTerminalMeasurements(){
        var r=Repcgfmc1Initialization.initialize(data(defaults()),measurements(false));
        assertEquals(0,r.resistance());assertEquals(0,r.reactance());assertEquals(1.02,r.remoteVoltage());
        assertEquals(.4,r.pTarget());assertEquals(.2,r.qTarget());assertEquals(0,r.activeIntegral());assertEquals(0,r.reactiveIntegral());
        assertEquals(0,r.voltageCorrection(),1e-15);
        var zero=new Repcgfmc1Initialization.Measurements(100,50,1,1,0,0,0,0,true);
        double[] c=defaults();c[RLOSS.ordinal()]=c[XLOSS.ordinal()]=0;
        r=Repcgfmc1Initialization.initialize(data(c),zero);assertEquals(0,r.resistance());assertEquals(0,r.reactance());
    }
    @Test void proportionalOnlyVoltageReferenceHandlesBothSignsAndDeadbands(){
        double[] c=defaults();c[KIVC.ordinal()]=0;c[KPVC.ordinal()]=8;c[DBVH.ordinal()]=.01;c[DBVL.ordinal()]=-.02;
        var positive=Repcgfmc1Initialization.initialize(data(c),measurements(true));
        assertEquals(.99+.01+.16/8,positive.remoteVoltageReference(),1e-15);assertEquals(0,positive.voltageIntegral());
        var negative=Repcgfmc1Initialization.initialize(data(c),new Repcgfmc1Initialization.Measurements(100,50,1.02,.99,.2,-.1,.19,-.08,true));
        assertEquals(.99-.02-.16/8,negative.remoteVoltageReference(),1e-15);
        c[KPVC.ordinal()]=2;
        assertThrows(IllegalArgumentException.class,()->Repcgfmc1Initialization.initialize(data(c),measurements(true)));
    }
    @Test void rejectsInvalidFieldsWithoutGuessingOtherToolCorrections(){
        for(int i=0;i<54;i++){double[] c=defaults();c[i]=Double.NaN;assertThrows(IllegalArgumentException.class,()->data(c));}
        double[] c=defaults();c[PLNTMVA.ordinal()]=-1;assertThrows(IllegalArgumentException.class,()->data(c));
        assertThrows(IllegalArgumentException.class,()->new Repcgfmc1Data(0,1,2,"TOO",0,0,0,defaults()));
        assertThrows(IllegalArgumentException.class,()->new Repcgfmc1Data(0,1,2,"1",2,0,0,defaults()));
        assertThrows(IllegalArgumentException.class,()->new Repcgfmc1Initialization.Measurements(0,50,1,1,0,0,0,0,true));
    }
}
