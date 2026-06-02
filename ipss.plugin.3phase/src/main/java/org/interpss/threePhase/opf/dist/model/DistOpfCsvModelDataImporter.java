package org.interpss.threePhase.opf.dist.model;

import java.io.BufferedReader;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.apache.commons.math3.complex.Complex;
import org.interpss.numeric.datatype.Complex3x1;
import org.interpss.numeric.datatype.Complex3x3;

import com.interpss.core.acsc.PhaseCode;

public class DistOpfCsvModelDataImporter {

	public DistOpfModelData importModel(Path directory) {
		return importModel(directory, true);
	}

	public DistOpfModelData importModel(Path directory, boolean includeVoltageLimits) {
		try {
			Map<String, Complex3x1> capacitors = capacitors(directory.resolve("cap_data.csv"));
			List<DistOpfBusData> buses = buses(directory.resolve("bus_data.csv"), capacitors, includeVoltageLimits);
			List<DistOpfDerData> ders = ders(directory.resolve("gen_data.csv"));
			Map<String, Map<PhaseCode, Double>> regulatorRatios = regulatorRatios(directory.resolve("reg_data.csv"));
			List<DistOpfBranchData> branches = branches(directory.resolve("branch_data.csv"), regulatorRatios,
					regulatorRatios.keySet());
			String swingBusId = swingBusId(buses);
			branches = orientBranches(swingBusId, branches);
			Map<String, List<DistOpfBranchData>> childrenByBusId =
					new LinkedHashMap<String, List<DistOpfBranchData>>();
			Map<String, DistOpfBranchData> parentBranchByBusId =
					new LinkedHashMap<String, DistOpfBranchData>();
			for (DistOpfBranchData branch : branches) {
				addChild(childrenByBusId, branch.getFromBusId(), branch);
				parentBranchByBusId.put(branch.getToBusId(), branch);
			}
			return new DistOpfModelData(1.0, swingBusId, buses, branches, ders,
					childrenByBusId, parentBranchByBusId);
		} catch (IOException e) {
			throw new IllegalArgumentException("Unable to import DistOPF CSV model from " + directory, e);
		}
	}

	private static List<DistOpfBusData> buses(Path path, Map<String, Complex3x1> capacitors,
			boolean includeVoltageLimits) throws IOException {
		List<DistOpfBusData> buses = new ArrayList<DistOpfBusData>();
		for (Map<String, String> row : rows(path)) {
			String id = value(row, "id");
			Set<PhaseCode> phases = phases(value(row, "phases"));
			Complex3x1 load = new Complex3x1(
					pq(row, "pl_a", "ql_a"), pq(row, "pl_b", "ql_b"), pq(row, "pl_c", "ql_c"));
			Complex3x1 voltage = new Complex3x1(new Complex(number(row, "v_a", 1.0), 0.0),
					new Complex(number(row, "v_b", 1.0), 0.0),
					new Complex(number(row, "v_c", 1.0), 0.0));
			buses.add(new DistOpfBusData(id, "SWING".equalsIgnoreCase(value(row, "bus_type")),
					number(row, "v_ln_base", 1.0), phases, load, capacitors.get(id),
					voltage, includeVoltageLimits ? numberObject(row, "v_min") : null,
					includeVoltageLimits ? numberObject(row, "v_max") : null));
		}
		return buses;
	}

	private static List<DistOpfBranchData> branches(Path path,
			Map<String, Map<PhaseCode, Double>> regulatorRatios, Set<String> regulatorBranches) throws IOException {
		List<DistOpfBranchData> branches = new ArrayList<DistOpfBranchData>();
		for (Map<String, String> row : rows(path)) {
			if ("OPEN".equalsIgnoreCase(value(row, "status"))) {
				continue;
			}
			String from = value(row, "fb");
			String to = value(row, "tb");
			String name = value(row, "name");
			String id = name == null || name.length() == 0 ? from + "->" + to : name;
			branches.add(new DistOpfBranchData(id, from, to, phases(value(row, "phases")),
					zabc(row), null, 1.0, regulatorRatios.get(key(from, to)),
					regulatorBranches.contains(key(from, to))));
		}
		return branches;
	}

