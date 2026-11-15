package org.interpss.core.dstab;

import static org.junit.jupiter.api.Assertions.*;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.zip.GZIPInputStream;

import org.apache.commons.math3.complex.Complex;
import org.interpss.CorePluginTestSetup;
import org.interpss.dstab.algo.DefaultDynamicSimulationSolver;
import com.interpss.dstab.svc.Svsmo3t2Model;
import org.interpss.fadapter.psse.PSSEMultiFileLoader;
import org.junit.jupiter.api.Test;

import com.interpss.core.acsc.fault.SimpleFaultCode;
import com.interpss.dstab.DStabObjectFactory;
import com.interpss.dstab.StaticLoadModel;
import com.interpss.dstab.algo.DynamicSimuAlgorithm;
import com.interpss.dstab.algo.DynamicSimuMethod;
import com.interpss.dstab.cache.StateMonitor;
import com.interpss.dstab.common.DStabSimuException;

/** Independent all-sample fault comparison, including both sides of each event. */
public class Svsmo3t2CoupledSmibTest extends CorePluginTestSetup {
    private static final double STEP = .0005;
    private static final double NETWORK_TOLERANCE = 1e-6;

    @Test void sixStatesCurrentAndNetworkBoundaryFollowTheIndependentFault() throws Exception {
        compare(false);
    }

    @Test void sharedStaggeredStatesFollowTheSameIndependentFault() throws Exception {
        compare(true);
    }

    private static void compare(boolean staggered) throws Exception {
        var fixture = Path.of("testData/psse/v33/SVSMO3T2");
        var context = new PSSEMultiFileLoader().loadDStab(fixture.resolve("smib.raw").toString(),
                fixture.resolve("smib.dyr").toString());
        var net = context.getDStabilityNet();
        var algorithm = context.getDynSimuAlgorithm();
        net.setStaticLoadIncludedInYMatrix(true);
        net.setStaticLoadModel(StaticLoadModel.CONST_Z);
        net.setReactiveStaticLoadModel(StaticLoadModel.CONST_Z);
        algorithm.getAclfAlgorithm().setTolerance(1e-10);
        algorithm.getAclfAlgorithm().getLfAdjAlgo().setLocalContinuousSvcAsPv(true);
        algorithm.getAclfAlgorithm().getLfAdjAlgo().getVoltAdjConfig().setAdjTolerance(1e-9);
        assertTrue(algorithm.getAclfAlgorithm().loadflow());
        algorithm.setSimuMethod(DynamicSimuMethod.MODIFIED_EULER);
        algorithm.setSimuStepSec(STEP);
        algorithm.setSimuOutputHandler(new StateMonitor());
        net.addDynamicEvent(DStabObjectFactory.createBusFaultEvent("Bus3", net,
                SimpleFaultCode.GROUND_3P, new Complex(0, 100. / 529.), null, .05, .05), "fault");
        var recorder = new Recording(algorithm);
        algorithm.setSolver(staggered ? new StaggeredRecorder(algorithm, recorder) : new Recorder(algorithm, recorder));
        assertTrue(algorithm.initialization());
        recorder.enabled = true;
        double[] initial = recorder.frame(0);
        while (algorithm.getSimuTime() < .8 - STEP / 2) assertTrue(algorithm.solveDEqnStep(true));
        recorder.frames.add(recorder.frame(algorithm.getSimuTime()));
        assertEquals(NETWORK_TOLERANCE, ((com.interpss.dstab.algo.dynamic.DynamicSimulationSolverBase)algorithm.getSolver())
                .getLastNetworkSolutionDiagnostics().voltageTolerance(), 0.);
        var rows = reference();
        assertEquals(rows.stream().filter(r -> r[0] >= 0).count(), recorder.frames.size());
        String[] names = {"V_1", "V_2", "V_3", "MACHINE_ANGLE", "VAR_0",
                "STATE_0", "STATE_1", "STATE_2", "STATE_3", "STATE_4", "STATE_5"};
        String[] header;
        try (var reader = reader()) { header = reader.readLine().split(","); }
        int[] columns = Arrays.stream(names).mapToInt(name -> Arrays.asList(header).indexOf(name)).toArray();
        var max = new double[names.length];
        var maxTime = new double[names.length];
        var squares = new double[names.length];
        double prefaultAngleMaximum = 0;
        int sample = 0;
        Path output = Files.createTempDirectory(Path.of("target"), staggered ? "svsmo3t2-staggered-fault-" : "svsmo3t2-coupled-").resolve("actual.csv");
        try (var writer = org.interpss.core.dstab.reference.BenchmarkOutputGuard.newWriter(output,
                org.interpss.core.dstab.reference.BenchmarkOutputGuard.estimateCsvBytes(rows.size(), names.length + 1))) {
            writer.write("time_s,actual_time_s," + String.join(",", names) + "\n");
            for (var row : rows) {
                double[] actual = row[0] < 0 ? initial : recorder.frames.get(sample++);
                if (row[0] >= 0) assertEquals(row[0], actual[0], 2e-5);
                writer.write(row[0] + "," + actual[0]);
                for (int i = 0; i < names.length; i++) {
                    double error = Math.abs(actual[i + 1] - row[columns[i]]);
                    assertTrue(Double.isFinite(error), names[i]);
                    squares[i] += error * error;
                    if (i == 3 && row[0] < .05) prefaultAngleMaximum = Math.max(prefaultAngleMaximum, error);
                    if (error > max[i]) { max[i] = error; maxTime[i] = row[0]; }
                    writer.write("," + actual[i + 1]);
                }
                writer.write('\n');
            }
        }
        var metrics = new LinkedHashMap<String, Object>();
        for (int i = 0; i < names.length; i++) metrics.put(names[i],
                Map.of("maximum", max[i], "time_s", maxTime[i], "rmse", Math.sqrt(squares[i] / rows.size())));
        System.out.println("SVSMO3T2 " + (staggered ? "STAGGERED_EULER" : "MODIFIED_EULER")
                + " tolerance=" + NETWORK_TOLERANCE + " comparison " + metrics + " trace=" + output);
        // The controller is explicitly PARTIAL until the complete model matrix is qualified.
        for (int i = 0; i < 3; i++) {
            final int col = columns[i];
            double range = rows.stream().mapToDouble(r -> r[col]).max().orElseThrow()
                    - rows.stream().mapToDouble(r -> r[col]).min().orElseThrow();
            assertTrue(Math.sqrt(squares[i] / rows.size()) / range < .001, names[i] + " range NRMSE");
            if (staggered) {
                assertTrue(max[i] < .000007, names[i] + " staggered maximum");
                assertTrue(Math.sqrt(squares[i] / rows.size()) < .000001, names[i] + " staggered RMSE");
            }
        }
        assertTrue(Math.sqrt(squares[3] / rows.size()) < .01, "Rotor-angle RMSE degrees");
        assertTrue(prefaultAngleMaximum < .002, "Prefault maximum rotor-angle error degrees");
        if (staggered) {
            assertTrue(max[3] < .0013, "Staggered rotor-angle maximum degrees");
            assertTrue(Math.sqrt(squares[3] / rows.size()) < .0005, "Staggered rotor-angle RMSE degrees");
        }
        double[] stateMaximum = staggered ? new double[]{.000012, .0000015, .00003, 1e-12, 1e-12, .000015}
                : new double[]{.0016, .0013, .012, 1e-12, 1e-12, .0003};
        double[] stateRmse = staggered ? new double[]{.0000014, .00000016, .0000048, 1e-12, 1e-12, .0000018}
                : new double[]{.00006, .00006, .0006, 1e-12, 1e-12, .00003};
        for (int i = 0; i < 6; i++) {
            assertTrue(max[i + 5] < stateMaximum[i], "STATE_" + i + " maximum absolute error");
            assertTrue(Math.sqrt(squares[i + 5] / rows.size()) < stateRmse[i], "STATE_" + i + " RMS error");
        }
    }

