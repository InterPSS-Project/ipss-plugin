package org.interpss.threePhase.system;

import static org.junit.jupiter.api.Assertions.assertTrue;

import org.apache.commons.math3.complex.Complex;
import org.interpss.numeric.datatype.Complex3x1;
import org.interpss.numeric.datatype.Complex3x3;
import org.interpss.threePhase.dataParser.opendss.OpenDSSDataParser;
import org.interpss.threePhase.dataParser.opendss.OpenDSSStaticDataParser;
import org.interpss.threePhase.powerflow.DistributionPFMethod;
import org.interpss.threePhase.powerflow.DistributionPowerFlowAlgorithm;
import org.interpss.threePhase.util.ThreePhaseObjectFactory;
import org.junit.jupiter.api.Test;

import com.interpss.core.aclf.AclfBranch;
import com.interpss.core.acsc.AcscBranch;
import com.interpss.core.threephase.IBranch3Phase;
import com.interpss.core.threephase.IBus3Phase;
import com.interpss.core.threephase.Static3PNetwork;

public class RegulatorTapCompensationKclTest {
	private static final double CURRENT_TOLERANCE = 1.0e-8;

	@Test
	void singlePhaseTapCompensationPreservesBothTerminalKclForFixedVoltages() {
		OpenDSSStaticDataParser parser = OpenDSSDataParser.forStaticNetwork();
		assertTrue(parser.parseFeederData("testData/feeder/IEEE123", "IEEE123Master.dss"));
		assertTrue(parser.calcVoltageBases());
		assertTrue(parser.convertActualValuesToPU(1.0));

		Static3PNetwork network = parser.getStaticNetwork();
		DistributionPowerFlowAlgorithm powerFlow = ThreePhaseObjectFactory
				.createDistPowerFlowAlgorithm(network);
		powerFlow.setPFMethod(DistributionPFMethod.Fixed_Point);
		powerFlow.setInitBusVoltageEnabled(true);
		powerFlow.setMaxIteration(200);
		powerFlow.setTolerance(1.0e-8);
		assertTrue(powerFlow.powerflow(), "Base IEEE123 power flow should converge before KCL check");

		AclfBranch aclfBranch = findBranch(network, "reg2a");
		IBranch3Phase branch = (IBranch3Phase) aclfBranch;
		Complex3x1 fromVoltage = ((IBus3Phase) aclfBranch.getFromBus()).get3PhaseVotlages();
		Complex3x1 toVoltage = ((IBus3Phase) aclfBranch.getToBus()).get3PhaseVotlages();

		Complex3x3 baseYff = copy(branch.getYffabc());
		Complex3x3 baseYft = copy(branch.getYftabc());
		Complex3x3 baseYtf = copy(branch.getYtfabc());
		Complex3x3 baseYtt = copy(branch.getYttabc());
		Complex3x1 baseFromCurrent = terminalCurrent(baseYff, baseYft, fromVoltage, toVoltage);
		Complex3x1 baseToCurrent = terminalCurrent(baseYtf, baseYtt, fromVoltage, toVoltage);

		((AcscBranch) aclfBranch).setToTurnRatio(((AcscBranch) aclfBranch).getToTurnRatio() + 0.00625);

		Complex3x1 rebuiltFromCurrent = terminalCurrent(branch.getYffabc(), branch.getYftabc(),
				fromVoltage, toVoltage);
		Complex3x1 rebuiltToCurrent = terminalCurrent(branch.getYtfabc(), branch.getYttabc(),
				fromVoltage, toVoltage);
		Complex3x1 fromCompensation = branch.getYffabc().subtract(baseYff).multiply(fromVoltage)
				.add(branch.getYftabc().subtract(baseYft).multiply(toVoltage));
		Complex3x1 toCompensation = branch.getYtfabc().subtract(baseYtf).multiply(fromVoltage)
				.add(branch.getYttabc().subtract(baseYtt).multiply(toVoltage));

		assertVectorEquals(rebuiltFromCurrent, baseFromCurrent.add(fromCompensation),
				"from-terminal KCL");
		assertVectorEquals(rebuiltToCurrent, baseToCurrent.add(toCompensation),
				"to-terminal KCL");
		assertTrue(abs(fromCompensation.a_0) > 0.0,
				"Phase-A tap change should produce a nonzero from-side compensation current");
		assertTrue(abs(toCompensation.a_0) > 0.0,
				"Phase-A tap change should produce a nonzero to-side compensation current");
		assertTrue(abs(fromCompensation.b_1) < CURRENT_TOLERANCE
				&& abs(fromCompensation.c_2) < CURRENT_TOLERANCE,
				"Single-phase reg2a compensation should not perturb inactive from-side phases");
		assertTrue(abs(toCompensation.b_1) < CURRENT_TOLERANCE
				&& abs(toCompensation.c_2) < CURRENT_TOLERANCE,
				"Single-phase reg2a compensation should not perturb inactive to-side phases");
	}

	private static Complex3x1 terminalCurrent(Complex3x3 selfY, Complex3x3 mutualY,
			Complex3x1 selfVoltage, Complex3x1 mutualVoltage) {
		return selfY.multiply(selfVoltage).add(mutualY.multiply(mutualVoltage));
	}

	private static AclfBranch findBranch(Static3PNetwork network, String namePart) {
		for(Object branchObject : network.getBranchList()) {
			AclfBranch branch = (AclfBranch) branchObject;
			if(branch.getId().contains(namePart) || branch.getName().contains(namePart)) {
				return branch;
			}
		}
		throw new AssertionError("Missing branch containing " + namePart);
	}

	private static Complex3x3 copy(Complex3x3 source) {
		Complex3x3 copy = new Complex3x3();
		copy.aa = source.aa;
		copy.ab = source.ab;
		copy.ac = source.ac;
		copy.ba = source.ba;
		copy.bb = source.bb;
		copy.bc = source.bc;
		copy.ca = source.ca;
		copy.cb = source.cb;
		copy.cc = source.cc;
		return copy;
	}

	private static void assertVectorEquals(Complex3x1 expected, Complex3x1 actual, String label) {
		assertComplexEquals(expected.a_0, actual.a_0, label + " phase A");
		assertComplexEquals(expected.b_1, actual.b_1, label + " phase B");
		assertComplexEquals(expected.c_2, actual.c_2, label + " phase C");
	}

	private static void assertComplexEquals(Complex expected, Complex actual, String label) {
		assertTrue(expected.subtract(actual).abs() < CURRENT_TOLERANCE,
				label + " mismatch: expected=" + expected + ", actual=" + actual);
	}

	private static double abs(Complex value) {
		return value == null ? 0.0 : value.abs();
	}
}
