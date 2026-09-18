package org.interpss.core.adapter.cim;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.nio.file.Files;
import java.nio.file.Path;

import org.interpss.CorePluginTestSetup;
import org.interpss.fadapter.cim.CGMESDirectParser;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import com.interpss.core.aclf.AclfBranch;
import com.interpss.core.aclf.AclfBranchCode;
import com.interpss.core.aclf.AclfNetwork;
import com.interpss.core.net.OriginalDataFormat;

/**
 * P0–P3 expansion stubs for ENTSO-E CGMES coverage.
 *
 * <p>P0 wires orphaned in-repo CGMES 2.4 fixtures and the first official
 * CGMES 3.0 (CIM100) CAS MiniGrid pack from the local download tree.
 * P1 covers MicroGrid Type1 multi-MAS / merged CGM import.
 * P2 adds PST, MicroGrid Type2/HVDC/Type3, SmallGrid SSH+SV, PowerFlow, optional RealGrid smoke.
 * P3 adds ReliCapGrid (Svedala / optional Belgovia) when the local clone exists.
 *
 * <p>CAS fixtures resolve from (1) {@code -Dipss.cgmes.cas.root}, (2) in-repo
 * symlinks under {@code testData/adpter/cim/cgmes3.0/cas/}, then (3) the Temp
 * download tree. Symlinks are gitignored; see {@code cas/README.md}.
 *
 * <p>Tests that need the pack skip via {@code assumeTrue} when files are absent.
 *
 * @see CIMDirectParserTest for the existing MicroGrid T4 / MiniGrid 2.4 / IEEE118 suite
 */
@Tag("cgmes-cas")
public class CGMESCasCoverageStubTest extends CorePluginTestSetup {

	private static final String TD24 = "testData/adpter/cim/cgmes2.4/";
	/** Preferred in-repo symlinks (see {@code testData/.../cgmes3.0/cas/README.md}). */
	private static final String TD30_CAS = "testData/adpter/cim/cgmes3.0/cas/";

	/**
	 * Resolve a CAS fixture directory. Order:
	 * <ol>
	 *   <li>{@code -Dipss.cgmes.cas.root=} unzipped CAS root (parent of {@code v3.0/}), then
	 *       {@code v3.0/} + {@code relativeUnderV30}</li>
	 *   <li>In-repo symlink under {@link #TD30_CAS}{@code localCasDirName}</li>
	 *   <li>Default Temp download tree under {@code ~/Documents/Temp/cgmes-test-data/}</li>
	 * </ol>
	 */
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

	private static void requireFiles(Path... files) {
		for (Path f : files) {
			assumeTrue(Files.isRegularFile(f),
					() -> "Missing CAS fixture (download/unzip under ~/Documents/Temp/cgmes-test-data or set -Dipss.cgmes.cas.root=...): "
							+ f);
		}
	}

	private static String[] abs(Path... files) {
		String[] out = new String[files.length];
		for (int i = 0; i < files.length; i++) {
			out[i] = files[i].toAbsolutePath().toString();
		}
		return out;
	}


	/**
	 * Resolve under CAS 2.4.15 Temp tree (or in-repo symlink under {@link #TD30_CAS}).
	 */
	private static Path cas24Dir(String localCasDirName, String relativeUnderCas24Root) {
		Path local = Path.of(TD30_CAS + localCasDirName);
		if (Files.isDirectory(local)) {
			return local;
		}
		String override = System.getProperty("ipss.cgmes.cas24.root");
		if (override != null && !override.isBlank()) {
			return Path.of(override).resolve(relativeUnderCas24Root);
		}
		String home = System.getProperty("user.home");
		return Path.of(home, "Documents", "Temp", "cgmes-test-data", "cas-2.4.15")
				.resolve(relativeUnderCas24Root);
	}

	/**
	 * Resolve under ReliCapGrid clone (or in-repo symlink under {@link #TD30_CAS}).
	 */
	private static Path relicapDir(String localCasDirName, String relativeUnderClone) {
		Path local = Path.of(TD30_CAS + localCasDirName);
		if (Files.isDirectory(local)) {
			return local;
		}
		String override = System.getProperty("ipss.cgmes.relicap.root");
		if (override != null && !override.isBlank()) {
			return Path.of(override).resolve(relativeUnderClone);
		}
		String home = System.getProperty("user.home");
		return Path.of(home, "Documents", "Temp", "cgmes-test-data", "relicapgrid")
				.resolve(relativeUnderClone);
	}

