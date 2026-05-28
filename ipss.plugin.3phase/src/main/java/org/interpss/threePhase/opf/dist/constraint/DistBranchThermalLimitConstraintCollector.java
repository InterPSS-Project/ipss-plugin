package org.interpss.threePhase.opf.dist.constraint;

import java.util.List;

import org.interpss.plugin.opf.constraint.OpfConstraint;
import org.interpss.threePhase.opf.dist.DistOpfOptions;
import org.interpss.threePhase.opf.dist.model.DistOpfBranchData;
import org.interpss.threePhase.opf.dist.model.DistOpfModelData;
import org.interpss.threePhase.opf.dist.model.DistOpfVariableIndex;

import com.interpss.core.acsc.PhaseCode;

public class DistBranchThermalLimitConstraintCollector extends BaseDistOpfConstraintCollector {

	private static final double SQRT2 = Math.sqrt(2.0);

	private final DistOpfOptions options;

	public DistBranchThermalLimitConstraintCollector(DistOpfModelData modelData,
			DistOpfVariableIndex variableIndex, List<OpfConstraint> constraints, DistOpfOptions options) {
		super(modelData, variableIndex, constraints);
		this.options = options;
	}

	@Override
	public void collectConstraint() {
		if (!options.isIncludeBranchThermalLimits()) {
			return;
		}
		for (DistOpfBranchData branch : modelData.getBranches()) {
			if (branch.getThermalLimitPu() == null) {
				continue;
			}
			for (PhaseCode phase : branch.getPhases()) {
				addOctagon(branch, phase, branch.getThermalLimitPu().doubleValue());
			}
		}
	}

	private void addOctagon(DistOpfBranchData branch, PhaseCode phase, double limit) {
		int p = variableIndex.branchP(branch.getId(), phase);
		int q = variableIndex.branchQ(branch.getId(), phase);
		String label = "Thermal@" + branch.getId() + "." + phase;
		addBounded(label + ".P", -limit, limit, new int[] { p }, new double[] { 1.0 });
		addBounded(label + ".Q", -limit, limit, new int[] { q }, new double[] { 1.0 });
		addBounded(label + ".PplusQ", -SQRT2 * limit, SQRT2 * limit,
				new int[] { p, q }, new double[] { 1.0, 1.0 });
		addBounded(label + ".PminusQ", -SQRT2 * limit, SQRT2 * limit,
				new int[] { p, q }, new double[] { 1.0, -1.0 });
	}
}
