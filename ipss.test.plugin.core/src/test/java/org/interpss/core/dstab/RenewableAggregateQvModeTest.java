package org.interpss.core.dstab;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.apache.commons.math3.complex.Complex;
import org.apache.commons.math3.linear.Array2DRowRealMatrix;
import org.apache.commons.math3.linear.EigenDecomposition;
import org.interpss.CorePluginTestSetup;
import org.interpss.dstab.renewable.Reeca1Data;
import org.interpss.dstab.renewable.Reeca1Model;
import org.interpss.dstab.renewable.Regca1Data;
import org.interpss.dstab.renewable.Regca1Model;
import org.interpss.dstab.renewable.Repca1Data;
import org.interpss.dstab.renewable.Repca1Model;
import org.interpss.fadapter.builder.AclfNetworkBuilder;
import org.interpss.fadapter.builder.DStabNetworkBuilder;
import org.interpss.fadapter.psse.PSSEMultiFileLoader;
import org.interpss.numeric.sparse.ISparseEqnComplex;
import org.junit.jupiter.api.Test;

import com.interpss.core.net.OriginalDataFormat;
import com.interpss.dstab.BaseDStabNetwork;
import com.interpss.dstab.DStabObjectFactory;
import com.interpss.dstab.DStabGen;
import com.interpss.dstab.DStabilityNetwork;
import com.interpss.dstab.algo.DynamicSimuAlgorithm;
import com.interpss.dstab.algo.DynamicSimuMethod;
import com.interpss.dstab.cache.StateMonitor;
import com.interpss.core.acsc.fault.SimpleFaultCode;

/** Public reduced-network reproducer for an aggregate REGCA1/REECA1/REPCA1 Q/V mode. */
public class RenewableAggregateQvModeTest extends CorePluginTestSetup {
    private static final double STEP = 1.0 / 240.0;
    private static final double CASE5_MODE_GROWTH = .8512333985675469;
    private static final double CASE5_MODE_FREQUENCY = 3.8082198136353123;

    @Test
    void aggregateProfileIsStationaryAndRecoversFromThreeCyclePoiFault() throws Exception {
        RunResult aggregate = run(30, false, .20, STEP);
        RunResult faulted = run(30, true, .20, STEP);

        System.out.printf(java.util.Locale.ROOT,
                "Renewable aggregate Q/V mode: thirtyPlants=%.9g faultMin=%.9g "
                        + "faultFinal=%.9g qvSensitivity=%.9g%n",
                aggregate.maximumVoltageDrift(),
                faulted.minimumPoiVoltage(), faulted.finalPoiVoltage(),
                aggregate.commonModeQvSensitivity());
        assertTrue(aggregate.maximumVoltageDrift() < 1.0e-6,
                "aggregate flat-run drift " + aggregate.maximumVoltageDrift());
        assertTrue(faulted.minimumPoiVoltage() < .2,
                "three-cycle POI fault must depress voltage");
        assertTrue(faulted.finalPoiVoltage() > .9,
                "aggregate renewable voltage must recover after clearing");
    }

    @Test
    void weakGridProfileExposesNoEventGrowthForReferenceComparison() throws Exception {
        RunResult strongerGrid = run(30, false, .8, STEP);
        RunResult weakGrid = run(30, false, 1.2, STEP);
        RunResult weakGridFineStep = run(30, false, 1.2, 1.0 / 960.0);

        System.out.printf(java.util.Locale.ROOT,
                "Renewable aggregate Q/V grid strength: x=.8 drift=%.9g sens=%.9g "
                        + "x=1.2 drift=%.9g sens=%.9g x=1.2/fine drift=%.9g%n",
                strongerGrid.maximumVoltageDrift(), strongerGrid.commonModeQvSensitivity(),
                weakGrid.maximumVoltageDrift(), weakGrid.commonModeQvSensitivity(),
                weakGridFineStep.maximumVoltageDrift());
        assertTrue(strongerGrid.maximumVoltageDrift() < 1.0e-6,
                "stronger-grid flat-run drift " + strongerGrid.maximumVoltageDrift());
        // This is a diagnostic roundoff-seeded mode, so its absolute endpoint
        // depends on the sparse-solver/JVM execution order. The invariant is the
        // weak-grid amplification and persistence when the step is reduced, not
        // which side of an arbitrary 1e-5 endpoint one particular run lands on.
        assertTrue(weakGrid.maximumVoltageDrift()
                        > 10.0 * strongerGrid.maximumVoltageDrift(),
                "weak-grid mode did not amplify the stronger-grid trajectory");
        assertTrue(weakGridFineStep.maximumVoltageDrift()
                        > weakGrid.maximumVoltageDrift(),
                "weak-grid mode disappeared at the finer step");
    }

