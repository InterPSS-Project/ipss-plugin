package org.interpss.core.adapter.builder.dstab;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;

import org.interpss.CorePluginTestSetup;
import org.interpss.dstab.control.gov.ieee.hydro1981Type3.Ieee1981Type3HydroGovernor;
import org.interpss.fadapter.builder.DStabNetworkBuilder;
import org.interpss.fadapter.psse.PSSEDStabDirectParser;
import org.interpss.fadapter.psse.dyr.DynamicModelImportStatus;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.interpss.common.exp.InterpssException;
import com.interpss.dstab.algo.DynamicSimuMethod;
import com.interpss.dstab.mach.Machine;

public class DStabNetworkBuilderIeeeg3dTest extends CorePluginTestSetup {
    private static final double TOL = 1.0e-9;

    @TempDir
    Path tempDir;

    @Test
    void aliasImportsDeadbandAndRatingIntoSharedIeeeg3Runtime() throws Exception {
        DStabNetworkBuilder builder = DStabBuilderTestFixture.createWithMachine();
        Path dyr = tempDir.resolve("ieeeg3d.dyr");
        Files.writeString(dyr,
                "1 'IEEEG3DU' '1' .1 .05 .2 -.2 2 0 .05 .1 1 1 1 0 0 1 .002 -.003 50 /\n");
        PSSEDStabDirectParser parser = new PSSEDStabDirectParser(builder).setStrictImport(true);

        parser.parseDynFile(dyr.toString());

        Machine machine = builder.getDStabNetwork().getMachine("Bus1-mach1");
        machine.setPm(.6);
        machine.setPe(.6);
        machine.setSpeed(1.0);
        Ieee1981Type3HydroGovernor governor =
                (Ieee1981Type3HydroGovernor) machine.getGovernor();
        assertNotNull(governor);
        assertEquals("IEEEG3D", governor.getName());
        assertEquals(.002, governor.getData().getDbH(), TOL);
        assertEquals(-.003, governor.getData().getDbL(), TOL);
        assertEquals(50.0, governor.getData().getTrate(), TOL);

        assertTrue(governor.initStates(machine.getDStabBus(), machine));
        assertEquals(.5, governor.ratingScale, TOL);
        assertEquals(2.0, governor.invRatingScale, TOL);
        assertEquals(1.2, governor.getGatePosition(), TOL);
        assertEquals(.6, governor.getMechanicalPower(), TOL);
        assertTrue(parser.getLastImportReport().isStrictlyComplete());
    }

    @Test
    void frequencyInsideDeadbandHoldsEquilibriumAndOutsideDeadbandResponds()
            throws Exception {
        DStabNetworkBuilder builder = DStabBuilderTestFixture.createWithMachine();
        Machine machine = builder.getDStabNetwork().getMachine("Bus1-mach1");
        machine.setPm(.6);
        machine.setPe(.6);
        Ieee1981Type3HydroGovernor governor = builder.addGovIeeeg3d("Bus1", "1",
                .1, .05, .2, -.2, 2, 0, .05, .1, 1, 1,
                1, 0, 0, 1, .002, -.003, 50);
        assertTrue(governor.initStates(machine.getDStabBus(), machine));

        machine.setSpeed(1.001);
        advance(governor, machine, 2_000, .001);
        assertEquals(.6, governor.getMechanicalPower(), 1.0e-10);

        machine.setSpeed(1.012);
        advance(governor, machine, 2_000, .001);
        assertTrue(governor.getMechanicalPower() < .59,
                "Expected effective +0.010 pu overspeed to close the gate, pm="
                        + governor.getMechanicalPower());
        advance(governor, machine, 38_000, .001);
        // On the 50-MW governor base, Rperm * gate = .06 - .010,
        // so gate = 1.0 and machine-base Pmech = .5 * gate = .5 pu.
        assertEquals(.5, governor.getMechanicalPower(), 2.0e-4);
    }

    @Test
    void asymmetricLowerDeadbandUsesItsOwnContinuousOffset() throws Exception {
        DStabNetworkBuilder builder = DStabBuilderTestFixture.createWithMachine();
        Machine machine = builder.getDStabNetwork().getMachine("Bus1-mach1");
        machine.setPm(.6);
        machine.setPe(.6);
        Ieee1981Type3HydroGovernor governor = builder.addGovIeeeg3d("Bus1", "1",
                .1, .05, .2, -.2, 2, 0, .05, .1, 1, 1,
                1, 0, 0, 1, .002, -.003, 50);
        assertTrue(governor.initStates(machine.getDStabBus(), machine));

        machine.setSpeed(.998);
        advance(governor, machine, 2_000, .001);
        assertEquals(.6, governor.getMechanicalPower(), 1.0e-10);

        machine.setSpeed(.987);
        advance(governor, machine, 2_000, .001);
        assertTrue(governor.getMechanicalPower() > .61,
                "Expected effective -0.010 pu underspeed to open the gate, pm="
                        + governor.getMechanicalPower());
    }

    @Test
    void strictImportRejectsInvalidDeadbandOrdering() throws Exception {
        DStabNetworkBuilder builder = DStabBuilderTestFixture.createWithMachine();
        Path dyr = tempDir.resolve("ieeeg3d-invalid.dyr");
        Files.writeString(dyr,
                "1 'IEEEG3D' '1' .1 .05 .2 -.2 2 0 .05 .1 1 1 1 0 0 1 -.002 .003 50 /\n");
        PSSEDStabDirectParser parser = new PSSEDStabDirectParser(builder).setStrictImport(true);

        assertThrows(InterpssException.class, () -> parser.parseDynFile(dyr.toString()));
        assertEquals(1, parser.getLastImportReport().count(DynamicModelImportStatus.REJECTED));
    }

    private static void advance(Ieee1981Type3HydroGovernor governor, Machine machine,
            int steps, double dt) {
        for (int i = 0; i < steps; i++) {
            assertTrue(governor.nextStep(dt, DynamicSimuMethod.MODIFIED_EULER, machine, 0));
            assertTrue(governor.nextStep(dt, DynamicSimuMethod.MODIFIED_EULER, machine, 1));
        }
    }
}
