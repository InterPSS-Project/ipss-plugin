package org.interpss.threePhase.qsts.control;

import java.util.List;

import org.interpss.threePhase.powerflow.DistributionPFMethod;
import org.interpss.threePhase.powerflow.control.CapacitorControlData;
import org.interpss.threePhase.powerflow.control.InverterControlData;
import org.interpss.threePhase.powerflow.control.RegulatorControlData;

public class QstsControlCompensationPolicy {
	private final boolean capacitorCurrentInjectionEnabled;
	private final boolean inverterCurrentInjectionEnabled;
	private final boolean regulatorTapCompensationEnabled;

	public QstsControlCompensationPolicy() {
		this(true, true, false);
	}

	public QstsControlCompensationPolicy(boolean capacitorCurrentInjectionEnabled,
			boolean inverterCurrentInjectionEnabled, boolean regulatorTapCompensationEnabled) {
		this.capacitorCurrentInjectionEnabled = capacitorCurrentInjectionEnabled;
		this.inverterCurrentInjectionEnabled = inverterCurrentInjectionEnabled;
		this.regulatorTapCompensationEnabled = regulatorTapCompensationEnabled;
	}

	public boolean canReuseFixedPointYMatrix(DistributionPFMethod method, boolean controlsEnabled,
			List<RegulatorControlData> regulatorControls, List<CapacitorControlData> capacitorControls,
			List<InverterControlData> inverterControls) {
		if(method != DistributionPFMethod.Fixed_Point) {
			return false;
		}
		if(!controlsEnabled) {
			return true;
		}
		if(hasItems(regulatorControls) && !regulatorTapCompensationEnabled) {
			return false;
		}
		if(hasItems(capacitorControls) && !capacitorCurrentInjectionEnabled) {
			return false;
		}
		if(hasItems(inverterControls) && !inverterCurrentInjectionEnabled) {
			return false;
		}
		return true;
	}

	public boolean isCapacitorCurrentInjectionEnabled() {
		return capacitorCurrentInjectionEnabled;
	}

	public boolean isInverterCurrentInjectionEnabled() {
		return inverterCurrentInjectionEnabled;
	}

	public boolean isRegulatorTapCompensationEnabled() {
		return regulatorTapCompensationEnabled;
	}

	private boolean hasItems(List<?> values) {
		return values != null && !values.isEmpty();
	}
}
