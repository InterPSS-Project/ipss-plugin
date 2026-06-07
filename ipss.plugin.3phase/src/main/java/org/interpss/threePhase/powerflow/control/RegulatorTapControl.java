package org.interpss.threePhase.powerflow.control;

import java.util.List;

import org.apache.commons.math3.complex.Complex;
import org.interpss.numeric.datatype.Complex3x1;
import org.interpss.threePhase.powerflow.control.RegulatorControlData.PhaseSelection;

import com.interpss.core.aclf.AclfBranch;
import com.interpss.core.aclf.BaseAclfBus;
import com.interpss.core.aclf.BaseAclfNetwork;
import com.interpss.core.acsc.AcscBranch;
import com.interpss.core.acsc.PhaseCode;
import com.interpss.core.net.Branch;
import com.interpss.core.threephase.IBranch3Phase;
import com.interpss.core.threephase.IBus3Phase;
import com.interpss.core.threephase.INetwork3Phase;

public class RegulatorTapControl {
	public boolean apply(INetwork3Phase network, List<RegulatorControlData> controls) {
		if(controls == null || controls.isEmpty()) {
			return false;
		}
		BaseAclfNetwork<?, ?> aclfNetwork = aclfNetwork(network);
		boolean changed = false;
		for(RegulatorControlData control : controls) {
			IBranch3Phase branch = findBranch(aclfNetwork, control.getBranchName());
			if(branch == null) {
				continue;
			}
			BaseAclfBus<?, ?> controlledBus = controlledBus(aclfNetwork, branch, control);
			if(!(controlledBus instanceof IBus3Phase)) {
				continue;
			}
			Complex3x1 vabc = ((IBus3Phase) controlledBus).get3PhaseVotlages();
			if(vabc == null) {
				continue;
			}
			ControlMeasurement measurement = measuredControlVoltage(aclfNetwork, branch, control,
					controlledBus, vabc);
			if(!Double.isFinite(measurement.controlVoltage) || measurement.controlVoltage <= 0.0) {
				continue;
			}
			double halfBand = Math.max(control.getBandwidth(), 0.0) * 0.5;
			double lowLimit = control.getTargetVoltage() - halfBand;
			double highLimit = control.getTargetVoltage() + halfBand;
			boolean tapChangeNeeded = measurement.controlVoltage < lowLimit || measurement.controlVoltage > highLimit;
			if(control.hasVLimit() && measurement.localVoltage > control.getVLimit()) {
				tapChangeNeeded = true;
			}
			if(!tapChangeNeeded) {
				continue;
			}
			int currentTap = currentTapPosition(branch, control);
			double voltageError = control.getTargetVoltage() - measurement.controlVoltage;
			if(control.hasVLimit() && measurement.localVoltage > control.getVLimit()) {
				voltageError = control.getVLimit() - measurement.localVoltage;
			}
			int requestedChange = (int) Math.round(voltageError * control.getPtRatio()
					/ measurement.baseWindingVoltage / control.getTapStep());
			if(requestedChange == 0) {
				requestedChange = measurement.controlVoltage < lowLimit ? 1 : -1;
			}
			requestedChange = staticTapChange(requestedChange, control.getMaxTapChange());
			int nextTap = Math.max(control.getMinTapPosition(),
					Math.min(control.getMaxTapPosition(), currentTap + requestedChange));
			if(nextTap != currentTap) {
				control.setTapPosition(nextTap);
				applyTapPosition(branch, control, nextTap);
				changed = true;
			}
		}
		return changed;
	}

	@SuppressWarnings("unchecked")
	private BaseAclfNetwork<?, ?> aclfNetwork(INetwork3Phase network) {
		if(network instanceof BaseAclfNetwork) {
			return (BaseAclfNetwork<?, ?>) network;
		}
		throw new UnsupportedOperationException("Regulator controls require a BaseAclfNetwork-backed three-phase network");
	}

	private IBranch3Phase findBranch(BaseAclfNetwork<?, ?> network, String branchName) {
		for(AclfBranch branch : (List<AclfBranch>) network.getBranchList()) {
			if(branch.isActive() && branch instanceof IBranch3Phase
					&& (branch.getName().equals(branchName) || branch.getId().equals(branchName))) {
				return (IBranch3Phase) branch;
			}
		}
		return null;
	}

