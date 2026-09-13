package org.interpss.core.dstab;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import org.interpss.IpssCorePlugin;
import org.interpss.dstab.renewable.Regca1Model;
import org.interpss.fadapter.psse.PSSEMultiFileLoader;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import com.interpss.core.algo.AclfMethodType;
import com.interpss.core.algo.LoadflowAlgorithm;
import com.interpss.core.acsc.fault.SimpleFaultCode;
import com.interpss.dstab.BaseDStabNetwork;
import com.interpss.dstab.DStabGen;
import com.interpss.dstab.DStabObjectFactory;
import com.interpss.dstab.algo.DynamicSimuAlgorithm;
import com.interpss.dstab.algo.DynamicSimuMethod;
import com.interpss.dstab.cache.StateMonitor;
import com.interpss.dstab.device.DynamicBusDevice;
import com.interpss.dstab.datatype.DStabSimuEvent;
import org.apache.commons.math3.complex.Complex;
import com.interpss.simu.SimuContext;

/** Full-stack initialization and no-event smoke gate for all Texas2k Series 24 cases. */
public class Texas2kSixCaseDynamicSmokeTest {
    private static final Path ROOT = relativeCaseRoot();
    private static final List<CaseFile> CASES = List.of(
            new CaseFile("Texas2k_series24_case1_2016summerpeak",
                    "Texas2k_series24_case1_2016summerPeak_v36.RAW", "dynamic_models_case1.dyr",
                    "dynamic_models_case1_gnet.idv", List.of("Bus1090")),
            new CaseFile("Texas2k_series24_case2_2016lowload",
                    "Texas2k_series24_case2_2016lowload.RAW", "dynamic_models_case2.dyr",
                    "dynamic_models_case2_gnet.idv", List.of("Bus1090")),
            new CaseFile("Texas2k_series24_case3_2024summerpeak",
                    "Texas2k_series24_case3_2024summerpeak_v30.RAW", "dynamic_models_case3.dyr",
                    "dynamic_models_case3_gnet.idv",
                    List.of("Bus1090", "Bus5394", "Bus5395", "Bus7095")),
            new CaseFile("Texas2k_series24_case4_2024lowload",
                    "Texas2k_series24_case4_2024lowload.RAW", "dynamic_models_case4.dyr",
                    "dynamic_models_case4_gnet.idv",
                    List.of("Bus1090", "Bus5394", "Bus5395", "Bus7095")),
            new CaseFile("Texas2k_series24_case5_2024highrenewables",
                    "Texas2k_series24_case5_2024highrenewables.RAW", "dynamic_models_case5.dyr",
                    "dynamic_models_case5_gnet.idv",
                    List.of("Bus1090", "Bus5394", "Bus5395", "Bus7095")),
            new CaseFile("Texas2k_series24_case6_2024lowloadwithgfm",
                    "Texas2k_series24_case6_2024lowloadwithgfm.RAW", "dynamic_models_case6.dyr",
                    "dynamic_models_case6_gnet.idv",
                    List.of("Bus1090", "Bus5394", "Bus5395", "Bus7095")));
    private static final Set<String> INTENTIONALLY_MODEL_REMOVED_GENERATORS = Set.of(
            "Bus5045:1", "Bus7099:2");

    @BeforeAll
    static void initializePlugin() {
        IpssCorePlugin.init();
    }

    private static Path relativeCaseRoot() {
        Path path = Path.of(System.getProperty("texas2k.case.root",
                Path.of("testData", "private", "texas2k").toString()));
        if (path.isAbsolute() || path.getRoot() != null) {
            throw new IllegalArgumentException("texas2k.case.root must be a relative path");
        }
        return path.normalize();
    }

    @Test
    void allSixCaseStacksInitializeAndHold() throws Exception {
        assumeTrue(Files.isDirectory(ROOT), "Missing private Texas2k root: " + ROOT);
        for (CaseFile source : CASES) verifyNoEvent(source);
    }

    @Test
    void allSixCasesRideThroughThreeCycleFault() throws Exception {
        assumeTrue(Files.isDirectory(ROOT), "Missing private Texas2k root: " + ROOT);
        for (CaseFile source : CASES) verifyFault(source);
    }

