
package org.interpss.plugin.optadj;

import org.interpss.CorePluginFactory;
import org.interpss.CorePluginTestSetup;
import org.interpss.fadapter.IpssFileAdapter;
import org.interpss.numeric.datatype.LimitType;
import com.interpss.common.exp.InterpssException;
import com.interpss.core.aclf.AclfBranch;
import com.interpss.core.aclf.AclfNetwork;

public class IEEE14_SensHelper_Test extends CorePluginTestSetup {

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
		net.createAclfGenUIDLookupTable(false).forEach((k, gen) -> {
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
