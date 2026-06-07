package org.interpss.threePhase.powerflow.control;

import org.apache.commons.math3.complex.Complex;
import org.interpss.numeric.datatype.Complex3x1;
import org.interpss.numeric.datatype.Unit.UnitType;

import com.interpss.core.acsc.PhaseCode;
public class InverterControlModel {
	public InverterControlResult applyVoltVarSetpoint(com.interpss.core.threephase.IPhaseGen generator, double baseKva,
			InverterControlData control, double targetReactivePowerKvar) {
		return applyReactivePowerSetpoint(generator, baseKva, control, targetReactivePowerKvar);
	}

	public InverterControlResult applyWattVarSetpoint(com.interpss.core.threephase.IPhaseGen generator, double baseKva,
			InverterControlData control, double targetReactivePowerKvar) {
		return applyReactivePowerSetpoint(generator, baseKva, control, targetReactivePowerKvar);
	}

	public InverterControlResult applyReactivePowerSetpoint(com.interpss.core.threephase.IPhaseGen generator, double baseKva,
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

	public InverterControlResult applyActivePowerSetpoint(com.interpss.core.threephase.IPhaseGen generator, double baseKva,
			InverterControlData control, double targetActivePowerKw) {
		if(generator == null || control == null || !control.isEnabled()) {
			return InverterControlResult.notApplied();
		}
		Complex3x1 power = generator.getPower3Phase(UnitType.PU);
		Complex totalPower = total(power);
		double totalQKvar = totalPower.getImaginary() * baseKva;
		double limitedPKw = limitActivePower(totalQKvar, targetActivePowerKw, control);
		double totalPPu = baseKva == 0.0 ? 0.0 : limitedPKw / baseKva;
		Complex3x1 updated = replaceActivePower(power, generator.getPhaseCode(), totalPPu);
		generator.setPower3Phase(updated, UnitType.PU);
		return new InverterControlResult(true, limitedPKw, totalQKvar,
				Math.abs(limitedPKw - targetActivePowerKw) > 1.0e-9);
	}

	public InverterControlResult applyPowerFactorSetpoint(com.interpss.core.threephase.IPhaseGen generator, double baseKva,
			InverterControlData control, double targetPowerFactor) {
		if(generator == null || control == null || !control.isEnabled()) {
			return InverterControlResult.notApplied();
		}
		double pf = limitedPowerFactor(targetPowerFactor, control);
		Complex3x1 power = generator.getPower3Phase(UnitType.PU);
		double totalPKw = total(power).getReal() * baseKva;
		double targetQKvar = reactivePowerForPowerFactor(totalPKw, pf);
		return applyReactivePowerSetpoint(generator, baseKva, control, targetQKvar);
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

	private double limitActivePower(double totalQKvar, double targetActivePowerKw,
			InverterControlData control) {
		if(control.getRatedKva() <= 0.0) {
			return targetActivePowerKw;
		}
		double capability = Math.sqrt(Math.max(0.0,
				control.getRatedKva() * control.getRatedKva() - totalQKvar * totalQKvar));
		return Math.max(-capability, Math.min(capability, targetActivePowerKw));
	}

	private double limitedPowerFactor(double targetPowerFactor, InverterControlData control) {
		double magnitude = Math.min(1.0, Math.abs(targetPowerFactor));
		if(control.getMinPowerFactor() > 0.0) {
			magnitude = Math.max(magnitude, Math.min(1.0, control.getMinPowerFactor()));
		}
		if(magnitude <= 0.0) {
			magnitude = 1.0;
		}
		return targetPowerFactor < 0.0 ? -magnitude : magnitude;
	}

	private double reactivePowerForPowerFactor(double totalPKw, double powerFactor) {
		double pf = Math.min(1.0, Math.max(-1.0, powerFactor));
		if(Math.abs(pf) >= 1.0 || Math.abs(totalPKw) <= 1.0e-12) {
			return 0.0;
		}
		double qMagnitude = Math.abs(totalPKw) * Math.tan(Math.acos(Math.abs(pf)));
		double qSign = pf < 0.0 ? -1.0 : 1.0;
		return qSign * qMagnitude;
	}

	private Complex3x1 replaceActivePower(Complex3x1 power, PhaseCode phaseCode, double totalPPu) {
		Complex3x1 value = power == null ? new Complex3x1() : power;
		int activePhaseCount = activePhaseCount(value, phaseCode);
		double phaseP = activePhaseCount == 0 ? 0.0 : totalPPu / activePhaseCount;
		return new Complex3x1(replaceP(value.a_0, active(value.a_0) || phaseActive(phaseCode, PhaseCode.A), phaseP),
				replaceP(value.b_1, active(value.b_1) || phaseActive(phaseCode, PhaseCode.B), phaseP),
				replaceP(value.c_2, active(value.c_2) || phaseActive(phaseCode, PhaseCode.C), phaseP));
	}

	private Complex replaceP(Complex value, boolean active, double phaseP) {
		Complex existing = value == null ? Complex.ZERO : value;
		return active ? new Complex(phaseP, existing.getImaginary()) : existing;
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
		private final double activePowerKw;
		private final double reactivePowerKvar;
		private final boolean limited;

		private InverterControlResult(boolean applied, double reactivePowerKvar, boolean limited) {
			this(applied, 0.0, reactivePowerKvar, limited);
		}

		private InverterControlResult(boolean applied, double activePowerKw, double reactivePowerKvar,
				boolean limited) {
			this.applied = applied;
			this.activePowerKw = activePowerKw;
			this.reactivePowerKvar = reactivePowerKvar;
			this.limited = limited;
		}

		public static InverterControlResult notApplied() {
			return new InverterControlResult(false, 0.0, false);
		}

		public boolean isApplied() {
			return applied;
		}

		public double getActivePowerKw() {
			return activePowerKw;
		}

		public double getReactivePowerKvar() {
			return reactivePowerKvar;
		}

		public boolean isLimited() {
			return limited;
		}
	}
}
