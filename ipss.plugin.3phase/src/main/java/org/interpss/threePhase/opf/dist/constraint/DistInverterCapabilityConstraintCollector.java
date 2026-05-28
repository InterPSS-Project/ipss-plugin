package org.interpss.threePhase.opf.dist.constraint;

import java.util.List;

import org.interpss.plugin.opf.constraint.OpfConstraint;
import org.interpss.threePhase.opf.dist.model.DistOpfDerData;
import org.interpss.threePhase.opf.dist.model.DistOpfModelData;
import org.interpss.threePhase.opf.dist.model.DistOpfVariableIndex;

import com.interpss.core.acsc.PhaseCode;

public class DistInverterCapabilityConstraintCollector extends BaseDistOpfConstraintCollector {

	private static final double SQRT2 = Math.sqrt(2.0);

	public DistInverterCapabilityConstraintCollector(DistOpfModelData modelData,
			DistOpfVariableIndex variableIndex, List<OpfConstraint> constraints) {
		super(modelData, variableIndex, constraints);
	}

	@Override
	public void collectConstraint() {
		for (DistOpfDerData der : modelData.getDers()) {
			if (der.getApparentPowerLimitPu() == null) {
				continue;
			}
			for (PhaseCode phase : der.getPhases()) {
				addOctagon(der, phase, der.getApparentPowerLimitPu().doubleValue());
			}
		}
	}

	private void addOctagon(DistOpfDerData der, PhaseCode phase, double limit) {
		int p = variableIndex.derP(der.getId(), phase);
		int q = variableIndex.derQ(der.getId(), phase);
		String label = "InverterCapability@" + der.getId() + "." + phase;
		addBounded(label + ".P", -limit, limit, new int[] { p }, new double[] { 1.0 });
		addBounded(label + ".Q", -limit, limit, new int[] { q }, new double[] { 1.0 });
		addBounded(label + ".PplusQ", -SQRT2 * limit, SQRT2 * limit,
				new int[] { p, q }, new double[] { 1.0, 1.0 });
		addBounded(label + ".PminusQ", -SQRT2 * limit, SQRT2 * limit,
				new int[] { p, q }, new double[] { 1.0, -1.0 });
	}
}
