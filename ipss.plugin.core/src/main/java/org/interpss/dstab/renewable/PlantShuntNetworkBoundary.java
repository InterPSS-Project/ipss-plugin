package org.interpss.dstab.renewable;

import java.util.*;
import org.apache.commons.math3.complex.Complex;
import com.interpss.core.aclf.ShuntCompensator;
import com.interpss.dstab.BaseDStabBus;
import com.interpss.dstab.BaseDStabNetwork;

/** Shared physical shunt binding. Individual nominal G+jB is mandatory; no
 * aggregate conductance inference or voltage-squared conversion is allowed.
 * The caller supplies measured reactive power in its kernel's native units. */
public final class PlantShuntNetworkBoundary {
    private record Fixed(BaseDStabBus<?,?> bus,ShuntCompensator device,Complex nominal) {}
    private final BaseDStabNetwork<?,?> network;
    private final List<RepccData.Shunt> keys;
    private final boolean enabled,branchFound;
    private final RepccShuntController controller;
    private Map<RepccData.Shunt,Fixed> fixed=Map.of();
    private boolean initialized;

    public PlantShuntNetworkBoundary(RepccData data,BaseDStabNetwork<?,?> network,boolean branchFound){
        this(network,data.shunts(),new RepccShuntController(data),branchFound,data.switchingFlag()==1&&data.switchingThresholdsValid());
    }
    public PlantShuntNetworkBoundary(HplntdData data,BaseDStabNetwork<?,?> network,boolean branchFound){
        this(network,data.shunts(),new RepccShuntController(data),branchFound,data.switchingFlag()==1&&data.switchingThresholdsValid());
    }
    private PlantShuntNetworkBoundary(BaseDStabNetwork<?,?> network,List<RepccData.Shunt> keys,
            RepccShuntController controller,boolean branchFound,boolean requested){
        this.network=Objects.requireNonNull(network);this.keys=List.copyOf(keys);this.controller=controller;
        this.branchFound=branchFound;enabled=requested&&branchFound;
    }
    /** Resolve the whole set before publishing any binding or controller state. */
    public void initialize(Map<RepccData.Shunt,Complex> nominalSystemAdmittances){
        Objects.requireNonNull(nominalSystemAdmittances);
        var resolved=new LinkedHashMap<RepccData.Shunt,Fixed>();var devices=new ArrayList<RepccShuntController.Device>();
        if(enabled)for(var key:keys){
            if(key.bus()==0)continue;
            if(resolved.containsKey(key))throw new IllegalArgumentException("Duplicate plant shunt "+key);
            var bus=network.getBus("Bus"+key.bus());
            if(bus==null)throw new IllegalArgumentException("Plant shunt bus not found: "+key);
            var matches=bus.getCompensatorList().stream().filter(s->s.getId().equals(key.id())).toList();
            if(matches.size()!=1)throw new IllegalArgumentException("One named fixed shunt required: "+key);
            var device=matches.getFirst();var nominal=nominalSystemAdmittances.get(key);
            if(nominal==null||!Double.isFinite(nominal.getReal())||!Double.isFinite(nominal.getImaginary()))
                throw new IllegalArgumentException("Finite individual nominal shunt G+jB required: "+key);
            if(device.getSteps()!=1||nominal.getImaginary()!=device.getB())
                throw new IllegalArgumentException("Exact single-step nominal shunt admittance required: "+key);
            resolved.put(key,new Fixed(bus,device,nominal));
            devices.add(new RepccShuntController.Device(key,nominal.getImaginary(),device.isActive()));
        }
        controller.initialize(devices,branchFound);fixed=Map.copyOf(resolved);initialized=true;
    }
    /** Accepted endpoint only. Status/cache updates survive subsequent Y rebuilds. */
    public List<RepccShuntController.Action> advance(double time,double reactivePower){
        if(!initialized)throw new IllegalStateException("Initialize physical plant shunts first");
        if(controller.isEnabled()){
            if(network.getYMatrix()==null)throw new IllegalStateException("Dynamic Y matrix required before shunt switching");
            for(var slot:controller.slots()){
                var binding=fixed.get(slot.key());
                if(binding!=null&&binding.device().isActive()!=slot.inService())
                    throw new IllegalStateException("External shunt status changed; reinitialize plant boundary: "+slot.key());
            }
        }
        var actions=controller.advance(time,reactivePower);
        for(var action:actions){
            var binding=fixed.get(action.key());var delta=binding.nominal().multiply(action.inService()?1:-1);
            binding.device().setStatus(action.inService());
            binding.bus().setShuntY(binding.bus().getShuntY().add(new Complex(delta.getReal(),0)));
            binding.bus().applyPositiveSequenceShuntAdmittanceChange(delta);
            if(binding.bus().isActive()){
                int index=binding.bus().getSortNumber();network.getYMatrix().addToA(delta,index,index);network.setYMatrixDirty(true);
            }
        }
        return actions;
    }
    public RepccShuntController controller(){
        if(!initialized)throw new IllegalStateException("Initialize physical plant shunts first");return controller;
    }
}
