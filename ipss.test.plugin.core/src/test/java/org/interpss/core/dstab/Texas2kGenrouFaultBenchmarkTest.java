package org.interpss.core.dstab;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Hashtable;
import java.util.List;

import org.apache.commons.math3.complex.Complex;
import org.interpss.IpssCorePlugin;
import org.interpss.fadapter.psse.PSSEMultiFileLoader;
import org.interpss.fadapter.psse.dyr.PsseDyrRecordReader;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.interpss.core.acsc.fault.SimpleFaultCode;
import com.interpss.core.algo.LoadflowAlgorithm;
import com.interpss.dstab.BaseDStabNetwork;
import com.interpss.dstab.DStabGen;
import com.interpss.dstab.DStabObjectFactory;
import com.interpss.dstab.algo.DynamicSimuAlgorithm;
import com.interpss.dstab.algo.DynamicSimuMethod;
import com.interpss.dstab.cache.StateMonitor;
import com.interpss.dstab.cache.StateMonitor.MonitorRecord;
import com.interpss.simu.SimuContext;

/** GENROU-only Texas2k trajectory fixture shared with the ANDES benchmark. */
public class Texas2kGenrouFaultBenchmarkTest {
    private static final Path CASE = Path.of(System.getProperty("texas2k.case1.dir",
            Path.of(System.getProperty("user.home"), "OneDrive", "Documents", "qiuhua",
                    "private_cases", "Texas2k_series24_cases_with_dynamics",
                    "Texas2k_series24_cases_with_dynamics",
                    "Texas2k_series24_case1_2016summerpeak").toString()));
    private static final Path RAW = CASE.resolve("Texas2k_series24_case1_2016summerPeak_v33.RAW");
    private static final Path DYR = CASE.resolve("dynamic_models_case1.dyr");
    private static final Path GNET = CASE.resolve("dynamic_models_case1_gnet.idv");

    @Test
    void exportsThreeCycleFaultTraceForAndesComparison(@TempDir Path tempDir) throws Exception {
        assumeTrue(Files.isRegularFile(RAW), "Missing Texas2k Case 1 v33 RAW");
        assumeTrue(Files.isRegularFile(DYR), "Missing Texas2k Case 1 DYR");
        assumeTrue(Files.isRegularFile(GNET), "Missing Texas2k Case 1 GNET IDV");
        IpssCorePlugin.init();

        Path genrouDyr = tempDir.resolve("genrou.dyr");
        StringBuilder records = new StringBuilder();
        PsseDyrRecordReader.read(DYR).stream()
                .filter(record -> record.canonicalModelName().equals("GENROU"))
                .forEach(record -> records.append(record.rawText()).append(" /\n"));
        Files.writeString(genrouDyr, records, StandardCharsets.UTF_8);

        SimuContext context = new PSSEMultiFileLoader().loadDStab(
                RAW.toString(), genrouDyr.toString(), GNET.toString());
        BaseDStabNetwork<?, ?> network = context.getDStabilityNet();
        network.setAllowGenWithoutMach(true);
        DynamicSimuAlgorithm algorithm = context.getDynSimuAlgorithm();
        LoadflowAlgorithm loadflow = algorithm.getAclfAlgorithm();
        loadflow.getDataCheckConfig().setAutoTurnLine2Xfr(true);
        loadflow.getDataCheckConfig().setTurnOffIslandBus(true);
        loadflow.setNonDivergent(true);
        loadflow.setMaxIterations(50);
        loadflow.setTolerance(1.0e-8);
        assertTrue(loadflow.loadflow(), "Texas2k Case 1 v33 load flow must converge");

        List<String> machines = network.getBusList().stream()
                .flatMap(bus -> bus.getContributeGenList().stream())
                .filter(DStabGen.class::isInstance)
                .map(DStabGen.class::cast)
                .map(DStabGen::getMach)
                .filter(java.util.Objects::nonNull)
                .map(machine -> machine.getId())
                .sorted()
                .toList();
        List<String> buses = network.getBusList().stream()
                .filter(bus -> bus.isActive())
                .map(bus -> bus.getId())
                .sorted()
                .toList();
        assertTrue(machines.size() == 410, "Expected all 410 Texas2k GENROU machines");
        for (String machine : List.of("Bus1051-mach1", "Bus2056-mach1")) {
            assertNotNull(network.getMachine(machine), "Missing monitored GENROU " + machine);
        }
        StateMonitor monitor = new StateMonitor();
        monitor.addBusStdMonitor(buses.toArray(String[]::new));
        monitor.addGeneratorStdMonitor(machines.toArray(String[]::new));
        algorithm.setSimuOutputHandler(monitor);
        algorithm.setSimuMethod(DynamicSimuMethod.MODIFIED_EULER);
        algorithm.setSimuStepSec(1.0 / 240.0);
        algorithm.setTotalSimuTimeSec(.25);
        algorithm.setOutPutPerSteps(1);
        network.addDynamicEvent(DStabObjectFactory.createBusFaultEvent(
                "Bus7159", network, SimpleFaultCode.GROUND_3P,
                new Complex(0.0, 1.0e-4), null, .05, .05),
                "ThreeCycleFault@Bus7159");

        assertTrue(algorithm.initialization(), "GENROU-only initialization");
        assertTrue(algorithm.performSimulation(), "GENROU-only fault simulation");
        Path output = Path.of("target", "andes-benchmarks", "texas2k-case1");
        Files.createDirectories(output);
        writeCsv(output.resolve("interpss-genrou.csv"), monitor, buses, machines);
    }

    private static void writeCsv(Path path, StateMonitor monitor, List<String> buses,
            List<String> machines) throws Exception {
        Hashtable<String, Hashtable<Integer, MonitorRecord>> voltages = monitor.getBusVoltTable();
        Hashtable<String, Hashtable<Integer, MonitorRecord>> speeds = monitor.getMachSpeedTable();
        StringBuilder csv = new StringBuilder("time_s");
        for (String bus : buses) csv.append(',').append(bus);
        for (String machine : machines) csv.append(',').append(machine);
        csv.append('\n');
        int samples = voltages.get(buses.get(0)).size();
        for (int index = 0; index < samples; index++) {
            csv.append(String.format(java.util.Locale.ROOT, "%.12g",
                    voltages.get(buses.get(0)).get(index).t));
            for (String bus : buses) csv.append(',').append(String.format(
                    java.util.Locale.ROOT, "%.12g", voltages.get(bus).get(index).value));
            for (String machine : machines) csv.append(',').append(String.format(
                    java.util.Locale.ROOT, "%.12g", speeds.get(machine).get(index).value));
            csv.append('\n');
        }
        Files.writeString(path, csv, StandardCharsets.UTF_8);
    }
}
