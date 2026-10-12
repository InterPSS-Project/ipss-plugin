package org.interpss.threePhase.state;

import java.util.*;
import com.interpss.common.datatype.Complex3x1ValueBean;
import com.interpss.common.exp.InterpssException;
import com.interpss.core.net.Bus;
import com.interpss.core.net.Branch;
import com.interpss.state.dstab.*;
import org.interpss.threePhase.dynamic.DStabNetwork3Phase;
import org.interpss.threePhase.dynamic.impl.DStabNetwork3phaseImpl;
import org.interpss.threePhase.util.ThreePhaseObjectFactory;

/** Full phase topology reconstruction using plugin elements and core reference rebinding. */
public final class DStab3PNetworkState extends DStabNetworkState {
    private static final Map<String,java.util.function.Supplier<? extends DStabNetwork3Phase>> FACTORIES=new java.util.concurrent.ConcurrentHashMap<>();
    static {
        FACTORIES.put(DStabNetwork3phaseImpl.class.getName(),ThreePhaseObjectFactory::create3PhaseDStabNetwork);
        com.interpss.state.aclf.Aclf3WBranchState.registerImplementation(
            org.interpss.threePhase.basic.dstab.impl.DStab3W3PBranchImpl.class,ThreePhaseObjectFactory::createBranch3W3Phase);
    }
    /** Register a behavior-only network subtype; extra fields require additional state support. */
    public static void registerImplementation(Class<? extends DStabNetwork3Phase> type,java.util.function.Supplier<? extends DStabNetwork3Phase> factory){
        FACTORIES.putIfAbsent(type.getName(),Objects.requireNonNull(factory));
    }
    public String implementationName;
    public DStab3PBusState[] phaseBuses;
    public DStab3PBranchState[] phaseBranches;
    public boolean loadModelConverted;
    public Map<String,Complex3x1ValueBean> customCurrents;

    public DStab3PNetworkState(DStabNetwork3Phase source){
        super(source,true);
        implementationName=source.getClass().getName();
        if(!FACTORIES.containsKey(implementationName))throw new IllegalArgumentException("Unsupported phase network implementation: "+source.getClass().getName());
        phaseBuses=source.getBusList().stream().map(DStab3PBusState::new).toArray(DStab3PBusState[]::new);
        phaseBranches=source.getBranchList().stream().map(DStab3PBranchState::new).toArray(DStab3PBranchState[]::new);
        busAry=phaseBuses;branchAry=phaseBranches;
        loadModelConverted=source.isLoadModelConverted();
        if(source.get3phaseCustomCurrInjTable()!=null){
            customCurrents=new LinkedHashMap<>();
            source.get3phaseCustomCurrInjTable().forEach((id,current)->customCurrents.put(id,new Complex3x1ValueBean(current)));
        }
    }
    public static boolean supports(DStabNetwork3Phase source){
        try{new DStab3PNetworkState(source);return true;}catch(IllegalArgumentException ex){return false;}
    }
    @Override protected Bus createDynamicBus(DStabBusState state){return DStab3PBusState.create((DStab3PBusState)state);}
    @Override protected Branch createDynamicBranch(DStabBranchState state){return DStab3PBranchState.create((DStab3PBranchState)state);}

    public static DStabNetwork3Phase create(DStab3PNetworkState state)throws InterpssException{
        state.busAry=state.phaseBuses;state.branchAry=state.phaseBranches;
        var factory=FACTORIES.get(state.implementationName);
        if(factory==null)throw new InterpssException("Unregistered phase network implementation: "+state.implementationName);
        var target=factory.get();
        if(target.getAclfNetAdjust()==null){var adjustment=com.interpss.core.aclf.netAdj.NetAdjustFactory.eINSTANCE.createAclfNetAdjustment();target.setAclfNetAdjust(adjustment);adjustment.setAclfNet(target);}
        state.map2EObject(target);
        state.settings.restore(target);target.setLoadModelConverted(state.loadModelConverted);
        if(state.customCurrents!=null){var currents=new Hashtable<String,org.interpss.numeric.datatype.Complex3x1>();
            state.customCurrents.forEach((id,current)->currents.put(id,current.toComplex3x1()));target.set3phaseCustomCurrInjTable(currents);}
        for(var bus:state.phaseBuses)for(var gen:bus.genAry)if(gen.machine!=null){
            gen.machine.create(target,bus.id,gen.id);gen.updateEObject(target.getBus(bus.id).getContributeGen(gen.id));
            for(var phase:bus.phaseGenerators)if(phase.id.equals(gen.id))phase.restorePhaseFields((org.interpss.threePhase.basic.dstab.DStab3PGen)target.getBus(bus.id).getContributeGen(gen.id));
        }
        for(var event:state.events)DynamicEventState.create(event,target);
        return target;
    }
}
