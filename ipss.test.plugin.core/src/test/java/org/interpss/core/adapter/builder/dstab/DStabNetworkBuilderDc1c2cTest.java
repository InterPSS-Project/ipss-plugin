package org.interpss.core.adapter.builder.dstab;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;

import org.apache.commons.math3.complex.Complex;
import org.interpss.IpssCorePlugin;
import org.interpss.dstab.control.exc.psse.dc1c.Dc1cData;
import org.interpss.dstab.control.exc.psse.dc1c.Dc1cExciter;
import org.interpss.dstab.control.exc.psse.dc2c.Dc2cExciter;
import org.interpss.fadapter.builder.DStabNetworkBuilder;
import org.interpss.fadapter.psse.PSSEDStabDirectParser;
import org.interpss.fadapter.psse.dyr.WeccApprovedDynamicModelCatalog;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.interpss.dstab.algo.DynamicSimuMethod;
import com.interpss.dstab.algo.DynamicSimuAlgorithm;
import com.interpss.dstab.DStabObjectFactory;
import com.interpss.dstab.cache.StateMonitor;
import com.interpss.dstab.mach.Machine;

/** Native PSS/E DC1C/DC2C import and IEEE/PowerWorld equation checks. */
public class DStabNetworkBuilderDc1c2cTest {
    private static final double TOL = 1.0e-9;

    @BeforeAll static void initializePlugin() { IpssCorePlugin.init(); }

    @Test
    void exactNineteenParameterRecordsMapRealCorpusLayout(@TempDir Path dir)
            throws Exception {
        for (String model : new String[] {"DC1C", "DC2C"}) {
            DStabNetworkBuilder builder = DStabBuilderTestFixture.createWithMachine();
            Path dyr = dir.resolve(model + ".dyr");
            Files.writeString(dyr, "1 '" + model + "' '1' 1 2 .02 27 .11 1 1 "
                    + "3 -1 1 .8 .05 2.1 1.65 .55 2.2 .79 99 0 /\n");
            new PSSEDStabDirectParser(builder).parseDynFile(dyr.toString());

            Dc1cExciter exciter = assertInstanceOf(Dc1cExciter.class,
                    builder.getDStabNetwork().getMachine("Bus1-mach1").getExciter());
            if (model.equals("DC2C")) assertInstanceOf(Dc2cExciter.class, exciter);
            Dc1cData data = exciter.getData();
            assertEquals(1, data.getOelLocation()); assertEquals(2, data.getUelLocation());
            assertEquals(.02, data.getTr(), TOL); assertEquals(27, data.getKa(), TOL);
            assertEquals(.11, data.getTa(), TOL); assertEquals(1, data.getTb(), TOL);
            assertEquals(1, data.getTc(), TOL); assertEquals(3, data.getVrmax(), TOL);
            assertEquals(-1, data.getVrmin(), TOL); assertEquals(.8, data.getTe(), TOL);
            assertEquals(.05, data.getKf(), TOL); assertEquals(2.1, data.getTf(), TOL);
            assertEquals(99, data.getVemax(), TOL);
            assertEquals(0, data.getVemin(), TOL); assertEquals(0, data.getSclLocation());
            assertEquals(0, data.getSpdmlt(), TOL);
        }
        assertTrue(WeccApprovedDynamicModelCatalog.findExciter("ESDC1C")
                .orElseThrow().isImplementedExactly());
        assertTrue(WeccApprovedDynamicModelCatalog.findExciter("ESDC2C")
                .orElseThrow().isImplementedExactly());
    }

    @Test
    void dc1cAndDc2cMatchIndependentFiveStateOracleAwayFromLimits() throws Exception {
        double dc1Error = maximumTrajectoryError(false);
        double dc2Error = maximumTrajectoryError(true);
        assertTrue(dc1Error < 1.0e-9, () -> "DC1C maximum error=" + dc1Error);
        assertTrue(dc2Error < 1.0e-9, () -> "DC2C maximum error=" + dc2Error);
    }

