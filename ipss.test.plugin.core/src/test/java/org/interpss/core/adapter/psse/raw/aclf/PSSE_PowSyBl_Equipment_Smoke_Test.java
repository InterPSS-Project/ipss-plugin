package org.interpss.core.adapter.psse.raw.aclf;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.interpss.CorePluginTestSetup;
import org.interpss.fadapter.psse.PSSEDirectParser;
import org.junit.jupiter.api.Test;

import com.interpss.core.aclf.AclfBus;
import com.interpss.core.aclf.AclfNetwork;

/**
 * PowSyBl equipment / control edge-case RAW smoke coverage.
 */
public class PSSE_PowSyBl_Equipment_Smoke_Test extends CorePluginTestSetup {

	private static final String DIR = "testData/psse/powsybl/equipment/";

	private static AclfNetwork parse(String file, int version) throws Exception {
		return new PSSEDirectParser(version).parse(DIR + file);
	}

	@Test
	public void switchedShunt() throws Exception {
		AclfNetwork net = parse("SwitchedShunt.raw", 33);
		assertTrue(net.getNoActiveBus() > 0);
		boolean found = false;
		for (AclfBus bus : net.getBusList()) {
			if (bus.isSwitchedShunt()) {
				found = true;
				assertNotNull(bus.getFirstSwitchedShunt(true));
				break;
			}
		}
		assertTrue(found, "expected at least one switched shunt");
	}

	@Test
	public void switchedShuntZeroVswlo() throws Exception {
		AclfNetwork net = parse("SwitchedShuntWithZeroVswlo.raw", 33);
		assertTrue(net.getNoActiveBus() > 0);
	}

	@Test
	public void threeMibT3wModified() throws Exception {
		AclfNetwork net = parse("ThreeMIB_T3W_modified.raw", 33);
		assertTrue(net.getNoActiveBus() > 0);
		assertTrue(net.getNoBranch() > 0);
	}

	@Test
	public void threeMibT3wPhase() throws Exception {
		assertTrue(parse("ThreeMIB_T3W_phase.raw", 33).getNoActiveBus() > 0);
	}

	@Test
	public void twoWindingPhase() throws Exception {
		assertTrue(parse("TwoWindingsTransformerPhase.raw", 33).getNoActiveBus() > 0);
	}

	@Test
	public void transformersZeroNomV() throws Exception {
		// Must not crash on zero nominal voltage (parser / builder tolerate or remap)
		assertTrue(parse("TransformersWithZeroNomV.raw", 33).getNoBus() > 0);
	}

	@Test
	public void remoteControl() throws Exception {
		assertTrue(parse("remoteControl.raw", 33).getNoBus() > 0);
	}

	@Test
	public void isolatedSlackBus() throws Exception {
		assertTrue(parse("IsolatedSlackBus.raw", 33).getNoBus() > 0);
	}

	@Test
	public void nonTransformerBranchZeroX() throws Exception {
		assertTrue(parse("NonTranformerBranchZeroX.raw", 33).getNoActiveBus() > 0);
	}

	@Test
	public void specialCharacters() throws Exception {
		assertTrue(parse("RawCaseWithSpecialCharacters.raw", 33).getNoActiveBus() > 0);
	}
}
