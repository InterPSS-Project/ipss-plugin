package org.interpss.plugin.fstate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Map;

import com.interpss.algo.fstate.FStateDclfAlgorithm;
import com.interpss.algo.fstate.plan.model.PlanMaintainModel;
import com.interpss.algo.fstate.plan.model.type.FSPlanMaintainModelType;
import com.interpss.algo.fstate.result.adapter.dclf.FStateDclfBranchInfoAdapter;
import com.interpss.algo.fstate.result.adapter.dclf.FStateDclfBusInfoAdapter;
import com.interpss.algo.fstate.result.adapter.dclf.FStateDclfSubStationInfoAdapter;
import com.interpss.algo.fstate.result.bean.FSDclfBranchInfoRec;
import com.interpss.algo.fstate.result.bean.FSDclfBusInfoRec;
import com.interpss.algo.fstate.result.bean.FSDclfSubStationInfoRec;
import com.interpss.core.algo.dclf.ContingencyAnalysisAlgorithm;

final class FSPluginDclfAlgoRunAssertions {

    static final String SAMPLE_BRANCH = "Bus22_to_Bus35_cirId_1";
    static final String SAMPLE_BUS_UID = "LBUS21";
    static final String SAMPLE_BUS_ID = "Bus21";
    static final String SAMPLE_SUB = "Sub01";
    static final String UNKNOWN_SUB = "unknownSub";

    static final int[] SPOT_TIME_POINTS = {0, 32, 64, 95};

    private static final double T0_REF_BUS_P = 5.228714093513246;
    private static final double SAMPLE_BRANCH_FLOW_MW = -650.0;
    private static final double SAMPLE_BUS_T0_ANGLE = -0.050318285044321634;
    private static final double SAMPLE_BUS_T0_POWER_MW = -274.0;

    private FSPluginDclfAlgoRunAssertions() {
    }

    static void assertDayAheadStructure(PlanMaintainModel model, FStateDclfAlgorithm fsAlgo) {
        assertEquals(FSPlanMaintainModelType.DayAhead, model.getPlanModelType());
        assertEquals(5, model.getNumsOfPeriods());
        assertEquals(96, model.getNumsOfTotalTimePoints());
        assertEquals(5, fsAlgo.getMStateDclfAlgo().getMsAclfNetList().size());
        assertEquals(96, fsAlgo.getMStateDclfAlgo().getMsDclfAlgoList().size());
        assertEquals(96, fsAlgo.getSimuSummaryInfoMap().size());
    }

    static void assertT0RefBusPower(FStateDclfAlgorithm fsAlgo) {
        ContingencyAnalysisAlgorithm dclfAlgo = fsAlgo.getDclfAlgo(0);
        double refBusP = dclfAlgo.getBusPower(dclfAlgo.getNetwork().getRefBusId());
        assertEquals(T0_REF_BUS_P, refBusP, 1.0e-6);
    }

    static void assertSampleBranchFlowSeries(FStateDclfAlgorithm fsAlgo) {
        Map<Integer, FSDclfBranchInfoRec> branchSeries =
                new FStateDclfBranchInfoAdapter(SAMPLE_BRANCH).adapt(fsAlgo);

        assertEquals(96, branchSeries.size());
        for (int t : SPOT_TIME_POINTS) {
            FSDclfBranchInfoRec rec = branchSeries.get(t);
            assertNotNull(rec, "T" + t);
            assertEquals(SAMPLE_BRANCH, rec.getBranchName());
            assertTrue(rec.isActive(), "T" + t + " active");
            assertEquals(SAMPLE_BRANCH_FLOW_MW, rec.getDclfFlow(), 0.1, "T" + t + " flow MW");
        }
    }

    static void assertSampleBusSeries(FStateDclfAlgorithm fsAlgo) {
        Map<Integer, FSDclfBusInfoRec> busSeries = new FStateDclfBusInfoAdapter(SAMPLE_BUS_UID).adapt(fsAlgo);

        assertEquals(96, busSeries.size());
        for (int t : SPOT_TIME_POINTS) {
            FSDclfBusInfoRec rec = busSeries.get(t);
            assertNotNull(rec, "T" + t);
            assertEquals(SAMPLE_BUS_UID, rec.getBusName());
            assertEquals(SAMPLE_BUS_ID, rec.getBusId());
        }
        assertEquals(SAMPLE_BUS_T0_ANGLE, busSeries.get(0).getBusAngle(), 1.0e-6);
        assertEquals(SAMPLE_BUS_T0_POWER_MW, busSeries.get(0).getBusPower(), 0.1);
    }

    static void assertSampleSubStationSeries(FStateDclfAlgorithm fsAlgo) {
        Map<Integer, FSDclfSubStationInfoRec> subSeries =
                new FStateDclfSubStationInfoAdapter(SAMPLE_SUB).adapt(fsAlgo);

        assertEquals(96, subSeries.size());
        for (int t : SPOT_TIME_POINTS) {
            FSDclfSubStationInfoRec rec = subSeries.get(t);
            assertNotNull(rec, "T" + t);
            assertEquals(SAMPLE_SUB, rec.getSubStationId());
            assertEquals(SAMPLE_SUB, rec.getSubStationName());
        }
    }

    static void assertUnknownSubStationSeriesEmpty(FStateDclfAlgorithm fsAlgo) {
        Map<Integer, FSDclfSubStationInfoRec> subSeries =
                new FStateDclfSubStationInfoAdapter(UNKNOWN_SUB).adapt(fsAlgo);
        assertTrue(subSeries.isEmpty());
    }
}
