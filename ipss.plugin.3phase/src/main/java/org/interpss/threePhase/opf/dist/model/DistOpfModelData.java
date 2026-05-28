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
	private final List<DistOpfDerData> ders;
	private final Map<String, List<DistOpfBranchData>> childrenByBusId;
	private final Map<String, DistOpfBranchData> parentBranchByBusId;

	public DistOpfModelData(double baseMva, String swingBusId,
			List<DistOpfBusData> buses, List<DistOpfBranchData> branches,
			Map<String, List<DistOpfBranchData>> childrenByBusId,
			Map<String, DistOpfBranchData> parentBranchByBusId) {
		this(baseMva, swingBusId, buses, branches, Collections.<DistOpfDerData>emptyList(),
				childrenByBusId, parentBranchByBusId);
	}

	public DistOpfModelData(double baseMva, String swingBusId,
			List<DistOpfBusData> buses, List<DistOpfBranchData> branches, List<DistOpfDerData> ders,
			Map<String, List<DistOpfBranchData>> childrenByBusId,
			Map<String, DistOpfBranchData> parentBranchByBusId) {
		this.baseMva = baseMva;
		this.swingBusId = swingBusId;
		this.buses = Collections.unmodifiableList(new ArrayList<DistOpfBusData>(buses));
		this.branches = Collections.unmodifiableList(new ArrayList<DistOpfBranchData>(branches));
		this.ders = Collections.unmodifiableList(new ArrayList<DistOpfDerData>(ders));
		this.childrenByBusId = copyChildren(childrenByBusId);
		this.parentBranchByBusId = Collections.unmodifiableMap(
				new LinkedHashMap<String, DistOpfBranchData>(parentBranchByBusId));
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

	public List<DistOpfDerData> getDers() {
		return ders;
	}

	public List<DistOpfDerData> getDers(String busId) {
		List<DistOpfDerData> busDers = new ArrayList<DistOpfDerData>();
		for (DistOpfDerData der : ders) {
			if (der.getBusId().equals(busId)) {
				busDers.add(der);
			}
		}
		return Collections.unmodifiableList(busDers);
	}

	public List<DistOpfBranchData> getChildren(String busId) {
		List<DistOpfBranchData> children = childrenByBusId.get(busId);
		return children == null ? Collections.<DistOpfBranchData>emptyList() : children;
	}

	public DistOpfBranchData getParentBranch(String busId) {
		return parentBranchByBusId.get(busId);
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
