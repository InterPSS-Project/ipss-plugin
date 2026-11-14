package org.interpss.core.adapter.builder.dstab;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;

import com.interpss.dstab.controller.pss.ieee2016pss6c.Ieee2016PSS6CStabilizer;
import com.interpss.dstab.controller.pss.ieee2016pss7c.Ieee2016PSS7CStabilizer;
import org.interpss.fadapter.psse.PSSEDStabDirectParser;
import org.interpss.fadapter.psse.dyr.DynamicModelCatalog;
import org.interpss.fadapter.psse.dyr.DynamicModelImportStatus;
import org.interpss.fadapter.psse.dyr.PsseDyrRecordReader;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import com.interpss.common.exp.InterpssException;

/** Exact native layouts must not infer another simulator's power filter. */
public class PssCanonicalNativeSchemaTest {
    private static final Path CASE = Path.of("testData/adpter/psse/v33/SMIB");

    private static List<String> parameters(String model) throws Exception {
        var record = PsseDyrRecordReader.read(CASE.resolve(
                "SMIB_v33_genrou_esst1a_" + model.toLowerCase(java.util.Locale.ROOT) + ".dyr"))
                .stream().filter(r -> r.canonicalModelName().equals(model)).findFirst().orElseThrow();
        return record.fields().subList(3, record.fields().size());
    }

    @Test void catalogAdmitsOnlyTheExactNativeWidths() {
        for (String model : List.of("PSS6C", "PSS7C")) {
            int width = model.equals("PSS6C") ? 34 : 38;
            var schema = DynamicModelCatalog.find(model).orElseThrow().recordSchema();
            assertEquals(Set.of(width), schema.acceptedParameterCounts(), model);
        }
    }

    @Test void strictAndLenientImportsRejectTheExtraFilterWithoutAttaching(@TempDir Path dir) throws Exception {
        for (String model : List.of("PSS6C", "PSS7C")) {
            var fields = new java.util.ArrayList<>(parameters(model));
            fields.add(fields.size() - 2, "0.123");
            Path input = dir.resolve(model + ".dyr");
            Files.writeString(input, "1 '" + model + "' '1' " + String.join(" ", fields) + " /\n");
            for (boolean strict : new boolean[]{false, true}) {
                var builder = DStabBuilderTestFixture.createWithMachine();
                var parser = new PSSEDStabDirectParser(builder).setStrictImport(strict);
                if (strict) assertThrows(InterpssException.class, () -> parser.parseDynFile(input.toString()));
                else parser.parseDynFile(input.toString());
                assertNull(builder.getDStabNetwork().getMachine("Bus1-mach1").getStabilizer(), model);
                var report = parser.getLastImportReport();
                assertFalse(report.isStrictlyComplete());
                assertEquals(1, report.count(DynamicModelImportStatus.REJECTED));
            }
        }
    }

    @Test void nativeTailMapsToCompensationWithNoInventedPowerFilter(@TempDir Path dir) throws Exception {
        for (String model : List.of("PSS6C", "PSS7C")) {
            var fields = new java.util.ArrayList<>(parameters(model));
            fields.set(fields.size() - 2, "0.217"); fields.set(fields.size() - 1, "0.043");
            Path input = dir.resolve(model + ".dyr");
            Files.writeString(input, "1 '" + model + "' '1' " + String.join(" ", fields) + " /\n");
            var builder = DStabBuilderTestFixture.createWithMachine();
            var parser = new PSSEDStabDirectParser(builder).setStrictImport(true);
            parser.parseDynFile(input.toString());
            var stabilizer = builder.getDStabNetwork().getMachine("Bus1-mach1").getStabilizer();
            double xcomp, tcomp, filter;
            if (stabilizer instanceof Ieee2016PSS6CStabilizer pss) {
                xcomp = pss.getData().xcomp(); tcomp = pss.getData().tcomp(); filter = pss.getData().tpgfilt();
            } else {
                var pss = (Ieee2016PSS7CStabilizer) stabilizer;
                xcomp = pss.getData().xcomp(); tcomp = pss.getData().tcomp(); filter = pss.getData().tpgfilt();
            }
            assertEquals(.217, xcomp); assertEquals(.043, tcomp); assertEquals(0, filter);
            assertTrue(parser.getLastImportReport().isStrictlyComplete());
        }
    }
}
