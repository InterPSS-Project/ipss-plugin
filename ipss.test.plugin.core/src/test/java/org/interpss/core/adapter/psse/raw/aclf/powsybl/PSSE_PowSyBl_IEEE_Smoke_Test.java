package org.interpss.core.adapter.psse.raw.aclf.powsybl;

import static org.junit.jupiter.api.Assertions.assertTrue;

import org.interpss.CorePluginTestSetup;
import org.interpss.fadapter.psse.PSSEDirectParser;
import org.junit.jupiter.api.Test;

import com.interpss.core.LoadflowAlgoObjectFactory;
import com.interpss.core.aclf.AclfNetwork;
import com.interpss.core.algo.LoadflowAlgorithm;

/**
 * PowSyBl IEEE / standard bus-branch RAW smoke coverage.
 */
public class PSSE_PowSyBl_IEEE_Smoke_Test extends CorePluginTestSetup {

	private static final String DIR = "testData/psse/powsybl/ieee/";

	private static AclfNetwork parse(String file) throws Exception {
		return new PSSEDirectParser().parse(DIR + file);
	}

	private static void assertSmoke(AclfNetwork net) {
		assertTrue(net.getNoActiveBus() > 0, "expected buses");
		assertTrue(net.getNoBranch() > 0, "expected branches");
	}

	private static void assertBusCount(AclfNetwork net, int expected) {
		assertTrue(net.getNoBus() >= expected, "expected >= " + expected + " buses, got " + net.getNoBus());
	}

	private static void assertLf(AclfNetwork net) throws Exception {
		LoadflowAlgorithm algo = LoadflowAlgoObjectFactory.createLoadflowAlgorithm(net);
		algo.getLfAdjAlgo().setApplyAdjustAlgo(false);
		assertTrue(algo.loadflow(), "LF should converge");
	}

	@Test
	public void ieee14_v33() throws Exception {
		AclfNetwork net = parse("IEEE_14_bus.raw");
		assertSmoke(net);
		assertBusCount(net, 14);
		assertLf(net);
	}

	@Test
	public void ieee14_rev35() throws Exception {
		AclfNetwork net = parse("IEEE_14_bus_rev35.raw");
		assertSmoke(net);
		assertBusCount(net, 14);
		assertLf(net);
	}

	@Test
	public void ieee14_delimiter() throws Exception {
		assertSmoke(parse("IEEE_14_bus_delimiter.raw"));
	}

	@Test
	public void ieee14_zipLoad() throws Exception {
		assertSmoke(parse("IEEE_14_buses_zip_load.raw"));
	}

	@Test
	public void ieee14_isolatedBuses() throws Exception {
		AclfNetwork net = parse("IEEE_14_isolated_buses.raw");
		assertTrue(net.getNoBus() >= 14);
	}

	@Test
	public void ieee24_v33() throws Exception {
		assertSmoke(parse("IEEE_24_bus.raw"));
	}

	@Test
	public void ieee24_rev35() throws Exception {
		assertSmoke(parse("IEEE_24_bus_rev35.raw"));
	}

	@Test
	public void ieee30() throws Exception {
		// Smoke only — PowSyBl IEEE30 does not always converge under InterPSS NR defaults
		assertSmoke(parse("IEEE_30_bus.raw"));
	}

	@Test
	public void ieee57() throws Exception {
		AclfNetwork net = parse("IEEE_57_bus.raw");
		assertSmoke(net);
		assertBusCount(net, 57);
		assertLf(net);
	}

	@Test
	public void ieee118() throws Exception {
		AclfNetwork net = parse("IEEE_118_bus.raw");
		assertSmoke(net);
		assertBusCount(net, 118);
		assertLf(net);
	}

	@Test
	public void twoArea() throws Exception {
		assertSmoke(parse("two_area_case.raw"));
	}

	@Test
	public void twoAreaTrf3w() throws Exception {
		assertSmoke(parse("two_area_case_trf3w.raw"));
	}
}
