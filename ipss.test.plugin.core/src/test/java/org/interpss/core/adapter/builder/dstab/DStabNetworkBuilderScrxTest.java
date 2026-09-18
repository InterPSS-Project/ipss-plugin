package org.interpss.core.adapter.builder.dstab;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;

import org.interpss.CorePluginTestSetup;
import org.interpss.dstab.control.exc.psse.scrx.ScrxData;
import org.interpss.dstab.control.exc.psse.scrx.ScrxExciter;
import org.interpss.fadapter.builder.DStabNetworkBuilder;
import org.interpss.fadapter.psse.PSSEDStabDirectParser;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.interpss.dstab.mach.Machine;

class DStabNetworkBuilderScrxTest extends CorePluginTestSetup {
    private static final double TOL = 1.0e-9;

    @TempDir Path tempDir;

    @Test
    void parsesBusFedRecordAndInitializesWithoutOutputJump() throws Exception {
        DStabNetworkBuilder builder = DStabBuilderTestFixture.createWithMachine();
        Path dyr = tempDir.resolve("scrx.dyr");
        Files.writeString(dyr, "1 'SCRX' '1' .2 1 .5 .1 2 -2 0 .1 /\n");
        PSSEDStabDirectParser parser = new PSSEDStabDirectParser(builder).setStrictImport(true);

        parser.parseDynFile(dyr.toString());
        Machine machine = builder.getDStabNetwork().getMachine("Bus1-mach1");
        machine.setEfd(1.2);
        ScrxExciter exciter = (ScrxExciter) machine.getExciter();

        assertNotNull(exciter);
        assertSame(exciter, machine.getExciter());
        assertEquals(.2, exciter.getData().getTaOverTb(), TOL);
        assertEquals(0, exciter.getData().getCswitch());
        assertTrue(exciter.initStates(machine.getDStabBus(), machine));
        assertEquals(.2, exciter.ta, TOL);
        assertEquals(1.0, exciter.k, TOL);
        assertEquals(2.0, exciter.efdmax, TOL);
        assertEquals(-2.0, exciter.efdmin, TOL);
        assertEquals(machine.getEfd(), exciter.getOutput(machine), TOL);
        assertTrue(parser.getLastImportReport().isStrictlyComplete());
    }

    @Test
    void initializesSolidFedAndAppliesDischargeResistorBoundary() throws Exception {
        DStabNetworkBuilder builder = DStabBuilderTestFixture.createWithMachine();
        Machine machine = builder.getDStabNetwork().getMachine("Bus1-mach1");
        machine.setEfd(2.5);
        ScrxData data = data();
        data.setCswitch(1);
        data.setEfdmin(-1.0);
        data.setEfdmax(1.0);
        ScrxExciter exciter = builder.addExcScrx("Bus1", "1", data);

        assertNotNull(exciter);
        assertTrue(exciter.initStates(machine.getDStabBus(), machine));
        assertEquals(2.5, exciter.initialInternalOutput, TOL);
        assertEquals(2.5, exciter.efdmax, TOL);
        assertEquals(machine.getEfd(), exciter.getOutput(machine), TOL);
        assertEquals(-.6, ScrxExciter.applyFieldBoundary(-1.0, .2, 3.0), TOL);
        assertEquals(-1.0, ScrxExciter.applyFieldBoundary(-1.0, 0.0, 3.0), TOL);
    }

    @Test
    void rejectsInvalidSwitchAndParameters() throws Exception {
        DStabNetworkBuilder builder = DStabBuilderTestFixture.createWithMachine();
        ScrxData data = data();
        data.setCswitch(2);
        assertNull(builder.addExcScrx("Bus1", "1", data));
        data = data(); data.setTb(-.1);
        assertNull(builder.addExcScrx("Bus1", "1", data));
        data = data(); data.setRcOverRfd(-.1);
        assertNull(builder.addExcScrx("Bus1", "1", data));
    }

    private static ScrxData data() {
        ScrxData data = new ScrxData();
        data.setTaOverTb(.2); data.setTb(1.0); data.setK(10.0); data.setTe(.1);
        data.setEfdmin(-5.0); data.setEfdmax(5.0); data.setRcOverRfd(.1);
        return data;
    }
}
