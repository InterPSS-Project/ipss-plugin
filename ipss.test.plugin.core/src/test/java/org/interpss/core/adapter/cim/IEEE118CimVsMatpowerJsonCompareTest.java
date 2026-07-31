package org.interpss.core.adapter.cim;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;

import org.interpss.CorePluginFactory;
import org.interpss.CorePluginTestSetup;
import org.interpss.fadapter.IpssFileAdapter;
import org.interpss.fadapter.cim.CIMDirectParser;
import org.interpss.util.AclfNetJsonComparator;
import org.junit.jupiter.api.Test;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonPrimitive;
import com.interpss.core.aclf.AclfNetwork;
import com.interpss.state.aclf.AclfNetworkState;

/**
 * Diagnostic comparison of IEEE118 imported via CIM vs MatPower.
 * Remaps CIM bus UUID ids to MatPower-style {@code BusN} using IdentifiedObject.name
 * before {@link AclfNetJsonComparator} so diffs focus on electrical data.
 * Does not assert JSON equality — prints the comparator output.
 */
public class IEEE118CimVsMatpowerJsonCompareTest extends CorePluginTestSetup {

	private static final String TD = "testData/adpter/cim/";
	private static final String CIM_FILE = TD + "IEEE118_CIM.xml";
	private static final String MATPOWER_FILE = TD + "IEEE118.m";

	private static final Predicate<String> META_FILTER = path ->
			!path.contains("timeStamp")
					&& !path.equals("/originalDataFormat")
					&& !path.equals("/id")
					&& !path.equals("/name")
					&& !path.equals("/desc")
					&& !path.endsWith("/extUID")
					&& !path.equals("/deviceUIDType")
					&& !path.equals("/nodeBreakerModel")
					&& !path.equals("/statusChangeInfo");

	@Test
	public void compareCimVsMatpowerJson() throws Exception {
		AclfNetwork cimNet = new CIMDirectParser().parse(CIM_FILE);
		AclfNetwork matNet = CorePluginFactory
				.getFileAdapter(IpssFileAdapter.FileFormat.MATPOWER)
				.load(MATPOWER_FILE)
				.getAclfNet();

		assertNotNull(cimNet);
		assertNotNull(matNet);
		assertEquals(193, cimNet.getNoBus(), "CIM bus count");
		assertEquals(193, matNet.getNoBus(), "MatPower bus count");
		assertEquals(matNet.getNoBranch(), cimNet.getNoBranch(),
				"Branch counts should match after both imports");

		System.out.println("=== Topology summary ===");
		System.out.println("CIM:      buses=" + cimNet.getNoBus()
				+ " branches=" + cimNet.getNoBranch());
		System.out.println("MatPower: buses=" + matNet.getNoBus()
				+ " branches=" + matNet.getNoBranch());

		String cimJson = normalizeCimBusIds(new AclfNetworkState(cimNet).toString());
		String matJson = new AclfNetworkState(matNet).toString();

		boolean equal = new AclfNetJsonComparator("IEEE118 CIM vs MatPower", META_FILTER)
				.compareJson(cimJson, matJson);

		System.out.println("=== Compare result: " + (equal ? "EQUAL" : "DIFFERENCES FOUND") + " ===");
	}

