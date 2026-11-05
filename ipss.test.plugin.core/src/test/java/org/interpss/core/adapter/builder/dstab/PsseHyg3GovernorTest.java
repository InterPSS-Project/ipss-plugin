package org.interpss.core.adapter.builder.dstab;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;

import org.interpss.CorePluginTestSetup;
import org.interpss.dstab.control.gov.psse.hyg3.PsseHyg3Governor;
import org.interpss.dstab.control.gov.psse.hyg3.PsseHyg3GovernorData;
import org.interpss.fadapter.builder.DStabNetworkBuilder;
import org.interpss.fadapter.psse.PSSEDStabDirectParser;
import org.interpss.fadapter.psse.dyr.DynamicModelCatalog;
import org.interpss.fadapter.psse.dyr.DynamicModelImportStatus;
import org.interpss.fadapter.psse.dyr.DynamicModelSupportStatus;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.interpss.dstab.algo.DynamicSimuMethod;
import com.interpss.dstab.mach.Machine;

public class PsseHyg3GovernorTest extends CorePluginTestSetup {
    private static final double TOL = 1.0e-9;

    @Test
    void directParserMapsTheHyg3u1PidIconAndAllThirtySixConstants(@TempDir Path tempDir)
            throws Exception {
        DStabNetworkBuilder builder = DStabBuilderTestFixture.createWithMachine();
        Path dyr = tempDir.resolve("hyg3u1.dyr");
        Files.writeString(dyr, "1 'HYG3U1' 1 1 "
                + "0 .0527 2 0 1.5 4.5 0 .05 11 .15 .1 -.1 .918 0 0 "
                + ".292 .374 .473 .603 .528 .681 .686 .820 .799 .893 .856 .929 "
                + "1 .153 .230 1.181 .5 11.6 .036 0 -.036 /\n");

        PSSEDStabDirectParser parser = new PSSEDStabDirectParser(builder).setStrictImport(true);
        parser.parseDynFile(dyr.toString());
        PsseHyg3Governor governor = (PsseHyg3Governor) builder.getDStabNetwork()
                .getMachine("Bus1-mach1").getGovernor();

        assertNotNull(governor);
        assertEquals(PsseHyg3GovernorData.PID_CONTROL,
                governor.getData().getControlFlag());
        assertEquals(PsseHyg3GovernorData.BacklashConvention.PER_UNIT_GATE,
                governor.getData().getBacklashConvention());
        assertEquals(0.0, governor.getData().getRgate(), TOL);
        assertEquals(.0527, governor.getData().getRelec(), TOL);
        assertEquals(2.0, governor.getData().getTt(), TOL);
        assertEquals(1.5, governor.getData().getK2(), TOL);
        assertEquals(4.5, governor.getData().getKi(), TOL);
        assertEquals(.292, governor.getData().getGv(0), TOL);
        assertEquals(.929, governor.getData().getPgv(5), TOL);
        assertEquals(1.0, governor.getData().getH0(), TOL);
        assertEquals(.153, governor.getData().getQnl(), TOL);
        assertEquals(.230, governor.getData().getTw(), TOL);
        assertEquals(1.181, governor.getData().getAt(), TOL);
        assertEquals(11.6, governor.getData().getTrate(), TOL);
        assertEquals(.036, governor.getData().getDbH(), TOL);
        assertEquals(-.036, governor.getData().getDbL(), TOL);
        assertEquals(1, parser.getLastImportReport().count(DynamicModelImportStatus.ATTACHED));
        assertEquals(1, parser.getLastImportReport().aliasConversionCount());
        assertTrue(parser.getLastImportReport().isStrictlyComplete());
    }

    @Test
    void builderPreservesIndependentTypedAndImportedBacklashConventions() throws Exception {
        assertEquals(PsseHyg3GovernorData.BacklashConvention.MW_ON_GOVERNOR_BASE,
                new PsseHyg3GovernorData().getBacklashConvention());
        for (var convention : PsseHyg3GovernorData.BacklashConvention.values()) {
            var builder=DStabBuilderTestFixture.createWithMachine();
            var data=data(PsseHyg3GovernorData.PID_CONTROL); data.setDb2(.02); data.setBacklashConvention(convention);
            data.setFrequencyDeadbandConvention(PsseHyg3GovernorData.FrequencyDeadbandConvention.HZ_HYSTERESIS);
            var governor=builder.addGovHyg3("Bus1","1",data);
            assertEquals(convention,governor.getData().getBacklashConvention());
            assertEquals(data.getFrequencyDeadbandConvention(),governor.getData().getFrequencyDeadbandConvention());
            assertEquals(.02,governor.getData().getDb2(),0.);
        }
    }

