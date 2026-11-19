package org.interpss.core.adapter.builder.dstab;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import org.interpss.CorePluginTestSetup;
import com.interpss.dstab.renewable.Regca1Model;
import com.interpss.dstab.renewable.Wtdta1Data;
import com.interpss.dstab.renewable.Wtdtb1Data;
import com.interpss.dstab.renewable.Wtdtb1Model;
import org.interpss.fadapter.builder.DStabNetworkBuilder;
import org.interpss.fadapter.psse.PSSEDStabDirectParser;
import org.interpss.fadapter.psse.PsseGnetIdvProcessor.GeneratorKey;
import org.interpss.fadapter.psse.dyr.DynamicModelCatalog;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import com.interpss.common.exp.InterpssException;
import com.interpss.dstab.DStabGen;

public class PsseWtdtb1ModelTest extends CorePluginTestSetup {
    private static final Wtdtb1Data DATA = new Wtdtb1Data(6.4, .7, .42, 1.7, .31, .123);
    private static final String STANDARD = "1 'WTDTB1' '1' 6.4 .7 .42 1.7 .31 .123 /\n";
    private static final String LEGACY = "1 'USRMDL' '1' 'WTDTBU1' 103 0 0 6 5 3 6.4 .7 .42 1.7 .31 .123 /\n";

    @Test void strictStandardRecordCanPrecedeItsConverterHost(@TempDir Path directory) throws Exception {
        var builder = DStabBuilderTestFixture.createBuilder();
        builder.getDStabNetwork().setFrequency(50);
        var parser = parse(builder, directory, STANDARD + host());
        assertTrue(parser.getLastImportReport().isStrictlyComplete());
        assertEquals(3, parser.getLastImportReport().totalRecordCount());
        var model = model(builder);
        assertNotNull(model);
        assertEquals(DATA, model.getData());
        assertEquals(100 * Math.PI, model.angularFrequency(), 1e-12);
        assertEquals(Wtdtb1Model.EquationConvention.PSSE_36_7, model.getEquationConvention());
        assertEquals("WTDTB1", DynamicModelCatalog.canonicalName("WTDTBU1"));
    }

    @Test void exactLegacyWrapperAndStandardRecordPreserveIdenticalParameters(@TempDir Path directory) throws Exception {
        var builder = DStabBuilderTestFixture.createBuilder();
        var parser = parse(builder, directory, host() + LEGACY);
        assertTrue(parser.getLastImportReport().isStrictlyComplete());
        assertEquals(DATA, model(builder).getData());
        assertEquals(Wtdtb1Model.EquationConvention.PSSE_36_7, model(builder).getEquationConvention());
    }

    @Test void malformedOrUnprovenLayoutsDoNotAttach(@TempDir Path directory) throws Exception {
        List<String> invalid = List.of(
                "1 'WTDTB1' 1 6.4 .7 .42 1.7 .31 /",
                "1 'WTDTB1' 1 6.4 .7 .42 1.7 .31 .123 99 /",
                "1 'WTDTB1' 1 2.7 1.8 .4 341 100 1 .1 1.5 /",
                "1 'WTDTBU1' 1 6.4 .7 .42 1.7 .31 .123 /",
                LEGACY.replace("103 0 0 6 5 3", "15 0 0 6 5 3"),
                LEGACY.replace("103 0 0 6 5 3", "103 1 0 6 5 3"),
                LEGACY.replace("103 0 0 6 5 3", "103 0 1 6 5 3"),
                LEGACY.replace("103 0 0 6 5 3", "103 0 0 5 5 3"),
                LEGACY.replace("103 0 0 6 5 3", "103 0 0 6 4 3"),
                LEGACY.replace("103 0 0 6 5 3", "103 0 0 6 5 2"),
                LEGACY.replace("'USRMDL'", "'USRLOD'"),
                LEGACY.replace("'WTDTBU1'", "'WTDTB1'"),
                "1 'USRLOD' 1 'WTDTB1' 6.4 .7 .42 1.7 .31 .123 /",
                STANDARD.replace("6.4 .7", "NaN .7"),
                STANDARD.replace(".31 .123", ".31 -1"),
                STANDARD.replace(".42 1.7", "1 1.7"));
        for (int index = 0; index < invalid.size(); index++) {
            var builder = DStabBuilderTestFixture.createBuilder();
            var parser = new PSSEDStabDirectParser(builder).setIgnoreMissingHosts(false).setStrictImport(true);
            Path file = directory.resolve("invalid-" + index + ".dyr");
            Files.writeString(file, host() + invalid.get(index));
            assertThrows(InterpssException.class, () -> parser.parseDynFile(file.toString()), invalid.get(index));
            assertFalse(parser.getLastImportReport().isStrictlyComplete());
            assertNull(model(builder), invalid.get(index));
        }
    }

