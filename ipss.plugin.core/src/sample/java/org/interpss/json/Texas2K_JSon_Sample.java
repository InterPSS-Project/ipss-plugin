package org.interpss.json;

import org.interpss.optadj.texas2k.Texas2K_Sample_Info;
import org.interpss.util.AclfNetJsonComparator;
import org.interpss.util.FileUtil;

import com.interpss.core.aclf.AclfBus;
import com.interpss.core.aclf.AclfNetwork;
import com.interpss.state.StateObjectFactory;
import com.interpss.state.aclf.AclfNetworkState;

public class Texas2K_JSon_Sample {
	public static void main(String[] args) throws Exception {
		AclfNetwork aclfNet = Texas2K_Sample_Info.loadNetwork();

		System.out.println("Fixed shunts: " + countFixedShunts(aclfNet));
		System.out.println("Switched shunts: " + countSwitchedShuntBuses(aclfNet));
		System.out.println("Switched shunt banks: " + countSwitchedShuntBanks(aclfNet));

		AclfBus bus = aclfNet.getBus("Bus2017");
		System.out.println("Bus2017: " + bus.toString());

		String jsonFile = "ipss.plugin.core/testData/json/texas2k.json";

		FileUtil.writeText2File(jsonFile, new AclfNetworkState(aclfNet).toString());

		AclfNetwork aclfNet1 = StateObjectFactory.createAclfNetwork(jsonFile);

		new AclfNetJsonComparator("Texas2k JSON round-trip").compareJson(aclfNet, aclfNet1);
	}

	private static int countFixedShunts(AclfNetwork net) {
		int count = 0;
		for (var busObj : net.getBusList()) {
			count += ((AclfBus) busObj).getCompensatorList().size();
			if (busObj.getShuntY() != null && busObj.getShuntY().abs() != 0.0) {
				count++;
			}
		}
		return count;
	}

	private static int countSwitchedShuntBuses(AclfNetwork net) {
		int count = 0;
		for (var busObj : net.getBusList()) {
			if (((AclfBus) busObj).isSwitchedShunt()) {
				count++;
			}
		}
		return count;
	}

	private static int countSwitchedShuntBanks(AclfNetwork net) {
		int count = 0;
		for (var busObj : net.getBusList()) {
			AclfBus bus = (AclfBus) busObj;
			if (bus.isSwitchedShunt()) {
				count += bus.getFirstSwitchedShunt(true).getShuntCompensatorList().size();
			}
		}
		return count;
	}
}
