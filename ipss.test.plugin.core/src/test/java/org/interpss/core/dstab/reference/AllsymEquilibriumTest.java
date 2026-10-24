package org.interpss.core.dstab.reference;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.TreeMap;

import org.interpss.IpssCorePlugin;
import org.interpss.dstab.algo.PsseStaggeredEulerSolver;
import org.interpss.fadapter.builder.DStabNetworkBuilder;
import org.interpss.fadapter.psse.PSSEDStabDirectParser;
import org.interpss.fadapter.psse.PSSEMultiFileLoader;
import org.interpss.fadapter.psse.dyr.PsseDyrRecordReader;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.google.gson.GsonBuilder;
import com.interpss.dstab.BaseDStabNetwork;
import com.interpss.dstab.StaticLoadModel;
import com.interpss.dstab.algo.DynamicSimuMethod;
import com.interpss.dstab.cache.StateMonitor;
import com.interpss.dstab.mach.Machine;

/** No-disturbance equilibrium gate on the unchanged, supplied synchronous case.
 * Flat means stationary dynamics at the solved operating point, not unit bus voltages.
 */
public class AllsymEquilibriumTest {
    private static final double STEP = 1. / 240.;
    private static final String RAW_HASH = "4aa4e8099579c87fda26d190413bbfb0c29304b103a3cea090a2cef46ac3203e";
    private static final String DYR_HASH = "7553531bd9adc3173da8049db9e253c44230474065bb579c58c772ab5171401d";

    @Test
    void suppliedCaseRemainsAtEquilibriumForTenSeconds(@TempDir Path output) throws Exception {
        Path input = Path.of("testData/private/allsym-equilibrium-20261022");
        assumeTrue(Files.isDirectory(input), "Private supplied ALLSYM fixture is not installed");
        verify(run(input, output));
    }

    /** Explicit evidence entry point: input directory, new output directory. */
    public static void main(String[] args) throws Exception {
        if (args.length != 2) throw new IllegalArgumentException("Expected input and output directories");
        Path output = Path.of(args[1]);
        if (Files.exists(output)) throw new IllegalArgumentException("Refusing to replace evidence: " + output);
        Files.createDirectories(output);
        verify(run(Path.of(args[0]), output));
    }

