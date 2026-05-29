package org.interpss.threePhase.opf.dist.solver;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.apache.commons.math3.complex.Complex;
import org.interpss.numeric.datatype.Complex3x1;
import org.interpss.numeric.datatype.Complex3x3;
import org.interpss.plugin.opf.constraint.OpfConstraint;
import org.interpss.threePhase.opf.dist.DistOpfOptions;
import org.interpss.threePhase.opf.dist.DistOpfStatus;
import org.interpss.threePhase.opf.dist.DistOpfVoltageModel;
import org.interpss.threePhase.opf.dist.model.DistOpfBranchData;
import org.interpss.threePhase.opf.dist.model.DistOpfBusData;
import org.interpss.threePhase.opf.dist.model.DistOpfDerData;
import org.interpss.threePhase.opf.dist.model.DistOpfModel;
import org.interpss.threePhase.opf.dist.model.DistOpfModelData;

import com.interpss.core.acsc.PhaseCode;
import com.interpss.opf.datatype.OpfConstraintType;

public class DirectLinDistFlowPowerFlowSolver implements DistOpfSolver {

	private static final int MAX_CAPACITOR_ITERATIONS = 50;
	private static final double CAPACITOR_TOLERANCE = 1.0e-10;

	@Override
	public DistOpfSolverResult solve(DistOpfModel model, DistOpfOptions options) {
		if (model.getModelData() == null) {
			return new DistOpfSolverResult(DistOpfStatus.ERROR, 0.0, Double.NaN,
					new double[0], "Direct LinDistFlow solver requires model data");
		}
		DistOpfModelData data = model.getModelData();
		Map<String, DistOpfBusData> buses = busesById(data);
		List<DistOpfBranchData> orderedBranches = orderedBranches(data);
		double[] x = new double[model.getNumberOfVariables()];
		initializeVoltages(model, data, x);
		for (int iteration = 0; iteration < MAX_CAPACITOR_ITERATIONS; iteration++) {
			solveBranchFlows(model, data, buses, orderedBranches, options, x);
			double maxVoltageDelta = solveVoltages(model, data, buses, orderedBranches, options, x);
			if (maxVoltageDelta <= CAPACITOR_TOLERANCE) {
				break;
			}
		}
		fixDerVariables(model, data, x);
		return new DistOpfSolverResult(DistOpfStatus.OPTIMAL, 0.0, maxResidual(model, x),
				x, "Direct LinDistFlow power flow");
	}

	private static void initializeVoltages(DistOpfModel model, DistOpfModelData data, double[] x) {
		for (DistOpfBusData bus : data.getBuses()) {
			for (PhaseCode phase : bus.getPhases()) {
				double voltage = bus.isSwing() ? bus.getInitialVoltageMagnitude(phase) : 1.0;
				x[model.getVariableIndex().busV2(bus.getId(), phase)] = voltage * voltage;
			}
		}
	}

	private static void solveBranchFlows(DistOpfModel model, DistOpfModelData data,
			Map<String, DistOpfBusData> buses, List<DistOpfBranchData> orderedBranches,
			DistOpfOptions options, double[] x) {
		for (int i = orderedBranches.size() - 1; i >= 0; i--) {
			DistOpfBranchData branch = orderedBranches.get(i);
			DistOpfBusData bus = buses.get(branch.getToBusId());
			if (bus == null) {
				continue;
			}
			for (PhaseCode phase : branch.getPhases()) {
				double p = p(bus.getLoad(), phase);
				double q = q(bus.getLoad(), phase) - fixedCapacitorQ(model, bus, phase, options, x);
				for (DistOpfBranchData child : data.getChildren(bus.getId())) {
					if (child.getPhases().contains(phase)) {
						p += x[model.getVariableIndex().branchP(child.getId(), phase)];
						q += x[model.getVariableIndex().branchQ(child.getId(), phase)];
					}
				}
				for (DistOpfDerData der : data.getDers(bus.getId())) {
					if (der.getPhases().contains(phase)) {
						p -= der.getP(phase);
						q -= der.getQ(phase);
					}
				}
				x[model.getVariableIndex().branchP(branch.getId(), phase)] = p;
				x[model.getVariableIndex().branchQ(branch.getId(), phase)] = q;
			}
		}
	}

	private static double fixedCapacitorQ(DistOpfModel model, DistOpfBusData bus, PhaseCode phase,
			DistOpfOptions options, double[] x) {
		double q = q(bus.getFixedCapacitorQ(), phase);
		if (q == 0.0) {
			return 0.0;
		}
		if (options.getVoltageModel() == DistOpfVoltageModel.ANGLE_COUPLED_LINDISTFLOW) {
			return q * x[model.getVariableIndex().busV2(bus.getId(), phase)];
		}
		return q;
	}

