package org.interpss.fstate;

import java.nio.file.Path;
import java.time.LocalDateTime;

import org.interpss.plugin.fstate.aux_fmt.Aux2PlanMaintainAdapter;
import org.interpss.plugin.fstate.aux_fmt.bean.AuxTssParsedData;
import org.interpss.plugin.fstate.aux_fmt.bean.AuxTssSchedule;
import org.interpss.plugin.fstate.aux_fmt.parser.AuxTssScheduleAuxParser;

import com.interpss.algo.fstate.plan.model.PlanMaintainModel;
import com.interpss.algo.fstate.plan.model.bean.EquipmentMaintainRec;
import com.interpss.algo.fstate.plan.model.bean.TimePointRec;
import com.interpss.algo.fstate.plan.model.type.EquipmentMaintainPlanState;
import com.interpss.algo.fstate.plan.model.type.FSPlanMaintainModelType;

/**
 * Loads IEEE39 day-ahead plan/maintain data from PowerWorld TSS text artifacts
 * ({@code *_schedules.aux}, {@code *_timepoints.csv}, optional {@code *_outages.csv})
 * into a {@link PlanMaintainModel}.
 *
 * <p>Mirror of {@code PowerWorld2PlanMaintainAdapterTest}.</p>
 */
public class PowerWorld2PlanMaintainAdapterSample {

    private static final Path IEEE39_PW_DIR = Path.of("ipss.plugin.core/testData/powerworld/ieee39/dayahead");

    public static void main(String[] args) throws Exception {
        PlanMaintainModel model = Aux2PlanMaintainAdapter.createDayAheadModel(IEEE39_PW_DIR);
        printPlanStructure(model);
        printFlatMwCheck(model);
        printMaintenanceWindows(model);
        printScheduleParserSummary();
    }

    private static void printPlanStructure(PlanMaintainModel model) {
        System.out.println("=== Plan structure ===");
        System.out.println("planModelType: " + model.getPlanModelType());
        System.out.println("totalTimePoints: " + model.getNumsOfTotalTimePoints());
        System.out.println("intervalMin: " + model.getTimePointIntervalMin());
        System.out.println("maintainRecords: " + model.getOriginalMaintainEquipemnts().size());

        LocalDateTime planStart = model.getPoint2TimeMap().get(0);
        LocalDateTime planEnd = model.getPoint2TimeMap().get(model.getNumsOfTotalTimePoints() - 1);
        System.out.println("horizon: " + planStart + " .. " + planEnd);

        TimePointRec firstPoint = model.getTimePeriodRecList().get(0).getTimePointRecList().get(0);
        System.out.println("T0 gens: " + firstPoint.getGenMap().size()
                + ", loads: " + firstPoint.getLoadMap().size());
        System.out.printf("T0 Bus31-G1 p=%.4f MW%n", firstPoint.getGenMap().get("Bus31-G1").getP());
        System.out.printf("T0 Bus39-L1 p=%.4f MW%n", firstPoint.getLoadMap().get("Bus39-L1").getP());

        if (model.getPlanModelType() != FSPlanMaintainModelType.DayAhead) {
            throw new IllegalStateException("Expected DayAhead plan type");
        }
        if (model.getNumsOfTotalTimePoints() != 96 || model.getTimePointIntervalMin() != 15) {
            throw new IllegalStateException("Unexpected horizon: points="
                    + model.getNumsOfTotalTimePoints() + ", interval=" + model.getTimePointIntervalMin());
        }
    }

    private static void printFlatMwCheck(PlanMaintainModel model) {
        System.out.println("\n=== MW profile (multi-point SchedPoint export) ===");
        TimePointRec t0 = model.getTimePeriodRecList().get(0).getTimePointRecList().get(0);
        TimePointRec t1 = model.getTimePeriodRecList().get(0).getTimePointRecList().get(1);
        TimePointRec t2 = model.getTimePeriodRecList().get(0).getTimePointRecList().get(2);
        TimePointRec t3 = model.getTimePeriodRecList().get(0).getTimePointRecList().get(3);

        double genT0 = t0.getGenMap().get("Bus31-G1").getP();
        double genT1 = t1.getGenMap().get("Bus31-G1").getP();
        double genT2 = t2.getGenMap().get("Bus31-G1").getP();
        double genT3 = t3.getGenMap().get("Bus31-G1").getP();

        System.out.printf("Bus31-G1: T0=%.4f T1=%.4f T2=%.4f T3=%.4f%n", genT0, genT1, genT2, genT3);
    }

    private static void printMaintenanceWindows(PlanMaintainModel model) {
        System.out.println("\n=== Maintenance windows ===");
        for (EquipmentMaintainRec rec : model.getOriginalMaintainEquipemnts()) {
            System.out.printf("%s [%s] %s -> %s planState=%s changeState=%s%n",
                    rec.getName(),
                    rec.getEquipType(),
                    rec.getStartTime(),
                    rec.getEndTime(),
                    rec.getPlanState(),
                    rec.isChangeState());
            if (rec.getPlanState() != EquipmentMaintainPlanState.Inactive || !rec.isChangeState()) {
                throw new IllegalStateException("Unexpected maintenance record: " + rec);
            }
        }
    }

    private static void printScheduleParserSummary() throws Exception {
        System.out.println("\n=== Schedule AUX parser ===");
        AuxTssParsedData parsed = new AuxTssScheduleAuxParser().parse(
                IEEE39_PW_DIR.resolve("ieee39_dayahead_schedules.aux"));
        System.out.println("schedules: " + parsed.schedules().size());
        System.out.println("subscriptions: " + parsed.subscriptions().size());

        AuxTssSchedule genSchedule = parsed.schedules().stream()
                .filter(s -> "Sched_Gen_Bus31-G1".equals(s.scheduleName()))
                .findFirst()
                .orElseThrow();
        System.out.printf("Sched_Gen_Bus31-G1: numeric=%s points=%d p0=%.4f%n",
                genSchedule.isNumeric(),
                genSchedule.points().size(),
                genSchedule.points().get(0).nValue());

        AuxTssSchedule maintSchedule = parsed.schedules().stream()
                .filter(s -> "Sched_Maint_Bus29_to_Bus26_cirId_1".equals(s.scheduleName()))
                .findFirst()
                .orElseThrow();
        System.out.printf("Sched_Maint_Bus29_to_Bus26_cirId_1: boolean=%s points=%d%n",
                maintSchedule.isBoolean(),
                maintSchedule.points().size());
    }
}
