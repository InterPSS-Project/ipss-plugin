package org.interpss.core.dstab;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import org.apache.commons.math3.complex.Complex;
import org.apache.commons.math3.linear.Array2DRowRealMatrix;
import org.apache.commons.math3.linear.SingularValueDecomposition;
import org.interpss.IpssCorePlugin;
import org.interpss.dstab.analysis.LocalRenewableQvEigenAnalyzer;
import org.interpss.dstab.analysis.LocalRenewableQvEigenReportWriter;
import org.interpss.dstab.analysis.LocalRenewableQvEigenAnalyzer.Device;
import org.interpss.dstab.analysis.LocalRenewableQvEigenAnalyzer.Mode;
import org.interpss.dstab.analysis.LocalRenewableQvEigenAnalyzer.StateComponent;
import org.interpss.dstab.renewable.Reeca1Model;
import org.interpss.dstab.renewable.Regca1Model;
import org.interpss.dstab.renewable.RenewableElectricalController;
import org.interpss.dstab.renewable.Repca1Model;
import org.interpss.fadapter.psse.PSSEMultiFileLoader;
import org.interpss.fadapter.psse.dyr.PsseDyrRecordReader;
import org.interpss.numeric.sparse.ISparseEqnComplex;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.interpss.core.algo.AclfMethodType;
import com.interpss.dstab.DStabGen;
import com.interpss.dstab.algo.DynamicSimuMethod;
import com.interpss.dstab.cache.StateMonitor;

/**
 * Opt-in one-second no-disturbance drift gate over every Texas2k bus and
 * synchronous machine. All selected cases execute before failures are reported.
 */
public class Texas2kOneSecondDriftTest {
    private static final double VOLTAGE_DRIFT_LIMIT = 1.0e-6;
    private static final double SPEED_DRIFT_LIMIT = 1.0e-7;
    private static final Set<String> RENEWABLE_MODELS = Set.of(
            "REGCA1", "REECA1", "REPCA1", "WTARA1", "WTPTA1", "WTTQA1", "REGFMA1");
    private static final List<String> CASE5_INTERACTING_BUSES = List.of(
            "Bus1004", "Bus1006", "Bus1007", "Bus1009", "Bus1011", "Bus1016",
            "Bus1021", "Bus1022", "Bus1023", "Bus1026", "Bus1027", "Bus1028",
            "Bus1029", "Bus1030", "Bus1032", "Bus1033", "Bus1035", "Bus1036",
            "Bus1037", "Bus1039", "Bus1040", "Bus1042", "Bus1043", "Bus1044",
            "Bus1046", "Bus1055", "Bus1057", "Bus1061", "Bus1062", "Bus1063",
            "Bus1083");
    private static final Path ROOT = relativePathProperty("texas2k.case.root",
            Path.of("testData", "private", "texas2k"));
    private static final List<CaseFile> CASES = List.of(
            new CaseFile("Texas2k_series24_case1_2016summerpeak",
                    "Texas2k_series24_case1_2016summerPeak_v36.RAW", "dynamic_models_case1.dyr",
                    "dynamic_models_case1_gnet.idv"),
            new CaseFile("Texas2k_series24_case2_2016lowload",
                    "Texas2k_series24_case2_2016lowload.RAW", "dynamic_models_case2.dyr",
                    "dynamic_models_case2_gnet.idv"),
            new CaseFile("Texas2k_series24_case3_2024summerpeak",
                    "Texas2k_series24_case3_2024summerpeak_v30.RAW", "dynamic_models_case3.dyr",
                    "dynamic_models_case3_gnet.idv"),
            new CaseFile("Texas2k_series24_case4_2024lowload",
                    "Texas2k_series24_case4_2024lowload.RAW", "dynamic_models_case4.dyr",
                    "dynamic_models_case4_gnet.idv"),
            new CaseFile("Texas2k_series24_case5_2024highrenewables",
                    "Texas2k_series24_case5_2024highrenewables.RAW", "dynamic_models_case5.dyr",
                    "dynamic_models_case5_gnet.idv"),
            new CaseFile("Texas2k_series24_case6_2024lowloadwithgfm",
                    "Texas2k_series24_case6_2024lowloadwithgfm.RAW", "dynamic_models_case6.dyr",
                    "dynamic_models_case6_gnet.idv"));

    @BeforeAll
    static void initializePlugin() {
        IpssCorePlugin.init();
    }

    @Test
    void allSixCasesMeetOneSecondVoltageAndSpeedDriftGates() throws Exception {
        assumeTrue(Boolean.getBoolean("texas2k.drift.enabled"),
                "Enable the private, long-running gate with -Dtexas2k.drift.enabled=true");
        assumeTrue(Files.isDirectory(ROOT), "Missing private Texas2k root: " + ROOT);
        String caseFilter = System.getProperty("texas2k.drift.case", "");
        List<CaseFile> selectedCases = CASES.stream()
                .filter(source -> caseFilter.isEmpty()
                        || source.directory().contains(caseFilter))
                .toList();
        assertFalse(selectedCases.isEmpty(), "No Texas2k case matches: " + caseFilter);
        assertAll("Texas2k one-second no-disturbance drift gates",
                selectedCases.stream().map(source -> () -> verify(source)));
    }

    @Test
    void diagnosticRankingIsLargestFirstAndDeterministicForTies() {
        List<RankingRow> ranked = rankRows(java.util.stream.Stream.of(
                new RankingRow("BUS_VOLTAGE", "", "Bus2", "magnitude", 0.2, 1.0, 1.2),
                new RankingRow("BUS_VOLTAGE", "", "Bus3", "magnitude", 0.1, 1.0, 0.9),
                new RankingRow("BUS_VOLTAGE", "", "Bus1", "magnitude", 0.2, 1.0, 0.8)), 2);

        assertEquals(List.of("Bus1", "Bus2"), ranked.stream()
                .map(RankingRow::id).toList());
    }

    @Test
    void diagnosticPathConfigurationRejectsAbsolutePaths() {
        String propertyName = "texas2k.relative.path.contract";
        String previous = System.getProperty(propertyName);
        try {
            System.setProperty(propertyName, "../../../portable/cases");
            assertEquals(Path.of("../../../portable/cases").normalize(),
                    relativePathProperty(propertyName, Path.of("unused")));

            System.setProperty(propertyName, Path.of("cases").toAbsolutePath().toString());
            assertThrows(IllegalArgumentException.class,
                    () -> relativePathProperty(propertyName, Path.of("unused")));

            System.setProperty(propertyName, "C:portable-cases");
            assertThrows(IllegalArgumentException.class,
                    () -> relativePathProperty(propertyName, Path.of("unused")));
        } finally {
            if (previous == null) System.clearProperty(propertyName);
            else System.setProperty(propertyName, previous);
        }
    }

