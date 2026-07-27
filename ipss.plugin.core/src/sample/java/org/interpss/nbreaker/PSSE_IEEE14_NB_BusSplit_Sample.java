package org.interpss.nbreaker;

import java.util.List;

import org.interpss.fadapter.psse.PSSEDirectParser;

import com.interpss.common.exp.InterpssException;
import com.interpss.core.CoreObjectFactory;
import com.interpss.core.aclf.AclfBus;
import com.interpss.core.aclf.AclfNetwork;
import com.interpss.core.net.Substation;
import com.interpss.core.net.nb.NBEquipConnection;
import com.interpss.core.net.nb.NBSwitch;

/**
 * Sample: import IEEE14 node-breaker RAW and split substation into groups by bus bars.
 */
public class PSSE_IEEE14_NB_BusSplit_Sample {

	/** Relative to {@code ipss.plugin.core} (launch.json cwd). */
	private static final String CASE =
			"testData/psse/nbreaker/IEEE_14_bus_nodeBreaker_rev35_exported.raw";

	public static void main(String[] args) throws InterpssException {
		AclfNetwork net = new PSSEDirectParser(35).parse(CASE);

		Substation sub2 = net.getSubstation("2");

		SubstationNBreakerHelper subHelper = new SubstationNBreakerHelper(sub2);

		NBSwitch busBar = subHelper.findSwitchByName("Sw-BusBars");
		// 0 open, 1 closed, 2 stuck closed (default 1)
		busBar.setCurrentStatus(0);

		int groupNo = subHelper.topoAnalysis();
		System.out.println("Total number of groups: " + groupNo);

		subHelper.printTopoFlags();

		subHelper.printEquipByGroup(groupNo);

		List<NBEquipConnection> group1 = subHelper.getEquipByGroup(1);
		System.out.println("Group 1 equip count: " + group1.size());

		List<NBEquipConnection> group2 = subHelper.getEquipByGroup(2);
		System.out.println("Group 2 equip count: " + group2.size());

		AclfBus bus1 = subHelper.getBusByGroup(1);
		AclfBus bus1Split = CoreObjectFactory.createAclfBus(bus1.getId() + "_split", net).get();
		bus1Split.setName(bus1.getName() + " Split");
		bus1Split.setBaseVoltage(bus1.getBaseVoltage());
		bus1Split.setVoltage(bus1.getVoltage());
		System.out.println("Split bus: " + bus1Split.getId()
				+ " name=" + bus1Split.getName()
				+ " Vbase=" + bus1Split.getBaseVoltage()
				+ " V=" + bus1Split.getVoltage()
				+ " (from " + bus1.getId() + ")");

		// TODO:
		// 1) remove the equip connection objects of group 1 from bus1
		// 2) add removed equip connection objects to bus1Split		
	}
}
