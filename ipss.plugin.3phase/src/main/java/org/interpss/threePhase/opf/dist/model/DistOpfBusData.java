package org.interpss.threePhase.opf.dist.model;

import java.util.Collections;
import java.util.EnumSet;
import java.util.Set;

import org.interpss.numeric.datatype.Complex3x1;

import com.interpss.core.acsc.PhaseCode;

public class DistOpfBusData {

	private final String id;
	private final boolean swing;
	private final double baseVoltage;
	private final Set<PhaseCode> phases;
	private final Complex3x1 load;

	public DistOpfBusData(String id, boolean swing, double baseVoltage,
			Set<PhaseCode> phases, Complex3x1 load) {
		this.id = id;
		this.swing = swing;
		this.baseVoltage = baseVoltage;
		this.phases = Collections.unmodifiableSet(EnumSet.copyOf(phases));
		this.load = load;
	}

	public String getId() {
		return id;
	}

	public boolean isSwing() {
		return swing;
	}

	public double getBaseVoltage() {
		return baseVoltage;
	}

	public Set<PhaseCode> getPhases() {
		return phases;
	}

	public Complex3x1 getLoad() {
		return load;
	}
}
