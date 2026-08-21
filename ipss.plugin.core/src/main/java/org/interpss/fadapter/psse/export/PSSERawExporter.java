/*
 * Copyright (C) 2006-2026 www.interpss.org
 */

package org.interpss.fadapter.psse.export;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.StringJoiner;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.interpss.core.aclf.BaseAclfNetwork;

/**
 * Standalone PSS/E RAW text exporter for networks produced by the direct PSS/E parsers.
 */
public class PSSERawExporter {
	private final JsonObject network;
	private final int version;
	private final double baseMva;

	public PSSERawExporter(BaseAclfNetwork<?, ?> aclfNet, int version) {
		if (version < 34 || version > 36) {
			throw new IllegalArgumentException("RAW export supports PSSE v34, v35 and v36");
		}
		this.version = version;
		this.network = new PSSEJsonExporter(aclfNet).export().getAsJsonObject("network");
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
		if (version >= 34) {
			end(out, "SYSTEM-WIDE DATA", "BUS DATA");
		}

		writeSimpleSection(out, "bus");
		writeSimpleSection(out, "load");
		writeSimpleSection(out, "fixshunt");
		if (version >= 36) end(out, "FIXED SHUNT DATA", "GENERATOR OWNER DATA");
		writeGeneratorSection(out);
		if (version >= 36) end(out, "GENERATOR DATA", "GENERATOR IMPEDANCE DATA");
		writeSimpleSection(out, "acline");
		writeSystemSwitchingDevices(out);
		writeTransformerSection(out);
		writeSimpleSection(out, "area");
		writeTwoTerminalDcSection(out);
		end(out, "TWO-TERMINAL DC DATA", "VSC DC LINE DATA");
		writeVscDcSection(out);
		end(out, "VSC DC LINE DATA", "IMPEDANCE CORRECTION DATA");
		end(out, "IMPEDANCE CORRECTION DATA", "MULTI-TERMINAL DC DATA");
		end(out, "MULTI-TERMINAL DC DATA", "MULTI-SECTION LINE DATA");
		writeSimpleSection(out, "zone");
		end(out, "ZONE DATA", "INTER-AREA TRANSFER DATA");
		writeSimpleSection(out, "owner");
		end(out, "OWNER DATA", "FACTS DATA");
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
			} else {
				out.append(csv(List.of(
						row.get("ibus"), row.get("machid"), row.get("pg"), row.get("qg"),
						row.get("qt"), row.get("qb"), row.get("vs"), row.get("ireg"),
						row.get("mbase"), row.get("zr"), row.get("zx"), row.get("rt"), row.get("xt"),
						row.get("gtap"), row.get("stat"), row.get("rmpct"), row.get("pt"), row.get("pb"),
						row.get("o1"), row.get("f1"), row.get("o2"), row.get("f2"),
						row.get("o3"), row.get("f3"), row.get("o4"), row.get("f4"), row.get("wmod"),
						row.get("wpf")))).append('\n');
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
					row.get("nmet"), row.get("name"), row.get("stat")))).append('\n');
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

	private void writeTransformerWinding(StringBuilder out, Map<String, JsonElement> row, String winding) {
		List<Object> values = new ArrayList<>();
		values.add(row.get("windv" + winding));
		values.add(row.get("nomv" + winding));
		values.add(row.get("ang" + winding));
		values.add(row.get("wdg1rate1"));
		values.add(row.get("wdg1rate2"));
		values.add(row.get("wdg1rate3"));
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
					row.get("vschd"), 0.0, row.get("rcomp"), row.get("delti"), row.get("met"))))
					.append('\n');
			out.append(csv(List.of(row.get("ipr"), row.get("nbr"), row.get("anmxr"), row.get("anmnr"),
					row.get("rcr"), row.get("xcr"), row.get("ebasr"), row.get("trr"), row.get("tapr"),
					row.get("tmxr"), row.get("tmnr"), row.get("stpr"), 0, 0, 0, 0, 0,
					row.get("xcapr")))).append('\n');
			out.append(csv(List.of(row.get("ipi"), row.get("nbi"), row.get("anmxi"), row.get("anmni"),
					row.get("rci"), row.get("xci"), row.get("ebasi"), row.get("tri"), row.get("tapi"),
					row.get("tmxi"), row.get("tmni"), row.get("stpi"), 0, 0, 0, 0, 0,
					row.get("xcapi")))).append('\n');
		}
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
				row.get("rmpct" + suffix)))).append('\n');
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
			} else {
				values.add(row.get("modsw"));
				values.add(row.get("adjm"));
				values.add(row.get("stat"));
				values.add(row.get("swreg"));
				values.add(row.get("vswhi"));
				values.add(row.get("vswlo"));
				values.add(row.get("binit"));
				values.add(row.get("rmpct"));
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
		if (value instanceof Number number) return n(number.doubleValue());
		return value.toString();
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

	private static void end(StringBuilder out, String current, String next) {
		out.append("0 / END OF ").append(current).append(", BEGIN ").append(next).append('\n');
	}

	private static String nextSectionName(String sectionName) {
		return switch (sectionName) {
		case "bus" -> "LOAD DATA";
		case "load" -> "FIXED SHUNT DATA";
		case "fixshunt" -> "GENERATOR DATA";
		case "acline" -> "SYSTEM SWITCHING DEVICE DATA";
		case "area" -> "TWO-TERMINAL DC DATA";
		case "zone" -> "INTER-AREA TRANSFER DATA";
		case "owner" -> "FACTS DATA";
		default -> "NEXT DATA";
		};
	}
}
