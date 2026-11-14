package com.interpss.dstab.controller.exc.ieee1981st1;
import static org.junit.jupiter.api.Assertions.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.interpss.IpssCorePlugin;
import org.interpss.fadapter.psse.PSSEMultiFileLoader;
import com.interpss.dstab.controller.cml.field.adapt.CMLControlBlockAdapter;
import com.interpss.dstab.controller.cml.field.adapt.CMLControlBlock1stOrderAdapter;
import com.interpss.dstab.controller.exc.ieee1981st1.IEEE1981ST1Exciter;
import com.interpss.dstab.controller.exc.ieee1981st1.St1DirectEvaluation;
public class St1BoundaryReuseTest {
    static String context;
    static void bits(double a,double b) { assertEquals(Double.doubleToLongBits(a),Double.doubleToLongBits(b),context); }
    static St1DirectEvaluation plan(IEEE1981ST1Exciter m) throws Exception {
        var field=IEEE1981ST1Exciter.class.getDeclaredField("directEvaluation");field.setAccessible(true);
        return (St1DirectEvaluation)field.get(m);
    }
    @Test void completeLocalStorageHistoryAndPublishedFieldMatchAcrossEvents() throws Exception {
        for(double ta:new double[]{0.,.02,.08})for(double kf:new double[]{0.,-0.,.03})for(int count:new int[]{1,4,10,128}) {
            var a=initializedModel(ta,kf);var b=initializedModel(ta,kf);
            a.enableDirectEvaluationExperiment(false,true);
            b.enableDirectEvaluationExperiment(true,true);
            int n=plan(a).localBlocks.length;
            double[][] ha={new double[n],new double[n],new double[n]},hb={new double[n],new double[n],new double[n]};
            double reference=a.getRefPoint();
            for(int step=0;step<80;step++) {
                context="ta="+ta+" kf="+kf+" count="+count+" step="+step;
                for(var m:List.of(a,b)) {
                    m.setRefPoint(reference+Math.sin(step*.1)*.01);
                    m.uel=step%4;m.setVuel(step%2*.1);m.setVoel(step%3==0?2.:8.);
                    m.vosError=step%2;m.vosOutput=1-m.vosError;
                    m.kc=step%7*.01;m.klr=step%3*.05;
                    m.getDStabBus().setVoltage(new org.apache.commons.math3.complex.Complex(.8+.2*Math.cos(step*.1),.02));
                }
                double fa=a.advanceDirectLocalStore(1./240.,count,step%13==0,step%29!=0,true,ha[0],ha[1],ha[2]);
                double fb=b.advanceDirectLocalStore(1./240.,count,step%13==0,step%29!=0,true,hb[0],hb[1],hb[2]);
                bits(fa,fb);a.getMachine().setEfd(fa);b.getMachine().setEfd(fb);
                for(int i=0;i<3;i++)for(int j=0;j<n;j++)bits(ha[i][j],hb[i][j]);
                for(int id=0;id<8;id++) {
                    var x=plan(a).nodes[id].block;var y=plan(b).nodes[id].block;
                    bits(x.getU(),y.getU());bits(x.getY(),y.getY());
                    if(x instanceof CMLControlBlock1stOrderAdapter xx) {
                        var yy=(CMLControlBlock1stOrderAdapter)y;
                        bits(xx.getStateX(),yy.getStateX());bits(xx.getDX_dt(),yy.getDX_dt());
                    }
                }
            }
            assertEquals(0,plan(a).boundaryReuseCount);
            assertEquals(ta>0. && kf==0.?80L*(count-1):0L,plan(b).boundaryReuseCount,
                context+" "+b.getMachine().getClass()+" "+b.getDStabBus().getClass()+" "+Arrays.toString(plan(b).localBlocks));
        }
    }
    @Test void nonzeroFeedbackStateAndLateObserverKeepTheOrdinaryPath() throws Exception {
        var m=initializedModel(.08,0.);m.enableDirectEvaluationExperiment(true,true);
        var p=plan(m);int n=p.localBlocks.length;
        double[][] storage={new double[n],new double[n],new double[n]};
        m.advanceDirectLocalStore(.001,4,false,false,true,storage[0],storage[1],storage[2]);
        assertEquals(3,p.boundaryReuseCount);
        var observer=new org.eclipse.emf.common.notify.impl.AdapterImpl();
        m.getMachine().eAdapters().add(observer);
        m.advanceDirectLocalStore(.001,4,false,true,true,storage[0],storage[1],storage[2]);
        assertEquals(3,p.boundaryReuseCount);
        m.getMachine().eAdapters().remove(observer);
        m.advanceDirectLocalStore(.001,4,false,true,true,storage[0],storage[1],storage[2]);
        assertEquals(6,p.boundaryReuseCount);
        ((CMLControlBlock1stOrderAdapter)p.nodes[5].block).setStateX(.01);
        m.advanceDirectLocalStore(.001,4,false,true,true,storage[0],storage[1],storage[2]);
        assertEquals(6,p.boundaryReuseCount);
    }
    @Test void nonzeroFeedbackHistoryKeepsTheOrdinaryBoundarySequence() throws Exception {
        var m=initializedModel(.08,0.);m.enableDirectEvaluationExperiment(true,true);
        var p=plan(m);int n=p.localBlocks.length;double[] history=new double[n];
        for(int i=0;i<n;i++)if(p.localIds[i]==5)history[i]=.01;
        m.advanceDirectLocalStore(.001,4,false,true,true,history,new double[n],new double[n]);
        assertEquals(0,p.boundaryReuseCount);
    }
    @Test void changedGateInputKeepsTheOrdinaryBoundarySequence() throws Exception {
        var m=initializedModel(.08,0.);
        var function=(com.interpss.dstab.controller.cml.field.func.HighValueFunction)
            m.getFieldWrapperList().stream().filter(w->w.getFieldName().equals("uelGate1Function")).findFirst().orElseThrow().getField();
        function.getVarRecList().get(1).setSymbolStr("this.vuel");
        m.enableDirectEvaluationExperiment(true,true);var p=plan(m);int n=p.localBlocks.length;
        m.advanceDirectLocalStore(.001,4,false,false,true,new double[n],new double[n],new double[n]);
        assertEquals(0,p.boundaryReuseCount);
    }
    private static IEEE1981ST1Exciter initializedModel(double ta,double kf) throws Exception {
        IpssCorePlugin.init();
        String base="testData/adpter/psse/v33/SMIB/";
        var context=new PSSEMultiFileLoader().loadDStab(base+"SMIB_v33.raw",
            base+"SMIB_v33_genrou_exst1.dyr");
        var exciter=(IEEE1981ST1Exciter)context.getDStabilityNet().getMachine("Bus1-mach1").getExciter();
        exciter.getData().setTa(ta);exciter.getData().setKf(kf);exciter.getData().setTf(.7);
        var algorithm=context.getDynSimuAlgorithm();
        algorithm.setSimuOutputHandler(new com.interpss.dstab.cache.StateMonitor());
        assertTrue(algorithm.getAclfAlgorithm().loadflow());
        assertTrue(algorithm.initialization());
        return (IEEE1981ST1Exciter)context.getDStabilityNet().getMachine("Bus1-mach1").getExciter();
    }
}
