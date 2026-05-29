package org.interpss.threePhase.opf.dist.validation;

import java.util.Map;

import org.apache.commons.math3.complex.Complex;
import org.interpss.numeric.datatype.Complex3x1;
import org.interpss.threePhase.basic.dstab.DStab3PBranch;
import org.interpss.threePhase.basic.dstab.DStab3PBus;
import org.interpss.threePhase.dynamic.DStabNetwork3Phase;
import org.interpss.threePhase.opf.dist.DistOpfOptions;
import org.interpss.threePhase.opf.dist.DistOpfResult;
import org.interpss.threePhase.opf.dist.model.DistOpfModelData;
import org.interpss.threePhase.opf.dist.model.DistOpfModelDataExtractor;
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
		capturePowerFlowVoltages(net, result);
		capturePowerFlowBranchPowers(net, result);
		result.setMaxPowerFlowVoltageDiff(maxVoltageDiff(result));
		result.setMaxPowerFlowBranchActivePowerDiff(maxBranchPowerDiff(
				result.getBranchActivePower(), result.getPowerFlowBranchActivePower()));
		result.setMaxPowerFlowBranchReactivePowerDiff(maxBranchPowerDiff(
				result.getBranchReactivePower(), result.getPowerFlowBranchReactivePower()));
		result.setMaxPowerFlowVoltageViolation(maxVoltageViolation(net, options));
		result.setMaxPowerFlowBranchLimitViolation(maxBranchLimitViolation(net));
		result.addDiagnostic("AC power-flow validation: converged=" + converged
				+ ", iterations=" + powerFlow.getIterationCount()
				+ ", max voltage diff=" + result.getMaxPowerFlowVoltageDiff()
				+ ", max branch P diff=" + result.getMaxPowerFlowBranchActivePowerDiff()
				+ ", max branch Q diff=" + result.getMaxPowerFlowBranchReactivePowerDiff());
		DistBranchFlowEquationValidation branchFlowValidation = new DistBranchFlowEquationValidation();
		DistOpfModelData modelData = new DistOpfModelDataExtractor().extract(net);
		result.setMaxBranchFlowVoltageDropResidual(
				branchFlowValidation.maxVoltageDropResidual(modelData, result));
		result.setMaxBranchFlowActivePowerBalanceResidual(
				branchFlowValidation.maxActivePowerBalanceResidual(modelData, result));
		result.setMaxBranchFlowReactivePowerBalanceResidual(
				branchFlowValidation.maxReactivePowerBalanceResidual(modelData, result));
		return result;
	}

	private static void capturePowerFlowVoltages(DStabNetwork3Phase net, DistOpfResult result) {
		for (DStab3PBus bus : net.getBusList()) {
			Complex3x1 vabc = bus.get3PhaseVotlages();
			captureVoltage(result, bus.getId(), "A", vabc == null ? null : vabc.a_0);
			captureVoltage(result, bus.getId(), "B", vabc == null ? null : vabc.b_1);
			captureVoltage(result, bus.getId(), "C", vabc == null ? null : vabc.c_2);
		}
	}

	private static void captureVoltage(DistOpfResult result, String busId, String phase, Complex voltage) {
		if (voltage != null && !Double.isNaN(voltage.abs())) {
			result.putPowerFlowBusVoltageMagnitude(busId, phase, voltage.abs());
		}
	}

	private static void capturePowerFlowBranchPowers(DStabNetwork3Phase net, DistOpfResult result) {
		for (DStab3PBranch branch : net.getBranchList()) {
			if (!branch.isActive()) {
				continue;
			}
			Complex3x1 current = branch.calc3PhaseCurrentFrom2To();
			Complex3x1 voltage = ((DStab3PBus) branch.getFromBus()).get3PhaseVotlages();
			captureBranchPower(result, branch.getId(), "A", voltage == null ? null : voltage.a_0,
					current == null ? null : current.a_0);
			captureBranchPower(result, branch.getId(), "B", voltage == null ? null : voltage.b_1,
					current == null ? null : current.b_1);
			captureBranchPower(result, branch.getId(), "C", voltage == null ? null : voltage.c_2,
					current == null ? null : current.c_2);
		}
	}

	private static void captureBranchPower(DistOpfResult result, String branchId, String phase,
			Complex voltage, Complex current) {
		if (voltage == null || current == null) {
			return;
		}
		Complex apparentPower = voltage.multiply(current.conjugate());
		result.putPowerFlowBranchActivePower(branchId, phase, apparentPower.getReal());
		result.putPowerFlowBranchReactivePower(branchId, phase, apparentPower.getImaginary());
	}

	private static double maxVoltageDiff(DistOpfResult result) {
		double maxDiff = 0.0;
		for (String key : result.getBusVoltageSquared().keySet()) {
			Double v2 = result.getBusVoltageSquared().get(key);
			Double voltage = result.getPowerFlowBusVoltageMagnitude().get(key);
			if (v2 != null && voltage != null) {
				maxDiff = Math.max(maxDiff,
						Math.abs(Math.sqrt(Math.max(v2.doubleValue(), 0.0)) - voltage.doubleValue()));
			}
		}
		return maxDiff;
	}

	private static double maxBranchPowerDiff(Map<String, Double> opf, Map<String, Double> powerFlow) {
		double maxDiff = 0.0;
		for (String key : opf.keySet()) {
			Double opfValue = opf.get(key);
			Double powerFlowValue = powerFlow.get(key);
			if (opfValue != null && powerFlowValue != null) {
				maxDiff = Math.max(maxDiff, Math.abs(opfValue.doubleValue() - powerFlowValue.doubleValue()));
			}
		}
		return maxDiff;
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
