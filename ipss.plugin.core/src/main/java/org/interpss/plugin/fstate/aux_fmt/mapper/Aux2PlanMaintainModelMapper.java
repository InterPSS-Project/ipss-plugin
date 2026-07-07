package org.interpss.plugin.fstate.aux_fmt.mapper;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import org.interpss.plugin.fstate.aux_fmt.bean.AuxOutageRecord;
import org.interpss.plugin.fstate.aux_fmt.bean.AuxTimepointsHorizon;
import org.interpss.plugin.fstate.aux_fmt.bean.AuxTssParsedData;
import org.interpss.plugin.fstate.aux_fmt.bean.AuxTssSchedule;
import org.interpss.plugin.fstate.aux_fmt.bean.AuxTssScheduleSub;
import org.interpss.plugin.fstate.aux_fmt.mapper.AuxScheduleEvaluator.OpenInterval;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.interpss.algo.fstate.plan.model.bean.EquipmentMaintainRec;
import com.interpss.algo.fstate.plan.model.bean.TPointPowerRec;
import com.interpss.algo.fstate.plan.model.bean.TimePointRec;
import com.interpss.algo.fstate.plan.model.type.EquipmentMaintainPlanState;
import com.interpss.algo.fstate.plan.model.type.EquipmentMaintainPlanType;
import com.interpss.algo.fstate.plan.model.type.MPlanEquipmentType;

/**
 * Maps parsed PowerWorld TSS data to {@link PlanMaintainModel} building blocks.
 */
public class Aux2PlanMaintainModelMapper {

    private static final Logger log = LoggerFactory.getLogger(Aux2PlanMaintainModelMapper.class);

    private static final String GEN_PREFIX = "Sched_Gen_";
    private static final String LOAD_PREFIX = "Sched_Load_";
    private static final String MAINT_PREFIX = "Sched_Maint_";

    public TimePointRec[] mapTimePoints(AuxTssParsedData parsed, AuxTimepointsHorizon horizon) {
        Map<String, AuxTssSchedule> genSchedules = new LinkedHashMap<>();
        Map<String, AuxTssSchedule> loadSchedules = new LinkedHashMap<>();

        for (AuxTssSchedule schedule : parsed.schedules()) {
            String name = schedule.scheduleName();
            if (name.startsWith(GEN_PREFIX)) {
                genSchedules.put(name.substring(GEN_PREFIX.length()), schedule);
            } else if (name.startsWith(LOAD_PREFIX)) {
                loadSchedules.put(name.substring(LOAD_PREFIX.length()), schedule);
            }
        }

        validateSubscriptions(parsed, genSchedules, loadSchedules);

        TimePointRec[] timePoints = new TimePointRec[horizon.count()];
        for (int i = 0; i < horizon.count(); i++) {
            TimePointRec rec = new TimePointRec();
            rec.setTimePoint(i);
            rec.setTimePointName(String.format(Locale.ROOT, "T%02d", i));

            Map<String, TPointPowerRec> genMap = new HashMap<>();
            for (Map.Entry<String, AuxTssSchedule> entry : genSchedules.entrySet()) {
                TPointPowerRec power = new TPointPowerRec();
                power.setStatus(true);
                power.setP(AuxScheduleEvaluator.evaluateNumeric(entry.getValue(), horizon.times().get(i)));
                power.setDp(0.0);
                genMap.put(entry.getKey(), power);
            }
            rec.setGenMap(genMap);

            Map<String, TPointPowerRec> loadMap = new HashMap<>();
            for (Map.Entry<String, AuxTssSchedule> entry : loadSchedules.entrySet()) {
                TPointPowerRec power = new TPointPowerRec();
                power.setStatus(true);
                power.setP(AuxScheduleEvaluator.evaluateNumeric(entry.getValue(), horizon.times().get(i)));
                power.setDp(0.0);
                loadMap.put(entry.getKey(), power);
            }
            rec.setLoadMap(loadMap);

            timePoints[i] = rec;
        }
        return timePoints;
    }

    public List<EquipmentMaintainRec> mapMaintenance(AuxTssParsedData parsed, List<AuxOutageRecord> outages) {
        List<EquipmentMaintainRec> fromSchedules = new ArrayList<>();
        for (AuxTssSchedule schedule : parsed.schedules()) {
            if (!schedule.scheduleName().startsWith(MAINT_PREFIX) || !schedule.isBoolean()) {
                continue;
            }
            String branchName = schedule.scheduleName().substring(MAINT_PREFIX.length());
            for (OpenInterval interval : AuxScheduleEvaluator.findOpenIntervals(schedule)) {
                fromSchedules.add(buildMaintainRec(branchName, interval.start(), interval.end()));
            }
        }

        if (!fromSchedules.isEmpty()) {
            return fromSchedules;
        }
        if (outages == null || outages.isEmpty()) {
            return List.of();
        }

        List<EquipmentMaintainRec> fromCsv = new ArrayList<>();
        for (AuxOutageRecord outage : outages) {
            fromCsv.add(buildMaintainRec(outage.branchLabel(), outage.startTime(), outage.endTime()));
        }
        return fromCsv;
    }

    private static EquipmentMaintainRec buildMaintainRec(
            String name,
            java.time.LocalDateTime startTime,
            java.time.LocalDateTime endTime) {
        EquipmentMaintainRec rec = new EquipmentMaintainRec();
        rec.setName(name);
        rec.setEquipType(MPlanEquipmentType.Acline);
        rec.setStartTime(startTime);
        rec.setEndTime(endTime);
        rec.setPlanState(EquipmentMaintainPlanState.Inactive);
        rec.setChangeState(true);
        rec.setSimuStatus(true);
        rec.setMaintainType(EquipmentMaintainPlanType.PlannedMaintain);
        return rec;
    }

    private static void validateSubscriptions(
            AuxTssParsedData parsed,
            Map<String, AuxTssSchedule> genSchedules,
            Map<String, AuxTssSchedule> loadSchedules) {
        for (AuxTssScheduleSub sub : parsed.subscriptions()) {
            String scheduleName = sub.scheduleName();
            if (scheduleName.startsWith(GEN_PREFIX)) {
                String device = scheduleName.substring(GEN_PREFIX.length());
                if (!device.equals(sub.objectIdentifier())) {
                    log.warn("TSScheduleSub ObjectIdentifier '{}' does not match schedule device '{}'",
                            sub.objectIdentifier(), device);
                }
                if (!genSchedules.containsKey(device)) {
                    log.warn("TSScheduleSub references missing gen schedule '{}'", scheduleName);
                }
            } else if (scheduleName.startsWith(LOAD_PREFIX)) {
                String device = scheduleName.substring(LOAD_PREFIX.length());
                if (!device.equals(sub.objectIdentifier())) {
                    log.warn("TSScheduleSub ObjectIdentifier '{}' does not match schedule device '{}'",
                            sub.objectIdentifier(), device);
                }
                if (!loadSchedules.containsKey(device)) {
                    log.warn("TSScheduleSub references missing load schedule '{}'", scheduleName);
                }
            }
        }
    }
}
