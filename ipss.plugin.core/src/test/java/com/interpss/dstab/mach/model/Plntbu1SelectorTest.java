package com.interpss.dstab.mach.model;

import com.interpss.dstab.mach.ieeevc.*;
import com.interpss.dstab.mach.model.*;

import static org.junit.jupiter.api.Assertions.*;
import org.apache.commons.math3.complex.Complex;
import org.junit.jupiter.api.Test;

/** Native selector boundaries, dormant limits and independent power-factor sensing. */
class Plntbu1SelectorTest {
    private Plntbu1Data data(int flag,int reactiveMode,boolean validActive) {
        return new Plntbu1Data(1,1,2,"1",0,reactiveMode,flag,
                .04,1,.1,0,.02,.7,0,0,.01,1,-1,0,0,1,-1,
                .25,.5,.00002,-.002,validActive?.003:-.003,.2,validActive?-1.1:1.1,
                1,-1,.02,20,20,100);
    }
    private Plntbu1Model initialized(Plntbu1Data data) {
        var model=new Plntbu1Model(data);
        model.setBoundary(Complex.ONE,Complex.ONE,new Complex(.4,.1),1);
        assertTrue(model.initialize());return model;
    }

    @Test void onlyOneEnablesTheWholeActivePathWithoutChangingTheEnteredSelector() {
        for(int flag:new int[]{0,71,2,-1}) {
            var data=data(flag,1,false);var model=initialized(data);
            assertEquals(flag,model.getData().frequencyFlag());assertFalse(data.activePowerControlEnabled());
            assertEquals(1.1,data.activeErrorMinimum(),0);assertEquals(-.003,data.frequencyDeadbandUpper(),0);
            double heldMeasurement=flag==0?0:.4;
            assertEquals(heldMeasurement,model.getActiveMeasurement(),0);
            model.setAuxiliaryInputs(.03,0,.5);
            model.setBoundary(Complex.ONE,Complex.ONE,new Complex(.1,.1),.98);
            for(int i=0;i<100;i++){assertTrue(model.step(.001,0));assertTrue(model.step(.001,1));}
            assertEquals(heldMeasurement,model.getActiveMeasurement(),0);assertEquals(0,model.getActiveIntegral(),0);
            assertEquals(0,model.getActiveLagState(),0);assertEquals(0,model.getActiveOutput(),0);
            assertTrue(model.getReactiveOutput()>0,"Reactive voltage control must remain active");
            assertEquals(0,model.getStateDerivativeSnapshot().rates().get("Real power PI integrator"),0);
            assertTrue(model.reinitialize());assertEquals(0,model.getActiveOutput(),0);
        }
        assertThrows(IllegalArgumentException.class,()->data(1,1,false));
        assertTrue(data(1,1,true).activePowerControlEnabled());
    }

    @Test void staggeredEndpointsCannotLeakDisabledStatesOrEvaluateDormantClamps() {
        var model=initialized(data(71,1,false));double[] states=new double[7],rates=new double[7];
        model.readStaggeredState(states,0);states[4]=.6;states[5]=.8;states[6]=.9;
        model.applyStaggeredState(states,0,.001);model.evaluateStaggered(.001,rates,0);
        for(int i=4;i<7;i++)assertEquals(0,rates[i],0);
        assertEquals(.4,model.getActiveMeasurement(),0);assertEquals(0,model.getActiveIntegral(),0);
        assertEquals(0,model.getActiveLagState(),0);assertEquals(0,model.getActiveOutput(),0);
    }

    @Test void disabledActiveControlStillMeasuresRealPowerForPowerFactorRegulation() {
        var model=initialized(data(0,2,false));assertEquals(.4,model.getActiveMeasurement(),0);
        // Use a step shorter than the independent measurement filter time.
        model.setBoundary(Complex.ONE,Complex.ONE,new Complex(.32,.1),1);
        for(int i=0;i<100;i++){assertTrue(model.step(.000001,0));assertTrue(model.step(.000001,1));}
        assertTrue(model.getActiveMeasurement()<.4);assertTrue(model.getActiveMeasurement()>.32);
        assertTrue(model.getReactiveOutput()<0,"Measured P must still drive the reactive PF correction");
        assertEquals(0,model.getActiveOutput(),0);
    }

    @Test void enabledActiveLoopRetainsItsPowerCommandResponse() {
        var model=initialized(data(1,1,true));model.setAuxiliaryInputs(0,0,.03);
        for(int i=0;i<20;i++){assertTrue(model.step(.000001,0));assertTrue(model.step(.000001,1));}
        assertTrue(model.getActiveIntegral()>0);assertTrue(model.getActiveOutput()>0);
    }
}
