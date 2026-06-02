package org.interpss.threePhase.opf.dist.model;

import java.util.LinkedHashMap;
import java.util.Map;

import org.apache.commons.math3.complex.Complex;
import org.interpss.numeric.datatype.Complex3x3;
import org.interpss.threePhase.opf.dist.DistOpfResult;

import com.interpss.core.acsc.PhaseCode;

public class DistBranchFlowLossProfile {

	private final Map<String, Double> currentSquaredByBranchPhase = new LinkedHashMap<String, Double>();

	public static DistBranchFlowLossProfile none() {
		return new DistBranchFlowLossProfile();
	}

	public static DistBranchFlowLossProfile fromResult(DistOpfModelData modelData, DistOpfResult result) {
		DistBranchFlowLossProfile profile = new DistBranchFlowLossProfile();
		for (DistOpfBranchData branch : modelData.getBranches()) {
			for (PhaseCode phase : branch.getPhases()) {
				Double fromV2 = result.getBusVoltageSquared(branch.getFromBusId(), phase.name());
				Double p = result.getBranchActivePower(branch.getId(), phase.name());
				Double q = result.getBranchReactivePower(branch.getId(), phase.name());
				if (fromV2 == null || p == null || q == null) {
					continue;
				}
				double ratio = branch.getVoltageRatio(phase);
				double sendingVoltage = ratio * ratio * fromV2.doubleValue();
				if (Math.abs(sendingVoltage) < 1.0e-12) {
					continue;
				}
				profile.putCurrentSquared(branch.getId(), phase,
						(p.doubleValue() * p.doubleValue() + q.doubleValue() * q.doubleValue()) / sendingVoltage);
			}
		}
		return profile;
	}

	public DistBranchFlowLossProfile putCurrentSquared(String branchId, PhaseCode phase, double value) {
		currentSquaredByBranchPhase.put(key(branchId, phase), Double.valueOf(value));
		return this;
	}

	public double activePowerLoss(DistOpfBranchData branch, PhaseCode phase) {
		return selfImpedance(branch.getZabc(), phase).getReal() * currentSquared(branch, phase);
	}

	public double reactivePowerLoss(DistOpfBranchData branch, PhaseCode phase) {
		return selfImpedance(branch.getZabc(), phase).getImaginary() * currentSquared(branch, phase);
	}

	public double voltageDropLoss(DistOpfBranchData branch, PhaseCode phase) {
		Complex z = selfImpedance(branch.getZabc(), phase);
		return (z.getReal() * z.getReal() + z.getImaginary() * z.getImaginary())
				* currentSquared(branch, phase);
	}

	private double currentSquared(DistOpfBranchData branch, PhaseCode phase) {
		Double value = currentSquaredByBranchPhase.get(key(branch.getId(), phase));
		return value == null ? 0.0 : value.doubleValue();
	}

	private static String key(String branchId, PhaseCode phase) {
		return branchId + ":" + phase.name();
	}

	private static Complex selfImpedance(Complex3x3 zabc, PhaseCode phase) {
		switch (phase) {
		case A:
			return zabc.aa;
		case B:
			return zabc.bb;
		case C:
			return zabc.cc;
		default:
			return Complex.ZERO;
		}
	}
}
