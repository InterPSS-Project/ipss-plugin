package org.interpss.core.adapter.cim;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.nio.file.Files;
import java.nio.file.Path;

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
 * P4 stubs: import + NR load-flow smoke (convergence), not SV golden compares yet.
 *
 * <p>SV voltage/angle/flow parity against Aclf results is intentionally left as
 * TODO comments — needs stable bus mapping and SV injection from the parser.
 */
@Tag("cgmes-cas")
@Tag("requires-cas-download")
@Tag("cgmes-p4-aclf")
public class CGMESCasP4AclfSmokeStubTest extends CorePluginTestSetup {

	private static final String TD30_CAS = "testData/adpter/cim/cgmes3.0/cas/";

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

	private static void runNrSmoke(AclfNetwork net) throws Exception {
		LoadflowAlgorithm algo = LoadflowAlgoObjectFactory.createLoadflowAlgorithm(net);
		algo.setInitBusVoltage(true);
		algo.setLfMethod(AclfMethodType.NR);
		algo.loadflow();
		assertTrue(net.isLfConverged(), "NR load-flow should converge (P4 smoke)");
		// P4 TODO: compare net bus V/angle to SV Voltage/Angle for matched TopologicalNodes
		// P4 TODO: compare branch flows to SvPowerFlow
	}

	@Test
	@DisplayName("P4: MiniGrid-Merged import + NR load-flow smoke")
	public void testP4_MiniGridMerged_AclfConverge() throws Exception {
		Path dir = casDir("MiniGrid-Merged", "MiniGrid/MiniGrid-Merged");
		assumeTrue(Files.isDirectory(dir), () -> "MiniGrid-Merged missing: " + dir);
		AclfNetwork net = new CGMESDirectParser().parse(new String[] {
				mustFile(dir, "MiniGrid_EQ.xml").toAbsolutePath().toString(),
				mustFile(dir, "MiniGrid_SSH.xml").toAbsolutePath().toString(),
				mustFile(dir, "MiniGrid_TP.xml").toAbsolutePath().toString(),
				mustFile(dir, "MiniGrid_SV.xml").toAbsolutePath().toString(),
				mustFile(dir, "MiniGrid_EQBD.xml").toAbsolutePath().toString()
		});
		assertTrue(net.getNoBus() > 0);
		runNrSmoke(net);
	}

	@Test
	@DisplayName("P4: PowerFlow explicit case import + NR load-flow smoke")
	public void testP4_PowerFlow_AclfConverge() throws Exception {
		Path primary = casDir("PowerFlow-Instance", "PowerFlow/PowerFlow");
		Path dir = Files.isDirectory(primary)
				? primary
				: casDir("PowerFlow", "PowerFlow").resolve("PowerFlow");
		assumeTrue(Files.isDirectory(dir), () -> "PowerFlow instance missing: " + dir);
		AclfNetwork net = new CGMESDirectParser().parse(new String[] {
				mustFile(dir, "PowerFlow_EQ.xml").toAbsolutePath().toString(),
				mustFile(dir, "PowerFlow_SSH.xml").toAbsolutePath().toString(),
				mustFile(dir, "PowerFlow_TP.xml").toAbsolutePath().toString(),
				mustFile(dir, "PowerFlow_SV.xml").toAbsolutePath().toString()
		});
		assertTrue(net.getNoBus() > 0);
		runNrSmoke(net);
	}

	@Test
	@DisplayName("P4: PST Type1 import + NR load-flow smoke (tap semantics later)")
	public void testP4_PstType1_AclfConverge() throws Exception {
		Path dir = casDir("PST-PhaseTapChangerLinear-Type1",
				"PST/PST_PhaseTapChangerLinear_Type1");
		assumeTrue(Files.isDirectory(dir), () -> "PST Type1 missing: " + dir);
		AclfNetwork net = new CGMESDirectParser().parse(new String[] {
				mustFile(dir, "PST_Type1_EQ.xml").toAbsolutePath().toString(),
				mustFile(dir, "PST_Type1_SSH.xml").toAbsolutePath().toString(),
				mustFile(dir, "PST_Type1_TP.xml").toAbsolutePath().toString(),
				mustFile(dir, "PST_Type1_SV.xml").toAbsolutePath().toString()
		});
		assertTrue(net.getNoBus() > 0);
		runNrSmoke(net);
		// P4 TODO: assert phase-shift / tap position mapping vs CIM PhaseTapChanger
	}
}
