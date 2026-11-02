package org.interpss.dstab.hvdc;

import java.util.Hashtable;
import org.apache.commons.math3.complex.Complex;
import org.interpss.dstab.control.util.StaggeredEulerParticipant;
import com.interpss.dstab.BaseDStabBus;
import com.interpss.dstab.algo.DynamicSimuMethod;
import com.interpss.dstab.device.DynamicBusDeviceType;
import com.interpss.dstab.device.impl.DynamicBusDeviceImpl;

/** Explicit typed attachment for ordinary conduction; not a complete CDC6T import path. */
public final class Cdc6tNormalTerminalPorts {
    private final BaseDStabBus<?,?> rectifier,inverter;
    private final Cdc6tNormalRuntime runtime;
    private final double initialVi,initialCurrent,initialVr;
    private final double[] currents=new double[4];
    private final Port rectifierPort,inverterPort;

    public Cdc6tNormalTerminalPorts(String id,BaseDStabBus<?,?> rectifier,BaseDStabBus<?,?> inverter,
            Cdc6tNormalRuntime runtime,double initialVi,double initialCurrent,double initialVr) {
        this.rectifier=java.util.Objects.requireNonNull(rectifier);this.inverter=java.util.Objects.requireNonNull(inverter);
        this.runtime=java.util.Objects.requireNonNull(runtime);
        if(rectifier==inverter||rectifier.getNetwork()==null||rectifier.getNetwork()!=inverter.getNetwork())
            throw new IllegalArgumentException("Distinct terminals in one network required");
        if(id==null||id.isBlank())throw new IllegalArgumentException("Named link required");
        for(double value:new double[]{initialVi,initialCurrent,initialVr})
            if(!Double.isFinite(value)||value<0)throw new IllegalArgumentException("Finite nonnegative solved DC boundary required");
        this.initialVi=initialVi;this.initialCurrent=initialCurrent;this.initialVr=initialVr;
        rectifierPort=new Port(id+":rectifier",rectifier,true);inverterPort=new Port(id+":inverter",inverter,false);
    }
    /** Explicit registration; callers must not separately convert static external DC power into load. */
    public void attach() {
        if(rectifier.getDynamicBusDeviceList().contains(rectifierPort)||inverter.getDynamicBusDeviceList().contains(inverterPort))
            throw new IllegalStateException("Terminal pair already attached");
        for(var device:rectifier.getDynamicBusDeviceList())if(rectifierPort.getId().equals(device.getId()))throw new IllegalArgumentException("Duplicate rectifier port");
        for(var device:inverter.getDynamicBusDeviceList())if(inverterPort.getId().equals(device.getId()))throw new IllegalArgumentException("Duplicate inverter port");
        // EMF maintains inverse list membership when the bus reference is assigned.
        rectifierPort.setDStabBus(rectifier);inverterPort.setDStabBus(inverter);
    }
    public Port rectifierPort(){return rectifierPort;}
    public Port inverterPort(){return inverterPort;}
    private void refresh() {
        if(!rectifierPort.attachmentIntact())throw new IllegalStateException("Both original DC terminal ports must remain attached");
        Complex vr=rectifier.getVoltage(),vi=inverter.getVoltage();
        runtime.evaluateBoundary(vr.getReal(),vr.getImaginary(),vi.getReal(),vi.getImaginary(),0,currents,0);
    }
    public final class Port extends DynamicBusDeviceImpl implements StaggeredEulerParticipant {
        private final boolean owner;
        private Port(String id,BaseDStabBus<?,?> bus,boolean owner) {
            this.owner=owner;setId(id);setExtendedDeviceId(id);setDeviceType(DynamicBusDeviceType.DYNAMIC_LOAD);
        }
        public boolean isStateOwner(){return owner;}
        public boolean attachmentIntact() {
            return rectifierPort.getDStabBus()==rectifier&&inverterPort.getDStabBus()==inverter
                &&rectifier.getDynamicBusDeviceList().contains(rectifierPort)&&inverter.getDynamicBusDeviceList().contains(inverterPort);
        }
        public boolean isOperational(){return rectifier.isActive()&&inverter.isActive()&&rectifierPort.isActive()&&inverterPort.isActive();}
        @Override public boolean initStates(BaseDStabBus<?,?> bus) {
            if(bus!=getDStabBus()||!attachmentIntact())return false;
            runtime.initialize(initialVi,initialCurrent,initialVr);return true;
        }
        @Override public Object getOutputObject() {
            if(!isOperational())return Complex.ZERO;
            refresh();int offset=owner?0:2;return new Complex(currents[offset],currents[offset+1]);
        }
        @Override public boolean nextStep(double dt,DynamicSimuMethod method,int stage) {
            if(!owner||!isOperational())return true;
            if(method!=DynamicSimuMethod.MODIFIED_EULER)throw new IllegalArgumentException("Use modified Euler or the shared staggered participant");
            refresh();runtime.step(dt,stage);return true;
        }
        @Override public boolean afterStep(double dt){return true;}
        @Override public boolean updateAttributes(boolean changed){return attachmentIntact();}
        @Override public Hashtable<String,Object> getStates(Object reference) {
            var result=new Hashtable<String,Object>();
            result.put(com.interpss.dstab.common.DStabOutSymbol.OUT_SYMBOL_BUS_DEVICE_ID,getExtendedDeviceId());
            if(owner)result.putAll(runtime.getIntegrationStates());return result;
        }
        @Override public int staggeredStateCount(){return owner?3:0;}
        @Override public void readStaggeredState(double[] values,int offset){if(owner)runtime.readStaggeredState(values,offset);else java.util.Objects.checkFromIndexSize(offset,0,values.length);}
        @Override public void evaluateStaggered(double h,double[] values,int offset){if(owner){refresh();runtime.evaluateStaggered(h,values,offset);}else java.util.Objects.checkFromIndexSize(offset,0,values.length);}
        @Override public void applyStaggeredState(double[] values,int offset,double h){if(owner)runtime.applyStaggeredState(values,offset,h);else java.util.Objects.checkFromIndexSize(offset,0,values.length);}
    }
}
