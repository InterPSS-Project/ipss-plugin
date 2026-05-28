package org.interpss.threePhase.opf.dist.model;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.Set;

import org.apache.commons.math3.complex.Complex;
import org.interpss.numeric.datatype.Complex3x1;
import org.interpss.numeric.datatype.Complex3x3;
import org.interpss.threePhase.basic.dstab.DStab3PBranch;
import org.interpss.threePhase.basic.dstab.DStab3PBus;
import org.interpss.threePhase.basic.dstab.DStab3PLoad;
import org.interpss.threePhase.dynamic.DStabNetwork3Phase;

import com.interpss.core.acsc.PhaseCode;

public class DistOpfModelDataExtractor {

	private static final double PHASE_TOLERANCE = 1.0e-10;

	public DistOpfModelData extract(DStabNetwork3Phase net) {
		List<DistOpfBusData> buses = new ArrayList<DistOpfBusData>();
		List<DistOpfBranchData> branches = new ArrayList<DistOpfBranchData>();
		String swingBusId = null;

		for (DStab3PBus bus : net.getBusList()) {
			if (!bus.isActive()) {
				continue;
			}
			if (bus.isSwing()) {
				if (swingBusId != null) {
					throw new IllegalArgumentException("DistOPF v1 supports one swing bus only");
				}
				swingBusId = bus.getId();
			}
			buses.add(new DistOpfBusData(bus.getId(), bus.isSwing(), bus.getBaseVoltage(),
					EnumSet.of(PhaseCode.A, PhaseCode.B, PhaseCode.C), safeLoad(bus)));
		}
		if (swingBusId == null) {
			throw new IllegalArgumentException("DistOPF requires one active swing bus");
		}

		for (DStab3PBranch branch : net.getBranchList()) {
			if (!branch.isActive()) {
				continue;
			}
			Complex3x3 zabc = branch.getZabc();
			branches.add(new DistOpfBranchData(branchId(branch), branch.getFromBus().getId(),
					branch.getToBus().getId(), phasesFromZabc(zabc), zabc));
		}

		Topology topology = validateRadialAndOrient(swingBusId, buses, branches);
		return new DistOpfModelData(net.getBaseMva(), swingBusId, buses, branches,
				topology.childrenByBusId, topology.parentBranchByBusId);
	}

	private static Complex3x1 safeLoad(DStab3PBus bus) {
		try {
			Complex3x1 load = bus.get3PhaseTotalLoad();
			if (load != null) {
				return load;
			}
		} catch (NullPointerException e) {
			// Some OpenDSS parser paths populate loads before initializing Vabc.
		}
		Complex3x1 load = new Complex3x1();
		for (DStab3PLoad threePhaseLoad : bus.getThreePhaseLoadList()) {
			if (threePhaseLoad.getInit3PhaseLoad() != null) {
				load = load.add(threePhaseLoad.getInit3PhaseLoad());
			}
		}
		return load;
	}

	private static String branchId(DStab3PBranch branch) {
		String id = branch.getId();
		if (id == null || id.length() == 0) {
			id = branch.getFromBus().getId() + "->" + branch.getToBus().getId() + "#" + branch.getCircuitNumber();
		}
		return id;
	}

	private static Set<PhaseCode> phasesFromZabc(Complex3x3 zabc) {
		Set<PhaseCode> phases = EnumSet.noneOf(PhaseCode.class);
		if (nonZero(zabc.aa)) {
			phases.add(PhaseCode.A);
		}
		if (nonZero(zabc.bb)) {
			phases.add(PhaseCode.B);
		}
		if (nonZero(zabc.cc)) {
			phases.add(PhaseCode.C);
		}
		if (phases.isEmpty()) {
			phases.add(PhaseCode.A);
			phases.add(PhaseCode.B);
			phases.add(PhaseCode.C);
		}
		return phases;
	}

	private static boolean nonZero(Complex value) {
		return value != null && value.abs() > PHASE_TOLERANCE;
	}

	private static Topology validateRadialAndOrient(String swingBusId,
			List<DistOpfBusData> buses, List<DistOpfBranchData> branches) {
		Set<String> busIds = new HashSet<String>();
		for (DistOpfBusData bus : buses) {
			busIds.add(bus.getId());
		}

		Map<String, List<DistOpfBranchData>> adjacent = new HashMap<String, List<DistOpfBranchData>>();
		for (DistOpfBranchData branch : branches) {
			addAdjacent(adjacent, branch.getFromBusId(), branch);
			addAdjacent(adjacent, branch.getToBusId(), branch);
		}

		Set<String> visited = new HashSet<String>();
		Map<String, List<DistOpfBranchData>> childrenByBusId = new LinkedHashMap<String, List<DistOpfBranchData>>();
		Map<String, DistOpfBranchData> parentBranchByBusId = new LinkedHashMap<String, DistOpfBranchData>();
		Queue<String> queue = new ArrayDeque<String>();
		queue.add(swingBusId);
		visited.add(swingBusId);

		int traversedBranches = 0;
		while (!queue.isEmpty()) {
			String busId = queue.remove();
			for (DistOpfBranchData branch : adjacent.getOrDefault(busId, Collections.<DistOpfBranchData>emptyList())) {
				String nextBusId = branch.getFromBusId().equals(busId) ? branch.getToBusId() : branch.getFromBusId();
				if (visited.contains(nextBusId)) {
					continue;
				}
				visited.add(nextBusId);
				queue.add(nextBusId);
				addChild(childrenByBusId, busId, branch);
				parentBranchByBusId.put(nextBusId, branch);
				traversedBranches++;
			}
		}

		if (visited.size() != busIds.size()) {
			throw new IllegalArgumentException("DistOPF v1 requires a connected feeder");
		}
		if (traversedBranches != branches.size() || branches.size() != buses.size() - 1) {
			throw new IllegalArgumentException("DistOPF v1 requires a radial feeder");
		}
		return new Topology(childrenByBusId, parentBranchByBusId);
	}

	private static void addAdjacent(Map<String, List<DistOpfBranchData>> adjacent,
			String busId, DistOpfBranchData branch) {
		List<DistOpfBranchData> list = adjacent.get(busId);
		if (list == null) {
			list = new ArrayList<DistOpfBranchData>();
			adjacent.put(busId, list);
		}
		list.add(branch);
	}

	private static void addChild(Map<String, List<DistOpfBranchData>> childrenByBusId,
			String busId, DistOpfBranchData branch) {
		List<DistOpfBranchData> list = childrenByBusId.get(busId);
		if (list == null) {
			list = new ArrayList<DistOpfBranchData>();
			childrenByBusId.put(busId, list);
		}
		list.add(branch);
	}

	private static class Topology {
		private final Map<String, List<DistOpfBranchData>> childrenByBusId;
		private final Map<String, DistOpfBranchData> parentBranchByBusId;

		private Topology(Map<String, List<DistOpfBranchData>> childrenByBusId,
				Map<String, DistOpfBranchData> parentBranchByBusId) {
			this.childrenByBusId = childrenByBusId;
			this.parentBranchByBusId = parentBranchByBusId;
		}
	}
}
