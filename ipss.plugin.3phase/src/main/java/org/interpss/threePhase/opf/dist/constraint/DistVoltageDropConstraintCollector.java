package org.interpss.threePhase.opf.dist.constraint;

import java.util.ArrayList;
import java.util.List;

import org.apache.commons.math3.complex.Complex;
import org.interpss.numeric.datatype.Complex3x3;
import org.interpss.threePhase.opf.dist.model.DistOpfBranchData;
import org.interpss.threePhase.opf.dist.model.DistOpfModelData;
import org.interpss.threePhase.opf.dist.model.DistOpfRegulatorData;
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
				values.add(branch.getVoltageRatio() * branch.getVoltageRatio());
				columns.add(variableIndex.busV2(branch.getToBusId(), phase));
				values.add(-1.0);
				for (PhaseCode coupledPhase : branch.getPhases()) {
					Complex z = z(branch.getZabc(), phase, coupledPhase);
					columns.add(variableIndex.branchP(branch.getId(), coupledPhase));
					values.add(-2.0 * z.getReal());
					columns.add(variableIndex.branchQ(branch.getId(), coupledPhase));
					values.add(-2.0 * z.getImaginary());
				}
				for (DistOpfRegulatorData regulator : modelData.getRegulators(branch.getId())) {
					if (regulator.getPhases().contains(phase)) {
						columns.add(variableIndex.regulatorTap(regulator.getId(), phase));
						values.add(regulator.getTapStepVoltageSquaredPu());
					}
				}
				addEquality("VDrop@" + branch.getId() + "." + phase, 0.0,
						toIntArray(columns), toDoubleArray(values));
			}
		}
	}

	private static Complex z(Complex3x3 zabc, PhaseCode rowPhase, PhaseCode columnPhase) {
		switch (rowPhase) {
		case A:
			return zFromA(zabc, columnPhase);
		case B:
			return zFromB(zabc, columnPhase);
		case C:
			return zFromC(zabc, columnPhase);
		default:
			return Complex.ZERO;
		}
	}

	private static Complex zFromA(Complex3x3 zabc, PhaseCode columnPhase) {
		switch (columnPhase) {
		case A:
			return zabc.aa;
		case B:
			return zabc.ab;
		case C:
			return zabc.ac;
		default:
			return Complex.ZERO;
		}
	}

	private static Complex zFromB(Complex3x3 zabc, PhaseCode columnPhase) {
		switch (columnPhase) {
		case A:
			return zabc.ba;
		case B:
			return zabc.bb;
		case C:
			return zabc.bc;
		default:
			return Complex.ZERO;
		}
	}

	private static Complex zFromC(Complex3x3 zabc, PhaseCode columnPhase) {
		switch (columnPhase) {
		case A:
			return zabc.ca;
		case B:
			return zabc.cb;
		case C:
			return zabc.cc;
		default:
			return Complex.ZERO;
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
