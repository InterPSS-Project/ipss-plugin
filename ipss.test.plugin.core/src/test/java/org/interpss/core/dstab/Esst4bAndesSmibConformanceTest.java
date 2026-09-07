package org.interpss.core.dstab;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.interpss.IpssCorePlugin;
import org.interpss.dstab.control.exc.ieee.y2005.st4b.IEEE2005ST4BExciter;
import org.interpss.fadapter.psse.PSSEMultiFileLoader;
import org.junit.jupiter.api.Test;

import com.interpss.dstab.DStabObjectFactory;
import com.interpss.dstab.algo.DynamicSimuMethod;
import com.interpss.dstab.cache.StateMonitor;
import com.interpss.dstab.common.DStabOutSymbol;
import com.interpss.dstab.datatype.DStabSimuEvent;
import com.interpss.dstab.devent.DynamicSimuEventType;

/** Trajectory comparison against the common implemented subset of ANDES 2.0.0 ESST4B. */
public class Esst4bAndesSmibConformanceTest {
    private static final double STEP = .0005;
    private static final Path CASE = Path.of("testData", "adpter", "psse", "v33", "SMIB");
    private static final Path REFERENCE = Path.of("src", "test", "resources", "reference",
            "andes", "esst4b-smib-line-trip.csv");

    @Test
    void lineTripTrajectoryMatchesAndes() throws Exception {
        IpssCorePlugin.init();
        var context = new PSSEMultiFileLoader().loadDStab(
                CASE.resolve("SMIB_v33.raw").toString(),
                CASE.resolve("SMIB_v33_genrou_esst4b.dyr").toString());
        var network = context.getDStabilityNet();
        var algorithm = context.getDynSimuAlgorithm();
        assertTrue(algorithm.getAclfAlgorithm().loadflow(), "SMIB load flow");
        algorithm.setSimuMethod(DynamicSimuMethod.MODIFIED_EULER);
        algorithm.setSimuStepSec(STEP);
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
        IEEE2005ST4BExciter exciter = (IEEE2005ST4BExciter) network
                .getMachine("Bus1-mach1").getExciter();
        InternalStateMonitor monitor = new InternalStateMonitor(exciter);
        monitor.addGeneratorStdMonitor(new String[] {"Bus1-mach1"});
        monitor.addBusStdMonitor(new String[] {"Bus1", "Bus2"});
        algorithm.setSimuOutputHandler(monitor);
        assertTrue(algorithm.initialization(), "ESST4B dynamic initialization");
        assertTrue(algorithm.performSimulation(), "ESST4B line-trip simulation");

        Series speed = series(monitor.getMachSpeedTable(), "Bus1-mach1");
        Series efd = series(monitor.getMachEfdTable(), "Bus1-mach1");
        Series bus1 = series(monitor.getBusVoltTable(), "Bus1");
        Series bus2 = series(monitor.getBusVoltTable(), "Bus2");
        assertTrue(monitor.internal.size() == speed.time.length,
                () -> "internal-state samples=" + monitor.internal.size()
                        + ", machine samples=" + speed.time.length);
        Series[] internal = monitor.internalSeries(speed.time);
        double[] max = new double[4];
        double[] maxTime = new double[4];
        for (double[] row : readReference()) {
            double time = row[0];
            double[] error = {Math.abs(speed.at(time) - row[2]),
                    Math.abs(efd.at(time) - row[5]), 0.0, 0.0};
            // The two solvers timestamp the algebraic network discontinuity on opposite
            // sides of the event. Compare voltage everywhere except the event instant.
            if (Math.abs(time - 1.0) > STEP / 2.0) {
                error[2] = Math.abs(bus1.at(time) - row[7]);
                error[3] = Math.abs(bus2.at(time) - row[8]);
            }
            for (int i = 0; i < max.length; i++) {
                if (error[i] > max[i]) {
                    max[i] = error[i];
                    maxTime[i] = time;
                }
            }
        }
        System.out.printf(java.util.Locale.ROOT,
                "ESST4B ANDES SMIB max errors: speed=%.9g@%.4f efd=%.9g@%.4f "
                        + "v1=%.9g@%.4f v2=%.9g@%.4f%n",
                max[0], maxTime[0], max[1], maxTime[1], max[2], maxTime[2],
                max[3], maxTime[3]);
        assertTrue(max[0] < 2e-5, "rotor-speed parity");
        assertTrue(max[1] < 5e-4, "field-voltage parity");
        assertTrue(max[2] < 5e-5, "generator-bus voltage parity");
        assertTrue(max[3] < 2e-5, "infinite-bus voltage parity");

        double[] internalMax = new double[internal.length];
        double[] internalMaxTime = new double[internal.length];
        for (double[] row : readReference()) {
            double time = row[0];
            for (int i = 0; i < internal.length; i++) {
                // Compare dynamic states at the event instant; compare algebraic block
                // outputs on all surrounding samples because event timestamps differ.
                if (Math.abs(time - 1.0) <= STEP / 2.0
                        && i != 1 && i != 3 && i != 4) {
                    continue;
                }
                double error = Math.abs(internal[i].at(time) - row[9 + i]);
                if (error > internalMax[i]) {
                    internalMax[i] = error;
                    internalMaxTime[i] = time;
                }
            }
        }
        System.out.printf(java.util.Locale.ROOT,
                "ESST4B ANDES internal max errors: sensed=%.9g@%.4f outerI=%.9g@%.4f "
                        + "Vr=%.9g@%.4f Va=%.9g@%.4f innerI=%.9g@%.4f Vm=%.9g@%.4f "
                        + "Vg=%.9g@%.4f Vb=%.9g@%.4f%n",
                internalMax[0], internalMaxTime[0], internalMax[1], internalMaxTime[1],
                internalMax[2], internalMaxTime[2], internalMax[3], internalMaxTime[3],
                internalMax[4], internalMaxTime[4], internalMax[5], internalMaxTime[5],
                internalMax[6], internalMaxTime[6], internalMax[7], internalMaxTime[7]);
        for (int i = 0; i < internalMax.length; i++) {
            assertTrue(internalMax[i] < 8e-4, "internal-state parity index " + i);
        }
    }

