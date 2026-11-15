package org.interpss.dstab.hvdc;

import java.util.ArrayList;
import java.util.Hashtable;
import java.util.List;
import java.util.Objects;
import org.apache.commons.math3.complex.Complex;
import org.interpss.dstab.control.util.StaggeredEulerParticipant;
import com.google.gson.Gson;
import com.interpss.core.aclf.hvdc.HvdcLine2TLCC;
import com.interpss.core.aclf.hvdc.HvdcOperationMode;
import com.interpss.core.aclf.hvdc.LccSourceControls;
import com.interpss.dstab.BaseDStabBus;
import com.interpss.dstab.BaseDStabNetwork;
import com.interpss.dstab.device.DynamicBusDevice;
import com.interpss.dstab.device.DynamicBusDeviceType;
import com.interpss.dstab.device.impl.DynamicBusDeviceImpl;
import com.interpss.state.dstab.DynamicBusDeviceState;

/** Exact source ownership retained before load flow; terminal equations are prepared after it. */
public final class Cdc6tDyrModel extends DynamicBusDeviceImpl implements StaggeredEulerParticipant {
    private record Source(String linkName,double[] constants,String id,boolean active,String ownerId,String inverterBusId) {}
    private static final Gson JSON=new Gson();
    static {
        DynamicBusDeviceState.registerExtension(Cdc6tDyrModel.class,new DynamicBusDeviceState.Extension(
            device->{
                var model=(Cdc6tDyrModel)device;
                if(model.binding!=null)throw new IllegalArgumentException("Capture CDC6T before dynamic initialization; running-state checkpointing is not supported");
                var owner=model.capturedOwner((BaseDStabNetwork<?,?>)model.getDStabBus().getNetwork());
                return JSON.toJson(new Source(model.parameters.linkName(),model.parameters.copySourceConstants(),model.getId(),model.isActive(),
                        owner.getId(),owner.getInverter().getBus().getId()));
            },(bus,json)->{
                var source=JSON.fromJson(json,Source.class);
                var model=new Cdc6tDyrModel(bus,new Cdc6tParameters(source.linkName(),source.constants()));
                model.copiedOwnerId=source.ownerId();model.copiedInverterBusId=source.inverterBusId();
                model.setId(source.id());model.setStatus(source.active());return model;
            }));
    }
    private final Cdc6tParameters parameters;
    private Cdc6tNetworkPreparation.Binding binding;
    private String copiedOwnerId,copiedInverterBusId;

    private Cdc6tDyrModel(BaseDStabBus<?,?> bus,Cdc6tParameters parameters){
        this.parameters=Objects.requireNonNull(parameters);
        setName("CDC6T");setId("CDC6T:"+parameters.linkName());
        setExtendedDeviceId(getId());setDeviceType(DynamicBusDeviceType.DYNAMIC_LOAD);
        setDStabBus(bus);
    }
    public Cdc6tParameters getParameters(){return parameters;}
    public Cdc6tNetworkPreparation.Binding getBinding(){return binding;}