    private static BufferedReader reader() throws Exception {
        var stream = Svsmo3t2CoupledSmibTest.class.getResourceAsStream("/dynamic-models/svsmo3t2/fault.csv.gz");
        assertNotNull(stream);
        byte[] data;
        try (var gzip = new GZIPInputStream(stream)) { data = gzip.readAllBytes(); }
        assertEquals("c63767ce0ecf989be0dd45253f341a4b6589e2faf2a6dc0a050569b734d7050d",
                java.util.HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256").digest(data)));
        return new BufferedReader(new java.io.StringReader(new String(data, StandardCharsets.UTF_8)));
    }
    private static List<double[]> reference() throws Exception {
        try (var reader = reader()) {
            reader.readLine();
            return reader.lines().map(line -> Arrays.stream(line.split(",")).mapToDouble(Double::parseDouble).toArray()).toList();
        }
    }
    private static final class Recorder extends DefaultDynamicSimulationSolver {
        final Recording recording;
        Recorder(DynamicSimuAlgorithm algorithm, Recording recording) { super(algorithm); this.recording = recording; converge_tol = NETWORK_TOLERANCE; }
        @Override public void beforeStep(double time) throws DStabSimuException {
            recording.before(time);
            super.beforeStep(time);
        }
        @Override public boolean networkSolutionStep() throws DStabSimuException {
            boolean solved = super.networkSolutionStep();
            recording.solved(solved, networkSolutionStage);
            return solved;
        }
    }

    private static final class StaggeredRecorder extends org.interpss.dstab.algo.StaggeredEulerSolver {
        final Recording recording;
        StaggeredRecorder(DynamicSimuAlgorithm algorithm, Recording recording) { super(algorithm); this.recording = recording; setNetworkSolutionTolerance(NETWORK_TOLERANCE); }
        @Override public void beforeStep(double time) throws DStabSimuException {
            recording.before(time);
            super.beforeStep(time);
        }
        @Override public boolean networkSolutionStep() throws DStabSimuException {
            boolean solved = super.networkSolutionStep();
            recording.solved(solved, networkSolutionStage);
            return solved;
        }
    }

    private static final class Recording {
        boolean enabled;
        final List<double[]> frames = new ArrayList<>();
        final DynamicSimuAlgorithm algorithm;
        Recording(DynamicSimuAlgorithm algorithm) { this.algorithm = algorithm; }
        void before(double time) {
            if (enabled && (Math.abs(time - .05) < 1e-9 || Math.abs(time - .1) < 1e-9)) frames.add(frame(time));
        }
        void solved(boolean solved, String stage) {
            if (solved && enabled && "INITIAL".equals(stage)) frames.add(frame(algorithm.getSimuTime()));
        }
        double[] frame(double time) {
            var net = algorithm.getNetwork();
            var model = net.getDStabBus("Bus3").getDynamicBusDeviceList().stream()
                    .filter(Svsmo3t2Model.class::isInstance).map(Svsmo3t2Model.class::cast).findFirst().orElseThrow();
            var states = model.getNamedStates().values().iterator();
            return new double[]{time, net.getBus("Bus1").getVoltageMag(), net.getBus("Bus2").getVoltageMag(),
                    net.getBus("Bus3").getVoltageMag(), Math.toDegrees(net.getMachine("Bus2-mach1").getAngle()),
                    model.getCurrent(), states.next(), states.next(), states.next(), states.next(), states.next(), states.next()};
        }
    }
}
