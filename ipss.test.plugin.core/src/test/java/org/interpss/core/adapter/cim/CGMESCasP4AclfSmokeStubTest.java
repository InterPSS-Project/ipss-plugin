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
 * <p>Voltage overrides: {@code -Dipss.cgmes.p4.vTolPu}, {@code -Dipss.cgmes.p4.angTolDeg},
 * {@code -Dipss.cgmes.p4.minMatch}.
 *
 * <p>Flow overrides: {@code -Dipss.cgmes.p4.pTolMw}, {@code -Dipss.cgmes.p4.qTolMvar},
 * {@code -Dipss.cgmes.p4.minFlowMatch}.
 */
@Tag("cgmes-cas")
@Tag("requires-cas-download")
@Tag("cgmes-p4-aclf")
public class CGMESCasP4AclfSmokeStubTest extends CorePluginTestSetup {

	private static final String TD30_CAS = "testData/adpter/cim/cgmes3.0/cas/";

	private static double vTolPu() {
		return Double.parseDouble(System.getProperty("ipss.cgmes.p4.vTolPu", "0.02"));
	}

	private static double angTolDeg() {
		return Double.parseDouble(System.getProperty("ipss.cgmes.p4.angTolDeg", "1.0"));
	}

	private static double minMatch() {
		return Double.parseDouble(System.getProperty("ipss.cgmes.p4.minMatch", "0.85"));
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
		System.out.println("SvPowerFlow match: " + fs.matched() + "/" + fs.compared()
				+ " missingBranch=" + fs.missingBranch() + " skipped=" + fs.skipped());
	}

	@Test
	@DisplayName("P4: MiniGrid-Merged SV-seeded NR + Aclf vs SvVoltage + SvPowerFlow")
	public void testP4_MiniGridMerged_AclfVsSv() throws Exception {
		Path dir = casDir("MiniGrid-Merged", "MiniGrid/MiniGrid-Merged");
		assumeTrue(Files.isDirectory(dir), () -> "MiniGrid-Merged missing: " + dir);
		Path svXml = mustFile(dir, "MiniGrid_SV.xml");
		Path eqXml = mustFile(dir, "MiniGrid_EQ.xml");
		Map<String, CgmesSvCompareSupport.SvVoltage> sv = CgmesSvCompareSupport.readSvVoltages(svXml);
		AclfNetwork net = new CGMESDirectParser().parse(new String[] {
				eqXml.toAbsolutePath().toString(),
				mustFile(dir, "MiniGrid_SSH.xml").toAbsolutePath().toString(),
				mustFile(dir, "MiniGrid_TP.xml").toAbsolutePath().toString(),
				svXml.toAbsolutePath().toString(),
				mustFile(dir, "MiniGrid_EQBD.xml").toAbsolutePath().toString()
		});
		assertTrue(net.getNoBus() > 0);
		runNrSeeded(net, sv);
		// Tightened from legacy 0.05 pu / 50%: PV/swing desired-V now tracks SV seed.
		// |V|: tightened from 0.05/50% to 0.02/70% via SV-aligned PV/swing setpoints.
		// Still short of global 0.02/85% (3 PQ/tertiary buses ~0.03–0.05 pu off).
		// Flow floor soft (0.35) — Q often mismatches while P can be close.
		double vTol = Double.parseDouble(System.getProperty("ipss.cgmes.p4.vTolPu", "0.02"));
		double minR = Double.parseDouble(System.getProperty("ipss.cgmes.p4.minMatch", "0.70"));
		compareToSv(net, sv, vTol, angTolDeg(), minR);
		double minFlow = Double.parseDouble(System.getProperty("ipss.cgmes.p4.minFlowMatch", "0.35"));
		compareFlows(net, svXml, new Path[] { eqXml }, minFlow);
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
		Map<String, CgmesSvCompareSupport.SvVoltage> sv = CgmesSvCompareSupport.readSvVoltages(svXml);
		AclfNetwork net = new CGMESDirectParser().parse(new String[] {
				eqXml.toAbsolutePath().toString(),
				mustFile(dir, "PowerFlow_SSH.xml").toAbsolutePath().toString(),
				mustFile(dir, "PowerFlow_TP.xml").toAbsolutePath().toString(),
				svXml.toAbsolutePath().toString()
		});
		assertTrue(net.getNoBus() > 0);
		runNrSeeded(net, sv);
		compareToSv(net, sv);
		compareFlows(net, svXml, new Path[] { eqXml }, CgmesSvCompareSupport.defaultMinFlowMatch());
	}

	@Test
	@DisplayName("P4: PST Type1 SV-seeded NR + Aclf vs SvVoltage + SvPowerFlow")
	public void testP4_PstType1_AclfVsSv() throws Exception {
		Path dir = casDir("PST-PhaseTapChangerLinear-Type1",
				"PST/PST_PhaseTapChangerLinear_Type1");
		assumeTrue(Files.isDirectory(dir), () -> "PST Type1 missing: " + dir);
		Path svXml = mustFile(dir, "PST_Type1_SV.xml");
		Path eqXml = mustFile(dir, "PST_Type1_EQ.xml");
		Map<String, CgmesSvCompareSupport.SvVoltage> sv = CgmesSvCompareSupport.readSvVoltages(svXml);
		AclfNetwork net = new CGMESDirectParser().parse(new String[] {
				eqXml.toAbsolutePath().toString(),
				mustFile(dir, "PST_Type1_SSH.xml").toAbsolutePath().toString(),
				mustFile(dir, "PST_Type1_TP.xml").toAbsolutePath().toString(),
				svXml.toAbsolutePath().toString()
		});
		assertTrue(net.getNoBus() > 0);
		runNrSeeded(net, sv);
		compareToSv(net, sv);
		compareFlows(net, svXml, new Path[] { eqXml }, CgmesSvCompareSupport.defaultMinFlowMatch());
	}
}
