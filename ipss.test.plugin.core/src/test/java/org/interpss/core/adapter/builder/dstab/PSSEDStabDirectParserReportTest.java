package org.interpss.core.adapter.builder.dstab;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;

import org.interpss.CorePluginTestSetup;
import org.interpss.dstab.control.exc.ieee.y1981.dc1.IEEE1981DC1Exciter;
import org.interpss.fadapter.builder.DStabNetworkBuilder;
import org.interpss.fadapter.psse.PSSEDStabDirectParser;
import org.interpss.fadapter.psse.dyr.DynamicModelImportStatus;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.interpss.common.exp.InterpssException;

public class PSSEDStabDirectParserReportTest extends CorePluginTestSetup {
    @TempDir
    Path tempDir;

    @Test
    void strictImportPassesWhenEverySourceRecordIsAttached() throws Exception {
        DStabNetworkBuilder builder = DStabBuilderTestFixture.createBuilder();
        Path dyr = tempDir.resolve("complete.dyr");
        Files.writeString(dyr, "1 'GENCLS' '1' 3.0 0.0 /\n");
        PSSEDStabDirectParser parser = new PSSEDStabDirectParser(builder).setStrictImport(true);

        parser.parseDynFile(dyr.toString());

        assertTrue(parser.getLastImportReport().isStrictlyComplete());
        assertEquals(1, parser.getLastImportReport().totalRecordCount());
        assertEquals(1, parser.getLastImportReport().count(DynamicModelImportStatus.ATTACHED));
        assertTrue(parser.getLastImportReport().entries().get(0).source().endsWith("complete.dyr"));
        assertEquals(1, parser.getLastImportReport().entries().get(0).startLine());
    }

    @Test
    void strictImportRejectsInvalidSupportedRecordAndPreservesReport() throws Exception {
        DStabNetworkBuilder builder = DStabBuilderTestFixture.createBuilder();
        Path dyr = tempDir.resolve("unsupported.dyr");
        Files.writeString(dyr, "1 'GGOV1' '1' 0 /\n");
        PSSEDStabDirectParser parser = new PSSEDStabDirectParser(builder).setStrictImport(true);

        InterpssException error = assertThrows(InterpssException.class,
                () -> parser.parseDynFile(dyr.toString()));

        assertTrue(error.getMessage().contains("Strict DYR import failed"));
        assertEquals(1, parser.getLastImportReport().count(DynamicModelImportStatus.REJECTED));
        assertEquals("GGOV1", parser.getLastImportReport().failures().get(0).canonicalModelName());
    }

    @Test
    void strictImportRejectsTruncatedAndUnattachedMachineRecords() throws Exception {
        DStabNetworkBuilder builder = DStabBuilderTestFixture.createBuilder();
        Path dyr = tempDir.resolve("invalid-machines.dyr");
        Files.writeString(dyr, "1 'GENROU' '1' 8.0 /\n"
                + "2 'GENROU' '1' 8.0 0.03 0.4 0.05 5.0 3.0 "
                + "1.8 1.7 0.3 0.55 0.25 0.15 0.10 0.20 /\n");
        PSSEDStabDirectParser parser = new PSSEDStabDirectParser(builder).setStrictImport(true);

        assertThrows(InterpssException.class, () -> parser.parseDynFile(dyr.toString()));

        assertEquals(1, parser.getLastImportReport().count(DynamicModelImportStatus.REJECTED));
        assertEquals(1, parser.getLastImportReport().count(DynamicModelImportStatus.MISSING_TARGET));
        assertTrue(parser.getLastImportReport().failures().get(0).message()
                .contains("expected 14 parameters"));
        assertTrue(parser.getLastImportReport().failures().get(1).message()
                .contains("target bus Bus2 does not exist"));
    }

    @Test
    void unsupportedGentpjIsNeverSilentlyAttachedAsGenrou() throws Exception {
        DStabNetworkBuilder builder = DStabBuilderTestFixture.createBuilder();
        Path dyr = tempDir.resolve("gentpj.dyr");
        Files.writeString(dyr, "1 'GENTPJ' '1' 8.0 0.03 0.4 0.05 5.0 3.0 "
                + "1.8 1.7 0.3 0.55 0.25 0.15 0.10 0.20 /\n");
        PSSEDStabDirectParser parser = new PSSEDStabDirectParser(builder)
                .setStrictImport(true);

        assertThrows(InterpssException.class, () -> parser.parseDynFile(dyr.toString()));

        assertEquals(1, parser.getLastImportReport()
                .count(DynamicModelImportStatus.UNSUPPORTED));
        assertEquals("GENTPJ", parser.getLastImportReport().failures().get(0)
                .canonicalModelName());
        assertTrue(parser.getLastImportReport().failures().get(0).message()
                .contains("not implemented"));
        assertNull(builder.getDStabNetwork().getMachine("Bus1-mach1"));
    }

    @Test
    void malformedNumericParameterIsReportedInsteadOfDefaulted() throws Exception {
        DStabNetworkBuilder builder = DStabBuilderTestFixture.createBuilder();
        Path dyr = tempDir.resolve("malformed-gencls.dyr");
        Files.writeString(dyr, "1 'GENCLS' '1' NOT_A_NUMBER 0.0 /\n");
        PSSEDStabDirectParser parser = new PSSEDStabDirectParser(builder)
                .setStrictImport(true);

        assertThrows(InterpssException.class, () -> parser.parseDynFile(dyr.toString()));

        assertEquals(1, parser.getLastImportReport().count(DynamicModelImportStatus.ERROR));
        assertTrue(parser.getLastImportReport().failures().get(0).message()
                .contains("Invalid floating-point DYR field 4"));
        assertNull(builder.getDStabNetwork().getMachine("Bus1-mach1"));
    }

    @Test
    void partialIeeex1CompatibilityPathIsVisibleAndFailsStrictCoverage() throws Exception {
        DStabNetworkBuilder builder = DStabBuilderTestFixture.createBuilder();
        Path dyr = tempDir.resolve("partial-ieeex1.dyr");
        Files.writeString(dyr, "1 'GENCLS' '1' 3.0 0.0 /\n"
                + "1 'IEEEX1' '1' 0.02 40.0 0.02 0.0 0.0 5.0 -5.0 "
                + "1.0 0.6 0.03 0.35 0 2.8 0.1 3.7 0.33 /\n");
        PSSEDStabDirectParser parser = new PSSEDStabDirectParser(builder)
                .setStrictImport(true);

        assertThrows(InterpssException.class, () -> parser.parseDynFile(dyr.toString()));

        assertEquals(1, parser.getLastImportReport().count(DynamicModelImportStatus.ATTACHED));
        assertEquals(1, parser.getLastImportReport().count(DynamicModelImportStatus.FALLBACK));
        var fallback = parser.getLastImportReport().failures().get(0);
        assertEquals("IEEEX1", fallback.canonicalModelName());
        assertTrue(fallback.message().contains("partial"));
        assertEquals(IEEE1981DC1Exciter.class.getName(), fallback.runtimeClassName());
        assertTrue(builder.getDStabNetwork().getMachine("Bus1-mach1").getExciter()
                instanceof IEEE1981DC1Exciter);
    }
}
