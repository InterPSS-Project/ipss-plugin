package org.interpss.core.adapter.builder.dstab;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;

import org.interpss.CorePluginTestSetup;
import com.interpss.dstab.controller.gov.weccwshydd.WshyddGovernor;
import org.interpss.fadapter.builder.DStabNetworkBuilder;
import org.interpss.fadapter.psse.PSSEDStabDirectParser;
import org.interpss.fadapter.psse.dyr.DynamicModelCatalog;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.interpss.common.exp.InterpssException;
import com.interpss.dstab.algo.DynamicSimuMethod;
import com.interpss.dstab.mach.Machine;

/** Synthetic schema and state checks for the double-derivative governor. */
public class WshyddGovernorTest extends CorePluginTestSetup {
    private static final double[] DATA = {.002, .0003, .08, 1.7, .06, .15,
            4.2, .045, .12, .9, .15, .20, .18, 1.10, 0.0, .001,
            0.0, .02, .23, .20, .47, .48, .76, .78, 1.05, 1.08,
            -.30, .55, 1.30, 0.0};

    @Test
    void exactRecordInitializesFlatWithNineNamedStates(@TempDir Path tempDir)
            throws Exception {
        DStabNetworkBuilder builder = DStabBuilderTestFixture.createWithMachine();
        Path dyr = tempDir.resolve("synthetic-double-derivative-hydro.dyr");
        Files.writeString(dyr, "1 'WSHYDD' '1' " + join(DATA) + " /\n");
        new PSSEDStabDirectParser(builder).setStrictImport(true).parseDynFile(dyr.toString());
        Machine machine = builder.getDStabNetwork().getMachine("Bus1-mach1");
        machine.setPm(.60); machine.setPe(.60); machine.setSpeed(1.0);
        WshyddGovernor governor = (WshyddGovernor) machine.getGovernor();
        assertNotNull(governor);
        assertEquals(30, DynamicModelCatalog.find("WSHYDD").orElseThrow().parameterCount());
        assertTrue(governor.initStates(machine.getDStabBus(), machine));
        assertEquals(9, governor.getNamedStates().size());
        advance(governor, machine, 50, .002);
        assertEquals(.60, governor.getOutput(machine), 1.0e-10);
    }

    @Test
    void speedStepExcitesBothDerivativePathsAndClosesGate() throws Exception {
        Fixture fixture = fixture();
        double gate = fixture.governor.getGatePosition();
        fixture.machine.setSpeed(1.015);
        advance(fixture.governor, fixture.machine, 300, .001);
        assertTrue(fixture.governor.getNamedStates().get("K1 state") < 0.0);
        assertTrue(fixture.governor.getNamedStates().get("KD first") < 0.0);
        assertTrue(fixture.governor.getGatePosition() < gate);
    }

    @Test
    void strictImportRejectsInvalidRateLimit(@TempDir Path tempDir) throws Exception {
        double[] invalid = DATA.clone(); invalid[11] = -.1;
        DStabNetworkBuilder builder = DStabBuilderTestFixture.createWithMachine();
        Path dyr = tempDir.resolve("invalid-double-derivative-hydro.dyr");
        Files.writeString(dyr, "1 'WSHYDD' '1' " + join(invalid) + " /\n");
        PSSEDStabDirectParser parser = new PSSEDStabDirectParser(builder).setStrictImport(true);
        assertThrows(InterpssException.class, () -> parser.parseDynFile(dyr.toString()));
    }

