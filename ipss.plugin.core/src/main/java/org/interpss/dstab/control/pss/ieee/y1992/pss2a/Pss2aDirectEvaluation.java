package org.interpss.dstab.control.pss.ieee.y1992.pss2a;

import java.util.*;
import com.interpss.dstab.controller.cml.annotate.AbstractAnnotateController;
import com.interpss.dstab.controller.cml.field.*;
import com.interpss.dstab.controller.cml.field.adapt.CMLControlBlockAdapter;
import com.interpss.dstab.controller.cml.field.block.*;
import com.interpss.dstab.controller.cml.runtime.CmlBlockKernel;
import com.interpss.dstab.controller.cml.wrapper.*;

/** Fixed standard graph experiment; block storage and kernels remain authoritative. */
final class Pss2aDirectEvaluation {
    final Ieee1992PSS2AStabilizer model;
    final Node[] nodes=new Node[10];
    final Node[] ordered;
    final Child first,second;
    final Entry[] entries;
    final Node[] captured=new Node[10];
    final double[] inputs=new double[10],states=new double[10],rates=new double[10];
    int capturedCount;
    boolean refreshing;
    boolean forwardOutput;
    final boolean singleRefresh;
    final boolean scalarPreparation;
    long singleRefreshCount;
    final Pss2aNumericFrame numericFrame;
    long numericStages;

