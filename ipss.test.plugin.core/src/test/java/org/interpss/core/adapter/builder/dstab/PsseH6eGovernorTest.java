package org.interpss.core.adapter.builder.dstab;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;

import org.interpss.CorePluginTestSetup;
import org.interpss.dstab.control.gov.psse.h6e.PsseH6eGovernor;
import org.interpss.dstab.control.gov.psse.h6e.PsseH6eGovernorData;
import org.interpss.fadapter.builder.DStabNetworkBuilder;
import org.interpss.fadapter.psse.PSSEDStabDirectParser;
import org.interpss.fadapter.psse.dyr.DynamicModelCatalog;
import org.interpss.fadapter.psse.dyr.DynamicModelImportStatus;
import org.interpss.fadapter.psse.dyr.DynamicModelSupportStatus;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.interpss.dstab.algo.DynamicSimuMethod;
import com.interpss.dstab.mach.Machine;

public class PsseH6eGovernorTest extends CorePluginTestSetup {
    private static final double TOL = 1.0e-9;

    @Test
    void mapsActualUsrmdlHeaderIconAndAllSixtyTwoConstants(@TempDir Path tempDir)
            throws Exception {
        DStabNetworkBuilder builder = DStabBuilderTestFixture.createWithMachine();
        Path dyr = tempDir.resolve("h6eu1.dyr");
        Files.writeString(dyr, "1 'USRMDL' 1 'H6EU1' 5 0 1 62 9 24 1 "
                + ".05 .05 .5 .025 2 .5 0 .05 .014 1 0 .1 .008 1.25 .05 "
                + "0 0 0 0 0 0 0 0 1 .07 0 1.03 .03 "
                + ".2 .3 .4 .5 .6 .7 .8 .9 1 0 "
                + ".303 .455 .577 .683 .775 .855 .923 .972 .98 1 "
                + "1 1 1 1 1 1 1 1 1 1.28 1 0 0 60 /\n");

        PSSEDStabDirectParser parser = new PSSEDStabDirectParser(builder).setStrictImport(true);
        parser.parseDynFile(dyr.toString());
        PsseH6eGovernor governor = (PsseH6eGovernor) builder.getDStabNetwork()
                .getMachine("Bus1-mach1").getGovernor();
        PsseH6eGovernorData d = governor.getData();

        assertNotNull(governor);
        assertEquals(1, d.getFd());
        assertEquals(.05, d.getRe(), TOL); assertEquals(.025, d.getTsp(), TOL);
        assertEquals(2.0, d.getKp(), TOL); assertEquals(.014, d.getVelm(), TOL);
        assertEquals(.008, d.getBuv(), TOL); assertEquals(1.25, d.getKg(), TOL);
        assertEquals(1.0, d.getDturb(), TOL); assertEquals(.07, d.getPgc(), TOL);
        assertEquals(1.03, d.getHdam(), TOL); assertEquals(.03, d.getTw(), TOL);
        assertEquals(.2, d.getGv(0), TOL); assertEquals(0.0, d.getGv(9), TOL);
        assertEquals(.303, d.getPgv(0), TOL); assertEquals(.98, d.getPgv(8), TOL);
        assertEquals(1.28, d.getBgv(9), TOL); assertEquals(1.0, d.getSprate(), TOL);
        assertEquals(60.0, d.getTrate(), TOL);
        assertEquals(1, parser.getLastImportReport().count(DynamicModelImportStatus.ATTACHED));
        assertEquals(1, parser.getLastImportReport().aliasConversionCount());
        assertTrue(parser.getLastImportReport().isStrictlyComplete());

        Machine machine = builder.getDStabNetwork().getMachine("Bus1-mach1");
        machine.setPm(.4); machine.setPe(.4); machine.setSpeed(1.0);
        machine.getDStabBus().setFreq(1.0);
        governor.configureIntegrationStep(.0025);
        assertTrue(governor.initStates(machine.getDStabBus(), machine));
        double gate0 = governor.getGatePosition();
        for (int i = 0; i < 400; i++) step(governor, machine, .0025);
        assertEquals(.4, governor.getOutput(machine), 2.0e-7);
        assertEquals(gate0, governor.getGatePosition(), 2.0e-7);
    }

