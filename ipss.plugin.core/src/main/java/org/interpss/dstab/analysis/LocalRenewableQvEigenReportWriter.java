package org.interpss.dstab.analysis;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

import org.interpss.dstab.analysis.LocalRenewableQvEigenAnalyzer.Analysis;
import org.interpss.dstab.analysis.LocalRenewableQvEigenAnalyzer.Device;
import org.interpss.dstab.analysis.LocalRenewableQvEigenAnalyzer.OperatingPointConstraint;
import org.interpss.dstab.analysis.LocalRenewableQvEigenAnalyzer.RegionalMode;
import org.interpss.dstab.analysis.LocalRenewableQvEigenAnalyzer.StateComponent;

/**
 * Writes the complete evidence bundle produced by
 * {@link LocalRenewableQvEigenAnalyzer} as dependency-free CSV files.
 *
 * <p>The report deliberately preserves both the unconstrained candidate mode
 * and the limiter-region qualification. Consumers must not interpret a
 * positive candidate eigenvalue as a physically admissible growing mode when
 * {@code two_sided_linearization_valid} is false or when the applicable
 * limiter region is not tangent-cone feasible.</p>
 */
public final class LocalRenewableQvEigenReportWriter {
    public static final String SUMMARY_FILE = "summary.csv";
    public static final String COUPLING_FILE = "coupling.csv";
    public static final String STATE_MATRIX_FILE = "state-matrix.csv";
    public static final String DEVICES_FILE = "devices.csv";
    public static final String DOMINANT_MODE_FILE = "dominant-mode.csv";
    public static final String CONSTRAINTS_FILE = "constraints.csv";
    public static final String LIMITER_REGIONS_FILE = "limiter-regions.csv";

    private LocalRenewableQvEigenReportWriter() { }

    /** Write a self-contained diagnostic report and return its resolved files. */
    public static Report write(Path directory, Analysis analysis) throws IOException {
        Objects.requireNonNull(directory, "directory");
        Objects.requireNonNull(analysis, "analysis");
        Path reportDirectory = directory.toAbsolutePath().normalize();
        Files.createDirectories(reportDirectory);

        Path coupling = reportDirectory.resolve(COUPLING_FILE);
        Path stateMatrix = reportDirectory.resolve(STATE_MATRIX_FILE);
        Path devices = reportDirectory.resolve(DEVICES_FILE);
        Path dominantMode = reportDirectory.resolve(DOMINANT_MODE_FILE);
        Path constraints = reportDirectory.resolve(CONSTRAINTS_FILE);
        Path limiterRegions = reportDirectory.resolve(LIMITER_REGIONS_FILE);
        Path summary = reportDirectory.resolve(SUMMARY_FILE);

        writeMatrix(coupling, "response_bus", analysis.busIds(), analysis.busIds(),
                analysis.couplingMatrix());
        List<String> stateLabels = analysis.dominantMode().components().stream()
                .map(component -> component.deviceId() + "/" + component.state()).toList();
        writeMatrix(stateMatrix, "state", stateLabels, stateLabels,
                analysis.stateMatrix());
        writeDevices(devices, analysis.devices());
        writeDominantMode(dominantMode, analysis.dominantMode().components());
        writeConstraints(constraints, analysis.operatingPointConstraints());
        writeLimiterRegions(limiterRegions, analysis.limiterRegionModes());
        writeSummary(summary, analysis);

        return new Report(reportDirectory, summary, coupling,
                stateMatrix, devices, dominantMode, constraints, limiterRegions);
    }

    private static void writeDevices(Path path, List<Device> devices) throws IOException {
        List<List<?>> rows = new ArrayList<>();
        rows.add(List.of("device_id", "bus_index", "system_scale", "v0", "iq0", "tg",
                "reeca_kqp", "reeca_kqi", "reeca_kvp", "reeca_kvi", "repca_tfltr",
                "repca_kp", "repca_ki", "repca_tft", "repca_tfv", "reeca_vmin",
                "reeca_vmax", "repca_qmin", "repca_qmax"));
        for (Device device : devices) {
            rows.add(List.of(device.deviceId(), device.busIndex(), device.systemScale(),
                    device.v0(), device.iq0(), device.tg(), device.kqp(), device.kqi(),
                    device.kvp(), device.kvi(), device.tfltr(), device.plantKp(),
                    device.plantKi(), device.tft(), device.tfv(), device.reecaVmin(),
                    device.reecaVmax(), device.plantQmin(), device.plantQmax()));
        }
        writeRows(path, rows);
    }

