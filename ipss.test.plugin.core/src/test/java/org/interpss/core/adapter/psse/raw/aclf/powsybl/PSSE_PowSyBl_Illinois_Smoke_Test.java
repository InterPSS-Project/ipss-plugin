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

	private static AclfNetwork parseTry(String file, int... versions) throws Exception {
		Exception last = null;
		for (int v : versions) {
			try {
				return new PSSEDirectParser(v).parse(DIR + file);
			} catch (Exception e) {
				last = e;
			}
		}
		throw last != null ? last : new IllegalStateException("no version tried");
	}

	@Test
	public void wscc9() throws Exception {
		assertTrue(parseTry("WSCC_9_bus.raw", 33, 30).getNoBus() >= 9);
	}

	@Test
	public void ieee14() throws Exception {
		assertTrue(parseTry("IEEE_14_bus.raw", 33, 30).getNoBus() >= 14);
	}

	@Test
	public void ieee24() throws Exception {
		assertTrue(parseTry("IEEE_24_bus.RAW", 33, 30).getNoBus() >= 24);
	}

	@Test
	public void ieee30() throws Exception {
		assertTrue(parseTry("IEEE_30_bus.RAW", 33, 30, 32).getNoBus() >= 30);
	}

	@Test
	public void ieee39() throws Exception {
		assertTrue(parseTry("IEEE_39_bus.RAW", 30, 33).getNoBus() >= 39);
	}

	@Test
	public void ieee57() throws Exception {
		assertTrue(parseTry("IEEE_57_bus.RAW", 33, 30).getNoBus() >= 57);
	}

	@Test
	public void ieee118() throws Exception {
		assertTrue(parseTry("IEEE_118_bus.RAW", 33, 30).getNoBus() >= 118);
	}

	@Test
	public void ieee300() throws Exception {
		assertTrue(parseTry("IEEE300Bus.raw", 30, 33).getNoBus() >= 300);
	}

	@Test
	public void twoArea() throws Exception {
		assertTrue(parseTry("two_area_case.RAW", 33, 30).getNoBus() > 0);
	}

	@Test
	public void ieeeRts96() throws Exception {
		assertTrue(parseTry("IEEE_RTS_96_bus.RAW", 33, 30).getNoBus() >= 70);
	}
}