    @Test
    void catalogAcceptsFlatNativeAndWrappedRecordShapes() {
        var descriptor = DynamicModelCatalog.find("H6EU1").orElseThrow();
        assertEquals("H6E", descriptor.canonicalName());
        assertEquals(63, descriptor.recordSchema().primaryParameterCount());
        assertTrue(descriptor.recordSchema().accepts(61));
        assertTrue(descriptor.recordSchema().accepts(69));
        assertEquals(DynamicModelSupportStatus.LOADABLE, descriptor.supportStatus());
    }

    @Test
    void speedAndLoadControlModesInitializeWithoutDrift() throws Exception {
        for (int fd : new int[] {0, 1}) {
            Fixture f = initialized(fd);
            assertEquals(.4, f.governor.getOutput(f.machine), TOL);
            double gate0 = f.governor.getGatePosition();
            for (int i = 0; i < 400; i++) step(f.governor, f.machine, .0025);
            assertEquals(.4, f.governor.getOutput(f.machine), 2.0e-8);
            assertEquals(gate0, f.governor.getGatePosition(), 2.0e-8);
        }
    }

    @Test
    void bothDroopModesOpenGateAfterUnderfrequency() throws Exception {
        Fixture speed = initialized(0);
        Fixture load = initialized(1);
        double speedGate0 = speed.governor.getGatePosition();
        double loadGate0 = load.governor.getGatePosition();
        speed.machine.setSpeed(.99);
        load.machine.setSpeed(.99);
        for (int i = 0; i < 800; i++) {
            step(speed.governor, speed.machine, .0025);
            step(load.governor, load.machine, .0025);
        }
        assertTrue(speed.governor.getGatePosition() > speedGate0);
        assertTrue(load.governor.getGatePosition() > loadGate0);
        assertTrue(speed.governor.getOutput(speed.machine) > .4);
        assertTrue(load.governor.getOutput(load.machine) > .4);
    }

    @Test
    void kaplanCurvesCombineBladeAngleAndGateIntoFlowArea() throws Exception {
        Fixture f = initialized(1);
        PsseH6eGovernorData d = f.governor.getData();
        double[] values = new double[10];
        for (int i = 0; i < 10; i++) values[i] = i / 9.0;
        d.setGv(values); d.setPgv(values); d.setBgv(values);
        assertEquals(.275, f.governor.flowArea(.5), TOL);
        assertEquals(.5, f.governor.bladeCurve(.5), TOL);
        // P(Q) is piecewise linear in flow Q, not in gate position.
        assertEquals(.49722222222222223, f.governor.powerCurve(.275), TOL);
    }

