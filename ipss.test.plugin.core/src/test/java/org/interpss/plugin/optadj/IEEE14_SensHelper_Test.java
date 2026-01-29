
package org.interpss.plugin.optadj;

import static org.junit.Assert.assertEquals;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

import org.interpss.CorePluginFactory;
import org.interpss.CorePluginTestSetup;
import org.interpss.fadapter.IpssFileAdapter;
import org.interpss.numeric.datatype.LimitType;
import org.interpss.plugin.optadj.algo.util.AclfNetGFSsHelper;
import org.interpss.plugin.optadj.algo.util.AclfNetLODFsHelper;
import org.interpss.plugin.optadj.algo.util.Sen2DMatrix;
import org.junit.Test;

import com.interpss.common.exp.InterpssException;
import com.interpss.core.aclf.AclfBranch;
import com.interpss.core.aclf.AclfNetwork;

public class IEEE14_SensHelper_Test extends CorePluginTestSetup {
	@Test
	public void gsfTest() throws InterpssException {
		AclfNetwork net = createSenTestCase();
		
		AclfNetGFSsHelper senHelper = new AclfNetGFSsHelper(net);
		
		Sen2DMatrix gfs = senHelper.calGFS();
		
		/*
		net.getBusList().forEach(bus -> {
			System.out.println("Bus " + bus.getId() + ", no: " + bus.getSortNumber());
		});
		
		net.getBranchList().forEach(br -> {
			System.out.println("Branch " + br.getId() + ", no: " + br.getSortNumber());
		});
		*/
		
		//System.out.println("GFS Matrix: \n" + gfs.toString());

		int ni = net.getBus("Bus2").getSortNumber();          		// 1
		int nj = net.getBranch("Bus1->Bus2(1)").getSortNumber();    // 0
		assertEquals(-0.838, gfs.get(ni, nj), 0.001);
		nj = net.getBranch("Bus13->Bus14(1)").getSortNumber();      // 19
		assertEquals(-0.002, gfs.get(ni, nj), 0.001);
		
		ni = net.getBus("Bus14").getSortNumber();                  // 13
		nj = net.getBranch("Bus1->Bus2(1)").getSortNumber();       // 0
		assertEquals(-0.644, gfs.get(ni, nj), 0.001);
		nj = net.getBranch("Bus13->Bus14(1)").getSortNumber();     // 19
		assertEquals(-0.397, gfs.get(ni, nj), 0.001);
		
		Set<String> busIdSet = new HashSet<>(Arrays.asList("Bus1", "Bus2", "Bus3", "Bus6", "Bus8"));
		
		gfs = senHelper.calGFS(busIdSet);
		
		//System.out.println("GFS Matrix: \n" + gfs.toString());
		
		ni = net.getBus("Bus2").getSortNumber();       // 1
		nj = net.getBranch("Bus1->Bus2(1)").getSortNumber();       // 0
		assertEquals(-0.838, gfs.get(ni, nj), 0.001);  
		nj = net.getBranch("Bus13->Bus14(1)").getSortNumber();     // 19
		assertEquals(-0.002, gfs.get(ni, nj), 0.001);
		
		ni = net.getBus("Bus8").getSortNumber();       // 7
		assertEquals(-0.657, gfs.get(ni, 0), 0.001);  
		assertEquals(-0.079, gfs.get(ni, 19), 0.001);
		
		// Bus2->Bus3(1), Bus2->Bus4(1), Bus2->Bus5(1), Bus3->Bus4(1)
		Set<String> branchIdSet = new HashSet<>(Arrays.asList("Bus2->Bus3(1)", 
						"Bus2->Bus4(1)", "Bus2->Bus5(1)", "Bus3->Bus4(1)"));
		gfs = senHelper.calGFS(busIdSet, branchIdSet);
		
		//System.out.println("GFS Matrix: \n" + gfs.toString());
		ni = net.getBus("Bus3").getSortNumber();             // 2
		nj = net.getBranch("Bus2->Bus3(1)").getSortNumber(); // 2
		assertEquals(-0.532, gfs.get(ni, nj), 0.001);  
		nj = net.getBranch("Bus3->Bus4(1)").getSortNumber();  // 5
		assertEquals(0.468, gfs.get(ni, nj), 0.001); 
		
		ni = net.getBus("Bus8").getSortNumber();              // 7
		nj = net.getBranch("Bus2->Bus3(1)").getSortNumber();  // 2
		assertEquals(-0.143, gfs.get(ni, nj), 0.001);  
		nj = net.getBranch("Bus3->Bus4(1)").getSortNumber();  // 5
		assertEquals(-0.143, gfs.get(ni, nj), 0.001);  
	}
	
