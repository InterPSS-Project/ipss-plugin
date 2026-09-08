package org.interpss.core.adapter.builder.dstab;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;

import org.apache.commons.math3.complex.Complex;
import org.interpss.CorePluginTestSetup;
import org.interpss.dstab.control.exc.psse.esdc1a.Esdc1aExciter;
import org.interpss.dstab.control.exc.psse.esdc2a.Esdc2aData;
import org.interpss.dstab.control.exc.psse.esdc2a.Esdc2aExciter;
import org.interpss.fadapter.builder.DStabNetworkBuilder;
import org.interpss.fadapter.psse.PSSEDStabDirectParser;
import org.interpss.fadapter.psse.dyr.WeccApprovedDynamicModelCatalog;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.interpss.dstab.DStabObjectFactory;
import com.interpss.dstab.algo.DynamicSimuAlgorithm;
import com.interpss.dstab.algo.DynamicSimuMethod;
import com.interpss.dstab.cache.StateMonitor;
import com.interpss.dstab.mach.Machine;

/** Native PSS/E ESDC1A/ESDC2A import and equation conformance tests. */
public class DStabNetworkBuilderEsdc1a2aTest extends CorePluginTestSetup {
    private static final double TOL = 1.0e-10;

    @Test
    void exactSixteenParameterRecordsPreserveUnusedSwitch(@TempDir Path dir)
            throws Exception {
        for (String model : new String[] {"ESDC1A", "ESDC2A"}) {
            DStabNetworkBuilder builder = DStabBuilderTestFixture.createWithMachine();
            Path dyr = dir.resolve(model.toLowerCase() + ".dyr");
            Files.writeString(dyr, "1 '" + model
                    + "' '1' .02 50 .05 .2 .1 5 -5 1 .4 .1 .3 1 3 .1 4 .2 /\n");
            PSSEDStabDirectParser parser = new PSSEDStabDirectParser(builder)
                    .setStrictImport(true);
            parser.parseDynFile(dyr.toString());
            Esdc2aExciter exciter = (Esdc2aExciter) builder.getDStabNetwork()
                    .getMachine("Bus1-mach1").getExciter();
            assertNotNull(exciter);
            assertEquals(1.0, exciter.getData().getSwitchValue(), TOL);
            assertTrue(parser.getLastImportReport().isStrictlyComplete());
        }
        assertTrue(WeccApprovedDynamicModelCatalog.findExciter("ESDC1A")
                .orElseThrow().isImplementedExactly());
        assertTrue(WeccApprovedDynamicModelCatalog.findExciter("ESDC2A")
                .orElseThrow().isImplementedExactly());
    }

    @Test
    void switchDoesNotActAsTheFormerIncorrectSpeedMultiplier() throws Exception {
        Fixture fixture = fixture(false, dynamicData());
        fixture.machine.setSpeed(.95);
        assertEquals(1.2, fixture.exciter.getOutput(fixture.machine), TOL);
        assertEquals(1.0, fixture.exciter.switchValue, TOL);
    }

    @Test
    void typedPowerWorldSpeedMultiplierIsSeparateFromPsseSwitch() throws Exception {
        Esdc2aData data = dynamicData();
        data.setSwitchValue(1.0);
        data.setSpdmlt(1.0);
        DStabNetworkBuilder builder = DStabBuilderTestFixture.createWithMachine();
        Machine machine = builder.getDStabNetwork().getMachine("Bus1-mach1");
        machine.setSpeed(1.0);
        machine.setEfd(1.2);
        Esdc2aExciter exciter = new Esdc2aExciter("Bus1-mach1_Exc", data, machine);
        assertTrue(exciter.initStates(machine.getDStabBus(), machine));

        machine.setSpeed(.95);
        assertEquals(1.14, exciter.getOutput(machine), TOL);
        assertEquals(1.0, exciter.getData().getSwitchValue(), TOL);
        assertEquals(1.0, exciter.getData().getSpdmlt(), TOL);
    }

    @Test
    void bothModelsMatchIndependentFiveStateDiagramOracle() throws Exception {
        assertTrue(maxTrajectoryError(false) < 1.0e-9);
        assertTrue(maxTrajectoryError(true) < 1.0e-9);
    }

