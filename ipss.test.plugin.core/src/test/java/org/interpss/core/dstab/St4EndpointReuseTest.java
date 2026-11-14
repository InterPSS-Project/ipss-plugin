package org.interpss.core.dstab;

import static org.junit.jupiter.api.Assertions.*;
import java.nio.file.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.apache.commons.math3.complex.Complex;
import org.interpss.IpssCorePlugin;
import org.interpss.fadapter.psse.PSSEMultiFileLoader;
import org.interpss.dstab.algo.PsseStaggeredEulerSolver;
import com.interpss.dstab.controller.exc.ieee2005st4b.IEEE2005ST4BExciter;
import com.interpss.dstab.algo.*;
import com.interpss.dstab.cache.StateMonitor;
import com.interpss.dstab.DStabObjectFactory;
import com.interpss.core.acsc.fault.SimpleFaultCode;
import com.interpss.dstab.mach.impl.RoundRotorMachineImpl;
import com.interpss.dstab.controller.cml.annotate.AbstractAnnotateController;
import com.interpss.dstab.controller.cml.field.adapt.CMLControlBlockAdapter;
import com.interpss.dstab.controller.cml.field.adapt.CMLControlBlock1stOrderAdapter;
import com.interpss.dstab.controller.cml.field.block.*;
import com.interpss.dstab.controller.cml.wrapper.BlockFieldAnWrapper;

