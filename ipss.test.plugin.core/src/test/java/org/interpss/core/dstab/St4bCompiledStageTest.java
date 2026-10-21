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
class St4bCompiledStageTest {
    @Test
    void coupledStatesAndHistoryMatchAcrossFaultsAndLiveControls(@TempDir Path temporary) throws Exception {
        for (boolean pss : new boolean[]{false,true}) {
            var ordinary=load(false,pss,temporary.resolve("ordinary-"+pss+".dyr"));
            var compiled=load(true,pss,temporary.resolve("compiled-"+pss+".dyr"));
            assertTrue(ordinary.exciter.isRuntimePlanCompiled());
            assertFalse(ordinary.exciter.hasCompiledStageEvaluator());
            assertTrue(compiled.exciter.hasCompiledStageEvaluator());
            assertTrue(compiled.exciter.isRuntimePlanCompiled());
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

    @Test void predictorCorrectorAndRepeatedObservationsMatch(@TempDir Path root) throws Exception {
        var ordinary=load(false,true,root.resolve("direct-off.dyr"));
        var compiled=load(true,true,root.resolve("direct-on.dyr"));
        long start=compiled.exciter.getCompiledStageEvaluationCount();
        for(int step=0;step<100;step++)for(boolean predictor:new boolean[]{true,false}) {
            for(var f:List.of(ordinary,compiled)) {
                f.exciter.setRefPoint(.98+Math.sin(step*.13)*.04);
                f.exciter.setVuel(step%3==0?.02:0.);
                f.exciter.setVoel(step%4==0?.1:Double.POSITIVE_INFINITY);
                var delay=block(f.exciter,"taDelayBlock").getField();
                var time=delay.getClass().getDeclaredField("t");time.setAccessible(true);
                time.setDouble(delay,step%2==0?0.:.03);
                assertTrue(f.exciter.eulerStep(predictor,.001,f.machine));
                assertEquals(AbstractAnnotateController.RuntimePhase.IDLE,f.exciter.getRuntimePhase());
            }
            assertEquals(snapshot(ordinary),snapshot(compiled),"Direct stage "+step+" "+predictor);
            for(int observation=0;observation<3;observation++) {
                var before=dynamicHistory(compiled.exciter);
                for(var f:List.of(ordinary,compiled)) {
                    f.machine.getParentGen().getParentBus().setVoltage(new Complex(.95+observation*.02,.01));
                    assertTrue(Double.isFinite(f.exciter.getOutput(f.machine)));
                }
                assertEquals(before,dynamicHistory(compiled.exciter),"Observation must retain state and derivatives");
                assertEquals(snapshot(ordinary),snapshot(compiled));
            }
        }
        assertEquals(200,compiled.exciter.getCompiledStageEvaluationCount()-start);
    }

    @Test void alteredInputsCustomBlocksAndLimitsUseGenericEvaluation(@TempDir Path root) throws Exception {
        var f=load(true,false,root.resolve("fallback.dyr"));var e=f.exciter;
        assertThrows(IllegalStateException.class,()->e.setCompiledStageEnabled(false));
        e.invalidateRuntimePlan();
        var wrapper=block(e,"vrPIBlock");var input=wrapper.getInputExp();
        var changed=new com.interpss.dstab.datatype.ExpCalculator("this.refPoint-this.trDelayBlock.y");
        com.interpss.dstab.controller.cml.annotate.util.CMLSymbolMapper.mapExpressionSymbolStr2Type(changed.getRecList());
        wrapper.setInputExp(changed);e.compileRuntimePlan();assertFalse(e.hasCompiledStageEvaluator());
        e.invalidateRuntimePlan();wrapper.setInputExp(input);e.compileRuntimePlan();assertTrue(e.hasCompiledStageEvaluator());
        e.invalidateRuntimePlan();
        var gate=(com.interpss.dstab.controller.cml.wrapper.FunctionFieldAnWrapper<?>)e.getFieldWrapperList().stream()
                .filter(w->w.getFieldName().equals("voelGate")).findFirst().orElseThrow();
        var originalInputs=gate.getInputs();gate.setInputs(new String[]{"this.voel","this.vmPIBlock.y"});
        e.compileRuntimePlan();assertFalse(e.hasCompiledStageEvaluator());
        e.invalidateRuntimePlan();gate.setInputs(originalInputs);e.compileRuntimePlan();assertTrue(e.hasCompiledStageEvaluator());
        e.invalidateRuntimePlan();
        var delay=(com.interpss.dstab.controller.cml.wrapper.ControlBlockFieldAnWrapper)block(e,"trDelayBlock");
        var original=delay.getField();int[] reads={0};
        var custom=new com.interpss.dstab.controller.cml.field.block.DelayControlBlock(1.,.1) {
            @Override public double getY(){reads[0]++;return super.getY();}
        };
        custom.initStateY0(1.);delay.setField(custom);e.compileRuntimePlan();assertFalse(e.hasCompiledStageEvaluator());
        reads[0]=0;assertTrue(e.eulerStep(true,.001,f.machine));assertTrue(reads[0]>0);
        e.invalidateRuntimePlan();delay.setField(original);
        var limit=new com.interpss.dstab.datatype.LimitExpression(2.,-2.) {
            @Override public com.interpss.dstab.datatype.ExpCalculator getMaxExp(){reads[0]++;return super.getMaxExp();}
        };
        var field=original.getClass().getDeclaredField("limit");field.setAccessible(true);field.set(original,limit);
        e.compileRuntimePlan();assertFalse(e.hasCompiledStageEvaluator());
        reads[0]=0;assertTrue(e.eulerStep(true,.001,f.machine));assertTrue(reads[0]>0);
    }

    @Test void sharedBlocksKeepOrderedFeedbackAndHistory(@TempDir Path root) throws Exception {
        var ordinary=load(false,false,root.resolve("shared-off.dyr"));
        var compiled=load(true,false,root.resolve("shared-on.dyr"));
        for(var f:List.of(ordinary,compiled)) {
            var e=f.exciter;e.invalidateRuntimePlan();
            var first=(com.interpss.dstab.controller.cml.wrapper.ControlBlockFieldAnWrapper)block(e,"vrPIBlock");
            var second=(com.interpss.dstab.controller.cml.wrapper.ControlBlockFieldAnWrapper)block(e,"vmPIBlock");
            second.setField(first.getField());e.compileRuntimePlan();
        }
        assertTrue(compiled.exciter.hasCompiledStageEvaluator());
        for(int step=0;step<30;step++)for(boolean predictor:new boolean[]{true,false}) {
            for(var f:List.of(ordinary,compiled)) {
                f.exciter.setRefPoint(1.+step*.0001);
                assertTrue(f.exciter.eulerStep(predictor,.0001,f.machine));
            }
            assertEquals(snapshot(ordinary),snapshot(compiled));
        }
    }

    private static com.interpss.dstab.controller.cml.wrapper.BlockFieldAnWrapper<?> block(AbstractAnnotateController e,String name) {
        return (com.interpss.dstab.controller.cml.wrapper.BlockFieldAnWrapper<?>)e.getFieldWrapperList().stream()
                .filter(w->w.getFieldName().equals(name)).findFirst().orElseThrow();
    }
    private static List<Object> dynamicHistory(AbstractAnnotateController e) {
        var values=new ArrayList<Object>();
        for(var wrapper:e.getFieldWrapperList())if(wrapper.getField() instanceof CMLControlBlock1stOrderAdapter b)
            add(values,b.getStateX(),b.getDX_dt());
        return values;
    }

    @Test
    void publicParameterBindingsStayLiveAndCustomProvidersRemainVirtual() throws Exception {
        var exciter=new IEEE2005ST4BExciter();
        var uel=exciter.bindCompiledParameter("vuel");
        var oel=exciter.bindCompiledParameter("voel");
        exciter.setVuel(.123); exciter.setVoel(.456);
        assertEquals(.123,uel.getAsDouble()); assertEquals(.456,oel.getAsDouble());
        exciter.setVuel(-.2);exciter.clearVoel();
        assertEquals(-.2,uel.getAsDouble());assertEquals(Double.POSITIVE_INFINITY,oel.getAsDouble());
        var custom=new IEEE2005ST4BExciter() {
            @Override public double getDoubleField(String name) throws com.interpss.common.exp.InterpssException {
                return name.equals("vuel") ? vuel+7. : super.getDoubleField(name);
            }
        };
        var binding=custom.bindCompiledParameter("vuel");custom.setVuel(.5);
        assertEquals(7.5,binding.getAsDouble());custom.setVuel(1.);
        assertEquals(8.,binding.getAsDouble());
    }

    @Test
    void invalidatingACompiledStructureRequiresSolverReinitialization(@TempDir Path temporary) throws Exception {
        var f=load(true,false,temporary.resolve("invalidated.dyr"));
        assertTrue(f.algorithm.solveDEqnStep(true));
        f.exciter.invalidateRuntimePlan();
        assertFalse(f.algorithm.solveDEqnStep(true));
        f.algorithm.getNetwork().setDStabNetInitialized(false);
        assertTrue(f.algorithm.getAclfAlgorithm().loadflow());
        assertTrue(f.algorithm.initialization());
        assertTrue(f.exciter.isRuntimePlanCompiled());
        assertTrue(f.algorithm.solveDEqnStep(true));
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

    private static void controllerSnapshot(List<Object> result,AbstractAnnotateController controller) throws Exception {
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
        if (controller instanceof IEEE2005ST4BExciter st4) {
            var field=st4.customBlock.getClass().getDeclaredField("VB");field.setAccessible(true);
            add(result,field.getDouble(st4.customBlock));
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
        var algorithm=context.getDynSimuAlgorithm();assertTrue(algorithm.getAclfAlgorithm().loadflow());
        var solver=new PsseStaggeredEulerSolver(algorithm);solver.setCompiledControllerGraphsEnabled(true);
        solver.setCompiledSt4bBindingsEnabled(true);
        ((IEEE2005ST4BExciter)algorithm.getNetwork().getMachine("Bus1-mach1").getExciter()).setCompiledStageEnabled(compiled);
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
