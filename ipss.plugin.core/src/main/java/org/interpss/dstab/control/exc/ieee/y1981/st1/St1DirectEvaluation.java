package org.interpss.dstab.control.exc.ieee.y1981.st1;

import java.util.*;
import com.interpss.dstab.controller.cml.annotate.util.CMLSymbolMapper;
import com.interpss.dstab.controller.cml.annotate.util.CMLSymbolMapper.FixedExpression;
import com.interpss.dstab.datatype.CMLVarEnum;
import com.interpss.dstab.datatype.CMLVarRec;
import com.interpss.dstab.datatype.ExpCalculator;
import com.interpss.common.exp.InterpssException;
import com.interpss.dstab.controller.cml.ICMLMachineVoltageProvider;
import com.interpss.dstab.controller.cml.field.*;
import com.interpss.dstab.controller.cml.field.block.*;
import com.interpss.dstab.controller.cml.field.ICMLStaticBlock.StaticBlockType;
import com.interpss.dstab.controller.cml.field.adapt.CMLControlBlockAdapter;
import com.interpss.dstab.controller.cml.field.func.HighValueFunction;
import com.interpss.dstab.controller.cml.runtime.CmlBlockKernel;
import com.interpss.dstab.controller.cml.wrapper.*;

/** Experimental evaluator for the unmodified ST1 graph. Not a general CML replacement. */
final class St1DirectEvaluation {
    final IEEE1981ST1Exciter model;
    final Node[] nodes = new Node[8];
    private final FixedExpression[] inputs = new FixedExpression[8];
    private final int[] inputDependencies=new int[8];
    private final int[] dependentInputs=new int[8];
    private int dirtyInputs;
    private final double[] cachedInput=new double[8];
    long reusedInputs,evaluatedInputs;

    private final FixedExpression gateExpression;
    final Node[] ordered;
    final com.interpss.dstab.controller.cml.field.adapt.CMLControlBlock1stOrderAdapter[] localBlocks;
    final boolean[] localLimited;
    final int[] localIds;
    private boolean primitiveLocal;
    long primitiveLocalAdvanceCount;
    private final double[] localU=new double[8],localX=new double[8],localD=new double[8],
        localK=new double[8],localT=new double[8],localT1=new double[8];
    private final StaticBlockType[] localTypes=new StaticBlockType[8];
    private final org.interpss.numeric.datatype.LimitType[] localLimits=new org.interpss.numeric.datatype.LimitType[8];
    private boolean fixedInputs, fixedPss, voltageRead, pssRead;
    private double fixedVoltageValue, fixedPssValue;
    final Node[] controls, feedback;
    final double[] states = new double[8], rates = new double[8];
    final BaseFieldAnWrapper<?> gate;
    static final String[] NAMES = {"trDelayBlock", "gainBlock", "filterBlock", "filterBlock1",
        "kaDelayBlock", "washoutBlock", "rateFeedbackInputBlock", "gainCustomBlock"};

    St1DirectEvaluation(IEEE1981ST1Exciter model) {
        this.model = model;
        BaseFieldAnWrapper<?> foundGate = null;
        var sorted = new ArrayList<Node>();
        for (var wrapper : model.getFieldWrapperList()) {
            if (wrapper.getFieldName().equals("uelGate1Function")) { foundGate=wrapper; continue; }
            int id=Arrays.asList(NAMES).indexOf(wrapper.getFieldName());
            if (id < 0 || !(wrapper instanceof BlockFieldAnWrapper<?> block))
                throw new IllegalStateException("Unexpected ST1 field: " + wrapper.getFieldName());
            Node node=new Node(id,block); nodes[id]=node; sorted.add(node);
        }
        if (foundGate == null || foundGate.getField().getClass() != HighValueFunction.class)
            throw new IllegalStateException("Unexpected ST1 selector");
        gate=foundGate;
        for (Node node : nodes) Objects.requireNonNull(node);
        localBlocks=sorted.stream().filter(n->n.control)
            .map(n->(com.interpss.dstab.controller.cml.field.adapt.CMLControlBlock1stOrderAdapter)n.block)
            .toArray(com.interpss.dstab.controller.cml.field.adapt.CMLControlBlock1stOrderAdapter[]::new);
        localIds=sorted.stream().filter(n->n.control).mapToInt(n->n.id).toArray();
        localLimited=new boolean[localBlocks.length];
        for(int i=0;i<localBlocks.length;i++) {
            localLimited[i]=localBlocks[i].getType()==StaticBlockType.NonWindup;
            if(localLimited[i] && !(localBlocks[i] instanceof DelayControlBlock))
                throw new IllegalStateException("Unsupported local ST1 projection");
        }
        sorted.sort(Comparator.comparingInt(n -> n.wrapper.getInitOrder()));
        ordered=sorted.toArray(Node[]::new);
        controls=sorted.stream().filter(n->n.control).toArray(Node[]::new);
        feedback=sorted.stream().filter(n->!n.control&&n.wrapper.isFeedback()).toArray(Node[]::new);
        Class<?>[] types={DelayControlBlock.class,GainBlock.class,FilterControlBlock.class,
            FilterControlBlock.class,DelayControlBlock.class,WashoutControlBlock.class};
        for(int i=0;i<types.length;i++)if(nodes[i].block.getClass()!=types[i])
            throw new IllegalStateException("Standard ST1 block required at "+i);

        try {
            for(Node node:nodes) {
                inputs[node.id]=new FixedExpression(node.wrapper.getInputExp(),model,this::bindSignal);
                int dependencies=0;
                for(var record:node.wrapper.getInputExp().getRecList())dependencies|=dependency(record);
                inputDependencies[node.id]=dependencies;
                if(dependencies>=0)for(int source=0;source<8;source++)
                    if((dependencies&(1<<source))!=0)dependentInputs[source]|=1<<node.id;
            }
            var expression=new ExpCalculator("this.uelGate1Function.y");
            CMLSymbolMapper.mapExpressionSymbolStr2Type(expression.getRecList());
            gateExpression=new FixedExpression(expression,model,this::bindSignal);
        } catch(InterpssException failure) { throw new IllegalStateException("Cannot compile ST1 equations",failure); }

    }

