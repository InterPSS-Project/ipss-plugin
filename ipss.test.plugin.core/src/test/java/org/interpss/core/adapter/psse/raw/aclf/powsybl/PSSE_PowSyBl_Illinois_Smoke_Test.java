package org.interpss.core.adapter.psse.raw.aclf.powsybl;

import static org.junit.jupiter.api.Assertions.assertTrue;

import org.interpss.CorePluginTestSetup;
import org.interpss.fadapter.psse.PSSEDirectParser;
import org.junit.jupiter.api.Test;

import com.interpss.core.aclf.AclfNetwork;

/**
 * PowSyBl Illinois literature-based smoke.
 * Not registered in {@code CorePluginTestSuite} (optional stretch).
 */
public class PSSE_PowSyBl_Illinois_Smoke_Test extends CorePluginTestSetup {

	private static final String DIR = "testData/psse/powsybl/illinois/";

	private static AclfNetwork parseTry(String file) throws Exception {
		return new PSSEDirectParser().parse(DIR + file);
	}

	@Test
	public void wscc9() throws Exception {
		assertTrue(parseTry("WSCC_9_bus.raw").getNoBus() >= 9);
	}

	@Test
	public void ieee14() throws Exception {
		assertTrue(parseTry("IEEE_14_bus.raw").getNoBus() >= 14);
	}

	@Test
	public void ieee24() throws Exception {
		assertTrue(parseTry("IEEE_24_bus.RAW").getNoBus() >= 24);
	}

	@Test
	public void ieee30() throws Exception {
		assertTrue(parseTry("IEEE_30_bus.RAW").getNoBus() >= 30);
	}

	@Test
	public void ieee39() throws Exception {
		assertTrue(parseTry("IEEE_39_bus.RAW").getNoBus() >= 39);
	}

	@Test
	public void ieee57() throws Exception {
		assertTrue(parseTry("IEEE_57_bus.RAW").getNoBus() >= 57);
	}

	@Test
	public void ieee118() throws Exception {
		assertTrue(parseTry("IEEE_118_bus.RAW").getNoBus() >= 118);
	}

	@Test
	public void ieee300() throws Exception {
		assertTrue(parseTry("IEEE300Bus.raw").getNoBus() >= 300);
	}

	@Test
	public void twoArea() throws Exception {
		assertTrue(parseTry("two_area_case.RAW").getNoBus() > 0);
	}

	@Test
	public void ieeeRts96() throws Exception {
		assertTrue(parseTry("IEEE_RTS_96_bus.RAW").getNoBus() >= 70);
	}
}
