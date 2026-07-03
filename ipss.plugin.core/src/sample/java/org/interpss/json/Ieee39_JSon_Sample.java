package org.interpss.json;

import org.interpss.optadj.ieee39.IEEE39_Sample_Data;
import org.interpss.util.AclfNetJsonComparator;
import org.interpss.util.FileUtil;

import com.interpss.core.aclf.AclfBus;
import com.interpss.core.aclf.AclfNetwork;
import com.interpss.state.StateObjectFactory;
import com.interpss.state.aclf.AclfNetworkState;

public class Ieee39_JSon_Sample {
	public static void main(String[] args) throws Exception {
		AclfNetwork aclfNet = IEEE39_Sample_Data.createTestCaseNetwork();

		aclfNet.getBusList().forEach(bus -> {
			bus.setExtUID("ExtUID_"+bus.getName());
			bus.getContributeGenList().forEach(gen -> {
				gen.setExtUID("ExtUID_" + gen.getName());
			});
			bus.getContributeLoadList().forEach(load -> {
				load.setExtUID("ExtUID_"+load.getName());
			});
		});

		aclfNet.getBranchList().forEach(branch -> {
			branch.setExtUID("ExtUID_" + branch.getName());
		});

		String jsonFile = "ipss.plugin.core/testData/json/ieee39.json";

		FileUtil.writeText2File(jsonFile, new AclfNetworkState(aclfNet).toString());

		AclfNetwork aclfNet1 = StateObjectFactory.createAclfNetwork(jsonFile);

		new AclfNetJsonComparator("IEEE39 JSON round-trip").compareJson(aclfNet, aclfNet1);
	}
}
