package org.interpss.threePhase.opf.dist;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.apache.commons.math3.complex.Complex;
import org.interpss.numeric.datatype.Complex3x1;
import org.interpss.numeric.datatype.Unit.UnitType;
import org.interpss.threePhase.basic.dstab.DStab3PBus;
import org.interpss.threePhase.basic.dstab.DStab3PGen;
import org.interpss.threePhase.dynamic.DStabNetwork3Phase;

public class DistOpfResult {

	private final DistOpfStatus status;
	private final double objectiveValue;
	private final double maxConstraintResidual;
	private final List<String> warnings = new ArrayList<String>();
	private final List<String> bindingConstraints = new ArrayList<String>();
	private final List<String> diagnostics = new ArrayList<String>();
	private final Map<String, Double> busVoltageSquared = new LinkedHashMap<String, Double>();
	private final Map<String, Double> branchActivePower = new LinkedHashMap<String, Double>();
	private final Map<String, Double> branchReactivePower = new LinkedHashMap<String, Double>();
	private final Map<String, Double> derActivePower = new LinkedHashMap<String, Double>();
	private final Map<String, Double> derReactivePower = new LinkedHashMap<String, Double>();
	private final Map<String, Double> capacitorStatus = new LinkedHashMap<String, Double>();
	private Boolean powerFlowConverged;
	private int powerFlowIterationCount = -1;
	private double maxPowerFlowVoltageDiff = Double.NaN;
	private double maxPowerFlowVoltageViolation = Double.NaN;
	private double maxPowerFlowBranchLimitViolation = Double.NaN;

	public DistOpfResult(DistOpfStatus status, double objectiveValue, double maxConstraintResidual) {
		this.status = status;
		this.objectiveValue = objectiveValue;
		this.maxConstraintResidual = maxConstraintResidual;
	}

	public DistOpfStatus getStatus() {
		return status;
	}

	public boolean isSolved() {
		return status == DistOpfStatus.OPTIMAL || status == DistOpfStatus.FEASIBLE;
	}

	public double getObjectiveValue() {
		return objectiveValue;
	}

	public double getMaxConstraintResidual() {
		return maxConstraintResidual;
	}

	public List<String> getWarnings() {
		return Collections.unmodifiableList(warnings);
	}

	public DistOpfResult addWarning(String warning) {
		this.warnings.add(warning);
		return this;
	}

	public List<String> getBindingConstraints() {
		return Collections.unmodifiableList(bindingConstraints);
	}

	public DistOpfResult addBindingConstraint(String bindingConstraint) {
		this.bindingConstraints.add(bindingConstraint);
		return this;
	}

	public DistOpfResult addBindingConstraints(List<String> bindingConstraints) {
		this.bindingConstraints.addAll(bindingConstraints);
		return this;
	}

	public List<String> getDiagnostics() {
		return Collections.unmodifiableList(diagnostics);
	}

	public DistOpfResult addDiagnostic(String diagnostic) {
		this.diagnostics.add(diagnostic);
		return this;
	}

	public DistOpfResult addDiagnostics(List<String> diagnostics) {
		this.diagnostics.addAll(diagnostics);
		return this;
	}

	public DistOpfResult putBusVoltageSquared(String busId, String phase, double value) {
		this.busVoltageSquared.put(key(busId, phase), value);
		return this;
	}

	public DistOpfResult putBranchActivePower(String branchId, String phase, double value) {
		this.branchActivePower.put(key(branchId, phase), value);
		return this;
	}

	public DistOpfResult putBranchReactivePower(String branchId, String phase, double value) {
		this.branchReactivePower.put(key(branchId, phase), value);
		return this;
	}

	public DistOpfResult putDerActivePower(String derId, String phase, double value) {
		this.derActivePower.put(key(derId, phase), value);
		return this;
	}

	public DistOpfResult putDerReactivePower(String derId, String phase, double value) {
		this.derReactivePower.put(key(derId, phase), value);
		return this;
	}

	public DistOpfResult putCapacitorStatus(String capacitorId, String phase, double value) {
		this.capacitorStatus.put(key(capacitorId, phase), value);
		return this;
	}

	public Double getBusVoltageSquared(String busId, String phase) {
		return this.busVoltageSquared.get(key(busId, phase));
	}

	public Double getBranchActivePower(String branchId, String phase) {
		return this.branchActivePower.get(key(branchId, phase));
	}

	public Double getBranchReactivePower(String branchId, String phase) {
		return this.branchReactivePower.get(key(branchId, phase));
	}

	public Double getDerActivePower(String derId, String phase) {
		return this.derActivePower.get(key(derId, phase));
	}

