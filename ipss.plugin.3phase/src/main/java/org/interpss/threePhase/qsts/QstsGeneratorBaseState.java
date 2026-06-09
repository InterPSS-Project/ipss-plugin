package org.interpss.threePhase.qsts;

import org.apache.commons.math3.complex.Complex;
import org.interpss.numeric.datatype.Complex3x1;
import org.interpss.numeric.datatype.Unit.UnitType;

import com.interpss.core.aclf.AclfGenCode;
import com.interpss.core.aclf.AclfGen;
import com.interpss.core.threephase.AclfGen3Phase;

public class QstsGeneratorBaseState {
	private final Object generator;
	private final AclfGen3Phase phaseGenerator;
	private Complex gen;
	private Complex3x1 phasePower;
	private AclfGenCode code;
	private double mvaBase;

	public QstsGeneratorBaseState(Object generator) {
		if(generator == null) {
			throw new IllegalArgumentException("QSTS generator base state requires a generator");
		}
		if(!(generator instanceof AclfGen3Phase)) {
			throw new IllegalArgumentException("QSTS generator base state requires AclfGen3Phase");
		}
		this.generator = generator;
		this.phaseGenerator = (AclfGen3Phase) generator;
		refreshFromGenerator();
	}

	public Object getGenerator() {
		return generator;
	}

	public String getGeneratorId() {
		return phaseGenerator.getId();
	}

	public void refreshFromGenerator() {
		this.gen = generator instanceof AclfGen ? copy(((AclfGen) generator).getGen()) : total();
		this.phasePower = copy(phaseGenerator.getPower3Phase(UnitType.PU));
		this.code = generator instanceof AclfGen ? ((AclfGen) generator).getCode() : null;
		this.mvaBase = phaseGenerator.getMvaBase();
	}

	public void restore() {
		if(generator instanceof AclfGen) {
			if(code != null) {
				((AclfGen) generator).setCode(code);
			}
		}
		phaseGenerator.setPower3Phase(copy(phasePower), UnitType.PU);
		if(generator instanceof AclfGen) {
			((AclfGen) generator).setMvaBase(mvaBase);
		}
	}

	public void applyMultiplier(double pMultiplier, double qMultiplier) {
		restore();
		phaseGenerator.setPower3Phase(scale(phasePower, pMultiplier, qMultiplier), UnitType.PU);
	}

	public Complex getGen() {
		return copy(gen);
	}

	public Complex3x1 getThreePhasePower() {
		return copy(phasePower);
	}

	public Complex getOnePhasePower() {
		return copy(AclfGen3Phase.phaseValue(phasePower, phaseGenerator.getPhaseCode()));
	}

	private static Complex scale(Complex value, double pMultiplier, double qMultiplier) {
		if(value == null) {
			return null;
		}
		return new Complex(value.getReal() * pMultiplier, value.getImaginary() * qMultiplier);
	}

	private static Complex3x1 scale(Complex3x1 value, double pMultiplier, double qMultiplier) {
		return value == null ? null : new Complex3x1(scale(value.a_0, pMultiplier, qMultiplier),
				scale(value.b_1, pMultiplier, qMultiplier), scale(value.c_2, pMultiplier, qMultiplier));
	}

	private static Complex copy(Complex value) {
		return value == null ? null : new Complex(value.getReal(), value.getImaginary());
	}

	private static Complex3x1 copy(Complex3x1 value) {
		return value == null ? null : new Complex3x1(copy(value.a_0), copy(value.b_1), copy(value.c_2));
	}

	private Complex total() {
		Complex3x1 power = phaseGenerator.getPower3Phase(UnitType.PU);
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
}