    private static void writeDominantMode(Path path, List<StateComponent> components)
            throws IOException {
        List<List<?>> rows = new ArrayList<>();
        rows.add(List.of("device_id", "state", "normalized_real", "normalized_imaginary",
                "normalized_magnitude"));
        for (StateComponent component : components) {
            rows.add(List.of(component.deviceId(), component.state(),
                    component.normalizedReal(), component.normalizedImaginary(),
                    component.magnitude()));
        }
        writeRows(path, rows);
    }

    private static void writeConstraints(Path path,
            List<OperatingPointConstraint> constraints) throws IOException {
        List<List<?>> rows = new ArrayList<>();
        rows.add(List.of("device_id", "signal", "value", "lower", "upper",
                "explanation"));
        for (OperatingPointConstraint constraint : constraints) {
            rows.add(List.of(constraint.deviceId(), constraint.signal(), constraint.value(),
                    constraint.lower(), constraint.upper(), constraint.explanation()));
        }
        writeRows(path, rows);
    }

    private static void writeLimiterRegions(Path path, List<RegionalMode> regions)
            throws IOException {
        List<List<?>> rows = new ArrayList<>();
        rows.add(List.of("region", "dominant_real", "dominant_imaginary", "frequency_hz",
                "cone_feasible", "witness_phase_radians", "assumptions"));
        for (int index = 0; index < regions.size(); index++) {
            RegionalMode region = regions.get(index);
            String assumptions = region.assumptions().stream()
                    .map(value -> value.deviceId() + "/" + value.signal() + "="
                            + value.branch())
                    .collect(Collectors.joining(";"));
            rows.add(List.of(index, region.dominantMode().real(),
                    region.dominantMode().imaginary(), region.dominantMode().frequencyHz(),
                    region.tangentCone().feasible(),
                    region.tangentCone().witnessPhaseRadians(), assumptions));
        }
        writeRows(path, rows);
    }

    private static void writeSummary(Path path, Analysis analysis) throws IOException {
        var mode = analysis.dominantMode();
        writeRows(path, List.of(
                List.of("metric", "value"),
                List.of("analysis_scope", "local_REPCA1_REECA1_REGCA1_QV"),
                List.of("bus_count", analysis.busIds().size()),
                List.of("device_count", analysis.devices().size()),
                List.of("state_count", analysis.stateMatrix().length),
                List.of("two_sided_linearization_valid",
                        analysis.isTwoSidedLinearizationValid()),
                List.of("operating_point_constraint_count",
                        analysis.operatingPointConstraints().size()),
                List.of("limiter_region_enumeration_complete",
                        analysis.limiterRegionEnumerationComplete()),
                List.of("dominant_eigenvalue_real", mode.real()),
                List.of("dominant_eigenvalue_imaginary", mode.imaginary()),
                List.of("dominant_frequency_hz", mode.frequencyHz()),
                List.of("interpretation", analysis.isTwoSidedLinearizationValid()
                        ? "two_sided_local_mode"
                        : "candidate_requires_limiter_region_qualification")));
    }

    private static void writeMatrix(Path path, String rowHeader, List<String> rowLabels,
            List<String> columnLabels, double[][] values) throws IOException {
        if (values.length != rowLabels.size()) {
            throw new IllegalArgumentException("Matrix row count does not match labels");
        }
        List<List<?>> rows = new ArrayList<>();
        List<Object> header = new ArrayList<>();
        header.add(rowHeader);
        header.addAll(columnLabels);
        rows.add(header);
        for (int row = 0; row < values.length; row++) {
            if (values[row].length != columnLabels.size()) {
                throw new IllegalArgumentException("Matrix column count does not match labels");
            }
            List<Object> fields = new ArrayList<>();
            fields.add(rowLabels.get(row));
            for (double value : values[row]) fields.add(value);
            rows.add(fields);
        }
        writeRows(path, rows);
    }

    private static void writeRows(Path path, List<? extends List<?>> rows)
            throws IOException {
        String content = rows.stream().map(row -> row.stream()
                .map(LocalRenewableQvEigenReportWriter::csv)
                .collect(Collectors.joining(",")))
                .collect(Collectors.joining("\n", "", "\n"));
        Files.writeString(path, content);
    }

    private static String csv(Object value) {
        String text = String.valueOf(value);
        if (text.indexOf(',') < 0 && text.indexOf('"') < 0
                && text.indexOf('\n') < 0 && text.indexOf('\r') < 0) return text;
        return '"' + text.replace("\"", "\"\"") + '"';
    }

    /** Files emitted by one report write. */
    public record Report(Path directory, Path summary, Path coupling,
            Path stateMatrix, Path devices, Path dominantMode,
            Path constraints, Path limiterRegions) { }
}
