package org.interpss.threePhase.opf.dist.model;

import java.util.Collections;
import java.util.EnumSet;
import java.util.Set;

import org.apache.commons.math3.complex.Complex;
import org.interpss.numeric.datatype.Complex3x1;

import com.interpss.core.acsc.PhaseCode;

public class DistOpfDerData {

	private final String id;
	private final String busId;
	private final Set<PhaseCode> phases;
	private final Complex3x1 power;

	public DistOpfDerData(String id, String busId, Set<PhaseCode> phases, Complex3x1 power) {
		this.id = id;
		this.busId = busId;
		this.phases = Collections.unmodifiableSet(EnumSet.copyOf(phases));
		this.power = power == null ? new Complex3x1() : power;
	}

	public String getId() {
		return id;
	}

	public String getBusId() {
		return busId;
	}

	public Set<PhaseCode> getPhases() {
		return phases;
	}

	public Complex3x1 getPower() {
		return power;
	}

	public double getP(PhaseCode phase) {
		return phaseValue(phase).getReal();
	}

	public double getQ(PhaseCode phase) {
		return phaseValue(phase).getImaginary();
	}

	public double getPMin(PhaseCode phase) {
		return Math.min(0.0, getP(phase));
	}

	public double getPMax(PhaseCode phase) {
		return Math.max(0.0, getP(phase));
	}

	public double getQAbsLimit(PhaseCode phase) {
		double p = Math.abs(getP(phase));
		double q = Math.abs(getQ(phase));
		return Math.max(Math.max(p, q), 1.0e-6);
	}

	private Complex phaseValue(PhaseCode phase) {
		switch (phase) {
		case A:
			return power.a_0 == null ? Complex.ZERO : power.a_0;
		case B:
			return power.b_1 == null ? Complex.ZERO : power.b_1;
		case C:
			return power.c_2 == null ? Complex.ZERO : power.c_2;
		default:
			return Complex.ZERO;
		}
	}
}