    @Test
    void texas1062Unit2ProfileHasPublicThreeCycleFaultReproducer() throws Exception {
        PlantProfile profile = texas1062Unit2Profile();
        RunResult productionStep = run(1, true, .05, STEP, profile, 4.0);
        RunResult fineStep = run(1, true, .05, 1.0 / 960.0, profile, 4.0);

        System.out.printf(java.util.Locale.ROOT,
                "Bus-1062-unit-2 public fault reproducer: drift=%.9g final=%.9g "
                        + "fineDrift=%.9g fineFinal=%.9g sensitivity=%.9g%n",
                productionStep.maximumVoltageDrift(), productionStep.finalPoiVoltage(),
                fineStep.maximumVoltageDrift(), fineStep.finalPoiVoltage(),
                productionStep.commonModeQvSensitivity());
        assertTrue(productionStep.maximumVoltageDrift() > 1.0e-4,
                "Bus-1062 controller mode was not excited at the production step");
        assertTrue(fineStep.maximumVoltageDrift() > 1.0e-4,
                "Bus-1062 controller mode disappeared at the finer step");
    }

    @Test
    void texas1062Unit2PublicLinearizationHasGrowingOscillatoryMode() throws Exception {
        PlantProfile profile = texas1062Unit2Profile();
        RunResult operatingPoint = run(1, false, .05, STEP, profile, .05);
        LinearMode mode = singleDeviceLinearMode(operatingPoint.commonModeQvSensitivity(),
                operatingPoint.plantVoltage(), operatingPoint.plantReactiveCurrent(),
                profile);

        System.out.printf(java.util.Locale.ROOT,
                "Bus-1062-unit-2 public Q/V linearization: sensitivity=%.9g "
                        + "eigenvalue=%.9g%+.9gj 1/s states=%s%n",
                operatingPoint.commonModeQvSensitivity(), mode.real(), mode.imaginary(),
                mode.participation());
        assertTrue(Math.abs(mode.real() - CASE5_MODE_GROWTH) / CASE5_MODE_GROWTH < .05,
                "public Bus-1062 growth rate does not reproduce the Case-5 mode");
        assertTrue(Math.abs(Math.abs(mode.imaginary()) - CASE5_MODE_FREQUENCY)
                        / CASE5_MODE_FREQUENCY < .02,
                "public Bus-1062 frequency does not reproduce the Case-5 mode");
        assertTrue(mode.participation().get(0).state().equals("REECA_V_PI"),
                "public mode must be led by the Bus-1062 REECA voltage PI");
        assertTrue(mode.participation().get(1).state().equals("REGCA_IQ"),
                "public mode must retain the Case-5 REGCA reactive-current component");
    }

