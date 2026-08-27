package org.interpss.core.adapter.psse.raw.aclf.powsybl;

import static org.junit.jupiter.api.Assertions.assertTrue;

import org.interpss.CorePluginTestSetup;
import org.interpss.fadapter.psse.PSSEDirectParser;
import org.junit.jupiter.api.Test;

import com.interpss.core.aclf.AclfNetwork;

/**
 * PowSyBl parser-unit leftovers (completed, Q-record, non-induction, whitespace-exported).
 */
public class PSSE_PowSyBl_Parser_Smoke_Test extends CorePluginTestSetup {

	private static final String DIR = "testData/psse/powsybl/parser/";

	private static AclfNetwork parse(String file) throws Exception {
		return new PSSEDirectParser().parse(DIR + file);
	}

	@Test
	public void exampleVersion32() throws Exception {
		AclfNetwork net = parse("ExampleVersion32.raw");
		assertTrue(net.getNoBus() > 0);
	}

	@Test
	public void ieee14Completed() throws Exception {
		AclfNetwork net = parse("IEEE_14_bus_completed.raw");
		assertTrue(net.getNoBus() >= 14);
		assertTrue(net.getNoBranch() > 0);
	}

	@Test
	public void ieee14CompletedRev35() throws Exception {
		AclfNetwork net = parse("IEEE_14_bus_completed_rev35.raw");
		assertTrue(net.getNoBus() >= 14);
	}

	@Test
	public void ieee14QRecordRev35() throws Exception {
		AclfNetwork net = parse("IEEE_14_bus_Q_record_rev35.raw");
		assertTrue(net.getNoBus() >= 14);
	}

	@Test
	public void ieee14NonInductionMachine() throws Exception {
		AclfNetwork net = parse("IEEE_14_bus_non_induction_machine_data.raw");
		assertTrue(net.getNoBus() >= 14);
	}

	@Test
	public void ieee14NonInductionMachineRev35() throws Exception {
		AclfNetwork net = parse("IEEE_14_bus_non_induction_machine_data_rev35.raw");
		assertTrue(net.getNoBus() >= 14);
	}

	@Test
	public void ieee14IsolatedBusesExported() throws Exception {
		AclfNetwork net = parse("IEEE_14_isolated_buses_exported.raw");
		assertTrue(net.getNoBus() >= 14);
	}

	@Test
	public void ieee14WhitespaceAsDelimiterExported() throws Exception {
		// Prefer PowSyBl comma-exported golden — DirectParser is comma-only
		AclfNetwork net = parse("IEEE_14_bus_whitespaceAsDelimiter_exported.raw");
		assertTrue(net.getNoBus() >= 14);
		assertTrue(net.getNoBranch() > 0);
	}
}
