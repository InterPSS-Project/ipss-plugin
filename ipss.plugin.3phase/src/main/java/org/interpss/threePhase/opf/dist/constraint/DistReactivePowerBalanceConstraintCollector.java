package org.interpss.threePhase.opf.dist.constraint;

import java.util.ArrayList;
import java.util.List;

import org.interpss.threePhase.opf.dist.model.DistOpfBranchData;
import org.interpss.threePhase.opf.dist.model.DistOpfBusData;
import org.interpss.threePhase.opf.dist.model.DistOpfDerData;
import org.interpss.threePhase.opf.dist.model.DistOpfModelData;
import org.interpss.threePhase.opf.dist.model.DistOpfVariableIndex;

import com.interpss.core.acsc.PhaseCode;

public class DistReactivePowerBalanceConstraintCollector extends BaseDistOpfConstraintCollector {

	public DistReactivePowerBalanceConstraintCollector(DistOpfModelData modelData,
			DistOpfVariableIndex variableIndex, List<org.interpss.plugin.opf.constraint.OpfConstraint> constraints) {
		super(modelData, variableIndex, constraints);
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
				addEquality("QBalance@" + bus.getId() + "." + phase,
						DistConstraintUtil.q(bus.getLoad(), phase), toIntArray(columns), toDoubleArray(values));
			}
		}
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
