package org.interpss.json;

import java.io.File;
import java.io.FileReader;

import org.interpss.optadj.texas2k.Texas2K_Sample_Info;
import org.interpss.util.AclfNetJsonComparator;
import org.interpss.util.FileUtil;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import com.interpss.core.aclf.AclfNetwork;
import com.interpss.state.aclf.AclfNetworkState;

public class Texas2K_JSon_Sample {
	public static void main(String[] args) throws Exception {
		AclfNetwork aclfNet = Texas2K_Sample_Info.loadNetwork();
		
		FileUtil.writeText2File("ipss.plugin.core/testData/json/texas2k.json", new AclfNetworkState(aclfNet).toString());

		JsonElement obj = JsonParser.parseReader(new FileReader(new File("ipss.plugin.core/testData/json/texas2k.json")));

		Gson gson = new GsonBuilder()
		        .serializeSpecialFloatingPointValues()
		        .create();
		AclfNetwork aclfNet1 = AclfNetworkState.create(gson.fromJson(obj, AclfNetworkState.class));

		new AclfNetJsonComparator("Texas2k JSON round-trip").compareJson(aclfNet, aclfNet1);
	}
}
