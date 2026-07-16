package sample.psse.busset;

import java.util.Set;

import org.interpss.fadapter.psse.PSSEJsonDirectParser;
import org.interpss.fadapter.psse.bean.PSSESchema;
import org.interpss.fadapter.psse.export.PSSEJSonExporter;

import com.interpss.core.LoadflowAlgoObjectFactory;
import com.interpss.core.aclf.AclfNetwork;
import com.interpss.core.algo.AclfMethodType;
import com.interpss.core.algo.LoadflowAlgorithm;
import com.interpss.core.funcImpl.topo.AclfNetTopoHelper;

public class PSSE_IEEE9Bus_BusSetSample { 
	public static void main(String args[]) throws Exception {
	    AclfNetwork net = new PSSEJsonDirectParser().parse("testdata/adpter/psse/json/ieee9.rawx");
	    
	    LoadflowAlgorithm algo = LoadflowAlgoObjectFactory.createLoadflowAlgorithm(net);
	  	algo.setLfMethod(AclfMethodType.PQ);
	  	algo.loadflow();
	    
	  	AclfNetTopoHelper helper = new AclfNetTopoHelper(net);
		Set<String> busIdSet = helper.findConnectedBuses(net.getBus("Bus1"), 2);
		System.out.println("Connected bus set: " + busIdSet);
		
		// Note: PSSESchema JSON object is no longer available from the direct parser.
		// To export with bus set filtering, read the JSON file directly with Gson.
		com.google.gson.Gson gson = new com.google.gson.Gson();
		PSSESchema psseJson = gson.fromJson(
				new java.io.FileReader("testdata/adpter/psse/json/ieee9.rawx"), PSSESchema.class);
		
		PSSEJSonExporter exporter = new PSSEJSonExporter(net, psseJson);
		exporter.filterAndUpdate(busIdSet);
		exporter.export("output/ieee9_busset.rawx");
	}
}