	/**
	 * Remap CIM bus UUID ids to {@code Bus} + numeric {@code name}, update bus
	 * {@code number}/{@code name}, and rewrite all JSON string values that embed
	 * those UUIDs (branch ids, gen/load refs, etc.).
	 */
	static String normalizeCimBusIds(String cimJson) {
		JsonObject root = JsonParser.parseString(cimJson).getAsJsonObject();
		JsonArray busAry = root.getAsJsonArray("busAry");
		if (busAry == null) {
			return cimJson;
		}

		Map<String, String> idMap = new HashMap<>();
		for (JsonElement el : busAry) {
			JsonObject bus = el.getAsJsonObject();
			String oldId = bus.get("id").getAsString();
			String name = bus.has("name") && !bus.get("name").isJsonNull()
					? bus.get("name").getAsString().trim()
					: null;
			if (name != null && name.matches("\\d+")) {
				String newId = "Bus" + name;
				idMap.put(oldId, newId);
				bus.addProperty("id", newId);
				bus.addProperty("name", newId);
				bus.addProperty("number", Integer.parseInt(name));
			}
		}

		// CIM overrides branch id to equipment UUID; name is like "1_2_1" or "64_61_0_1"
		JsonArray branchAry = root.getAsJsonArray("branchAry");
		if (branchAry != null) {
			for (JsonElement el : branchAry) {
				JsonObject br = el.getAsJsonObject();
				String name = br.has("name") && !br.get("name").isJsonNull()
						? br.get("name").getAsString().trim()
						: null;
				String mapped = mapCimBranchNameToMatpowerId(name);
				if (mapped != null) {
					String oldId = br.get("id").getAsString();
					idMap.put(oldId, mapped);
					br.addProperty("id", mapped);
					br.addProperty("name", mapped.replace("->", "_to_").replace("(", "_cirId_").replace(")", ""));
				}
			}
		}

		// Contribute load/gen ids: MatPower uses "1", "2", …; CIM uses UUIDs — normalize by order
		normalizeContributeIds(busAry);

		if (idMap.isEmpty()) {
			return root.toString();
		}

		// Longest-first so overlapping substrings cannot occur incorrectly
		List<Map.Entry<String, String>> replacements = new ArrayList<>(idMap.entrySet());
		replacements.sort(Comparator.comparingInt((Map.Entry<String, String> e) -> e.getKey().length()).reversed());

		rewriteStringValues(root, replacements);
		return root.toString();
	}

	/** Map CIM branch name {@code f_t_c} or {@code f_t_0_c} → {@code Busf->Bust(c)}. */
	static String mapCimBranchNameToMatpowerId(String name) {
		if (name == null) {
			return null;
		}
		if (name.matches("\\d+_\\d+_\\d+")) {
			String[] p = name.split("_");
			return "Bus" + p[0] + "->" + "Bus" + p[1] + "(" + p[2] + ")";
		}
		if (name.matches("\\d+_\\d+_0_\\d+")) {
			String[] p = name.split("_");
			return "Bus" + p[0] + "->" + "Bus" + p[1] + "(" + p[3] + ")";
		}
		return null;
	}

	private static void normalizeContributeIds(JsonArray busAry) {
		for (JsonElement el : busAry) {
			JsonObject bus = el.getAsJsonObject();
			renumberArrayIds(bus.getAsJsonArray("loadAry"));
			renumberArrayIds(bus.getAsJsonArray("genAry"));
		}
	}

	private static void renumberArrayIds(JsonArray ary) {
		if (ary == null) {
			return;
		}
		for (int i = 0; i < ary.size(); i++) {
			JsonObject obj = ary.get(i).getAsJsonObject();
			String newId = String.valueOf(i + 1);
			obj.addProperty("id", newId);
			obj.addProperty("name", newId);
		}
	}

	private static void rewriteStringValues(JsonElement el, List<Map.Entry<String, String>> replacements) {
		if (el == null || el.isJsonNull()) {
			return;
		}
		if (el.isJsonObject()) {
			JsonObject obj = el.getAsJsonObject();
			for (Map.Entry<String, JsonElement> e : obj.entrySet()) {
				JsonElement child = e.getValue();
				if (child.isJsonPrimitive() && child.getAsJsonPrimitive().isString()) {
					String s = child.getAsString();
					String rewritten = applyReplacements(s, replacements);
					if (!rewritten.equals(s)) {
						obj.addProperty(e.getKey(), rewritten);
					}
				} else {
					rewriteStringValues(child, replacements);
				}
			}
		} else if (el.isJsonArray()) {
			JsonArray arr = el.getAsJsonArray();
			for (int i = 0; i < arr.size(); i++) {
				JsonElement child = arr.get(i);
				if (child.isJsonPrimitive() && child.getAsJsonPrimitive().isString()) {
					String s = child.getAsString();
					String rewritten = applyReplacements(s, replacements);
					if (!rewritten.equals(s)) {
						arr.set(i, new JsonPrimitive(rewritten));
					}
				} else {
					rewriteStringValues(child, replacements);
				}
			}
		}
	}

	private static String applyReplacements(String s, List<Map.Entry<String, String>> replacements) {
		String out = s;
		for (Map.Entry<String, String> r : replacements) {
			if (out.contains(r.getKey())) {
				out = out.replace(r.getKey(), r.getValue());
			}
		}
		return out;
	}
}
