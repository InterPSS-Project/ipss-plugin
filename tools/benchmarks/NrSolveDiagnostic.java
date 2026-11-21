package org.interpss.benchmark;

import java.lang.management.ManagementFactory;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.Locale;
import com.google.gson.GsonBuilder;
import com.interpss.core.LoadflowAlgoObjectFactory;
import com.interpss.core.aclf.AclfNetwork;
import com.interpss.core.algo.AclfMethodType;
import com.interpss.core.sparse.solver.SparseEqnSolverProvider;
import jdk.jfr.Event;
import jdk.jfr.Name;
import jdk.jfr.Recording;
import org.interpss.fadapter.matpower.MatpowerDirectParser;

/** Separate diagnostic runs; optional JFR never enables the algorithm's profiler. */
public final class NrSolveDiagnostic {
    @Name("org.interpss.benchmark.NrSolvePhase")
    public static final class SolvePhase extends Event {
        public String phase;
        public int call;
    }

    private static final java.lang.management.ThreadMXBean THREADS = ManagementFactory.getThreadMXBean();
    private static final java.lang.management.CompilationMXBean COMPILER = ManagementFactory.getCompilationMXBean();
    private static final java.util.List<java.lang.management.GarbageCollectorMXBean> COLLECTORS = ManagementFactory.getGarbageCollectorMXBeans();
    private record Counters(long threadCpu, long processCpu, long compilation, long gcCount, long gcTime, long allocation, long classes) {
        static Counters read() {
            var os = ManagementFactory.getOperatingSystemMXBean();
            long process = os instanceof com.sun.management.OperatingSystemMXBean system ? system.getProcessCpuTime() : -1;
            long allocated = THREADS instanceof com.sun.management.ThreadMXBean threads && threads.isThreadAllocatedMemorySupported()
                    ? threads.getThreadAllocatedBytes(Thread.currentThread().threadId()) : -1;
            long count = 0, time = 0;
            for (var collector : COLLECTORS) {
                count += Math.max(0, collector.getCollectionCount());
                time += Math.max(0, collector.getCollectionTime());
            }
            return new Counters(THREADS.getCurrentThreadCpuTime(), process, COMPILER.getTotalCompilationTime(), count, time,
                    allocated, ManagementFactory.getClassLoadingMXBean().getTotalLoadedClassCount());
        }
    }
    private static void reset(AclfNetwork network) {
        for (var bus : network.getBusList())
            bus.setVoltage(bus.isGenPV() || bus.isSwing() ? bus.getDesiredVoltMag() : 1, 0);
    }
    public static void main(String[] args) throws Exception {
        Locale.setDefault(Locale.ROOT);
        Path input = Path.of(args[0]), output = Path.of(args[1]);
        int warmups = Integer.parseInt(args[2]), repeats = Integer.parseInt(args[3]);
        if (warmups < 1 || repeats < 1) throw new IllegalArgumentException("Require initialization warmup and a measurement");
        Recording recording = null;
        if (!args[4].equals("-")) {
            recording = new Recording(jdk.jfr.Configuration.getConfiguration("profile"));
            recording.enable(SolvePhase.class);
            recording.enable("jdk.Compilation").withThreshold(Duration.ZERO);
            recording.enable("jdk.ExecutionSample").withPeriod(Duration.ofMillis(2));
            recording.start();
        }
        SparseEqnSolverProvider.useJavaKlu();
        var network = new MatpowerDirectParser().parse(input.toString());
        network.setPolarCoordinate(true);
        network.getBusLoadLowVoltConfig().setApplyVoltAdjust(false);
        for (var bus : network.getBusList()) if (bus.isSwing()) bus.setDesiredVoltAng(0);
        var algorithm = LoadflowAlgoObjectFactory.createLoadflowAlgorithm(network);
        algorithm.setPreparedNewtonEnabled(true);
        algorithm.setLfMethod(AclfMethodType.NR); algorithm.setTolerance(1e-6); algorithm.setMaxIterations(20);
        algorithm.setInitBusVoltage(false); algorithm.getDataCheckConfig().setAutoTurnLine2Xfr(true);
        algorithm.getNrMethodConfig().setNonDivergent(false);
        algorithm.getNrMethodConfig().setStepSizeOptimization(false);
        algorithm.getNrMethodConfig().setVariableUpdateLimit(false);
        algorithm.getLfAdjAlgo().setApplyAdjustAlgo(false); algorithm.getMessageLog().setEnabled(false);
        var rows = new ArrayList<Object>();
        for (int call = 0; call < warmups + repeats; call++) {
            var event = new SolvePhase(); event.phase = call < warmups ? "warmup" : "measured"; event.call = call;
            event.begin(); var before = Counters.read();
            long start = System.nanoTime();
            reset(network);
            boolean converged = algorithm.loadflow(call == 0);
            long elapsed = System.nanoTime() - start;
            var after = Counters.read(); event.end(); event.commit();
            var termination = algorithm.getTerminationReport();
            if (!converged || !termination.converged() || !algorithm.wasPreparedNewtonUsed())
                throw new IllegalStateException("Invalid diagnostic solve: " + termination);
            var row = new LinkedHashMap<String, Object>();
            row.put("phase", event.phase); row.put("call", call); row.put("elapsed_ms", elapsed / 1e6);
            row.put("main_thread_cpu_ms", (after.threadCpu - before.threadCpu) / 1e6);
            row.put("process_cpu_ms", (after.processCpu - before.processCpu) / 1e6);
            row.put("compilation_counter_ms", after.compilation - before.compilation);
            row.put("gc_count", after.gcCount - before.gcCount); row.put("gc_ms", after.gcTime - before.gcTime);
            row.put("main_thread_allocated_bytes", after.allocation - before.allocation);
            row.put("loaded_classes", after.classes - before.classes);
            row.put("iterations", termination.iteration()); row.put("mismatch_pu", termination.maxEquationMismatchPu());
            rows.add(row);
        }
        if (recording != null) {
            recording.stop(); recording.dump(Path.of(args[4])); recording.close();
        }
        var solver = (com.interpss.core.algo.impl.solver.DefaultNrSolver) algorithm.getLfCalculator().getNrSolver();
        var result = new LinkedHashMap<String, Object>(); result.put("rows", rows);
        result.put("preparations", algorithm.getPreparedNewtonBuildCount());
        result.put("workspace_scopes", solver.getPreparedWorkspaceScopeCount());
        result.put("residual_evaluations", solver.getPreparedResidualCount());
        var origins = new LinkedHashMap<String, String>();
        for (String name : new String[]{"com.interpss.core.algo.impl.solver.DefaultNrSolver", "org.interpss.fadapter.matpower.MatpowerDirectParser"})
            origins.put(name, Class.forName(name).getProtectionDomain().getCodeSource().getLocation().toString());
        result.put("class_origins", origins);
        Files.writeString(output, new GsonBuilder().setPrettyPrinting().create().toJson(result));
        try (var voltages = new java.io.PrintWriter(Files.newBufferedWriter(Path.of(output + "-voltage.csv")))) {
            voltages.println("bus,vm_pu,va_rad");
            for (var bus : network.getBusList()) voltages.printf("%d,%.16g,%.16g%n", bus.getNumber(), bus.getVoltageMag(), bus.getVoltageAng());
        }
        System.out.println(output.getFileName() + ": " + rows.getLast());
    }
}
