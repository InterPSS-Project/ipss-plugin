package org.interpss.threePhase.opf.dist.model;

import java.util.Collections;
import java.util.EnumSet;
import java.util.EnumMap;
import java.util.Map;
import java.util.Set;

import org.interpss.numeric.datatype.Complex3x3;

import com.interpss.core.acsc.PhaseCode;

public class DistOpfBranchData {

	private final String id;
	private final String fromBusId;
	private final String toBusId;
	private final Set<PhaseCode> phases;
	private final Complex3x3 zabc;
	private final Double thermalLimitPu;
	private final double voltageRatio;
	private final Map<PhaseCode, Double> voltageRatioByPhase;
	private final boolean fixedVoltageRatioOnly;

	public DistOpfBranchData(String id, String fromBusId, String toBusId,
			Set<PhaseCode> phases, Complex3x3 zabc) {
		this(id, fromBusId, toBusId, phases, zabc, null);
	}

	public DistOpfBranchData(String id, String fromBusId, String toBusId,
			Set<PhaseCode> phases, Complex3x3 zabc, Double thermalLimitPu) {
		this(id, fromBusId, toBusId, phases, zabc, thermalLimitPu, 1.0);
	}

	public DistOpfBranchData(String id, String fromBusId, String toBusId,
			Set<PhaseCode> phases, Complex3x3 zabc, Double thermalLimitPu, double voltageRatio) {
		this(id, fromBusId, toBusId, phases, zabc, thermalLimitPu, voltageRatio, null);
	}

	public DistOpfBranchData(String id, String fromBusId, String toBusId,
			Set<PhaseCode> phases, Complex3x3 zabc, Double thermalLimitPu,
			double voltageRatio, Map<PhaseCode, Double> voltageRatioByPhase) {
		this(id, fromBusId, toBusId, phases, zabc, thermalLimitPu, voltageRatio, voltageRatioByPhase, false);
	}

	public DistOpfBranchData(String id, String fromBusId, String toBusId,
			Set<PhaseCode> phases, Complex3x3 zabc, Double thermalLimitPu,
			double voltageRatio, Map<PhaseCode, Double> voltageRatioByPhase, boolean fixedVoltageRatioOnly) {
		this.id = id;
		this.fromBusId = fromBusId;
		this.toBusId = toBusId;
		this.phases = Collections.unmodifiableSet(EnumSet.copyOf(phases));
		this.zabc = zabc;
		this.thermalLimitPu = thermalLimitPu;
		this.voltageRatio = voltageRatio;
		this.voltageRatioByPhase = copyVoltageRatios(voltageRatioByPhase);
		this.fixedVoltageRatioOnly = fixedVoltageRatioOnly;
	}

	public String getId() {
		return id;
	}

	public String getFromBusId() {
		return fromBusId;
	}

	public String getToBusId() {
		return toBusId;
	}

	public Set<PhaseCode> getPhases() {
		return phases;
	}

	public Complex3x3 getZabc() {
		return zabc;
	}

	public Double getThermalLimitPu() {
		return thermalLimitPu;
	}

	public double getVoltageRatio() {
		return voltageRatio;
	}

	public double getVoltageRatio(PhaseCode phase) {
		Double ratio = voltageRatioByPhase.get(phase);
		return ratio == null ? voltageRatio : ratio.doubleValue();
	}

	public boolean isFixedVoltageRatioOnly() {
		return fixedVoltageRatioOnly;
	}

	private static Map<PhaseCode, Double> copyVoltageRatios(Map<PhaseCode, Double> source) {
		Map<PhaseCode, Double> copy = new EnumMap<PhaseCode, Double>(PhaseCode.class);
		if (source != null) {
			copy.putAll(source);
		}
		return Collections.unmodifiableMap(copy);
	}
}
