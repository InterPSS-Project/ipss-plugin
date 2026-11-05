package org.interpss.core.dstab;

import static org.junit.jupiter.api.Assertions.*;
import org.interpss.dstab.hvdc.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/** Physical/equation and shared-integration gates; not complete converter trajectory acceptance. */
public class Chvdc2DcLineTest {
    @ParameterizedTest @ValueSource(booleans={false,true})
    void bothMethodsConvergeToTheExactRlStep(boolean staggered){
        double coarse=rl(.002,staggered),fine=rl(.001,staggered);
        assertTrue(coarse/fine>3.9&&coarse/fine<4.2);assertTrue(fine<.001);
    }
    private static double rl(double dt,boolean staggered){
        var m=new Chvdc2DcLine(5,2.5,0,true);m.initialize(505000,500000);m.setInput(506000,500000);
        double[] state=new double[1],previous=new double[1],rate=new double[1];
        for(int n=0;n<Math.round(.2/dt);n++)advance(m,staggered,n,dt,state,previous,rate);
        assertEquals(m.rectifierAmperes(),m.inverterAmperes());
        return Math.abs(m.rectifierAmperes()-(1200-200*Math.exp(-.2/.5)));
    }
    @ParameterizedTest @ValueSource(booleans={false,true})
    void capacitorModeConvergesToIndependentDampedOscillator(boolean staggered){
        double coarse=lc(.0002,staggered),fine=lc(.0001,staggered);
        assertTrue(coarse/fine>3.8&&coarse/fine<4.2);assertTrue(fine<.002);
    }
    private static double lc(double dt,boolean staggered){
        // Zero terminal forcing with symmetric stored capacitor voltage yields
        // v'' + (R/L)v' + 4v/(LC)=0, i_r=-i_i=C v'/2.
        double r=2,l=4,c=.01,time=.1,alpha=r/(2*l),w=Math.sqrt(4/(l*c)-alpha*alpha);
        var m=new Chvdc2DcLine(r,l,c,true);m.initialize(0,0);m.applyStaggeredState(new double[]{0,100,0},0,0);
        double[] state=new double[3],previous=new double[3],rate=new double[3];
        for(int n=0;n<Math.round(time/dt);n++)advance(m,staggered,n,dt,state,previous,rate);
        double v=100*Math.exp(-alpha*time)*(Math.cos(w*time)+alpha/w*Math.sin(w*time));
        double current=-.5*c*100*Math.exp(-alpha*time)*(w+alpha*alpha/w)*Math.sin(w*time);
        assertEquals(current,m.rectifierAmperes(),.0001);assertEquals(-current,m.inverterAmperes(),.0001);
        return Math.abs(v-m.midpointVolts());
    }
    private static void advance(Chvdc2DcLine m,boolean staggered,int n,double dt,double[] state,double[] previous,double[] rate){
        if(staggered){
            m.readStaggeredState(state,0);m.evaluateStaggered(dt,rate,0);
            for(int i=0;i<state.length;i++)state[i]+=dt*(n==0?rate[i]:1.5*rate[i]-.5*previous[i]);
            m.applyStaggeredState(state,0,dt);System.arraycopy(rate,0,previous,0,rate.length);
        }else{m.step(dt,0);m.step(dt,1);}
    }
    @Test void instantaneousPowerBalanceIncludesBothInductorsCapacitorAndLoss(){
        var m=new Chvdc2DcLine(5,2.5,.000026,true);m.initialize(505000,500000);
        m.applyStaggeredState(new double[]{1100,502000,900},0,0);double[] d=new double[3];m.evaluateStaggered(.0005,d,0);
        double storedRate=1.25*1100*d[0]+.000026*502000*d[1]+1.25*900*d[2];
        double powerIn=505000*1100-500000*900,loss=2.5*(1100*1100+900*900);
        assertEquals(powerIn-loss,storedRate,1e-6);
    }
    @Test void noDynamicsUsesEveryTrialVoltageWithoutAdvancingHistory(){
        var m=new Chvdc2DcLine(5,0,0,false);m.initialize(505000,500000);
        assertEquals(0,m.staggeredStateCount());assertEquals(1000,m.rectifierAmperes());
        m.setInput(510000,500000);assertEquals(2000,m.inverterAmperes());
        m.step(.001,0);m.step(.001,1);assertEquals(2000,m.rectifierAmperes());assertTrue(m.getIntegrationStates().isEmpty());
    }
    @Test void physicalCoefficientPreparationIncludesCommutationInductanceWithoutChangingSource(){
        var values=Chvdc2ParametersTest.constants();values[47]=26;
        var p=new Chvdc2Parameters("link",Chvdc2Parameters.Dialect.CHVDC2U1,new int[]{0,0,1},values);
        var m=p.prepareDcLine(5,2,13.5,13,60);
        assertEquals(2.5+3.5*26.5/(120*Math.PI),m.effectiveHenries(),1e-15);
        assertEquals(26e-6,m.capacitanceFarads(),1e-18);assertArrayEquals(values,p.copySourceConstants());
        assertThrows(IllegalArgumentException.class,()->p.prepareDcLine(5,0,13.5,13,60));
        assertThrows(IllegalArgumentException.class,()->p.prepareDcLine(5,2,-1,13,60));
        assertThrows(IllegalArgumentException.class,()->p.prepareDcLine(5,2,13.5,13,0));
    }
    @Test void lifecycleRejectsMixedOwnershipAndInvalidWritesAtomically(){
        var m=new Chvdc2DcLine(5,2.5,.000026,true);double[] state=new double[3];
        assertThrows(IllegalStateException.class,()->m.rectifierAmperes());m.initialize(505000,500000);
        var initial=m.getIntegrationStates();m.evaluateStaggered(.001,state,0);assertArrayEquals(new double[3],state);
        m.setInput(506000,500000);m.step(.0001,0);var predicted=m.getIntegrationStates();
        assertThrows(IllegalStateException.class,()->m.evaluateStaggered(.001,state,0));
        assertThrows(IllegalStateException.class,()->m.applyStaggeredState(state,0,0));
        assertThrows(IllegalStateException.class,()->m.step(.0002,1));
        m.step(0,1);assertEquals(predicted,m.getIntegrationStates());m.cancelPrediction();assertEquals(initial,m.getIntegrationStates());
        assertThrows(IllegalArgumentException.class,()->m.applyStaggeredState(new double[]{1,2,Double.NaN},0,.001));
        assertThrows(IllegalArgumentException.class,()->m.setInput(1,Double.NaN));assertEquals(initial,m.getIntegrationStates());
        m.evaluateStaggered(.0001,state,0);assertEquals(800,state[0]);
        m.initialize(505000,500000);assertEquals(initial,m.getIntegrationStates());
    }
    @Test void outerIntervalSelectsCapacitanceWithoutChangingEnteredData(){
        var values=Chvdc2ParametersTest.constants();values[47]=.0001;
        var p=new Chvdc2Parameters("link",Chvdc2Parameters.Dialect.CHVDC2U1,new int[]{0,0,1},values);
        var coarse=p.prepareDcLine(5,2,13.5,13,60,.0005);
        var fine=p.prepareDcLine(5,2,13.5,13,60,.00025);
        assertEquals(1,coarse.staggeredStateCount());assertEquals(0,coarse.capacitanceFarads());
        assertEquals(3,fine.staggeredStateCount());assertEquals(1e-10,fine.capacitanceFarads(),1e-25);
        assertEquals(coarse.effectiveHenries(),fine.effectiveHenries());assertArrayEquals(values,p.copySourceConstants());
        assertEquals(3,p.prepareDcLine(5,2,13.5,13,60).staggeredStateCount(),"Physical-only preparation has no implicit integration interval");
    }
    @Test void capacitanceThresholdIsStrictAndDisabledDynamicsRemainAlgebraic(){
        var values=Chvdc2ParametersTest.constants();values[44]=1000;values[45]=values[46]=0;values[47]=1e6;
        var p=new Chvdc2Parameters("link",Chvdc2Parameters.Dialect.CHVDC2,new int[]{0,0,1},values);
        double frequency=1/(2*Math.PI),threshold=10/(3*frequency);
        assertEquals(10,3*frequency*threshold,0);
        assertEquals(3,p.prepareDcLine(5,1,0,0,60,threshold).staggeredStateCount());
        assertEquals(1,p.prepareDcLine(5,1,0,0,60,Math.nextUp(threshold)).staggeredStateCount());
        var off=new Chvdc2Parameters("link",Chvdc2Parameters.Dialect.CHVDC2,new int[]{0,0,0},values);
        assertEquals(0,off.prepareDcLine(5,1,0,0,60,2*threshold).staggeredStateCount());
        assertArrayEquals(values,off.copySourceConstants());
    }
    @ParameterizedTest @ValueSource(booleans={false,true})
    void localRefinementDoesNotRestoreASuppressedCapacitor(boolean staggered){
        var values=Chvdc2ParametersTest.constants();values[47]=.0001;
        var p=new Chvdc2Parameters("link",Chvdc2Parameters.Dialect.CHVDC2,new int[]{0,0,1},values);
        var prepared=p.prepareDcLine(5,2,13.5,13,60,.0005);
        var rl=new Chvdc2DcLine(5,prepared.effectiveHenries(),0,true);
        double[] state=new double[1],previous=new double[1],rate=new double[1],otherState=new double[1],otherPrevious=new double[1],otherRate=new double[1];
        prepared.initialize(505000,500000);rl.initialize(505000,500000);
        prepared.setInput(506000,500000);rl.setInput(506000,500000);
        for(int n=0;n<100;n++){
            advance(prepared,staggered,n,.000001,state,previous,rate);
            advance(rl,staggered,n,.000001,otherState,otherPrevious,otherRate);
            assertEquals(rl.rectifierAmperes(),prepared.rectifierAmperes(),0);
            assertEquals(1,prepared.staggeredStateCount());assertEquals(0,prepared.capacitanceFarads());
        }
        assertEquals(.0001,p.value(Chvdc2Parameters.Constant.C_LINE_MICRO_F));
    }
    @Test void invalidPreparationIntervalsAndUnderflowCannotSilentlyChangeTheCircuit(){
        var values=Chvdc2ParametersTest.constants();values[47]=26;
        var p=new Chvdc2Parameters("link",Chvdc2Parameters.Dialect.CHVDC2,new int[]{0,0,1},values);
        for(double h:new double[]{0,-1,Double.NaN,Double.POSITIVE_INFINITY})
            assertThrows(IllegalArgumentException.class,()->p.prepareDcLine(5,2,13.5,13,60,h));
        values[47]=Double.MIN_VALUE;
        var tiny=new Chvdc2Parameters("link",Chvdc2Parameters.Dialect.CHVDC2,new int[]{0,0,1},values);
        assertThrows(IllegalArgumentException.class,()->tiny.prepareDcLine(5,2,13.5,13,60,.0005));
    }
}
