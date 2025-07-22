package org.interpss.plugin.jsonCopy;

import org.ieee.odm.adapter.IODMAdapter;
import org.ieee.odm.adapter.psse.PSSEAdapter;
import org.ieee.odm.adapter.psse.raw.PSSERawAdapter;
import org.ieee.odm.model.aclf.AclfModelParser;
import org.interpss.CorePluginTestSetup;
import org.interpss.odm.mapper.ODMAclfParserMapper;
import static org.junit.Assert.assertTrue;
import org.junit.Test;

import com.interpss.core.aclf.AclfBus;
import com.interpss.core.aclf.AclfNetwork;
import com.interpss.core.aclf.hvdc.HvdcLine2TLCC;
import com.interpss.core.funcImpl.AclfNetObjectComparator;
import com.interpss.simu.SimuContext;
import com.interpss.simu.SimuCtxType;
import com.interpss.simu.SimuObjectFactory;

public class Kundur_2Area_LCCHVDC2T_JsonCopyTest extends CorePluginTestSetup {

	
	@Test
	public void test_LCCHVDC_JSonCopy() throws Exception {
		AclfNetwork net = createTestCase();
		
		HvdcLine2TLCC<AclfBus> lccHVDC = (HvdcLine2TLCC<AclfBus>) net.getSpecialBranchList().get(0);
		// we need to set the name, otherwise the json copy will not work
		lccHVDC.setName("Hvdc line: Bus7->Bus9(1)");
		assertTrue(lccHVDC.getRectifier().getParentHvdc() != null);
		assertTrue(lccHVDC.getInverter().getParentHvdc() != null);
		
		AclfNetwork netCopy = net.jsonCopy();
		
		//AclfBus bus7 = net.getBus("Bus7");
		//AclfBus bus7Copy = netCopy.getBus("Bus7");
		
		//assertTrue("", net.diffState(netCopy));
  		AclfNetObjectComparator comp = new AclfNetObjectComparator(net, netCopy);
  		comp.compareNetwork();
  		
  		System.out.println("Differences found: " + comp.getDiffMsgList());
  		assertTrue("" + comp.getDiffMsgList(), comp.getDiffMsgList().size() == 0);
		
		
	}
	

	private AclfNetwork createTestCase() {
		System.out.println("Kundur 2-area LCC HVDC test case creation ...");
		
		IODMAdapter adapter = new PSSERawAdapter(PSSEAdapter.PsseVersion.PSSE_33);
		assertTrue(adapter.parseInputFile("testData/adpter/psse/v33/Kundur_2area_LCC_HVDC.raw"));
		
		AclfModelParser parser = (AclfModelParser)adapter.getModel();
		//parser.stdout();
		
		SimuContext simuCtx = SimuObjectFactory.createSimuNetwork(SimuCtxType.ACLF_NETWORK);
		if (!new ODMAclfParserMapper()
					.map2Model(parser, simuCtx)) {
  	  		System.out.println("Error: ODM model to InterPSS SimuCtx mapping error, please contact support@interpss.com");
  	  		return null;
		}		
		
		AclfNetwork net = simuCtx.getAclfNet();
		//System.out.println(net.net2String());		

		HvdcLine2TLCC<AclfBus> lccHVDC = (HvdcLine2TLCC<AclfBus>) net.getSpecialBranchList().get(0);
		assertTrue(lccHVDC.getRectifier().getParentHvdc() != null);
		assertTrue(lccHVDC.getInverter().getParentHvdc() != null);
		
		System.out.println("Kundur 2-area LCC HVDC test case created");
		
		return net;
	}
}
