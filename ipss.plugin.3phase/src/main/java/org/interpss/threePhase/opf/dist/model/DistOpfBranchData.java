package org.interpss.threePhase.opf.dist.model;

import java.util.Collections;
import java.util.EnumSet;
import java.util.Set;

import org.interpss.numeric.datatype.Complex3x3;

import com.interpss.core.acsc.PhaseCode;

public class DistOpfBranchData {

	private final String id;
	private final String fromBusId;
	private final String toBusId;
	private final Set<PhaseCode> phases;
	private final Complex3x3 zabc;

	public DistOpfBranchData(String id, String fromBusId, String toBusId,
			Set<PhaseCode> phases, Complex3x3 zabc) {
		this.id = id;
		this.fromBusId = fromBusId;
		this.toBusId = toBusId;
		this.phases = Collections.unmodifiableSet(EnumSet.copyOf(phases));
		this.zabc = zabc;
	}

	public String getId() {
		return id;
	}

	public String getFromBusId() {
		return fromBusId;
	}

	public String getToBusId() {
		return toBusId;
	}

	public Set<PhaseCode> getPhases() {
		return phases;
	}

	public Complex3x3 getZabc() {
		return zabc;
	}
}