	@Test
	public void lodfTest() throws InterpssException {
		AclfNetwork net = createSenTestCase();
		
		AclfNetLODFsHelper senHelper = new AclfNetLODFsHelper(net);
		
		Sen2DMatrix lodf = senHelper.calLODF();
		
		//System.out.println("LODF Matrix: \n" + lodf.toString());
		
		int ni = net.getBranch("Bus1->Bus2(1)").getSortNumber();              // 0
		int nj = net.getBranch("Bus2->Bus3(1)").getSortNumber();  // 2
		assertEquals(-0.169, lodf.get(ni, nj), 0.001);   
		nj = net.getBranch("Bus4->Bus5(1)").getSortNumber();      // 6
		assertEquals(-0.494, lodf.get(ni, nj), 0.001);
		
		ni = net.getBranch("Bus2->Bus4(1)").getSortNumber();              		// 3
		nj = net.getBranch("Bus2->Bus3(1)").getSortNumber();  		// 2
		assertEquals(0.285, lodf.get(ni, nj), 0.001);   
		nj = net.getBranch("Bus4->Bus5(1)").getSortNumber();      	// 6
		assertEquals(-0.676, lodf.get(ni, nj), 0.001);
		
		Set<String> outBranchIdSet = new HashSet<>(Arrays.asList(
				"Bus1->Bus2(1)", "Bus2->Bus4(1)", "Bus2->Bus5(1)", "Bus3->Bus4(1)"));
		
		lodf = senHelper.calLODF(outBranchIdSet);
		
		ni = net.getBranch("Bus1->Bus2(1)").getSortNumber();              // 0
		nj = net.getBranch("Bus2->Bus3(1)").getSortNumber();  // 2
		assertEquals(-0.169, lodf.get(ni, nj), 0.001);   
		nj = net.getBranch("Bus4->Bus5(1)").getSortNumber();      // 6
		assertEquals(-0.494, lodf.get(ni, nj), 0.001);
		
		ni = net.getBranch("Bus2->Bus4(1)").getSortNumber();              		// 3
		nj = net.getBranch("Bus2->Bus3(1)").getSortNumber();  		// 2
		assertEquals(0.285, lodf.get(ni, nj), 0.001);   
		nj = net.getBranch("Bus4->Bus5(1)").getSortNumber();      	// 6
		assertEquals(-0.676, lodf.get(ni, nj), 0.001);
		
		Set<String> monBranchIdSet = new HashSet<>(Arrays.asList(
				"Bus2->Bus3(1)", "Bus2->Bus4(1)", "Bus2->Bus5(1)", "Bus4->Bus5(1)"));
		
		lodf = senHelper.calLODF(outBranchIdSet, monBranchIdSet);
		
		ni = net.getBranch("Bus1->Bus2(1)").getSortNumber();              // 0
		nj = net.getBranch("Bus2->Bus3(1)").getSortNumber();  // 2
		assertEquals(-0.169, lodf.get(ni, nj), 0.001);   
		nj = net.getBranch("Bus4->Bus5(1)").getSortNumber();      // 6
		assertEquals(-0.494, lodf.get(ni, nj), 0.001);
		
		ni = net.getBranch("Bus2->Bus4(1)").getSortNumber();              		// 3
		nj = net.getBranch("Bus2->Bus3(1)").getSortNumber();  		// 2
		assertEquals(0.285, lodf.get(ni, nj), 0.001);   
		nj = net.getBranch("Bus4->Bus5(1)").getSortNumber();      	// 6
		assertEquals(-0.676, lodf.get(ni, nj), 0.001);
	}
	
	public static AclfNetwork createSenTestCase() throws InterpssException {
		AclfNetwork net = CorePluginFactory
				.getFileAdapter(IpssFileAdapter.FileFormat.IEEECDF)
				.load("testData/adpter/ieee_format/ieee14.ieee")
				.getAclfNet();
		
		// set the branch rating.
		net.getBranchList().stream() 
			.forEach(branch -> {
				AclfBranch aclfBranch = (AclfBranch) branch;
				// Mva1 is used for basecase loading limit
				aclfBranch.setRatingMva1(100.0);
				// Mva2 is used for contingency loading limit
				aclfBranch.setRatingMva2(120.0);
			});
		
		// set the generator Pgen limit
		net.createAclfGenNameLookupTable(false).forEach((k, gen) -> {
			//System.out.println("Adj Gen: " + gen.getName());
			if (gen.getPGenLimit() == null) {
				gen.setPGenLimit(new LimitType(5, 0));
			}
		});
		
		return net;
	}
}

/*
Bus Bus1, no: 0
Bus Bus2, no: 1
Bus Bus3, no: 2
Bus Bus4, no: 3
Bus Bus5, no: 4
Bus Bus6, no: 5
Bus Bus7, no: 6
Bus Bus8, no: 7
Bus Bus9, no: 8
Bus Bus10, no: 9
Bus Bus11, no: 10
Bus Bus12, no: 11
Bus Bus13, no: 12
Bus Bus14, no: 13
Branch Bus1->Bus2(1), no: 0
Branch Bus1->Bus5(1), no: 1
Branch Bus2->Bus3(1), no: 2
Branch Bus2->Bus4(1), no: 3
Branch Bus2->Bus5(1), no: 4
Branch Bus3->Bus4(1), no: 5
Branch Bus4->Bus5(1), no: 6
Branch Bus4->Bus7(1), no: 7
Branch Bus4->Bus9(1), no: 8
Branch Bus5->Bus6(1), no: 9
Branch Bus6->Bus11(1), no: 10
Branch Bus6->Bus12(1), no: 11
Branch Bus6->Bus13(1), no: 12
Branch Bus7->Bus8(1), no: 13
Branch Bus7->Bus9(1), no: 14
Branch Bus9->Bus10(1), no: 15
Branch Bus9->Bus14(1), no: 16
Branch Bus10->Bus11(1), no: 17
Branch Bus12->Bus13(1), no: 18
Branch Bus13->Bus14(1), no: 19
 */ 
