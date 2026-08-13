/*
 * @(#)PSSESubstationImporter.java
 *
 * Copyright (C) 2006-2026 www.interpss.org
 */

package org.interpss.fadapter.psse;

import java.io.BufferedReader;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

import org.interpss.fadapter.builder.AclfNetworkBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.interpss.common.exp.InterpssException;
import com.interpss.core.CoreObjectFactory;
import com.interpss.core.NBModelObjectFactory;
import com.interpss.core.aclf.Aclf3WBranch;
import com.interpss.core.aclf.AclfBranch;
import com.interpss.core.aclf.AclfNetwork;
import com.interpss.core.aclf.BaseAclfBus;
import com.interpss.core.aclf.adj.SwitchedShunt;
import com.interpss.core.net.Branch;
import com.interpss.core.net.NameTag;
import com.interpss.core.net.Substation;
import com.interpss.core.net.nb.NBEquipConnection;
import com.interpss.core.net.nb.NBModelEquipType;
import com.interpss.core.net.nb.NBModelSwitchType;
import com.interpss.core.net.nb.NBNode;
import com.interpss.core.net.nb.NBSwitch;

/**
 * Imports PSS/E Substation Data Group (node-breaker overlay) into
 * {@link Substation} / {@code com.interpss.core.net.nb} objects.
 * Does not create bus-branch breakers or alter the electrical network topology.
 * Supports nested RAW blocks and flat RAWX tables ({@code sub}/{@code subnode}/
 * {@code subswd}/{@code subterm}).
 */
public class PSSESubstationImporter {
	private static final Logger log = LoggerFactory.getLogger(PSSESubstationImporter.class);
	private static final String BUS_ID_PREFIX = "Bus";
	private static final double DEFAULT_SWITCH_XPU = 0.0001;

	private final AclfNetworkBuilder builder;

	public PSSESubstationImporter(AclfNetworkBuilder builder) {
		this.builder = builder;
	}

	/**
	 * Parse all substation blocks until a record with substation number 0 (or EOF / Q).
	 */
	public void parse(BufferedReader reader) throws IOException, InterpssException {
		String line;
		while ((line = readDataLine(reader)) != null) {
			if (PSSEDataRec.isEndRec(line)) {
				// Overall end of substation data (IS=0) or Q
				break;
			}
			PSSEDataRec header = new PSSEDataRec(line);
			int isub = header.getInt(0);
			if (isub <= 0) {
				break;
			}
			parseOneSubstation(reader, header, isub);
		}
	}

