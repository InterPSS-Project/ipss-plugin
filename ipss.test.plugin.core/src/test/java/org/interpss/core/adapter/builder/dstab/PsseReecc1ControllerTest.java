package org.interpss.core.adapter.builder.dstab;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;

import org.interpss.dstab.renewable.Reecc1Model;
import org.interpss.dstab.renewable.Regca1Model;
import org.interpss.fadapter.builder.DStabNetworkBuilder;
import org.interpss.fadapter.psse.PSSEDStabDirectParser;
import org.interpss.fadapter.psse.dyr.DynamicModelCatalog;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.interpss.dstab.DStabGen;

/** Exact flat/wrapper schema and attachment checks for REECC1. */
public class PsseReecc1ControllerTest {
    private static final String REGCA =
            "1 'REGCA1' '1' 1 .02 10 .9 .5 1.22 1.2 .8 .4 -1.3 .02 .7 0 0 .8 /\n";
    private static final String PARAMETERS =
            "0 1 1 1 1 .88 1.12 .023 -.04 .03 .55 .91 -.87 0 .031 "
            + ".92 -.89 1.08 -.95 .12 .18 1.15 4.7 .019 1.8 -1.6 .96 -.88 "
            + "1.17 .037 .2 .75 1.3 1.05 0 0 0 0 .2 .72 1.3 1.08 0 0 0 0 "
            + "13750 .64 .92 .14";

    @Test
    void flatAndWrappedFormsAttachTheSameSevenStateController(@TempDir Path tempDir)
            throws Exception {
        Reecc1Model flat = parse(tempDir.resolve("flat.dyr"),
                REGCA + "1 'REECC1' '1' " + PARAMETERS + " /\n");
        Reecc1Model wrapped = parse(tempDir.resolve("wrapped.dyr"),
                REGCA + "1 'USRMDL' '1' 'REECCU1' 102 0 5 45 7 6 "
                        + PARAMETERS + " /\n");

        assertEquals(flat.getData(), wrapped.getData());
        assertEquals(7, flat.getNamedStates().size());
        assertEquals(.64, flat.getStateOfCharge(), 0.0);
        var descriptor = DynamicModelCatalog.find("REECCU1").orElseThrow();
        assertEquals("REECC1", descriptor.canonicalName());
        assertEquals(Set.of(50, 56), descriptor.recordSchema().acceptedParameterCounts());
    }

    @Test
    void rejectsAnIncorrectNativeAllocation(@TempDir Path tempDir) throws Exception {
        DStabNetworkBuilder builder = DStabBuilderTestFixture.createBuilder();
        Path dyr = tempDir.resolve("wrong.dyr");
        Files.writeString(dyr, REGCA
                + "1 'USRMDL' '1' 'REECCU1' 102 0 5 45 6 6 " + PARAMETERS + " /\n");
        PSSEDStabDirectParser parser = new PSSEDStabDirectParser(builder);
        parser.parseDynFile(dyr.toString());

        assertNull(controller(builder));
        assertTrue(!parser.getLastImportReport().isStrictlyComplete());
    }

    private static Reecc1Model parse(Path dyr, String contents) throws Exception {
        DStabNetworkBuilder builder = DStabBuilderTestFixture.createBuilder();
        Files.writeString(dyr, contents);
        PSSEDStabDirectParser parser = new PSSEDStabDirectParser(builder).setStrictImport(true);
        parser.parseDynFile(dyr.toString());
        assertTrue(parser.getLastImportReport().isStrictlyComplete());
        Reecc1Model model = assertInstanceOf(Reecc1Model.class, controller(builder));
        model.initialize(.40, .10, 1.0);
        return model;
    }

    private static Object controller(DStabNetworkBuilder builder) {
        DStabGen gen = (DStabGen) builder.getDStabNetwork().getDStabBus("Bus1")
                .getContributeGen("1");
        return ((Regca1Model) gen.getDynamicGenDevice()).getActiveElectricalController();
    }
}