	/**
	 * First regular {@code *.xml} under {@code dir} whose name matches the profile
	 * token. {@code EQ} does not match {@code EQ_BD}/{@code EQBD}.
	 */
	private static Path pickProfile(Path dir, String profile) throws Exception {
		assumeTrue(Files.isDirectory(dir), () -> "Missing dir: " + dir);
		String p = profile.toUpperCase();
		java.util.List<Path> xmls = new java.util.ArrayList<>();
		try (java.util.stream.Stream<Path> s = Files.list(dir)) {
			s.filter(Files::isRegularFile)
					.filter(x -> x.getFileName().toString().toLowerCase().endsWith(".xml"))
					.sorted()
					.forEach(xmls::add);
		}
		for (Path x : xmls) {
			String n = x.getFileName().toString().toUpperCase();
			if ("EQ_BD".equals(p) || "EQBD".equals(p)) {
				if (n.contains("EQ_BD") || n.contains("EQBD")) {
					return x;
				}
			} else if ("EQ".equals(p)) {
				if ((n.contains("_EQ_") || n.endsWith("_EQ.XML") || n.contains("_EQ."))
						&& !n.contains("EQ_BD") && !n.contains("EQBD")) {
					return x;
				}
			} else if (n.contains("_" + p + "_") || n.endsWith("_" + p + ".XML")
					|| n.contains("_" + p + ".")) {
				return x;
			}
		}
		assumeTrue(false, () -> "No *" + profile + "*.xml under " + dir + " files=" + xmls);
		return dir; // unreachable
	}


	/**
	 * First {@code *.xml} under {@code dir} whose name contains {@code mustContain}
	 * (case-insensitive) and matches the profile token via {@link #pickProfile} rules.
	 */
	private static Path pickProfileContaining(Path dir, String profile, String mustContain)
			throws Exception {
		assumeTrue(Files.isDirectory(dir), () -> "Missing dir: " + dir);
		String needle = mustContain.toUpperCase();
		String p = profile.toUpperCase();
		java.util.List<Path> xmls = new java.util.ArrayList<>();
		try (java.util.stream.Stream<Path> s = Files.list(dir)) {
			s.filter(Files::isRegularFile)
					.filter(x -> x.getFileName().toString().toLowerCase().endsWith(".xml"))
					.filter(x -> x.getFileName().toString().toUpperCase().contains(needle))
					.sorted()
					.forEach(xmls::add);
		}
		assumeTrue(!xmls.isEmpty(),
				() -> "No *" + mustContain + "*.xml under " + dir);
		for (Path x : xmls) {
			String n = x.getFileName().toString().toUpperCase();
			if ("EQ_BD".equals(p) || "EQBD".equals(p)) {
				if (n.contains("EQ_BD") || n.contains("EQBD")) {
					return x;
				}
			} else if ("EQ".equals(p)) {
				if ((n.contains("_EQ_") || n.endsWith("_EQ.XML") || n.contains("_EQ."))
						&& !n.contains("EQ_BD") && !n.contains("EQBD")) {
					return x;
				}
			} else if (n.contains("_" + p + "_") || n.endsWith("_" + p + ".XML")
					|| n.contains("_" + p + ".")) {
				return x;
			}
		}
		assumeTrue(false, () -> "No *" + profile + "* matching " + mustContain + " under " + dir
				+ " candidates=" + xmls);
		return dir;
	}

	private static Path firstExistingSubdir(Path parent, String... names) throws Exception {
		if (!Files.isDirectory(parent)) {
			return parent.resolve(names[0]);
		}
		for (String n : names) {
			Path p = parent.resolve(n);
			if (Files.isDirectory(p)) {
				return p;
			}
		}
		try (java.util.stream.Stream<Path> s = Files.list(parent)) {
			return s.filter(Files::isDirectory)
					.filter(p -> {
						String bn = p.getFileName().toString();
						for (String n : names) {
							if (bn.equalsIgnoreCase(n) || bn.contains(n)) {
								return true;
							}
						}
						return false;
					})
					.findFirst()
					.orElse(parent.resolve(names[0]));
		}
	}


	// -------------------------------------------------------------------------
	// P0 — orphaned in-repo CGMES 2.4 SmallGrid fixtures
	// -------------------------------------------------------------------------

	@Test
	@DisplayName("P0: SmallGrid bus-branch EQ+TP+SSH import (in-repo, unused before)")
	public void testSmallGrid_BB_Import() throws Exception {
		AclfNetwork net = new CGMESDirectParser().parse(new String[] {
				TD24 + "SmallGrid_BB_EQ_V3.xml",
				TD24 + "SmallGrid_BB_TP_V3.xml",
				TD24 + "SmallGrid_BB_SSH_V3.xml"
		});
		assertEquals(OriginalDataFormat.CIM, net.getOriginalDataFormat());
		assertTrue(net.getNoBus() > 0, "SmallGrid BB should create buses");
		assertTrue(net.getNoBranch() > 0, "SmallGrid BB should create branches");
		// Tighten once baseline counts are recorded from a green run:
		// assertEquals(N_BUS, net.getNoBus());
	}

