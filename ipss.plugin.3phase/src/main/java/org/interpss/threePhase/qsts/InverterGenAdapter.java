package org.interpss.threePhase.qsts;

import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

import org.apache.commons.math3.complex.Complex;
import org.interpss.numeric.datatype.Complex3x1;
import org.interpss.numeric.datatype.Unit.UnitType;
import org.interpss.threePhase.powerflow.control.InverterControlData;
import org.interpss.threePhase.powerflow.control.InverterControlModel;
import org.interpss.threePhase.powerflow.control.InverterControlModel.InverterControlResult;

public class InverterGenAdapter {
	private final com.interpss.core.threephase.AclfGen3Phase generator;
	private final InverterControlModel controlModel;
	private final Map<String, QstsControlCurve> curvesById = new LinkedHashMap<>();
	private InverterCapabilityData capabilityData = InverterCapabilityData.none();
	private double terminalVoltagePu = Double.NaN;
	private boolean cutInState = true;

	public InverterGenAdapter(com.interpss.core.threephase.AclfGen3Phase generator) {
		this(generator, new InverterControlModel());
	}

	public InverterGenAdapter(com.interpss.core.threephase.AclfGen3Phase generator,
			InverterControlModel controlModel) {
		if(generator == null) {
			throw new IllegalArgumentException("Inverter adapter requires AclfGen3Phase");
		}
		this.generator = generator;
		this.controlModel = controlModel == null ? new InverterControlModel() : controlModel;
	}

	public com.interpss.core.threephase.AclfGen3Phase getGenerator() {
		return generator;
	}

	public String getGeneratorId() {
		return generator.getId();
	}

	public InverterGenAdapter addCurve(QstsControlCurve curve) {
		if(curve != null) {
			curvesById.put(normalize(curve.getId()), curve);
		}
		return this;
	}

	public InverterGenAdapter setCapabilityData(InverterCapabilityData capabilityData) {
		this.capabilityData = capabilityData == null ? InverterCapabilityData.none() : capabilityData;
		this.cutInState = this.capabilityData.getCutInPowerKw() <= 0.0;
		return this;
	}

	public InverterCapabilityData getCapabilityData() {
		return capabilityData;
	}

	public InverterGenAdapter setTerminalVoltage(Complex3x1 terminalVoltage) {
		this.terminalVoltagePu = averageMagnitude(terminalVoltage);
		return this;
	}

	public InverterGenAdapter setTerminalVoltagePu(double terminalVoltagePu) {
		this.terminalVoltagePu = terminalVoltagePu;
		return this;
	}

	public InverterControlResult apply(InverterControlData control, QstsStepContext context,
			double networkBaseKva) {
		if(control == null || !control.isEnabled() || !capabilityAllowsControl(networkBaseKva)) {
			return InverterControlResult.notApplied();
		}
		InverterControlData effectiveControl = effectiveControl(control);
		if(control.getControlMode() == InverterControlData.ControlMode.VOLTWATT) {
			double target = limitAvailableActivePower(resolvedActivePowerTarget(control));
			if(Double.isFinite(target)) {
				return controlModel.applyActivePowerSetpoint(generator, networkBaseKva,
						effectiveControl, target);
			}
			return InverterControlResult.notApplied();
		}
		if(control.getControlMode() == InverterControlData.ControlMode.WATTPF) {
			double target = resolvedPowerFactorTarget(control, networkBaseKva);
			if(Double.isFinite(target)) {
				return controlModel.applyPowerFactorSetpoint(generator, networkBaseKva,
						effectiveControl, target);
			}
			return InverterControlResult.notApplied();
		}
		double target = resolvedReactivePowerTarget(control, networkBaseKva);
		if(Double.isFinite(target)) {
			if(control.getControlMode() == InverterControlData.ControlMode.WATTVAR) {
				return controlModel.applyWattVarSetpoint(generator, networkBaseKva,
						effectiveControl, target);
			}
			return controlModel.applyVoltVarSetpoint(generator, networkBaseKva,
					effectiveControl, target);
		}
		return InverterControlResult.notApplied();
	}

	public double getCurrentActivePowerKw(double networkBaseKva) {
		return totalPower().getReal() * networkBaseKva;
	}

	public double getCurrentReactivePowerKvar(double networkBaseKva) {
		return totalPower().getImaginary() * networkBaseKva;
	}

