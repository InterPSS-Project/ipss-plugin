package org.interpss.plugin.optadj.ieee39;

import org.interpss.CorePluginFactory;
import org.interpss.CorePluginTestSetup;
import org.interpss.fadapter.IpssFileAdapter.FileFormat;
import org.interpss.numeric.datatype.LimitType;

import com.interpss.core.aclf.AclfBranch;
import com.interpss.core.aclf.AclfNetwork;

/** Shared IEEE-39 test network setup for optadj regression tests. */
public class IEEE39_TestCaseInfo extends CorePluginTestSetup {

	private static final String IEEE39_IEEE = TEST_ROOT + "testData/adpter/ieee_format/ieee39.ieee";
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
				.forEach(gen -> {
					if (gen.getName().equals("Bus39-G1")) {
						gen.setPGenLimit(new LimitType(10, 0));
					} else if (gen.getName().equals("Bus38-G1")) {
						gen.setPGenLimit(new LimitType(8.3, 0));
					} else {
						gen.setPGenLimit(new LimitType(7, 0));
					}
				});

		net.getBranchList().forEach(branch -> {
			AclfBranch aclfBranch = (AclfBranch) branch;
			//aclfBranch.setName(aclfBranch.getId());
			aclfBranch.setRatingMva1(BRANCH_RATING_MVA);
		});

		return net;
	}
}