	/**
	 * Import flat RAWX tables {@code sub}, {@code subnode}, {@code subswd}, {@code subterm}.
	 * No-op when {@code sub} is absent.
	 */
	public void parseRawx(JsonObject network) throws InterpssException {
		if (network == null || !network.has("sub")) {
			return;
		}

		Map<Integer, Substation> substations = new HashMap<>();
		Map<Integer, Map<Integer, NBNode>> nodesBySub = new HashMap<>();

		forEachRawxRow(network, "sub", row -> {
			int isub = rawxInt(row, "isub", 0);
			if (isub <= 0) {
				return;
			}
			String name = rawxString(row, "name", "").trim();
			double lati = rawxDouble(row, "lati", 0.0);
			double longi = rawxDouble(row, "long", 0.0);
			double srg = rawxDouble(row, "srg", 0.1);
			Substation sub = createSubstation(isub, name, lati, longi, srg);
			substations.put(isub, sub);
			nodesBySub.put(isub, new HashMap<>());
		});

		forEachRawxRow(network, "subnode", row -> {
			int isub = rawxInt(row, "isub", 0);
			Substation sub = substations.get(isub);
			if (sub == null) {
				log.warn("RAWX subnode references missing substation {}", isub);
				return;
			}
			int ni = rawxInt(row, "inode", 0);
			if (ni <= 0) {
				return;
			}
			String nodeName = rawxString(row, "name", "").trim();
			int busNum = rawxInt(row, "ibus", 0);
			int status = rawxInt(row, "stat", 1);
			double vm = rawxDouble(row, "vm", 1.0);
			double va = rawxDouble(row, "va", 0.0);
			addNode(sub, isub, ni, nodeName, busNum, status, vm, va, nodesBySub.get(isub));
		});

		forEachRawxRow(network, "subswd", row -> {
			int isub = rawxInt(row, "isub", 0);
			Substation sub = substations.get(isub);
			Map<Integer, NBNode> nodesByNi = nodesBySub.get(isub);
			if (sub == null || nodesByNi == null) {
				log.warn("RAWX subswd references missing substation {}", isub);
				return;
			}
			int ni = rawxInt(row, "inode", 0);
			int nj = rawxInt(row, "jnode", 0);
			if (ni <= 0) {
				return;
			}
			String ckt = rawxString(row, "swdid", "1").trim();
			String swName = rawxString(row, "name", "").trim();
			int typeCode = rawxInt(row, "type", 1);
			int status = rawxInt(row, "stat", 1);
			int nstat = rawxInt(row, "nstat", 1);
			double xpu = rawxDouble(row, "xpu", DEFAULT_SWITCH_XPU);
			if (xpu == 0.0) {
				xpu = DEFAULT_SWITCH_XPU;
			}
			String rsetnam = rawxString(row, "rsetnam", "").trim();
			addSwitch(sub, isub, ni, nj, ckt, swName, typeCode, status, nstat, xpu, rsetnam, nodesByNi);
		});

		forEachRawxRow(network, "subterm", row -> {
			int isub = rawxInt(row, "isub", 0);
			Substation sub = substations.get(isub);
			Map<Integer, NBNode> nodesByNi = nodesBySub.get(isub);
			if (sub == null || nodesByNi == null) {
				log.warn("RAWX subterm references missing substation {}", isub);
				return;
			}
			int ni = rawxInt(row, "inode", 0);
			String typeCode = rawxString(row, "type", "").trim().toUpperCase();
			String eqId = rawxString(row, "eqid", "").trim();
			int busI = rawxInt(row, "ibus", 0);
			Integer jbus = rawxIntOrNull(row, "jbus");
			Integer kbus = rawxIntOrNull(row, "kbus");
			if (busI <= 0 || typeCode.isEmpty()) {
				return;
			}
			addTerminalRawx(sub, isub, ni, typeCode, eqId, busI, jbus, kbus, nodesByNi);
		});
	}

	private void parseOneSubstation(BufferedReader reader, PSSEDataRec header, int isub)
			throws IOException, InterpssException {
		String name = header.getString(1, "").trim();
		double lati = header.getDouble(2, 0.0);
		double longi = header.getDouble(3, 0.0);
		double srg = header.getDouble(4, 0.1);

		Substation sub = createSubstation(isub, name, lati, longi, srg);
		Map<Integer, NBNode> nodesByNi = new HashMap<>();
		parseNodes(reader, sub, isub, nodesByNi);
		parseSwitches(reader, sub, isub, nodesByNi);
		parseTerminals(reader, sub, isub, nodesByNi);
	}

	private Substation createSubstation(int isub, String name, double lati, double longi, double srg) {
		String resolvedName = (name == null || name.isEmpty()) ? "Substation " + isub : name;
		String subId = String.valueOf(isub);
		Substation sub = CoreObjectFactory.createSubstation();
		sub.setId(subId);
		sub.setName(resolvedName);
		sub.setNumber(isub);
		sub.setLatitude(lati);
		sub.setLongitude(longi);
		sub.setGroundingResistance(srg);
		builder.getNetwork().addSubstation(sub);
		builder.getNetwork().setNodeBreakerModel(true);
		return sub;
	}

	private void parseNodes(BufferedReader reader, Substation sub, int isub, Map<Integer, NBNode> nodesByNi)
			throws IOException {
		String line;
		while ((line = readDataLine(reader)) != null) {
			if (PSSEDataRec.isEndRec(line)) {
				break;
			}
			PSSEDataRec rec = new PSSEDataRec(line);
			int ni = rec.getInt(0);
			if (ni <= 0) {
				break;
			}
			String nodeName = rec.getString(1, "").trim();
			int busNum = rec.getInt(2);
			int status = rec.getInt(3, 1);
			double vm = rec.getDouble(4, 1.0);
			double va = rec.getDouble(5, 0.0);
			addNode(sub, isub, ni, nodeName, busNum, status, vm, va, nodesByNi);
		}
	}