	private BaseAclfBus<?, ?> controlledBus(BaseAclfNetwork<?, ?> network, IBranch3Phase branch,
			RegulatorControlData control) {
		if(control.hasRegulatedBus()) {
			BaseAclfBus<?, ?> bus = findBus(network, control.getRegulatedBusId());
			if(bus != null) {
				return bus;
			}
		}
		Branch aclfBranch = (Branch) branch;
		return control.getWinding() == 1
				? (BaseAclfBus<?, ?>) aclfBranch.getFromBus()
				: (BaseAclfBus<?, ?>) aclfBranch.getToBus();
	}

	private BaseAclfBus<?, ?> findBus(BaseAclfNetwork<?, ?> network, String busId) {
		for(BaseAclfBus<?, ?> bus : (List<BaseAclfBus<?, ?>>) network.getBusList()) {
			if(bus.getId().equals(busId) || bus.getId().equalsIgnoreCase(busId)
					|| bus.getName().equals(busId) || bus.getName().equalsIgnoreCase(busId)) {
				return bus;
			}
		}
		return null;
	}

	private ControlMeasurement measuredControlVoltage(BaseAclfNetwork<?, ?> network, IBranch3Phase branch,
			RegulatorControlData control, BaseAclfBus<?, ?> controlledBus, Complex3x1 vabc) {
		double baseWindingVoltage = basePhaseVoltage(controlledBus, control.getPhaseCode());
		double ptRatio = control.hasRegulatedBus() ? control.getRemotePtRatio() : control.getPtRatio();
		Complex voltage = selectedVoltage(vabc.multiply(baseWindingVoltage), control);
		Complex controlVoltage = voltage.divide(ptRatio);
		if(!control.hasRegulatedBus() && control.hasLineDropCompensation() && control.getCtPrim() > 0.0) {
			Complex current = selectedCurrent(network, branch, control);
			Complex compensator = new Complex(control.getLineDropR(), control.getLineDropX())
					.multiply(current.divide(control.getCtPrim()));
			controlVoltage = controlVoltage.add(compensator);
		}
		double localVoltage = selectedVoltage(vabc.multiply(baseWindingVoltage), control).abs() / control.getPtRatio();
		return new ControlMeasurement(controlVoltage.abs(), localVoltage, baseWindingVoltage);
	}

	private Complex selectedVoltage(Complex3x1 vabc, RegulatorControlData control) {
		Complex selected = null;
		for(int phase : activePhases(control.getPhaseCode())) {
			Complex voltage = phaseVoltage(vabc, phase);
			if(voltage == null) {
				continue;
			}
			if(selected == null) {
				selected = voltage;
			}
			else if(control.getPhaseSelection() == PhaseSelection.MAX && voltage.abs() > selected.abs()) {
				selected = voltage;
			}
			else if(control.getPhaseSelection() == PhaseSelection.MIN && voltage.abs() < selected.abs()) {
				selected = voltage;
			}
		}
		return selected == null ? Complex.ZERO : selected;
	}

	private Complex selectedCurrent(BaseAclfNetwork<?, ?> network, IBranch3Phase branch, RegulatorControlData control) {
		Branch aclfBranch = (Branch) branch;
		BaseAclfBus<?, ?> currentBaseBus = control.getWinding() == 1
				? (BaseAclfBus<?, ?>) aclfBranch.getFromBus()
				: (BaseAclfBus<?, ?>) aclfBranch.getToBus();
		Complex3x1 currentPu = control.getWinding() == 1
				? branch.getYffabc().multiply(((IBus3Phase) aclfBranch.getFromBus()).get3PhaseVotlages())
						.add(branch.getYftabc().multiply(((IBus3Phase) aclfBranch.getToBus()).get3PhaseVotlages()))
				: branch.getYttabc().multiply(((IBus3Phase) aclfBranch.getToBus()).get3PhaseVotlages())
						.add(branch.getYtfabc().multiply(((IBus3Phase) aclfBranch.getFromBus()).get3PhaseVotlages()));
		double baseCurrent = baseCurrent(network, currentBaseBus);
		return selectedVoltage(currentPu.multiply(baseCurrent), control);
	}

