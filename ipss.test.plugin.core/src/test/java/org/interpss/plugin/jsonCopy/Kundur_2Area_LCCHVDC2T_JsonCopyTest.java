package org.interpss.plugin.jsonCopy;

import static org.junit.jupiter.api.Assertions.assertTrue;

import org.interpss.CorePluginTestSetup;
import org.interpss.fadapter.psse.PSSEDirectParser;
import org.junit.jupiter.api.Test;

import com.interpss.core.aclf.AclfBus;
import com.interpss.core.aclf.AclfNetwork;
import com.interpss.core.aclf.hvdc.HvdcLine2TLCC;
import com.interpss.core.funcImpl.compare.AclfNetObjectComparator;

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
		
		//assertTrue(net.diffState(netCopy));
  		AclfNetObjectComparator comp = new AclfNetObjectComparator(net, netCopy);
  		comp.compareNetwork();
  		
  		System.out.println("Differences found: " + comp.getDiffMsgList());
  		assertTrue(comp.getDiffMsgList().size() == 0, "" + comp.getDiffMsgList());
		
		
	}
	

	private AclfNetwork createTestCase() throws Exception {
		System.out.println("Kundur 2-area LCC HVDC test case creation ...");
		
		AclfNetwork net = new PSSEDirectParser(33).parse("testData/adpter/psse/v33/Kundur_2area_LCC_HVDC.raw");
		//System.out.println(net.net2String());		

		HvdcLine2TLCC<AclfBus> lccHVDC = (HvdcLine2TLCC<AclfBus>) net.getSpecialBranchList().get(0);
		assertTrue(lccHVDC.getRectifier().getParentHvdc() != null);
		assertTrue(lccHVDC.getInverter().getParentHvdc() != null);
		
		System.out.println("Kundur 2-area LCC HVDC test case created");
		
		return net;
	}
}
