package org.interpss.core.dstab.reference;

import java.io.BufferedWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;

import org.apache.commons.math3.complex.Complex;
import org.interpss.IpssCorePlugin;
import org.interpss.fadapter.psse.PSSEMultiFileLoader;
import org.interpss.fadapter.psse.PsseDStabSolver;
import org.interpss.dstab.renewable.Regca1Model;
import org.interpss.dstab.algo.PsseStaggeredEulerSolver;

import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.interpss.core.acsc.fault.SimpleFaultCode;
import com.interpss.dstab.BaseDStabNetwork;
import com.interpss.dstab.DStabObjectFactory;
import com.interpss.dstab.DStabGen;
import com.interpss.dstab.StaticLoadModel;
import com.interpss.dstab.algo.DynamicSimuMethod;
import com.interpss.dstab.cache.StateMonitor;
import com.interpss.dstab.common.DStabSimuException;

/** Runs one staged, unchanged Texas2k case against an explicit reference event.
 * This is a command-line evidence producer, not an assertion of trajectory parity.
 */
public final class Texas2kReferenceRunner {
    private Texas2kReferenceRunner() { }

    public static void main(String[] args) throws Exception {
        if (args.length != 2) throw new IllegalArgumentException("Expected plan.json event_key");
        Path planPath = Path.of(args[0]);
        Path root = planPath.toAbsolutePath().getParent();
        JsonObject plan = JsonParser.parseString(Files.readString(planPath)).getAsJsonObject();
        JsonObject event = null;
        for (var candidate : plan.getAsJsonArray("events")) {
            var object = candidate.getAsJsonObject();
            if (object.get("event_key").getAsString().equals(args[1])) event = object;
        }
        if (event == null) throw new IllegalArgumentException("Unknown event " + args[1]);
        Path out = root.resolve("actual").resolve(args[1]);
        Files.createDirectories(out);
        Path trace = out.resolve("interpss.csv");
        if (Files.exists(trace)) throw new IllegalArgumentException("Refusing to replace " + args[1]);
        Map<String, Object> status = new LinkedHashMap<>();
        status.put("event_key", args[1]);
        status.put("integration_method", "modified-euler");
        status.put("network_voltage_tolerance", 1.e-8);
        status.put("network_max_iterations", 500);
        double reached = 0.;
        long started = System.nanoTime();
        try {
            IpssCorePlugin.init();
            status.put("sparse_solver", IpssCorePlugin.configureSparseSolver("java-klu").active().toString());
            Path inputs = root.resolve("inputs").resolve(event.get("case_name").getAsString());
            var context = new PSSEMultiFileLoader().loadDStab(
                    inputs.resolve(event.get("raw").getAsString()).toString(),
                    inputs.resolve(event.get("dyr").getAsString()).toString());
            var net = context.getDStabilityNet();
            var algo = context.getDynSimuAlgorithm();
            net.setBypassDataCheck(true);
            net.setAllowGenWithoutMach(true);
            net.setStaticLoadModel(plan.has("active_load_model")
                    ? StaticLoadModel.valueOf(plan.get("active_load_model").getAsString())
                    : StaticLoadModel.CONST_I);
            net.setReactiveStaticLoadModel(plan.has("reactive_load_model")
                    ? StaticLoadModel.valueOf(plan.get("reactive_load_model").getAsString())
                    : StaticLoadModel.CONST_Z);
            // A fixed admittance represents only the all-impedance profile.
            // Mixed P/Q profiles must use the engine's initialized load-current path.
            net.setStaticLoadIncludedInYMatrix(net.getStaticLoadModel() == StaticLoadModel.CONST_Z
                    && net.getReactiveStaticLoadModel() == StaticLoadModel.CONST_Z);
            status.put("static_load_in_y_matrix", net.isStaticLoadIncludedInYMatrix());
            status.put("active_load_model", net.getStaticLoadModel().toString());
            status.put("reactive_load_model", net.getReactiveStaticLoadModel().toString());
            status.put("machine_power_quantity", "instantaneous terminal P/Q on system MVA base");
            var lf = algo.getAclfAlgorithm();
            lf.getDataCheckConfig().setAutoTurnLine2Xfr(true);
            lf.getDataCheckConfig().setTurnOffIslandBus(true);
            lf.getDataCheckConfig().setAllowGenWithoutMachine(true);
            lf.setNonDivergent(true);
            int loadflowIterations = plan.has("loadflow_max_iterations")
                    ? plan.get("loadflow_max_iterations").getAsInt() : 50;
            lf.setMaxIterations(loadflowIterations);
            status.put("loadflow_max_iterations", loadflowIterations);
            lf.setTolerance(1.e-10);
            if (plan.has("fixed_powerflow_controls") && plan.get("fixed_powerflow_controls").getAsBoolean()) {
                lf.setInitBusVoltage(false);
                lf.getLfAdjAlgo().getVoltAdjConfig().setXfrTapControl(false);
                lf.getLfAdjAlgo().getVoltAdjConfig().setSwitchedShuntAdjust(false);
                lf.getLfAdjAlgo().getPowerAdjConfig().setPsXfrPControl(false);
                lf.getNetAdjAlgo().setAreaInterchangeControlEnabled(false);
                status.put("fixed_powerflow_controls", true);
            }
            if (plan.has("adjust_loadflow")) {
                lf.getLfAdjAlgo().setApplyAdjustAlgo(plan.get("adjust_loadflow").getAsBoolean());
                status.put("adjust_loadflow", plan.get("adjust_loadflow").getAsBoolean());
            }
            if (plan.has("initialize_from_raw") && plan.get("initialize_from_raw").getAsBoolean()) {
                // Diagnostic only: use the operating point supplied by the solved RAW.
                // No reference trajectory values are supplied to the initialization.
                net.setLfConverged(true);
                status.put("initialization_source", "solved-raw-diagnostic");
            } else {
                if (!lf.loadflow()) throw new IllegalStateException("Load flow failed");
                var termination = lf.getTerminationReport();
                status.put("loadflow_termination", termination.status().toString());
                status.put("loadflow_unsettled_controls", termination.unsettledControlCount());
                if (!termination.controlsSettled())
                    throw new IllegalStateException("Load-flow controls remain unsettled: " + termination);
            }
            net.setBypassDataCheck(false);
            if (!net.checkData(lf.getDataCheckConfig()))
                throw new IllegalStateException("Network data validation failed");
            double outputStep = event.get("dt_s").getAsDouble();
            double step = plan.has("integration_step_s")
                    ? plan.get("integration_step_s").getAsDouble() : outputStep;
            double ratio = outputStep / step;
            if (!Double.isFinite(step) || step <= 0. || ratio < 1.
                    || Math.abs(ratio - Math.rint(ratio)) > 1.e-9)
                throw new IllegalArgumentException("Integration step must divide the reference output step");
            int outputEvery = (int)Math.round(ratio);
            double stop = event.get("end_s").getAsDouble();
            if (plan.has("stop_s")) stop = Math.min(stop, plan.get("stop_s").getAsDouble());
            algo.setSimuMethod(DynamicSimuMethod.MODIFIED_EULER);
            algo.setSimuStepSec(step);
            algo.setTotalSimuTimeSec(stop);
            algo.setSimuOutputHandler(new StateMonitor());
            algo.setOutPutPerSteps(1);
            String renewableScheme = plan.has("renewable_integration_scheme")
                    ? plan.get("renewable_integration_scheme").getAsString() : "MODIFIED_EULER";
            var scheme = Regca1Model.IntegrationScheme.valueOf(renewableScheme);
            boolean renewableSinglePrecision = plan.has("renewable_single_precision_states")
                    && plan.get("renewable_single_precision_states").getAsBoolean();
            int configuredRenewables = 0;
            for (var networkBus : net.getBusList()) {
                for (var generator : networkBus.getContributeGenList()) {
                    if (generator instanceof DStabGen dynamicGen
                            && dynamicGen.getDynamicGenDevice() instanceof Regca1Model renewable) {
                        renewable.setIntegrationScheme(scheme);
                        renewable.setSinglePrecisionStates(renewableSinglePrecision);
                        configuredRenewables++;
                    }
                }
            }
            status.put("renewable_integration_scheme", renewableScheme);
            status.put("configured_regca1_devices", configuredRenewables);
            status.put("renewable_single_precision_states", renewableSinglePrecision);
            String bus = "Bus" + event.get("target_bus").getAsInt();
            double start = event.get("start_s").getAsDouble();
            if (event.get("event_type").getAsString().equals("bus_fault")) {
                // The event processor applies this at the first grid point at/after clear_s.
                net.addDynamicEvent(DStabObjectFactory.createBusFaultEvent(bus, net,
                        SimpleFaultCode.GROUND_3P, Complex.ZERO, null, start,
                        event.get("clear_s").getAsDouble() - start), args[1]);
            } else {
                net.addDynamicEvent(DStabObjectFactory.createGeneratorTripEvent(bus,
                        event.get("target_machine_id").getAsString(), net, start), args[1]);
            }
            boolean fullNetworkDiagnostics = plan.has("full_network_diagnostics")
                    && plan.get("full_network_diagnostics").getAsBoolean();
            var fullNetworkFormat = plan.has("full_network_format")
                    ? Texas2kFullNetworkRecorder.Format.valueOf(plan.get("full_network_format").getAsString())
                    : Texas2kFullNetworkRecorder.Format.CSV_GZIP;
            long simulationStarted;
            try (var writer = BenchmarkOutputGuard.newWriter(trace, 0);
                    var fullNetwork = fullNetworkDiagnostics ? new Texas2kFullNetworkRecorder(net, out, fullNetworkFormat) : null) {
                final JsonObject selected = event;
                // Preserve the loader's initialization and integration-step configuration.
                String globalScheme = plan.has("solver_integration_scheme")
                        ? plan.get("solver_integration_scheme").getAsString() : "MODIFIED_EULER";
                if (!globalScheme.equals("MODIFIED_EULER") && !globalScheme.equals("STAGGERED_EULER"))
                    throw new IllegalArgumentException("Unknown shared integration scheme: "+globalScheme);
                status.put("solver_integration_scheme", globalScheme);
                if (globalScheme.equals("STAGGERED_EULER")) {
                    algo.setSolver(new PsseStaggeredEulerSolver(algo) {
                        { setNetworkSolutionTolerance(1.e-8); setMaxIterationTimes(500); }
                        @Override public boolean hasDynEvent(double t) {
                            for (var e : algo.getNetwork().getDynamicEventList()) {
                                double boundary = e.isActive() ? e.getStartTimeSec()+e.getDurationSec()
                                        : e.getStartTimeSec();
                                if (Math.abs(t-boundary)<1.e-9 || (e.isActive()&&e.getDurationSec()>0
                                        &&t>boundary&&t<boundary+algo.getSimuStepSec())) return true;
                            }
                            return false;
                        }
                        @Override protected void rebaseDynamicModelInputs(DynamicSimuMethod method)
                                throws DStabSimuException {
                            super.rebaseDynamicModelInputs(method);
                            try { write(writer, algo.getSimuTime(), "EVENT_POST", record(net, selected), fullNetwork); }
                            catch (Exception failure) { throw new DStabSimuException("Event observation failed: "+failure); }
                        }
                    });
                } else algo.setSolver(new PsseDStabSolver(algo) {
                    { converge_tol = 1.e-8; setMaxIterationTimes(500); }
                    @Override public boolean hasDynEvent(double t) {
                        // Include a clearing time that lies between integration grid points.
                        // This is a pure boundary query; hasEventAt() mutates event status.
                        for (var e : algo.getNetwork().getDynamicEventList()) {
                            double boundary = e.isActive() ? e.getStartTimeSec() + e.getDurationSec()
                                    : e.getStartTimeSec();
                            if (Math.abs(t - boundary) < 1.e-9
                                    || (e.isActive() && e.getDurationSec() > 0 && t > boundary
                                        && t < boundary + algo.getSimuStepSec())) return true;
                        }
                        return false;
                    }
                    @Override public boolean networkSolutionStep() throws DStabSimuException {
                        if (!super.networkSolutionStep())
                            throw new DStabSimuException("Network convergence failure: "
                                    + getLastNetworkSolutionDiagnostics());
                        return true;
                    }
                    @Override protected void rebaseDynamicModelInputs(DynamicSimuMethod method)
                            throws DStabSimuException {
                        super.rebaseDynamicModelInputs(method);
                        try { write(writer, algo.getSimuTime(), "EVENT_POST", record(net, selected), fullNetwork); }
                        catch (Exception failure) {
                            throw new DStabSimuException("Event observation failed: " + failure);
                        }
                    }
                });
                if (!algo.initialization()) throw new IllegalStateException("Initialization failed");
                status.put("constant_current_load_voltage_curve", net.getConstantCurrentLoadVoltageCurve().toString());
                if (!net.isDStabNetInitialized())
                    throw new IllegalStateException("A dynamic device failed initialization");
                if (algo.getSolver() instanceof PsseStaggeredEulerSolver shared) {
                    status.put("bound_synchronous_machines",shared.getBoundMachineCount());
                    status.put("bound_renewables",shared.getBoundRenewableCount());
                    status.put("bound_differential_states",shared.getBoundDifferentialStateCount());
                    status.put("renewable_integration_scheme","TWO_STEP");
                }
                double maximumCompensation = 0.;
                String leadingCompensationBus = "";
                try (var currents = Files.newBufferedWriter(out.resolve("initial-norton-compensation.csv"))) {
                    currents.write("bus,current_real_pu,current_imag_pu,current_magnitude_pu\n");
                    for (var entry : net.getCustomBusCurrInjHashtable().entrySet()) {
                        Complex current = entry.getValue();
                        currents.write(entry.getKey() + "," + current.getReal() + ","
                                + current.getImaginary() + "," + current.abs() + "\n");
                        if (current.abs() > maximumCompensation) {
                            maximumCompensation = current.abs();
                            leadingCompensationBus = entry.getKey();
                        }
                    }
                }
                status.put("maximum_initial_norton_compensation_pu", maximumCompensation);
                status.put("leading_initial_norton_compensation_bus", leadingCompensationBus);
                boolean retainCompensation = !plan.has("retain_norton_compensation")
                        || plan.get("retain_norton_compensation").getAsBoolean();
                if (!retainCompensation) net.setCustomBusCurrInjHashtable(null);
                status.put("retain_norton_compensation", retainCompensation);
                boolean auditInitialization = plan.has("audit_initialization")
                        && plan.get("audit_initialization").getAsBoolean();
                if (auditInitialization) {
                    Files.writeString(out.resolve("core-machine-initial-conditions.txt"), net.getMachineInitCondition());
                    Texas2kInitializationAudit.write(net, out.resolve("initialization-state-audit.json"));
                }
                simulationStarted = System.nanoTime();
                status.put("setup_initialization_wall_s", (simulationStarted - started) / 1.e9);
                var initial = record(net, event);
                writer.write("time_s,sample_kind," + String.join(",", initial.keySet()) + "\n");
                int historySteps = plan.has("startup_history_steps")
                        ? plan.get("startup_history_steps").getAsInt() : 0;
                if (historySteps != 0 && historySteps != 2)
                    throw new IllegalArgumentException("Expected zero or two native startup history steps");
                status.put("startup_history_steps", historySteps);
                if (historySteps == 2) {
                    algo.setSimuTime(-historySteps * step);
                    for (int index = 0; index < historySteps; index++) {
                        write(writer, algo.getSimuTime(), "HISTORY", record(net, event), fullNetwork);
                        if (!algo.solveDEqnStep(true))
                            throw new IllegalStateException("Startup history integration failed");
                        if (auditInitialization)
                            Texas2kInitializationAudit.write(net, out.resolve("startup-state-audit-" + index + ".json"));
                    }
                    initial = record(net, event);
                }
                write(writer, 0., "INITIAL", initial, fullNetwork);
                status.put("step_s", step);
                status.put("reference_output_step_s", outputStep);
                status.put("stop_s", stop);
                int count = (int)Math.round(stop / step);
                for (int index = 0; index < count; index++) {
                    if (!algo.solveDEqnStep(true))
                        throw new IllegalStateException("Integration failed at " + algo.getSimuTime());
                    reached = algo.getSimuTime();
                    if ((index + 1) % outputEvery == 0)
                        write(writer, reached, "STEP_END", record(net, event), fullNetwork);
                    if ((index + 1) % 2000 == 0) {
                        writer.flush();
                        System.out.println(args[1] + " t=" + reached);
                    }
                }
            }
            status.put("status", "completed");
            // Includes startup samples, events, all channel recording and final writer close.
            status.put("simulation_output_wall_s", (System.nanoTime() - simulationStarted) / 1.e9);
            status.put("full_network_format", fullNetworkDiagnostics ? fullNetworkFormat.name() : "DISABLED");
        } catch (Exception failure) {
            status.put("status", "failed");
            status.put("error", failure.toString());
            throw failure;
        } finally {
            status.put("reached_s", reached);
            status.put("elapsed_s", (System.nanoTime() - started) / 1.e9);
            Files.writeString(out.resolve("status.json"), new GsonBuilder().setPrettyPrinting()
                    .create().toJson(status) + "\n");
        }
    }

