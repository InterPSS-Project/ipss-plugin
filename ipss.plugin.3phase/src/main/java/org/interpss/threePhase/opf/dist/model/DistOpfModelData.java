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
	private final List<DistOpfCapacitorData> capacitors;
	private final List<DistOpfRegulatorData> regulators;
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
		this(baseMva, swingBusId, buses, branches, ders, Collections.<DistOpfCapacitorData>emptyList(),
				childrenByBusId, parentBranchByBusId);
	}

	public DistOpfModelData(double baseMva, String swingBusId,
			List<DistOpfBusData> buses, List<DistOpfBranchData> branches, List<DistOpfDerData> ders,
			List<DistOpfCapacitorData> capacitors,
			Map<String, List<DistOpfBranchData>> childrenByBusId,
			Map<String, DistOpfBranchData> parentBranchByBusId) {
		this(baseMva, swingBusId, buses, branches, ders, capacitors,
				Collections.<DistOpfRegulatorData>emptyList(), childrenByBusId, parentBranchByBusId);
	}

	public DistOpfModelData(double baseMva, String swingBusId,
			List<DistOpfBusData> buses, List<DistOpfBranchData> branches, List<DistOpfDerData> ders,
			List<DistOpfCapacitorData> capacitors, List<DistOpfRegulatorData> regulators,
			Map<String, List<DistOpfBranchData>> childrenByBusId,
			Map<String, DistOpfBranchData> parentBranchByBusId) {
		this.baseMva = baseMva;
		this.swingBusId = swingBusId;
		this.buses = Collections.unmodifiableList(new ArrayList<DistOpfBusData>(buses));
		this.branches = Collections.unmodifiableList(new ArrayList<DistOpfBranchData>(branches));
		this.ders = Collections.unmodifiableList(new ArrayList<DistOpfDerData>(ders));
		this.capacitors = Collections.unmodifiableList(new ArrayList<DistOpfCapacitorData>(capacitors));
		this.regulators = Collections.unmodifiableList(new ArrayList<DistOpfRegulatorData>(regulators));
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

	public List<DistOpfCapacitorData> getCapacitors() {
		return capacitors;
	}

	public List<DistOpfCapacitorData> getCapacitors(String busId) {
		List<DistOpfCapacitorData> busCapacitors = new ArrayList<DistOpfCapacitorData>();
		for (DistOpfCapacitorData capacitor : capacitors) {
			if (capacitor.getBusId().equals(busId)) {
				busCapacitors.add(capacitor);
			}
		}
		return Collections.unmodifiableList(busCapacitors);
	}

	public List<DistOpfRegulatorData> getRegulators() {
		return regulators;
	}

	public List<DistOpfRegulatorData> getRegulators(String branchId) {
		List<DistOpfRegulatorData> branchRegulators = new ArrayList<DistOpfRegulatorData>();
		for (DistOpfRegulatorData regulator : regulators) {
			if (regulator.getBranchId().equals(branchId)) {
				branchRegulators.add(regulator);
			}
		}
		return Collections.unmodifiableList(branchRegulators);
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