    @Test
    void case5ReportsControllerWeightedQvMode() throws Exception {
        assumeTrue(Boolean.getBoolean("texas2k.gridStrength.enabled"),
                "Enable the private diagnostic with -Dtexas2k.gridStrength.enabled=true");
        CaseFile source = CASES.get(4);
        Path directory = ROOT.resolve(source.directory());
        Path raw = directory.resolve(source.raw());
        Path dyr = directory.resolve(source.dyr());
        Path gnet = directory.resolve(source.gnet());
        assumeTrue(Files.isRegularFile(raw), "Missing private Texas2k RAW: " + raw);
        assumeTrue(Files.isRegularFile(dyr), "Missing private Texas2k DYR: " + dyr);
        assumeTrue(Files.isRegularFile(gnet), "Missing private Texas2k GNET IDV: " + gnet);

        var context = new PSSEMultiFileLoader().loadDStab(
                raw.toString(), dyr.toString(), gnet.toString());
        var network = context.getDStabilityNet();
        network.setBypassDataCheck(true);
        network.setAllowGenWithoutMach(true);
        var loadflow = context.getDynSimuAlgorithm().getAclfAlgorithm();
        loadflow.getDataCheckConfig().setAutoTurnLine2Xfr(true);
        loadflow.getDataCheckConfig().setTurnOffIslandBus(true);
        loadflow.setNonDivergent(true);
        loadflow.setMaxIterations(50);
        loadflow.setTolerance(1.0e-8);
        assertTrue(loadflow.loadflow(), () -> "Case 5 grid-strength load flow: "
                + network.maxMismatch(AclfMethodType.NR));
        ISparseEqnComplex y = network.formYMatrix();
        network.getBusList().stream().filter(bus -> bus.isSwing()).forEach(bus ->
                y.setA(new Complex(0.0, 1.0e10), bus.getSortNumber(), bus.getSortNumber()));
        y.factorization(1.0e-20);

        for (String busId : List.of(
                "Bus1083", "Bus1011", "Bus1046", "Bus1062", "Bus3051")) {
            var bus = network.getBus(busId);
            assertNotNull(bus, "Missing diagnostic bus " + busId);
            y.setB2Zero();
            y.setB2Unity(bus.getSortNumber());
            y.solveEqn();
            Complex z = y.getX(bus.getSortNumber());
            System.out.printf(java.util.Locale.ROOT,
                    "Texas2k Case 5 driving-point impedance %s: %.9g+j%.9g |Z|=%.9g pu%n",
                    busId, z.getReal(), z.getImaginary(), z.abs());
            assertTrue(Double.isFinite(z.abs()) && z.abs() > 0.0,
                    "invalid driving-point impedance at " + busId + ": " + z);
        }

        y.setB2Zero();
        Complex perBusReactiveCurrent = new Complex(0.0,
                -1.0 / CASE5_INTERACTING_BUSES.size());
        CASE5_INTERACTING_BUSES.forEach(busId -> y.setBi(perBusReactiveCurrent,
                network.getBus(busId).getSortNumber()));
        y.solveEqn();
        Difference commonMode = CASE5_INTERACTING_BUSES.stream().map(busId -> {
            var bus = network.getBus(busId);
            Complex voltage = bus.getVoltage();
            Complex deltaVoltage = y.getX(bus.getSortNumber());
            double deltaMagnitude = deltaVoltage.multiply(voltage.conjugate()).getReal()
                    / voltage.abs();
            return new Difference(busId, Math.abs(deltaMagnitude));
        }).max(java.util.Comparator.comparingDouble(Difference::value)).orElseThrow();
        System.out.printf(java.util.Locale.ROOT,
                "Texas2k Case 5 normalized common-Q voltage sensitivity: %.9g pu/pu@%s%n",
                commonMode.value(), commonMode.id());

        var qvAnalysis = LocalRenewableQvEigenAnalyzer.analyze(
                network, CASE5_INTERACTING_BUSES);
        double[][] coupling = qvAnalysis.couplingMatrix();
        var decomposition = new SingularValueDecomposition(
                new Array2DRowRealMatrix(coupling, false));
        double dominantValue = decomposition.getSingularValues()[0];
        double[] responseMode = decomposition.getU().getColumn(0);
        double[] injectionMode = decomposition.getV().getColumn(0);
        List<ModeParticipation> dominantResponses = rankMode(responseMode);
        List<ModeParticipation> dominantInjections = rankMode(injectionMode);
        System.out.printf(java.util.Locale.ROOT,
                "Texas2k Case 5 Q-to-|V| coupling dominant singular value: %.9g%n",
                dominantValue);
        System.out.println("  dominant voltage-response buses: " + dominantResponses);
        System.out.println("  dominant reactive-injection buses: " + dominantInjections);
        assertTrue(Double.isFinite(dominantValue) && dominantValue > 0.0,
                "invalid dominant Q-to-|V| coupling singular value: " + dominantValue);

        List<Device> devices = qvAnalysis.devices();
        Mode qvMode = qvAnalysis.dominantMode();
        System.out.printf(java.util.Locale.ROOT,
                "Texas2k Case 5 candidate Q/V linearization: devices=%d max eigenvalue="
                        + "%.9g%+.9gj 1/s validTwoSided=%s%n",
                devices.size(), qvMode.real(), qvMode.imaginary(),
                qvAnalysis.isTwoSidedLinearizationValid());
        System.out.println("  dominant controller states: " + qvMode.participation());
        System.out.println("  operating-point constraints: "
                + qvAnalysis.operatingPointConstraints());
        assertTrue(Double.isFinite(qvMode.real()) && Double.isFinite(qvMode.imaginary()),
                "invalid controller-weighted Q/V eigenvalue: " + qvMode);
        assertTrue(qvAnalysis.isTwoSidedLinearizationValid(),
                "Case 5 PIQ perturbations must be interior to VMIN-V0 and VMAX-V0");
        assertTrue(qvAnalysis.limiterRegionEnumerationComplete());
        assertEquals(1, qvAnalysis.limiterRegionModes().size());
        assertTrue(qvAnalysis.operatingPointConstraints().isEmpty());
        System.out.println("  limiter-region envelopes: "
                + qvAnalysis.limiterRegionModes().stream().map(region ->
                        (region.assumptions().isEmpty() ? "INTERIOR"
                                : region.assumptions().get(0).branch()) + "="
                                + region.dominantMode().real() + "+j"
                                + region.dominantMode().imaginary() + ", coneFeasible="
                                + region.tangentCone().feasible()).toList());
        Path reportDirectory = Path.of(System.getProperty("texas2k.gridStrength.reportDir",
                Path.of("target", "dynamic-model-validation", "texas2k-case5-qv")
                        .toString()));
        LocalRenewableQvEigenReportWriter.write(reportDirectory, qvAnalysis);
        System.out.println("  machine-readable Q/V benchmark: "
                + reportDirectory.toAbsolutePath());
    }

