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
import org.interpss.numeric.datatype.LimitType;
import org.interpss.numeric.datatype.Unit.UnitType;
import org.interpss.threePhase.basic.dstab.DStab1PLoad;
import org.interpss.threePhase.basic.dstab.DStab3PBranch;
import org.interpss.threePhase.basic.dstab.DStab3PBus;
import org.interpss.threePhase.basic.dstab.DStab3PGen;
import org.interpss.threePhase.basic.dstab.DStab3PLoad;
import org.interpss.threePhase.dynamic.DStabNetwork3Phase;

import com.interpss.core.abc.LoadConnectionType;
import com.interpss.core.aclf.AclfLoadCode;
import com.interpss.core.acsc.PhaseCode;

public class DistOpfModelDataExtractor {

	private static final double PHASE_TOLERANCE = 1.0e-10;

	public DistOpfModelData extract(DStabNetwork3Phase net) {
		List<DistOpfBusData> buses = new ArrayList<DistOpfBusData>();
		List<DistOpfBranchData> branches = new ArrayList<DistOpfBranchData>();
		List<DistOpfDerData> ders = new ArrayList<DistOpfDerData>();
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
			FixedLoadAndCapacitor fixedLoad = fixedLoadAndCapacitor(bus);
			VoltageLimit voltageLimit = voltageLimit(bus);
			buses.add(new DistOpfBusData(bus.getId(), bus.isSwing(), bus.getBaseVoltage(),
					EnumSet.of(PhaseCode.A, PhaseCode.B, PhaseCode.C), fixedLoad.load,
					fixedLoad.capacitorQ, voltageLimit.minPu, voltageLimit.maxPu));
			ders.addAll(extractDers(bus, net.getBaseMva()));
		}
		if (swingBusId == null) {
			throw new IllegalArgumentException("DistOPF requires one active swing bus");
		}

		for (DStab3PBranch branch : net.getBranchList()) {
			if (!branch.isActive()) {
				continue;
			}
			Complex3x3 zabc = zabc(branch);
			branches.add(new DistOpfBranchData(branchId(branch), branch.getFromBus().getId(),
					branch.getToBus().getId(), phasesFromBranch(branch, zabc), zabc,
					thermalLimitPu(branch, net.getBaseMva()), voltageRatio(branch)));
		}
		branches = mergeParallelPhaseBranches(branches);

