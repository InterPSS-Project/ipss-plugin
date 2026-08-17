package org.interpss.threePhase.opf.dist.solver;

import java.util.ArrayList;
import java.util.List;
import java.util.OptionalDouble;

import org.interpss.optimization.backend.SolveOptions;
import org.interpss.optimization.backend.SolveResult;
import org.interpss.optimization.backend.SolverBackend;
import org.interpss.optimization.backend.TerminationReason;
import org.interpss.optimization.backend.highs.HighsBackendFactory;
import org.interpss.optimization.model.ObjectiveSense;
import org.interpss.optimization.model.VariableType;
import org.interpss.optimization.sparse.SparseLinearModel;
import org.interpss.plugin.opf.constraint.OpfConstraint;
import org.interpss.threePhase.opf.dist.DistOpfOptions;
import org.interpss.threePhase.opf.dist.DistOpfStatus;
import org.interpss.threePhase.opf.dist.model.DistOpfModel;
import org.interpss.threePhase.opf.dist.util.DistOpfLimitUtil;

import com.interpss.opf.datatype.OpfConstraintType;

public class IpssOptimizationDistOpfSolver implements DistOpfSolver {

	private final SolverBackend backend;

	public IpssOptimizationDistOpfSolver() {
		this(HighsBackendFactory.desktopDefault());
	}

	public IpssOptimizationDistOpfSolver(SolverBackend backend) {
		if (backend == null) {
			throw new IllegalArgumentException("backend cannot be null");
		}
		this.backend = backend;
	}

	@Override
	public DistOpfSolverResult solve(DistOpfModel model, DistOpfOptions options) {
		if (model.getNumberOfVariables() == 0) {
			return new DistOpfSolverResult(DistOpfStatus.NOT_SOLVED, 0.0, 0.0,
					new double[0], "Empty DistOPF model");
		}
		SparseLinearModel sparseModel = sparseModel(model);
		try {
			SolveResult result = backend.solve(sparseModel, solveOptions());
			double[] primal = primal(model, result);
			DistOpfStatus status = mapStatus(result.terminationReason(), primal);
			double objective = objective(result);
			double maxResidual = maxResidual(model, primal);
			List<String> diagnostics = diagnostics(model, result, maxResidual);
			List<String> bindingConstraints = isFeasible(status)
					? bindingConstraints(model, primal, options.getSolverTolerance())
					: new ArrayList<String>();
			return new DistOpfSolverResult(status, objective, maxResidual, primal,
					message(result), bindingConstraints, diagnostics);
		} catch (RuntimeException | LinkageError e) {
			return failure(model, e);
		}
	}

	private static SolveOptions solveOptions() {
		return SolveOptions.builder().useSolverDefaultMipGap().build();
	}

	private static SparseLinearModel sparseModel(DistOpfModel model) {
		int variableCount = model.getNumberOfVariables();
		int constraintCount = model.getConstraints().size();
		int nonzeroCount = nonzeroCount(model);
		double[] lowerBounds = variableLowerBounds(model, variableCount);
		double[] upperBounds = variableUpperBounds(model, variableCount);
		VariableType[] variableTypes = variableTypes(model, variableCount, lowerBounds, upperBounds);
		double[] objective = objective(model, variableCount);
		int[] rowStarts = new int[constraintCount + 1];
		int[] columnIndices = new int[nonzeroCount];
		double[] coefficients = new double[nonzeroCount];
		double[] constraintLowerBounds = new double[constraintCount];
		double[] constraintUpperBounds = new double[constraintCount];
		String[] constraintNames = new String[constraintCount];

		int row = 0;
		int position = 0;
		for (OpfConstraint constraint : model.getConstraints()) {
			rowStarts[row] = position;
			for (int i = 0; i < constraint.getColNo().size(); i++) {
				columnIndices[position] = constraint.getColNo().get(i);
				coefficients[position] = constraint.getVal().get(i);
				position++;
			}
			constraintLowerBounds[row] = lowerLimit(constraint);
			constraintUpperBounds[row] = upperLimit(constraint);
			constraintNames[row] = constraint.getDesc() == null || constraint.getDesc().isBlank()
					? "c" + row
					: constraint.getDesc();
			row++;
		}
		rowStarts[constraintCount] = position;

		return new SparseLinearModel(variableCount, constraintCount, ObjectiveSense.MINIMIZE, 0.0,
				lowerBounds, upperBounds, variableTypes, objective, rowStarts, columnIndices,
				coefficients, constraintLowerBounds, constraintUpperBounds, variableNames(variableCount),
				constraintNames, 0, 0);
	}

