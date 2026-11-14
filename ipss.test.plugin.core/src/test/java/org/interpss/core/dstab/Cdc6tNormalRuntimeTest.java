package org.interpss.core.dstab;

import static org.junit.jupiter.api.Assertions.*;
import com.interpss.dstab.hvdc.model.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

public class Cdc6tNormalRuntimeTest {
    private static Cdc6tNormalRuntime runtime() {
        var runtime=new Cdc6tNormalRuntime(new Cdc6tMeasurements(.05,.05,.05),
            new Cdc6tCurrentOrder(Cdc6tCurrentOrder.Mode.POWER,-500,500,0),
            new Cdc6tVoltageCurrentLimit(250,450,425,2000,500,2000),
            new Cdc6tConductingLink(new Cdc6tConverterBoundary(true,2,230000,.9263,.9375,13.5939,1e8),
                new Cdc6tConverterBoundary(false,2,230000,.9097,.94375,13.3174,1e8),5,Math.toRadians(5),Math.toRadians(20)));
        runtime.initialize(500000,1000,505000);return runtime;
    }
    @ParameterizedTest @ValueSource(booleans={false,true})
    void sameMeasurementOwnerMaintainsEquilibriumUnderBothIntegrationLifecycles(boolean staggered) {
        var model=runtime();double[] currents=new double[4],state=new double[3],rates=new double[3],previous=new double[3];
        double h=.0005;
        for(int step=0;step<100;step++) {
            model.evaluateBoundary(1,0,1,0,0,currents,0);
            if(staggered) {
                model.readStaggeredState(state,0);model.evaluateStaggered(h,rates,0);
                for(int i=0;i<3;i++) {state[i]+=h*(step==0?rates[i]:1.5*rates[i]-.5*previous[i]);previous[i]=rates[i];}
                model.applyStaggeredState(state,0,h);
            } else {
                model.step(h,0);model.evaluateBoundary(1,0,1,0,0,currents,0);model.step(h,1);
            }
        }
        model.readStaggeredState(state,0);assertArrayEquals(new double[]{500000,1000,505000},state,1e-10);
    }
    @Test void rotatingPhasorsPreserveTerminalPowerAndVoltageTrialsRefreshInputs() {
        var model=runtime();double[] currents=new double[4],boundary=new double[8],before=new double[3],after=new double[3],rates=new double[3];
        double rr=.95*Math.cos(.3),ri=.95*Math.sin(.3),ir=.96*Math.cos(-.2),ii=.96*Math.sin(-.2);
        model.readStaggeredState(before,0);model.evaluateBoundary(rr,ri,ir,ii,0,currents,0);model.readBoundary(boundary,0);
        assertEquals(-boundary[0],rr*currents[0]+ri*currents[1],1e-12);
        assertEquals(-boundary[1],ri*currents[0]-rr*currents[1],1e-12);
        assertEquals(-boundary[3],ir*currents[2]+ii*currents[3],1e-12);
        assertEquals(-boundary[4],ii*currents[2]-ir*currents[3],1e-12);
        model.evaluateBoundary(.95,0,.85,0,0,currents,0);model.evaluateStaggered(.0005,rates,0);
        assertTrue(rates[0]<0,"New inverter voltage must refresh DC transducer input");
        model.readStaggeredState(after,0);assertArrayEquals(before,after,0,"Network trials cannot integrate states");
    }
    @Test void staleBoundaryRejectedAndPredictorRollbackRestoresSingleStateOwner() {
        var model=runtime();double[] currents=new double[4],state=new double[3];
        assertThrows(IllegalStateException.class,()->model.step(.001,0));
        model.evaluateBoundary(.95,0,.85,0,0,currents,0);model.step(.001,0);
        assertThrows(IllegalStateException.class,()->model.step(.001,1));
        model.cancelPrediction();model.readStaggeredState(state,0);
        assertArrayEquals(new double[]{500000,1000,505000},state,0);
        double[] before=currents.clone();
        assertThrows(IllegalArgumentException.class,()->model.evaluateBoundary(0,0,1,0,0,currents,0));
        assertArrayEquals(before,currents,0);
    }
    @ParameterizedTest @ValueSource(booleans={false,true})
    void disturbedStateTrajectoriesConvergeWithRefinedIntervals(boolean staggered) {
        double[] fine=integrate(false,.0000625),coarse=integrate(staggered,.001),half=integrate(staggered,.0005);
        for(int i=0;i<3;i++) {
            double coarseError=Math.abs(coarse[i]-fine[i]),halfError=Math.abs(half[i]-fine[i]);
            assertTrue(coarseError>1e-7,"Exercise nontrivial integration coordinate "+i);
            assertTrue(halfError<.4*coarseError,"Second-order refinement for coordinate "+i);
        }
    }
    private static double[] integrate(boolean staggered,double h) {
        var model=runtime();double[] currents=new double[4],state=new double[3],rates=new double[3],previous=new double[3];
        for(int step=0;step<(int)Math.round(.1/h);step++) {
            model.evaluateBoundary(.95,0,.85,0,0,currents,0);
            if(staggered) {
                model.readStaggeredState(state,0);model.evaluateStaggered(h,rates,0);
                for(int i=0;i<3;i++) {state[i]+=h*(step==0?rates[i]:1.5*rates[i]-.5*previous[i]);previous[i]=rates[i];}
                model.applyStaggeredState(state,0,h);
            } else {
                model.step(h,0);model.evaluateBoundary(.95,0,.85,0,0,currents,0);model.step(h,1);
            }
        }
        model.readStaggeredState(state,0);return state;
    }
}
