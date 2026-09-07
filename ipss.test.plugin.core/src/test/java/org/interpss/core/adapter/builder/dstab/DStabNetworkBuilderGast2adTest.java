package org.interpss.core.adapter.builder.dstab;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;

import org.interpss.CorePluginTestSetup;
import org.interpss.dstab.control.gov.psse.gast2a.PsseGast2adGovernor;
import org.interpss.dstab.control.gov.psse.gast2a.PsseGast2adGovernorData;
import org.interpss.fadapter.builder.DStabNetworkBuilder;
import org.interpss.fadapter.psse.PSSEDStabDirectParser;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.interpss.dstab.algo.DynamicSimuMethod;
import com.interpss.dstab.mach.Machine;

/** Equation, initialization, correction, and DYR coverage for GAST2AD. */
public class DStabNetworkBuilderGast2adTest extends CorePluginTestSetup {
    @TempDir
    Path tempDir;

    @Test
    void aliasImportsAllThirtyThreeFieldsAndFlatRuns() throws Exception {
        DStabNetworkBuilder builder = DStabBuilderTestFixture.createWithMachine();
        Path dyr = tempDir.resolve("gast2adu.dyr");
        Files.writeString(dyr,
                "1 'GAST2ADU' '1' 20 .1 .5 1 .02 .1 50 0 1.2 0 .03 1 1 .2 1 .1 .05 .4 .6 .2 .3 1 .1 .1 .1 .05 .9 .8 1 0 1.027777777778 .003 -.004 /\n");

        PSSEDStabDirectParser parser = new PSSEDStabDirectParser(builder)
                .setStrictImport(true);
        parser.parseDynFile(dyr.toString());
        Machine machine = builder.getDStabNetwork().getMachine("Bus1-mach1");
        machine.setPm(.6);
        machine.setPe(.6);
        machine.setSpeed(1.0);
        PsseGast2adGovernor governor = (PsseGast2adGovernor) machine.getGovernor();

        assertNotNull(governor);
        assertEquals(1, governor.getData().getZ());
        assertEquals(50.0, governor.getData().getTrate(), 1e-12);
        assertEquals(.003, governor.getData().getDbH(), 1e-12);
        assertEquals(-.004, governor.getData().getDbL(), 1e-12);
        assertTrue(governor.initStates(machine.getDStabBus(), machine));
        double initial = governor.getOutput(machine);
        advance(governor, machine, 20, .01);
        assertEquals(initial, governor.getOutput(machine), 1e-9);
        assertTrue(parser.getLastImportReport().isStrictlyComplete());
    }

    @Test
    void droopTransferUsesPublishedLeadLagRealization() throws Exception {
        Fixture fixture = fixture(1);
        double initial = fixture.governor.getSpeedCommand();

        fixture.machine.setSpeed(1.013);

        // Deadband leaves +0.010 pu speed error. The direct X/Y path therefore
        // changes by W*(X/Y)*(-0.010) = -0.040 before the lag state moves.
        assertEquals(initial - .04, fixture.governor.getSpeedCommand(), 1e-10);
    }

    @Test
    void isochronousTransferIntegratesFrequencyError() throws Exception {
        Fixture fixture = fixture(0);
        double initialState = fixture.governor.getGovernorState();
        fixture.machine.setSpeed(1.013);

        advance(fixture.governor, fixture.machine, 1, .01);

        // d(state)/dt = W/Y*(-0.010) = -0.4.
        assertEquals(initialState - .004, fixture.governor.getGovernorState(), 1e-10);
        assertTrue(fixture.governor.getSpeedCommand() < initialState);
    }

    @Test
    void deadbandAndLowValueSelectorFollowPublishedDiagram() throws Exception {
        Fixture fixture = fixture(1);
        assertEquals(.007, fixture.governor.applySpeedDeadband(.010), 1e-12);
        assertEquals(-.006, fixture.governor.applySpeedDeadband(-.010), 1e-12);

        fixture.machine.setSpeed(1.02);
        advance(fixture.governor, fixture.machine, 10, .01);

        assertEquals(Math.min(fixture.governor.getSpeedCommand(),
                fixture.governor.getTemperatureCommand()),
                fixture.governor.getLowValueSelect(), 1e-10);
        assertTrue(Double.isFinite(fixture.governor.getOutput(fixture.machine)));
    }

    @Test
    void initializationExpandsLimitsWithoutChangingSourceRecord() throws Exception {
        DStabNetworkBuilder builder = DStabBuilderTestFixture.createWithMachine();
        Machine machine = builder.getDStabNetwork().getMachine("Bus1-mach1");
        machine.setPm(.6);
        machine.setPe(.6);
        machine.setSpeed(1.0);
        PsseGast2adGovernorData data = data(1);
        data.setMaxLimit(.10);
        data.setMinLimit(.05);
        PsseGast2adGovernor governor = builder.addGovGast2ad("Bus1", "1", data);

        assertNotNull(governor);
        assertTrue(governor.initStates(machine.getDStabBus(), machine));
        assertTrue(governor.getEffectiveMaxLimit() > .10);
        assertEquals(.10, data.getMaxLimit(), 1e-12);
        assertEquals(.05, data.getMinLimit(), 1e-12);
    }

