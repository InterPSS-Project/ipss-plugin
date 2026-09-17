package org.interpss.core.dstab;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Hashtable;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import org.apache.commons.math3.complex.Complex;
import org.interpss.IpssCorePlugin;
import org.interpss.fadapter.psse.PSSEMultiFileLoader;
import org.interpss.fadapter.psse.dyr.PsseDyrRecord;
import org.interpss.fadapter.psse.dyr.PsseDyrRecordReader;
import org.interpss.dstab.renewable.Regca1Model;
import org.interpss.dstab.renewable.Reeca1Data;
import org.interpss.dstab.renewable.Reeca1Model;
import org.interpss.dstab.renewable.RenewableElectricalController;
import org.interpss.dstab.renewable.Repca1Model;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import com.interpss.common.exp.InterpssException;
import com.interpss.core.acsc.fault.SimpleFaultCode;
import com.interpss.core.algo.LoadflowAlgorithm;
import com.interpss.dstab.BaseDStabNetwork;
import com.interpss.dstab.DStabObjectFactory;
import com.interpss.dstab.DStabGen;
import com.interpss.dstab.algo.DynamicSimuAlgorithm;
import com.interpss.dstab.algo.DynamicSimuMethod;
import com.interpss.dstab.common.DStabOutSymbol;
import com.interpss.dstab.common.IDStabSimuOutputHandler;
import com.interpss.dstab.datatype.DStabSimuEvent;
import com.interpss.simu.SimuContext;

/**
 * Opt-in staged Case 1 diagnostic. It monitors every active bus without retaining
 * all samples in memory and ranks the first post-fault voltage, relative-angle,
 * or frequency deviation as controller families are added.
 */
public class Texas2kCase1StagedFaultDiagnosticTest {
    private static final Path ROOT = Path.of(System.getProperty(
            "texas2k.case1.staged.root",
            Path.of("..", "target", "private-texas2k-case1-debug").toString())).normalize();
    private static final Path INPUT = ROOT.resolve("input");
    private static final Path OUTPUT = Path.of(System.getProperty(
            "texas2k.case1.staged.output", ROOT.toString())).normalize();
    private static final Path RAW = INPUT.resolve(
            "Texas2k_series24_case1_2016summerPeak_v36.RAW");
    private static final Path DYR = INPUT.resolve("dynamic_models_case1.dyr");
    private static final Path GNET = INPUT.resolve("dynamic_models_case1_gnet.idv");
    private static final Path REMOVE = INPUT.resolve("dynamic_models_case1_MODREMOVE.idv");
    private static final double FAULT_START = 1.0;
    private static final double FAULT_CLEAR = FAULT_START + 1.0 / 12.0;
    private static final double VOLTAGE_THRESHOLD = 0.05;
    private static final double VOLTAGE_THRESHOLD_1_PERCENT = 0.01;
    private static final double VOLTAGE_THRESHOLD_2_PERCENT = 0.02;
    private static final double VOLTAGE_THRESHOLD_10_PERCENT = 0.10;
    private static final double SUSTAINED_VOLTAGE_DURATION = 0.05;
    private static final double ANGLE_THRESHOLD_DEG = 5.0;
    private static final double FREQUENCY_THRESHOLD_PU = 0.002;
    private static final double SIMULATION_STEP = Double.parseDouble(System.getProperty(
            "texas2k.case1.staged.step", Double.toString(1.0 / 240.0)));
    private static final double SIMULATION_END = Double.parseDouble(System.getProperty(
            "texas2k.case1.staged.end", "10.0"));

    private static final Set<String> MACHINES = Set.of("GENROU", "GENSAL");
    private static final Set<String> EXCITERS = Set.of("ESST4B", "EXST1", "ESST1A", "IEEET1");
    private static final Set<String> GOVERNORS = Set.of("GGOV1", "IEEEG1", "HYGOV");
    private static final Set<String> STABILIZERS = Set.of("PSS2A");
    private static final Set<String> CAUSAL_EIGHT = Set.of(
            "1070", "3055", "3061", "3062", "3063", "3068", "3081", "3131");

    @BeforeAll
    static void initializePlugin() {
        IpssCorePlugin.init();
    }

    @Test
    void rankFirstPostFaultDeviationByControllerLayer() throws Exception {
        assumeTrue(Boolean.getBoolean("texas2k.case1.staged.enabled"),
                "Enable with -Dtexas2k.case1.staged.enabled=true");
        for (Path input : List.of(RAW, DYR, GNET, REMOVE)) {
            assumeTrue(Files.isRegularFile(input), "Missing staged private input: " + input);
        }

        List<PsseDyrRecord> records = PsseDyrRecordReader.read(DYR);
        Map<String, String> stacks = modelStacks(records);
        String stageFilter = System.getProperty("texas2k.case1.staged.stage", "");
        List<Stage> selected = stages().stream()
                .filter(stage -> stageFilter.isBlank()
                        || stage.name().equalsIgnoreCase(stageFilter))
                .toList();
        assertTrue(!selected.isEmpty(), "No diagnostic stage matches: " + stageFilter);
        List<StageResult> results = new ArrayList<>();
        for (Stage stage : selected) results.add(runStage(stage, records, stacks));
        writeCombinedSummary(results);
        assertTrue(results.stream().allMatch(StageResult::initialized),
                () -> "One or more staged cases failed initialization: " + results);
    }

