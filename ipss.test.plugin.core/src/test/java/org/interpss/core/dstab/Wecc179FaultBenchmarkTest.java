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
import com.interpss.core.sparse.solver.SparseEqnSolverProvider;
import com.interpss.dstab.BaseDStabNetwork;
import com.interpss.dstab.DStabObjectFactory;
import com.interpss.dstab.algo.DynamicSimuAlgorithm;
import com.interpss.dstab.algo.DynamicSimuMethod;
import com.interpss.dstab.cache.StateMonitor;
import com.interpss.dstab.cache.StateMonitor.MonitorRecord;
import com.interpss.simu.SimuContext;

class Wecc179FaultBenchmarkTest {
    private static final Path CASE_DIR = Path.of(System.getProperty("andes.wecc179.case.dir",
            Path.of("target", "andes-python", "andes", "cases", "wecc").toString()));
    private static final Path RAW = CASE_DIR.resolve("wecc.raw");
    private static final Path DYR = CASE_DIR.resolve("wecc_gencls.dyr");
    private static final String[] BUSES = { "Bus84", "Bus83", "Bus155", "Bus35" };
    private static final String[] MACHINES = { "Bus35-mach1", "Bus44-mach1", "Bus158-mach1" };

    @Test
    void runsThreeCycleFaultAtBus84WithClassicalMachines() throws Exception {
        assumeTrue(Files.isRegularFile(RAW), "Install ANDES or set -Dandes.wecc179.case.dir");
        assumeTrue(Files.isRegularFile(DYR), "Install ANDES or set -Dandes.wecc179.case.dir");
        IpssCorePlugin.init();
        SparseEqnSolverProvider.useJavaKlu();

        SimuContext context = new PSSEMultiFileLoader().loadDStab(RAW.toString(), DYR.toString());
        BaseDStabNetwork<?, ?> network = context.getDStabilityNet();
        network.setBypassDataCheck(true);
        DynamicSimuAlgorithm algorithm = context.getDynSimuAlgorithm();
        LoadflowAlgorithm loadflow = algorithm.getAclfAlgorithm();
        loadflow.getDataCheckConfig().setAutoTurnLine2Xfr(true);
        loadflow.getDataCheckConfig().setTurnOffIslandBus(false);
        loadflow.setNonDivergent(true);
        loadflow.setMaxIterations(50);
        loadflow.setTolerance(1.0e-10);
        assertTrue(loadflow.loadflow(), "WECC179 load flow must converge");

        for (String machine : MACHINES) {
            assertNotNull(network.getMachine(machine), "Missing monitored machine " + machine);
        }
        StateMonitor monitor = new StateMonitor();
        monitor.addBusStdMonitor(BUSES);
        monitor.addGeneratorStdMonitor(MACHINES);
        algorithm.setSimuOutputHandler(monitor);
        algorithm.setSimuMethod(DynamicSimuMethod.MODIFIED_EULER);
        algorithm.setSimuStepSec(Double.parseDouble(
                System.getProperty("wecc179.time.step", Double.toString(1.0 / 240.0))));
        algorithm.setTotalSimuTimeSec(Double.parseDouble(
                System.getProperty("wecc179.total.time", "5.0")));
        algorithm.setOutPutPerSteps(1);
        network.addDynamicEvent(DStabObjectFactory.createBusFaultEvent(
                "Bus84", network, SimpleFaultCode.GROUND_3P,
                new Complex(0.0, 5.0e-3), null, 1.0, 0.05),
                "ThreePhaseFault@Bus84");

        assertTrue(algorithm.initialization(), "WECC179 dynamic initialization must converge");
        while (algorithm.getSimuTime() <= algorithm.getTotalSimuTimeSec()) {
            assertTrue(algorithm.solveDEqnStep(true),
                    "WECC179 simulation failed at t=" + algorithm.getSimuTime());
        }

        Path output = Path.of("target", "wecc179-fault-benchmark");
        Files.createDirectories(output);
        writeCsv(output.resolve("interpss.csv"), monitor);
        for (String bus : BUSES) {
            double minimum = monitor.getBusVoltTable().get(bus).values().stream()
                    .mapToDouble(record -> record.value).min().orElseThrow();
            System.out.printf("%s minimum voltage: %.9f pu%n", bus, minimum);
        }
        for (String machine : MACHINES) {
            double maximumDeviation = monitor.getMachSpeedTable().get(machine).values().stream()
                    .mapToDouble(record -> Math.abs(record.value - 1.0)).max().orElseThrow();
            System.out.printf("%s maximum speed deviation: %.9f pu%n", machine, maximumDeviation);
        }
    }

    private static void writeCsv(Path path, StateMonitor monitor) throws Exception {
        Hashtable<String, Hashtable<Integer, MonitorRecord>> voltages = monitor.getBusVoltTable();
        Hashtable<String, Hashtable<Integer, MonitorRecord>> speeds = monitor.getMachSpeedTable();
        StringBuilder csv = new StringBuilder("time_s");
        for (String bus : BUSES) csv.append(',').append(bus);
        for (String machine : MACHINES) csv.append(',').append(machine);
        csv.append('\n');
        int samples = voltages.get(BUSES[0]).size();
        for (int index = 0; index < samples; index++) {
            csv.append(String.format(java.util.Locale.ROOT, "%.12g", voltages.get(BUSES[0]).get(index).t));
            for (String bus : BUSES) csv.append(',').append(String.format(java.util.Locale.ROOT,
                    "%.12g", voltages.get(bus).get(index).value));
            for (String machine : MACHINES) csv.append(',').append(String.format(java.util.Locale.ROOT,
                    "%.12g", speeds.get(machine).get(index).value));
            csv.append('\n');
        }
        Files.writeString(path, csv, StandardCharsets.UTF_8);
    }

}
