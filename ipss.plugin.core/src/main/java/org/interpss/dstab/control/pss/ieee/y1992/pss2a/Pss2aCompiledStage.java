package org.interpss.dstab.control.pss.ieee.y1992.pss2a;

import java.util.*;
import com.interpss.dstab.controller.cml.runtime.*;
import com.interpss.dstab.controller.cml.field.*;
import com.interpss.dstab.controller.cml.field.adapt.CMLControlBlockAdapter;
import com.interpss.dstab.controller.cml.field.block.*;
import com.interpss.dstab.controller.cml.wrapper.*;
import com.interpss.dstab.datatype.ExpCalculator;

/** Fixed standard signal wiring; block equations and storage remain authoritative. */
final class Pss2aCompiledStage implements CmlCompiledStage {
    private final Ieee1992PSS2AStabilizer model;
    private final Node[] nodes=new Node[10], captured=new Node[10];
    private final double[] inputs=new double[10],states=new double[10],rates=new double[10];
    private final Child first,second;
    private final Entry[] ordered;
    private final Entry[] observationOrder;
    private int capturedCount;
    private final boolean orderedPureGraph;
    private static final String[] NAMES={"washoutBlock1", "washoutBlock2", "delayBlock", "washoutBlock1", "washoutBlock2", "delayBlock", "filterNthBlock", "filterBlock1", "filterBlock2", "outputBlock"};
    // BEGIN GENERATED SIGNATURES
    private static final String[] INPUTS={"+this.input-this.refPoint",
        "+this.washoutBlock1.y",
        "+this.washoutBlock2.y",
        "+this.input-this.refPoint",
        "+this.washoutBlock1.y",
        "+this.washoutBlock2.y",
        "+this.customBlock1.y+this.ks3*this.customBlock2.y",
        "+this.refPoint+this.filterNthBlock.y-this.ks4*this.customBlock2.y",
        "+this.filterBlock1.y",
        "+this.filterBlock2.y"};
    // END GENERATED SIGNATURES