    private static StageResult runStage(Stage stage, List<PsseDyrRecord> records,
            Map<String, String> stacks) throws Exception {
        Path stageDyr = OUTPUT.resolve(stage.fileStem() + ".dyr");
        String regcaVariant = System.getProperty(
                "texas2k.case1.staged.regcaVariant", "NORMAL");
        String removedBusesProperty = System.getProperty(
                "texas2k.case1.staged.removedConverterBuses", "");
        Set<String> allConverterBuses = records.stream()
                .filter(record -> record.canonicalModelName().equals("REGCA1"))
                .map(record -> Integer.toString(record.busNumber()))
                .collect(java.util.stream.Collectors.toUnmodifiableSet());
        Set<String> removedConverterBuses = removedBusesProperty.equalsIgnoreCase("CAUSAL_EIGHT")
                ? CAUSAL_EIGHT
                : removedBusesProperty.equalsIgnoreCase("ALL_CONVERTERS")
                        ? allConverterBuses : parsedSet(removedBusesProperty);
        String removedDynamicProperty = System.getProperty(
                "texas2k.case1.staged.removedDynamicBuses", "");
        Set<String> explicitlyRemovedDynamicBuses = removedDynamicProperty
                .equalsIgnoreCase("ALL_SYNCHRONOUS")
                        ? records.stream().filter(record -> Set.of("GENROU", "GENSAL")
                                        .contains(record.canonicalModelName()))
                                .map(record -> Integer.toString(record.busNumber()))
                                .collect(java.util.stream.Collectors.toUnmodifiableSet())
                        : removedDynamicProperty.equalsIgnoreCase("ALL_DYNAMIC")
                                ? records.stream().filter(record -> Set.of(
                                                "REGCA1", "GENROU", "GENSAL")
                                                .contains(record.canonicalModelName()))
                                        .map(record -> Integer.toString(record.busNumber()))
                                        .collect(java.util.stream.Collectors.toUnmodifiableSet())
                                : parsedSet(removedDynamicProperty);
        Set<String> removedSynchronousRegions = parsedSet(System.getProperty(
                "texas2k.case1.staged.removedSynchronousRegions", ""));
        Set<String> regionRemovedDynamicBuses = records.stream()
                .filter(record -> Set.of("GENROU", "GENSAL")
                        .contains(record.canonicalModelName()))
                .filter(record -> removedSynchronousRegions.contains(
                        Integer.toString(record.busNumber()).substring(0, 1)))
                .map(record -> Integer.toString(record.busNumber()))
                .collect(java.util.stream.Collectors.toUnmodifiableSet());
        Set<String> retainedDynamicBuses = parsedSet(System.getProperty(
                "texas2k.case1.staged.retainedDynamicBuses", ""));
        Set<String> disabledPssBuses = parsedSet(System.getProperty(
                "texas2k.case1.staged.disabledPssBuses", ""));
        Set<String> disabledPssRegions = parsedSet(System.getProperty(
                "texas2k.case1.staged.disabledPssRegions", ""));
        Set<String> removedDynamicBuses = union(removedConverterBuses,
                explicitlyRemovedDynamicBuses, regionRemovedDynamicBuses).stream()
                .filter(bus -> !retainedDynamicBuses.contains(bus))
                .collect(java.util.stream.Collectors.toUnmodifiableSet());
        StringBuilder content = new StringBuilder();
        records.stream().filter(record -> stage.models().contains(record.canonicalModelName()))
                .filter(record -> !(record.canonicalModelName().equals("PSS2A")
                        && (disabledPssBuses.contains(Integer.toString(record.busNumber()))
                                || disabledPssRegions.contains(Integer.toString(
                                        record.busNumber()).substring(0, 1)))))
                .filter(record -> !removedDynamicBuses.contains(
                        Integer.toString(record.busNumber())))
                .forEach(record -> content.append(diagnosticRecord(record, regcaVariant,
                                System.getProperty("texas2k.case1.staged.reecaVariant", "NORMAL")))
                        .append(" /\n"));
        if (stage.freezeConverters()) {
            records.stream().filter(record -> record.canonicalModelName().equals("REGCA1"))
                    .filter(record -> !removedDynamicBuses.contains(
                            Integer.toString(record.busNumber())))
                    .forEach(record -> content.append(record.busNumber())
                            .append(" 'GENCLS' '").append(record.deviceId().replace("'", "''"))
                            .append("' 999999 0 /\n"));
        }
        records.stream().filter(record -> Set.of("REGCA1", "GENROU", "GENSAL")
                        .contains(record.canonicalModelName()))
                .filter(record -> removedDynamicBuses.contains(
                        Integer.toString(record.busNumber())))
                .forEach(record -> content.append(record.busNumber())
                        .append(" 'GENCLS' '").append(record.deviceId().replace("'", "''"))
                        .append("' 999999 0 /\n"));
        Files.createDirectories(OUTPUT);
        Files.writeString(stageDyr, content, StandardCharsets.UTF_8);

        SimuContext context = new PSSEMultiFileLoader().loadDStab(
                RAW.toString(), stageDyr.toString(), GNET.toString(), REMOVE.toString());
        BaseDStabNetwork<?, ?> network = context.getDStabilityNet();
        ControllerMode controllerMode = ControllerMode.valueOf(System.getProperty(
                "texas2k.case1.staged.controllerMode", stage.controllerMode().name())
                .trim().toUpperCase(Locale.ROOT));
        applyControllerMode(network, controllerMode);
        network.setBypassDataCheck(true);
        network.setAllowGenWithoutMach(true);
        DynamicSimuAlgorithm algorithm = context.getDynSimuAlgorithm();
        LoadflowAlgorithm loadflow = algorithm.getAclfAlgorithm();
        loadflow.getDataCheckConfig().setAutoTurnLine2Xfr(true);
        loadflow.getDataCheckConfig().setTurnOffIslandBus(true);
        loadflow.setNonDivergent(true);
        loadflow.setMaxIterations(50);
        loadflow.setTolerance(1.0e-8);
        assertTrue(loadflow.loadflow(), stage.name() + " load flow");

        String referenceBus = network.getBusList().stream().filter(bus -> bus.isActive())
                .filter(bus -> bus.isSwing()).map(bus -> bus.getId()).findFirst()
                .orElseThrow(() -> new IllegalStateException("No active swing bus"));
        List<String> buses = network.getBusList().stream().filter(bus -> bus.isActive())
                .map(bus -> bus.getId()).sorted().toList();
        AllBusDeviationMonitor monitor = new AllBusDeviationMonitor(
                network, buses, referenceBus, FAULT_CLEAR, SIMULATION_STEP, stacks);
        algorithm.setSimuOutputHandler(monitor);
        algorithm.setSimuMethod(DynamicSimuMethod.MODIFIED_EULER);
        algorithm.setSimuStepSec(SIMULATION_STEP);
        algorithm.setTotalSimuTimeSec(SIMULATION_END);
        algorithm.setOutPutPerSteps(1);
        network.addDynamicEvent(DStabObjectFactory.createBusFaultEvent(
                "Bus7249", network, SimpleFaultCode.GROUND_3P, Complex.ZERO, null,
                FAULT_START, 1.0 / 12.0), "Case1StagedFault@Bus7249");

        boolean initialized = algorithm.initialization();
        boolean completed = initialized && algorithm.performSimulation();
        monitor.finish();
        List<BusMetric> ranking = monitor.ranking();
        writeStageSummary(stage, initialized, completed, monitor.lastTime(), ranking);
        monitor.writeVoltageOnsetSummary(OUTPUT.resolve(stage.fileStem()
                + "-voltage-onset.csv"));
        monitor.writeVoltageRecoverySummary(OUTPUT.resolve(stage.fileStem()
                + "-voltage-recovery.csv"));
        monitor.writeRenewableSummary(OUTPUT.resolve(stage.fileStem()
                + "-renewable-state-ranking.csv"));
        monitor.writeRenewableTrace(OUTPUT.resolve(stage.fileStem()
                + "-renewable-state-trace.csv"));
        System.out.printf(Locale.ROOT,
                "Case1 staged fault %-31s initialized=%s completed=%s end=%.6f first=%s%n",
                stage.name(), initialized, completed, monitor.lastTime(),
                ranking.isEmpty() ? "none" : ranking.get(0));
        return new StageResult(stage.name(), initialized, completed, monitor.lastTime(), ranking);
    }

