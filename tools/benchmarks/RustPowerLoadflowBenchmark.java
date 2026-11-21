package org.interpss.benchmark;

import java.io.PrintWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;

import org.interpss.fadapter.matpower.MatpowerDirectParser;
import com.interpss.core.LoadflowAlgoObjectFactory;
import com.interpss.core.aclf.AclfNetwork;
import com.interpss.core.algo.AclfMethodType;
import com.interpss.core.algo.LoadflowAlgorithm;
import com.interpss.core.sparse.solver.SparseEqnSolverProvider;

/** Standalone repeated, flat-start AC power-flow benchmark; no production changes. */
public final class RustPowerLoadflowBenchmark {
    private static void reset(AclfNetwork net) {
        for (var bus : net.getBusList()) {
            bus.setVoltage(bus.isGenPV() || bus.isSwing() ? bus.getDesiredVoltMag() : 1.0, 0.0);
        }
    }

    public static void main(String[] args) throws Exception {
        Locale.setDefault(Locale.US);
        Path directory = Path.of(args[0]);
        String backend = args[1];
        int fork = Integer.parseInt(args[2]);
        int smallWarmup = Integer.parseInt(args[3]);
        int smallRepeats = Integer.parseInt(args[4]);
        int largeWarmup = Integer.parseInt(args[5]);
        int largeRepeats = Integer.parseInt(args[6]);
        if (backend.equals("java-klu")) SparseEqnSolverProvider.useJavaKlu();
        else if (backend.equals("csj")) SparseEqnSolverProvider.useCSJ();
        else throw new IllegalArgumentException("Unknown backend: " + backend);

        try (PrintWriter rows = new PrintWriter(Files.newBufferedWriter(
                directory.resolve("interpss-" + backend + "-" + fork + ".csv")))) {
            rows.println("case,backend,fork,repeat,elapsed_ms,iterations,max_mismatch_pu");
            for (int c = 7; c < args.length; c++) {
                String name = args[c];
                AclfNetwork net = new MatpowerDirectParser().parse(directory.resolve(name + ".m").toString());
                for (var bus : net.getBusList()) if (bus.isSwing()) bus.setDesiredVoltAng(0.0);
                net.setPolarCoordinate(true);
                net.getBusLoadLowVoltConfig().setApplyVoltAdjust(false);
                LoadflowAlgorithm algorithm = LoadflowAlgoObjectFactory.createLoadflowAlgorithm(net);
                algorithm.setLfMethod(AclfMethodType.NR);
                algorithm.setTolerance(1e-6);
                algorithm.setMaxIterations(20);
                algorithm.setInitBusVoltage(false);
                algorithm.getDataCheckConfig().setAutoTurnLine2Xfr(true);
                algorithm.getNrMethodConfig().setNonDivergent(false);
                algorithm.getNrMethodConfig().setVariableUpdateLimit(false);
                algorithm.getLfAdjAlgo().setApplyAdjustAlgo(false);
                algorithm.getMessageLog().setEnabled(true);
                reset(net);
                if (!algorithm.loadflow(true)) throw new IllegalStateException(name + " initial solve failed: "
                        + algorithm.getTerminationReport() + " " + algorithm.getMessageLog().getFormattedLines());
                algorithm.getMessageLog().setEnabled(false);
                int warmup = net.getNoBus() > 1000 ? largeWarmup : smallWarmup;
                int repeats = net.getNoBus() > 1000 ? largeRepeats : smallRepeats;
                for (int i = -warmup; i < repeats; i++) {
                    long begin = System.nanoTime();
                    reset(net);
                    boolean success = algorithm.loadflow(false);
                    long elapsed = System.nanoTime() - begin;
                    var report = algorithm.getTerminationReport();
                    if (!success || !report.converged() || report.iteration() < 1
                            || report.maxEquationMismatchPu() > 1e-6) {
                        throw new IllegalStateException(name + " invalid solve " + report);
                    }
                    if (i >= 0) rows.printf("%s,%s,%d,%d,%.9f,%d,%.12g%n", name,
                            backend, fork, i, elapsed / 1e6, report.iteration(), report.maxEquationMismatchPu());
                }
                try (PrintWriter voltages = new PrintWriter(Files.newBufferedWriter(directory.resolve(
                        name + "-interpss-" + backend + "-" + fork + "-voltage.csv")))) {
                    voltages.println("bus,vm_pu,va_rad");
                    for (var bus : net.getBusList()) voltages.printf("%d,%.16g,%.16g%n",
                            bus.getNumber(), bus.getVoltageMag(), bus.getVoltageAng());
                }
                rows.flush();
                System.out.printf("InterPSS %s fork %d %s: %d samples, %d iterations%n", backend,
                        fork, name, repeats, algorithm.getTerminationReport().iteration());
            }
        }
    }
}