    @Test
    void namedNumericEditsReachBothControllerBranchesThroughBuilder() throws Exception {
        String[] names = {"Rgate", "Relec", "Tt", "Td", "K2", "Ki", "K1", "Tf",
                "Kg", "Tp", "Velopen", "Velclose", "Pmax", "Pmin", "Db2",
                "H0", "Qnl", "Tw", "At", "Dturb", "Trate", "DbH", "Eps", "DbL"};
        double[] values = {.07, .03, .16, .04, .18, 1.9, .22, .08,
                4.8, .09, .24, -.23, 1.1, .01, .005,
                1.05, .11, .9, 1.3, .12, 55., .014, .001, -.016};
        for (int flag : new int[]{PsseHyg3GovernorData.PID_CONTROL,
                PsseHyg3GovernorData.DOUBLE_DERIVATIVE_CONTROL}) {
          for (boolean staggered : new boolean[]{false, true}) {
            var data = new PsseHyg3GovernorData();
            data.setValue("ControlFlag", flag);
            data.setValue("cflag", (double)flag);
            org.junit.jupiter.api.Assertions.assertThrows(IllegalArgumentException.class,
                    () -> data.setValue("cflag", .5));
            assertEquals(flag, data.getControlFlag());
            for (int i = 0; i < names.length; i++) data.setValue(names[i], values[i]);
            double[] gate = {.1, .25, .5, .7, .85, 1.};
            for (int i = 0; i < gate.length; i++) {
                data.setValue("GV" + (i + 1), gate[i]);
                data.setValue("PGV" + (i + 1), gate[i]);
            }
            var builder = DStabBuilderTestFixture.createWithMachine();
            var machine = builder.getDStabNetwork().getMachine("Bus1-mach1");
            machine.setPm(.4); machine.setPe(.4); machine.setSpeed(1.);
            var governor = builder.addGovHyg3("Bus1", "1", data);
            assertNotNull(governor);
            var attached = governor.getData();
            assertEquals(flag, attached.getControlFlag());
            assertArrayEquals(values, new double[]{attached.getRgate(), attached.getRelec(),
                    attached.getTt(), attached.getTd(), attached.getK2(), attached.getKi(),
                    attached.getK1(), attached.getTf(), attached.getKg(), attached.getTp(),
                    attached.getVelopen(), attached.getVelclose(), attached.getPmax(), attached.getPmin(),
                    attached.getDb2(), attached.getH0(), attached.getQnl(), attached.getTw(),
                    attached.getAt(), attached.getDturb(), attached.getTrate(), attached.getDbH(),
                    attached.getEps(), attached.getDbL()}, 0.);
            assertArrayEquals(gate, attached.getGv(), 0.);
            assertArrayEquals(gate, attached.getPgv(), 0.);
            governor.configureIntegrationStep(.005);
            assertTrue(governor.initStates(machine.getDStabBus(), machine));
            machine.setSpeed(.99);
            if (staggered) {
                double[] states = new double[governor.staggeredStateCount()];
                double[] rates = new double[states.length];
                governor.readStaggeredState(states, 0);
                governor.evaluateStaggered(.005, rates, 0);
                for (int i = 0; i < states.length; i++) states[i] += .005 * rates[i];
                governor.applyStaggeredState(states, 0, .005);
                governor.completeStaggeredStep(.005);
            } else step(governor, machine, .005);
            assertTrue(Double.isFinite(governor.getOutput(machine)));
          }
        }
    }

    @Test
    void catalogDeclaresTheEffectiveRecordShapeAndApprovedMapping() {
        var descriptor = DynamicModelCatalog.find("HYG3U1").orElseThrow();
        assertEquals("HYG3", descriptor.canonicalName());
        assertEquals(37, descriptor.recordSchema().primaryParameterCount());
        assertEquals(DynamicModelSupportStatus.LOADABLE, descriptor.supportStatus());
    }

