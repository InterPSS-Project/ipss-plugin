package org.interpss.plugin.sensitivity;

import static com.interpss.core.DclfAlgoObjectFactory.createContingencyAnalysisAlgorithm;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Random;
import java.util.Set;

import org.interpss.CorePluginFactory;
import org.interpss.CorePluginTestSetup;
import org.interpss.fadapter.IpssFileAdapter;
import org.interpss.plugin.sensitivity.DcSensitivityStudyDefinition.CalculationOptions;
import org.interpss.plugin.sensitivity.DcSensitivityStudyDefinition.EndpointCatalog;
import org.interpss.plugin.sensitivity.DcSensitivityStudyDefinition.MonitorSet;
import org.interpss.plugin.sensitivity.DcSensitivityStudyDefinition.MultiOutageLodfSpec;
import org.interpss.plugin.sensitivity.DcSensitivityStudyDefinition.NetworkReference;
import org.interpss.plugin.sensitivity.DcSensitivityStudyDefinition.OutageGroup;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import com.interpss.core.LoadflowAlgoObjectFactory;
import com.interpss.core.aclf.AclfBranch;
import com.interpss.core.aclf.AclfNetwork;
import com.interpss.core.algo.AclfMethodType;
import com.interpss.core.algo.dclf.DclfMethod;

/** Opt-in accuracy and timing benchmark; CSV artifacts are written below target. */
@Tag("extended")
class MultiOutageLodfDclfBenchmarkTest extends CorePluginTestSetup {
    private static final int WARMUPS = 3;
    private static final int REPEATS = 9;
    private static final double TOLERANCE_MW = 1.0e-6;

    @ParameterizedTest
    @ValueSource(ints = {14, 118, 300})
    void compareWithLinesPhysicallyOpened(int buses) throws Exception {
        AclfNetwork sensitivityNet = load(buses);
        List<String> allBranches = sensitivityNet.getBranchList().stream()
                .filter(AclfBranch::isActive).map(AclfBranch::getId).toList();
        // Separate base and post-outage networks keep the reference independent of the runner.
        Map<String, Double> baseFlows = direct(load(buses), allBranches).flows();
        List<String> summary = new ArrayList<>(List.of(
                "case,outages,order,monitors,max_abs_error_mw,rms_error_mw,runner_median_ms,direct_median_ms,outage_ids"));
        List<String> details = new ArrayList<>(List.of(
                "case,outages,order,branch,base_mw,predicted_mw,direct_mw,abs_error_mw"));
        List<String> timings = new ArrayList<>(List.of(
                "case,outages,order,repeat,runner_ms,direct_ms"));
        Path output = Path.of("target", "mlodf-dclf-benchmark");
        Files.createDirectories(output);

        for (int count : List.of(2, 3, 5)) {
            List<String> outages = selectConnectedOutages(sensitivityNet, count, 20260925L + count);
            List<String> monitors = allBranches.stream().filter(id -> !outages.contains(id)).toList();
            AclfNetwork postNet = load(buses);
            for (String id : outages) postNet.getBranch(id).setStatus(false);
            assertTrue(isConnected(postNet, Set.of()), "Post-outage network must remain connected");

            for (String order : List.of("forward", "reverse")) {
                List<String> orderedOutages = order.equals("forward") ? outages : outages.reversed();
                DcSensitivityStudyDefinition study = new DcSensitivityStudyDefinition(
                        DcSensitivityStudyDefinition.CURRENT_SCHEMA_VERSION, "benchmark", "DCLF comparison",
                        new NetworkReference("", ""), EndpointCatalog.empty(),
                        List.of(new MultiOutageLodfSpec(List.of(new OutageGroup("outage", "outage", orderedOutages)),
                                new MonitorSet(monitors, List.of()))), CalculationOptions.defaults());
                List<Double> runnerMs = new ArrayList<>();
                List<Double> directMs = new ArrayList<>();
                double maxError = 0.0;
                double sumSquaredError = 0.0;
                for (int iteration = -WARMUPS; iteration < REPEATS; iteration++) {
                    TimedFlows predicted;
                    TimedFlows actual;
                    // Alternate execution order to reduce systematic timing bias.
                    if ((iteration & 1) == 0) {
                        predicted = predict(sensitivityNet, study, monitors, baseFlows, count);
                        actual = direct(postNet, monitors);
                    } else {
                        actual = direct(postNet, monitors);
                        predicted = predict(sensitivityNet, study, monitors, baseFlows, count);
                    }
                    for (String id : monitors) {
                        double expected = actual.flows().get(id);
                        double observed = predicted.flows().get(id);
                        double error = Math.abs(expected - observed);
                        assertEquals(expected, observed, TOLERANCE_MW,
                                "IEEE" + buses + " N-" + count + " " + order + " " + id);
                        if (iteration >= 0) {
                            maxError = Math.max(maxError, error);
                            sumSquaredError += error * error;
                        }
                        if (iteration == 0) {
                            details.add(String.format(Locale.ROOT, "IEEE%d,%d,%s,%s,%.12g,%.12g,%.12g,%.12g",
                                    buses, count, order, id, baseFlows.get(id), observed, expected, error));
                        }
                    }
                    if (iteration >= 0) {
                        runnerMs.add(predicted.millis());
                        directMs.add(actual.millis());
                        timings.add(String.format(Locale.ROOT, "IEEE%d,%d,%s,%d,%.6f,%.6f",
                                buses, count, order, iteration, predicted.millis(), actual.millis()));
                    }
                }
                String row = String.format(Locale.ROOT, "IEEE%d,%d,%s,%d,%.12g,%.12g,%.6f,%.6f,%s",
                        buses, count, order, monitors.size(), maxError,
                        Math.sqrt(sumSquaredError / (REPEATS * monitors.size())),
                        median(runnerMs), median(directMs), String.join(";", orderedOutages));
                summary.add(row);
                System.out.println("MLODF_DCLF_BENCHMARK," + row);
            }
        }
        Files.write(output.resolve("ieee" + buses + "-summary.csv"), summary);
        Files.write(output.resolve("ieee" + buses + "-flows.csv"), details);
        Files.write(output.resolve("ieee" + buses + "-timings.csv"), timings);
    }

