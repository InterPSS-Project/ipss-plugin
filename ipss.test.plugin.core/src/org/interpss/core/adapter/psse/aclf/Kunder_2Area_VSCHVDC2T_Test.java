package org.interpss.core.adapter.psse.aclf;

import static org.junit.Assert.assertTrue;

import org.ieee.odm.adapter.IODMAdapter;
import org.ieee.odm.adapter.psse.PSSEAdapter;
import org.ieee.odm.model.aclf.AclfModelParser;
import org.interpss.CorePluginTestSetup;
import org.interpss.display.AclfOutFunc;
import org.interpss.mapper.odm.ODMAclfParserMapper;
import org.junit.Test;

import com.interpss.CoreObjectFactory;
import com.interpss.SimuObjectFactory;
import com.interpss.core.aclf.AclfNetwork;
import com.interpss.core.aclf.hvdc.HvdcLine2TVSC;
import com.interpss.core.algo.LoadflowAlgorithm;
import com.interpss.simu.SimuContext;
import com.interpss.simu.SimuCtxType;

public class Kunder_2Area_VSCHVDC2T_Test extends CorePluginTestSetup {
	@Test
	public void test_VSCHVDC_DataInput_Loadflow() throws Exception {
		
		IODMAdapter adapter = new PSSEAdapter(PSSEAdapter.PsseVersion.PSSE_30);
		assertTrue(adapter.parseInputFile("testdata/adpter/psse/v30/Kunder_2area/Kunder_2area_vschvdc_v30.raw"));
		
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
		
		assertTrue(net.getSpecialBranchList().size()==1);
		
		assertTrue(!net.getBus("Bus7").isGen());
		assertTrue(!net.getBus("Bus9").isGen());
		HvdcLine2TVSC vscHVDC= (HvdcLine2TVSC) net.getSpecialBranchList().get(0);
		System.out.println(vscHVDC.getId());
		System.out.println(vscHVDC.getName());
		
		//test vschvdc initPowerFlow function
		 vscHVDC.initPowerFlow();
		 
	   assertTrue(net.getBus("Bus7").isGenPQ());
	   assertTrue(net.getBus("Bus9").isGenPV());
		 
		//System.out.println(net.net2String());
		 
		LoadflowAlgorithm algo = CoreObjectFactory.createLoadflowAlgorithm(net);
		algo.getLfAdjAlgo().setApplyAdjustAlgo(false);
	  	algo.loadflow();
  		//System.out.println(net.net2String());
	  	
  		assertTrue(net.isLfConverged());
  		
  		System.out.println(AclfOutFunc.loadFlowSummary(net));
		
	}
	
	
	
	
	

}