    @Test
    void pidAndDoubleDerivativeBranchesInitializeOnTrateBaseWithoutDrift()
            throws Exception {
        for (int flag : new int[] {PsseHyg3GovernorData.PID_CONTROL,
                PsseHyg3GovernorData.DOUBLE_DERIVATIVE_CONTROL}) {
            Fixture fixture = initialized(flag);
            assertEquals(50.0, fixture.governor.getGovernorBaseMva(), TOL);
            assertEquals(0.4, fixture.governor.getOutput(fixture.machine), TOL);
            double gate0 = fixture.governor.getGateOutput();
            for (int i = 0; i < 400; i++) step(fixture.governor, fixture.machine, .005);
            assertEquals(0.4, fixture.governor.getOutput(fixture.machine), 2.0e-8);
            assertEquals(gate0, fixture.governor.getGateOutput(), 2.0e-8);
        }
    }

    @Test
    void bothPublishedControllerBranchesOpenTheGateForUnderfrequency() throws Exception {
        Fixture pid = initialized(PsseHyg3GovernorData.PID_CONTROL);
        Fixture derivative = initialized(PsseHyg3GovernorData.DOUBLE_DERIVATIVE_CONTROL);
        double pidGate0 = pid.governor.getGatePosition();
        double ddGate0 = derivative.governor.getGatePosition();
        pid.machine.setSpeed(.99);
        derivative.machine.setSpeed(.99);

        for (int i = 0; i < 400; i++) {
            step(pid.governor, pid.machine, .005);
            step(derivative.governor, derivative.machine, .005);
        }

        assertTrue(pid.governor.getGatePosition() > pidGate0);
        assertTrue(derivative.governor.getGatePosition() > ddGate0);
        assertTrue(pid.governor.getOutput(pid.machine) > .4);
        assertTrue(derivative.governor.getOutput(derivative.machine) > .4);
    }

    @Test
    void nonlinearCurveUsesSixPointsImplicitEndpointsAndDocumentedBuiltinCurve()
            throws Exception {
        Fixture fixture = initialized(PsseHyg3GovernorData.PID_CONTROL);
        assertEquals(0.0, fixture.governor.gateCurve(0.0), TOL);
        assertEquals(0.5, fixture.governor.gateCurve(0.5), TOL);
        assertEquals(1.0, fixture.governor.gateCurve(1.0), TOL);
        assertEquals(0.625, fixture.governor.inverseGateCurve(0.625), TOL);

        fixture.governor.getData().setGv(0, -1.0);
        assertEquals(0.20, fixture.governor.gateCurve(.25), TOL);
        assertEquals(.67, fixture.governor.inverseGateCurve(.80), TOL);
    }

    @Test
    void appliesPowerWorldCorrectionsDeadbandUnitsAndDynamicLimitExpansion()
            throws Exception {
        DStabNetworkBuilder builder = DStabBuilderTestFixture.createWithMachine();
        Machine machine = builder.getDStabNetwork().getMachine("Bus1-mach1");
        machine.setPm(.01); machine.setPe(.01); machine.setSpeed(1.0);
        PsseHyg3GovernorData data = data(PsseHyg3GovernorData.PID_CONTROL);
        data.setAt(0.0);
        data.setTw(.005);
        data.setPmax(.1);
        data.setPmin(.8);
        data.setDbH(.036);
        data.setDbL(-.036);
        PsseHyg3Governor governor = builder.addGovHyg3("Bus1", "1", data);
        governor.configureIntegrationStep(.01);

        assertTrue(governor.initStates(machine.getDStabBus(), machine));
        assertEquals(.01, governor.getEffectiveAt(), TOL);
        assertEquals(.01, governor.getEffectiveTw(), TOL);
        assertTrue(governor.getEffectivePmax() >= governor.getGatePosition());
        assertTrue(governor.getEffectivePmin() <= governor.getGatePosition());
        assertEquals(0.0, governor.applyFrequencyDeadband(.0005), TOL,
                ".0005 pu is .03 Hz and remains inside a .036 Hz deadband");
        double expected = .001 - .036 / machine.getDStabBus().getNetwork().getFrequency();
        assertEquals(expected, governor.applyFrequencyDeadband(.001), TOL,
                "The documented Hz deadband must use the network base frequency");
        assertEquals(0.0, data.getAt(), TOL, "autocorrection must not overwrite imported data");
        assertEquals(.005, data.getTw(), TOL);
    }

