/*
 * Copyright (C) 2006-2026 www.interpss.org
 */

package org.interpss.fadapter.psse.export;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.StringJoiner;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.interpss.core.aclf.BaseAclfNetwork;
import com.interpss.core.algo.LoadflowAlgorithmInitializer;

import org.interpss.fadapter.psse.PsseLoadflowSolutionSettings;

/**
 * Standalone PSS/E RAW text exporter for networks produced by the direct PSS/E
 * parsers.
 *
 * <p>The exporter serializes the canonical model built by
 * {@link PSSEJsonExporter}; it does not make a second set of solved-state
 * decisions. Version-specific writers adapt section order and columns for
 * v30-v36. System-wide source records are preserved where supported, and bus
 * voltage magnitude/angle use the shared high-precision numeric formatter so a
 * solved case can satisfy the iteration-zero mismatch gate after re-import.</p>
 *
 * <p>Optional LCC/VSC solved diagnostics are appended as trailing comments.
 * PSS/E-compatible readers ignore those comments, while InterPSS users retain
 * firing/extinction angle, tap position, and terminal P/Q evidence for audit or
 * future initialization.</p>
 */
public class PSSERawExporter {
	private final JsonObject network;
	private final int version;
	private final double baseMva;
	private final List<String> systemWideDataLines;

	public PSSERawExporter(BaseAclfNetwork<?, ?> aclfNet, int version) {
		this(aclfNet, version, false);
	}

	public PSSERawExporter(BaseAclfNetwork<?, ?> aclfNet, int version, boolean exportSolvedState) {
		if (version < 30 || version > 36) {
			throw new IllegalArgumentException("RAW export supports PSSE v30-v36");
		}
		this.version = version;
		Object initializer = aclfNet.getExtraInfo().get(
				LoadflowAlgorithmInitializer.NETWORK_EXTRA_INFO_KEY);
		this.systemWideDataLines = initializer instanceof PsseLoadflowSolutionSettings settings
				? settings.rawLines() : List.of();
		this.network = new PSSEJsonExporter(aclfNet, exportSolvedState).export().getAsJsonObject("network");
		this.baseMva = firstDataRow("caseid").get(1).getAsDouble();
	}

	public void export(Path path) throws IOException {
		Files.writeString(path, exportToString());
	}

	public void export(String filename) throws IOException {
		export(Path.of(filename));
	}

	public String exportToString() {
		StringBuilder out = new StringBuilder();
		out.append("0, ").append(n(baseMva)).append(", ").append(version)
				.append(", 0, 1, 60.0 / PSS/E RAW exported by InterPSS\n");
		out.append("InterPSS RAW export\n");
		out.append("Exported from ACLF network\n");
		if (version == 30) {
			writeV30Sections(out);
			out.append("Q\n");
			return out.toString();
		}
		if (version >= 34) {
			for (String line : systemWideDataLines) {
				out.append(line).append('\n');
			}
			end(out, "SYSTEM-WIDE DATA", "BUS DATA");
		}

		writeSimpleSection(out, "bus");
		writeSimpleSection(out, "load");
		writeSimpleSection(out, "fixshunt");
		if (version >= 36) end(out, "FIXED SHUNT DATA", "GENERATOR OWNER DATA");
		writeGeneratorSection(out);
		if (version >= 36) end(out, "GENERATOR DATA", "GENERATOR IMPEDANCE DATA");
		if (version < 34) {
			writeV30AcLineSection(out);
		} else {
			writeSimpleSection(out, "acline");
			writeSystemSwitchingDevices(out);
		}
		writeTransformerSection(out);
		writeSimpleSection(out, "area");
		writeTwoTerminalDcSection(out);
		end(out, "TWO-TERMINAL DC DATA", "VSC DC LINE DATA");
		writeVscDcSection(out);
		end(out, "VSC DC LINE DATA", "IMPEDANCE CORRECTION DATA");
		writeImpedanceCorrectionSection(out);
		end(out, "MULTI-TERMINAL DC DATA", "MULTI-SECTION LINE DATA");
		end(out, "MULTI-SECTION LINE DATA", "ZONE DATA");
		writeSimpleSection(out, "zone");
		end(out, "INTER-AREA TRANSFER DATA", "OWNER DATA");
		writeSimpleSection(out, "owner");
		writeSimpleSection(out, "facts");
		writeSwitchedShuntSection(out);
		if (version >= 33) {
			end(out, "SWITCHED SHUNT DATA", "GNE DATA");
			end(out, "GNE DATA", "INDUCTION MACHINE DATA");
		}
		if (version >= 36) {
			end(out, "INDUCTION MACHINE DATA", "LOAD TYPE DATA");
			end(out, "LOAD TYPE DATA", "INTERFACE DATA");
			end(out, "INTERFACE DATA", "INTERFACE ELEMENT DATA");
			end(out, "INTERFACE ELEMENT DATA", "SUBSTATION DATA");
		}
		out.append("Q\n");
		return out.toString();
	}

