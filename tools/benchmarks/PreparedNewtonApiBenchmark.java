package org.interpss.benchmark;

import java.io.PrintWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import java.util.LinkedHashMap;
import com.google.gson.GsonBuilder;
import org.interpss.fadapter.matpower.MatpowerDirectParser;
import com.interpss.core.LoadflowAlgoObjectFactory;
import com.interpss.core.aclf.AclfNetwork;
import com.interpss.core.algo.AclfMethodType;
import com.interpss.core.sparse.solver.SparseEqnSolverProvider;

/** Matched measurements through the maintained public load-flow API. */
public final class PreparedNewtonApiBenchmark {
    @SuppressWarnings({"unchecked", "rawtypes"})
    private static boolean selectExecution(Object algorithm, boolean prepared) throws Exception {
        try {
            Class<?> mode = Class.forName("com.interpss.core.algo.NrExecutionMode");
            Object requested = Enum.valueOf((Class<? extends Enum>) mode, prepared ? "CACHED" : "GENERIC");
            algorithm.getClass().getMethod("setNrExecutionMode", mode).invoke(algorithm, requested);
            return true;
        } catch (ClassNotFoundException | NoSuchMethodException legacy) {
            if (Boolean.getBoolean("ipss.benchmark.requireExecutionReport"))
                throw new IllegalStateException("This benchmark requires the explicit NR execution contract", legacy);
            return false;
        }
    }

    private static Object executionReport(Object algorithm, boolean prepared, boolean explicit) throws Exception {
        if (!explicit) return null;
        Object report = algorithm.getClass().getMethod("getNrExecutionReport").invoke(algorithm);
        String requested = report.getClass().getMethod("requestedMode").invoke(report).toString();
        String actual = report.getClass().getMethod("actualStrategy").invoke(report).toString();
        String expected = prepared ? "CACHED" : "GENERIC";
        if (!expected.equals(requested) || !expected.equals(actual))
            throw new IllegalStateException("Unexpected requested/actual NR mode: " + report);
        return report;
    }

    private static long compilationMillis() {
        var compiler = java.lang.management.ManagementFactory.getCompilationMXBean();
        return compiler != null && compiler.isCompilationTimeMonitoringSupported() ? compiler.getTotalCompilationTime() : -1;
    }

    private static long processCpuNanos() {
        var operatingSystem = java.lang.management.ManagementFactory.getOperatingSystemMXBean();
        return operatingSystem instanceof com.sun.management.OperatingSystemMXBean system ? system.getProcessCpuTime() : -1;
    }

    private static Long counterDelta(long end, long start) {
        return end >= 0 && start >= 0 ? end - start : null;
    }

    private static void reset(AclfNetwork net, double[][] start) {
        int i = 0;
        for (var bus : net.getBusList()) {
            bus.setVoltage(start == null ? (bus.isGenPV() || bus.isSwing() ? bus.getDesiredVoltMag() : 1) : start[i][0],
                    start == null ? 0 : start[i][1]);
            i++;
        }
    }

