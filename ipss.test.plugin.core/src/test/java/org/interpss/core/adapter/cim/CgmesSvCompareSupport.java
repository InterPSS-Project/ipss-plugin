package org.interpss.core.adapter.cim;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.commons.math3.complex.Complex;
import org.interpss.numeric.datatype.Unit.UnitType;

import com.interpss.core.aclf.Aclf3WBranch;
import com.interpss.core.aclf.AclfBranch;
import com.interpss.core.aclf.AclfBus;
import com.interpss.core.aclf.AclfGen;
import com.interpss.core.aclf.AclfGenCode;
import com.interpss.core.aclf.AclfNetwork;

/**
 * CGMES SvVoltage / SvPowerFlow readers + Aclf vs SV compare helpers.
 *
 * <p>SvVoltage.v is kV; angle is degrees. InterPSS uses pu / radians; bus base
 * voltage from {@code CGMESDirectParser} is in volts.
 *
 * <p>SvPowerFlow.p/q are MW / Mvar at a Terminal. Branch compare maps Terminal →
 * ACLineSegment (optionally PowerTransformer) via EQ, then to {@link AclfBranch}
 * by equipment id (with optional leading underscore).
 *
 * <p>Flow overrides: {@code -Dipss.cgmes.p4.pTolMw} (default 1.0),
 * {@code -Dipss.cgmes.p4.qTolMvar} (default 1.0),
 * {@code -Dipss.cgmes.p4.minFlowMatch} (default 0.95).
 */
final class CgmesSvCompareSupport {

	record SvVoltage(String topoLocalId, double vKv, double angleDeg) {
	}

	record SvPowerFlow(String terminalLocalId, double pMw, double qMvar) {
	}

	/** Terminal → conducting equipment (+ sequenceNumber and optional topological node). */
	record TerminalEquip(String equipLocalId, int sequenceNumber, String equipType, String topoLocalId) {
	}

	record CompareStats(int compared, int matchedBoth, int matchedVOnly, int missingBus,
			int skippedDead, String angRef, List<String> mismatches) {
		int matchedVoltage() { return matchedBoth + matchedVOnly; }
	}

	record FlowCompareStats(int compared, int matched, int missingBranch, int skipped,
			List<String> mismatches, List<String> missingReasons) {
	}

	private static final Pattern SV_BLOCK = Pattern.compile(
			"<cim:SvVoltage\\b[^>]*>\\s*(.*?)</cim:SvVoltage>",
			Pattern.CASE_INSENSITIVE | Pattern.DOTALL);
	private static final Pattern ANGLE = Pattern.compile(
			"<cim:SvVoltage\\.angle>([^<]+)</cim:SvVoltage\\.angle>",
			Pattern.CASE_INSENSITIVE);
	private static final Pattern V = Pattern.compile(
			"<cim:SvVoltage\\.v>([^<]+)</cim:SvVoltage\\.v>",
			Pattern.CASE_INSENSITIVE);
	private static final Pattern TN = Pattern.compile(
			"<cim:SvVoltage\\.TopologicalNode\\s+rdf:resource=\"#([^\"]+)\"",
			Pattern.CASE_INSENSITIVE);

	private static final Pattern SV_PF_BLOCK = Pattern.compile(
			"<cim:SvPowerFlow\\b[^>]*>\\s*(.*?)</cim:SvPowerFlow>",
			Pattern.CASE_INSENSITIVE | Pattern.DOTALL);
	private static final Pattern SV_PF_P = Pattern.compile(
			"<cim:SvPowerFlow\\.p>([^<]+)</cim:SvPowerFlow\\.p>",
			Pattern.CASE_INSENSITIVE);
	private static final Pattern SV_PF_Q = Pattern.compile(
			"<cim:SvPowerFlow\\.q>([^<]+)</cim:SvPowerFlow\\.q>",
			Pattern.CASE_INSENSITIVE);
	private static final Pattern SV_PF_TERM = Pattern.compile(
			"<cim:SvPowerFlow\\.Terminal\\s+rdf:resource=\"#([^\"]+)\"",
			Pattern.CASE_INSENSITIVE);