    private static Map<String, Peak> run(Path input, Path output) throws Exception {
        long started = System.nanoTime();
        Path raw = input.resolve("ALLSYM.raw"), dyr = input.resolve("GEN_ESST4B_IEEEG1_IEEEST.dyr");
        assertEquals(RAW_HASH, hash(raw));
        assertEquals(DYR_HASH, hash(dyr));
        var status = new LinkedHashMap<String, Object>();
        var peaks = new LinkedHashMap<String, Peak>();
        try {
            IpssCorePlugin.init();
            status.put("sparse_solver", IpssCorePlugin.configureSparseSolver("java-klu").active().toString());
            var context = new PSSEMultiFileLoader().loadDStab(raw.toString());
            var net = context.getDStabilityNet();
            // This supplied pair contains stabilizers on fixed-excitation machines.
            // Account for those unused outputs explicitly; never invent an exciter.
            var parser = new PSSEDStabDirectParser(new DStabNetworkBuilder(net)).setStrictImport(false);
            try { parser.parseDynFile(dyr.toString()); }
            finally { status.put("import", parser.getLastImportReport()); }
            var report = parser.getLastImportReport();
            status.put("import", report);
            assertEquals(1259, report.totalRecordCount());
            assertEquals(156, report.failures().size(), "Changed fixture import coverage");
            var unused = new java.util.ArrayList<String>();
            for (var entry : report.failures()) {
                assertEquals("IEEEST", entry.canonicalModelName(), entry.toString());
                assertEquals(org.interpss.fadapter.psse.dyr.DynamicModelImportStatus.REJECTED, entry.status());
                var owner = net.getMachine("Bus" + entry.busNumber() + "-mach" + entry.deviceId());
                assertNotNull(owner, "An unused stabilizer must still have a machine");
                assertNull(owner.getExciter(), "Only a stabilizer without an excitation input port is unused");
                unused.add(owner.getId());
            }
            status.put("unused_stabilizers_without_exciter", unused);
            status.put("missing_exciter_policy", "fixed field voltage; unconnected stabilizer output unused");
            var inventory = new TreeMap<String, Integer>();
            for (var record : PsseDyrRecordReader.read(dyr)) inventory.merge(record.canonicalModelName(), 1, Integer::sum);
            status.put("models", inventory);
            status.put("raw_sha256", RAW_HASH); status.put("dyr_sha256", DYR_HASH);
            net.setAllowGenWithoutMach(false);
            net.setStaticLoadModel(StaticLoadModel.CONST_I);
            net.setReactiveStaticLoadModel(StaticLoadModel.CONST_Z);
            net.setStaticLoadIncludedInYMatrix(false);
            net.setStaticLoadZPartInYMatrix(true);
            var algo = context.getDynSimuAlgorithm();
            var lf = algo.getAclfAlgorithm();
            lf.getDataCheckConfig().setAutoTurnLine2Xfr(true);
            lf.getDataCheckConfig().setTurnOffIslandBus(true);
            lf.setInitBusVoltage(false);
            lf.getLfAdjAlgo().getVoltAdjConfig().setXfrTapControl(false);
            lf.getLfAdjAlgo().getPowerAdjConfig().setPsXfrPControl(false);
            lf.getNetAdjAlgo().setAreaInterchangeControlEnabled(false);
            lf.setMaxIterations(100);
            lf.setTolerance(1.e-3);
            Texas2kPowerflowPreparation.solve(lf, raw, status);
            var solver = new PsseStaggeredEulerSolver(algo);
            solver.setNetworkSolutionTolerance(1.e-5);
            solver.setMaxIterationTimes(org.interpss.fadapter.psse.PsseDStabSolver.DEFAULT_MAXIMUM_NETWORK_ITERATIONS);
            solver.setMinimumNetworkIterations(2);
            solver.setSt1Substeps(1);
            algo.setSolver(solver);
            algo.setSimuMethod(DynamicSimuMethod.MODIFIED_EULER);
            algo.setSimuStepSec(STEP);
            algo.setTotalSimuTimeSec(10.);
            var monitor = new StateMonitor(); monitor.prepareMonitoring();
            algo.setSimuOutputHandler(monitor);
            assertTrue(algo.initialization(), "Dynamic initialization");
            assertTrue(net.isDStabNetInitialized(), "All devices initialized");
            assertEquals(432, solver.getBoundMachineCount());
            assertEquals(2000, net.getBusList().stream().filter(b -> b.isActive()).count());
            status.put("parameter_validation", solver.getParameterValidationReport());
            status.put("active_machines", solver.getBoundMachineCount());
            status.put("active_buses", net.getBusList().stream().filter(b -> b.isActive()).count());
            status.put("differential_states", solver.getBoundDifferentialStateCount());
            status.put("step_s", STEP); status.put("startup_history_steps", 0);
            status.put("network_voltage_tolerance_pu", 1.e-5);
            status.put("powerflow_mismatch_tolerance_pu", 1.e-3);
            writeAudit(net, output.resolve("initial-state-audit.json"));
            Files.writeString(output.resolve("machine-initial-conditions.txt"), net.getMachineInitCondition());
            var initial = sample(net);
            assertEquals(5728, initial.size(), "Every active bus and machine must be observed");
            int samples = 1;
            try (var trace = BenchmarkOutputGuard.newWriter(output.resolve("drift.csv"), 0)) {
                trace.write("time_s,voltage_pu,bus_angle_deg,speed_pu,rotor_angle_deg,p_pu,q_pu\n");
                trace.write("0,0,0,0,0,0,0\n");
                for (int tick = 1; tick <= 2400; tick++) {
                    assertTrue(algo.solveDEqnStep(true), "Step " + tick);
                    double time = tick * STEP;
                    var instantaneous = new LinkedHashMap<String, Peak>();
                    var current = sample(net);
                    assertEquals(initial.keySet(), current.keySet(), "Channel coverage changed during the run");
                    for (var entry : current.entrySet()) {
                        double delta = Math.abs(entry.getValue() - initial.get(entry.getKey()));
                        String family = entry.getKey().substring(0, entry.getKey().indexOf(':'));
                        update(peaks, family, entry.getKey(), time, delta);
                        update(instantaneous, family, entry.getKey(), time, delta);
                    }
                    trace.write(Double.toString(time));
                    for (String family : new String[]{"voltage", "bus_angle", "speed", "rotor_angle", "p", "q"})
                        trace.write("," + instantaneous.get(family).error());
                    trace.write("\n");
                    samples++;
                    if (tick == 1) writeAudit(net, output.resolve("first-step-state-audit.json"));
                }
            }
            status.put("samples", samples); status.put("channels_checked", initial.size());
            status.put("reached_s", algo.getSimuTime());
            writeAudit(net, output.resolve("final-state-audit.json"));
            verify(peaks);
            status.put("completed", true);
        } catch (Exception | AssertionError failure) {
            status.put("completed", false);
            status.put("failure", failure.toString());
            throw failure;
        } finally {
            status.put("peak_drift", peaks);
            status.put("wall_s", (System.nanoTime() - started) / 1.e9);
            Files.writeString(output.resolve("status.json"), new GsonBuilder().setPrettyPrinting().create().toJson(status) + "\n");
            System.out.println("ALLSYM equilibrium: " + peaks);
        }
        return peaks;
    }