    @Test
    void dc1cUsesConstantAndDc2cUsesBusFedRegulatorLimits() throws Exception {
        Fixture dc1 = fixture(false, standardData());
        Fixture dc2 = fixture(true, standardData());
        dc1.machine.getDStabBus().setVoltage(new Complex(.8, 0));
        dc2.machine.getDStabBus().setVoltage(new Complex(.8, 0));
        assertEquals(5.0, dc1.exciter.getDynamicRegulatorUpperLimit(), TOL);
        assertEquals(4.0, dc2.exciter.getDynamicRegulatorUpperLimit(), TOL);
        assertEquals(-5.0, dc1.exciter.getDynamicRegulatorLowerLimit(), TOL);
        assertEquals(-4.0, dc2.exciter.getDynamicRegulatorLowerLimit(), TOL);
    }

    @Test
    void dc2cMovingVoltageLimitClampsTheRegulatorStateWithoutRebound()
            throws Exception {
        Dc1cData data = standardData();
        data.setVrmax(2.0); data.setVrmin(-2.0);
        Fixture fixture = fixture(true, data);
        fixture.exciter.setRefPoint(fixture.exciter.getRefPoint() + 1.0);
        for (int i = 0; i < 1000; i++) step(fixture, 1.0e-4);
        assertEquals(fixture.exciter.getDynamicRegulatorUpperLimit(),
                fixture.exciter.getRegulatorOutput(), 2.0e-6);

        fixture.machine.getDStabBus().setVoltage(new Complex(.75, 0));
        step(fixture, 1.0e-4);
        assertEquals(1.5, fixture.exciter.getRegulatorOutput(), 2.0e-6);

        fixture.machine.getDStabBus().setVoltage(new Complex(.95, 0));
        assertEquals(1.5, fixture.exciter.getRegulatorOutput(), 2.0e-6,
                "raising the moving ceiling must not reveal a hidden pre-fault state");
    }

    @Test
    void summationInputsUsePublishedUelOelAndSclSigns() throws Exception {
        Dc1cData data = standardData();
        data.setUelLocation(Dc1cExciter.INPUT_SUMMATION);
        data.setOelLocation(Dc1cExciter.INPUT_SUMMATION);
        data.setSclLocation(Dc1cExciter.INPUT_SUMMATION);
        Fixture fixture = fixture(false, data);
        double initial = fixture.exciter.getVoltageError();
        fixture.exciter.setVuel(.2);
        fixture.exciter.setVoel(.1);
        fixture.exciter.setVsclSum(.05);
        assertEquals(initial + .05, fixture.exciter.getVoltageError(), TOL);
    }

    @Test
    void takeoverInputsApplyHighAndLowValueGates() throws Exception {
        Dc1cData data = standardData();
        data.setUelLocation(Dc1cExciter.INPUT_TAKEOVER);
        Fixture high = fixture(false, data);
        high.exciter.setVuel(2.0);
        assertEquals(2.0, high.exciter.getRegulatorOutput(), TOL);

        data = standardData();
        data.setOelLocation(Dc1cExciter.INPUT_TAKEOVER);
        Fixture low = fixture(false, data);
        low.exciter.setVoel(.4);
        assertEquals(.4, low.exciter.getRegulatorOutput(), TOL);
    }

    @Test
    void exciterFieldLimitsAreNonWindupBoundaries() throws Exception {
        Dc1cData data = standardData();
        data.setVemax(1.21); data.setVemin(.2);
        Fixture fixture = fixture(false, data);
        fixture.exciter.setRefPoint(fixture.exciter.getRefPoint() + 1.0);
        for (int i = 0; i < 3000; i++) step(fixture, 1.0e-4);
        assertEquals(1.21, fixture.exciter.getInternalFieldVoltage(), 2.0e-6);
        for (int i = 0; i < 200; i++) step(fixture, 1.0e-4);
        assertEquals(1.21, fixture.exciter.getInternalFieldVoltage(), 2.0e-6);
    }

