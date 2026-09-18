package org.interpss.core.dstab;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.interpss.IpssCorePlugin;
import org.interpss.core.dstab.reference.EmbeddedCsvTrajectoryValues;
import org.interpss.fadapter.psse.PSSEMultiFileLoader;
import org.junit.jupiter.api.Test;

import com.interpss.dstab.DStabObjectFactory;
import com.interpss.dstab.algo.DynamicSimuMethod;
import com.interpss.dstab.cache.StateMonitor;
import com.interpss.dstab.devent.DynamicSimuEventType;

/** Trajectory comparison against ANDES 2.0.0 for GENROU plus EXDC2. */
public class Exdc2AndesSmibConformanceTest {
    private static final Path CASE = Path.of(
            "testData", "adpter", "psse", "v33", "SMIB");
    private static final Path REFERENCE = Path.of("src", "test", "resources",
            "reference", "andes", "exdc2-smib-line-trip.csv");

    @Test
    void lineTripTrajectoryMatchesAndes() throws Exception {
        IpssCorePlugin.init();
        var context = new PSSEMultiFileLoader().loadDStab(
                CASE.resolve("SMIB_v33.raw").toString(),
                CASE.resolve("SMIB_v33_genrou_exdc2.dyr").toString());
        var network = context.getDStabilityNet();
        var algorithm = context.getDynSimuAlgorithm();
        assertTrue(algorithm.getAclfAlgorithm().loadflow(), "SMIB load flow");
        algorithm.setSimuMethod(DynamicSimuMethod.MODIFIED_EULER);
        algorithm.setSimuStepSec(0.001);
        algorithm.setTotalSimuTimeSec(5.0);
        algorithm.setOutPutPerSteps(1);

        var outage = DStabObjectFactory.createDEvent("TripLine1", "TripLine1",
                DynamicSimuEventType.BRANCH_OUTAGE, network);
        outage.setStartTimeSec(1.0);
        outage.setDurationSec(999.0);
        outage.setPermanent(true);
        outage.setBranchDynamicEvent(DStabObjectFactory.createBranchOutageEvent(
                network.getBranch("Bus1", "Bus2", "1").getId(), network));
        network.addDynamicEvent(outage, "TripLine1");

        StateMonitor monitor = new StateMonitor();
        monitor.addGeneratorStdMonitor(new String[] {"Bus1-mach1"});
        monitor.addBusStdMonitor(new String[] {"Bus1", "Bus2"});
        algorithm.setSimuOutputHandler(monitor);
        assertTrue(algorithm.initialization(), "EXDC2 dynamic initialization");
        assertTrue(algorithm.performSimulation(), "EXDC2 line-trip simulation");

        Series speed = series(monitor.getMachSpeedTable(), "Bus1-mach1");
        Series efd = series(monitor.getMachEfdTable(), "Bus1-mach1");
        Series bus1 = series(monitor.getBusVoltTable(), "Bus1");
        Series bus2 = series(monitor.getBusVoltTable(), "Bus2");
        double[] max = new double[4];
        for (double[] row : readReference()) {
            double time = row[0];
            max[0] = Math.max(max[0], Math.abs(speed.at(time) - row[2]));
            max[1] = Math.max(max[1], Math.abs(efd.at(time) - row[5]));
            max[2] = Math.max(max[2], Math.abs(bus1.at(time) - row[7]));
            max[3] = Math.max(max[3], Math.abs(bus2.at(time) - row[8]));
        }
        System.out.printf(java.util.Locale.ROOT,
                "EXDC2 ANDES SMIB max errors: speed=%.9g efd=%.9g v1=%.9g v2=%.9g%n",
                max[0], max[1], max[2], max[3]);
        assertTrue(max[0] < 2.0e-5, "rotor-speed parity");
        assertTrue(max[1] < 3.0e-4, "field-voltage parity");
        assertTrue(max[2] < 5.0e-5, "generator-bus voltage parity");
        assertTrue(max[3] < 2.0e-5, "infinite-bus voltage parity");
    }

    private static Series series(
            java.util.Hashtable<String, java.util.Hashtable<Integer,
                    StateMonitor.MonitorRecord>> table, String id) {
        var records = table.get(id);
        double[] time = new double[records.size()];
        double[] value = new double[records.size()];
        for (int i = 0; i < records.size(); i++) {
            time[i] = records.get(i).getTime();
            value[i] = records.get(i).getValue();
        }
        return new Series(time, value);
    }

    private static List<double[]> readReference() throws Exception {
        List<double[]> result = new ArrayList<>();
        for (String line : EmbeddedCsvTrajectoryValues.lines("exdc2-smib-line-trip.csv")) {
            if (line.isBlank() || line.startsWith("#") || line.startsWith("time_s")) continue;
            String[] values = line.split(",");
            double[] row = new double[values.length];
            for (int i = 0; i < values.length; i++) row[i] = Double.parseDouble(values[i]);
            result.add(row);
        }
        return result;
    }

    private record Series(double[] time, double[] value) {
        double at(double target) {
            int index = Arrays.binarySearch(time, target);
            if (index >= 0) return value[index];
            int upper = -index - 1;
            if (upper == 0 || upper == time.length) {
                throw new IllegalArgumentException("Reference time outside trajectory: " + target);
            }
            int lower = upper - 1;
            double fraction = (target - time[lower]) / (time[upper] - time[lower]);
            return value[lower] + fraction * (value[upper] - value[lower]);
        }
    }
}
