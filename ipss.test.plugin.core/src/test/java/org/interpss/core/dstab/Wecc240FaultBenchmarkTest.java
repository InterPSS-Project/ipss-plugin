package org.interpss.core.dstab;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Hashtable;

import org.apache.commons.math3.complex.Complex;
import org.interpss.IpssCorePlugin;
import org.interpss.fadapter.psse.PSSEMultiFileLoader;
import org.junit.jupiter.api.Test;

import com.interpss.core.acsc.fault.SimpleFaultCode;
import com.interpss.core.algo.LoadflowAlgorithm;
import com.interpss.dstab.BaseDStabNetwork;
import com.interpss.dstab.DStabObjectFactory;
import com.interpss.dstab.algo.DynamicSimuAlgorithm;
import com.interpss.dstab.algo.DynamicSimuMethod;
import com.interpss.dstab.cache.StateMonitor;
import com.interpss.dstab.cache.StateMonitor.MonitorRecord;
import com.interpss.simu.SimuContext;

class Wecc240FaultBenchmarkTest {
    private static final Path CASE_DIR = Path.of("testData", "private", "wecc240",
            "WECC 240-bus case (2018 summar peak) for 2021 IEEE-NASPI OSL Contest");
    private static final Path RAW = CASE_DIR.resolve("240busWECC_2018_PSS.raw");
    private static final Path DYR = CASE_DIR.resolve("240busWECC_2018_PSS.dyr");
    private static final String[] BUSES = { "Bus2401", "Bus2402", "Bus2404", "Bus2501" };
    private static final String[] MACHINES = {
            "Bus2438-machEG", "Bus2630-machG", "Bus2634-machC"
    };

    @Test
    void runsThreeCycleFaultAtBus2401() throws Exception {
        assumeTrue(Files.isRegularFile(RAW));
        assumeTrue(Files.isRegularFile(DYR));
        IpssCorePlugin.init();

        SimuContext context = new PSSEMultiFileLoader().loadDStab(RAW.toString(), DYR.toString());
        BaseDStabNetwork<?, ?> network = context.getDStabilityNet();
        network.setBypassDataCheck(true);
        DynamicSimuAlgorithm algorithm = context.getDynSimuAlgorithm();
        LoadflowAlgorithm loadflow = algorithm.getAclfAlgorithm();
        loadflow.getDataCheckConfig().setAutoTurnLine2Xfr(true);
        loadflow.getDataCheckConfig().setTurnOffIslandBus(true);
        loadflow.setNonDivergent(true);
        loadflow.setMaxIterations(50);
        loadflow.setTolerance(1.0e-10);
        assertTrue(loadflow.loadflow(), "WECC240 load flow must converge");

        for (String machine : MACHINES) {
            assertNotNull(network.getMachine(machine), "Missing monitored machine " + machine);
        }
        StateMonitor monitor = new StateMonitor();
        monitor.addBusStdMonitor(BUSES);
        monitor.addGeneratorStdMonitor(MACHINES);
        algorithm.setSimuOutputHandler(monitor);
        algorithm.setSimuMethod(DynamicSimuMethod.MODIFIED_EULER);
        algorithm.setSimuStepSec(1.0 / 240.0);
        algorithm.setTotalSimuTimeSec(5.0);
        algorithm.setOutPutPerSteps(1);

        network.addDynamicEvent(DStabObjectFactory.createBusFaultEvent(
                "Bus2401", network, SimpleFaultCode.GROUND_3P,
                new Complex(0.0, 1.0e-4), null, 1.0, 0.05),
                "ThreePhaseFault@Bus2401");

        assertTrue(algorithm.initialization(), "WECC240 dynamic initialization must converge");
        assertTrue(algorithm.performSimulation(), "WECC240 fault simulation must complete");

        Path output = Path.of("target", "wecc240-fault-benchmark");
        Files.createDirectories(output);
        writeCsv(output.resolve("interpss.csv"), BUSES, MACHINES,
                monitor.getBusVoltTable(), monitor.getMachSpeedTable());

        System.out.println("InterPSS WECC240 Bus 2401 fault summary");
        for (String bus : BUSES) {
            double minimum = monitor.getBusVoltTable().get(bus).values().stream()
                    .mapToDouble(record -> record.value).min().orElseThrow();
            System.out.printf("  %s minimum voltage: %.9f pu%n", bus, minimum);
        }
        for (String machine : MACHINES) {
            double maximumDeviation = monitor.getMachSpeedTable().get(machine).values().stream()
                    .mapToDouble(record -> Math.abs(record.value - 1.0)).max().orElseThrow();
            System.out.printf("  %s maximum speed deviation: %.9f pu%n", machine, maximumDeviation);
        }
    }

    private static void writeCsv(Path path, String[] buses, String[] machines,
            Hashtable<String, Hashtable<Integer, MonitorRecord>> voltages,
            Hashtable<String, Hashtable<Integer, MonitorRecord>> speeds) throws Exception {
        StringBuilder csv = new StringBuilder("time_s");
        for (String bus : buses) csv.append(',').append(bus);
        for (String machine : machines) csv.append(',').append(machine);
        csv.append('\n');

        int samples = voltages.get(buses[0]).size();
        for (int index = 0; index < samples; index++) {
            MonitorRecord timeRecord = voltages.get(buses[0]).get(index);
            csv.append(String.format(java.util.Locale.ROOT, "%.12g", timeRecord.t));
            for (String bus : buses) {
                csv.append(',').append(String.format(java.util.Locale.ROOT, "%.12g",
                        voltages.get(bus).get(index).value));
            }
            for (String machine : machines) {
                csv.append(',').append(String.format(java.util.Locale.ROOT, "%.12g",
                        speeds.get(machine).get(index).value));
            }
            csv.append('\n');
        }
        Files.writeString(path, csv, StandardCharsets.UTF_8);
    }
}
