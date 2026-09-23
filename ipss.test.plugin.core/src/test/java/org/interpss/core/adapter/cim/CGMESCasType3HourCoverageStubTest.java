package org.interpss.core.adapter.cim;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.interpss.CorePluginTestSetup;
import org.interpss.fadapter.cim.CGMESDirectParser;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import com.interpss.core.aclf.AclfNetwork;

/**
 * Parameterized MicroGrid Type3 CGM hour coverage (shared EQ, per-hour SSH/TP/SV).
 *
 * <p>Default source returns a <strong>subset</strong> of hours (first, +6h, +12h, last)
 * so a normal {@code cgmes-cas} run stays bounded. Set
 * {@code -Dipss.cgmes.type3.allHours=true} to exercise all 24 published hours.
 */
@Tag("cgmes-cas")
public class CGMESCasType3HourCoverageStubTest extends CorePluginTestSetup {

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

	static Stream<String> type3Hours() throws Exception {
		Path igms = casDir("MicroGrid-Type3-IGMs", "MicroGrid/MicroGrid-Type3/IGMs");
		assumeTrue(Files.isDirectory(igms), () -> "Type3 IGMs missing: " + igms);
		List<String> hours = new ArrayList<>();
		try (Stream<Path> s = Files.list(igms)) {
			s.map(p -> p.getFileName().toString())
					.filter(n -> n.contains("_BE_SSH_"))
					.map(n -> n.substring(0, n.indexOf("_1D_BE_SSH")))
					.sorted()
					.distinct()
					.forEach(hours::add);
		}
		assumeTrue(!hours.isEmpty(), "No Type3 BE SSH hours found");
		if (Boolean.getBoolean("ipss.cgmes.type3.allHours")) {
			return hours.stream();
		}
		// Subset: first, ~+6h, ~+12h, last
		List<String> subset = new ArrayList<>();
		subset.add(hours.get(0));
		if (hours.size() > 6) {
			subset.add(hours.get(6));
		}
		if (hours.size() > 12) {
			subset.add(hours.get(12));
		}
		if (hours.size() > 1) {
			subset.add(hours.get(hours.size() - 1));
		}
		return subset.stream().distinct();
	}

	private static Path pickContaining(Path dir, String profile, String mustContain) throws Exception {
		assumeTrue(Files.isDirectory(dir), () -> "Missing dir: " + dir);
		String needle = mustContain.toUpperCase();
		String p = profile.toUpperCase();
		List<Path> xmls;
		try (Stream<Path> s = Files.list(dir)) {
			xmls = s.filter(Files::isRegularFile)
					.filter(x -> x.getFileName().toString().toLowerCase().endsWith(".xml"))
					.filter(x -> x.getFileName().toString().toUpperCase().contains(needle))
					.sorted()
					.collect(Collectors.toList());
		}
		assumeTrue(!xmls.isEmpty(), () -> "No *" + mustContain + "* under " + dir);
		for (Path x : xmls) {
			String n = x.getFileName().toString().toUpperCase();
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
		assumeTrue(false, () -> "No profile " + profile + " for " + mustContain);
		return dir;
	}

	private static Path pickEqBd(Path igms) throws Exception {
		try (Stream<Path> s = Files.list(igms)) {
			return s.filter(Files::isRegularFile)
					.filter(x -> {
						String n = x.getFileName().toString().toUpperCase();
						return n.contains("EQ_BD") || n.contains("EQBD");
					})
					.findFirst()
					.orElseThrow(() -> new org.opentest4j.TestAbortedException("No EQ_BD in " + igms));
		}
	}

	@ParameterizedTest(name = "Type3 CGM hour {0}")
	@MethodSource("type3Hours")
	@DisplayName("P2: CAS v3.0 MicroGrid Type3 CGM hour (shared EQ)")
	public void testCasV30_MicroGridType3_CgmHour(String hour) throws Exception {
		Path igms = casDir("MicroGrid-Type3-IGMs", "MicroGrid/MicroGrid-Type3/IGMs");
		Path cgms = casDir("MicroGrid-Type3-CGMs", "MicroGrid/MicroGrid-Type3/CGMs");
		assumeTrue(Files.isDirectory(igms) && Files.isDirectory(cgms));

		// EQ is published once for the series (first hour stamp)
		Path beEq = pickContaining(igms, "EQ", "_1D_BE_EQ");
		Path nlEq = pickContaining(igms, "EQ", "_1D_NL_EQ");
		Path bd = pickEqBd(igms);
		Path beSsh = pickContaining(igms, "SSH", hour + "_1D_BE");
		Path nlSsh = pickContaining(igms, "SSH", hour + "_1D_NL");
		Path tp = pickContaining(cgms, "TP", hour + "_1D_ASSEMBLED");
		Path sv = pickContaining(cgms, "SV", hour + "_1D_ASSEMBLED");

		AclfNetwork net = new CGMESDirectParser().parse(new String[] {
				bd.toAbsolutePath().toString(),
				beEq.toAbsolutePath().toString(),
				nlEq.toAbsolutePath().toString(),
				beSsh.toAbsolutePath().toString(),
				nlSsh.toAbsolutePath().toString(),
				tp.toAbsolutePath().toString(),
				sv.toAbsolutePath().toString()
		});
		assertTrue(net.getNoBus() > 0, () -> "Type3 CGM " + hour + " should create buses");
		assertTrue(net.getNoBranch() > 0, () -> "Type3 CGM " + hour + " should create branches");
	}
}
