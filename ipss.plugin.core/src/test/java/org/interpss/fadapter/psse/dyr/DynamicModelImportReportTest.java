package org.interpss.fadapter.psse.dyr;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.StringReader;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.google.gson.JsonParser;

class DynamicModelImportReportTest {
    @Test
    void reportsExactSourceAndAttachmentCounts() throws Exception {
        List<PsseDyrRecord> records = PsseDyrRecordReader.read(new StringReader(
                "1 'GENROU' 1 1 /\n2 'GGOV1' 1 2 /"), "report.dyr");
        DynamicModelImportReport report = DynamicModelImportReport.builder("report.dyr")
                .add(records.get(0), DynamicModelImportStatus.ATTACHED, "")
                .add(records.get(1), DynamicModelImportStatus.UNSUPPORTED, "not implemented")
                .build();

        assertEquals(2, report.totalRecordCount());
        assertEquals(1, report.count(DynamicModelImportStatus.ATTACHED));
        assertEquals(1, report.count(DynamicModelImportStatus.UNSUPPORTED));
        assertEquals(1, report.sourceCountsByModel().get("GENROU"));
        assertEquals(1, report.sourceCountsByModel().get("GGOV1"));
        assertEquals(1, report.attachedCountsByModel().get("GENROU"));
        assertFalse(report.attachedCountsByModel().containsKey("GGOV1"));
        assertFalse(report.isStrictlyComplete());
        assertTrue(report.failureSummary().contains("GGOV1"));
        var json = JsonParser.parseString(report.toJson()).getAsJsonObject();
        assertEquals(2, json.getAsJsonArray("entries").size());
        assertEquals(1, json.getAsJsonObject("countsByStatus").get("UNSUPPORTED").getAsInt());
    }

    @Test
    void completeReportPassesStrictGate() throws Exception {
        PsseDyrRecord record = PsseDyrRecordReader.read(new StringReader(
                "1 'GENROU' 1 1 /"), "complete.dyr").get(0);
        DynamicModelImportReport report = DynamicModelImportReport.builder("complete.dyr")
                .add(record, DynamicModelImportStatus.ATTACHED, "")
                .build();
        assertTrue(report.isStrictlyComplete());
        assertTrue(report.failures().isEmpty());
    }

    @Test
    void strictGateRejectsEveryDegradedDisposition() throws Exception {
        PsseDyrRecord record = PsseDyrRecordReader.read(new StringReader(
                "1 'GENROU' 1 1 /"), "degraded.dyr").get(0);
        for (DynamicModelImportStatus status : DynamicModelImportStatus.values()) {
            DynamicModelImportReport report = DynamicModelImportReport.builder("degraded.dyr")
                    .add(record, status, status.name())
                    .build();
            if (status == DynamicModelImportStatus.ATTACHED
                    || status == DynamicModelImportStatus.SKIPPED_GNET) {
                assertTrue(report.isStrictlyComplete(), status.name());
            } else {
                assertFalse(report.isStrictlyComplete(), status.name());
                assertEquals(1, report.failures().size(), status.name());
            }
        }
    }
}
