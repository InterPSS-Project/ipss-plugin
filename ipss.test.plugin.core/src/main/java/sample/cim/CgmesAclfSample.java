package sample.cim;

import static com.interpss.common.util.NetUtilFunc.ToBranchId;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.interpss.display.AclfOutFunc;
import org.interpss.fadapter.cim.CGMESDirectParser;

import com.interpss.core.LoadflowAlgoObjectFactory;
import com.interpss.core.aclf.AclfBranch;
import com.interpss.core.aclf.AclfBus;
import com.interpss.core.aclf.AclfGen;
import com.interpss.core.aclf.AclfGenCode;
import com.interpss.core.aclf.AclfNetModelType;
import com.interpss.core.aclf.AclfNetwork;
import com.interpss.core.algo.AclfMethodType;
import com.interpss.core.algo.LoadflowAlgorithm;
import com.interpss.core.funcImpl.zeroz.AclfNetZeroZBranchHelper;
import com.interpss.core.funcImpl.zeroz.AclfNetZeroZDeconsolidator;

/**
 * Shared CGMES ACLF sample runner mirroring {@code CGMESCasP4AclfSmokeStubTest.solveNrSeeded}:
 * parse profiles, seed from SvVoltage, optional large-net prep, zero-Z consolidate, NR,
 * deconsolidate on success, then print a short summary.
 */
public final class CgmesAclfSample {

	private static final String TD30_CAS = "testData/adpter/cim/cgmes3.0/cas/";
	private static final String TD24 = "testData/adpter/cim/cgmes2.4/";

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

	public record SvVoltage(String topoLocalId, double vKv, double angleDeg) {
	}

	private CgmesAclfSample() {
	}

	/**
	 * Resolve a CAS pack directory: {@code -Dipss.cgmes.cas.root}, then in-repo
	 * {@code testData/...}, then {@code ipss.test.plugin.core/testData/...}, then Temp fallback.
	 */
	public static Path casDir(String localCasDirName, String relativeUnderV30) {
		String override = System.getProperty("ipss.cgmes.cas.root");
		if (override != null && !override.isBlank()) {
			return Path.of(override).resolve("v3.0").resolve(relativeUnderV30);
		}
		Path local = resolveExistingDir(TD30_CAS + localCasDirName);
		if (local != null) {
			return local;
		}
		String home = System.getProperty("user.home");
		return Path.of(home, "Documents", "Temp", "cgmes-test-data", "cas-v3.0.3",
				"CGMES_ConformityAssessmentScheme_TestConfigurations_v3-0-3", "v3.0")
				.resolve(relativeUnderV30);
	}

	/** cgmes2.4 fixture directory (module-relative or repo-prefixed). */
	public static Path cgmes24Dir() {
		Path local = resolveExistingDir(TD24);
		return local != null ? local : Path.of(TD24);
	}

	private static Path resolveExistingDir(String relative) {
		Path p = Path.of(relative);
		if (Files.isDirectory(p)) {
			return p;
		}
		Path prefixed = Path.of("ipss.test.plugin.core", relative);
		if (Files.isDirectory(prefixed)) {
			return prefixed;
		}
		return null;
	}

	/** Require a regular file under {@code dir}; return null and print if missing. */
	public static Path requireFile(Path dir, String name) {
		Path f = dir.resolve(name);
		if (!Files.isRegularFile(f)) {
			System.out.println("Missing file: " + f.toAbsolutePath());
			return null;
		}
		return f;
	}

	public static boolean requireDir(Path dir, String label) {
		if (!Files.isDirectory(dir)) {
			System.out.println(label + " missing: " + dir.toAbsolutePath());
			return false;
		}
		return true;
	}

	public static String[] abs(Path... files) {
		String[] out = new String[files.length];
		for (int i = 0; i < files.length; i++) {
			out[i] = files[i].toAbsolutePath().toString();
		}
		return out;
	}

	/**
	 * Parse {@code inputs}, seed from {@code svXml}, run NR (soft or hard message only),
	 * and print summary. Returns without throwing when fixtures are incomplete.
	 */
	public static void run(String label, Path svXml, Path... inputs) throws Exception {
		for (Path p : inputs) {
			if (p == null || !Files.isRegularFile(p)) {
				System.out.println(label + ": skip — missing input profile");
				return;
			}
		}
		if (svXml == null || !Files.isRegularFile(svXml)) {
			System.out.println(label + ": skip — missing SV: " + svXml);
			return;
		}

		Map<String, SvVoltage> sv = readSvVoltages(svXml);
		if (sv.isEmpty()) {
			System.out.println(label + ": skip — no SvVoltage entries in " + svXml);
			return;
		}

		AclfNetwork net = new CGMESDirectParser().parse(abs(inputs));
		System.out.println(label + ": buses=" + net.getNoBus() + ", branches=" + net.getNoBranch()
				+ ", model=" + net.getAclfNetModelType());

		boolean ok = solveNrSeeded(net, sv);
		System.out.println(label + ": seeded=" + lastSeededCount + ", converged=" + ok);
		System.out.println(label + ": maxMismatch=" + net.maxMismatch(AclfMethodType.NR));
		//if (net.getNoBus() <= 500) {
			System.out.println(AclfOutFunc.loadFlowSummary(net));
		//} else {
		//	System.out.println(label + ": skipped full summary (buses > 500)");
		//}
	}

