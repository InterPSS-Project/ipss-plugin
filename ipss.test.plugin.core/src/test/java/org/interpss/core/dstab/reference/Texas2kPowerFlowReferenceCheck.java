package org.interpss.core.dstab.reference;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;

import org.interpss.IpssCorePlugin;
import org.interpss.fadapter.psse.PSSEMultiFileLoader;

import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

/** Exports the solved operating point before any dynamic-state initialization. */
public final class Texas2kPowerFlowReferenceCheck {
    private Texas2kPowerFlowReferenceCheck() { }

    public static void main(String[] args) throws Exception {
        if (args.length != 3) throw new IllegalArgumentException("Expected plan.json case-number output-folder");
        Path planPath = Path.of(args[0]).toAbsolutePath();
        int number = Integer.parseInt(args[1]);
        Path out = Path.of(args[2]).toAbsolutePath();
        if (Files.exists(out)) throw new IllegalArgumentException("Refusing to replace previous evidence");
        Files.createDirectories(out);
        Map<String, Object> status = new LinkedHashMap<>();
        status.put("case_number", number);
        status.put("scope", "power flow only; before dynamic initialization");
        long started = System.nanoTime();
        try {
            JsonObject plan = JsonParser.parseString(Files.readString(planPath)).getAsJsonObject();
            JsonObject event = null;
            for (var candidate : plan.getAsJsonArray("events")) {
                var object = candidate.getAsJsonObject();
                if (object.get("case_number").getAsInt() == number) { event = object; break; }
            }
            if (event == null) throw new IllegalArgumentException("Missing case " + number);
            IpssCorePlugin.init();
            status.put("sparse_solver", IpssCorePlugin.configureSparseSolver("java-klu").active().toString());
            Path inputs = planPath.getParent().resolve("inputs").resolve(event.get("case_name").getAsString());
            var context = new PSSEMultiFileLoader().loadDStab(
                    inputs.resolve(event.get("raw").getAsString()).toString(),
                    inputs.resolve(event.get("dyr").getAsString()).toString());
            var net = context.getDStabilityNet();
            var lf = context.getDynSimuAlgorithm().getAclfAlgorithm();
            net.setAllowGenWithoutMach(true);
            lf.getDataCheckConfig().setAllowGenWithoutMachine(true);
            lf.getDataCheckConfig().setAutoTurnLine2Xfr(true);
            lf.getDataCheckConfig().setTurnOffIslandBus(true);
            lf.setTolerance(1.e-10);
            lf.setMaxIterations(1000);
            lf.setNonDivergent(true);
            lf.setInitBusVoltage(false);
            boolean enableSavedControls = plan.has("enable_saved_powerflow_controls")
                    && plan.get("enable_saved_powerflow_controls").getAsBoolean();
            lf.getLfAdjAlgo().getVoltAdjConfig().setXfrTapControl(enableSavedControls);
            lf.getLfAdjAlgo().getVoltAdjConfig().setSwitchedShuntAdjust(enableSavedControls);
            lf.getLfAdjAlgo().getPowerAdjConfig().setPsXfrPControl(enableSavedControls);
            lf.getNetAdjAlgo().setAreaInterchangeControlEnabled(enableSavedControls);
            boolean augmentedZeroZ = plan.has("use_augmented_zero_impedance_powerflow")
                    && plan.get("use_augmented_zero_impedance_powerflow").getAsBoolean();
            if (augmentedZeroZ) {
                net.setAclfNetModelType(com.interpss.core.aclf.AclfNetModelType.ZBR_MODEL);
                lf.getDataCheckConfig().setAutoSetZeroZBranch(false);
                lf.getLfCalculator().setNrSolver(new com.interpss.core.algo.impl.solver.ZbrNrSolver(
                        net, lf.getNrMethodConfig()));
            }
            status.put("augmented_zero_impedance_powerflow", augmentedZeroZ);
            status.put("zero_impedance_threshold_pu", net.getZeroZBranchThreshold());
            status.put("eligible_zero_impedance_branches", net.getBranchList().stream()
                    .filter(branch -> branch.isActive() && branch.isZeroZBranch()).count());
            boolean solved = lf.loadflow();
            var report = lf.getTerminationReport();
            status.put("loadflow_termination", report.status().toString());
            status.put("unsettled_controls", report.unsettledControlCount());
            status.put("iterations", report.iteration());
            status.put("maximum_p_mismatch_pu", report.maxPpu());
            status.put("maximum_q_mismatch_pu", report.maxQpu());
            status.put("tolerance_pu", 1.e-10);
            status.put("maximum_iterations", 1000);
            status.put("fixed_taps_shunts_phase_shifts_and_interchange", !enableSavedControls);
            status.put("system_mva_base", net.getBaseMva());
            status.put("powerflow_solver_class", lf.getLfCalculator().getNrSolver().getClass().getName());
            int reactiveViolations = 0;
            try (var writer = Files.newBufferedWriter(out.resolve("reactive-limit-state.csv"))) {
                writer.write("bus,gen_code,control_active,adjust_active,voltage_pu,target_voltage_pu,required_q_pu,q_min_pu,q_max_pu\n");
                for (var bus : net.getBusList()) {
                    var limit = bus.getPVBusLimit();
                    if (!bus.isActive() || limit == null) continue;
                    double q = bus.toPVBus().getGenResults().getImaginary();
                    if (bus.isGenPV() && limit.getQLimit().isViolated(q, 1.e-7)) reactiveViolations++;
                    writer.write(bus.getId() + "," + bus.getGenCode() + "," + limit.isControlStatus()
                            + "," + limit.isAdjustStatus() + "," + bus.getVoltageMag() + ","
                            + limit.getVSpecified() + "," + q + "," + limit.getQLimit().getMin()
                            + "," + limit.getQLimit().getMax() + "\n");
                }
            }
            status.put("pv_bus_reactive_limit_violations", reactiveViolations);
            try (var writer = Files.newBufferedWriter(out.resolve("bus-voltage.csv"))) {
                writer.write("bus,active,voltage_pu,angle_deg\n");
                for (var bus : net.getBusList()) {
                    writer.write(bus.getId() + "," + bus.isActive() + "," + bus.getVoltageMag()
                            + "," + Math.toDegrees(bus.getVoltageAng()) + "\n");
                }
            }
            try (var writer = Files.newBufferedWriter(out.resolve("generators.csv"))) {
                writer.write("bus,id,active,p_pu_system,q_pu_system,mva_base\n");
                for (var bus : net.getBusList()) for (var gen : bus.getContributeGenList()) {
                    writer.write(bus.getId() + "," + gen.getId() + "," + gen.isActive() + ","
                            + gen.getGen().getReal() + "," + gen.getGen().getImaginary()
                            + "," + gen.getMvaBase() + "\n");
                }
            }
            if (!solved || !report.controlsSettled())
                throw new IllegalStateException("Power flow did not fully converge: " + report);
            status.put("status", "completed");
        } catch (Exception failure) {
            status.put("status", "failed");
            status.put("error", failure.toString());
            throw failure;
        } finally {
            status.put("elapsed_s", (System.nanoTime() - started) / 1.e9);
            Files.writeString(out.resolve("status.json"), new GsonBuilder().setPrettyPrinting()
                    .create().toJson(status) + "\n");
        }
    }
}
