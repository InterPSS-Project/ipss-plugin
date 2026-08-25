package org.interpss.fadapter.psse;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.interpss.core.aclf.BaseAclfNetwork;
import com.interpss.core.algo.LoadflowAlgorithm;
import com.interpss.core.algo.LoadflowAlgorithmInitializer;

/**
 * Immutable representation of the PSS/E v34+ system-wide load-flow solution
 * records. Recognized fields are exposed with their native types, while the
 * complete key/value block and original lines remain available so unsupported
 * settings are not silently discarded.
 *
 * <p>Application is intentionally selective. Numerical fields with a verified
 * InterPSS equivalent are always eligible; binary device-activity fields are
 * applied only by {@link ApplicationPolicy#SAVED_SOLUTION_REPLAY}. Unknown,
 * unsupported, malformed, and non-binary values are reported instead of being
 * coerced. Normal versus non-divergent NR is an explicit run-configuration
 * policy and is not inferred from a non-standard solver token.</p>
 *
 * <p>Callers should apply these imported values before explicit JSON settings.
 * That ordering makes source data the baseline while keeping a field supplied
 * by the user authoritative.</p>
 */
public final class PsseLoadflowSolutionSettings
		implements LoadflowAlgorithmInitializer {

	public static final String APPLICATION_REPORT_EXTRA_INFO_KEY =
			PsseLoadflowSolutionSettings.class.getName() + ".applicationReport";

	private static final Pattern VALUE_PATTERN = Pattern.compile(
			"(?i)([A-Z][A-Z0-9_]*)\\s*=\\s*([^,\\s/]+)");
	private static final Map<String, Set<String>> KNOWN_FIELDS = Map.of(
			"GENERAL", Set.of("THRSHZ", "PQBRAK", "BLOWUP"),
			"GAUSS", Set.of("ITMX", "ACCP", "ACCQ", "ACCM", "TOL"),
			"NEWTON", Set.of("ITMXN", "ACCN", "TOLN", "VCTOLQ", "VCTOLV",
					"DVLIM", "NDVFCT"),
			"ADJUST", Set.of("ADJTHR", "ACCTAP", "TAPLIM", "SWVBND",
					"MXTPSS", "MXSWIM"),
			"TYSL", Set.of("ITMXTY", "ACCTY", "TOLTY"),
			"SOLVER", Set.of("$ACTIVITY", "ACTAPS", "AREAIN", "PHSHFT",
					"DCTAPS", "SWSHNT", "FLATST", "VARLIM"));

	public record GeneralSettings(Double thrshz, Double pqbrak, Double blowup) {}

	public record GaussSettings(Integer itmx, Double accp, Double accq,
			Double accm, Double tol) {}

	public record NewtonSettings(Integer itmxn, Double accn, Double toln,
			Double vctolq, Double vctolv, Double dvlim, Double ndvfct) {}

	public record AdjustSettings(Double adjthr, Double acctap, Double taplim,
			Double swvbnd, Integer mxtpss, Integer mxswim) {}

	public record TyslSettings(Integer itmxty, Double accty, Double tolty) {}

	public record SolverSettings(String activity, Integer actaps,
			Integer areain, Integer phshft, Integer dctaps, Integer swshnt,
			Integer flatst, Integer varlim, Integer nondiv) {}

	public enum MappingStatus {
		APPLIED,
		UNSUPPORTED,
		INVALID
	}

	/**
	 * Numerical settings are safe defaults for every imported case. Solver
	 * activity flags are applied only for an explicit saved-solution replay,
	 * because a caller may intentionally construct a different control study.
	 */
	public enum ApplicationPolicy {
		NUMERICAL_SETTINGS,
		SAVED_SOLUTION_REPLAY
	}

	public record Mapping(String field, String rawValue, String resolvedValue,
			MappingStatus status, String message) {}

	public record ApplicationReport(List<Mapping> mappings) {
		public ApplicationReport {
			mappings = List.copyOf(mappings);
		}
	}

	private final int sourceVersion;
	private final GeneralSettings general;
	private final GaussSettings gauss;
	private final NewtonSettings newton;
	private final AdjustSettings adjust;
	private final TyslSettings tysl;
	private final SolverSettings solver;
	private final Map<String, Map<String, String>> rawValues;
	private final List<String> rawLines;

	private PsseLoadflowSolutionSettings(int sourceVersion,
			Map<String, Map<String, String>> sections, List<String> rawLines) {
		this.sourceVersion = sourceVersion;
		this.rawValues = immutableSections(sections);
		this.rawLines = List.copyOf(rawLines);

		Map<String, String> g = section("GENERAL");
		Map<String, String> gs = section("GAUSS");
		Map<String, String> n = section("NEWTON");
		Map<String, String> a = section("ADJUST");
		Map<String, String> t = section("TYSL");
		Map<String, String> s = section("SOLVER");
		this.general = new GeneralSettings(dbl(g, "THRSHZ"), dbl(g, "PQBRAK"),
				dbl(g, "BLOWUP"));
		this.gauss = new GaussSettings(integer(gs, "ITMX"), dbl(gs, "ACCP"),
				dbl(gs, "ACCQ"), dbl(gs, "ACCM"), dbl(gs, "TOL"));
		this.newton = new NewtonSettings(integer(n, "ITMXN"), dbl(n, "ACCN"),
				dbl(n, "TOLN"), dbl(n, "VCTOLQ"), dbl(n, "VCTOLV"),
				dbl(n, "DVLIM"), dbl(n, "NDVFCT"));
		this.adjust = new AdjustSettings(dbl(a, "ADJTHR"), dbl(a, "ACCTAP"),
				dbl(a, "TAPLIM"), dbl(a, "SWVBND"), integer(a, "MXTPSS"),
				integer(a, "MXSWIM"));
		this.tysl = new TyslSettings(integer(t, "ITMXTY"), dbl(t, "ACCTY"),
				dbl(t, "TOLTY"));
		this.solver = new SolverSettings(s.get("$ACTIVITY"), integer(s, "ACTAPS"),
				integer(s, "AREAIN"), integer(s, "PHSHFT"), integer(s, "DCTAPS"),
				integer(s, "SWSHNT"), integer(s, "FLATST"), integer(s, "VARLIM"),
				null);
	}

	public static Builder builder(int sourceVersion) {
		return new Builder(sourceVersion);
	}

	public int sourceVersion() { return sourceVersion; }
	public GeneralSettings general() { return general; }
	public GaussSettings gauss() { return gauss; }
	public NewtonSettings newton() { return newton; }
	public AdjustSettings adjust() { return adjust; }
	public TyslSettings tysl() { return tysl; }
	public SolverSettings solver() { return solver; }
	public Map<String, Map<String, String>> rawValues() { return rawValues; }
	public List<String> rawLines() { return rawLines; }

	@Override
	public void apply(LoadflowAlgorithm algorithm, BaseAclfNetwork<?, ?> network) {
		ApplicationReport report = applyTo(algorithm, network,
				ApplicationPolicy.NUMERICAL_SETTINGS);
		network.getExtraInfo().put(APPLICATION_REPORT_EXTRA_INFO_KEY, report);
	}

	/**
	 * Apply mappings whose InterPSS semantics are direct. Factory defaults have
	 * already been installed; normal caller configuration performed after
	 * factory creation therefore overrides these imported values.
	 */
	public ApplicationReport applyTo(LoadflowAlgorithm algorithm,
			BaseAclfNetwork<?, ?> network) {
		return applyTo(algorithm, network, ApplicationPolicy.NUMERICAL_SETTINGS);
	}

	public ApplicationReport applyTo(LoadflowAlgorithm algorithm,
			BaseAclfNetwork<?, ?> network, ApplicationPolicy policy) {
		List<Mapping> result = new ArrayList<>();
		applyPositiveDouble(result, "GENERAL.THRSHZ", raw("GENERAL", "THRSHZ"),
				general.thrshz(), value -> network.setZeroZBranchThreshold(value),
				"pu");
		applyPositiveDouble(result, "GENERAL.PQBRAK", raw("GENERAL", "PQBRAK"),
				general.pqbrak(),
				value -> network.getBusLoadLowVoltConfig().setVConstPMin(value), "pu");
		applyPositiveInt(result, "NEWTON.ITMXN", raw("NEWTON", "ITMXN"),
				newton.itmxn(), algorithm::setMaxIterations, "iterations");
		applyPositiveDouble(result, "NEWTON.DVLIM", raw("NEWTON", "DVLIM"),
				newton.dvlim(), value -> {
					algorithm.setVariableUpdateLimit(true);
					algorithm.setDeltaVMagLimit(value);
					// PSS/E DVLIM scales both vectors based only on max |dV/V|.
					algorithm.setDeltaVAngLimit(Double.MAX_VALUE);
				}, "relative dV/V; uniformly scales magnitude and angle corrections");

		if (newton.toln() != null) {
			double baseMva = network.getBaseKva() * 0.001;
			if (newton.toln() > 0.0 && baseMva > 0.0) {
				double tolerancePu = newton.toln() / baseMva;
				algorithm.setTolerance(tolerancePu);
				result.add(applied("NEWTON.TOLN", raw("NEWTON", "TOLN"),
						Double.toString(tolerancePu), "MVA converted to pu on case base"));
			} else {
				result.add(invalid("NEWTON.TOLN", raw("NEWTON", "TOLN"),
						"TOLN and case base must be positive"));
			}
		} else if (raw("NEWTON", "TOLN") != null) {
			result.add(invalid("NEWTON.TOLN", raw("NEWTON", "TOLN"),
					"value is not a valid number"));
		}

		applyPositiveDouble(result, "ADJUST.ADJTHR", raw("ADJUST", "ADJTHR"),
				adjust.adjthr(),
				value -> algorithm.getLfAdjAlgo().setVoltageAdjustmentThreshold(value),
				"pu voltage-iterate gate");
		applyNonNegativeInt(result, "ADJUST.MXTPSS", raw("ADJUST", "MXTPSS"),
				adjust.mxtpss(),
				value -> algorithm.getLfAdjAlgo()
						.setMaxTapAndShuntAdjustmentIterations(value), "iterations");

		if (policy == ApplicationPolicy.SAVED_SOLUTION_REPLAY) {
			applyBinary(result, "SOLVER.ACTAPS", solver.actaps(), enabled -> algorithm
					.getLfAdjAlgo().getVoltAdjConfig().setXfrTapControl(enabled));
			applyBinary(result, "SOLVER.AREAIN", solver.areain(), enabled -> algorithm
					.getNetAdjAlgo().setAreaInterchangeControlEnabled(enabled));
			applyBinary(result, "SOLVER.PHSHFT", solver.phshft(), enabled -> algorithm
					.getLfAdjAlgo().getPowerAdjConfig().setPsXfrPControl(enabled));
			applyBinary(result, "SOLVER.DCTAPS", solver.dctaps(), enabled -> algorithm
					.getLfAdjAlgo().getVoltAdjConfig().setHvdcTapControl(enabled));
			applyBinary(result, "SOLVER.SWSHNT", solver.swshnt(), enabled -> algorithm
					.getLfAdjAlgo().getVoltAdjConfig().setSwitchedShuntAdjust(enabled));
		} else {
			preserveSolverModes(result);
		}

		preserveUnsupported(result);
		return new ApplicationReport(result);
	}

	private void preserveUnsupported(List<Mapping> result) {
		unsupported(result, "GENERAL.BLOWUP", general.blowup());
		unsupported(result, "GAUSS.ITMX", gauss.itmx());
		unsupported(result, "GAUSS.ACCP", gauss.accp());
		unsupported(result, "GAUSS.ACCQ", gauss.accq());
		unsupported(result, "GAUSS.ACCM", gauss.accm());
		unsupported(result, "GAUSS.TOL", gauss.tol());
		unsupported(result, "NEWTON.ACCN", newton.accn());
		unsupported(result, "NEWTON.VCTOLQ", newton.vctolq());
		unsupported(result, "NEWTON.VCTOLV", newton.vctolv());
		unsupported(result, "NEWTON.NDVFCT", newton.ndvfct());
		unsupported(result, "ADJUST.ACCTAP", adjust.acctap());
		unsupported(result, "ADJUST.TAPLIM", adjust.taplim());
		unsupported(result, "ADJUST.SWVBND", adjust.swvbnd());
		unsupported(result, "ADJUST.MXSWIM", adjust.mxswim());
		unsupported(result, "TYSL.ITMXTY", tysl.itmxty());
		unsupported(result, "TYSL.ACCTY", tysl.accty());
		unsupported(result, "TYSL.TOLTY", tysl.tolty());
		unsupported(result, "SOLVER.FLATST", solver.flatst());
		unsupported(result, "SOLVER.VARLIM", solver.varlim());
		preserveUnknown(result);
	}

	private void preserveUnknown(List<Mapping> result) {
		rawValues.forEach((section, values) -> values.forEach((key, value) -> {
			if (!KNOWN_FIELDS.getOrDefault(section, Set.of()).contains(key)) {
				result.add(new Mapping(section + "." + key, value, null,
						MappingStatus.UNSUPPORTED,
						"preserved unknown field; no InterPSS mapping"));
			}
		}));
	}

	private void preserveSolverModes(List<Mapping> result) {
		preservedSolverMode(result, "SOLVER.ACTAPS", solver.actaps());
		preservedSolverMode(result, "SOLVER.AREAIN", solver.areain());
		preservedSolverMode(result, "SOLVER.PHSHFT", solver.phshft());
		preservedSolverMode(result, "SOLVER.DCTAPS", solver.dctaps());
		preservedSolverMode(result, "SOLVER.SWSHNT", solver.swshnt());
	}

	private void preservedSolverMode(List<Mapping> result, String field,
			Integer value) {
		String[] parts = field.split("\\.", 2);
		String rawValue = raw(parts[0], parts[1]);
		if (value == null) {
			if (rawValue != null) {
				result.add(invalid(field, rawValue, "value is not a valid integer"));
			}
			return;
		}
		result.add(new Mapping(field, raw(parts[0], parts[1]), null,
				MappingStatus.UNSUPPORTED,
				"preserved; not applied by this RAW settings application policy"));
	}

	private void unsupported(List<Mapping> result, String field, Object value) {
		String[] parts = field.split("\\.", 2);
		String rawValue = raw(parts[0], parts[1]);
		if (rawValue != null) {
			if (value == null) {
				result.add(invalid(field, rawValue, "value is not a valid number"));
				return;
			}
			result.add(new Mapping(field, raw(parts[0], parts[1]), null,
					MappingStatus.UNSUPPORTED,
					"preserved; no verified one-to-one InterPSS mapping"));
		}
	}

	private void applyBinary(List<Mapping> result, String field, Integer value,
			java.util.function.Consumer<Boolean> setter) {
		String[] parts = field.split("\\.", 2);
		String rawValue = raw(parts[0], parts[1]);
		if (value == null) {
			if (rawValue != null) {
				result.add(invalid(field, rawValue, "value is not a valid integer"));
			}
			return;
		}
		if (value == 0 || value == 1) {
			boolean enabled = value == 1;
			setter.accept(enabled);
			result.add(applied(field, rawValue, Boolean.toString(enabled),
					"binary RAW mode mapped to InterPSS enable flag"));
		} else {
			result.add(new Mapping(field, rawValue, null, MappingStatus.UNSUPPORTED,
					"non-binary mode preserved because a Boolean mapping would be lossy"));
		}
	}

	private static void applyPositiveDouble(List<Mapping> result, String field,
			String rawValue, Double value, java.util.function.DoubleConsumer setter,
			String unit) {
		if (value == null) {
			if (rawValue != null) {
				result.add(invalid(field, rawValue, "value is not a valid number"));
			}
			return;
		}
		if (Double.isFinite(value) && value >= 0.0) {
			setter.accept(value);
			result.add(applied(field, rawValue, Double.toString(value), unit));
		} else {
			result.add(invalid(field, rawValue, "value must be finite and non-negative"));
		}
	}

	private static void applyPositiveInt(List<Mapping> result, String field,
			String rawValue, Integer value, java.util.function.IntConsumer setter,
			String unit) {
		if (value == null) {
			if (rawValue != null) {
				result.add(invalid(field, rawValue, "value is not a valid integer"));
			}
			return;
		}
		if (value > 0) {
			setter.accept(value);
			result.add(applied(field, rawValue, Integer.toString(value), unit));
		} else {
			result.add(invalid(field, rawValue, "value must be positive"));
		}
	}

	private static void applyNonNegativeInt(List<Mapping> result, String field,
			String rawValue, Integer value, java.util.function.IntConsumer setter,
			String unit) {
		if (value == null) {
			if (rawValue != null) {
				result.add(invalid(field, rawValue, "value is not a valid integer"));
			}
			return;
		}
		if (value >= 0) {
			setter.accept(value);
			result.add(applied(field, rawValue, Integer.toString(value), unit));
		} else {
			result.add(invalid(field, rawValue, "value must be non-negative"));
		}
	}

	private static Mapping applied(String field, String rawValue,
			String resolvedValue, String message) {
		return new Mapping(field, rawValue, resolvedValue,
				MappingStatus.APPLIED, message);
	}

	private static Mapping invalid(String field, String rawValue, String message) {
		return new Mapping(field, rawValue, null, MappingStatus.INVALID, message);
	}

	private String raw(String section, String key) {
		return section(section).get(key);
	}

	private Map<String, String> section(String name) {
		return rawValues.getOrDefault(name, Map.of());
	}

	private static Double dbl(Map<String, String> values, String key) {
		String value = values.get(key);
		if (value == null) return null;
		try {
			return Double.valueOf(value);
		} catch (NumberFormatException e) {
			return null;
		}
	}

	private static Integer integer(Map<String, String> values, String key) {
		Double value = dbl(values, key);
		if (value == null || !Double.isFinite(value)
				|| value != Math.rint(value)
				|| value < Integer.MIN_VALUE || value > Integer.MAX_VALUE) {
			return null;
		}
		return value.intValue();
	}

	private static Map<String, Map<String, String>> immutableSections(
			Map<String, Map<String, String>> sections) {
		Map<String, Map<String, String>> copy = new LinkedHashMap<>();
		sections.forEach((name, values) -> copy.put(name, Map.copyOf(values)));
		return Map.copyOf(copy);
	}

	public static final class Builder {
		private final int sourceVersion;
		private final Map<String, Map<String, String>> sections =
				new LinkedHashMap<>();
		private final List<String> rawLines = new ArrayList<>();

		private Builder(int sourceVersion) {
			this.sourceVersion = sourceVersion;
		}

		public Builder addLine(String rawLine) {
			if (rawLine == null || rawLine.isBlank()) return this;
			rawLines.add(rawLine);
			String[] tokens = rawLine.split(",", -1);
			String section = tokens[0].trim().toUpperCase(Locale.ROOT);
			Map<String, String> values = sections.computeIfAbsent(section,
					ignored -> new LinkedHashMap<>());
			if ("SOLVER".equals(section) && tokens.length > 1
					&& !tokens[1].contains("=")) {
				String activity = tokens[1].trim();
				if (!activity.isEmpty()) values.put("$ACTIVITY", activity);
			}
			Matcher matcher = VALUE_PATTERN.matcher(rawLine);
			while (matcher.find()) {
				values.put(matcher.group(1).toUpperCase(Locale.ROOT), matcher.group(2));
			}
			return this;
		}

		public PsseLoadflowSolutionSettings build() {
			return new PsseLoadflowSolutionSettings(sourceVersion, sections, rawLines);
		}
	}
}
