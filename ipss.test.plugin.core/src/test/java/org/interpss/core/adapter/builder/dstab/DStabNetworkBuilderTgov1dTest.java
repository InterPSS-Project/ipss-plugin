package org.interpss.core.adapter.builder.dstab;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;

import org.interpss.CorePluginTestSetup;
import org.interpss.dstab.control.gov.psse.tgov1.PsseTGov1SteamTurGovernor;
import org.interpss.fadapter.builder.DStabNetworkBuilder;
import org.interpss.fadapter.psse.PSSEDStabDirectParser;
import org.interpss.fadapter.psse.dyr.DynamicModelImportStatus;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.interpss.common.exp.InterpssException;
import com.interpss.dstab.mach.Machine;

class DStabNetworkBuilderTgov1dTest extends CorePluginTestSetup {
    private static final double TOL = 1.0e-9;

    @TempDir
    Path tempDir;

    @Test
    void parseTgov1d_mapsDeadbandRatingAndRuntimeLimits() throws Exception {
        DStabNetworkBuilder builder = DStabBuilderTestFixture.createWithMachine();
        Path dyr = tempDir.resolve("tgov1d.dyr");
        Files.writeString(dyr,
                "1 'TGOV1D' '1' 0.05 0.4 0.1 1.1 1.0 5.0 0.2 0.002 -0.003 50.0 /\n");
        PSSEDStabDirectParser parser = new PSSEDStabDirectParser(builder).setStrictImport(true);

        parser.parseDynFile(dyr.toString());

        Machine machine = builder.getDStabNetwork().getMachine("Bus1-mach1");
        PsseTGov1SteamTurGovernor gov =
                (PsseTGov1SteamTurGovernor) machine.getGovernor();
        assertNotNull(gov);
        assertEquals(0.002, gov.getData().getDbH(), TOL);
        assertEquals(-0.003, gov.getData().getDbL(), TOL);
        assertEquals(50.0, gov.getData().getTrate(), TOL);
        machine.setPm(0.7164);
        machine.setSpeed(1.0);
        assertTrue(gov.initStates(machine.getDStabBus(), machine));
        assertEquals(Math.max(1.1, machine.getPm() / 0.5), gov.vmax, TOL);
        assertEquals(0.1, gov.vmin, TOL);
        assertEquals(0.5, gov.ratingScale, TOL);
        assertEquals(machine.getPm(), gov.getOutput(machine), TOL);

        machine.setSpeed(1.001);
        gov.speedDeadbandBlock.eulerStep1(machine.getSpeed() - 1.0, 0.0);
        assertEquals(0.0, gov.speedDeadbandBlock.getY(), TOL);
        machine.setSpeed(1.01);
        gov.speedDeadbandBlock.eulerStep1(machine.getSpeed() - 1.0, 0.0);
        assertEquals(0.008, gov.speedDeadbandBlock.getY(), TOL);
        machine.setSpeed(0.99);
        gov.speedDeadbandBlock.eulerStep1(machine.getSpeed() - 1.0, 0.0);
        assertEquals(-0.007, gov.speedDeadbandBlock.getY(), TOL);
        assertTrue(parser.getLastImportReport().isStrictlyComplete());
    }

    @Test
    void zeroDeadbandIsIdentityAndAliasLoads() throws Exception {
        DStabNetworkBuilder builder = DStabBuilderTestFixture.createWithMachine();
        Path dyr = tempDir.resolve("tgov1du.dyr");
        Files.writeString(dyr,
                "1 'TGOV1DU' '1' 0.05 0.4 1.0 0.0 1.0 5.0 0.0 0.0 0.0 0.0 /\n");
        PSSEDStabDirectParser parser = new PSSEDStabDirectParser(builder).setStrictImport(true);
        parser.parseDynFile(dyr.toString());

        Machine machine = builder.getDStabNetwork().getMachine("Bus1-mach1");
        PsseTGov1SteamTurGovernor gov =
                (PsseTGov1SteamTurGovernor) machine.getGovernor();
        assertTrue(gov.initStates(machine.getDStabBus(), machine));
        machine.setSpeed(1.0125);
        gov.speedDeadbandBlock.eulerStep1(machine.getSpeed() - 1.0, 0.0);
        assertEquals(0.0125, gov.speedDeadbandBlock.getY(), TOL);
        assertTrue(parser.getLastImportReport().isStrictlyComplete());
    }

    @Test
    void strictImportRejectsInvalidDeadbandOrdering() throws Exception {
        DStabNetworkBuilder builder = DStabBuilderTestFixture.createWithMachine();
        Path dyr = tempDir.resolve("tgov1d-invalid.dyr");
        Files.writeString(dyr,
                "1 'TGOV1D' '1' 0.05 0.4 1.0 0.0 1.0 5.0 0.0 -0.002 0.003 50.0 /\n");
        PSSEDStabDirectParser parser = new PSSEDStabDirectParser(builder).setStrictImport(true);

        assertThrows(InterpssException.class, () -> parser.parseDynFile(dyr.toString()));
        assertEquals(1, parser.getLastImportReport().count(DynamicModelImportStatus.REJECTED));
    }
}