	private void writeV30Sections(StringBuilder out) {
		writeV30BusSection(out);
		writeV30LoadSection(out);
		writeGeneratorSection(out);
		writeV30AcLineSection(out);
		writeTransformerSection(out);
		writeSimpleSection(out, "area");
		writeTwoTerminalDcSection(out);
		end(out, "TWO-TERMINAL DC DATA", "VSC DC LINE DATA");
		writeVscDcSection(out);
		end(out, "VSC DC LINE DATA", "SWITCHED SHUNT DATA");
		writeSwitchedShuntSection(out);
		end(out, "SWITCHED SHUNT DATA", "TRANS. IMP. CORR. TABLE DATA");
		writeImpedanceCorrectionSection(out);
		end(out, "MULTI-TERMINAL DC DATA", "MULTI-SECTION LINE DATA");
		end(out, "MULTI-SECTION LINE DATA", "ZONE DATA");
		writeSimpleSection(out, "zone");
		end(out, "INTER-AREA TRANSFER DATA", "OWNER DATA");
		writeSimpleSection(out, "owner");
		for (JsonArray row : rawRows("facts")) {
			out.append(csv(jsonValues(row))).append('\n');
		}
		out.append("0 / END OF FACTS DEVICE DATA, END OF CASE DATA\n");
	}

	private void writeV30BusSection(StringBuilder out) {
		Map<Integer, double[]> fixedShunts = new HashMap<>();
		for (Map<String, JsonElement> row : rows("fixshunt")) {
			if (intValue(row.get("stat")) != 1) {
				continue;
			}
			double[] shunt = fixedShunts.computeIfAbsent(
					intValue(row.get("ibus")), ignored -> new double[2]);
			shunt[0] += doubleValue(row.get("gl"));
			shunt[1] += doubleValue(row.get("bl"));
		}
		for (Map<String, JsonElement> row : rows("bus")) {
			double[] shunt = fixedShunts.getOrDefault(
					intValue(row.get("ibus")), new double[2]);
			out.append(csv(List.of(
					row.get("ibus"), row.get("name"), row.get("baskv"), row.get("ide"),
					shunt[0], shunt[1], row.get("area"), row.get("zone"),
					row.get("vm"), row.get("va"), row.get("owner")))).append('\n');
		}
		end(out, "BUS DATA", "LOAD DATA");
	}

	private void writeV30LoadSection(StringBuilder out) {
		for (Map<String, JsonElement> row : rows("load")) {
			out.append(csv(List.of(
					row.get("ibus"), row.get("loadid"), row.get("stat"), row.get("area"),
					row.get("zone"), row.get("pl"), row.get("ql"), row.get("ip"),
					row.get("iq"), row.get("yp"), row.get("yq"), row.get("owner"))))
					.append('\n');
		}
		end(out, "LOAD DATA", "GENERATOR DATA");
	}

	private void writeV30AcLineSection(StringBuilder out) {
		for (Map<String, JsonElement> row : rows("acline")) {
			List<Object> values = new ArrayList<>(List.of(
					row.get("ibus"), row.get("jbus"), row.get("ckt"), row.get("rpu"),
					row.get("xpu"), row.get("bpu"), row.get("rate1"), row.get("rate2"),
					row.get("rate3"), row.get("gi"), row.get("bi"), row.get("gj"),
					row.get("bj"), row.get("stat")));
			if (version >= 31) {
				values.add(row.get("met"));
			}
			values.add(row.get("len"));
			values.add(row.get("o1"));
			values.add(row.get("f1"));
			out.append(csv(values)).append('\n');
		}
		end(out, "NON-TRANSFORMER BRANCH DATA", "TRANSFORMER DATA");
	}

	private void writeSimpleSection(StringBuilder out, String sectionName) {
		for (JsonArray row : rawRows(sectionName)) {
			out.append(csv(jsonValues(row))).append('\n');
		}
		end(out, sectionName.toUpperCase() + " DATA", nextSectionName(sectionName));
	}