	@Test
	@DisplayName("P0: SmallGrid node-breaker EQ+TP+SSH import (in-repo)")
	public void testSmallGrid_NB_Import() throws Exception {
		AclfNetwork net = new CGMESDirectParser().parse(new String[] {
				TD24 + "SmallGrid_NB_EQ_V3.xml",
				TD24 + "SmallGrid_NB_TP_V3.xml",
				TD24 + "SmallGrid_NB_SSH_V3.xml"
		});
		assertTrue(net.getNoBus() > 0);
		assertTrue(net.getNoBranch() > 0);
	}

	@Test
	@DisplayName("P0: SmallGrid HVDC EQ+TP+SSH import smoke (in-repo)")
	public void testSmallGrid_HVDC_ImportSmoke() throws Exception {
		// May fail if HVDC converters/DC lines are not mapped yet — keep as coverage probe.
		AclfNetwork net = new CGMESDirectParser().parse(new String[] {
				TD24 + "SmallGrid_HVDC_EQ_V3.xml",
				TD24 + "SmallGrid_HVDC_TP_V3.xml",
				TD24 + "SmallGrid_HVDC_SSH_V3.xml"
		});
		assertTrue(net.getNoBus() > 0, "HVDC SmallGrid should at least build AC topology");
	}

	@Test
	@DisplayName("P0: SmallGrid BB with EQ_BD + TP_BD (2.4 boundary)")
	public void testSmallGrid_BB_WithBoundary() throws Exception {
		AclfNetwork withBd = new CGMESDirectParser().parse(new String[] {
				TD24 + "SmallGrid_BB_EQ_V3.xml",
				TD24 + "SmallGrid_BB_TP_V3.xml",
				TD24 + "SmallGrid_BB_EQ_BD_V3.xml",
				TD24 + "SmallGrid_BB_TP_BD_V3.xml"
		});
		AclfNetwork withoutBd = new CGMESDirectParser().parse(new String[] {
				TD24 + "SmallGrid_BB_EQ_V3.xml",
				TD24 + "SmallGrid_BB_TP_V3.xml"
		});
		assertTrue(withBd.getNoBus() > 0);
		assertTrue(withBd.getNoBus() <= withoutBd.getNoBus() + 5,
				"EQBD/TPBD should not inflate bus count substantially");
	}

	// -------------------------------------------------------------------------
	// P0 — first official CGMES 3.0 / CIM100 CAS MiniGrid
	// -------------------------------------------------------------------------

	@Test
	@Tag("requires-cas-download")
	@DisplayName("P0: CAS v3.0.3 MiniGrid-Merged EQ+SSH+TP+SV+EQBD (CIM100)")
	public void testCasV30_MiniGridMerged_Import() throws Exception {
		Path dir = casDir("MiniGrid-Merged", "MiniGrid/MiniGrid-Merged");
		Path eq = dir.resolve("MiniGrid_EQ.xml");
		Path ssh = dir.resolve("MiniGrid_SSH.xml");
		Path tp = dir.resolve("MiniGrid_TP.xml");
		Path sv = dir.resolve("MiniGrid_SV.xml");
		Path eqbd = dir.resolve("MiniGrid_EQBD.xml");
		requireFiles(eq, ssh, tp, sv, eqbd);

		AclfNetwork net = new CGMESDirectParser().parse(abs(eq, ssh, tp, sv, eqbd));
		assertEquals(OriginalDataFormat.CIM, net.getOriginalDataFormat());
		assertTrue(net.getNoBus() > 0, "CIM100 MiniGrid should create buses");
		assertTrue(net.getNoBranch() > 0, "CIM100 MiniGrid should create branches");
		// Expected ~13 physical TopologicalNodes (+ star buses for 3W); tighten after green run.
		int xfr = 0;
		for (AclfBranch b : net.getBranchList()) {
			if (b.getBranchCode() == AclfBranchCode.XFORMER
					|| b.getBranchCode() == AclfBranchCode.W3_XFORMER) {
				xfr++;
			}
		}
		assertTrue(xfr >= 1, "MiniGrid-Merged should include transformers");
	}

	// -------------------------------------------------------------------------
	// P1 — MicroGrid Type1 multi-MAS / merged CGM (CGMES 3.0)
	// -------------------------------------------------------------------------