    @Test void zeroTimeEventObservationPreservesPredictorAndStateHistory() throws Exception {
        var observed=fixture();var ordinary=fixture();
        observed.machine.setSpeed(1.01);ordinary.machine.setSpeed(1.01);
        for(int tick=0;tick<50;tick++) {
            assertTrue(observed.governor.nextStep(.001,DynamicSimuMethod.MODIFIED_EULER,observed.machine,0));
            assertTrue(ordinary.governor.nextStep(.001,DynamicSimuMethod.MODIFIED_EULER,ordinary.machine,0));
            var before=observed.governor.getNamedStates();double output=observed.governor.getOutput(observed.machine);
            assertTrue(observed.governor.nextStep(0.,DynamicSimuMethod.MODIFIED_EULER,observed.machine,0));
            assertTrue(observed.governor.nextStep(0.,DynamicSimuMethod.MODIFIED_EULER,observed.machine,1));
            assertEquals(before,observed.governor.getNamedStates());assertEquals(output,observed.governor.getOutput(observed.machine),0.);
            assertTrue(observed.governor.nextStep(.001,DynamicSimuMethod.MODIFIED_EULER,observed.machine,1));
            assertTrue(ordinary.governor.nextStep(.001,DynamicSimuMethod.MODIFIED_EULER,ordinary.machine,1));
            assertEquals(ordinary.governor.getNamedStates(),observed.governor.getNamedStates());
        }
    }

    @Test
    void sustainedSpeedErrorChangesTheValveCommandAfterDerivativesDecay() throws Exception {
        double[] parameters = DATA.clone();
        parameters[0] = parameters[1] = parameters[3] = parameters[5] = parameters[8] = parameters[15] = 0.0;
        parameters[6] = 20.0;
        parameters[7] = 0.10;
        Fixture fixture = fixture(parameters);
        double initialGate = fixture.governor.getGatePosition();
        fixture.machine.setSpeed(0.996);
        advance(fixture.governor, fixture.machine, 8_000, .001);

        assertEquals(initialGate + .004 / .10, fixture.governor.getNamedStates().get("CV"), 1.0e-6);
        assertEquals(initialGate + .004 / .10, fixture.governor.getGatePosition(), 2.0e-4);
    }

    @Test
    void travelRatesBoundTheServoStateBeforeAndAfterCommandReversal() throws Exception {
        double[] parameters = DATA.clone();
        parameters[11] = .02;
        parameters[12] = 0.0;
        Fixture fixture = fixture(parameters);
        double initialGate = fixture.governor.getGatePosition();
        fixture.governor.setRefPoint(-.10);
        advance(fixture.governor, fixture.machine, 1_000, .001);
        assertEquals(0.0, fixture.governor.getNamedStates().get("Valve speed"), 1.0e-12);
        assertEquals(initialGate, fixture.governor.getGatePosition(), 1.0e-12);

        fixture.governor.setRefPoint(.25);
        advance(fixture.governor, fixture.machine, 2_000, .001);
        assertEquals(.02, fixture.governor.getNamedStates().get("Valve speed"), 1.0e-12);
        assertTrue(fixture.governor.getGatePosition() > initialGate);
    }

    private static Fixture fixture() throws Exception {
        return fixture(DATA.clone());
    }

    private static Fixture fixture(double[] parameters) throws Exception {
        DStabNetworkBuilder builder = DStabBuilderTestFixture.createWithMachine();
        Machine machine = builder.getDStabNetwork().getMachine("Bus1-mach1");
        machine.setPm(.60); machine.setPe(.60); machine.setSpeed(1.0);
        WshyddGovernor governor = builder.addGovWshydd("Bus1", "1", parameters);
        assertNotNull(governor); assertTrue(governor.initStates(machine.getDStabBus(), machine));
        return new Fixture(machine, governor);
    }
    private static void advance(WshyddGovernor governor, Machine machine,
            int count, double dt) {
        for (int index=0; index<count; index++) {
            assertTrue(governor.nextStep(dt, DynamicSimuMethod.MODIFIED_EULER, machine, 0));
            assertTrue(governor.nextStep(dt, DynamicSimuMethod.MODIFIED_EULER, machine, 1));
        }
    }
    private static String join(double[] values) {
        StringBuilder text=new StringBuilder();
        for(double value:values)text.append(value).append(' ');
        return text.toString();
    }
    private record Fixture(Machine machine, WshyddGovernor governor) { }
}