    public static void main(String[] args) throws Exception {
        Locale.setDefault(Locale.ROOT);
        Path output = Path.of(args[0]);
        String mode = args[1];
        boolean prepared = mode.equals("prepared");
        if (!prepared && !mode.equals("generic")) throw new IllegalArgumentException(mode);
        int fork = Integer.parseInt(args[2]);
        int smallWarmups = Integer.parseInt(args[3]), smallRepeats = Integer.parseInt(args[4]);
        int largeWarmups = Integer.parseInt(args[5]), largeRepeats = Integer.parseInt(args[6]);
        SparseEqnSolverProvider.useJavaKlu();
        var diagnostics = new LinkedHashMap<String, Object>();
        var execution = new LinkedHashMap<String, Object>();
        try (var timings = new PrintWriter(Files.newBufferedWriter(output.resolve("interpss-" + mode + "-" + fork + ".csv")))) {
            timings.println("case,backend,fork,repeat,elapsed_ms,iterations,max_mismatch_pu");
            for (int c = 7; c < args.length; c++) {
                String name = args[c];
                AclfNetwork net = new MatpowerDirectParser().parse(output.resolve(name + ".m").toString());
                Path startPath = output.resolve(name + "-start.csv");
                double[][] startVoltage = null;
                if (Files.exists(startPath)) {
                    startVoltage = Files.readAllLines(startPath).stream().skip(1).map(line -> {
                        String[] parts = line.split(",");
                        return new double[]{Double.parseDouble(parts[0]), Double.parseDouble(parts[1])};
                    }).toArray(double[][]::new);
                    if (startVoltage.length != net.getNoBus()) throw new IllegalArgumentException("Start size mismatch");
                }
                net.setPolarCoordinate(true); net.getBusLoadLowVoltConfig().setApplyVoltAdjust(false);
                for (var bus : net.getBusList()) if (bus.isSwing()) bus.setDesiredVoltAng(0);
                var algorithm = LoadflowAlgoObjectFactory.createLoadflowAlgorithm(net);
                algorithm.setPreparedNewtonEnabled(prepared);
                boolean explicitExecution = selectExecution(algorithm, prepared);
                algorithm.setLfMethod(AclfMethodType.NR); algorithm.setTolerance(1e-6); algorithm.setMaxIterations(20);
                algorithm.setInitBusVoltage(false); algorithm.getDataCheckConfig().setAutoTurnLine2Xfr(true);
                algorithm.getNrMethodConfig().setNonDivergent(false);
                algorithm.getNrMethodConfig().setStepSizeOptimization(false);
                algorithm.getNrMethodConfig().setVariableUpdateLimit(false);
                algorithm.getLfAdjAlgo().setApplyAdjustAlgo(false); algorithm.getMessageLog().setEnabled(false);
                reset(net, startVoltage);
                long compilationStart = compilationMillis(), processCpuStart = processCpuNanos();
                if (!algorithm.loadflow(true)) throw new IllegalStateException(name + " initialization failed");
                executionReport(algorithm, prepared, explicitExecution);
                var host = (com.interpss.core.algo.LfCalculatorHost) algorithm.getLfCalculator();
                Object firstSymbolic = prepared ? ((com.interpss.core.sparse.solver.klu.KLUSquareMatrixEqnDoubleSolver.SymbolStruct)
                        host.nrEngine().lfEqnJMatrixCache.getSymbolTable()).symbolTable() : null;
                int warmups = net.getNoBus() > 1000 ? largeWarmups : smallWarmups;
                int repeats = net.getNoBus() > 1000 ? largeRepeats : smallRepeats;
                boolean initialSolveIsWarmup = Boolean.getBoolean("ipss.benchmark.initialSolveIsWarmup");
                if (initialSolveIsWarmup && warmups < 1) throw new IllegalArgumentException("Initialization requires one warmup");
                int requestedWarmups = warmups;
                if (initialSolveIsWarmup) warmups--;
                int loadflowCalls = 1;
                for (int r = -warmups; r < repeats; r++) {
                    long start = System.nanoTime();
                    reset(net, startVoltage);
                    boolean converged = algorithm.loadflow(false);
                    long elapsed = System.nanoTime() - start;
                    loadflowCalls++;
                    var report = algorithm.getTerminationReport();
                    if (!converged || !report.converged() || report.iteration() < 1
                            || report.maxEquationMismatchPu() > 1e-6 || algorithm.wasPreparedNewtonUsed() != prepared)
                        throw new IllegalStateException(name + " invalid " + mode + " solve: " + report);
                    if (r >= 0) timings.printf("%s,%s,%d,%d,%.9f,%d,%.12g%n", name, mode, fork, r,
                            elapsed / 1e6, report.iteration(), report.maxEquationMismatchPu());
                }
                if (prepared && algorithm.getPreparedNewtonBuildCount() != 1) throw new IllegalStateException("Unexpected rebuilds: " + name);
                var actualExecution = new LinkedHashMap<String, Object>();
                actualExecution.put("selectionApi", explicitExecution ? "NrExecutionMode" : "legacy_prepared_boolean");
                actualExecution.put("report", executionReport(algorithm, prepared, explicitExecution));
                actualExecution.put("loadflowCalls", loadflowCalls);
                actualExecution.put("warmupLoadflowCalls", requestedWarmups + (initialSolveIsWarmup ? 0 : 1));
                actualExecution.put("measuredLoadflowCalls", repeats);
                actualExecution.put("batchCompilationMillis", counterDelta(compilationMillis(), compilationStart));
                actualExecution.put("batchProcessCpuNanos", counterDelta(processCpuNanos(), processCpuStart));
                actualExecution.put("batchScope", "initialization, warmups, measured solves and untimed validation");
                execution.put(name, actualExecution);
                if (prepared) {
                    var matrix = (com.interpss.core.sparse.impl.klu.KLUSparseEqnMatrix2x2Impl) host.nrEngine().lfEqnJMatrixCache;
                    if (matrix.preparedCsc() == null) throw new IllegalStateException("Prepared Jacobian hooks were not used");
                    var nrSolver = (com.interpss.core.algo.impl.solver.DefaultNrSolver) host.getNrSolver();
                    if (nrSolver.getPreparedResidualCount() == 0 || nrSolver.getPreparedSolveScopeCount() == 0 || nrSolver.getPreparedWorkspaceScopeCount() == 0)
                        throw new IllegalStateException("Integrated standard NR workspaces were not used");
                    if (firstSymbolic != ((com.interpss.core.sparse.solver.klu.KLUSquareMatrixEqnDoubleSolver.SymbolStruct)
                            matrix.getSymbolTable()).symbolTable()) throw new IllegalStateException("Symbolic structure was not retained");
                    var counters = new LinkedHashMap<String, Long>();
                    counters.put("preparations", algorithm.getPreparedNewtonBuildCount());
                    counters.put("scalarEquations", (long) matrix.getDimension());
                    counters.put("structuralEntries", (long) matrix.getTotalElements());
                    counters.put("factorizedEntries", matrix.getFactorizedElementCount());
                    counters.put("preparedResidualEvaluations", nrSolver.getPreparedResidualCount());
                    counters.put("preparedWorkspaceScopes", nrSolver.getPreparedWorkspaceScopeCount());
                    counters.put("stableSolveScopes", nrSolver.getPreparedSolveScopeCount());
                    counters.put("retainedSymbolicAcrossSolves", 1L);
                    counters.put("loadflowCalls", (long) loadflowCalls);
                    counters.put("warmupLoadflowCalls", (long) requestedWarmups + (initialSolveIsWarmup ? 0 : 1));
                    counters.put("measuredLoadflowCalls", (long) repeats);
                    counters.put("initialSolveCountedAsWarmup", initialSolveIsWarmup ? 1L : 0L);
                    diagnostics.put(name, counters);
                }
                try (var voltages = new PrintWriter(Files.newBufferedWriter(output.resolve(name + "-interpss-" + mode + "-" + fork + "-voltage.csv")))) {
                    voltages.println("bus,vm_pu,va_rad");
                    for (var bus : net.getBusList()) voltages.printf("%d,%.16g,%.16g%n", bus.getNumber(), bus.getVoltageMag(), bus.getVoltageAng());
                }
                timings.flush();
                System.out.printf("%s %s fork %d: %d samples, %d iterations, %d preparations%n", mode, name, fork,
                        repeats, algorithm.getTerminationReport().iteration(), algorithm.getPreparedNewtonBuildCount());
            }
        }
        Files.writeString(output.resolve("diagnostics-" + mode + "-" + fork + ".json"),
                new GsonBuilder().setPrettyPrinting().create().toJson(diagnostics));
        Files.writeString(output.resolve("execution-" + mode + "-" + fork + ".json"),
                new GsonBuilder().setPrettyPrinting().create().toJson(execution));
    }
}