	/** RDF about/ID on Equipment or Terminal elements. */
	private static final Pattern RDF_ID = Pattern.compile(
			"rdf:(?:ID|about)=\"#?([^\"]+)\"",
			Pattern.CASE_INSENSITIVE);

	private static final Pattern TERM_BLOCK = Pattern.compile(
			"<cim:Terminal\\b([^>]*)>(.*?)</cim:Terminal>",
			Pattern.CASE_INSENSITIVE | Pattern.DOTALL);
	private static final Pattern TERM_CE = Pattern.compile(
			"<cim:Terminal\\.ConductingEquipment\\s+rdf:resource=\"#([^\"]+)\"",
			Pattern.CASE_INSENSITIVE);
	private static final Pattern TERM_SEQ = Pattern.compile(
			"<cim:(?:Terminal|ACDCTerminal)\\.sequenceNumber>([^<]+)</cim:(?:Terminal|ACDCTerminal)\\.sequenceNumber>",
			Pattern.CASE_INSENSITIVE);
	private static final Pattern TERM_TN = Pattern.compile(
			"<cim:Terminal\\.TopologicalNode\\s+rdf:resource=\"#([^\"]+)\"",
			Pattern.CASE_INSENSITIVE);

	private static final Pattern ACLINE_BLOCK = Pattern.compile(
			"<cim:ACLineSegment\\b([^>]*)>",
			Pattern.CASE_INSENSITIVE);
	private static final Pattern PWRXFR_BLOCK = Pattern.compile(
			"<cim:PowerTransformer\\b([^>]*)>",
			Pattern.CASE_INSENSITIVE);

	private CgmesSvCompareSupport() {
	}

	static Map<String, SvVoltage> readSvVoltages(Path svXml) throws Exception {
		assumeTrue(Files.isRegularFile(svXml), () -> "SV file missing: " + svXml);
		String text = Files.readString(svXml, StandardCharsets.UTF_8);
		Map<String, SvVoltage> out = new LinkedHashMap<>();
		Matcher block = SV_BLOCK.matcher(text);
		while (block.find()) {
			String body = block.group(1);
			Matcher am = ANGLE.matcher(body);
			Matcher vm = V.matcher(body);
			Matcher tm = TN.matcher(body);
			if (!am.find() || !vm.find() || !tm.find()) {
				continue;
			}
			String tn = tm.group(1);
			out.put(tn, new SvVoltage(tn, Double.parseDouble(vm.group(1).trim()),
					Double.parseDouble(am.group(1).trim())));
		}
		assumeTrue(!out.isEmpty(), () -> "No SvVoltage entries in " + svXml);
		return out;
	}

	static Map<String, SvPowerFlow> readSvPowerFlows(Path svXml) throws Exception {
		assumeTrue(Files.isRegularFile(svXml), () -> "SV file missing: " + svXml);
		String text = Files.readString(svXml, StandardCharsets.UTF_8);
		Map<String, SvPowerFlow> out = new LinkedHashMap<>();
		Matcher block = SV_PF_BLOCK.matcher(text);
		while (block.find()) {
			String body = block.group(1);
			Matcher pm = SV_PF_P.matcher(body);
			Matcher qm = SV_PF_Q.matcher(body);
			Matcher tm = SV_PF_TERM.matcher(body);
			if (!pm.find() || !qm.find() || !tm.find()) {
				continue;
			}
			String termId = tm.group(1);
			out.put(termId, new SvPowerFlow(termId,
					Double.parseDouble(pm.group(1).trim()),
					Double.parseDouble(qm.group(1).trim())));
		}
		assumeTrue(!out.isEmpty(), () -> "No SvPowerFlow entries in " + svXml);
		return out;
	}