    private static LinearMode singleDeviceLinearMode(double coupling, double v0,
            double iq0, PlantProfile profile) {
        Reeca1Data reeca = profile.reeca();
        Repca1Data repca = profile.repca();
        double tg = regcaData().tg();
        double leadRatio = repca.tft() / repca.tfv();
        double[][] state = new double[6][6];
        state[0][0] = -1.0 / repca.tfltr();
        state[1][0] = -repca.ki();
        state[2][0] = -repca.kp() / repca.tfv();
        state[2][1] = 1.0 / repca.tfv();
        state[2][2] = -1.0 / repca.tfv();

        double[] qError = new double[6];
        qError[0] = -leadRatio * repca.kp();
        qError[1] = leadRatio;
        qError[2] = 1.0 - leadRatio;
        qError[5] = -(iq0 * coupling + v0);
        state[0][5] = coupling / repca.tfltr();
        for (int column = 0; column < state.length; column++) {
            state[3][column] += reeca.kqi() * qError[column];
            state[4][column] += reeca.kvi() * reeca.kqp() * qError[column];
            state[5][column] += reeca.kvp() * reeca.kqp() * qError[column] / tg;
        }
        state[4][3] += reeca.kvi();
        state[5][3] += reeca.kvp() / tg;
        state[5][4] += 1.0 / tg;
        state[5][5] -= 1.0 / tg;

        EigenDecomposition decomposition = new EigenDecomposition(
                new Array2DRowRealMatrix(state, false));
        int dominant = java.util.stream.IntStream.range(0, state.length).boxed()
                .max(java.util.Comparator.comparingDouble(
                        decomposition::getRealEigenvalue)).orElseThrow();
        double[] real = decomposition.getEigenvector(dominant).toArray();
        double[] magnitude = java.util.Arrays.stream(real).map(Math::abs).toArray();
        double imaginary = decomposition.getImagEigenvalue(dominant);
        if (Math.abs(imaginary) > 1.0e-9) {
            int conjugate = java.util.stream.IntStream.range(0, state.length)
                    .filter(index -> index != dominant)
                    .filter(index -> Math.abs(decomposition.getRealEigenvalue(index)
                                    - decomposition.getRealEigenvalue(dominant)) < 1.0e-8
                            && Math.abs(decomposition.getImagEigenvalue(index) + imaginary)
                                    < 1.0e-8)
                    .findFirst().orElseThrow();
            double[] quadrature = decomposition.getEigenvector(conjugate).toArray();
            for (int index = 0; index < magnitude.length; index++) {
                magnitude[index] = Math.hypot(real[index], quadrature[index]);
            }
        }
        double maximum = java.util.Arrays.stream(magnitude).max().orElseThrow();
        List<StateParticipation> participation = java.util.stream.IntStream
                .range(0, magnitude.length)
                .mapToObj(index -> new StateParticipation(switch (index) {
                    case 0 -> "REPCA_VFILT";
                    case 1 -> "REPCA_Q_PI";
                    case 2 -> "REPCA_LEAD_LAG";
                    case 3 -> "REECA_Q_PI";
                    case 4 -> "REECA_V_PI";
                    default -> "REGCA_IQ";
                }, magnitude[index] / maximum))
                .sorted(java.util.Comparator.comparingDouble(StateParticipation::magnitude)
                        .reversed())
                .toList();
        return new LinearMode(decomposition.getRealEigenvalue(dominant),
                imaginary, participation);
    }

