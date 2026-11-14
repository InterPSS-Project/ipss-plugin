package org.interpss.dstab.control.oel.psse.oel3c;

import java.util.Collections;
import java.util.Hashtable;
import java.util.LinkedHashMap;
import java.util.Map;
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
import com.interpss.dstab.mach.MachineIfdBase;

/** Auxiliary OEL3C with no network injection and an explicitly compatible summation port. */
public final class Oel3cModel extends DynLoadModelImpl
        implements ICMLStateProvider,Oel3cExciterPort.Limiter,NetworkIterationAware {
    private final Machine machine;
    private final Oel3cExciterPort port;
    private final Oel3cData data;
    private final Hashtable<String,Object> states=new Hashtable<>();
    private Oel3cKernel kernel;
    private double input,output,savedInput,savedOutput,stepInput,stepOutput;
    private boolean ready,solving,predictionPending;
    public Oel3cModel(BaseDStabBus<?,?> bus,Machine machine,String id,Oel3cData data) {
        if(bus==null||machine==null||data==null||machine.getDStabBus()!=bus)
            throw new IllegalArgumentException("OEL3C requires its owning bus, machine and data");
        if(!(machine.getExciter() instanceof Oel3cExciterPort target)
                ||target.oel3cConnection()!=Oel3cExciterPort.Connection.SUMMATION)
            throw new IllegalArgumentException("OEL3C requires a compatible summation-point exciter port");
        this.machine=machine;this.port=target;this.data=data;
        if(!port.bindOel3c(this))throw new IllegalArgumentException("Exciter OEL port is already occupied");
        setId(id);setName("OEL3C");setExtendedDeviceId("OEL3C_"+id+"@"+bus.getId());
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
        ready=solving=predictionPending=false;kernel=null;input=output=0;
        states.put(DStabOutSymbol.OUT_SYMBOL_BUS_DEVICE_ID,getExtendedDeviceId());return true;
    }
    /** The core initializes bus auxiliaries before the owning machine/exciter. */
    @Override public boolean initializeAfterExciter(double initialExciterFieldCurrent){
        ready=predictionPending=false;if(!sameOwner())return false;
        try {
            input=selectedInput(initialExciterFieldCurrent,true);Oel3cKernel candidate=new Oel3cKernel(data);
            candidate.initialize(input);double value=candidate.signals(input).output();
            kernel=candidate;output=value;ready=true;return true;
        }catch(RuntimeException failure){return false;}
    }
    private double selectedInput(double initialVfe,boolean initializing){
        double value=switch(data.effectiveInputFlag()){
            case 2->machine.calculateIfd(MachineIfdBase.EXCITER);
            case 3->initializing?initialVfe:port.oel3cExciterFieldCurrent();
            default->machine.getEfd();
        };
        if(!Double.isFinite(value))throw new IllegalStateException("Nonfinite selected OEL3C physical input");
        return value;
    }
    @Override public boolean nextStep(double dt,DynamicSimuMethod method,int flag){
        if(!ready||!sameOwner()||method!=DynamicSimuMethod.MODIFIED_EULER
                ||!Double.isFinite(dt)||dt<0||(flag!=0&&flag!=1))return false;
        if(!enabled()){
            kernel.cancelPrediction();
            if(predictionPending){input=stepInput;output=stepOutput;predictionPending=false;}
            return true;
        }
        try {
            double sampled=selectedInput(0,false),previousInput=input,previousOutput=output;
            if(!kernel.step(dt,flag,sampled))return false;
            if(dt>0){
                if(flag==0){stepInput=previousInput;stepOutput=previousOutput;predictionPending=true;}
                else predictionPending=false;
            }
            input=sampled;output=kernel.signals(input).output();return true;
        }catch(RuntimeException failure){return false;}
    }
    @Override public double output(){return ready&&sameOwner()&&enabled()?output:0;}
    @Override public double getOutput(){return output();}
    @Override public Object getOutputObject(){return Complex.ZERO;}
    @Override public boolean afterStep(double dt){return ready&&sameOwner();}
    @Override public boolean updateAttributes(boolean netChange){return ready&&sameOwner();}
    @Override public void beginNetworkSolution(Context context){
        if(!ready)return;if(solving)throw new IllegalStateException("OEL3C network solve already active");
        savedInput=input;savedOutput=output;solving=true;
    }
    private void refreshAlgebraicInput() throws DStabSimuException {
        if(!ready||!enabled()||data.filterTime()!=0)return;
        if(!solving||!sameOwner())throw new DStabSimuException("Invalid OEL3C algebraic owner/lifecycle");
        try {input=selectedInput(0,false);output=kernel.signals(input).output();}
        catch(RuntimeException failure){throw new DStabSimuException("OEL3C physical input evaluation failed: "+failure.getMessage());}
    }
    @Override public void beforeNetworkIteration(int iteration)throws DStabSimuException{refreshAlgebraicInput();}
    @Override public void afterNetworkConvergence()throws DStabSimuException{refreshAlgebraicInput();}
    @Override public void endNetworkSolution(boolean converged){
        if(!solving)return;if(!converged){input=savedInput;output=savedOutput;}solving=false;
    }
    public boolean isInitialized(){return ready;}
    public Oel3cData getData(){return data;}
    public Machine getMachine(){return machine;}
    public double getSelectedInput(){return input;}
    public Oel3cKernel.Signals getSignals(){if(!ready)throw new IllegalStateException("Initialize OEL3C first");return kernel.signals(input);}
    @Override public Map<String,Double> getNamedStates(){return ready?kernel.namedStates():Map.of();}
    public Map<String,Double> getPublishedStates(){
        if(!ready)return Map.of();Map<String,Double> values=new LinkedHashMap<>();
        values.put("OEL Input",kernel.signals(input).scaledInput());values.put("Integrator VIOEL",kernel.state().integral());
        return Collections.unmodifiableMap(values);
    }
    @Override public Hashtable<String,Object> getStates(Object ref){
        states.put("OEL3C_OUTPUT",output());if(ready)states.put("OEL3C_ERROR",kernel.signals(input).error());return states;
    }
}
