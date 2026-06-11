package org.interpss.threePhase.powerflow;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;

import org.apache.commons.math3.complex.Complex;
import org.interpss.numeric.datatype.Complex3x1;
import org.interpss.threePhase.dataParser.opendss.OpenDSSDataParser;
import org.interpss.threePhase.dataParser.opendss.OpenDSSStaticDataParser;
import org.interpss.threePhase.util.ThreePhaseObjectFactory;
import org.junit.jupiter.api.Test;

import com.interpss.core.acsc.PhaseCode;
import com.interpss.core.threephase.IBranch3Phase;
import com.interpss.core.threephase.IBus3Phase;
import com.interpss.core.threephase.Static3PBranch;
import com.interpss.core.threephase.Static3PNetwork;

class DistributionPowerFlowPhaseMaskTest {

	@Test
	void fixedPointBranchCurrentRefreshMasksInactiveSinglePhaseCurrents() throws IOException {
		OpenDSSStaticDataParser parser = OpenDSSDataParser.forStaticNetwork();
		parser.setRegControlEnabled(false);
		assertTrue(parser.parseFeederData("testData/feeder/IEEE8500", "Master-InterPSS.dss"));
		assertTrue(parser.calcVoltageBases());
		assertTrue(parser.convertActualValuesToPU(1.0));

		Static3PNetwork network = parser.getStaticNetwork();
		DistributionPowerFlowAlgorithm powerFlow = ThreePhaseObjectFactory.createDistPowerFlowAlgorithm(network);
		powerFlow.setPFMethod(DistributionPFMethod.Fixed_Point);
		powerFlow.setInitBusVoltageEnabled(true);
		powerFlow.setMaxIteration(1000);
		powerFlow.setTolerance(1.0e-6);
		assertTrue(powerFlow.powerflow(), "IEEE8500 static fixed-point power flow failed");

		Static3PBranch branch = branchByName(network, "ln5480058-1");
		assertNotNull(branch, "Missing IEEE8500 single-phase line on mismatch path");
		assertEquals(PhaseCode.A, branch.getPhaseCode());

		Complex3x1 fromCurrent = branch.getCurrentAbcAtFromSide();
		Complex3x1 toCurrent = branch.getCurrentAbcAtToSide();
		assertZero(fromCurrent.b_1);
		assertZero(fromCurrent.c_2);
		assertZero(toCurrent.b_1);
		assertZero(toCurrent.c_2);

		Complex3x1 fromVoltage = ((IBus3Phase) branch.getFromBus()).get3PhaseVotlages();
		Complex3x1 toVoltage = ((IBus3Phase) branch.getToBus()).get3PhaseVotlages();
		assertComplexEquals(branchCurrentFromFormula(branch, fromVoltage, toVoltage), fromCurrent.a_0);
		assertComplexEquals(branchCurrentToFormula(branch, fromVoltage, toVoltage), toCurrent.a_0);
	}

	private static Static3PBranch branchByName(Static3PNetwork network, String branchName) {
		for(Static3PBranch branch : network.getBranchList()) {
			String id = branch.getId().toLowerCase();
			String name = branch.getName() == null ? "" : branch.getName().toLowerCase();
			if(id.equals(branchName) || id.endsWith(":" + branchName) || name.equals(branchName)) {
				return branch;
			}
		}
		return null;
	}

	private static Complex branchCurrentFromFormula(IBranch3Phase branch, Complex3x1 fromVoltage,
			Complex3x1 toVoltage) {
		return branch.getYffabc().aa.multiply(fromVoltage.a_0)
				.add(branch.getYftabc().aa.multiply(toVoltage.a_0));
	}

	private static Complex branchCurrentToFormula(IBranch3Phase branch, Complex3x1 fromVoltage,
			Complex3x1 toVoltage) {
		return branch.getYttabc().aa.multiply(toVoltage.a_0)
				.add(branch.getYtfabc().aa.multiply(fromVoltage.a_0));
	}

	private static void assertZero(Complex value) {
		assertEquals(0.0, value.abs(), 1.0e-12);
	}

	private static void assertComplexEquals(Complex expected, Complex actual) {
		assertEquals(expected.getReal(), actual.getReal(), 1.0e-10);
		assertEquals(expected.getImaginary(), actual.getImaginary(), 1.0e-10);
	}
}