    @Test
    void publicPsseFixtureRunsMatchedAndesFault() throws Exception {
        Path directory = Path.of("testData", "adpter", "psse", "v33", "renewable");
        var context = new PSSEMultiFileLoader().loadDStab(
                directory.resolve("regca_reeca_repca_bus1062.raw").toString(),
                directory.resolve("regca_reeca_repca_bus1062.dyr").toString());
        var network = context.getDStabilityNet();
        var algorithm = context.getDynSimuAlgorithm();
        network.setBypassDataCheck(true);
        network.setAllowGenWithoutMach(true);
        assertTrue(algorithm.getAclfAlgorithm().loadflow(), "public PSS/E fixture load flow");
        algorithm.setSimuMethod(DynamicSimuMethod.MODIFIED_EULER);
        double simulationStep = Double.parseDouble(System.getProperty(
                "renewable.bus1062.step", Double.toString(STEP)));
        algorithm.setSimuStepSec(simulationStep);
        algorithm.setTotalSimuTimeSec(4.0);
        algorithm.setOutPutPerSteps(1);
        StateMonitor monitor = new StateMonitor();
        monitor.addBusStdMonitor(new String[] {"Bus1", "Bus2"});
        algorithm.setSimuOutputHandler(monitor);
        network.addDynamicEvent(DStabObjectFactory.createBusFaultEvent(
                "Bus2", network, SimpleFaultCode.GROUND_3P,
                new Complex(0, .05), null, .05, .05), "ThreeCycleFault@Poi");
        assertTrue(algorithm.initialization(), "public PSS/E fixture initialization");
        Regca1Model converter = (Regca1Model) ((DStabGen) network.getBus("Bus1")
                .getContributeGen("1")).getDynamicGenDevice();
        List<double[]> internalTrace = new ArrayList<>();
        recordInternalTrace(internalTrace, algorithm.getSimuTime(), network, converter);
        while (algorithm.getSimuTime() <= algorithm.getTotalSimuTimeSec()) {
            assertTrue(algorithm.solveDEqnStep(true),
                    "public PSS/E fixture failed at t=" + algorithm.getSimuTime());
            recordInternalTrace(internalTrace, algorithm.getSimuTime(), network, converter);
        }

        double plantMin = minimum(monitor, "Bus1");
        double plantFinal = last(monitor, "Bus1");
        double poiMin = minimum(monitor, "Bus2");
        double poiFinal = last(monitor, "Bus2");
        System.out.printf(java.util.Locale.ROOT,
                "InterPSS Bus-1062 public benchmark: plantMin=%.9g plantFinal=%.9g "
                        + "poiMin=%.9g poiFinal=%.9g%n",
                plantMin, plantFinal, poiMin, poiFinal);
        String outputVariant = System.getProperty(
                "renewable.bus1062.output", "bus1062-public");
        Path output = Path.of("target", "andes-benchmarks", outputVariant,
                "interpss.csv");
        Files.createDirectories(output.getParent());
        writeBusTrace(output, monitor, "Bus1", "Bus2");
        writeInternalTrace(output.resolveSibling("interpss-internal.csv"), internalTrace);
        assertTrue(plantMin < .7 && poiMin < .7, "fault must depress fixture voltages");
        assertTrue(plantFinal > .9 && poiFinal > .9, "fixture must retain finite recovery");
    }

    private static void recordInternalTrace(List<double[]> trace, double time,
            BaseDStabNetwork<?, ?> network, Regca1Model converter) {
        Reeca1Model reeca = converter.getReeca1Controller();
        Repca1Model repca = reeca.getPlantController();
        trace.add(new double[] {
                time,
                network.getBus("Bus1").getVoltageMag(),
                network.getBus("Bus2").getVoltageMag(),
                converter.getFilteredVoltage(),
                converter.getIpRegulatorState(),
                converter.getIqRegulatorState(),
                converter.getIp(),
                converter.getIq(),
                reeca.getMeasuredVoltage(),
                reeca.getMeasuredActivePower(),
                reeca.getReactivePowerTarget(),
                reeca.getReactiveControlError(),
                reeca.getReactiveControlIntegral(),
                reeca.getReactiveControlPreLimitOutput(),
                reeca.getReactiveControlOutput(),
                reeca.getVoltageControlError(),
                reeca.getVoltageControlIntegral(),
                reeca.getVoltageControlPreLimitOutput(),
                reeca.getVoltageControlOutput(),
                reeca.getPreliminaryReactiveCurrentLimit(),
                reeca.getReactiveCurrentInjection(),
                reeca.getActiveCurrentLimit(),
                reeca.getReactiveCurrentLimit(),
                reeca.isVoltageDip() ? 1.0 : 0.0,
                reeca.getActivePowerFilter(),
                reeca.getActivePowerOrder(),
                reeca.getIpcmd(),
                reeca.getIqcmd(),
                repca.getMeasuredReactiveOrVoltage(),
                repca.getReactiveReference(),
                repca.getReactiveControlRawError(),
                repca.getReactiveControlDeadbandOutput(),
                repca.getReactiveControlLimitedError(),
                repca.getReactiveControlPreLimitOutput(),
                repca.getReactiveControlIntegral(),
                repca.getReactiveControlOutput(),
                repca.getLeadLagState(),
                repca.getQref()
        });
    }

