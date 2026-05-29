package org.interpss.threePhase.opf.dist.model;

import java.util.Collections;
import java.util.EnumSet;
import java.util.Set;

import org.apache.commons.math3.complex.Complex;
import org.interpss.numeric.datatype.Complex3x1;

import com.interpss.core.acsc.PhaseCode;

public class DistOpfCapacitorData {

	private final String id;
	private final String busId;
	private final Set<PhaseCode> phases;
	private final Complex3x1 reactivePower;

	public DistOpfCapacitorData(String id, String busId, Set<PhaseCode> phases,
			Complex3x1 reactivePower) {
		this.id = id;
		this.busId = busId;
		this.phases = Collections.unmodifiableSet(EnumSet.copyOf(phases));
		this.reactivePower = reactivePower == null ? new Complex3x1() : reactivePower;
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

	public Complex3x1 getReactivePower() {
		return reactivePower;
	}

	public double getQ(PhaseCode phase) {
		switch (phase) {
		case A:
			return q(reactivePower.a_0);
		case B:
			return q(reactivePower.b_1);
		case C:
			return q(reactivePower.c_2);
		default:
			return 0.0;
		}
	}

	private static double q(Complex value) {
		return value == null ? 0.0 : value.getImaginary();
	}
}
