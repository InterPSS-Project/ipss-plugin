package org.interpss.plugin.fstate.aux_fmt;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import java.time.LocalDateTime;

import org.interpss.plugin.fstate.aux_fmt.Aux2PlanMaintainAdapter;
import org.interpss.plugin.fstate.aux_fmt.bean.AuxTssParsedData;
import org.interpss.plugin.fstate.aux_fmt.bean.AuxTssSchedule;
import org.interpss.plugin.fstate.aux_fmt.parser.AuxTssScheduleAuxParser;
import org.junit.jupiter.api.Test;

import com.interpss.algo.fstate.plan.model.PlanMaintainModel;
import com.interpss.algo.fstate.plan.model.bean.EquipmentMaintainRec;
import com.interpss.algo.fstate.plan.model.bean.TimePointRec;
import com.interpss.algo.fstate.plan.model.type.EquipmentMaintainPlanState;
import com.interpss.algo.fstate.plan.model.type.FSPlanMaintainModelType;
import com.interpss.algo.fstate.plan.model.type.MPlanEquipmentType;

class PowerWorldPlanMaintainAdapterTest {

    private static final Path IEEE39_PW_DIR = Path.of("testData/powerworld/ieee39");

    @Test
    void loadIeee39Fixture_structure() throws Exception {
        PlanMaintainModel model = Aux2PlanMaintainAdapter.load(IEEE39_PW_DIR);

        assertEquals(FSPlanMaintainModelType.DayAhead, model.getPlanModelType());
        assertEquals(96, model.getNumsOfTotalTimePoints());
        assertEquals(15, model.getTimePointIntervalMin());
        assertEquals(2, model.getOriginalMaintainEquipemnts().size());

        LocalDateTime planStart = LocalDateTime.of(2026, 6, 27, 0, 0);
        assertEquals(planStart, model.getPoint2TimeMap().get(0));
        assertEquals(planStart.plusMinutes(15L * 95), model.getPoint2TimeMap().get(95));

        TimePointRec firstPoint = model.getTimePeriodRecList().get(0).getTimePointRecList().get(0);
        assertEquals(10, firstPoint.getGenMap().size());
        assertEquals(19, firstPoint.getLoadMap().size());
        assertEquals(572.8349, firstPoint.getGenMap().get("Bus31-G1").getP(), 1.0e-4);
        assertEquals(1104.0, firstPoint.getLoadMap().get("Bus39-L1").getP(), 1.0e-6);
    }

    @Test
    void loadIeee39Fixture_flatMwAcrossTimePoints() throws Exception {
        PlanMaintainModel model = Aux2PlanMaintainAdapter.load(IEEE39_PW_DIR);

        TimePointRec t0 = model.getTimePeriodRecList().get(0).getTimePointRecList().get(0);
        TimePointRec t1 = model.getTimePeriodRecList().get(0).getTimePointRecList().get(1);
        TimePointRec t2 = model.getTimePeriodRecList().get(0).getTimePointRecList().get(2);

        assertEquals(t0.getGenMap().get("Bus31-G1").getP(), t1.getGenMap().get("Bus31-G1").getP(), 1.0e-9);
        assertEquals(t0.getGenMap().get("Bus31-G1").getP(), t2.getGenMap().get("Bus31-G1").getP(), 1.0e-9);
        assertEquals(t0.getLoadMap().get("Bus39-L1").getP(), t1.getLoadMap().get("Bus39-L1").getP(), 1.0e-9);
        assertEquals(t0.getLoadMap().get("Bus39-L1").getP(), t2.getLoadMap().get("Bus39-L1").getP(), 1.0e-9);
    }

    @Test
    void loadIeee39Fixture_maintenanceWindows() throws Exception {
        PlanMaintainModel model = Aux2PlanMaintainAdapter.load(IEEE39_PW_DIR);

        EquipmentMaintainRec rec1 = model.getOriginalMaintainEquipemnts().get(0);
        assertEquals("Bus29_to_Bus26_cirId_1", rec1.getName());
        assertEquals(MPlanEquipmentType.Acline, rec1.getEquipType());
        assertEquals(EquipmentMaintainPlanState.Inactive, rec1.getPlanState());
        assertTrue(rec1.isChangeState());
        assertEquals(LocalDateTime.of(2026, 6, 27, 8, 0), rec1.getStartTime());
        assertEquals(LocalDateTime.of(2026, 6, 27, 11, 0), rec1.getEndTime());

        EquipmentMaintainRec rec2 = model.getOriginalMaintainEquipemnts().get(1);
        assertEquals("Bus26_to_Bus25_cirId_1", rec2.getName());
        assertEquals(MPlanEquipmentType.Acline, rec2.getEquipType());
        assertEquals(EquipmentMaintainPlanState.Inactive, rec2.getPlanState());
        assertTrue(rec2.isChangeState());
        assertEquals(LocalDateTime.of(2026, 6, 27, 14, 0), rec2.getStartTime());
        assertEquals(LocalDateTime.of(2026, 6, 27, 16, 0), rec2.getEndTime());
    }

    @Test
    void scheduleAuxParser_extractsSubdataSchedules() throws Exception {
        AuxTssParsedData parsed = new AuxTssScheduleAuxParser().parse(
                IEEE39_PW_DIR.resolve("ieee39_dayahead_plan_schedules.aux"));

        assertEquals(31, parsed.schedules().size());
        assertEquals(31, parsed.subscriptions().size());

        AuxTssSchedule genSchedule = parsed.schedules().stream()
                .filter(s -> "Sched_Gen_Bus31-G1".equals(s.scheduleName()))
                .findFirst()
                .orElseThrow();
        assertNotNull(genSchedule);
        assertTrue(genSchedule.isNumeric());
        assertEquals(1, genSchedule.points().size());
        assertEquals(572.8349, genSchedule.points().get(0).nValue(), 1.0e-4);

        AuxTssSchedule maintSchedule = parsed.schedules().stream()
                .filter(s -> "Sched_Maint_Bus29_to_Bus26_cirId_1".equals(s.scheduleName()))
                .findFirst()
                .orElseThrow();
        assertTrue(maintSchedule.isBoolean());
        assertEquals(3, maintSchedule.points().size());
    }
}
