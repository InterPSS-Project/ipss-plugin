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
		assertBranchCurrentsMatchActivePhases(branch);

		Static3PBranch phaseBBranch = branchByName(network, "ln6076240-1");
		assertNotNull(phaseBBranch, "Missing IEEE8500 phase-B single-phase line");
		assertEquals(PhaseCode.B, phaseBBranch.getPhaseCode());
		assertBranchCurrentsMatchActivePhases(phaseBBranch);
	}

	@Test
	void fixedPointBranchCurrentRefreshMasksInactiveTwoPhaseCurrents() throws IOException {
		OpenDSSStaticDataParser parser = OpenDSSDataParser.forStaticNetwork();
		parser.setRegControlEnabled(false);
		assertTrue(parser.parseFeederData("testData/feeder/IEEE123", "IEEE123Master.dss"));
		assertTrue(parser.calcVoltageBases());
		assertTrue(parser.convertActualValuesToPU(1.0));

		Static3PNetwork network = parser.getStaticNetwork();
		DistributionPowerFlowAlgorithm powerFlow = ThreePhaseObjectFactory.createDistPowerFlowAlgorithm(network);
		powerFlow.setPFMethod(DistributionPFMethod.Fixed_Point);
		powerFlow.setInitBusVoltageEnabled(true);
		powerFlow.setMaxIteration(1000);
		powerFlow.setTolerance(1.0e-6);
		assertTrue(powerFlow.powerflow(), "IEEE123 static fixed-point power flow failed");

		Static3PBranch branch = branchByName(network, "l27");
		assertNotNull(branch, "Missing IEEE123 two-phase line L27");
		assertEquals(PhaseCode.AC, branch.getPhaseCode());
		assertBranchCurrentsMatchActivePhases(branch);
	}

	private static void assertBranchCurrentsMatchActivePhases(Static3PBranch branch) {
		Complex3x1 fromCurrent = branch.getCurrentAbcAtFromSide();
		Complex3x1 toCurrent = branch.getCurrentAbcAtToSide();
		int phaseMask = phaseMask(branch.getPhaseCode());
		for(int phase = 0; phase < 3; phase++) {
			if((phaseMask & (1 << phase)) == 0) {
				assertZero(phaseValue(fromCurrent, phase));
				assertZero(phaseValue(toCurrent, phase));
			}
		}

		Complex3x1 fromVoltage = ((IBus3Phase) branch.getFromBus()).get3PhaseVotlages();
		Complex3x1 toVoltage = ((IBus3Phase) branch.getToBus()).get3PhaseVotlages();
		for(int phase = 0; phase < 3; phase++) {
			if((phaseMask & (1 << phase)) != 0) {
				assertComplexEquals(branchCurrentFromFormula(branch, fromVoltage, toVoltage, phase, phaseMask),
						phaseValue(fromCurrent, phase));
				assertComplexEquals(branchCurrentToFormula(branch, fromVoltage, toVoltage, phase, phaseMask),
						phaseValue(toCurrent, phase));
			}
		}
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
			Complex3x1 toVoltage, int row, int phaseMask) {
		return branchCurrentFormula(branch.getYffabc(), fromVoltage, branch.getYftabc(), toVoltage,
				row, phaseMask);
	}

	private static Complex branchCurrentToFormula(IBranch3Phase branch, Complex3x1 fromVoltage,
			Complex3x1 toVoltage, int row, int phaseMask) {
		return branchCurrentFormula(branch.getYttabc(), toVoltage, branch.getYtfabc(), fromVoltage,
				row, phaseMask);
	}

	private static Complex branchCurrentFormula(org.interpss.numeric.datatype.Complex3x3 ySelf,
			Complex3x1 vSelf, org.interpss.numeric.datatype.Complex3x3 yOther, Complex3x1 vOther,
			int row, int phaseMask) {
		Complex current = Complex.ZERO;
		for(int col = 0; col < 3; col++) {
			if((phaseMask & (1 << col)) != 0) {
				current = current.add(phaseValue(ySelf, row, col).multiply(phaseValue(vSelf, col)))
						.add(phaseValue(yOther, row, col).multiply(phaseValue(vOther, col)));
			}
		}
		return current;
	}

	private static Complex phaseValue(Complex3x1 value, int phase) {
		if(phase == 1) {
			return value.b_1;
		}
		if(phase == 2) {
			return value.c_2;
		}
		return value.a_0;
	}

	private static Complex phaseValue(org.interpss.numeric.datatype.Complex3x3 value, int row, int col) {
		if(row == 0 && col == 0) return value.aa;
		if(row == 0 && col == 1) return value.ab;
		if(row == 0 && col == 2) return value.ac;
		if(row == 1 && col == 0) return value.ba;
		if(row == 1 && col == 1) return value.bb;
		if(row == 1 && col == 2) return value.bc;
		if(row == 2 && col == 0) return value.ca;
		if(row == 2 && col == 1) return value.cb;
		return value.cc;
	}

	private static int phaseMask(PhaseCode phaseCode) {
		if(phaseCode == PhaseCode.A) return 0b001;
		if(phaseCode == PhaseCode.B) return 0b010;
		if(phaseCode == PhaseCode.C) return 0b100;
		if(phaseCode == PhaseCode.AB) return 0b011;
		if(phaseCode == PhaseCode.AC) return 0b101;
		if(phaseCode == PhaseCode.BC) return 0b110;
		return 0b111;
	}

	private static void assertZero(Complex value) {
		assertEquals(0.0, value.abs(), 1.0e-12);
	}

	private static void assertComplexEquals(Complex expected, Complex actual) {
		assertEquals(expected.getReal(), actual.getReal(), 1.0e-10);
		assertEquals(expected.getImaginary(), actual.getImaginary(), 1.0e-10);
	}
}
