package org.interpss.core.dstab;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import org.apache.commons.math3.complex.Complex;
import org.interpss.IpssCorePlugin;
import org.interpss.dstab.algo.PsseStaggeredEulerSolver;
import org.interpss.dstab.control.exc.ieee.y2005.st4b.IEEE2005ST4BExciter;
import org.interpss.fadapter.psse.PSSEMultiFileLoader;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.interpss.core.acsc.fault.SimpleFaultCode;
import com.interpss.dstab.DStabObjectFactory;
import com.interpss.dstab.algo.DynamicSimuAlgorithm;
import com.interpss.dstab.cache.StateMonitor;
import com.interpss.dstab.controller.cml.annotate.AbstractAnnotateController;
import com.interpss.dstab.controller.cml.field.adapt.CMLControlBlock1stOrderAdapter;
import com.interpss.dstab.controller.cml.field.adapt.CMLControlBlockAdapter;
import com.interpss.dstab.mach.impl.RoundRotorMachineImpl;

/** Fixed signal bindings retain the ordered feedback and rectifier observations. */
class Pss2aCompiledStageTest {
    private static org.interpss.dstab.control.pss.ieee.y1992.pss2a.Ieee1992PSS2AStabilizer pss(Fixture f) {
        return (org.interpss.dstab.control.pss.ieee.y1992.pss2a.Ieee1992PSS2AStabilizer)f.machine.getStabilizer();
    }
    @Test void directStages(@TempDir Path root) throws Exception {
        var ordinary=load(false,true,root.resolve("direct-ordinary.dyr"));
        var compiled=load(true,true,root.resolve("direct-compiled.dyr"));
        for(int step=0;step<200;step++) {
            for(boolean predictor:new boolean[]{true,false}) {
                for(var f:List.of(ordinary,compiled)) {
                    var p=pss(f);p.setRefPoint((step%11-5)*.02);
                    p.ks3=1.+(step%7)*.13;p.ks4=(step%5)*.2;
                    p.input1Signal=Math.sin(step*.13);p.input2Signal=Math.cos(step*.17);
                    assertTrue(p.eulerStep(predictor,.001,f.machine));
                    assertEquals(AbstractAnnotateController.RuntimePhase.IDLE,p.getRuntimePhase());
                }
                assertEquals(snapshot(ordinary),snapshot(compiled),"Direct stage "+step+" "+predictor);
            }
        }
        assertTrue(pss(compiled).getCompiledStageEvaluationCount()>400);
        var p=pss(compiled);p.invalidateRuntimePlan();assertFalse(p.hasCompiledStageEvaluator());
        var wrapper=(com.interpss.dstab.controller.cml.wrapper.BlockFieldAnWrapper<?>)p.getFieldWrapperList().stream()
                .filter(w->w.getFieldName().equals("filterBlock1")).findFirst().orElseThrow();
        var expression=new com.interpss.dstab.datatype.ExpCalculator("this.filterNthBlock.y");
        com.interpss.dstab.controller.cml.annotate.util.CMLSymbolMapper.mapExpressionSymbolStr2Type(expression.getRecList());
        wrapper.setInputExp(expression);p.compileRuntimePlan();
        assertFalse(p.hasCompiledStageEvaluator(),"Changed equation must retain generic evaluation");
    }

    @Test
    void coupledStatesAndHistoryMatchAcrossFaultsAndLiveControls(@TempDir Path temporary) throws Exception {
        for (boolean pss : new boolean[]{true}) {
            var ordinary=load(false,pss,temporary.resolve("ordinary-"+pss+".dyr"));
            var compiled=load(true,pss,temporary.resolve("compiled-"+pss+".dyr"));
            assertFalse(pss(ordinary).hasCompiledStageEvaluator());
            assertTrue(pss(compiled).hasCompiledStageEvaluator());
            // The rectifier reads machine current and updates its stored bridge voltage.
            // Compiling its inputs does not authorize reordering or reusing its output.
            assertFalse(compiled.exciter.isCompiledOutputSchedulingEnabled());
            assertThrows(IllegalStateException.class,()->compiled.solver.setCompiledSt4bBindingsEnabled(false));
            double reference=ordinary.exciter.getRefPoint();
            for (int step=0;step<240;step++) {
                for (var f : List.of(ordinary,compiled)) {
                    if (step%17==0) {
                        f.exciter.setRefPoint(reference+(step%3-1)*.03);
                        f.solver.notifyDiscontinuity();
                    }
                    f.exciter.setVuel(step%23==0?.015:0.);
                    f.exciter.setVoel(step%31==0?.2:Double.POSITIVE_INFINITY);
                    f.exciter.kc=.08+step%7*.01;
                    f.exciter.vbmax=8.+step%4;
                    f.exciter.setStatus(step!=73);
                    if (pss) f.machine.getStabilizer().setStatus(step!=97);
                    assertTrue(f.algorithm.solveDEqnStep(true));
                }
                assertEquals(snapshot(ordinary),snapshot(compiled),"PSS="+pss+", step="+step);
            }
        }
    }