    @Test
    void documentedSmallTimeConstantCorrectionsAreRuntimeOnly() throws Exception {
        DStabNetworkBuilder builder = DStabBuilderTestFixture.createWithMachine();
        Machine machine = builder.getDStabNetwork().getMachine("Bus1-mach1");
        machine.setPm(.6);
        machine.setPe(.6);
        machine.setSpeed(1.0);
        PsseGast2adGovernorData data = data(1);
        data.setY(.001);
        data.setT(.001);
        data.setEtd(.001);
        data.setTcd(.015);
        PsseGast2adGovernor governor = builder.addGovGast2ad("Bus1", "1", data);
        assertNotNull(governor);
        governor.configureIntegrationStep(.01, 2.0);

        assertTrue(governor.initStates(machine.getDStabBus(), machine));
        assertEquals(.02, governor.getEffectiveY(), 1e-12);
        assertEquals(0.0, governor.getEffectiveCommandDelay(), 1e-12);
        assertEquals(0.0, governor.getEffectiveExhaustDelay(), 1e-12);
        assertEquals(.02, governor.getEffectiveGasDelay(), 1e-12);
        assertEquals(.001, data.getY(), 1e-12);
        assertEquals(.001, data.getEtd(), 1e-12);
    }

    @Test
    void invalidSelectorAndPhysicalParametersAreRejected() throws Exception {
        DStabNetworkBuilder builder = DStabBuilderTestFixture.createWithMachine();
        PsseGast2adGovernorData invalidZ = data(2);
        assertNull(builder.addGovGast2ad("Bus1", "1", invalidZ));

        PsseGast2adGovernorData zeroBf2 = data(1);
        zeroBf2.setBf2(0.0);
        assertNull(builder.addGovGast2ad("Bus1", "1", zeroBf2));

        PsseGast2adGovernorData zeroYIsochronous = data(0);
        zeroYIsochronous.setY(0.0);
        assertNull(builder.addGovGast2ad("Bus1", "1", zeroYIsochronous));
    }

    @Test
    void dataObjectAcceptsIntegerAndDoubleSelectorAssignments() {
        PsseGast2adGovernorData data = new PsseGast2adGovernorData();
        data.setValue("Z", 1);
        assertEquals(1, data.getZ());
        data.setValue("z", 0.0);
        assertEquals(0, data.getZ());
        assertFalse(data.getZ() == 1);
    }

    @Test
    void externalPowerReferenceIsAppliedAsAnIncrementWithoutInitializationJump()
            throws Exception {
        Fixture fixture = fixture(1);
        double initial = fixture.governor.getSpeedCommand();

        fixture.governor.setRefPoint(fixture.machine.getPm());
        assertEquals(initial, fixture.governor.getSpeedCommand(), 1e-12);

        fixture.governor.setRefPoint(fixture.machine.getPm() + .02);
        assertEquals(initial + .004, fixture.governor.getSpeedCommand(), 1e-10);
    }

    private static Fixture fixture(int z) throws Exception {
        DStabNetworkBuilder builder = DStabBuilderTestFixture.createWithMachine();
        Machine machine = builder.getDStabNetwork().getMachine("Bus1-mach1");
        machine.setPm(.6);
        machine.setPe(.6);
        machine.setSpeed(1.0);
        PsseGast2adGovernor governor = builder.addGovGast2ad("Bus1", "1", data(z));
        assertNotNull(governor);
        governor.configureIntegrationStep(.01);
        assertTrue(governor.initStates(machine.getDStabBus(), machine));
        return new Fixture(machine, governor);
    }

    private static PsseGast2adGovernorData data(int z) {
        PsseGast2adGovernorData data = new PsseGast2adGovernorData();
        data.setW(20.0); data.setX(.1); data.setY(.5); data.setZ(z);
        data.setEtd(.02); data.setTcd(.1); data.setTrate(0.0); data.setT(0.0);
        data.setMaxLimit(1.2); data.setMinLimit(0.0); data.setEcr(.03);
        data.setK3(1.0); data.setA(1.0); data.setB(.2); data.setC(1.0);
        data.setTauF(.1); data.setKf(.05); data.setK5(.4); data.setK4(.6);
        data.setT3(.2); data.setT4(.3); data.setTauT(1.0); data.setT5(.1);
        data.setAf1(.1); data.setBf1(.1); data.setAf2(.05); data.setBf2(.9);
        data.setCf2(.8); data.setTr(1.0); data.setK6(0.0);
        data.setTc(.9611111111111111); data.setDbH(.003); data.setDbL(-.004);
        return data;
    }

    private static void advance(PsseGast2adGovernor governor, Machine machine,
            int count, double dt) {
        for (int i = 0; i < count; i++) {
            assertTrue(governor.nextStep(dt, DynamicSimuMethod.MODIFIED_EULER,
                    machine, 0));
            assertTrue(governor.nextStep(dt, DynamicSimuMethod.MODIFIED_EULER,
                    machine, 1));
        }
    }

    private record Fixture(Machine machine, PsseGast2adGovernor governor) {
    }
}