	private void writeGeneratorSection(StringBuilder out) {
		for (Map<String, JsonElement> row : rows("generator")) {
			if (version >= 35) {
				out.append(csv(List.of(
						row.get("ibus"), row.get("machid"), row.get("pg"), row.get("qg"),
						row.get("qt"), row.get("qb"), row.get("vs"), row.get("ireg"), row.get("nreg"),
						row.get("mbase"), row.get("zr"), row.get("zx"), row.get("rt"), row.get("xt"),
						row.get("gtap"), row.get("stat"), row.get("rmpct"), row.get("pt"), row.get("pb"),
						row.get("baslod"), row.get("o1"), row.get("f1"), row.get("o2"), row.get("f2"),
						row.get("o3"), row.get("f3"), row.get("o4"), row.get("f4"), row.get("wmod"),
						row.get("wpf")))).append('\n');
			} else if (version >= 32) {
				out.append(csv(List.of(
						row.get("ibus"), row.get("machid"), row.get("pg"), row.get("qg"),
						row.get("qt"), row.get("qb"), row.get("vs"), row.get("ireg"),
						row.get("mbase"), row.get("zr"), row.get("zx"), row.get("rt"), row.get("xt"),
						row.get("gtap"), row.get("stat"), row.get("rmpct"), row.get("pt"), row.get("pb"),
						row.get("o1"), row.get("f1"), row.get("o2"), row.get("f2"),
						row.get("o3"), row.get("f3"), row.get("o4"), row.get("f4"), row.get("wmod"),
						row.get("wpf")))).append('\n');
			} else {
				out.append(csv(List.of(
						row.get("ibus"), row.get("machid"), row.get("pg"), row.get("qg"),
						row.get("qt"), row.get("qb"), row.get("vs"), row.get("ireg"),
						row.get("mbase"), row.get("zr"), row.get("zx"), row.get("rt"), row.get("xt"),
						row.get("gtap"), row.get("stat"), row.get("rmpct"), row.get("pt"), row.get("pb"),
						row.get("o1"), row.get("f1"), row.get("o2"), row.get("f2"),
						row.get("o3"), row.get("f3"), row.get("o4"), row.get("f4"))))
						.append('\n');
			}
		}
		end(out, "GENERATOR DATA", "BRANCH DATA");
	}

	private void writeSystemSwitchingDevices(StringBuilder out) {
		for (Map<String, JsonElement> row : rows("sysswd")) {
			out.append(csv(List.of(
					row.get("ibus"), row.get("jbus"), row.get("ckt"), row.get("xpu"),
					"DEFAULT", row.get("stat"), row.get("stat"), 1, row.get("stype"), row.get("name"))))
					.append('\n');
		}
		end(out, "SYSTEM SWITCHING DEVICE DATA", "TRANSFORMER DATA");
	}

	private void writeTransformerSection(StringBuilder out) {
		for (Map<String, JsonElement> row : rows("transformer")) {
			out.append(csv(List.of(row.get("ibus"), row.get("jbus"), row.get("kbus"), row.get("ckt"),
					row.get("cw"), row.get("cz"), row.get("cm"), row.get("mag1"), row.get("mag2"),
					row.get("nmet"), row.get("name"), row.get("stat"),
					row.get("o1"), row.get("f1"), row.get("o2"), row.get("f2"),
					row.get("o3"), row.get("f3"), row.get("o4"), row.get("f4"),
					row.get("vecgrp")))).append('\n');
			out.append(csv(List.of(row.get("r1_2"), row.get("x1_2"), row.get("sbase1_2"),
					row.get("r2_3"), row.get("x2_3"), row.get("sbase2_3"),
					row.get("r3_1"), row.get("x3_1"), row.get("sbase3_1"),
					row.get("vmstar"), row.get("anstar")))).append('\n');
			writeTransformerWinding(out, row, "1");
			writeTransformerWinding(out, row, "2");
			if (intValue(row.get("kbus")) != 0) {
				writeTransformerWinding(out, row, "3");
			}
		}
		end(out, "TRANSFORMER DATA", "AREA DATA");
	}