    @Test
    void algebraicFieldAndTypedSpeedMultiplierRemainSupported() throws Exception {
        Dc1cData data = standardData();
        data.setTe(0.0); data.setTf(0.0); data.setSpdmlt(1.0);
        Fixture fixture = fixture(false, data);
        fixture.machine.setSpeed(.95);
        assertEquals(1.14, fixture.exciter.getOutput(fixture.machine), TOL);
        fixture.exciter.setRefPoint(fixture.exciter.getRefPoint() + .02);
        assertTrue(fixture.exciter.nextStep(0.0, DynamicSimuMethod.MODIFIED_EULER,
                fixture.machine, 0));
        assertTrue(Double.isFinite(fixture.exciter.getOutput(fixture.machine)));
    }

    @Test
    void mappedDc2cParticipatesInTheDynamicSolver() throws Exception {
        DStabNetworkBuilder builder = DStabBuilderTestFixture.createWithMachine();
        Dc2cExciter exciter = builder.addExcDc2c("Bus1", "1", 1, 1,
                .02, 20, .05, .2, .05, 5, -5, 1, .4, .1, .3,
                0, 0, 0, 0, 99, -99);
        assertNotNull(exciter);
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

    private static double maximumTrajectoryError(boolean dc2) throws Exception {
        Fixture fixture = fixture(dc2, standardData());
        fixture.exciter.setRefPoint(fixture.exciter.getRefPoint() + .01);
        double vt = fixture.machine.getDStabBus().getVoltageMag();
        double[] state = {1.2, vt, 1.2, 1.2, .06};
        double maximum = 0.0;
        double dt = 1.0e-4;
        for (int i = 0; i < 1000; i++) {
            double[] first = derivatives(state, vt, .01);
            double[] predicted = add(state, first, dt);
            double[] second = derivatives(predicted, vt, .01);
            for (int j = 0; j < state.length; j++) {
                state[j] += .5 * (first[j] + second[j]) * dt;
            }
            step(fixture, dt);
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

    private static Fixture fixture(boolean dc2, Dc1cData data) throws Exception {
        DStabNetworkBuilder builder = DStabBuilderTestFixture.createWithMachine();
        Machine machine = builder.getDStabNetwork().getMachine("Bus1-mach1");
        machine.setSpeed(1.0); machine.setEfd(1.2);
        Dc1cExciter exciter = dc2
                ? new Dc2cExciter("Bus1-mach1_Exc", data, machine)
                : new Dc1cExciter("Bus1-mach1_Exc", data, machine);
        assertTrue(exciter.initStates(machine.getDStabBus(), machine));
        return new Fixture(machine, exciter);
    }

    private static Dc1cData standardData() {
        Dc1cData data = new Dc1cData();
        data.setTr(.1); data.setKa(20); data.setTa(.05); data.setTb(.2); data.setTc(.05);
        data.setVrmax(5); data.setVrmin(-5); data.setKe(1); data.setTe(.4);
        data.setKf(.1); data.setTf(.3); data.setVemax(99); data.setVemin(-99);
        return data;
    }

    private static void step(Fixture fixture, double dt) {
        assertTrue(fixture.exciter.nextStep(dt, DynamicSimuMethod.MODIFIED_EULER,
                fixture.machine, 0));
        assertTrue(fixture.exciter.nextStep(dt, DynamicSimuMethod.MODIFIED_EULER,
                fixture.machine, 1));
    }

    private static double[] add(double[] state, double[] derivative, double dt) {
        double[] result = new double[state.length];
        for (int i = 0; i < state.length; i++) result[i] = state[i] + derivative[i] * dt;
        return result;
    }

    private record Fixture(Machine machine, Dc1cExciter exciter) { }
}