    @Test
    void machineReadableQvReportPreservesFullMatricesAndEigenvector(@TempDir Path directory)
            throws Exception {
        List<String> buses = List.of("Bus1");
        double[][] coupling = {{0.125}};
        Device device = new Device("Bus1", "1", 0,
                0.75, 1.02, -0.04, .02, .3, .4, .5, .6,
                .07, .8, .9, .1, .2);
        double[][] state = new double[6][6];
        for (int index = 0; index < state.length; index++) state[index][index] = -index - 1.0;
        List<StateComponent> vector = new ArrayList<>();
        for (int index = 0; index < 6; index++) {
            vector.add(new StateComponent("Bus1:1",
                    LocalRenewableQvEigenAnalyzer.stateName(index),
                    index / 10.0, -index / 20.0, Math.hypot(index / 10.0, index / 20.0)));
        }
        Mode mode = new Mode(.01, -.25, vector);

        var analysis = new LocalRenewableQvEigenAnalyzer.Analysis(buses, coupling,
                List.of(device), state, mode, List.of(), List.of(), true);
        var report = LocalRenewableQvEigenReportWriter.write(directory, analysis);
        assertEquals(directory.toAbsolutePath().normalize(), report.directory());
        assertTrue(report.summary().isAbsolute());

        List<String> couplingRows = Files.readAllLines(report.coupling());
        List<String> stateRows = Files.readAllLines(report.stateMatrix());
        List<String> vectorRows = Files.readAllLines(report.dominantMode());
        assertEquals(2, couplingRows.size());
        assertEquals("response_bus,Bus1", couplingRows.get(0));
        assertEquals(7, stateRows.size());
        assertEquals(7, vectorRows.size());
        assertTrue(vectorRows.get(6).startsWith("Bus1:1,REGCA_IQ,"));
        assertTrue(Files.readString(report.summary())
                .contains("dominant_eigenvalue_imaginary,-0.25"));
        assertTrue(Files.readString(report.summary())
                .contains("two_sided_linearization_valid,true"));
        assertTrue(Files.readString(report.summary())
                .contains("interpretation,two_sided_local_mode"));
        assertEquals(1, Files.readAllLines(report.constraints()).size());
        assertEquals("region,dominant_real,dominant_imaginary,frequency_hz,cone_feasible,"
                        + "witness_phase_radians,assumptions",
                Files.readAllLines(report.limiterRegions()).get(0));
        assertEquals(2, Files.readAllLines(report.devices()).size());
    }

    private static List<ModeParticipation> rankMode(double[] vector) {
        double maximum = java.util.Arrays.stream(vector).map(Math::abs).max().orElseThrow();
        return java.util.stream.IntStream.range(0, vector.length)
                .mapToObj(index -> new ModeParticipation(CASE5_INTERACTING_BUSES.get(index),
                        Math.abs(vector[index]) / maximum, Math.signum(vector[index])))
                .sorted(java.util.Comparator.comparingDouble(ModeParticipation::magnitude)
                        .reversed())
                .limit(10)
                .toList();
    }