	private static List<DistOpfBranchData> orientBranches(String swingBusId, List<DistOpfBranchData> branches) {
		Map<String, List<DistOpfBranchData>> adjacency = new LinkedHashMap<String, List<DistOpfBranchData>>();
		for (DistOpfBranchData branch : branches) {
			addAdjacent(adjacency, branch.getFromBusId(), branch);
			addAdjacent(adjacency, branch.getToBusId(), branch);
		}
		List<DistOpfBranchData> oriented = new ArrayList<DistOpfBranchData>();
		Set<DistOpfBranchData> used = new HashSet<DistOpfBranchData>();
		orientChildren(swingBusId, adjacency, used, oriented);
		if (oriented.size() != branches.size()) {
			for (DistOpfBranchData branch : branches) {
				if (!used.contains(branch)) {
					oriented.add(branch);
				}
			}
		}
		return oriented;
	}

	private static void orientChildren(String busId, Map<String, List<DistOpfBranchData>> adjacency,
			Set<DistOpfBranchData> used, List<DistOpfBranchData> oriented) {
		List<DistOpfBranchData> branches = adjacency.get(busId);
		if (branches == null) {
			return;
		}
		for (DistOpfBranchData branch : branches) {
			if (used.contains(branch)) {
				continue;
			}
			used.add(branch);
			DistOpfBranchData orientedBranch = branch.getFromBusId().equals(busId)
					? branch
					: reverse(branch);
			oriented.add(orientedBranch);
			orientChildren(orientedBranch.getToBusId(), adjacency, used, oriented);
		}
	}

	private static DistOpfBranchData reverse(DistOpfBranchData branch) {
		return new DistOpfBranchData(branch.getId(), branch.getToBusId(), branch.getFromBusId(),
				branch.getPhases(), branch.getZabc(), branch.getThermalLimitPu(), branch.getVoltageRatio(),
				branch.getVoltageRatioByPhase(), branch.isFixedVoltageRatioOnly());
	}

	private static List<DistOpfDerData> ders(Path path) throws IOException {
		List<DistOpfDerData> ders = new ArrayList<DistOpfDerData>();
		if (!Files.exists(path)) {
			return ders;
		}
		for (Map<String, String> row : rows(path)) {
			String id = value(row, "id");
			if (id == null || id.length() == 0) {
				continue;
			}
			double limit = Math.max(number(row, "sa_max", 0.0),
					Math.max(number(row, "sb_max", 0.0), number(row, "sc_max", 0.0)));
			ders.add(new DistOpfDerData(value(row, "name"), id, phases(value(row, "phases")),
					new Complex3x1(pq(row, "pa", "qa"), pq(row, "pb", "qb"), pq(row, "pc", "qc")),
					limit > 0.0 ? Double.valueOf(limit) : null));
		}
		return ders;
	}

	private static Map<String, Complex3x1> capacitors(Path path) throws IOException {
		Map<String, Complex3x1> capacitors = new LinkedHashMap<String, Complex3x1>();
		if (!Files.exists(path)) {
			return capacitors;
		}
		for (Map<String, String> row : rows(path)) {
			capacitors.put(value(row, "id"), new Complex3x1(
					new Complex(0.0, number(row, "qa", 0.0)),
					new Complex(0.0, number(row, "qb", 0.0)),
					new Complex(0.0, number(row, "qc", 0.0))));
		}
		return capacitors;
	}

	private static Map<String, Map<PhaseCode, Double>> regulatorRatios(Path path) throws IOException {
		Map<String, Map<PhaseCode, Double>> ratios = new LinkedHashMap<String, Map<PhaseCode, Double>>();
		if (!Files.exists(path)) {
			return ratios;
		}
		for (Map<String, String> row : rows(path)) {
			Map<PhaseCode, Double> phaseRatios = new EnumMap<PhaseCode, Double>(PhaseCode.class);
			phaseRatios.put(PhaseCode.A, Double.valueOf(regulatorRatio(row, "ratio_a", "tap_a")));
			phaseRatios.put(PhaseCode.B, Double.valueOf(regulatorRatio(row, "ratio_b", "tap_b")));
			phaseRatios.put(PhaseCode.C, Double.valueOf(regulatorRatio(row, "ratio_c", "tap_c")));
			ratios.put(key(value(row, "fb"), value(row, "tb")), phaseRatios);
		}
		return ratios;
	}