	private static int lastSeededCount;

	/**
	 * Seed from SV, consolidate zero-Z branches, run NR, and deconsolidate after
	 * a solution. Returns whether NR converged.
	 */
	public static boolean solveNrSeeded(AclfNetwork net, Map<String, SvVoltage> sv) throws Exception {
		lastSeededCount = seedFromSv(net, sv);
		if (lastSeededCount <= 0) {
			System.out.println("Warning: no buses seeded from SvVoltage");
		}
		if (net.getNoBus() > 1000) {
			alignTinyLineComponents(net);
			fillFlatFromNeighbor(net);
		}
		Map<AclfBranch, String> cgmesBranchIds = null;
		AclfNetModelType model = net.getAclfNetModelType();
		if (model == AclfNetModelType.ZBR_MODEL || model == AclfNetModelType.ZBR_DECONSOLIDATED) {
			cgmesBranchIds = structuralBranchIds(net);
			new AclfNetZeroZBranchHelper(net).consolidate();
		}
		LoadflowAlgorithm algo = LoadflowAlgoObjectFactory.createLoadflowAlgorithm(net);
		algo.setInitBusVoltage(false);
		algo.setLfMethod(AclfMethodType.NR);
		algo.getDataCheckConfig().setAutoTurnLine2Xfr(true);
		if (net.getNoBus() > 1000) {
			algo.setVariableUpdateLimit(true);
		}
		algo.loadflow();
		if (net.isLfConverged() && net.getAclfNetModelType() == AclfNetModelType.ZBR_CONSOLIDATED) {
			new AclfNetZeroZDeconsolidator(net).deconsolidate(true);
			restoreCgmesBranchIds(net, cgmesBranchIds);
		}
		return net.isLfConverged();
	}

	public static Map<String, SvVoltage> readSvVoltages(Path svXml) throws Exception {
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
		return out;
	}