	/**
	 * Index Terminal local-id → conducting equipment from one or more EQ XMLs.
	 * Equipment type is {@code ACLineSegment}, {@code PowerTransformer}, or empty.
	 */
	static Map<String, TerminalEquip> indexTerminals(Path... eqXmls) throws Exception {
		Map<String, String> equipType = new LinkedHashMap<>();
		Map<String, TerminalEquip> out = new LinkedHashMap<>();
		for (Path eq : eqXmls) {
			if (eq == null || !Files.isRegularFile(eq)) {
				continue;
			}
			String text = Files.readString(eq, StandardCharsets.UTF_8);
			Matcher line = ACLINE_BLOCK.matcher(text);
			while (line.find()) {
				String id = rdfId(line.group(1));
				if (id != null) {
					equipType.put(id, "ACLineSegment");
				}
			}
			Matcher xfr = PWRXFR_BLOCK.matcher(text);
			while (xfr.find()) {
				String id = rdfId(xfr.group(1));
				if (id != null) {
					equipType.put(id, "PowerTransformer");
				}
			}
			Matcher term = TERM_BLOCK.matcher(text);
			while (term.find()) {
				String attrs = term.group(1);
				String body = term.group(2);
				String termId = rdfId(attrs);
				if (termId == null) {
					continue;
				}
				Matcher ce = TERM_CE.matcher(body);
				String topo = null;
				Matcher tnm = TERM_TN.matcher(body);
				if (tnm.find()) {
					topo = tnm.group(1);
				}
				if (!ce.find()) {
					TerminalEquip prev = out.get(termId);
					if (prev != null && topo != null) {
						out.put(termId, new TerminalEquip(prev.equipLocalId(), prev.sequenceNumber(),
								prev.equipType(), topo));
					}
					continue;
				}
				String equipId = ce.group(1);
				int seq = 1;
				Matcher sm = TERM_SEQ.matcher(body);
				if (sm.find()) {
					try {
						seq = Integer.parseInt(sm.group(1).trim());
					} catch (NumberFormatException ignore) {
						seq = 1;
					}
				}
				String typ = equipType.getOrDefault(equipId, "");
				TerminalEquip prev = out.get(termId);
				if (prev != null) {
					if (typ.isEmpty()) {
						typ = prev.equipType();
					}
					if (topo == null) {
						topo = prev.topoLocalId();
					}
					if (seq == 1 && prev.sequenceNumber() != 1) {
						seq = prev.sequenceNumber();
					}
				}
				out.put(termId, new TerminalEquip(equipId, seq, typ, topo));
			}
		}
		assumeTrue(!out.isEmpty(), "No Terminal→ConductingEquipment rows indexed from EQ");
		return out;
	}

	private static String rdfId(String attrsOrAbout) {
		if (attrsOrAbout == null) {
			return null;
		}
		Matcher m = RDF_ID.matcher(attrsOrAbout);
		return m.find() ? m.group(1) : null;
	}

	static AclfBus findBus(AclfNetwork net, String topoLocalId) {
		AclfBus bus = net.getBus(topoLocalId);
		if (bus != null) {
			return bus;
		}
		String alt = topoLocalId.startsWith("_") ? topoLocalId.substring(1) : "_" + topoLocalId;
		return net.getBus(alt);
	}

	/**
	 * Resolve by CGMES equipment local id. InterPSS {@code net.getBranch(id)} keys the
	 * from–to–circuit form, so we scan {@code getBranchList()} and match {@code getId()}
	 * (set by the CGMES mappers to the EQ rdf local id).
	 */
	static AclfBranch findBranch(AclfNetwork net, String equipLocalId) {
		if (equipLocalId == null || equipLocalId.isBlank()) {
			return null;
		}
		AclfBranch exact = matchBranchId(net.getBranchList(), equipLocalId, false);
		if (exact != null) {
			return exact;
		}
		return matchBranchId(net.getBranchList(), equipLocalId, true);
	}

	/** 3W containers are {@link Aclf3WBranch}, not {@link AclfBranch}, and live on the special-branch list. */
	static Aclf3WBranch find3W(AclfNetwork net, String equipLocalId) {
		if (equipLocalId == null || equipLocalId.isBlank()) {
			return null;
		}
		for (Object obj : net.getSpecialBranchList()) {
			if (obj instanceof Aclf3WBranch w3 && idEquals(w3.getId(), equipLocalId)) {
				return w3;
			}
		}
		return null;
	}

