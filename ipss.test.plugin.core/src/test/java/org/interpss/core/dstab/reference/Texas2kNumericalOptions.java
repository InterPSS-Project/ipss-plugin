package org.interpss.core.dstab.reference;

import java.util.Map;

import com.google.gson.JsonObject;
import com.interpss.dstab.algo.defaultImpl.DStabSolverImpl.VoltageConvergenceNorm;
import org.interpss.dstab.algo.PsseStaggeredEulerSolver;
import org.interpss.dstab.algo.PsseStaggeredEulerSolver.MachineMeasurementStage;

/** Explicit numerical boundaries shared by accuracy and timing producers. */
record Texas2kNumericalOptions(double networkTolerance, VoltageConvergenceNorm norm,
        int minimumIterations, boolean reuseEndpoint, boolean loadZInMatrix,
        boolean converterVoltageMemory, MachineMeasurementStage machineStage, boolean rectangularNetworkVoltage) {
    Texas2kNumericalOptions {
        if (!Double.isFinite(networkTolerance) || networkTolerance <= 0.)
            throw new IllegalArgumentException("Network tolerance must be finite and positive");
        if (minimumIterations < 1 || minimumIterations > 500)
            throw new IllegalArgumentException("Minimum network iterations must be between 1 and 500");
        java.util.Objects.requireNonNull(norm);
        java.util.Objects.requireNonNull(machineStage);
    }

    static Texas2kNumericalOptions read(JsonObject plan) {
        return new Texas2kNumericalOptions(
                plan.has("network_voltage_tolerance") ? plan.get("network_voltage_tolerance").getAsDouble() : 1.e-8,
                plan.has("network_convergence_norm") ? VoltageConvergenceNorm.valueOf(plan.get("network_convergence_norm").getAsString()) : VoltageConvergenceNorm.MAX_COMPONENT,
                plan.has("minimum_network_iterations") ? plan.get("minimum_network_iterations").getAsInt() : 2,
                enabled(plan, "reuse_network_endpoint"), enabled(plan, "static_load_z_part_in_y_matrix"),
                enabled(plan, "converter_network_voltage_memory"),
                plan.has("machine_measurement_stage") ? MachineMeasurementStage.valueOf(plan.get("machine_measurement_stage").getAsString()) : MachineMeasurementStage.LAST_NETWORK_INJECTION,
                enabled(plan, "rectangular_network_voltage"));
    }

    private static boolean enabled(JsonObject plan, String key) {
        return plan.has(key) && plan.get(key).getAsBoolean();
    }

    void configure(PsseStaggeredEulerSolver solver) {
        solver.setNetworkSolutionTolerance(networkTolerance);
        solver.setVoltageConvergenceNorm(norm);
        solver.setMinimumNetworkIterations(minimumIterations);
        solver.setReuseNetworkEndpoint(reuseEndpoint);
        solver.setMachineMeasurementStage(machineStage);
        solver.setUseRectangularNetworkVoltage(rectangularNetworkVoltage);
    }

    void report(Map<String, Object> status) {
        status.put("network_voltage_tolerance", networkTolerance);
        status.put("network_convergence_norm", norm.toString());
        status.put("minimum_network_iterations", minimumIterations);
        status.put("reuse_network_endpoint", reuseEndpoint);
        status.put("static_load_z_part_in_y_matrix", loadZInMatrix);
        status.put("converter_network_voltage_memory", converterVoltageMemory);
        status.put("machine_measurement_stage", machineStage.toString());
        status.put("rectangular_network_voltage", rectangularNetworkVoltage);
    }
}