	private static int nonzeroCount(DistOpfModel model) {
		int count = 0;
		for (OpfConstraint constraint : model.getConstraints()) {
			count += constraint.getColNo().size();
		}
		return count;
	}

	private static double[] variableLowerBounds(DistOpfModel model, int variableCount) {
		double[] lowerBounds = new double[variableCount];
		double[] source = model.getLowerBounds();
		for (int i = 0; i < variableCount; i++) {
			lowerBounds[i] = source != null && i < source.length && Double.isFinite(source[i])
					? source[i]
					: Double.NEGATIVE_INFINITY;
		}
		return lowerBounds;
	}

	private static double[] variableUpperBounds(DistOpfModel model, int variableCount) {
		double[] upperBounds = new double[variableCount];
		double[] source = model.getUpperBounds();
		for (int i = 0; i < variableCount; i++) {
			upperBounds[i] = source != null && i < source.length && Double.isFinite(source[i])
					? source[i]
					: Double.POSITIVE_INFINITY;
		}
		return upperBounds;
	}

	private static VariableType[] variableTypes(DistOpfModel model, int variableCount,
			double[] lowerBounds, double[] upperBounds) {
		VariableType[] types = new VariableType[variableCount];
		boolean[] integerVariables = model.getIntegerVariables();
		for (int i = 0; i < variableCount; i++) {
			boolean integer = integerVariables != null && i < integerVariables.length && integerVariables[i];
			types[i] = !integer ? VariableType.CONTINUOUS
					: lowerBounds[i] >= 0.0 && upperBounds[i] <= 1.0
							? VariableType.BINARY
							: VariableType.INTEGER;
		}
		return types;
	}

	private static double[] objective(DistOpfModel model, int variableCount) {
		double[] objective = new double[variableCount];
		double[] source = model.getLinearObjective();
		if (source != null) {
			System.arraycopy(source, 0, objective, 0, Math.min(source.length, objective.length));
		}
		return objective;
	}

	private static String[] variableNames(int variableCount) {
		String[] names = new String[variableCount];
		for (int i = 0; i < variableCount; i++) {
			names[i] = "x" + i;
		}
		return names;
	}

	private static double lowerLimit(OpfConstraint constraint) {
		if (constraint.getCstType() == OpfConstraintType.EQUALITY) {
			return constraint.getUpperLimit();
		}
		return DistOpfLimitUtil.hasFiniteLowerLimit(constraint.getLowerLimit())
				? constraint.getLowerLimit()
				: Double.NEGATIVE_INFINITY;
	}

	private static double upperLimit(OpfConstraint constraint) {
		if (constraint.getCstType() == OpfConstraintType.EQUALITY) {
			return constraint.getUpperLimit();
		}
		return DistOpfLimitUtil.hasFiniteUpperLimit(constraint.getUpperLimit())
				? constraint.getUpperLimit()
				: Double.POSITIVE_INFINITY;
	}

	private static double[] primal(DistOpfModel model, SolveResult result) {
		double[] values = result.primalValues();
		if (values == null || values.length < model.getNumberOfVariables()) {
			return new double[0];
		}
		double[] primal = new double[model.getNumberOfVariables()];
		System.arraycopy(values, 0, primal, 0, primal.length);
		return primal;
	}

	private static double objective(SolveResult result) {
		OptionalDouble objective = result.primalObjective();
		return objective.isPresent() ? objective.getAsDouble() : Double.NaN;
	}

	private static String message(SolveResult result) {
		String message = result.message();
		return message == null || message.isBlank()
				? result.terminationReason().name()
				: message;
	}

	private static DistOpfStatus mapStatus(TerminationReason reason, double[] primal) {
		if (reason == TerminationReason.OPTIMAL) {
			return DistOpfStatus.OPTIMAL;
		}
		if (reason == TerminationReason.LIMIT && validPrimal(primal)) {
			return DistOpfStatus.FEASIBLE;
		}
		if (reason == TerminationReason.INFEASIBLE) {
			return DistOpfStatus.INFEASIBLE;
		}
		if (reason == TerminationReason.UNBOUNDED) {
			return DistOpfStatus.UNBOUNDED;
		}
		return DistOpfStatus.ERROR;
	}

