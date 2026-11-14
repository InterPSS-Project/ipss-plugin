package org.interpss.dstab.control.oel.psse.oel4c;

import java.util.Hashtable;
import java.util.Map;
import java.util.Objects;
import org.apache.commons.math3.complex.Complex;
import com.interpss.dstab.controller.exc.Oel3cExciterPort;
import com.interpss.core.net.Network;
import com.interpss.dstab.BaseDStabBus;
import com.interpss.dstab.algo.DynamicSimuMethod;
import com.interpss.dstab.common.DStabOutSymbol;
import com.interpss.dstab.common.DStabSimuException;
import com.interpss.dstab.controller.cml.ICMLStateProvider;
import com.interpss.dstab.device.NetworkIterationAware;
import com.interpss.dstab.dynLoad.impl.DynLoadModelImpl;
import com.interpss.dstab.mach.Machine;

/** Bounded typed reactive-power limiter; no record-import or independent coupled acceptance claim. */
public final class Oel4cModel extends DynLoadModelImpl
        implements ICMLStateProvider,Oel3cExciterPort.Limiter,NetworkIterationAware {
    private final Machine machine;private final Oel3cExciterPort port;private final Oel4cData data;
    private final Hashtable<String,Object> states=new Hashtable<>();
    private Oel4cKernel kernel;private Oel4cKernel.Checkpoint accepted;
    private double input,output,savedInput,savedOutput,stepInput,stepOutput,stepSize,effectivePickup;
    private boolean ready,solving,pending,corrected;
    public Oel4cModel(BaseDStabBus<?,?> bus,Machine machine,String id,Oel4cData data){
        if(bus==null||machine==null||machine.getDStabBus()!=bus)
            throw new IllegalArgumentException("OEL4C requires its owning bus and machine");
        this.data=Objects.requireNonNull(data);
        if(!(machine.getExciter() instanceof Oel3cExciterPort target)
                ||target.oel3cConnection()!=Oel3cExciterPort.Connection.SUMMATION)
            throw new IllegalArgumentException("OEL4C requires a compatible summation-point exciter");
        this.machine=machine;port=target;
        if(!port.bindOel3c(this))throw new IllegalArgumentException("Exciter OEL port is occupied");
        setId(id);setName("OEL4C");setExtendedDeviceId("OEL4C_"+id+"@"+bus.getId());
        setLoadPercent(0);setLoadFactor(0);setLoadPQ(Complex.ZERO);setInitLoadPQ(Complex.ZERO);
        setEquivY(Complex.ZERO);setNortonCurInj(Complex.ZERO);setCompensateShuntY(Complex.ZERO);
        setCurrInj2Net(Complex.ZERO);setDStabBus(bus);
    }
    private boolean sameOwner(){return machine.getExciter()==port&&machine.getDStabBus()==getDStabBus()
        &&port.oel3cConnection()==Oel3cExciterPort.Connection.SUMMATION;}
    private boolean enabled(){return isActive()&&machine.isActive()&&getDStabBus().isActive();}
    @Override public boolean initStates(){return initStates(getDStabBus());}
    @Override public boolean initStates(BaseDStabBus<?,?> bus,Machine ignored){return initStates(bus);}
    @Override public boolean initStates(BaseDStabBus<?,?> bus,Network<?,?> ignored){return initStates(bus);}
    @Override public boolean initStates(BaseDStabBus<?,?> bus){
        if(bus!=getDStabBus()||!sameOwner())return false;
        ready=solving=pending=corrected=false;kernel=null;accepted=null;input=output=0;
        try{data.effectivePickup(reactiveMaximum());}catch(RuntimeException failure){return false;}
        states.put(DStabOutSymbol.OUT_SYMBOL_BUS_DEVICE_ID,getExtendedDeviceId());return true;
    }
    @Override public boolean initializeAfterExciter(double unusedFieldCurrent){
        ready=pending=corrected=false;if(!sameOwner())return false;
        try{
            double maximum=reactiveMaximum();
            double q=measure();var candidate=new Oel4cKernel(data);candidate.initialize(maximum);
            double value=candidate.signals(q).output();kernel=candidate;input=q;output=value;
            effectivePickup=data.effectivePickup(maximum);ready=true;return true;
        }catch(RuntimeException failure){return false;}
    }
    private double reactiveMaximum(){
        double ratio=baseRatio();if(data.reactivePickup()!=0)return Double.NaN;
        var limit=machine.getParentGen().getQGenLimit();
        if(limit==null)throw new IllegalStateException("Missing owner reactive maximum");
        return limit.getMax()*ratio;
    }
    /** Static generator limits are on network MVA; controller quantities are on owner machine MVA. */
    private double baseRatio(){
        if(machine.getParentGen()==null)throw new IllegalStateException("Missing owner generator");
        double machineBase=machine.getParentGen().getMvaBase(),networkBase=getDStabBus().getNetwork().getBaseMva();
        if(!Double.isFinite(machineBase)||!Double.isFinite(networkBase)||machineBase<=0||networkBase<=0)
            throw new IllegalStateException("Invalid OEL4C MVA base");
        return networkBase/machineBase;
    }
    /** Recompute from this machine's current network boundary, never a bus aggregate or cached event average. */
    private double measure(){
        Complex voltage=getDStabBus().getVoltage();
        Complex current=machine.getIgen().subtract(voltage.multiply(machine.getYgen()));
        double q=voltage.multiply(current.conjugate()).getImaginary()*baseRatio();
        if(!Double.isFinite(q))throw new IllegalStateException("Nonfinite OEL4C reactive power");return q;
    }
    private void cancelTrial(){
        if(pending){kernel.restore(accepted);input=stepInput;output=stepOutput;pending=corrected=false;accepted=null;}
    }
    @Override public boolean nextStep(double dt,DynamicSimuMethod method,int flag){
        if(!ready||!sameOwner()||method!=DynamicSimuMethod.MODIFIED_EULER
            ||!Double.isFinite(dt)||dt<0||(flag!=0&&flag!=1))return false;
        if(!enabled()){cancelTrial();return true;}
        if(dt>0&&((flag==0&&pending)||(flag==1&&(!pending||corrected||dt!=stepSize))))return false;
        try{
            double q=measure();var checkpoint=dt>0&&flag==0?kernel.checkpoint():null;
            if(!kernel.step(dt,flag,q))return false;
            if(dt>0&&flag==0){accepted=checkpoint;stepInput=input;stepOutput=output;stepSize=dt;pending=true;corrected=false;}
            else if(dt>0)corrected=true;
            input=q;output=kernel.signals(q).output();return true;
        }catch(RuntimeException failure){return false;}
    }
    /** The corrected state is provisional until the solver accepts the step. */
    @Override public boolean afterStep(double dt){
        if(!ready||!sameOwner()||!Double.isFinite(dt)||dt<0)return false;
        if(!enabled()){cancelTrial();return true;}if(dt==0)return true;
        if(!pending)return true;if(!corrected||dt!=stepSize)return false;
        pending=corrected=false;accepted=null;return true;
    }
    @Override public double output(){return ready&&sameOwner()&&enabled()?output:0;}
    @Override public double getOutput(){return output();}
    @Override public Object getOutputObject(){return Complex.ZERO;}
    @Override public boolean updateAttributes(boolean changed){return ready&&sameOwner();}
    @Override public void beginNetworkSolution(Context context){
        if(!ready)return;if(solving)throw new IllegalStateException("OEL4C network solve already active");
        savedInput=input;savedOutput=output;solving=true;
    }
    private void refresh()throws DStabSimuException{
        if(!ready||!enabled())return;
        if(!solving||!sameOwner())throw new DStabSimuException("Invalid OEL4C owner/lifecycle");
        try{double q=measure();double value=kernel.signals(q).output();input=q;output=value;}
        catch(RuntimeException failure){throw new DStabSimuException("OEL4C input evaluation failed: "+failure.getMessage());}
    }
    @Override public void beforeNetworkIteration(int iteration)throws DStabSimuException{refresh();}
    @Override public void afterNetworkConvergence()throws DStabSimuException{refresh();}
    @Override public void endNetworkSolution(boolean converged){
        if(!solving)return;
        if(!converged){if(pending)cancelTrial();else{input=savedInput;output=savedOutput;}}
        solving=false;
    }
    public boolean isInitialized(){return ready;}
    public Machine getMachine(){return machine;}
    public Oel4cData getData(){return data;}
    public double getEffectivePickup(){if(!ready)throw new IllegalStateException("Initialize OEL4C first");return effectivePickup;}
    public double getSampledReactivePower(){if(!ready)throw new IllegalStateException("Initialize OEL4C first");return input;}
    public Oel4cKernel.Signals getSignals(){if(!ready)throw new IllegalStateException("Initialize OEL4C first");return kernel.signals(input);}
    @Override public Map<String,Double> getNamedStates(){return ready?kernel.namedStates():Map.of();}
    @Override public Hashtable<String,Object> getStates(Object ref){
        states.put("OEL4C_OUTPUT",output());if(ready){states.put("OEL4C_Q",input);states.put("OEL4C_TIMER",getSignals().elapsed());}return states;
    }
}
