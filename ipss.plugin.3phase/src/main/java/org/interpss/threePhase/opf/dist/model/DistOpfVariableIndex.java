package org.interpss.threePhase.opf.dist.model;

import java.util.LinkedHashMap;
import java.util.Map;

import com.interpss.core.acsc.PhaseCode;

public class DistOpfVariableIndex {

	public enum VariableType {
		BRANCH_P,
		BRANCH_Q,
		BUS_V2,
		DER_P,
		DER_Q,
		CURTAILMENT
	}

	private final Map<String, Integer> indexByKey = new LinkedHashMap<String, Integer>();

	public int branchP(String branchId, PhaseCode phase) {
		return getOrCreate(VariableType.BRANCH_P, branchId, phase);
	}

	public int branchQ(String branchId, PhaseCode phase) {
		return getOrCreate(VariableType.BRANCH_Q, branchId, phase);
	}

	public int busV2(String busId, PhaseCode phase) {
		return getOrCreate(VariableType.BUS_V2, busId, phase);
	}

	public int derP(String busId, PhaseCode phase) {
		return getOrCreate(VariableType.DER_P, busId, phase);
	}

	public int derQ(String busId, PhaseCode phase) {
		return getOrCreate(VariableType.DER_Q, busId, phase);
	}

	public int curtailment(String busId, PhaseCode phase) {
		return getOrCreate(VariableType.CURTAILMENT, busId, phase);
	}

	public int size() {
		return indexByKey.size();
	}

	private int getOrCreate(VariableType type, String elementId, PhaseCode phase) {
		String key = type.name() + ":" + elementId + ":" + phase.name();
		Integer index = indexByKey.get(key);
		if (index == null) {
			index = Integer.valueOf(indexByKey.size());
			indexByKey.put(key, index);
		}
		return index.intValue();
	}
}
