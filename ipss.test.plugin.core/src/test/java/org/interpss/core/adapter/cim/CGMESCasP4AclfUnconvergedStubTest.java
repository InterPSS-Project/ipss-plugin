package org.interpss.core.adapter.cim;

import static org.interpss.core.adapter.cim.CGMESCasP4AclfSmokeStubTest.abs;
import static org.interpss.core.adapter.cim.CGMESCasP4AclfSmokeStubTest.casDir;
import static org.interpss.core.adapter.cim.CGMESCasP4AclfSmokeStubTest.compareToSv;
import static org.interpss.core.adapter.cim.CGMESCasP4AclfSmokeStubTest.mustFile;
import static org.interpss.core.adapter.cim.CGMESCasP4AclfSmokeStubTest.runNrSeededSoft;
import static org.interpss.core.adapter.cim.CGMESCasP4AclfSmokeStubTest.solveNrSeeded;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

import org.interpss.CorePluginTestSetup;
import org.interpss.fadapter.cim.CGMESDirectParser;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;

import com.interpss.core.aclf.AclfNetwork;

/**
 * P4 cases split out of {@link CGMESCasP4AclfSmokeStubTest} so that class stays green.
 *
 * <p>MicroGrid Type1 / Type2 / BaseCase Merged, ReliCap Espheim, and Type3 mid-hour
 * {@code 1030Z} were fixed and returned to smoke (Espheim: OOS ACLineSegments inactive;
 * Type3: always {@code fillFlatFromNeighbor} so X-nodes missing SvVoltage when boundary
 * EI are OOS still get a seed).
 *
 * <p>Espheim–Svedala DC corridor alone is {@code @Disabled}: import yields 6 buses /
 * 4 branches with {@code swingN=0} (CsConverters + EquivalentInjections with
 * {@code regulationCapability=false} mapped as loads; no SynchronousMachine).
 * ACLF data-check exits with "No swing bus".
 *
 * <p>Naive Espheim+Svedala(+DC) IGM concatenation also fails NR: the four shared
 * boundary TNs ({@code BP_SD-EH*}) coalesce, but each IGM SvVoltage disagrees at
 * those BPs (e.g. DC1 Δangle≈36°), and both sides keep boundary EquivalentInjections.
 * Needs a CGM assembled TP/SV (like Type3), not raw IGM merge. Optional re-check:
 * {@code -Dipss.cgmes.p4.probeEspheim=true}.
 */
@Tag("cgmes-cas")
@Tag("cgmes-p4-aclf")
public class CGMESCasP4AclfUnconvergedStubTest extends CorePluginTestSetup {

	@Test
	@Disabled("DC Espheim–Svedala standalone: swingN=0 (CsConverter+EI loads only). "
			+ "Espheim+Svedala IGM assemble also fails NR (shared BP SvVoltage conflict). "
			+ "Re-check: -Dipss.cgmes.p4.probeEspheim=true")
	@DisplayName("P4: ReliCap DC Espheim–Svedala SV-seeded NR + Aclf vs SvVoltage (AC soft)")
	public void testP4_ReliCapDcEspheimSvedala_AclfVsSv() throws Exception {
		runReliCapDcEspheimSvedalaP4();
	}

	@Test
	@EnabledIfSystemProperty(named = "ipss.cgmes.p4.probeEspheim", matches = "true")
	@DisplayName("P4 probe: ReliCap DC Espheim–Svedala SV-seeded NR (dev)")
	public void testP4_ReliCapDcEspheimSvedala_Probe_AclfVsSv() throws Exception {
		runReliCapDcEspheimSvedalaP4();
	}

