package org.interpss.threePhase.powerflow.control;

import org.apache.commons.math3.complex.Complex;
import org.interpss.numeric.datatype.Complex3x1;
import org.interpss.numeric.datatype.Unit.UnitType;

import com.interpss.core.acsc.PhaseCode;
import com.interpss.core.threephase.IPhaseGen;

public class InverterControlModel {
	public InverterControlResult applyReactivePowerSetpoint(IPhaseGen generator, double baseKva,
			InverterControlData control, double targetReactivePowerKvar) {
		if(generator == null || control == null || !control.isEnabled()) {
			return InverterControlResult.notApplied();
		}
		Complex3x1 power = generator.getPower3Phase(UnitType.PU);
		Complex totalPower = total(power);
		double totalPKw = totalPower.getReal() * baseKva;
		double limitedQKvar = limitReactivePower(totalPKw, targetReactivePowerKvar, control);
		double totalQPu = baseKva == 0.0 ? 0.0 : limitedQKvar / baseKva;
		Complex3x1 updated = replaceReactivePower(power, generator.getPhaseCode(), totalQPu);
		generator.setPower3Phase(updated, UnitType.PU);
		return new InverterControlResult(true, limitedQKvar,
				Math.abs(limitedQKvar - targetReactivePowerKvar) > 1.0e-9);
	}

	private double limitReactivePower(double totalPKw, double targetReactivePowerKvar,
			InverterControlData control) {
		double qMin = Double.isFinite(control.getMinReactivePowerKvar())
				? control.getMinReactivePowerKvar() : -Double.MAX_VALUE;
		double qMax = Double.isFinite(control.getMaxReactivePowerKvar())
				? control.getMaxReactivePowerKvar() : Double.MAX_VALUE;
		if(control.getRatedKva() > 0.0) {
			double capability = Math.sqrt(Math.max(0.0,
					control.getRatedKva() * control.getRatedKva() - totalPKw * totalPKw));
			qMin = Math.max(qMin, -capability);
			qMax = Math.min(qMax, capability);
		}
		return Math.max(qMin, Math.min(qMax, targetReactivePowerKvar));
	}

	private Complex3x1 replaceReactivePower(Complex3x1 power, PhaseCode phaseCode, double totalQPu) {
		Complex3x1 value = power == null ? new Complex3x1() : power;
		int activePhaseCount = activePhaseCount(value, phaseCode);
		double phaseQ = activePhaseCount == 0 ? 0.0 : totalQPu / activePhaseCount;
		return new Complex3x1(replaceQ(value.a_0, active(value.a_0) || phaseActive(phaseCode, PhaseCode.A), phaseQ),
				replaceQ(value.b_1, active(value.b_1) || phaseActive(phaseCode, PhaseCode.B), phaseQ),
				replaceQ(value.c_2, active(value.c_2) || phaseActive(phaseCode, PhaseCode.C), phaseQ));
	}

	private Complex replaceQ(Complex value, boolean active, double phaseQ) {
		Complex existing = value == null ? Complex.ZERO : value;
		return active ? new Complex(existing.getReal(), phaseQ) : existing;
	}

	private int activePhaseCount(Complex3x1 power, PhaseCode phaseCode) {
		int count = 0;
		if(active(power.a_0) || phaseActive(phaseCode, PhaseCode.A)) {
			count++;
		}
		if(active(power.b_1) || phaseActive(phaseCode, PhaseCode.B)) {
			count++;
		}
		if(active(power.c_2) || phaseActive(phaseCode, PhaseCode.C)) {
			count++;
		}
		return count;
	}

	private boolean phaseActive(PhaseCode phaseCode, PhaseCode phase) {
		if(phaseCode == null || phaseCode == PhaseCode.ABC) {
			return true;
		}
		if(phase == PhaseCode.A) {
			return phaseCode == PhaseCode.A || phaseCode == PhaseCode.AB || phaseCode == PhaseCode.AC;
		}
		if(phase == PhaseCode.B) {
			return phaseCode == PhaseCode.B || phaseCode == PhaseCode.AB || phaseCode == PhaseCode.BC;
		}
		return phaseCode == PhaseCode.C || phaseCode == PhaseCode.AC || phaseCode == PhaseCode.BC;
	}

	private boolean active(Complex value) {
		return value != null && value.abs() > 1.0e-12;
	}

	private Complex total(Complex3x1 power) {
		if(power == null) {
			return Complex.ZERO;
		}
		return add(add(power.a_0, power.b_1), power.c_2);
	}

	private Complex add(Complex left, Complex right) {
		Complex a = left == null ? Complex.ZERO : left;
		Complex b = right == null ? Complex.ZERO : right;
		return a.add(b);
	}

	public static class InverterControlResult {
		private final boolean applied;
		private final double reactivePowerKvar;
		private final boolean limited;

		private InverterControlResult(boolean applied, double reactivePowerKvar, boolean limited) {
			this.applied = applied;
			this.reactivePowerKvar = reactivePowerKvar;
			this.limited = limited;
		}

		private static InverterControlResult notApplied() {
			return new InverterControlResult(false, 0.0, false);
		}

		public boolean isApplied() {
			return applied;
		}

		public double getReactivePowerKvar() {
			return reactivePowerKvar;
		}

		public boolean isLimited() {
			return limited;
		}
	}
}
