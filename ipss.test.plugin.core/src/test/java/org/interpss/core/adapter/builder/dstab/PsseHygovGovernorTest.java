package org.interpss.core.adapter.builder.dstab;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;

import org.interpss.CorePluginTestSetup;
import org.interpss.dstab.control.gov.psse.hygov.PsseHygovGovernor;
import org.interpss.dstab.control.gov.psse.hygov.PsseHygovGovernorData;
import org.interpss.fadapter.builder.DStabNetworkBuilder;
import org.interpss.fadapter.psse.PSSEDStabDirectParser;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.interpss.dstab.algo.DynamicSimuMethod;
import com.interpss.dstab.mach.Machine;

public class PsseHygovGovernorTest extends CorePluginTestSetup {
    private static final double TOL = 1.0e-9;

    @Test
    void builderAttachesAllTwelveParameters() throws Exception {
        DStabNetworkBuilder builder = DStabBuilderTestFixture.createWithMachine();
        PsseHygovGovernorData data = texasData();
        PsseHygovGovernor governor = builder.addGovHygov("Bus1", "1", data);

        assertNotNull(governor);
        assertSame(governor, builder.getDStabNetwork().getMachine("Bus1-mach1").getGovernor());
        assertArrayEquals(values(data), values(governor.getData()), TOL);
    }

    @Test
    void initializesWithoutDriftAndUsesTemporaryDroopTimeConstant() throws Exception {
        DStabNetworkBuilder fastBuilder = DStabBuilderTestFixture.createWithMachine();
        Machine fastMachine = fastBuilder.getDStabNetwork().getMachine("Bus1-mach1");
        fastMachine.setSpeed(1.0); fastMachine.setPm(0.4); fastMachine.setPe(0.4);
        PsseHygovGovernorData fastData = texasData();
        PsseHygovGovernor fast = fastBuilder.addGovHygov("Bus1", "1", fastData);
        assertTrue(fast.initStates(fastBuilder.getDStabNetwork().getDStabBus("Bus1"), fastMachine));

        double initialGate = fast.getGatePosition();
        for (int i = 0; i < 200; i++) step(fast, fastMachine, 0.005);
        assertEquals(0.4, fast.getOutput(fastMachine), 2.0e-8);
        assertEquals(initialGate, fast.getGatePosition(), 2.0e-8);

        DStabNetworkBuilder slowBuilder = DStabBuilderTestFixture.createWithMachine();
        Machine slowMachine = slowBuilder.getDStabNetwork().getMachine("Bus1-mach1");
        slowMachine.setSpeed(1.0); slowMachine.setPm(0.4); slowMachine.setPe(0.4);
        PsseHygovGovernorData slowData = texasData();
        slowData.setTr(fastData.getTr() * 2.0);
        PsseHygovGovernor slow = slowBuilder.addGovHygov("Bus1", "1", slowData);
        assertTrue(slow.initStates(slowBuilder.getDStabNetwork().getDStabBus("Bus1"), slowMachine));

        fastMachine.setSpeed(0.99);
        slowMachine.setSpeed(0.99);
        for (int i = 0; i < 400; i++) {
            step(fast, fastMachine, 0.005);
            step(slow, slowMachine, 0.005);
        }
        assertTrue(fast.getGatePosition() > initialGate);
        assertTrue(fast.getGatePosition() > slow.getGatePosition(),
                "A shorter Tr must release temporary droop faster");
    }

    @Test
    void appliesGateVelocityLimitAndZeroFilterBypass() throws Exception {
        DStabNetworkBuilder builder = DStabBuilderTestFixture.createWithMachine();
        Machine machine = builder.getDStabNetwork().getMachine("Bus1-mach1");
        machine.setSpeed(1.0); machine.setPm(0.4); machine.setPe(0.4);
        PsseHygovGovernorData data = texasData();
        data.setTf(0.0);
        data.setVelm(0.02);
        PsseHygovGovernor governor = builder.addGovHygov("Bus1", "1", data);
        assertTrue(governor.initStates(builder.getDStabNetwork().getDStabBus("Bus1"), machine));
        double initialDesiredGate = governor.getDesiredGate();

        machine.setSpeed(0.98);
        for (int i = 0; i < 200; i++) step(governor, machine, 0.005);
        assertTrue(governor.getDesiredGate() > initialDesiredGate);
        double movement = governor.getDesiredGate() - initialDesiredGate;
        assertTrue(movement <= 0.0201,
                "Desired gate movement must respect VELM, movement=" + movement);
    }