    Pss2aDirectEvaluation(Ieee1992PSS2AStabilizer model) {
        this(model,!Boolean.getBoolean("ipss.experiment.disablePssScalarPreparation"));
    }
    Pss2aDirectEvaluation(Ieee1992PSS2AStabilizer model,boolean allowScalarPreparation) {
        this(model,allowScalarPreparation,Boolean.getBoolean("ipss.experiment.pssNumericFrame"));
    }
    Pss2aDirectEvaluation(Ieee1992PSS2AStabilizer model,boolean allowScalarPreparation,boolean useNumericFrame) {
        this.model=model;
        first=new Child(model.customBlock1,0);second=new Child(model.customBlock2,3);
        var order=new ArrayList<Entry>();var parentNodes=new ArrayList<Node>();
        for(var wrapper:model.getFieldWrapperList()) {
            if(wrapper.getFieldName().equals("customBlock1")) {
                if(wrapper.getField()!=model.customBlock1)throw new IllegalStateException("Child replaced");
                order.add(new Entry((BlockFieldAnWrapper<?>)wrapper,first,null));continue;
            }
            if(wrapper.getFieldName().equals("customBlock2")) {
                if(wrapper.getField()!=model.customBlock2)throw new IllegalStateException("Child replaced");
                order.add(new Entry((BlockFieldAnWrapper<?>)wrapper,second,null));continue;
            }
            int id=switch(wrapper.getFieldName()) {
                case "filterNthBlock" -> 6;case "filterBlock1" -> 7;
                case "filterBlock2" -> 8;case "outputBlock" -> 9;
                default -> throw new IllegalStateException("Unexpected PSS2A field");
            };
            Node node=new Node(id,(BlockFieldAnWrapper<?>)wrapper);nodes[id]=node;
            parentNodes.add(node);order.add(new Entry(node.wrapper,null,node));
        }
        order.sort(Comparator.comparingInt(e->e.wrapper.getInitOrder()));
        entries=order.toArray(Entry[]::new);ordered=parentNodes.toArray(Node[]::new);
        for(Node node:nodes)Objects.requireNonNull(node);
        forwardOutput=first.forwardOutput && second.forwardOutput && entries.length==6;
        for(int i=0;i<entries.length;i++)
            forwardOutput &= i<2 ? entries[i].child!=null : entries[i].node!=null && entries[i].node.id==i+4;
        Class<?>[] types={WashoutControlBlock.class,Pss2aWashoutBlock.class,DelayControlBlock.class,
            WashoutControlBlock.class,Pss2aWashoutBlock.class,DelayControlBlock.class,
            FilterNthOrderBlock.class,FilterControlBlock.class,FilterControlBlock.class,Pss2aLeadLagBlock.class};
        boolean standard=first.model.getClass()==Ieee1992PSS2AStabilizer.CustomExciter.class
            && second.model.getClass()==Ieee1992PSS2AStabilizer.CustomExciter.class;
        for(int i=0;i<types.length;i++)standard &= nodes[i].block.getClass()==types[i];
        singleRefresh=forwardOutput && standard && !Boolean.getBoolean("ipss.experiment.disablePssSingleRefresh");
        scalarPreparation=forwardOutput && standard && allowScalarPreparation;
        numericFrame=useNumericFrame
            ?Pss2aNumericFrame.create(this):null;
        System.err.println("PSS output forward traversal eligible: "+forwardOutput);

        model.customBlock1.directOutput=first::output;
        model.customBlock2.directOutput=second::output;
    }
    record Entry(BlockFieldAnWrapper<?> wrapper,Child child,Node node) {}
    double y(int id) {
        return switch(id) {
            case 0,3 -> ((WashoutControlBlock)nodes[id].block).getY();
            case 1,4 -> ((Pss2aWashoutBlock)nodes[id].block).getY();
            case 2,5 -> ((DelayControlBlock)nodes[id].block).getY();
            case 6 -> ((FilterNthOrderBlock)nodes[id].block).getY();
            case 7,8 -> ((FilterControlBlock)nodes[id].block).getY();
            case 9 -> ((Pss2aLeadLagBlock)nodes[id].block).getY();
            default -> throw new IllegalArgumentException();
        };
    }
    double input(int id) {
        if(id<6) {
            Child child=id<3?first:second;
            int local=id%3;
            if(local==0) {double a=child.model.getRuntimeInputValue(),b=child.model.getRefPoint();return (0.+a)+(-b);}
            return 0.+y(id-1);
        }
        switch(id) {
        case 6: {
            double a=first.model.getRuntimeOutputValue(),b=model.ks3,c=second.model.getRuntimeOutputValue();
            double sum=0.;sum+=a;sum+=0.;sum+=b*c;return sum;
        }
        case 7: {
            double a=model.getRefPoint(),b=y(6),c=model.ks4,d=second.model.getRuntimeOutputValue();
            double sum=0.;sum+=a;sum+=b;sum+=0.;sum+=(-c)*d;return sum;
        }
        case 8:return 0.+y(7);
        case 9:return 0.+y(8);
        default:throw new IllegalArgumentException();
        }
    }
    boolean step(boolean predictor,double dt) {
        if(numericFrame!=null && numericFrame.eligible()) {
            numericFrame.step(predictor,dt);numericStages++;return true;
        }
        capturedCount=0;
        prepare(true);
        for(int i=0;i<capturedCount;i++)
            captured[i].kernel.advance(predictor,inputs[i],dt,null,null,states,rates,i);
        prepare(false);
        if(singleRefresh)singleRefreshCount++;else prepare(false);
        return true;
    }
    void prepare(boolean capture) {
        if(scalarPreparation) {prepareScalar(capture);return;}
        for(Entry entry:entries) {
            if(entry.child!=null) {
                Child child=entry.child;
                if(entry.wrapper.getField()!=child.model)throw new IllegalStateException("PSS2A child replaced");
                child.model.setRuntimeInputValue(0.+(child==first?model.input1Signal:model.input2Signal));
                child.prepare(capture);
                child.model.setRuntimeOutputValue(child.preparedOutput());
            } else entry.node.prepare(capture);
        }
    }
    /** Same ordered equations and operand reads as input(), without per-node dispatch. */
    private void prepareScalar(boolean capture) {
        prepareChild(entries[0],capture);
        prepareChild(entries[1],capture);
        nodes[6].checkBinding();
        double a=first.model.getRuntimeOutputValue(),b=model.ks3,c=second.model.getRuntimeOutputValue();
        double sum=0.;sum+=a;sum+=0.;sum+=b*c;
        nodes[6].accept(sum,capture);
        nodes[7].checkBinding();
        a=model.getRefPoint();b=((FilterNthOrderBlock)nodes[6].block).getY();
        c=model.ks4;double d=second.model.getRuntimeOutputValue();
        sum=0.;sum+=a;sum+=b;sum+=0.;sum+=(-c)*d;
        nodes[7].accept(sum,capture);
        nodes[8].checkBinding();
        nodes[8].accept(0.+((FilterControlBlock)nodes[7].block).getY(),capture);
        nodes[9].checkBinding();
        nodes[9].accept(0.+((FilterControlBlock)nodes[8].block).getY(),capture);
    }
    private void prepareChild(Entry entry,boolean capture) {
        Child child=entry.child;
        if(entry.wrapper.getField()!=child.model)throw new IllegalStateException("PSS2A child replaced");
        child.model.setRuntimeInputValue(0.+(child==first?model.input1Signal:model.input2Signal));
        Node n0=child.ordered[0],n1=child.ordered[1],n2=child.ordered[2];
        n0.checkBinding();
        double a=child.model.getRuntimeInputValue(),b=child.model.getRefPoint();
        n0.accept((0.+a)+(-b),capture);
        n1.checkBinding();
        n1.accept(0.+((WashoutControlBlock)n0.block).getY(),capture);
        n2.checkBinding();
        n2.accept(0.+((Pss2aWashoutBlock)n1.block).getY(),capture);
        child.model.setRuntimeOutputValue(child.preparedOutput());
    }
    boolean algebraic() {
        for(Node node:nodes)if(node.algebraic())return true;
        return false;
    }
    double output() {
        if(!refreshing && forwardOutput && algebraic()) {
            refreshing=true;
            try {prepare(false);double value=0.+y(9);return Double.isFinite(value)?value:Double.NaN;}
            finally {refreshing=false;}
        }
        if(!refreshing && algebraic()) {
            refreshing=true;
            try {
                double previous=Double.NaN;boolean converged=false;
                for(int i=0;i<64;i++) {
                    prepare(false);double value=0.+y(9);
                    if(Double.isFinite(value)&&Math.abs(value-previous)<=1.e-14*(1.+Math.abs(value))) {converged=true;break;}
                    previous=value;
                }
                if(!converged)return Double.NaN;
            }finally {refreshing=false;}
        }
        return 0.+y(9);
    }
    final class Child {
        final Ieee1992PSS2AStabilizer.CustomExciter model;
        final Node[] ordered;
        final int base;
        boolean refreshing;
        final boolean forwardOutput;
        Child(Ieee1992PSS2AStabilizer.CustomExciter model,int base) {
            this.model=model;this.base=base;var order=new ArrayList<Node>();
            for(var wrapper:model.getFieldWrapperList()) {
                int local=switch(wrapper.getFieldName()) {
                    case "washoutBlock1" -> 0;case "washoutBlock2" -> 1;case "delayBlock" -> 2;
                    default -> throw new IllegalStateException("Unexpected PSS2A child field");
                };
                Node node=new Node(base+local,(BlockFieldAnWrapper<?>)wrapper);
                nodes[node.id]=node;order.add(node);
            }
            order.sort(Comparator.comparingInt(n->n.wrapper.getInitOrder()));ordered=order.toArray(Node[]::new);
            forwardOutput=ordered.length==3&&ordered[0].id==base&&ordered[1].id==base+1&&ordered[2].id==base+2;

        }
        void prepare(boolean capture) {for(Node node:ordered)node.prepare(capture);}
        double preparedOutput() {
            if(!forwardOutput)return output();
            // The ordered child has just evaluated every input at this stage.
            // Standard child getters have no history updates; retain the finite check.
            boolean checkFinite=!refreshing && algebraic();
            double value=0.+y(base+2);
            return checkFinite && !Double.isFinite(value)?Double.NaN:value;
        }