	private double resolvedActivePowerTarget(InverterControlData control) {
		if(Double.isFinite(control.getTargetActivePowerKw())) {
			return control.getTargetActivePowerKw();
		}
		QstsControlCurve curve = curve(control);
		return curve == null || !Double.isFinite(terminalVoltagePu)
				? Double.NaN : curve.evaluate(terminalVoltagePu);
	}

	private double resolvedReactivePowerTarget(InverterControlData control, double networkBaseKva) {
		if(Double.isFinite(control.getTargetReactivePowerKvar())) {
			return control.getTargetReactivePowerKvar();
		}
		QstsControlCurve curve = curve(control);
		if(curve == null) {
			return Double.NaN;
		}
		if(control.getControlMode() == InverterControlData.ControlMode.WATTVAR) {
			return curve.evaluate(getCurrentActivePowerKw(networkBaseKva));
		}
		return Double.isFinite(terminalVoltagePu) ? curve.evaluate(terminalVoltagePu) : Double.NaN;
	}

	private double resolvedPowerFactorTarget(InverterControlData control, double networkBaseKva) {
		if(Double.isFinite(control.getTargetPowerFactor())) {
			return control.getTargetPowerFactor();
		}
		QstsControlCurve curve = curve(control);
		return curve == null ? Double.NaN : curve.evaluate(getCurrentActivePowerKw(networkBaseKva));
	}

	private double limitAvailableActivePower(double targetActivePowerKw) {
		double available = capabilityData.getAvailableActivePowerKw();
		if(!Double.isFinite(targetActivePowerKw) || !Double.isFinite(available) || available < 0.0) {
			return targetActivePowerKw;
		}
		if(targetActivePowerKw >= 0.0) {
			return Math.min(targetActivePowerKw, available);
		}
		return targetActivePowerKw;
	}

	private boolean capabilityAllowsControl(double networkBaseKva) {
		if(!capabilityData.isEnabled()) {
			return false;
		}
		double activePowerKw = Math.abs(getCurrentActivePowerKw(networkBaseKva));
		if(cutInState && capabilityData.getCutOutPowerKw() > 0.0
				&& activePowerKw < capabilityData.getCutOutPowerKw()) {
			cutInState = false;
		}
		if(!cutInState && capabilityData.getCutInPowerKw() > 0.0
				&& activePowerKw >= capabilityData.getCutInPowerKw()) {
			cutInState = true;
		}
		return cutInState;
	}

	private InverterControlData effectiveControl(InverterControlData control) {
		double ratedKva = control.getRatedKva() > 0.0
				? control.getRatedKva() : capabilityData.getRatedKva();
		double minQ = Double.isFinite(control.getMinReactivePowerKvar())
				? control.getMinReactivePowerKvar() : capabilityData.getMinReactivePowerKvar();
		double maxQ = Double.isFinite(control.getMaxReactivePowerKvar())
				? control.getMaxReactivePowerKvar() : capabilityData.getMaxReactivePowerKvar();
		return new InverterControlData(control.getId(), control.getGeneratorId(),
				control.getControlMode(), control.getCurveId(), ratedKva, minQ, maxQ,
				control.getMinPowerFactor(), control.getTargetActivePowerKw(),
				control.getTargetReactivePowerKvar(), control.getTargetPowerFactor(),
				control.isEnabled());
	}

	private QstsControlCurve curve(InverterControlData control) {
		return curvesById.get(normalize(control.getCurveId()));
	}

	private Complex totalPower() {
		Complex3x1 power = generator.getPower3Phase(UnitType.PU);
		if(power == null) {
			return Complex.ZERO;
		}
		return value(power.a_0).add(value(power.b_1)).add(value(power.c_2));
	}

	private double averageMagnitude(Complex3x1 voltage) {
		if(voltage == null) {
			return Double.NaN;
		}
		double sum = 0.0;
		int count = 0;
		for(Complex value : new Complex[] {voltage.a_0, voltage.b_1, voltage.c_2}) {
			if(value != null && value.abs() > 1.0e-12) {
				sum += value.abs();
				count++;
			}
		}
		return count == 0 ? Double.NaN : sum / count;
	}

	private static Complex value(Complex value) {
		return value == null ? Complex.ZERO : value;
	}

	private static String normalize(String value) {
		return value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
	}
}
