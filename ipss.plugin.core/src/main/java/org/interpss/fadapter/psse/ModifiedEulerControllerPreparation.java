package org.interpss.fadapter.psse;

import org.interpss.dstab.algo.DefaultDynamicSimulationSolver;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import com.interpss.common.exp.InterpssException;
import com.interpss.dstab.algo.DynamicSimuAlgorithm;
import com.interpss.dstab.controller.cml.annotate.AbstractAnnotateController;
import com.interpss.dstab.controller.cml.annotate.AnController;
import com.interpss.dstab.controller.cml.annotate.AnControllerField;
import com.interpss.dstab.controller.cml.annotate.AnFunctionField;
import com.interpss.dstab.controller.cml.annotate.util.CMLSymbolMapper;
import com.interpss.dstab.controller.cml.field.ICMLLimitBlock;
import com.interpss.dstab.controller.cml.wrapper.BlockFieldAnWrapper;
import com.interpss.dstab.controller.cml.wrapper.FunctionFieldAnWrapper;
import com.interpss.dstab.datatype.ExpCalculator;
import org.interpss.dstab.control.exc.ieee.y1981.st1.IEEE1981ST1Exciter;
import org.interpss.dstab.control.exc.ieee.y2005.st4b.IEEE2005ST4BExciter;
import org.interpss.dstab.control.pss.ieee.y1992.pss2a.Ieee1992PSS2AStabilizer;
import org.interpss.dstab.control.pss.psse.ieeest.IeeestStabilizer;

/** Prepare audited controller graphs once, retaining compatible custom dispatch. */
final class ModifiedEulerControllerPreparation {
    private final List<AbstractAnnotateController> owned=new ArrayList<>();

    void clear() {
        for(var controller:owned) {
            clearDirect(controller);
            controller.invalidateRuntimePlan();
            controller.setRuntimeExpressionBindingsEnabled(false);
        }
        owned.clear();
    }

    DefaultDynamicSimulationSolver.ControllerPreparationReport prepare(DynamicSimuAlgorithm algorithm) {
        int compiled=0,st1=0,st4=0,pss=0,ieeest=0,compatible=0;
        var seen=java.util.Collections.newSetFromMap(new java.util.IdentityHashMap<Object,Boolean>());
        var graphs=new java.util.IdentityHashMap<AbstractAnnotateController,Boolean>();
        java.util.function.Predicate<AbstractAnnotateController> standard=
            model->graphs.computeIfAbsent(model,ModifiedEulerControllerPreparation::standardGraph);
        var network=algorithm.getNetwork();
        for(var bus:network.getBusList())for(var generator:bus.getContributeGenList()) {
            if(!(generator.getDynamicGenDevice() instanceof com.interpss.dstab.mach.Machine machine))continue;
            boolean owner=network.getClass()==com.interpss.dstab.impl.DStabilityNetworkImpl.class
                && network.eAdapters().isEmpty()&&bus.getClass()==com.interpss.dstab.impl.DStabBusImpl.class
                &&bus.eAdapters().isEmpty()&&machine.eAdapters().isEmpty()
                &&Arrays.asList(machine.getExciter(),machine.getStabilizer(),machine.getGovernor()).stream()
                    .allMatch(c->c==null||(c.eAdapters().isEmpty()&&BusFrequencySelection.isAuditedController(c)
                        &&(!(c instanceof AbstractAnnotateController graph)||standard.test(graph))))
                && (machine.getClass()==org.interpss.dstab.mach.IeeeVcRoundRotorMachine.class
                    ||machine.getClass()==org.interpss.dstab.mach.IeeeVcSalientPoleMachine.class);
            // Include inactive attachments, which may become active after initialization.
            for(var candidate:Arrays.asList(machine.getExciter(),machine.getStabilizer(),machine.getGovernor())) {
                if(candidate==null||!seen.add(candidate))continue;
                if(!(candidate instanceof AbstractAnnotateController controller)||!owner
                        ||!audited(controller)||!standard.test(controller)) {compatible++;continue;}
                // Respect an explicitly prepared caller-owned plan.
                if(controller.isRuntimePlanCompiled()){compatible++;continue;}
                try {
                    if(controller instanceof IeeestStabilizer model)model.setPreparedDirectStage(true);
                    controller.compileRuntimePlan();
                    if(controller instanceof IEEE1981ST1Exciter model){model.enableModifiedEulerEvaluation();st1++;}
                    else if(controller instanceof IEEE2005ST4BExciter model){model.enableDirectEvaluationExperiment();st4++;}
                    else if(controller instanceof Ieee1992PSS2AStabilizer model){model.enableDirectEvaluationExperiment();pss++;}
                    else if(controller instanceof IeeestStabilizer&&controller.hasCompiledStageEvaluator())ieeest++;
                    owned.add(controller);compiled++;
                } catch(InterpssException|IllegalArgumentException|IllegalStateException unsupported) {
                    clearDirect(controller);controller.invalidateRuntimePlan();
                    controller.setRuntimeExpressionBindingsEnabled(false);compatible++;
                }
            }
        }
        return new DefaultDynamicSimulationSolver.ControllerPreparationReport(compiled,st1,st4,pss,ieeest,compatible);
    }