    private static final String[] EXPECTED_INPUTS=Arrays.stream(INPUTS).map(Pss2aCompiledStage::mappedSignature).toArray(String[]::new);
    private static final String[] CHILD_INPUTS={mappedSignature("this.input1Signal"),mappedSignature("this.input2Signal")};
    private static String mappedSignature(String expression) {
        try {
            var parsed=new ExpCalculator(expression);
            com.interpss.dstab.controller.cml.annotate.util.CMLSymbolMapper.mapExpressionSymbolStr2Type(parsed.getRecList());
            return signature(parsed);
        } catch (com.interpss.common.exp.InterpssException failure) {throw new IllegalStateException(failure);}
    }
    static Pss2aCompiledStage tryCreate(Ieee1992PSS2AStabilizer model) {
        if(model.getClass()!=Ieee1992PSS2AStabilizer.class)return null;
        try{return new Pss2aCompiledStage(model);}
        catch(IllegalArgumentException | IllegalStateException failure){return null;}
    }
    private Pss2aCompiledStage(Ieee1992PSS2AStabilizer model) {
        this.model=model;
        first=new Child(model.customBlock1,0);second=new Child(model.customBlock2,3);
        var order=new ArrayList<Entry>();
        if(model.getFieldWrapperList().size()!=6)throw new IllegalArgumentException("Changed parent graph");
        for(var field:model.getFieldWrapperList()) {
            if(!(field instanceof BlockFieldAnWrapper<?> wrapper))throw new IllegalArgumentException("Unexpected wrapper");
            Child child=wrapper.getFieldName().equals("customBlock1")?first:wrapper.getFieldName().equals("customBlock2")?second:null;
            if(child!=null) {
                if(wrapper.getClass()!=ControllerFieldAnWrapper.class || wrapper.getField()!=child.model || wrapper.isFeedback())
                    throw new IllegalArgumentException("Changed child binding");
                String expected=CHILD_INPUTS[child==first?0:1];
                if(!signature(wrapper.getInputExp()).equals(expected))throw new IllegalArgumentException("Changed child input");
                order.add(new Entry(wrapper,child,null));
            } else {
                int id=Arrays.asList(NAMES).indexOf(wrapper.getFieldName());
                if(id<6)throw new IllegalArgumentException("Unexpected field");
                Node node=new Node(id,wrapper);nodes[id]=node;order.add(new Entry(wrapper,null,node));
            }
        }
        for(Node node:nodes)if(node==null)throw new IllegalArgumentException("Incomplete fixed graph");
        order.sort(Comparator.comparingInt(e->e.wrapper.getInitOrder()));ordered=order.toArray(Entry[]::new);
        boolean forward=first.forward && second.forward;
        for(int i=0;i<ordered.length;i++) forward &= i<2 ? ordered[i].child!=null
                : ordered[i].node!=null && ordered[i].node.id==i+4;
        var unique=Collections.newSetFromMap(new IdentityHashMap<Object,Boolean>());
        for(Node node:nodes) {
            forward &= unique.add(node.block) && node.kernel.supportsPureOutputRefresh();
            if(node.block instanceof FilterNthOrderBlock compound)
                for(var inner:compound.getIntegrationBlocks()) forward &= unique.add(inner);
        }
        orderedPureGraph=forward;
        var graph=model.getCompiledSignalGraph();
        var observations=new ArrayList<Entry>();
        if(graph!=null && graph.complete() && graph.acyclic()) {
            for(var group:graph.groups()) {
                if(group.fields().size()!=1)throw new IllegalArgumentException("Changed observation group");
                String name=group.fields().get(0);
                observations.add(Arrays.stream(ordered).filter(e->e.wrapper.getFieldName().equals(name))
                        .findFirst().orElseThrow(()->new IllegalArgumentException("Unknown observation field")));
            }
        }
        observationOrder=observations.size()==ordered.length?observations.toArray(Entry[]::new):null;
    }
    private static String signature(ExpCalculator expression) {
        var result=new StringBuilder();
        for(var record:expression.getRecList())result.append(record.opt).append(':')
                .append(record.getSymbolType()).append(':').append(record.getSymbolStr()).append(';');
        return result.toString();
    }
    private record Entry(BlockFieldAnWrapper<?> wrapper,Child child,Node node) {}
    @Override public void prepare(boolean predictor,double dt,boolean capture) {
        prepare(ordered,capture);
    }
    private void prepare(Entry[] entries,boolean capture) {
        for(Entry entry:entries) {
            if(entry.child!=null) {
                Child child=entry.child;
                child.model.setRuntimeInputValue(0.+(child==first?model.input1Signal:model.input2Signal));
                for(Node node:child.ordered)node.prepare(capture);
                child.model.setRuntimeOutputValue(child.model.preparedRuntimeOutput(model.getMachine()));
            } else entry.node.prepare(capture);
        }
    }
    @Override public void advance(boolean predictor,double dt) {
        for(int i=0;i<capturedCount;i++)captured[i].kernel.advance(predictor,inputs[i],dt,null,null,states,rates,i);
    }
    @Override public boolean requiresRepeatedOutputPreparation() {return !orderedPureGraph;}
    @Override public boolean supportsObservationPreparation() {
        return orderedPureGraph && model.isCompiledObservationPreparationEnabled();
    }
    @Override public boolean prepareScheduledObservation() {
        if(!supportsObservationPreparation() || observationOrder==null)return false;
        prepare(observationOrder,false);
        return true;
    }
    @Override public void release() {Arrays.fill(captured,0,capturedCount,null);capturedCount=0;}
    private double y(int id) {
        return switch(id) {
            case 0,3->((WashoutControlBlock)nodes[id].block).getY();
            case 1,4->((Pss2aWashoutBlock)nodes[id].block).getY();
            case 2,5->((DelayControlBlock)nodes[id].block).getY();
            case 6->((FilterNthOrderBlock)nodes[id].block).getY();
            case 7,8->((FilterControlBlock)nodes[id].block).getY();
            case 9->((Pss2aLeadLagBlock)nodes[id].block).getY();
            default->throw new IllegalArgumentException("Unknown output slot");
        };
    }
    // BEGIN GENERATED ARITHMETIC
    private double input(int id) {
        switch(id) {
            case 0: {
                double v0 = first.model.getRuntimeInputValue();
                double v1 = first.model.getRefPoint();
                v1 = -v1;
                return 0. + v0 + v1;
            }
            case 1: {
                double v0 = y(0);
                return 0. + v0;
            }
            case 2: {
                double v0 = y(1);
                return 0. + v0;
            }
            case 3: {
                double v0 = second.model.getRuntimeInputValue();
                double v1 = second.model.getRefPoint();
                v1 = -v1;
                return 0. + v0 + v1;
            }
            case 4: {
                double v0 = y(3);
                return 0. + v0;
            }
            case 5: {
                double v0 = y(4);
                return 0. + v0;
            }
            case 6: {
                double v0 = first.model.getRuntimeOutputValue();
                double v1 = model.ks3;
                double v2 = second.model.getRuntimeOutputValue();
                v2 = v1 * v2; v1 = 0.;
                return 0. + v0 + v1 + v2;
            }
            case 7: {
                double v0 = model.getRefPoint();
                double v1 = y(6);
                double v2 = model.ks4;
                double v3 = second.model.getRuntimeOutputValue();
                v2 = -v2;
                v3 = v2 * v3; v2 = 0.;
                return 0. + v0 + v1 + v2 + v3;
            }
            case 8: {
                double v0 = y(7);
                return 0. + v0;
            }
            case 9: {
                double v0 = y(8);
                return 0. + v0;
            }
            default: throw new IllegalArgumentException("Unknown input slot");
        }
    }
    // END GENERATED ARITHMETIC
    private final class Child {
        final Ieee1992PSS2AStabilizer.CustomExciter model;
        final Node[] ordered;
        final boolean forward;
        Child(Ieee1992PSS2AStabilizer.CustomExciter model,int offset) {
            this.model=model;
            if(model.getClass()!=Ieee1992PSS2AStabilizer.CustomExciter.class || model.getFieldWrapperList().size()!=3)
                throw new IllegalArgumentException("Changed child graph");
            var order=new ArrayList<Node>();
            for(var field:model.getFieldWrapperList()) {
                int local=Arrays.asList(NAMES).indexOf(field.getFieldName());
                if(local<0 || local>2 || !(field instanceof BlockFieldAnWrapper<?> wrapper))throw new IllegalArgumentException("Unknown child field");
                Node node=new Node(offset+local,wrapper);nodes[node.id]=node;order.add(node);
            }
            order.sort(Comparator.comparingInt(n->n.order));ordered=order.toArray(Node[]::new);
            forward=ordered.length==3 && ordered[0].id==offset && ordered[1].id==offset+1 && ordered[2].id==offset+2;
        }
    }
    private final class Node {
        final int id,order;final CMLControlBlockAdapter block;final CmlBlockKernel kernel;
        Node(int id,BlockFieldAnWrapper<?> wrapper) {
            this.id=id;this.order=wrapper.getInitOrder();
            if(wrapper.getClass()!=ControlBlockFieldAnWrapper.class || wrapper.isFeedback()
                    || !signature(wrapper.getInputExp()).equals(EXPECTED_INPUTS[id]))throw new IllegalArgumentException("Changed block wiring");
            Class<?>[] types={WashoutControlBlock.class,Pss2aWashoutBlock.class,DelayControlBlock.class,
                WashoutControlBlock.class,Pss2aWashoutBlock.class,DelayControlBlock.class,
                FilterNthOrderBlock.class,FilterControlBlock.class,FilterControlBlock.class,Pss2aLeadLagBlock.class};
            if(wrapper.getField().getClass()!=types[id])throw new IllegalArgumentException("Custom block");
            block=(CMLControlBlockAdapter)wrapper.getField();kernel=CmlBlockKernel.create(block);
            if(block instanceof ICMLLimitBlock limited && limited.getLimit()!=null &&
                    (limited.getLimit().getClass()!=com.interpss.dstab.datatype.LimitExpression.class
                    || limited.getLimit().getMaxExp()!=null || limited.getLimit().getMinExp()!=null))
                throw new IllegalArgumentException("Variable limits require generic evaluation");
        }
        void prepare(boolean capture) {
            double value=input(id);block.setStageInput(value);
            if(capture){captured[capturedCount]=this;inputs[capturedCount++]=value;}
        }
    }
}