    @Test
    void appliesPublishedCorrectionsWithoutMutatingImportedParameters() throws Exception {
        DStabNetworkBuilder builder = DStabBuilderTestFixture.createWithMachine();
        Machine machine = builder.getDStabNetwork().getMachine("Bus1-mach1");
        machine.setPm(.4); machine.setPe(.4); machine.setSpeed(1.0);
        PsseH6eGovernorData data = data(1);
        data.setFd(7); data.setKi(0.0); data.setTpe(.003); data.setTsp(-.003);
        data.setTd(.006); data.setTg(.003); data.setTw(.003);
        data.setVelm(-.2); data.setBuv(-.04); data.setDturb(-1); data.setDeff(-2);
        data.setGmax(.2); data.setGmin(.8); data.setBgvmin(0.0);
        PsseH6eGovernor governor = builder.addGovH6e("Bus1", "1", data);
        governor.configureIntegrationStep(.01);

        assertTrue(governor.initStates(machine.getDStabBus(), machine));
        assertEquals(0.0, governor.getEffectiveTpe(), TOL);
        assertEquals(-.01, governor.getEffectiveTsp(), TOL);
        assertEquals(.01, governor.getEffectiveTd(), TOL);
        assertEquals(0.0, governor.getEffectiveTg(), TOL);
        assertEquals(.01, governor.getEffectiveTw(), TOL);
        assertEquals(1.0e-6, governor.getEffectiveKi(), TOL);
        assertEquals(1.0e-5, governor.getEffectiveBgvmin(), TOL);
        assertTrue(governor.getEffectiveGmax() >= governor.getGatePosition());
        assertTrue(governor.getEffectiveGmin() <= governor.getGatePosition());
        assertEquals(.003, data.getTpe(), TOL); assertEquals(-.003, data.getTsp(), TOL);
        assertEquals(-1.0, data.getDturb(), TOL); assertEquals(0.0, data.getBgvmin(), TOL);
    }

    @Test
    void negativeTspSelectsBusFrequencyAndReferenceUsesRateLimit() throws Exception {
        Fixture f = initialized(1, -.02, .01);
        double gate0 = f.governor.getGatePosition();
        f.machine.setSpeed(1.0);
        f.machine.getDStabBus().setFreq(.99);
        for (int i = 0; i < 800; i++) step(f.governor, f.machine, .0025);
        assertTrue(f.governor.getGatePosition() > gate0);

        double ref0 = f.governor.getMovingReference();
        f.governor.setRefPoint(ref0 + .1);
        step(f.governor, f.machine, .1);
        assertEquals(ref0 + .001, f.governor.getMovingReference(), TOL);
    }

    private static Fixture initialized(int fd) throws Exception { return initialized(fd, .02, 0.0); }

    private static Fixture initialized(int fd, double tsp, double sprate) throws Exception {
        DStabNetworkBuilder builder = DStabBuilderTestFixture.createWithMachine();
        Machine machine = builder.getDStabNetwork().getMachine("Bus1-mach1");
        machine.setPm(.4); machine.setPe(.4); machine.setSpeed(1.0);
        machine.getDStabBus().setFreq(1.0);
        PsseH6eGovernorData data = data(fd); data.setTsp(tsp); data.setSprate(sprate);
        PsseH6eGovernor governor = builder.addGovH6e("Bus1", "1", data);
        governor.configureIntegrationStep(.0025);
        assertNotNull(governor); assertSame(governor, machine.getGovernor());
        assertTrue(governor.initStates(machine.getDStabBus(), machine));
        return new Fixture(machine, governor);
    }

    private static PsseH6eGovernorData data(int fd) {
        PsseH6eGovernorData d = new PsseH6eGovernorData();
        d.setFd(fd); d.setRe(.05); d.setRg(.05); d.setTpe(.1); d.setTsp(.02);
        d.setKp(2); d.setKi(1); d.setKd(.05); d.setTd(.05);
        d.setVelm(.2); d.setGmax(1); d.setGmin(0); d.setBuf(.1); d.setBuv(.05);
        d.setKg(5); d.setTg(.05); d.setBgvmin(.1); d.setBlv(1);
        d.setHdam(1); d.setTw(.5); d.setTrate(50);
        double[] points = new double[10];
        double[] blades = new double[10];
        for (int i = 0; i < 10; i++) { points[i] = i / 9.0; blades[i] = 1.0; }
        d.setGv(points); d.setPgv(points); d.setBgv(blades);
        return d;
    }

    private static void step(PsseH6eGovernor governor, Machine machine, double dt) {
        governor.nextStep(dt, DynamicSimuMethod.MODIFIED_EULER, machine, 0);
        governor.nextStep(dt, DynamicSimuMethod.MODIFIED_EULER, machine, 1);
    }

    private record Fixture(Machine machine, PsseH6eGovernor governor) { }
}
