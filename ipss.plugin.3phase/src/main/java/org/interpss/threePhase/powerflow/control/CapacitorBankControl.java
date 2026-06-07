package org.interpss.threePhase.powerflow.control;

import java.util.List;
import java.util.IdentityHashMap;
import java.util.Map;

import org.apache.commons.math3.complex.Complex;
import org.interpss.numeric.datatype.Complex3x1;
import org.interpss.threePhase.qsts.control.QstsControlAction;
import org.interpss.threePhase.qsts.control.QstsControlQueue;
import org.interpss.threePhase.powerflow.control.CapacitorControlData.ControlType;
import org.interpss.threePhase.powerflow.control.CapacitorControlData.PhaseSelection;

import com.interpss.core.aclf.AclfBranch;
import com.interpss.core.aclf.BaseAclfBus;
import com.interpss.core.aclf.BaseAclfNetwork;
import com.interpss.core.acsc.PhaseCode;
import com.interpss.core.net.Branch;
import com.interpss.core.threephase.IBranch3Phase;
import com.interpss.core.threephase.IBus3Phase;
import com.interpss.core.threephase.INetwork3Phase;
import com.interpss.core.threephase.IPhaseLoad;

public class CapacitorBankControl {
	private final Map<IPhaseLoad, Complex3x1> baseLoadByCapacitor = new IdentityHashMap<IPhaseLoad, Complex3x1>();

	public boolean apply(INetwork3Phase network, List<CapacitorControlData> controls) {
		if(controls == null || controls.isEmpty()) {
			return false;
		}
		BaseAclfNetwork<?, ?> aclfNetwork = aclfNetwork(network);
		boolean changed = false;
		for(CapacitorControlData control : controls) {
			IPhaseLoad capacitor = findCapacitorLoad(aclfNetwork, control.getCapacitorId());
			if(capacitor == null) {
				continue;
			}
			CapacitorMeasurement measurement = measure(aclfNetwork, control, capacitor);
			Boolean nextState = requestedState(control, measurement);
			if(nextState == null || nextState.booleanValue() == control.isClosed()) {
				continue;
			}
			setClosedState(control, capacitor, nextState.booleanValue());
			changed = true;
		}
		return changed;
	}

	public int scheduleDelayed(INetwork3Phase network, List<CapacitorControlData> controls,
			QstsControlQueue queue, double currentTimeSeconds) {
		if(controls == null || controls.isEmpty() || queue == null) {
			return 0;
		}
		BaseAclfNetwork<?, ?> aclfNetwork = aclfNetwork(network);
		int scheduled = 0;
		for(CapacitorControlData control : controls) {
			IPhaseLoad capacitor = findCapacitorLoad(aclfNetwork, control.getCapacitorId());
			if(capacitor == null) {
				continue;
			}
			CapacitorMeasurement measurement = measure(aclfNetwork, control, capacitor);
			Boolean nextState = requestedState(control, measurement);
			String key = controlActionKey(control);
			if(nextState == null || nextState.booleanValue() == control.isClosed()) {
				queue.cancel(key);
				continue;
			}
			double delaySeconds = nextState.booleanValue()
					? control.getOnDelaySeconds() : control.getOffDelaySeconds();
			queue.schedule(new CapacitorSwitchAction(key, currentTimeSeconds + delaySeconds,
					control, capacitor, nextState.booleanValue()));
			scheduled++;
		}
		return scheduled;
	}

	@SuppressWarnings("unchecked")
	private BaseAclfNetwork<?, ?> aclfNetwork(INetwork3Phase network) {
		if(network instanceof BaseAclfNetwork) {
			return (BaseAclfNetwork<?, ?>) network;
		}
		throw new UnsupportedOperationException("Capacitor controls require a BaseAclfNetwork-backed three-phase network");
	}

