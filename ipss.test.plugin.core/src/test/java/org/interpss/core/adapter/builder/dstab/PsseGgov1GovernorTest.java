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
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import com.interpss.dstab.algo.DynamicSimuMethod;
import com.interpss.dstab.mach.Machine;

public class PsseGgov1GovernorTest extends CorePluginTestSetup {
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
    void ggov1duParserMapsFinalDeadbandFieldsAndDeadbandsOnlyFrequency(@TempDir Path tempDir)
            throws Exception {
        DStabNetworkBuilder builder = DStabBuilderTestFixture.createWithMachine();
        Path dyr = tempDir.resolve("ggov1du.dyr");
        Files.writeString(dyr, "1 'GGOV1DU' 1 0 0 0.0 1 0.05 -0.05 4.18 0.71 0 1 "
                + "1 0.15 0.4 1.22 0.18 0.16 0 0 3 0 0 1 0 1 -1 0 "
                + "0.01 0 0.1 50.0 0 4 5 99 -99 0.002 -0.003 /\n");

        PSSEDStabDirectParser parser = new PSSEDStabDirectParser(builder).setStrictImport(true);
        parser.parseDynFile(dyr.toString());
        Machine machine = builder.getDStabNetwork().getMachine("Bus1-mach1");
        machine.setSpeed(1.0);
        machine.setPm(0.4);
        machine.setPe(0.4);
        PsseGgov1Governor governor = (PsseGgov1Governor) machine.getGovernor();

        assertNotNull(governor);
        assertEquals("GGOV1D", governor.getName());
        assertEquals(0.002, governor.getData().getDbH(), TOL);
        assertEquals(-0.003, governor.getData().getDbL(), TOL);
        assertTrue(governor.initStates(builder.getDStabNetwork().getDStabBus("Bus1"), machine));
        assertEquals(0.0, governor.applyFrequencyDeadband(-0.002), TOL);
        assertEquals(-0.007, governor.applyFrequencyDeadband(-0.010), TOL);
        assertEquals(0.008, governor.applyFrequencyDeadband(0.010), TOL);
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

    @ParameterizedTest(name = "GGOV1 Rselect={0}, Flag={1}")
    @CsvSource({"1,0", "1,1", "0,0", "0,1", "-1,0", "-1,1", "-2,0", "-2,1"})
    void supportsEveryDocumentedDroopAndFuelSourceCombination(int rselect, int flag)
            throws Exception {
        DStabNetworkBuilder builder = DStabBuilderTestFixture.createWithMachine();
        Machine machine = builder.getDStabNetwork().getMachine("Bus1-mach1");
        machine.setSpeed(1.0);
        machine.setPm(0.4);
        machine.setPe(0.4);
        PsseGgov1GovernorData data = texasData();
        data.setRselect(rselect);
        data.setFlag(flag);
        if (rselect == 0) data.setR(0.0);
        PsseGgov1Governor governor = builder.addGovGgov1("Bus1", "1", data);

        assertNotNull(governor);
        assertTrue(governor.initStates(machine.getDStabBus(), machine));
        machine.setSpeed(0.995);
        for (int i = 0; i < 20; i++) step(governor, machine, 0.005);
        assertTrue(Double.isFinite(governor.getOutput(machine)));
        assertTrue(Double.isFinite(governor.getDroopFeedback()));
        assertTrue(Double.isFinite(governor.getFuelFlow()));
    }

    @Test
    void rselectUsesPublishedElectricalValveAndGovernorOutputSignals() throws Exception {
        PsseGgov1Governor electrical = initializedGovernor(1, 0);
        assertEquals(0.4 / 3.9024, electrical.getDroopFeedback(), TOL,
                "Electrical-power droop feedback is expressed on the Trate governor base");

        PsseGgov1Governor valve = initializedGovernor(-1, 0);
        valve.getMachine().setSpeed(0.99);
        for (int i = 0; i < 20; i++) step(valve, valve.getMachine(), 0.005);
        assertEquals(valve.getValveStroke(), valve.getDroopFeedback(), TOL);

        PsseGgov1Governor governorOutput = initializedGovernor(-2, 0);
        governorOutput.getMachine().setSpeed(0.99);
        for (int i = 0; i < 20; i++) step(governorOutput, governorOutput.getMachine(), 0.005);
        assertEquals(governorOutput.getFsr(), governorOutput.getDroopFeedback(), TOL);
        assertTrue(Math.abs(governorOutput.getFsr() - governorOutput.getValveStroke()) > 1.0e-5,
                "Requested and true valve stroke must diverge to distinguish Rselect -2 from -1");

        PsseGgov1Governor isochronous = initializedGovernor(0, 0);
        assertEquals(0.0, isochronous.getDroopFeedback(), TOL);
    }

    @Test
    void flagOneMakesFuelFlowProportionalToShaftSpeed() throws Exception {
        PsseGgov1Governor independent = initializedGovernor(1, 0);
        PsseGgov1Governor proportional = initializedGovernor(1, 1);
        independent.getMachine().setSpeed(0.98);
        proportional.getMachine().setSpeed(0.98);

        assertEquals(independent.getValveStroke(), independent.getFuelFlow(), TOL);
        assertEquals(0.98 * proportional.getValveStroke(), proportional.getFuelFlow(), TOL);
    }

    @Test
    void normalizesPowerWorldLimitsWithoutOverwritingImportedData() throws Exception {
        DStabNetworkBuilder builder = DStabBuilderTestFixture.createWithMachine();
        Machine machine = builder.getDStabNetwork().getMachine("Bus1-mach1");
        machine.setPm(0.4);
        machine.setPe(0.4);
        PsseGgov1GovernorData data = texasData();
        data.setMaxerr(-0.08);
        data.setMinerr(0.04);
        data.setVmax(0.1);
        data.setVmin(1.2);
        data.setRopen(1.0);
        data.setRclose(-0.05);
        PsseGgov1Governor governor = builder.addGovGgov1("Bus1", "1", data);

        assertTrue(governor.initStates(machine.getDStabBus(), machine));
        assertEquals(0.04, governor.getEffectiveMaxerr(), TOL);
        assertEquals(-0.08, governor.getEffectiveMinerr(), TOL);
        assertEquals(1.0, governor.getEffectiveVmax(), TOL);
        assertTrue(governor.getEffectiveVmin() <= governor.getValveStroke());
        assertEquals(0.1, governor.getEffectiveRopen(), TOL);
        assertEquals(-0.1, governor.getEffectiveRclose(), TOL);

        assertEquals(-0.08, governor.getData().getMaxerr(), TOL);
        assertEquals(0.04, governor.getData().getMinerr(), TOL);
        assertEquals(0.1, governor.getData().getVmax(), TOL);
        assertEquals(1.2, governor.getData().getVmin(), TOL);
        assertEquals(1.0, governor.getData().getRopen(), TOL);
        assertEquals(-0.05, governor.getData().getRclose(), TOL);
    }

    @Test
    void correctedOpeningRateBoundsValveMotion() throws Exception {
        PsseGgov1Governor governor = initializedGovernor(1, 0);
        Machine machine = governor.getMachine();
        double initialValve = governor.getValveStroke();
        machine.setSpeed(0.90);

        double elapsed = 0.2;
        for (int i = 0; i < 40; i++) step(governor, machine, 0.005);

        assertTrue(governor.getValveStroke() > initialValve);
        assertTrue(governor.getValveStroke() <= initialValve
                + governor.getEffectiveRopen() * elapsed + 1.0e-9);
    }

    @Test
    void supervisoryLoadControlRaisesFuelRequestWhenElectricalPowerFalls() throws Exception {
        DStabNetworkBuilder builder = DStabBuilderTestFixture.createWithMachine();
        Machine machine = builder.getDStabNetwork().getMachine("Bus1-mach1");
        machine.setPm(0.4);
        machine.setPe(0.4);
        PsseGgov1GovernorData data = texasData();
        data.setRselect(0);
        // GGOV1 limits the supervisory MW integrator to +/-1.1R, even when
        // Rselect=0 disables the primary droop-feedback signal.
        data.setR(0.05);
        data.setKimw(0.5);
        PsseGgov1Governor governor = builder.addGovGgov1("Bus1", "1", data);
        assertTrue(governor.initStates(machine.getDStabBus(), machine));
        double initialValve = governor.getValveStroke();

        machine.setPe(0.3);
        for (int i = 0; i < 200; i++) step(governor, machine, 0.005);

        assertTrue(governor.getValveStroke() > initialValve);
        assertTrue(governor.getOutput(machine) > 0.4);
    }

    @Test
    void loadRejectionClosesValveWithinRateAndPositionLimits() throws Exception {
        PsseGgov1Governor governor = initializedGovernor(1, 0);
        Machine machine = governor.getMachine();
        double initialValve = governor.getValveStroke();
        machine.setSpeed(1.10);

        double elapsed = 0.2;
        for (int i = 0; i < 40; i++) step(governor, machine, 0.005);

        assertTrue(governor.getValveStroke() < initialValve);
        assertTrue(governor.getValveStroke() >= initialValve
                + governor.getEffectiveRclose() * elapsed - 1.0e-9);
        assertTrue(governor.getValveStroke() >= governor.getEffectiveVmin() - TOL);
    }

    @Test
    void valveRemainsInsideExpandedUpperAndLowerPositionLimits() throws Exception {
        PsseGgov1Governor governor = initializedGovernor(1, 0);
        Machine machine = governor.getMachine();

        machine.setSpeed(0.8);
        for (int i = 0; i < 3000; i++) step(governor, machine, 0.005);
        assertTrue(governor.getValveStroke() <= governor.getEffectiveVmax() + TOL);

        machine.setSpeed(1.2);
        for (int i = 0; i < 3000; i++) step(governor, machine, 0.005);
        assertTrue(governor.getValveStroke() >= governor.getEffectiveVmin() - TOL);
    }

    private static PsseGgov1Governor initializedGovernor(int rselect, int flag)
            throws Exception {
        DStabNetworkBuilder builder = DStabBuilderTestFixture.createWithMachine();
        Machine machine = builder.getDStabNetwork().getMachine("Bus1-mach1");
        machine.setSpeed(1.0);
        machine.setPm(0.4);
        machine.setPe(0.4);
        PsseGgov1GovernorData data = texasData();
        data.setRselect(rselect);
        data.setFlag(flag);
        if (rselect == 0) data.setR(0.0);
        PsseGgov1Governor governor = builder.addGovGgov1("Bus1", "1", data);
        assertNotNull(governor);
        assertTrue(governor.initStates(machine.getDStabBus(), machine));
        return governor;
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
