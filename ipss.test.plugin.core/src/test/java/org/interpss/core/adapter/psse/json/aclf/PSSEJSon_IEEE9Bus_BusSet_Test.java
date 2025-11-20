package org.interpss.core.adapter.psse.json.aclf;
 
import static org.junit.Assert.assertEquals;

import java.util.Set;

import org.ieee.odm.adapter.IODMAdapter;
import org.ieee.odm.adapter.psse.bean.PSSESchema;
import org.ieee.odm.adapter.psse.json.PSSEJSonAdapter;
import org.ieee.odm.model.aclf.AclfModelParser;
import org.interpss.CorePluginTestSetup;
import org.interpss.fadapter.export.PSSEJSonExporter;
import org.interpss.odm.mapper.ODMAclfParserMapper;
import org.junit.Test;

import com.interpss.core.LoadflowAlgoObjectFactory;
import com.interpss.core.aclf.AclfNetwork;
import com.interpss.core.algo.AclfMethodType;
import com.interpss.core.algo.LoadflowAlgorithm;
import com.interpss.core.funcImpl.topo.AclfNetTopoHelper;
import com.interpss.simu.SimuContext;
import com.interpss.simu.SimuCtxType;
import com.interpss.simu.SimuObjectFactory;

public class PSSEJSon_IEEE9Bus_BusSet_Test extends CorePluginTestSetup { 
	@Test
	public void testJSonExport() throws Exception {
	    IODMAdapter adapter = new PSSEJSonAdapter();
	    adapter.parseInputFile("testdata/adpter/psse/json/ieee9.rawx");
	    
	    AclfModelParser parser = (AclfModelParser)adapter.getModel();
	    //parser.stdout();
	    
	    SimuContext simuCtx = SimuObjectFactory.createSimuNetwork(SimuCtxType.ACLF_NETWORK);
	    if (!new ODMAclfParserMapper().map2Model(parser, simuCtx)) {
	        System.out.println("Error: ODM model to InterPSS SimuCtx mapping error, please contact support@interpss.com");
	   	 return;
	    }	
	    AclfNetwork net = simuCtx.getAclfNet();
	    
	    // run a loadflow
	    LoadflowAlgorithm algo = LoadflowAlgoObjectFactory.createLoadflowAlgorithm(net);
	  	algo.setLfMethod(AclfMethodType.PQ);
	  	algo.loadflow();
  		//System.out.println(net.net2String());
	    
	  	// find the bus set connected to Bus1 within 2 hops
		AclfNetTopoHelper helper = new AclfNetTopoHelper(net);
		Set<String> busIdSet = helper.findConnectedBuses(net.getBus("Bus1"), 2);
		System.out.println("Connected bus set: " + busIdSet);
		
		PSSESchema psseJson = parser.getJsonObject();
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