	public Double getDerReactivePower(String derId, String phase) {
		return this.derReactivePower.get(key(derId, phase));
	}

	public Double getCapacitorStatus(String capacitorId, String phase) {
		return this.capacitorStatus.get(key(capacitorId, phase));
	}

	public Map<String, Double> getBusVoltageSquared() {
		return Collections.unmodifiableMap(busVoltageSquared);
	}

	public Map<String, Double> getBranchActivePower() {
		return Collections.unmodifiableMap(branchActivePower);
	}

	public Map<String, Double> getBranchReactivePower() {
		return Collections.unmodifiableMap(branchReactivePower);
	}

	public Map<String, Double> getDerActivePower() {
		return Collections.unmodifiableMap(derActivePower);
	}

	public Map<String, Double> getDerReactivePower() {
		return Collections.unmodifiableMap(derReactivePower);
	}

	public Map<String, Double> getCapacitorStatus() {
		return Collections.unmodifiableMap(capacitorStatus);
	}

	public Boolean getPowerFlowConverged() {
		return powerFlowConverged;
	}

	public DistOpfResult setPowerFlowConverged(boolean powerFlowConverged) {
		this.powerFlowConverged = Boolean.valueOf(powerFlowConverged);
		return this;
	}

	public int getPowerFlowIterationCount() {
		return powerFlowIterationCount;
	}

	public DistOpfResult setPowerFlowIterationCount(int powerFlowIterationCount) {
		this.powerFlowIterationCount = powerFlowIterationCount;
		return this;
	}

	public double getMaxPowerFlowVoltageDiff() {
		return maxPowerFlowVoltageDiff;
	}

	public DistOpfResult setMaxPowerFlowVoltageDiff(double maxPowerFlowVoltageDiff) {
		this.maxPowerFlowVoltageDiff = maxPowerFlowVoltageDiff;
		return this;
	}

	public double getMaxPowerFlowVoltageViolation() {
		return maxPowerFlowVoltageViolation;
	}

	public DistOpfResult setMaxPowerFlowVoltageViolation(double maxPowerFlowVoltageViolation) {
		this.maxPowerFlowVoltageViolation = maxPowerFlowVoltageViolation;
		return this;
	}

	public double getMaxPowerFlowBranchLimitViolation() {
		return maxPowerFlowBranchLimitViolation;
	}

	public DistOpfResult setMaxPowerFlowBranchLimitViolation(double maxPowerFlowBranchLimitViolation) {
		this.maxPowerFlowBranchLimitViolation = maxPowerFlowBranchLimitViolation;
		return this;
	}

	public void applySetpointsToNetwork(DStabNetwork3Phase net) {
		for (DStab3PBus bus : net.getBusList()) {
			for (DStab3PGen gen : bus.getThreePhaseGenList()) {
				applySetpoint(gen);
			}
			for (DStab3PGen gen : bus.getContributeGenList()) {
				applySetpoint(gen);
			}
		}
	}

	private static String key(String id, String phase) {
		return id + "." + phase;
	}

	private void applySetpoint(DStab3PGen gen) {
		if (gen == null || gen.getId() == null || !hasDerSetpoint(gen.getId())) {
			return;
		}
		Complex3x1 current = gen.getPower3Phase(UnitType.PU);
		Complex3x1 updated = current == null ? new Complex3x1() : current;
		updated.a_0 = phaseSetpoint(gen.getId(), "A", updated.a_0);
		updated.b_1 = phaseSetpoint(gen.getId(), "B", updated.b_1);
		updated.c_2 = phaseSetpoint(gen.getId(), "C", updated.c_2);
		gen.setPower3Phase(updated, UnitType.PU);
	}

	private boolean hasDerSetpoint(String derId) {
		return derActivePower.containsKey(key(derId, "A"))
				|| derActivePower.containsKey(key(derId, "B"))
				|| derActivePower.containsKey(key(derId, "C"))
				|| derReactivePower.containsKey(key(derId, "A"))
				|| derReactivePower.containsKey(key(derId, "B"))
				|| derReactivePower.containsKey(key(derId, "C"));
	}

	private Complex phaseSetpoint(String derId, String phase, Complex current) {
		Double p = derActivePower.get(key(derId, phase));
		Double q = derReactivePower.get(key(derId, phase));
		if (p == null && q == null) {
			return current == null ? Complex.ZERO : current;
		}
		double real = p == null ? (current == null ? 0.0 : current.getReal()) : p.doubleValue();
		double imag = q == null ? (current == null ? 0.0 : current.getImaginary()) : q.doubleValue();
		return new Complex(real, imag);
	}
}