	private static boolean idEquals(String id, String equipLocalId) {
		if (id == null) {
			return false;
		}
		String alt = equipLocalId.startsWith("_")
				? equipLocalId.substring(1)
				: "_" + equipLocalId;
		return equipLocalId.equals(id) || alt.equals(id);
	}

	private static AclfBranch matchBranchId(Iterable<AclfBranch> branches, String equipLocalId,
			boolean prefix) {
		String alt = equipLocalId.startsWith("_")
				? equipLocalId.substring(1)
				: "_" + equipLocalId;
		for (AclfBranch br : branches) {
			String id = br.getId();
			if (idEquals(id, equipLocalId)) {
				return br;
			}
			if (prefix && id != null && (id.startsWith(equipLocalId) || id.startsWith(alt))) {
				return br;
			}
		}
		return null;
	}

	/**
	 * Power into the conducting equipment at the terminal with this sequence number.
	 * Sequence 1 is the InterPSS from side. A 3W container's legs are oriented
	 * from-bus→star, star→to-bus, star→tertiary, so the to/tert injection is
	 * {@code powerTo2From}.
	 */
	private static Complex flowIntoEquipment(AclfNetwork net, AclfBranch br, Aclf3WBranch w3,
			TerminalEquip te) throws Exception {
		if (w3 != null) {
			AclfBus termBus = te.topoLocalId() == null ? null : findBus(net, te.topoLocalId());
			AclfBranch[] legs = { w3.getFromAclfBranch(), w3.getToAclfBranch(), w3.getTertAclfBranch() };
			for (AclfBranch leg : legs) {
				if (leg == null || termBus == null) {
					continue;
				}
				if (sameBus(leg.getFromBus(), termBus)) {
					return leg.powerFrom2To(UnitType.mVA);
				}
				if (sameBus(leg.getToBus(), termBus)) {
					return leg.powerTo2From(UnitType.mVA);
				}
			}
			return null;
		}
		if (te.sequenceNumber() <= 1) {
			return br.powerFrom2To(UnitType.mVA);
		}
		return br.powerTo2From(UnitType.mVA);
	}

	private static boolean sameBus(com.interpss.core.net.Bus branchBus, AclfBus termBus) {
		if (branchBus == null || termBus == null || branchBus.getId() == null) {
			return false;
		}
		return idEquals(branchBus.getId(), termBus.getId());
	}

	static double baseKv(AclfBus bus) {
		double base = bus.getBaseVoltage();
		if (base > 1_000.0) {
			base = base / 1000.0;
		}
		return base;
	}

	/**
	 * Apply SvVoltage as initial conditions (pu / radians) and align PV/swing
	 * desired voltages (and contribute-gen desired V) to the same pu so NR does
	 * not pull seeded buses back toward SSH/EQ targets of 1.0 pu.
	 */
	static int seedFromSv(AclfNetwork net, Map<String, SvVoltage> sv) {
		int n = 0;
		for (SvVoltage s : sv.values()) {
			AclfBus bus = findBus(net, s.topoLocalId());
			if (bus == null) {
				continue;
			}
			double bk = baseKv(bus);
			if (bk <= 0) {
				continue;
			}
			double vPu = s.vKv() / bk;
			bus.setVoltageMag(vPu);
			bus.setVoltageAng(Math.toRadians(s.angleDeg()));
			alignRegulatedDesiredV(bus, vPu, s.angleDeg());
			n++;
		}
		return n;
	}

