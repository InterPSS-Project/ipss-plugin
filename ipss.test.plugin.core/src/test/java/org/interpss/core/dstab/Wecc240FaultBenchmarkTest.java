package org.interpss.core.dstab;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.Hashtable;

import org.apache.commons.math3.complex.Complex;
import org.interpss.IpssCorePlugin;
import org.interpss.dstab.renewable.Regca1Model;
import org.interpss.dstab.renewable.Reecb1Model;
import org.interpss.dstab.validation.DynamicTraceCsv;
import org.interpss.dstab.validation.StateMonitorTraceAdapter;
import org.interpss.fadapter.psse.PSSEMultiFileLoader;
import org.junit.jupiter.api.Test;

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

class Wecc240FaultBenchmarkTest {
    private static final Path CASE_DIR = Path.of(System.getProperty("wecc240.case.dir",
            Path.of("testData", "private", "wecc240",
                    "WECC 240-bus case (2018 summar peak) for 2021 IEEE-NASPI OSL Contest")
                    .toString()));
    private static final Path RAW = CASE_DIR.resolve("240busWECC_2018_PSS.raw");
    private static final Path DYR = CASE_DIR.resolve("240busWECC_2018_PSS.dyr");
    private static final String RENEWABLE_BUS = "Bus2431";
    private static final String RENEWABLE_DEVICE = "S";
    private static final String[] BUSES = {
            "Bus2401", RENEWABLE_BUS, "Bus2402", "Bus2404", "Bus2501"
    };
    private static final String[] MACHINES = {
            "Bus2438-machEG", "Bus2630-machG", "Bus2634-machC"
    };
    private static final double RECOVERY_WINDOW_START = 4.5;
    private static final double MAX_BUS_FINAL_DELTA = 0.01;
    private static final double MAX_BUS_TAIL_SPAN = 0.005;
    private static final double MAX_SPEED_DEVIATION = 0.01;
    private static final double MAX_SPEED_FINAL_DEVIATION = 0.002;
    private static final double MAX_SPEED_TAIL_SPAN = 0.005;
    private static final double MAX_RENEWABLE_STATE_DELTA = 0.01;

    @Test
    void threeCycleFaultNearRenewableBusRecoversWithoutInstability() throws Exception {
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
        assertNotNull(network.getBranch("Bus2401", RENEWABLE_BUS, "1"),
                "Fault bus must be directly connected to the renewable plant bus");
        DStabGen renewableGenerator = (DStabGen) network.getBus(RENEWABLE_BUS)
                .getContributeGen(RENEWABLE_DEVICE);
        assertNotNull(renewableGenerator, "Missing renewable generator at " + RENEWABLE_BUS);
        assertTrue(renewableGenerator.getDynamicGenDevice() instanceof Regca1Model,
                "Expected REGCA1 at " + RENEWABLE_BUS + ':' + RENEWABLE_DEVICE);
        Regca1Model renewable = (Regca1Model) renewableGenerator.getDynamicGenDevice();
        assertTrue(renewable.getElectricalController() instanceof Reecb1Model,
                "Expected REECB1 at " + RENEWABLE_BUS + ':' + RENEWABLE_DEVICE);
        Reecb1Model renewableController = (Reecb1Model) renewable.getElectricalController();
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
        double initialRenewableIp = renewable.getIpRegulatorState();
        double initialRenewableIq = renewable.getIqRegulatorState();
        double initialRenewableMeasuredVoltage = renewableController.getMeasuredVoltage();
        assertTrue(algorithm.performSimulation(), "WECC240 fault simulation must complete");

        Path output = Path.of("target", "wecc240-fault-benchmark");
        Files.createDirectories(output);
        writeCsv(output.resolve("interpss.csv"), BUSES, MACHINES,
                monitor.getBusVoltTable(), monitor.getMachSpeedTable());
        DynamicTraceCsv.write(output.resolve("interpss-long.csv"),
                StateMonitorTraceAdapter.standard(monitor));

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
        System.out.printf("  %s REGCA1 recovery: Vmeas %.9f -> %.9f pu, Ip %.9f -> %.9f pu, "
                        + "Iq %.9f -> %.9f pu%n",
                RENEWABLE_BUS, initialRenewableMeasuredVoltage,
                renewableController.getMeasuredVoltage(), initialRenewableIp,
                renewable.getIpRegulatorState(), initialRenewableIq,
                renewable.getIqRegulatorState());

        assertTrue(minimum(monitor.getBusVoltTable().get("Bus2401")) < 0.1,
                "Fault must depress Bus2401 below 0.1 pu");
        assertTrue(minimum(monitor.getBusVoltTable().get(RENEWABLE_BUS)) < 0.1,
                "Fault must depress the adjacent renewable bus below 0.1 pu");
        for (String bus : BUSES) {
            assertRecoveredVoltage(bus, monitor.getBusVoltTable().get(bus));
        }
        for (String machine : MACHINES) {
            assertStableSpeed(machine, monitor.getMachSpeedTable().get(machine));
        }
        assertFinite("renewable measured voltage", renewableController.getMeasuredVoltage());
        assertFinite("renewable active-current state", renewable.getIpRegulatorState());
        assertFinite("renewable reactive-current state", renewable.getIqRegulatorState());
        assertTrue(Math.abs(renewableController.getMeasuredVoltage()
                        - initialRenewableMeasuredVoltage) <= MAX_RENEWABLE_STATE_DELTA,
                "Renewable measured voltage did not recover");
        assertTrue(Math.abs(renewable.getIpRegulatorState() - initialRenewableIp)
                        <= MAX_RENEWABLE_STATE_DELTA,
                "Renewable active-current state did not recover");
        assertTrue(Math.abs(renewable.getIqRegulatorState() - initialRenewableIq)
                        <= MAX_RENEWABLE_STATE_DELTA,
                "Renewable reactive-current state did not recover");
    }

