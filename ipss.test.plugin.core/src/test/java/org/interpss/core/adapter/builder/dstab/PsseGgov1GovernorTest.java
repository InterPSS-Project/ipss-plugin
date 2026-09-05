package org.interpss.core.adapter.builder.dstab;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;

import org.interpss.CorePluginTestSetup;
import org.interpss.dstab.control.gov.psse.ggov1.PsseGgov1Governor;
import org.interpss.dstab.control.gov.psse.ggov1.PsseGgov1GovernorData;
import org.interpss.fadapter.builder.DStabNetworkBuilder;
import org.interpss.fadapter.psse.PSSEDStabDirectParser;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.interpss.dstab.algo.DynamicSimuMethod;
import com.interpss.dstab.mach.Machine;

class PsseGgov1GovernorTest extends CorePluginTestSetup {
    private static final double TOL = 1.0e-9;

    @Test
    void builderAttachesExactDataIncludingTransportDelay() throws Exception {
        DStabNetworkBuilder builder = DStabBuilderTestFixture.createWithMachine();
        PsseGgov1GovernorData data = texasData();
        PsseGgov1Governor governor = builder.addGovGgov1("Bus1", "1", data);

        assertNotNull(governor);
        assertSame(governor, builder.getDStabNetwork().getMachine("Bus1-mach1").getGovernor());
        assertEquals(1, governor.getData().getRselect());
        assertEquals(390.24, governor.getData().getTrate(), TOL);
        assertEquals(4.0, governor.getData().getTsa(), TOL);
        assertEquals(-99.0, governor.getData().getRdown(), TOL);

        data.setTeng(0.1);
        DStabNetworkBuilder delayBuilder = DStabBuilderTestFixture.createWithMachine();
        assertNotNull(delayBuilder.addGovGgov1("Bus1", "1", data));
    }

    @Test
    void initializesOnTrateBaseWithoutDriftAndRespondsToFrequency() throws Exception {
        DStabNetworkBuilder builder = DStabBuilderTestFixture.createWithMachine();
        Machine machine = builder.getDStabNetwork().getMachine("Bus1-mach1");
        machine.setSpeed(1.0);
        machine.setPm(0.4);
        machine.setPe(0.4);
        PsseGgov1GovernorData data = texasData();
        data.setTrate(50.0);
        PsseGgov1Governor governor = builder.addGovGgov1("Bus1", "1", data);

        assertTrue(governor.initStates(builder.getDStabNetwork().getDStabBus("Bus1"), machine));
        assertEquals(50.0, governor.getGovernorBaseMva(machine), TOL);
        assertEquals(0.4, governor.getOutput(machine), TOL);
        double initialValve = governor.getValveStroke();
        for (int i = 0; i < 200; i++) step(governor, machine, 0.005);
        assertEquals(0.4, governor.getOutput(machine), 2.0e-8);
        assertEquals(initialValve, governor.getValveStroke(), 2.0e-8);

        machine.setSpeed(0.99);
        for (int i = 0; i < 400; i++) step(governor, machine, 0.005);
        assertTrue(governor.getValveStroke() > initialValve + 1.0e-3);
        assertTrue(governor.getOutput(machine) > 0.4);
    }

    @Test
    void directParserUsesPsseFieldOrder(@TempDir Path tempDir) throws Exception {
        DStabNetworkBuilder builder = DStabBuilderTestFixture.createWithMachine();
        Path dyr = tempDir.resolve("ggov1.dyr");
        Files.writeString(dyr, "1 'GGOV1' 1 1 0 0.03 1 0.05 -0.05 4.18 0.71 0 1 "
                + "1 0.15 0.4 1.22 0.18 0.16 0 0 3 2.56 0.85 1 0 1 -1 0 "
                + "0.01 10 0.1 390.24 0 4 5 99 -99 /\n");

        PSSEDStabDirectParser parser = new PSSEDStabDirectParser(builder).setStrictImport(true);
        parser.parseDynFile(dyr.toString());
        PsseGgov1Governor governor = (PsseGgov1Governor) builder.getDStabNetwork()
                .getMachine("Bus1-mach1").getGovernor();
        assertNotNull(governor);
        assertArrayEquals(new double[] { 1, 0, .03, 1, .05, -.05, 4.18, .71, 0, 1,
                1, .15, .4, 1.22, .18, .16, 0, 0, 3, 2.56, .85, 1, 0, 1, -1,
                0, .01, 10, .1, 390.24, 0, 4, 5, 99, -99 }, values(governor.getData()), TOL);
        assertTrue(parser.getLastImportReport().isStrictlyComplete());
    }

