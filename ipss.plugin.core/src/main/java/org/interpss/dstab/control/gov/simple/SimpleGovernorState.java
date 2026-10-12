package org.interpss.dstab.control.gov.simple;

import com.interpss.dstab.controller.cml.field.block.DelayControlBlock;
import com.interpss.dstab.controller.cml.field.block.GainBlock;
import com.interpss.dstab.controller.cml.wrapper.BlockFieldAnWrapper;
import com.interpss.state.dstab.DelayControlBlockState;
import com.interpss.state.dstab.GainBlockState;

/** Independent configuration, references, and two-block runtime state of the simple governor. */
public final class SimpleGovernorState {
    public double configuredGain, configuredTime, configuredMax, configuredMin;
    public double ka, ta, ks, pmax, pmin, reference;
    public DelayControlBlockState delay;
    public GainBlockState gain;
    public int delayOrder, gainOrder;
    public boolean delayInitialized, gainInitialized, declaredDelay, declaredGain, namedStates;
    private static BlockFieldAnWrapper<?> wrapper(SimpleGovernor controller,String name) {
        return controller.getFieldWrapperList().stream().filter(w->w.getFieldName().equals(name))
                .filter(w->w instanceof BlockFieldAnWrapper<?>).map(w->(BlockFieldAnWrapper<?>)w)
                .findFirst().orElseThrow(()->new IllegalArgumentException("Missing governor block: "+name));
    }
    public SimpleGovernorState(SimpleGovernor source) {
        if(source.getClass()!=SimpleGovernor.class || source.isChildController())
            throw new IllegalArgumentException("Simple governor requires its exact standalone model");
        configuredGain=source.getData().getK();configuredTime=source.getData().getT1();
        configuredMax=source.getData().getPmax();configuredMin=source.getData().getPmin();
        ka=source.ka;ta=source.ta;ks=source.ks;pmax=source.pmax;pmin=source.pmin;reference=source.getRefPoint();
        if(source.getFieldWrapperList()==null) {
            if(source.delayBlock!=null || source.gainBlock!=null)
                throw new IllegalArgumentException("Simple governor has unbound declared blocks");
            return;
        }
        if(source.getFieldWrapperList().size()!=2)
            throw new IllegalArgumentException("Unexpected simple governor blocks");
        var dw=wrapper(source,"delayBlock");var gw=wrapper(source,"gainBlock");
        if(!(dw.getField() instanceof DelayControlBlock db) || !(gw.getField() instanceof GainBlock gb)
                || (source.delayBlock!=null && source.delayBlock!=db) || (source.gainBlock!=null && source.gainBlock!=gb))
            throw new IllegalArgumentException("Simple governor blocks are not bound");
        delay=new DelayControlBlockState(db);gain=new GainBlockState(gb);
        if(!"NoLimit".equals(delay.type)||!"Limit".equals(gain.type))
            throw new IllegalArgumentException("Unexpected simple governor block modes");
        delayOrder=dw.getInitOrder();gainOrder=gw.getInitOrder();
        delayInitialized=dw.isInitialized();gainInitialized=gw.isInitialized();
        declaredDelay=source.delayBlock!=null;declaredGain=source.gainBlock!=null;
        namedStates=!source.getNamedStateBlocks().isEmpty();
    }
    public void restore(SimpleGovernor target) {
        target.getData().setK(configuredGain);target.getData().setT1(configuredTime);
        target.getData().setPmax(configuredMax);target.getData().setPmin(configuredMin);
        if((delay==null)!=(gain==null))throw new IllegalArgumentException("Incomplete governor block state");
        if(delay!=null) {
            target.ka=delay.gain;target.ta=delay.timeConstant;
            target.ks=gain.gain;target.pmax=gain.maximum;target.pmin=gain.minimum;
            if(!target.parseAnnotation() || !target.mapSymbolStr2Type())
                throw new IllegalArgumentException("Cannot rebind copied simple governor annotations");
            var dw=wrapper(target,"delayBlock");var gw=wrapper(target,"gainBlock");
            var db=(DelayControlBlock)dw.getField();var gb=(GainBlock)gw.getField();
            db.restoreIntegrationState(delay.input,delay.state,delay.derivative);gb.eulerStep1(gain.input,0);
            dw.setInitOrder(delayOrder);gw.setInitOrder(gainOrder);
            dw.setInitialized(delayInitialized);gw.setInitialized(gainInitialized);
            if(declaredDelay)target.delayBlock=db;if(declaredGain)target.gainBlock=gb;
            if(namedStates)target.refreshNamedStateBlocks();
        }
        target.ka=ka;target.ta=ta;target.ks=ks;target.pmax=pmax;target.pmin=pmin;target.setRefPoint(reference);
    }
}
