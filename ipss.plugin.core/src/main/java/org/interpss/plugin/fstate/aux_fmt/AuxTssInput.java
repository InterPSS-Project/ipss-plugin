package org.interpss.plugin.fstate.aux_fmt;

import java.nio.file.Path;

import com.interpss.algo.fstate.plan.model.type.FSPlanMaintainModelType;

/**
 * Input paths for {@link Aux2PlanMaintainAdapter}.
 */
public record AuxTssInput(
        Path schedulesAux,
        Path timepointsCsv,
        Path outagesCsv,
        FSPlanMaintainModelType planModelType,
        Integer intervalMinutesOverride) {

    public AuxTssInput {
        if (planModelType == null) {
            planModelType = FSPlanMaintainModelType.DayAhead;
        }
    }
}