	@Test
	@Tag("requires-cas-download")
	@DisplayName("P1: CAS v3.0.3 MicroGrid Type1 BE-MAS EQ+SSH+TP import")
	public void testCasV30_MicroGridType1_BE_Import() throws Exception {
		Path dir = casDir("MicroGrid-Type1-BE-MAS", "MicroGrid/MicroGrid-Type1/MicroGrid-Type1-BE-MAS");
		Path bd = casDir("MicroGrid-BD-MAS", "MicroGrid/MicroGrid-Type1/MicroGrid-BD-MAS")
				.resolve("20171002T0930Z_ENTSO-E_EQ_BD_2.xml");
		Path eq = dir.resolve("20210323T1730Z_1D_BE_EQ_1.xml");
		Path ssh = dir.resolve("20210323T1730Z_1D_BE_SSH_1.xml");
		Path tp = dir.resolve("20210323T1730Z_1D_BE_TP_1.xml");
		requireFiles(eq, ssh, tp, bd);

		AclfNetwork net = new CGMESDirectParser().parse(abs(eq, ssh, tp, bd));
		assertTrue(net.getNoBus() > 0);
		assertTrue(net.getNoBranch() > 0);
	}

	@Test
	@Tag("requires-cas-download")
	@DisplayName("P1: CAS v3.0.3 MicroGrid Type1 Merged (BE+NL EQ/SSH + Assembled TP/SV + EQBD)")
	public void testCasV30_MicroGridType1_Merged_Import() throws Exception {
		Path merged = casDir("MicroGrid-Type1-Merged", "MicroGrid/MicroGrid-Type1/MicroGrid-Type1-Merged");
		Path eqBd = merged.resolve("20171002T0930Z_ENTSO-E_EQ_BD_2.xml");
		Path beEq = merged.resolve("20210323T1730Z_1D_BE_EQ_1.xml");
		Path beSsh = merged.resolve("20210323T1730Z_1D_BE_SSH_1.xml");
		Path nlEq = merged.resolve("20210323T1730Z_1D_NL_EQ_1.xml");
		Path nlSsh = merged.resolve("20210323T1730Z_1D_NL_SSH_1.xml");
		Path tp = merged.resolve("20210323T1730Z_1D_ASSEMBLED_TP_1.xml");
		Path sv = merged.resolve("20210323T1730Z_1D_ASSEMBLED_SV_1.xml");
		requireFiles(eqBd, beEq, beSsh, nlEq, nlSsh, tp, sv);

		AclfNetwork net = new CGMESDirectParser().parse(
				abs(eqBd, beEq, beSsh, nlEq, nlSsh, tp, sv));
		assertTrue(net.getNoBus() > 0, "Merged Type1 CGM should create buses");
		assertTrue(net.getNoBranch() > 0, "Merged Type1 CGM should create branches");
		// Target after green run (gridoxide / CAS docs often cite ~17 buses for this fixture):
		// assertEquals(17, net.getNoBus());
	}

	// -------------------------------------------------------------------------
	// P2 — PST / MicroGrid Type2 / SmallGrid SSH+SV / optional RealGrid
	// -------------------------------------------------------------------------

	@Test
	@Tag("requires-cas-download")
	@DisplayName("P2: CAS v3.0 PST PhaseTapChangerLinear Type1 EQ+SSH+TP+SV")
	public void testCasV30_PST_PhaseTapChangerLinear_Type1() throws Exception {
		// Official tree (cimgo): PST/PST_PhaseTapChangerLinear_Type1
		Path primary = casDir("PST-PhaseTapChangerLinear-Type1",
				"PST/PST_PhaseTapChangerLinear_Type1");
		Path dir = Files.isDirectory(primary)
				? primary
				: firstExistingSubdir(casDir("PST", "PST"),
						"PST_PhaseTapChangerLinear_Type1",
						"PST_PhaseTapChangerLinear_Type1",
						"PhaseTapChangerLinear_Type1");
		assumeTrue(Files.isDirectory(dir),
				() -> "PST Type1 dir missing (expected under CAS v3.0 PST/): " + dir);

		Path eq = pickProfile(dir, "EQ");
		Path ssh = pickProfile(dir, "SSH");
		Path tp = pickProfile(dir, "TP");
		Path sv = pickProfile(dir, "SV");
		requireFiles(eq, ssh, tp, sv);

		AclfNetwork net = new CGMESDirectParser().parse(abs(eq, ssh, tp, sv));
		assertEquals(OriginalDataFormat.CIM, net.getOriginalDataFormat());
		assertTrue(net.getNoBus() > 0, "PST Type1 should create buses");
		assertTrue(net.getNoBranch() > 0, "PST Type1 should create branches");
		int xfr = 0;
		for (AclfBranch b : net.getBranchList()) {
			if (b.getBranchCode() == AclfBranchCode.XFORMER
					|| b.getBranchCode() == AclfBranchCode.W3_XFORMER) {
				xfr++;
			}
		}
		assertTrue(xfr >= 1, "PST case should include at least one transformer");
		// P4 TODO: assert phase-shift / tap mapping; Aclf vs SV angles
		// assertEquals(N_BUS, net.getNoBus());
	}

