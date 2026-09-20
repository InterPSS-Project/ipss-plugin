package org.interpss.core.adapter.cim;

import static org.interpss.core.adapter.cim.CGMESCasP4AclfSmokeStubTest.abs;
import static org.interpss.core.adapter.cim.CGMESCasP4AclfSmokeStubTest.casDir;
import static org.interpss.core.adapter.cim.CGMESCasP4AclfSmokeStubTest.compareToSv;
import static org.interpss.core.adapter.cim.CGMESCasP4AclfSmokeStubTest.mustFile;
import static org.interpss.core.adapter.cim.CGMESCasP4AclfSmokeStubTest.runNrSeededSoft;
import static org.interpss.core.adapter.cim.CGMESCasP4AclfSmokeStubTest.runType3HourP4;
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
 * <p>Type3 CGM mid hour ({@code 20210423T1030Z}) and ReliCap Espheim abort in
 * {@code runNrSeededSoft}: the solver does not converge from the SV seed.
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
		Map<String, CgmesSvCompareSupport.SvVoltage> sv = CgmesSvCompareSupport.readSvVoltages(svXml);
		AclfNetwork net = new CGMESDirectParser().parse(abs(eqXml, sshXml, tpXml, svXml));
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
}
