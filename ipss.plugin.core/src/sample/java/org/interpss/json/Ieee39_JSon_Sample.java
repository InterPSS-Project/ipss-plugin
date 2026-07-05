package org.interpss.json;

import org.interpss.optadj.ieee39.IEEE39_Sample_Data;
import org.interpss.util.AclfNetJsonComparator;
import org.interpss.util.FileUtil;

import org.interpss.numeric.datatype.Unit.UnitType;

import com.interpss.core.CoreObjectFactory;
import com.interpss.core.aclf.AclfNetwork;
import com.interpss.core.net.Substation;
import com.interpss.state.StateObjectFactory;
import com.interpss.state.aclf.AclfNetworkState;

public class Ieee39_JSon_Sample {
	public static void main(String[] args) throws Exception {
		AclfNetwork aclfNet = IEEE39_Sample_Data.createTestCaseNetwork();

		aclfNet.getBusList().forEach(bus -> bus.setBaseVoltage(345.0, UnitType.kV));
		for (int i = 30; i <= 38; i++) {
			aclfNet.getBus("Bus" + i).setBaseVoltage(22.0, UnitType.kV);
		}

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

		String[][] substationBuses = {
				{"Sub01", "Bus1", "Bus39"},
				{"Sub02", "Bus2", "Bus30"},
				{"Sub03", "Bus3"},
				{"Sub04", "Bus4"},
				{"Sub05", "Bus5"},
				{"Sub06", "Bus6", "Bus31"},
				{"Sub07", "Bus7"},
				{"Sub08", "Bus8"},
				{"Sub09", "Bus9"},
				{"Sub10", "Bus10", "Bus32"},
				{"Sub11", "Bus11"},
				{"Sub12", "Bus12"},
				{"Sub13", "Bus13"},
				{"Sub14", "Bus14"},
				{"Sub15", "Bus15"},
				{"Sub16", "Bus16"},
				{"Sub17", "Bus17"},
				{"Sub18", "Bus18"},
				{"Sub19", "Bus19", "Bus33"},
				{"Sub20", "Bus20", "Bus34"},
				{"Sub21", "Bus21"},
				{"Sub22", "Bus22", "Bus35"},
				{"Sub23", "Bus23", "Bus36"},
				{"Sub24", "Bus24"},
				{"Sub25", "Bus25", "Bus37"},
				{"Sub26", "Bus26"},
				{"Sub27", "Bus27"},
				{"Sub28", "Bus28"},
				{"Sub29", "Bus29", "Bus38"},
		};
		for (String[] entry : substationBuses) {
			Substation substation = CoreObjectFactory.createSubstation(entry[0], aclfNet);
			double maxVoltage = 0.0;
			for (int i = 1; i < entry.length; i++) {
				var bus = aclfNet.getBus(entry[i]);
				bus.setSubstation(substation);
				maxVoltage = Math.max(maxVoltage, bus.getBaseVoltage());
			}
			substation.setVoltLevel(maxVoltage);
		}

		String jsonFile = "ipss.plugin.core/testData/json/ieee39.json";

		FileUtil.writeText2File(jsonFile, new AclfNetworkState(aclfNet).toString());

		AclfNetwork aclfNet1 = StateObjectFactory.createAclfNetwork(jsonFile);

		new AclfNetJsonComparator("IEEE39 JSON round-trip").compareJson(aclfNet, aclfNet1);
	}
}