    private static Map<String, Double> record(BaseDStabNetwork<?, ?> net, JsonObject event) {
        Map<String, Double> values = new LinkedHashMap<>();
        for (var number : event.getAsJsonArray("nearby_buses")) {
            int id = number.getAsInt();
            var bus = net.getBus("Bus" + id);
            if (bus == null || !bus.isActive()) throw new IllegalStateException("Missing bus " + id);
            values.put("bus_voltage_pu__bus_" + id, bus.getVoltageMag());
            values.put("bus_angle_deg__bus_" + id, Math.toDegrees(bus.getVoltageAng()));
        }
        var observedGenerators = event.has("diagnostic_generators")
                ? event.getAsJsonArray("diagnostic_generators") : event.getAsJsonArray("nearby_generators");
        for (var generator : observedGenerators) {
            var g = generator.getAsJsonObject();
            String id = "Bus" + g.get("bus").getAsInt() + "-mach" + g.get("id").getAsString();
            var machine = net.getMachine(id);
            if (machine == null) throw new IllegalStateException("Missing machine " + id);
            String suffix = "__bus_" + g.get("bus").getAsInt() + "__id_" + g.get("id").getAsString();
            // Commercial machine channels are zero after disconnection. Keep
            // the retained internal states intact; this is an output convention.
            boolean active = machine.isActive();
            values.put("machine_ANGLE_deg" + suffix, active ? Math.toDegrees(machine.getAngle()) : 0.);
            values.put("machine_SPEED_dev_pu" + suffix, active ? machine.getSpeed() - 1. : 0.);
            values.put("machine_EFD_pu" + suffix, active ? machine.getEfd() : 0.);
            values.put("machine_PMECH_pu_mbase" + suffix, active ? machine.getPm() : 0.);
            values.put("machine_ETERM_pu" + suffix, active ? machine.getDStabBus().getVoltageMag() : 0.);
            // getPe() includes stator copper loss and may be averaged at an
            // event. PSS/E PELEC/QELEC require the instantaneous terminal power.
            Complex terminalPower = active
                    ? Texas2kFullNetworkRecorder.terminalPowerWithoutCacheChanges(machine)
                    : Complex.ZERO;
            values.put("machine_AIRGAP_pu_mbase" + suffix, active ? machine.getPe() : 0.);
            values.put("machine_PELEC_pu_sbase" + suffix, terminalPower.getReal());
            values.put("machine_QELEC_pu" + suffix, terminalPower.getImaginary());
        }
        return values;
    }

    private static void write(BufferedWriter writer, double time, String kind,
            Map<String, Double> values, Texas2kFullNetworkRecorder fullNetwork) throws Exception {
        writer.write(Double.toString(time));
        writer.write("," + kind);
        for (var entry : values.entrySet()) {
            if (!Double.isFinite(entry.getValue()))
                throw new IllegalStateException("Nonfinite " + entry.getKey() + " at " + time);
            writer.write("," + entry.getValue());
        }
        writer.newLine();
        if (fullNetwork != null) fullNetwork.record(time, kind);
    }
}
