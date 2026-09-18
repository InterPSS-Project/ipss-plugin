package org.interpss.core.adapter.cim;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.interpss.CorePluginTestSetup;
import org.interpss.fadapter.cim.CGMESDirectParser;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import com.interpss.core.aclf.AclfNetwork;

/**
 * Remaining ReliCapGrid coverage: Britheim / Portheim IGMs, HVDC corridors,
 * multi-MAS+DC CGM assemble smoke (Jotunheim TP/SV), and Network Code profile
 * <em>file presence</em> (not Aclf import — NCP is out of scope for the parser).
 */
@Tag("cgmes-cas")
@Tag("requires-cas-download")
@Tag("relicap")
public class CGMESReliCapDcCoverageStubTest extends CorePluginTestSetup {

	private static final String TD30_CAS = "testData/adpter/cim/cgmes3.0/cas/";

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

	private static Path pickProfile(Path dir, String profile) throws Exception {
		assumeTrue(Files.isDirectory(dir), () -> "Missing dir: " + dir);
		String p = profile.toUpperCase(Locale.ROOT);
		List<Path> xmls;
		try (Stream<Path> s = Files.list(dir)) {
			xmls = s.filter(Files::isRegularFile)
					.filter(x -> x.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".xml"))
					.sorted()
					.collect(Collectors.toList());
		}
		for (Path x : xmls) {
			String n = x.getFileName().toString().toUpperCase(Locale.ROOT);
			if ("EQ".equals(p)) {
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
		return dir;
	}

	private static String[] abs(Path... files) {
		String[] out = new String[files.length];
		for (int i = 0; i < files.length; i++) {
			out[i] = files[i].toAbsolutePath().toString();
		}
		return out;
	}

	private static void requireFiles(Path... files) {
		for (Path f : files) {
			assumeTrue(Files.isRegularFile(f), () -> "Missing: " + f);
		}
	}

	private static AclfNetwork importIgm(String linkName, String relative) throws Exception {
		Path dir = relicapDir(linkName, relative);
		assumeTrue(Files.isDirectory(dir), () -> "Missing ReliCap dir: " + dir);
		Path eq = pickProfile(dir, "EQ");
		Path ssh = pickProfile(dir, "SSH");
		Path tp = pickProfile(dir, "TP");
		List<Path> args = new ArrayList<>();
		args.add(eq);
		args.add(ssh);
		args.add(tp);
		try {
			args.add(pickProfile(dir, "SV"));
		} catch (org.opentest4j.TestAbortedException ignore) {
			// EQ+SSH+TP enough
		}
		requireFiles(args.toArray(new Path[0]));
		return new CGMESDirectParser().parse(abs(args.toArray(new Path[0])));
	}

	// -------------------------------------------------------------------------
	// Remaining AC MAS IGMs
	// -------------------------------------------------------------------------

	@Test
	@DisplayName("P3: ReliCapGrid Britheim IGM EQ+SSH+TP(+SV)")
	public void testReliCap_Britheim_Igm_Import() throws Exception {
		AclfNetwork net = importIgm("ReliCap-Britheim-cimxml", "Instance/Britheim/Grid/cimxml");
		assertTrue(net.getNoBus() > 0);
		assertTrue(net.getNoBranch() > 0);
	}

	@Test
	@DisplayName("P3: ReliCapGrid Portheim IGM EQ+SSH+TP(+SV)")
	public void testReliCap_Portheim_Igm_Import() throws Exception {
		AclfNetwork net = importIgm("ReliCap-Portheim-cimxml", "Instance/Portheim/Grid/cimxml");
		assertTrue(net.getNoBus() > 0);
		assertTrue(net.getNoBranch() > 0);
	}

	// -------------------------------------------------------------------------
	// HVDC corridors
	// -------------------------------------------------------------------------

	@Test
	@DisplayName("P3: ReliCapGrid DC Espheim–Svedala HVDC EQ+SSH+TP(+SV) smoke")
	public void testReliCap_DcEspheimSvedala_ImportSmoke() throws Exception {
		AclfNetwork net = importIgm("ReliCap-DC-Espheim-Svedala-cimxml",
				"Instance/DC-Espheim-Svedala/Grid/cimxml");
		assertTrue(net.getNoBus() > 0, "HVDC corridor should at least build topology");
		// May expose DC converter mapping gaps — keep as coverage probe
	}

	@Test
	@DisplayName("P3: ReliCapGrid DC Nordheim–Galia HVDC EQ+SSH+TP(+SV) smoke")
	public void testReliCap_DcNordheimGalia_ImportSmoke() throws Exception {
		AclfNetwork net = importIgm("ReliCap-DC-Nordheim-Galia-cimxml",
				"Instance/DC-Nordheim-Galia/Grid/cimxml");
		assertTrue(net.getNoBus() > 0, "HVDC corridor should at least build topology");
	}

	// -------------------------------------------------------------------------
	// Boundary presence + multi-MAS+DC CGM (Jotunheim TP/SV)
	// -------------------------------------------------------------------------

	@Test
	@DisplayName("P3: ReliCapGrid boundary EQBD-style files present")
	public void testReliCap_BoundaryFiles_Present() throws Exception {
		Path dir = relicapDir("ReliCap-boundary-cimxml", "Instance/boundaryData/Grid/cimxml");
		assumeTrue(Files.isDirectory(dir), () -> "boundaryData missing: " + dir);
		List<Path> borders;
		try (Stream<Path> s = Files.list(dir)) {
			borders = s.filter(Files::isRegularFile)
					.filter(p -> p.getFileName().toString().startsWith("Boundary_"))
					.sorted()
					.collect(Collectors.toList());
		}
		assertTrue(borders.size() >= 4, () -> "Expected several Boundary_*.xml, found " + borders);
		// Not parsed alone (EQBD fragments) — used by multi-MAS CGM smoke below
	}

	@Test
	@Tag("relicap-cgm")
	@DisplayName("P3: ReliCapGrid multi-MAS+DC CGM smoke (EQ/SSH + Jotunheim TP/SV + boundaries)")
	public void testReliCap_MultiMasDc_CgmAssemble_Smoke() throws Exception {
		// Jotunheim Grid/cimxml ships TP+SV only (assembled CGM state); EQ/SSH come from MAS + DC.
		String[][] igms = {
				{ "ReliCap-Svedala-cimxml", "Instance/Svedala/Grid/cimxml" },
				{ "ReliCap-Belgovia-cimxml", "Instance/Belgovia/Grid/cimxml" },
				{ "ReliCap-Espheim-cimxml", "Instance/Espheim/Grid/cimxml" },
				{ "ReliCap-Nordheim-cimxml", "Instance/Nordheim/Grid/cimxml" },
				{ "ReliCap-Galia-cimxml", "Instance/Galia/Grid/cimxml" },
				{ "ReliCap-Britheim-cimxml", "Instance/Britheim/Grid/cimxml" },
				{ "ReliCap-Portheim-cimxml", "Instance/Portheim/Grid/cimxml" },
				{ "ReliCap-DC-Espheim-Svedala-cimxml", "Instance/DC-Espheim-Svedala/Grid/cimxml" },
				{ "ReliCap-DC-Nordheim-Galia-cimxml", "Instance/DC-Nordheim-Galia/Grid/cimxml" },
		};
		List<Path> args = new ArrayList<>();
		for (String[] igm : igms) {
			Path dir = relicapDir(igm[0], igm[1]);
			assumeTrue(Files.isDirectory(dir), () -> "Missing for CGM assemble: " + dir);
			args.add(pickProfile(dir, "EQ"));
			args.add(pickProfile(dir, "SSH"));
		}
		Path jotun = relicapDir("ReliCap-Jotunheim-cimxml", "Instance/Jotunheim/Grid/cimxml");
		assumeTrue(Files.isDirectory(jotun), () -> "Jotunheim TP/SV missing: " + jotun);
		args.add(pickProfile(jotun, "TP"));
		args.add(pickProfile(jotun, "SV"));

		Path bdDir = relicapDir("ReliCap-boundary-cimxml", "Instance/boundaryData/Grid/cimxml");
		if (Files.isDirectory(bdDir)) {
			try (Stream<Path> s = Files.list(bdDir)) {
				s.filter(Files::isRegularFile)
						.filter(p -> p.getFileName().toString().endsWith(".xml"))
						.sorted()
						.forEach(args::add);
			}
		}

		requireFiles(args.toArray(new Path[0]));
		AclfNetwork net = new CGMESDirectParser().parse(abs(args.toArray(new Path[0])));
		assertTrue(net.getNoBus() > 0, "Multi-MAS+DC CGM should create buses");
		assertTrue(net.getNoBranch() > 0, "Multi-MAS+DC CGM should create branches");
		// Large assemble — tighten counts after green run; HVDC gaps may appear
	}

	// -------------------------------------------------------------------------
	// Network Code Profiles — presence only (not Aclf import targets)
	// -------------------------------------------------------------------------

	@Test
	@Tag("relicap-ncp")
	@DisplayName("P3: ReliCap Britheim Network Code profiles present (AE/CO/ER/SIS/SSI)")
	public void testReliCap_Britheim_NcpFiles_Present() throws Exception {
		Path dir = relicapDir("ReliCap-Britheim-NCP", "Instance/Britheim/NetworkCode/cimxml");
		assumeTrue(Files.isDirectory(dir), () -> "Britheim NCP missing: " + dir);
		for (String name : List.of("Britheim_AE.xml", "Britheim_CO.xml", "Britheim_ER.xml",
				"Britheim_SIS.xml", "Britheim_SSI.xml")) {
			assertTrue(Files.isRegularFile(dir.resolve(name)), () -> "Missing NCP file: " + name);
		}
		// Intentionally no CGMESDirectParser — NCP is not an Aclf equipment model
	}

	@Test
	@Tag("relicap-ncp")
	@DisplayName("P3: ReliCap DC Espheim–Svedala Network Code CO/RA present")
	public void testReliCap_DcEspheimSvedala_NcpFiles_Present() throws Exception {
		Path dir = relicapDir("ReliCap-DC-Espheim-Svedala-NCP",
				"Instance/DC-Espheim-Svedala/NetworkCode/cimxml");
		assumeTrue(Files.isDirectory(dir), () -> "DC Espheim-Svedala NCP missing: " + dir);
		assertTrue(Files.isRegularFile(dir.resolve("DC-Espheim-Svedala_CO.xml")));
		assertTrue(Files.isRegularFile(dir.resolve("DC-Espheim-Svedala_RA.xml")));
	}
}