    /** Reject malformed ownership before changing any device list. No solved operating point is required. */
    public static Cdc6tDyrModel attach(BaseDStabNetwork<?,?> network,Cdc6tParameters parameters){
        Objects.requireNonNull(network);Objects.requireNonNull(parameters);
        if(network.isDStabNetInitialized())throw new IllegalStateException("Import CDC6T before dynamic initialization");
        var owner=resolveOwner(network,parameters.linkName());
        if(!owner.isActive()||owner.getOperationMode()!=HvdcOperationMode.REC1_INV1
                ||owner.getRectifier2()!=null||owner.getInverter2()!=null||owner.isPuBasedPowerFlowAlgo())
            throw new IllegalArgumentException("CDC6T requires an active ordinary single rectifier/inverter pair");
        if(!(owner.getExtensionObject() instanceof LccSourceControls controls))
            throw new IllegalArgumentException("CDC6T requires retained LCC source controls");
        if(controls.modeTransferKilovolts()<0||controls.rectifierCapacitorOhms()!=0||controls.inverterCapacitorOhms()!=0)
            throw new IllegalArgumentException("CDC6T requires nonnegative mode-transfer voltage and non-capacitor-commutated equipment");
        if(owner.getRectifier()==null||owner.getInverter()==null
                ||!(owner.getRectifier().getBus() instanceof BaseDStabBus<?,?> rectifier)
                ||!(owner.getInverter().getBus() instanceof BaseDStabBus<?,?> inverter)
                ||rectifier==inverter||!rectifier.isActive()||!inverter.isActive()
                ||rectifier.getNetwork()!=network||inverter.getNetwork()!=network)
            throw new IllegalArgumentException("CDC6T requires distinct active terminals in its owning network");
        for(var converter:List.of(owner.getRectifier(),owner.getInverter()))
            if(converter.getCommutingCapacitor()!=0||converter.getCommutingZ()==null
                    ||converter.getCommutingZ().isNaN()||converter.getCommutingZ().isInfinite()
                    ||converter.getCommutingZ().getReal()<0)
                throw new IllegalArgumentException("CDC6T requires finite non-capacitor-commutated converter impedance");
        for(var model:models(network))if(model.parameters.linkName().equals(parameters.linkName()))
            throw new IllegalArgumentException("Duplicate CDC6T owner: "+parameters.linkName());
        for(var model:Chvdc2DyrModel.models(network))if(model.getParameters().linkName().equals(parameters.linkName()))
            throw new IllegalArgumentException("Static link already has a CHVDC2 owner");
        for(var bus:List.of(rectifier,inverter))for(var device:bus.getDynamicBusDeviceList())
            if(("CDC6T:"+parameters.linkName()).equals(device.getId())
                    ||(parameters.linkName()+":rectifier").equals(device.getId())
                    ||(parameters.linkName()+":inverter").equals(device.getId()))
                throw new IllegalArgumentException("Conflicting CDC6T terminal owner: "+parameters.linkName());
        return new Cdc6tDyrModel(rectifier,parameters);
    }
    private static HvdcLine2TLCC<?> resolveOwner(BaseDStabNetwork<?,?> network,String name){
        HvdcLine2TLCC<?> found=null;
        for(var branch:network.getSpecialBranchList())if(branch instanceof HvdcLine2TLCC<?> candidate&&name.equals(candidate.getName())){
            if(found!=null)throw new IllegalArgumentException("Ambiguous static DC-link name: "+name);
            found=candidate;
        }
        if(found==null)throw new IllegalArgumentException("No static DC-link owner named "+name);
        return found;
    }
    public static List<Cdc6tDyrModel> models(BaseDStabNetwork<?,?> network){
        var result=new ArrayList<Cdc6tDyrModel>();
        for(var bus:network.getBusList())for(var device:bus.getDynamicBusDeviceList())
            if(device instanceof Cdc6tDyrModel model)result.add(model);
        return List.copyOf(result);
    }
    private HvdcLine2TLCC<?> capturedOwner(BaseDStabNetwork<?,?> network){
        if(copiedOwnerId==null)return resolveOwner(network,parameters.linkName());
        var matches=network.getSpecialBranchList().stream()
                .filter(branch->branch instanceof HvdcLine2TLCC<?> &&copiedOwnerId.equals(branch.getId())).toList();
        if(matches.size()!=1)throw new IllegalArgumentException("Copied CDC6T static owner is missing or ambiguous");
        var owner=(HvdcLine2TLCC<?>)matches.getFirst();
        if(owner.getRectifier().getBus()!=getDStabBus()
                ||!copiedInverterBusId.equals(owner.getInverter().getBus().getId()))
            throw new IllegalArgumentException("Copied CDC6T terminal identity changed");
        if(!parameters.linkName().equals(owner.getName())&&!("Hvdc line: "+owner.getId()).equals(owner.getName()))
            throw new IllegalArgumentException("Copied CDC6T owner has a conflicting explicit name");
        return owner;
    }
    /** Prepare every link first. A failed preparation leaves every existing terminal pair intact. */
    public static void prepareNetwork(BaseDStabNetwork<?,?> network){
        var models=models(network);if(models.isEmpty())return;
        if(!network.isLfConverged())throw new IllegalStateException("Solve load flow before initializing imported CDC6T models");
        // The generic branch-copy builder assigns a generated name while reconnecting terminals.
        // Restore only explicitly captured source identity, never infer a link from a bus number.
        for(var model:models)if(model.copiedOwnerId!=null){
            var owner=model.capturedOwner(network);
            if(!model.parameters.linkName().equals(owner.getName())){
                owner.setName(model.parameters.linkName());
            }
        }
        var prepared=new ArrayList<Cdc6tNetworkPreparation.Binding>();
        var oldPorts=new java.util.HashSet<DynamicBusDevice>();
        for(var model:models){
            if(model.binding!=null){oldPorts.add(model.binding.ports().rectifierPort());oldPorts.add(model.binding.ports().inverterPort());}
            var binding=model.isActive()?Cdc6tNetworkPreparation.prepare(network,model.parameters):null;
            if(binding!=null&&binding.owner().getRectifier().getBus()!=model.getDStabBus())
                throw new IllegalArgumentException("Imported CDC6T moved away from its original rectifier bus");
            prepared.add(binding);
        }
        for(var binding:prepared)if(binding!=null)for(var port:List.of(binding.ports().rectifierPort(),binding.ports().inverterPort())){
            var bus=port==binding.ports().rectifierPort()?binding.owner().getRectifier().getBus():binding.owner().getInverter().getBus();
            for(var device:((BaseDStabBus<?,?>)bus).getDynamicBusDeviceList())
                if(!oldPorts.contains(device)&&port.getId().equals(device.getId()))
                    throw new IllegalArgumentException("Conflicting CDC6T terminal port: "+port.getId());
        }
        for(var port:oldPorts)port.setDStabBus(null);
        for(int index=0;index<models.size();index++){
            var binding=prepared.get(index);if(binding!=null)binding.ports().attach();
            models.get(index).binding=binding;
        }
        network.setDStabNetInitialized(false);
    }
    @Override public boolean initStates(BaseDStabBus<?,?> bus){
        if(bus!=getDStabBus()||binding==null)
            throw new IllegalStateException("Imported CDC6T requires DefaultDynamicSimulationSolver or StaggeredEulerSolver initialization");
        return binding.ports().rectifierPort().attachmentIntact();
    }
    @Override public Object getOutputObject(){return Complex.ZERO;}
    @Override public boolean nextStep(double step,com.interpss.dstab.algo.DynamicSimuMethod method,int stage){return true;}
    @Override public boolean afterStep(double step){return true;}
    @Override public boolean updateAttributes(boolean changed){return true;}
    @Override public Hashtable<String,Object> getStates(Object reference){
        var states=new Hashtable<String,Object>();
        states.put(com.interpss.dstab.common.DStabOutSymbol.OUT_SYMBOL_BUS_DEVICE_ID,getExtendedDeviceId());
        return states;
    }
    @Override public int staggeredStateCount(){return 0;}
    @Override public void readStaggeredState(double[] values,int offset){Objects.checkFromIndexSize(offset,0,values.length);}
    @Override public void evaluateStaggered(double step,double[] values,int offset){Objects.checkFromIndexSize(offset,0,values.length);}
    @Override public void applyStaggeredState(double[] values,int offset,double step){Objects.checkFromIndexSize(offset,0,values.length);}
}
