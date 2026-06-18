package org.interpss.json;

import org.interpss.optadj.texas2k.Texas2K_Sample_Info;
import org.interpss.util.AclfNetJsonComparator;
import org.interpss.util.FileUtil;

import com.interpss.core.aclf.AclfNetwork;
import com.interpss.state.StateObjectFactory;
import com.interpss.state.aclf.AclfNetworkState;

public class Texas2K_JSon_Sample {
	public static void main(String[] args) throws Exception {
		AclfNetwork aclfNet = Texas2K_Sample_Info.loadNetwork();
		String jsonFile = "ipss.plugin.core/testData/json/texas2k.json";

		FileUtil.writeText2File(jsonFile, new AclfNetworkState(aclfNet).toString());

		AclfNetwork aclfNet1 = StateObjectFactory.createAclfNetwork(jsonFile);

		new AclfNetJsonComparator("Texas2k JSON round-trip").compareJson(aclfNet, aclfNet1);
	}
}