    private static void writeInternalTrace(Path path, List<double[]> rows) throws Exception {
        String[] headings = {
                "time_s", "Bus1", "Bus2", "REGCA_VF", "REGCA_IP_STATE",
                "REGCA_IQ_STATE", "REGCA_IP", "REGCA_IQ", "REECA_VMEAS",
                "REECA_PMEAS", "REECA_Q_TARGET", "REECA_Q_ERROR",
                "REECA_Q_PI_XI", "REECA_Q_PI_PRELIMIT", "REECA_Q_PI_Y",
                "REECA_V_ERROR", "REECA_V_PI_XI", "REECA_V_PI_PRELIMIT",
                "REECA_V_PI_Y", "REECA_IQ_PRELIM_MAX", "REECA_IQ_INJECTION",
                "REECA_IP_MAX", "REECA_IQ_MAX", "REECA_VOLTAGE_DIP", "REECA_PFILT",
                "REECA_PORD", "REECA_IPCMD", "REECA_IQCMD", "REPCA_QV_MEAS",
                "REPCA_QV_REF", "REPCA_ERROR_RAW", "REPCA_ERROR_DB",
                "REPCA_ERROR_LIMITED", "REPCA_PI_PRELIMIT",
                "REPCA_Q_PI_XI", "REPCA_Q_PI_Y", "REPCA_LEAD_LAG", "REPCA_QEXT"
        };
        StringBuilder csv = new StringBuilder(String.join(",", headings)).append('\n');
        for (double[] row : rows) {
            for (int index = 0; index < row.length; index++) {
                if (index > 0) csv.append(',');
                csv.append(String.format(java.util.Locale.ROOT, "%.12g", row[index]));
            }
            csv.append('\n');
        }
        Files.writeString(path, csv, StandardCharsets.UTF_8);
    }

    private static double minimum(StateMonitor monitor, String busId) {
        return monitor.getBusVoltTable().get(busId).values().stream()
                .mapToDouble(value -> value.value).min().orElseThrow();
    }

    private static double last(StateMonitor monitor, String busId) {
        var values = monitor.getBusVoltTable().get(busId);
        return values.get(values.size() - 1).value;
    }

    private static void writeBusTrace(Path path, StateMonitor monitor, String... buses)
            throws Exception {
        StringBuilder csv = new StringBuilder("time_s");
        for (String bus : buses) csv.append(',').append(bus);
        csv.append('\n');
        int samples = monitor.getBusVoltTable().get(buses[0]).size();
        for (int index = 0; index < samples; index++) {
            var time = monitor.getBusVoltTable().get(buses[0]).get(index);
            csv.append(String.format(java.util.Locale.ROOT, "%.12g", time.t));
            for (String bus : buses) {
                csv.append(',').append(String.format(java.util.Locale.ROOT, "%.12g",
                        monitor.getBusVoltTable().get(bus).get(index).value));
            }
            csv.append('\n');
        }
        Files.writeString(path, csv, StandardCharsets.UTF_8);
    }

    private static RunResult run(int plantCount, boolean withFault, double gridReactance,
            double simulationStep) throws Exception {
        return run(plantCount, withFault, gridReactance, simulationStep,
                new PlantProfile(.01, 0.0, .10, reecaData(), repcaData()),
                withFault ? .5 : 1.0);
    }

