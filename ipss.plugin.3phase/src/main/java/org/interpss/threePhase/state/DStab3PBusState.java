package org.interpss.threePhase.state;

import java.util.*;
import com.interpss.common.datatype.Complex3x1ValueBean;
import com.interpss.dstab.DStabObjectFactory;
import com.interpss.dstab.DstabFactory;
import com.interpss.state.dstab.DStabBusState;
import com.interpss.state.dstab.DStabGenState;
import org.interpss.numeric.datatype.Complex3x1;
import org.interpss.threePhase.basic.dstab.*;
import org.interpss.threePhase.basic.dstab.impl.DStab3PBusImpl;

/** Phase bus values and contained devices, with identity-preserving list reconstruction. */
public final class DStab3PBusState extends DStabBusState {
    public Complex3x1ValueBean phaseVoltage,initialVoltage,netLoad;
    public DStabPhaseLoadState[] phaseLoads;
    public DStab3PGenState[] phaseGenerators;
    public String[] singleLoadIds,threeLoadIds,phaseGeneratorIds;
    public int[] phaseAMotors,phaseBMotors,phaseCMotors;
    public org.interpss.threePhase.dynamic.model.InductionMotor3PhaseAdapter.ModelState[] phaseAdapters;
    public int[] phaseAdapterDeviceIndices,threePhaseAdapterIndices;
    private int[] phaseMotorIndices(DStab3PBus source,
            java.util.List<org.interpss.threePhase.dynamic.model.DynLoadModel1Phase> motors,com.interpss.core.acsc.PhaseCode phase) {
        return motors.stream().mapToInt(motor->{
            if(motor.getPhase()!=phase||motor.getDStabBus()!=source)throw new IllegalArgumentException("Motor phase/owner does not match containing list");
            var generic=((com.interpss.dstab.BaseDStabBus<?,?>)source).getDynamicBusDeviceList();
            for(int i=0;i<generic.size();i++)if(generic.get(i)==motor)return dynamicDeviceIndices[i];
            throw new IllegalArgumentException("Phase motor is missing from its bus device containment");
        }).toArray();
    }

    public DStab3PBusState(DStab3PBus source) {
        super(source);
        if(source.getClass()!=DStab3PBusImpl.class)throw new IllegalArgumentException("Unsupported phase bus implementation: "+source.getClass().getName());
        var adapters=new ArrayList<org.interpss.threePhase.dynamic.model.InductionMotor3PhaseAdapter>();
        var adapterIndices=new IdentityHashMap<org.interpss.threePhase.dynamic.model.DynLoadModel3Phase,Integer>();
        threePhaseAdapterIndices=source.getThreePhaseDynLoadList().stream().mapToInt(load->{
            if(!(load instanceof org.interpss.threePhase.dynamic.model.InductionMotor3PhaseAdapter adapter))
                throw new IllegalArgumentException("Unsupported three-phase dynamic load: "+load.getClass().getName());
            if(!adapterIndices.containsKey(load)){adapterIndices.put(load,adapters.size());adapters.add(adapter);}
            return adapterIndices.get(load);
        }).toArray();
        phaseAdapters=adapters.stream().map(org.interpss.threePhase.dynamic.model.InductionMotor3PhaseAdapter.ModelState::new)
                .toArray(org.interpss.threePhase.dynamic.model.InductionMotor3PhaseAdapter.ModelState[]::new);
        phaseAdapterDeviceIndices=adapters.stream().mapToInt(adapter->{
            var generic=source.getDynamicBusDeviceList();
            for(int i=0;i<generic.size();i++)if(generic.get(i)==adapter)return dynamicDeviceIndices[i];
            if(adapter.getDStabBus()!=null)throw new IllegalArgumentException("Bound adapter missing from its bus device pool");
            return -1;
        }).toArray();
        phaseAMotors=phaseMotorIndices(source,source.getPhaseADynLoadList(),com.interpss.core.acsc.PhaseCode.A);
        phaseBMotors=phaseMotorIndices(source,source.getPhaseBDynLoadList(),com.interpss.core.acsc.PhaseCode.B);
        phaseCMotors=phaseMotorIndices(source,source.getPhaseCDynLoadList(),com.interpss.core.acsc.PhaseCode.C);
        phaseVoltage=capture(source.get3PhaseVotlages()); initialVoltage=capture(source.get3PhaseInitVoltage()); netLoad=capture(source.get3PhaseNetLoadResults());
        var loads=new LinkedHashMap<String,DStab1PLoad>();
        source.getSinglePhaseLoadList().forEach(load->add(loads,load.getId(),load));
        source.getThreePhaseLoadList().forEach(load->add(loads,load.getId(),(DStab1PLoad)load));
        for(var load:((com.interpss.dstab.BaseDStabBus<?,?>)source).getContributeLoadList()){
            if(load instanceof DStab1PLoad phase)add(loads,load.getId(),phase);
            else if(loads.containsKey(load.getId()))throw new IllegalArgumentException("Ambiguous contained load identity: "+load.getId());
        }
        phaseLoads=loads.values().stream().map(DStabPhaseLoadState::new).toArray(DStabPhaseLoadState[]::new);
        singleLoadIds=source.getSinglePhaseLoadList().stream().map(DStab1PLoad::getId).toArray(String[]::new);
        threeLoadIds=source.getThreePhaseLoadList().stream().map(DStab3PLoad::getId).toArray(String[]::new);
        var generators=new LinkedHashMap<String,DStab3PGen>();
        source.getThreePhaseGenList().forEach(gen->add(generators,gen.getId(),gen));
        for(var gen:((com.interpss.dstab.BaseDStabBus<?,?>)source).getContributeGenList()){
            if(gen instanceof DStab3PGen phase)add(generators,gen.getId(),phase);
            else if(generators.containsKey(gen.getId()))throw new IllegalArgumentException("Ambiguous contained generator identity: "+gen.getId());
        }
        phaseGenerators=generators.values().stream().map(DStab3PGenState::new).toArray(DStab3PGenState[]::new);
        phaseGeneratorIds=source.getThreePhaseGenList().stream().map(DStab3PGen::getId).toArray(String[]::new);
    }
    private static <T> void add(Map<String,T> objects,String id,T object){
        var previous=objects.putIfAbsent(id,object);
        if(previous!=null&&previous!=object)throw new IllegalArgumentException("Ambiguous contained device identity: "+id);
    }
    private static Complex3x1ValueBean capture(Complex3x1 value){return value==null?null:new Complex3x1ValueBean(value);}
    private static Complex3x1 restore(Complex3x1ValueBean value){return value==null?null:value.toComplex3x1();}

