package org.interpss.dstab.control.exc.simple;

import com.interpss.dstab.controller.cml.wrapper.BlockFieldAnWrapper;
import com.interpss.state.dstab.DelayControlBlockState;

/** Simple-exciter data, runtime parameters, reference point, and delay integration state. */
public final class SimpleExciterState {
    public double ka, ta, vrmax, vrmin, k, t, vmax, vmin, reference;
    public DelayControlBlockState delay;
    public int order;
    public boolean namedStates, initialized, declaredBlockPresent;
    public SimpleExciterState(SimpleExciter source) {
        if (source.getClass()!=SimpleExciter.class || source.isChildController())
            throw new IllegalArgumentException("Simple exciter requires its exact standalone model");
        ka=source.getData().getKa(); ta=source.getData().getTa();
        vrmax=source.getData().getVrmax(); vrmin=source.getData().getVrmin();
        k=source.k; t=source.t; vmax=source.vmax; vmin=source.vmin; reference=source.getRefPoint();
        var wrappers=source.getFieldWrapperList();
        if(wrappers==null && source.delayBlock!=null)
            throw new IllegalArgumentException("Simple exciter has an unbound declared delay block");
        if (wrappers!=null) {
            if(wrappers.size()!=1 || !(wrappers.get(0) instanceof BlockFieldAnWrapper<?> block)
                    || !(block.getField() instanceof com.interpss.dstab.controller.cml.field.block.DelayControlBlock delayBlock)
                    || (source.delayBlock!=null && source.delayBlock!=delayBlock))
                throw new IllegalArgumentException("Simple exciter annotation block is not bound");
            declaredBlockPresent=source.delayBlock!=null; initialized=block.isInitialized();
            delay=new DelayControlBlockState(delayBlock); order=block.getInitOrder();
            namedStates=!source.getNamedStateBlocks().isEmpty();
            if (!"Limit".equals(delay.type))
                throw new IllegalArgumentException("Simple exciter delay requires Limit mode");
        }
    }
    public void restore(SimpleExciter target) {
        target.getData().setKa(ka); target.getData().setTa(ta);
        target.getData().setVrmax(vrmax); target.getData().setVrmin(vrmin);
        if(delay!=null) {
            // Parse with the saved block's parameters, without initializing any machine or block state.
            target.k=delay.gain; target.t=delay.timeConstant;
            target.vmax=delay.maximum; target.vmin=delay.minimum;
            if(!target.parseAnnotation() || !target.mapSymbolStr2Type())
                throw new IllegalArgumentException("Cannot rebind copied simple exciter annotations");
            var wrapper=(BlockFieldAnWrapper<?>)target.getFieldWrapperList().get(0);
            var block=(com.interpss.dstab.controller.cml.field.block.DelayControlBlock)wrapper.getField();
            block.restoreIntegrationState(delay.input,delay.state,delay.derivative);
            wrapper.setInitOrder(order); wrapper.setInitialized(initialized);
            if(declaredBlockPresent)target.delayBlock=block;
            if(namedStates)target.refreshNamedStateBlocks();
        }
        target.k=k; target.t=t; target.vmax=vmax; target.vmin=vmin; target.setRefPoint(reference);
    }
}
