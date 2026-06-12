package org.interpss.threePhase.dataParser.opendss;

import org.apache.commons.math3.complex.Complex;
import org.interpss.numeric.datatype.Complex3x1;
import org.interpss.numeric.datatype.Unit.UnitType;

import com.interpss.core.threephase.impl.Static3PGenImpl;

/**
 * Static OpenDSS generator model with model-1 voltage-limit fallback.
 */
public class OpenDSSStaticGenerator extends Static3PGenImpl {
	private int openDssModel = 1;
	private double vminpu = 0.9;
	private double vmaxpu = 1.1;

	public OpenDSSStaticGenerator() {
		super();
	}

	public int getOpenDssModel() {
		return openDssModel;
	}

	public void setOpenDssModel(int openDssModel) {
		this.openDssModel = openDssModel;
	}

	public double getVminpu() {
		return vminpu;
	}

	public void setVminpu(double vminpu) {
		this.vminpu = vminpu;
	}

	public double getVmaxpu() {
		return vmaxpu;
	}

	public void setVmaxpu(double vmaxpu) {
		this.vmaxpu = vmaxpu;
	}

	@Override
	public void addEquivCurrInj(double[] vabc, int voltageOffset, double[] accumulator,
			int accumulatorOffset, int activePhaseMask) {
		if(openDssModel != 1) {
			super.addEquivCurrInj(vabc, voltageOffset, accumulator, accumulatorOffset, activePhaseMask);
			return;
		}
		if(vabc == null || accumulator == null
				|| vabc.length < voltageOffset + 6
				|| accumulator.length < accumulatorOffset + 6) {
			return;
		}
		Complex3x1 scheduled = getPower3Phase(UnitType.PU);
		if((activePhaseMask & 0b001) != 0) {
			addGeneratorCurrent(terminalInjectionPower(scheduled == null ? null : scheduled.a_0,
					vabc[voltageOffset], vabc[voltageOffset + 1]),
					vabc[voltageOffset], vabc[voltageOffset + 1], accumulator, accumulatorOffset);
		}
		if((activePhaseMask & 0b010) != 0) {
			addGeneratorCurrent(terminalInjectionPower(scheduled == null ? null : scheduled.b_1,
					vabc[voltageOffset + 2], vabc[voltageOffset + 3]),
					vabc[voltageOffset + 2], vabc[voltageOffset + 3], accumulator, accumulatorOffset + 2);
		}
		if((activePhaseMask & 0b100) != 0) {
			addGeneratorCurrent(terminalInjectionPower(scheduled == null ? null : scheduled.c_2,
					vabc[voltageOffset + 4], vabc[voltageOffset + 5]),
					vabc[voltageOffset + 4], vabc[voltageOffset + 5], accumulator, accumulatorOffset + 4);
		}
	}

	public Complex3x1 getTerminalPower3Phase(Complex3x1 voltage, UnitType unit) {
		Complex3x1 powerPu = getPower3Phase(UnitType.PU);
		Complex3x1 terminalPu = new Complex3x1(
				terminalPower(powerPu == null ? null : powerPu.a_0,
						real(voltage == null ? null : voltage.a_0),
						imaginary(voltage == null ? null : voltage.a_0)),
				terminalPower(powerPu == null ? null : powerPu.b_1,
						real(voltage == null ? null : voltage.b_1),
						imaginary(voltage == null ? null : voltage.b_1)),
				terminalPower(powerPu == null ? null : powerPu.c_2,
						real(voltage == null ? null : voltage.c_2),
						imaginary(voltage == null ? null : voltage.c_2)));
		switch(unit) {
		case PU:
			return terminalPu;
		case mVA:
			return terminalPu.multiply(getMvaBase());
		case kVA:
			return terminalPu.multiply(getMvaBase() * 1000.0);
		default:
			throw new IllegalArgumentException("The unit should be PU, mVA or kVA");
		}
	}

	private Complex terminalPower(Complex scheduledPower, double voltageReal, double voltageImaginary) {
		Complex power = scheduledPower == null ? Complex.ZERO : scheduledPower;
		if(openDssModel != 1) {
			return power;
		}
		double scale = voltageLimitScale(voltageReal, voltageImaginary);
		return scale == 1.0 ? power : power.multiply(scale);
	}

	private Complex terminalInjectionPower(Complex scheduledPower, double voltageReal,
			double voltageImaginary) {
		// OpenDSS generator schedules are stored on the three-phase network base;
		// fixed-point phase currents are assembled on the per-phase base.
		return terminalPower(scheduledPower, voltageReal, voltageImaginary).multiply(3.0);
	}

	private double voltageLimitScale(double voltageReal, double voltageImaginary) {
		double vmag = Math.hypot(voltageReal, voltageImaginary);
		if(vmag == 0.0) {
			return 0.0;
		}
		if(vminpu > 0.0 && vmag < vminpu) {
			double ratio = vmag / vminpu;
			return ratio * ratio;
		}
		if(vmaxpu > 0.0 && vmag > vmaxpu) {
			double ratio = vmag / vmaxpu;
			return ratio * ratio;
		}
		return 1.0;
	}

	private static void addGeneratorCurrent(Complex power, double voltageReal, double voltageImaginary,
			double[] accumulator, int offset) {
		double denominator = voltageReal * voltageReal + voltageImaginary * voltageImaginary;
		if(denominator < 1.0e-6) {
			return;
		}
		double powerReal = real(power);
		double powerImaginary = imaginary(power);
		accumulator[offset] += (powerReal * voltageReal + powerImaginary * voltageImaginary) / denominator;
		accumulator[offset + 1] += (powerReal * voltageImaginary - powerImaginary * voltageReal) / denominator;
	}

	private static double real(Complex value) {
		return value == null ? 0.0 : value.getReal();
	}

	private static double imaginary(Complex value) {
		return value == null ? 0.0 : value.getImaginary();
	}
}
