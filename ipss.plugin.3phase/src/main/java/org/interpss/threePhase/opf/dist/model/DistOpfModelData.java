package org.interpss.threePhase.opf.dist.model;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class DistOpfModelData {

	private final double baseMva;
	private final String swingBusId;
	private final List<DistOpfBusData> buses;
	private final List<DistOpfBranchData> branches;
	private final Map<String, List<DistOpfBranchData>> childrenByBusId;

	public DistOpfModelData(double baseMva, String swingBusId,
			List<DistOpfBusData> buses, List<DistOpfBranchData> branches,
			Map<String, List<DistOpfBranchData>> childrenByBusId) {
		this.baseMva = baseMva;
		this.swingBusId = swingBusId;
		this.buses = Collections.unmodifiableList(new ArrayList<DistOpfBusData>(buses));
		this.branches = Collections.unmodifiableList(new ArrayList<DistOpfBranchData>(branches));
		this.childrenByBusId = copyChildren(childrenByBusId);
	}

	public double getBaseMva() {
		return baseMva;
	}

	public String getSwingBusId() {
		return swingBusId;
	}

	public List<DistOpfBusData> getBuses() {
		return buses;
	}

	public List<DistOpfBranchData> getBranches() {
		return branches;
	}

	public List<DistOpfBranchData> getChildren(String busId) {
		List<DistOpfBranchData> children = childrenByBusId.get(busId);
		return children == null ? Collections.<DistOpfBranchData>emptyList() : children;
	}

	private static Map<String, List<DistOpfBranchData>> copyChildren(
			Map<String, List<DistOpfBranchData>> source) {
		Map<String, List<DistOpfBranchData>> copy = new LinkedHashMap<String, List<DistOpfBranchData>>();
		for (Map.Entry<String, List<DistOpfBranchData>> entry : source.entrySet()) {
			copy.put(entry.getKey(), Collections.unmodifiableList(
					new ArrayList<DistOpfBranchData>(entry.getValue())));
		}
		return Collections.unmodifiableMap(copy);
	}
}
