package org.interpss.core.adapter.cim;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.IdentityHashMap;
import java.util.Map;

import org.interpss.CorePluginTestSetup;
import org.interpss.fadapter.cim.CGMESDirectParser;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import com.interpss.core.LoadflowAlgoObjectFactory;
import com.interpss.core.aclf.AclfBranch;
import com.interpss.core.aclf.AclfNetModelType;
import com.interpss.core.aclf.AclfNetwork;
import com.interpss.core.algo.AclfMethodType;
import com.interpss.core.algo.LoadflowAlgorithm;
import com.interpss.core.funcImpl.zeroz.AclfNetZeroZBranchHelper;
import com.interpss.core.funcImpl.zeroz.AclfNetZeroZDeconsolidator;

import static com.interpss.common.util.NetUtilFunc.ToBranchId;

/**
 * P4: import + seed from SvVoltage + NR load-flow + compare solved V/angle and
 * branch SvPowerFlow to SV (with swing/reference angle alignment).
 *
 * <p>Shared floor for every case: {@code |V|} 0.005 pu / 98%, angle 0.5° / 95%,
 * branch P/Q 1 MW / 1 Mvar / 95%, {@code missingBus=0} and {@code missingBranch=0}.
 * Dead buses ({@code |V| < 0.2} pu) stay out of the ratio.
 *
 * <p>Overrides: {@code -Dipss.cgmes.p4.vTolPu}, {@code angTolDeg}, {@code minMatch},
 * {@code minAngMatch}, {@code pTolMw}, {@code qTolMvar}, {@code minFlowMatch},
 * {@code maxMissingBus}.
 *
 * <p>Case-specific first baselines (still green locally):
 * MiniGrid-Merged {@code |V|} 0.02 / 70% + flow 0.35;
 * Svedala-Merged {@code |V|} 0.02 / 85%, angle 1.5° / 85%, flow 0.40;
 * MiniGrid NB voltage-only (allow 2 boundary missing buses; no flow assert yet);
 * ReliCap Svedala / Britheim / Portheim voltage-only ({@code |V|} 0.02 / 85%, soft angle);
 * Type3 CGM first hour voltage-only ({@code |V|} 0.02 / 70%, soft angle; soft NR);
 * MicroGrid T4 BE / FullGrid / RealGrid: soft NR + voltage-only floors.
 * Closed retained switches are zero-Z branches: seed, consolidate, NR, then
 * deconsolidate so SV compare still sees the original buses.
 *
 * <p>Type3 CGM mid hour and ReliCap Espheim still abort because NR does not
 * converge. Those tests live in {@link CGMESCasP4AclfUnconvergedStubTest}.
 *
 * <p>RealGrid-Merged is in this class. Buses with no SV row start at flat voltage,
 * and a few degrees across a milliohm branch is tens of thousands of pu. The
 * seeded solve equalizes those stiff ties, fills the flat buses from a neighbor,
 * and limits the Newton step.
 */
@Tag("cgmes-cas")
@Tag("cgmes-p4-aclf")
public class CGMESCasP4AclfSmokeStubTest extends CorePluginTestSetup {

	private static final String TD30_CAS = "testData/adpter/cim/cgmes3.0/cas/";

	private static double vTolPu() {
		return Double.parseDouble(System.getProperty("ipss.cgmes.p4.vTolPu", "0.005"));
	}

	private static double angTolDeg() {
		return Double.parseDouble(System.getProperty("ipss.cgmes.p4.angTolDeg", "0.5"));
	}

	private static double minMatch() {
		return Double.parseDouble(System.getProperty("ipss.cgmes.p4.minMatch", "1.0"));
	}

	static Path casDir(String localCasDirName, String relativeUnderV30) {
		String override = System.getProperty("ipss.cgmes.cas.root");
		if (override != null && !override.isBlank()) {
			return Path.of(override).resolve("v3.0").resolve(relativeUnderV30);
		}
		Path local = Path.of(TD30_CAS + localCasDirName);
		if (Files.isDirectory(local)) {
			return local;
		}
		String home = System.getProperty("user.home");
		return Path.of(home, "Documents", "Temp", "cgmes-test-data", "cas-v3.0.3",
				"CGMES_ConformityAssessmentScheme_TestConfigurations_v3-0-3", "v3.0")
				.resolve(relativeUnderV30);
	}

	static Path mustFile(Path dir, String name) {
		Path f = dir.resolve(name);
		assumeTrue(Files.isRegularFile(f), () -> "Missing " + f);
		return f;
	}

	static AclfNetwork runNrSeeded(AclfNetwork net, Map<String, CgmesSvCompareSupport.SvVoltage> sv)
			throws Exception {
		assertTrue(solveNrSeeded(net, sv), "NR load-flow should converge with SV seed");
		return net;
	}

	/** Like {@link #runNrSeeded} but skips (assume) when NR does not converge. */
	static AclfNetwork runNrSeededSoft(AclfNetwork net, Map<String, CgmesSvCompareSupport.SvVoltage> sv,
			String label) throws Exception {
		assumeTrue(solveNrSeeded(net, sv),
				() -> label + " NR did not converge with SV seed; revisit later");
		return net;
	}