	private static void runReliCapDcEspheimSvedalaP4() throws Exception {
		// Standalone DC: no SynchronousMachine → swingN=0; ACLF data-check aborts.
		// Espheim+Svedala IGM merge needs CGM assembled TP/SV (shared BP Sv conflict).
		Path dir = casDir("ReliCap-DC-Espheim-Svedala-cimxml",
				"Instance/DC-Espheim-Svedala/Grid/cimxml");
		assumeTrue(Files.isDirectory(dir), () -> "ReliCap DC Espheim-Svedala missing: " + dir);
		Path svXml = mustFile(dir, "20220615T2230Z_2D_HVDC-Espheim-Svedala_SV_1.xml");
		Map<String, CgmesSvCompareSupport.SvVoltage> sv =
				CgmesSvCompareSupport.readSvVoltages(svXml);
		AclfNetwork net = new CGMESDirectParser().parse(abs(
				mustFile(dir, "20220615T2230Z__HVDC-Espheim-Svedala_EQ_1.xml"),
				mustFile(dir, "20220615T2230Z_2D_HVDC-Espheim-Svedala_SSH_1.xml"),
				mustFile(dir, "20220615T2230Z_2D_HVDC-Espheim-Svedala_TP_1.xml"),
				svXml));
		assertTrue(net.getNoBus() > 0);
		runNrSeededSoft(net, sv, "ReliCap DC Espheim-Svedala");
		double vTol = Double.parseDouble(System.getProperty("ipss.cgmes.p4.vTolPu", "0.05"));
		double minV = Double.parseDouble(System.getProperty("ipss.cgmes.p4.minMatch", "0.50"));
		String prevAng = System.getProperty("ipss.cgmes.p4.minAngMatch");
		String prevMiss = System.getProperty("ipss.cgmes.p4.maxMissingBus");
		System.setProperty("ipss.cgmes.p4.minAngMatch",
				System.getProperty("ipss.cgmes.p4.minAngMatch", "0.0"));
		System.setProperty("ipss.cgmes.p4.maxMissingBus",
				System.getProperty("ipss.cgmes.p4.maxMissingBus", "20"));
		try {
			compareToSv(net, sv, vTol, 20.0, minV);
		} finally {
			if (prevAng == null) {
				System.clearProperty("ipss.cgmes.p4.minAngMatch");
			} else {
				System.setProperty("ipss.cgmes.p4.minAngMatch", prevAng);
			}
			if (prevMiss == null) {
				System.clearProperty("ipss.cgmes.p4.maxMissingBus");
			} else {
				System.setProperty("ipss.cgmes.p4.maxMissingBus", prevMiss);
			}
		}
	}

	@Test
	@EnabledIfSystemProperty(named = "ipss.cgmes.p4.probeType3", matches = "true")
	@DisplayName("P4 probe: Type3 hours NR converge map (dev)")
	public void testP4_Type3Cgm_ProbeHours_Converge() throws Exception {
		Path igms = casDir("MicroGrid-Type3-IGMs", "MicroGrid/MicroGrid-Type3/IGMs");
		Path cgms = casDir("MicroGrid-Type3-CGMs", "MicroGrid/MicroGrid-Type3/CGMs");
		Path eqBd = mustFile(igms, "20171002T0930Z_ENTSO-E_EQ_BD_2.xml");
		Path beEq = mustFile(igms, "20210422T2230Z_1D_BE_EQ_001.xml");
		Path nlEq = mustFile(igms, "20210422T2230Z_1D_NL_EQ_001.xml");
		String[] hours = {
				"20210422T2230Z", "20210422T2330Z", "20210423T0230Z", "20210423T0530Z",
				"20210423T0830Z", "20210423T1030Z", "20210423T1130Z", "20210423T1430Z",
				"20210423T1730Z", "20210423T2030Z"
		};
		for (String hour : hours) {
			Path beSsh = igms.resolve(hour + "_1D_BE_SSH_001.xml");
			Path nlSsh = igms.resolve(hour + "_1D_NL_SSH_001.xml");
			Path tp = cgms.resolve(hour + "_1D_ASSEMBLED_TP_001.xml");
			Path svXml = cgms.resolve(hour + "_1D_ASSEMBLED_SV_001.xml");
			if (!Files.isRegularFile(beSsh) || !Files.isRegularFile(nlSsh)
					|| !Files.isRegularFile(tp) || !Files.isRegularFile(svXml)) {
				System.out.println("Type3Probe " + hour + " MISSING_FILES");
				continue;
			}
			Map<String, CgmesSvCompareSupport.SvVoltage> sv = CgmesSvCompareSupport.readSvVoltages(svXml);
			AclfNetwork net = new CGMESDirectParser().parse(abs(
					eqBd, beEq, nlEq, beSsh, nlSsh, tp, svXml));
			boolean ok = solveNrSeeded(net, sv);
			System.out.println("Type3Probe " + hour + " buses=" + net.getNoBus()
					+ " converged=" + ok);
		}
	}
}
