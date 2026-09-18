package org.interpss.core.adapter.builder.dstab;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;

import org.interpss.CorePluginTestSetup;
import org.interpss.dstab.control.gov.psse.hygovr.PsseHygovrGovernor;
import org.interpss.dstab.control.gov.psse.hygovr.PsseHygovrGovernorData;
import org.interpss.fadapter.builder.DStabNetworkBuilder;
import org.interpss.fadapter.psse.PSSEDStabDirectParser;
import org.interpss.fadapter.psse.dyr.DynamicModelCatalog;
import org.interpss.fadapter.psse.dyr.DynamicModelImportStatus;
import org.interpss.fadapter.psse.dyr.DynamicModelSupportStatus;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.interpss.dstab.algo.DynamicSimuMethod;
import com.interpss.dstab.mach.Machine;

public class PsseHygovrGovernorTest extends CorePluginTestSetup {
    private static final double TOL = 1.0e-9;

    @Test
    void mapsNativeTwentySixConstantRecord(@TempDir Path tempDir) throws Exception {
        DStabNetworkBuilder builder = DStabBuilderTestFixture.createWithMachine();
        Path dyr = tempDir.resolve("hygovr1.dyr");
        Files.writeString(dyr, "1 'HYGOVR1' 1 0 0 .02 .6 .02 .8 .15 6.5 2 0 0 "
                + ".2 .05 0 4 .1 .077 .077 1 0 0 1.2 1 1 0 10.35 /\n");

        PSSEDStabDirectParser parser = new PSSEDStabDirectParser(builder).setStrictImport(true);
        parser.parseDynFile(dyr.toString());
        PsseHygovrGovernor governor = (PsseHygovrGovernor) builder.getDStabNetwork()
                .getMachine("Bus1-mach1").getGovernor();
        PsseHygovrGovernorData d = governor.getData();

        assertNotNull(governor);
        assertEquals(.02, d.getTd(), TOL); assertEquals(.6, d.getT1(), TOL);
        assertEquals(.02, d.getT2(), TOL); assertEquals(6.5, d.getT5(), TOL);
        assertEquals(2.0, d.getT6(), TOL); assertEquals(.2, d.getKp(), TOL);
        assertEquals(.05, d.getR(), TOL); assertEquals(4.0, d.getKg(), TOL);
        assertEquals(.077, d.getVelopen(), TOL); assertEquals(.077, d.getVelclose(), TOL);
        assertEquals(1.2, d.getTw(), TOL); assertEquals(10.35, d.getTrate(), TOL);
        assertEquals(1, parser.getLastImportReport().count(DynamicModelImportStatus.ATTACHED));
        assertEquals(1, parser.getLastImportReport().aliasConversionCount());
        assertTrue(parser.getLastImportReport().isStrictlyComplete());
    }

    @Test
    void catalogRecognizesNativeAndLegacyNames() {
        var descriptor = DynamicModelCatalog.find("HYGOVR1").orElseThrow();
        assertEquals("HYGOVR", descriptor.canonicalName());
        assertEquals(26, descriptor.parameterCount());
        assertEquals("HYGOVR", DynamicModelCatalog.canonicalName("HYGOVRU"));
        assertEquals(DynamicModelSupportStatus.LOADABLE, descriptor.supportStatus());
    }

    @Test
    void initializesOnTurbineBaseAndHoldsEquilibrium() throws Exception {
        Fixture f = initialized(.4, .4, 50.0);
        assertEquals(50.0, f.governor.getGovernorBaseMva(), TOL);
        assertEquals(.8, f.governor.getGatePosition(), TOL);
        for (int i = 0; i < 800; i++) step(f.governor, f.machine, .0025);
        assertEquals(.4, f.governor.getOutput(f.machine), 2.0e-8);
        assertEquals(.8, f.governor.getGatePosition(), 2.0e-8);
        assertEquals(1.0, f.governor.getHead(), 2.0e-8);
    }

    @Test
    void underfrequencyOpensGateAndRaisesMechanicalPower() throws Exception {
        Fixture f = initialized(.4, .4, 50.0);
        double gate0 = f.governor.getGatePosition();
        f.machine.setSpeed(.99);
        for (int i = 0; i < 8000; i++) step(f.governor, f.machine, .0025);
        assertTrue(f.governor.getGatePosition() > gate0 + 1.0e-4);
        assertTrue(f.governor.getOutput(f.machine) > .4);
    }