    @Test
    void turbineDampingUsesGateAndTrateBaseExactly() throws Exception {
        Fixture fixture = initialized(PsseHyg3GovernorData.PID_CONTROL);
        double gate = fixture.governor.getGateOutput();
        fixture.machine.setSpeed(1.01);
        step(fixture.governor, fixture.machine, 0.0);

        double governorBaseDamping = fixture.governor.getData().getDturb() * .01 * gate;
        double expectedMachineBase = .4 - governorBaseDamping * .5;
        assertEquals(expectedMachineBase, fixture.governor.getOutput(fixture.machine), TOL);
    }

    @Test
    void servoRateLimitAndMwBacklashActBeforeTheNonlinearTurbine() throws Exception {
        DStabNetworkBuilder builder = DStabBuilderTestFixture.createWithMachine();
        Machine machine = builder.getDStabNetwork().getMachine("Bus1-mach1");
        machine.setPm(.4); machine.setPe(.4); machine.setSpeed(1.0);
        PsseHyg3GovernorData data = data(PsseHyg3GovernorData.PID_CONTROL);
        data.setDb2(5.0); // 5 MW / 50 MVA = 0.1 pu backlash.
        PsseHyg3Governor governor = builder.addGovHyg3("Bus1", "1", data);
        assertTrue(governor.initStates(machine.getDStabBus(), machine));
        double gate0 = governor.getGatePosition();
        double outputGate0 = governor.getGateOutput();
        governor.setAuxiliaryInput(5.0);

        for (int i = 0; i < 50; i++) step(governor, machine, .005);
        assertTrue(governor.getGatePosition() > gate0);
        assertTrue(governor.getGatePosition() <= gate0 + data.getVelopen() * .25 + TOL);
        assertEquals(outputGate0, governor.getGateOutput(), TOL,
                "Gate output must remain fixed inside the 0.1 pu backlash band");

        for (int i = 0; i < 1000; i++) step(governor, machine, .005);
        assertTrue(governor.getGateOutput() > outputGate0,
                "Gate output must move after the accumulated servo travel exceeds backlash");
        assertTrue(governor.getGatePosition() <= governor.getEffectivePmax() + TOL);
    }

    private static Fixture initialized(int flag) throws Exception {
        DStabNetworkBuilder builder = DStabBuilderTestFixture.createWithMachine();
        Machine machine = builder.getDStabNetwork().getMachine("Bus1-mach1");
        machine.setPm(.4); machine.setPe(.4); machine.setSpeed(1.0);
        PsseHyg3Governor governor = builder.addGovHyg3("Bus1", "1", data(flag));
        governor.configureIntegrationStep(.005);
        assertNotNull(governor);
        assertSame(governor, machine.getGovernor());
        assertTrue(governor.initStates(machine.getDStabBus(), machine));
        return new Fixture(machine, governor);
    }

    private static PsseHyg3GovernorData data(int flag) {
        PsseHyg3GovernorData data = new PsseHyg3GovernorData();
        data.setControlFlag(flag);
        data.setRgate(.05); data.setRelec(.02); data.setTt(.2); data.setTd(.05);
        data.setK2(flag == PsseHyg3GovernorData.PID_CONTROL ? 1.0 : .2);
        data.setKi(2.0); data.setK1(.2); data.setTf(.1);
        data.setKg(5.0); data.setTp(.1); data.setVelopen(.2); data.setVelclose(-.2);
        data.setPmax(1.0); data.setPmin(0.0); data.setDb2(0.0);
        data.setGv(new double[] {.1, .25, .5, .7, .85, 1.0});
        data.setPgv(new double[] {.1, .25, .5, .7, .85, 1.0});
        data.setH0(1.0); data.setQnl(.1); data.setTw(1.0);
        data.setAt(1.2); data.setDturb(.1); data.setTrate(50.0);
        return data;
    }

    private static void step(PsseHyg3Governor governor, Machine machine, double dt) {
        governor.nextStep(dt, DynamicSimuMethod.MODIFIED_EULER, machine, 0);
        governor.nextStep(dt, DynamicSimuMethod.MODIFIED_EULER, machine, 1);
    }

    private record Fixture(Machine machine, PsseHyg3Governor governor) { }
}
