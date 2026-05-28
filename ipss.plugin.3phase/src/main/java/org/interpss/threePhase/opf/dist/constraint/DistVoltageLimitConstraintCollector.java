package org.interpss.threePhase.opf.dist.constraint;

import java.util.List;

import org.interpss.threePhase.opf.dist.DistOpfOptions;
import org.interpss.threePhase.opf.dist.model.DistOpfBusData;
import org.interpss.threePhase.opf.dist.model.DistOpfModelData;
import org.interpss.threePhase.opf.dist.model.DistOpfVariableIndex;

import com.interpss.core.acsc.PhaseCode;

public class DistVoltageLimitConstraintCollector extends BaseDistOpfConstraintCollector {

	private final DistOpfOptions options;

	public DistVoltageLimitConstraintCollector(DistOpfModelData modelData,
			DistOpfVariableIndex variableIndex, List<org.interpss.plugin.opf.constraint.OpfConstraint> constraints,
			DistOpfOptions options) {
		super(modelData, variableIndex, constraints);
		this.options = options;
	}

	@Override
	public void collectConstraint() {
		double lower = options.getMinVoltagePu() * options.getMinVoltagePu();
		double upper = options.getMaxVoltagePu() * options.getMaxVoltagePu();
		for (DistOpfBusData bus : modelData.getBuses()) {
			for (PhaseCode phase : bus.getPhases()) {
				addBounded("VLimit@" + bus.getId() + "." + phase, lower, upper,
						new int[] { variableIndex.busV2(bus.getId(), phase) }, new double[] { 1.0 });
			}
		}
	}
}