	@Test
	@Tag("requires-cas-download")
	@DisplayName("P2: CAS v3.0 MicroGrid Type2 BE-MAS EQ+SSH+TP (+EQBD)")
	public void testCasV30_MicroGridType2_BE_Import() throws Exception {
		Path dir = casDir("MicroGrid-Type2-BE-MAS",
				"MicroGrid/MicroGrid-Type2/MicroGrid-Type2-BE-MAS");
		Path bdDir = casDir("MicroGrid-Type2-BD-MAS",
				"MicroGrid/MicroGrid-Type2/MicroGrid-Type2-BD-MAS");
		if (!Files.isDirectory(bdDir)) {
			bdDir = casDir("MicroGrid-BD-MAS", "MicroGrid/MicroGrid-Type1/MicroGrid-BD-MAS");
		}
		Path eq = pickProfile(dir, "EQ");
		Path ssh = pickProfile(dir, "SSH");
		Path tp = pickProfile(dir, "TP");
		Path bd = pickProfile(bdDir, "EQ_BD");
		requireFiles(eq, ssh, tp, bd);

		AclfNetwork net = new CGMESDirectParser().parse(abs(eq, ssh, tp, bd));
		assertTrue(net.getNoBus() > 0);
		assertTrue(net.getNoBranch() > 0);
		// assertEquals(N_BUS, net.getNoBus());
	}

	@Test
	@Tag("requires-cas-download")
	@DisplayName("P2: CAS v3.0 MicroGrid Type2 Merged EQ/SSH + Assembled TP/SV (+EQBD) [SSH+SV]")
	public void testCasV30_MicroGridType2_Merged_SshSv() throws Exception {
		Path merged = casDir("MicroGrid-Type2-Merged",
				"MicroGrid/MicroGrid-Type2/MicroGrid-Type2-Merged");
		assumeTrue(Files.isDirectory(merged), () -> "Type2-Merged missing: " + merged);

		java.util.List<Path> files = new java.util.ArrayList<>();
		try (java.util.stream.Stream<Path> s = Files.list(merged)) {
			s.filter(Files::isRegularFile)
					.filter(p -> p.getFileName().toString().toLowerCase().endsWith(".xml"))
					.sorted()
					.forEach(files::add);
		}
		assumeTrue(files.size() >= 4, () -> "Type2-Merged needs several profiles; found " + files);

		boolean hasSsh = files.stream().anyMatch(p -> p.getFileName().toString().toUpperCase().contains("_SSH"));
		boolean hasSv = files.stream().anyMatch(p -> p.getFileName().toString().toUpperCase().contains("_SV"));
		assumeTrue(hasSsh, "Type2-Merged should include SSH");
		assumeTrue(hasSv, "Type2-Merged should include SV (PF-related smoke)");

		AclfNetwork net = new CGMESDirectParser().parse(abs(files.toArray(new Path[0])));
		assertTrue(net.getNoBus() > 0, "Type2 Merged should create buses");
		assertTrue(net.getNoBranch() > 0, "Type2 Merged should create branches");
		// P4 TODO: AclfNetwork load-flow vs Assembled SV voltages/flows
		// assertEquals(N_BUS, net.getNoBus());
	}

	@Test
	@Tag("requires-cas-download")
	@DisplayName("P2: CAS v3.0 SmallGrid EQ+SSH+TP(+SV) import smoke")
	public void testCasV30_SmallGrid_SshSv_Import() throws Exception {
		Path primary = casDir("SmallGrid", "SmallGrid");
		Path dir = Files.isDirectory(primary)
				? primary
				: firstExistingSubdir(casDir("SmallGrid-root", "SmallGrid"),
						"SmallGrid", "BaseCase", "SmallGrid-BaseCase");
		assumeTrue(Files.isDirectory(dir), () -> "CAS SmallGrid missing: " + dir);

		Path eqDir = dir;
		try {
			pickProfile(eqDir, "EQ");
		} catch (org.opentest4j.TestAbortedException ex) {
			eqDir = firstExistingSubdir(dir, "BaseCase", "BE", "BB", "NB");
		}

		Path eq = pickProfile(eqDir, "EQ");
		Path ssh = pickProfile(eqDir, "SSH");
		Path tp = pickProfile(eqDir, "TP");
		java.util.List<Path> args = new java.util.ArrayList<>();
		args.add(eq);
		args.add(ssh);
		args.add(tp);
		try {
			args.add(pickProfile(eqDir, "SV"));
		} catch (org.opentest4j.TestAbortedException ignore) {
			// SSH+TP still a useful structural smoke
		}
		requireFiles(args.toArray(new Path[0]));

		AclfNetwork net = new CGMESDirectParser().parse(abs(args.toArray(new Path[0])));
		assertTrue(net.getNoBus() > 0);
		assertTrue(net.getNoBranch() > 0);
		// P4 TODO: compare Aclf vs SV when SV present
	}