	private void writeImpedanceCorrectionSection(StringBuilder out) {
		Map<Integer, List<Map<String, JsonElement>>> tables = new LinkedHashMap<>();
		for (Map<String, JsonElement> row : rows("impcor")) {
			int tableNumber = intValue(row.get("itable"));
			if (tableNumber > 0) {
				tables.computeIfAbsent(tableNumber, ignored -> new ArrayList<>()).add(row);
			}
		}
		for (Map.Entry<Integer, List<Map<String, JsonElement>>> entry : tables.entrySet()) {
			List<Object> values = new ArrayList<>();
			values.add(entry.getKey());
			for (Map<String, JsonElement> point : entry.getValue()) {
				values.add(point.get("tap"));
				values.add(point.get("refact"));
				if (version >= 34) {
					values.add(point.get("imfact"));
				}
			}
			values.add(0.0);
			values.add(0.0);
			if (version >= 34) {
				values.add(0.0);
			}
			out.append(csv(values)).append('\n');
		}
		end(out, "IMPEDANCE CORRECTION DATA", "MULTI-TERMINAL DC DATA");
	}

	private void writeTransformerWinding(StringBuilder out, Map<String, JsonElement> row, String winding) {
		List<Object> values = new ArrayList<>();
		values.add(row.get("windv" + winding));
		values.add(row.get("nomv" + winding));
		values.add(row.get("ang" + winding));
		values.add(row.get("wdg1rate1"));
		values.add(row.get("wdg1rate2"));
		values.add(row.get("wdg1rate3"));
		if (version == 30) {
			values.add(winding.equals("1") ? row.get("cod1") : 0);
			values.add(winding.equals("1") ? row.get("cont1") : 0);
			values.add(winding.equals("1") ? row.get("rma1") : 1.1);
			values.add(winding.equals("1") ? row.get("rmi1") : 0.9);
			values.add(winding.equals("1") ? row.get("vma1") : 1.1);
			values.add(winding.equals("1") ? row.get("vmi1") : 0.9);
			values.add(winding.equals("1") ? row.get("ntp1") : 33);
			values.add(row.get("tab" + winding));
			values.add(0);
			values.add(0);
			out.append(csv(values)).append('\n');
			return;
		}
		for (int i = 4; i <= 12; i++) values.add(0.0);
		values.add(winding.equals("1") ? row.get("cod1") : 0);
		values.add(winding.equals("1") ? row.get("cont1") : 0);
		if (version >= 35) values.add(0);
		values.add(winding.equals("1") ? row.get("rma1") : 1.1);
		values.add(winding.equals("1") ? row.get("rmi1") : 0.9);
		values.add(winding.equals("1") ? row.get("vma1") : 1.1);
		values.add(winding.equals("1") ? row.get("vmi1") : 0.9);
		values.add(winding.equals("1") ? row.get("ntp1") : 33);
		values.add(row.get("tab" + winding));
		values.add(0);
		values.add(0);
		values.add(0);
		out.append(csv(values)).append('\n');
	}

	private void writeTwoTerminalDcSection(StringBuilder out) {
		for (Map<String, JsonElement> row : rows("twotermdc")) {
			out.append(csv(List.of(row.get("name"), row.get("mdc"), row.get("rdc"), row.get("setvl"),
					row.get("vschd"), row.get("vcmod"), row.get("rcomp"), row.get("delti"),
					row.get("met"), row.get("dcvmin"), row.get("cccitmx"), row.get("cccacc"))))
					.append('\n');
			writeTwoTerminalDcConverter(out, row, "r");
			writeTwoTerminalDcConverter(out, row, "i");
		}
	}

	private void writeTwoTerminalDcConverter(
			StringBuilder out,
			Map<String, JsonElement> row,
			String terminal) {
		String bus = terminal.equals("r") ? "ipr" : "ipi";
		String bridges = terminal.equals("r") ? "nbr" : "nbi";
		String maxAngle = terminal.equals("r") ? "anmxr" : "anmxi";
		String minAngle = terminal.equals("r") ? "anmnr" : "anmni";
		List<Object> values = new ArrayList<>(List.of(
				row.get(bus), row.get(bridges), row.get(maxAngle), row.get(minAngle),
				row.get("rc" + terminal), row.get("xc" + terminal), row.get("ebas" + terminal),
				row.get("tr" + terminal), row.get("tap" + terminal),
				row.get("tmx" + terminal), row.get("tmn" + terminal), row.get("stp" + terminal),
				row.get("ic" + terminal)));
		if (version >= 35) {
			values.add(row.get("nd" + terminal));
		}
		values.add(row.get("if" + terminal));
		values.add(row.get("it" + terminal));
		values.add(row.get("id" + terminal));
		values.add(row.get("xcap" + terminal));
		if (version < 35) {
			values.add(row.get("nd" + terminal));
		}
		out.append(csv(values));
		appendLccSolvedStateComment(out, row, terminal);
		out.append('\n');
	}