	private void addNode(Substation sub, int isub, int ni, String nodeName, int busNum, int status,
			double vm, double va, Map<Integer, NBNode> nodesByNi) {
		String resolvedName = (nodeName == null || nodeName.isEmpty()) ? sub.getName() + " " + ni : nodeName;
		String busId = BUS_ID_PREFIX + busNum;
		BaseAclfBus bus = builder.getBus(busId);
		if (bus == null) {
			log.warn("Substation {}: node {} references missing bus {}", isub, ni, busId);
		} else if (bus.getSubstation() == null) {
			sub.addBus(bus);
			// 3W star bus follows from-bus substation (even if from-bus / nodes inactive).
			assign3WStarBusesToSubstation(bus, sub);
		}

		String nodeId = "NBNode_" + isub + "-" + ni + "@" + sub.getName();
		NBNode node = NBModelObjectFactory.createNBNode(sub, nodeId, bus);
		node.setName(resolvedName);
		node.setNumber(ni);
		node.setStatus(status == 1);
		node.setVoltageMag(vm);
		node.setVoltageAng(va);
		nodesByNi.put(ni, node);
	}

	private void parseSwitches(BufferedReader reader, Substation sub, int isub, Map<Integer, NBNode> nodesByNi)
			throws IOException {
		String line;
		while ((line = readDataLine(reader)) != null) {
			if (PSSEDataRec.isEndRec(line)) {
				break;
			}
			PSSEDataRec rec = new PSSEDataRec(line);
			int ni = rec.getInt(0);
			if (ni <= 0) {
				break;
			}
			int nj = rec.getInt(1);
			String ckt = rec.getString(2, "1").trim();
			String swName = rec.getString(3, "").trim();
			int typeCode = rec.getInt(4, 1);
			int status = rec.getInt(5, 1);
			int nstat = rec.getInt(6, 1);
			double xpu = rec.getDouble(7, DEFAULT_SWITCH_XPU);
			String rsetnam = "";
			// Optional rating-table name may follow numeric fields; skip pure numbers.
			if (rec.size() > 8) {
				String maybeName = rec.getString(8, "").trim();
				if (!maybeName.isEmpty() && !isNumericToken(maybeName)) {
					rsetnam = maybeName;
				} else if (rec.size() > 11) {
					String last = rec.getString(rec.size() - 1, "").trim();
					if (!last.isEmpty() && !isNumericToken(last)) {
						rsetnam = last;
					}
				}
			}
			addSwitch(sub, isub, ni, nj, ckt, swName, typeCode, status, nstat, xpu, rsetnam, nodesByNi);
		}
	}

	private void addSwitch(Substation sub, int isub, int ni, int nj, String ckt, String swName, int typeCode,
			int status, int nstat, double xpu, String rsetnam, Map<Integer, NBNode> nodesByNi) {
		String resolvedCkt = (ckt == null || ckt.isEmpty()) ? "1" : ckt.trim();
		NBNode from = nodesByNi.get(ni);
		NBNode to = nodesByNi.get(nj);
		if (from == null || to == null) {
			log.warn("Substation {}: switch {}-{} CKT={} references missing node(s)", isub, ni, nj, resolvedCkt);
			return;
		}

		NBModelSwitchType swType = mapSwitchType(typeCode);
		String swId = "NBSwitch_" + isub + "-" + ni + "-" + nj + "-" + resolvedCkt + "@" + sub.getName();
		NBSwitch sw = NBModelObjectFactory.createNBSwitch(sub, swId, from, to, resolvedCkt, swType);
		if (swName != null && !swName.isEmpty()) {
			sw.setName(swName);
		}
		sw.setCurrentStatus(status);
		sw.setNormalStatus(nstat);
		sw.setXpu(xpu);
		sw.setNameRating(rsetnam != null ? rsetnam : "");
	}

	private void parseTerminals(BufferedReader reader, Substation sub, int isub, Map<Integer, NBNode> nodesByNi)
			throws IOException {
		String line;
		while ((line = readDataLine(reader)) != null) {
			if (PSSEDataRec.isEndRec(line)) {
				break;
			}
			PSSEDataRec rec = new PSSEDataRec(line);
			int busI = rec.getInt(0);
			if (busI <= 0) {
				break;
			}
			int ni = rec.getInt(1);
			String typeCode = rec.getString(2, "").trim().toUpperCase();
			if (typeCode.isEmpty()) {
				log.warn("Substation {}: terminal at bus {} missing type code", isub, busI);
				continue;
			}

			char code = typeCode.charAt(0);
			String eqId;
			Integer jbus = null;
			Integer kbus = null;
			switch (code) {
			case 'L':
			case 'F':
			case 'M':
			case 'S':
			case 'I':
				eqId = rec.getString(3, "1").trim();
				break;
			case 'B':
			case '2':
				jbus = rec.getInt(3);
				eqId = rec.getString(4, "1").trim();
				break;
			case '3':
				jbus = rec.getInt(3);
				kbus = rec.getInt(4);
				eqId = rec.getString(5, "1").trim();
				break;
			case 'D':
			case 'V':
			case 'N':
			case 'A':
				eqId = rec.getString(3, "").trim();
				break;
			default:
				log.warn("Substation {}: unsupported terminal type '{}'", isub, typeCode);
				continue;
			}
			addTerminal(sub, isub, ni, typeCode, eqId, busI, jbus, kbus, nodesByNi);
		}
	}

