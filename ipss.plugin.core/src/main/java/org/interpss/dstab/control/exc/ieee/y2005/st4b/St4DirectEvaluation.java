package org.interpss.dstab.control.exc.ieee.y2005.st4b;

import java.util.*;
import com.interpss.dstab.controller.cml.ICMLMachineVoltageProvider;
import com.interpss.dstab.controller.cml.field.*;
import com.interpss.dstab.controller.cml.field.adapt.CMLControlBlockAdapter;
import com.interpss.dstab.controller.cml.field.block.DelayControlBlock;
import com.interpss.dstab.controller.cml.field.func.LowValueFunction;
import com.interpss.dstab.controller.cml.runtime.CmlBlockKernel;
import com.interpss.dstab.controller.cml.wrapper.*;

/** Experimental standard ST4 graph preserving state kernels and all refresh passes. */
final class St4DirectEvaluation {
    final IEEE2005ST4BExciter model;
    final Node[] nodes=new Node[6];
    final Node[] ordered;
    final double[] inputs=new double[6],states=new double[6],rates=new double[6];
    boolean refreshing;
    boolean scopedEndpoint,preparedStable;
    long endpointOutputReuses;
    // Exact block classes have no public setters for limit objects or expressions.
    // Numeric bounds remain live; every prepare pass checks bound block identity.
    private final boolean standardEndpointGraph;
    private boolean checkPreparedInputs;
    private static final Class<?> BRIDGE_TYPE=new IEEE2005ST4BExciter().customBlock.getClass();
    boolean endpointEligible(com.interpss.dstab.mach.Machine machine,boolean fixedPss) {
        if(machine!=model.getMachine() || !standardEndpointGraph || model.getClass()!=IEEE2005ST4BExciter.class || model.eNotificationRequired())return false;
        Class<?> type=machine.getClass();
        if(type!=com.interpss.dstab.mach.impl.RoundRotorMachineImpl.class
                && type!=com.interpss.dstab.mach.impl.SalientPoleMachineImpl.class
                && type!=org.interpss.dstab.mach.IeeeVcRoundRotorMachine.class
                && type!=org.interpss.dstab.mach.IeeeVcSalientPoleMachine.class)return false;
        var bus=machine.getDStabBus();
        if(((org.eclipse.emf.ecore.impl.BasicEObjectImpl)machine).eNotificationRequired()
                || bus.getClass()!=com.interpss.dstab.impl.DStabBusImpl.class
                || ((org.eclipse.emf.ecore.impl.BasicEObjectImpl)bus).eNotificationRequired()
                || bus.getVoltage().getClass()!=org.apache.commons.math3.complex.Complex.class)return false;
        if(machine.getStabilizer()!=null && (!fixedPss || machine.getStabilizer().getClass()!=
                org.interpss.dstab.control.pss.ieee.y1992.pss2a.Ieee1992PSS2AStabilizer.class))return false;
        return true;
    }
    private boolean standardGraph() {
        for(int i=0;i<6;i++) {
            var b=nodes[i].block;Class<?> expected=switch(i) {
                case 0,2->DelayControlBlock.class;
                case 1,4->Esst4bPIControlBlock.class;
                case 3->com.interpss.dstab.controller.cml.field.block.GainBlock.class;
                default->BRIDGE_TYPE;
            };
            if(b.getClass()!=expected || nodes[i].wrapper.getField()!=b)return false;
            if(b instanceof ICMLLimitBlock limited && limited.getLimit()!=null) {
                var limit=limited.getLimit();Class<?> limitType=limit.getClass();
                if(limitType!=org.interpss.numeric.datatype.LimitType.class
                        && limitType!=com.interpss.dstab.datatype.LimitExpression.class)return false;
                if(limitType==com.interpss.dstab.datatype.LimitExpression.class
                        && (((com.interpss.dstab.datatype.LimitExpression)limit).getMaxExp()!=null
                        || ((com.interpss.dstab.datatype.LimitExpression)limit).getMinExp()!=null))return false;
            }
        }
        return true;
    }
    double preparedEndpointOutput() {
        if(scopedEndpoint && preparedStable && algebraic()) {
            double value=0.+y(5);
            if(Double.isFinite(value)) {endpointOutputReuses++;return value;}
        }
        return output();
    }

