package org.interpss.plugin.optadj;

import org.interpss.CorePluginFactory;
import org.interpss.CorePluginTestSetup;
import org.interpss.fadapter.IpssFileAdapter.FileFormat;
import org.interpss.numeric.datatype.LimitType;
import com.interpss.core.aclf.AclfBranch;
import com.interpss.core.aclf.AclfNetwork;

/** Shared IEEE-39 test network setup for optadj regression tests. */
public class IEEE39_TestCaseInfo extends CorePluginTestSetup {

	private static final String IEEE39_IEEE = "testData/adpter/ieee_format/ieee39.ieee";
	private static final double BRANCH_RATING_MVA = 600.0;

	static AclfNetwork createTestCaseNetwork() throws Exception {
		AclfNetwork net = CorePluginFactory.getFileAdapter(FileFormat.IEEECDF)
				.load(IEEE39_IEEE)
				.getAclfNet();

		net.getBusList().forEach(bus -> {
			if (bus.getGenP() == 0) {
				bus.getContributeGenList().clear();
			}
		});

		net.createAclfGenNameLookupTable(true).values()
				.forEach(gen -> gen.setPGenLimit(new LimitType(7, 0)));

		net.getBranchList().forEach(branch -> {
			AclfBranch aclfBranch = (AclfBranch) branch;
			aclfBranch.setRatingMva1(BRANCH_RATING_MVA);
		});

		return net;
	}
}