	private static void appendLccSolvedStateComment(StringBuilder out,
			Map<String, JsonElement> row, String terminal) {
		JsonElement angle = row.get(terminal.equals("r")
				? "ipss_alpha_r_deg" : "ipss_gamma_i_deg");
		if (!hasValue(angle)) {
			return;
		}
		out.append(" /* [IPSS_SOLVED_STATE, ")
				.append(terminal.equals("r")
						? "firing_angle_deg=" : "extinction_angle_deg=")
				.append(rawValue(angle))
				.append(", tap_ratio=").append(rawValue(row.get("tap" + terminal)))
				.append(", discrete_tap_position=")
				.append(rawValue(row.get("ipss_tap_pos_" + terminal)))
				.append(", p_into_converter_pu=")
				.append(rawValue(row.get("ipss_p_into_converter_" + terminal + "_pu")))
				.append(", q_into_converter_pu=")
				.append(rawValue(row.get("ipss_q_into_converter_" + terminal + "_pu")))
				.append("] */");
	}

	private void writeVscDcSection(StringBuilder out) {
		for (Map<String, JsonElement> row : rows("vscdc")) {
			out.append(csv(List.of(row.get("name"), row.get("mdc"), row.get("rdc"),
					row.get("o1"), row.get("f1"), row.get("o2"), row.get("f2"),
					row.get("o3"), row.get("f3"), row.get("o4"), row.get("f4"))))
					.append('\n');
			writeVscConverter(out, row, "1");
			writeVscConverter(out, row, "2");
		}
	}

	private void writeVscConverter(StringBuilder out, Map<String, JsonElement> row, String suffix) {
		out.append(csv(List.of(
				row.get("ibus" + suffix), row.get("type" + suffix), row.get("mode" + suffix),
				row.get("dcset" + suffix), row.get("acset" + suffix),
				row.get("aloss" + suffix), row.get("bloss" + suffix),
				row.get("minloss" + suffix), row.get("smax" + suffix),
				row.get("imax" + suffix), row.get("pwf" + suffix),
				row.get("maxq" + suffix), row.get("minq" + suffix),
				row.get("vsreg" + suffix), row.get("nreg" + suffix),
				row.get("rmpct" + suffix))));
		appendVscSolvedStateComment(out, row, suffix);
		out.append('\n');
	}

	private static void appendVscSolvedStateComment(StringBuilder out,
			Map<String, JsonElement> row, String suffix) {
		JsonElement p = row.get("ipss_p_into_converter_" + suffix + "_pu");
		if (!hasValue(p)) {
			return;
		}
		out.append(" /* [IPSS_SOLVED_STATE, terminal=").append(suffix)
				.append(", p_into_converter_pu=").append(rawValue(p))
				.append(", q_into_converter_pu=")
				.append(rawValue(row.get("ipss_q_into_converter_" + suffix + "_pu")))
				.append("] */");
	}

	private void writeSwitchedShuntSection(StringBuilder out) {
		for (Map<String, JsonElement> row : rows("swshunt")) {
			List<Object> values = new ArrayList<>();
			values.add(row.get("ibus"));
			if (version >= 35) {
				values.add(row.get("shntid"));
				values.add(row.get("modsw"));
				values.add(row.get("adjm"));
				values.add(row.get("stat"));
				values.add(row.get("vswhi"));
				values.add(row.get("vswlo"));
				values.add(row.get("swreg"));
				values.add(row.get("nreg"));
				values.add(row.get("rmpct"));
				values.add(row.get("rmidnt"));
				values.add(row.get("binit"));
				if (version >= 36) values.add("");
				for (int i = 1; i <= 8; i++) {
					values.add(row.get("s" + i));
					values.add(row.get("n" + i));
					values.add(row.get("b" + i));
				}
			} else if (version >= 33) {
				values.add(row.get("modsw"));
				values.add(row.get("adjm"));
				values.add(row.get("stat"));
				values.add(row.get("vswhi"));
				values.add(row.get("vswlo"));
				values.add(row.get("swreg"));
				values.add(row.get("rmpct"));
				values.add(row.get("rmidnt"));
				values.add(row.get("binit"));
				for (int i = 1; i <= 8; i++) {
					values.add(row.get("n" + i));
					values.add(row.get("b" + i));
				}
				values.add(row.get("nreg"));
			} else {
				values.add(row.get("modsw"));
				values.add(row.get("vswhi"));
				values.add(row.get("vswlo"));
				values.add(row.get("swreg"));
				values.add(row.get("rmpct"));
				values.add(row.get("rmidnt"));
				values.add(row.get("binit"));
				for (int i = 1; i <= 8; i++) {
					values.add(row.get("n" + i));
					values.add(row.get("b" + i));
				}
			}
			out.append(csv(values)).append('\n');
		}
	}

