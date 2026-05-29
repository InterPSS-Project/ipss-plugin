package org.interpss.threePhase.opf.dist;

import java.util.List;

import org.interpss.threePhase.opf.dist.impl.DistOpfResultMapper;
import org.interpss.threePhase.opf.dist.model.DistOpfModel;
import org.interpss.threePhase.opf.dist.model.DistOpfModelData;
import org.interpss.threePhase.opf.dist.model.LinDistFlowModelBuilder;
import org.interpss.threePhase.opf.dist.solver.DistOpfSolverFactory;
import org.interpss.threePhase.opf.dist.solver.DistOpfSolverResult;

public class DistOpfScheduleSolver {

	public DistOpfScheduleResult solve(DistOpfModelData modelData, List<DistOpfSchedulePeriod> periods) {
		DistOpfScheduleResult scheduleResult = new DistOpfScheduleResult();
		for (DistOpfSchedulePeriod period : periods) {
			DistOpfOptions options = period.getOptions();
			DistOpfModel model = new LinDistFlowModelBuilder().build(modelData, options,
					period.getControlMode(), period.getObjective());
			DistOpfSolverResult solverResult = DistOpfSolverFactory.create(options).solve(model, options);
			DistOpfResult result = new DistOpfResult(solverResult.getStatus(),
					solverResult.getObjectiveValue(), solverResult.getMaxConstraintResidual());
			DistOpfResultMapper.map(model, solverResult, result);
			scheduleResult.addPeriodResult(result);
		}
		return scheduleResult;
	}
}