    private static void verifyFault(CaseFile source) throws Exception {
        SimuContext context = loadCase(source);
        BaseDStabNetwork<?, ?> network = context.getDStabilityNet();
        DynamicSimuAlgorithm algorithm = context.getDynSimuAlgorithm();
        configureSimulation(algorithm, .25);

        StateMonitor monitor = new StateMonitor();
        monitor.addBusStdMonitor(new String[] {"Bus7159", "Bus7186", "Bus7227"});
        monitor.addGeneratorStdMonitor(new String[] {"Bus1051-mach1", "Bus2056-mach1"});
        algorithm.setSimuOutputHandler(monitor);
        network.addDynamicEvent(DStabObjectFactory.createBusFaultEvent(
                "Bus7159", network, SimpleFaultCode.GROUND_3P, Complex.ZERO, null,
                .05, .05), "ThreeCycleFault@Bus7159");

        assertTrue(algorithm.initialization(), source.directory() + " fault initialization");
        assertTrue(algorithm.performSimulation(), source.directory() + " fault simulation");
        var faultVoltage = monitor.getBusVoltTable().get("Bus7159");
        assertTrue(faultVoltage.values().stream().allMatch(value -> Double.isFinite(value.value)),
                source.directory() + " finite fault-bus voltage");
        assertTrue(faultVoltage.values().stream().mapToDouble(value -> value.value).min()
                        .orElseThrow() < .1,
                source.directory() + " fault-bus voltage depression");
        assertTrue(faultVoltage.get(faultVoltage.size() - 1).value > .7,
                source.directory() + " fault-bus voltage recovery");
        for (String machineId : List.of("Bus1051-mach1", "Bus2056-mach1")) {
            var speed = monitor.getMachSpeedTable().get(machineId);
            assertTrue(speed.values().stream().allMatch(value -> Double.isFinite(value.value)),
                    source.directory() + " finite speed for " + machineId);
        }
    }

    private static void verifyNoEvent(CaseFile source) throws Exception {
        SimuContext context = loadCase(source);
        BaseDStabNetwork<?, ?> network = context.getDStabilityNet();
        DynamicSimuAlgorithm algorithm = context.getDynSimuAlgorithm();
        configureSimulation(algorithm, .1);
        Complex initial1051Voltage = network.getBus("Bus1051").getVoltage();
        Complex initial2056Voltage = network.getBus("Bus2056").getVoltage();
        double initial1051Speed = network.getMachine("Bus1051-mach1").getSpeed();
        double initial2056Speed = network.getMachine("Bus2056-mach1").getSpeed();
        StateMonitor monitor = new StateMonitor();
        algorithm.setSimuOutputHandler(monitor);

        List<String> outputStateFailures = diagnoseDynamicOutputStates(network, algorithm, monitor);
        assertTrue(outputStateFailures.isEmpty(), () -> source.directory()
                + " dynamic output-state failures: " + outputStateFailures);

        assertTrue(algorithm.initialization(), source.directory() + " initialization");
        assertTrue(algorithm.performSimulation(), source.directory() + " no-event simulation");
        assertTrue(network.getBus("Bus1051").getVoltage().subtract(initial1051Voltage).abs()
                        <= 2.0e-4,
                source.directory() + " Bus1051 voltage drift");
        assertTrue(network.getBus("Bus2056").getVoltage().subtract(initial2056Voltage).abs()
                        <= 2.0e-4,
                source.directory() + " Bus2056 voltage drift");
        assertTrue(Math.abs(network.getMachine("Bus1051-mach1").getSpeed() - initial1051Speed)
                        <= 2.0e-5,
                source.directory() + " Bus1051 speed drift");
        assertTrue(Math.abs(network.getMachine("Bus2056-mach1").getSpeed() - initial2056Speed)
                        <= 2.0e-5,
                source.directory() + " Bus2056 speed drift");
    }