    @Test
    void esdc1aUsesConstantAndEsdc2aUsesTerminalScaledRegulatorLimits()
            throws Exception {
        Fixture dc1 = fixture(true, algebraicData());
        Fixture dc2 = fixture(false, algebraicData());
        dc1.machine.getDStabBus().setVoltage(new Complex(.8, 0.0));
        dc2.machine.getDStabBus().setVoltage(new Complex(.8, 0.0));
        assertEquals(5.0, dc1.exciter.getDynamicRegulatorUpperLimit(), TOL);
        assertEquals(4.0, dc2.exciter.getDynamicRegulatorUpperLimit(), TOL);
        assertEquals(-5.0, dc1.exciter.getDynamicRegulatorLowerLimit(), TOL);
        assertEquals(-4.0, dc2.exciter.getDynamicRegulatorLowerLimit(), TOL);
    }

    @Test
    void algebraicZeroTimeBlocksSolveWithoutStartupJump() throws Exception {
        Esdc2aData data = algebraicData();
        data.setTa(0.0);
        data.setTb(0.0);
        data.setTe(0.0);
        data.setTf(0.0);
        Fixture fixture = fixture(true, data);
        fixture.exciter.setRefPoint(fixture.exciter.getRefPoint() + .1);
        assertTrue(fixture.exciter.nextStep(0.0, DynamicSimuMethod.MODIFIED_EULER,
                fixture.machine, 0));
        assertEquals(2.2, fixture.exciter.getOutput(fixture.machine), 1.0e-9);
    }

    @Test
    void appliesPublishedTimeCorrectionsAndInitializationLimitExpansion()
            throws Exception {
        Esdc2aData data = dynamicData();
        data.setTr(.03);
        data.setTa(.01);
        data.setTe(.01);
        data.setTf(.01);
        data.setVrmax(-2.0);
        data.setVrmin(-3.0);
        DStabNetworkBuilder builder = DStabBuilderTestFixture.createWithMachine();
        Esdc2aExciter exciter = builder.addExcEsdc1a("Bus1", "1",
                data.getTr(), data.getKa(), data.getTa(), data.getTc(), data.getTb(),
                data.getVrmax(), data.getVrmin(), data.getKe(), data.getTe(),
                data.getKf(), data.getTf(), data.getSwitchValue(), data.getE1(),
                data.getSe1(), data.getE2(), data.getSe2());
        Machine machine = builder.getDStabNetwork().getMachine("Bus1-mach1");
        machine.setEfd(1.2);
        exciter.configureIntegrationStep(.1, 1.0);
        assertTrue(exciter.initStates(machine.getDStabBus(), machine));
        assertEquals(.05, exciter.tr, TOL);
        assertEquals(.1, exciter.ta, TOL);
        assertEquals(.1, exciter.te, TOL);
        assertEquals(.1, exciter.tf, TOL);
        assertTrue(exciter.vrmaxVt >= exciter.getRegulatorOutput());
        assertEquals(-3.0, exciter.vrminVt, TOL);

        data.setTr(.01);
        data.setTf(-1.0);
        Fixture bypass = fixture(true, data);
        bypass.exciter.configureIntegrationStep(.1, 1.0);
        assertTrue(bypass.exciter.initStates(bypass.machine.getDStabBus(), bypass.machine));
        assertEquals(0.0, bypass.exciter.tr, TOL);
        assertEquals(0.0, bypass.exciter.tf, TOL);
    }

    @Test
    void regulatorLimitIsNonWindupAndModelParticipatesInSolver() throws Exception {
        Esdc2aData data = dynamicData();
        data.setVrmax(1.21);
        Fixture fixture = fixture(true, data);
        fixture.exciter.setRefPoint(fixture.exciter.getRefPoint() + 1.0);
        for (int i = 0; i < 100; i++) step(fixture.exciter, fixture.machine, .001);
        assertEquals(1.21, fixture.exciter.getRegulatorOutput(), 1.0e-8);

        DStabNetworkBuilder builder = DStabBuilderTestFixture.createWithMachine();
        Esdc2aExciter solverExciter = builder.addExcEsdc2a("Bus1", "1",
                .02, 20, .05, .05, .2, 5, -5, 1, .4, .1, .3,
                0, 0, 0, 0, 0);
        assertNotNull(solverExciter);
        DynamicSimuAlgorithm algorithm = DStabObjectFactory
                .createDynamicSimuAlgorithm(builder.getDStabNetwork());
        algorithm.setSimuMethod(DynamicSimuMethod.MODIFIED_EULER);
        algorithm.setSimuStepSec(.005);
        algorithm.setTotalSimuTimeSec(.02);
        algorithm.setSimuOutputHandler(new StateMonitor());
        assertTrue(algorithm.getAclfAlgorithm().loadflow());
        assertTrue(algorithm.initialization());
        assertTrue(algorithm.performSimulation());
    }

