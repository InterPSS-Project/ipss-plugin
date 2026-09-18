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
 * P0/P1 expansion stubs for ENTSO-E CGMES coverage.
 *
 * <p>P0 wires orphaned in-repo CGMES 2.4 fixtures and the first official
 * CGMES 3.0 (CIM100) CAS MiniGrid pack from the local download tree.
 * P1 covers MicroGrid Type1 multi-MAS / merged CGM import.
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
}
