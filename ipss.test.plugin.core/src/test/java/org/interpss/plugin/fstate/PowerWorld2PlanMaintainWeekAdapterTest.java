package org.interpss.plugin.fstate;

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

public class PowerWorld2PlanMaintainWeekAdapterTest {

    private static final Path IEEE39_PW_WEEK_DIR = Path.of("testData/powerworld/ieee39/week");

    @Test
    void loadIeee39WeekFixture_structure() throws Exception {
        PlanMaintainModel model = Aux2PlanMaintainAdapter.createWeekModel(IEEE39_PW_WEEK_DIR);

        assertEquals(FSPlanMaintainModelType.Week, model.getPlanModelType());
        assertEquals(168, model.getNumsOfTotalTimePoints());
        assertEquals(60, model.getTimePointIntervalMin());
        assertEquals(7, model.getNumsOfPeriods());
        assertEquals(3, model.getOriginalMaintainEquipemnts().size());

        LocalDateTime planStart = LocalDateTime.of(2026, 6, 23, 0, 0);
        assertEquals(planStart, model.getPoint2TimeMap().get(0));
        assertEquals(planStart.plusHours(167), model.getPoint2TimeMap().get(167));

        TimePointRec firstPoint = model.getTimePeriodRecList().get(0).getTimePointRecList().get(0);
        assertEquals(10, firstPoint.getGenMap().size());
        assertEquals(19, firstPoint.getLoadMap().size());
        assertEquals(572.8349, firstPoint.getGenMap().get("Bus31-G1").getP(), 1.0e-4);
        assertEquals(1104.0, firstPoint.getLoadMap().get("Bus39-L1").getP(), 1.0e-6);
    }

    @Test
    void loadIeee39WeekFixture_mwProfile() throws Exception {
        PlanMaintainModel model = Aux2PlanMaintainAdapter.createWeekModel(IEEE39_PW_WEEK_DIR);

        TimePointRec t0 = model.getTimePeriodRecList().get(0).getTimePointRecList().get(0);
        TimePointRec t1 = model.getTimePeriodRecList().get(0).getTimePointRecList().get(1);
        TimePointRec t2 = model.getTimePeriodRecList().get(0).getTimePointRecList().get(2);
        TimePointRec t24 = model.getTimePointRec(24);

        assertEquals(601.4766, t1.getGenMap().get("Bus31-G1").getP(), 1.0e-4);
        assertEquals(544.1932, t2.getGenMap().get("Bus31-G1").getP(), 1.0e-4);
        assertEquals(t0.getGenMap().get("Bus31-G1").getP(), t24.getGenMap().get("Bus31-G1").getP(), 1.0e-6);
    }

    @Test
    void loadIeee39WeekFixture_maintenanceWindows() throws Exception {
        PlanMaintainModel model = Aux2PlanMaintainAdapter.createWeekModel(IEEE39_PW_WEEK_DIR);

        EquipmentMaintainRec rec1 = model.getOriginalMaintainEquipemnts().get(0);
        assertEquals("Bus29_to_Bus26_cirId_1", rec1.getName());
        assertEquals(MPlanEquipmentType.Acline, rec1.getEquipType());
        assertEquals(EquipmentMaintainPlanState.Inactive, rec1.getPlanState());
        assertTrue(rec1.isChangeState());
        assertEquals(LocalDateTime.of(2026, 6, 23, 8, 0), rec1.getStartTime());
        assertEquals(LocalDateTime.of(2026, 6, 23, 12, 0), rec1.getEndTime());

        EquipmentMaintainRec rec2 = model.getOriginalMaintainEquipemnts().get(1);
        assertEquals("Bus22_to_Bus23_cirId_1", rec2.getName());
        assertEquals(LocalDateTime.of(2026, 6, 25, 10, 0), rec2.getStartTime());
        assertEquals(LocalDateTime.of(2026, 6, 25, 16, 0), rec2.getEndTime());

        EquipmentMaintainRec rec3 = model.getOriginalMaintainEquipemnts().get(2);
        assertEquals("Bus26_to_Bus25_cirId_1", rec3.getName());
        assertEquals(LocalDateTime.of(2026, 6, 27, 14, 0), rec3.getStartTime());
        assertEquals(LocalDateTime.of(2026, 6, 27, 18, 0), rec3.getEndTime());
    }

    @Test
    void scheduleAuxParser_extractsSubdataSchedules() throws Exception {
        AuxTssParsedData parsed = new AuxTssScheduleAuxParser().parse(
                IEEE39_PW_WEEK_DIR.resolve("ieee39_week_schedules.aux"));

        assertEquals(32, parsed.schedules().size());
        assertEquals(32, parsed.subscriptions().size());

        AuxTssSchedule genSchedule = parsed.schedules().stream()
                .filter(s -> "Sched_Gen_Bus31-G1".equals(s.scheduleName()))
                .findFirst()
                .orElseThrow();
        assertNotNull(genSchedule);
        assertTrue(genSchedule.isNumeric());
        assertEquals(4, genSchedule.points().size());
        assertEquals(572.8349, genSchedule.points().get(0).nValue(), 1.0e-4);

        AuxTssSchedule maintSchedule = parsed.schedules().stream()
                .filter(s -> "Sched_Maint_Bus22_to_Bus23_cirId_1".equals(s.scheduleName()))
                .findFirst()
                .orElseThrow();
        assertTrue(maintSchedule.isBoolean());
        assertEquals(3, maintSchedule.points().size());
    }
}