    private static String diagnosticRecord(PsseDyrRecord record, String regcaVariant,
            String reecaVariant) {
        if (record.canonicalModelName().equals("REECA1")
                && !reecaVariant.equalsIgnoreCase("NORMAL")) {
            List<String> parameters = new ArrayList<>(record.parameters());
            switch (reecaVariant.toUpperCase(Locale.ROOT)) {
                case "KQV_ZERO" -> parameters.set(11, "0");
                case "KQI_ZERO" -> parameters.set(24, "0");
                case "KVI_ZERO" -> parameters.set(26, "0");
                case "Q_AND_V_INTEGRATORS_ZERO" -> {
                    parameters.set(24, "0");
                    parameters.set(26, "0");
                }
                default -> throw new IllegalArgumentException(
                        "Unknown REECA1 diagnostic variant: " + reecaVariant);
            }
            return record.busNumber() + " '" + record.sourceModelName() + "' '"
                    + record.deviceId().replace("'", "''") + "' "
                    + String.join(" ", parameters);
        }
        return regcaRecord(record, regcaVariant);
    }

    private static String regcaRecord(PsseDyrRecord record, String variantName) {
        if (!record.canonicalModelName().equals("REGCA1")
                || variantName.equalsIgnoreCase("NORMAL")) {
            return record.rawText();
        }
        List<String> parameters = new ArrayList<>(record.parameters());
        switch (variantName.toUpperCase(Locale.ROOT)) {
            case "LVPL_DISABLED" -> parameters.set(0, "0");
            case "LVG_DISABLED" -> {
                parameters.set(7, "0.01");
                parameters.set(8, "0");
            }
            case "LVPL_AND_LVG_DISABLED" -> {
                parameters.set(0, "0");
                parameters.set(7, "0.01");
                parameters.set(8, "0");
            }
            case "CURRENT_LAG_BYPASSED" -> parameters.set(1, "0");
            case "IQR_UNLIMITED" -> {
                parameters.set(12, "1000000");
                parameters.set(13, "-1000000");
            }
            default -> throw new IllegalArgumentException(
                    "Unknown REGCA1 diagnostic variant: " + variantName);
        }
        return record.busNumber() + " '" + record.sourceModelName() + "' '"
                + record.deviceId().replace("'", "''") + "' "
                + String.join(" ", parameters);
    }

    private static void writeStageSummary(Stage stage, boolean initialized, boolean completed,
            double endTime, List<BusMetric> ranking) throws Exception {
        StringBuilder csv = new StringBuilder("stage,status,end_time_s,rank,bus,first_any_s,"
                + "first_voltage_s,first_relative_angle_s,first_frequency_s,"
                + "max_voltage_deviation_pu,max_relative_angle_deviation_deg,"
                + "max_frequency_deviation_pu,model_stack\n");
        int rank = 0;
        for (BusMetric metric : ranking) {
            csv.append(stage.name()).append(',')
                    .append(completed ? "complete" : initialized ? "solver_failed" : "init_failed")
                    .append(',').append(format(endTime)).append(',').append(++rank).append(',')
                    .append(metric.bus()).append(',').append(format(metric.firstAny())).append(',')
                    .append(format(metric.firstVoltage())).append(',')
                    .append(format(metric.firstAngle())).append(',')
                    .append(format(metric.firstFrequency())).append(',')
                    .append(format(metric.maxVoltage())).append(',')
                    .append(format(metric.maxAngle())).append(',')
                    .append(format(metric.maxFrequency())).append(',')
                    .append('"').append(metric.modelStack().replace("\"", "\"\"")).append('"')
                    .append('\n');
        }
        Files.writeString(OUTPUT.resolve(stage.fileStem() + "-ranking.csv"), csv,
                StandardCharsets.UTF_8);
    }

    private static void writeCombinedSummary(List<StageResult> results) throws Exception {
        StringBuilder csv = new StringBuilder(
                "stage,status,end_time_s,first_bus,first_deviation_s,max_voltage_bus,"
                + "max_voltage_deviation_pu,max_angle_bus,max_relative_angle_deviation_deg,"
                + "max_frequency_bus,max_frequency_deviation_pu\n");
        for (StageResult result : results) {
            List<BusMetric> ranking = result.ranking();
            BusMetric first = ranking.isEmpty() ? BusMetric.empty() : ranking.get(0);
            BusMetric maxV = ranking.stream().max(Comparator.comparingDouble(BusMetric::maxVoltage))
                    .orElse(BusMetric.empty());
            BusMetric maxA = ranking.stream().max(Comparator.comparingDouble(BusMetric::maxAngle))
                    .orElse(BusMetric.empty());
            BusMetric maxF = ranking.stream().max(Comparator.comparingDouble(BusMetric::maxFrequency))
                    .orElse(BusMetric.empty());
            csv.append(result.stage()).append(',')
                    .append(result.completed() ? "complete"
                            : result.initialized() ? "solver_failed" : "init_failed")
                    .append(',').append(format(result.endTime())).append(',')
                    .append(first.bus()).append(',').append(format(first.firstAny())).append(',')
                    .append(maxV.bus()).append(',').append(format(maxV.maxVoltage())).append(',')
                    .append(maxA.bus()).append(',').append(format(maxA.maxAngle())).append(',')
                    .append(maxF.bus()).append(',').append(format(maxF.maxFrequency())).append('\n');
        }
        Files.writeString(OUTPUT.resolve("stage-summary.csv"), csv, StandardCharsets.UTF_8);
    }

