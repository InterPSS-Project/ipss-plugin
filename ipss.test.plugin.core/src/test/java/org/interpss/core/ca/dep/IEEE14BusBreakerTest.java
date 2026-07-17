package org.interpss.core.ca.dep;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.interpss.CorePluginTestSetup;
import org.interpss.plugin.pssl.plugin.IpssAdapter;
import org.junit.jupiter.api.Test;

import com.interpss.common.exp.InterpssException;
import com.interpss.core.aclf.AclfBranch;
import com.interpss.core.aclf.AclfNetwork;
import com.interpss.core.aclf.BaseAclfBus;
import com.interpss.core.funcImpl.zeroz.dep.ZeroZBranchFunction;
import com.interpss.core.funcImpl.zeroz.dep.ZeroZBranchProcesor;
import com.interpss.core.net.Branch;
import com.interpss.core.net.Bus;

@Deprecated
public class IEEE14BusBreakerTest extends CorePluginTestSetup {
	//@Test 
	public void processZeroZBranch() throws  InterpssException {
		// Create an AclfNetwork object
		AclfNetwork net = IpssAdapter.importAclfNet("testData/odm/zeroz/Ieee14Bus_breaker.xml")
				.setFormat(IpssAdapter.FileFormat.IEEE_ODM)
				.load()
				.getImportedObj();
		
	  	net.setZeroZBranchThreshold(0.00001);
	  	net.accept(new ZeroZBranchProcesor(true));
	  	//assertTrue(net.isZeroZBranchModel());
	  	
	  	// at this point, all buses and small-z branches should be visited
	  	for (Bus b : net.getBusList())
	  		assertTrue(b.isBooleanFlag());
	  	for (Branch b : net.getBranchList())
	  		if (((AclfBranch)b).isZeroZBranch())
	  			assertTrue(b.isBooleanFlag());
	}	
	
	//@Test 
	public void findZeroZPath() throws  InterpssException {
		// Create an AclfNetwork object
		AclfNetwork net = IpssAdapter.importAclfNet("testData/odm/zeroz/Ieee14Bus_breaker.xml")
				.setFormat(IpssAdapter.FileFormat.IEEE_ODM)
				.load()
				.getImportedObj();
		
	  	net.setVisitedStatus(false);
	  	net.setZeroZBranchThreshold(0.00001);
	  	ZeroZBranchFunction.markZeroZBranch.accept(net);		
		
	  	List<BaseAclfBus<?,?>> list = net.getBus("Bus1").findZeroZPathBuses();
	  	//System.out.println(list);
	  	assertTrue(list.size() == 4);	
	  	
	  	list = net.getBus("Bus14").findZeroZPathBuses();
	  	//System.out.println(list);
	  	assertTrue(list.size() == 3);	   
	
	  	// there is a zero-z branch loop
	  	list = net.getBus("Bus7").findZeroZPathBuses();
	  	//System.out.println(list);
	  	assertTrue(list.size() == 5);	

	  	list = net.getBus("Bus2").findZeroZPathBuses();
	  	//System.out.println(list);
	  	assertTrue(list.size() == 1);	
	}	
}
