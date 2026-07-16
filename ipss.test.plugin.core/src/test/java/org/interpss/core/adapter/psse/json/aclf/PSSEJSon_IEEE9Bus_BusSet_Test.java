package org.interpss.core.adapter.psse.json.aclf;
 
import static org.junit.jupiter.api.Assertions.assertEquals;

import java.io.FileReader;
import java.util.Set;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.interpss.fadapter.psse.bean.PSSESchema;
import org.interpss.CorePluginTestSetup;
import org.interpss.fadapter.psse.PSSEJsonDirectParser;
import org.interpss.fadapter.psse.export.PSSEJSonExporter;
import org.junit.jupiter.api.Test;

import com.interpss.core.LoadflowAlgoObjectFactory;
import com.interpss.core.aclf.AclfNetwork;
import com.interpss.core.algo.AclfMethodType;
import com.interpss.core.algo.LoadflowAlgorithm;
import com.interpss.core.funcImpl.topo.AclfNetTopoHelper;

public class PSSEJSon_IEEE9Bus_BusSet_Test extends CorePluginTestSetup { 
	@Test
	public void testJSonExport() throws Exception {
	    AclfNetwork net = new PSSEJsonDirectParser().parse("testData/adpter/psse/json/ieee9.rawx");
	    
	    // run a loadflow
	    LoadflowAlgorithm algo = LoadflowAlgoObjectFactory.createLoadflowAlgorithm(net);
	  	algo.setLfMethod(AclfMethodType.PQ);
	  	algo.loadflow();
  		//System.out.println(net.net2String());
	    
	  	// find the bus set connected to Bus1 within 2 hops
		AclfNetTopoHelper helper = new AclfNetTopoHelper(net);
		Set<String> busIdSet = helper.findConnectedBuses(net.getBus("Bus1"), 2);
		System.out.println("Connected bus set: " + busIdSet);
		
		// Read PSSESchema separately from the JSON file
		PSSESchema psseJson;
		try (FileReader reader = new FileReader("testData/adpter/psse/json/ieee9.rawx")) {
			JsonObject root = JsonParser.parseReader(reader).getAsJsonObject();
			psseJson = new Gson().fromJson(root, PSSESchema.class);
		}
		//System.out.println("Before Bus Data: " + psseJson.getNetwork().getBus().getData());
		
		// export the bus set data to a new PSSE json file
		PSSEJSonExporter exporter = new PSSEJSonExporter(net, psseJson);
		exporter.filterAndUpdate(busIdSet);
		
		assertEquals(psseJson.getNetwork().getBus().getData().size(), 4);
		assertEquals(psseJson.getNetwork().getGenerator().getData().size(), 1);
		assertEquals(psseJson.getNetwork().getLoad().getData().size(), 2);
		
		assertEquals(psseJson.getNetwork().getAcline().getData().size(), 2);
		assertEquals(psseJson.getNetwork().getTransformer().getData().size(), 1);
	}
}


