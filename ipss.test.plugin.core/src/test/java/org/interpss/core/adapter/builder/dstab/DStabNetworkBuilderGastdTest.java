package org.interpss.core.adapter.builder.dstab;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;

import org.interpss.CorePluginTestSetup;
import org.interpss.dstab.control.gov.psse.gast.PsseGASTGasTurGovernor;
import org.interpss.fadapter.builder.DStabNetworkBuilder;
import org.interpss.fadapter.psse.PSSEDStabDirectParser;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.interpss.dstab.mach.Machine;

class DStabNetworkBuilderGastdTest extends CorePluginTestSetup {
    private static final double TOL = 1.0e-9;

    @TempDir Path tempDir;

    @Test
    void loadsAliasNormalizesLimitsAndInitializesOnTurbineBase() throws Exception {
        DStabNetworkBuilder builder = DStabBuilderTestFixture.createWithMachine();
        Path dyr = tempDir.resolve("gastdu.dyr");
        Files.writeString(dyr,
                "1 'GASTDU' '1' .05 .4 .1 3 1 2 -.05 1 0 .002 -.003 50 /\n");
        PSSEDStabDirectParser parser = new PSSEDStabDirectParser(builder).setStrictImport(true);

        parser.parseDynFile(dyr.toString());
        Machine machine = builder.getDStabNetwork().getMachine("Bus1-mach1");
        machine.setSpeed(1.0); machine.setPm(.4); machine.setPe(.4);
        PsseGASTGasTurGovernor governor =
                (PsseGASTGasTurGovernor) machine.getGovernor();

        assertNotNull(governor);
        assertEquals("GASTD", governor.getName());
        assertEquals(.002, governor.getData().getDbH(), TOL);
        assertEquals(-.003, governor.getData().getDbL(), TOL);
        assertEquals(50.0, governor.getData().getTrate(), TOL);
        assertTrue(governor.initStates(machine.getDStabBus(), machine));
        assertEquals(.5, governor.ratingScale, TOL);
        assertEquals(1.0, governor.vmax, TOL);
        assertEquals(-.05, governor.vmin, TOL);
        assertEquals(machine.getPm(), governor.getOutput(machine), TOL);
        governor.speedDeadbandBlock.eulerStep1(-.002, 0.0);
        assertEquals(0.0, governor.speedDeadbandBlock.getY(), TOL);
        governor.speedDeadbandBlock.eulerStep1(-.010, 0.0);
        assertEquals(-.007, governor.speedDeadbandBlock.getY(), TOL);
        assertTrue(parser.getLastImportReport().isStrictlyComplete());
    }

    @Test
    void expandsLimitsAndCapsReferenceAtLoadLimit() throws Exception {
        DStabNetworkBuilder builder = DStabBuilderTestFixture.createWithMachine();
        Machine machine = builder.getDStabNetwork().getMachine("Bus1-mach1");
        machine.setSpeed(1.0); machine.setPm(.8); machine.setPe(.8);
        PsseGASTGasTurGovernor governor = builder.addGovGastd("Bus1", "1",
                .05, .4, .1, 3, .6, 2, .5, .1, 0, .002, -.003, 50);

        assertNotNull(governor);
        assertTrue(governor.initStates(machine.getDStabBus(), machine));
        assertEquals(1.6, governor.vmax, TOL);
        assertEquals(.1, governor.vmin, TOL);
        assertEquals(.6, governor.getRefPoint(), TOL);
        assertEquals(machine.getPm(), governor.getOutput(machine), TOL);
    }

    @Test
    void rejectsInvalidExtendedParameters() throws Exception {
        DStabNetworkBuilder builder = DStabBuilderTestFixture.createWithMachine();
        assertNull(builder.addGovGastd("Bus1", "1",
                0, .4, .1, 3, 1, 2, 1, 0, 0, .002, -.003, 50));
        assertNull(builder.addGovGastd("Bus1", "1",
                .05, .4, .1, 3, 1, 2, 1, 0, 0, -.002, -.003, 50));
        assertNull(builder.addGovGastd("Bus1", "1",
                .05, .4, .1, 3, 1, 2, 1, 0, 0, .002, .003, 50));
        assertNull(builder.addGovGastd("Bus1", "1",
                .05, .4, .1, 3, 1, 2, 1, 0, 0, .002, -.003, -1));
    }
}