    private static void verify(CaseFile source) throws Exception {
        Path directory = ROOT.resolve(source.directory());
        Path raw = directory.resolve(source.raw());
        Path dyr = directory.resolve(source.dyr());
        Path gnet = directory.resolve(source.gnet());
        assumeTrue(Files.isRegularFile(raw), "Missing Texas2k RAW: " + raw);
        assumeTrue(Files.isRegularFile(dyr), "Missing Texas2k DYR: " + dyr);
        assumeTrue(Files.isRegularFile(gnet), "Missing Texas2k GNET IDV: " + gnet);

        Path effectiveDyr = dyr;
        String renewableSelection = System.getProperty("texas2k.drift.renewables");
        if (Boolean.getBoolean("texas2k.drift.excludeRenewables") || renewableSelection != null) {
            List<org.interpss.fadapter.psse.dyr.PsseDyrRecord> sourceRecords =
                    PsseDyrRecordReader.read(dyr);
            Set<String> selectedRenewables = renewableSelection == null ? Set.of()
                    : java.util.Arrays.stream(renewableSelection.split(","))
                            .map(String::trim).filter(value -> !value.isEmpty())
                            .collect(Collectors.toSet());
            Set<Integer> selectedRenewableBuses = java.util.Arrays.stream(
                            System.getProperty("texas2k.drift.renewableBuses", "").split(","))
                    .map(String::trim).filter(value -> !value.isEmpty())
                    .map(Integer::parseInt).collect(Collectors.toSet());
            Set<String> selectedRenewableDevices = java.util.Arrays.stream(
                            System.getProperty("texas2k.drift.renewableDevices", "").split(","))
                    .map(String::trim).filter(value -> !value.isEmpty())
                    .collect(Collectors.toSet());
            String selectedQFlag = System.getProperty("texas2k.drift.qFlag", "");
            Set<String> selectedQFlagDevices = selectedQFlag.isEmpty() ? Set.of()
                    : sourceRecords.stream()
                            .filter(record -> record.canonicalModelName().equals("REECA1"))
                            .filter(record -> record.parameters().get(3).equals(selectedQFlag))
                            .map(Texas2kOneSecondDriftTest::deviceKey)
                            .collect(Collectors.toSet());
            String selectedPFlag = System.getProperty("texas2k.drift.pFlag", "");
            Set<String> selectedPFlagDevices = selectedPFlag.isEmpty() ? Set.of()
                    : sourceRecords.stream()
                            .filter(record -> record.canonicalModelName().equals("REECA1"))
                            .filter(record -> record.parameters().get(4).equals(selectedPFlag))
                            .map(Texas2kOneSecondDriftTest::deviceKey)
                            .collect(Collectors.toSet());
            Path filteredDirectory = Files.createTempDirectory("texas2k-filtered-");
            filteredDirectory.toFile().deleteOnExit();
            effectiveDyr = filteredDirectory.resolve("selected.dyr");
            effectiveDyr.toFile().deleteOnExit();
            boolean onlySelectedRenewables = Boolean.getBoolean(
                    "texas2k.drift.onlySelectedRenewables");
            String filtered = sourceRecords.stream()
                    .filter(record -> (selectedRenewables.contains(record.canonicalModelName())
                                    && ((selectedRenewableBuses.isEmpty()
                                                    && selectedRenewableDevices.isEmpty())
                                            || selectedRenewableBuses.contains(record.busNumber())
                                            || selectedRenewableDevices.contains(deviceKey(record)))
                                    && (selectedQFlag.isEmpty()
                                            || selectedQFlagDevices.contains(deviceKey(record)))
                                    && (selectedPFlag.isEmpty()
                                            || selectedPFlagDevices.contains(deviceKey(record))))
                            || (!onlySelectedRenewables
                                    && !RENEWABLE_MODELS.contains(record.canonicalModelName())))
                    .map(record -> record.rawText() + " /")
                    .collect(Collectors.joining(System.lineSeparator()));
            Files.writeString(effectiveDyr, filtered);
            copyPreparationSibling(dyr, effectiveDyr, "_MODREMOVE.idv");
            System.out.println("Filtered DYR staged as: " + effectiveDyr.getFileName());
        }
        PSSEMultiFileLoader loader = new PSSEMultiFileLoader();
        var context = loader.loadDStab(raw.toString(), effectiveDyr.toString(),
                gnet.toString());
        var network = context.getDStabilityNet();
        var algorithm = context.getDynSimuAlgorithm();
        if (Boolean.getBoolean("texas2k.drift.freezeElectricalControllers")) {
            network.getBusList().forEach(bus -> bus.getContributeGenList().stream()
                    .filter(DStabGen.class::isInstance)
                    .map(DStabGen.class::cast)
                    .map(DStabGen::getDynamicGenDevice)
                    .filter(Regca1Model.class::isInstance)
                    .map(Regca1Model.class::cast)
                    .forEach(converter -> converter.setActiveElectricalController(
                            new FixedCurrentController())));
        } else if (Boolean.getBoolean("texas2k.drift.freezeActiveCommand")
                || Boolean.getBoolean("texas2k.drift.freezeReactiveCommand")) {
            boolean freezeActive = Boolean.getBoolean("texas2k.drift.freezeActiveCommand");
            boolean freezeReactive = Boolean.getBoolean("texas2k.drift.freezeReactiveCommand");
            network.getBusList().forEach(bus -> bus.getContributeGenList().stream()
                    .filter(DStabGen.class::isInstance)
                    .map(DStabGen.class::cast)
                    .map(DStabGen::getDynamicGenDevice)
                    .filter(Regca1Model.class::isInstance)
                    .map(Regca1Model.class::cast)
                    .forEach(converter -> converter.setActiveElectricalController(
                            new FrozenAxisController(converter.getActiveElectricalController(),
                                    freezeActive, freezeReactive))));
        }
        if (Boolean.getBoolean("texas2k.drift.disablePlantControllers")) {
            network.getBusList().forEach(bus -> bus.getContributeGenList().stream()
                    .filter(DStabGen.class::isInstance)
                    .map(DStabGen.class::cast)
                    .map(DStabGen::getDynamicGenDevice)
                    .filter(Regca1Model.class::isInstance)
                    .map(Regca1Model.class::cast)
                    .filter(converter -> converter.getActiveElectricalController() != null)
                    .forEach(converter -> converter.getActiveElectricalController()
                            .setPlantController(null)));
        }
        if (Boolean.getBoolean("texas2k.drift.disableGovernors")) {
            network.getBusList().forEach(bus -> bus.getContributeGenList().stream()
                    .filter(DStabGen.class::isInstance)
                    .map(DStabGen.class::cast)
                    .map(DStabGen::getMach)
                    .filter(java.util.Objects::nonNull)
                    .filter(machine -> machine.getGovernor() != null)
                    .forEach(machine -> machine.getGovernor().setStatus(false)));
        }
        network.setBypassDataCheck(true);
        network.setAllowGenWithoutMach(true);
        var loadflow = algorithm.getAclfAlgorithm();
        loadflow.getDataCheckConfig().setAutoTurnLine2Xfr(true);
        loadflow.getDataCheckConfig().setTurnOffIslandBus(true);
        loadflow.setNonDivergent(true);
        loadflow.setMaxIterations(50);
        loadflow.setTolerance(Double.parseDouble(
                System.getProperty("texas2k.drift.loadflowTolerance", "1.0e-8")));
        assertTrue(loadflow.loadflow(), () -> source.directory() + " load flow: "
                + network.maxMismatch(AclfMethodType.NR));
        network.setBypassDataCheck(false);
        network.checkData(loadflow.getDataCheckConfig());
        assertTrue(network.initDStabNet(), source.directory() + " DStab network initialization");

        algorithm.setSimuMethod(DynamicSimuMethod.MODIFIED_EULER);
        algorithm.setSimuStepSec(Double.parseDouble(
                System.getProperty("texas2k.drift.step", Double.toString(1.0 / 240.0))));
        algorithm.setTotalSimuTimeSec(Double.parseDouble(
                System.getProperty("texas2k.drift.endTime", "1.0")));
        algorithm.setOutPutPerSteps(1);
        algorithm.setSimuOutputHandler(new StateMonitor());
        assertTrue(algorithm.initialization(), source.directory() + " initialization");

        Map<String, Double> initialVoltage = new LinkedHashMap<>();
        network.getBusList().stream().filter(bus -> bus.isActive()).forEach(bus ->
                initialVoltage.put(bus.getId(), bus.getVoltage().abs()));
        Map<String, Double> initialSpeed = new LinkedHashMap<>();
        network.getBusList().stream().filter(bus -> bus.isActive()).forEach(bus ->
                bus.getContributeGenList().stream()
                        .filter(DStabGen.class::isInstance)
                        .map(DStabGen.class::cast)
                        .filter(DStabGen::isActive)
                        .map(DStabGen::getMach)
                        .filter(java.util.Objects::nonNull)
                        .forEach(machine -> initialSpeed.put(
                                machine.getId(), machine.getSpeed())));
        Map<String, WindSnapshot> initialWind = new LinkedHashMap<>();
        Map<String, ReecaSnapshot> initialReeca = new LinkedHashMap<>();
        Map<String, RepcaSnapshot> initialRepca = new LinkedHashMap<>();
        Map<String, ConverterPowerSnapshot> initialConverterPower = new LinkedHashMap<>();
        Map<String, ControlStatus> initialControlStatus = new LinkedHashMap<>();
        network.getBusList().forEach(bus -> bus.getContributeGenList().stream()
                .filter(DStabGen.class::isInstance)
                .map(DStabGen.class::cast)
                .filter(DStabGen::isActive)
                .filter(gen -> gen.getDynamicGenDevice() instanceof Regca1Model)
                .forEach(gen -> {
                    Regca1Model converter = (Regca1Model) gen.getDynamicGenDevice();
                    String deviceId = bus.getId() + ":" + gen.getId();
                    initialConverterPower.put(deviceId, converterPower(converter));
                    var controller = converter.getReeca1Controller();
                    if (controller != null) {
                        initialReeca.put(deviceId,
                                reecaSnapshot(converter, controller));
                        initialControlStatus.put(deviceId, controlStatus(controller));
                        if (controller.getPlantController() != null) {
                            initialRepca.put(deviceId,
                                    repcaSnapshot(controller.getPlantController()));
                        }
                    }
                    if (controller == null || controller.getWindControlStack() == null
                            || controller.getWindControlStack().getTorqueController() == null) return;
                    var torque = controller.getWindControlStack().getTorqueController();
                    initialWind.put(bus.getId() + ":" + gen.getId(), new WindSnapshot(
                            converter.getStates(null).get("REGCA1_P") instanceof Number p
                                    ? p.doubleValue() : Double.NaN,
                            torque.getPref(), torque.getTorque(), torque.getFilteredPower(),
                            torque.getSpeedReference()));
                }));
        Difference iqBoundary = initialReeca.entrySet().stream()
                .map(entry -> new Difference(entry.getKey(),
                        Math.abs(entry.getValue().iqOut() - entry.getValue().iqState())))
                .max(java.util.Comparator.comparingDouble(Difference::value))
                .orElse(new Difference("none", 0.0));
        long initialDipCount = network.getBusList().stream()
                .flatMap(bus -> bus.getContributeGenList().stream())
                .filter(DStabGen.class::isInstance).map(DStabGen.class::cast)
                .filter(DStabGen::isActive)
                .map(DStabGen::getDynamicGenDevice).filter(Regca1Model.class::isInstance)
                .map(Regca1Model.class::cast).map(Regca1Model::getReeca1Controller)
                .filter(java.util.Objects::nonNull)
                .filter(controller -> controller.getMeasuredVoltage() < controller.getData().vdip()
                        || controller.getMeasuredVoltage() > controller.getData().vup())
                .count();
        network.getBusList().forEach(bus -> bus.getContributeGenList().stream()
                .filter(DStabGen.class::isInstance).map(DStabGen.class::cast)
                .filter(DStabGen::isActive)
                .filter(gen -> gen.getDynamicGenDevice() instanceof Regca1Model)
                .forEach(gen -> {
                    Reeca1Model controller = ((Regca1Model) gen.getDynamicGenDevice())
                            .getReeca1Controller();
                    if (controller != null && (controller.getMeasuredVoltage() < controller.getData().vdip()
                            || controller.getMeasuredVoltage() > controller.getData().vup())) {
                        System.out.printf(java.util.Locale.ROOT,
                                "  initial dip %s:%s V=%.9g thresholds=[%.9g,%.9g]%n",
                                bus.getId(), gen.getId(), controller.getMeasuredVoltage(),
                                controller.getData().vdip(), controller.getData().vup());
                    }
                }));
        System.out.printf(java.util.Locale.ROOT,
                "%s initial REGCA1 Iq boundary mismatch=%.9g@%s REECA1 dips=%d%n",
                source.directory(), iqBoundary.value(), iqBoundary.id(), initialDipCount);

        if (Boolean.getBoolean("texas2k.drift.traceFirstDivergence")) {
            boolean reported = false;
            boolean controllerMovementReported = false;
            boolean dipTransitionReported = false;
            boolean limitTransitionReported = false;
            double controllerMovementLimit = Double.parseDouble(System.getProperty(
                    "texas2k.drift.controllerMovementLimit", "1.0e-10"));
            double endTime = algorithm.getTotalSimuTimeSec();
            // Match AbstractDStabSolver.performSimulation(): it executes the step
            // whose starting time is exactly the configured end time.
            while (algorithm.getSimuTime() <= endTime) {
                assertTrue(algorithm.solveDEqnStep(true), source.directory() + " simulation step");
                if (!controllerMovementReported) {
                    StateDifference movement = worstControllerStateDifference(
                            network, initialReeca, initialRepca);
                    if (movement.delta() > controllerMovementLimit) {
                        System.out.printf(java.util.Locale.ROOT,
                                "%s first controller movement at t=%.9g: %s %s %s "
                                        + "delta=%.9g initial=%.9g current=%.9g%n",
                                source.directory(), algorithm.getSimuTime(), movement.model(),
                                movement.id(), movement.state(), movement.delta(),
                                movement.initial(), movement.current());
                        reportPflagAggregate(algorithm.getSimuTime(), network,
                                initialConverterPower);
                        controllerMovementReported = true;
                    }
                }
                if (!reported) {
                    Difference stepVoltage = worstVoltageDifference(network, initialVoltage);
                    Difference stepSpeed = worstSpeedDifference(network, initialSpeed);
                    if (stepVoltage.value() > VOLTAGE_DRIFT_LIMIT
                            || stepSpeed.value() > SPEED_DRIFT_LIMIT) {
                        reportFirstDivergence(source, algorithm.getSimuTime(), stepVoltage,
                                stepSpeed, network, initialReeca, initialRepca,
                                initialConverterPower);
                        reported = true;
                    }
                }
                if (!dipTransitionReported) {
                    dipTransitionReported = reportControlTransitions(source,
                            algorithm.getSimuTime(), network, initialControlStatus, true);
                }
                if (!limitTransitionReported) {
                    limitTransitionReported = reportControlTransitions(source,
                            algorithm.getSimuTime(), network, initialControlStatus, false);
                }
            }
        } else {
            assertTrue(algorithm.performSimulation(), source.directory() + " simulation");
        }
        Difference voltage = worstVoltageDifference(network, initialVoltage);
        Difference speed = worstSpeedDifference(network, initialSpeed);
        reportFinalRanking(source, network, initialVoltage, initialSpeed,
                initialReeca, initialRepca);
        Difference wind = initialWind.entrySet().stream().map(entry -> {
            String[] key = entry.getKey().split(":", 2);
            DStabGen gen = (DStabGen) network.getBus(key[0]).getContributeGen(key[1]);
            Regca1Model converter = (Regca1Model) gen.getDynamicGenDevice();
            double pref = converter.getReeca1Controller().getWindControlStack()
                    .getTorqueController().getPref();
            return new Difference(entry.getKey(), Math.abs(pref - entry.getValue().pref()));
        }).max(java.util.Comparator.comparingDouble(Difference::value))
                .orElse(new Difference("none", 0.0));
        Difference reeca = initialReeca.entrySet().stream().map(entry -> {
            String[] key = entry.getKey().split(":", 2);
            DStabGen gen = (DStabGen) network.getBus(key[0]).getContributeGen(key[1]);
            Regca1Model converter = (Regca1Model) gen.getDynamicGenDevice();
            ReecaSnapshot current = reecaSnapshot(converter, converter.getReeca1Controller());
            return new Difference(entry.getKey(), current.maxDifference(entry.getValue()));
        }).max(java.util.Comparator.comparingDouble(Difference::value))
                .orElse(new Difference("none", 0.0));
        if (!wind.id().equals("none")) {
            String[] key = wind.id().split(":", 2);
            DStabGen gen = (DStabGen) network.getBus(key[0]).getContributeGen(key[1]);
            Regca1Model converter = (Regca1Model) gen.getDynamicGenDevice();
            var torque = converter.getReeca1Controller().getWindControlStack()
                    .getTorqueController();
            System.out.printf(java.util.Locale.ROOT,
                    "%s WTTQA1 %s initial=%s final[pref=%.9g torque=%.9g pe=%.9g filt=%.9g wref=%.9g]%n",
                    source.directory(), wind.id(), initialWind.get(wind.id()), torque.getPref(),
                    torque.getTorque(), ((Number) converter.getStates(null).get("REGCA1_P")).doubleValue(),
                    torque.getFilteredPower(), torque.getSpeedReference());
        }
        if (!reeca.id().equals("none")) {
            String[] key = reeca.id().split(":", 2);
            DStabGen gen = (DStabGen) network.getBus(key[0]).getContributeGen(key[1]);
            Regca1Model converter = (Regca1Model) gen.getDynamicGenDevice();
            Reeca1Model controller = converter.getReeca1Controller();
            System.out.printf(java.util.Locale.ROOT,
                    "%s REECA1 %s data=%s%n  initial=%s%n  final=%s Q=%.9g%n",
                    source.directory(), reeca.id(), controller.getData(),
                    initialReeca.get(reeca.id()), reecaSnapshot(converter, controller),
                    ((Number) converter.getStates(null).get("REGCA1_Q")).doubleValue());
        }
        System.out.printf(java.util.Locale.ROOT,
                "%s one-second drift: voltage=%.9g@%s speed=%.9g@%s windPref=%.9g@%s reeca=%.9g@%s%n",
                source.directory(), voltage.value(), voltage.id(), speed.value(), speed.id(),
                wind.value(), wind.id(), reeca.value(), reeca.id());
        assertAll(source.directory() + " drift limits",
                () -> assertTrue(Double.isFinite(voltage.value())
                                && voltage.value() <= VOLTAGE_DRIFT_LIMIT,
                        source.directory() + " worst voltage drift " + voltage),
                () -> assertTrue(Double.isFinite(speed.value())
                                && speed.value() <= SPEED_DRIFT_LIMIT,
                        source.directory() + " worst speed drift " + speed));
    }

