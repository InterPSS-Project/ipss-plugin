package org.interpss.dstab.renewable;

import java.util.*;
import org.apache.commons.math3.complex.Complex;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static org.interpss.dstab.renewable.HplntdData.Parameter.*;

class HplntdKernelTest {
    private static HplntdData flags(double[] c,int mode,int comp,int active,int reactive){return new HplntdData(2,new RepccData.Branch(2,3,"1"),comp,mode,active,1,1,0,new RepccData.Branch(0,0,"0"),Collections.nCopies(10,new RepccData.Shunt(0,"0")),2,reactive,c);}
    private static void disabledStates(HplntdKernel kernel,int mode,int active,int reactive){
        double[] state=kernel.states();
        if(active==0)for(int index:new int[]{5,6,9,10})assertEquals(0,state[index],0,"Inactive active storage "+index);
        if(active==0&&(reactive==0||mode!=2))assertEquals(0,state[4],0);
        if(reactive==0)for(int index:new int[]{0,1,2,3,8,11,12})assertEquals(0,state[index],0,"Inactive reactive storage "+index);
        var out=kernel.output(input(1,0));
        if(active==0){assertEquals(.32,out.activeCommand(),0);assertEquals(0,out.active(),0);}
        if(reactive==0){assertEquals(.12,out.reactiveCommand(),0);assertEquals(0,out.reactive(),0);}
    }
    @Test void disabledPathsKeepInitialCommandsAcrossBothStagesAndAlgebraicBypasses(){
        for(int mode=0;mode<3;mode++)for(int active=0;active<2;active++)for(int reactive=0;reactive<2;reactive++)for(int bypass=0;bypass<2;bypass++){
            double[] c=constants();if(bypass==1)for(var p:new HplntdData.Parameter[]{TFLTR,TP,TC,TFRQ,TFV,TG})c[p.ordinal()]=0;
            var k=new HplntdKernel(flags(c,mode,1,active,reactive));k.initialize(initial());disabledStates(k,mode,active,reactive);
            k.setReferences(new HplntdKernel.References(1.05,.25,.03,.6,.9,.01,.01));
            for(int tick=0;tick<10;tick++){
                var in=new HplntdKernel.Input(new Complex(1.01),new Complex(.4,-.2),.4,.2,.02,1.01);
                k.step(0,in);disabledStates(k,mode,active,reactive);
                k.step(.0005,in,0);disabledStates(k,mode,active,reactive);
                k.step(.0005,in,1);disabledStates(k,mode,active,reactive);
            }
            assertTrue(k.states()[7]>1,"Shared voltage measurement remains live");
            if(active==0&&reactive==1&&mode==2)assertTrue(k.states()[4]>.3,"PF mode retains its shared active measurement");
        }
    }
    @Test void disabledGainsAndOutputLimitsDoNotConstrainHeldCommands(){
        double[] c=constants();c[KI.ordinal()]=c[KIG.ordinal()]=0;c[QMAX.ordinal()]=.05;c[PMAX.ordinal()]=.1;
        var k=new HplntdKernel(flags(c,0,1,0,0));k.initialize(initial());disabledStates(k,0,0,0);
        assertThrows(IllegalArgumentException.class,()->new HplntdKernel(flags(c,0,1,1,0)).initialize(initial()));
        assertThrows(IllegalArgumentException.class,()->new HplntdKernel(flags(c,0,1,0,1)).initialize(initial()));
    }
    @Test void disabledPathsHaveZeroStaggeredDerivativesAndPreserveNamedStateOrder(){
        for(int active=0;active<2;active++)for(int reactive=0;reactive<2;reactive++){
            var k=new HplntdKernel(flags(constants(),2,0,active,reactive));k.initialize(initial());
            k.setReferences(new HplntdKernel.References(1.1,.3,.02,.5,.9,0,0));k.setStaggeredInput(input(1.03,.04));
            double[] rates=new double[13];k.evaluateStaggered(.0005,rates,0);
            if(active==0)for(int i:new int[]{5,6,9,10})assertEquals(0,rates[i],0);
            if(reactive==0)for(int i:new int[]{0,1,2,3,8,11,12})assertEquals(0,rates[i],0);
            assertEquals(HplntdInitialization.STATE_NAMES,new ArrayList<>(k.getNamedStates().keySet()));
        }
    }
    private static double[] constants(){return new double[]{.02,1,5,0,.05,.7,0,.05,.02,1,-1,0,0,1,-1,1,.1,.02,-.0006,.0006,99,-99,2,-1,.1,20,20,1.08,.95,1,-1,99,-99,99,-99,99,-99,.8,-.8,99,-99,1,-1,.02,-.25,-.5,.3,.4,.5,.1,.06,120,.01,.02,.8,.015,-.015,0,.01,-.02,1.1};}
    private static HplntdData data(double[] c,int mode,int comp){return new HplntdData(2,new RepccData.Branch(2,3,"1"),comp,mode,1,1,1,0,new RepccData.Branch(0,0,"0"),Collections.nCopies(10,new RepccData.Shunt(0,"0")),2,1,c);}
    private static HplntdInitialization.Measurements initial(){return new HplntdInitialization.Measurements(Complex.ONE,new Complex(.3,-.1),.3,.1,.32,.12);}
    private static HplntdKernel.Input input(double voltage,double frequency){return new HplntdKernel.Input(new Complex(voltage),new Complex(.3,-.1),.3,.1,frequency,voltage);}
    @Test void allReferenceModesInitializeIndependentlyAtEquilibrium(){for(int mode=0;mode<3;mode++)for(int comp=0;comp<2;comp++){var k=new HplntdKernel(data(constants(),mode,comp));k.initialize(initial());var before=k.states();for(int i=0;i<100;i++)k.step(.0005,input(1,0));assertArrayEquals(before,k.states(),2e-15);assertEquals(0,k.output(input(1,0)).active(),2e-15);assertEquals(0,k.output(input(1,0)).reactive(),2e-15);assertEquals(13,k.getNamedStates().size());assertThrows(UnsupportedOperationException.class,()->k.getNamedStates().clear());}}
    @Test void frequencyRatesAndShortTimeBypass(){var c=constants();var k=new HplntdKernel(data(c,0,1));k.initialize(initial());k.step(.001,input(1,.1));assertEquals(.00001,k.states()[9],1e-15);k.step(.001,input(1,-.1));assertEquals(-.00001,k.states()[9],1e-15);c[TFRQ.ordinal()]=.001;var bypass=new HplntdKernel(data(c,0,1));bypass.initialize(initial());bypass.step(.001,input(1,.1));assertEquals(.1,bypass.states()[9],0);c[TFRQ.ordinal()]=.002;var equal=new HplntdKernel(data(c,0,1));equal.initialize(initial());equal.step(.001,input(1,.1));assertEquals(.00001,equal.states()[9],1e-15);}
    @Test void lowAndHighFreezeHoldFourStatesAndRestartRecovery(){for(double voltage:new double[]{.5,1.3}){var c=constants();c[TFLTR.ordinal()]=0;var k=new HplntdKernel(data(c,0,1));k.initialize(initial());var refs=k.references();k.setReferences(new HplntdKernel.References(refs.voltage(),.3,0,.5,refs.powerFactor(),0,0));var before=k.states();k.step(.001,input(voltage,0));assertTrue(k.output(input(voltage,0)).frozen());for(int index:new int[]{2,3,5,6})assertEquals(before[index],k.states()[index],0);k.step(.001,input(1,0));assertTrue(k.output(input(1,0)).frozen());for(int i=0;i<5;i++)k.step(.001,input(1,0));k.step(.001,input(voltage,0));k.step(.001,input(1,0));for(int i=0;i<9;i++)k.step(.001,input(1,0));assertTrue(k.output(input(1,0)).frozen());for(int i=0;i<3;i++)k.step(.001,input(1,0));assertFalse(k.output(input(1,0)).frozen());assertNotEquals(before[2],k.states()[2]);}}
    @Test void incrementalOutputsKeepInitialReferenceCoordinates(){var k=new HplntdKernel(data(constants(),0,1));k.initialize(initial());var refs=k.references();k.setReferences(new HplntdKernel.References(0,.2,0,.4,0,0,0));for(int i=0;i<100;i++)k.step(.0005,input(1,0));var out=k.output(input(1,0));assertEquals(out.activeCommand()-.32,out.active(),0);assertEquals(out.reactiveCommand()-.12,out.reactive(),0);assertTrue(out.active()>0);assertTrue(out.reactive()>0);}
    @Test void fallbackAndStageGuards(){var d=data(constants(),2,1);assertEquals(0,d.effectiveReferenceFlag(.009));assertEquals(2,d.effectiveReferenceFlag(-.01));var k=new HplntdKernel(d);assertThrows(IllegalStateException.class,k::states);k.initialize(initial());assertThrows(IllegalStateException.class,()->k.step(.001,input(1,0),1));k.step(.001,input(1,0),0);assertThrows(IllegalStateException.class,()->k.step(.001,input(1,0),0));assertThrows(IllegalStateException.class,()->k.step(.002,input(1,0),1));k.step(.001,input(1,0),1);assertThrows(IllegalArgumentException.class,()->d.referenceSlewTime(Double.MAX_VALUE));}
    @Test void proportionalSaturationDoesNotFreezeReactiveIntegral(){var c=constants();c[KP.ordinal()]=10;var k=new HplntdKernel(data(c,0,1));k.initialize(initial());k.setReferences(new HplntdKernel.References(0,.4,0,.3,0,0,0));for(int i=0;i<20;i++)k.step(.0005,input(1,0));assertEquals(1,k.output(input(1,0)).reactivePi(),0);double before=k.states()[2];k.step(.0005,input(1,0));assertTrue(k.states()[2]>before,"Integral state continues below its own bound despite saturated PI output");}
    @Test void staggeredBoundaryDoesNotAdvanceClockDuringEvaluation(){var k=new HplntdKernel(data(constants(),0,1));k.initialize(initial());k.setStaggeredInput(input(1,.1));var before=k.states();var rates=new double[13];k.evaluateStaggered(.001,rates,0);k.evaluateStaggered(.001,rates,0);assertArrayEquals(before,k.states(),0);assertEquals(0,k.time());assertEquals(.01,rates[9],0);var trial=before.clone();trial[9]+=1.5*.001*rates[9];k.applyStaggeredState(trial,0,.001);assertEquals(0,k.time());k.completeStaggeredStep(.001);assertEquals(.001,k.time());assertEquals(.000015,k.states()[9],1e-15);k.step(.001,input(1,0),0);assertThrows(IllegalStateException.class,()->k.evaluateStaggered(.001,rates,0));k.step(.001,input(1,0),1);}
    @Test void staggeredFreezeProjectsFourStatesAndNotReferences(){var c=constants();c[TFLTR.ordinal()]=0;var k=new HplntdKernel(data(c,0,1));k.initialize(initial());k.setStaggeredInput(input(.5,0));k.evaluateStaggered(.001,new double[13],0);var before=k.states();var trial=before.clone();for(int index:new int[]{2,3,5,6,10,11})trial[index]+=.01;k.applyStaggeredState(trial,0,.001);for(int index:new int[]{2,3,5,6})assertEquals(before[index],k.states()[index],0);for(int index:new int[]{10,11})assertEquals(trial[index],k.states()[index],0);}
    @Test void acceptedClockIsExplicitAndCannotRepeatOrJump(){var k=new HplntdKernel(data(constants(),0,1));k.initialize(initial());k.setStaggeredInput(input(1,0));k.evaluateStaggered(.00025,new double[13],0);double rounded=(double)(float).00025;k.completeStaggeredStep(.00025,rounded);assertEquals(rounded,k.time(),0);assertThrows(IllegalArgumentException.class,()->k.completeStaggeredStep(.00025,rounded));assertThrows(IllegalArgumentException.class,()->k.completeStaggeredStep(.00025,.01));assertEquals(rounded,k.time(),0);}
}