	private Boolean requestedState(CapacitorControlData control, CapacitorMeasurement measurement) {
		if(!Double.isFinite(measurement.controlValue)) {
			return null;
		}
		if(control.isVoltageOverride()) {
			if(control.getVMin() > 0.0 && measurement.localVoltage < control.getVMin()) {
				return Boolean.TRUE;
			}
			if(control.getVMax() > 0.0 && measurement.localVoltage > control.getVMax()) {
				return Boolean.FALSE;
			}
		}
		if(control.getControlType() == ControlType.VOLTAGE) {
			if(!control.isClosed() && measurement.controlValue <= control.getOnSetting()) {
				return Boolean.TRUE;
			}
			if(control.isClosed() && measurement.controlValue >= control.getOffSetting()) {
				return Boolean.FALSE;
			}
			return null;
		}
		if(control.getControlType() == ControlType.CURRENT || control.getControlType() == ControlType.KVAR
				|| control.getControlType() == ControlType.PF) {
			if(!control.isClosed() && measurement.controlValue >= control.getOnSetting()) {
				return Boolean.TRUE;
			}
			if(control.isClosed() && measurement.controlValue <= control.getOffSetting()) {
				return Boolean.FALSE;
			}
		}
		return null;
	}

	private CapacitorMeasurement measure(BaseAclfNetwork<?, ?> network, CapacitorControlData control,
			IPhaseLoad capacitor) {
		IBranch3Phase branch = control.hasMonitoredElement()
				? findBranch(network, control.getMonitoredElementName()) : null;
		BaseAclfBus<?, ?> bus = branch == null ? parentBus(network, capacitor) : terminalBus(branch, control.getTerminal());
		if(!(bus instanceof IBus3Phase) || ((IBus3Phase) bus).get3PhaseVotlages() == null) {
			return new CapacitorMeasurement(Double.NaN, Double.NaN);
		}
		double localVoltage = selectedPhysicalVoltage((IBus3Phase) bus, control, 1.0);
		if(control.getControlType() == ControlType.VOLTAGE || branch == null) {
			return new CapacitorMeasurement(selectedPhysicalVoltage((IBus3Phase) bus, control,
					control.getPtRatio()), localVoltage);
		}
		Complex3x1 currentPu = branchCurrent(branch, control.getTerminal());
		Complex3x1 voltagePu = ((IBus3Phase) bus).get3PhaseVotlages();
		if(control.getControlType() == ControlType.CURRENT) {
			double current = selectedMagnitude(currentPu.multiply(baseCurrent(network, bus) / control.getCtRatio()),
					control);
			return new CapacitorMeasurement(current, localVoltage);
		}
		Complex3x1 powerKva = voltagePu.multiply(currentPu.conjugate()).multiply(network.getBaseKva());
		if(control.getControlType() == ControlType.KVAR) {
			return new CapacitorMeasurement(selectedReactivePower(powerKva, control), localVoltage);
		}
		if(control.getControlType() == ControlType.PF) {
			Complex power = selected(powerKva, control);
			double pf = power.abs() == 0.0 ? 1.0 : power.getReal() / power.abs();
			return new CapacitorMeasurement(pf, localVoltage);
		}
		return new CapacitorMeasurement(Double.NaN, localVoltage);
	}

	private void applyState(IPhaseLoad capacitor, boolean closed) {
		Complex3x1 baseLoad = baseLoadByCapacitor.get(capacitor);
		if(baseLoad == null) {
			baseLoad = initialLoad(capacitor);
			baseLoadByCapacitor.put(capacitor, baseLoad);
		}
		capacitor.set3PhaseLoad(closed ? copy(baseLoad) : zero());
	}

	private void setClosedState(CapacitorControlData control, IPhaseLoad capacitor, boolean closed) {
		control.setClosed(closed);
		applyState(capacitor, closed);
	}

	private String controlActionKey(CapacitorControlData control) {
		return "capacitor:" + (control.getCapacitorId() == null ? control.getId() : control.getCapacitorId());
	}

	private Complex3x1 initialLoad(IPhaseLoad capacitor) {
		Complex3x1 load = capacitor.getInit3PhaseLoad();
		return load == null ? zero() : copy(load);
	}

	private IPhaseLoad findCapacitorLoad(BaseAclfNetwork<?, ?> network, String capacitorId) {
		for(BaseAclfBus<?, ?> bus : (List<BaseAclfBus<?, ?>>) network.getBusList()) {
			if(bus instanceof IBus3Phase) {
				for(IPhaseLoad load : ((IBus3Phase) bus).getPhaseLoadList()) {
					if(matches(load.getId(), capacitorId)) {
						return load;
					}
				}
			}
			for(Object load : bus.getContributeLoadList()) {
				if(load instanceof IPhaseLoad && matches(((IPhaseLoad) load).getId(), capacitorId)) {
					return (IPhaseLoad) load;
				}
			}
		}
		return null;
	}