    /** Preserve PSS/E case-preparation directives in filtered DYR diagnostics. */
    private static void copyPreparationSibling(Path sourceDyr, Path filteredDyr,
            String suffix) throws java.io.IOException {
        String sourceName = sourceDyr.getFileName().toString();
        String sourceStem = sourceName.substring(0, sourceName.length() - 4);
        Path source = sourceDyr.resolveSibling(sourceStem + suffix);
        if (!Files.isRegularFile(source)) return;
        String filteredName = filteredDyr.getFileName().toString();
        String filteredStem = filteredName.substring(0, filteredName.length() - 4);
        Path target = filteredDyr.resolveSibling(filteredStem + suffix);
        Files.copy(source, target);
        target.toFile().deleteOnExit();
    }

    private record Difference(String id, double value) { }

    private record StateDifference(String model, String id, String state,
            double delta, double initial, double current) { }

    private record RankingRow(String category, String model, String id, String state,
            double delta, double initial, double current) { }

    private record ConverterPowerSnapshot(double p, double q) { }

    private record ModeParticipation(String busId, double magnitude, double sign) { }

    private static String deviceKey(org.interpss.fadapter.psse.dyr.PsseDyrRecord record) {
        return record.busNumber() + ":" + record.deviceId();
    }

    private static Difference worstVoltageDifference(
            com.interpss.dstab.BaseDStabNetwork<?, ?> network,
            Map<String, Double> initialVoltage) {
        return initialVoltage.entrySet().stream()
                .map(entry -> new Difference(entry.getKey(), Math.abs(
                        network.getBus(entry.getKey()).getVoltage().abs() - entry.getValue())))
                .max(java.util.Comparator.comparingDouble(Difference::value))
                .orElse(new Difference("none", 0.0));
    }