        boolean algebraic() {for(Node node:ordered)if(node.algebraic())return true;return false;}
        double output() {
            if(!refreshing && forwardOutput && algebraic()) {
                refreshing=true;
                try {prepare(false);double value=0.+y(base+2);return Double.isFinite(value)?value:Double.NaN;}
                finally {refreshing=false;}
            }
            if(!refreshing && algebraic()) {
                refreshing=true;
                try {
                    double previous=Double.NaN;boolean converged=false;
                    for(int i=0;i<64;i++) {
                        prepare(false);double value=0.+y(base+2);
                        if(Double.isFinite(value)&&Math.abs(value-previous)<=1.e-14*(1.+Math.abs(value))) {converged=true;break;}
                        previous=value;
                    }
                    if(!converged)return Double.NaN;
                }finally {refreshing=false;}
            }
            return 0.+y(base+2);
        }
    }
    final class Node {
        final int id;
        final BlockFieldAnWrapper<?> wrapper;
        final CMLControlBlockAdapter block;
        final CmlBlockKernel kernel;
        Node(int id,BlockFieldAnWrapper<?> wrapper) {
            this.id=id;this.wrapper=wrapper;this.block=(CMLControlBlockAdapter)wrapper.getField();
            kernel=CmlBlockKernel.create(block);
            if(wrapper.isFeedback())throw new IllegalStateException("Unexpected PSS2A feedback");
            if(block instanceof ICMLLimitBlock limited && limited.getLimit()!=null &&
                (limited.getLimit().getMaxExp()!=null || limited.getLimit().getMinExp()!=null))
                throw new IllegalStateException("Variable limit requires general evaluator");
        }
        void prepare(boolean capture) {
            checkBinding();
            accept(input(id),capture);
        }
        void checkBinding() {
            if(wrapper.getField()!=block)throw new IllegalStateException("PSS2A field replaced");
        }
        void accept(double value,boolean capture) {
            block.setStageInput(value);
            if(capture) {captured[capturedCount]=this;inputs[capturedCount++]=value;}
        }
        boolean algebraic() {
            return block instanceof DelayControlBlock d && d.getT()==0.
                || block instanceof WashoutControlBlock w && w.getT()==0.
                || block instanceof FilterControlBlock f && f.getT2()==0.
                || block instanceof FilterNthOrderBlock n && (n.getN()==0 || n.getT2()==0.);
        }
    }
}
