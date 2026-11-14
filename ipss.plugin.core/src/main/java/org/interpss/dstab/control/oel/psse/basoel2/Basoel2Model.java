package org.interpss.dstab.control.oel.psse.basoel2;

import java.util.Collections;
import java.util.Hashtable;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import org.apache.commons.math3.complex.Complex;
import com.interpss.dstab.controller.exc.Basoel2ExciterPort;
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

/** Typed summation runtime. Native import and reference-dialect acceptance remain separate. */
public final class Basoel2Model extends DynLoadModelImpl
        implements ICMLStateProvider,Basoel2ExciterPort.Limiter,NetworkIterationAware {
    private final Machine machine;
    private final Basoel2ExciterPort port;
    private final Basoel2Data data;
    private final Basoel2Kernel.TimerSemantics timerSemantics;
    private final Hashtable<String,Object> states=new Hashtable<>();
    private Basoel2Kernel kernel;
    private Basoel2Kernel.Inputs input,savedInput,stepInput;
    private double output,savedOutput,stepOutput;
    private boolean ready,solving,pending,corrected;

    public Basoel2Model(BaseDStabBus<?,?> bus,Machine machine,String id,Basoel2Data data,
            Basoel2Kernel.TimerSemantics timerSemantics){
        if(bus==null||machine==null||machine.getDStabBus()!=bus)
            throw new IllegalArgumentException("BASOEL2 requires its owning bus and machine");
        this.data=Objects.requireNonNull(data);this.timerSemantics=Objects.requireNonNull(timerSemantics);
        if(!(machine.getExciter() instanceof Basoel2ExciterPort target)
                ||target.oel3cConnection()!=Basoel2ExciterPort.Connection.SUMMATION)
            throw new IllegalArgumentException("BASOEL2 requires a compatible summation-point exciter");
        this.machine=machine;port=target;
        if(!port.bindOel3c(this))throw new IllegalArgumentException("Exciter OEL port is occupied");
        setId(id);setName("BASOEL2");setExtendedDeviceId("BASOEL2_"+id+"@"+bus.getId());
        setLoadPercent(0);setLoadFactor(0);setLoadPQ(Complex.ZERO);setInitLoadPQ(Complex.ZERO);
        setEquivY(Complex.ZERO);setNortonCurInj(Complex.ZERO);setCompensateShuntY(Complex.ZERO);
        setCurrInj2Net(Complex.ZERO);setDStabBus(bus);
    }
    private boolean sameOwner(){return machine.getExciter()==port&&machine.getDStabBus()==getDStabBus()
        &&port.oel3cConnection()==Basoel2ExciterPort.Connection.SUMMATION;}
    private boolean enabled(){return isActive()&&machine.isActive()&&getDStabBus().isActive();}
    @Override public boolean initStates(){return initStates(getDStabBus());}
    @Override public boolean initStates(BaseDStabBus<?,?> bus,Machine ignored){return initStates(bus);}
    @Override public boolean initStates(BaseDStabBus<?,?> bus,Network<?,?> ignored){return initStates(bus);}
    @Override public boolean initStates(BaseDStabBus<?,?> bus){
        if(bus!=getDStabBus()||!sameOwner())return false;
        ready=solving=pending=corrected=false;kernel=null;input=null;output=0;
        states.put(DStabOutSymbol.OUT_SYMBOL_BUS_DEVICE_ID,getExtendedDeviceId());return true;
    }
    @Override public boolean initializeAfterExciter(double initialVfe){
        ready=pending=corrected=false;if(!sameOwner())return false;
        try{
            var sampled=sample(initialVfe,true);var candidate=new Basoel2Kernel(data,timerSemantics);
            candidate.initialize(sampled);double value=candidate.signals(sampled).total();
            kernel=candidate;input=sampled;output=value;ready=true;return true;
        }catch(RuntimeException failure){return false;}
    }
    private Basoel2Kernel.Inputs sample(double initialVfe,boolean initializing){
        double selected=switch(data.inputFlag()){
            case 0->machine.getEfd();
            case 1->machine.calculateIfd(MachineIfdBase.EXCITER);
            case 2->initializing?initialVfe:port.oel3cExciterFieldCurrent();
            default->throw new IllegalStateException("Invalid BASOEL2 input selector");
        };
        return new Basoel2Kernel.Inputs(selected,getDStabBus().getFreq(),
            getDStabBus().getVoltageMag(),port.basoel2VoltageReference());
    }
    private void cancelTrial(){
        kernel.cancelStep();
        if(pending){input=stepInput;output=stepOutput;pending=corrected=false;}
    }
    @Override public boolean nextStep(double dt,DynamicSimuMethod method,int flag){
        if(!ready||!sameOwner()||method!=DynamicSimuMethod.MODIFIED_EULER
                ||!Double.isFinite(dt)||dt<0||(flag!=0&&flag!=1))return false;
        if(!enabled()){cancelTrial();return true;}
        try{
            var sampled=sample(0,false);var previous=input;double previousOutput=output;
            if(!kernel.step(dt,flag,sampled))return false;
            if(dt>0){
                if(flag==0){stepInput=previous;stepOutput=previousOutput;pending=true;corrected=false;}
                else corrected=true;
            }
            input=sampled;output=kernel.signals(input).total();return true;
        }catch(RuntimeException failure){return false;}
    }
    /** Timer changes are discrete endpoint actions, never network-iteration side effects. */
    @Override public boolean afterStep(double dt){
        if(!ready||!sameOwner()||!Double.isFinite(dt)||dt<0)return false;
        if(!enabled()){cancelTrial();return true;}
        if(dt==0)return true;
        if(!pending)return true;
        if(!corrected)return false;
        try{
            var sampled=sample(0,false);
            if(!kernel.acceptEndpoint(dt,sampled))return false;
            input=sampled;output=kernel.signals(input).total();pending=corrected=false;return true;
        }catch(RuntimeException failure){return false;}
    }
    @Override public double output(){return ready&&sameOwner()&&enabled()?output:0;}
    @Override public double getOutput(){return output();}
    @Override public Object getOutputObject(){return Complex.ZERO;}
    @Override public boolean updateAttributes(boolean changed){return ready&&sameOwner();}
    @Override public void beginNetworkSolution(Context context){
        if(!ready)return;if(solving)throw new IllegalStateException("BASOEL2 solve already active");
        savedInput=input;savedOutput=output;solving=true;
    }
    private void refresh()throws DStabSimuException{
        if(!ready||!enabled())return;
        if(!solving||!sameOwner())throw new DStabSimuException("Invalid BASOEL2 owner/lifecycle");
        try{var sampled=sample(0,false);double value=kernel.signals(sampled).total();input=sampled;output=value;}
        catch(RuntimeException failure){throw new DStabSimuException("BASOEL2 physical input evaluation failed: "+failure.getMessage());}
    }
    @Override public void beforeNetworkIteration(int iteration)throws DStabSimuException{refresh();}
    @Override public void afterNetworkConvergence()throws DStabSimuException{refresh();}
    @Override public void endNetworkSolution(boolean converged){
        if(!solving)return;if(!converged){input=savedInput;output=savedOutput;}solving=false;
    }
    public boolean isInitialized(){return ready;}
    public Machine getMachine(){return machine;}
    public Basoel2Data getData(){return data;}
    public Basoel2Kernel.TimerSemantics getTimerSemantics(){return timerSemantics;}
    public Basoel2Kernel.Inputs getSampledInput(){if(!ready)throw new IllegalStateException("Initialize BASOEL2 first");return input;}
    public Basoel2Kernel.Signals getSignals(){if(!ready)throw new IllegalStateException("Initialize BASOEL2 first");return kernel.signals(input);}
    public java.util.List<Basoel2Kernel.Timer> getTimers(){return ready?kernel.timers():java.util.List.of();}
    @Override public Map<String,Double> getNamedStates(){return ready?kernel.namedStates():Map.of();}
    public Map<String,Double> getPublishedStates(){
        if(!ready)return Map.of();var signals=kernel.signals(input);Map<String,Double> values=new LinkedHashMap<>();
        values.put("OEL Input",signals.sensedInput());values.put("PI integral",kernel.state().integral());
        values.put("Sensed terminal frequency",signals.sensedFrequency());return Collections.unmodifiableMap(values);
    }
    @Override public Hashtable<String,Object> getStates(Object ref){
        states.put("BASOEL2_OUTPUT",output());if(ready)states.put("BASOEL2_REFERENCE",kernel.signals(input).reference());return states;
    }
}
