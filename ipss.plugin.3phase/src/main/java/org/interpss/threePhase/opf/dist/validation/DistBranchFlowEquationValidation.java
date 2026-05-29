package org.interpss.threePhase.opf.dist.validation;

import org.apache.commons.math3.complex.Complex;
import org.interpss.numeric.datatype.Complex3x1;
import org.interpss.numeric.datatype.Complex3x3;
import org.interpss.threePhase.opf.dist.DistOpfResult;
import org.interpss.threePhase.opf.dist.model.DistOpfBranchData;
import org.interpss.threePhase.opf.dist.model.DistOpfBusData;
import org.interpss.threePhase.opf.dist.model.DistOpfDerData;
import org.interpss.threePhase.opf.dist.model.DistOpfModelData;

import com.interpss.core.acsc.PhaseCode;

public class DistBranchFlowEquationValidation {

	public double maxVoltageDropResidual(DistOpfModelData modelData, DistOpfResult result) {
		double maxResidual = 0.0;
		for (DistOpfBranchData branch : modelData.getBranches()) {
			for (PhaseCode phase : branch.getPhases()) {
				maxResidual = Math.max(maxResidual, Math.abs(voltageDropResidual(branch, phase, result)));
			}
		}
		return maxResidual;
	}

	public double maxActivePowerBalanceResidual(DistOpfModelData modelData, DistOpfResult result) {
		double maxResidual = 0.0;
		for (DistOpfBusData bus : modelData.getBuses()) {
			if (bus.isSwing()) {
				continue;
			}
			DistOpfBranchData parent = modelData.getParentBranch(bus.getId());
			if (parent == null) {
				continue;
			}
			for (PhaseCode phase : bus.getPhases()) {
				if (parent.getPhases().contains(phase)) {
					maxResidual = Math.max(maxResidual,
							Math.abs(activePowerBalanceResidual(modelData, bus, parent, phase, result)));
				}
			}
		}
		return maxResidual;
	}

	public double maxReactivePowerBalanceResidual(DistOpfModelData modelData, DistOpfResult result) {
		double maxResidual = 0.0;
		for (DistOpfBusData bus : modelData.getBuses()) {
			if (bus.isSwing()) {
				continue;
			}
			DistOpfBranchData parent = modelData.getParentBranch(bus.getId());
			if (parent == null) {
				continue;
			}
			for (PhaseCode phase : bus.getPhases()) {
				if (parent.getPhases().contains(phase)) {
					maxResidual = Math.max(maxResidual,
							Math.abs(reactivePowerBalanceResidual(modelData, bus, parent, phase, result)));
				}
			}
		}
		return maxResidual;
	}

	public double voltageDropResidual(DistOpfBranchData branch, PhaseCode phase, DistOpfResult result) {
		Double fromV2 = result.getBusVoltageSquared(branch.getFromBusId(), phase.name());
		Double toV2 = result.getBusVoltageSquared(branch.getToBusId(), phase.name());
		Double p = result.getBranchActivePower(branch.getId(), phase.name());
		Double q = result.getBranchReactivePower(branch.getId(), phase.name());
		if (fromV2 == null || toV2 == null || p == null || q == null) {
			return 0.0;
		}
		Complex z = selfImpedance(branch.getZabc(), phase);
		double ratio = branch.getVoltageRatio(phase);
		double sendingVoltage = ratio * ratio * fromV2.doubleValue();
		if (Math.abs(sendingVoltage) < 1.0e-12) {
			return 0.0;
		}
		double linearDrop = 2.0 * (z.getReal() * p.doubleValue() + z.getImaginary() * q.doubleValue());
		double lossCorrection = (z.getReal() * z.getReal() + z.getImaginary() * z.getImaginary())
				* (p.doubleValue() * p.doubleValue() + q.doubleValue() * q.doubleValue()) / sendingVoltage;
		return sendingVoltage - toV2.doubleValue() - linearDrop + lossCorrection;
	}

	public double activePowerBalanceResidual(DistOpfModelData modelData, DistOpfBusData bus,
			DistOpfBranchData parent, PhaseCode phase, DistOpfResult result) {
		Double parentP = result.getBranchActivePower(parent.getId(), phase.name());
		if (parentP == null) {
			return 0.0;
		}
		double residual = parentP.doubleValue();
		for (DistOpfBranchData child : modelData.getChildren(bus.getId())) {
			Double childP = result.getBranchActivePower(child.getId(), phase.name());
			if (childP != null) {
				residual -= childP.doubleValue();
			}
		}
		for (DistOpfDerData der : modelData.getDers(bus.getId())) {
			Double derP = result.getDerActivePower(der.getId(), phase.name());
			if (derP != null) {
				residual += derP.doubleValue();
			}
		}
		residual -= p(bus.getLoad(), phase);
		residual -= selfImpedance(parent.getZabc(), phase).getReal() * currentSquared(parent, phase, result);
		return residual;
	}

	public double reactivePowerBalanceResidual(DistOpfModelData modelData, DistOpfBusData bus,
			DistOpfBranchData parent, PhaseCode phase, DistOpfResult result) {
		Double parentQ = result.getBranchReactivePower(parent.getId(), phase.name());
		if (parentQ == null) {
			return 0.0;
		}
		double residual = parentQ.doubleValue();
		for (DistOpfBranchData child : modelData.getChildren(bus.getId())) {
			Double childQ = result.getBranchReactivePower(child.getId(), phase.name());
			if (childQ != null) {
				residual -= childQ.doubleValue();
			}
		}
		for (DistOpfDerData der : modelData.getDers(bus.getId())) {
			Double derQ = result.getDerReactivePower(der.getId(), phase.name());
			if (derQ != null) {
				residual += derQ.doubleValue();
			}
		}
		residual -= q(bus.getLoad(), phase) - q(bus.getFixedCapacitorQ(), phase);
		residual -= selfImpedance(parent.getZabc(), phase).getImaginary() * currentSquared(parent, phase, result);
		return residual;
	}

	private static double currentSquared(DistOpfBranchData branch, PhaseCode phase, DistOpfResult result) {
		Double fromV2 = result.getBusVoltageSquared(branch.getFromBusId(), phase.name());
		Double p = result.getBranchActivePower(branch.getId(), phase.name());
		Double q = result.getBranchReactivePower(branch.getId(), phase.name());
		if (fromV2 == null || p == null || q == null || Math.abs(fromV2.doubleValue()) < 1.0e-12) {
			return 0.0;
		}
		double ratio = branch.getVoltageRatio(phase);
		double sendingVoltage = ratio * ratio * fromV2.doubleValue();
		if (Math.abs(sendingVoltage) < 1.0e-12) {
			return 0.0;
		}
		return (p.doubleValue() * p.doubleValue() + q.doubleValue() * q.doubleValue()) / sendingVoltage;
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

	private static double p(Complex3x1 value, PhaseCode phase) {
		Complex complex = phaseValue(value, phase);
		return complex == null ? 0.0 : complex.getReal();
	}

	private static double q(Complex3x1 value, PhaseCode phase) {
		Complex complex = phaseValue(value, phase);
		return complex == null ? 0.0 : complex.getImaginary();
	}

	private static Complex phaseValue(Complex3x1 value, PhaseCode phase) {
		if (value == null) {
			return null;
		}
		switch (phase) {
		case A:
			return value.a_0;
		case B:
			return value.b_1;
		case C:
			return value.c_2;
		default:
			return Complex.ZERO;
		}
	}
}