	@Test
	@Tag("requires-cas-download")
	@DisplayName("P2: CAS v3.0 RealGrid/FullGrid smoke (optional; skip if absent)")
	public void testCasV30_RealGrid_ImportSmoke_Optional() throws Exception {
		Path primary = casDir("RealGrid", "RealGrid");
		Path dir = Files.isDirectory(primary) ? primary : casDir("FullGrid", "FullGrid");
		assumeTrue(Files.isDirectory(dir),
				() -> "RealGrid/FullGrid not in local CAS — optional P2 skip");

		Path eqDir = dir;
		try {
			pickProfile(eqDir, "EQ");
		} catch (org.opentest4j.TestAbortedException ex) {
			eqDir = firstExistingSubdir(dir, "BaseCase", "BE", "Merged");
		}
		Path eq = pickProfile(eqDir, "EQ");
		Path ssh = pickProfile(eqDir, "SSH");
		Path tp = pickProfile(eqDir, "TP");
		requireFiles(eq, ssh, tp);

		AclfNetwork net = new CGMESDirectParser().parse(abs(eq, ssh, tp));
		assertTrue(net.getNoBus() > 0, "RealGrid/FullGrid should create buses");
		assertTrue(net.getNoBranch() > 0, "RealGrid/FullGrid should create branches");
		// Large model — leave exact counts / Aclf-vs-SV for P4
	}


	// -------------------------------------------------------------------------
	// P2b — PST Type2/Table Type3, Type2-HVDC, Type3 time-series, PowerFlow, CAS Svedala
	// -------------------------------------------------------------------------

	@Test
	@Tag("requires-cas-download")
	@DisplayName("P2: CAS v3.0 PST PhaseTapChangerLinear Type2 EQ+SSH+TP+SV")
	public void testCasV30_PST_PhaseTapChangerLinear_Type2() throws Exception {
		Path dir = casDir("PST-PhaseTapChangerLinear-Type2",
				"PST/PST_PhaseTapChangerLinear_Type2");
		assumeTrue(Files.isDirectory(dir), () -> "PST Type2 missing: " + dir);
		Path eq = pickProfile(dir, "EQ");
		Path ssh = pickProfile(dir, "SSH");
		Path tp = pickProfile(dir, "TP");
		Path sv = pickProfile(dir, "SV");
		requireFiles(eq, ssh, tp, sv);

		AclfNetwork net = new CGMESDirectParser().parse(abs(eq, ssh, tp, sv));
		assertTrue(net.getNoBus() > 0, "PST Type2 should create buses");
		assertTrue(net.getNoBranch() > 0, "PST Type2 should create branches");
		// P4 TODO: linear tap / phase-shift semantics
	}

	@Test
	@Tag("requires-cas-download")
	@DisplayName("P2: CAS v3.0 PST PhaseTapChangerTable Type3 EQ+SSH+TP+SV")
	public void testCasV30_PST_PhaseTapChangerTable_Type3() throws Exception {
		Path dir = casDir("PST-PhaseTapChangerTable-Type3",
				"PST/PST_PhaseTapChangerTable_Type3");
		assumeTrue(Files.isDirectory(dir), () -> "PST Table Type3 missing: " + dir);
		Path eq = pickProfile(dir, "EQ");
		Path ssh = pickProfile(dir, "SSH");
		Path tp = pickProfile(dir, "TP");
		Path sv = pickProfile(dir, "SV");
		requireFiles(eq, ssh, tp, sv);

		AclfNetwork net = new CGMESDirectParser().parse(abs(eq, ssh, tp, sv));
		assertTrue(net.getNoBus() > 0, "PST Table Type3 should create buses");
		assertTrue(net.getNoBranch() > 0, "PST Table Type3 should create branches");
		// P4 TODO: tabular phase-tap mapping
	}