	private static double solveVoltages(DistOpfModel model, DistOpfModelData data,
			Map<String, DistOpfBusData> buses, List<DistOpfBranchData> orderedBranches,
			DistOpfOptions options, double[] x) {
		double maxDelta = 0.0;
		DistOpfBusData swing = buses.get(data.getSwingBusId());
		if (swing != null) {
			for (PhaseCode phase : swing.getPhases()) {
				int index = model.getVariableIndex().busV2(swing.getId(), phase);
				double voltage = swing.getInitialVoltageMagnitude(phase);
				maxDelta = Math.max(maxDelta, Math.abs(x[index] - voltage * voltage));
				x[index] = voltage * voltage;
			}
		}
		for (DistOpfBranchData branch : orderedBranches) {
			for (PhaseCode phase : branch.getPhases()) {
				int toIndex = model.getVariableIndex().busV2(branch.getToBusId(), phase);
				double oldVoltage = x[toIndex];
				double voltage = branch.getVoltageRatio(phase) * branch.getVoltageRatio(phase)
						* x[model.getVariableIndex().busV2(branch.getFromBusId(), phase)];
				if (!angleCoupledIdealVoltageRatioBranch(branch, options)) {
					for (PhaseCode coupledPhase : branch.getPhases()) {
						Complex z = z(branch.getZabc(), phase, coupledPhase);
						voltage += pCoefficient(options, phase, coupledPhase, z)
								* x[model.getVariableIndex().branchP(branch.getId(), coupledPhase)];
						voltage += qCoefficient(options, phase, coupledPhase, z)
								* x[model.getVariableIndex().branchQ(branch.getId(), coupledPhase)];
					}
				}
				x[toIndex] = voltage;
				maxDelta = Math.max(maxDelta, Math.abs(voltage - oldVoltage));
			}
		}
		return maxDelta;
	}

	private static boolean angleCoupledIdealVoltageRatioBranch(DistOpfBranchData branch, DistOpfOptions options) {
		if (options.getVoltageModel() != DistOpfVoltageModel.ANGLE_COUPLED_LINDISTFLOW) {
			return false;
		}
		if (branch.isFixedVoltageRatioOnly()) {
			return true;
		}
		for (PhaseCode phase : branch.getPhases()) {
			if (Math.abs(branch.getVoltageRatio(phase) - 1.0) > 1.0e-12) {
				return true;
			}
		}
		return false;
	}

	private static void fixDerVariables(DistOpfModel model, DistOpfModelData data, double[] x) {
		for (DistOpfDerData der : data.getDers()) {
			for (PhaseCode phase : der.getPhases()) {
				x[model.getVariableIndex().derP(der.getId(), phase)] = der.getP(phase);
				x[model.getVariableIndex().derQ(der.getId(), phase)] = der.getQ(phase);
			}
		}
	}

	private static List<DistOpfBranchData> orderedBranches(DistOpfModelData data) {
		List<DistOpfBranchData> ordered = new ArrayList<DistOpfBranchData>();
		addChildren(data, data.getSwingBusId(), ordered);
		return ordered;
	}

	private static void addChildren(DistOpfModelData data, String busId, List<DistOpfBranchData> ordered) {
		for (DistOpfBranchData child : data.getChildren(busId)) {
			ordered.add(child);
			addChildren(data, child.getToBusId(), ordered);
		}
	}

	private static Map<String, DistOpfBusData> busesById(DistOpfModelData data) {
		Map<String, DistOpfBusData> buses = new LinkedHashMap<String, DistOpfBusData>();
		for (DistOpfBusData bus : data.getBuses()) {
			buses.put(bus.getId(), bus);
		}
		return buses;
	}

	private static double pCoefficient(DistOpfOptions options, PhaseCode rowPhase,
			PhaseCode columnPhase, Complex z) {
		if (options.getVoltageModel() != DistOpfVoltageModel.ANGLE_COUPLED_LINDISTFLOW
				|| rowPhase == columnPhase) {
			return -2.0 * z.getReal();
		}
		if (columnPhase == nextPhase(rowPhase)) {
			return z.getReal() - Math.sqrt(3.0) * z.getImaginary();
		}
		return z.getReal() + Math.sqrt(3.0) * z.getImaginary();
	}

	private static double qCoefficient(DistOpfOptions options, PhaseCode rowPhase,
			PhaseCode columnPhase, Complex z) {
		if (options.getVoltageModel() != DistOpfVoltageModel.ANGLE_COUPLED_LINDISTFLOW
				|| rowPhase == columnPhase) {
			return -2.0 * z.getImaginary();
		}
		if (columnPhase == nextPhase(rowPhase)) {
			return z.getImaginary() + Math.sqrt(3.0) * z.getReal();
		}
		return z.getImaginary() - Math.sqrt(3.0) * z.getReal();
	}

	private static PhaseCode nextPhase(PhaseCode phase) {
		switch (phase) {
		case A:
			return PhaseCode.B;
		case B:
			return PhaseCode.C;
		case C:
			return PhaseCode.A;
		default:
			return phase;
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
			return zabc.ab;
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
			return zabc.ac;
		case B:
			return zabc.bc;
		case C:
			return zabc.cc;
		default:
			return Complex.ZERO;
		}
	}

	private static double p(Complex3x1 value, PhaseCode phase) {
		return phaseValue(value, phase).getReal();
	}

	private static double q(Complex3x1 value, PhaseCode phase) {
		return phaseValue(value, phase).getImaginary();
	}

	private static Complex phaseValue(Complex3x1 value, PhaseCode phase) {
		if (value == null) {
			return Complex.ZERO;
		}
		switch (phase) {
		case A:
			return value.a_0 == null ? Complex.ZERO : value.a_0;
		case B:
			return value.b_1 == null ? Complex.ZERO : value.b_1;
		case C:
			return value.c_2 == null ? Complex.ZERO : value.c_2;
		default:
			return Complex.ZERO;
		}
	}

	private static double maxResidual(DistOpfModel model, double[] primal) {
		double maxResidual = 0.0;
		for (OpfConstraint constraint : model.getConstraints()) {
			double activity = 0.0;
			for (int i = 0; i < constraint.getColNo().size(); i++) {
				activity += constraint.getVal().get(i) * primal[constraint.getColNo().get(i)];
			}
			if (constraint.getCstType() == OpfConstraintType.EQUALITY) {
				maxResidual = Math.max(maxResidual, Math.abs(activity - constraint.getUpperLimit()));
			}
		}
		return maxResidual;
	}
}