    @Test
    void dieselTransportDelayHoldsTurbineInputForItsPhysicalDuration() throws Exception {
        DStabNetworkBuilder builder = DStabBuilderTestFixture.createWithMachine();
        Machine machine = builder.getDStabNetwork().getMachine("Bus1-mach1");
        machine.setSpeed(1.0);
        machine.setPm(0.4);
        machine.setPe(0.4);
        PsseGgov1GovernorData data = texasData();
        data.setTeng(.05);
        data.setTb(0.0);
        data.setTc(0.0);
        PsseGgov1Governor governor = builder.addGovGgov1("Bus1", "1", data);
        assertTrue(governor.initStates(builder.getDStabNetwork().getDStabBus("Bus1"), machine));

        machine.setSpeed(.99);
        for (int i = 0; i < 9; i++) step(governor, machine, .005);
        assertEquals(.4, governor.getOutput(machine), 1.0e-9);
        for (int i = 0; i < 20; i++) step(governor, machine, .005);
        assertTrue(governor.getOutput(machine) > .4);
    }

    private static void step(PsseGgov1Governor governor, Machine machine, double dt) {
        governor.nextStep(dt, DynamicSimuMethod.MODIFIED_EULER, machine, 0);
        governor.nextStep(dt, DynamicSimuMethod.MODIFIED_EULER, machine, 1);
    }

    private static PsseGgov1GovernorData texasData() {
        PsseGgov1GovernorData d = new PsseGgov1GovernorData();
        d.setRselect(1); d.setFlag(0); d.setR(0.03); d.setTpelec(1.0);
        d.setMaxerr(0.05); d.setMinerr(-0.05); d.setKpgov(4.18); d.setKigov(0.71);
        d.setKdgov(0); d.setTdgov(1); d.setVmax(1); d.setVmin(0.15);
        d.setTact(0.4); d.setKturb(1.22); d.setWfnl(0.18); d.setTb(0.16);
        d.setTc(0); d.setTeng(0); d.setTfload(3); d.setKpload(2.56);
        d.setKiload(0.85); d.setLdref(1); d.setDm(0); d.setRopen(1); d.setRclose(-1);
        d.setKimw(0); d.setAset(0.01); d.setKa(10); d.setTa(0.1); d.setTrate(390.24);
        d.setDb(0); d.setTsa(4); d.setTsb(5); d.setRup(99); d.setRdown(-99);
        return d;
    }

    private static double[] values(PsseGgov1GovernorData d) {
        return new double[] { d.getRselect(), d.getFlag(), d.getR(), d.getTpelec(),
                d.getMaxerr(), d.getMinerr(), d.getKpgov(), d.getKigov(), d.getKdgov(),
                d.getTdgov(), d.getVmax(), d.getVmin(), d.getTact(), d.getKturb(),
                d.getWfnl(), d.getTb(), d.getTc(), d.getTeng(), d.getTfload(),
                d.getKpload(), d.getKiload(), d.getLdref(), d.getDm(), d.getRopen(),
                d.getRclose(), d.getKimw(), d.getAset(), d.getKa(), d.getTa(),
                d.getTrate(), d.getDb(), d.getTsa(), d.getTsb(), d.getRup(), d.getRdown() };
    }
}
