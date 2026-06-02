package org.interpss.threePhase.opf.dist.constraint;

import java.util.ArrayList;
import java.util.List;

import org.interpss.plugin.opf.constraint.OpfConstraint;
import org.interpss.threePhase.opf.dist.DistOpfObjective;
import org.interpss.threePhase.opf.dist.DistOpfOptions;
import org.interpss.threePhase.opf.dist.model.DistOpfBranchData;
import org.interpss.threePhase.opf.dist.model.DistOpfModelData;
import org.interpss.threePhase.opf.dist.model.DistOpfVariableIndex;

import com.interpss.core.acsc.PhaseCode;

public class DistSubstationTargetConstraintCollector extends BaseDistOpfConstraintCollector {

	private final DistOpfOptions options;
	private final DistOpfObjective objective;

	public DistSubstationTargetConstraintCollector(DistOpfModelData modelData,
			DistOpfVariableIndex variableIndex, List<OpfConstraint> constraints,
			DistOpfOptions options, DistOpfObjective objective) {
		super(modelData, variableIndex, constraints);
		this.options = options;
		this.objective = objective;
	}

	@Override
	public void collectConstraint() {
		if (objective == DistOpfObjective.TARGET_SUBSTATION_P && options.getTargetSubstationPPu() != null) {
			addTargetConstraint("TargetSubstationP", true, options.getTargetSubstationPPu().doubleValue());
		}
		if (objective == DistOpfObjective.TARGET_SUBSTATION_Q && options.getTargetSubstationQPu() != null) {
			addTargetConstraint("TargetSubstationQ", false, options.getTargetSubstationQPu().doubleValue());
		}
	}

	private void addTargetConstraint(String description, boolean activePower, double target) {
		List<Integer> columns = new ArrayList<Integer>();
		List<Double> values = new ArrayList<Double>();
		for (DistOpfBranchData branch : modelData.getChildren(modelData.getSwingBusId())) {
			for (PhaseCode phase : branch.getPhases()) {
				columns.add(activePower ? variableIndex.branchP(branch.getId(), phase)
						: variableIndex.branchQ(branch.getId(), phase));
				values.add(1.0);
			}
		}
		columns.add(activePower ? variableIndex.targetPPositive(modelData.getSwingBusId())
				: variableIndex.targetQPositive(modelData.getSwingBusId()));
		values.add(-1.0);
		columns.add(activePower ? variableIndex.targetPNegative(modelData.getSwingBusId())
				: variableIndex.targetQNegative(modelData.getSwingBusId()));
		values.add(1.0);
		addEquality(description + "@" + modelData.getSwingBusId(), target, toIntArray(columns), toDoubleArray(values));
		addGreaterThan(description + "PositiveMin@" + modelData.getSwingBusId(), 0.0,
				new int[] { activePower ? variableIndex.targetPPositive(modelData.getSwingBusId())
						: variableIndex.targetQPositive(modelData.getSwingBusId()) },
				new double[] { 1.0 });
		addGreaterThan(description + "NegativeMin@" + modelData.getSwingBusId(), 0.0,
				new int[] { activePower ? variableIndex.targetPNegative(modelData.getSwingBusId())
						: variableIndex.targetQNegative(modelData.getSwingBusId()) },
				new double[] { 1.0 });
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
