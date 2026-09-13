package org.interpss.core.adapter.builder.dstab;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;

import org.interpss.CorePluginTestSetup;
import org.interpss.dstab.control.gov.wecc.wshygp.WshygpGovernor;
import org.interpss.fadapter.builder.DStabNetworkBuilder;
import org.interpss.fadapter.psse.PSSEDStabDirectParser;
import org.interpss.fadapter.psse.dyr.DynamicModelCatalog;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.interpss.common.exp.InterpssException;
import com.interpss.dstab.algo.DynamicSimuMethod;
import com.interpss.dstab.mach.Machine;

/** Independent synthetic checks for the WSHYGP compatibility model. */
public class WshygpGovernorTest extends CorePluginTestSetup {
    private static final double[] DATA = {.002, .0003, .08, 5.5, .06, .12,
            8.5, .045, .12, .9, .15, .20, .18, 1.10, 0.0, .001,
            0.0, .02, .23, .20, .47, .48, .76, .78, 1.05, 1.08,
            -.30, .55, 1.30, 0.0};

    @Test
    void exactRecordInitializesFlatWithSevenNamedStates(@TempDir Path tempDir)
            throws Exception {
        DStabNetworkBuilder builder = DStabBuilderTestFixture.createWithMachine();
        Path dyr = tempDir.resolve("synthetic-hydro.dyr");
        Files.writeString(dyr, "1 'WSHYGP' '1' " + join(DATA) + " /\n");
        new PSSEDStabDirectParser(builder).setStrictImport(true).parseDynFile(dyr.toString());
        Machine machine = builder.getDStabNetwork().getMachine("Bus1-mach1");
        machine.setPm(.60); machine.setPe(.60); machine.setSpeed(1.0);
        WshygpGovernor governor = (WshygpGovernor) machine.getGovernor();
        assertNotNull(governor);
        assertEquals(30, DynamicModelCatalog.find("WSHYGP").orElseThrow().parameterCount());
        assertTrue(governor.initStates(machine.getDStabBus(), machine));
        assertEquals(Set.of("Output, Td", "Integrator state", "Derivative state",
                "Valve speed", "Gate position", "Generator power", "Turbine"),
                governor.getNamedStates().keySet());
        assertEquals(.60, governor.getOutput(machine), 1.0e-12);
        advance(governor, machine, 50, .002);
        assertEquals(.60, governor.getOutput(machine), 1.0e-10);
    }

    @Test
    void nonlinearGateCurveAndSpeedResponseHaveExpectedDirection() throws Exception {
        Fixture fixture = fixture();
        assertEquals(.32833333333333337, fixture.governor.gateCurve(.34), 1.0e-12);
        double gate = fixture.governor.getGatePosition();
        fixture.machine.setSpeed(1.015);
        advance(fixture.governor, fixture.machine, 2000, .002);
        assertTrue(fixture.governor.getGatePosition() < gate);
        assertTrue(fixture.governor.getOutput(fixture.machine) < .60);
    }

    @Test
    void strictImportRejectsNonmonotonicCurve(@TempDir Path tempDir) throws Exception {
        double[] invalid = DATA.clone();
        invalid[20] = .10;
        DStabNetworkBuilder builder = DStabBuilderTestFixture.createWithMachine();
        Path dyr = tempDir.resolve("invalid-hydro.dyr");
        Files.writeString(dyr, "1 'WSHYGP' '1' " + join(invalid) + " /\n");
        PSSEDStabDirectParser parser = new PSSEDStabDirectParser(builder).setStrictImport(true);
        assertThrows(InterpssException.class, () -> parser.parseDynFile(dyr.toString()));
    }

    private static Fixture fixture() throws Exception {
        DStabNetworkBuilder builder = DStabBuilderTestFixture.createWithMachine();
        Machine machine = builder.getDStabNetwork().getMachine("Bus1-mach1");
        machine.setPm(.60); machine.setPe(.60); machine.setSpeed(1.0);
        WshygpGovernor governor = builder.addGovWshygp("Bus1", "1", DATA.clone());
        assertNotNull(governor);
        assertTrue(governor.initStates(machine.getDStabBus(), machine));
        return new Fixture(machine, governor);
    }
    private static void advance(WshygpGovernor governor, Machine machine,
            int count, double dt) {
        for (int index = 0; index < count; index++) {
            assertTrue(governor.nextStep(dt, DynamicSimuMethod.MODIFIED_EULER, machine, 0));
            assertTrue(governor.nextStep(dt, DynamicSimuMethod.MODIFIED_EULER, machine, 1));
        }
    }
    private static String join(double[] values) {
        StringBuilder text = new StringBuilder();
        for (double value : values) text.append(value).append(' ');
        return text.toString();
    }
    private record Fixture(Machine machine, WshygpGovernor governor) { }
}