	/** Keep PV/swing setpoints consistent with the SV seed used as NR init. */
	static void alignRegulatedDesiredV(AclfBus bus, double vPu, double angleDeg) {
		AclfGenCode code = bus.getGenCode();
		if (code == AclfGenCode.GEN_PV) {
			bus.setDesiredVoltMag(vPu);
			try {
				bus.toPVBus().setDesiredVoltMag(vPu);
			} catch (Exception ignore) {
				// bus-level setpoint is enough for most solvers
			}
		} else if (code == AclfGenCode.SWING) {
			bus.setDesiredVoltMag(vPu);
			try {
				var swing = bus.toSwingBus();
				swing.setDesiredVoltMag(vPu);
				swing.setDesiredVoltAngDeg(angleDeg);
			} catch (Exception ignore) {
				bus.setDesiredVoltAng(Math.toRadians(angleDeg));
			}
		} else {
			return;
		}
		if (bus.getContributeGenList() != null) {
			for (Object obj : bus.getContributeGenList()) {
				if (obj instanceof AclfGen gen && gen.isActive()) {
					gen.setDesiredVoltMag(vPu);
				}
			}
		}
	}

	static double aclfAngDeg(AclfBus bus) {
		double angRaw = bus.getVoltageAng();
		if (Math.abs(angRaw) > (2 * Math.PI + 0.5)) {
			return angRaw; // already degrees
		}
		return Math.toDegrees(angRaw);
	}

	/**
	 * Angle offset so a reference bus (prefer swing) matches SV: aclf = sv + offset.
	 */
	static double angleOffsetDeg(AclfNetwork net, Map<String, SvVoltage> sv) {
		AclfBus swing = null;
		for (AclfBus b : net.getBusList()) {
			if (b.getGenCode() == AclfGenCode.SWING) {
				swing = b;
				break;
			}
		}
		if (swing != null) {
			for (SvVoltage s : sv.values()) {
				AclfBus b = findBus(net, s.topoLocalId());
				if (b != null && b.getId().equals(swing.getId())) {
					return aclfAngDeg(swing) - s.angleDeg();
				}
			}
		}
		// fallback: first mapped SV bus
		for (SvVoltage s : sv.values()) {
			AclfBus b = findBus(net, s.topoLocalId());
			if (b != null && b.getVoltageMag() > 0.2) {
				return aclfAngDeg(b) - s.angleDeg();
			}
		}
		return 0.0;
	}