		Topology topology = validateRadialAndOrient(swingBusId, buses, branches);
		return new DistOpfModelData(net.getBaseMva(), swingBusId, buses, branches, ders,
				topology.childrenByBusId, topology.parentBranchByBusId);
	}

	private static FixedLoadAndCapacitor fixedLoadAndCapacitor(DStab3PBus bus) {
		Complex3x1 totalLoad = safeLoad(bus);
		Complex3x1 capacitorQ = new Complex3x1();
		for (DStab3PLoad threePhaseLoad : bus.getThreePhaseLoadList()) {
			if (isFixedCapacitor(threePhaseLoad)) {
				Complex3x1 capLoad = threePhaseLoad.getInit3PhaseLoad();
				capacitorQ = capacitorQ.add(capacitorInjection(capLoad));
				totalLoad = totalLoad.subtract(capLoad);
			}
		}
		return new FixedLoadAndCapacitor(totalLoad, capacitorQ);
	}

	private static VoltageLimit voltageLimit(DStab3PBus bus) {
		LimitType limit = bus.getVLimit();
		if (limit == null || limit.isError() || limit.getMin() <= 0.0 || limit.getMax() <= 0.0) {
			return new VoltageLimit(null, null);
		}
		return new VoltageLimit(Double.valueOf(limit.getMin()), Double.valueOf(limit.getMax()));
	}

	private static Complex3x1 safeLoad(DStab3PBus bus) {
		Complex3x1 load = new Complex3x1();
		for (DStab3PLoad threePhaseLoad : bus.getThreePhaseLoadList()) {
			if (threePhaseLoad.getInit3PhaseLoad() != null) {
				load = load.add(threePhaseLoad.getInit3PhaseLoad());
			}
		}
		for (DStab1PLoad singlePhaseLoad : bus.getSinglePhaseLoadList()) {
			if (singlePhaseLoad.isActive()) {
				addSinglePhaseLoad(load, singlePhaseLoad);
			}
		}
		return load;
	}

	private static void addSinglePhaseLoad(Complex3x1 load, DStab1PLoad singlePhaseLoad) {
		Complex value = singlePhaseLoad.getLoad(1.0);
		if (value == null) {
			return;
		}
		PhaseCode phase = singlePhaseLoad.getPhaseCode();
		LoadConnectionType connection = singlePhaseLoad.getLoadConnectionType();
		if (connection == LoadConnectionType.SINGLE_PHASE_DELTA) {
			Complex half = value.divide(2.0);
			if (phase == PhaseCode.AB) {
				load.a_0 = load.a_0.add(half);
				load.b_1 = load.b_1.add(half);
			}
			else if (phase == PhaseCode.BC) {
				load.b_1 = load.b_1.add(half);
				load.c_2 = load.c_2.add(half);
			}
			else if (phase == PhaseCode.AC) {
				load.a_0 = load.a_0.add(half);
				load.c_2 = load.c_2.add(half);
			}
			return;
		}
		if (phase == PhaseCode.A) {
			load.a_0 = load.a_0.add(value);
		}
		else if (phase == PhaseCode.B) {
			load.b_1 = load.b_1.add(value);
		}
		else if (phase == PhaseCode.C) {
			load.c_2 = load.c_2.add(value);
		}
	}

	private static boolean isFixedCapacitor(DStab3PLoad load) {
		Complex3x1 value = load.getInit3PhaseLoad();
		return load.getCode() == AclfLoadCode.CONST_Z
				&& value != null
				&& isCapacitorPhase(value.a_0)
				&& isCapacitorPhase(value.b_1)
				&& isCapacitorPhase(value.c_2);
	}

	private static boolean isCapacitorPhase(Complex value) {
		return value == null || Math.abs(value.getReal()) < PHASE_TOLERANCE
				&& value.getImaginary() <= PHASE_TOLERANCE;
	}

	private static Complex3x1 capacitorInjection(Complex3x1 capLoad) {
		Complex3x1 injection = new Complex3x1();
		injection.a_0 = capacitorInjection(capLoad.a_0);
		injection.b_1 = capacitorInjection(capLoad.b_1);
		injection.c_2 = capacitorInjection(capLoad.c_2);
		return injection;
	}

	private static Complex capacitorInjection(Complex capLoadPhase) {
		if (capLoadPhase == null || capLoadPhase.getImaginary() >= 0.0) {
			return Complex.ZERO;
		}
		return new Complex(0.0, -capLoadPhase.getImaginary());
	}

	private static List<DistOpfDerData> extractDers(DStab3PBus bus, double baseMva) {
		List<DistOpfDerData> ders = new ArrayList<DistOpfDerData>();
		Set<DStab3PGen> seen = new HashSet<DStab3PGen>();
		for (DStab3PGen gen : bus.getThreePhaseGenList()) {
			addDer(bus, gen, seen, ders, baseMva);
		}
		for (DStab3PGen gen : bus.getContributeGenList()) {
			addDer(bus, gen, seen, ders, baseMva);
		}
		return ders;
	}

	private static void addDer(DStab3PBus bus, DStab3PGen gen, Set<DStab3PGen> seen,
			List<DistOpfDerData> ders, double baseMva) {
		if (gen == null || seen.contains(gen)) {
			return;
		}
		seen.add(gen);
		Complex3x1 power = gen.getPower3Phase(UnitType.PU);
		if (power == null && gen.getGen() != null) {
			Complex perPhasePower = gen.getGen().divide(3.0);
			power = new Complex3x1(perPhasePower, perPhasePower, perPhasePower);
		}
		if (power == null) {
			return;
		}
		String id = gen.getId();
		if (id == null || id.length() == 0) {
			id = "DER@" + bus.getId() + "#" + ders.size();
		}
		ders.add(new DistOpfDerData(id, bus.getId(),
				EnumSet.of(PhaseCode.A, PhaseCode.B, PhaseCode.C), power,
				apparentPowerLimitPu(gen, baseMva, power)));
	}

	private static Double apparentPowerLimitPu(DStab3PGen gen, double baseMva, Complex3x1 power) {
		double baseLimit = maxApparentPower(power);
		if (baseMva > 0.0 && gen.getMvaBase() > 0.0) {
			baseLimit = Math.max(baseLimit, gen.getMvaBase() / baseMva / 3.0);
		}
		return baseLimit > 0.0 ? Double.valueOf(baseLimit) : null;
	}

	private static double maxApparentPower(Complex3x1 power) {
		double max = 0.0;
		if (power.a_0 != null) {
			max = Math.max(max, power.a_0.abs());
		}
		if (power.b_1 != null) {
			max = Math.max(max, power.b_1.abs());
		}
		if (power.c_2 != null) {
			max = Math.max(max, power.c_2.abs());
		}
		return max;
	}

	private static String branchId(DStab3PBranch branch) {
		String id = branch.getId();
		if (id == null || id.length() == 0) {
			id = branch.getFromBus().getId() + "->" + branch.getToBus().getId() + "#" + branch.getCircuitNumber();
		}
		return id;
	}

	private static Set<PhaseCode> phasesFromBranch(DStab3PBranch branch, Complex3x3 zabc) {
		if (branch.getPhaseCode() == PhaseCode.A) {
			return EnumSet.of(PhaseCode.A);
		}
		if (branch.getPhaseCode() == PhaseCode.B) {
			return EnumSet.of(PhaseCode.B);
		}
		if (branch.getPhaseCode() == PhaseCode.C) {
			return EnumSet.of(PhaseCode.C);
		}
		return phasesFromZabc(zabc);
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

	private static Complex3x3 zabc(DStab3PBranch branch) {
		Complex3x3 zabc = branch.getZabc();
		if (zabc != null) {
			return zabc;
		}
		Complex z = branch.getZ();
		if (z == null) {
			z = Complex.ZERO;
		}
		Complex3x3 scalarZabc = new Complex3x3();
		if (branch.getPhaseCode() == PhaseCode.A) {
			scalarZabc.aa = z;
		}
		else if (branch.getPhaseCode() == PhaseCode.B) {
			scalarZabc.bb = z;
		}
		else if (branch.getPhaseCode() == PhaseCode.C) {
			scalarZabc.cc = z;
		}
		else {
			scalarZabc = Complex3x3.createUnitMatrix().multiply(z);
		}
		return scalarZabc;
	}

	private static Double thermalLimitPu(DStab3PBranch branch, double baseMva) {
		if (baseMva <= 0.0) {
			return null;
		}
		if (branch.getRatingMva1() > 0.0) {
			return Double.valueOf(branch.getRatingMva1() / baseMva);
		}
		if (branch.isXfr() && branch.getXfrRatedKVA() > 0.0) {
			return Double.valueOf(branch.getXfrRatedKVA() / 1000.0 / baseMva);
		}
		return null;
	}

	private static double voltageRatio(DStab3PBranch branch) {
		double fromTurnRatio = branch.getFromTurnRatio();
		double toTurnRatio = branch.getToTurnRatio();
		if (fromTurnRatio <= 0.0 || toTurnRatio <= 0.0) {
			return 1.0;
		}
		return toTurnRatio / fromTurnRatio;
	}

	private static boolean nonZero(Complex value) {
		return value != null && value.abs() > PHASE_TOLERANCE;
	}

	private static List<DistOpfBranchData> mergeParallelPhaseBranches(List<DistOpfBranchData> branches) {
		Map<String, List<DistOpfBranchData>> byEndpoints = new LinkedHashMap<String, List<DistOpfBranchData>>();
		for (DistOpfBranchData branch : branches) {
			String key = branch.getFromBusId() + "->" + branch.getToBusId();
			List<DistOpfBranchData> group = byEndpoints.get(key);
			if (group == null) {
				group = new ArrayList<DistOpfBranchData>();
				byEndpoints.put(key, group);
			}
			group.add(branch);
		}

		List<DistOpfBranchData> merged = new ArrayList<DistOpfBranchData>();
		for (List<DistOpfBranchData> group : byEndpoints.values()) {
			if (group.size() == 1 || !canMergeParallelPhaseBranches(group)) {
				merged.addAll(group);
			}
			else {
				merged.add(mergeParallelPhaseBranchGroup(group));
			}
		}
		return merged;
	}

	private static boolean canMergeParallelPhaseBranches(List<DistOpfBranchData> group) {
		Set<PhaseCode> phases = EnumSet.noneOf(PhaseCode.class);
		double ratio = group.get(0).getVoltageRatio();
		for (DistOpfBranchData branch : group) {
			if (Math.abs(branch.getVoltageRatio() - ratio) > PHASE_TOLERANCE) {
				return false;
			}
			for (PhaseCode phase : branch.getPhases()) {
				if (phases.contains(phase)) {
					return false;
				}
				phases.add(phase);
			}
		}
		return true;
	}

	private static DistOpfBranchData mergeParallelPhaseBranchGroup(List<DistOpfBranchData> group) {
		DistOpfBranchData first = group.get(0);
		StringBuilder id = new StringBuilder(first.getId());
		Set<PhaseCode> phases = EnumSet.noneOf(PhaseCode.class);
		Complex3x3 zabc = new Complex3x3();
		Double thermalLimitPu = null;
		for (DistOpfBranchData branch : group) {
			if (branch != first) {
				id.append("+").append(branch.getId());
			}
			phases.addAll(branch.getPhases());
			zabc = zabc.add(branch.getZabc());
			thermalLimitPu = minLimit(thermalLimitPu, branch.getThermalLimitPu());
		}
		return new DistOpfBranchData(id.toString(), first.getFromBusId(), first.getToBusId(),
				phases, zabc, thermalLimitPu, first.getVoltageRatio());
	}

	private static Double minLimit(Double current, Double candidate) {
		if (candidate == null) {
			return current;
		}
		if (current == null || candidate.doubleValue() < current.doubleValue()) {
			return candidate;
		}
		return current;
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
		Set<String> treeBranchIds = new HashSet<String>();
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
				treeBranchIds.add(branch.getId());
				traversedBranches++;
			}
		}

		if (visited.size() != busIds.size()) {
			throw new IllegalArgumentException("DistOPF v1 requires a connected feeder");
		}
		if (traversedBranches != branches.size() || branches.size() != buses.size() - 1) {
			throw new IllegalArgumentException("DistOPF v1 requires a radial feeder: buses="
					+ buses.size() + ", branches=" + branches.size()
					+ ", traversedBranches=" + traversedBranches
					+ ", nonTreeBranches=" + nonTreeBranchIds(branches, treeBranchIds));
		}
		return new Topology(childrenByBusId, parentBranchByBusId);
	}

	private static List<String> nonTreeBranchIds(List<DistOpfBranchData> branches,
			Set<String> treeBranchIds) {
		List<String> ids = new ArrayList<String>();
		for (DistOpfBranchData branch : branches) {
			if (!treeBranchIds.contains(branch.getId())) {
				ids.add(branch.getId() + "(" + branch.getFromBusId() + "->"
						+ branch.getToBusId() + ")");
			}
		}
		return ids;
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

	private static class FixedLoadAndCapacitor {
		private final Complex3x1 load;
		private final Complex3x1 capacitorQ;

		private FixedLoadAndCapacitor(Complex3x1 load, Complex3x1 capacitorQ) {
			this.load = load;
			this.capacitorQ = capacitorQ;
		}
	}

	private static class VoltageLimit {
		private final Double minPu;
		private final Double maxPu;

		private VoltageLimit(Double minPu, Double maxPu) {
			this.minPu = minPu;
			this.maxPu = maxPu;
		}
	}
}