    private static Map<String, String> modelStacks(List<PsseDyrRecord> records) {
        Map<String, LinkedHashSet<String>> grouped = new LinkedHashMap<>();
        for (PsseDyrRecord record : records) {
            String bus = "Bus" + record.busNumber();
            grouped.computeIfAbsent(bus, unused -> new LinkedHashSet<>())
                    .add(record.canonicalModelName() + "[" + record.deviceId() + "]");
        }
        Map<String, String> result = new HashMap<>();
        grouped.forEach((bus, models) -> result.put(bus, String.join("|", models)));
        return Map.copyOf(result);
    }

    private static List<Stage> stages() {
        Set<String> machineExciter = union(MACHINES, EXCITERS);
        Set<String> machineGovernor = union(MACHINES, GOVERNORS);
        Set<String> controlled = union(machineExciter, GOVERNORS);
        Set<String> renewablePlant = union(controlled, STABILIZERS,
                Set.of("REGCA1", "REECA1", "REPCA1"));
        return List.of(
                new Stage("MACHINES", "stage-01-machines", MACHINES, true,
                        ControllerMode.NORMAL),
                new Stage("MACHINES_PLUS_ESST4B", "stage-01a-machines-esst4b",
                        union(MACHINES, Set.of("ESST4B")), true, ControllerMode.NORMAL),
                new Stage("MACHINES_PLUS_EXST1", "stage-01b-machines-exst1",
                        union(MACHINES, Set.of("EXST1")), true, ControllerMode.NORMAL),
                new Stage("MACHINES_PLUS_ESST1A", "stage-01c-machines-esst1a",
                        union(MACHINES, Set.of("ESST1A")), true, ControllerMode.NORMAL),
                new Stage("MACHINES_PLUS_IEEET1", "stage-01d-machines-ieeet1",
                        union(MACHINES, Set.of("IEEET1")), true, ControllerMode.NORMAL),
                new Stage("MACHINES_PLUS_EXCITERS", "stage-02-machines-exciters",
                        machineExciter, true, ControllerMode.NORMAL),
                new Stage("MACHINES_PLUS_GOVERNORS", "stage-03-machines-governors",
                        machineGovernor, true, ControllerMode.NORMAL),
                new Stage("MACHINES_PLUS_EXCITERS_GOVERNORS",
                        "stage-04-machines-exciters-governors", controlled, true,
                        ControllerMode.NORMAL),
                new Stage("FULL_CONVENTIONAL", "stage-05-full-conventional",
                        union(controlled, STABILIZERS), true, ControllerMode.NORMAL),
                new Stage("REGCA_REECA_FIXED_COMMANDS",
                        "stage-06a-regca-reeca-fixed-commands",
                        union(controlled, STABILIZERS, Set.of("REGCA1", "REECA1")), false,
                        ControllerMode.FREEZE_BOTH),
                new Stage("REGCA_REECA_ACTIVE_ONLY",
                        "stage-06b-regca-reeca-active-only",
                        union(controlled, STABILIZERS, Set.of("REGCA1", "REECA1")), false,
                        ControllerMode.FREEZE_REACTIVE),
                new Stage("REGCA_REECA_REACTIVE_ONLY",
                        "stage-06c-regca-reeca-reactive-only",
                        union(controlled, STABILIZERS, Set.of("REGCA1", "REECA1")), false,
                        ControllerMode.FREEZE_ACTIVE),
                new Stage("CONVENTIONAL_PLUS_REGCA_REECA",
                        "stage-06-conventional-regca-reeca",
                        union(controlled, STABILIZERS, Set.of("REGCA1", "REECA1")), false,
                        ControllerMode.NORMAL),
                new Stage("CONVENTIONAL_PLUS_REGCA_REECA_REPCA",
                        "stage-07-conventional-regca-reeca-repca",
                        renewablePlant, false,
                        ControllerMode.NORMAL),
                new Stage("RENEWABLE_PLANT_PLUS_WTARA",
                        "stage-07a-renewable-plant-wtara",
                        union(renewablePlant, Set.of("WTARA1")), false,
                        ControllerMode.NORMAL),
                new Stage("RENEWABLE_PLANT_PLUS_WTPTA",
                        "stage-07b-renewable-plant-wtpta",
                        union(renewablePlant, Set.of("WTPTA1")), false,
                        ControllerMode.NORMAL),
                new Stage("RENEWABLE_PLANT_PLUS_WTTQA",
                        "stage-07c-renewable-plant-wttqa",
                        union(renewablePlant, Set.of("WTTQA1")), false,
                        ControllerMode.NORMAL),
                new Stage("FULL", "stage-08-full",
                        PsseDyrRecordReaderUnchecked.modelNames(DYR), false,
                        ControllerMode.NORMAL));
    }

    private static void applyControllerMode(BaseDStabNetwork<?, ?> network,
            ControllerMode mode) {
        if (mode == ControllerMode.NORMAL) return;
        Set<String> reactiveEnabledPrefixes = Set.of(System.getProperty(
                        "texas2k.case1.staged.reactiveEnabledPrefixes", "").split(","));
        Set<String> reactiveEnabledBuses = Set.of(System.getProperty(
                        "texas2k.case1.staged.reactiveEnabledBuses", "").split(","));
        network.getBusList().stream().flatMap(bus -> bus.getContributeGenList().stream())
                .map(gen -> (DStabGen) gen)
                .filter(gen -> gen.getDynamicGenDevice() instanceof Regca1Model)
                .map(gen -> (Regca1Model) gen.getDynamicGenDevice())
                .forEach(converter -> converter.setActiveElectricalController(
                        new FrozenAxisController(converter.getActiveElectricalController(),
                                mode == ControllerMode.FREEZE_ACTIVE
                                        || mode == ControllerMode.FREEZE_BOTH,
                                mode == ControllerMode.FREEZE_REACTIVE
                                        && reactiveEnabledPrefixes.stream()
                                                .filter(prefix -> !prefix.isBlank())
                                                .noneMatch(prefix -> converter.getDStabBus()
                                                        .getId().substring(3)
                                                        .startsWith(prefix))
                                        && reactiveEnabledBuses.stream()
                                                .filter(bus -> !bus.isBlank())
                                                .noneMatch(bus -> converter.getDStabBus().getId()
                                                        .equals("Bus" + bus))
                                        || mode == ControllerMode.FREEZE_BOTH)));
    }

    private static Set<String> parsedSet(String csv) {
        if (csv.isBlank()) return Set.of();
        return java.util.Arrays.stream(csv.split(","))
                .map(String::trim).filter(value -> !value.isBlank())
                .collect(java.util.stream.Collectors.toUnmodifiableSet());
    }

