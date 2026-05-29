package org.interpss.threePhase.opf.dist.model;

import java.util.Set;

import org.apache.commons.math3.complex.Complex;
import org.interpss.numeric.datatype.Complex3x1;

import com.interpss.core.acsc.PhaseCode;

public class DistOpfBatteryData extends DistOpfDerData {

	private final double chargeLimitPu;
	private final double dischargeLimitPu;

	public DistOpfBatteryData(String id, String busId, Set<PhaseCode> phases,
			double chargeLimitPu, double dischargeLimitPu, Double apparentPowerLimitPu) {
		this(id, busId, phases, new Complex3x1(), chargeLimitPu, dischargeLimitPu,
				apparentPowerLimitPu);
	}

	public DistOpfBatteryData(String id, String busId, Set<PhaseCode> phases,
			Complex3x1 power, double chargeLimitPu, double dischargeLimitPu,
			Double apparentPowerLimitPu) {
		super(id, busId, phases, power, apparentPowerLimitPu);
		this.chargeLimitPu = Math.abs(chargeLimitPu);
		this.dischargeLimitPu = Math.abs(dischargeLimitPu);
	}

	@Override
	public double getPMin(PhaseCode phase) {
		return -chargeLimitPu;
	}

	@Override
	public double getPMax(PhaseCode phase) {
		return dischargeLimitPu;
	}

	@Override
	public double getQAbsLimit(PhaseCode phase) {
		Double apparentPowerLimitPu = getApparentPowerLimitPu();
		if (apparentPowerLimitPu != null) {
			return apparentPowerLimitPu.doubleValue();
		}
		Complex power = phasePower(phase);
		return Math.max(Math.max(chargeLimitPu, dischargeLimitPu),
				Math.max(Math.abs(power.getReal()), Math.abs(power.getImaginary())));
	}

	public double getChargeLimitPu() {
		return chargeLimitPu;
	}

	public double getDischargeLimitPu() {
		return dischargeLimitPu;
	}

	private Complex phasePower(PhaseCode phase) {
		switch (phase) {
		case A:
			return getPower().a_0 == null ? Complex.ZERO : getPower().a_0;
		case B:
			return getPower().b_1 == null ? Complex.ZERO : getPower().b_1;
		case C:
			return getPower().c_2 == null ? Complex.ZERO : getPower().c_2;
		default:
			return Complex.ZERO;
		}
	}
}