	private static double regulatorRatio(Map<String, String> row, String ratioColumn, String tapColumn) {
		String ratio = value(row, ratioColumn);
		if (ratio != null && ratio.length() > 0) {
			return Double.parseDouble(ratio);
		}
		return 1.0 + 0.00625 * number(row, tapColumn, 0.0);
	}

	private static Complex3x3 zabc(Map<String, String> row) {
		return new Complex3x3(new Complex[][] {
				{ z(row, "raa", "xaa"), z(row, "rab", "xab"), z(row, "rac", "xac") },
				{ z(row, "rab", "xab"), z(row, "rbb", "xbb"), z(row, "rbc", "xbc") },
				{ z(row, "rac", "xac"), z(row, "rbc", "xbc"), z(row, "rcc", "xcc") } });
	}

	private static Complex z(Map<String, String> row, String rColumn, String xColumn) {
		return new Complex(number(row, rColumn, 0.0), number(row, xColumn, 0.0));
	}

	private static Complex pq(Map<String, String> row, String pColumn, String qColumn) {
		return new Complex(number(row, pColumn, 0.0), number(row, qColumn, 0.0));
	}

	private static String swingBusId(List<DistOpfBusData> buses) {
		for (DistOpfBusData bus : buses) {
			if (bus.isSwing()) {
				return bus.getId();
			}
		}
		throw new IllegalArgumentException("DistOPF CSV model does not define a swing bus");
	}

	private static List<Map<String, String>> rows(Path path) throws IOException {
		List<Map<String, String>> rows = new ArrayList<Map<String, String>>();
		try (BufferedReader reader = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
			String headerLine = reader.readLine();
			if (headerLine == null) {
				return rows;
			}
			String[] headers = headerLine.split(",", -1);
			String line = reader.readLine();
			while (line != null) {
				if (line.trim().length() > 0) {
					String[] values = line.split(",", -1);
					Map<String, String> row = new LinkedHashMap<String, String>();
					for (int i = 0; i < headers.length; i++) {
						row.put(headers[i], i < values.length ? values[i] : "");
					}
					rows.add(row);
				}
				line = reader.readLine();
			}
		}
		return rows;
	}

	private static Set<PhaseCode> phases(String value) {
		EnumSet<PhaseCode> phases = EnumSet.noneOf(PhaseCode.class);
		String normalized = value == null ? "" : value.toLowerCase();
		if (normalized.contains("a")) {
			phases.add(PhaseCode.A);
		}
		if (normalized.contains("b")) {
			phases.add(PhaseCode.B);
		}
		if (normalized.contains("c")) {
			phases.add(PhaseCode.C);
		}
		return phases;
	}

	private static Double numberObject(Map<String, String> row, String column) {
		String value = value(row, column);
		return value == null || value.length() == 0 ? null : Double.valueOf(value);
	}

	private static double number(Map<String, String> row, String column, double defaultValue) {
		String value = value(row, column);
		return value == null || value.length() == 0 ? defaultValue : Double.parseDouble(value);
	}

	private static String value(Map<String, String> row, String column) {
		String value = row.get(column);
		return value == null ? null : value.trim();
	}

	private static String key(String fromBusId, String toBusId) {
		return fromBusId + "->" + toBusId;
	}

	private static void addAdjacent(Map<String, List<DistOpfBranchData>> adjacency,
			String busId, DistOpfBranchData branch) {
		List<DistOpfBranchData> branches = adjacency.get(busId);
		if (branches == null) {
			branches = new ArrayList<DistOpfBranchData>();
			adjacency.put(busId, branches);
		}
		branches.add(branch);
	}

	private static void addChild(Map<String, List<DistOpfBranchData>> childrenByBusId,
			String busId, DistOpfBranchData branch) {
		List<DistOpfBranchData> children = childrenByBusId.get(busId);
		if (children == null) {
			children = new ArrayList<DistOpfBranchData>();
			childrenByBusId.put(busId, children);
		}
		children.add(branch);
	}
}
