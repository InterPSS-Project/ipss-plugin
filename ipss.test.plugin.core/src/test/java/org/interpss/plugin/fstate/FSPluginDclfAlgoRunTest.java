package org.interpss.plugin.fstate;

import static org.interpss.plugin.fstate.FSPluginDclfAlgoRunAssertions.assertDayAheadStructure;
import static org.interpss.plugin.fstate.FSPluginDclfAlgoRunAssertions.assertSampleBranchFlowSeries;
import static org.interpss.plugin.fstate.FSPluginDclfAlgoRunAssertions.assertSampleBusSeries;
import static org.interpss.plugin.fstate.FSPluginDclfAlgoRunAssertions.assertSampleSubStationSeries;
import static org.interpss.plugin.fstate.FSPluginDclfAlgoRunAssertions.assertT0RefBusPower;
import static org.interpss.plugin.fstate.FSPluginDclfAlgoRunAssertions.assertUnknownSubStationSeriesEmpty;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import org.interpss.CorePluginTestSetup;
import org.junit.jupiter.api.Test;

import com.interpss.algo.fstate.FStateDclfAlgorithm;
import com.interpss.algo.fstate.plan.PlanMaintainModelBuilder;
import com.interpss.algo.fstate.plan.model.PlanMaintainModel;

/**
 * Regression tests for {@code org.interpss.fstate.FSPluginDclfAlgoRunSample}.
 */
public class FSPluginDclfAlgoRunTest extends CorePluginTestSetup {

    private static final Path PLAN_JSON = Path.of(
            "../ipss.plugin.core/testData/psse/v30/ieee39Raw_dayahead_plan_maintain_plan.json");

    private PlanMaintainModel loadPlanModel() throws Exception {
        return PlanMaintainModelBuilder.fromJson(Files.readString(PLAN_JSON, StandardCharsets.UTF_8));
    }

    @Test
    void jsonPlan_runSample_structure() throws Exception {
        PlanMaintainModel model = loadPlanModel();
        FStateDclfAlgorithm fsAlgo = IEEE39FStateTestFixture.runDclfAssessment(model);
        assertDayAheadStructure(model, fsAlgo);
    }

    @Test
    void jsonPlan_runSample_t0RefBusPower() throws Exception {
        FStateDclfAlgorithm fsAlgo = IEEE39FStateTestFixture.runDclfAssessment(loadPlanModel());
        assertT0RefBusPower(fsAlgo);
    }

    @Test
    void jsonPlan_runSample_branchFlowSeries() throws Exception {
        FStateDclfAlgorithm fsAlgo = IEEE39FStateTestFixture.runDclfAssessment(loadPlanModel());
        assertSampleBranchFlowSeries(fsAlgo);
    }

    @Test
    void jsonPlan_runSample_busSeries() throws Exception {
        FStateDclfAlgorithm fsAlgo = IEEE39FStateTestFixture.runDclfAssessment(loadPlanModel());
        assertSampleBusSeries(fsAlgo);
    }

    @Test
    void jsonPlan_runSample_subStationSeries() throws Exception {
        FStateDclfAlgorithm fsAlgo = IEEE39FStateTestFixture.runDclfAssessment(loadPlanModel());
        assertSampleSubStationSeries(fsAlgo);
    }

    @Test
    void jsonPlan_runSample_unknownSubStationSeries_empty() throws Exception {
        FStateDclfAlgorithm fsAlgo = IEEE39FStateTestFixture.runDclfAssessment(loadPlanModel());
        assertUnknownSubStationSeriesEmpty(fsAlgo);
    }
}
