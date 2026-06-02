package org.interpss.threePhase.opf.dist.constraint;

import java.util.List;

import org.interpss.plugin.opf.constraint.OpfConstraint;
import org.interpss.threePhase.opf.dist.DistOpfControlMode;
import org.interpss.threePhase.opf.dist.model.DistOpfDerData;
import org.interpss.threePhase.opf.dist.model.DistOpfModelData;
import org.interpss.threePhase.opf.dist.model.DistOpfVariableIndex;

import com.interpss.core.acsc.PhaseCode;

public class DistDerLimitConstraintCollector extends BaseDistOpfConstraintCollector {

	private final DistOpfControlMode controlMode;

	public DistDerLimitConstraintCollector(DistOpfModelData modelData,
			DistOpfVariableIndex variableIndex, List<OpfConstraint> constraints,
			DistOpfControlMode controlMode) {
		super(modelData, variableIndex, constraints);
		this.controlMode = controlMode;
	}

	@Override
	public void collectConstraint() {
		for (DistOpfDerData der : modelData.getDers()) {
			for (PhaseCode phase : der.getPhases()) {
				addPConstraint(der, phase);
				addQConstraint(der, phase);
			}
		}
	}

	private void addPConstraint(DistOpfDerData der, PhaseCode phase) {
		int p = variableIndex.derP(der.getId(), phase);
		if (controlsP()) {
			double upper = der.getPMax(phase);
			addBounded("DERP@" + der.getId() + "." + phase, der.getPMin(phase), upper,
					new int[] { p }, new double[] { 1.0 });
			int curtailment = variableIndex.curtailment(der.getId(), phase);
			addGreaterThan("CurtailmentMin@" + der.getId() + "." + phase, 0.0,
					new int[] { curtailment }, new double[] { 1.0 });
			addEquality("CurtailmentDef@" + der.getId() + "." + phase, upper,
					new int[] { p, curtailment }, new double[] { 1.0, 1.0 });
		} else {
			addEquality("DERPFixed@" + der.getId() + "." + phase, der.getP(phase),
					new int[] { p }, new double[] { 1.0 });
		}
	}

	private void addQConstraint(DistOpfDerData der, PhaseCode phase) {
		int q = variableIndex.derQ(der.getId(), phase);
		if (controlsQ()) {
			double limit = der.getQAbsLimit(phase);
			addBounded("DERQ@" + der.getId() + "." + phase, -limit, limit,
					new int[] { q }, new double[] { 1.0 });
		} else {
			addEquality("DERQFixed@" + der.getId() + "." + phase, der.getQ(phase),
					new int[] { q }, new double[] { 1.0 });
		}
	}

	private boolean controlsP() {
		return controlMode == DistOpfControlMode.P || controlMode == DistOpfControlMode.PQ;
	}

	private boolean controlsQ() {
		return controlMode == DistOpfControlMode.Q || controlMode == DistOpfControlMode.PQ;
	}
}