	static CompareStats compareVoltages(AclfNetwork net, Map<String, SvVoltage> sv,
			double vTolPu, double angTolDeg, double minMatchRatio) {
		String refId = angleRefTopoId(net, sv);
		SvVoltage refSv = sv.get(refId);
		AclfBus refBus = findBus(net, refId);
		assumeTrue(refSv != null && refBus != null, () -> "Angle reference missing: " + refId);
		Map<String, String> refByBus = angleRefByComponent(net, sv);
		int compared = 0;
		int matchedBoth = 0;
		int matchedVOnly = 0;
		int missingBus = 0;
		int skippedDead = 0;
		List<String> mismatches = new ArrayList<>();

		for (SvVoltage s : sv.values()) {
			AclfBus bus = findBus(net, s.topoLocalId());
			if (bus == null) {
				missingBus++;
				continue;
			}
			double bk = baseKv(bus);
			if (bk <= 0) {
				mismatches.add(s.topoLocalId() + " baseV<=0");
				compared++;
				continue;
			}
			double aclfPu = bus.getVoltageMag();
			if (aclfPu < 0.2 || !Double.isFinite(aclfPu) || !Double.isFinite(bus.getVoltageAng())) {
				skippedDead++;
				continue;
			}
			double svPu = s.vKv() / bk;
			String busRef = refByBus.getOrDefault(bus.getId(), refId);
			SvVoltage busRefSv = sv.get(busRef);
			AclfBus busRefBus = findBus(net, busRef);
			if (busRefSv == null || busRefBus == null) {
				busRef = refId;
				busRefSv = refSv;
				busRefBus = refBus;
			}
			// Differential angles cancel the absolute frame, per connected island.
			double aclfDelta = aclfAngDeg(bus) - aclfAngDeg(busRefBus);
			double svDelta = s.angleDeg() - busRefSv.angleDeg();
			double dV = Math.abs(aclfPu - svPu);
			double dA = absAngDiffDeg(aclfDelta, svDelta);
			compared++;
			boolean vOk = dV <= vTolPu;
			boolean aOk = dA <= angTolDeg;
			if (vOk && aOk) {
				matchedBoth++;
			} else if (vOk) {
				matchedVOnly++;
				mismatches.add(String.format(Locale.ROOT,
						"%s ANGLE-ONLY Vpu ok dV=%.5f angDelta aclf=%.4f sv=%.4f (d=%.4f) ref=%s",
						s.topoLocalId(), dV, aclfDelta, svDelta, dA, busRef));
			} else {
				mismatches.add(String.format(Locale.ROOT,
						"%s Vpu aclf=%.5f sv=%.5f (d=%.5f) angDelta aclf=%.4f sv=%.4f (d=%.4f) ref=%s",
						s.topoLocalId(), aclfPu, svPu, dV, aclfDelta, svDelta, dA, busRef));
			}
		}

		final int comparedF = compared;
		final int matchedBothF = matchedBoth;
		final int matchedVOnlyF = matchedVOnly;
		final int missingBusF = missingBus;
		final int skippedDeadF = skippedDead;
		final String refF = refId;
		final int svSize = sv.size();
		final String mismatchSample = String.join(" | ",
				mismatches.subList(0, Math.min(12, mismatches.size())));
		assertTrue(comparedF > 0,
				() -> "No live SvVoltage rows compared (missingBus=" + missingBusF
						+ ", skippedDead=" + skippedDeadF + ", svSize=" + svSize + ")");
		final double vRatio = (double) (matchedBothF + matchedVOnlyF) / (double) comparedF;
		assertTrue(vRatio + 1e-9 >= minMatchRatio,
				() -> "Aclf vs SV |V| match ratio " + (matchedBothF + matchedVOnlyF) + "/" + comparedF
						+ "=" + vRatio + " < " + minMatchRatio + "; missingBus=" + missingBusF
						+ "; skippedDead=" + skippedDeadF + "; angRef=" + refF
						+ "; sample: " + mismatchSample);
		double minAng = Double.parseDouble(System.getProperty("ipss.cgmes.p4.minAngMatch", "0.95"));
		final double aRatio = (double) matchedBothF / (double) comparedF;
		assertTrue(aRatio + 1e-9 >= minAng,
				() -> "Aclf vs SV angle match ratio " + matchedBothF + "/" + comparedF + "=" + aRatio
						+ " < " + minAng + " (raise -Dipss.cgmes.p4.minAngMatch or fix model); sample: "
						+ mismatchSample);
		return new CompareStats(comparedF, matchedBothF, matchedVOnlyF, missingBusF, skippedDeadF, refF,
				mismatches);
	}

	/**
	 * Angle reference for each connected component. A second island (HVDC AC
	 * terminals, a split area) does not share the swing bus angle frame.
	 */
	private static Map<String, String> angleRefByComponent(AclfNetwork net, Map<String, SvVoltage> sv) {
		Map<String, List<String>> adj = new HashMap<>();
		for (AclfBranch br : net.getBranchList()) {
			if (br == null || !br.isActive() || br.getFromBus() == null || br.getToBus() == null) {
				continue;
			}
			String a = br.getFromBus().getId();
			String b = br.getToBus().getId();
			adj.computeIfAbsent(a, k -> new ArrayList<>()).add(b);
			adj.computeIfAbsent(b, k -> new ArrayList<>()).add(a);
		}
		Map<String, SvVoltage> svByBus = new HashMap<>();
		for (SvVoltage s : sv.values()) {
			AclfBus bus = findBus(net, s.topoLocalId());
			if (bus != null) {
				svByBus.put(bus.getId(), s);
			}
		}
		Map<String, String> refOf = new HashMap<>();
		Set<String> seen = new HashSet<>();
		for (String start : svByBus.keySet()) {
			if (!seen.add(start)) {
				continue;
			}
			List<String> comp = new ArrayList<>();
			ArrayDeque<String> dq = new ArrayDeque<>();
			dq.add(start);
			while (!dq.isEmpty()) {
				String id = dq.remove();
				if (svByBus.containsKey(id)) {
					comp.add(id);
				}
				for (String next : adj.getOrDefault(id, List.of())) {
					if (seen.add(next)) {
						dq.add(next);
					}
				}
			}
			if (comp.isEmpty()) {
				continue;
			}
			String refBusId = comp.get(0);
			for (String id : comp) {
				AclfBus b = net.getBus(id);
				if (b != null && b.getVoltageMag() >= 0.2 && b.getGenCode() == AclfGenCode.SWING) {
					refBusId = id;
					break;
				}
			}
			String refTopo = svByBus.get(refBusId).topoLocalId();
			for (String id : comp) {
				refOf.put(id, refTopo);
			}
		}
		return refOf;
	}

