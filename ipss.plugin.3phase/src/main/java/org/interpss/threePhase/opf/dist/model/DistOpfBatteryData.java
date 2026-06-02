package org.interpss.threePhase.opf.dist.model;

import java.util.Set;

import org.apache.commons.math3.complex.Complex;
import org.interpss.numeric.datatype.Complex3x1;

import com.interpss.core.acsc.PhaseCode;

public class DistOpfBatteryData extends DistOpfDerData {

	private final double chargeLimitPu;
	private final double dischargeLimitPu;
	private final double energyCapacityPuHour;
	private final double initialStateOfChargePu;
	private final double minStateOfChargePu;
	private final double maxStateOfChargePu;

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
		this.energyCapacityPuHour = 0.0;
		this.initialStateOfChargePu = 0.0;
		this.minStateOfChargePu = 0.0;
		this.maxStateOfChargePu = 1.0;
	}

	public DistOpfBatteryData(String id, String busId, Set<PhaseCode> phases,
			Complex3x1 power, double chargeLimitPu, double dischargeLimitPu,
			Double apparentPowerLimitPu, double energyCapacityPuHour,
			double initialStateOfChargePu, double minStateOfChargePu,
			double maxStateOfChargePu) {
		super(id, busId, phases, power, apparentPowerLimitPu);
		this.chargeLimitPu = Math.abs(chargeLimitPu);
		this.dischargeLimitPu = Math.abs(dischargeLimitPu);
		this.energyCapacityPuHour = Math.max(0.0, energyCapacityPuHour);
		this.initialStateOfChargePu = initialStateOfChargePu;
		this.minStateOfChargePu = minStateOfChargePu;
		this.maxStateOfChargePu = maxStateOfChargePu;
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

	public boolean hasStateOfChargeLimits() {
		return energyCapacityPuHour > 0.0;
	}

	public double getEnergyCapacityPuHour() {
		return energyCapacityPuHour;
	}

	public double getInitialStateOfChargePu() {
		return initialStateOfChargePu;
	}

	public double getMinStateOfChargePu() {
		return minStateOfChargePu;
	}

	public double getMaxStateOfChargePu() {
		return maxStateOfChargePu;
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