	private void addTerminalRawx(Substation sub, int isub, int ni, String typeCode, String eqId, int busI,
			Integer jbus, Integer kbus, Map<Integer, NBNode> nodesByNi) {
		addTerminal(sub, isub, ni, typeCode, eqId, busI, jbus, kbus, nodesByNi);
	}

	private void addTerminal(Substation sub, int isub, int ni, String typeCode, String eqIdIn, int busI,
			Integer jbus, Integer kbus, Map<Integer, NBNode> nodesByNi) {
		NBNode node = null;
		if (ni > 0) {
			node = nodesByNi.get(ni);
			if (node == null) {
				log.warn("Substation {}: terminal type {} references missing node {}", isub, typeCode, ni);
			}
		}

		NBModelEquipType equipType = mapEquipType(typeCode);
		BaseAclfBus fromBus = builder.getBus(BUS_ID_PREFIX + busI);
		BaseAclfBus toBus = null;
		BaseAclfBus tertBus = null;
		NameTag equip = null;
		String eqId = eqIdIn;
		String connId;

		char code = typeCode.charAt(0);
		switch (code) {
		case 'L':
		case 'F':
		case 'M':
		case 'S':
		case 'I': {
			if (eqId == null || eqId.isEmpty()) {
				eqId = "1";
			}
			connId = isub + "-" + typeCode + "-" + busI + "-" + eqId;
			equip = resolveBusEquipment(fromBus, equipType, eqId);
			break;
		}
		case 'B':
		case '2': {
			int j = jbus != null ? jbus : 0;
			if (eqId == null || eqId.isEmpty()) {
				eqId = "1";
			}
			toBus = builder.getBus(BUS_ID_PREFIX + j);
			connId = isub + "-" + typeCode + "-" + busI + "-" + j + "-" + eqId;
			equip = resolveBranch(fromBus, toBus, eqId);
			break;
		}
		case '3': {
			int j = jbus != null ? jbus : 0;
			int k = kbus != null ? kbus : 0;
			if (eqId == null || eqId.isEmpty()) {
				eqId = "1";
			}
			toBus = builder.getBus(BUS_ID_PREFIX + j);
			tertBus = builder.getBus(BUS_ID_PREFIX + k);
			connId = isub + "-" + typeCode + "-" + busI + "-" + j + "-" + k + "-" + eqId;
			equip = resolve3WBranch(fromBus, toBus, tertBus, eqId);
			break;
		}
		case 'D':
		case 'V':
		case 'N':
		case 'A': {
			if (eqId == null) {
				eqId = "";
			}
			connId = isub + "-" + typeCode + "-" + busI + "-" + eqId;
			equip = resolveNamedSpecial(eqId, fromBus, code);
			break;
		}
		default:
			log.warn("Substation {}: unsupported terminal type '{}'", isub, typeCode);
			return;
		}

		if (equip == null && equipType != NBModelEquipType.NOT_DEFINED) {
			log.warn("Substation {}: unresolved terminal {} at bus {} eqId={}", isub, typeCode, busI,
					eqId != null ? eqId : "");
		}

		NBEquipConnection conn = NBModelObjectFactory.createNBEquipConnection(
				sub, connId, node, equipType, equip, fromBus, toBus, tertBus);
		conn.setName(typeCode + ":" + (eqId != null ? eqId : ""));
	}

	@FunctionalInterface
	private interface RawxRowConsumer {
		void accept(Map<String, JsonElement> row) throws InterpssException;
	}