    private static RunResult run(int plantCount, boolean withFault, double gridReactance,
            double simulationStep, PlantProfile profile, double endTime) throws Exception {
        DStabilityNetwork network = DStabObjectFactory.createDStabilityNetwork();
        network.setBaseKva(100000.0);
        AclfNetworkBuilder topology = new AclfNetworkBuilder(network);
        topology.setNetworkInfo("renewable-qv-mode", "renewable-qv-mode", 100000.0,
                OriginalDataFormat.PSSE);
        topology.addBus("Grid", "Infinite grid", 1L, 230000.0, 1.0, 0.0,
                null, null, null);
        topology.setSwingBus("Grid", 1.0, 0.0);
        topology.addContributeGen("Grid", "1", true, 0, 0, 100, 1.0,
                0, 0, 0, 0, new Complex(0, .01), null, 0, null, 0, 0);
        topology.addBus("Poi", "Common POI", 2L, 230000.0, 1.0, 0.0,
                null, null, null);
        topology.setNonGenBus("Poi");
        topology.addLine("Poi", "Grid", "1", new Complex(.01, gridReactance), Complex.ZERO,
                null, null, 0, 0, 0, true);

        for (int i = 1; i <= plantCount; i++) {
            String bus = "Plant" + i;
            topology.addBus(bus, bus, 100L + i, 230000.0, 1.0, 0.0,
                    null, null, null);
            topology.setPQBus(bus, profile.p(), profile.q(), 0.0, 0.0);
            topology.addContributeGen(bus, "1", true, profile.p(), profile.q(), 100.0, 1.0,
                    1.0, -1.0, 1.0, 0.0, new Complex(0, .10), null,
                    0.0, null, 0.0, 0.0);
            topology.addLine(bus, "Poi", "1", new Complex(.005, profile.collectorX()), Complex.ZERO,
                    null, null, 0, 0, 0, true);
        }

        DStabNetworkBuilder dynamics = new DStabNetworkBuilder(network);
        dynamics.addInfiniteMachine("Grid", "1");
        for (int i = 1; i <= plantCount; i++) {
            String bus = "Plant" + i;
            dynamics.addRegca1(bus, "1", regcaData());
            dynamics.addReeca1(bus, "1", profile.reeca());
            dynamics.addRepca1(bus, "1", profile.repca());
        }

        DynamicSimuAlgorithm algorithm = DStabObjectFactory.createDynamicSimuAlgorithm(network);
        algorithm.setSimuMethod(DynamicSimuMethod.MODIFIED_EULER);
        algorithm.setSimuStepSec(simulationStep);
        algorithm.setTotalSimuTimeSec(endTime);
        algorithm.setOutPutPerSteps(1);
        StateMonitor monitor = new StateMonitor();
        monitor.addBusStdMonitor(new String[] {"Poi"});
        algorithm.setSimuOutputHandler(monitor);
        assertTrue(algorithm.getAclfAlgorithm().loadflow(), "reduced-network load flow");
        double commonModeQvSensitivity = commonModeQvSensitivity(network, plantCount);
        double plantVoltage = network.getBus("Plant1").getVoltageMag();
        double plantReactiveCurrent = ((DStabGen) network.getBus("Plant1")
                .getContributeGen("1")).getGen().getImaginary() / plantVoltage;
        if (withFault) {
            network.addDynamicEvent(DStabObjectFactory.createBusFaultEvent(
                    "Poi", network, SimpleFaultCode.GROUND_3P,
                    new Complex(0, 1.0e-4), null, .05, .05),
                    "ThreeCycleFault@Poi");
        }
        assertTrue(algorithm.initialization(), "reduced-network dynamic initialization");

        Map<String, Double> initialVoltage = new LinkedHashMap<>();
        network.getBusList().forEach(bus -> initialVoltage.put(bus.getId(), bus.getVoltageMag()));
        assertTrue(algorithm.performSimulation(), "reduced-network no-event simulation");
        double maximumDrift = initialVoltage.entrySet().stream()
                .mapToDouble(entry -> Math.abs(network.getBus(entry.getKey()).getVoltageMag()
                        - entry.getValue()))
                .max().orElseThrow();
        var poiVoltage = monitor.getBusVoltTable().get("Poi");
        double minimumPoiVoltage = poiVoltage.values().stream()
                .mapToDouble(value -> value.value).min().orElseThrow();
        double finalPoiVoltage = poiVoltage.get(poiVoltage.size() - 1).value;
        return new RunResult(maximumDrift, minimumPoiVoltage, finalPoiVoltage,
                commonModeQvSensitivity, initialVoltage.get("Plant1"),
                plantReactiveCurrent);
    }

