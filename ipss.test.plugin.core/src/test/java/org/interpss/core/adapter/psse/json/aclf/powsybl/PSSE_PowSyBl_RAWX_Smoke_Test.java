package org.interpss.core.adapter.psse.json.aclf.powsybl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.interpss.CorePluginTestSetup;
import org.interpss.fadapter.psse.PSSEDirectParser;
import org.interpss.fadapter.psse.PSSEJsonDirectParser;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

import com.interpss.core.aclf.AclfNetwork;

/**
 * PowSyBl RAWX smoke coverage via {@link PSSEJsonDirectParser}.
 */
public class PSSE_PowSyBl_RAWX_Smoke_Test extends CorePluginTestSetup {

	private static final String DIR = "testData/psse/powsybl/rawx/";

	@Test
	public void ieee14Rev35Rawx() throws Exception {
		AclfNetwork net = new PSSEJsonDirectParser().parse(DIR + "IEEE_14_bus_rev35.rawx");
		assertTrue(net.getNoActiveBus() >= 14);
		assertTrue(net.getNoBranch() > 0);
	}

	@Test
	public void ieee14RawVsRawxBusCount() throws Exception {
		AclfNetwork raw = new PSSEDirectParser(35).parse("testData/psse/powsybl/ieee/IEEE_14_bus_rev35.raw");
		AclfNetwork rawx = new PSSEJsonDirectParser().parse(DIR + "IEEE_14_bus_rev35.rawx");
		assertEquals(raw.getNoActiveBus(), rawx.getNoActiveBus());
	}

	@Test
	public void ieee24Rev35Rawx() throws Exception {
		AclfNetwork net = new PSSEJsonDirectParser().parse(DIR + "IEEE_24_bus_rev35.rawx");
		assertTrue(net.getNoActiveBus() >= 24);
	}

	@Test
	public void twoSubstationsRawx() throws Exception {
		AclfNetwork net = new PSSEJsonDirectParser().parse(DIR + "twoSubstations_rev35.rawx");
		assertTrue(net.getNoActiveBus() > 0);
	}

	@Test
	@Disabled("NB overlay via RAWX not yet wired; track against RAW twoSubstations/IEEE14 NB tests")
	public void ieee14NodeBreakerRawx() throws Exception {
		AclfNetwork net = new PSSEJsonDirectParser().parse(DIR + "IEEE_14_bus_nodeBreaker_rev35.rawx");
		assertTrue(net.getSubstationMap().size() >= 2);
	}

	@Test
	public void minimalExampleRawx() throws Exception {
		AclfNetwork net = new PSSEJsonDirectParser().parse(DIR + "MinimalExample.rawx");
		assertTrue(net.getNoBus() > 0);
	}
}
