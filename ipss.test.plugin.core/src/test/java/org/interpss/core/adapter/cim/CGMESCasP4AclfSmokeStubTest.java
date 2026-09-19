package org.interpss.core.adapter.cim;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

import org.interpss.CorePluginTestSetup;
import org.interpss.fadapter.cim.CGMESDirectParser;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import com.interpss.core.LoadflowAlgoObjectFactory;
import com.interpss.core.aclf.AclfNetwork;
import com.interpss.core.algo.AclfMethodType;
import com.interpss.core.algo.LoadflowAlgorithm;

/**
 * P4: import + seed from SvVoltage + NR load-flow + compare solved V/angle and
 * branch SvPowerFlow to SV (with swing/reference angle alignment).
 *
 * <p>Shared floor for every case: {@code |V|} 0.005 pu / 98%, angle 0.5° / 95%,
 * branch P/Q 1 MW / 1 Mvar / 95%, {@code missingBus=0} and {@code missingBranch=0}.
 * Dead buses ({@code |V| < 0.2} pu) stay out of the ratio.
 *
 * <p>Overrides: {@code -Dipss.cgmes.p4.vTolPu}, {@code angTolDeg}, {@code minMatch},
 * {@code minAngMatch}, {@code pTolMw}, {@code qTolMvar}, {@code minFlowMatch}.
 */
@Tag("cgmes-cas")
@Tag("requires-cas-download")
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
		return Double.parseDouble(System.getProperty("ipss.cgmes.p4.minMatch", "0.98"));
	}

	private static Path casDir(String localCasDirName, String relativeUnderV30) {
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

	private static Path mustFile(Path dir, String name) {
		Path f = dir.resolve(name);
		assumeTrue(Files.isRegularFile(f), () -> "Missing " + f);
		return f;
	}

	private static AclfNetwork runNrSeeded(AclfNetwork net, Map<String, CgmesSvCompareSupport.SvVoltage> sv)
			throws Exception {
		int seeded = CgmesSvCompareSupport.seedFromSv(net, sv);
		assertTrue(seeded > 0, "Should seed at least one bus from SvVoltage");
		LoadflowAlgorithm algo = LoadflowAlgoObjectFactory.createLoadflowAlgorithm(net);
		algo.setInitBusVoltage(false); // keep SV seed
		algo.setLfMethod(AclfMethodType.NR);
		algo.getDataCheckConfig().setAutoTurnLine2Xfr(true);
		algo.loadflow();
		assertTrue(net.isLfConverged(), "NR load-flow should converge with SV seed");
		return net;
	}

	private static void compareToSv(AclfNetwork net, Map<String, CgmesSvCompareSupport.SvVoltage> sv) {
		compareToSv(net, sv, vTolPu(), angTolDeg(), minMatch());
	}

	private static void compareToSv(AclfNetwork net, Map<String, CgmesSvCompareSupport.SvVoltage> sv,
			double vTol, double angTol, double minRatio) {
		CgmesSvCompareSupport.CompareStats stats = CgmesSvCompareSupport.compareVoltages(
				net, sv, vTol, angTol, minRatio);
		CgmesSvCompareSupport.printVoltageDump(net.getId(), stats);
		assertTrue(stats.matchedVoltage() > 0);
		assertTrue(stats.missingBus() == 0,
				() -> "Every SvVoltage TopologicalNode should map to an Aclf bus; missing="
						+ stats.missingBus());
	}

	private static void compareFlows(AclfNetwork net, Path svXml, Path[] eqXmls, double minFlowMatch)
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

	private static String[] abs(Path... files) {
		String[] out = new String[files.length];
		for (int i = 0; i < files.length; i++) {
			out[i] = files[i].toAbsolutePath().toString();
		}
		return out;
	}

	private static void runSeededCompare(Path svXml, Path[] terminalFiles, Path... inputs) throws Exception {
		Map<String, CgmesSvCompareSupport.SvVoltage> sv = CgmesSvCompareSupport.readSvVoltages(svXml);
		AclfNetwork net = new CGMESDirectParser().parse(abs(inputs));
		assertTrue(net.getNoBus() > 0);
		runNrSeeded(net, sv);
		compareToSv(net, sv);
		compareFlows(net, svXml, terminalFiles, CgmesSvCompareSupport.defaultMinFlowMatch());
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
}
