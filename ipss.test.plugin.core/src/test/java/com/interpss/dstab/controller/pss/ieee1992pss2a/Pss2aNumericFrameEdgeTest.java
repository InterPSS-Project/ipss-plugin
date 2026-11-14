package com.interpss.dstab.controller.pss.ieee1992pss2a;
import static org.junit.jupiter.api.Assertions.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import com.interpss.dstab.controller.cml.field.adapt.CMLControlBlock1stOrderAdapter;
import com.interpss.dstab.controller.cml.field.block.*;
public class Pss2aNumericFrameEdgeTest {
    @Test void liveTopLevelTimesRestorationAndZeroStepArithmetic() throws Exception {
        var a=Pss2aNumericFrameTest.plan(false,4,2,.08);var b=Pss2aNumericFrameTest.plan(true,4,2,.08);
        double[] times={0.,-0.,1.e-13,1.e-12,1.0001e-12,-.03,.05,Double.NaN,Double.POSITIVE_INFINITY};
        for(int step=0;step<times.length;step++) {
            for(var p:List.of(a,b)) {
                for(int i:new int[]{0,1,2,3,4,5,7,8}) {
                    var block=(CMLControlBlock1stOrderAdapter)p.nodes[i].block;
                    Class<?> type=block instanceof WashoutControlBlock?WashoutControlBlock.class:block.getClass();
                    var time=type.getDeclaredField(block instanceof FilterControlBlock?"t2":"t");time.setAccessible(true);time.setDouble(block,times[step]);
                    block.restoreIntegrationState(-0.,step%2==0?-0.:.002*i,.001*i);
                }
                p.model.input1Signal=step*.01;p.model.input2Signal=-step*.02;
            }
            Pss2aNumericFrameTest.advancePair(a,b,true,0.);
            Pss2aNumericFrameTest.advancePair(a,b,false,.001);
        }
        assertEquals(times.length*2,b.numericStages);
    }
    @Test void defaultAndFallbackSwitchingDoesNotLeaveStaleFrameStorage() throws Exception {
        var a=Pss2aNumericFrameTest.plan(false,2,1,.05);var b=Pss2aNumericFrameTest.plan(true,2,1,.05);
        for(int i=0;i<30;i++) {
            for(var p:List.of(a,b)) {
                p.model.input1Signal=.04*Math.sin(i);p.model.input2Signal=.03*Math.cos(i);
                if(i%2==0)p.model.eAdapters().add(new org.eclipse.emf.common.notify.impl.AdapterImpl());
                else p.model.eAdapters().clear();
            }
            Pss2aNumericFrameTest.advancePair(a,b,true,.004166666666666667);
            Pss2aNumericFrameTest.advancePair(a,b,false,.004166666666666667);
        }
        assertEquals(30,b.numericStages);
    }
}