	private double measuredVoltagePu(Complex3x1 vabc, PhaseCode phaseCode) {
		double sum = 0.0;
		int count = 0;
		for(int phase : activePhases(phaseCode)) {
			Complex voltage = phaseVoltage(vabc, phase);
			if(voltage != null) {
				sum += voltage.abs();
				count++;
			}
		}
		return count == 0 ? Double.NaN : sum / count;
	}

	private int staticTapChange(int requestedChange, int maxTapChange) {
		if(maxTapChange == 0) {
			return 0;
		}
		int direction = requestedChange > 0 ? 1 : -1;
		int steps = (int) Math.floor(0.7 * Math.abs(requestedChange));
		if(steps == 0) {
			steps = 1;
		}
		int limit = maxTapChange < 0 ? Integer.MAX_VALUE : maxTapChange;
		if(steps > limit) {
			steps = limit;
		}
		return direction * steps;
	}

	private double basePhaseVoltage(BaseAclfBus<?, ?> bus, PhaseCode phaseCode) {
		return bus.getBaseVoltage() / Math.sqrt(3.0);
	}

	private double baseCurrent(BaseAclfNetwork<?, ?> network, BaseAclfBus<?, ?> bus) {
		double baseVoltage = bus.getBaseVoltage();
		if(baseVoltage <= 0.0) {
			return 1.0;
		}
		return network.getBaseKva() * 1000.0 / (Math.sqrt(3.0) * baseVoltage);
	}

	private Complex phaseVoltage(Complex3x1 vabc, int phase) {
		if(phase == 0) {
			return vabc.a_0;
		}
		if(phase == 1) {
			return vabc.b_1;
		}
		return vabc.c_2;
	}

	private int currentTapPosition(IBranch3Phase branch, RegulatorControlData control) {
		double ratio = averageControlledRatio(branch, control);
		int tap = (int) Math.round((ratio - 1.0) / control.getTapStep());
		control.setTapPosition(tap);
		return control.getTapPosition();
	}

	private double averageControlledRatio(IBranch3Phase branch, RegulatorControlData control) {
		double[] ratios = controlledRatios(branch, control.getTapWinding());
		double sum = 0.0;
		int count = 0;
		for(int phase : activePhases(control.getPhaseCode())) {
			sum += ratios[phase];
			count++;
		}
		return count == 0 ? 1.0 : sum / count;
	}

	private void applyTapPosition(IBranch3Phase branch, RegulatorControlData control, int tapPosition) {
		double tapRatio = 1.0 + tapPosition * control.getTapStep();
		if(!branch.hasPhaseTurnRatio()) {
			AcscBranch acscBranch = (AcscBranch) branch;
			if(control.getTapWinding() == 1) {
				acscBranch.setFromTurnRatio(tapRatio);
			}
			else {
				acscBranch.setToTurnRatio(tapRatio);
			}
			return;
		}
		double[] fromRatios = fromRatios(branch);
		double[] toRatios = toRatios(branch);
		double[] controlledRatios = control.getTapWinding() == 1 ? fromRatios : toRatios;
		for(int phase : activePhases(control.getPhaseCode())) {
			controlledRatios[phase] = tapRatio;
		}
		branch.setFromTurnRatioABC(fromRatios[0], fromRatios[1], fromRatios[2]);
		branch.setToTurnRatioABC(toRatios[0], toRatios[1], toRatios[2]);
	}

	private double[] controlledRatios(IBranch3Phase branch, int winding) {
		return winding == 1 ? fromRatios(branch) : toRatios(branch);
	}

	private double[] fromRatios(IBranch3Phase branch) {
		if(branch.hasPhaseTurnRatio()) {
			return branch.getFromTurnRatioABC();
		}
		double ratio = ((AcscBranch) branch).getFromTurnRatio();
		return new double[] {ratio, ratio, ratio};
	}

	private double[] toRatios(IBranch3Phase branch) {
		if(branch.hasPhaseTurnRatio()) {
			return branch.getToTurnRatioABC();
		}
		double ratio = ((AcscBranch) branch).getToTurnRatio();
		return new double[] {ratio, ratio, ratio};
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

	private static class ControlMeasurement {
		private final double controlVoltage;
		private final double localVoltage;
		private final double baseWindingVoltage;

		private ControlMeasurement(double controlVoltage, double localVoltage, double baseWindingVoltage) {
			this.controlVoltage = controlVoltage;
			this.localVoltage = localVoltage;
			this.baseWindingVoltage = baseWindingVoltage;
		}
	}

}
