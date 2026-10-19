package org.interpss.dstab.renewable;

import static org.junit.jupiter.api.Assertions.*;
import static org.interpss.dstab.renewable.HplntdData.Parameter.*;
import java.util.*;
import org.apache.commons.math3.complex.Complex;
import org.junit.jupiter.api.Test;

class HplntdProportionalKernelTest {
    private static double[] constants(){return new double[]{.02,10,0,0,.05,.7,0,.05,.02,1,-1,0,0,1,-1,1,0,.02,-.0006,.0006,99,-99,2,-1,.1,20,20,1.08,.95,1,-1,99,-99,99,-99,99,-99,.8,-.8,99,-99,1,-1,.02,-.25,-.5,.3,.4,.5,.1,.06,120,.01,.02,.8,.015,-.015,0,.01,-.02,1.1};}
    private static HplntdData data(double[] c,int mode,int compensation,int feedback,int feedforward){return new HplntdData(2,new RepccData.Branch(2,3,"1"),compensation,mode,1,feedback,feedforward,0,new RepccData.Branch(0,0,"0"),Collections.nCopies(10,new RepccData.Shunt(0,"0")),2,1,c);}
    private static HplntdInitialization.Measurements initial(double p,double q){return new HplntdInitialization.Measurements(Complex.ONE,new Complex(.3,-.1),.3,.1,p,q);}
    private static HplntdKernel.Input input(){return new HplntdKernel.Input(Complex.ONE,new Complex(.3,-.1),.3,.1,0,1);}
    private static void stationary(HplntdKernel k){double[] before=k.states();for(int tick=0;tick<100;tick++)k.step(.0005,input());assertArrayEquals(before,k.states(),2e-14);assertEquals(0,k.output(input()).active(),2e-14);assertEquals(0,k.output(input()).reactive(),2e-14);}

    @Test void activeProportionalEquilibriumCoversAllFeedbackFeedforwardCombinations(){
        for(int feedback=0;feedback<2;feedback++)for(int feedforward=0;feedforward<2;feedforward++){
            var c=constants();c[KPG.ordinal()]=2;var k=new HplntdKernel(data(c,0,1,feedback,feedforward));k.initialize(initial(.32,.12));
            double expected=(.32+2*feedback*.3)/(2+feedforward);
            assertEquals(expected,k.references().active(),1e-15);assertEquals(0,k.states()[5],0);stationary(k);
        }
    }
    @Test void reactiveProportionalEquilibriumCoversReferenceSelectorsSignsAndDeadbands(){
        for(int mode=0;mode<3;mode++)for(int comp=0;comp<2;comp++)for(double q:new double[]{.12,-.12}){
            var c=constants();c[DBD1.ordinal()]=-.005;c[DBD2.ordinal()]=.01;
            var k=new HplntdKernel(data(c,mode,comp,1,1));k.initialize(initial(.32,q));
            assertEquals(0,k.states()[2],0);assertEquals(q,k.output(input()).reactivePi(),2e-15);stationary(k);
        }
    }
    @Test void inverseDeadbandAddsTheCorrectSignedThreshold(){
        var c=constants();c[DBD1.ordinal()]=-.005;c[DBD2.ordinal()]=.01;
        var positive=new HplntdKernel(data(c,0,1,1,1));positive.initialize(initial(.32,.12));
        var negative=new HplntdKernel(data(c,0,1,1,1));negative.initialize(initial(.32,-.12));
        assertEquals(.122,positive.references().reactive(),1e-15);assertEquals(.083,negative.references().reactive(),1e-15);
        assertEquals(.012,positive.output(input()).reactiveError(),1e-15);assertEquals(-.012,negative.output(input()).reactiveError(),1e-15);
    }
    @Test void pfMeasurementDoesNotBecomeElectricalFeedbackWhenFeedbackIsDisabled(){
        var c=constants();c[KI.ordinal()]=5;c[KIG.ordinal()]=.1;
        var k=new HplntdKernel(data(c,2,1,0,1));k.initialize(initial(.32,.12));
        assertEquals(.3,k.states()[4],0);assertEquals(0,k.references().active(),0);assertEquals(.32,k.states()[5],0);stationary(k);
    }
    @Test void feedforwardCanSupportZeroActiveProportionalAndIntegralGains(){
        var c=constants();c[KPG.ordinal()]=0;c[PFEMIN.ordinal()]=c[PFEMAX.ordinal()]=0;var k=new HplntdKernel(data(c,0,1,1,1));k.initialize(initial(.32,.12));
        assertEquals(.32,k.references().active(),0);assertEquals(0,k.output(input()).activePi(),0);stationary(k);
    }
    @Test void infeasibleAndNegativeGainEquilibriaAreRejectedWithoutRetuning(){
        for(var parameter:new HplntdData.Parameter[]{KP,KPG}){
            var c=constants();c[parameter.ordinal()]=0;
            assertThrows(IllegalArgumentException.class,()->new HplntdKernel(data(c,0,1,1,0)).initialize(initial(.32,.12)));
        }
        for(var parameter:new HplntdData.Parameter[]{KI,KIG}){
            var c=constants();c[parameter.ordinal()]=-1;
            assertThrows(IllegalArgumentException.class,()->new HplntdKernel(data(c,0,1,1,1)).initialize(initial(.32,.12)));
        }
        var c=constants();c[EMAX.ordinal()]=.001;var error=c;assertThrows(IllegalArgumentException.class,()->new HplntdKernel(data(error,0,1,1,1)).initialize(initial(.32,.12)));
        c=constants();c[VREFMAX.ordinal()]=1.001;var voltage=c;assertThrows(IllegalArgumentException.class,()->new HplntdKernel(data(voltage,1,0,1,1)).initialize(initial(.32,.12)));
        c=constants();c[QREFMAX.ordinal()]=.105;var reactive=c;assertThrows(IllegalArgumentException.class,()->new HplntdKernel(data(reactive,0,1,1,1)).initialize(initial(.32,.12)));
        c=constants();c[PFEMAX.ordinal()]=.01;var active=c;assertThrows(IllegalArgumentException.class,()->new HplntdKernel(data(active,0,1,1,0)).initialize(initial(.32,.12)));
    }
    @Test void zeroIntegralStorageNeverAccumulatesDuringEitherStage(){
        var k=new HplntdKernel(data(constants(),0,1,1,1));k.initialize(initial(.32,.12));var r=k.references();
        k.setReferences(new HplntdKernel.References(r.voltage(),r.reactive()+.02,0,r.active()+.05,r.powerFactor(),0,0));
        for(int tick=0;tick<100;tick++)for(int stage=0;stage<2;stage++){k.step(.0005,input(),stage);assertEquals(0,k.states()[2],0);assertEquals(0,k.states()[5],0);}
        assertTrue(k.output(input()).active()>0);assertTrue(k.output(input()).reactive()>0);
        k.setStaggeredInput(input());double[] rates=new double[13];k.evaluateStaggered(.0005,rates,0);assertEquals(0,rates[2],0);assertEquals(0,rates[5],0);
    }
    @Test void reinitializationResetsReferencesAndKeepsZeroIntegralStorage(){
        var k=new HplntdKernel(data(constants(),0,1,1,1));k.initialize(initial(.32,.12));k.step(.001,input());
        k.initialize(initial(.5,.2));assertEquals(0,k.time(),0);assertEquals(.4,k.references().active(),1e-15);assertEquals(.12,k.references().reactive(),1e-15);
        assertEquals(0,k.states()[2],0);assertEquals(0,k.states()[5],0);stationary(k);
    }
}
