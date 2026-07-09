package org.interpss.plugin.fstate;

import static org.interpss.plugin.fstate.FSPluginDclfAlgoRunAssertions.assertGenNotFoundEmpty;
import static org.interpss.plugin.fstate.FSPluginDclfAlgoRunAssertions.assertSampleBranchFlowSeries;
import static org.interpss.plugin.fstate.FSPluginDclfAlgoRunAssertions.assertSampleBusSeries;
import static org.interpss.plugin.fstate.FSPluginDclfAlgoRunAssertions.assertSampleSubStationSeries;
import static org.interpss.plugin.fstate.FSPluginDclfAlgoRunAssertions.assertT0RefBusPower;
import static org.interpss.plugin.fstate.FSPluginDclfAlgoRunAssertions.assertUnknownSubStationSeriesEmpty;
import static org.interpss.plugin.fstate.FSPluginDclfAlgoRunAssertions.assertWeekStructure;

import java.nio.file.Path;

import org.interpss.CorePluginTestSetup;
import org.interpss.plugin.fstate.aux_fmt.Aux2PlanMaintainAdapter;
import org.junit.jupiter.api.Test;

import com.interpss.algo.fstate.FStateDclfAlgorithm;
import com.interpss.algo.fstate.plan.model.PlanMaintainModel;

/**
 * Regression tests for {@code org.interpss.fstate.Aux_FSPluginWeekDclfAlgoRunSample}.
 */
public class AuxFSPluginWeekDclfAlgoRunTest extends CorePluginTestSetup {

    private static final Path IEEE39_PW_WEEK_DIR = Path.of("testData/powerworld/ieee39/week");

    private PlanMaintainModel loadPlanModel() throws Exception {
        return Aux2PlanMaintainAdapter.createWeekModel(IEEE39_PW_WEEK_DIR);
    }

    @Test
    void auxWeekPlan_runSample_structure() throws Exception {
        PlanMaintainModel model = loadPlanModel();
        FStateDclfAlgorithm fsAlgo = IEEE39Raw_FState_TestFixture.runDclfAssessment(model);
        assertWeekStructure(model, fsAlgo);
        assertGenNotFoundEmpty(fsAlgo);
    }

    @Test
    void auxWeekPlan_runSample_t0RefBusPower() throws Exception {
        FStateDclfAlgorithm fsAlgo = IEEE39Raw_FState_TestFixture.runDclfAssessment(loadPlanModel());
        assertT0RefBusPower(fsAlgo);
    }

    @Test
    void auxWeekPlan_runSample_branchFlowSeries() throws Exception {
        FStateDclfAlgorithm fsAlgo = IEEE39Raw_FState_TestFixture.runDclfAssessment(loadPlanModel());
        assertSampleBranchFlowSeries(fsAlgo, 168);
    }

    @Test
    void auxWeekPlan_runSample_busSeries() throws Exception {
        FStateDclfAlgorithm fsAlgo = IEEE39Raw_FState_TestFixture.runDclfAssessment(loadPlanModel());
        assertSampleBusSeries(fsAlgo, 168);
    }

    @Test
    void auxWeekPlan_runSample_subStationSeries() throws Exception {
        FStateDclfAlgorithm fsAlgo = IEEE39Raw_FState_TestFixture.runDclfAssessment(loadPlanModel());
        assertSampleSubStationSeries(fsAlgo, 168);
    }

    @Test
    void auxWeekPlan_runSample_unknownSubStationSeries_empty() throws Exception {
        FStateDclfAlgorithm fsAlgo = IEEE39Raw_FState_TestFixture.runDclfAssessment(loadPlanModel());
        assertUnknownSubStationSeriesEmpty(fsAlgo);
    }
}
