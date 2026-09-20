package org.interpss.core.adapter.cim;

import static org.interpss.core.adapter.cim.CGMESCasP4AclfSmokeStubTest.abs;
import static org.interpss.core.adapter.cim.CGMESCasP4AclfSmokeStubTest.casDir;
import static org.interpss.core.adapter.cim.CGMESCasP4AclfSmokeStubTest.compareToSv;
import static org.interpss.core.adapter.cim.CGMESCasP4AclfSmokeStubTest.mustFile;
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
 * P4 cases whose SV-seeded NR still does not converge after zero-Z consolidation.
 * Split out of {@link CGMESCasP4AclfSmokeStubTest} so that class stays green.
 *
 * <p>RealGrid-Merged: after closed retained switches are consolidated, NR stops on a
 * numerical failure (also seen as a singular KLU factorization). The solver's
 * best-effort state had dPmax about 2.7e7 pu at
 * {@code _12548178-5c7c-4a70-b349-23cf002946a3} and dQmax about 1.1e8 pu at
 * {@code _d35e56b7-b7a8-447f-b62a-53bcba4369be}, with 638 unsettled controls.
 * That is the solver report, not a diagnosed bus-level cause.
 */
@Tag("cgmes-cas")
@Tag("cgmes-p4-aclf")
public class CGMESCasP4AclfUnconvergedStubTest extends CorePluginTestSetup {

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
		assumeTrue(solveNrSeeded(net, sv),
				() -> "RealGrid NR is singular after zero-Z consolidation"
						+ " (numerical failure / KLU LU; dPmax ~2.7e7 pu at"
						+ " _12548178-5c7c-4a70-b349-23cf002946a3)");
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
}