    @Test
    void directParserUsesPsseFieldOrder(@TempDir Path tempDir) throws Exception {
        DStabNetworkBuilder builder = DStabBuilderTestFixture.createWithMachine();
        Path dyr = tempDir.resolve("hygov.dyr");
        Files.writeString(dyr,
                "1 'HYGOV' 1 0.05 0.5 8.38 0.1 0.95 0.11 1 0 1.05 1.09 0.2 0.12 /\n");

        PSSEDStabDirectParser parser = new PSSEDStabDirectParser(builder).setStrictImport(true);
        parser.parseDynFile(dyr.toString());
        PsseHygovGovernor governor = (PsseHygovGovernor) builder.getDStabNetwork()
                .getMachine("Bus1-mach1").getGovernor();
        assertNotNull(governor);
        assertArrayEquals(new double[] { .05, .5, 8.38, .1, .95, .11,
                1, 0, 1.05, 1.09, .2, .12 }, values(governor.getData()), TOL);
        assertTrue(parser.getLastImportReport().isStrictlyComplete());
    }

    @Test
    void hygovduMapsDeadbandAndRatingAndNormalizesReversedGateLimits(@TempDir Path tempDir)
            throws Exception {
        DStabNetworkBuilder builder = DStabBuilderTestFixture.createWithMachine();
        Path dyr = tempDir.resolve("hygovdu.dyr");
        Files.writeString(dyr,
                "1 'HYGOVDU' 1 0.05 0.5 8.38 0.1 0.95 0.11 0 1 1.05 1.09 0.2 0.12 "
                        + "0.002 -0.003 50.0 /\n");

        PSSEDStabDirectParser parser = new PSSEDStabDirectParser(builder).setStrictImport(true);
        parser.parseDynFile(dyr.toString());
        Machine machine = builder.getDStabNetwork().getMachine("Bus1-mach1");
        machine.setSpeed(1.0); machine.setPm(0.4); machine.setPe(0.4);
        PsseHygovGovernor governor = (PsseHygovGovernor) machine.getGovernor();

        assertNotNull(governor);
        assertEquals("HYGOVD", governor.getName());
        assertEquals(0.002, governor.getData().getDbH(), TOL);
        assertEquals(-0.003, governor.getData().getDbL(), TOL);
        assertEquals(50.0, governor.getData().getTrate(), TOL);
        assertTrue(governor.initStates(builder.getDStabNetwork().getDStabBus("Bus1"), machine));
        assertEquals(50.0, governor.getGovernorBaseMva(machine), TOL);
        assertEquals(0.4, governor.getOutput(machine), TOL);
        assertEquals(0.0, governor.applyFrequencyDeadband(-0.002), TOL);
        assertEquals(-0.007, governor.applyFrequencyDeadband(-0.010), TOL);
        assertEquals(0.008, governor.applyFrequencyDeadband(0.010), TOL);
        assertTrue(parser.getLastImportReport().isStrictlyComplete());
    }

    private static void step(PsseHygovGovernor governor, Machine machine, double dt) {
        governor.nextStep(dt, DynamicSimuMethod.MODIFIED_EULER, machine, 0);
        governor.nextStep(dt, DynamicSimuMethod.MODIFIED_EULER, machine, 1);
    }

    private static PsseHygovGovernorData texasData() {
        PsseHygovGovernorData d = new PsseHygovGovernorData();
        d.setR(.05); d.setRtemp(.5); d.setTr(8.38); d.setTf(.1);
        d.setTg(.95); d.setVelm(.11); d.setGmax(1); d.setGmin(0);
        d.setTw(1.05); d.setAt(1.09); d.setDturb(.2); d.setQnl(.12);
        return d;
    }

    private static double[] values(PsseHygovGovernorData d) {
        return new double[] { d.getR(), d.getRtemp(), d.getTr(), d.getTf(), d.getTg(),
                d.getVelm(), d.getGmax(), d.getGmin(), d.getTw(), d.getAt(),
                d.getDturb(), d.getQnl() };
    }
}