	@Test
	@Tag("requires-cas-download")
	@DisplayName("P2: CAS v3.0 MicroGrid Type2 HVDC-MAS EQ+SSH+TP(+SV) smoke")
	public void testCasV30_MicroGridType2_HVDC_ImportSmoke() throws Exception {
		Path dir = casDir("MicroGrid-Type2-HVDC-MAS",
				"MicroGrid/MicroGrid-Type2/MicroGrid-Type2-HVDC-MAS");
		Path bdDir = casDir("MicroGrid-Type2-BD-MAS",
				"MicroGrid/MicroGrid-Type2/MicroGrid-Type2-BD-MAS");
		assumeTrue(Files.isDirectory(dir), () -> "Type2 HVDC-MAS missing: " + dir);

		Path eq = pickProfile(dir, "EQ");
		Path ssh = pickProfile(dir, "SSH");
		Path tp = pickProfile(dir, "TP");
		java.util.List<Path> args = new java.util.ArrayList<>();
		args.add(eq);
		args.add(ssh);
		args.add(tp);
		try {
			args.add(pickProfile(dir, "SV"));
		} catch (org.opentest4j.TestAbortedException ignore) {
			// optional
		}
		if (Files.isDirectory(bdDir)) {
			try {
				args.add(pickProfile(bdDir, "EQ_BD"));
			} catch (org.opentest4j.TestAbortedException ignore) {
				// optional boundary
			}
		}
		requireFiles(args.toArray(new Path[0]));

		AclfNetwork net = new CGMESDirectParser().parse(abs(args.toArray(new Path[0])));
		assertTrue(net.getNoBus() > 0, "Type2 HVDC should at least build AC topology");
		assertTrue(net.getNoBranch() > 0);
		// May expose HVDC converter gaps — keep as coverage probe
	}

	@Test
	@Tag("requires-cas-download")
	@DisplayName("P2: CAS v3.0 MicroGrid Type3 IGM first-hour BE EQ+SSH+TP(+BD)")
	public void testCasV30_MicroGridType3_Igm_BE_FirstHour() throws Exception {
		Path igms = casDir("MicroGrid-Type3-IGMs", "MicroGrid/MicroGrid-Type3/IGMs");
		assumeTrue(Files.isDirectory(igms), () -> "Type3 IGMs missing: " + igms);
		// Flat time-series folder; pin first published hour (20210422T2230Z)
		final String hour = "20210422T2230Z";
		Path eq = pickProfileContaining(igms, "EQ", hour + "_1D_BE");
		Path ssh = pickProfileContaining(igms, "SSH", hour + "_1D_BE");
		Path tp = pickProfileContaining(igms, "TP", hour + "_1D_BE");
		Path bd = pickProfile(igms, "EQ_BD");
		requireFiles(eq, ssh, tp, bd);

		AclfNetwork net = new CGMESDirectParser().parse(abs(eq, ssh, tp, bd));
		assertTrue(net.getNoBus() > 0, "Type3 BE IGM should create buses");
		assertTrue(net.getNoBranch() > 0);
	}

	@Test
	@Tag("requires-cas-download")
	@DisplayName("P2: CAS v3.0 MicroGrid Type3 CGM first-hour BE+NL EQ/SSH + Assembled TP/SV")
	public void testCasV30_MicroGridType3_Cgm_FirstHour() throws Exception {
		Path igms = casDir("MicroGrid-Type3-IGMs", "MicroGrid/MicroGrid-Type3/IGMs");
		Path cgms = casDir("MicroGrid-Type3-CGMs", "MicroGrid/MicroGrid-Type3/CGMs");
		assumeTrue(Files.isDirectory(igms), () -> "Type3 IGMs missing: " + igms);
		assumeTrue(Files.isDirectory(cgms), () -> "Type3 CGMs missing: " + cgms);
		final String hour = "20210422T2230Z";

		Path beEq = pickProfileContaining(igms, "EQ", hour + "_1D_BE");
		Path nlEq = pickProfileContaining(igms, "EQ", hour + "_1D_NL");
		Path beSsh = pickProfileContaining(igms, "SSH", hour + "_1D_BE");
		Path nlSsh = pickProfileContaining(igms, "SSH", hour + "_1D_NL");
		Path bd = pickProfile(igms, "EQ_BD");
		Path tp = pickProfileContaining(cgms, "TP", hour + "_1D_ASSEMBLED");
		Path sv = pickProfileContaining(cgms, "SV", hour + "_1D_ASSEMBLED");
		requireFiles(beEq, nlEq, beSsh, nlSsh, bd, tp, sv);

		AclfNetwork net = new CGMESDirectParser().parse(
				abs(bd, beEq, nlEq, beSsh, nlSsh, tp, sv));
		assertTrue(net.getNoBus() > 0, "Type3 CGM should create buses");
		assertTrue(net.getNoBranch() > 0);
		// Remaining Type3 hours left for a parameterized suite later
	}

