package org.interpss.dstab.control.exc.psse.expic1;

import java.lang.reflect.Field;
import java.util.Map;
import org.interpss.dstab.control.util.IntegrationStateAccess;
import org.interpss.dstab.control.util.IntegrationStateMonitor;
import org.interpss.dstab.control.util.IntegrationStepAware;
import org.interpss.dstab.control.util.StaggeredEulerParticipant;
import com.interpss.dstab.BaseDStabBus;
import com.interpss.dstab.algo.DynamicSimuMethod;
import com.interpss.dstab.controller.cml.ICMLMachineVoltageProvider;
import com.interpss.dstab.controller.cml.annotate.AnController;
import com.interpss.dstab.controller.cml.annotate.AnnotateExciter;
import com.interpss.dstab.mach.Machine;
import com.interpss.dstab.mach.MachineIfdBase;

/** EXPIC1 proportional/integral excitation with machine-base bridge measurements. */
@AnController(input="mach.vt",output="this.outputSignal",refPoint="this.reference",display={})
public final class Expic1Exciter extends AnnotateExciter implements IntegrationStepAware,IntegrationStateAccess,StaggeredEulerParticipant {
    private final Expic1Kernel kernel;
    private boolean initialized;
    public double outputSignal,reference;
    public Expic1Exciter(String id,Expic1Data data,Machine machine){
        super(id,"EXPIC1","Excitation");kernel=new Expic1Kernel(data);this._data=data;setMachine(machine);
    }
    public Expic1Data getData(){return kernel.getData();}
    public Expic1Kernel getKernel(){return kernel;}
    @Override public void configureIntegrationStep(double dt){
        if(!Double.isFinite(dt)||dt<=0)throw new IllegalArgumentException("Positive integration step required");
    }
    private double sensed(Machine machine){
        return machine instanceof ICMLMachineVoltageProvider provider?provider.getCmlMachineVoltage():machine.getDStabBus().getVoltageMag();
    }
    private double stabilizer(Machine machine){return machine.getStabilizer()==null?0:machine.getStabilizer().getOutput(machine);}
    private double bridge(Machine machine){return kernel.bridgeVoltage(machine.getDStabBus().getVoltage(),machine.getIxy(),machine.calculateIfd(MachineIfdBase.EXCITER));}
    private void observe(Machine machine){kernel.observe(sensed(machine),bridge(machine),stabilizer(machine));}
    @Override public boolean initStates(BaseDStabBus<?,?> bus,Machine machine){
        try{kernel.initialize(machine.getEfd(),sensed(machine),bridge(machine),stabilizer(machine));}
        catch(IllegalArgumentException failure){return false;}
        initialized=true;reference=kernel.getReference();outputSignal=kernel.output();return true;
    }
    @Override public boolean nextStep(double dt,DynamicSimuMethod method,Machine machine,int flag){
        if(!initialized)return false;
        if(method==DynamicSimuMethod.MODIFIED_EULER)kernel.step(dt,flag,sensed(machine),bridge(machine),stabilizer(machine));
        else return false;
        outputSignal=kernel.output();return Double.isFinite(outputSignal);
    }
    @Override public double getOutput(Machine machine){if(!initialized)return machine.getEfd();observe(machine);outputSignal=kernel.output();return outputSignal;}
    @Override public double getRefPoint(){return kernel.getReference();}
    @Override public void setRefPoint(double value){kernel.setReference(value);reference=value;}
    @Override public Map<String,Double> getNamedStates(){if(!initialized)return Map.of();observe(getMachine());return kernel.getNamedStates();}
    @Override public Map<String,Double> getIntegrationStates(){return kernel.getIntegrationStates();}
    @Override public IntegrationStateMonitor.Snapshot getStateDerivativeSnapshot(){return kernel.getStateDerivativeSnapshot();}
    @Override public int staggeredStateCount(){return kernel.staggeredStateCount();}
    @Override public void readStaggeredState(double[] values,int offset){kernel.readStaggeredState(values,offset);}
    @Override public void evaluateStaggered(double stepSec,double[] rates,int offset){
        observe(getMachine());kernel.evaluateStaggered(stepSec,rates,offset);outputSignal=kernel.output();
    }
    @Override public void applyStaggeredState(double[] values,int offset,double stepSec){
        kernel.applyStaggeredState(values,offset,stepSec);outputSignal=kernel.output();
    }
    @Override public AnController getAnController(){return getClass().getAnnotation(AnController.class);}
    @Override public Field getField(String name)throws Exception{return getClass().getField(name);}
    @Override public Object getFieldObject(Field field)throws Exception{return field.get(this);}
}
