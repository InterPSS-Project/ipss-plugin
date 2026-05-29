package org.interpss.threePhase.opf.dist.solver;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import org.apache.commons.math3.optim.MaxIter;
import org.apache.commons.math3.optim.PointValuePair;
import org.apache.commons.math3.optim.linear.LinearConstraint;
import org.apache.commons.math3.optim.linear.LinearConstraintSet;
import org.apache.commons.math3.optim.linear.LinearObjectiveFunction;
import org.apache.commons.math3.optim.linear.NoFeasibleSolutionException;
import org.apache.commons.math3.optim.linear.NonNegativeConstraint;
import org.apache.commons.math3.optim.linear.Relationship;
import org.apache.commons.math3.optim.linear.SimplexSolver;
import org.apache.commons.math3.optim.linear.UnboundedSolutionException;
import org.apache.commons.math3.optim.nonlinear.scalar.GoalType;
import org.interpss.plugin.opf.constraint.OpfConstraint;
import org.interpss.threePhase.opf.dist.DistOpfOptions;
import org.interpss.threePhase.opf.dist.DistOpfStatus;
import org.interpss.threePhase.opf.dist.model.DistOpfModel;
import org.interpss.threePhase.opf.dist.util.DistOpfLimitUtil;

import com.interpss.opf.datatype.OpfConstraintType;

public class ApacheLpDistOpfSolver implements DistOpfSolver {

	@Override
	public DistOpfSolverResult solve(DistOpfModel model, DistOpfOptions options) {
		if (model.getNumberOfVariables() == 0) {
			return new DistOpfSolverResult(DistOpfStatus.NOT_SOLVED, 0.0, 0.0,
					new double[0], "Empty DistOPF model");
		}
		if (hasIntegerVariables(model)) {
			List<String> diagnostics = new ArrayList<String>();
			diagnostics.add("Apache LP DistOPF solver supports continuous LP models only.");
			diagnostics.add("Select ojAlgo or another MIP-capable solver for capacitor/regulator integer controls.");
			return new DistOpfSolverResult(DistOpfStatus.ERROR, Double.NaN, Double.NaN,
					new double[0], "Apache LP DistOPF solver cannot solve integer models",
					new ArrayList<String>(), diagnostics);
		}

		try {
			Collection<LinearConstraint> constraints = new ArrayList<LinearConstraint>();
			addModelConstraints(model, constraints);
			addVariableBounds(model, constraints);
			LinearObjectiveFunction objective = new LinearObjectiveFunction(
					objective(model), 0.0);
			PointValuePair optimum = new SimplexSolver(options.getSolverTolerance())
					.optimize(new MaxIter(10000), objective,
							new LinearConstraintSet(constraints), GoalType.MINIMIZE,
							new NonNegativeConstraint(false));
			double[] primal = expand(optimum.getPoint(), model.getNumberOfVariables());
			double maxResidual = maxResidual(model, primal);
			List<String> bindingConstraints = bindingConstraints(model, primal, options.getSolverTolerance());
			return new DistOpfSolverResult(DistOpfStatus.OPTIMAL, optimum.getValue(), maxResidual,
					primal, "Apache Commons Math Simplex OPTIMAL", bindingConstraints,
					new ArrayList<String>());
		} catch (NoFeasibleSolutionException e) {
			return failure(DistOpfStatus.INFEASIBLE, model, "Apache LP solver reported infeasible model", e);
		} catch (UnboundedSolutionException e) {
			return failure(DistOpfStatus.UNBOUNDED, model, "Apache LP solver reported unbounded model", e);
		} catch (RuntimeException e) {
			return failure(DistOpfStatus.ERROR, model, "Apache LP solver error", e);
		}
	}

	private static void addModelConstraints(DistOpfModel model, Collection<LinearConstraint> constraints) {
		for (OpfConstraint constraint : model.getConstraints()) {
			double[] coefficients = new double[model.getNumberOfVariables()];
			for (int i = 0; i < constraint.getColNo().size(); i++) {
				coefficients[constraint.getColNo().get(i)] += constraint.getVal().get(i);
			}
			if (constraint.getCstType() == OpfConstraintType.EQUALITY) {
				constraints.add(new LinearConstraint(coefficients, Relationship.EQ, constraint.getUpperLimit()));
			} else {
				if (DistOpfLimitUtil.hasFiniteLowerLimit(constraint.getLowerLimit())) {
					constraints.add(new LinearConstraint(coefficients, Relationship.GEQ, constraint.getLowerLimit()));
				}
				if (DistOpfLimitUtil.hasFiniteUpperLimit(constraint.getUpperLimit())) {
					constraints.add(new LinearConstraint(coefficients, Relationship.LEQ, constraint.getUpperLimit()));
				}
			}
		}
	}

	private static void addVariableBounds(DistOpfModel model, Collection<LinearConstraint> constraints) {
		double[] lowerBounds = model.getLowerBounds();
		double[] upperBounds = model.getUpperBounds();
		for (int i = 0; i < model.getNumberOfVariables(); i++) {
			double[] coefficient = new double[model.getNumberOfVariables()];
			coefficient[i] = 1.0;
			if (lowerBounds != null && i < lowerBounds.length && Double.isFinite(lowerBounds[i])) {
				constraints.add(new LinearConstraint(coefficient, Relationship.GEQ, lowerBounds[i]));
			}
			if (upperBounds != null && i < upperBounds.length && Double.isFinite(upperBounds[i])) {
				constraints.add(new LinearConstraint(coefficient, Relationship.LEQ, upperBounds[i]));
			}
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

	private static double[] objective(DistOpfModel model) {
		double[] source = model.getLinearObjective();
		double[] objective = new double[model.getNumberOfVariables()];
		if (source != null) {
			System.arraycopy(source, 0, objective, 0, Math.min(source.length, objective.length));
		}
		return objective;
	}

	private static double[] expand(double[] source, int size) {
		double[] expanded = new double[size];
		if (source != null) {
			System.arraycopy(source, 0, expanded, 0, Math.min(source.length, size));
		}
		return expanded;
	}

	private static DistOpfSolverResult failure(DistOpfStatus status, DistOpfModel model,
			String message, RuntimeException e) {
		List<String> diagnostics = new ArrayList<String>();
		diagnostics.add(message);
		diagnostics.add(e.getClass().getSimpleName() + ": " + e.getMessage());
		diagnostics.add("Variables: " + model.getNumberOfVariables());
		diagnostics.add("Constraints: " + model.getConstraints().size());
		return new DistOpfSolverResult(status, Double.NaN, Double.NaN, new double[0],
				message, new ArrayList<String>(), diagnostics);
	}

	private static double maxResidual(DistOpfModel model, double[] primal) {
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

	private static double activity(OpfConstraint constraint, double[] primal) {
		double activity = 0.0;
		for (int i = 0; i < constraint.getColNo().size(); i++) {
			activity += constraint.getVal().get(i) * primal[constraint.getColNo().get(i)];
		}
		return activity;
	}
}