	private BaseAclfBus<?, ?> parentBus(BaseAclfNetwork<?, ?> network, IPhaseLoad capacitor) {
		for(BaseAclfBus<?, ?> bus : (List<BaseAclfBus<?, ?>>) network.getBusList()) {
			if(bus instanceof IBus3Phase && ((IBus3Phase) bus).getPhaseLoadList().contains(capacitor)) {
				return bus;
			}
			if(bus.getContributeLoadList().contains(capacitor)) {
				return bus;
			}
		}
		return null;
	}

	private IBranch3Phase findBranch(BaseAclfNetwork<?, ?> network, String name) {
		String normalizedName = normalizeElementName(name);
		for(AclfBranch branch : (List<AclfBranch>) network.getBranchList()) {
			if(branch.isActive() && branch instanceof IBranch3Phase
					&& (matches(branch.getName(), normalizedName) || matches(branch.getId(), normalizedName))) {
				return (IBranch3Phase) branch;
			}
		}
		return null;
	}

	private BaseAclfBus<?, ?> terminalBus(IBranch3Phase branch, int terminal) {
		Branch aclfBranch = (Branch) branch;
		return terminal == 2 ? (BaseAclfBus<?, ?>) aclfBranch.getToBus()
				: (BaseAclfBus<?, ?>) aclfBranch.getFromBus();
	}

	private Complex3x1 branchCurrent(IBranch3Phase branch, int terminal) {
		Branch aclfBranch = (Branch) branch;
		Complex3x1 fromVoltage = ((IBus3Phase) aclfBranch.getFromBus()).get3PhaseVotlages();
		Complex3x1 toVoltage = ((IBus3Phase) aclfBranch.getToBus()).get3PhaseVotlages();
		return terminal == 2
				? branch.getYttabc().multiply(toVoltage).add(branch.getYtfabc().multiply(fromVoltage))
				: branch.getYffabc().multiply(fromVoltage).add(branch.getYftabc().multiply(toVoltage));
	}

	private double selectedPhysicalVoltage(IBus3Phase bus, CapacitorControlData control, double ratio) {
		return selectedMagnitude(bus.get3PhaseVotlages().multiply(basePhaseVoltage((BaseAclfBus<?, ?>) bus) / ratio),
				control);
	}

	private double selectedReactivePower(Complex3x1 powerKva, CapacitorControlData control) {
		if(control.getPhaseSelection() == PhaseSelection.AVG) {
			double sum = 0.0;
			int count = 0;
			for(int phase : activePhases(control.getPhaseCode())) {
				sum += phaseValue(powerKva, phase).getImaginary();
				count++;
			}
			return count == 0 ? Double.NaN : sum / count;
		}
		return selected(powerKva, control).getImaginary();
	}

	private Complex selected(Complex3x1 values, CapacitorControlData control) {
		Complex selected = null;
		double sumReal = 0.0;
		double sumImag = 0.0;
		int count = 0;
		for(int phase : activePhases(control.getPhaseCode())) {
			Complex value = phaseValue(values, phase);
			if(control.getPhaseSelection() == PhaseSelection.AVG) {
				sumReal += value.getReal();
				sumImag += value.getImaginary();
				count++;
				continue;
			}
			if(selected == null) {
				selected = value;
			}
			else if(control.getPhaseSelection() == PhaseSelection.MAX && value.abs() > selected.abs()) {
				selected = value;
			}
			else if(control.getPhaseSelection() == PhaseSelection.MIN && value.abs() < selected.abs()) {
				selected = value;
			}
		}
		if(control.getPhaseSelection() == PhaseSelection.AVG) {
			return count == 0 ? Complex.ZERO : new Complex(sumReal / count, sumImag / count);
		}
		return selected == null ? Complex.ZERO : selected;
	}