	private static boolean isFeasible(DistOpfStatus status) {
		return status == DistOpfStatus.OPTIMAL || status == DistOpfStatus.FEASIBLE;
	}

	private static List<String> diagnostics(DistOpfModel model, SolveResult result, double maxResidual) {
		List<String> diagnostics = new ArrayList<String>();
		if (result.terminationReason() != TerminationReason.OPTIMAL) {
			diagnostics.add("Solver status: " + result.terminationReason());
			diagnostics.add(message(result));
			diagnostics.add("Variables: " + model.getNumberOfVariables());
			diagnostics.add("Constraints: " + model.getConstraints().size());
			diagnostics.add("Max residual: " + maxResidual);
		}
		return diagnostics;
	}

	private static DistOpfSolverResult failure(DistOpfModel model, Throwable e) {
		List<String> diagnostics = new ArrayList<String>();
		diagnostics.add("IPSS optimization backend error");
		diagnostics.add(e.getClass().getSimpleName() + ": " + e.getMessage());
		diagnostics.add("Variables: " + model.getNumberOfVariables());
		diagnostics.add("Constraints: " + model.getConstraints().size());
		return new DistOpfSolverResult(DistOpfStatus.ERROR, Double.NaN, Double.NaN,
				new double[0], "IPSS optimization backend error",
				new ArrayList<String>(), diagnostics);
	}

	private static double maxResidual(DistOpfModel model, double[] primal) {
		if (!validPrimal(model, primal)) {
			return Double.NaN;
		}
		double maxResidual = 0.0;
		for (OpfConstraint constraint : model.getConstraints()) {
			double activity = activity(constraint, primal);
			double residual = 0.0;
			if (constraint.getCstType() == OpfConstraintType.EQUALITY) {
				residual = Math.abs(activity - constraint.getUpperLimit());
			} else {
				if (DistOpfLimitUtil.hasFiniteLowerLimit(constraint.getLowerLimit())
						&& activity < constraint.getLowerLimit()) {
					residual = Math.max(residual, constraint.getLowerLimit() - activity);
				}
				if (DistOpfLimitUtil.hasFiniteUpperLimit(constraint.getUpperLimit())
						&& activity > constraint.getUpperLimit()) {
					residual = Math.max(residual, activity - constraint.getUpperLimit());
				}
			}
			maxResidual = Math.max(maxResidual, residual);
		}
		return maxResidual;
	}

	private static List<String> bindingConstraints(DistOpfModel model, double[] primal, double tolerance) {
		List<String> bindingConstraints = new ArrayList<String>();
		if (!validPrimal(model, primal)) {
			return bindingConstraints;
		}
		double tol = Math.max(tolerance, 1.0e-7);
		for (OpfConstraint constraint : model.getConstraints()) {
			if (constraint.getCstType() == OpfConstraintType.EQUALITY) {
				continue;
			}
			double activity = activity(constraint, primal);
			if (DistOpfLimitUtil.hasFiniteLowerLimit(constraint.getLowerLimit())
					&& Math.abs(activity - constraint.getLowerLimit()) <= tol) {
				bindingConstraints.add(constraint.getDesc() + "@lower");
			}
			if (DistOpfLimitUtil.hasFiniteUpperLimit(constraint.getUpperLimit())
					&& Math.abs(activity - constraint.getUpperLimit()) <= tol) {
				bindingConstraints.add(constraint.getDesc() + "@upper");
			}
		}
		return bindingConstraints;
	}

	private static boolean validPrimal(double[] primal) {
		if (primal == null || primal.length == 0) {
			return false;
		}
		for (double value : primal) {
			if (!Double.isFinite(value)) {
				return false;
			}
		}
		return true;
	}

	private static boolean validPrimal(DistOpfModel model, double[] primal) {
		return primal != null && primal.length >= model.getNumberOfVariables()
				&& validPrimal(primal);
	}

	private static double activity(OpfConstraint constraint, double[] primal) {
		double activity = 0.0;
		for (int i = 0; i < constraint.getColNo().size(); i++) {
			activity += constraint.getVal().get(i) * primal[constraint.getColNo().get(i)];
		}
		return activity;
	}
}
