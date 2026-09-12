package org.interpss.core.dstab.mach;

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

/** Trajectory comparison against ANDES 2.0.0 for a public GENROU SMIB case. */
public class GenrouAndesSmibConformanceTest {
    private static final Path CASE = Path.of(
            "testData", "adpter", "psse", "v33", "SMIB");
    private static final Path REFERENCE = Path.of(
            "src", "test", "resources", "reference", "andes", "genrou-smib-line-trip.csv");

    @Test
    void lineTripTrajectoryMatchesAndes() throws Exception {
        IpssCorePlugin.init();
        var context = new PSSEMultiFileLoader().loadDStab(
                CASE.resolve("SMIB_v33.raw").toString(),
                CASE.resolve("SMIB_v33_genrou.dyr").toString());
        var network = context.getDStabilityNet();
        var algorithm = context.getDynSimuAlgorithm();
        assertTrue(algorithm.getAclfAlgorithm().loadflow(), "SMIB load flow");

        algorithm.setSimuMethod(DynamicSimuMethod.MODIFIED_EULER);
        algorithm.setSimuStepSec(.001);
        algorithm.setTotalSimuTimeSec(5.0);
        algorithm.setOutPutPerSteps(1);
        var branch = network.getBranch("Bus1", "Bus2", "1");
        var outage = DStabObjectFactory.createDEvent(
                "TripLine1", "TripLine1", DynamicSimuEventType.BRANCH_OUTAGE, network);
        outage.setStartTimeSec(1.0);
        outage.setDurationSec(999.0);
        outage.setPermanent(true);
        outage.setBranchDynamicEvent(DStabObjectFactory.createBranchOutageEvent(
                branch.getId(), network));
        network.addDynamicEvent(outage, "TripLine1");

        StateMonitor monitor = new StateMonitor();
        monitor.addGeneratorStdMonitor(new String[] {"Bus1-mach1"});
        monitor.addBusStdMonitor(new String[] {"Bus1", "Bus2"});
        algorithm.setSimuOutputHandler(monitor);
        assertTrue(algorithm.initialization(), "SMIB dynamic initialization");
        assertTrue(algorithm.performSimulation(), "SMIB line-trip simulation");

        List<double[]> reference = readReference();
        Series angle = series(monitor.getMachAngleTable(), "Bus1-mach1");
        Series speed = series(monitor.getMachSpeedTable(), "Bus1-mach1");
        Series te = series(monitor.getMachPeTable(), "Bus1-mach1");
        Series tm = series(monitor.getMachPmTable(), "Bus1-mach1");
        Series efd = series(monitor.getMachEfdTable(), "Bus1-mach1");
        Series qe = series(monitor.getMachQgenTable(), "Bus1-mach1");
        Series bus1 = series(monitor.getBusVoltTable(), "Bus1");
        Series bus2 = series(monitor.getBusVoltTable(), "Bus2");
        double[] max = new double[8];
        for (int index = 0; index < reference.size(); index++) {
            double[] expected = reference.get(index);
            double time = expected[0];
            max[0] = Math.max(max[0], Math.abs(angle.at(time) - expected[1]));
            max[1] = Math.max(max[1], Math.abs(speed.at(time) - expected[2]));
            max[2] = Math.max(max[2], Math.abs(te.at(time) - expected[3]));
            max[3] = Math.max(max[3], Math.abs(tm.at(time) - expected[4]));
            max[4] = Math.max(max[4], Math.abs(efd.at(time) - expected[5]));
            max[5] = Math.max(max[5], Math.abs(qe.at(time) - expected[6]));
            max[6] = Math.max(max[6], Math.abs(bus1.at(time) - expected[7]));
            max[7] = Math.max(max[7], Math.abs(bus2.at(time) - expected[8]));
        }
        System.out.printf(java.util.Locale.ROOT,
                "GENROU ANDES SMIB max errors: angle=%.9g speed=%.9g te=%.9g "
                + "tm=%.9g efd=%.9g q=%.9g v1=%.9g v2=%.9g%n",
                max[0], max[1], max[2], max[3], max[4], max[5], max[6], max[7]);

        assertTrue(max[0] < .03, "rotor angle parity");
        assertTrue(max[1] < 2.0e-5, "rotor speed parity");
        assertTrue(max[2] < .002, "electrical torque parity");
        assertTrue(max[3] < 1.0e-9, "mechanical torque parity");
        assertTrue(max[4] < 1.0e-7, "field-voltage parity");
        assertTrue(max[5] < 2.0e-4, "reactive-power parity");
        assertTrue(max[6] < 5.0e-5, "generator-bus voltage parity");
        assertTrue(max[7] < 2.0e-5, "infinite-bus voltage parity");
    }

    private static Series series(
            java.util.Hashtable<String, java.util.Hashtable<Integer,
                    StateMonitor.MonitorRecord>> table,
            String id) {
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
        for (String line : EmbeddedCsvTrajectoryValues.lines("genrou-smib-line-trip.csv")) {
            if (line.isBlank() || line.startsWith("#") || line.startsWith("time_s")) {
                continue;
            }
            String[] values = line.split(",");
            double[] row = new double[values.length];
            for (int i = 0; i < values.length; i++) row[i] = Double.parseDouble(values[i]);
            result.add(row);
        }
        return result;
    }

    private record Series(double[] time, double[] value) {
        double at(double target) {
            if (Math.abs(target - time[0]) < 1.0e-8) return value[0];
            if (Math.abs(target - time[time.length - 1]) < 1.0e-8) {
                return value[value.length - 1];
            }
            int index = Arrays.binarySearch(time, target);
            if (index >= 0) return value[index];
            int upper = -index - 1;
            if (upper == 0 || upper == time.length) {
                throw new IllegalArgumentException("Reference time outside actual trajectory: "
                        + target);
            }
            int lower = upper - 1;
            double fraction = (target - time[lower]) / (time[upper] - time[lower]);
            return value[lower] + fraction * (value[upper] - value[lower]);
        }
    }
}
