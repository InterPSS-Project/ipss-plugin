package org.interpss.threePhase.opf.dist.validation;

import org.apache.commons.math3.complex.Complex;
import org.interpss.numeric.datatype.Complex3x1;
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
		result.setMaxPowerFlowVoltageDiff(maxVoltageDiff(net, result));
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
}
