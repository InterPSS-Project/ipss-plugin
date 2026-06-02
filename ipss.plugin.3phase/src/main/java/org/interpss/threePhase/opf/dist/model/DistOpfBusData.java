package org.interpss.threePhase.opf.dist.model;

import java.util.Collections;
import java.util.EnumSet;
import java.util.Set;

import org.apache.commons.math3.complex.Complex;
import org.interpss.numeric.datatype.Complex3x1;

import com.interpss.core.acsc.PhaseCode;

public class DistOpfBusData {

	private final String id;
	private final boolean swing;
	private final double baseVoltage;
	private final Set<PhaseCode> phases;
	private final Complex3x1 load;
	private final Complex3x1 fixedCapacitorQ;
	private final Complex3x1 initialVoltageMagnitude;
	private final Double minVoltagePu;
	private final Double maxVoltagePu;

	public DistOpfBusData(String id, boolean swing, double baseVoltage,
			Set<PhaseCode> phases, Complex3x1 load) {
		this(id, swing, baseVoltage, phases, load, new Complex3x1());
	}

	public DistOpfBusData(String id, boolean swing, double baseVoltage,
			Set<PhaseCode> phases, Complex3x1 load, Complex3x1 fixedCapacitorQ) {
		this(id, swing, baseVoltage, phases, load, fixedCapacitorQ, null, null);
	}

	public DistOpfBusData(String id, boolean swing, double baseVoltage,
			Set<PhaseCode> phases, Complex3x1 load, Complex3x1 fixedCapacitorQ,
			Double minVoltagePu, Double maxVoltagePu) {
		this(id, swing, baseVoltage, phases, load, fixedCapacitorQ,
				new Complex3x1(new Complex(1.0, 0.0), new Complex(1.0, 0.0), new Complex(1.0, 0.0)),
				minVoltagePu, maxVoltagePu);
	}

	public DistOpfBusData(String id, boolean swing, double baseVoltage,
			Set<PhaseCode> phases, Complex3x1 load, Complex3x1 fixedCapacitorQ,
			Complex3x1 initialVoltageMagnitude, Double minVoltagePu, Double maxVoltagePu) {
		this.id = id;
		this.swing = swing;
		this.baseVoltage = baseVoltage;
		this.phases = Collections.unmodifiableSet(EnumSet.copyOf(phases));
		this.load = load;
		this.fixedCapacitorQ = fixedCapacitorQ == null ? new Complex3x1() : fixedCapacitorQ;
		this.initialVoltageMagnitude = initialVoltageMagnitude == null
				? new Complex3x1(new Complex(1.0, 0.0), new Complex(1.0, 0.0), new Complex(1.0, 0.0))
				: initialVoltageMagnitude;
		this.minVoltagePu = minVoltagePu;
		this.maxVoltagePu = maxVoltagePu;
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

	public Complex3x1 getFixedCapacitorQ() {
		return fixedCapacitorQ;
	}

	public double getInitialVoltageMagnitude(PhaseCode phase) {
		Complex value;
		switch (phase) {
		case A:
			value = initialVoltageMagnitude.a_0;
			break;
		case B:
			value = initialVoltageMagnitude.b_1;
			break;
		case C:
			value = initialVoltageMagnitude.c_2;
			break;
		default:
			value = null;
		}
		return value == null ? 1.0 : value.getReal();
	}

	public Double getMinVoltagePu() {
		return minVoltagePu;
	}

	public Double getMaxVoltagePu() {
		return maxVoltagePu;
	}
}
