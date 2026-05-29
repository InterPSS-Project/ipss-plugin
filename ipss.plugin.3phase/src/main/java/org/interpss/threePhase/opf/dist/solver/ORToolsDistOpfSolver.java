package org.interpss.threePhase.opf.dist.solver;

import java.util.ArrayList;
import java.util.List;

import org.interpss.plugin.opf.constraint.OpfConstraint;
import org.interpss.threePhase.opf.dist.DistOpfOptions;
import org.interpss.threePhase.opf.dist.DistOpfStatus;
import org.interpss.threePhase.opf.dist.model.DistOpfModel;
import org.interpss.threePhase.opf.dist.util.DistOpfLimitUtil;

import com.google.ortools.Loader;
import com.google.ortools.linearsolver.MPConstraint;
import com.google.ortools.linearsolver.MPObjective;
import com.google.ortools.linearsolver.MPSolver;
import com.google.ortools.linearsolver.MPVariable;
import com.interpss.opf.datatype.OpfConstraintType;

public class ORToolsDistOpfSolver implements DistOpfSolver {

	@Override
	public DistOpfSolverResult solve(DistOpfModel model, DistOpfOptions options) {
		if (model.getNumberOfVariables() == 0) {
			return new DistOpfSolverResult(DistOpfStatus.NOT_SOLVED, 0.0, 0.0,
					new double[0], "Empty DistOPF model");
		}
		if (hasIntegerVariables(model)) {
			return new DistOpfSolverResult(DistOpfStatus.ERROR, Double.NaN, Double.NaN,
					new double[model.getNumberOfVariables()],
					"OR-Tools GLOP adapter supports continuous LP models only",
					new ArrayList<String>(), diagnostics(model, "Integer variables are present"));
		}
		MPSolver solver;
		try {
			Loader.loadNativeLibraries();
			solver = MPSolver.createSolver("GLOP");
		} catch (RuntimeException | UnsatisfiedLinkError e) {
			return new DistOpfSolverResult(DistOpfStatus.ERROR, Double.NaN, Double.NaN,
					new double[model.getNumberOfVariables()],
					"Unable to load OR-Tools native libraries",
					new ArrayList<String>(), diagnostics(model, e.getMessage()));
		}
		if (solver == null) {
			return new DistOpfSolverResult(DistOpfStatus.ERROR, Double.NaN, Double.NaN,
					new double[model.getNumberOfVariables()],
					"Unable to create OR-Tools GLOP solver",
					new ArrayList<String>(), diagnostics(model, "MPSolver.createSolver(\"GLOP\") returned null"));
		}
		MPVariable[] variables = variables(model, solver);
		objective(model, solver.objective(), variables);
		for (OpfConstraint constraint : model.getConstraints()) {
			MPConstraint row = solver.makeConstraint(lowerLimit(constraint), upperLimit(constraint),
					constraint.getDesc());
			for (int i = 0; i < constraint.getColNo().size(); i++) {
				row.setCoefficient(variables[constraint.getColNo().get(i)], constraint.getVal().get(i));
			}
		}
		MPSolver.ResultStatus status = solver.solve();
		double[] primal = primal(variables);
		DistOpfStatus mappedStatus = mapStatus(status);
		double maxResidual = maxResidual(model, primal);
		List<String> diagnostics = status == MPSolver.ResultStatus.OPTIMAL
				? new ArrayList<String>()
				: diagnostics(model, "Solver status: " + status);
		return new DistOpfSolverResult(mappedStatus, solver.objective().value(), maxResidual,
				primal, status.toString(), bindingConstraints(model, primal, options.getSolverTolerance()),
				diagnostics);
	}

	public boolean isOrToolsAvailable() {
		try {
			Loader.loadNativeLibraries();
			return true;
		} catch (RuntimeException | UnsatisfiedLinkError e) {
			return false;
		}
	}

