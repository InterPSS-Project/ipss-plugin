package org.interpss.dstab.control.pss.ieee.y1992.pss2a;

import static org.junit.jupiter.api.Assertions.*;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.interpss.IpssCorePlugin;
import org.interpss.fadapter.psse.PSSEMultiFileLoader;
import com.interpss.dstab.controller.cml.field.adapt.CMLControlBlock1stOrderAdapter;
import com.interpss.dstab.controller.cml.field.block.FilterNthOrderBlock;
import com.interpss.dstab.controller.cml.field.block.DelayControlBlock;
import com.interpss.dstab.controller.cml.wrapper.ControlBlockFieldAnWrapper;

/** Scalar scheduling must retain every stored state and observation bit. */
public class Pss2aScalarPreparationTest {
    @Test void stagesAndLiveParametersMatchTheOrderedEvaluator() throws Exception {
        for(boolean zeroTime:new boolean[]{false,true}) {
            var generic=new Pss2aDirectEvaluation(model(zeroTime),false);
            var scalar=new Pss2aDirectEvaluation(model(zeroTime),true);
            assertTrue(scalar.scalarPreparation);
            assertFalse(generic.scalarPreparation);
            assertEquals(zeroTime,scalar.nodes[2].algebraic());
            assertEquals(zeroTime,scalar.nodes[5].algebraic());
            for(int step=0;step<150;step++)for(boolean predictor:new boolean[]{true,false}) {
                for(var plan:List.of(generic,scalar)) {
                    var p=plan.model;
                    p.input1Signal=Math.sin(step*.13);p.input2Signal=Math.cos(step*.17);
                    p.setRefPoint((step%11-5)*.02);p.ks3=1.+step%7*.13;p.ks4=step%5*.2;
                    p.customBlock1.setRefPoint(step%3*.005);
                    p.customBlock2.setRefPoint(-step%5*.003);
                    assertTrue(plan.step(predictor,.001));
                }
                var actual=snapshot(scalar);
                for(long bits:actual)assertTrue(Double.isFinite(Double.longBitsToDouble(bits)));
                assertEquals(snapshot(generic),actual,"stage "+step+" "+predictor);
            }
        }
    }

    @Test void observationsAndNonfiniteRecoveryRetainTheirState() throws Exception {
        var generic=new Pss2aDirectEvaluation(model(true),false);
        var scalar=new Pss2aDirectEvaluation(model(true),true);
        for(double input:new double[]{-0.,.04,Double.NaN,Double.POSITIVE_INFINITY,
                Double.NEGATIVE_INFINITY,-.02,0.}) {
            generic.model.input1Signal=input;scalar.model.input1Signal=input;
            generic.model.input2Signal=-input;scalar.model.input2Signal=-input;
            for(int repeat=0;repeat<3;repeat++) {
                assertEquals(Double.doubleToLongBits(generic.output()),Double.doubleToLongBits(scalar.output()));
                assertEquals(snapshot(generic),snapshot(scalar));
            }
        }
    }

    @Test void customBlockRetainsGenericPathAndReplacedBindingFails() throws Exception {
        var p=model();
        var wrapper=(ControlBlockFieldAnWrapper)p.customBlock1.getFieldWrapperList().stream()
            .filter(w->w.getFieldName().equals("delayBlock")).findFirst().orElseThrow();
        var original=wrapper.getField();
        int[] reads={0};
        var custom=new DelayControlBlock(1.,.1) {
            @Override public double getY(){reads[0]++;return super.getY();}
        };
        custom.initStateY0(0.);wrapper.setField(custom);
        var fallback=new Pss2aDirectEvaluation(p,true);
        assertFalse(fallback.scalarPreparation);
        fallback.prepare(false);assertTrue(reads[0]>0);
        wrapper.setField(original);
        var scalar=new Pss2aDirectEvaluation(p,true);
        assertTrue(scalar.scalarPreparation);
        wrapper.setField(custom);
        assertThrows(IllegalStateException.class,()->scalar.prepare(false));
        var replacement=new DelayControlBlock(1.,0.);replacement.initStateY0(0.);
        wrapper.setField(replacement);
        var rebuilt=new Pss2aDirectEvaluation(p,true);
        assertTrue(rebuilt.scalarPreparation);
        assertTrue(rebuilt.first.algebraic(),"new binding must use the replacement time constant");
    }

    private static List<Long> snapshot(Pss2aDirectEvaluation plan) {
        var result=new ArrayList<Long>();
        add(result,plan.output());
        for(var node:plan.nodes) {
            var b=node.block;add(result,b.getU(),b.getStateX(),b.getY());
            if(b instanceof CMLControlBlock1stOrderAdapter first)add(result,first.getDX_dt());
            if(b instanceof FilterNthOrderBlock compound)
                for(var inner:compound.getIntegrationBlocks())add(result,inner.getU(),inner.getStateX(),inner.getDX_dt());
            if(b instanceof Pss2aLeadLagBlock lag)add(result,lag.getStateDerivative());
        }
        return result;
    }
    private static void add(List<Long> result,double... values) {
        for(double value:values)result.add(Double.doubleToLongBits(value));
    }
    private static Ieee1992PSS2AStabilizer model() throws Exception {
        return model(false);
    }
    static Ieee1992PSS2AStabilizer model(boolean zeroTime) throws Exception {
        IpssCorePlugin.init();String base="testData/adpter/psse/v33/SMIB/";
        var context=new PSSEMultiFileLoader().loadDStab(base+"SMIB_v33.raw",base+"SMIB_v33_genrou_esst1a_pss2a.dyr");
        var algorithm=context.getDynSimuAlgorithm();
        var p=(Ieee1992PSS2AStabilizer)context.getDStabilityNet().getMachine("Bus1-mach1").getStabilizer();
        p.getData().setT6(zeroTime?0.:.05);p.getData().setT7(zeroTime?0.:.05);
        algorithm.setSimuOutputHandler(new com.interpss.dstab.cache.StateMonitor());
        assertTrue(algorithm.getAclfAlgorithm().loadflow());assertTrue(algorithm.initialization());
        return p;
    }
}