    private static TimedFlows predict(AclfNetwork net, DcSensitivityStudyDefinition study,
            List<String> monitors, Map<String, Double> baseFlows, int outageCount) {
        long start = System.nanoTime();
        InMemorySensitivityResultSink sink = new InMemorySensitivityResultSink();
        var manifest = new DefaultDcSensitivityRunner().run(net, study, sink);
        Map<String, Double> predicted = new LinkedHashMap<>();
        for (String id : monitors) predicted.put(id, baseFlows.get(id));
        for (var row : sink.rows()) {
            predicted.merge(row.monitorId(), row.factor() * baseFlows.get(row.outageId()), Double::sum);
        }
        double elapsed = (System.nanoTime() - start) / 1.0e6;
        assertTrue(manifest.complete());
        assertTrue(manifest.diagnostics().isEmpty(), "Benchmark must not silently skip invalid inputs");
        assertEquals((long) monitors.size() * outageCount, manifest.candidateCount());
        assertEquals(monitors.size() * outageCount, sink.rows().size());
        return new TimedFlows(predicted, elapsed);
    }

    private static TimedFlows direct(AclfNetwork net, List<String> monitors) throws Exception {
        long start = System.nanoTime();
        // A new algorithm rebuilds/factorizes the matrix for the current branch statuses.
        var algo = createContingencyAnalysisAlgorithm(net);
        boolean solved = algo.calculateDclf(DclfMethod.STD);
        Map<String, Double> flows = new LinkedHashMap<>();
        for (String id : monitors) flows.put(id, algo.getDclfAlgoBranch(id).getDclfFlow() * net.getBaseMva());
        double elapsed = (System.nanoTime() - start) / 1.0e6;
        assertTrue(solved, "Direct DCLF must converge");
        return new TimedFlows(flows, elapsed);
    }

    private static List<String> selectConnectedOutages(AclfNetwork net, int count, long seed) {
        List<AclfBranch> candidates = new ArrayList<>(net.getBranchList().stream()
                .filter(branch -> branch.isActive() && branch.isLine() && !branch.isConnect2RefBus())
                .toList());
        Collections.shuffle(candidates, new Random(seed));
        List<String> selected = new ArrayList<>();
        for (AclfBranch branch : candidates) {
            selected.add(branch.getId());
            if (!isConnected(net, new HashSet<>(selected))) selected.removeLast();
            if (selected.size() == count) return List.copyOf(selected);
        }
        throw new AssertionError("Unable to select " + count + " connected line outages");
    }

    private static boolean isConnected(AclfNetwork net, Set<String> removed) {
        Map<String, List<String>> adjacent = new HashMap<>();
        net.getBusList().stream().filter(bus -> bus.isActive())
                .forEach(bus -> adjacent.put(bus.getId(), new ArrayList<>()));
        for (AclfBranch branch : net.getBranchList()) {
            String from = branch.getFromBus().getId();
            String to = branch.getToBus().getId();
            if (branch.isActive() && !removed.contains(branch.getId())
                    && adjacent.containsKey(from) && adjacent.containsKey(to)) {
                adjacent.get(from).add(to);
                adjacent.get(to).add(from);
            }
        }
        Set<String> visited = new HashSet<>();
        ArrayDeque<String> queue = new ArrayDeque<>();
        queue.add(adjacent.keySet().iterator().next());
        while (!queue.isEmpty()) {
            String bus = queue.removeFirst();
            if (visited.add(bus)) queue.addAll(adjacent.get(bus));
        }
        return visited.size() == adjacent.size();
    }

    private static AclfNetwork load(int buses) throws Exception {
        AclfNetwork net = CorePluginFactory.getFileAdapter(IpssFileAdapter.FileFormat.IEEECDF)
                .load("testData/adpter/ieee_format/ieee" + buses + ".ieee").getAclfNet();
        var loadflow = LoadflowAlgoObjectFactory.createLoadflowAlgorithm(net);
        loadflow.setLfMethod(AclfMethodType.NR);
        assertTrue(loadflow.loadflow());
        return net;
    }

    private static double median(List<Double> samples) {
        List<Double> sorted = new ArrayList<>(samples);
        Collections.sort(sorted);
        return sorted.get(sorted.size() / 2);
    }

    private record TimedFlows(Map<String, Double> flows, double millis) { }
}