    private static void assertRecoveredVoltage(String bus,
            Hashtable<Integer, MonitorRecord> records) {
        assertNotNull(records, "Missing voltage trace for " + bus);
        assertFiniteTrace(bus + " voltage", records);
        double initial = first(records).value;
        double end = last(records).value;
        assertTrue(Math.abs(end - initial) <= MAX_BUS_FINAL_DELTA,
                () -> bus + " final voltage differs from its initial value by "
                        + Math.abs(end - initial) + " pu");
        assertTrue(spanAfter(records, RECOVERY_WINDOW_START) <= MAX_BUS_TAIL_SPAN,
                () -> bus + " voltage remains oscillatory in the final 0.5 s");
    }

    private static void assertStableSpeed(String machine,
            Hashtable<Integer, MonitorRecord> records) {
        assertNotNull(records, "Missing speed trace for " + machine);
        assertFiniteTrace(machine + " speed", records);
        double initial = first(records).value;
        double maximumDeviation = records.values().stream()
                .mapToDouble(record -> Math.abs(record.value - initial)).max().orElseThrow();
        double finalDeviation = Math.abs(last(records).value - initial);
        assertTrue(maximumDeviation <= MAX_SPEED_DEVIATION,
                () -> machine + " speed deviation exceeded " + MAX_SPEED_DEVIATION + " pu");
        assertTrue(finalDeviation <= MAX_SPEED_FINAL_DEVIATION,
                () -> machine + " final speed deviation is " + finalDeviation + " pu");
        assertTrue(spanAfter(records, RECOVERY_WINDOW_START) <= MAX_SPEED_TAIL_SPAN,
                () -> machine + " speed remains oscillatory in the final 0.5 s");
    }

    private static void assertFiniteTrace(String label,
            Hashtable<Integer, MonitorRecord> records) {
        assertTrue(!records.isEmpty(), "Empty " + label + " trace");
        assertTrue(records.values().stream()
                        .allMatch(record -> Double.isFinite(record.t)
                                && Double.isFinite(record.value)),
                "Non-finite sample in " + label + " trace");
    }

    private static void assertFinite(String label, double value) {
        assertTrue(Double.isFinite(value), label + " is not finite");
    }

    private static MonitorRecord first(Hashtable<Integer, MonitorRecord> records) {
        return records.values().stream().min(Comparator.comparingDouble(record -> record.t))
                .orElseThrow();
    }

    private static MonitorRecord last(Hashtable<Integer, MonitorRecord> records) {
        return records.values().stream().max(Comparator.comparingDouble(record -> record.t))
                .orElseThrow();
    }

    private static double minimum(Hashtable<Integer, MonitorRecord> records) {
        return records.values().stream().mapToDouble(record -> record.value).min().orElseThrow();
    }

    private static double spanAfter(Hashtable<Integer, MonitorRecord> records, double startTime) {
        double minimum = records.values().stream().filter(record -> record.t >= startTime)
                .mapToDouble(record -> record.value).min().orElseThrow();
        double maximum = records.values().stream().filter(record -> record.t >= startTime)
                .mapToDouble(record -> record.value).max().orElseThrow();
        return maximum - minimum;
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