	private void forEachRawxRow(JsonObject network, String sectionName, RawxRowConsumer consumer)
			throws InterpssException {
		if (!network.has(sectionName)) {
			return;
		}
		JsonObject section = network.getAsJsonObject(sectionName);
		if (!section.has("fields") || !section.has("data")) {
			return;
		}
		JsonArray fields = section.getAsJsonArray("fields");
		JsonArray data = section.getAsJsonArray("data");
		String[] fieldNames = new String[fields.size()];
		for (int i = 0; i < fields.size(); i++) {
			fieldNames[i] = fields.get(i).getAsString().toLowerCase();
		}
		for (JsonElement rowElem : data) {
			JsonArray row = rowElem.getAsJsonArray();
			Map<String, JsonElement> rowMap = new HashMap<>();
			for (int i = 0; i < Math.min(fieldNames.length, row.size()); i++) {
				rowMap.put(fieldNames[i], row.get(i));
			}
			consumer.accept(rowMap);
		}
	}

	private static int rawxInt(Map<String, JsonElement> row, String key, int defaultValue) {
		JsonElement el = row.get(key);
		if (el == null || el.isJsonNull()) {
			return defaultValue;
		}
		try {
			return el.getAsInt();
		} catch (Exception e) {
			try {
				return (int) Math.round(el.getAsDouble());
			} catch (Exception e2) {
				return defaultValue;
			}
		}
	}

	private static Integer rawxIntOrNull(Map<String, JsonElement> row, String key) {
		JsonElement el = row.get(key);
		if (el == null || el.isJsonNull()) {
			return null;
		}
		try {
			return el.getAsInt();
		} catch (Exception e) {
			try {
				return (int) Math.round(el.getAsDouble());
			} catch (Exception e2) {
				return null;
			}
		}
	}

	private static double rawxDouble(Map<String, JsonElement> row, String key, double defaultValue) {
		JsonElement el = row.get(key);
		if (el == null || el.isJsonNull()) {
			return defaultValue;
		}
		try {
			return el.getAsDouble();
		} catch (Exception e) {
			return defaultValue;
		}
	}

	private static String rawxString(Map<String, JsonElement> row, String key, String defaultValue) {
		JsonElement el = row.get(key);
		if (el == null || el.isJsonNull()) {
			return defaultValue;
		}
		try {
			return el.getAsString();
		} catch (Exception e) {
			return defaultValue;
		}
	}

	private NameTag resolveBusEquipment(BaseAclfBus bus, NBModelEquipType type, String eqId) {
		if (bus == null) {
			return null;
		}
		switch (type) {
		case LOAD:
			return bus.getContributeLoad(eqId);
		case MACHINE:
			return bus.getContributeGen(eqId);
		case SWITCHED_SHUNT: {
			for (Object o : bus.getSwitchedShuntList()) {
				if (o instanceof SwitchedShunt shunt && eqId.equals(shunt.getId())) {
					return shunt;
				}
			}
			// RAW v34 and earlier permit only one switched shunt per bus and
			// do not carry its ID in the switched-shunt section. Substation
			// Data may nevertheless use a terminal identifier other than "1".
			return bus.getSwitchedShuntList().size() == 1
					? (NameTag) bus.getSwitchedShuntList().get(0)
					: null;
		}
		case FIXED_SHUNT:
			return bus.getCompensator(eqId);
		case IND_MACH:
			return builder.getNamedEquipment("I|" + bus.getId() + "|" + eqId);
		default:
			return null;
		}
	}

	private NameTag resolveBranch(BaseAclfBus fromBus, BaseAclfBus toBus, String ckt) {
		if (fromBus == null || toBus == null) {
			return null;
		}
		AclfNetwork net = builder.getNetwork();
		AclfBranch bra = net.getBranch(fromBus.getId(), toBus.getId(), ckt);
		if (bra == null) {
			bra = net.getBranch(toBus.getId(), fromBus.getId(), ckt);
		}
		return bra;
	}

	/**
	 * Star buses of 3W transformers with {@code fromBus} as primary inherit that bus's substation.
	 */
	private void assign3WStarBusesToSubstation(BaseAclfBus fromBus, Substation sub) {
		AclfNetwork net = builder.getNetwork();
		for (Branch bra : net.getSpecialBranchList()) {
			if (!(bra instanceof Aclf3WBranch xfr)) {
				continue;
			}
			if (xfr.getFromBus() == fromBus && xfr.getStarBus() != null
					&& xfr.getStarBus().getSubstation() == null) {
				sub.addBus(xfr.getStarBus());
			}
		}
	}