    private static SimuContext loadCase(CaseFile source) throws Exception {
        Path directory = ROOT.resolve(source.directory());
        Path raw = directory.resolve(source.raw());
        Path dyr = directory.resolve(source.dyr());
        Path gnet = directory.resolve(source.gnet());
        assumeTrue(Files.isRegularFile(raw), "Missing Texas2k RAW: " + raw);
        assumeTrue(Files.isRegularFile(dyr), "Missing Texas2k DYR: " + dyr);
        assumeTrue(Files.isRegularFile(gnet), "Missing Texas2k GNET IDV: " + gnet);

        // Deliberately omit IDV arguments: the PSS/E loader must discover both
        // sibling GNET and MODREMOVE files, but not the sibling PSLF DYD.
        PSSEMultiFileLoader loader = new PSSEMultiFileLoader();
        SimuContext context = loader.loadDStab(raw.toString(), dyr.toString());
        BaseDStabNetwork<?, ?> network = context.getDStabilityNet();
        for (String busId : source.gnetBuses()) {
            var bus = network.getBus(busId);
            assertTrue(bus.getContributeGenList().stream().noneMatch(gen -> gen.isActive()),
                    source.directory() + " active generator remains after GNET at " + busId);
            assertTrue(bus.getContributeLoadList().stream()
                            .anyMatch(load -> load.isActive() && load.getId().startsWith("GNET-")),
                    source.directory() + " missing GNET negative load at " + busId);
        }
        network.setBypassDataCheck(true);
        network.setAllowGenWithoutMach(true);

        DynamicSimuAlgorithm algorithm = context.getDynSimuAlgorithm();
        LoadflowAlgorithm loadflow = algorithm.getAclfAlgorithm();
        loadflow.getDataCheckConfig().setAutoTurnLine2Xfr(true);
        loadflow.getDataCheckConfig().setTurnOffIslandBus(true);
        loadflow.setNonDivergent(true);
        loadflow.setMaxIterations(50);
        loadflow.setTolerance(1.0e-8);
        assertTrue(loadflow.loadflow(), () -> source.directory() + " load flow: "
                + network.maxMismatch(AclfMethodType.NR));

        network.setBypassDataCheck(false);
        network.checkData(loadflow.getDataCheckConfig());
        List<String> initializationFailures = diagnoseGeneratorInitialization(network);
        assertTrue(initializationFailures.isEmpty(), () -> source.directory()
                + " generator initialization failures: " + initializationFailures);
        assertTrue(network.initDStabNet(), source.directory() + " network initialization call");
        assertTrue(network.isDStabNetInitialized(),
                source.directory() + " network did not retain initialized state");
        assertRegcaInitialPowerBases(source, network);
        return context;
    }

    private static void assertRegcaInitialPowerBases(CaseFile source,
            BaseDStabNetwork<?, ?> network) {
        List<String> failures = new ArrayList<>();
        double systemBase = network.getBaseMva();
        network.getBusList().forEach(bus -> bus.getContributeGenList().stream()
                .filter(DStabGen.class::isInstance)
                .map(DStabGen.class::cast)
                .filter(DStabGen::isActive)
                .filter(gen -> gen.getDynamicGenDevice() instanceof Regca1Model)
                .forEach(gen -> {
                    Regca1Model converter = (Regca1Model) gen.getDynamicGenDevice();
                    double deviceBase = gen.getMvaBase() > 1.0e-9
                            ? gen.getMvaBase() : systemBase;
                    double deviceScale = systemBase / deviceBase;
                    Complex sourcePower = gen.getGen();
                    double feedbackP = ((Number) converter.getStates(null)
                            .get("REGCA1_P")).doubleValue();
                    double feedbackQ = ((Number) converter.getStates(null)
                            .get("REGCA1_Q")).doubleValue();
                    double feedbackError = Math.max(
                            Math.abs(feedbackP - sourcePower.getReal() * deviceScale),
                            Math.abs(feedbackQ - sourcePower.getImaginary() * deviceScale));

                    Complex voltage = bus.getVoltage();
                    Complex injected = (Complex) converter.getOutputObject();
                    Complex z = gen.getPosGenZ();
                    if (z != null) {
                        z = z.multiply(gen.getZMultiFactor());
                        if (z.abs() > 1.0e-9) injected = injected.subtract(voltage.divide(z));
                    }
                    Complex reconstructed = voltage.multiply(injected.conjugate());
                    double injectionError = Math.max(
                            Math.abs(reconstructed.getReal() - sourcePower.getReal()),
                            Math.abs(reconstructed.getImaginary() - sourcePower.getImaginary()));
                    if (feedbackError > 1.0e-8 || injectionError > 1.0e-8) {
                        failures.add(bus.getId() + ":" + gen.getId()
                                + " feedbackError=" + feedbackError
                                + " injectionError=" + injectionError
                                + " systemBase=" + systemBase
                                + " deviceBase=" + deviceBase);
                    }
                }));
        assertTrue(failures.isEmpty(), () -> source.directory()
                + " REGCA1 initial P/Q base mismatches: " + failures);
    }

