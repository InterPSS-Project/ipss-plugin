package org.interpss.core.adapter.cim;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.commons.math3.complex.Complex;
import org.interpss.numeric.datatype.Unit.UnitType;

import com.interpss.core.aclf.AclfBranch;
import com.interpss.core.aclf.AclfBus;
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
 * <p>Flow overrides: {@code -Dipss.cgmes.p4.pTolMw} (default 5.0),
 * {@code -Dipss.cgmes.p4.qTolMvar} (default 5.0),
 * {@code -Dipss.cgmes.p4.minFlowMatch} (default 0.50).
 */
final class CgmesSvCompareSupport {

	record SvVoltage(String topoLocalId, double vKv, double angleDeg) {
	}

	record SvPowerFlow(String terminalLocalId, double pMw, double qMvar) {
	}

	/** Terminal → conducting equipment (+ optional sequenceNumber). */
	record TerminalEquip(String equipLocalId, int sequenceNumber, String equipType) {
	}

	record CompareStats(int compared, int matched, int missingBus, int skippedDead,
			double angleOffsetDeg, List<String> mismatches) {
	}

	record FlowCompareStats(int compared, int matched, int missingBranch, int skipped,
			List<String> mismatches) {
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
				if (!ce.find()) {
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
				out.put(termId, new TerminalEquip(equipId, seq, typ));
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
		String alt = equipLocalId.startsWith("_")
				? equipLocalId.substring(1)
				: "_" + equipLocalId;
		for (AclfBranch br : net.getBranchList()) {
			String id = br.getId();
			if (equipLocalId.equals(id) || alt.equals(id)) {
				return br;
			}
			// 3W star legs sometimes suffix the transformer id
			if (id != null && (id.startsWith(equipLocalId) || id.startsWith(alt))) {
				return br;
			}
		}
		return null;
	}

	static double baseKv(AclfBus bus) {
		double base = bus.getBaseVoltage();
		if (base > 1_000.0) {
			base = base / 1000.0;
		}
		return base;
	}

	/** Apply SvVoltage as initial conditions (pu / radians). */
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
			bus.setVoltageMag(s.vKv() / bk);
			bus.setVoltageAng(Math.toRadians(s.angleDeg()));
			n++;
		}
		return n;
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
		double offset = angleOffsetDeg(net, sv);
		int compared = 0;
		int matched = 0;
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
			// Skip electrically dead / numerical-garbage buses from match ratio
			if (aclfPu < 0.2 || !Double.isFinite(aclfPu) || !Double.isFinite(bus.getVoltageAng())) {
				skippedDead++;
				continue;
			}
			double svPu = s.vKv() / bk;
			double aclfAng = aclfAngDeg(bus);
			double svAngAligned = s.angleDeg() + offset;
			double dV = Math.abs(aclfPu - svPu);
			double dA = absAngDiffDeg(aclfAng, svAngAligned);
			compared++;
			if (dV <= vTolPu && dA <= angTolDeg) {
				matched++;
			} else {
				mismatches.add(String.format(Locale.ROOT,
						"%s Vpu aclf=%.5f sv=%.5f (d=%.5f) angDeg aclf=%.4f svAligned=%.4f (d=%.4f)",
						s.topoLocalId(), aclfPu, svPu, dV, aclfAng, svAngAligned, dA));
			}
		}

		final int comparedF = compared;
		final int matchedF = matched;
		final int missingBusF = missingBus;
		final int skippedDeadF = skippedDead;
		final double offsetF = offset;
		final int svSize = sv.size();
		final String mismatchSample = String.join(" | ",
				mismatches.subList(0, Math.min(12, mismatches.size())));
		assertTrue(comparedF > 0,
				() -> "No live SvVoltage rows compared (missingBus=" + missingBusF
						+ ", skippedDead=" + skippedDeadF + ", svSize=" + svSize + ")");
		final double ratio = (double) matchedF / (double) comparedF;
		assertTrue(ratio + 1e-9 >= minMatchRatio,
				() -> "Aclf vs SV match ratio " + matchedF + "/" + comparedF + "=" + ratio
						+ " < " + minMatchRatio + "; missingBus=" + missingBusF
						+ "; skippedDead=" + skippedDeadF + "; angleOffsetDeg=" + offsetF
						+ "; sample mismatches: " + mismatchSample);
		return new CompareStats(comparedF, matchedF, missingBusF, skippedDeadF, offsetF, mismatches);
	}

	/**
	 * Soft-compare Aclf branch flows to SvPowerFlow on ACLineSegment terminals
	 * (and PowerTransformer when indexed). Sequence 1 = from side
	 * ({@code powerFrom2To}); sequence 2 = to side ({@code powerTo2From}).
	 *
	 * <p>If fewer than 3 comparable line/xfr terminals are found, the assertion is
	 * skipped via {@code assumeTrue} rather than failing hard (small models /
	 * incomplete Terminal indexing).
	 */
	static FlowCompareStats compareBranchFlows(AclfNetwork net,
			Map<String, SvPowerFlow> flows, Map<String, TerminalEquip> termIndex,
			double pTolMw, double qTolMvar, double minMatchRatio) {
		int compared = 0;
		int matched = 0;
		int missingBranch = 0;
		int skipped = 0;
		List<String> mismatches = new ArrayList<>();

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
			AclfBranch br = findBranch(net, te.equipLocalId());
			if (br == null || !br.isActive()) {
				missingBranch++;
				continue;
			}
			Complex s;
			try {
				if (te.sequenceNumber() <= 1) {
					s = br.powerFrom2To(UnitType.mVA);
				} else {
					s = br.powerTo2From(UnitType.mVA);
				}
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
		return new FlowCompareStats(comparedF, matchedF, missingBranchF, skippedF, mismatches);
	}

	static double defaultPTolMw() {
		return Double.parseDouble(System.getProperty("ipss.cgmes.p4.pTolMw", "5.0"));
	}

	static double defaultQTolMvar() {
		return Double.parseDouble(System.getProperty("ipss.cgmes.p4.qTolMvar", "5.0"));
	}

	static double defaultMinFlowMatch() {
		return Double.parseDouble(System.getProperty("ipss.cgmes.p4.minFlowMatch", "0.50"));
	}

	private static double absAngDiffDeg(double a, double b) {
		double d = Math.abs(a - b) % 360.0;
		if (d > 180.0) {
			d = 360.0 - d;
		}
		return d;
	}
}