    @Test void duplicateRecordIsRejectedWithoutReplacingTheFirstModel(@TempDir Path directory) throws Exception {
        var builder = DStabBuilderTestFixture.createBuilder();
        var parser = new PSSEDStabDirectParser(builder).setIgnoreMissingHosts(false).setStrictImport(true);
        Path file = directory.resolve("duplicate.dyr");
        Files.writeString(file, host() + STANDARD + STANDARD.replace("6.4 .7", "9.9 .7"));
        assertThrows(InterpssException.class, () -> parser.parseDynFile(file.toString()));
        assertEquals(4, parser.getLastImportReport().totalRecordCount());
        assertEquals(DATA, model(builder).getData());
    }

    @Test void builderDoesNotReplaceEitherExistingDriveTrain(@TempDir Path directory) throws Exception {
        var builder = DStabBuilderTestFixture.createBuilder();
        parse(builder, directory, host() + STANDARD);
        var first = model(builder);
        assertNull(builder.addWtdtb1("Bus1", "1", DATA));
        assertNull(builder.addWtdta1("Bus1", "1", new Wtdta1Data(4, .1, .5, .02, .2)));
        assertSame(first, model(builder));
        var legacy = DStabBuilderTestFixture.createBuilder();
        parse(legacy, directory, host() + "1 'WTDTA1' 1 4 .1 .5 .02 .2 /\n");
        assertNull(legacy.addWtdtb1("Bus1", "1", DATA));
        assertNull(model(legacy));
    }

    @Test void compatibleElectricalControllerAndStaticTargetAreRequired(@TempDir Path directory) throws Exception {
        for (String text : List.of(STANDARD, host() + STANDARD.replace("1 'WTDTB1'", "9 'WTDTB1'"),
                host() + STANDARD.replace("'WTDTB1' '1'", "'WTDTB1' '9'"))) {
            var builder = DStabBuilderTestFixture.createBuilder();
            var parser = new PSSEDStabDirectParser(builder).setIgnoreMissingHosts(false).setStrictImport(true);
            Path file = directory.resolve("missing.dyr");
            Files.writeString(file, text);
            assertThrows(InterpssException.class, () -> parser.parseDynFile(file.toString()));
            assertFalse(parser.getLastImportReport().isStrictlyComplete());
            assertNull(model(builder));
        }
        var builder = DStabBuilderTestFixture.createBuilder();
        assertNull(builder.addWtdtb1("Bus1", "1", DATA));
        assertNull(builder.addWtdtb1("Bus9", "1", DATA));
    }

    @Test void gnetAndModelRemovalSkipTheWholeTargetStack(@TempDir Path directory) throws Exception {
        for (boolean gnet : new boolean[] {true, false}) {
            var builder = DStabBuilderTestFixture.createBuilder();
            var parser = new PSSEDStabDirectParser(builder).setIgnoreMissingHosts(false).setStrictImport(true);
            var keys = Set.of(new GeneratorKey("Bus1", "1"));
            if (gnet) parser.setGnetRemovedGenerators(keys);
            else parser.setModelRemovedGenerators(keys);
            Path file = directory.resolve("removed.dyr");
            Files.writeString(file, host() + STANDARD);
            parser.parseDynFile(file.toString());
            assertTrue(parser.getLastImportReport().isStrictlyComplete());
            assertEquals(3, parser.getLastImportReport().totalRecordCount());
            assertNull(model(builder));
        }
    }

    private static PSSEDStabDirectParser parse(DStabNetworkBuilder builder, Path directory, String text) throws Exception {
        Path file = directory.resolve("model.dyr");
        Files.writeString(file, text);
        var parser = new PSSEDStabDirectParser(builder).setIgnoreMissingHosts(false).setStrictImport(true);
        parser.parseDynFile(file.toString());
        return parser;
    }
    private static String host() throws Exception {
        var lines = Files.readAllLines(Path.of("testData/adpter/psse/v33/renewable/regca_reeca_repca_bus1062.dyr"));
        return lines.get(0) + "\n" + lines.get(1) + "\n";
    }
    private static Wtdtb1Model model(DStabNetworkBuilder builder) {
        var generator = (DStabGen)builder.getDStabNetwork().getDStabBus("Bus1").getContributeGen("1");
        if (!(generator.getDynamicGenDevice() instanceof Regca1Model converter)
                || converter.getReeca1Controller() == null
                || converter.getReeca1Controller().getWindControlStack() == null) return null;
        return converter.getReeca1Controller().getWindControlStack().getTorsionalEmulation();
    }
}
