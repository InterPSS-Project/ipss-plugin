package org.interpss.dstab.renewable;

import java.util.*;
import org.apache.commons.math3.complex.Complex;
import com.interpss.dstab.*;
import com.interpss.dstab.algo.DynamicSimuMethod;
import com.interpss.dstab.controller.cml.ICMLStateProvider;
import com.interpss.dstab.device.DynamicGenDevice;
import com.interpss.dstab.device.impl.DynamicBusDeviceImpl;

/** Native REGFMC1U hybrid network adapter. All kernel quantities use machine base.
 * No REPCA1 alias/plant adapter is inferred for the distinct REPCGFMC1 interface. */
public final class Regfmc1Model extends DynamicBusDeviceImpl implements DynamicGenDevice,ICMLStateProvider {
    private final Regfmc1Data data;
    private DStabGen parent;
    private Regfmc1Kernel kernel;
    private double systemMva,deviceMva,p,q,pOffset,qOffset,vOffset,wOffset,gfmPower,gfmIq;
    public Regfmc1Model(DStabGen gen,BaseDStabBus<?,?> bus,String id,Regfmc1Data data) {
        this.data=Objects.requireNonNull(data);Objects.requireNonNull(gen);Objects.requireNonNull(bus);
        setDStabBus(bus);setId(id);setExtendedDeviceId("REGFMC1U_"+id+"@"+bus.getId());
        gen.setPosGenZ(data.sourceImpedance());setParentGen(gen);
    }
    @Override public boolean initStates(BaseDStabBus<?,?> bus) {
        if(parent==null||parent.getGen()==null||bus==null)return false;
        systemMva=bus.getNetwork().getBaseMva();deviceMva=parent.getMvaBase();
        if(!Double.isFinite(systemMva)||!Double.isFinite(deviceMva)||systemMva<=0||deviceMva<=0)return false;
        double factor=com.interpss.core.funcImpl.AcscFunction.zMultiFactor.apply(parent);
        if(!Double.isFinite(factor)||factor<=0)return false;parent.setZMultiFactor(factor);
        kernel=new Regfmc1Kernel(data,bus.getNetwork().getFrequency());
        Complex power=parent.getGen().multiply(systemMva/deviceMva);kernel.initialize(bus.getVoltage(),power);
        p=power.getReal();q=power.getImaginary();pOffset=qOffset=vOffset=wOffset=gfmPower=gfmIq=0;return true;
    }
    private boolean ownerActive(){return parent!=null&&parent.isActive()&&getDStabBus().isActive();}
    @Override public boolean nextStep(double dt,DynamicSimuMethod method,int flag) {
        if(!ownerActive())return true;
        if(kernel==null||method!=DynamicSimuMethod.MODIFIED_EULER||flag<0||flag>1)return false;
        kernel.setReferenceSteps(pOffset,qOffset,vOffset,wOffset,gfmPower,gfmIq);
        kernel.step(dt,getDStabBus().getVoltage(),flag);return true;
    }
    @Override public Object getOutputObject() {
        if(kernel==null||!ownerActive())return Complex.ZERO;
        Complex v=getDStabBus().getVoltage(),z=parent.getPosGenZ().multiply(parent.getZMultiFactor());
        return kernel.boundary(v).totalCurrent().multiply(deviceMva/systemMva).add(v.divide(z));
    }
    @Override public boolean updateAttributes(boolean netChange) {
        if(kernel==null)return false;if(!ownerActive()){p=q=0;return true;}
        Complex v=getDStabBus().getVoltage(),power=v.multiply(kernel.boundary(v).totalCurrent().conjugate());
        p=power.getReal();q=power.getImaginary();return Double.isFinite(p)&&Double.isFinite(q);
    }
    @Override public boolean afterStep(double dt){return true;}
    @Override public DStabGen getParentGen(){return parent;}
    @Override public void setParentGen(DStabGen gen){parent=gen;if(gen!=null)gen.setDynamicGenDevice(this);}
    @Override public Map<String,Double> getNamedStates(){return kernel==null?Map.of():kernel.getNamedStates();}
    @Override public Hashtable<String,Object> getStates(Object ref) {
        var values=new Hashtable<String,Object>();values.put("REGFMC1U_P",getActivePower());values.put("REGFMC1U_Q",getReactivePower());
        values.put(com.interpss.dstab.common.DStabOutSymbol.OUT_SYMBOL_BUS_DEVICE_ID,getExtendedDeviceId());
        if(kernel!=null){values.put("REGFMC1U_SPEED",1+kernel.speedDeviation());values.putAll(getNamedStates());}return values;
    }
    public Regfmc1Data getData(){return data;}
    public Regfmc1Kernel getKernel(){return kernel;}
    public double getActivePower(){return ownerActive()?p:0;}
    public double getReactivePower(){return ownerActive()?q:0;}
    public void setReferenceOffsets(double activeGfl,double reactiveGfl,double voltage,double speedDeviation,double activeGfm,double reactiveGfmCurrent) {
        for(double value:new double[]{activeGfl,reactiveGfl,voltage,speedDeviation,activeGfm,reactiveGfmCurrent})
            if(!Double.isFinite(value))throw new IllegalArgumentException("Finite REGFMC1U references required");
        pOffset=activeGfl;qOffset=reactiveGfl;vOffset=voltage;wOffset=speedDeviation;gfmPower=activeGfm;gfmIq=reactiveGfmCurrent;
    }
}
