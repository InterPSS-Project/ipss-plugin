package sample.psse.busset;
 
import java.util.Set;

import org.ieee.odm.adapter.IODMAdapter;
import org.ieee.odm.adapter.psse.bean.PSSESchema;
import org.ieee.odm.adapter.psse.json.PSSEJSonAdapter;
import org.ieee.odm.model.aclf.AclfModelParser;
import org.interpss.fadapter.export.psse.PSSEJSonBusUpdater;
import org.interpss.odm.mapper.ODMAclfParserMapper;

import com.interpss.core.LoadflowAlgoObjectFactory;
import com.interpss.core.aclf.AclfNetwork;
import com.interpss.core.algo.AclfMethodType;
import com.interpss.core.algo.LoadflowAlgorithm;
import com.interpss.core.funcImpl.topo.AclfNetTopoHelper;
import com.interpss.simu.SimuContext;
import com.interpss.simu.SimuCtxType;
import com.interpss.simu.SimuObjectFactory;

public class PSSE_IEEE9Bus_BusSetSample { 
	public static void main(String args[]) throws Exception {
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
	    
	    LoadflowAlgorithm algo = LoadflowAlgoObjectFactory.createLoadflowAlgorithm(net);
	  	algo.setLfMethod(AclfMethodType.PQ);
	  	algo.loadflow();
  		//System.out.println(net.net2String());
	    
		AclfNetTopoHelper helper = new AclfNetTopoHelper(net);
		
		Set<String> busSet = helper.findConnectedBuses(net.getBus("Bus1"), 2);
		System.out.println("Connected bus set: " + busSet);
		
		PSSESchema psseJson = parser.getJsonObject();
		System.out.println("Before Bus Data: " + psseJson.getNetwork().getBus().getData());
		
		PSSEJSonBusUpdater busUpdater = new PSSEJSonBusUpdater(psseJson.getNetwork().getBus(), net); 
		busUpdater.filter(busSet);
		busUpdater.update();
	  	
		System.out.println("Before Bus Data: " + psseJson.getNetwork().getBus().getData());
	}
}


