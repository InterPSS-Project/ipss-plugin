package org.interpss.threePhase.util;

import java.util.function.Function;

import org.interpss.dstab.dynLoad.InductionMotor;
import org.interpss.threePhase.basic.acsc.Acsc3PXformer;
import org.interpss.threePhase.basic.dstab.DStab3PBranch;
import org.interpss.threePhase.basic.dstab.DStab3PGen;
import org.interpss.threePhase.dynamic.model.DStabGen3PhaseAdapter;
import org.interpss.threePhase.dynamic.model.InductionMotor3PhaseAdapter;

import com.interpss.dstab.DStabGen;

public class ThreePhaseUtilFunction {
	
	
	public static Function<DStab3PBranch, Acsc3PXformer> threePhaseXfrAptr = bra -> {
		Acsc3PXformer adpter = ThreePhaseObjectFactory.create3PXformer();
		adpter.set3PBranch(bra);
		return adpter;
	};
	
	public static Function<DStabGen, DStabGen3PhaseAdapter> threePhaseGenAptr = gen -> {
		DStabGen3PhaseAdapter adpter = ThreePhaseObjectFactory.create3PDynGenerator(gen.getId());
		adpter.setGen(gen);
		return adpter;
	};
	
	public static Function<InductionMotor, InductionMotor3PhaseAdapter> threePhaseInductionMotorAptr = indMotor -> {
		InductionMotor3PhaseAdapter adpter = new InductionMotor3PhaseAdapter(indMotor);
		
		return adpter;
	};
	
	

}
