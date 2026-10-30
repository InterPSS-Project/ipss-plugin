package org.interpss.benchmark;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import com.google.gson.GsonBuilder;
import org.interpss.fadapter.matpower.MatpowerDirectParser;
import com.interpss.core.LoadflowAlgoObjectFactory;
import com.interpss.core.aclf.AclfNetwork;
import com.interpss.core.algo.AclfMethodType;
import com.interpss.core.algo.impl.LfNrProfiler;
import com.interpss.core.sparse.solver.SparseEqnSolverProvider;
import com.interpss.core.sparse.solver.klu.KLUSolverProfiler;

/** Diagnostic phase counters, kept separate from performance measurements. */
public final class RustPowerLoadflowProfile {
    private static void reset(AclfNetwork net) {
        for (var bus : net.getBusList()) bus.setVoltage(
                bus.isGenPV() || bus.isSwing() ? bus.getDesiredVoltMag() : 1.0, 0.0);
    }

    public static void main(String[] args) throws Exception {
        Path inputs = Path.of(args[0]), output = Path.of(args[1]);
        Files.createDirectories(output);
        SparseEqnSolverProvider.useJavaKlu();
        for (String name : new String[]{"case39", "case118", "case9241pegase"}) {
            var net = new MatpowerDirectParser().parse(inputs.resolve(name + ".m").toString());
            for (var bus : net.getBusList()) if (bus.isSwing()) bus.setDesiredVoltAng(0.0);
            net.setPolarCoordinate(true);
            net.getBusLoadLowVoltConfig().setApplyVoltAdjust(false);
            var algorithm = LoadflowAlgoObjectFactory.createLoadflowAlgorithm(net);
            algorithm.setLfMethod(AclfMethodType.NR);
            algorithm.setTolerance(1e-6);
            algorithm.setMaxIterations(20);
            algorithm.setInitBusVoltage(false);
            algorithm.getDataCheckConfig().setAutoTurnLine2Xfr(true);
            algorithm.getNrMethodConfig().setNonDivergent(false);
            algorithm.getNrMethodConfig().setVariableUpdateLimit(false);
            algorithm.getLfAdjAlgo().setApplyAdjustAlgo(false);
            algorithm.getMessageLog().setEnabled(false);
            reset(net);
            if (!algorithm.loadflow(true)) throw new IllegalStateException(name);
            int warmups = net.getNoBus() > 1000 ? 30 : 500;
            int repeats = net.getNoBus() > 1000 ? 20 : 100;
            for (int i = 0; i < warmups; i++) {
                reset(net);
                if (!algorithm.loadflow(false)) throw new IllegalStateException(name);
            }
            LfNrProfiler.reset();
            KLUSolverProfiler.reset();
            long start = System.nanoTime();
            try (var nr = LfNrProfiler.withEnabled(true); var klu = KLUSolverProfiler.withEnabled(true)) {
                for (int i = 0; i < repeats; i++) {
                    reset(net);
                    if (!algorithm.loadflow(false)) throw new IllegalStateException(name);
                }
            }
            var report = new LinkedHashMap<String, Object>();
            report.put("case", name);
            report.put("repeats", repeats);
            report.put("elapsed_ns", System.nanoTime() - start);
            report.put("nr", LfNrProfiler.snapshot());
            report.put("klu", KLUSolverProfiler.snapshot());
            Files.writeString(output.resolve(name + ".json"), new GsonBuilder().setPrettyPrinting().create().toJson(report));
            System.out.println(name + " profile saved");
        }
    }
}