	private static boolean hasIntegerVariables(DistOpfModel model) {
		boolean[] integerVariables = model.getIntegerVariables();
		if (integerVariables == null) {
			return false;
		}
		for (boolean integerVariable : integerVariables) {
			if (integerVariable) {
				return true;
			}
		}
		return false;
	}

	private static MPVariable[] variables(DistOpfModel model, MPSolver solver) {
		MPVariable[] variables = new MPVariable[model.getNumberOfVariables()];
		double[] lowerBounds = model.getLowerBounds();
		double[] upperBounds = model.getUpperBounds();
		for (int i = 0; i < variables.length; i++) {
			variables[i] = solver.makeNumVar(bound(lowerBounds, i, -MPSolver.infinity()),
					bound(upperBounds, i, MPSolver.infinity()), "x" + i);
		}
		return variables;
	}

	private static double bound(double[] bounds, int index, double defaultValue) {
		return bounds != null && index < bounds.length && Double.isFinite(bounds[index])
				? bounds[index]
				: defaultValue;
	}

	private static void objective(DistOpfModel model, MPObjective objective, MPVariable[] variables) {
		double[] weights = model.getLinearObjective();
		if (weights != null) {
			for (int i = 0; i < variables.length && i < weights.length; i++) {
				objective.setCoefficient(variables[i], weights[i]);
			}
		}
		objective.setMinimization();
	}

	private static double lowerLimit(OpfConstraint constraint) {
		if (constraint.getCstType() == OpfConstraintType.EQUALITY) {
			return constraint.getUpperLimit();
		}
		return DistOpfLimitUtil.hasFiniteLowerLimit(constraint.getLowerLimit())
				? constraint.getLowerLimit()
				: -MPSolver.infinity();
	}

	private static double upperLimit(OpfConstraint constraint) {
		if (constraint.getCstType() == OpfConstraintType.EQUALITY) {
			return constraint.getUpperLimit();
		}
		return DistOpfLimitUtil.hasFiniteUpperLimit(constraint.getUpperLimit())
				? constraint.getUpperLimit()
				: MPSolver.infinity();
	}

	private static DistOpfStatus mapStatus(MPSolver.ResultStatus status) {
		if (status == MPSolver.ResultStatus.OPTIMAL) {
			return DistOpfStatus.OPTIMAL;
		}
		if (status == MPSolver.ResultStatus.FEASIBLE) {
			return DistOpfStatus.FEASIBLE;
		}
		if (status == MPSolver.ResultStatus.INFEASIBLE) {
			return DistOpfStatus.INFEASIBLE;
		}
		if (status == MPSolver.ResultStatus.UNBOUNDED) {
			return DistOpfStatus.UNBOUNDED;
		}
		return DistOpfStatus.ERROR;
	}

	private static double[] primal(MPVariable[] variables) {
		double[] primal = new double[variables.length];
		for (int i = 0; i < variables.length; i++) {
			primal[i] = variables[i].solutionValue();
		}
		return primal;
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

	private static List<String> diagnostics(DistOpfModel model, String message) {
		List<String> diagnostics = new ArrayList<String>();
		diagnostics.add(message);
		diagnostics.add("Variables: " + model.getNumberOfVariables());
		diagnostics.add("Constraints: " + model.getConstraints().size());
		return diagnostics;
	}

	private static boolean validPrimal(DistOpfModel model, double[] primal) {
		if (primal == null || primal.length < model.getNumberOfVariables()) {
			return false;
		}
		for (int i = 0; i < model.getNumberOfVariables(); i++) {
			if (!Double.isFinite(primal[i])) {
				return false;
			}
		}
		return true;
	}

	private static double activity(OpfConstraint constraint, double[] primal) {
		double activity = 0.0;
		for (int i = 0; i < constraint.getColNo().size(); i++) {
			activity += constraint.getVal().get(i) * primal[constraint.getColNo().get(i)];
		}
		return activity;
	}
}
