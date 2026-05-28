package org.interpss.threePhase.opf.dist.constraint;

import java.util.ArrayList;
import java.util.List;

import org.interpss.numeric.datatype.Complex3x3;
import org.interpss.threePhase.opf.dist.model.DistOpfBranchData;
import org.interpss.threePhase.opf.dist.model.DistOpfModelData;
import org.interpss.threePhase.opf.dist.model.DistOpfVariableIndex;

import com.interpss.core.acsc.PhaseCode;

public class DistVoltageDropConstraintCollector extends BaseDistOpfConstraintCollector {

	public DistVoltageDropConstraintCollector(DistOpfModelData modelData,
			DistOpfVariableIndex variableIndex, List<org.interpss.plugin.opf.constraint.OpfConstraint> constraints) {
		super(modelData, variableIndex, constraints);
	}

	@Override
	public void collectConstraint() {
		for (DistOpfBranchData branch : modelData.getBranches()) {
			for (PhaseCode phase : branch.getPhases()) {
				List<Integer> columns = new ArrayList<Integer>();
				List<Double> values = new ArrayList<Double>();
				columns.add(variableIndex.busV2(branch.getFromBusId(), phase));
				values.add(1.0);
				columns.add(variableIndex.busV2(branch.getToBusId(), phase));
				values.add(-1.0);
				columns.add(variableIndex.branchP(branch.getId(), phase));
				values.add(-2.0 * r(branch.getZabc(), phase));
				columns.add(variableIndex.branchQ(branch.getId(), phase));
				values.add(-2.0 * x(branch.getZabc(), phase));
				addEquality("VDrop@" + branch.getId() + "." + phase, 0.0,
						toIntArray(columns), toDoubleArray(values));
			}
		}
	}

	private static double r(Complex3x3 zabc, PhaseCode phase) {
		switch (phase) {
		case A:
			return zabc.aa.getReal();
		case B:
			return zabc.bb.getReal();
		case C:
			return zabc.cc.getReal();
		default:
			return 0.0;
		}
	}

	private static double x(Complex3x3 zabc, PhaseCode phase) {
		switch (phase) {
		case A:
			return zabc.aa.getImaginary();
		case B:
			return zabc.bb.getImaginary();
		case C:
			return zabc.cc.getImaginary();
		default:
			return 0.0;
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
