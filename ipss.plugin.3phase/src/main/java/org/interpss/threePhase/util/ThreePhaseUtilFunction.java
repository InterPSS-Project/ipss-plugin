package org.interpss.threePhase.util;

import java.util.function.Function;

import org.interpss.dstab.dynLoad.InductionMotor;
import org.interpss.threePhase.basic.dstab.DStab3PBranch;
import org.interpss.threePhase.dynamic.model.DStabGen3PhaseAdapter;
import org.interpss.threePhase.dynamic.model.InductionMotor3PhaseAdapter;

import com.interpss.core.threephase.Static3PXformer;
import com.interpss.core.threephase.Static3PhaseFactory;
import com.interpss.dstab.DStabGen;

public class ThreePhaseUtilFunction {


	public static Function<DStab3PBranch, Static3PXformer> threePhaseXfrAptr = bra -> {
		Static3PXformer adpter = Static3PhaseFactory.eINSTANCE.createStatic3PXformer();
		adpter.setBranch3P(bra);
		return adpter;
	};

	public static Function<DStabGen, DStabGen3PhaseAdapter> threePhaseGenAptr = gen -> {
		DStabGen3PhaseAdapter adpter = ThreePhaseObjectFactory.create3PDynGenerator(gen.getId());
		adpter.setGen(gen);
		return adpter;
	};

    /** Reuse an explicitly configured phase view without creating a second physical load. */
    public static Function<InductionMotor, InductionMotor3PhaseAdapter> threePhaseInductionMotorAptr = motor -> {
        if(motor.getDStabBus() instanceof org.interpss.threePhase.basic.dstab.DStab3PBus bus){
            InductionMotor3PhaseAdapter found=null;
            for(var load:bus.getThreePhaseDynLoadList())if(load instanceof InductionMotor3PhaseAdapter adapter
                    &&adapter.getInductionMotor()==motor){
                if(found!=null&&found!=adapter)throw new IllegalArgumentException("Multiple phase adapters for one induction motor: "+motor.getId());
                found=adapter;
            }
            if(found!=null)return found;
        }
        return new InductionMotor3PhaseAdapter(motor);
    };

    /** Keep physical motors in the solver's generic list and their views in the phase list. */
    public static void normalizeInductionMotorAdapters(org.interpss.threePhase.basic.dstab.DStab3PBus bus){
        var views=new java.util.IdentityHashMap<InductionMotor,InductionMotor3PhaseAdapter>();
        var adapters=new java.util.ArrayList<InductionMotor3PhaseAdapter>();
        for(var load:bus.getThreePhaseDynLoadList())if(load instanceof InductionMotor3PhaseAdapter adapter)adapters.add(adapter);
        for(var device:bus.getDynamicBusDeviceList())if(device instanceof InductionMotor3PhaseAdapter adapter)adapters.add(adapter);
        // Validate the whole set before changing containment.
        for(var adapter:adapters){
            var motor=adapter.getInductionMotor();
            if(motor==null||motor.getDStabBus()!=bus||!bus.getDynamicBusDeviceList().contains(motor)
                    ||adapter.getDStabBus()!=null&&adapter.getDStabBus()!=bus
                    ||adapter.getParentBus()!=bus)
                throw new IllegalArgumentException("Induction adapter must reference a motor on its containing bus");
            var previous=views.putIfAbsent(motor,adapter);
            if(previous!=null&&previous!=adapter)throw new IllegalArgumentException("Multiple phase adapters for one induction motor: "+motor.getId());
            if(adapter.isActive()!=motor.isActive())throw new IllegalArgumentException("Induction adapter and motor activity must agree: "+motor.getId());
        }
        for(var adapter:views.values()){
            if(!bus.getThreePhaseDynLoadList().contains(adapter))bus.getThreePhaseDynLoadList().add(adapter);
            bus.getDynamicBusDeviceList().remove(adapter);
        }
    }



}
