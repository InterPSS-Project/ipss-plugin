package org.interpss.core.adapter.builder.dstab;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.file.Files;
import java.nio.file.Path;

import org.interpss.CorePluginTestSetup;
import com.interpss.dstab.controller.gov.hygov.PsseHygovGovernor;
import com.interpss.dstab.controller.gov.hygov.PsseHygovGovernorData;
import org.interpss.fadapter.psse.PSSEDStabDirectParser;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Entered position limits remain active when the solved initial gate is outside them. */
class HygovEnteredGateLimitTest extends CorePluginTestSetup {
    @Test
    void upperLimitDrivesTheInitialGateBackWithoutChangingInitialPower() throws Exception {
        checkInitialGate(1.00702809856, 0.0, 1.0, 1.0);
    }

    @Test
    void lowerLimitDrivesTheInitialGateBackWithoutChangingInitialPower() throws Exception {
        checkInitialGate(.4, .6, 1.0, .6);
    }

    @Test
    void initialGateInsideTheEnteredRangeHasZeroRates() throws Exception {
        checkInitialGate(.8, 0.0, 1.0, .8);
    }

    @Test
    void importedRecordKeepsTheEnteredUpperLimit(@TempDir Path temporary) throws Exception {
        var builder = DStabBuilderTestFixture.createWithMachine();
        Path record = temporary.resolve("hydro.dyr");
        Files.writeString(record,
                "1 'HYGOV' 1 .05 .66 9.79 .17 .43 .18 1 0 1.1 1.05 .2 .1 /\n");
        var parser = new PSSEDStabDirectParser(builder).setStrictImport(true);
        parser.parseDynFile(record.toString());
        assertTrue(parser.getLastImportReport().isStrictlyComplete());
        var machine = builder.getDStabNetwork().getMachine("Bus1-mach1");
        var governor = (PsseHygovGovernor) machine.getGovernor();
        double initialGate = 1.00702809856;
        machine.setSpeed(1.0);
        machine.setPm(1.05 * (initialGate - .1));
        governor.configureIntegrationStep(.001);
        assertTrue(governor.initStates(machine.getDStabBus(), machine));
        double[] rates = new double[4];
        governor.evaluateStaggered(.001, rates, 0);
        assertEquals((1.0 - initialGate) / .43, rates[2], 1e-14);
        assertEquals(initialGate, governor.getGatePosition(), 1e-14);
        assertEquals(1.0, governor.getData().getGmax(), 0.0);
    }

    private static void checkInitialGate(double initialGate, double lower, double upper,
            double limitedCommand) throws Exception {
        var builder = DStabBuilderTestFixture.createWithMachine();
        var machine = builder.getDStabNetwork().getMachine("Bus1-mach1");
        var data = new PsseHygovGovernorData();
        data.setR(.05); data.setRtemp(.66); data.setTr(9.79); data.setTf(.17);
        data.setTg(.43); data.setVelm(.18); data.setGmax(upper); data.setGmin(lower);
        data.setTw(1.1); data.setAt(1.05); data.setDturb(.2); data.setQnl(.1);
        double initialPower = data.getAt() * (initialGate - data.getQnl());
        machine.setSpeed(1.0); machine.setPm(initialPower); machine.setPe(initialPower);
        var governor = builder.addGovHygov("Bus1", "1", data);
        governor.configureIntegrationStep(.001);
        assertTrue(governor.initStates(machine.getDStabBus(), machine));
        var before = governor.getIntegrationStates();
        double[] rates = new double[4];
        governor.evaluateStaggered(.001, rates, 0);
        assertEquals(initialPower, governor.getOutput(machine), 1e-14);
        assertEquals(initialGate, governor.getGatePosition(), 1e-14);
        assertEquals(initialGate, governor.getWaterFlow(), 1e-14);
        assertEquals((limitedCommand - initialGate) / data.getTg(), rates[2], 1e-14);
        assertEquals(0.0, rates[3], 1e-14, "The initial turbine head remains one");
        assertEquals(before, governor.getIntegrationStates(), "Derivative evaluation is read-only");
        assertEquals(upper, governor.getData().getGmax(), 0.0);
        assertEquals(lower, governor.getData().getGmin(), 0.0);
    }
}
