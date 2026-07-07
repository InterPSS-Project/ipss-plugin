package org.interpss.plugin.fstate.aux_fmt.mapper;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import org.interpss.plugin.fstate.aux_fmt.bean.AuxSchedPoint;
import org.interpss.plugin.fstate.aux_fmt.bean.AuxTssSchedule;

/**
 * Step-hold evaluation of PowerWorld TSS schedules at a given timestamp.
 */
public final class AuxScheduleEvaluator {

    private AuxScheduleEvaluator() {
    }

    public static double evaluateNumeric(AuxTssSchedule schedule, LocalDateTime time) {
        AuxSchedPoint point = lastPointAtOrBefore(schedule.points(), time);
        if (point == null) {
            return 0.0;
        }
        return point.nValue();
    }

    public static boolean evaluateClosed(AuxTssSchedule schedule, LocalDateTime time) {
        AuxSchedPoint point = lastPointAtOrBefore(schedule.points(), time);
        if (point == null) {
            return true;
        }
        return !"OPEN".equalsIgnoreCase(point.bValue());
    }

    public static List<OpenInterval> findOpenIntervals(AuxTssSchedule schedule) {
        List<AuxSchedPoint> sorted = new ArrayList<>(schedule.points());
        sorted.sort(Comparator.comparing(AuxSchedPoint::time));

        List<OpenInterval> intervals = new ArrayList<>();
        LocalDateTime openStart = null;
        for (AuxSchedPoint point : sorted) {
            boolean open = "OPEN".equalsIgnoreCase(point.bValue());
            if (open && openStart == null) {
                openStart = point.time();
            } else if (!open && openStart != null) {
                intervals.add(new OpenInterval(openStart, point.time()));
                openStart = null;
            }
        }
        return intervals;
    }

    private static AuxSchedPoint lastPointAtOrBefore(List<AuxSchedPoint> points, LocalDateTime time) {
        if (points == null || points.isEmpty()) {
            return null;
        }
        AuxSchedPoint selected = null;
        for (AuxSchedPoint point : points) {
            if (!point.time().isAfter(time)) {
                if (selected == null || point.time().isAfter(selected.time())) {
                    selected = point;
                }
            }
        }
        if (selected == null) {
            return points.stream().min(Comparator.comparing(AuxSchedPoint::time)).orElse(null);
        }
        return selected;
    }

    public record OpenInterval(LocalDateTime start, LocalDateTime end) {
    }
}
