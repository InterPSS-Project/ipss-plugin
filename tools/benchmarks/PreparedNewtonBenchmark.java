package org.interpss.benchmark;

import java.nio.file.*;
import java.io.PrintWriter;
import java.util.*;
import org.apache.commons.math3.complex.Complex;
import org.interpss.fadapter.matpower.MatpowerDirectParser;
import com.interpss.core.algo.impl.solver.PreparedNewtonSolver;
import com.interpss.core.LoadflowAlgoObjectFactory;
import com.interpss.core.aclf.*;

/** Measures the prepared numerical context and publication through network objects. */
public final class PreparedNewtonBenchmark {
    public static void main(String[] args) throws Exception {
        Path input = Path.of(args[0]), output = Path.of(args[1]);
        Files.createDirectories(output);
        int fork = Integer.parseInt(args[2]);
        boolean profile = args.length > 3 && args[3].equals("profile");
        boolean cachedAllocation = Arrays.asList(args).contains("cached-generation");
        try (PrintWriter timings = new PrintWriter(Files.newBufferedWriter(output.resolve("interpss-prepared-" + fork + ".csv")))) {
            timings.println("case,backend,fork,repeat,elapsed_ms,iterations,max_mismatch_pu");
            for (String name : new String[]{"case39", "case118", "case9241pegase"}) {
                AclfNetwork net = new MatpowerDirectParser().parse(input.resolve(name + ".m").toString());
                var init = LoadflowAlgoObjectFactory.createLoadflowAlgorithm(net);
                init.getDataCheckConfig().setAutoTurnLine2Xfr(true);
                init.getLfAdjAlgo().setApplyAdjustAlgo(false);
                net.getBusLoadLowVoltConfig().setApplyVoltAdjust(false);
                if (!init.initializeAclfNet()) throw new IllegalStateException(name);
                List<AclfBus> buses = new ArrayList<>();
                for (var bus : net.getBusList()) if (!bus.isGenPV() && !bus.isSwing()) buses.add(bus);
                int npq = buses.size();
                for (var bus : net.getBusList()) if (bus.isGenPV()) buses.add(bus);
                int npv = buses.size() - npq;
                for (var bus : net.getBusList()) if (bus.isSwing()) buses.add(bus);
                int n = buses.size();
                IdentityHashMap<AclfBus, Integer> order = new IdentityHashMap<>();
                List<TreeMap<Integer, Complex>> columns = new ArrayList<>();
                for (int i = 0; i < n; i++) { order.put(buses.get(i), i); columns.add(new TreeMap<>()); }
                for (int i = 0; i < n; i++) columns.get(i).put(i, buses.get(i).yii());
                for (var branch : net.getBranchList()) {
                    int f = order.get(branch.getFromBus()), t = order.get(branch.getToBus());
                    columns.get(t).merge(f, branch.yft(), Complex::add);
                    columns.get(f).merge(t, branch.ytf(), Complex::add);
                }
                int[] yp = new int[n + 1];
                for (int k = 0; k < n; k++) yp[k + 1] = yp[k] + columns.get(k).size();
                int[] yi = new int[yp[n]];
                double[] yg = new double[yi.length], yb = new double[yi.length];
                for (int k = 0; k < n; k++) {
                    int p = yp[k];
                    for (var e : columns.get(k).entrySet()) {
                        yi[p] = e.getKey(); yg[p] = e.getValue().getReal(); yb[p] = e.getValue().getImaginary(); p++;
                    }
                }
                PreparedNewtonSolver solver = new PreparedNewtonSolver(yp, yi, yg, yb, npq, npv);
                double[] p = new double[n], q = new double[n], vm = new double[n], va = new double[n];
                for (int i = 0; i < n; i++) {
                    var bus = buses.get(i);
                    p[i] = bus.getGenP() - bus.getLoadP(); q[i] = bus.getGenQ() - bus.getLoadQ();
                    vm[i] = i >= npq ? bus.getDesiredVoltMag() : 1;
                }
                int warmups = n > 1000 ? 50 : Integer.getInteger("prepared.warmups", 2000), repeats = n > 1000 ? 60 : 3000;
                long solveNs = 0, publishNs = 0, allocateNs = 0;
                for (int r = -warmups; r < repeats; r++) {
                    long start = System.nanoTime();
                    var result = solver.solve(vm, va, p, q, 1e-6, 20);
                    long afterSolve = profile ? System.nanoTime() : 0;
                    for (int i = 0; i < n; i++) buses.get(i).setVoltage(solver.voltageMagnitude(i), solver.voltageAngle(i));
                    long afterPublish = profile ? System.nanoTime() : 0;
                    if (cachedAllocation) {
                        for (int i = 0; i < n; i++) {
                            var bus = buses.get(i);
                            if (bus.isGen() && !bus.getContributeGenList().isEmpty()) {
                                bus.allocateContributeGenQ(1e-6, new Complex(
                                        bus.isSwing() ? solver.calculatedP(i) + bus.getLoadP() : bus.getGenP(),
                                        solver.calculatedQ(i) + bus.getLoadQ()));
                            }
                        }
                    } else net.allocateContributeGenQ(1e-6);
                    long elapsed = System.nanoTime() - start;
                    if (!result.converged() || result.iterations() < 1) throw new IllegalStateException(name + " " + result);
                    if (r >= 0) timings.printf(Locale.US, "%s,prepared,%d,%d,%.9f,%d,%.12g%n", name, fork, r,
                            elapsed / 1e6, result.iterations(), Math.max(result.maxP(), result.maxQ()));
                    if (profile && r >= 0) { solveNs += afterSolve - start; publishNs += afterPublish - afterSolve; allocateNs += start + elapsed - afterPublish; }
                }
                try (PrintWriter voltages = new PrintWriter(Files.newBufferedWriter(output.resolve(name + "-interpss-prepared-" + fork + "-voltage.csv")))) {
                    voltages.println("bus,vm_pu,va_rad");
                    for (var bus : net.getBusList()) voltages.printf(Locale.US, "%d,%.16g,%.16g%n", bus.getNumber(), bus.getVoltageMag(), bus.getVoltageAng());
                }
                timings.flush();
                System.out.printf("%s: dimension %d, analyze %d, factor %d, refactor %d, pivot fallback %d%n", name,
                        solver.equationCount(), solver.symbolicAnalyses(), solver.factorizations(), solver.refactorizations(), solver.pivotFallbacks());
                if (profile) System.out.printf("%s phase means: solve %.6f ms, publish %.6f ms, allocation %.6f ms%n", name,
                        solveNs / repeats / 1e6, publishNs / repeats / 1e6, allocateNs / repeats / 1e6);
            }
        }
    }
}