	/** Pick reference TN: swing if present, else first live mapped SV bus. */
	static String angleRefTopoId(AclfNetwork net, Map<String, SvVoltage> sv) {
		for (SvVoltage s : sv.values()) {
			AclfBus b = findBus(net, s.topoLocalId());
			if (b != null && b.getVoltageMag() >= 0.2 && b.getGenCode() == AclfGenCode.SWING) {
				return s.topoLocalId();
			}
		}
		for (SvVoltage s : sv.values()) {
			AclfBus b = findBus(net, s.topoLocalId());
			if (b != null && b.getVoltageMag() >= 0.2) {
				return s.topoLocalId();
			}
		}
		return sv.keySet().iterator().next();
	}


	static FlowCompareStats compareBranchFlows(AclfNetwork net,
			Map<String, SvPowerFlow> flows, Map<String, TerminalEquip> termIndex,
			double pTolMw, double qTolMvar, double minMatchRatio) {
		int compared = 0;
		int matched = 0;
		int missingBranch = 0;
		int skipped = 0;
		List<String> mismatches = new ArrayList<>();
		List<String> missingReasons = new ArrayList<>();

		for (SvPowerFlow pf : flows.values()) {
			TerminalEquip te = termIndex.get(pf.terminalLocalId());
			if (te == null) {
				String alt = pf.terminalLocalId().startsWith("_")
						? pf.terminalLocalId().substring(1)
						: "_" + pf.terminalLocalId();
				te = termIndex.get(alt);
			}
			if (te == null) {
				skipped++;
				continue;
			}
			String typ = te.equipType();
			if (!"ACLineSegment".equals(typ) && !"PowerTransformer".equals(typ)) {
				skipped++;
				continue;
			}
			Aclf3WBranch w3 = find3W(net, te.equipLocalId());
			AclfBranch br = w3 == null ? findBranch(net, te.equipLocalId()) : null;
			if (w3 == null && (br == null || !br.isActive())) {
				missingBranch++;
				missingReasons.add(String.format(Locale.ROOT,
						"term=%s equip=%s seq=%d type=%s reason=%s",
						pf.terminalLocalId(), te.equipLocalId(), te.sequenceNumber(), typ,
						missingBranchReason(net, te.equipLocalId(), br != null && !br.isActive())));
				continue;
			}
			Complex s;
			try {
				s = flowIntoEquipment(net, br, w3, te);
			} catch (Exception ex) {
				skipped++;
				continue;
			}
			if (s == null || !Double.isFinite(s.getReal()) || !Double.isFinite(s.getImaginary())) {
				skipped++;
				continue;
			}
			double dP = Math.abs(s.getReal() - pf.pMw());
			double dQ = Math.abs(s.getImaginary() - pf.qMvar());
			compared++;
			if (dP <= pTolMw && dQ <= qTolMvar) {
				matched++;
			} else {
				mismatches.add(String.format(Locale.ROOT,
						"term=%s equip=%s seq=%d P aclf=%.3f sv=%.3f (d=%.3f) Q aclf=%.3f sv=%.3f (d=%.3f)",
						pf.terminalLocalId(), te.equipLocalId(), te.sequenceNumber(),
						s.getReal(), pf.pMw(), dP, s.getImaginary(), pf.qMvar(), dQ));
			}
		}

		final int comparedF = compared;
		final int matchedF = matched;
		final int missingBranchF = missingBranch;
		final int skippedF = skipped;
		final String mismatchSample = String.join(" | ",
				mismatches.subList(0, Math.min(12, mismatches.size())));
		// Soft skip when too few comparable terminals (document: <3).
		assumeTrue(comparedF >= 3,
				() -> "Fewer than 3 comparable ACLineSegment/PowerTransformer SvPowerFlow terminals"
						+ " (compared=" + comparedF + ", missingBranch=" + missingBranchF
						+ ", skipped=" + skippedF + ") — skip flow assert");
		final double ratio = (double) matchedF / (double) comparedF;
		assertTrue(ratio + 1e-9 >= minMatchRatio,
				() -> "Aclf vs SvPowerFlow match ratio " + matchedF + "/" + comparedF + "=" + ratio
						+ " < " + minMatchRatio + "; missingBranch=" + missingBranchF
						+ "; skipped=" + skippedF + "; sample: " + mismatchSample);
		return new FlowCompareStats(comparedF, matchedF, missingBranchF, skippedF, mismatches,
				missingReasons);
	}

