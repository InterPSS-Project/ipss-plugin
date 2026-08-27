package org.interpss.core.adapter.psse.raw.aclf.powsybl;

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

	private static AclfNetwork parse(String file) throws Exception {
		return new PSSEDirectParser().parse(DIR + file);
	}

	@Test
	public void switchedShunt() throws Exception {
		AclfNetwork net = parse("SwitchedShunt.raw");
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
		AclfNetwork net = parse("SwitchedShuntWithZeroVswlo.raw");
		assertTrue(net.getNoActiveBus() > 0);
	}

	@Test
	public void threeMibT3wModified() throws Exception {
		AclfNetwork net = parse("ThreeMIB_T3W_modified.raw");
		assertTrue(net.getNoActiveBus() > 0);
		assertTrue(net.getNoBranch() > 0);
		// 3-winding creates a star bus in InterPSS
		boolean has3wStar = net.getBusList().stream()
				.anyMatch(b -> b.getId() != null && b.getId().contains("3W"));
		assertTrue(has3wStar || net.getNoBranch() >= 3,
				"expected 3W topology markers or enough branches");
	}

	@Test
	public void threeMibT3wPhase() throws Exception {
		assertTrue(parse("ThreeMIB_T3W_phase.raw").getNoActiveBus() > 0);
	}

	@Test
	public void twoWindingPhase() throws Exception {
		assertTrue(parse("TwoWindingsTransformerPhase.raw").getNoActiveBus() > 0);
	}

	@Test
	public void transformersZeroNomV() throws Exception {
		// Must not crash on zero nominal voltage (parser / builder tolerate or remap)
		assertTrue(parse("TransformersWithZeroNomV.raw").getNoBus() > 0);
	}

	@Test
	public void remoteControl() throws Exception {
		assertTrue(parse("remoteControl.raw").getNoBus() > 0);
	}

	@Test
	public void isolatedSlackBus() throws Exception {
		assertTrue(parse("IsolatedSlackBus.raw").getNoBus() > 0);
	}

	@Test
	public void nonTransformerBranchZeroX() throws Exception {
		assertTrue(parse("NonTranformerBranchZeroX.raw").getNoActiveBus() > 0);
	}

	@Test
	public void specialCharacters() throws Exception {
		assertTrue(parse("RawCaseWithSpecialCharacters.raw").getNoActiveBus() > 0);
	}

	@Test
	public void transformersVoltageControlUndefinedBus() throws Exception {
		assertTrue(parse("TransformersWithVoltageControlAndNotDefinedControlledBus.raw").getNoBus() > 0);
	}

	@Test
	public void substationThreeBusesSameNomVTwoAreas() throws Exception {
		AclfNetwork net = parse("SubstationWithThreeBusesAtTheSameNominalVoltageInTwoDifferentAreas.raw");
		assertTrue(net.getNoBus() >= 3);
	}
}