    double y(int id) {
        if(primitiveLocal && id<6)return primitiveY(id);
        return switch(id) {
            case 0,4 -> ((DelayControlBlock)nodes[id].block).getY();
            case 1 -> ((GainBlock)nodes[id].block).getY();
            case 2,3 -> ((FilterControlBlock)nodes[id].block).getY();
            case 5 -> ((WashoutControlBlock)nodes[id].block).getY();
            case 6,7 -> nodes[id].block.getY();
            default -> throw new IllegalArgumentException();
        };
    }
    double pss() {
        var machine=model.getMachine();
        if(fixedInputs && fixedPss && pssRead)return fixedPssValue;
        double value=machine.getStabilizer()==null ? 0. : machine.getStabilizer().getOutput(machine);
        if(fixedInputs && fixedPss){fixedPssValue=value;pssRead=true;}
        return value;
    }
    double gate() {
        return evaluateExpression(gateExpression);
    }
    double signal(String name) {
        return switch(name) {
            case "this.gainBlock.y" -> y(1);
            case "this.uelGate1Function.y" -> gate();
            case "this.kaDelayBlock.y" -> y(4);
            case "this.trDelayBlock.y" -> y(0);
            case "this.filterBlock.y" -> y(2);
            case "this.filterBlock1.y" -> y(3);
            case "this.washoutBlock.y" -> y(5);
            default -> throw new IllegalArgumentException(name);
        };
    }
    private java.util.function.DoubleSupplier bindSignal(CMLVarRec record) {
        if(record.getSymbolType()==CMLVarEnum.MachVt)return this::voltage;
        if(record.getSymbolType()==CMLVarEnum.PssVs)return this::pss;
        if(record.getSymbolType()==CMLVarEnum.ControllerFieldOutput) {
            int id=Arrays.asList(NAMES).indexOf(record.getPrimaryFieldName());
            if(id>=0)return ()->y(id);
        }
        return null;
    }
    private double voltage() {
        if(fixedInputs && voltageRead)return fixedVoltageValue;
        var machine=model.getMachine();
        double value=machine instanceof ICMLMachineVoltageProvider p ? p.getCmlMachineVoltage()
            : model.getDStabBus().getVoltage().abs()/machine.getVMultiFactor();
        if(fixedInputs){fixedVoltageValue=value;voltageRead=true;}
        return value;
    }
    private double evaluateExpression(FixedExpression expression) {
        try { return expression.evaluate(); }
        catch(InterpssException failure) { throw new IllegalStateException("ST1 input evaluation failed",failure); }
    }
    private int dependency(CMLVarRec record) {
        return switch(record.getSymbolType()) {
            case Constant,ControllerFieldParameter,ControllerRefPoint,MachVt,PssVs -> 0;
            case ControllerFieldOutput -> {
                int id=Arrays.asList(NAMES).indexOf(record.getPrimaryFieldName());
                if(id>=0)yield 1<<id;
                if(record.getPrimaryFieldName().equals("uelGate1Function"))yield 1<<1;
                yield -1;
            }
            default -> -1;
        };
    }
    double input(int id) {
        if(!primitiveLocal || inputDependencies[id]<0)return evaluateExpression(inputs[id]);
        if((dirtyInputs&(1<<id))==0){reusedInputs++;return cachedInput[id];}
        evaluatedInputs++;double value=evaluateExpression(inputs[id]);
        cachedInput[id]=value;dirtyInputs&=~(1<<id);return value;
    }
    private void outputChanged(int id) {
        dirtyInputs|=dependentInputs[id];
        // The exact feedback tap also reads the regulator directly for its pre-field limiter signal.
        if(id==4)dirtyInputs|=dependentInputs[6];
    }
    private void localInput(int id,double value) {
        if(Double.doubleToRawLongBits(localU[id])!=Double.doubleToRawLongBits(value))outputChanged(id);
        localU[id]=value;
    }
    private void localState(int id,double value) {
        if(Double.doubleToRawLongBits(localX[id])!=Double.doubleToRawLongBits(value))outputChanged(id);
        localX[id]=value;
    }
    double advanceLocalStore(double dt,boolean discontinuity,boolean wasActive,
            boolean cachePss,double[] history,double[] start,double[] rates) {
        if(history.length!=localBlocks.length || start.length!=localBlocks.length || rates.length!=localBlocks.length)
            throw new IllegalArgumentException("ST1 state layout differs from its bound blocks");
        if(fixedInputs)throw new IllegalStateException("Nested ST1 local integration");
        fixedInputs=true;fixedPss=cachePss;voltageRead=false;pssRead=false;
        try {
            if(!Boolean.getBoolean("ipss.experiment.disableSt1PrimitiveLocal")
                    && (cachePss || model.getMachine().getStabilizer()==null)) {
                capturePrimitiveLocal();primitiveLocal=true;primitiveLocalAdvanceCount++;
            }
            double h=dt/10,field=model.getMachine().getEfd();
            for(int sub=0;sub<10;sub++) {
                evaluate();
                for(int i=0;i<localBlocks.length;i++) {
                    var block=localBlocks[i];
                    start[i]=primitiveLocal?localX[localIds[i]]:block.getStateX();
                    rates[i]=primitiveLocal?localD[localIds[i]]:block.getDX_dt();
                    if(!wasActive)history[i]=rates[i];
                    if(sub==0 && discontinuity)rates[i]=.5*(rates[i]+history[i]);
                }
                wasActive=true;
                for(int i=0;i<localBlocks.length;i++) {
                    double trial=start[i]+h*(1.5*rates[i]-.5*history[i]);
                    double end=localLimited[i]?((DelayControlBlock)localBlocks[i]).getLimit().limit(trial):trial;
                    if(primitiveLocal)localState(localIds[i],end);else localBlocks[i].setStateX(end);
                    history[i]=end!=trial?0.:rates[i];
                }
                field=model.getStaggeredFieldVoltage();
                evaluate();
            }
            return field;
        } finally {
            if(primitiveLocal) {
                for(int i=0;i<localBlocks.length;i++) {
                    int id=localIds[i];localBlocks[i].restoreIntegrationState(localU[id],localX[id],localD[id]);
                }
            }
            primitiveLocal=false;fixedInputs=false;fixedPss=false;voltageRead=false;pssRead=false;
        }
    }
    private void capturePrimitiveLocal() {
        dirtyInputs=255;
        for(int id=0;id<6;id++) {
            var block=(com.interpss.dstab.controller.cml.field.adapt.CMLStaticBlockAdapter)nodes[id].block;
            localU[id]=block.getU();localTypes[id]=block.getType();
            if(block instanceof com.interpss.dstab.controller.cml.field.adapt.CMLControlBlock1stOrderAdapter b) {
                localX[id]=b.getStateX();localD[id]=b.getDX_dt();
            }
            if(block instanceof DelayControlBlock b) {
                localK[id]=b.getK();localT[id]=b.getT();localLimits[id]=b.getLimit();
            } else if(block instanceof FilterControlBlock b) {
                localK[id]=b.getK();localT[id]=b.getT2();localT1[id]=b.getT1();localLimits[id]=b.getLimit();
            } else if(block instanceof WashoutControlBlock b) {
                localK[id]=b.getK();localT[id]=b.getT();
            } else if(block instanceof GainBlock b) {
                localK[id]=b.getK();localLimits[id]=b.getLimit();
            }
        }
    }
    private double primitiveY(int id) {
        double result;
        if(id==0 || id==4) {
            result=localT[id]<=0.?localK[id]*localU[id]:localX[id];
            return localTypes[id]==StaticBlockType.Limit || localTypes[id]==StaticBlockType.NonWindup
                ?localLimits[id].limit(result):result;
        }
        if(id==1) {
            result=localU[id]*localK[id];
            return localTypes[id]==StaticBlockType.Limit?localLimits[id].limit(result):result;
        }
        if(id==2 || id==3) {
            result=localT[id]>0.?localX[id]+localK[id]*localT1[id]*localU[id]/localT[id]:localU[id]*localK[id];
            return localTypes[id]==StaticBlockType.Limit || localTypes[id]==StaticBlockType.NonWindup && localT[id]==0.
                ?localLimits[id].limit(result):result;
        }
        return localT[id]==0.?0.:localK[id]*localU[id]-localX[id];
    }
    private void advancePrimitiveZero(Node node) {
        int id=node.id;double u=node.input,x=localX[id],rate;
        if(id==0 || id==4) {
            if(localTypes[id]==StaticBlockType.NonWindup) {
                if(localLimits[id].isViolated(x))x=localLimits[id].limit(x);
                rate=localT[id]>0.?(localK[id]*u-x)/localT[id]:0.;
                if(rate>0. && x>=localLimits[id].getMax())rate=0.;
                if(rate<0. && x<=localLimits[id].getMin())rate=0.;
            } else rate=localT[id]>0.?(localK[id]*u-x)/localT[id]:0.;
        } else if(id==2 || id==3)
            rate=localT[id]>0.?(localK[id]*(1.-localT1[id]/localT[id])*u-x)/localT[id]:0.;
        else rate=localT[id]==0.?0.:(localK[id]*u-x)/localT[id];
        localInput(id,u);localState(id,x+rate*0.);localD[id]=rate;
    }
    void evaluate() {
        for(Node node:nodes)if(node.wrapper.getField()!=node.block)
            throw new IllegalStateException("ST1 structure changed during a frozen plan");
        prepare(true);
        for (Node node : controls) advanceZero(node);
        prepare(false);
        prepare(false);
    }
    void prepare(boolean capture) {
        for (Node node : ordered) node.prepare(capture);
        for (Node node : feedback) node.prepare(false);
    }
    void advanceZero(Node node) {
        if(primitiveLocal){advancePrimitiveZero(node);return;}
        double input=node.input;
        if(node.id==0||node.id==4) {
            DelayControlBlock b=(DelayControlBlock)node.block;
            if(b.getType()==StaticBlockType.NonWindup) {
                node.kernel.advance(true,input,0.,null,null,states,rates,node.id);return;
            }
            double x=b.getStateX();
            double rate=b.getT()>0.?(b.getK()*input-x)/b.getT():0.;
            states[node.id]=x+rate*0.;rates[node.id]=rate;
            b.restoreIntegrationState(input,states[node.id],rate);
        } else if(node.id==2||node.id==3) {
            FilterControlBlock b=(FilterControlBlock)node.block;
            if(b.getType()==StaticBlockType.NonWindup) {
                node.kernel.advance(true,input,0.,null,null,states,rates,node.id);return;
            }
            double x=b.getStateX();
            double rate=b.getT2()>0.?(b.getK()*(1.-b.getT1()/b.getT2())*input-x)/b.getT2():0.;
            states[node.id]=x+rate*0.;rates[node.id]=rate;
            b.restoreIntegrationState(input,states[node.id],rate);
        } else if(node.id==5) {
            WashoutControlBlock b=(WashoutControlBlock)node.block;
            if(b.getType()==StaticBlockType.NonWindup) {
                node.kernel.advance(true,input,0.,null,null,states,rates,node.id);return;
            }
            double x=b.getStateX();
            double rate=b.getT()==0.?0.:(b.getK()*input-x)/b.getT();
            states[node.id]=x+rate*0.;rates[node.id]=rate;
            b.restoreIntegrationState(input,states[node.id],rate);
        } else throw new IllegalStateException("Unexpected ST1 control");
    }
    final class Node {
        final int id;
        final BlockFieldAnWrapper<?> wrapper;
        final boolean control;
        final ICMLStaticBlock block;
        final CmlBlockKernel kernel;
        double input;
        Node(int id,BlockFieldAnWrapper<?> wrapper) {
            this.id=id; this.wrapper=wrapper; block=(ICMLStaticBlock)wrapper.getField();
            control=wrapper instanceof ControlBlockFieldAnWrapper;
            kernel=CmlBlockKernel.create(block);
            if (block instanceof ICMLLimitBlock limited && limited.getLimit()!=null
                && (limited.getLimit().getMaxExp()!=null || limited.getLimit().getMinExp()!=null))
                throw new IllegalStateException("Variable limit requires general evaluator");
        }
        void prepare(boolean capture) {
            double value=St1DirectEvaluation.this.input(id);
            if (control) {
                if(primitiveLocal)localInput(id,value);
                else ((CMLControlBlockAdapter)block).setStageInput(value);
                if(capture) input=value;
            } else {
                double previousInput=primitiveLocal?block.getU():0.;
                block.eulerStep1(value,0.);
                if(primitiveLocal) {
                    if(id<6)localInput(id,value);
                    else if(Double.doubleToRawLongBits(previousInput)!=Double.doubleToRawLongBits(block.getU()))outputChanged(id);
                }
            }
        }
    }
}