	@Test
	@Tag("requires-cas-download")
	@DisplayName("P2: CAS v3.0 PowerFlow explicit LF case EQ+SSH+TP+SV")
	public void testCasV30_PowerFlow_ExplicitLoadFlow() throws Exception {
		Path primary = casDir("PowerFlow-Instance", "PowerFlow/PowerFlow");
		Path dir = Files.isDirectory(primary)
				? primary
				: casDir("PowerFlow", "PowerFlow").resolve("PowerFlow");
		assumeTrue(Files.isDirectory(dir), () -> "PowerFlow instance missing: " + dir);
		Path eq = pickProfile(dir, "EQ");
		Path ssh = pickProfile(dir, "SSH");
		Path tp = pickProfile(dir, "TP");
		Path sv = pickProfile(dir, "SV");
		requireFiles(eq, ssh, tp, sv);

		AclfNetwork net = new CGMESDirectParser().parse(abs(eq, ssh, tp, sv));
		assertEquals(OriginalDataFormat.CIM, net.getOriginalDataFormat());
		assertTrue(net.getNoBus() > 0, "PowerFlow case should create buses");
		assertTrue(net.getNoBranch() > 0);
		// P4 TODO: run Aclf and compare to SV voltages/flows (explicit LF docs in pack)
	}

	@Test
	@Tag("requires-cas-download")
	@DisplayName("P2: CAS v3.0 Svedala-Merged EQ+SSH+TP+SV+EQBD")
	public void testCasV30_SvedalaMerged_Import() throws Exception {
		Path dir = casDir("CAS-Svedala-Merged", "Svedala/Svedala-Merged");
		assumeTrue(Files.isDirectory(dir), () -> "CAS Svedala-Merged missing: " + dir);
		Path eq = pickProfile(dir, "EQ");
		Path ssh = pickProfile(dir, "SSH");
		Path tp = pickProfile(dir, "TP");
		Path sv = pickProfile(dir, "SV");
		Path eqbd = pickProfile(dir, "EQBD");
		requireFiles(eq, ssh, tp, sv, eqbd);

		AclfNetwork net = new CGMESDirectParser().parse(abs(eq, ssh, tp, sv, eqbd));
		assertTrue(net.getNoBus() > 0, "CAS Svedala-Merged should create buses");
		assertTrue(net.getNoBranch() > 0);
		// Distinct from ReliCap Svedala IGM (P3)
	}

	// -------------------------------------------------------------------------
	// P3 — ReliCapGrid / Network Code sample packs
	// -------------------------------------------------------------------------

	@Test
	@Tag("requires-cas-download")
	@DisplayName("P3: ReliCapGrid Svedala IGM EQ+SSH+TP(+SV) import smoke")
	public void testReliCap_Svedala_Igm_Import() throws Exception {
		Path dir = relicapDir("ReliCap-Svedala-cimxml",
				"Instance/Svedala/Grid/cimxml");
		assumeTrue(Files.isDirectory(dir),
				() -> "ReliCap Svedala cimxml missing (clone under ~/Documents/Temp/cgmes-test-data/relicapgrid or symlink): "
						+ dir);

		Path eq = pickProfile(dir, "EQ");
		Path ssh = pickProfile(dir, "SSH");
		Path tp = pickProfile(dir, "TP");
		java.util.List<Path> args = new java.util.ArrayList<>();
		args.add(eq);
		args.add(ssh);
		args.add(tp);
		try {
			args.add(pickProfile(dir, "SV"));
		} catch (org.opentest4j.TestAbortedException ignore) {
			// EQ+SSH+TP enough for import smoke
		}
		requireFiles(args.toArray(new Path[0]));

		AclfNetwork net = new CGMESDirectParser().parse(abs(args.toArray(new Path[0])));
		assertTrue(net.getNoBus() > 0, "Svedala IGM should create buses");
		assertTrue(net.getNoBranch() > 0, "Svedala IGM should create branches");
		// Upstream names (GitHub cgmes-3.0_ncp-2.5_tc-2.0):
		//   20220615T2230Z__Svedala_EQ_1.xml
		//   20220615T2230Z_2D_Svedala_SSH_1.xml
		//   20220615T2230Z_2D_Svedala_TP_1.xml
		//   20220615T2230Z_2D_Svedala_SV_1.xml
		// NetworkCode profiles (AE/CO/RA/…) are out of scope for Aclf import — P4+
		// assertEquals(N_BUS, net.getNoBus());
	}

	@Test
	@Tag("requires-cas-download")
	@DisplayName("P3: ReliCapGrid Belgovia IGM smoke (optional)")
	public void testReliCap_Belgovia_Igm_Import_Optional() throws Exception {
		Path dir = relicapDir("ReliCap-Belgovia-cimxml",
				"Instance/Belgovia/Grid/cimxml");
		assumeTrue(Files.isDirectory(dir),
				() -> "Belgovia cimxml not present — optional P3 skip: " + dir);

		Path eq = pickProfile(dir, "EQ");
		Path ssh = pickProfile(dir, "SSH");
		Path tp = pickProfile(dir, "TP");
		requireFiles(eq, ssh, tp);

		AclfNetwork net = new CGMESDirectParser().parse(abs(eq, ssh, tp));
		assertTrue(net.getNoBus() > 0);
		assertTrue(net.getNoBranch() > 0);
	}
}
