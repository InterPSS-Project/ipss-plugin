package org.interpss.threePhase.qsts;

import org.apache.commons.math3.complex.Complex;
import org.interpss.numeric.datatype.Complex3x1;
import org.interpss.numeric.datatype.Unit.UnitType;

import com.interpss.core.acsc.PhaseCode;
import com.interpss.core.threephase.AclfGen3Phase;

public class QstsStorageBaseState {
	private final AclfGen3Phase generator;
	private final double baseKva;
	private final double baseKw;
	private final double baseKvar;
	private final double kwRated;
	private final double kwhRated;
	private final double reserveKwh;
	private final double chargeEfficiency;
	private final double dischargeEfficiency;
	private double storedKwh;

	public QstsStorageBaseState(AclfGen3Phase generator, double baseKva, double kwRated,
			double kwhRated, double storedKwh, double reserveKwh,
			double chargeEfficiency, double dischargeEfficiency) {
		if(generator == null) {
			throw new IllegalArgumentException("QSTS storage state requires a phase generator");
		}
		this.generator = generator;
		this.baseKva = baseKva;
		this.kwRated = Math.max(0.0, kwRated);
		this.kwhRated = Math.max(0.0, kwhRated);
		this.reserveKwh = Math.max(0.0, reserveKwh);
		this.chargeEfficiency = normalizeEfficiency(chargeEfficiency);
		this.dischargeEfficiency = normalizeEfficiency(dischargeEfficiency);
		this.storedKwh = Math.max(this.reserveKwh, Math.min(this.kwhRated, storedKwh));
		Complex total = total(generator.getPower3Phase(UnitType.PU));
		this.baseKw = total.getReal() * baseKva;
		this.baseKvar = total.getImaginary() * baseKva;
	}

	public double applyScheduledPower(double requestedKw, double stepHours) {
		return applyScheduledPower(requestedKw, currentKvar(), stepHours);
	}

	public boolean applyScheduledMultiplier(double pMultiplier, double qMultiplier, double stepHours) {
		Complex beforePower = total(generator.getPower3Phase(UnitType.PU));
		double beforeStoredKwh = this.storedKwh;
		applyScheduledPower(baseKw * pMultiplier, baseKvar * qMultiplier, stepHours);
		Complex afterPower = total(generator.getPower3Phase(UnitType.PU));
		return Math.abs(afterPower.getReal() - beforePower.getReal()) > 1.0e-12
				|| Math.abs(afterPower.getImaginary() - beforePower.getImaginary()) > 1.0e-12
				|| Math.abs(this.storedKwh - beforeStoredKwh) > 1.0e-9;
	}

	public double applyScheduledPower(double requestedKw, double requestedKvar, double stepHours) {
		double hours = stepHours > 0.0 ? stepHours : 1.0;
		double limitedKw = Math.max(-kwRated, Math.min(kwRated, requestedKw));
		if(limitedKw > 0.0) {
			double availableKwh = Math.max(0.0, storedKwh - reserveKwh);
			limitedKw = Math.min(limitedKw, availableKwh * dischargeEfficiency / hours);
			storedKwh -= limitedKw * hours / dischargeEfficiency;
		}
		else if(limitedKw < 0.0) {
			double headroomKwh = Math.max(0.0, kwhRated - storedKwh);
			double chargeKwLimit = headroomKwh / (chargeEfficiency * hours);
			limitedKw = -Math.min(Math.abs(limitedKw), chargeKwLimit);
			storedKwh += Math.abs(limitedKw) * hours * chargeEfficiency;
		}
		setGeneratorPower(limitedKw, requestedKvar);
		return limitedKw;
	}

	public AclfGen3Phase getGenerator() {
		return generator;
	}

	public String getStorageId() {
		return generator.getId();
	}

	public double getBaseKw() {
		return baseKw;
	}

	public double getStoredKwh() {
		return storedKwh;
	}

	public double getKwhRated() {
		return kwhRated;
	}

	private double currentKvar() {
		Complex3x1 power = generator.getPower3Phase(UnitType.PU);
		return total(power).getImaginary() * baseKva;
	}

	private void setGeneratorPower(double kw, double kvar) {
		Complex3x1 power = generator.getPower3Phase(UnitType.PU);
		double qPu = baseKva == 0.0 ? 0.0 : kvar / baseKva;
		double pPu = baseKva == 0.0 ? 0.0 : kw / baseKva;
		int phaseCount = activePhaseCount(generator.getPhaseCode(), power);
		double phaseP = phaseCount == 0 ? 0.0 : pPu / phaseCount;
		double phaseQ = phaseCount == 0 ? 0.0 : qPu / phaseCount;
		PhaseCode phaseCode = generator.getPhaseCode();
		generator.setPower3Phase(new Complex3x1(
				phaseActive(phaseCode, PhaseCode.A, power == null ? null : power.a_0)
						? new Complex(phaseP, phaseQ) : Complex.ZERO,
				phaseActive(phaseCode, PhaseCode.B, power == null ? null : power.b_1)
						? new Complex(phaseP, phaseQ) : Complex.ZERO,
				phaseActive(phaseCode, PhaseCode.C, power == null ? null : power.c_2)
						? new Complex(phaseP, phaseQ) : Complex.ZERO), UnitType.PU);
	}

	private static double normalizeEfficiency(double efficiency) {
		if(efficiency > 1.0) {
			return efficiency / 100.0;
		}
		return efficiency > 0.0 ? efficiency : 1.0;
	}

	private static Complex total(Complex3x1 power) {
		if(power == null) {
			return Complex.ZERO;
		}
		return add(add(power.a_0, power.b_1), power.c_2);
	}

	private static Complex add(Complex left, Complex right) {
		Complex a = left == null ? Complex.ZERO : left;
		Complex b = right == null ? Complex.ZERO : right;
		return a.add(b);
	}

	private static int activePhaseCount(PhaseCode phaseCode, Complex3x1 power) {
		int count = 0;
		if(phaseActive(phaseCode, PhaseCode.A, power == null ? null : power.a_0)) {
			count++;
		}
		if(phaseActive(phaseCode, PhaseCode.B, power == null ? null : power.b_1)) {
			count++;
		}
		if(phaseActive(phaseCode, PhaseCode.C, power == null ? null : power.c_2)) {
			count++;
		}
		return count;
	}

	private static boolean phaseActive(PhaseCode phaseCode, PhaseCode phase, Complex power) {
		if(power != null && power.abs() > 1.0e-12) {
			return true;
		}
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
}
