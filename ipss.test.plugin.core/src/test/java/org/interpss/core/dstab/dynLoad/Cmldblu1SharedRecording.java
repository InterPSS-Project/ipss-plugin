package org.interpss.core.dstab.dynLoad;

import static org.junit.jupiter.api.Assertions.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.zip.GZIPOutputStream;
import org.apache.commons.math3.complex.Complex;
import org.interpss.IpssCorePlugin;
import org.interpss.core.dstab.reference.BenchmarkOutputGuard;
import org.interpss.dstab.algo.DefaultDynamicSimulationSolver;
import org.interpss.dstab.algo.StaggeredEulerSolver;
import org.interpss.dstab.dynLoad.impl.Cmldblu1Model;
import org.interpss.fadapter.psse.PSSEMultiFileLoader;
import com.interpss.core.acsc.fault.SimpleFaultCode;
import com.interpss.dstab.DStabObjectFactory;
import com.interpss.dstab.algo.DynamicSimuAlgorithm;
import com.interpss.dstab.algo.DynamicSimuMethod;
import com.interpss.dstab.common.DStabSimuException;

/** Complete synthetic recordings, including distinct left and right fault boundaries. */
final class Cmldblu1SharedRecording {
    record Frame(double time, String kind, Map<String, Double> values) { }
    final DynamicSimuAlgorithm algorithm;
    final boolean flat;
    final boolean repeated;
    final List<Frame> frames = new ArrayList<>();
    boolean enabled;
    boolean recordRelayEvents;
    Cmldblu1Model model;
    Frame initial;

