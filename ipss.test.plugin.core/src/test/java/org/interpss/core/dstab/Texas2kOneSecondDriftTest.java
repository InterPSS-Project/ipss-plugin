package org.interpss.core.dstab;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import org.apache.commons.math3.complex.Complex;
import org.interpss.IpssCorePlugin;
import org.interpss.dstab.renewable.Reeca1Model;
import org.interpss.dstab.renewable.Regca1Model;
import org.interpss.dstab.renewable.RenewableElectricalController;
import org.interpss.dstab.renewable.Repca1Model;
import org.interpss.fadapter.psse.PSSEMultiFileLoader;
import org.interpss.fadapter.psse.dyr.PsseDyrRecordReader;
import org.interpss.numeric.sparse.ISparseEqnComplex;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

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
    private static final Path ROOT = Path.of(System.getProperty("texas2k.case.root",
            Path.of(System.getProperty("user.home"), "OneDrive", "Documents", "qiuhua",
                    "private_cases", "Texas2k_series24_cases_with_dynamics",
                    "Texas2k_series24_cases_with_dynamics").toString()));
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
    void case5ReportsPassiveDrivingPointImpedances() throws Exception {
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

        for (String busId : List.of("Bus1083", "Bus1011", "Bus1046", "Bus3051")) {
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

        List<String> interactingBuses = List.of(
                "Bus1004", "Bus1006", "Bus1007", "Bus1009", "Bus1011", "Bus1016",
                "Bus1021", "Bus1022", "Bus1023", "Bus1026", "Bus1027", "Bus1028",
                "Bus1029", "Bus1030", "Bus1032", "Bus1033", "Bus1035", "Bus1036",
                "Bus1037", "Bus1039", "Bus1040", "Bus1042", "Bus1043", "Bus1044",
                "Bus1046", "Bus1055", "Bus1057", "Bus1061", "Bus1062", "Bus1063",
                "Bus1083");
        y.setB2Zero();
        Complex perBusReactiveCurrent = new Complex(0.0, -1.0 / interactingBuses.size());
        interactingBuses.forEach(busId -> y.setBi(perBusReactiveCurrent,
                network.getBus(busId).getSortNumber()));
        y.solveEqn();
        Difference commonMode = interactingBuses.stream().map(busId -> {
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
            effectiveDyr = Files.createTempFile("texas2k-conventional-", ".dyr");
            boolean onlySelectedRenewables = Boolean.getBoolean(
                    "texas2k.drift.onlySelectedRenewables");
            String filtered = sourceRecords.stream()
                    .filter(record -> (selectedRenewables.contains(record.canonicalModelName())
                                    && ((selectedRenewableBuses.isEmpty()
                                                    && selectedRenewableDevices.isEmpty())
                                            || selectedRenewableBuses.contains(record.busNumber())
                                            || selectedRenewableDevices.contains(deviceKey(record)))
                                    && (selectedQFlag.isEmpty()
                                            || selectedQFlagDevices.contains(deviceKey(record))))
                            || (!onlySelectedRenewables
                                    && !RENEWABLE_MODELS.contains(record.canonicalModelName())))
                    .map(record -> record.rawText() + " /")
                    .collect(Collectors.joining(System.lineSeparator()));
            Files.writeString(effectiveDyr, filtered);
            System.out.println("Filtered DYR: " + effectiveDyr);
        }
        var context = new PSSEMultiFileLoader().loadDStab(
                raw.toString(), effectiveDyr.toString(), gnet.toString());
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
        PsseDyrRecordReader.read(dyr).stream()
                .filter(record -> record.canonicalModelName().equals("GENROU")
                        || record.canonicalModelName().equals("GENSAL"))
                .forEach(record -> {
                    String id = "Bus" + record.busNumber() + "-mach" + record.deviceId();
                    if (network.getMachine(id) != null) {
                        initialSpeed.put(id, network.getMachine(id).getSpeed());
                    }
                });
        Map<String, WindSnapshot> initialWind = new LinkedHashMap<>();
        Map<String, ReecaSnapshot> initialReeca = new LinkedHashMap<>();
        Map<String, RepcaSnapshot> initialRepca = new LinkedHashMap<>();
        Map<String, ControlStatus> initialControlStatus = new LinkedHashMap<>();
        network.getBusList().forEach(bus -> bus.getContributeGenList().stream()
                .filter(DStabGen.class::isInstance)
                .map(DStabGen.class::cast)
                .filter(DStabGen::isActive)
                .filter(gen -> gen.getDynamicGenDevice() instanceof Regca1Model)
                .forEach(gen -> {
                    Regca1Model converter = (Regca1Model) gen.getDynamicGenDevice();
                    var controller = converter.getReeca1Controller();
                    if (controller != null) {
                        String deviceId = bus.getId() + ":" + gen.getId();
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
            boolean dipTransitionReported = false;
            boolean limitTransitionReported = false;
            double endTime = algorithm.getTotalSimuTimeSec();
            while (algorithm.getSimuTime() < endTime - 0.5 * algorithm.getSimuStepSec()) {
                assertTrue(algorithm.solveDEqnStep(true), source.directory() + " simulation step");
                if (!reported) {
                    Difference stepVoltage = worstVoltageDifference(network, initialVoltage);
                    Difference stepSpeed = worstSpeedDifference(network, initialSpeed);
                    if (stepVoltage.value() > VOLTAGE_DRIFT_LIMIT
                            || stepSpeed.value() > SPEED_DRIFT_LIMIT) {
                        reportFirstDivergence(source, algorithm.getSimuTime(), stepVoltage,
                                stepSpeed, network, initialReeca, initialRepca);
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

    private record Difference(String id, double value) { }

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

    private static void reportFirstDivergence(CaseFile source, double time,
            Difference voltage, Difference speed,
            com.interpss.dstab.BaseDStabNetwork<?, ?> network,
            Map<String, ReecaSnapshot> initialReeca,
            Map<String, RepcaSnapshot> initialRepca) {
        System.out.printf(java.util.Locale.ROOT,
                "%s first drift threshold at t=%.9g: voltage=%.9g@%s speed=%.9g@%s%n",
                source.directory(), time, voltage.value(), voltage.id(), speed.value(), speed.id());
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
    }

    private record CaseFile(String directory, String raw, String dyr, String gnet) { }
}