    @SafeVarargs
    private static Set<String> union(Set<String>... sets) {
        LinkedHashSet<String> values = new LinkedHashSet<>();
        for (Set<String> set : sets) values.addAll(set);
        return Set.copyOf(values);
    }

    private static String format(double value) {
        return Double.isFinite(value) ? String.format(Locale.ROOT, "%.12g", value) : "";
    }

    private static double wrapDegrees(double value) {
        double wrapped = value % 360.0;
        if (wrapped > 180.0) wrapped -= 360.0;
        if (wrapped < -180.0) wrapped += 360.0;
        return wrapped;
    }

    private enum ControllerMode { NORMAL, FREEZE_ACTIVE, FREEZE_REACTIVE, FREEZE_BOTH }

    private record Stage(String name, String fileStem, Set<String> models,
            boolean freezeConverters, ControllerMode controllerMode) { }

    private record StageResult(String stage, boolean initialized, boolean completed,
            double endTime, List<BusMetric> ranking) { }

    private record Snapshot(double voltage, double angle, double frequency) { }

    private static final class MutableMetric {
        private final String bus;
        private final String modelStack;
        private final double initialVoltage;
        private double firstVoltage = Double.POSITIVE_INFINITY;
        private double firstVoltage1Percent = Double.POSITIVE_INFINITY;
        private double firstVoltage2Percent = Double.POSITIVE_INFINITY;
        private double firstVoltage10Percent = Double.POSITIVE_INFINITY;
        private double firstSustainedVoltage = Double.POSITIVE_INFINITY;
        private double voltage5PercentRunStart = Double.POSITIVE_INFINITY;
        private double voltageAtFirst5Percent = Double.NaN;
        private double voltageAtFirst10Percent = Double.NaN;
        private double minVoltage = Double.POSITIVE_INFINITY;
        private double timeMinVoltage = Double.NaN;
        private double maxVoltageValue = Double.NEGATIVE_INFINITY;
        private double timeMaxVoltage = Double.NaN;
        private double voltageAtRecoveryWindowStart = Double.NaN;
        private double finalVoltage = Double.NaN;
        private double firstAngle = Double.POSITIVE_INFINITY;
        private double firstFrequency = Double.POSITIVE_INFINITY;
        private double maxVoltage;
        private double maxAngle;
        private double maxFrequency;

        private MutableMetric(String bus, String modelStack, double initialVoltage) {
            this.bus = bus;
            this.modelStack = modelStack;
            this.initialVoltage = initialVoltage;
        }

        private BusMetric freeze() {
            return new BusMetric(bus, Math.min(firstVoltage, Math.min(firstAngle, firstFrequency)),
                    firstVoltage, firstAngle, firstFrequency,
                    maxVoltage, maxAngle, maxFrequency, modelStack);
        }
    }

    private record BusMetric(String bus, double firstAny, double firstVoltage,
            double firstAngle, double firstFrequency, double maxVoltage, double maxAngle,
            double maxFrequency, String modelStack) {
        private static BusMetric empty() {
            return new BusMetric("", Double.POSITIVE_INFINITY, Double.POSITIVE_INFINITY,
                    Double.POSITIVE_INFINITY, Double.POSITIVE_INFINITY, 0, 0, 0, "");
        }

        private double severity() {
            return Math.max(maxVoltage / VOLTAGE_THRESHOLD,
                    Math.max(maxAngle / ANGLE_THRESHOLD_DEG,
                            maxFrequency / FREQUENCY_THRESHOLD_PU));
        }
    }

    private static final class AllBusDeviationMonitor implements IDStabSimuOutputHandler {
        private final List<RenewableMetric> renewableMetrics;
        private final List<String> buses;
        private final String referenceBus;
        private final double analyzeAfter;
        private final Map<String, Snapshot> initial = new HashMap<>();
        private final Map<String, Snapshot> sample = new HashMap<>();
        private final Map<String, MutableMetric> metrics = new HashMap<>();
        private final Set<String> traceBuses;
        private final StringBuilder renewableTrace = new StringBuilder(
                "time_s,bus,device,bus_voltage_pu,measured_voltage_pu,"
                + "regca_filtered_voltage_pu,regca_ip_state,regca_iq_state,"
                + "regca_active_power_pu,regca_reactive_power_pu,"
                + "reeca_p_filter,reeca_p_order,reeca_q_current,reeca_q_integral,"
                + "reeca_v_integral,reeca_ipcmd,reeca_iqcmd,"
                + "repca_p_integral,repca_q_integral,repca_pext,repca_qext\n");
        private double sampleTime = Double.NaN;
        private double renewableSampleTime = Double.NaN;
        private double lastTime;

        private AllBusDeviationMonitor(BaseDStabNetwork<?, ?> network, List<String> buses,
                String referenceBus, double faultClear, double simulationStep,
                Map<String, String> stacks) {
            this.buses = List.copyOf(buses);
            this.referenceBus = referenceBus;
            this.analyzeAfter = faultClear + 0.5 * simulationStep;
            this.traceBuses = parsedSet(System.getProperty(
                            "texas2k.case1.staged.traceBuses", "")).stream()
                    .map(bus -> bus.startsWith("Bus") ? bus : "Bus" + bus)
                    .collect(java.util.stream.Collectors.toUnmodifiableSet());
            this.renewableMetrics = network.getBusList().stream()
                    .flatMap(bus -> bus.getContributeGenList().stream())
                    .map(gen -> (DStabGen) gen)
                    .filter(gen -> gen.getDynamicGenDevice() instanceof Regca1Model)
                    .map(gen -> new RenewableMetric(gen.getParentBus().getId(), gen.getId(),
                            (Regca1Model) gen.getDynamicGenDevice()))
                    .filter(metric -> metric.controller != null)
                    .toList();
            for (String busId : buses) {
                var bus = network.getBus(busId);
                initial.put(busId, new Snapshot(bus.getVoltageMag(),
                        Math.toDegrees(bus.getVoltage().getArgument()), bus.getFreq()));
                metrics.put(busId, new MutableMetric(busId, stacks.getOrDefault(busId, ""),
                        bus.getVoltageMag()));
            }
        }