    @Test void alteredMetadataAndCustomBlocksRetainGenericEvaluation(@TempDir Path root) throws Exception {
        var fixture=load(true,true,root.resolve("fallback.dyr"));var p=pss(fixture);
        assertThrows(IllegalStateException.class,()->p.setCompiledStageEnabled(false));
        p.invalidateRuntimePlan();
        var input=block(p,"filterBlock1").getInputExp();
        var record=input.getRecList()[2];var originalType=record.getSymbolType();
        record.setSymbolType(com.interpss.dstab.datatype.CMLVarEnum.MachSpeed);
        p.compileRuntimePlan();assertFalse(p.hasCompiledStageEvaluator());
        p.invalidateRuntimePlan();record.setSymbolType(originalType);p.compileRuntimePlan();
        assertTrue(p.hasCompiledStageEvaluator());
        p.invalidateRuntimePlan();
        var child=(AbstractAnnotateController)block(p,"customBlock1").getField();
        var delay=block(child,"delayBlock");var original=delay.getField();
        int[] outputReads={0};
        var custom=new com.interpss.dstab.controller.cml.field.block.DelayControlBlock(1.,.1) {
            @Override public double getY(){outputReads[0]++;return super.getY();}
        };
        custom.initStateY0(0.);
        ((com.interpss.dstab.controller.cml.wrapper.ControlBlockFieldAnWrapper)delay).setField(custom);
        p.compileRuntimePlan();assertFalse(p.hasCompiledStageEvaluator());
        assertTrue(p.eulerStep(true,.001,fixture.machine));assertTrue(outputReads[0]>0);
        p.invalidateRuntimePlan();
        ((com.interpss.dstab.controller.cml.wrapper.ControlBlockFieldAnWrapper)delay)
                .setField((com.interpss.dstab.controller.cml.field.ICMLControlBlock)original);
        int[] limitReads={0};
        var customLimit=new com.interpss.dstab.datatype.LimitExpression(.2,-.2) {
            @Override public com.interpss.dstab.datatype.ExpCalculator getMaxExp() {
                limitReads[0]++;return super.getMaxExp();
            }
        };
        var field=com.interpss.dstab.controller.cml.field.block.DelayControlBlock.class.getDeclaredField("limit");
        field.setAccessible(true);field.set(original,customLimit);
        p.compileRuntimePlan();assertFalse(p.hasCompiledStageEvaluator());
        limitReads[0]=0;assertTrue(p.eulerStep(true,.001,fixture.machine));assertTrue(limitReads[0]>0);
    }

    @Test void sharedBlocksKeepRepeatedPreparationAndOriginalHistory(@TempDir Path root) throws Exception {
        var ordinary=load(false,true,root.resolve("shared-ordinary.dyr"));
        var compiled=load(true,true,root.resolve("shared-compiled.dyr"));
        for(var fixture:List.of(ordinary,compiled)) {
            var p=pss(fixture);p.invalidateRuntimePlan();
            var first=(com.interpss.dstab.controller.cml.wrapper.ControlBlockFieldAnWrapper)block(p,"filterBlock1");
            var second=(com.interpss.dstab.controller.cml.wrapper.ControlBlockFieldAnWrapper)block(p,"filterBlock2");
            second.setField(first.getField());p.compileRuntimePlan();
        }
        var field=AbstractAnnotateController.class.getDeclaredField("compiledStageEvaluator");field.setAccessible(true);
        var evaluator=(com.interpss.dstab.controller.cml.runtime.CmlCompiledStage)field.get(pss(compiled));
        assertNotNull(evaluator);assertTrue(evaluator.requiresRepeatedOutputPreparation());
        for(int step=0;step<50;step++)for(boolean predictor:new boolean[]{true,false}) {
            for(var fixture:List.of(ordinary,compiled)) {
                var p=pss(fixture);p.input1Signal=step*.002;p.input2Signal=(50-step)*.003;
                assertTrue(p.eulerStep(predictor,.001,fixture.machine));
            }
            assertEquals(snapshot(ordinary),snapshot(compiled));
        }
    }

    private static com.interpss.dstab.controller.cml.wrapper.BlockFieldAnWrapper<?> block(AbstractAnnotateController controller,String name) {
        return (com.interpss.dstab.controller.cml.wrapper.BlockFieldAnWrapper<?>)controller.getFieldWrapperList().stream()
                .filter(w->w.getFieldName().equals(name)).findFirst().orElseThrow();
    }

