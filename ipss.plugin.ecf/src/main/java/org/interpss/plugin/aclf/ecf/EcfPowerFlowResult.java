package org.interpss.plugin.aclf.ecf;

import java.util.Map;
import org.apache.commons.math3.complex.Complex;

/** Immutable accepted point. Powers and currents are per unit. Results never
 * publish into the caller's mutable network, including on successful solves. */
public record EcfPowerFlowResult(Status status, String detail, int iterations,
        double maximumCurrentMismatch, double maximumPowerMismatch, double maximumVoltageConstraint,
        Map<String, Complex> voltages, Map<String, Complex> generation,
        Map<String, TerminalFlow> branchFlows) {
    public enum Status { CONVERGED, INVALID_INPUT, UNSUPPORTED_MODEL, UNSUPPLIED_ISLAND,
        NO_CONVERGENCE, NUMERICAL_FAILURE }
    public record TerminalFlow(Complex fromCurrent, Complex toCurrent, Complex fromPower, Complex toPower) { }
    public EcfPowerFlowResult {
        voltages = Map.copyOf(voltages); generation = Map.copyOf(generation); branchFlows = Map.copyOf(branchFlows);
    }
    public boolean converged() { return status == Status.CONVERGED; }
}
