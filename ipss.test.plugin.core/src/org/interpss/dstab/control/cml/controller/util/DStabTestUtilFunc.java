package org.interpss.dstab.control.cml.controller.util;

import org.interpss.numeric.datatype.Unit.UnitType;

import com.interpss.DStabObjectFactory;
import com.interpss.core.aclf.AclfGenCode;
import com.interpss.dstab.DStabBus;
import com.interpss.dstab.DStabilityNetwork;
import com.interpss.dstab.mach.Eq1Machine;
import com.interpss.dstab.mach.MachineType;

public class DStabTestUtilFunc {
	public static DStabilityNetwork createTestNetwork() {
		DStabilityNetwork net = DStabObjectFactory.createDStabilityNetwork();
		net.setFrequency(60.0);

		DStabBus bus = DStabObjectFactory.createDStabBus("BusId", net);
		bus.setName("BusName");
		bus.setBaseVoltage(1000);
		bus.setGenCode(AclfGenCode.GEN_PQ);

		Eq1Machine mach = (Eq1Machine)DStabObjectFactory.
							createMachine("MachId", "MachName", MachineType.EQ1_MODEL, net, "BusId");
		mach.setRating(100, UnitType.mVA, net.getBaseKva());
		mach.setRatedVoltage(1000.0);
		mach.setMultiFactors(bus);
		mach.setDStabBus(bus);
		return net;
	}
}
