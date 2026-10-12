package org.interpss.dstab.control.pss.simple;

import com.interpss.dstab.controller.cml.field.block.FilterControlBlock;
import com.interpss.dstab.controller.cml.wrapper.BlockFieldAnWrapper;
import com.interpss.state.dstab.FilterControlBlockState;

/** Independent configuration and both lead-lag integration states of the simple stabilizer. */
public final class SimpleStabilizerState {
    public double configuredGain, configuredT1, configuredT2, configuredT3, configuredT4, configuredMax, configuredMin;
    public double k1, k2, t1, t2, t3, t4, vmax, vmin, reference;
    public FilterControlBlockState first, second;
    public int firstOrder, secondOrder;
    public boolean firstInitialized, secondInitialized, declaredFirst, declaredSecond, namedStates;
    private static BlockFieldAnWrapper<?> wrapper(SimpleStabilizer controller,String name) {
        return controller.getFieldWrapperList().stream().filter(w->w.getFieldName().equals(name))
                .filter(w->w instanceof BlockFieldAnWrapper<?>).map(w->(BlockFieldAnWrapper<?>)w)
                .findFirst().orElseThrow(()->new IllegalArgumentException("Missing stabilizer block: "+name));
    }
    public SimpleStabilizerState(SimpleStabilizer source) {
        if(source.getClass()!=SimpleStabilizer.class || source.isChildController())
            throw new IllegalArgumentException("Simple stabilizer requires its exact standalone model");
        var data=source.getData();
        configuredGain=data.getKs();configuredT1=data.getT1();configuredT2=data.getT2();
        configuredT3=data.getT3();configuredT4=data.getT4();configuredMax=data.getVsmax();configuredMin=data.getVsmin();
        k1=source.k1;k2=source.k2;t1=source.t1;t2=source.t2;t3=source.t3;t4=source.t4;
        vmax=source.vmax;vmin=source.vmin;reference=source.getRefPoint();
        if(source.getFieldWrapperList()==null) {
            if(source.filterBlock1!=null || source.filterBlock2!=null)
                throw new IllegalArgumentException("Simple stabilizer has unbound declared blocks");
            return;
        }
        if(source.getFieldWrapperList().size()!=2)
            throw new IllegalArgumentException("Unexpected simple stabilizer blocks");
        var fw=wrapper(source,"filterBlock1");var sw=wrapper(source,"filterBlock2");
        if(!(fw.getField() instanceof FilterControlBlock fb) || !(sw.getField() instanceof FilterControlBlock sb)
                || (source.filterBlock1!=null && source.filterBlock1!=fb) || (source.filterBlock2!=null && source.filterBlock2!=sb))
            throw new IllegalArgumentException("Simple stabilizer blocks are not bound");
        first=new FilterControlBlockState(fb);second=new FilterControlBlockState(sb);
        if(!"NoLimit".equals(first.type)||!"Limit".equals(second.type))
            throw new IllegalArgumentException("Unexpected simple stabilizer block modes");
        firstOrder=fw.getInitOrder();secondOrder=sw.getInitOrder();
        firstInitialized=fw.isInitialized();secondInitialized=sw.isInitialized();
        declaredFirst=source.filterBlock1!=null;declaredSecond=source.filterBlock2!=null;
        namedStates=!source.getNamedStateBlocks().isEmpty();
    }
    public void restore(SimpleStabilizer target) {
        var data=target.getData();data.setKs(configuredGain);data.setT1(configuredT1);data.setT2(configuredT2);
        data.setT3(configuredT3);data.setT4(configuredT4);data.setVsmax(configuredMax);data.setVsmin(configuredMin);
        if((first==null)!=(second==null))throw new IllegalArgumentException("Incomplete stabilizer block state");
        if(first!=null) {
            target.k1=first.gain;target.t1=first.leadTime;target.t2=first.lagTime;
            target.k2=second.gain;target.t3=second.leadTime;target.t4=second.lagTime;
            target.vmax=second.maximum;target.vmin=second.minimum;
            if(!target.parseAnnotation() || !target.mapSymbolStr2Type())
                throw new IllegalArgumentException("Cannot rebind copied simple stabilizer annotations");
            var fw=wrapper(target,"filterBlock1");var sw=wrapper(target,"filterBlock2");
            var fb=(FilterControlBlock)fw.getField();var sb=(FilterControlBlock)sw.getField();
            fb.restoreIntegrationState(first.input,first.state,first.derivative);
            sb.restoreIntegrationState(second.input,second.state,second.derivative);
            fw.setInitOrder(firstOrder);sw.setInitOrder(secondOrder);
            fw.setInitialized(firstInitialized);sw.setInitialized(secondInitialized);
            if(declaredFirst)target.filterBlock1=fb;if(declaredSecond)target.filterBlock2=sb;
            if(namedStates)target.refreshNamedStateBlocks();
        }
        target.k1=k1;target.k2=k2;target.t1=t1;target.t2=t2;target.t3=t3;target.t4=t4;
        target.vmax=vmax;target.vmin=vmin;target.setRefPoint(reference);
    }
}
