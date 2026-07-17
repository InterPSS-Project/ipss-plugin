package org.interpss.core.ca.dep;

import static org.junit.jupiter.api.Assertions.assertTrue;

import org.interpss.CorePluginTestSetup;
import org.interpss.plugin.pssl.plugin.IpssAdapter;

import com.interpss.common.exp.InterpssException;
import com.interpss.core.aclf.AclfNetwork;
import com.interpss.core.funcImpl.zeroz.dep.ZeroZBranchProcesor;

@Deprecated
public class IEEE14BusBreaker_equivCABranch_Test extends CorePluginTestSetup {
	//@Test 
	public void case1_smallZ() throws  InterpssException {
		// Create an AclfNetwork object
		AclfNetwork net = IpssAdapter.importAclfNet("testData/odm/zeroz/Ieee14Bus_breaker.xml")
				.setFormat(IpssAdapter.FileFormat.IEEE_ODM)
				.load()
				.getImportedObj();
	  	//System.out.println(net.net2String());
		
	  	net.accept(new ZeroZBranchProcesor(true));
	  	//assertTrue(net.isZeroZBranchModel());

	  	/*
	  	 * identify equivalent CA branches
	  	 */
	  	//for (Branch branch : net.getBranchList()) 
	  	//	branch.identifyEquivCABranch();	  	
	  	
	  	//System.out.println("Branch Bus1->Bus15-1(1) equivCABranch: " + net.getBranch("Bus1->Bus15-1(1)").getEquivCABranchId());
	  	//System.out.println("Branch Bus15-1->Bus15(1) equivCABranch: " + net.getBranch("Bus15-1->Bus15(1)").getEquivCABranchId());
	  	
	  	/*
	  	 * Branch Bus1->Bus15-1(1) and Bus15-1->Bus15(1) are small-Z branches. Their equiv CA branch
	  	 * is branch Bus15->Bus2 with normal Z.
	  	 */
	  	//assertTrue(net.getBranch("Bus1->Bus15-1(1)").getEquivCABranchId().equals("Bus15->Bus2(1)"));
	  	//assertTrue(net.getBranch("Bus15-1->Bus15(1)").getEquivCABranchId().equals("Bus15->Bus2(1)"));
	}	
	
	//@Test 
	public void case1_smallZ_1() throws  InterpssException {
		// test casa with a small-Z brach loop at Bus-14
		
		// Create an AclfNetwork object
		AclfNetwork net = IpssAdapter.importAclfNet("testData/odm/zeroz/ieee14Bus_breaker_1.xml")
				.setFormat(IpssAdapter.FileFormat.IEEE_ODM)
				.load()
				.getImportedObj();
	  	//System.out.println(net.net2String());
		
	  	net.accept(new ZeroZBranchProcesor(true));
	  	//assertTrue(net.isZeroZBranchModel());
	  	//for (Branch branch : net.getBranchList()) 
	  	//	branch.identifyEquivCABranch();	  	
	  	
	  	//System.out.println("Branch Bus1->Bus15(1) equivCABranch: " + net.getBranch("Bus1->Bus15(1)").getEquivCABranchId());
	  	//assertTrue(net.getBranch("Bus1->Bus15(1)").getEquivCABranchId().equals("Bus15->Bus2(1)"));
    }
}