    private static Difference worstSpeedDifference(
            com.interpss.dstab.BaseDStabNetwork<?, ?> network,
            Map<String, Double> initialSpeed) {
        return initialSpeed.entrySet().stream()
                .map(entry -> new Difference(entry.getKey(), Math.abs(
                        network.getMachine(entry.getKey()).getSpeed() - entry.getValue())))
                .max(java.util.Comparator.comparingDouble(Difference::value))
                .orElse(new Difference("none", 0.0));
    }

    private static void reportFinalRanking(CaseFile source,
            com.interpss.dstab.BaseDStabNetwork<?, ?> network,
            Map<String, Double> initialVoltage, Map<String, Double> initialSpeed,
            Map<String, ReecaSnapshot> initialReeca,
            Map<String, RepcaSnapshot> initialRepca) throws java.io.IOException {
        int limit = Math.max(1, Integer.getInteger("texas2k.drift.rankingLimit", 10));
        List<RankingRow> voltage = rankRows(initialVoltage.entrySet().stream().map(entry -> {
            double current = network.getBus(entry.getKey()).getVoltage().abs();
            return new RankingRow("BUS_VOLTAGE", "", entry.getKey(), "magnitude",
                    Math.abs(current - entry.getValue()), entry.getValue(), current);
        }), limit);
        List<RankingRow> speed = rankRows(initialSpeed.entrySet().stream().map(entry -> {
            double current = network.getMachine(entry.getKey()).getSpeed();
            return new RankingRow("MACHINE_SPEED", "", entry.getKey(), "speed",
                    Math.abs(current - entry.getValue()), entry.getValue(), current);
        }), limit);
        List<RankingRow> controllers = rankRows(controllerStateDifferences(
                network, initialReeca, initialRepca).map(value -> new RankingRow(
                        "CONTROLLER_STATE", value.model(), value.id(), value.state(),
                        value.delta(), value.initial(), value.current())), limit);

        System.out.println(source.directory() + " final top-" + limit + " drift ranking:");
        java.util.stream.Stream.of(voltage, speed, controllers).flatMap(List::stream)
                .forEach(row -> System.out.printf(java.util.Locale.ROOT,
                        "  %s %s %s %s delta=%.9g initial=%.9g final=%.9g%n",
                        row.category(), row.model(), row.id(), row.state(), row.delta(),
                        row.initial(), row.current()));
        writeRankingCsv(source, java.util.stream.Stream.of(voltage, speed, controllers)
                .flatMap(List::stream).toList());
    }

    private static List<RankingRow> rankRows(java.util.stream.Stream<RankingRow> rows,
            int limit) {
        return rows.sorted(java.util.Comparator.comparingDouble(RankingRow::delta).reversed()
                        .thenComparing(RankingRow::category)
                        .thenComparing(RankingRow::model)
                        .thenComparing(RankingRow::id)
                        .thenComparing(RankingRow::state))
                .limit(limit).toList();
    }

    private static void writeRankingCsv(CaseFile source, List<RankingRow> rows)
            throws java.io.IOException {
        Path reportDirectory = relativePathProperty("texas2k.drift.reportDir",
                Path.of("target", "dynamic-model-validation", "texas2k-flat-ranking"));
        Files.createDirectories(reportDirectory);
        StringBuilder csv = new StringBuilder(
                "category,model,id,state,absolute_delta,initial,final\n");
        for (RankingRow row : rows) {
            csv.append(row.category()).append(',').append(row.model()).append(',')
                    .append(row.id()).append(',').append(row.state()).append(',')
                    .append(String.format(java.util.Locale.ROOT, "%.17g,%.17g,%.17g%n",
                            row.delta(), row.initial(), row.current()));
        }
        Path output = reportDirectory.resolve(source.directory() + ".csv");
        Files.writeString(output, csv);
        System.out.println("  ranking CSV: " + output.normalize());
    }

    private static Path relativePathProperty(String propertyName, Path defaultPath) {
        String configured = System.getProperty(propertyName);
        Path path = (configured == null || configured.isBlank()
                ? defaultPath : Path.of(configured)).normalize();
        if (path.isAbsolute() || path.getRoot() != null) {
            throw new IllegalArgumentException(propertyName + " must be a relative path");
        }
        return path;
    }