    private static List<Object> snapshot(Fixture f) throws Exception {
        var result=new ArrayList<Object>();
        result.add(f.machine.captureMechanicalIntegrationState());
        result.add(f.machine.captureRoundElectricalState());
        add(result,f.machine.getAngle(),f.machine.getSpeed(),f.machine.getEfd(),f.machine.getPe(),f.machine.getPm());
        for (var bus:f.algorithm.getNetwork().getBusList())
            add(result,bus.getVoltage().getReal(),bus.getVoltage().getImaginary());
        controllerSnapshot(result,f.exciter);
        if(f.machine.getStabilizer() instanceof AbstractAnnotateController pss)controllerSnapshot(result,pss);
        var previous=PsseStaggeredEulerSolver.class.getDeclaredField("previous");previous.setAccessible(true);
        add(result,(double[])previous.get(f.solver));
        return result;
    }

    private static void controllerSnapshot(List<Object> result,AbstractAnnotateController controller) {
        for(var wrapper:controller.getFieldWrapperList()) {
            if(wrapper.getField() instanceof AbstractAnnotateController child)controllerSnapshot(result,child);
            else if(wrapper.getField() instanceof CMLControlBlockAdapter block) {
                add(result,block.getU(),block.getStateX(),block.getY());
                if(block instanceof CMLControlBlock1stOrderAdapter first)add(result,first.getDX_dt());
                if(block instanceof com.interpss.dstab.controller.cml.field.block.FilterNthOrderBlock compound)
                    for(var inner:compound.getIntegrationBlocks())add(result,inner.getU(),inner.getStateX(),inner.getDX_dt());
                if(block instanceof org.interpss.dstab.control.pss.ieee.y1992.pss2a.Pss2aLeadLagBlock lag)
                    add(result,lag.getStateDerivative());
            }
        }
        add(result,controller.getOutput(controller.getMachine()));
    }

    private static void add(List<Object> values,double... samples) {
        for(double sample:samples) {
            assertTrue(Double.isFinite(sample));values.add(Double.doubleToRawLongBits(sample));
        }
    }

    private static Fixture load(boolean compiled,boolean pss,Path dynamicFile) throws Exception {
        IpssCorePlugin.init();Path base=Path.of("testData/adpter/psse/v33/SMIB");
        String records=Files.readString(base.resolve("SMIB_v33_genrou_esst4b.dyr"));
        if(pss) records+="\n"+Files.readAllLines(base.resolve("SMIB_v33_genrou_esst1a_pss2a.dyr")).stream()
                .filter(line->line.contains("'PSS2A'")).findFirst().orElseThrow()+"\n";
        Files.writeString(dynamicFile,records);
        var context=new PSSEMultiFileLoader().loadDStab(base.resolve("SMIB_v33.raw").toString(),dynamicFile.toString());
        var algorithm=context.getDynSimuAlgorithm();
        for(var bus:algorithm.getNetwork().getBusList())for(var gen:bus.getContributeGenList())
            if(gen.getDynamicGenDevice() instanceof com.interpss.dstab.mach.Machine m
                    && m.getStabilizer() instanceof org.interpss.dstab.control.pss.ieee.y1992.pss2a.Ieee1992PSS2AStabilizer p)
                p.setCompiledStageEnabled(compiled);
        assertTrue(algorithm.getAclfAlgorithm().loadflow());
        var solver=new PsseStaggeredEulerSolver(algorithm);solver.setCompiledControllerGraphsEnabled(true);
        solver.setCompiledSt4bBindingsEnabled(true);
        solver.setNetworkSolutionTolerance(1.e-5);solver.setReuseNetworkEndpoint(true);
        algorithm.setSolver(solver);algorithm.setSimuStepSec(1./240.);algorithm.setTotalSimuTimeSec(1.);
        algorithm.setSimuOutputHandler(new StateMonitor());
        var net=algorithm.getNetwork();
        net.addDynamicEvent(DStabObjectFactory.createBusFaultEvent("Bus1",net,
                SimpleFaultCode.GROUND_3P,new Complex(0.,.2),null,.2,.05),"BindingFault");
        assertTrue(algorithm.initialization());
        var machine=(RoundRotorMachineImpl)net.getMachine("Bus1-mach1");
        return new Fixture(algorithm,solver,machine,(IEEE2005ST4BExciter)machine.getExciter());
    }

    private record Fixture(DynamicSimuAlgorithm algorithm,PsseStaggeredEulerSolver solver,
            RoundRotorMachineImpl machine,IEEE2005ST4BExciter exciter) { }
}