    @SuppressWarnings({"rawtypes","unchecked"})
    public static DStab3PBus create(DStab3PBusState state){
        var target=new DStab3PBusImpl();state.restoreFrequencyMeasurement(target);
        state.map2EObject(target);
        var generators=new LinkedHashMap<String,DStab3PGen>();
        for(var gen:state.phaseGenerators){var copy=DStab3PGenState.create(gen);copy.setParentBus(target);generators.put(gen.id,copy);}
        var contributedGen=(List)target.getContributeGenList();contributedGen.clear();
        for(var gen:state.genAry){var copy=generators.containsKey(gen.id)?generators.get(gen.id):DStabGenState.create(gen);copy.setParentBus(target);contributedGen.add(copy);}
        for(var id:state.phaseGeneratorIds)target.getThreePhaseGenList().add(Objects.requireNonNull(generators.get(id)));
        var loads=new LinkedHashMap<String,DStab1PLoad>();
        for(var load:state.phaseLoads){var copy=DStabPhaseLoadState.create(load);copy.setParentBus(target);loads.put(load.id,copy);}
        var contributedLoad=(List)target.getContributeLoadList();contributedLoad.clear();
        for(var load:state.loadAry){
            var copy=loads.get(load.id);
            if(copy!=null)contributedLoad.add(copy);
            else {var scalar=DstabFactory.eINSTANCE.createDStabLoad();load.map2EObject(scalar);scalar.setParentBus(target);contributedLoad.add(scalar);}
        }
        for(var id:state.singleLoadIds)target.getSinglePhaseLoadList().add(Objects.requireNonNull(loads.get(id)));
        for(var id:state.threeLoadIds)target.getThreePhaseLoadList().add((DStab3PLoad)Objects.requireNonNull(loads.get(id)));
        target.setFreq(state.frequency);target.set3PhaseVotlages(restore(state.phaseVoltage));
        target.setThreePhaseInitVoltage(restore(state.initialVoltage));target.set3PhaseNetLoadResults(restore(state.netLoad));
        var dynamic=state.restoreDynamicDevices(target);
        if(state.phaseAMotors!=null)for(int index:state.phaseAMotors)target.getPhaseADynLoadList().add((org.interpss.threePhase.dynamic.model.DynLoadModel1Phase)dynamic.get(index));
        if(state.phaseBMotors!=null)for(int index:state.phaseBMotors)target.getPhaseBDynLoadList().add((org.interpss.threePhase.dynamic.model.DynLoadModel1Phase)dynamic.get(index));
        if(state.phaseCMotors!=null)for(int index:state.phaseCMotors)target.getPhaseCDynLoadList().add((org.interpss.threePhase.dynamic.model.DynLoadModel1Phase)dynamic.get(index));
        if(state.phaseAdapters!=null){
            var adapters=new ArrayList<org.interpss.threePhase.dynamic.model.InductionMotor3PhaseAdapter>();
            for(int i=0;i<state.phaseAdapters.length;i++){
                int index=state.phaseAdapterDeviceIndices[i];
                adapters.add(index<0?state.phaseAdapters[i].create(target,dynamic)
                    :(org.interpss.threePhase.dynamic.model.InductionMotor3PhaseAdapter)dynamic.get(index));
            }
            for(int index:state.threePhaseAdapterIndices)target.getThreePhaseDynLoadList().add(adapters.get(index));
        }
        return target;
    }
}
