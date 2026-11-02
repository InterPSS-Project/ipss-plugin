package org.interpss.core.dstab;

import static org.junit.jupiter.api.Assertions.*;
import org.interpss.dstab.hvdc.Cdc6tMeasurements;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/** Analytic transducer and lifecycle checks, separate from complete HVDC qualification. */
public class Cdc6tMeasurementsTest {
    @ParameterizedTest
    @ValueSource(booleans={false,true})
    void commonRhsConvergesToAnalyticThreeLagResponse(boolean participant) {
        double coarse=run(.001,participant),fine=run(.0005,participant);
        assertTrue(coarse/fine>3.9&&coarse/fine<4.2,"Second-order convergence against exact exponential");
        // The second-order truncation error at h=.5 ms is approximately .091 V.
        assertTrue(fine<.1,"Volts at the tightest measurement time");
    }
    private static double run(double dt,boolean participant) {
        var model=new Cdc6tMeasurements(.05,.04,.06);model.initialize(500000,1000,505000);
        model.setInput(480000,900,484500);
        double[] x=new double[3],k1=new double[3],k2=new double[3],trial=new double[3];
        for(int n=0;n<Math.round(.1/dt);n++) {
            if(participant) {
                model.readStaggeredState(x,0);model.evaluateStaggered(dt,k1,0);
                // Accepted two-step rates, with an Euler bootstrap, exercise the staggered contract.
                for(int i=0;i<3;i++)trial[i]=x[i]+dt*(n==0?k1[i]:1.5*k1[i]-.5*k2[i]);
                model.applyStaggeredState(trial,0,dt);
                System.arraycopy(k1,0,k2,0,3);
            }else {model.step(dt,0);model.step(dt,1);}
        }
        model.readStaggeredState(x,0);
        double[] initial={500000,1000,505000},input={480000,900,484500},time={.05,.04,.06};
        double error=0;
        for(int i=0;i<3;i++)error=Math.max(error,Math.abs(x[i]-(input[i]+(initial[i]-input[i])*Math.exp(-.1/time[i]))));
        return error;
    }
    @Test void equilibriumAndTrialLifecycleAreExplicitAndAtomic() {
        var model=new Cdc6tMeasurements(.05,.04,.06);
        assertThrows(IllegalStateException.class,()->model.step(.001,0));
        model.initialize(500000,1000,505000);var initial=model.getIntegrationStates();
        var rates=new double[]{7,7,7,7,7};model.evaluateStaggered(.001,rates,1);
        assertArrayEquals(new double[]{7,0,0,0,7},rates);
        assertEquals(initial,model.getIntegrationStates());
        model.setInput(480000,900,484500);model.step(.001,0);
        var prediction=model.getIntegrationStates();
        model.step(0,0);model.step(0,1);assertEquals(prediction,model.getIntegrationStates());
        assertThrows(IllegalStateException.class,()->model.evaluateStaggered(.001,rates,1));
        assertThrows(IllegalStateException.class,()->model.step(.002,1));
        assertEquals(prediction,model.getIntegrationStates());model.cancelPrediction();
        assertEquals(initial,model.getIntegrationStates());
        assertThrows(IllegalArgumentException.class,()->model.applyStaggeredState(new double[]{1,2,Double.NaN},0,.001));
        assertEquals(initial,model.getIntegrationStates());
        assertThrows(IllegalArgumentException.class,()->model.setInput(1,Double.NaN,2));
        model.evaluateStaggered(.001,rates,1);
        assertEquals(-400000,rates[1],1e-9);assertEquals(-2500,rates[2],1e-9);
        model.initialize(500000,1000,505000);model.step(.001,0);model.step(.001,1);
        assertEquals(initial,model.getIntegrationStates());
    }
    @Test void unsupportedTimesAndNonfiniteIntervalsAreRejected() {
        for(double t:new double[]{-1,Double.NaN,Double.POSITIVE_INFINITY})
            assertThrows(IllegalArgumentException.class,()->new Cdc6tMeasurements(t,.05,.05));
        var model=new Cdc6tMeasurements(.05,.05,.05);model.initialize(1,2,3);
        var initial=model.getIntegrationStates();
        for(double dt:new double[]{-1,Double.NaN,Double.POSITIVE_INFINITY})
            assertThrows(IllegalArgumentException.class,()->model.step(dt,0));
        assertEquals(initial,model.getIntegrationStates());
    }
    @Test void outerIntervalBypassKeepsCommandSamplingSeparateFromAcceptedProjection(){
        var model=new Cdc6tMeasurements(0,.000999,.001);
        model.configureIntegrationStep(.0005);model.initialize(500000,1000,505000);
        model.setInput(450000,1100,455000);
        double[] state=new double[3],rates=new double[3],command=new double[3];
        model.evaluateStaggered(.00001,rates,0);
        assertArrayEquals(new double[]{0,0,-50000000},rates,0,"Local stage size must not change the prepared bypass mask");
        model.readStaggeredState(state,0);assertArrayEquals(new double[]{500000,1000,505000},state);
        model.acceptAlgebraicInputs();model.readStaggeredState(state,0);
        assertArrayEquals(new double[]{450000,1100,505000},state);
        model.readCommandMeasurements(command,0);assertArrayEquals(new double[]{500000,1000,505000},command);
        model.sampleCommandMeasurements();model.readCommandMeasurements(command,0);assertArrayEquals(state,command);
        model.applyStaggeredState(new double[]{123,456,500000},0,.0005);
        model.readStaggeredState(state,0);assertArrayEquals(new double[]{450000,1100,500000},state);
        model.step(.0005,0);
        assertThrows(IllegalStateException.class,()->model.configureIntegrationStep(.001));
        model.cancelPrediction();
    }
    @Test void equalityRetainsTheExplicitModifiedEulerEquation(){
        var model=new Cdc6tMeasurements(.001,.001001,.05);
        model.configureIntegrationStep(.0005);model.initialize(500000,1000,505000);
        model.setInput(450000,1100,455000);model.acceptAlgebraicInputs();
        double[] state=new double[3];model.readStaggeredState(state,0);
        assertArrayEquals(new double[]{500000,1000,505000},state,"Equality must not bypass or floor source times");
        model.step(.0005,0);model.step(.0005,1);model.readStaggeredState(state,0);
        double[] initial={500000,1000,505000},input={450000,1100,455000},times={.001,.001001,.05};
        for(int i=0;i<3;i++){
            double ratio=.0005/times[i];
            assertEquals(initial[i]+(input[i]-initial[i])*(ratio-.5*ratio*ratio),state[i],1e-9);
        }
    }
}