    private static void reportFirstDivergence(CaseFile source, double time,
            Difference voltage, Difference speed,
            com.interpss.dstab.BaseDStabNetwork<?, ?> network,
            Map<String, ReecaSnapshot> initialReeca,
            Map<String, RepcaSnapshot> initialRepca,
            Map<String, ConverterPowerSnapshot> initialConverterPower) {
        System.out.printf(java.util.Locale.ROOT,
                "%s first drift threshold at t=%.9g: voltage=%.9g@%s speed=%.9g@%s%n",
                source.directory(), time, voltage.value(), voltage.id(), speed.value(), speed.id());
        reportPflagAggregate(time, network, initialConverterPower);
        initialReeca.entrySet().stream().map(entry -> {
            String[] key = entry.getKey().split(":", 2);
            DStabGen gen = (DStabGen) network.getBus(key[0]).getContributeGen(key[1]);
            Regca1Model converter = (Regca1Model) gen.getDynamicGenDevice();
            ReecaSnapshot current = reecaSnapshot(converter, converter.getReeca1Controller());
            return new ControllerDifference("REECA1", entry.getKey(),
                    current.maxDifference(entry.getValue()), entry.getValue().toString(),
                    current.toString());
        }).sorted(java.util.Comparator.comparingDouble(ControllerDifference::value).reversed())
                .limit(10).forEach(Texas2kOneSecondDriftTest::printControllerDifference);
        initialRepca.entrySet().stream().map(entry -> {
            String[] key = entry.getKey().split(":", 2);
            DStabGen gen = (DStabGen) network.getBus(key[0]).getContributeGen(key[1]);
            Regca1Model converter = (Regca1Model) gen.getDynamicGenDevice();
            RepcaSnapshot current = repcaSnapshot(
                    converter.getReeca1Controller().getPlantController());
            return new ControllerDifference("REPCA1", entry.getKey(),
                    current.maxDifference(entry.getValue()), entry.getValue().toString(),
                    current.toString());
        }).sorted(java.util.Comparator.comparingDouble(ControllerDifference::value).reversed())
                .limit(10).forEach(Texas2kOneSecondDriftTest::printControllerDifference);
    }

    private static StateDifference worstControllerStateDifference(
            com.interpss.dstab.BaseDStabNetwork<?, ?> network,
            Map<String, ReecaSnapshot> initialReeca,
            Map<String, RepcaSnapshot> initialRepca) {
        return controllerStateDifferences(network, initialReeca, initialRepca)
                .max(java.util.Comparator.comparingDouble(StateDifference::delta))
                .orElse(new StateDifference("none", "none", "none", 0.0, 0.0, 0.0));
    }

    private static java.util.stream.Stream<StateDifference> controllerStateDifferences(
            com.interpss.dstab.BaseDStabNetwork<?, ?> network,
            Map<String, ReecaSnapshot> initialReeca,
            Map<String, RepcaSnapshot> initialRepca) {
        java.util.stream.Stream<StateDifference> reeca = initialReeca.entrySet().stream()
                .flatMap(entry -> {
                    String[] key = entry.getKey().split(":", 2);
                    DStabGen gen = (DStabGen) network.getBus(key[0]).getContributeGen(key[1]);
                    ReecaSnapshot current = reecaSnapshot((Regca1Model) gen.getDynamicGenDevice(),
                            ((Regca1Model) gen.getDynamicGenDevice()).getReeca1Controller());
                    return entry.getValue().stateDifferences("REECA1", entry.getKey(), current);
                });
        java.util.stream.Stream<StateDifference> repca = initialRepca.entrySet().stream()
                .flatMap(entry -> {
                    String[] key = entry.getKey().split(":", 2);
                    DStabGen gen = (DStabGen) network.getBus(key[0]).getContributeGen(key[1]);
                    RepcaSnapshot current = repcaSnapshot(((Regca1Model) gen.getDynamicGenDevice())
                            .getReeca1Controller().getPlantController());
                    return entry.getValue().stateDifferences("REPCA1", entry.getKey(), current);
                });
        return java.util.stream.Stream.concat(reeca, repca);
    }

    private static void reportPflagAggregate(double time,
            com.interpss.dstab.BaseDStabNetwork<?, ?> network,
            Map<String, ConverterPowerSnapshot> initialPower) {
        for (int pFlag : List.of(0, 1)) {
            double initialP = 0.0;
            double initialQ = 0.0;
            double currentP = 0.0;
            double currentQ = 0.0;
            int count = 0;
            for (Map.Entry<String, ConverterPowerSnapshot> entry : initialPower.entrySet()) {
                String[] key = entry.getKey().split(":", 2);
                DStabGen gen = (DStabGen) network.getBus(key[0]).getContributeGen(key[1]);
                Regca1Model converter = (Regca1Model) gen.getDynamicGenDevice();
                Reeca1Model controller = converter.getReeca1Controller();
                if (controller == null || controller.getData().pFlag() != pFlag) continue;
                ConverterPowerSnapshot current = converterPower(converter);
                initialP += entry.getValue().p();
                initialQ += entry.getValue().q();
                currentP += current.p();
                currentQ += current.q();
                count++;
            }
            System.out.printf(java.util.Locale.ROOT,
                    "  t=%.9g PFLAG=%d devices=%d aggregate dP=%.9g dQ=%.9g "
                            + "initial[P=%.9g,Q=%.9g] current[P=%.9g,Q=%.9g]%n",
                    time, pFlag, count, currentP - initialP, currentQ - initialQ,
                    initialP, initialQ, currentP, currentQ);
        }
    }

    private static ConverterPowerSnapshot converterPower(Regca1Model converter) {
        Map<String, Object> states = converter.getStates(null);
        return new ConverterPowerSnapshot(((Number) states.get("REGCA1_P")).doubleValue(),
                ((Number) states.get("REGCA1_Q")).doubleValue());
    }

    private static void printControllerDifference(ControllerDifference difference) {
        System.out.printf(java.util.Locale.ROOT,
                "  %s %s maxDelta=%.9g%n    initial=%s%n    current=%s%n",
                difference.model(), difference.id(), difference.value(),
                difference.initial(), difference.current());
    }

    private record ControllerDifference(String model, String id, double value,
            String initial, String current) { }

    private static ControlStatus controlStatus(Reeca1Model controller) {
        double tolerance = 1.0e-8;
        return new ControlStatus(controller.isVoltageDip(),
                Math.abs(controller.getIpcmd()) >= controller.getActiveCurrentLimit() - tolerance,
                Math.abs(controller.getIqcmd()) >= controller.getReactiveCurrentLimit() - tolerance);
    }

