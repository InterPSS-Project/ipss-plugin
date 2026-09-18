package org.interpss.core.adapter.builder.dstab;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;

import org.interpss.CorePluginTestSetup;
import org.interpss.dstab.control.gov.wecc.wshydd.WshyddGovernor;
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

    private static Fixture fixture() throws Exception {
        DStabNetworkBuilder builder = DStabBuilderTestFixture.createWithMachine();
        Machine machine = builder.getDStabNetwork().getMachine("Bus1-mach1");
        machine.setPm(.60); machine.setPe(.60); machine.setSpeed(1.0);
        WshyddGovernor governor = builder.addGovWshydd("Bus1", "1", DATA.clone());
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
