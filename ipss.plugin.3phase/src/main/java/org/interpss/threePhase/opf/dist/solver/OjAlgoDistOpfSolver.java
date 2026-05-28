package org.interpss.threePhase.opf.dist.solver;

import org.interpss.plugin.opf.constraint.OpfConstraint;
import org.interpss.threePhase.opf.dist.DistOpfOptions;
import org.interpss.threePhase.opf.dist.DistOpfStatus;
import org.interpss.threePhase.opf.dist.model.DistOpfModel;
import org.ojalgo.optimisation.Expression;
import org.ojalgo.optimisation.ExpressionsBasedModel;
import org.ojalgo.optimisation.Optimisation;
import org.ojalgo.optimisation.Variable;

import com.interpss.opf.datatype.OpfConstraintType;

public class OjAlgoDistOpfSolver implements DistOpfSolver {

	@Override
	public DistOpfSolverResult solve(DistOpfModel model, DistOpfOptions options) {
		if (model.getNumberOfVariables() == 0) {
			return new DistOpfSolverResult(DistOpfStatus.NOT_SOLVED, 0.0, 0.0,
					new double[0], "Empty DistOPF model");
		}
		ExpressionsBasedModel ojModel = new ExpressionsBasedModel();
		double[] objective = model.getLinearObjective();
		for (int i = 0; i < model.getNumberOfVariables(); i++) {
			Variable variable = ojModel.addVariable("x" + i);
			if (objective != null && i < objective.length) {
				variable.weight(objective[i]);
			}
		}
		for (OpfConstraint constraint : model.getConstraints()) {
			Expression expression = ojModel.addExpression(constraint.getDesc());
			for (int i = 0; i < constraint.getColNo().size(); i++) {
				expression.set(constraint.getColNo().get(i), constraint.getVal().get(i));
			}
			applyBounds(expression, constraint);
		}

		Optimisation.Result result = ojModel.minimise();
		double[] primal = result.toRawCopy1D();
		DistOpfStatus status = mapStatus(result.getState());
		return new DistOpfSolverResult(status, result.getValue(), maxResidual(model, primal),
				primal, result.getState().toString());
	}

	private static void applyBounds(Expression expression, OpfConstraint constraint) {
		if (constraint.getCstType() == OpfConstraintType.EQUALITY) {
			expression.level(constraint.getUpperLimit());
		} else {
			if (constraint.getLowerLimit() > -1.0e19) {
				expression.lower(constraint.getLowerLimit());
			}
			if (constraint.getUpperLimit() < 1.0e19) {
				expression.upper(constraint.getUpperLimit());
			}
		}
	}

	private static DistOpfStatus mapStatus(Optimisation.State state) {
		if (state.isOptimal()) {
			return DistOpfStatus.OPTIMAL;
		}
		if (state.isFeasible()) {
			return DistOpfStatus.FEASIBLE;
		}
		if (state == Optimisation.State.INFEASIBLE) {
			return DistOpfStatus.INFEASIBLE;
		}
		if (state == Optimisation.State.UNBOUNDED) {
			return DistOpfStatus.UNBOUNDED;
		}
		return DistOpfStatus.ERROR;
	}

	private static double maxResidual(DistOpfModel model, double[] primal) {
		double maxResidual = 0.0;
		for (OpfConstraint constraint : model.getConstraints()) {
			double activity = activity(constraint, primal);
			double residual = 0.0;
			if (constraint.getCstType() == OpfConstraintType.EQUALITY) {
				residual = Math.abs(activity - constraint.getUpperLimit());
			} else {
				if (constraint.getLowerLimit() > -1.0e19 && activity < constraint.getLowerLimit()) {
					residual = Math.max(residual, constraint.getLowerLimit() - activity);
				}
				if (constraint.getUpperLimit() < 1.0e19 && activity > constraint.getUpperLimit()) {
					residual = Math.max(residual, activity - constraint.getUpperLimit());
				}
			}
			maxResidual = Math.max(maxResidual, residual);
		}
		return maxResidual;
	}

	private static double activity(OpfConstraint constraint, double[] primal) {
		double activity = 0.0;
		for (int i = 0; i < constraint.getColNo().size(); i++) {
			activity += constraint.getVal().get(i) * primal[constraint.getColNo().get(i)];
		}
		return activity;
	}
}