    private static boolean reportControlTransitions(CaseFile source, double time,
            com.interpss.dstab.BaseDStabNetwork<?, ?> network,
            Map<String, ControlStatus> initialStatus, boolean dipTransition) {
        List<String> transitions = initialStatus.entrySet().stream().filter(entry -> {
            String[] key = entry.getKey().split(":", 2);
            DStabGen gen = (DStabGen) network.getBus(key[0]).getContributeGen(key[1]);
            Reeca1Model controller = ((Regca1Model) gen.getDynamicGenDevice())
                    .getReeca1Controller();
            ControlStatus current = controlStatus(controller);
            return dipTransition
                    ? !entry.getValue().voltageDip() && current.voltageDip()
                    : (!entry.getValue().ipLimited() && current.ipLimited())
                            || (!entry.getValue().iqLimited() && current.iqLimited());
        }).limit(10).map(Map.Entry::getKey).toList();
        if (transitions.isEmpty()) return false;
        System.out.printf(java.util.Locale.ROOT, "%s first %s transition at t=%.9g:%n",
                source.directory(), dipTransition ? "voltage-dip" : "current-limit", time);
        transitions.forEach(id -> {
            String[] key = id.split(":", 2);
            DStabGen gen = (DStabGen) network.getBus(key[0]).getContributeGen(key[1]);
            Regca1Model converter = (Regca1Model) gen.getDynamicGenDevice();
            Reeca1Model controller = converter.getReeca1Controller();
            System.out.printf(java.util.Locale.ROOT,
                    "  %s V=%.9g status=%s Ipcmd=%.9g/%.9g Iqcmd=%.9g/%.9g%n",
                    id, network.getBus(key[0]).getVoltageMag(), controlStatus(controller),
                    controller.getIpcmd(), controller.getActiveCurrentLimit(),
                    controller.getIqcmd(), controller.getReactiveCurrentLimit());
        });
        return true;
    }

    private record ControlStatus(boolean voltageDip, boolean ipLimited, boolean iqLimited) { }

    /** Diagnostic controller used to isolate REGCA1 from its electrical controls. */
    private static final class FixedCurrentController implements RenewableElectricalController {
        private double ipcmd;
        private double iqcmd;

        @Override public void initialize(double p, double q, double v) {
            ipcmd = p / Math.max(.01, Math.abs(v));
            iqcmd = -q / Math.max(.01, Math.abs(v));
        }
        @Override public void step(double dt, double p, double q, double v, double frequency) { }
        @Override public double getIpcmd() { return ipcmd; }
        @Override public double getIqcmd() { return iqcmd; }
        @Override public Repca1Model getPlantController() { return null; }
        @Override public void setPlantController(Repca1Model controller) { }
    }

    /** Diagnostic wrapper that freezes one REECA1 command at its initial value. */
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
        @Override public double getIpcmd() {
            return freezeActive ? initialIp : delegate.getIpcmd();
        }
        @Override public double getIqcmd() {
            return freezeReactive ? initialIq : delegate.getIqcmd();
        }
        @Override public Repca1Model getPlantController() { return delegate.getPlantController(); }
        @Override public void setPlantController(Repca1Model controller) {
            delegate.setPlantController(controller);
        }
    }

    private record WindSnapshot(double p, double pref, double torque,
            double filteredPower, double speedReference) { }

    private static RepcaSnapshot repcaSnapshot(Repca1Model controller) {
        return new RepcaSnapshot(controller.getPref(), controller.getQref(),
                controller.getMeasuredActivePower(),
                controller.getMeasuredReactiveOrVoltage(),
                controller.getActiveControlIntegral(),
                controller.getReactiveControlIntegral(), controller.getLeadLagState(),
                controller.getActiveLagState());
    }

    private record RepcaSnapshot(double pref, double qref, double pMeasured,
            double qvMeasured, double pIntegral, double qIntegral,
            double leadLagState, double pLagState) {
        double maxDifference(RepcaSnapshot other) {
            return java.util.stream.DoubleStream.of(
                    Math.abs(pref - other.pref), Math.abs(qref - other.qref),
                    Math.abs(pMeasured - other.pMeasured),
                    Math.abs(qvMeasured - other.qvMeasured),
                    Math.abs(pIntegral - other.pIntegral),
                    Math.abs(qIntegral - other.qIntegral),
                    Math.abs(leadLagState - other.leadLagState),
                    Math.abs(pLagState - other.pLagState)).max().orElse(0.0);
        }

        java.util.stream.Stream<StateDifference> stateDifferences(
                String model, String id, RepcaSnapshot current) {
            return java.util.stream.Stream.of(
                    stateDifference(model, id, "pref", pref, current.pref),
                    stateDifference(model, id, "qref", qref, current.qref),
                    stateDifference(model, id, "pMeasured", pMeasured, current.pMeasured),
                    stateDifference(model, id, "qvMeasured", qvMeasured, current.qvMeasured),
                    stateDifference(model, id, "pIntegral", pIntegral, current.pIntegral),
                    stateDifference(model, id, "qIntegral", qIntegral, current.qIntegral),
                    stateDifference(model, id, "leadLagState", leadLagState,
                            current.leadLagState),
                    stateDifference(model, id, "pLagState", pLagState, current.pLagState));
        }
    }

    private static ReecaSnapshot reecaSnapshot(Regca1Model converter, Reeca1Model controller) {
        return new ReecaSnapshot(controller.getMeasuredVoltage(),
                controller.getMeasuredActivePower(), controller.getActivePowerFilter(),
                controller.getActivePowerOrder(), controller.getReactiveCurrentState(),
                controller.getVoltageControlIntegral(), controller.getIpcmd(),
                controller.getIqcmd(), converter.getIpRegulatorState(),
                converter.getIqRegulatorState(), converter.getIq());
    }

    private record ReecaSnapshot(double vMeasured, double pMeasured, double pFilter,
            double pOrder, double qCurrent, double vIntegral, double ipcmd, double iqcmd,
            double ipState, double iqState, double iqOut) {
        double maxDifference(ReecaSnapshot other) {
            return java.util.stream.DoubleStream.of(
                    Math.abs(vMeasured - other.vMeasured),
                    Math.abs(pMeasured - other.pMeasured),
                    Math.abs(pFilter - other.pFilter),
                    Math.abs(pOrder - other.pOrder),
                    Math.abs(qCurrent - other.qCurrent),
                    Math.abs(vIntegral - other.vIntegral),
                    Math.abs(ipcmd - other.ipcmd), Math.abs(iqcmd - other.iqcmd),
                    Math.abs(ipState - other.ipState), Math.abs(iqState - other.iqState))
                    .max().orElse(0.0);
        }

        java.util.stream.Stream<StateDifference> stateDifferences(
                String model, String id, ReecaSnapshot current) {
            return java.util.stream.Stream.of(
                    stateDifference(model, id, "vMeasured", vMeasured, current.vMeasured),
                    stateDifference(model, id, "pMeasured", pMeasured, current.pMeasured),
                    stateDifference(model, id, "pFilter", pFilter, current.pFilter),
                    stateDifference(model, id, "pOrder", pOrder, current.pOrder),
                    stateDifference(model, id, "qCurrent", qCurrent, current.qCurrent),
                    stateDifference(model, id, "vIntegral", vIntegral, current.vIntegral),
                    stateDifference(model, id, "ipcmd", ipcmd, current.ipcmd),
                    stateDifference(model, id, "iqcmd", iqcmd, current.iqcmd),
                    stateDifference(model, id, "ipState", ipState, current.ipState),
                    stateDifference(model, id, "iqState", iqState, current.iqState),
                    stateDifference(model, id, "iqOut", iqOut, current.iqOut));
        }
    }

    private static StateDifference stateDifference(String model, String id, String state,
            double initial, double current) {
        return new StateDifference(model, id, state, Math.abs(current - initial), initial, current);
    }

    private record CaseFile(String directory, String raw, String dyr, String gnet) { }
}