    private static Map<String, Double> sample(BaseDStabNetwork<?, ?> net) {
        var values = new LinkedHashMap<String, Double>();
        for (var bus : net.getBusList()) if (bus.isActive()) {
            values.put("voltage:" + bus.getId(), bus.getVoltageMag());
            values.put("bus_angle:" + bus.getId(), Math.toDegrees(bus.getVoltageAng()));
            for (var gen : bus.getContributeGenList()) if (gen.isActive()) {
                assertInstanceOf(Machine.class, gen.getDynamicGenDevice(), "Missing synchronous model: " + bus.getId() + "/" + gen.getId());
                var m = (Machine) gen.getDynamicGenDevice();
                if (!m.isActive()) continue;
                values.put("speed:" + m.getId(), m.getSpeed());
                values.put("rotor_angle:" + m.getId(), Math.toDegrees(m.getAngle()));
                var power = Texas2kFullNetworkRecorder.terminalPowerWithoutCacheChanges(m);
                values.put("p:" + m.getId(), power.getReal());
                values.put("q:" + m.getId(), power.getImaginary());
            }
        }
        values.forEach((id, value) -> assertTrue(Double.isFinite(value), "Nonfinite " + id));
        return values;
    }

    private static void update(Map<String, Peak> peaks, String family, String channel, double time, double error) {
        if (!peaks.containsKey(family) || peaks.get(family).error() < error)
            peaks.put(family, new Peak(channel, time, error));
    }

    private static void verify(Map<String, Peak> peaks) {
        assertAll("Ten-second equilibrium", () -> assertTrue(peaks.get("voltage").error() < 1.e-6, peaks.toString()),
                () -> assertTrue(peaks.get("speed").error() < 1.e-7, peaks.toString()),
                () -> assertTrue(peaks.get("rotor_angle").error() < .001, peaks.toString()),
                () -> assertTrue(peaks.get("bus_angle").error() < .001, peaks.toString()),
                () -> assertTrue(peaks.get("p").error() < 1.e-6, peaks.toString()),
                () -> assertTrue(peaks.get("q").error() < 1.e-6, peaks.toString()));
    }

    private static void writeAudit(BaseDStabNetwork<?, ?> net, Path output) throws Exception {
        Texas2kInitializationAudit.write(net, output);
        var rows = com.google.gson.JsonParser.parseString(Files.readString(output)).getAsJsonArray();
        for (var row : rows) {
            var machine = row.getAsJsonObject();
            for (String family : new String[]{"mechanical", "electrical"})
                for (var value : machine.getAsJsonObject(family).entrySet())
                    if (value.getKey().startsWith("dx") || value.getKey().endsWith("Derivative"))
                        assertTrue(Math.abs(value.getValue().getAsDouble()) < 1.e-8,
                                machine.get("bus") + " " + family + " " + value);
            for (String family : new String[]{"exciter", "governor", "stabilizer"}) {
                var controller = machine.getAsJsonObject(family);
                if (controller.isEmpty()) continue;
                assertEquals(0, controller.getAsJsonArray("rate_coverage_gaps").size(), controller.toString());
                for (var rate : controller.getAsJsonObject("rates").entrySet())
                    assertTrue(Math.abs(rate.getValue().getAsDouble()) < 1.e-8,
                            machine.get("bus") + " " + family + " " + rate);
            }
        }
    }

    private static String hash(Path path) throws Exception {
        return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(path)));
    }

    private record Peak(String channel, double time, double error) { }
}
