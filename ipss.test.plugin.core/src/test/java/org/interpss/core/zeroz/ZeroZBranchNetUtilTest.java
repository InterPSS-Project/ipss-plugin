package org.interpss.core.zeroz;

import static org.junit.Assert.assertTrue;

import org.interpss.CorePluginTestSetup;
import org.interpss.plugin.pssl.plugin.IpssAdapter;
import org.junit.Test;

import com.interpss.common.exp.InterpssException;
import com.interpss.core.aclf.AclfNetModelType;
import com.interpss.core.aclf.AclfNetwork;


// ZeroZBranch Mark : Zero Z Branch Util Func Test
public class ZeroZBranchNetUtilTest extends CorePluginTestSetup {
	@Test 
	public void test() throws  InterpssException {
		// Create an AclfNetwork object
		AclfNetwork net = IpssAdapter.importAclfNet("testData/odm/zeroz/Ieee14Bus_breaker.xml")
				.setFormat(IpssAdapter.FileFormat.IEEE_ODM)
				.load()
				.getImportedObj();
		net.setAclfNetModelType(AclfNetModelType.ZBR_MODEL);
	  	//System.out.println(net.net2String());

		//assertTrue("", net.getBus("Bus7").isPureZbrConnectBus());
		//assertTrue("", !net.getBus("Bus71").isPureZbrConnectBus());
		
		assertTrue("", net.getBus("Bus7").getNoConnectedZbr(false) == 3);
		assertTrue("", net.getBus("Bus71").getNoConnectedZbr(false) == 1);
		
		assertTrue("", net.getBus("Bus7").getNoConnectedZbr(true) == 3);
		assertTrue("", net.getBus("Bus71").getNoConnectedZbr(true) == 3);

		assertTrue("", net.getBus("Bus7").findZeroZPathBuses().size() == 4);
		assertTrue("", !net.getBus("Bus7").hasZbrLoop());

		assertTrue("", net.getBus("Bus1").findZeroZPathBuses().size() == 4);
		assertTrue("", net.getBus("Bus1").getNoConnectedZbr(true) == 3);
		assertTrue("", !net.getBus("Bus1").hasZbrLoop());
		
		assertTrue("", net.getBus("Bus18").findZeroZPathBuses().size() == 3);
		assertTrue("", net.getBus("Bus18").getNoConnectedZbr(true) == 2);
		assertTrue("", !net.getBus("Bus18").hasZbrLoop());
    }	
	
	@Test 
	public void test_loop() throws  InterpssException {
		// Create an AclfNetwork object
		AclfNetwork net = IpssAdapter.importAclfNet("testData/odm/zeroz/Ieee14Bus_breaker_loop.xml")
				.setFormat(IpssAdapter.FileFormat.IEEE_ODM)
				.load()
				.getImportedObj();
	  	//System.out.println(net.net2String());

		//assertTrue("", net.getBus("Bus7").isPureZbrConnectBus());
		//assertTrue("", !net.getBus("Bus71").isPureZbrConnectBus());
		
		assertTrue("", net.getBus("Bus7").getNoConnectedZbr(false) == 3);
		assertTrue("", net.getBus("Bus71").getNoConnectedZbr(false) == 2);
		
		assertTrue("", net.getBus("Bus7").getNoConnectedZbr(true) == 5);
		assertTrue("", net.getBus("Bus71").getNoConnectedZbr(true) == 5);
		assertTrue("", net.getBus("Bus7").findZeroZPathBuses().size() == 5);
		assertTrue("", net.getBus("Bus7").hasZbrLoop());
		
		assertTrue("", net.getBus("Bus1").findZeroZPathBuses().size() == 3);
		assertTrue("", net.getBus("Bus1").getNoConnectedZbr(true) == 2);	
		assertTrue("", !net.getBus("Bus1").hasZbrLoop());
		
		assertTrue("", net.getBus("Bus18").findZeroZPathBuses().size() == 4);
		assertTrue("", net.getBus("Bus18").getNoConnectedZbr(true) == 3);
		assertTrue("", !net.getBus("Bus18").hasZbrLoop());
    }	
}


	