        @Override
        public boolean onSimuEvent(DStabSimuEvent event) {
            if (event.getType() != DStabSimuEvent.PlotStepBusStates) return true;
            Hashtable<String, Object> values = event.getHashtableData();
            double time = ((Number) values.get(DStabOutSymbol.OUT_SYMBOL_TIME)).doubleValue();
            if (Double.isFinite(sampleTime) && Math.abs(time - sampleTime) > 1.0e-10) {
                finalizeSample();
                sample.clear();
            }
            sampleTime = time;
            lastTime = time;
            if (!Double.isFinite(renewableSampleTime)
                    || Math.abs(time - renewableSampleTime) > 1.0e-10) {
                renewableMetrics.forEach(metric -> metric.sample(time));
                renewableSampleTime = time;
            }
            String bus = (String) values.get(DStabOutSymbol.OUT_SYMBOL_BUS_ID);
            Snapshot busSnapshot = new Snapshot(
                    ((Number) values.get(DStabOutSymbol.OUT_SYMBOL_BUS_VMAG)).doubleValue(),
                    ((Number) values.get(DStabOutSymbol.OUT_SYMBOL_BUS_VANG)).doubleValue(),
                    ((Number) values.get(DStabOutSymbol.OUT_SYMBOL_BUS_FREQ)).doubleValue());
            sample.put(bus, busSnapshot);
            if (traceBuses.contains(bus)) appendRenewableTrace(time, bus, busSnapshot);
            return true;
        }

        private void appendRenewableTrace(double time, String bus, Snapshot busSnapshot) {
            renewableMetrics.stream().filter(metric -> metric.bus.equals(bus)).forEach(metric -> {
                Reeca1Model controller = metric.controller;
                Repca1Model plant = controller.getPlantController();
                Map<String, Double> regca = metric.converter.getNamedStates();
                Map<String, Double> reeca = controller.getNamedStates();
                Map<String, Double> repca = plant == null ? Map.of() : plant.getNamedStates();
                renewableTrace.append(format(time)).append(',').append(bus).append(',')
                        .append(metric.device).append(',').append(format(busSnapshot.voltage()))
                        .append(',').append(format(state(reeca, "Measured Voltage")))
                        .append(',').append(format(state(regca, "Filtered Voltage")))
                        .append(',').append(format(state(regca, "Active Current Regulator")))
                        .append(',').append(format(state(regca, "Reactive Current Regulator")))
                        .append(',').append(format(state(regca, "Active Power")))
                        .append(',').append(format(state(regca, "Reactive Power")))
                        .append(',').append(format(state(reeca, "Active Power Filter")))
                        .append(',').append(format(state(reeca, "Active Power Order")))
                        .append(',').append(format(state(reeca, "Reactive Current")))
                        .append(',').append(format(state(reeca, "Reactive Control Integral")))
                        .append(',').append(format(state(reeca, "Voltage Control Integral")))
                        .append(',').append(format(state(reeca, "Active Current Command")))
                        .append(',').append(format(state(reeca, "Reactive Current Command")))
                        .append(',').append(format(state(repca, "Active Control Integral")))
                        .append(',').append(format(state(repca, "Reactive Control Integral")))
                        .append(',').append(format(state(repca, "Active Power Command")))
                        .append(',').append(format(state(repca, "Reactive Command"))).append('\n');
            });
        }

        private static double state(Map<String, Double> states, String name) {
            return states.getOrDefault(name, Double.NaN);
        }

        private void finish() {
            finalizeSample();
            sample.clear();
        }

        private void finalizeSample() {
            if (!Double.isFinite(sampleTime) || sampleTime < analyzeAfter) return;
            Snapshot reference = sample.get(referenceBus);
            Snapshot initialReference = initial.get(referenceBus);
            double referenceAngleChange = reference == null || initialReference == null ? 0.0
                    : wrapDegrees(reference.angle() - initialReference.angle());
            for (Map.Entry<String, Snapshot> entry : sample.entrySet()) {
                Snapshot origin = initial.get(entry.getKey());
                MutableMetric metric = metrics.get(entry.getKey());
                if (origin == null || metric == null) continue;
                Snapshot value = entry.getValue();
                double voltage = Math.abs(value.voltage() - origin.voltage());
                if (value.voltage() < metric.minVoltage) {
                    metric.minVoltage = value.voltage();
                    metric.timeMinVoltage = sampleTime;
                }
                if (value.voltage() > metric.maxVoltageValue) {
                    metric.maxVoltageValue = value.voltage();
                    metric.timeMaxVoltage = sampleTime;
                }
                if (!Double.isFinite(metric.voltageAtRecoveryWindowStart)
                        && sampleTime >= SIMULATION_END - 0.1 - 1.0e-10) {
                    metric.voltageAtRecoveryWindowStart = value.voltage();
                }
                metric.finalVoltage = value.voltage();
                double angle = Math.abs(wrapDegrees(
                        value.angle() - origin.angle() - referenceAngleChange));
                double frequency = Math.abs(value.frequency() - origin.frequency());
                metric.maxVoltage = Math.max(metric.maxVoltage, voltage);
                metric.maxAngle = Math.max(metric.maxAngle, angle);
                metric.maxFrequency = Math.max(metric.maxFrequency, frequency);
                if (voltage >= VOLTAGE_THRESHOLD && !Double.isFinite(metric.firstVoltage)) {
                    metric.firstVoltage = sampleTime;
                    metric.voltageAtFirst5Percent = value.voltage();
                }
                if (voltage >= VOLTAGE_THRESHOLD_1_PERCENT
                        && !Double.isFinite(metric.firstVoltage1Percent)) {
                    metric.firstVoltage1Percent = sampleTime;
                }
                if (voltage >= VOLTAGE_THRESHOLD_2_PERCENT
                        && !Double.isFinite(metric.firstVoltage2Percent)) {
                    metric.firstVoltage2Percent = sampleTime;
                }
                if (voltage >= VOLTAGE_THRESHOLD_10_PERCENT
                        && !Double.isFinite(metric.firstVoltage10Percent)) {
                    metric.firstVoltage10Percent = sampleTime;
                    metric.voltageAtFirst10Percent = value.voltage();
                }
                if (voltage >= VOLTAGE_THRESHOLD) {
                    if (!Double.isFinite(metric.voltage5PercentRunStart)) {
                        metric.voltage5PercentRunStart = sampleTime;
                    }
                    if (!Double.isFinite(metric.firstSustainedVoltage)
                            && sampleTime - metric.voltage5PercentRunStart
                                    >= SUSTAINED_VOLTAGE_DURATION - 1.0e-10) {
                        metric.firstSustainedVoltage = metric.voltage5PercentRunStart;
                    }
                } else {
                    metric.voltage5PercentRunStart = Double.POSITIVE_INFINITY;
                }
                if (angle >= ANGLE_THRESHOLD_DEG && !Double.isFinite(metric.firstAngle)) {
                    metric.firstAngle = sampleTime;
                }
                if (frequency >= FREQUENCY_THRESHOLD_PU
                        && !Double.isFinite(metric.firstFrequency)) {
                    metric.firstFrequency = sampleTime;
                }
            }
        }

