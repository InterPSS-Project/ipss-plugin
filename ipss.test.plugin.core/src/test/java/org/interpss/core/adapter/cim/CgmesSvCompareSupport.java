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

import com.interpss.core.aclf.AclfBus;
import com.interpss.core.aclf.AclfGenCode;
import com.interpss.core.aclf.AclfNetwork;

/**
 * CGMES SvVoltage reader + Aclf vs SV compare (with angle-reference alignment).
 *
 * <p>SvVoltage.v is kV; angle is degrees. InterPSS uses pu / radians; bus base
 * voltage from {@code CGMESDirectParser} is in volts.
 */
final class CgmesSvCompareSupport {

	record SvVoltage(String topoLocalId, double vKv, double angleDeg) {
	}

	record CompareStats(int compared, int matchedBoth, int matchedVOnly, int missingBus,
			int skippedDead, String angRef, List<String> mismatches) {
		int matchedVoltage() { return matchedBoth + matchedVOnly; }
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

	static AclfBus findBus(AclfNetwork net, String topoLocalId) {
		AclfBus bus = net.getBus(topoLocalId);
		if (bus != null) {
			return bus;
		}
		String alt = topoLocalId.startsWith("_") ? topoLocalId.substring(1) : "_" + topoLocalId;
		return net.getBus(alt);
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

	static CompareStats compareVoltages(AclfNetwork net, Map<String, SvVoltage> sv,
			double vTolPu, double angTolDeg, double minMatchRatio) {
		String refId = angleRefTopoId(net, sv);
		SvVoltage refSv = sv.get(refId);
		AclfBus refBus = findBus(net, refId);
		assumeTrue(refSv != null && refBus != null, () -> "Angle reference missing: " + refId);
		double refAclfAng = aclfAngDeg(refBus);
		double refSvAng = refSv.angleDeg();
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
			// Differential angles cancel absolute reference frame
			double aclfDelta = aclfAngDeg(bus) - refAclfAng;
			double svDelta = s.angleDeg() - refSvAng;
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
						s.topoLocalId(), dV, aclfDelta, svDelta, dA, refId));
			} else {
				mismatches.add(String.format(Locale.ROOT,
						"%s Vpu aclf=%.5f sv=%.5f (d=%.5f) angDelta aclf=%.4f sv=%.4f (d=%.4f) ref=%s",
						s.topoLocalId(), aclfPu, svPu, dV, aclfDelta, svDelta, dA, refId));
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
		// Angle is a softer gate — absolute frames and flat solutions can fail |V|-OK buses.
		double minAng = Double.parseDouble(System.getProperty("ipss.cgmes.p4.minAngMatch", "0.5"));
		final double aRatio = (double) matchedBothF / (double) comparedF;
		assertTrue(aRatio + 1e-9 >= minAng,
				() -> "Aclf vs SV angle match ratio " + matchedBothF + "/" + comparedF + "=" + aRatio
						+ " < " + minAng + " (raise -Dipss.cgmes.p4.minAngMatch or fix model); sample: "
						+ mismatchSample);
		return new CompareStats(comparedF, matchedBothF, matchedVOnlyF, missingBusF, skippedDeadF, refF,
				mismatches);
	}

	private static double absAngDiffDeg(double a, double b) {
		double d = Math.abs(a - b) % 360.0;
		if (d > 180.0) {
			d = 360.0 - d;
		}
		return d;
	}
}
