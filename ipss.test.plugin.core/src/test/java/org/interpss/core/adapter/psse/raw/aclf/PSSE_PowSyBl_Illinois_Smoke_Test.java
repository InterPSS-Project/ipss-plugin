package org.interpss.core.adapter.psse.raw.aclf;

import static org.junit.jupiter.api.Assertions.assertTrue;

import org.interpss.CorePluginTestSetup;
import org.interpss.fadapter.psse.PSSEDirectParser;
import org.junit.jupiter.api.Test;

import com.interpss.core.aclf.AclfNetwork;

/**
 * PowSyBl Illinois literature-based smoke (WSCC 9, IEEE 39, IEEE 300).
 * Not registered in {@code CorePluginTestSuite} (optional stretch).
 */
public class PSSE_PowSyBl_Illinois_Smoke_Test extends CorePluginTestSetup {

	private static final String DIR = "testData/psse/powsybl/illinois/";

	@Test
	public void wscc9() throws Exception {
		AclfNetwork net = new PSSEDirectParser(33).parse(DIR + "WSCC_9_bus.raw");
		assertTrue(net.getNoActiveBus() >= 9);
	}

	@Test
	public void ieee39() throws Exception {
		// Version in file header — try 30 first (common for older Illinois pack)
		AclfNetwork net;
		try {
			net = new PSSEDirectParser(30).parse(DIR + "IEEE_39_bus.RAW");
		} catch (Exception e) {
			net = new PSSEDirectParser(33).parse(DIR + "IEEE_39_bus.RAW");
		}
		assertTrue(net.getNoActiveBus() >= 39);
	}

	@Test
	public void ieee300() throws Exception {
		AclfNetwork net;
		try {
			net = new PSSEDirectParser(30).parse(DIR + "IEEE300Bus.raw");
		} catch (Exception e) {
			net = new PSSEDirectParser(33).parse(DIR + "IEEE300Bus.raw");
		}
		assertTrue(net.getNoActiveBus() >= 300);
	}
}