    private static void configureSimulation(DynamicSimuAlgorithm algorithm, double endTime) {
        algorithm.setSimuMethod(DynamicSimuMethod.MODIFIED_EULER);
        algorithm.setSimuStepSec(1.0 / 240.0);
        algorithm.setTotalSimuTimeSec(endTime);
        algorithm.setOutPutPerSteps(1);
    }

    private static List<String> diagnoseGeneratorInitialization(BaseDStabNetwork<?, ?> network) {
        List<String> failures = new ArrayList<>();
        network.getBusList().forEach(bus -> bus.getContributeGenList().stream()
                .filter(DStabGen.class::isInstance)
                .map(DStabGen.class::cast)
                .filter(DStabGen::isActive)
                .forEach(gen -> {
                    try {
                        if (gen.getDynamicGenDevice() == null) {
                            String key = bus.getId() + ":" + gen.getId();
                            if (!INTENTIONALLY_MODEL_REMOVED_GENERATORS.contains(key)) {
                                failures.add(key + "=missing-device");
                            }
                        } else if (!gen.getDynamicGenDevice().initStates(bus)) {
                            failures.add(bus.getId() + ":" + gen.getId() + "="
                                    + gen.getDynamicGenDevice().getClass().getSimpleName());
                        }
                    } catch (RuntimeException ex) {
                        failures.add(bus.getId() + ":" + gen.getId() + "="
                                + gen.getDynamicGenDevice().getClass().getSimpleName()
                                + "(" + ex.getClass().getSimpleName() + ":" + ex.getMessage() + ")");
                    }
                }));
        return failures;
    }

    private static List<String> diagnoseDynamicOutputStates(BaseDStabNetwork<?, ?> network,
            DynamicSimuAlgorithm algorithm, StateMonitor monitor) {
        List<String> failures = new ArrayList<>();
        network.getBusList().forEach(bus -> {
            bus.getContributeGenList().stream()
                    .filter(DStabGen.class::isInstance)
                    .map(DStabGen.class::cast)
                    .filter(gen -> gen.getMach() == null)
                    .filter(gen -> gen.getDynamicGenDevice() instanceof DynamicBusDevice)
                    .map(gen -> (DynamicBusDevice) gen.getDynamicGenDevice())
                    .filter(DynamicBusDevice::isActive)
                    .forEach(device -> buildDynamicState(
                            device, bus.getId(), algorithm, monitor, failures));
            bus.getDynamicBusDeviceList().stream()
                    .filter(DynamicBusDevice::isActive)
                    .forEach(device -> buildDynamicState(
                            device, bus.getId(), algorithm, monitor, failures));
        });
        return failures;
    }

    private static void buildDynamicState(DynamicBusDevice device, String busId,
            DynamicSimuAlgorithm algorithm, StateMonitor monitor, List<String> failures) {
        try {
            var states = com.interpss.dstab.funcImpl.DStabFunction.BuiltDynamicBusDeviceState
                    .f(device, 0.0, algorithm);
            if (!monitor.onSimuEvent(new DStabSimuEvent(
                    DStabSimuEvent.PlotStepDynamicBusDeviceStates, states))) {
                failures.add(busId + ":" + device.getId() + "="
                        + device.getClass().getSimpleName() + "(output-handler-rejected)");
            }
        } catch (Exception ex) {
            failures.add(busId + ":" + device.getId() + "="
                    + device.getClass().getSimpleName() + "("
                    + ex.getClass().getSimpleName() + ":" + ex.getMessage() + ")");
        }
    }

    private record CaseFile(String directory, String raw, String dyr, String gnet,
            List<String> gnetBuses) { }
}