	private double selectedMagnitude(Complex3x1 values, CapacitorControlData control) {
		double selected = Double.NaN;
		double sum = 0.0;
		int count = 0;
		for(int phase : activePhases(control.getPhaseCode())) {
			double value = phaseValue(values, phase).abs();
			if(control.getPhaseSelection() == PhaseSelection.AVG) {
				sum += value;
				count++;
				continue;
			}
			if(!Double.isFinite(selected)) {
				selected = value;
			}
			else if(control.getPhaseSelection() == PhaseSelection.MAX && value > selected) {
				selected = value;
			}
			else if(control.getPhaseSelection() == PhaseSelection.MIN && value < selected) {
				selected = value;
			}
		}
		if(control.getPhaseSelection() == PhaseSelection.AVG) {
			return count == 0 ? Double.NaN : sum / count;
		}
		return selected;
	}

	private double basePhaseVoltage(BaseAclfBus<?, ?> bus) {
		return bus.getBaseVoltage() / Math.sqrt(3.0);
	}

	private double baseCurrent(BaseAclfNetwork<?, ?> network, BaseAclfBus<?, ?> bus) {
		double baseVoltage = bus.getBaseVoltage();
		if(baseVoltage <= 0.0) {
			return 1.0;
		}
		return network.getBaseKva() * 1000.0 / (Math.sqrt(3.0) * baseVoltage);
	}

	private int[] activePhases(PhaseCode phaseCode) {
		if(phaseCode == PhaseCode.A) {
			return new int[] {0};
		}
		if(phaseCode == PhaseCode.B) {
			return new int[] {1};
		}
		if(phaseCode == PhaseCode.C) {
			return new int[] {2};
		}
		if(phaseCode == PhaseCode.AB) {
			return new int[] {0, 1};
		}
		if(phaseCode == PhaseCode.AC) {
			return new int[] {0, 2};
		}
		if(phaseCode == PhaseCode.BC) {
			return new int[] {1, 2};
		}
		return new int[] {0, 1, 2};
	}

	private Complex phaseValue(Complex3x1 values, int phase) {
		if(phase == 0) {
			return values.a_0 == null ? Complex.ZERO : values.a_0;
		}
		if(phase == 1) {
			return values.b_1 == null ? Complex.ZERO : values.b_1;
		}
		return values.c_2 == null ? Complex.ZERO : values.c_2;
	}

	private String normalizeElementName(String name) {
		if(name == null) {
			return "";
		}
		int dot = name.indexOf('.');
		return dot >= 0 ? name.substring(dot + 1) : name;
	}

	private boolean matches(String actual, String expected) {
		return actual != null && expected != null && actual.equalsIgnoreCase(expected);
	}

	private Complex3x1 zero() {
		return new Complex3x1(Complex.ZERO, Complex.ZERO, Complex.ZERO);
	}

	private Complex3x1 copy(Complex3x1 value) {
		return new Complex3x1(copy(value.a_0), copy(value.b_1), copy(value.c_2));
	}

	private Complex copy(Complex value) {
		return value == null ? Complex.ZERO : new Complex(value.getReal(), value.getImaginary());
	}

	private static class CapacitorMeasurement {
		private final double controlValue;
		private final double localVoltage;

		private CapacitorMeasurement(double controlValue, double localVoltage) {
			this.controlValue = controlValue;
			this.localVoltage = localVoltage;
		}
	}

	private class CapacitorSwitchAction implements QstsControlAction {
		private final String key;
		private final double executeTimeSeconds;
		private final CapacitorControlData control;
		private final IPhaseLoad capacitor;
		private final boolean closed;

		private CapacitorSwitchAction(String key, double executeTimeSeconds,
				CapacitorControlData control, IPhaseLoad capacitor, boolean closed) {
			this.key = key;
			this.executeTimeSeconds = executeTimeSeconds;
			this.control = control;
			this.capacitor = capacitor;
			this.closed = closed;
		}

		@Override
		public String getKey() {
			return key;
		}

		@Override
		public double getExecuteTimeSeconds() {
			return executeTimeSeconds;
		}

		@Override
		public boolean apply() {
			if(control.isClosed() == closed) {
				return false;
			}
			setClosedState(control, capacitor, closed);
			return true;
		}
	}
}
