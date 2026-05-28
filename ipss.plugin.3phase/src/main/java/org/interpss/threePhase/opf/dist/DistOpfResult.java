package org.interpss.threePhase.opf.dist;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.interpss.threePhase.dynamic.DStabNetwork3Phase;

public class DistOpfResult {

	private final DistOpfStatus status;
	private final double objectiveValue;
	private final double maxConstraintResidual;
	private final List<String> warnings = new ArrayList<String>();
	private final Map<String, Double> busVoltageSquared = new LinkedHashMap<String, Double>();
	private final Map<String, Double> branchActivePower = new LinkedHashMap<String, Double>();
	private final Map<String, Double> branchReactivePower = new LinkedHashMap<String, Double>();

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

	public Double getBusVoltageSquared(String busId, String phase) {
		return this.busVoltageSquared.get(key(busId, phase));
	}

	public Double getBranchActivePower(String branchId, String phase) {
		return this.branchActivePower.get(key(branchId, phase));
	}

	public Double getBranchReactivePower(String branchId, String phase) {
		return this.branchReactivePower.get(key(branchId, phase));
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

	public void applySetpointsToNetwork(DStabNetwork3Phase net) {
		// Setpoint mapping is added with DER extraction. Keep solve() non-mutating.
	}

	private static String key(String id, String phase) {
		return id + "." + phase;
	}
}