    private static double maxTrajectoryError(boolean dc1) throws Exception {
        Fixture fixture = fixture(dc1, dynamicData());
        double vt = fixture.machine.getDStabBus().getVoltageMag();
        fixture.exciter.setRefPoint(fixture.exciter.getRefPoint() + .01);
        double[] state = {1.2, vt, 1.2, 1.2, 1.2 / 20.0};
        double dt = .0001;
        double maximum = 0.0;
        for (int i = 0; i < 1000; i++) {
            double[] first = derivatives(state, vt, .01);
            double[] predicted = add(state, first, dt);
            double[] second = derivatives(predicted, vt, .01);
            for (int j = 0; j < state.length; j++) {
                state[j] += .5 * (first[j] + second[j]) * dt;
            }
            step(fixture.exciter, fixture.machine, dt);
            double[] actual = {fixture.exciter.getInternalFieldVoltage(),
                    fixture.exciter.getSensedVoltage(), fixture.exciter.getRegulatorOutput(),
                    fixture.exciter.getWashoutLagState(), fixture.exciter.getLeadLagState()};
            for (int j = 0; j < state.length; j++) {
                maximum = Math.max(maximum, Math.abs(state[j] - actual[j]));
            }
        }
        return maximum;
    }

    private static double[] derivatives(double[] state, double vt, double referenceStep) {
        double washout = .1 * (state[0] - state[3]) / .3;
        double error = vt + 1.2 / 20.0 + referenceStep - state[1] - washout;
        double leadLag = .05 / .2 * error + (1.0 - .05 / .2) * state[4];
        return new double[] {(state[2] - state[0]) / .4,
                (vt - state[1]) / .1, (20.0 * leadLag - state[2]) / .05,
                (state[0] - state[3]) / .3, (error - state[4]) / .2};
    }

    private static Fixture fixture(boolean dc1, Esdc2aData data) throws Exception {
        DStabNetworkBuilder builder = DStabBuilderTestFixture.createWithMachine();
        Esdc2aExciter exciter = dc1
                ? builder.addExcEsdc1a("Bus1", "1", data.getTr(), data.getKa(),
                        data.getTa(), data.getTc(), data.getTb(), data.getVrmax(),
                        data.getVrmin(), data.getKe(), data.getTe(), data.getKf(),
                        data.getTf(), data.getSwitchValue(), data.getE1(), data.getSe1(),
                        data.getE2(), data.getSe2())
                : builder.addExcEsdc2a("Bus1", "1", data.getTr(), data.getKa(),
                        data.getTa(), data.getTc(), data.getTb(), data.getVrmax(),
                        data.getVrmin(), data.getKe(), data.getTe(), data.getKf(),
                        data.getTf(), data.getSwitchValue(), data.getE1(), data.getSe1(),
                        data.getE2(), data.getSe2());
        Machine machine = builder.getDStabNetwork().getMachine("Bus1-mach1");
        machine.setEfd(1.2);
        assertTrue(exciter.initStates(machine.getDStabBus(), machine));
        assertEquals(dc1, exciter instanceof Esdc1aExciter);
        return new Fixture(machine, exciter);
    }

    private static Esdc2aData algebraicData() {
        Esdc2aData data = new Esdc2aData();
        data.setTr(0.0); data.setKa(10.0); data.setTa(.05);
        data.setTb(.2); data.setTc(.05); data.setVrmax(5.0); data.setVrmin(-5.0);
        data.setKe(1.0); data.setTe(.4); data.setKf(0.0); data.setTf(0.0);
        data.setSwitchValue(1.0);
        return data;
    }

    private static Esdc2aData dynamicData() {
        Esdc2aData data = algebraicData();
        data.setTr(.1); data.setKa(20.0); data.setTa(.05);
        data.setKf(.1); data.setTf(.3);
        return data;
    }

    private static void step(Esdc2aExciter exciter, Machine machine, double dt) {
        assertTrue(exciter.nextStep(dt, DynamicSimuMethod.MODIFIED_EULER, machine, 0));
        assertTrue(exciter.nextStep(dt, DynamicSimuMethod.MODIFIED_EULER, machine, 1));
    }

    private static double[] add(double[] state, double[] derivative, double dt) {
        double[] result = new double[state.length];
        for (int i = 0; i < state.length; i++) result[i] = state[i] + derivative[i] * dt;
        return result;
    }

    private record Fixture(Machine machine, Esdc2aExciter exciter) { }
}