    private Cmldblu1SharedRecording(DynamicSimuAlgorithm algorithm, boolean flat, boolean repeated) {
        this.algorithm = algorithm; this.flat = flat; this.repeated = repeated;
    }
    Frame capture(double time, String kind) {
        var net = algorithm.getNetwork();
        var values = new TreeMap<String, Double>();
        for (int bus = 1; bus <= 3; bus++) {
            var voltage = net.getDStabBus("Bus" + bus).getVoltage();
            values.put("V" + bus, voltage.abs()); values.put("A" + bus, Math.toDegrees(voltage.getArgument()));
        }
        var machine = net.getMachine("Bus1-mach1");
        var voltage = machine.getDStabBus().getVoltage();
        var power = voltage.multiply(machine.getIgen().subtract(voltage.multiply(machine.getYgen())).conjugate());
        values.put("ANGLE_DEG", Math.toDegrees(machine.getAngle()));
        values.put("SPEED", machine.getSpeed() - 1); values.put("PMECH", machine.getPm());
        values.put("P_PU", power.getReal()); values.put("Q_PU", power.getImaginary());
        // Boundary coordinates distinguish source-machine integration effects from load errors.
        if (machine instanceof com.interpss.dstab.mach.RoundRotorMachine round) {
            values.put("MACHINE.Eq1", round.getEq1());
            values.put("MACHINE.Ed1", round.getEd1());
            values.put("MACHINE.Psikd", round.getPsikd());
            values.put("MACHINE.Psikq", round.getPsikq());
        }
        values.putAll(model.getNamedStates());
        if (model.get1PhaseACMotor() instanceof com.interpss.dstab.controller.runtime.IntegrationStateAccess owner) {
            var histories = owner.getIntegrationHistories();
            values.put("MotorD.RunA", histories.get("RunA"));
            values.put("MotorD.RunB", histories.get("RunB"));
        }
        values.put("Load.V", model.getLoadBus().getVoltageMag());
        values.put("Load.A", model.getLoadBus().getVoltageAng());
        values.put("Low.V", model.getLowVoltBus().getVoltageMag());
        values.put("Low.A", model.getLowVoltBus().getVoltageAng());
        if (recordRelayEvents) {
            // A load diagnostic can use current parameters with the last solved
            // boundary. Preserve this separately from the physical bus voltage.
            double prepared=model.getLoadBus().getVoltageMag();
            var transformer=model.getDistributionTransformer();
            if (model.getRemainingCapacity()>0 && transformer!=null && model.getLowVoltBus()!=model.getLoadBus()) {
                var feeder=net.getBranchList().stream().filter(b -> b.getFromBus()==model.getLowVoltBus()
                        && b.getToBus()==model.getLoadBus()).findFirst().orElseThrow();
                double tap=transformer.getToTurnRatio();var low=model.getLowVoltBus().getVoltage();
                var incoming=model.getDStabBus().getVoltage().subtract(low.divide(tap)).divide(transformer.getZ());
                var feederCurrent=incoming.divide(tap).subtract(model.getLowVoltBus().getShuntY().multiply(low));
                prepared=low.subtract(feeder.getZ().multiply(feederCurrent)).abs();
            }
            values.put("Load.PreparedV",prepared);
        }
        for (var bus : net.getBusList()) for (var device : bus.getDynamicBusDeviceList())
            if (device instanceof org.interpss.dstab.relay.Tiocr1RelayModel relay) {
                values.put("RELAY.Current", relay.currentMagnitude());
                values.put("RELAY.Position", relay.getPosition());
                values.put("RELAY.Shed", relay.getShedFraction());
                values.put("RELAY.Operated", relay.isOperated() ? 1. : 0.);
            }
        return new Frame(time, kind, Collections.unmodifiableMap(values));
    }
    void before(double time) {
        if (enabled && !flat && (Math.abs(time - .05) < 1e-9 || Math.abs(time - .1) < 1e-9
                || repeated && (Math.abs(time - .2) < 1e-9 || Math.abs(time - .25) < 1e-9)))
            frames.add(capture(time, "EVENT_PRE"));
    }
    void afterAcceptedRelayAction(Frame before) {
        if (before != null && before.values().getOrDefault("RELAY.Operated", 1.) == 0.) {
            var after=capture(before.time(), "CHECK");
            if (after.values().getOrDefault("RELAY.Operated", 0.) == 1.) {
                var values=new TreeMap<>(before.values());
                values.put("Load.PreparedV",after.values().get("Load.PreparedV"));
                frames.add(new Frame(before.time(),before.kind(),Collections.unmodifiableMap(values)));
            }
        }
    }
    static Cmldblu1SharedRecording simulate(String fixture, boolean staggered, boolean flat, double h) throws Exception {
        return simulate(fixture, Cmldblu1SharedSmibTest.CASE.resolve(fixture + ".dyr"), staggered, flat, h);
    }
    static Cmldblu1SharedRecording simulateStaticControl(Path dynamicFile, boolean staggered, double h) throws Exception {
        return simulate("static-control", dynamicFile, staggered, false, h);
    }
    static Cmldblu1SharedRecording simulateStaticControl(Path dynamicFile, boolean staggered, double h,
            double faultX, double secondFaultX) throws Exception {
        return simulate("static-control", dynamicFile, staggered, false, h, faultX, secondFaultX);
    }
    static Cmldblu1SharedRecording simulateStaticControl(Path dynamicFile, boolean staggered, double h,
            double faultX, double secondFaultX, double endTime) throws Exception {
        return simulate("static-control", dynamicFile, staggered, false, h, faultX, secondFaultX, endTime);
    }
    static Cmldblu1SharedRecording simulateControl(Path dynamicFile,boolean staggered,boolean flat,
            double h,double faultX,double secondFaultX,double endTime)throws Exception {
        return simulate("control",dynamicFile,staggered,flat,h,faultX,secondFaultX,endTime);
    }
    private static Cmldblu1SharedRecording simulate(String fixture, Path dynamicFile, boolean staggered, boolean flat, double h) throws Exception {
        return simulate(fixture, dynamicFile, staggered, flat, h,
                fixture.endsWith("electronic") || fixture.endsWith("-restart") ? .01 : .2,
                fixture.endsWith("-restart") ? .01 : 0);
    }
    private static Cmldblu1SharedRecording simulate(String fixture, Path dynamicFile, boolean staggered,
            boolean flat, double h, double faultX, double secondFaultX) throws Exception {
        return simulate(fixture, dynamicFile, staggered, flat, h, faultX, secondFaultX, .5);
    }
    private static Cmldblu1SharedRecording simulate(String fixture, Path dynamicFile, boolean staggered,
            boolean flat, double h, double faultX, double secondFaultX, double endTime) throws Exception {
        // Retain a reference's final clock-rounded overshoot instead of discarding its last sample.
        // Accumulated single-precision clocks can require several extra steps.
        // Keep a bounded recording allowance; callers must verify every source timestamp.
        if (!Double.isFinite(h) || h <= 0 || !Double.isFinite(endTime) || endTime < .5 || endTime > 11)
            throw new IllegalArgumentException("Recording end time must be finite and between 0.5 and 11 seconds");
        var loader = new PSSEMultiFileLoader();
        var context = loader.loadDStab(Cmldblu1SharedSmibTest.CASE.resolve("load-network.raw").toString(),
                dynamicFile.toString());
        assertTrue(loader.getLastDynamicImportReport().isStrictlyComplete());
        var algorithm = context.getDynSimuAlgorithm();
        assertTrue(algorithm.getAclfAlgorithm().loadflow());
        algorithm.setSimuMethod(DynamicSimuMethod.MODIFIED_EULER);
        algorithm.setSimuStepSec(h); algorithm.setTotalSimuTimeSec(endTime);
        algorithm.setSimuOutputHandler(new com.interpss.dstab.cache.StateMonitor());
        boolean repeated = secondFaultX > 0;
        var result = new Cmldblu1SharedRecording(algorithm, flat, repeated);
        algorithm.setSolver(staggered ? new SharedRecorder(algorithm, result) : new OrdinaryRecorder(algorithm, result));
        if (!flat) algorithm.getNetwork().addDynamicEvent(DStabObjectFactory.createBusFaultEvent("Bus2",
                algorithm.getNetwork(), SimpleFaultCode.GROUND_3P,
                new Complex(0, faultX), null, .05, .05), "fault");
        if (!flat && repeated) algorithm.getNetwork().addDynamicEvent(DStabObjectFactory.createBusFaultEvent("Bus2",
                algorithm.getNetwork(), SimpleFaultCode.GROUND_3P, new Complex(0,secondFaultX), null, .2, .05), "second-fault");
        assertTrue(algorithm.initialization());
        result.model = assertInstanceOf(Cmldblu1Model.class,
                algorithm.getNetwork().getDStabBus("Bus2").getInfoOnlyDynModel());
        result.recordRelayEvents = algorithm.getNetwork().getBusList().stream()
                .anyMatch(bus -> bus.getDynamicBusDeviceList().stream()
                        .anyMatch(org.interpss.dstab.relay.Tiocr1RelayModel.class::isInstance));
        result.initial = result.capture(0, "INITIAL"); result.enabled = true;
        while (algorithm.getSimuTime() < endTime - h / 2) assertTrue(algorithm.solveDEqnStep(true));
        result.frames.add(result.capture(algorithm.getSimuTime(), "FINAL"));
        return result;
    }
    private static final class OrdinaryRecorder extends DefaultDynamicSimulationSolver {
        final Cmldblu1SharedRecording recording;
        OrdinaryRecorder(DynamicSimuAlgorithm a, Cmldblu1SharedRecording r) { super(a); recording = r; }
        @Override public void beforeStep(double t) throws DStabSimuException { recording.before(t); super.beforeStep(t); }
        @Override public void afterStep(double t) throws DStabSimuException {
            var before=recording.enabled && recording.recordRelayEvents ? recording.capture(t,"RELAY_EVENT_PRE") : null;
            super.afterStep(t);recording.afterAcceptedRelayAction(before);
        }
        @Override public boolean networkSolutionStep() throws DStabSimuException {
            boolean solved = super.networkSolutionStep();
            if (solved && recording.enabled && "INITIAL".equals(networkSolutionStage))
                recording.frames.add(recording.capture(dstabAlgo.getSimuTime(), "GRID"));
            return solved;
        }
    }
    private static final class SharedRecorder extends StaggeredEulerSolver {
        final Cmldblu1SharedRecording recording;
        SharedRecorder(DynamicSimuAlgorithm a, Cmldblu1SharedRecording r) {
            super(a); recording = r; setReuseNetworkEndpoint(false);
        }
        @Override public void afterStep(double t) throws DStabSimuException {
            var before=recording.enabled && recording.recordRelayEvents ? recording.capture(t,"RELAY_EVENT_PRE") : null;
            super.afterStep(t);recording.afterAcceptedRelayAction(before);
        }
        @Override public void beforeStep(double t) throws DStabSimuException { recording.before(t); super.beforeStep(t); }
        @Override public boolean networkSolutionStep() throws DStabSimuException {
            boolean solved = super.networkSolutionStep();
            if (solved && recording.enabled && "INITIAL".equals(networkSolutionStage))
                recording.frames.add(recording.capture(dstabAlgo.getSimuTime(), "GRID"));
            return solved;
        }
    }
    void write(Path path) throws Exception {
        try (var raw = BenchmarkOutputGuard.newOutputStream(path,
                BenchmarkOutputGuard.estimateCsvBytes(frames.size(), initial.values.size() + 2));
                var zip = new GZIPOutputStream(raw);
                var writer = new BufferedWriter(new OutputStreamWriter(zip, StandardCharsets.UTF_8))) {
            writer.write("time_s,sample_kind," + String.join(",", initial.values.keySet()) + "\n");
            for (var row : frames) {
                writer.write(row.time + "," + row.kind);
                for (double value : row.values.values()) { assertTrue(Double.isFinite(value)); writer.write("," + value); }
                writer.write("\n");
            }
        }
    }
    /** Standalone launch also permits a frozen pre-change class overlay. */
    public static void main(String[] args) throws Exception {
        IpssCorePlugin.init(); IpssCorePlugin.configureSparseSolver("java-klu");
        Path output = Path.of(args[0]); Files.createDirectories(output);
        for (String fixture : List.of("cmldblu1", "cmldblu1-mixed", "cmldblu1-electronic"))
            for (boolean flat : List.of(true, false)) {
                var recording = simulate(fixture, false, flat, .0005);
                recording.write(output.resolve(fixture + (flat ? "-flat" : "-fault") + ".csv.gz"));
            }
    }
}
