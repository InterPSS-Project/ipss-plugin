package org.interpss.core.adapter.cim;

import static org.interpss.core.adapter.cim.CGMESCasP4AclfSmokeStubTest.abs;
import static org.interpss.core.adapter.cim.CGMESCasP4AclfSmokeStubTest.casDir;
import static org.interpss.core.adapter.cim.CGMESCasP4AclfSmokeStubTest.compareToSv;
import static org.interpss.core.adapter.cim.CGMESCasP4AclfSmokeStubTest.mustFile;
import static org.interpss.core.adapter.cim.CGMESCasP4AclfSmokeStubTest.runNrSeededSoft;
import static org.interpss.core.adapter.cim.CGMESCasP4AclfSmokeStubTest.runType3HourP4;
import static org.interpss.core.adapter.cim.CGMESCasP4AclfSmokeStubTest.solveNrSeeded;
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

import com.interpss.core.aclf.AclfNetwork;

/**
 * P4 cases whose SV-seeded NR still does not converge. Split out of
 * {@link CGMESCasP4AclfSmokeStubTest} so that class stays green.
 *
 * <p>Type3 CGM hour {@code 20210423T1030Z}, ReliCap Espheim, and the
 * Espheim–Svedala DC corridor abort in {@code runNrSeededSoft}: the solver
 * does not converge from the SV seed. The Type3 hour probe stays off unless
 * {@code -Dipss.cgmes.p4.probeType3=true}.
 */
@Tag("cgmes-cas")
@Tag("cgmes-p4-aclf")
public class CGMESCasP4AclfUnconvergedStubTest extends CorePluginTestSetup {

	@Test
	@DisplayName("P4: Type3 CGM mid hour SV-seeded NR + Aclf vs SvVoltage")
	public void testP4_Type3Cgm_MidHour_AclfVsSv() throws Exception {
		runType3HourP4("20210423T1030Z");
	}

	@Test
	@DisplayName("P4: ReliCap Espheim IGM SV-seeded NR + Aclf vs SvVoltage")
	public void testP4_ReliCapEspheim_AclfVsSv() throws Exception {
		Path dir = casDir("ReliCap-Espheim-cimxml", "Instance/Espheim/Grid/cimxml");
		assumeTrue(Files.isDirectory(dir), () -> "ReliCap Espheim cimxml missing: " + dir);
		Path svXml = mustFile(dir, "20220615T2230Z_2D_Espheim_SV_1.xml");
		Path eqXml = mustFile(dir, "20220615T2230Z__Espheim_EQ_1.xml");
		Path tpXml = mustFile(dir, "20220615T2230Z_2D_Espheim_TP_1.xml");
		Path sshXml = mustFile(dir, "20220615T2230Z_2D_Espheim_SSH_1.xml");
		Path border = casDir("ReliCap-boundary-cimxml", "Instance/boundaryData/Grid/cimxml")
				.resolve("Boundary_Border-Svedala-Espheim.xml");
		Map<String, CgmesSvCompareSupport.SvVoltage> sv = CgmesSvCompareSupport.readSvVoltages(svXml);
		java.util.List<Path> inputs = new java.util.ArrayList<>();
		inputs.add(eqXml);
		inputs.add(sshXml);
		inputs.add(tpXml);
		inputs.add(svXml);
		if (Files.isRegularFile(border)) {
			inputs.add(border);
		}
		AclfNetwork net = new CGMESDirectParser().parse(abs(inputs.toArray(new Path[0])));
		assertTrue(net.getNoBus() > 0);
		runNrSeededSoft(net, sv, "ReliCap Espheim");
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
	@DisplayName("P4: ReliCap DC Espheim–Svedala SV-seeded NR + Aclf vs SvVoltage (AC soft)")
	public void testP4_ReliCapDcEspheimSvedala_AclfVsSv() throws Exception {
		Path dir = casDir("ReliCap-DC-Espheim-Svedala-cimxml",
				"Instance/DC-Espheim-Svedala/Grid/cimxml");
		assumeTrue(Files.isDirectory(dir), () -> "ReliCap DC Espheim-Svedala missing: " + dir);
		Path eqXml = mustFile(dir, "20220615T2230Z__HVDC-Espheim-Svedala_EQ_1.xml");
		Path sshXml = mustFile(dir, "20220615T2230Z_2D_HVDC-Espheim-Svedala_SSH_1.xml");
		Path tpXml = mustFile(dir, "20220615T2230Z_2D_HVDC-Espheim-Svedala_TP_1.xml");
		Path svXml = mustFile(dir, "20220615T2230Z_2D_HVDC-Espheim-Svedala_SV_1.xml");
		Map<String, CgmesSvCompareSupport.SvVoltage> sv = CgmesSvCompareSupport.readSvVoltages(svXml);
		AclfNetwork net = new CGMESDirectParser().parse(abs(eqXml, sshXml, tpXml, svXml));
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
	@DisplayName("P4 probe: Type3 hours NR converge map (dev)")
	public void testP4_Type3Cgm_ProbeHours_Converge() throws Exception {
		assumeTrue(Boolean.getBoolean("ipss.cgmes.p4.probeType3"),
				() -> "enable with -Dipss.cgmes.p4.probeType3=true");
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
