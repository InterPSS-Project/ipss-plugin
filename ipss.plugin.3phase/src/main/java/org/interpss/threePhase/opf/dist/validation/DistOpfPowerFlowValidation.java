package org.interpss.threePhase.opf.dist.validation;

import org.apache.commons.math3.complex.Complex;
import org.interpss.numeric.datatype.Complex3x1;
import org.interpss.threePhase.basic.dstab.DStab3PBranch;
import org.interpss.threePhase.basic.dstab.DStab3PBus;
import org.interpss.threePhase.dynamic.DStabNetwork3Phase;
import org.interpss.threePhase.opf.dist.DistOpfOptions;
import org.interpss.threePhase.opf.dist.DistOpfResult;
import org.interpss.threePhase.powerflow.DistributionPowerFlowAlgorithm;
import org.interpss.threePhase.util.ThreePhaseObjectFactory;

public class DistOpfPowerFlowValidation {

	public DistOpfResult validate(DStabNetwork3Phase net, DistOpfResult result, DistOpfOptions options) {
		if (!result.isSolved()) {
			return result.addWarning("Power-flow validation skipped because DistOPF was not solved");
		}
		result.applySetpointsToNetwork(net);
		DistributionPowerFlowAlgorithm powerFlow = ThreePhaseObjectFactory.createDistPowerFlowAlgorithm(net);
		powerFlow.setTolerance(options.getPowerFlowTolerance());
		powerFlow.setMaxIteration(options.getMaxPowerFlowIterations());
		boolean converged = powerFlow.powerflow();
		result.setPowerFlowConverged(converged);
		result.setPowerFlowIterationCount(powerFlow.getIterationCount());
		result.setMaxPowerFlowVoltageDiff(maxVoltageDiff(net, result));
		result.setMaxPowerFlowVoltageViolation(maxVoltageViolation(net, options));
		result.setMaxPowerFlowBranchLimitViolation(maxBranchLimitViolation(net));
		return result;
	}

	private static double maxVoltageDiff(DStabNetwork3Phase net, DistOpfResult result) {
		double maxDiff = 0.0;
		for (DStab3PBus bus : net.getBusList()) {
			Complex3x1 vabc = bus.get3PhaseVotlages();
			maxDiff = Math.max(maxDiff, phaseDiff(result, bus.getId(), "A", vabc == null ? null : vabc.a_0));
			maxDiff = Math.max(maxDiff, phaseDiff(result, bus.getId(), "B", vabc == null ? null : vabc.b_1));
			maxDiff = Math.max(maxDiff, phaseDiff(result, bus.getId(), "C", vabc == null ? null : vabc.c_2));
		}
		return maxDiff;
	}

	private static double phaseDiff(DistOpfResult result, String busId, String phase, Complex voltage) {
		Double v2 = result.getBusVoltageSquared(busId, phase);
		if (v2 == null || voltage == null || Double.isNaN(voltage.abs())) {
			return 0.0;
		}
		return Math.abs(Math.sqrt(Math.max(v2.doubleValue(), 0.0)) - voltage.abs());
	}

	private static double maxVoltageViolation(DStabNetwork3Phase net, DistOpfOptions options) {
		double maxViolation = 0.0;
		for (DStab3PBus bus : net.getBusList()) {
			Complex3x1 vabc = bus.get3PhaseVotlages();
			maxViolation = Math.max(maxViolation, voltageViolation(vabc == null ? null : vabc.a_0, options));
			maxViolation = Math.max(maxViolation, voltageViolation(vabc == null ? null : vabc.b_1, options));
			maxViolation = Math.max(maxViolation, voltageViolation(vabc == null ? null : vabc.c_2, options));
		}
		return maxViolation;
	}

	private static double voltageViolation(Complex voltage, DistOpfOptions options) {
		if (voltage == null || Double.isNaN(voltage.abs())) {
			return 0.0;
		}
		double magnitude = voltage.abs();
		if (magnitude < options.getMinVoltagePu()) {
			return options.getMinVoltagePu() - magnitude;
		}
		if (magnitude > options.getMaxVoltagePu()) {
			return magnitude - options.getMaxVoltagePu();
		}
		return 0.0;
	}

	private static double maxBranchLimitViolation(DStabNetwork3Phase net) {
		double maxViolation = 0.0;
		for (DStab3PBranch branch : net.getBranchList()) {
			if (!branch.isActive() || branch.getRatingMva1() <= 0.0) {
				continue;
			}
			double limit = branch.getRatingMva1() / net.getBaseMva();
			Complex3x1 current = branch.calc3PhaseCurrentFrom2To();
			Complex3x1 voltage = ((DStab3PBus) branch.getFromBus()).get3PhaseVotlages();
			maxViolation = Math.max(maxViolation, branchPhaseViolation(voltage.a_0, current.a_0, limit));
			maxViolation = Math.max(maxViolation, branchPhaseViolation(voltage.b_1, current.b_1, limit));
			maxViolation = Math.max(maxViolation, branchPhaseViolation(voltage.c_2, current.c_2, limit));
		}
		return maxViolation;
	}

	private static double branchPhaseViolation(Complex voltage, Complex current, double limit) {
		if (voltage == null || current == null) {
			return 0.0;
		}
		double apparentPower = voltage.multiply(current.conjugate()).abs();
		return apparentPower > limit ? apparentPower - limit : 0.0;
	}
}