    private static boolean audited(AbstractAnnotateController model) {
        return BusFrequencySelection.isAuditedController(model);
    }
    private static void clearDirect(AbstractAnnotateController model) {
        if(model instanceof IEEE1981ST1Exciter controller)controller.clearDirectEvaluation();
        else if(model instanceof IEEE2005ST4BExciter controller)controller.clearDirectEvaluation();
        else if(model instanceof Ieee1992PSS2AStabilizer controller)controller.clearDirectEvaluation();
    }

    /** Reject replaced equations, blocks, children, callbacks and observed controllers. */
    private static boolean standardGraph(AbstractAnnotateController model) {
        if(!model.eAdapters().isEmpty()||model.getFieldWrapperList()==null)return false;
        try {
            AnController annotation=model.getClass().getAnnotation(AnController.class);
            if(annotation==null||!sameExpression(model.getInputExp(),annotation.input())
                    ||!sameExpression(model.getOutputExp(),annotation.output()))return false;
            Set<String> names=new HashSet<>();
            for(var wrapper:model.getFieldWrapperList()) {
                Class<?> wrapperType=wrapper.getClass();
                if(wrapperType!=com.interpss.dstab.controller.cml.wrapper.ControlBlockFieldAnWrapper.class
                        &&wrapperType!=com.interpss.dstab.controller.cml.wrapper.StaticBlockFieldAnWrapper.class
                        &&wrapperType!=com.interpss.dstab.controller.cml.wrapper.ControllerFieldAnWrapper.class
                        &&wrapperType!=FunctionFieldAnWrapper.class)return false;
                if((wrapper instanceof BlockFieldAnWrapper<?> holder&&holder.isDebug())
                        ||!names.add(wrapper.getFieldName()))return false;
                Field field=findField(model.getClass(),wrapper.getFieldName());
                if(!field.trySetAccessible())return false;
                Object configured=field.get(model),value=wrapper.getField();
                if(value==null||(configured!=null&&configured!=value))return false;
                if(value.getClass()!=field.getType()) {
                    if(value.getClass().getEnclosingClass()!=model.getClass())return false;
                    Field enclosing=value.getClass().getDeclaredField("this$0");
                    if(!enclosing.trySetAccessible()||enclosing.get(value)!=model)return false;
                }
                if(value instanceof ICMLLimitBlock limited&&limited.getLimit()!=null
                        &&(limited.getLimit().getClass()!=com.interpss.dstab.datatype.LimitExpression.class
                            ||limited.getLimit().getMaxExp()!=null||limited.getLimit().getMinExp()!=null))return false;
                AnControllerField block=field.getAnnotation(AnControllerField.class);
                if(block!=null) {
                    if(!(wrapper instanceof BlockFieldAnWrapper<?> holder)
                            ||!sameExpression(holder.getInputExp(),block.input())
                            ||wrapper.isFeedback()!=block.feedback())return false;
                } else {
                    AnFunctionField function=field.getAnnotation(AnFunctionField.class);
                    if(function==null||!(wrapper instanceof FunctionFieldAnWrapper<?> holder)
                            ||!Arrays.equals(holder.getInputs(),function.input()))return false;
                }
                if(value instanceof AbstractAnnotateController child) {
                    if(!(model instanceof Ieee1992PSS2AStabilizer parent)
                            ||!parent.isBuiltInSignalConditioner(child)||!standardGraph(child))return false;
                }
            }
            int fields=0;
            for(Class<?> type=model.getClass();type!=null;type=type.getSuperclass())
                for(Field field:type.getDeclaredFields())
                    if(field.isAnnotationPresent(AnControllerField.class)||field.isAnnotationPresent(AnFunctionField.class))fields++;
            return names.size()==fields;
        }catch(Exception unsupported){return false;}
    }
    private static Field findField(Class<?> type,String name) throws NoSuchFieldException {
        for(Class<?> owner=type;owner!=null;owner=owner.getSuperclass()) {
            try{return owner.getDeclaredField(name);}catch(NoSuchFieldException absent){ }
        }
        throw new NoSuchFieldException(name);
    }
    private static boolean sameExpression(ExpCalculator actual,String expected) throws InterpssException {
        if(actual==null)return expected.isEmpty();
        var expression=new ExpCalculator(expected);
        CMLSymbolMapper.mapExpressionSymbolStr2Type(expression.getRecList());
        var left=actual.getRecList();var right=expression.getRecList();
        if(left.length!=right.length)return false;
        for(int i=0;i<left.length;i++)
            if(left[i].opt!=right[i].opt||left[i].getSymbolType()!=right[i].getSymbolType()
                    ||!java.util.Objects.equals(left[i].getSymbolStr(),right[i].getSymbolStr()))return false;
        return true;
    }
}