        private List<BusMetric> ranking() {
            return metrics.values().stream().map(MutableMetric::freeze)
                    .sorted(Comparator.comparingDouble(BusMetric::firstAny)
                            .thenComparing(Comparator.comparingDouble(BusMetric::severity).reversed())
                            .thenComparing(BusMetric::bus))
                    .toList();
        }

        private double lastTime() {
            return lastTime;
        }

        private void writeRenewableSummary(Path output) throws Exception {
            StringBuilder csv = new StringBuilder("rank,bus,device,pf_flag,v_flag,q_flag,"
                    + "p_flag,pq_flag,kqv,kqi,kvi,qmax,qmin,vmax,vmin,imax,tg,"
                    + "max_abs_iqcmd_delta,time_iqcmd_s,max_abs_regca_iq_delta,"
                    + "time_regca_iq_s,max_abs_q_integral_delta,max_abs_v_integral_delta,"
                    + "max_abs_reactive_injection,min_measured_voltage,max_measured_voltage,"
                    + "dip_samples,iq_limit_samples\n");
            List<RenewableMetric> ranked = renewableMetrics.stream()
                    .sorted(Comparator.comparingDouble(RenewableMetric::severity).reversed())
                    .toList();
            int rank = 0;
            for (RenewableMetric metric : ranked) {
                Reeca1Data data = metric.controller.getData();
                csv.append(++rank).append(',').append(metric.bus).append(',')
                        .append(metric.device).append(',').append(data.pfFlag()).append(',')
                        .append(data.vFlag()).append(',').append(data.qFlag()).append(',')
                        .append(data.pFlag()).append(',').append(data.pqFlag()).append(',')
                        .append(format(data.kqv())).append(',').append(format(data.kqi()))
                        .append(',').append(format(data.kvi())).append(',')
                        .append(format(data.qmax())).append(',').append(format(data.qmin()))
                        .append(',').append(format(data.vmax())).append(',')
                        .append(format(data.vmin())).append(',').append(format(data.imax()))
                        .append(',').append(format(metric.converter.getData().tg())).append(',')
                        .append(format(metric.maxIqcmdDelta)).append(',')
                        .append(format(metric.timeIqcmd)).append(',')
                        .append(format(metric.maxRegcaIqDelta)).append(',')
                        .append(format(metric.timeRegcaIq)).append(',')
                        .append(format(metric.maxQIntegralDelta)).append(',')
                        .append(format(metric.maxVIntegralDelta)).append(',')
                        .append(format(metric.maxReactiveInjection)).append(',')
                        .append(format(metric.minMeasuredVoltage)).append(',')
                        .append(format(metric.maxMeasuredVoltage)).append(',')
                        .append(metric.dipSamples).append(',').append(metric.iqLimitSamples)
                        .append('\n');
            }
            Files.writeString(output, csv, StandardCharsets.UTF_8);
        }

        private void writeRenewableTrace(Path output) throws Exception {
            if (!traceBuses.isEmpty()) {
                Files.writeString(output, renewableTrace, StandardCharsets.UTF_8);
            }
        }

        private void writeVoltageOnsetSummary(Path output) throws Exception {
            StringBuilder csv = new StringBuilder("rank,bus,initial_voltage_pu,first_1pct_s,first_2pct_s,"
                    + "first_5pct_s,first_10pct_s,first_sustained_5pct_s,"
                    + "voltage_at_first_5pct_pu,voltage_at_first_10pct_pu,"
                    + "minimum_voltage_pu,time_minimum_voltage_s,maximum_voltage_pu,"
                    + "time_maximum_voltage_s,max_voltage_deviation_pu,model_stack\n");
            List<MutableMetric> ranked = metrics.values().stream()
                    .filter(metric -> Double.isFinite(metric.firstVoltage1Percent))
                    .sorted(Comparator.comparingDouble(
                                    (MutableMetric metric) -> metric.firstVoltage1Percent)
                            .thenComparingDouble(metric -> metric.firstVoltage2Percent)
                            .thenComparingDouble(metric -> metric.firstVoltage)
                            .thenComparing(metric -> metric.bus))
                    .toList();
            int rank = 0;
            for (MutableMetric metric : ranked) {
                csv.append(++rank).append(',').append(metric.bus).append(',')
                        .append(format(metric.initialVoltage)).append(',')
                        .append(format(metric.firstVoltage1Percent)).append(',')
                        .append(format(metric.firstVoltage2Percent)).append(',')
                        .append(format(metric.firstVoltage)).append(',')
                        .append(format(metric.firstVoltage10Percent)).append(',')
                        .append(format(metric.firstSustainedVoltage)).append(',')
                        .append(format(metric.voltageAtFirst5Percent)).append(',')
                        .append(format(metric.voltageAtFirst10Percent)).append(',')
                        .append(format(metric.minVoltage)).append(',')
                        .append(format(metric.timeMinVoltage)).append(',')
                        .append(format(metric.maxVoltageValue)).append(',')
                        .append(format(metric.timeMaxVoltage)).append(',')
                        .append(format(metric.maxVoltage)).append(',')
                        .append('"').append(metric.modelStack.replace("\"", "\"\""))
                        .append('"').append('\n');
            }
            Files.writeString(output, csv, StandardCharsets.UTF_8);
        }

        private void writeVoltageRecoverySummary(Path output) throws Exception {
            StringBuilder csv = new StringBuilder("rank,bus,initial_voltage_pu,"
                    + "final_voltage_pu,absolute_final_deviation_pu,"
                    + "voltage_100ms_before_end_pu,recent_voltage_slope_pu_per_s,"
                    + "inside_0p9_to_1p1,settling_slope_below_0p01,model_stack\n");
            List<MutableMetric> ranked = metrics.values().stream()
                    .filter(metric -> Double.isFinite(metric.finalVoltage))
                    .sorted(Comparator.comparingDouble((MutableMetric metric) ->
                                    Math.abs(metric.finalVoltage - metric.initialVoltage))
                            .reversed().thenComparing(metric -> metric.bus))
                    .toList();
            int rank = 0;
            for (MutableMetric metric : ranked) {
                double deviation = Math.abs(metric.finalVoltage - metric.initialVoltage);
                double slope = Double.isFinite(metric.voltageAtRecoveryWindowStart)
                        ? (metric.finalVoltage - metric.voltageAtRecoveryWindowStart) / 0.1
                        : Double.NaN;
                csv.append(++rank).append(',').append(metric.bus).append(',')
                        .append(format(metric.initialVoltage)).append(',')
                        .append(format(metric.finalVoltage)).append(',')
                        .append(format(deviation)).append(',')
                        .append(format(metric.voltageAtRecoveryWindowStart)).append(',')
                        .append(format(slope)).append(',')
                        .append(metric.finalVoltage >= 0.9 && metric.finalVoltage <= 1.1)
                        .append(',').append(Double.isFinite(slope) && Math.abs(slope) <= 0.01)
                        .append(',').append('"')
                        .append(metric.modelStack.replace("\"", "\"\""))
                        .append('"').append('\n');
            }
            Files.writeString(output, csv, StandardCharsets.UTF_8);
        }