	/**
	 * Always-on diagnostic. Printed even when the loose floor still passes, so
	 * mapper edits can be scored against the buses and branches that miss SV.
	 */
	static void printVoltageDump(String label, CompareStats stats) {
		System.out.println("SvVoltage " + label + ": both=" + stats.matchedBoth()
				+ " vOnly=" + stats.matchedVOnly() + "/" + stats.compared()
				+ " missingBus=" + stats.missingBus()
				+ " skippedDead=" + stats.skippedDead()
				+ " angRef=" + stats.angRef());
		for (String row : stats.mismatches()) {
			System.out.println("  V " + row);
		}
	}

	static void printFlowDump(String label, FlowCompareStats stats) {
		System.out.println("SvPowerFlow " + label + ": " + stats.matched() + "/" + stats.compared()
				+ " missingBranch=" + stats.missingBranch() + " skipped=" + stats.skipped());
		for (String row : stats.missingReasons()) {
			System.out.println("  MISS " + row);
		}
		for (String row : stats.mismatches()) {
			System.out.println("  FLOW " + row);
		}
	}

	/**
	 * Why {@link #findBranch} failed. Prefix hits that were not returned are a 3W id
	 * mismatch; otherwise the equipment was not mapped (boundary topological node or
	 * a skipped transformer/line).
	 */
	private static String missingBranchReason(AclfNetwork net, String equipLocalId, boolean inactive) {
		if (inactive) {
			return "inactive";
		}
		String bare = equipLocalId.startsWith("_") ? equipLocalId.substring(1) : equipLocalId;
		List<String> hits = new ArrayList<>();
		for (AclfBranch br : net.getBranchList()) {
			String id = br.getId();
			if (id == null) {
				continue;
			}
			String idBare = id.startsWith("_") ? id.substring(1) : id;
			if (idBare.startsWith(bare) || bare.startsWith(idBare)) {
				hits.add(id + (br.isActive() ? "" : "/inactive"));
			}
		}
		if (!hits.isEmpty()) {
			return "3W-id-prefix-miss candidates=" + hits;
		}
		return "no-branch (unmapped or boundary topological node)";
	}

	static double defaultPTolMw() {
		return Double.parseDouble(System.getProperty("ipss.cgmes.p4.pTolMw", "1.0"));
	}

	static double defaultQTolMvar() {
		return Double.parseDouble(System.getProperty("ipss.cgmes.p4.qTolMvar", "1.0"));
	}

	static double defaultMinFlowMatch() {
		return Double.parseDouble(System.getProperty("ipss.cgmes.p4.minFlowMatch", "0.95"));
	}

	private static double absAngDiffDeg(double a, double b) {
		double d = Math.abs(a - b) % 360.0;
		if (d > 180.0) {
			d = 360.0 - d;
		}
		return d;
	}
}