    St4DirectEvaluation(IEEE2005ST4BExciter model) {
        this.model=model;var order=new ArrayList<Node>();boolean gateFound=false;
        for(var wrapper:model.getFieldWrapperList()) {
            if(wrapper.getFieldName().equals("voelGate")) {
                if(wrapper.getField().getClass()!=LowValueFunction.class)throw new IllegalStateException("Custom gate");
                gateFound=true;continue;
            }
            int id=switch(wrapper.getFieldName()) {
                case "trDelayBlock"->0;case "vrPIBlock"->1;case "taDelayBlock"->2;
                case "kgGainBlock"->3;case "vmPIBlock"->4;case "customBlock"->5;
                default->throw new IllegalStateException("Unexpected ST4 field");
            };
            Node node=new Node(id,(BlockFieldAnWrapper<?>)wrapper);nodes[id]=node;order.add(node);
        }
        if(!gateFound)throw new IllegalStateException("Missing gate");
        for(Node node:nodes)Objects.requireNonNull(node);
        order.sort(Comparator.comparingInt(n->n.wrapper.getInitOrder()));ordered=order.toArray(Node[]::new);
        standardEndpointGraph=standardGraph();
    }
    double y(int id) {return nodes[id].block.getY();}
    double input(int id) {
        switch(id) {
        case 0: {
            var machine=model.getMachine();
            return 0.+(machine instanceof ICMLMachineVoltageProvider p?p.getCmlMachineVoltage()
                :model.getDStabBus().getVoltage().abs()/machine.getVMultiFactor());
        }
        case 1: {
            double a=model.getRefPoint(),b=y(0);
            var machine=model.getMachine();
            double c=machine.getStabilizer()==null?0.:machine.getStabilizer().getOutput(machine),d=model.vuel;
            double sum=0.;sum+=a;sum+=-b;sum+=c;sum+=d;return sum;
        }
        case 2:return 0.+y(1);
        case 3:return 0.+y(5);
        case 4: {double a=y(2),b=y(3);double sum=0.;sum+=a;sum+=-b;return sum;}
        case 5: {
            double a=y(4),b=y(4),c=y(4),d=model.voel;
            double gate=c<d?c:d;
            double sum=0.;sum+=a;sum+=-b;sum+=gate;return sum;
        }
        default:throw new IllegalArgumentException();
        }
    }
    boolean step(boolean predictor,double dt) {
        prepare(predictor,true);
        for(Node node:ordered)if(node.control)node.kernel.advance(predictor,inputs[node.id],dt,null,null,states,rates,node.id);
        prepare(predictor,false);
        preparedStable=scopedEndpoint && predictor && dt==0.;
        checkPreparedInputs=preparedStable;
        try {prepare(predictor,false);}finally{checkPreparedInputs=false;}
        return true;
    }
    void prepare(boolean predictor,boolean capture) {
        for(Node node:ordered)node.prepare(predictor,capture);
        for(Node node:ordered)if(!node.control && node.wrapper.isFeedback())node.prepare(predictor,false);
    }
    boolean algebraic() {
        for(Node node:nodes)if(node.block instanceof DelayControlBlock d && d.getT()==0.)return true;
        return false;
    }
    double output() {
        if(!refreshing && algebraic()) {
            refreshing=true;
            try {
                double previous=Double.NaN;boolean converged=false;
                for(int i=0;i<64;i++) {
                    prepare(false,false);double value=0.+y(5);
                    if(Double.isFinite(value)&&Math.abs(value-previous)<=1.e-14*(1.+Math.abs(value))) {converged=true;break;}
                    previous=value;
                }
                if(!converged)return Double.NaN;
            }finally {refreshing=false;}
        }
        return 0.+y(5);
    }
    final class Node {
        final int id;final BlockFieldAnWrapper<?> wrapper;final ICMLStaticBlock block;
        final CmlBlockKernel kernel;final boolean control;
        Node(int id,BlockFieldAnWrapper<?> wrapper) {
            this.id=id;this.wrapper=wrapper;block=(ICMLStaticBlock)wrapper.getField();
            control=wrapper instanceof ControlBlockFieldAnWrapper;kernel=CmlBlockKernel.create(block);
            if(block instanceof ICMLLimitBlock limited && limited.getLimit()!=null &&
                (limited.getLimit().getMaxExp()!=null || limited.getLimit().getMinExp()!=null))
                throw new IllegalStateException("Variable limit requires general evaluator");
        }
        void prepare(boolean predictor,boolean capture) {
            if(wrapper.getField()!=block)throw new IllegalStateException("ST4 field replaced");
            double value=input(id);
            if(checkPreparedInputs)preparedStable &= Double.isFinite(value)
                && Double.doubleToRawLongBits(block.getU())==Double.doubleToRawLongBits(value);

            if(control) {((CMLControlBlockAdapter)block).setStageInput(value);if(capture)inputs[id]=value;}
            else if(predictor)block.eulerStep1(value,0.);else block.eulerStep2(value,0.);
        }
    }
}