    @Test
    void leadLagStepMatchesPublishedTransferFunction() throws Exception {
        DStabNetworkBuilder builder = DStabBuilderTestFixture.createWithMachine();
        Machine machine = builder.getDStabNetwork().getMachine("Bus1-mach1");
        machine.setPm(.4); machine.setPe(.4); machine.setSpeed(1.0);
        PsseHygovrGovernorData data = data(0);
        data.setTd(0); data.setT1(.1); data.setT2(.2);
        data.setT3(0); data.setT4(0); data.setT5(0); data.setT6(0);
        data.setT7(0); data.setT8(0); data.setKp(0);
        PsseHygovrGovernor governor = builder.addGovHygovr1("Bus1", "1", data);
        governor.configureIntegrationStep(.0001);
        assertTrue(governor.initStates(machine.getDStabBus(), machine));

        machine.setSpeed(1.01);
        for (int i = 0; i < 10_000; i++) step(governor, machine, .0001);
        double expected = .01 * (1.0 + (.1 / .2 - 1.0) * Math.exp(-1.0 / .2));
        assertEquals(expected, governor.getProcessedSpeedDeviation(), 2.0e-9);
    }

    @Test
    void positiveCloseRateIsTreatedAsNegativeAndLimitsExpandAtInitialization() throws Exception {
        DStabNetworkBuilder builder = DStabBuilderTestFixture.createWithMachine();
        Machine machine = builder.getDStabNetwork().getMachine("Bus1-mach1");
        machine.setPm(.4); machine.setPe(.4); machine.setSpeed(1.0);
        PsseHygovrGovernorData data = data(0.0);
        data.setPmax(.2); data.setPmin(.7); data.setVelopen(-.08); data.setVelclose(.07);
        data.setTd(.003); data.setTp(.003); data.setTt(.006); data.setTw(.003);
        PsseHygovrGovernor governor = builder.addGovHygovr1("Bus1", "1", data);
        governor.configureIntegrationStep(.01);

        assertTrue(governor.initStates(machine.getDStabBus(), machine));
        assertEquals(.01, governor.getEffectiveTd(), TOL);
        assertEquals(0.0, governor.getEffectiveTp(), TOL);
        assertEquals(.01, governor.getEffectiveTt(), TOL);
        assertEquals(.01, governor.getEffectiveTw(), TOL);
        assertTrue(governor.getEffectivePmax() >= governor.getGatePosition());
        assertTrue(governor.getEffectivePmin() <= governor.getGatePosition());
        assertEquals(.003, data.getTd(), TOL);
        machine.setSpeed(1.01);
        double gate0 = governor.getGatePosition();
        step(governor, machine, 1.0);
        assertTrue(governor.getGatePosition() >= gate0 - .07 - TOL);
    }

    @Test
    void builderAttachesGovernorToExistingMachine() throws Exception {
        DStabNetworkBuilder builder = DStabBuilderTestFixture.createWithMachine();
        Machine machine = builder.getDStabNetwork().getMachine("Bus1-mach1");
        PsseHygovrGovernor governor = builder.addGovHygovr1("Bus1", "1", data(0));
        assertNotNull(governor);
        assertSame(governor, machine.getGovernor());
    }

    private static Fixture initialized(double pm, double pe, double trate) throws Exception {
        DStabNetworkBuilder builder = DStabBuilderTestFixture.createWithMachine();
        Machine machine = builder.getDStabNetwork().getMachine("Bus1-mach1");
        machine.setPm(pm); machine.setPe(pe); machine.setSpeed(1.0);
        PsseHygovrGovernorData data = data(trate);
        PsseHygovrGovernor governor = builder.addGovHygovr1("Bus1", "1", data);
        governor.configureIntegrationStep(.0025);
        assertTrue(governor.initStates(machine.getDStabBus(), machine));
        return new Fixture(machine, governor);
    }

    private static PsseHygovrGovernorData data(double trate) {
        PsseHygovrGovernorData d = new PsseHygovrGovernorData();
        d.setTd(.02); d.setT1(.1); d.setT2(.2); d.setT3(.1); d.setT4(.3);
        d.setT5(.1); d.setT6(.4); d.setT7(.1); d.setT8(.5);
        d.setKp(1); d.setR(.05); d.setTt(.1); d.setKg(4); d.setTp(.05);
        d.setVelopen(.2); d.setVelclose(-.2); d.setPmax(1); d.setPmin(0);
        d.setTw(.5); d.setAt(1); d.setTrate(trate);
        return d;
    }

    private static void step(PsseHygovrGovernor governor, Machine machine, double dt) {
        governor.nextStep(dt, DynamicSimuMethod.MODIFIED_EULER, machine, 0);
        governor.nextStep(dt, DynamicSimuMethod.MODIFIED_EULER, machine, 1);
    }

    private record Fixture(Machine machine, PsseHygovrGovernor governor) { }
}
