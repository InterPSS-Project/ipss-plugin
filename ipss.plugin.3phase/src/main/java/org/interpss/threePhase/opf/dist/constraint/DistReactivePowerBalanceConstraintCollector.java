package org.interpss.threePhase.opf.dist.constraint;

import java.util.ArrayList;
import java.util.List;

import org.interpss.threePhase.opf.dist.DistOpfOptions;
import org.interpss.threePhase.opf.dist.DistOpfVoltageModel;
import org.interpss.threePhase.opf.dist.model.DistOpfBranchData;
import org.interpss.threePhase.opf.dist.model.DistOpfBusData;
import org.interpss.threePhase.opf.dist.model.DistOpfCapacitorData;
import org.interpss.threePhase.opf.dist.model.DistOpfDerData;
import org.interpss.threePhase.opf.dist.model.DistOpfModelData;
import org.interpss.threePhase.opf.dist.model.DistOpfVariableIndex;
import org.interpss.threePhase.opf.dist.model.DistBranchFlowLossProfile;

import com.interpss.core.acsc.PhaseCode;

public class DistReactivePowerBalanceConstraintCollector extends BaseDistOpfConstraintCollector {

	private final DistOpfOptions options;
	private final DistBranchFlowLossProfile lossProfile;

	public DistReactivePowerBalanceConstraintCollector(DistOpfModelData modelData,
			DistOpfVariableIndex variableIndex, List<org.interpss.plugin.opf.constraint.OpfConstraint> constraints) {
		this(modelData, variableIndex, constraints, new DistOpfOptions(), DistBranchFlowLossProfile.none());
	}

	public DistReactivePowerBalanceConstraintCollector(DistOpfModelData modelData,
			DistOpfVariableIndex variableIndex, List<org.interpss.plugin.opf.constraint.OpfConstraint> constraints,
			DistOpfOptions options) {
		this(modelData, variableIndex, constraints, options, DistBranchFlowLossProfile.none());
	}

	public DistReactivePowerBalanceConstraintCollector(DistOpfModelData modelData,
			DistOpfVariableIndex variableIndex, List<org.interpss.plugin.opf.constraint.OpfConstraint> constraints,
			DistOpfOptions options, DistBranchFlowLossProfile lossProfile) {
		super(modelData, variableIndex, constraints);
		this.options = options;
		this.lossProfile = lossProfile;
	}

	@Override
	public void collectConstraint() {
		for (DistOpfBusData bus : modelData.getBuses()) {
			if (bus.isSwing()) {
				continue;
			}
			DistOpfBranchData parent = modelData.getParentBranch(bus.getId());
			for (PhaseCode phase : bus.getPhases()) {
				if (parent == null || !parent.getPhases().contains(phase)) {
					continue;
				}
				List<Integer> columns = new ArrayList<Integer>();
				List<Double> values = new ArrayList<Double>();
				columns.add(variableIndex.branchQ(parent.getId(), phase));
				values.add(1.0);
				for (DistOpfBranchData child : modelData.getChildren(bus.getId())) {
					if (child.getPhases().contains(phase)) {
						columns.add(variableIndex.branchQ(child.getId(), phase));
						values.add(-1.0);
					}
				}
				for (DistOpfDerData der : modelData.getDers(bus.getId())) {
					if (der.getPhases().contains(phase)) {
						columns.add(variableIndex.derQ(der.getId(), phase));
						values.add(1.0);
					}
				}
				for (DistOpfCapacitorData capacitor : modelData.getCapacitors(bus.getId())) {
					if (capacitor.getPhases().contains(phase)) {
						columns.add(variableIndex.capacitorStatus(capacitor.getId(), phase));
						values.add(capacitor.getQ(phase));
					}
				}
				if (options.getVoltageModel() == DistOpfVoltageModel.ANGLE_COUPLED_LINDISTFLOW
						&& DistConstraintUtil.q(bus.getFixedCapacitorQ(), phase) != 0.0) {
					columns.add(variableIndex.busV2(bus.getId(), phase));
					values.add(DistConstraintUtil.q(bus.getFixedCapacitorQ(), phase));
				}
				addEquality("QBalance@" + bus.getId() + "." + phase,
						qDemand(bus, phase) + lossProfile.reactivePowerLoss(parent, phase),
						toIntArray(columns), toDoubleArray(values));
			}
		}
	}

	private double qDemand(DistOpfBusData bus, PhaseCode phase) {
		double demand = DistConstraintUtil.q(bus.getLoad(), phase);
		if (options.isFixedCapacitors()
				&& options.getVoltageModel() != DistOpfVoltageModel.ANGLE_COUPLED_LINDISTFLOW) {
			demand -= DistConstraintUtil.q(bus.getFixedCapacitorQ(), phase);
		}
		return demand;
	}

	private static int[] toIntArray(List<Integer> values) {
		int[] array = new int[values.size()];
		for (int i = 0; i < values.size(); i++) {
			array[i] = values.get(i).intValue();
		}
		return array;
	}

	private static double[] toDoubleArray(List<Double> values) {
		double[] array = new double[values.size()];
		for (int i = 0; i < values.size(); i++) {
			array[i] = values.get(i).doubleValue();
		}
		return array;
	}
}
