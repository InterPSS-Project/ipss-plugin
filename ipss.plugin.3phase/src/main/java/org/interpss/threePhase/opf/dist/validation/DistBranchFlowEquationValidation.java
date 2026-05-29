package org.interpss.threePhase.opf.dist.validation;

import org.apache.commons.math3.complex.Complex;
import org.interpss.numeric.datatype.Complex3x3;
import org.interpss.threePhase.opf.dist.DistOpfResult;
import org.interpss.threePhase.opf.dist.model.DistOpfBranchData;
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