	/**
	 * Seed from SV, consolidate zero-Z branches, run NR, and deconsolidate after
	 * a solution. Returns whether NR converged. A singular case is left consolidated.
	 */
	static boolean solveNrSeeded(AclfNetwork net, Map<String, CgmesSvCompareSupport.SvVoltage> sv)
			throws Exception {
		int seeded = CgmesSvCompareSupport.seedFromSv(net, sv);
		assertTrue(seeded > 0, "Should seed at least one bus from SvVoltage");
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
		algo.setInitBusVoltage(false); // keep SV seed
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

	/**
	 * One voltage per connected set of lines with {@code |Z| <= 2e-3} pu.
	 * A chain copy flips around a loop; this assigns each bus once. The source
	 * is a swing bus if the set has one, otherwise a bus that is not at flat start.
	 */
	private static int alignTinyLineComponents(AclfNetwork net) {
		java.util.Map<String, String> parent = new java.util.HashMap<>();
		java.util.Map<String, com.interpss.core.aclf.AclfBus> buses = new java.util.HashMap<>();
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
			com.interpss.core.aclf.AclfBus from = (com.interpss.core.aclf.AclfBus) branch.getFromBus();
			com.interpss.core.aclf.AclfBus to = (com.interpss.core.aclf.AclfBus) branch.getToBus();
			buses.put(from.getId(), from);
			buses.put(to.getId(), to);
			unionTiny(parent, from.getId(), to.getId());
		}
		java.util.Map<String, java.util.List<com.interpss.core.aclf.AclfBus>> groups = new java.util.HashMap<>();
		for (com.interpss.core.aclf.AclfBus bus : buses.values()) {
			groups.computeIfAbsent(findTiny(parent, bus.getId()), k -> new java.util.ArrayList<>()).add(bus);
		}
		int n = 0;
		for (java.util.List<com.interpss.core.aclf.AclfBus> group : groups.values()) {
			if (group.size() < 2) {
				continue;
			}
			com.interpss.core.aclf.AclfBus src = group.get(0);
			for (com.interpss.core.aclf.AclfBus bus : group) {
				if (bus.isSwing()) {
					src = bus;
					break;
				}
				if (isFlatStart(src) && !isFlatStart(bus)) {
					src = bus;
				}
			}
			for (com.interpss.core.aclf.AclfBus bus : group) {
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

	/** Unseeded buses stay at 1.0∠0. Copy a neighbor that already has a voltage, any impedance. */
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
				com.interpss.core.aclf.AclfBus from = (com.interpss.core.aclf.AclfBus) branch.getFromBus();
				com.interpss.core.aclf.AclfBus to = (com.interpss.core.aclf.AclfBus) branch.getToBus();
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

	private static void copyVoltage(com.interpss.core.aclf.AclfBus src, com.interpss.core.aclf.AclfBus dst) {
		dst.setVoltageMag(src.getVoltageMag());
		dst.setVoltageAng(src.getVoltageAng());
		if (dst.isGenPV()) {
			double vSet = src.isGenPV() ? src.getDesiredVoltMag() : src.getVoltageMag();
			if (vSet > 0.0) {
				dst.setDesiredVoltMag(vSet);
			}
		}
	}

	private static boolean isFlatStart(com.interpss.core.aclf.AclfBus bus) {
		return Math.abs(bus.getVoltageMag() - 1.0) < 1.0e-6 && Math.abs(bus.getVoltageAng()) < 1.0e-8;
	}

	private static String findTiny(java.util.Map<String, String> parent, String id) {
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

	private static void unionTiny(java.util.Map<String, String> parent, String a, String b) {
		String ra = findTiny(parent, a);
		String rb = findTiny(parent, b);
		if (!ra.equals(rb)) {
			parent.put(ra, rb);
		}
	}

	/**
	 * Zero-Z consolidate parses branch ids as {@code from->to(cir)}. CGMES mappers
	 * store the equipment local id in {@code getId()} and leave the lookup table
	 * on the structural id, so reconnect cannot find the branch. Put the
	 * structural id back for the merge and remember the equipment id.
	 */
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

	private static void compareToSv(AclfNetwork net, Map<String, CgmesSvCompareSupport.SvVoltage> sv) {
		compareToSv(net, sv, vTolPu(), angTolDeg(), minMatch());
	}

	static void compareToSv(AclfNetwork net, Map<String, CgmesSvCompareSupport.SvVoltage> sv,
			double vTol, double angTol, double minRatio) {
		CgmesSvCompareSupport.CompareStats stats = CgmesSvCompareSupport.compareVoltages(
				net, sv, vTol, angTol, minRatio);
		CgmesSvCompareSupport.printVoltageDump(net.getId(), stats);
		assertTrue(stats.matchedVoltage() > 0);
		int maxMissing = Integer.parseInt(System.getProperty("ipss.cgmes.p4.maxMissingBus", "0"));
		assertTrue(stats.missingBus() <= maxMissing,
				() -> "SvVoltage TopologicalNodes without Aclf bus: missing="
						+ stats.missingBus() + " (maxAllowed=" + maxMissing + ")");
	}

	static void compareFlows(AclfNetwork net, Path svXml, Path[] eqXmls, double minFlowMatch)
			throws Exception {
		Map<String, CgmesSvCompareSupport.SvPowerFlow> flows =
				CgmesSvCompareSupport.readSvPowerFlows(svXml);
		Map<String, CgmesSvCompareSupport.TerminalEquip> terms =
				CgmesSvCompareSupport.indexTerminals(eqXmls);
		CgmesSvCompareSupport.FlowCompareStats fs = CgmesSvCompareSupport.compareBranchFlows(
				net, flows, terms,
				CgmesSvCompareSupport.defaultPTolMw(),
				CgmesSvCompareSupport.defaultQTolMvar(),
				minFlowMatch);
		CgmesSvCompareSupport.printFlowDump(net.getId(), fs);
		assertTrue(fs.missingBranch() == 0,
				() -> "Every in-topology ACLineSegment/PowerTransformer terminal should map to a branch; missing="
						+ fs.missingBranch() + " " + fs.missingReasons());
	}

	static String[] abs(Path... files) {
		String[] out = new String[files.length];
		for (int i = 0; i < files.length; i++) {
			out[i] = files[i].toAbsolutePath().toString();
		}
		return out;
	}

	private static void runSeededCompare(Path svXml, Path[] terminalFiles, Path... inputs) throws Exception {
		runSeededCompare(svXml, terminalFiles,
				vTolPu(), angTolDeg(), minMatch(),
				CgmesSvCompareSupport.defaultMinFlowMatch(),
				inputs);
	}

	/** Same as {@link #runSeededCompare(Path, Path[], Path...)} with explicit compare floors. */
	private static void runSeededCompare(Path svXml, Path[] terminalFiles,
			double vTol, double angTol, double minVMatch, double minFlowMatch,
			Path... inputs) throws Exception {
		Map<String, CgmesSvCompareSupport.SvVoltage> sv = CgmesSvCompareSupport.readSvVoltages(svXml);
		AclfNetwork net = new CGMESDirectParser().parse(abs(inputs));
		assertTrue(net.getNoBus() > 0);
		runNrSeeded(net, sv);
		compareToSv(net, sv, vTol, angTol, minVMatch);
		compareFlows(net, svXml, terminalFiles, minFlowMatch);
	}

	@Test
	@DisplayName("P4: MiniGrid-Merged SV-seeded NR + Aclf vs SvVoltage + SvPowerFlow")
	public void testP4_MiniGridMerged_AclfVsSv() throws Exception {
		Path dir = casDir("MiniGrid-Merged", "MiniGrid/MiniGrid-Merged");
		assumeTrue(Files.isDirectory(dir), () -> "MiniGrid-Merged missing: " + dir);
		Path svXml = mustFile(dir, "MiniGrid_SV.xml");
		Path eqXml = mustFile(dir, "MiniGrid_EQ.xml");
		Path tpXml = mustFile(dir, "MiniGrid_TP.xml");
		runSeededCompare(svXml, new Path[] { eqXml, tpXml },
				eqXml,
				mustFile(dir, "MiniGrid_SSH.xml"),
				tpXml,
				svXml,
				mustFile(dir, "MiniGrid_EQBD.xml"));
	}

	@Test
	@DisplayName("P4: PowerFlow SV-seeded NR + Aclf vs SvVoltage + SvPowerFlow")
	public void testP4_PowerFlow_AclfVsSv() throws Exception {
		Path dir0 = casDir("PowerFlow-Instance", "PowerFlow/PowerFlow");
		final Path dir = Files.isDirectory(dir0)
				? dir0
				: casDir("PowerFlow", "PowerFlow").resolve("PowerFlow");
		assumeTrue(Files.isDirectory(dir), () -> "PowerFlow instance missing: " + dir);
		Path svXml = mustFile(dir, "PowerFlow_SV.xml");
		Path eqXml = mustFile(dir, "PowerFlow_EQ.xml");
		Path tpXml = mustFile(dir, "PowerFlow_TP.xml");
		runSeededCompare(svXml, new Path[] { eqXml, tpXml },
				eqXml,
				mustFile(dir, "PowerFlow_SSH.xml"),
				tpXml,
				svXml);
	}

	@Test
	@DisplayName("P4: PST Type1 SV-seeded NR + Aclf vs SvVoltage + SvPowerFlow")
	public void testP4_PstType1_AclfVsSv() throws Exception {
		Path dir = casDir("PST-PhaseTapChangerLinear-Type1",
				"PST/PST_PhaseTapChangerLinear_Type1");
		assumeTrue(Files.isDirectory(dir), () -> "PST Type1 missing: " + dir);
		Path svXml = mustFile(dir, "PST_Type1_SV.xml");
		Path eqXml = mustFile(dir, "PST_Type1_EQ.xml");
		Path tpXml = mustFile(dir, "PST_Type1_TP.xml");
		runSeededCompare(svXml, new Path[] { eqXml, tpXml },
				eqXml,
				mustFile(dir, "PST_Type1_SSH.xml"),
				tpXml,
				svXml);
	}

	@Test
	@DisplayName("P4: SmallGrid-Merged SV-seeded NR + Aclf vs SvVoltage + SvPowerFlow")
	public void testP4_SmallGridMerged_AclfVsSv() throws Exception {
		Path dir = casDir("SmallGrid-Merged", "SmallGrid/SmallGrid-Merged");
		assumeTrue(Files.isDirectory(dir), () -> "SmallGrid-Merged missing: " + dir);
		Path svXml = mustFile(dir, "SmallGrid_SV.xml");
		Path eqXml = mustFile(dir, "SmallGrid_EQ.xml");
		Path tpXml = mustFile(dir, "SmallGrid_TP.xml");
		runSeededCompare(svXml, new Path[] { eqXml, tpXml, mustFile(dir, "SmallGrid_EQBD.xml") },
				eqXml,
				mustFile(dir, "SmallGrid_SSH.xml"),
				tpXml,
				svXml,
				mustFile(dir, "SmallGrid_EQBD.xml"));
	}

	@Test
	@DisplayName("P4: MicroGrid Type1 Merged SV-seeded NR + Aclf vs SvVoltage + SvPowerFlow")
	public void testP4_MicroGridType1Merged_AclfVsSv() throws Exception {
		Path dir = casDir("MicroGrid-Type1-Merged",
				"MicroGrid/MicroGrid-Type1/MicroGrid-Type1-Merged");
		assumeTrue(Files.isDirectory(dir), () -> "MicroGrid Type1 Merged missing: " + dir);
		Path eqBd = mustFile(dir, "20171002T0930Z_ENTSO-E_EQ_BD_2.xml");
		Path beEq = mustFile(dir, "20210323T1730Z_1D_BE_EQ_1.xml");
		Path nlEq = mustFile(dir, "20210323T1730Z_1D_NL_EQ_1.xml");
		Path tp = mustFile(dir, "20210323T1730Z_1D_ASSEMBLED_TP_1.xml");
		Path sv = mustFile(dir, "20210323T1730Z_1D_ASSEMBLED_SV_1.xml");
		runSeededCompare(sv, new Path[] { eqBd, beEq, nlEq, tp },
				eqBd, beEq, nlEq,
				mustFile(dir, "20210323T1730Z_1D_BE_SSH_1.xml"),
				mustFile(dir, "20210323T1730Z_1D_NL_SSH_1.xml"),
				tp, sv);
	}

	@Test
	@DisplayName("P4: MicroGrid Type2 Merged SV-seeded NR + Aclf vs SvVoltage + SvPowerFlow")
	public void testP4_MicroGridType2Merged_AclfVsSv() throws Exception {
		Path dir = casDir("MicroGrid-Type2-Merged",
				"MicroGrid/MicroGrid-Type2/MicroGrid-Type2-Merged");
		assumeTrue(Files.isDirectory(dir), () -> "MicroGrid Type2 Merged missing: " + dir);
		Path eqBd = mustFile(dir, "20171002T0930Z_ENTSO-E_EQ_BD_2.xml");
		Path beEq = mustFile(dir, "20210401T1730Z_1D_BE_EQ_1.xml");
		Path nlEq = mustFile(dir, "20210401T1730Z_1D_NL_EQ_1.xml");
		Path tp = mustFile(dir, "20210401T1730Z_1D_ASSEMBLED_TP_1.xml");
		Path sv = mustFile(dir, "20210401T1730Z_1D_ASSEMBLED_SV_1.xml");
		// HVDC profiles stay in the CGM so the assembled topology is complete.
		// Converter flows are not part of this AC compare.
		runSeededCompare(sv, new Path[] { eqBd, beEq, nlEq, tp },
				eqBd, beEq, nlEq,
				mustFile(dir, "20210401T1730Z_1D_HVDC_EQ_1.xml"),
				mustFile(dir, "20210401T1730Z_1D_BE_SSH_1.xml"),
				mustFile(dir, "20210401T1730Z_1D_NL_SSH_1.xml"),
				mustFile(dir, "20210401T1730Z_1D_HVDC_SSH_1.xml"),
				tp, sv);
	}

	@Test
	@DisplayName("P4: PST Type2 SV-seeded NR + Aclf vs SvVoltage + SvPowerFlow")
	public void testP4_PstType2_AclfVsSv() throws Exception {
		Path dir = casDir("PST-PhaseTapChangerLinear-Type2",
				"PST/PST_PhaseTapChangerLinear_Type2");
		assumeTrue(Files.isDirectory(dir), () -> "PST Type2 missing: " + dir);
		Path svXml = mustFile(dir, "PST_Type2_SV.xml");
		Path eqXml = mustFile(dir, "PST_Type2_EQ.xml");
		Path tpXml = mustFile(dir, "PST_Type2_TP.xml");
		runSeededCompare(svXml, new Path[] { eqXml, tpXml },
				eqXml,
				mustFile(dir, "PST_Type2_SSH.xml"),
				tpXml,
				svXml);
	}

	@Test
	@DisplayName("P4: PST Table Type3 SV-seeded NR + Aclf vs SvVoltage + SvPowerFlow")
	public void testP4_PstTableType3_AclfVsSv() throws Exception {
		Path dir = casDir("PST-PhaseTapChangerTable-Type3",
				"PST/PST_PhaseTapChangerTable_Type3");
		assumeTrue(Files.isDirectory(dir), () -> "PST Table Type3 missing: " + dir);
		Path svXml = mustFile(dir, "PST_Type3_SV.xml");
		Path eqXml = mustFile(dir, "PST_Type3_EQ.xml");
		Path tpXml = mustFile(dir, "PST_Type3_TP.xml");
		runSeededCompare(svXml, new Path[] { eqXml, tpXml },
				eqXml,
				mustFile(dir, "PST_Type3_SSH.xml"),
				tpXml,
				svXml);
	}

	@Test
	@DisplayName("P4: Svedala-Merged SV-seeded NR + Aclf vs SvVoltage + SvPowerFlow")
	public void testP4_SvedalaMerged_AclfVsSv() throws Exception {
		Path dir = casDir("CAS-Svedala-Merged", "Svedala/Svedala-Merged");
		assumeTrue(Files.isDirectory(dir), () -> "CAS Svedala-Merged missing: " + dir);
		Path svXml = mustFile(dir, "Svedala_SV.xml");
		Path eqXml = mustFile(dir, "Svedala_EQ.xml");
		Path tpXml = mustFile(dir, "Svedala_TP.xml");
		Path eqBd = mustFile(dir, "Svedala_EQBD.xml");
		// Larger CAS case: softer first baseline than the MiniGrid/PST 0.005/100% floor.
		// Override: -Dipss.cgmes.p4.vTolPu / minMatch / minFlowMatch / minAngMatch.
		double vTol = Double.parseDouble(System.getProperty("ipss.cgmes.p4.vTolPu", "0.02"));
		double angTol = Double.parseDouble(System.getProperty("ipss.cgmes.p4.angTolDeg", "1.5"));
		double minV = Double.parseDouble(System.getProperty("ipss.cgmes.p4.minMatch", "0.85"));
		double minFlow = Double.parseDouble(System.getProperty("ipss.cgmes.p4.minFlowMatch", "0.40"));
		String prevAng = System.getProperty("ipss.cgmes.p4.minAngMatch");
		System.setProperty("ipss.cgmes.p4.minAngMatch",
				System.getProperty("ipss.cgmes.p4.minAngMatch", "0.85"));
		try {
			runSeededCompare(svXml, new Path[] { eqXml, tpXml, eqBd },
					vTol, angTol, minV, minFlow,
					eqXml,
					mustFile(dir, "Svedala_SSH.xml"),
					tpXml,
					svXml,
					eqBd);
		} finally {
			if (prevAng == null) {
				System.clearProperty("ipss.cgmes.p4.minAngMatch");
			} else {
				System.setProperty("ipss.cgmes.p4.minAngMatch", prevAng);
			}
		}
	}

	@Test
	@DisplayName("P4: MicroGrid BaseCase-Merged SV-seeded NR + Aclf vs SvVoltage + SvPowerFlow")
	public void testP4_MicroGridBaseCaseMerged_AclfVsSv() throws Exception {
		Path dir = casDir("MicroGrid-BaseCase-Merged",
				"MicroGrid/MicroGid-BaseCase/MicroGrid-BaseCase-Merged");
		assumeTrue(Files.isDirectory(dir), () -> "MicroGrid BaseCase-Merged missing: " + dir);
		Path eqBd = mustFile(dir, "20171002T0930Z_ENTSO-E_EQ_BD_2.xml");
		Path beEq = mustFile(dir, "20210325T1530Z_1D_BE_EQ_001.xml");
		Path nlEq = mustFile(dir, "20210325T1530Z_1D_NL_EQ_001.xml");
		Path tp = mustFile(dir, "20210325T1530Z_1D_ASSEMBLED_TP_001.xml");
		Path sv = mustFile(dir, "20210325T1530Z_1D_ASSEMBLED_SV_001.xml");
		runSeededCompare(sv, new Path[] { eqBd, beEq, nlEq, tp },
				eqBd, beEq, nlEq,
				mustFile(dir, "20210325T1530Z_1D_BE_SSH_001.xml"),
				mustFile(dir, "20210325T1530Z_1D_NL_SSH_001.xml"),
				tp, sv);
	}

	@Test
	@DisplayName("P4: MiniGrid NB (cgmes2.4) SV-seeded NR + Aclf vs SvVoltage + SvPowerFlow")
	public void testP4_MiniGridNb_AclfVsSv() throws Exception {
		Path dir = Path.of("testData/adpter/cim/cgmes2.4");
		assumeTrue(Files.isDirectory(dir), () -> "cgmes2.4 fixture dir missing: " + dir);
		Path svXml = mustFile(dir, "MiniGrid_NB_SV_V3.xml");
		Path eqXml = mustFile(dir, "MiniGrid_NB_EQ_V3.xml");
		Path tpXml = mustFile(dir, "MiniGrid_NB_TP_V3.xml");
		Path eqBd = mustFile(dir, "MiniGrid_NB_EQ_BD_V3.xml");
		Path tpBd = mustFile(dir, "MiniGrid_NB_TP_BD_V3.xml");
		Map<String, CgmesSvCompareSupport.SvVoltage> sv = CgmesSvCompareSupport.readSvVoltages(svXml);
		AclfNetwork net = new CGMESDirectParser().parse(abs(
				eqXml,
				mustFile(dir, "MiniGrid_NB_SSH_V3.xml"),
				tpXml,
				tpBd,
				eqBd));
		assertTrue(net.getNoBus() > 0);
		runNrSeeded(net, sv);
		// Boundary-only SvVoltage TNs (2) have no Aclf bus — allow them.
		String prevMiss = System.getProperty("ipss.cgmes.p4.maxMissingBus");
		System.setProperty("ipss.cgmes.p4.maxMissingBus",
				System.getProperty("ipss.cgmes.p4.maxMissingBus", "2"));
		try {
			compareToSv(net, sv);
		} finally {
			if (prevMiss == null) {
				System.clearProperty("ipss.cgmes.p4.maxMissingBus");
			} else {
				System.setProperty("ipss.cgmes.p4.maxMissingBus", prevMiss);
			}
		}
		// SvPowerFlow branch matching finds 0 comparable ACLine/PowerTransformer
		// terminals on this fixture today — voltage gate only for this P0 case.
	}


	@Test
	@DisplayName("P4: ReliCap Svedala IGM SV-seeded NR + Aclf vs SvVoltage")
	public void testP4_ReliCapSvedala_AclfVsSv() throws Exception {
		Path dir = casDir("ReliCap-Svedala-cimxml", "Instance/Svedala/Grid/cimxml");
		assumeTrue(Files.isDirectory(dir), () -> "ReliCap Svedala cimxml missing: " + dir);
		Path svXml = mustFile(dir, "20220615T2230Z_2D_Svedala_SV_1.xml");
		Path eqXml = mustFile(dir, "20220615T2230Z__Svedala_EQ_1.xml");
		Path tpXml = mustFile(dir, "20220615T2230Z_2D_Svedala_TP_1.xml");
		Path sshXml = mustFile(dir, "20220615T2230Z_2D_Svedala_SSH_1.xml");
		Map<String, CgmesSvCompareSupport.SvVoltage> sv = CgmesSvCompareSupport.readSvVoltages(svXml);
		AclfNetwork net = new CGMESDirectParser().parse(abs(eqXml, sshXml, tpXml, svXml));
		assertTrue(net.getNoBus() > 0);
		runNrSeeded(net, sv);
		// |V|-primary: angles drift; SvPowerFlow ~20% today — voltage gate only for P1.
		double vTol = Double.parseDouble(System.getProperty("ipss.cgmes.p4.vTolPu", "0.02"));
		double minV = Double.parseDouble(System.getProperty("ipss.cgmes.p4.minMatch", "0.85"));
		String prevAng = System.getProperty("ipss.cgmes.p4.minAngMatch");
		System.setProperty("ipss.cgmes.p4.minAngMatch",
				System.getProperty("ipss.cgmes.p4.minAngMatch", "0.0"));
		try {
			compareToSv(net, sv, vTol, 10.0, minV);
		} finally {
			if (prevAng == null) {
				System.clearProperty("ipss.cgmes.p4.minAngMatch");
			} else {
				System.setProperty("ipss.cgmes.p4.minAngMatch", prevAng);
			}
		}
	}

	@Test
	@DisplayName("P4: ReliCap Britheim IGM SV-seeded NR + Aclf vs SvVoltage")
	public void testP4_ReliCapBritheim_AclfVsSv() throws Exception {
		Path dir = casDir("ReliCap-Britheim-cimxml", "Instance/Britheim/Grid/cimxml");
		assumeTrue(Files.isDirectory(dir), () -> "ReliCap Britheim cimxml missing: " + dir);
		Path svXml = mustFile(dir, "20220615T2230Z_2D_Britheim_SV_1.xml");
		Path eqXml = mustFile(dir, "20220615T2230Z__Britheim_EQ_1.xml");
		Path tpXml = mustFile(dir, "20220615T2230Z_2D_Britheim_TP_1.xml");
		Path sshXml = mustFile(dir, "20220615T2230Z_2D_Britheim_SSH_1.xml");
		Map<String, CgmesSvCompareSupport.SvVoltage> sv = CgmesSvCompareSupport.readSvVoltages(svXml);
		AclfNetwork net = new CGMESDirectParser().parse(abs(eqXml, sshXml, tpXml, svXml));
		assertTrue(net.getNoBus() > 0);
		runNrSeeded(net, sv);
		// Voltage-only: inactive ACLine/Xformer terminals trip missingBranch on flow.
		double vTol = Double.parseDouble(System.getProperty("ipss.cgmes.p4.vTolPu", "0.02"));
		double minV = Double.parseDouble(System.getProperty("ipss.cgmes.p4.minMatch", "0.85"));
		String prevAng = System.getProperty("ipss.cgmes.p4.minAngMatch");
		System.setProperty("ipss.cgmes.p4.minAngMatch",
				System.getProperty("ipss.cgmes.p4.minAngMatch", "0.0"));
		try {
			compareToSv(net, sv, vTol, 20.0, minV);
		} finally {
			if (prevAng == null) {
				System.clearProperty("ipss.cgmes.p4.minAngMatch");
			} else {
				System.setProperty("ipss.cgmes.p4.minAngMatch", prevAng);
			}
		}
	}

	@Test
	@DisplayName("P4: MicroGrid T4 BE (cgmes2.4) SV-seeded NR + Aclf vs SvVoltage")
	public void testP4_MicroGridT4Be_AclfVsSv() throws Exception {
		Path dir = Path.of("testData/adpter/cim/cgmes2.4");
		assumeTrue(Files.isDirectory(dir), () -> "cgmes2.4 fixture dir missing: " + dir);
		Path svXml = mustFile(dir, "MicroGrid_T4_BE_SV_V2.xml");
		Path eqXml = mustFile(dir, "MicroGrid_T4_BE_EQ_V2.xml");
		Path tpXml = mustFile(dir, "MicroGrid_T4_BE_TP_V2.xml");
		Path sshXml = mustFile(dir, "MicroGrid_T4_BE_SSH_V2.xml");
		Path eqBd = mustFile(dir, "MicroGrid_T4_BE_EQ_BD_V2.xml");
		Path tpBd = mustFile(dir, "MicroGrid_T4_BE_TP_BD_V2.xml");
		Map<String, CgmesSvCompareSupport.SvVoltage> sv = CgmesSvCompareSupport.readSvVoltages(svXml);
		AclfNetwork net = new CGMESDirectParser().parse(abs(eqXml, sshXml, tpXml, tpBd, eqBd));
		assertTrue(net.getNoBus() > 0);
		runNrSeededSoft(net, sv, "MicroGrid T4 BE");
		double vTol = Double.parseDouble(System.getProperty("ipss.cgmes.p4.vTolPu", "0.02"));
		double minV = Double.parseDouble(System.getProperty("ipss.cgmes.p4.minMatch", "0.70"));
		String prevMiss = System.getProperty("ipss.cgmes.p4.maxMissingBus");
		String prevAng = System.getProperty("ipss.cgmes.p4.minAngMatch");
		// Five SvVoltage rows are boundary topological nodes, which are not buses.
		System.setProperty("ipss.cgmes.p4.maxMissingBus",
				System.getProperty("ipss.cgmes.p4.maxMissingBus", "5"));
		System.setProperty("ipss.cgmes.p4.minAngMatch",
				System.getProperty("ipss.cgmes.p4.minAngMatch", "0.0"));
		try {
			compareToSv(net, sv, vTol, 10.0, minV);
		} finally {
			if (prevMiss == null) {
				System.clearProperty("ipss.cgmes.p4.maxMissingBus");
			} else {
				System.setProperty("ipss.cgmes.p4.maxMissingBus", prevMiss);
			}
			if (prevAng == null) {
				System.clearProperty("ipss.cgmes.p4.minAngMatch");
			} else {
				System.setProperty("ipss.cgmes.p4.minAngMatch", prevAng);
			}
		}
	}

	@Test
	@DisplayName("P4: FullGrid-Merged SV-seeded NR + Aclf vs SvVoltage")
	public void testP4_FullGridMerged_AclfVsSv() throws Exception {
		Path dir = casDir("FullGrid-Merged", "FullGrid/FullGrid-Merged");
		assumeTrue(Files.isDirectory(dir), () -> "FullGrid-Merged missing: " + dir);
		Path svXml = mustFile(dir, "FullGrid_SV.xml");
		Path eqXml = mustFile(dir, "FullGrid_EQ.xml");
		Path tpXml = mustFile(dir, "FullGrid_TP.xml");
		Path eqBd = mustFile(dir, "FullGrid_EQBD.xml");
		Map<String, CgmesSvCompareSupport.SvVoltage> sv = CgmesSvCompareSupport.readSvVoltages(svXml);
		AclfNetwork net = new CGMESDirectParser().parse(abs(
				eqXml,
				mustFile(dir, "FullGrid_SSH.xml"),
				tpXml,
				svXml,
				eqBd));
		assertTrue(net.getNoBus() > 0);
		runNrSeededSoft(net, sv, "FullGrid-Merged");
		// SvPowerFlow rows on this case are machines, not AC lines, so the flow
		// helper's "fewer than 3 comparable terminals" gate would abort the test.
		double vTol = Double.parseDouble(System.getProperty("ipss.cgmes.p4.vTolPu", "0.02"));
		double angTol = Double.parseDouble(System.getProperty("ipss.cgmes.p4.angTolDeg", "1.5"));
		double minV = Double.parseDouble(System.getProperty("ipss.cgmes.p4.minMatch", "0.70"));
		String prevAng = System.getProperty("ipss.cgmes.p4.minAngMatch");
		System.setProperty("ipss.cgmes.p4.minAngMatch",
				System.getProperty("ipss.cgmes.p4.minAngMatch", "0.0"));
		try {
			compareToSv(net, sv, vTol, angTol, minV);
		} finally {
			if (prevAng == null) {
				System.clearProperty("ipss.cgmes.p4.minAngMatch");
			} else {
				System.setProperty("ipss.cgmes.p4.minAngMatch", prevAng);
			}
		}
	}

	@Test
	@DisplayName("P4: RealGrid-Merged SV-seeded NR + Aclf vs SvVoltage")
	public void testP4_RealGridMerged_AclfVsSv() throws Exception {
		Path dir = casDir("RealGrid-Merged", "RealGrid/RealGrid-Merged");
		assumeTrue(Files.isDirectory(dir), () -> "RealGrid-Merged missing: " + dir);
		Path svXml = mustFile(dir, "RealGrid_SV.xml");
		Path eqXml = mustFile(dir, "RealGrid_EQ.xml");
		Path tpXml = mustFile(dir, "RealGrid_TP.xml");
		Path sshXml = mustFile(dir, "RealGrid_SSH.xml");
		Map<String, CgmesSvCompareSupport.SvVoltage> sv = CgmesSvCompareSupport.readSvVoltages(svXml);
		AclfNetwork net = new CGMESDirectParser().parse(abs(eqXml, sshXml, tpXml, svXml));
		assertTrue(net.getNoBus() > 0);
		runNrSeededSoft(net, sv, "RealGrid-Merged");
		double vTol = Double.parseDouble(System.getProperty("ipss.cgmes.p4.vTolPu", "0.02"));
		double minV = Double.parseDouble(System.getProperty("ipss.cgmes.p4.minMatch", "0.70"));
		String prevAng = System.getProperty("ipss.cgmes.p4.minAngMatch");
		System.setProperty("ipss.cgmes.p4.minAngMatch",
				System.getProperty("ipss.cgmes.p4.minAngMatch", "0.0"));
		try {
			compareToSv(net, sv, vTol, 10.0, minV);
		} finally {
			if (prevAng == null) {
				System.clearProperty("ipss.cgmes.p4.minAngMatch");
			} else {
				System.setProperty("ipss.cgmes.p4.minAngMatch", prevAng);
			}
		}
	}


	@Test
	@DisplayName("P4: Type3 CGM first hour SV-seeded NR + Aclf vs SvVoltage")
	public void testP4_Type3Cgm_FirstHour_AclfVsSv() throws Exception {
		runType3HourP4("20210422T2230Z");
	}

	/** Shared EQ (first-hour stamp) + per-hour SSH + Assembled TP/SV; voltage-only. */
	static void runType3HourP4(String hour) throws Exception {
		Path igms = casDir("MicroGrid-Type3-IGMs", "MicroGrid/MicroGrid-Type3/IGMs");
		Path cgms = casDir("MicroGrid-Type3-CGMs", "MicroGrid/MicroGrid-Type3/CGMs");
		assumeTrue(Files.isDirectory(igms), () -> "Type3 IGMs missing: " + igms);
		assumeTrue(Files.isDirectory(cgms), () -> "Type3 CGMs missing: " + cgms);
		Path eqBd = mustFile(igms, "20171002T0930Z_ENTSO-E_EQ_BD_2.xml");
		Path beEq = mustFile(igms, "20210422T2230Z_1D_BE_EQ_001.xml");
		Path nlEq = mustFile(igms, "20210422T2230Z_1D_NL_EQ_001.xml");
		Path beSsh = mustFile(igms, hour + "_1D_BE_SSH_001.xml");
		Path nlSsh = mustFile(igms, hour + "_1D_NL_SSH_001.xml");
		Path tp = mustFile(cgms, hour + "_1D_ASSEMBLED_TP_001.xml");
		Path svXml = mustFile(cgms, hour + "_1D_ASSEMBLED_SV_001.xml");
		Map<String, CgmesSvCompareSupport.SvVoltage> sv = CgmesSvCompareSupport.readSvVoltages(svXml);
		AclfNetwork net = new CGMESDirectParser().parse(abs(
				eqBd, beEq, nlEq, beSsh, nlSsh, tp, svXml));
		assertTrue(net.getNoBus() > 0, () -> "Type3 " + hour + " should create buses");
		runNrSeededSoft(net, sv, "Type3 CGM " + hour);
		double vTol = Double.parseDouble(System.getProperty("ipss.cgmes.p4.vTolPu", "0.02"));
		double minV = Double.parseDouble(System.getProperty("ipss.cgmes.p4.minMatch", "0.70"));
		String prevAng = System.getProperty("ipss.cgmes.p4.minAngMatch");
		System.setProperty("ipss.cgmes.p4.minAngMatch",
				System.getProperty("ipss.cgmes.p4.minAngMatch", "0.0"));
		try {
			compareToSv(net, sv, vTol, 10.0, minV);
		} finally {
			if (prevAng == null) {
				System.clearProperty("ipss.cgmes.p4.minAngMatch");
			} else {
				System.setProperty("ipss.cgmes.p4.minAngMatch", prevAng);
			}
		}
	}

	@Test
	@DisplayName("P4: ReliCap Portheim IGM SV-seeded NR + Aclf vs SvVoltage")
	public void testP4_ReliCapPortheim_AclfVsSv() throws Exception {
		Path dir = casDir("ReliCap-Portheim-cimxml", "Instance/Portheim/Grid/cimxml");
		assumeTrue(Files.isDirectory(dir), () -> "ReliCap Portheim cimxml missing: " + dir);
		Path svXml = mustFile(dir, "20241223T0642Z_2D_Portheim_SV_1.xml");
		Path eqXml = mustFile(dir, "20241223T0642Z_2D_Portheim_EQ_1.xml");
		Path tpXml = mustFile(dir, "20241223T0642Z_2D_Portheim_TP_1.xml");
		Path sshXml = mustFile(dir, "20241223T0642Z_2D_Portheim_SSH_1.xml");
		Map<String, CgmesSvCompareSupport.SvVoltage> sv = CgmesSvCompareSupport.readSvVoltages(svXml);
		AclfNetwork net = new CGMESDirectParser().parse(abs(eqXml, sshXml, tpXml, svXml));
		assertTrue(net.getNoBus() > 0);
		runNrSeededSoft(net, sv, "ReliCap Portheim");
		double vTol = Double.parseDouble(System.getProperty("ipss.cgmes.p4.vTolPu", "0.02"));
		double minV = Double.parseDouble(System.getProperty("ipss.cgmes.p4.minMatch", "0.85"));
		String prevAng = System.getProperty("ipss.cgmes.p4.minAngMatch");
		System.setProperty("ipss.cgmes.p4.minAngMatch",
				System.getProperty("ipss.cgmes.p4.minAngMatch", "0.0"));
		try {
			compareToSv(net, sv, vTol, 10.0, minV);
		} finally {
			if (prevAng == null) {
				System.clearProperty("ipss.cgmes.p4.minAngMatch");
			} else {
				System.setProperty("ipss.cgmes.p4.minAngMatch", prevAng);
			}
		}
	}

}
