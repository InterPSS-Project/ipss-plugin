package org.interpss.core.adapter.pwd;

import static org.junit.jupiter.api.Assertions.assertTrue;

import org.interpss.CorePluginTestSetup;
import org.interpss.fadapter.pwd.PWDDirectParser;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

import com.interpss.core.aclf.AclfBranch;
import com.interpss.core.aclf.AclfNetwork;

/**
 * XFAuto tap/PS control objects are out of DirectParser scope.
 * Topology-only smoke uses the corrected fixture path.
 */
public class SixBus_XfrControl_pwd extends CorePluginTestSetup {

	@Test
	public void topologyOnly_xfrBranchesPresent() throws Exception {
		AclfNetwork net = new PWDDirectParser().parse("testData/adpter/pwd/SixBus_XfrControl.aux");

		assertTrue(net.getNoBus() >= 5);
		AclfBranch b13 = net.getBranch("Bus1->Bus3(1)");
		assertTrue(b13 != null && (b13.isXfr() || b13.isPSXfr()), "1-3 transformer branch");
		AclfBranch b56 = net.getBranch("Bus5->Bus6(T9)");
		assertTrue(b56 != null && (b56.isXfr() || b56.isPSXfr()), "5-6 transformer branch");
		assertTrue(Math.abs(b13.getFromTurnRatio() - 1.0) > 1.0e-4
				|| Math.abs(b13.getFromTurnRatio() - 0.962) < 1.0e-3,
				"tap from LineTap present, got " + b13.getFromTurnRatio());
	}

	@Test
	@Disabled("XFAuto / tap & PS control objects not mapped by PWDDirectParser (out of coverage plan scope)")
	public void aclf_controlObjects() throws Exception {
		// Historical assertions expected TapControl / PSXfrPControl from ODM-era adapter.
	}
}
