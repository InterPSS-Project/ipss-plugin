package org.interpss.dstab.control.pss.ieee.y1992.pss2a;

import java.util.*;
import com.interpss.dstab.controller.cml.field.ICMLStaticBlock.StaticBlockType;
import com.interpss.dstab.controller.cml.field.adapt.CMLControlBlock1stOrderAdapter;
import com.interpss.dstab.controller.cml.field.block.*;
import com.interpss.dstab.controller.cml.wrapper.*;

/**
 * Numerical stage for the exact standard graph. Block storage remains authoritative
 * between calls; live parameters and restored states are loaded at each stage.
 * Custom callbacks, aliases and projected blocks retain the ordinary evaluator.
 * Enabled only by the explicit controller-frame experiment option.
 */
final class Pss2aNumericFrame {
    final Pss2aDirectEvaluation plan;
    final CMLControlBlock1stOrderAdapter[] blocks;
    // Kind: mandatory washout, optional washout, delay, filter.
    // Owner maps each physical state to the original capture-order scratch slot.
    final int[] kinds, owners;
    final double[] x, dx, u, gain, time, lead;
    final int tail;
    double compoundInput, terminalInput;

    static Pss2aNumericFrame create(Pss2aDirectEvaluation p) {
        if (!p.scalarPreparation || !p.singleRefresh || p.model.getClass()!=Ieee1992PSS2AStabilizer.class) return null;
        Set<Object> seen=Collections.newSetFromMap(new IdentityHashMap<>());
        for (var entry:p.entries) {
            Class<?> expected=entry.child()==null?ControlBlockFieldAnWrapper.class:ControllerFieldAnWrapper.class;
            if (entry.wrapper().getClass()!=expected) return null;
        }
        for(var node:p.nodes) {
            if(node.wrapper.getClass()!=ControlBlockFieldAnWrapper.class || !seen.add(node.block))return null;
        }
        for(var child:((FilterNthOrderBlock)p.nodes[6].block).getIntegrationBlocks())
            if((child.getClass()!=FilterControlBlock.class && child.getClass()!=DelayControlBlock.class) || !seen.add(child))return null;
        return new Pss2aNumericFrame(p);
    }
    private Pss2aNumericFrame(Pss2aDirectEvaluation p) {
        plan=p;
        var inner=((FilterNthOrderBlock)p.nodes[6].block).getIntegrationBlocks();
        tail=6+inner.size();int size=tail+2;
        blocks=new CMLControlBlock1stOrderAdapter[size];kinds=new int[size];owners=new int[size];Arrays.fill(owners,-1);
        for(int i=0;i<6;i++){blocks[i]=(CMLControlBlock1stOrderAdapter)p.nodes[i].block;kinds[i]=i%3;owners[i]=p.entries[0].child().base==0?i:(i+3)%6;}
        for(int i=0;i<inner.size();i++){blocks[6+i]=inner.get(i);kinds[6+i]=inner.get(i) instanceof FilterControlBlock?3:2;}
        for(int i=0;i<2;i++){blocks[tail+i]=(CMLControlBlock1stOrderAdapter)p.nodes[7+i].block;kinds[tail+i]=3;owners[tail+i]=7+i;}
        x=new double[size];dx=new double[size];u=new double[size];gain=new double[size];time=new double[size];lead=new double[size];
    }
    boolean eligible() {
        if(!plan.model.eAdapters().isEmpty() || !plan.first.model.eAdapters().isEmpty() || !plan.second.model.eAdapters().isEmpty())return false;
        // Runtime structure changes take the original ordered checks and error path.
        for(var entry:plan.entries)if(entry.child()!=null && entry.wrapper().getField()!=entry.child().model)return false;
        for(var node:plan.nodes)if(node.wrapper.getField()!=node.block)return false;
        for(var block:blocks)if(block.getType()!=StaticBlockType.NoLimit)return false;
        return true;
    }
    void load() {
        for(int i=0;i<blocks.length;i++) {
            var b=blocks[i];x[i]=b.getStateX();dx[i]=b.getDX_dt();
            switch(kinds[i]) {
                case 0,1 -> {var w=(WashoutControlBlock)b;gain[i]=w.getK();time[i]=w.getT();}
                case 2 -> {var d=(DelayControlBlock)b;gain[i]=d.getK();time[i]=d.getT();}
                case 3 -> {var f=(FilterControlBlock)b;gain[i]=f.getK();time[i]=f.getT2();lead[i]=f.getT1();}
            }
        }
    }
    double output(int i) {
        return switch(kinds[i]) {
            case 0 -> time[i]==0.?0.:gain[i]*u[i]-x[i];
            case 1 -> time[i]<=1.e-12?gain[i]*u[i]:(time[i]==0.?0.:gain[i]*u[i]-x[i]);
            case 2 -> time[i]<=0.?gain[i]*u[i]:x[i];
            case 3 -> time[i]>0.?x[i]+gain[i]*lead[i]*u[i]/time[i]:u[i]*gain[i];
            default -> throw new AssertionError();
        };
    }
    void child(Pss2aDirectEvaluation.Child c,int start,double signal) {
        c.model.setRuntimeInputValue(0.+signal);
        u[start]=(0.+c.model.getRuntimeInputValue())+(-c.model.getRefPoint());
        u[start+1]=0.+output(start);u[start+2]=0.+output(start+1);
        double value=0.+output(start+2);
        boolean algebraic=time[start]==0. || time[start+1]==0. || time[start+2]==0.;
        c.model.setRuntimeOutputValue(!c.refreshing && algebraic && !Double.isFinite(value)?Double.NaN:value);
    }
    void prepareFrame(boolean capture) {
        var model=plan.model;
        for(int i=0;i<2;i++) {var child=plan.entries[i].child();child(child,child.base,child==plan.first?model.input1Signal:model.input2Signal);}
        double a=plan.first.model.getRuntimeOutputValue(),b=model.ks3,c=plan.second.model.getRuntimeOutputValue();
        double sum=0.;sum+=a;sum+=0.;sum+=b*c;compoundInput=sum;
        for(int i=6;i<tail;i++){u[i]=sum;sum=output(i);}
        a=model.getRefPoint();b=sum;c=model.ks4;double d=plan.second.model.getRuntimeOutputValue();
        sum=0.;sum+=a;sum+=b;sum+=0.;sum+=(-c)*d;
        u[tail]=sum;u[tail+1]=0.+output(tail);terminalInput=0.+output(tail+1);
        if(capture) {
            plan.inputs[6]=compoundInput;plan.inputs[9]=terminalInput;
            for(int i=0;i<blocks.length;i++)if(owners[i]>=0)plan.inputs[owners[i]]=u[i];
        }
    }
    void step(boolean predictor,double dt) {
        // Freeze inputs before advancing any physical state. The two child chains
        // can have reversed initialization order, retained by prepareFrame().
        load();prepareFrame(true);
        for(int i=0;i<blocks.length;i++) {
            if(kinds[i]==1 && time[i]<=1.e-12)continue;
            double derivative=switch(kinds[i]) {
                case 0,1 -> time[i]==0.?0.:(gain[i]*u[i]-x[i])/time[i];
                case 2 -> time[i]>0.?(gain[i]*u[i]-x[i])/time[i]:0.;
                case 3 -> time[i]>0.?(gain[i]*(1.-lead[i]/time[i])*u[i]-x[i])/time[i]:0.;
                default -> throw new AssertionError();
            };
            x[i]=predictor?x[i]+derivative*dt:x[i]+.5*(derivative-dx[i])*dt;
            if(predictor)dx[i]=derivative;
            int owner=owners[i];
            if(owner>=0 && kinds[i]!=1){plan.states[owner]=x[i];plan.rates[owner]=dx[i];}
        }

        if(predictor)plan.nodes[9].block.eulerStep1(plan.inputs[9],dt);
        else plan.nodes[9].block.eulerStep2(plan.inputs[9],dt);
        plan.capturedCount=10;
        for(int i=0;i<6;i++)plan.captured[owners[i]]=plan.nodes[i];
        for(int i=6;i<10;i++)plan.captured[i]=plan.nodes[i];
        // Compute the final inputs from the updated states, then publish them.
        // No external callback can observe the uncommitted frame in this path.
        prepareFrame(false);
        for(int i=0;i<blocks.length;i++)blocks[i].restoreIntegrationState(u[i],x[i],dx[i]);
        plan.nodes[6].block.setStageInput(compoundInput);
        plan.nodes[9].block.setStageInput(terminalInput);
        plan.singleRefreshCount++;
    }
}
