package org.interpss.core.dstab.cml.controller.util;

import org.interpss.numeric.datatype.Unit.UnitType;

import com.interpss.DStabObjectFactory;
import com.interpss.common.exp.InterpssException;
import com.interpss.core.aclf.AclfGenCode;
import com.interpss.dstab.BaseDStabBus;
import com.interpss.dstab.BaseDStabNetwork;
import com.interpss.dstab.DStabGen;
import com.interpss.dstab.mach.Eq1Machine;
import com.interpss.dstab.mach.MachineType;

public class DStabTestUtilFunc {
	public static BaseDStabNetwork createTestNetwork()  throws InterpssException {
		BaseDStabNetwork net = DStabObjectFactory.createDStabilityNetwork();
		net.setFrequency(60.0);

<<<<<<< HEAD
		BaseDStabBus bus = DStabObjectFactory.createDStabBus("BusId", net);
=======
		DStabBus bus = (DStabBus) DStabObjectFactory.createDStabBus("BusId", net);
>>>>>>> refs/remotes/origin/cmpldw_merge_06022018
		bus.setName("BusName");
		bus.setBaseVoltage(1000);
		bus.setGenCode(AclfGenCode.GEN_PQ);
		
		DStabGen gen = DStabObjectFactory.createDStabGen("G1");
		bus.getContributeGenList().add(gen);

		Eq1Machine mach = (Eq1Machine)DStabObjectFactory.
							createMachine("MachId", "MachName", MachineType.EQ1_MODEL, net, "BusId", "G1");
		mach.setRating(100, UnitType.mVA, net.getBaseKva());
		mach.setRatedVoltage(1000.0);
		mach.calMultiFactors();
		gen.setMach(mach);
		return net;
	}
}
