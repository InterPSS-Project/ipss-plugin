package org.interpss.core.adapter.builder.dstab;

import static org.junit.jupiter.api.Assertions.*;
import org.apache.commons.math3.complex.Complex;
import org.interpss.dstab.mach.*;
import org.junit.jupiter.api.Test;

/** Shared WT3E1 equations, trial isolation and ordinary integration controls. */
public class Wt3e1SharedStateTest {
    @Test void sharedHeunMatchesOrdinaryForAllSelectorBranches() throws Exception {
        double h=.0005;
        for(int mode:new int[]{-1,0,1}) for(int limit:new int[]{0,1,2}) {
            Wt3e1Model ordinary=model(mode,limit), shared=model(mode,limit);
            double[] x=new double[10], first=new double[10], second=new double[10], trial=new double[10];
            for(int step=0;step<200;step++) {
                double speed=.15+.03*Math.sin(step*.03);
                ordinary.setSpeedDeviation(speed);shared.setSpeedDeviation(speed);
                shared.readStaggeredState(x,0);shared.evaluateStaggered(h,first,0);
                ordinary.step(h,0);
                for(int i=0;i<10;i++)trial[i]=x[i]+h*first[i];
                shared.applyStaggeredState(trial,0,h);shared.evaluateStaggered(h,second,0);
                for(int i=0;i<10;i++)trial[i]=x[i]+(.5*h)*(first[i]+second[i]);
                shared.applyStaggeredState(trial,0,h);ordinary.step(h,1);
                assertEquals(ordinary.getNamedStates(),shared.getNamedStates());
            }
        }
    }

    @Test void repeatedRatesAreReadOnlyAndDiagnosticUsesEvaluationStates() throws Exception {
        Wt3e1Model model=model(1,1);double[] before=new double[10], after=new double[10], rate=new double[12];
        model.readStaggeredState(before,0);model.setSpeedDeviation(.3);
        model.evaluateStaggered(.001,rate,1);double[] first=rate.clone();
        model.evaluateStaggered(.001,rate,1);model.readStaggeredState(after,0);
        assertArrayEquals(before,after);assertArrayEquals(first,rate);
        assertEquals(10,model.getStateDerivativeSnapshot().rates().size());
        assertEquals(0,rate[0]);assertEquals(0,rate[11]);
    }

    @Test void sensorAndLimiterRatesFollowIndependentEquations() throws Exception {
        var builder=DStabBuilderTestFixture.createBuilder();
        var generator=builder.addWt3g1("Bus1","1",new Wt3g1Data(40,.33403,24,.6,.12,1.63));
        var model=builder.addWt3e1("Bus1","1",data(1,1));assertTrue(generator.initStates(generator.getDStabBus()));
        double[] x=new double[10], rate=new double[10];model.readStaggeredState(x,0);
        double initialSensor=x[4];generator.getDStabBus().setVoltage(new Complex(.9,0));
        x[1]=data(1,1).qmax();x[2]=data(1,1).pmax();
        model.applyStaggeredState(x,0,.001);model.setSpeedDeviation(.4);model.evaluateStaggered(.001,rate,0);
        assertEquals((.9-initialSensor)/.02,rate[4],1e-13);
        // At the upper reactive bound with a positive voltage error, integration must freeze.
        x[4]=.9;model.applyStaggeredState(x,0,.001);model.evaluateStaggered(.001,rate,0);
        assertEquals(0,rate[1]);assertEquals(0,rate[2]);
    }

    @Test void sharedTwoStepSensorConvergesToAnalyticConstantInput() throws Exception {
        var builder=DStabBuilderTestFixture.createBuilder();
        var generator=builder.addWt3g1("Bus1","1",new Wt3g1Data(40,.33403,24,.6,.12,1.63));
        var model=builder.addWt3e1("Bus1","1",data(1,1));assertTrue(generator.initStates(generator.getDStabBus()));
        double[] x=new double[10], rate=new double[10], previous=new double[10];
        model.readStaggeredState(x,0);double sensor=x[4], h=.0005;
        generator.getDStabBus().setVoltage(new Complex(.9,0));
        for(int step=0;step<100;step++) {
            model.evaluateStaggered(h,rate,0);
            for(int i=0;i<10;i++) { x[i]+=h*(step==0?rate[i]:1.5*rate[i]-.5*previous[i]);previous[i]=rate[i]; }
            model.applyStaggeredState(x,0,h);model.completeStaggeredStep(h);
        }
        assertEquals(.9+(sensor-.9)*Math.exp(-100*h/.02),x[4],1e-5);
        assertTrue(model.getNamedStates().values().stream().allMatch(Double::isFinite));
    }

    @Test void invalidTrialsAndOverlappingOwnersPreserveStateAndAllowRetry() throws Exception {
        Wt3e1Model model=model(1,1);double[] x=new double[10], bad=new double[10];model.readStaggeredState(x,0);
        System.arraycopy(x,0,bad,0,10);bad[9]=Double.NaN;
        assertThrows(IllegalArgumentException.class,()->model.applyStaggeredState(bad,0,.001));
        double[] restored=new double[10];model.readStaggeredState(restored,0);assertArrayEquals(x,restored);
        assertThrows(IndexOutOfBoundsException.class,()->model.readStaggeredState(new double[9],0));
        assertThrows(IllegalStateException.class,()->model.step(.001,1));
        model.step(.001,0);
        assertThrows(IllegalStateException.class,()->model.step(.001,0));
        assertThrows(IllegalStateException.class,()->model.step(.002,1));
        assertThrows(IllegalStateException.class,()->model.evaluateStaggered(.001,bad,0));
        assertThrows(IllegalStateException.class,()->model.applyStaggeredState(x,0,.001));
        model.cancelPrediction();model.readStaggeredState(restored,0);assertArrayEquals(x,restored);
        model.step(.001,0);model.step(.001,1);
        model.initialize();assertTrue(model.getStateDerivativeSnapshot().rates().isEmpty());
    }

    private static Wt3e1Model model(int varFlag,int limit) throws Exception {
        var builder=DStabBuilderTestFixture.createBuilder();
        var generator=builder.addWt3g1("Bus1","1",new Wt3g1Data(40,.33403,24,.6,.12,1.63));
        var model=builder.addWt3e1("Bus1","1",data(varFlag,limit));
        assertTrue(generator.initStates(generator.getDStabBus()));return model;
    }
    private static Wt3e1Data data(int mode,int limit){return new Wt3e1Data(
            0,mode,limit,0,0,0,.12,15,4,0,.04,2.5,.5,1.1,.05,.45,-.45,
            1.1,.02,.4,-.4,4,.1,.88,1.12,35,-.5,1.45,.05,.05,1,.3,.69,.78,.98,.74,1.2);}
}