	private List<Map<String, JsonElement>> rows(String sectionName) {
		List<Map<String, JsonElement>> result = new ArrayList<>();
		JsonObject section = network.getAsJsonObject(sectionName);
		if (section == null) return result;
		JsonArray fields = section.getAsJsonArray("fields");
		JsonArray data = section.getAsJsonArray("data");
		if (fields == null || data == null) return result;
		for (JsonElement rowElement : data) {
			JsonArray row = rowElement.getAsJsonArray();
			Map<String, JsonElement> map = new HashMap<>();
			for (int i = 0; i < fields.size(); i++) {
				map.put(fields.get(i).getAsString().toLowerCase(),
						i < row.size() ? row.get(i) : null);
			}
			result.add(map);
		}
		return result;
	}

	private JsonArray firstDataRow(String sectionName) {
		return network.getAsJsonObject(sectionName).getAsJsonArray("data").get(0).getAsJsonArray();
	}

	private List<JsonArray> rawRows(String sectionName) {
		List<JsonArray> result = new ArrayList<>();
		JsonObject section = network.getAsJsonObject(sectionName);
		if (section == null) return result;
		JsonArray data = section.getAsJsonArray("data");
		if (data == null) return result;
		for (JsonElement rowElement : data) {
			result.add(rowElement.getAsJsonArray());
		}
		return result;
	}

	private static List<JsonElement> jsonValues(JsonArray row) {
		List<JsonElement> values = new ArrayList<>();
		row.forEach(values::add);
		return values;
	}

	private static String csv(List<?> values) {
		StringJoiner joiner = new StringJoiner(",");
		for (Object value : values) {
			joiner.add(rawValue(value));
		}
		return joiner.toString();
	}

	private static String rawValue(Object value) {
		if (value == null) return "";
		if (value instanceof JsonElement element) {
			if (element == null || element.isJsonNull()) return "";
			if (element.isJsonPrimitive() && element.getAsJsonPrimitive().isString()) {
				return q(element.getAsString());
			}
			return element.getAsString();
		}
		if (value instanceof String str) return q(str);
		if (value instanceof Byte || value instanceof Short
				|| value instanceof Integer || value instanceof Long) {
			return Long.toString(((Number) value).longValue());
		}
		if (value instanceof Number number) return n(number.doubleValue());
		return value.toString();
	}

	private static boolean hasValue(JsonElement value) {
		return value != null && !value.isJsonNull();
	}

	private static String q(String value) {
		return "'" + (value == null ? "" : value.replace("'", "''")) + "'";
	}

	private static String n(double value) {
		return Double.toString(value);
	}

	private static int intValue(JsonElement value) {
		if (value == null || value.isJsonNull()) return 0;
		return value.getAsInt();
	}

	private static double doubleValue(JsonElement value) {
		if (value == null || value.isJsonNull()) return 0.0;
		return value.getAsDouble();
	}

	private static void end(StringBuilder out, String current, String next) {
		out.append("0 / END OF ").append(current).append(", BEGIN ").append(next).append('\n');
	}

	private String nextSectionName(String sectionName) {
		return switch (sectionName) {
		case "bus" -> "LOAD DATA";
		case "load" -> "FIXED SHUNT DATA";
		case "fixshunt" -> "GENERATOR DATA";
		case "acline" -> version >= 34 ? "SYSTEM SWITCHING DEVICE DATA" : "TRANSFORMER DATA";
		case "area" -> "TWO-TERMINAL DC DATA";
		case "zone" -> "INTER-AREA TRANSFER DATA";
		case "owner" -> "FACTS DATA";
		case "facts" -> "SWITCHED SHUNT DATA";
		default -> "NEXT DATA";
		};
	}
}