public class St4EndpointReuseTest {
    record Fixture(DynamicSimuAlgorithm algorithm,PsseStaggeredEulerSolver solver,
        RoundRotorMachineImpl machine,IEEE2005ST4BExciter exciter) {}
    Fixture load(boolean optimized,boolean pss,Path path) throws Exception {
        IpssCorePlugin.init();Path base=Path.of("testData/adpter/psse/v33/SMIB");
        String text=Files.readString(base.resolve("SMIB_v33_genrou_esst4b.dyr"));
        if(pss)text+="\n"+Files.readAllLines(base.resolve("SMIB_v33_genrou_esst1a_pss2a.dyr")).stream()
            .filter(line->line.contains("'PSS2A'")).findFirst().orElseThrow()+"\n";
        Files.writeString(path,text);
        var context=new PSSEMultiFileLoader().loadDStab(base.resolve("SMIB_v33.raw").toString(),path.toString());
        var algo=context.getDynSimuAlgorithm();assertTrue(algo.getAclfAlgorithm().loadflow());
        var solver=new PsseStaggeredEulerSolver(algo);
        assertTrue(solver.isSt4EndpointReuseEnabled());
        solver.setSt4EndpointReuseEnabled(optimized);
        solver.setNetworkSolutionTolerance(1.e-5);solver.setReuseNetworkEndpoint(true);
        algo.setSolver(solver);algo.setSimuMethod(DynamicSimuMethod.MODIFIED_EULER);
        algo.setSimuStepSec(1./240.);algo.setTotalSimuTimeSec(1.);algo.setSimuOutputHandler(new StateMonitor());
        var net=algo.getNetwork();
        net.addDynamicEvent(DStabObjectFactory.createBusFaultEvent("Bus1",net,SimpleFaultCode.GROUND_3P,new Complex(0.,.2),null,.2,.05),"EndpointFault");
        assertTrue(algo.initialization());var machine=(RoundRotorMachineImpl)net.getMachine("Bus1-mach1");
        return new Fixture(algo,solver,machine,(IEEE2005ST4BExciter)machine.getExciter());
    }
    @Test void coupledEventsActivationAndLiveControlsRemainBitwiseEqual(@TempDir Path temp) throws Exception {
        for(boolean pss:new boolean[]{false,true}) {
            var ordinary=load(false,pss,temp.resolve("a-"+pss+".dyr"));var fast=load(true,pss,temp.resolve("b-"+pss+".dyr"));
            double reference=ordinary.exciter.getRefPoint();
            for(int i=0;i<240;i++) {
                for(var f:List.of(ordinary,fast)) {
                    if(i%17==0){f.exciter.setRefPoint(reference+(i%3-1)*.03);f.solver.notifyDiscontinuity();}
                    f.exciter.setVuel(i%23==0?.015:0.);f.exciter.setVoel(i%31==0?.2:Double.POSITIVE_INFINITY);
                    f.exciter.kc=.08+i%7*.01;f.exciter.vbmax=8.+i%4;f.exciter.setStatus(i!=73);
                    if(pss)f.machine.getStabilizer().setStatus(i!=97);
                    assertTrue(f.algorithm.solveDEqnStep(true));
                }
                assertEquals(snapshot(ordinary),snapshot(fast),"PSS="+pss+", step="+i);
            }
            assertEquals(0,ordinary.exciter.getEndpointOutputReuseCount());
            assertTrue(fast.exciter.getEndpointOutputReuseCount()>100);
        }
    }
    @Test void observedMachineUsesOrdinaryEvaluation(@TempDir Path temp) throws Exception {
        var a=load(false,false,temp.resolve("a.dyr"));var b=load(false,false,temp.resolve("b.dyr"));
        a.machine.eAdapters().add(new org.eclipse.emf.common.notify.impl.AdapterImpl());
        b.machine.eAdapters().add(new org.eclipse.emf.common.notify.impl.AdapterImpl());
        assertTrue(a.exciter.nextStep(0.,DynamicSimuMethod.MODIFIED_EULER,a.machine,0));
        long expected=Double.doubleToRawLongBits(a.exciter.getOutput(a.machine));
        assertEquals(expected,Double.doubleToRawLongBits(b.exciter.evaluateFixedInputEndpoint(b.machine,false)));
        assertEquals(snapshot(a),snapshot(b));assertEquals(0,b.exciter.getEndpointOutputReuseCount());
    }
    @Test void customBlockReadsAreNotSuppressed(@TempDir Path temp) throws Exception {
        var a=load(false,false,temp.resolve("a.dyr"));var b=load(false,false,temp.resolve("b.dyr"));int[] reads=new int[2];
        int index=0;
        for(var f:List.of(a,b)) {
            final int slot=index++;
            var wrapper=(BlockFieldAnWrapper)f.exciter.getFieldWrapperList().stream().filter(w->w.getFieldName().equals("trDelayBlock")).findFirst().orElseThrow();
            var old=(DelayControlBlock)wrapper.getField();
            var replacement=new DelayControlBlock(old.getK(),old.getT()){@Override public double getY(){reads[slot]++;return super.getY();}};
            replacement.restoreIntegrationState(old.getU(),old.getStateX(),old.getDX_dt());
            wrapper.setField(replacement);f.exciter.enableDirectEvaluationExperiment();
        }
        assertTrue(a.exciter.nextStep(0.,DynamicSimuMethod.MODIFIED_EULER,a.machine,0));
        assertEquals(Double.doubleToRawLongBits(a.exciter.getOutput(a.machine)),Double.doubleToRawLongBits(b.exciter.evaluateFixedInputEndpoint(b.machine,false)));
        assertEquals(reads[0],reads[1]);assertTrue(reads[0]>0);assertEquals(0,b.exciter.getEndpointOutputReuseCount());
        assertEquals(snapshot(a),snapshot(b));
    }
    @Test void endpointScopeEndsBeforeTheNextParameterOrVoltageChange(@TempDir Path temp) throws Exception {
        var a=load(false,false,temp.resolve("a.dyr"));var b=load(false,false,temp.resolve("b.dyr"));
        for(int i=0;i<80;i++) {
            for(var f:List.of(a,b)) {
                f.machine.getDStabBus().setVoltage(new Complex(.95+.001*i,.03));
                f.exciter.kp=2.+i*.02;f.exciter.ki=.2+i*.01;f.exciter.angKp_deg=i;
                f.exciter.setVuel(i%2==0?0.:-0.);f.exciter.setVoel(i%5==0?.3:Double.POSITIVE_INFINITY);
            }
            assertTrue(a.exciter.nextStep(0.,DynamicSimuMethod.MODIFIED_EULER,a.machine,0));
            assertEquals(Double.doubleToRawLongBits(a.exciter.getOutput(a.machine)),Double.doubleToRawLongBits(b.exciter.evaluateFixedInputEndpoint(b.machine,false)));
            assertEquals(snapshot(a),snapshot(b));
        }
        assertTrue(b.exciter.getEndpointOutputReuseCount()>0);
    }
    @Test void unsettledFeedbackKeepsTheIterativeOutputPath(@TempDir Path temp) throws Exception {
        var a=load(false,false,temp.resolve("a.dyr"));var b=load(false,false,temp.resolve("b.dyr"));
        var gainField=GainBlock.class.getDeclaredField("k");gainField.setAccessible(true);
        for(var f:List.of(a,b)) {
            var gain=f.exciter.getFieldWrapperList().stream().filter(w->w.getFieldName().equals("kgGainBlock")).findFirst().orElseThrow().getField();
            gainField.setDouble(gain,.05);f.exciter.setRefPoint(f.exciter.getRefPoint()+.001);
        }
        assertTrue(a.exciter.nextStep(0.,DynamicSimuMethod.MODIFIED_EULER,a.machine,0));
        double expected=a.exciter.getOutput(a.machine),actual=b.exciter.evaluateFixedInputEndpoint(b.machine,false);
        assertTrue(Double.isFinite(expected));assertEquals(Double.doubleToRawLongBits(expected),Double.doubleToRawLongBits(actual));
        assertEquals(snapshot(a),snapshot(b));assertEquals(0,b.exciter.getEndpointOutputReuseCount());
    }
    @Test void liveNumericLimitsRemainVisibleWithTheBoundGraph(@TempDir Path temp) throws Exception {
        var a=load(false,false,temp.resolve("a.dyr"));var b=load(false,false,temp.resolve("b.dyr"));
        for(int i=0;i<30;i++) {
            for(var f:List.of(a,b)) {
                var gain=(GainBlock)f.exciter.getFieldWrapperList().stream().filter(w->w.getFieldName().equals("kgGainBlock")).findFirst().orElseThrow().getField();
                var upper=org.interpss.numeric.datatype.LimitType.class.getDeclaredField("_max");
                var lower=org.interpss.numeric.datatype.LimitType.class.getDeclaredField("_min");
                upper.setAccessible(true);lower.setAccessible(true);
                upper.setDouble(gain.getLimit(),.05+.01*i);lower.setDouble(gain.getLimit(),-.2-.01*i);
                f.exciter.setRefPoint(f.exciter.getRefPoint()+(i%2==0?.001:-.001));
            }
            assertTrue(a.exciter.nextStep(0.,DynamicSimuMethod.MODIFIED_EULER,a.machine,0));
            assertEquals(Double.doubleToRawLongBits(a.exciter.getOutput(a.machine)),Double.doubleToRawLongBits(b.exciter.evaluateFixedInputEndpoint(b.machine,false)));
            assertEquals(snapshot(a),snapshot(b));
        }
    }
    @Test void nonfiniteInputRetainsFailureOutputAndDoesNotReuse(@TempDir Path temp) throws Exception {
        var a=load(false,false,temp.resolve("a.dyr"));var b=load(false,false,temp.resolve("b.dyr"));
        a.exciter.setVuel(Double.NaN);b.exciter.setVuel(Double.NaN);
        assertTrue(a.exciter.nextStep(0.,DynamicSimuMethod.MODIFIED_EULER,a.machine,0));
        double expected=a.exciter.getOutput(a.machine),actual=b.exciter.evaluateFixedInputEndpoint(b.machine,false);
        assertTrue(Double.isNaN(expected));assertTrue(Double.isNaN(actual));
        assertEquals(0,b.exciter.getEndpointOutputReuseCount());
    }
    @Test void reinitializationRebuildsBoundBlocks(@TempDir Path temp) throws Exception {
        var a=load(false,false,temp.resolve("a.dyr"));var b=load(true,false,temp.resolve("b.dyr"));
        for(var f:List.of(a,b)) {
            f.exciter.getData().setTr(.02);
            assertTrue(f.exciter.initStates(f.machine.getDStabBus(),f.machine));
        }
        // Explicitly rebuilt control is the oracle for the reinitialized graph.
        a.exciter.enableDirectEvaluationExperiment();
        for(int i=0;i<10;i++) {
            a.machine.getDStabBus().setVoltage(new Complex(.99+i*.001,.01));
            b.machine.getDStabBus().setVoltage(new Complex(.99+i*.001,.01));
            assertTrue(a.exciter.nextStep(0.,DynamicSimuMethod.MODIFIED_EULER,a.machine,0));
            assertEquals(Double.doubleToRawLongBits(a.exciter.getOutput(a.machine)),
                    Double.doubleToRawLongBits(b.exciter.evaluateFixedInputEndpoint(b.machine,false)));
            assertEquals(snapshot(a),snapshot(b));
        }
        assertThrows(IllegalStateException.class,()->b.solver.setSt4EndpointReuseEnabled(false));
    }
    static List<Object> snapshot(Fixture f) {
        var result=new ArrayList<Object>();result.add(f.machine.captureMechanicalIntegrationState());result.add(f.machine.captureRoundElectricalState());
        add(result,f.machine.getAngle(),f.machine.getSpeed(),f.machine.getEfd(),f.machine.getPe(),f.machine.getPm());
        for(var bus:f.algorithm.getNetwork().getBusList())add(result,bus.getVoltage().getReal(),bus.getVoltage().getImaginary());
        controller(result,f.exciter);if(f.machine.getStabilizer() instanceof AbstractAnnotateController pss)controller(result,pss);return result;
    }
    static void controller(List<Object> result,AbstractAnnotateController c) {
        for(var w:c.getFieldWrapperList()) {
            if(w.getField() instanceof AbstractAnnotateController child)controller(result,child);
            else if(w.getField() instanceof CMLControlBlockAdapter b) {
                add(result,b.getU(),b.getStateX());
                if(b instanceof CMLControlBlock1stOrderAdapter first)add(result,first.getDX_dt());
                if(b instanceof FilterNthOrderBlock compound)for(var inner:compound.getIntegrationBlocks())add(result,inner.getU(),inner.getStateX(),inner.getDX_dt());
            }
        }
    }
    static void add(List<Object> result,double... values){for(double v:values){assertTrue(Double.isFinite(v));result.add(Double.doubleToRawLongBits(v));}}
}