        @Override
        public boolean init(String scriptFilename, BaseDStabNetwork<?, ?> net)
                throws InterpssException {
            return true;
        }

        @Override
        public boolean close() {
            finish();
            return true;
        }

        @Override
        public boolean isOutputFilter() {
            return true;
        }

        @Override
        public void setOutputFilter(boolean filter) { }

        @Override
        public List<String> getOutputVarIdList() {
            return buses;
        }

        @Override
        public void setOutputVarIdList(String[] list) {
            throw new UnsupportedOperationException("Fixed all-bus diagnostic filter");
        }
    }

    private static final class RenewableMetric {
        private static final double LIMIT_EPS = 1.0e-7;
        private final String bus;
        private final String device;
        private final Regca1Model converter;
        private final Reeca1Model controller;
        private boolean initialized;
        private double initialIqcmd;
        private double initialRegcaIq;
        private double initialQIntegral;
        private double initialVIntegral;
        private double maxIqcmdDelta;
        private double timeIqcmd;
        private double maxRegcaIqDelta;
        private double timeRegcaIq;
        private double maxQIntegralDelta;
        private double maxVIntegralDelta;
        private double maxReactiveInjection;
        private double minMeasuredVoltage = Double.POSITIVE_INFINITY;
        private double maxMeasuredVoltage = Double.NEGATIVE_INFINITY;
        private long dipSamples;
        private long iqLimitSamples;

        private RenewableMetric(String bus, String device, Regca1Model converter) {
            this.bus = bus;
            this.device = device;
            this.converter = converter;
            this.controller = converter.getReeca1Controller();
        }

        private void sample(double time) {
            if (controller == null) return;
            if (!initialized) {
                initialIqcmd = controller.getIqcmd();
                initialRegcaIq = converter.getIqRegulatorState();
                initialQIntegral = controller.getReactiveControlIntegral();
                initialVIntegral = controller.getVoltageControlIntegral();
                initialized = true;
            }
            double iqcmdDelta = Math.abs(controller.getIqcmd() - initialIqcmd);
            if (iqcmdDelta > maxIqcmdDelta) {
                maxIqcmdDelta = iqcmdDelta;
                timeIqcmd = time;
            }
            double regcaIqDelta = Math.abs(converter.getIqRegulatorState() - initialRegcaIq);
            if (regcaIqDelta > maxRegcaIqDelta) {
                maxRegcaIqDelta = regcaIqDelta;
                timeRegcaIq = time;
            }
            maxQIntegralDelta = Math.max(maxQIntegralDelta,
                    Math.abs(controller.getReactiveControlIntegral() - initialQIntegral));
            maxVIntegralDelta = Math.max(maxVIntegralDelta,
                    Math.abs(controller.getVoltageControlIntegral() - initialVIntegral));
            maxReactiveInjection = Math.max(maxReactiveInjection,
                    Math.abs(controller.getReactiveCurrentInjection()));
            minMeasuredVoltage = Math.min(minMeasuredVoltage, controller.getMeasuredVoltage());
            maxMeasuredVoltage = Math.max(maxMeasuredVoltage, controller.getMeasuredVoltage());
            if (controller.isVoltageDip()) dipSamples++;
            if (Math.abs(controller.getVoltageControlPreLimitOutput())
                    >= controller.getPreliminaryReactiveCurrentLimit() - LIMIT_EPS) {
                iqLimitSamples++;
            }
        }

        private double severity() {
            return Math.max(maxIqcmdDelta, Math.max(maxRegcaIqDelta,
                    Math.max(maxQIntegralDelta, maxVIntegralDelta)));
        }
    }

    private static final class PsseDyrRecordReaderUnchecked {
        private static Set<String> modelNames(Path path) {
            try {
                return PsseDyrRecordReader.read(path).stream()
                        .map(PsseDyrRecord::canonicalModelName)
                        .collect(java.util.stream.Collectors.toUnmodifiableSet());
            } catch (Exception exception) {
                throw new IllegalStateException("Unable to read staged DYR", exception);
            }
        }
    }

    /** Diagnostic wrapper that keeps either REECA1 command at its initialized value. */
    private static final class FrozenAxisController implements RenewableElectricalController {
        private final RenewableElectricalController delegate;
        private final boolean freezeActive;
        private final boolean freezeReactive;
        private double initialIp;
        private double initialIq;

        private FrozenAxisController(RenewableElectricalController delegate,
                boolean freezeActive, boolean freezeReactive) {
            this.delegate = delegate;
            this.freezeActive = freezeActive;
            this.freezeReactive = freezeReactive;
        }

        @Override public void configureIntegrationStep(double step) {
            delegate.configureIntegrationStep(step);
        }
        @Override public void initialize(double p, double q, double v) {
            delegate.initialize(p, q, v);
            initialIp = delegate.getIpcmd();
            initialIq = delegate.getIqcmd();
        }
        @Override public void step(double dt, double p, double q, double v, double frequency) {
            delegate.step(dt, p, q, v, frequency);
        }
        @Override public void step(double dt, double p, double q, double v,
                double frequency, int flag) {
            delegate.step(dt, p, q, v, frequency, flag);
        }
        @Override public double getIpcmd() {
            return freezeActive ? initialIp : delegate.getIpcmd();
        }
        @Override public double getIqcmd() {
            return freezeReactive ? initialIq : delegate.getIqcmd();
        }
        @Override public Repca1Model getPlantController() {
            return delegate.getPlantController();
        }
        @Override public void setPlantController(Repca1Model controller) {
            delegate.setPlantController(controller);
        }
    }
}
