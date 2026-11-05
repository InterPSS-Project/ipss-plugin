package org.interpss.dstab.control.pss.psse.ieeest;

import java.util.*;
import com.interpss.dstab.controller.cml.runtime.*;
import com.interpss.dstab.controller.cml.field.*;
import com.interpss.dstab.controller.cml.field.adapt.CMLControlBlockAdapter;
import com.interpss.dstab.controller.cml.field.block.FilterControlBlock;
import com.interpss.dstab.controller.cml.wrapper.*;
import com.interpss.dstab.datatype.ExpCalculator;

/** Typed input wiring; existing block kernels retain all numerical state and history. */
final class IeeestDirectStage implements CmlCompiledStage {
    private final IeeestStabilizer model;
    private final Node[] nodes=new Node[7],ordered,feedback;
    private final double[] inputs=new double[7],states=new double[7],rates=new double[7];
    private record Node(int id,BlockFieldAnWrapper<?> wrapper,ICMLStaticBlock block,CmlBlockKernel kernel,boolean control) {}
    IeeestDirectStage(IeeestStabilizer model) {
        this.model=model;var order=new ArrayList<Node>();var feeds=new ArrayList<Node>();
        var prototype=new IeeestStabilizer("types",model.getData(),null);
        Class<?>[] types={prototype.busMeasurement.getClass(),SecondOrderLagBlock.class,SecondOrderLeadLagBlock.class,
                FilterControlBlock.class,FilterControlBlock.class,IeeestTerminalBlock.class,prototype.outputGate.getClass()};
        String[][] symbols={{"this.one"},{"this.speedGain","mach.speed","this.speedGain","this.speedRef","this.peGain","mach.pe","this.peGain","this.peRef","this.pmGain","mach.pm","this.pmGain","this.pmRef","this.busMeasurement.y"},
                {"this.secondOrderLag.y"},{"this.secondOrderLeadLag.y"},{"this.leadLag1.y"},{"this.ks","this.leadLag2.y"},{"this.washout.y"}};
        for(var wrapper:model.getFieldWrapperList()) {
            if(!(wrapper instanceof BlockFieldAnWrapper<?> w))throw new IllegalArgumentException("Non-block field");
            if(w.getClass()!=ControlBlockFieldAnWrapper.class&&w.getClass()!=StaticBlockFieldAnWrapper.class)throw new IllegalArgumentException("Custom wrapper");
            int id=switch(w.getFieldName()){case "busMeasurement"->0;case "secondOrderLag"->1;case "secondOrderLeadLag"->2;case "leadLag1"->3;case "leadLag2"->4;case "washout"->5;case "outputGate"->6;default->throw new IllegalArgumentException("Field");};
            if(nodes[id]!=null||w.getField().getClass()!=types[id])throw new IllegalArgumentException("Block type");
            var block=(ICMLStaticBlock)w.getField();
            if(block instanceof ICMLLimitBlock limited&&limited.getLimit()!=null&&(limited.getLimit().getMaxExp()!=null||limited.getLimit().getMinExp()!=null))throw new IllegalArgumentException("Expression limits");
            var records=w.getInputExp().getRecList();if(records.length!=symbols[id].length)throw new IllegalArgumentException("Input length");
            for(int i=0;i<records.length;i++) {
                var expected=id==1&&i<12?(i%2==1?ExpCalculator.Opt.Mul:i%4==2?ExpCalculator.Opt.Sub:ExpCalculator.Opt.Add):id==5&&i==1?ExpCalculator.Opt.Mul:ExpCalculator.Opt.Add;
                if(!records[i].getSymbolStr().equals(symbols[id][i])||records[i].opt!=expected)throw new IllegalArgumentException("Input wiring "+w.getFieldName()+" / "+i+" / "+records[i].getSymbolStr());
            }
            var n=new Node(id,w,block,CmlBlockKernel.create(block),w instanceof ControlBlockFieldAnWrapper);nodes[id]=n;order.add(n);if(!n.control&&w.isFeedback())feeds.add(n);
        }
        for(var n:nodes)Objects.requireNonNull(n);
        order.sort(Comparator.comparingInt(n->n.wrapper.getInitOrder()));ordered=order.toArray(Node[]::new);feedback=feeds.toArray(Node[]::new);
    }
    private double y(int id){return nodes[id].kernel.getAsDouble();}
    private double input(int id) {
        switch(id) {
        case 0:return 0.+model.one;
        case 1: {
            // Read every operand first, then retain the existing sign, product and sum order.
            var machine=model.getMachine();
            double a=model.speedGain,b=machine.getSpeed(),c=model.speedGain,d=model.speedRef,
                    e=model.peGain,f=machine.getPe(),g=model.peGain,h=model.peRef,
                    i=model.pmGain,j=machine.getPm(),k=model.pmGain,l=model.pmRef,m=y(0);
            c=-c;g=-g;k=-k;
            b=a*b;a=0.;d=c*d;c=0.;f=e*f;e=0.;h=g*h;g=0.;j=i*j;i=0.;l=k*l;k=0.;
            double sum=0.;sum+=a;sum+=b;sum+=c;sum+=d;sum+=e;sum+=f;sum+=g;sum+=h;sum+=i;sum+=j;sum+=k;sum+=l;sum+=m;return sum;
        }
        case 2:return 0.+y(1);
        case 3:return 0.+y(2);
        case 4:return 0.+y(3);
        case 5: {double a=model.ks,b=y(4);return 0.+(a*b);}
        case 6:return 0.+y(5);
        default:throw new IllegalArgumentException();
        }
    }
    private void prepare(Node node,boolean predictor,boolean capture) {
        double value=input(node.id);
        if(node.control) {
            ((CMLControlBlockAdapter)node.block).setStageInput(value);
            if(capture)inputs[node.id]=value;
        } else if(predictor)node.block.eulerStep1(value,0.);else node.block.eulerStep2(value,0.);
    }
    @Override public void prepare(boolean predictor,double dt,boolean capture) {
        for(var node:ordered)prepare(node,predictor,capture);
        for(var node:feedback)prepare(node,predictor,false);
    }
    @Override public void advance(boolean predictor,double dt) {
        for(var node:ordered)if(node.control)node.kernel.advance(predictor,inputs[node.id],dt,null,null,states,rates,node.id);
    }
    @Override public void release() {}
    @Override public boolean supportsObservationPreparation(){return true;}
}