	private NameTag resolve3WBranch(BaseAclfBus fromBus, BaseAclfBus toBus, BaseAclfBus tertBus, String ckt) {
		if (fromBus == null || toBus == null || tertBus == null) {
			return null;
		}
		AclfNetwork net = builder.getNetwork();
		String a = fromBus.getId();
		String b = toBus.getId();
		String c = tertBus.getId();
		// Terminal records list windings in arbitrary order vs the 3W creation order
		String[][] orders = {
				{ a, b, c }, { a, c, b },
				{ b, a, c }, { b, c, a },
				{ c, a, b }, { c, b, a }
		};
		for (String[] o : orders) {
			Aclf3WBranch xfr = net.get3WXfr(o[0], o[1], o[2], ckt);
			if (xfr != null) {
				return xfr;
			}
		}
		return null;
	}

	private NameTag resolveNamedSpecial(String name, BaseAclfBus hintBus, char typeCode) {
		if (name == null || name.isEmpty()) {
			return null;
		}
		String n = name.trim();
		AclfNetwork net = builder.getNetwork();
		if (typeCode == 'A') {
			NameTag svc = findSvcByName(hintBus, n);
			if (svc != null) {
				return svc;
			}
			for (Object busObj : net.getBusList()) {
				if (busObj instanceof BaseAclfBus bus) {
					svc = findSvcByName(bus, n);
					if (svc != null) {
						return svc;
					}
				}
			}
		}
		if (typeCode == 'N') {
			NameTag mtdc = builder.getNamedEquipment(n);
			if (mtdc == null) {
				mtdc = builder.getNamedEquipment("N|" + n);
			}
			if (mtdc != null) {
				return mtdc;
			}
		}
		for (Branch bra : net.getBranchList()) {
			if (nameMatches(bra, n)) {
				return bra;
			}
		}
		for (Branch bra : net.getSpecialBranchList()) {
			if (nameMatches(bra, n)) {
				return bra;
			}
		}
		return null;
	}

	private static boolean nameMatches(Branch bra, String n) {
		return n.equals(trimToEmpty(bra.getName()))
				|| n.equals(trimToEmpty(bra.getId()))
				|| n.equals(trimToEmpty(bra.getCircuitNumber()));
	}

	private static String trimToEmpty(String s) {
		return s == null ? "" : s.trim();
	}

	private static NameTag findSvcByName(BaseAclfBus bus, String name) {
		if (bus == null || !bus.isStaticVarCompensator()) {
			return null;
		}
		for (Object o : bus.getStaticVarCompensatorList()) {
			if (o instanceof com.interpss.core.aclf.facts.StaticVarCompensator svc
					&& (name.equals(trimToEmpty(svc.getId())) || name.equals(trimToEmpty(svc.getName())))) {
				return svc;
			}
		}
		return null;
	}

	private static NBModelSwitchType mapSwitchType(int psseType) {
		NBModelSwitchType t = NBModelSwitchType.get(psseType - 1);
		return t != null ? t : NBModelSwitchType.CONNECTOR;
	}

	private static NBModelEquipType mapEquipType(String code) {
		if (code == null || code.isEmpty()) {
			return NBModelEquipType.NOT_DEFINED;
		}
		return switch (code.charAt(0)) {
		case 'L' -> NBModelEquipType.LOAD;
		case 'F' -> NBModelEquipType.FIXED_SHUNT;
		case 'M' -> NBModelEquipType.MACHINE;
		case 'B', '2' -> NBModelEquipType.ACLF_BRANCH;
		case '3' -> NBModelEquipType.W3_XFORMER;
		case 'S' -> NBModelEquipType.SWITCHED_SHUNT;
		case 'I' -> NBModelEquipType.IND_MACH;
		case 'D' -> NBModelEquipType.T2_HVDC;
		case 'V' -> NBModelEquipType.VSC_HVDC;
		case 'N' -> NBModelEquipType.MULTI_THVDC;
		case 'A' -> NBModelEquipType.FACTS;
		default -> NBModelEquipType.NOT_DEFINED;
		};
	}

	private static boolean isNumericToken(String s) {
		try {
			Double.parseDouble(s);
			return true;
		} catch (NumberFormatException e) {
			return false;
		}
	}

	private static String readDataLine(BufferedReader reader) throws IOException {
		String line;
		while ((line = reader.readLine()) != null) {
			String trimmed = line.trim();
			// Skip blank lines, @! headers, // comments, and PowSyBl-style "/ BEGIN ..." markers
			if (trimmed.isEmpty() || trimmed.startsWith("//") || trimmed.startsWith("@!")
					|| trimmed.startsWith("/")) {
				continue;
			}
			return line;
		}
		return null;
	}
}
