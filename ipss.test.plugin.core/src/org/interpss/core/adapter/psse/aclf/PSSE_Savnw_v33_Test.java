package org.interpss.core.adapter.psse.aclf;

import static org.interpss.pssl.plugin.IpssAdapter.FileFormat.PSSE;
import static org.junit.Assert.assertTrue;

import org.ieee.odm.adapter.IODMAdapter;
import org.ieee.odm.adapter.psse.PSSEAdapter;
import org.ieee.odm.model.aclf.AclfModelParser;
import org.interpss.CorePluginTestSetup;
import org.interpss.datamodel.bean.aclf.AclfNetBean;
import org.interpss.display.AclfOutFunc;
import org.interpss.mapper.bean.aclf.AclfNet2BeanMapper;
import org.interpss.mapper.odm.ODMAclfParserMapper;
import org.interpss.pssl.plugin.IpssAdapter;
import org.interpss.pssl.plugin.IpssAdapter.PsseVersion;
import org.junit.Test;

import com.interpss.SimuObjectFactory;
import com.interpss.core.CoreObjectFactory;
import com.interpss.core.aclf.AclfNetwork;
import com.interpss.core.algo.AclfMethod;
import com.interpss.core.algo.LoadflowAlgorithm;
import com.interpss.simu.SimuContext;
import com.interpss.simu.SimuCtxType;

public class PSSE_Savnw_v33_Test extends CorePluginTestSetup {
	@Test
	public void testDataInputAndPowerflow() throws Exception {
//		AclfNetwork net = IpssAdapter.importAclfNet("testdata/adpter/psse/v33/savnw.raw")
//				.setFormat(PSSE)
//				.setPsseVersion(PsseVersion.PSSE_33)
//				.load()
//				.getImportedObj();
//		IODMAdapter adapter = new PSSEAdapter(PSSEAdapter.PsseVersion.PSSE_30);
//		assertTrue(adapter.parseInputFile("testdata/adpter/psse/v30/savnw_v30.raw"));
//		
		IODMAdapter adapter = new PSSEAdapter(PSSEAdapter.PsseVersion.PSSE_33);
		assertTrue(adapter.parseInputFile("testdata/adpter/psse/v33/savnw.raw"));
		
		
		AclfModelParser parser = (AclfModelParser)adapter.getModel();
		parser.stdout();
		
		SimuContext simuCtx = SimuObjectFactory.createSimuNetwork(SimuCtxType.ACLF_NETWORK);
		if (!new ODMAclfParserMapper()
					.map2Model(parser, simuCtx)) {
  	  		System.out.println("Error: ODM model to InterPSS SimuCtx mapping error, please contact support@interpss.com");
  	  		return;
		}		
		
		
		AclfNetwork net = simuCtx.getAclfNet();
		
		//System.out.println(net.net2String());
		
		LoadflowAlgorithm algo = CoreObjectFactory.createLoadflowAlgorithm(net);
	  	//algo.setLfMethod(AclfMethod.PQ);
		algo.setNonDivergent(true);
	  	algo.setLfMethod(AclfMethod.NR);
	  	algo.getLfAdjAlgo().setApplyAdjustAlgo(false);
	  	
	  	algo.loadflow();
  	
	
		assertTrue(net.isLfConverged());
		
		System.out.println(AclfOutFunc.loadFlowSummary(net));
	}
	
	@Test
	public void compare() throws Exception {
		// load the test data V30
		AclfNetwork net30 = IpssAdapter.importAclfNet("testdata/adpter/psse/v30/savnw_v30.raw")
				.setFormat(PSSE)
				.setPsseVersion(PsseVersion.PSSE_30)
				.load()
				.getImportedObj();
		AclfNetBean netBean30 = new AclfNet2BeanMapper().map2Model(net30);
		
		

		
		// load the test data V33
		AclfNetwork net33 = IpssAdapter.importAclfNet("testdata/adpter/psse/v33/savnw.raw")
				.setFormat(PSSE)
				.setPsseVersion(PsseVersion.PSSE_33)
				.load()
				.getImportedObj();
		AclfNetBean netBean33 = new AclfNet2BeanMapper().map2Model(net33);
		
		// compare the data model with V30
		netBean30.compareTo(netBean33);		
	}

}
