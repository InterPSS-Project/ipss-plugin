package org.interpss.dstab.renewable;

import java.util.*;
import org.apache.commons.math3.complex.Complex;
import com.interpss.dstab.*;
import com.interpss.dstab.algo.DynamicSimuMethod;
import com.interpss.dstab.controller.cml.ICMLStateProvider;
import com.interpss.dstab.device.DynamicGenDevice;
import com.interpss.dstab.device.impl.DynamicBusDeviceImpl;

/** Native 36.7 REGFMB1U network adapter. Kernel quantities use machine base. */
public final class Regfmb1Model extends DynamicBusDeviceImpl implements DynamicGenDevice,ICMLStateProvider {
    private final Regfmb1Data data;
    private DStabGen parent;
    private Regfmb1Kernel kernel;
    private Repca1Model plant;
    private double systemMva,deviceMva,p,q,pOffset,qvOffset,frequencyOffset;
    public Regfmb1Model(DStabGen gen,BaseDStabBus<?,?> bus,String id,Regfmb1Data data) {
        this.data=Objects.requireNonNull(data);setDStabBus(bus);setId(id);setExtendedDeviceId("REGFMB1U_"+id+"@"+bus.getId());
        gen.setPosGenZ(data.sourceImpedance());setParentGen(gen);
    }
    @Override public boolean initStates(BaseDStabBus<?,?> bus) {
        if(parent==null||parent.getGen()==null||bus==null)return false;
        systemMva=bus.getNetwork().getBaseMva();deviceMva=parent.getMvaBase()>0?parent.getMvaBase():systemMva;
        if(!(systemMva>0&&deviceMva>0))return false;
        parent.setZMultiFactor(com.interpss.core.funcImpl.AcscFunction.zMultiFactor.apply(parent));
        kernel=new Regfmb1Kernel(data,bus.getNetwork().getFrequency());
        Complex power=parent.getGen().multiply(systemMva/deviceMva);
        kernel.initialize(Regfmb1Kernel.Terminal.fromPower(bus.getVoltage(),power));
        p=power.getReal();q=power.getImaginary();pOffset=qvOffset=frequencyOffset=0;
        if(plant!=null)plant.initialize(p,q,bus.getVoltageMag());
        return true;
    }
    private boolean ownerActive(){return parent.isActive()&&getDStabBus().isActive();}
    private Regfmb1Kernel.Terminal terminal() {
        Complex v=getDStabBus().getVoltage();var boundary=new Regfmb1Kernel.Terminal(v,Complex.ZERO);
        return new Regfmb1Kernel.Terminal(v,kernel.current(boundary));
    }
    @Override public boolean nextStep(double dt,DynamicSimuMethod method,int flag) {
        if(!ownerActive())return true;
        if(kernel==null||method!=DynamicSimuMethod.MODIFIED_EULER||flag<0||flag>1)return false;
        var t=terminal();
        if(plant!=null&&flag==0&&dt>0)plant.step(dt,t.p(),t.q(),t.voltage().abs(),getDStabBus().getFreq());
        kernel.setReferenceSteps(pOffset+(plant==null?0:plant.getPref()),qvOffset+(plant==null?0:plant.getQref()),frequencyOffset);
        kernel.step(dt,t,flag);return true;
    }
    @Override public Object getOutputObject() {
        if(kernel==null||!ownerActive())return Complex.ZERO;
        Complex v=getDStabBus().getVoltage(),z=parent.getPosGenZ().multiply(parent.getZMultiFactor());
        // Physical current is on machine base; cancel exactly the system-base
        // Norton admittance already included by the core matrix builder.
        return kernel.current(new Regfmb1Kernel.Terminal(v,Complex.ZERO)).multiply(deviceMva/systemMva).add(v.divide(z));
    }
    @Override public boolean updateAttributes(boolean netChange) {
        if(kernel==null)return false;if(!ownerActive()){p=q=0;return true;}
        var t=terminal();p=t.p();q=t.q();return Double.isFinite(p)&&Double.isFinite(q);
    }
    @Override public boolean afterStep(double dt){return true;}
    @Override public DStabGen getParentGen(){return parent;}
    @Override public void setParentGen(DStabGen gen){parent=gen;if(gen!=null)gen.setDynamicGenDevice(this);}
    @Override public Map<String,Double> getNamedStates(){return kernel==null?Map.of():kernel.getNamedStates();}
    @Override public Hashtable<String,Object> getStates(Object ref) {
        var values=new Hashtable<String,Object>();values.put("REGFMB1U_P",getActivePower());values.put("REGFMB1U_Q",getReactivePower());
        values.put(com.interpss.dstab.common.DStabOutSymbol.OUT_SYMBOL_BUS_DEVICE_ID,getExtendedDeviceId());
        if(kernel!=null){values.put("REGFMB1U_SPEED",1+kernel.speedDeviation());values.putAll(getNamedStates());}return values;
    }
    public Regfmb1Data getData(){return data;}
    public Regfmb1Kernel getKernel(){return kernel;}
    public double getActivePower(){return ownerActive()?p:0;}
    public double getReactivePower(){return ownerActive()?q:0;}
    public Repca1Model getPlantController(){return plant;}
    public void setPlantController(Repca1Model value){if(kernel!=null)throw new IllegalStateException("Attach REGFMB1U plant before initialization");plant=value;}
    public void setReferenceOffsets(double active,double reactiveOrVoltage,double frequencyDeviation) {
        if(!Double.isFinite(active)||!Double.isFinite(reactiveOrVoltage)||!Double.isFinite(frequencyDeviation))throw new IllegalArgumentException("Finite REGFMB1U references required");
        pOffset=active;qvOffset=reactiveOrVoltage;frequencyOffset=frequencyDeviation;
    }
}