    private static double commonModeQvSensitivity(DStabilityNetwork network, int plantCount)
            throws Exception {
        ISparseEqnComplex y = network.formYMatrix();
        network.getBusList().stream().filter(bus -> bus.isSwing()).forEach(bus ->
                y.setA(new Complex(0.0, 1.0e10), bus.getSortNumber(), bus.getSortNumber()));
        y.factorization(1.0e-20);
        y.setB2Zero();
        Complex perPlantReactiveCurrent = new Complex(0.0, -1.0 / plantCount);
        for (int i = 1; i <= plantCount; i++) {
            y.setBi(perPlantReactiveCurrent, network.getBus("Plant" + i).getSortNumber());
        }
        y.solveEqn();
        double maximum = 0.0;
        for (int i = 1; i <= plantCount; i++) {
            var bus = network.getBus("Plant" + i);
            Complex voltage = bus.getVoltage();
            Complex deltaVoltage = y.getX(bus.getSortNumber());
            double deltaMagnitude = deltaVoltage.multiply(voltage.conjugate()).getReal()
                    / voltage.abs();
            maximum = Math.max(maximum, Math.abs(deltaMagnitude));
        }
        return maximum;
    }

    private static Regca1Data regcaData() {
        return new Regca1Data(1, .02, 10, .9, .5, 1.22, 1.2, .8,
                .4, -1.3, .02, .7, 0, 0, .8);
    }

    private static Reeca1Data reecaData() {
        return new Reeca1Data(0, 0, 1, 1, 0, 0,
                .85, 1.15, .02, 0, 0, 5, 1.1, -1.1, 0, 0, 0, .5,
                .02, .436, -.436, 1.1, .9, 1.3, 2.1, 2.8, 2.9, 0, .02,
                99, -99, 1, 0, 1.3, .03,
                0, 0, 0, 0, 0, 0, 0, 0,
                0, 0, 0, 0, 0, 0, 0, 0);
    }

    private static Repca1Data repcaData() {
        return new Repca1Data(0, 0, 0, "0", 1, 1, 0,
                .02, 7, 4.1, 0, 1.48, .7, 0, 0, 0,
                1, -1, 0, 0, 1, -1, .2, .2, .02,
                0, 0, 1, -1, 2, 0, .1, 20, 0, 0);
    }

    private static PlantProfile texas1062Unit2Profile() {
        Reeca1Data reeca = new Reeca1Data(0, 0, 1, 1, 1, 0,
                .85, 1.15, .02, 0, 0, 5, 1.1, -1.1, 0, 0, 0, .5,
                .02, .436, -.436, 1.1, .9, 0, .5, 0, 24.8, 0, .02,
                99, -99, 1, 0, 1.3, .02,
                0, 0, 0, 0, 0, 0, 0, 0,
                0, 0, 0, 0, 0, 0, 0, 0);
        Repca1Data repca = new Repca1Data(0, 0, 0, "0", 0, 1, 1,
                .02, 17, 2.9, 0, 1.42, .7, 0, 0, 0,
                1, -1, 0, 0, 1, -1, .4, .21, .02,
                0, 0, 1, -1, 2, 0, .1, 20, 0, 0);
        return new PlantProfile(.24, .057, .10, reeca, repca);
    }

    private record RunResult(double maximumVoltageDrift, double minimumPoiVoltage,
            double finalPoiVoltage, double commonModeQvSensitivity,
            double plantVoltage, double plantReactiveCurrent) { }

    private record LinearMode(double real, double imaginary,
            List<StateParticipation> participation) { }

    private record StateParticipation(String state, double magnitude) { }

    private record PlantProfile(double p, double q, double collectorX,
            Reeca1Data reeca, Repca1Data repca) { }
}