	public static int seedFromSv(AclfNetwork net, Map<String, SvVoltage> sv) {
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

	private static void alignRegulatedDesiredV(AclfBus bus, double vPu, double angleDeg) {
		AclfGenCode code = bus.getGenCode();
		if (code == AclfGenCode.GEN_PV) {
			bus.setDesiredVoltMag(vPu);
			try {
				bus.toPVBus().setDesiredVoltMag(vPu);
			} catch (Exception ignore) {
				// bus-level setpoint is enough
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

	private static AclfBus findBus(AclfNetwork net, String topoLocalId) {
		AclfBus bus = net.getBus(topoLocalId);
		if (bus != null) {
			return bus;
		}
		String alt = topoLocalId.startsWith("_") ? topoLocalId.substring(1) : "_" + topoLocalId;
		return net.getBus(alt);
	}

	private static double baseKv(AclfBus bus) {
		double base = bus.getBaseVoltage();
		if (base > 1_000.0) {
			base = base / 1000.0;
		}
		return base;
	}

	private static int alignTinyLineComponents(AclfNetwork net) {
		Map<String, String> parent = new HashMap<>();
		Map<String, AclfBus> buses = new HashMap<>();
		for (AclfBranch branch : net.getBranchList()) {
			if (!branch.isActive() || branch.getZ() == null
					|| branch.getFromBus() == null || branch.getToBus() == null) {
				continue;
			}
			if (branch.getZ().abs() > 2.0e-3) {
				continue;
			}
			boolean nearUnityXfr = branch.isXfr() && !branch.isPSXfr()
					&& Math.abs(branch.getFromTurnRatio() - 1.0) < 0.05
					&& Math.abs(branch.getToTurnRatio() - 1.0) < 0.05;
			if (!branch.isLine() && !nearUnityXfr) {
				continue;
			}
			AclfBus from = (AclfBus) branch.getFromBus();
			AclfBus to = (AclfBus) branch.getToBus();
			buses.put(from.getId(), from);
			buses.put(to.getId(), to);
			unionTiny(parent, from.getId(), to.getId());
		}
		Map<String, List<AclfBus>> groups = new HashMap<>();
		for (AclfBus bus : buses.values()) {
			groups.computeIfAbsent(findTiny(parent, bus.getId()), k -> new ArrayList<>()).add(bus);
		}
		int n = 0;
		for (List<AclfBus> group : groups.values()) {
			if (group.size() < 2) {
				continue;
			}
			AclfBus src = group.get(0);
			for (AclfBus bus : group) {
				if (bus.isSwing()) {
					src = bus;
					break;
				}
				if (isFlatStart(src) && !isFlatStart(bus)) {
					src = bus;
				}
			}
			for (AclfBus bus : group) {
				if (bus == src) {
					continue;
				}
				bus.setVoltageMag(src.getVoltageMag());
				bus.setVoltageAng(src.getVoltageAng());
				if (bus.isGenPV()) {
					double vSet = src.isGenPV() ? src.getDesiredVoltMag() : src.getVoltageMag();
					if (vSet > 0.0) {
						bus.setDesiredVoltMag(vSet);
					}
				}
				n++;
			}
		}
		return n;
	}

	private static int fillFlatFromNeighbor(AclfNetwork net) {
		int n = 0;
		boolean changed = true;
		for (int pass = 0; changed && pass < 40; pass++) {
			changed = false;
			for (AclfBranch branch : net.getBranchList()) {
				if (!branch.isActive() || branch.isPSXfr()
						|| branch.getFromBus() == null || branch.getToBus() == null) {
					continue;
				}
				AclfBus from = (AclfBus) branch.getFromBus();
				AclfBus to = (AclfBus) branch.getToBus();
				if (isFlatStart(to) && !isFlatStart(from)) {
					copyVoltage(from, to);
					changed = true;
					n++;
				} else if (isFlatStart(from) && !isFlatStart(to)) {
					copyVoltage(to, from);
					changed = true;
					n++;
				}
			}
		}
		return n;
	}

	private static void copyVoltage(AclfBus src, AclfBus dst) {
		dst.setVoltageMag(src.getVoltageMag());
		dst.setVoltageAng(src.getVoltageAng());
		if (dst.isGenPV()) {
			double vSet = src.isGenPV() ? src.getDesiredVoltMag() : src.getVoltageMag();
			if (vSet > 0.0) {
				dst.setDesiredVoltMag(vSet);
			}
		}
	}

	private static boolean isFlatStart(AclfBus bus) {
		return Math.abs(bus.getVoltageMag() - 1.0) < 1.0e-6 && Math.abs(bus.getVoltageAng()) < 1.0e-8;
	}

	private static String findTiny(Map<String, String> parent, String id) {
		parent.putIfAbsent(id, id);
		String root = id;
		while (!parent.get(root).equals(root)) {
			root = parent.get(root);
		}
		String cursor = id;
		while (!cursor.equals(root)) {
			String next = parent.get(cursor);
			parent.put(cursor, root);
			cursor = next;
		}
		return root;
	}

	private static void unionTiny(Map<String, String> parent, String a, String b) {
		String ra = findTiny(parent, a);
		String rb = findTiny(parent, b);
		if (!ra.equals(rb)) {
			parent.put(ra, rb);
		}
	}

	private static Map<AclfBranch, String> structuralBranchIds(AclfNetwork net) {
		Map<AclfBranch, String> saved = new IdentityHashMap<>();
		for (AclfBranch branch : net.getBranchList()) {
			String id = branch.getId();
			if (id != null && id.contains("->")) {
				continue;
			}
			if (branch.getFromBus() == null || branch.getToBus() == null
					|| branch.getCircuitNumber() == null) {
				continue;
			}
			saved.put(branch, id);
			branch.setId(ToBranchId.f(branch.getFromBus().getId(), branch.getToBus().getId(),
					branch.getCircuitNumber()));
		}
		net.rebuildLookupTable();
		return saved;
	}

	private static void restoreCgmesBranchIds(AclfNetwork net, Map<AclfBranch, String> saved) {
		if (saved == null || saved.isEmpty()) {
			return;
		}
		for (Map.Entry<AclfBranch, String> entry : saved.entrySet()) {
			if (entry.getValue() != null) {
				entry.getKey().setId(entry.getValue());
			}
		}
		net.rebuildLookupTable();
	}

	/** Shared Type3 CGM hour sample (first-hour EQ + per-hour SSH/TP/SV). */
	public static void runType3Hour(String hour) throws Exception {
		Path igms = casDir("MicroGrid-Type3-IGMs", "MicroGrid/MicroGrid-Type3/IGMs");
		Path cgms = casDir("MicroGrid-Type3-CGMs", "MicroGrid/MicroGrid-Type3/CGMs");
		if (!requireDir(igms, "Type3 IGMs") || !requireDir(cgms, "Type3 CGMs")) {
			return;
		}
		Path eqBd = requireFile(igms, "20171002T0930Z_ENTSO-E_EQ_BD_2.xml");
		Path beEq = requireFile(igms, "20210422T2230Z_1D_BE_EQ_001.xml");
		Path nlEq = requireFile(igms, "20210422T2230Z_1D_NL_EQ_001.xml");
		Path beSsh = requireFile(igms, hour + "_1D_BE_SSH_001.xml");
		Path nlSsh = requireFile(igms, hour + "_1D_NL_SSH_001.xml");
		Path tp = requireFile(cgms, hour + "_1D_ASSEMBLED_TP_001.xml");
		Path svXml = requireFile(cgms, hour + "_1D_ASSEMBLED_SV_001.xml");
		run("Type3 CGM " + hour, svXml, eqBd, beEq, nlEq, beSsh, nlSsh, tp, svXml);
	}
}
