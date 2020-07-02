package org.interpss.threePhase.util;

import java.util.function.Function;

import org.interpss.dstab.dynLoad.InductionMotor;
import org.interpss.threePhase.basic.Branch3Phase;
import org.interpss.threePhase.basic.Gen3Phase;
import org.interpss.threePhase.basic.Transformer3Phase;
import org.interpss.threePhase.dynamic.model.DStabGen3PhaseAdapter;
import org.interpss.threePhase.dynamic.model.InductionMotor3PhaseAdapter;

import com.interpss.dstab.DStabGen;

public class ThreePhaseUtilFunction {
	
	
	public static Function<Branch3Phase, Transformer3Phase> threePhaseXfrAptr = bra -> {
		Transformer3Phase adpter = ThreePhaseObjectFactory.create3PXformer();
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
