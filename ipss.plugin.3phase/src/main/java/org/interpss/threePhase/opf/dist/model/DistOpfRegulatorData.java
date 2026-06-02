package org.interpss.threePhase.opf.dist.model;

import java.util.Collections;
import java.util.EnumSet;
import java.util.Set;

import com.interpss.core.acsc.PhaseCode;

public class DistOpfRegulatorData {

	private final String id;
	private final String branchId;
	private final Set<PhaseCode> phases;
	private final int minTap;
	private final int maxTap;
	private final double tapStepVoltageSquaredPu;

	public DistOpfRegulatorData(String id, String branchId, Set<PhaseCode> phases,
			int minTap, int maxTap, double tapStepVoltageSquaredPu) {
		this.id = id;
		this.branchId = branchId;
		this.phases = Collections.unmodifiableSet(EnumSet.copyOf(phases));
		this.minTap = minTap;
		this.maxTap = maxTap;
		this.tapStepVoltageSquaredPu = tapStepVoltageSquaredPu;
	}

	public String getId() {
		return id;
	}

	public String getBranchId() {
		return branchId;
	}

	public Set<PhaseCode> getPhases() {
		return phases;
	}

	public int getMinTap() {
		return minTap;
	}

	public int getMaxTap() {
		return maxTap;
	}

	public double getTapStepVoltageSquaredPu() {
		return tapStepVoltageSquaredPu;
	}
}
