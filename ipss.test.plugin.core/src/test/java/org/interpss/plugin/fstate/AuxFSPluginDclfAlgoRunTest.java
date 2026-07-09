package org.interpss.plugin.fstate;

import static org.interpss.plugin.fstate.FSPluginDclfAlgoRunAssertions.assertDayAheadStructure;
import static org.interpss.plugin.fstate.FSPluginDclfAlgoRunAssertions.assertSampleBranchFlowSeries;
import static org.interpss.plugin.fstate.FSPluginDclfAlgoRunAssertions.assertSampleBusSeries;
import static org.interpss.plugin.fstate.FSPluginDclfAlgoRunAssertions.assertSampleSubStationSeries;
import static org.interpss.plugin.fstate.FSPluginDclfAlgoRunAssertions.assertT0RefBusPower;
import static org.interpss.plugin.fstate.FSPluginDclfAlgoRunAssertions.assertUnknownSubStationSeriesEmpty;
import java.nio.file.Path;

import org.interpss.CorePluginTestSetup;
import org.interpss.plugin.fstate.aux_fmt.Aux2PlanMaintainAdapter;
import org.junit.jupiter.api.Test;

import com.interpss.algo.fstate.FStateDclfAlgorithm;
import com.interpss.algo.fstate.plan.model.PlanMaintainModel;

/**
 * Regression tests for {@code org.interpss.fstate.Aux_FSPluginDclfAlgoRunSample}.
 */
public class AuxFSPluginDclfAlgoRunTest extends CorePluginTestSetup {

    private static final Path IEEE39_PW_DIR = Path.of("testData/powerworld/ieee39");

    private PlanMaintainModel loadPlanModel() throws Exception {
        return Aux2PlanMaintainAdapter.load(IEEE39_PW_DIR);
    }

    @Test
    void auxPlan_runSample_structure() throws Exception {
        PlanMaintainModel model = loadPlanModel();
        FStateDclfAlgorithm fsAlgo = IEEE39FStateTestFixture.runDclfAssessment(model);
        assertDayAheadStructure(model, fsAlgo);
    }

    @Test
    void auxPlan_runSample_t0RefBusPower() throws Exception {
        FStateDclfAlgorithm fsAlgo = IEEE39FStateTestFixture.runDclfAssessment(loadPlanModel());
        assertT0RefBusPower(fsAlgo);
    }

    @Test
    void auxPlan_runSample_branchFlowSeries() throws Exception {
        FStateDclfAlgorithm fsAlgo = IEEE39FStateTestFixture.runDclfAssessment(loadPlanModel());
        assertSampleBranchFlowSeries(fsAlgo);
    }

    @Test
    void auxPlan_runSample_busSeries() throws Exception {
        FStateDclfAlgorithm fsAlgo = IEEE39FStateTestFixture.runDclfAssessment(loadPlanModel());
        assertSampleBusSeries(fsAlgo);
    }

    @Test
    void auxPlan_runSample_subStationSeries() throws Exception {
        FStateDclfAlgorithm fsAlgo = IEEE39FStateTestFixture.runDclfAssessment(loadPlanModel());
        assertSampleSubStationSeries(fsAlgo);
    }

    @Test
    void auxPlan_runSample_unknownSubStationSeries_empty() throws Exception {
        FStateDclfAlgorithm fsAlgo = IEEE39FStateTestFixture.runDclfAssessment(loadPlanModel());
        assertUnknownSubStationSeriesEmpty(fsAlgo);
    }
}
