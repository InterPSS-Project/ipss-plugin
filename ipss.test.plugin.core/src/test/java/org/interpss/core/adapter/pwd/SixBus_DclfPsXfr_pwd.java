package org.interpss.core.adapter.pwd;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.interpss.CorePluginTestSetup;
import org.interpss.fadapter.pwd.PWDDirectParser;
import org.interpss.plugin.pssl.plugin.IpssAdapter;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

import com.interpss.core.DclfAlgoObjectFactory;
import com.interpss.core.aclf.AclfBranch;
import com.interpss.core.aclf.AclfNetwork;
import com.interpss.core.algo.dclf.SenAnalysisAlgorithm;

/**
 * Narrowed SixBus phase-shifter DCLF smoke for PWD AUX.
 * Print-only historical variants remain disabled.
 */
public class SixBus_DclfPsXfr_pwd extends CorePluginTestSetup {

	@Test
	public void sixBus_2WPsXfr_parseAndDclf() throws Exception {
		AclfNetwork net = new PWDDirectParser().parse("testData/adpter/pwd/SixBus_2WPsXfr.aux");

		assertTrue(net.getNoBus() >= 5);
		assertTrue(net.getNoBranch() >= 5);
		assertTrue(net.getBranchList().stream().anyMatch(b -> ((AclfBranch) b).isPSXfr()),
				"Bus5->Bus6 LinePhase=30 should map as PS xfr");
		assertTrue(net.getBusList().stream().anyMatch(b -> b.isSwing()),
				"swing bus required for DCLF");

		SenAnalysisAlgorithm algo = DclfAlgoObjectFactory.createSenAnalysisAlgorithm(net);
		boolean ok = algo.calculateDclf();
		assertTrue(ok, "DCLF should succeed on SixBus_2WPsXfr");
		assertTrue(Double.isFinite(algo.getBusAngle("Bus1")), "DCLF angle finite");
		assertTrue(Double.isFinite(algo.getBusAngle("Bus5")), "DCLF angle finite");
	}

	@Test
	public void sixBus_2WPsXfr_facadeLoad() throws Exception {
		AclfNetwork net = IpssAdapter.importAclfNet("testData/adpter/pwd/SixBus_2WPsXfr.aux")
				.setFormat(IpssAdapter.FileFormat.PWD)
				.load()
				.getImportedObj();
		assertNotNull(net);
		assertTrue(net.getNoBus() >= 5);
		assertTrue(net.getNoBranch() >= 5);
	}

	@Test
	@Disabled("Duplicate print-only historical variant of SixBus_2WPsXfr_1")
	public void dclf1() throws Exception {
		AclfNetwork net = IpssAdapter.importAclfNet("testData/adpter/pwd/SixBus_2WPsXfr_1.aux")
				.setFormat(IpssAdapter.FileFormat.PWD)
				.load()
				.getImportedObj();
		SenAnalysisAlgorithm algo = DclfAlgoObjectFactory.createSenAnalysisAlgorithm(net);
		algo.calculateDclf();
	}

	@Test
	@Disabled("Duplicate print-only historical variant")
	public void dclf2() throws Exception {
		AclfNetwork net = IpssAdapter.importAclfNet("testData/adpter/pwd/SixBus_2WPsXfr_1.aux")
				.setFormat(IpssAdapter.FileFormat.PWD)
				.load()
				.getImportedObj();
		SenAnalysisAlgorithm algo = DclfAlgoObjectFactory.createSenAnalysisAlgorithm(net);
		algo.calculateDclf();
	}

	@Test
	@Disabled("Duplicate print-only historical variant")
	public void dclf3() throws Exception {
		AclfNetwork net = IpssAdapter.importAclfNet("testData/adpter/pwd/SixBus_2WPsXfr_1.aux")
				.setFormat(IpssAdapter.FileFormat.PWD)
				.load()
				.getImportedObj();
		SenAnalysisAlgorithm algo = DclfAlgoObjectFactory.createSenAnalysisAlgorithm(net);
		algo.calculateDclf();
	}

	@Test
	@Disabled("Duplicate print-only historical variant")
	public void dclf4() throws Exception {
		AclfNetwork net = IpssAdapter.importAclfNet("testData/adpter/pwd/SixBus_2WPsXfr_2.aux")
				.setFormat(IpssAdapter.FileFormat.PWD)
				.load()
				.getImportedObj();
		SenAnalysisAlgorithm algo = DclfAlgoObjectFactory.createSenAnalysisAlgorithm(net);
		algo.calculateDclf();
	}
}