    private static Series series(java.util.Hashtable<String,
            java.util.Hashtable<Integer, StateMonitor.MonitorRecord>> table, String id) {
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
        List<double[]> rows = new ArrayList<>();
        for (String line : Files.readAllLines(REFERENCE)) {
            if (line.isBlank() || line.startsWith("#") || line.startsWith("time_s")) continue;
            String[] values = line.split(",");
            double[] row = new double[values.length];
            for (int i = 0; i < values.length; i++) row[i] = Double.parseDouble(values[i]);
            rows.add(row);
        }
        return rows;
    }

    private record Series(double[] time, double[] value) {
        double at(double target) {
            int index = Arrays.binarySearch(time, target);
            if (index >= 0) return value[index];
            int upper = -index - 1;
            if (upper == 0 || upper == time.length) throw new IllegalArgumentException();
            int lower = upper - 1;
            double fraction = (target - time[lower]) / (time[upper] - time[lower]);
            return value[lower] + fraction * (value[upper] - value[lower]);
        }
    }

    private static final class InternalStateMonitor extends StateMonitor {
        private final IEEE2005ST4BExciter exciter;
        private final List<double[]> internal = new ArrayList<>();

        private InternalStateMonitor(IEEE2005ST4BExciter exciter) {
            this.exciter = exciter;
        }

        @Override
        public boolean onSimuEvent(DStabSimuEvent event) {
            boolean accepted = super.onSimuEvent(event);
            if (accepted && event.getType() == DStabSimuEvent.PlotStepMachineStates
                    && "Bus1-mach1".equals(event.getHashtableData().get(
                            DStabOutSymbol.OUT_SYMBOL_MACH_ID))
                    && exciter.hasInitializedBlocks()) {
                internal.add(new double[] {exciter.getSensedVoltage(),
                        exciter.getVoltageRegulatorIntegrator(),
                        exciter.getVoltageRegulatorOutput(), exciter.getRegulatorDelayOutput(),
                        exciter.getFieldVoltageRegulatorIntegrator(),
                        exciter.getFieldVoltageRegulatorOutput(), exciter.getExcitationFeedback(),
                        exciter.getBridgeVoltage()});
            }
            return accepted;
        }

        private Series[] internalSeries(double[] time) {
            Series[] result = new Series[8];
            for (int column = 0; column < result.length; column++) {
                double[] values = new double[internal.size()];
                for (int row = 0; row < values.length; row++) {
                    values[row] = internal.get(row)[column];
                }
                result[column] = new Series(time, values);
            }
            return result;
        }
    }
}
