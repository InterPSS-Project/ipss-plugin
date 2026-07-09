package org.interpss.plugin.fstate.aux_fmt;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;

import org.interpss.plugin.fstate.aux_fmt.bean.AuxOutageRecord;
import org.interpss.plugin.fstate.aux_fmt.bean.AuxTimepointsHorizon;
import org.interpss.plugin.fstate.aux_fmt.bean.AuxTssParsedData;
import org.interpss.plugin.fstate.aux_fmt.mapper.Aux2PlanMaintainModelMapper;
import org.interpss.plugin.fstate.aux_fmt.parser.AuxOutageCsvParser;
import org.interpss.plugin.fstate.aux_fmt.parser.AuxTimepointsCsvParser;
import org.interpss.plugin.fstate.aux_fmt.parser.AuxTssScheduleAuxParser;

import com.interpss.algo.fstate.plan.PlanMaintainModelBuilder;
import com.interpss.algo.fstate.plan.model.PlanMaintainModel;
import com.interpss.algo.fstate.plan.model.bean.EquipmentMaintainRec;
import com.interpss.algo.fstate.plan.model.bean.TimePointRec;
import com.interpss.algo.fstate.plan.model.type.FSPlanMaintainModelType;

/**
 * Loads PowerWorld TSS text artifacts and builds a {@link PlanMaintainModel}.
 */
public final class Aux2PlanMaintainAdapter {

    private static final AuxTssScheduleAuxParser SCHEDULE_PARSER = new AuxTssScheduleAuxParser();
    private static final AuxTimepointsCsvParser TIMEPOINTS_PARSER = new AuxTimepointsCsvParser();
    private static final AuxOutageCsvParser OUTAGE_PARSER = new AuxOutageCsvParser();
    private static final Aux2PlanMaintainModelMapper MAPPER = new Aux2PlanMaintainModelMapper();

    private static final String SFX_DAYAHEAD_SCHEDULES_AUX = "dayahead_schedules.aux";
    private static final String SFX_DAYAHEAD_TIMEPOINTS_CSV = "dayahead_timepoints.csv";
    private static final String SFX_DAYAHEAD_OUTAGES_CSV = "dayahead_outages.csv";

    private static final String SFX_WEEK_SCHEDULES_AUX = "week_schedules.aux";
    private static final String SFX_WEEK_TIMEPOINTS_CSV = "week_timepoints.csv";
    private static final String SFX_WEEK_OUTAGES_CSV = "week_outages.csv";

    private Aux2PlanMaintainAdapter() {
    }

    /**
     * Loads an DayAhead plan from a directory containing {@code *_schedules.aux},
     * {@code *_timepoints.csv}, and optionally {@code *_outages.csv}.
     */
    public static PlanMaintainModel createDayAheadModel(Path directory) throws Exception {
        Path schedulesAux = findSingleFile(directory, SFX_DAYAHEAD_SCHEDULES_AUX);
        Path timepointsCsv = findSingleFile(directory, SFX_DAYAHEAD_TIMEPOINTS_CSV);
        Path outagesCsv = findOptionalFile(directory, SFX_DAYAHEAD_OUTAGES_CSV);
        return load(new AuxTssInput(schedulesAux, timepointsCsv, outagesCsv, FSPlanMaintainModelType.DayAhead, 15));
    }

    /**
     * Loads a Week plan from a directory containing {@code *_week_schedules.aux},
     * {@code *_week_timepoints.csv}, and optionally {@code *_week_outages.csv}.
     */
    public static PlanMaintainModel createWeekModel(Path directory) throws Exception {
        Path schedulesAux = findSingleFile(directory, SFX_WEEK_SCHEDULES_AUX);
        Path timepointsCsv = findSingleFile(directory, SFX_WEEK_TIMEPOINTS_CSV);
        Path outagesCsv = findOptionalFile(directory, SFX_WEEK_OUTAGES_CSV);
        return load(new AuxTssInput(schedulesAux, timepointsCsv, outagesCsv, FSPlanMaintainModelType.Week, 60));
    }

    public static PlanMaintainModel load(AuxTssInput input) throws Exception {
        AuxTssParsedData parsed = SCHEDULE_PARSER.parse(input.schedulesAux());
        AuxTimepointsHorizon horizon = TIMEPOINTS_PARSER.parse(input.timepointsCsv());
        int intervalMin = input.intervalMinutesOverride() != null
                ? input.intervalMinutesOverride()
                : horizon.intervalMin();

        List<AuxOutageRecord> outages = null;
        if (input.outagesCsv() != null && Files.exists(input.outagesCsv())) {
            outages = OUTAGE_PARSER.parse(input.outagesCsv());
        }

        TimePointRec[] timePoints = MAPPER.mapTimePoints(parsed, horizon);
        List<EquipmentMaintainRec> maintainRecs = MAPPER.mapMaintenance(parsed, outages);

        FSPlanMaintainModelType planModelType = input.planModelType() != null
                ? input.planModelType()
                : FSPlanMaintainModelType.DayAhead;

        return new PlanMaintainModelBuilder()
                .numTimePoints(horizon.count())
                .planStartDate(horizon.start())
                .planModelType(planModelType)
                .timePointIntervalMin(intervalMin)
                .equipmentMaintainRecs(maintainRecs)
                .timePoints(timePoints)
                .build();
    }

    private static Path findSingleFile(Path directory, String suffix) throws IOException {
        try (Stream<Path> stream = Files.list(directory)) {
            List<Path> matches = stream
                    .filter(Files::isRegularFile)
                    .filter(path -> path.getFileName().toString().endsWith(suffix))
                    .toList();
            if (matches.size() != 1) {
                throw new IOException("Expected exactly one *" + suffix + " in " + directory + ", found " + matches.size());
            }
            return matches.get(0);
        }
    }

    private static Path findOptionalFile(Path directory, String suffix) throws IOException {
        try (Stream<Path> stream = Files.list(directory)) {
            List<Path> matches = stream
                    .filter(Files::isRegularFile)
                    .filter(path -> path.getFileName().toString().endsWith(suffix))
                    .toList();
            if (matches.isEmpty()) {
                return null;
            }
            if (matches.size() > 1) {
                throw new IOException("Expected at most one *" + suffix + " in " + directory + ", found " + matches.size());
            }
            return matches.get(0);
        }
    }
}
