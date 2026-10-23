package org.interpss.dstab.control.pss.ieee.y1992.pss2a;

import static org.junit.jupiter.api.Assertions.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.interpss.numeric.datatype.LimitType;
import com.interpss.dstab.controller.cml.field.ICMLStaticBlock.StaticBlockType;
import com.interpss.dstab.controller.cml.field.adapt.CMLControlBlock1stOrderAdapter;
import com.interpss.dstab.controller.cml.field.block.*;
import com.interpss.dstab.controller.cml.wrapper.ControlBlockFieldAnWrapper;

public class Pss2aNumericFrameTest {
    static ControlBlockFieldAnWrapper field(Ieee1992PSS2AStabilizer p,String name){
        return (ControlBlockFieldAnWrapper)p.getFieldWrapperList().stream().filter(w->w.getFieldName().equals(name)).findFirst().orElseThrow();
    }
    private static final String OPTION="ipss.experiment.pssNumericFrame";
    static Pss2aDirectEvaluation construct(Ieee1992PSS2AStabilizer model, boolean enabled) {
        String previous=System.getProperty(OPTION);
        try {
            System.setProperty(OPTION,Boolean.toString(enabled));
            return new Pss2aDirectEvaluation(model,true);
        } finally {
            if(previous==null)System.clearProperty(OPTION);else System.setProperty(OPTION,previous);
        }
    }
    static Pss2aDirectEvaluation plan(boolean enabled,int m,int n,double t) throws Exception {
        var p=Pss2aScalarPreparationTest.model(t==0.);
        var compound=new FilterNthOrderBlock(.03,t,m,n);assertTrue(compound.initStateY0(0.));
        p.filterNthBlock=compound;field(p,"filterNthBlock").setField(compound);
        return construct(p,enabled);
    }
    static void add(List<Long> out,double... values){for(double v:values)out.add(Double.doubleToLongBits(v));}
    static List<Long> stored(Pss2aDirectEvaluation p) throws Exception {
        var out=new ArrayList<Long>();
        add(out,p.model.customBlock1.getRuntimeInputValue(),p.model.customBlock2.getRuntimeInputValue(),
            p.model.customBlock1.getRuntimeOutputValue(),p.model.customBlock2.getRuntimeOutputValue());
        for(var node:p.nodes){
            var b=node.block;add(out,b.getU(),b.getStateX());
            if(b instanceof CMLControlBlock1stOrderAdapter first)add(out,first.getDX_dt());
            if(b instanceof FilterNthOrderBlock compound)for(var child:compound.getIntegrationBlocks())add(out,child.getU(),child.getStateX(),child.getDX_dt());
            if(b instanceof Pss2aLeadLagBlock lag){
                add(out,lag.getStateDerivative());var start=Pss2aLeadLagBlock.class.getDeclaredField("predictorStartState");start.setAccessible(true);add(out,start.getDouble(lag));
            }
        }
        for(int i=0;i<10;i++)add(out,p.inputs[i],p.states[i],p.rates[i]);return out;
    }
    static void equal(Pss2aDirectEvaluation a,Pss2aDirectEvaluation b) throws Exception {
        assertEquals(stored(a),stored(b),"stored values before output refresh");
        assertEquals(Double.doubleToLongBits(a.output()),Double.doubleToLongBits(b.output()));
        assertEquals(stored(a),stored(b),"stored values after output refresh");
    }
    static void advancePair(Pss2aDirectEvaluation a,Pss2aDirectEvaluation b,boolean predictor,double dt) throws Exception {
        assertTrue(a.step(predictor,dt));assertTrue(b.step(predictor,dt));equal(a,b);
    }
    @Test void cascadeOrdersZeroAndNonzeroStepsPreserveAllStoredStates() throws Exception {
        for(int[] order:new int[][]{{1,0},{1,1},{3,2},{5,1}})for(double t:new double[]{0.,.08}){
            var a=plan(false,order[0],order[1],t);var b=plan(true,order[0],order[1],t);
            assertNull(a.numericFrame);assertNotNull(b.numericFrame);
            for(int k=0;k<60;k++)for(boolean predictor:new boolean[]{true,false}){
                for(var p:List.of(a,b)){
                    p.model.input1Signal=Math.sin(k*.13)*.02;p.model.input2Signal=Math.cos(k*.17)*.03;
                    p.model.ks3=1.+k%3*.02;p.model.ks4=k%7*.01;p.model.setRefPoint(k%5*.002);
                }
                advancePair(a,b,predictor,k%3==0?0.:1./240.);
            }
            assertEquals(120,b.numericStages);
        }
    }
    @Test void externalRestorationAndLiveChildParametersRemainAuthoritative() throws Exception {
        var a=plan(false,3,2,.08);var b=plan(true,3,2,.08);
        for(int k=0;k<20;k++){
            for(var p:List.of(a,b)){
                var children=((FilterNthOrderBlock)p.nodes[6].block).getIntegrationBlocks();
                for(int i=0;i<children.size();i++){
                    var child=children.get(i);child.restoreIntegrationState(.03*i,.01*(i-k),.02*k);
                    var gain=child.getClass().getDeclaredField("k");gain.setAccessible(true);gain.setDouble(child,1.+k*.01);
                    var time=child.getClass().getDeclaredField(child instanceof FilterControlBlock?"t2":"t");time.setAccessible(true);time.setDouble(child,k%2==0?0.:.05);
                }
                p.model.input1Signal=k*.003;p.model.input2Signal=-k*.002;
            }
            advancePair(a,b,true,0.);advancePair(a,b,false,.001);
        }
        assertEquals(40,b.numericStages);
    }
    @Test void lateLimiterChangesFallBackAndNoLimitRestoresEligibility() throws Exception {
        for(boolean inner:new boolean[]{true,false})for(var type:new StaticBlockType[]{StaticBlockType.Limit,StaticBlockType.NonWindup}){
            var a=plan(false,2,1,.05);var b=plan(true,2,1,.05);
            for(var p:List.of(a,b)){
                var block=inner?((FilterNthOrderBlock)p.nodes[6].block).getIntegrationBlocks().get(0):(CMLControlBlock1stOrderAdapter)p.nodes[7].block;
                block.setType(type);var limit=FilterControlBlock.class.getDeclaredField("limit");limit.setAccessible(true);limit.set(block,new LimitType(.01,-.01));
                p.model.input1Signal=.5;p.model.input2Signal=.2;
            }
            advancePair(a,b,true,.001);advancePair(a,b,false,.001);assertEquals(0,b.numericStages);
            for(var p:List.of(a,b)){
                var block=inner?((FilterNthOrderBlock)p.nodes[6].block).getIntegrationBlocks().get(0):(CMLControlBlock1stOrderAdapter)p.nodes[7].block;
                block.setType(StaticBlockType.NoLimit);
            }
            advancePair(a,b,true,.001);assertEquals(1,b.numericStages);
        }
    }
    @Test void aliasedInternalStateAndCustomCompoundRetainOriginalProtocol() throws Exception {
        var plans=new ArrayList<Pss2aDirectEvaluation>();
        for(boolean enabled:new boolean[]{false,true}){
            var p=plan(enabled,2,1,.08).model;var internal=(FilterControlBlock)p.filterNthBlock.getIntegrationBlocks().get(0);
            p.filterBlock1=internal;field(p,"filterBlock1").setField(internal);
            plans.add(construct(p,enabled));
        }
        assertNull(plans.get(1).numericFrame);advancePair(plans.get(0),plans.get(1),true,.001);
        int[] calls=new int[2];plans.clear();
        for(int i=0;i<2;i++){
            var p=plan(i==1,2,1,.08).model;int index=i;
            var custom=new FilterNthOrderBlock(.03,.08,2,1){@Override public void setStageInput(double v){calls[index]++;super.setStageInput(v);}};
            assertTrue(custom.initStateY0(0.));p.filterNthBlock=custom;field(p,"filterNthBlock").setField(custom);
            plans.add(construct(p,i==1));
        }
        assertNull(plans.get(1).numericFrame);advancePair(plans.get(0),plans.get(1),true,.001);
        assertEquals(calls[0],calls[1]);assertTrue(calls[0]>0);
    }
    @Test void nonfiniteInputsSignedZeroAndBindingReplacementRemainCompatible() throws Exception {
        var a=plan(false,3,1,0.);var b=plan(true,3,1,0.);
        for(double value:new double[]{-0.,0.,.02,Double.NaN,Double.POSITIVE_INFINITY,Double.NEGATIVE_INFINITY}){
            for(var p:List.of(a,b)){p.model.input1Signal=value;p.model.input2Signal=-value;}
            advancePair(a,b,true,0.);advancePair(a,b,false,.001);
        }
        for(var p:List.of(a,b)){
            field(p.model,"filterNthBlock").setField(new FilterNthOrderBlock(.01,.02,1,1));
            assertThrows(IllegalStateException.class,()->p.step(true,0.));
        }
    }
}