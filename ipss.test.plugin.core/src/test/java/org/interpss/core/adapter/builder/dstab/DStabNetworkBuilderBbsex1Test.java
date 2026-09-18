package org.interpss.core.adapter.builder.dstab;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;

import org.interpss.CorePluginTestSetup;
import org.interpss.dstab.control.exc.psse.bbsex1.Bbsex1Data;
import org.interpss.dstab.control.exc.psse.bbsex1.Bbsex1Exciter;
import org.interpss.fadapter.builder.DStabNetworkBuilder;
import org.interpss.fadapter.psse.PSSEDStabDirectParser;
import org.interpss.fadapter.psse.dyr.DynamicModelCatalog;
import org.interpss.fadapter.psse.dyr.DynamicModelSupportStatus;
import org.interpss.fadapter.psse.dyr.WeccApprovedDynamicModelCatalog;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.interpss.dstab.DStabObjectFactory;
import com.interpss.dstab.algo.DynamicSimuAlgorithm;
import com.interpss.dstab.algo.DynamicSimuMethod;
import com.interpss.dstab.cache.StateMonitor;
import com.interpss.dstab.controller.deqn.AbstractStabilizer;
import com.interpss.dstab.mach.Machine;

/** Native PSS/E BBSEX1 import and PowerWorld/Dynawo equation tests. */
public class DStabNetworkBuilderBbsex1Test extends CorePluginTestSetup {
    private static final double TOL = 1.0e-10;

    @Test
    void parsesExactElevenParameterPsseRecordWithoutPslfAlias(@TempDir Path dir)
            throws Exception {
        DStabNetworkBuilder builder = DStabBuilderTestFixture.createWithMachine();
        Path dyr = dir.resolve("bbsex1.dyr");
        Files.writeString(dyr,
                "1 'BBSEX1' 1 .04 40 4 1.2 .02 .1 6.1 -6.1 5.3 -4.1 1 /\n");
        PSSEDStabDirectParser parser = new PSSEDStabDirectParser(builder).setStrictImport(true);
        parser.parseDynFile(dyr.toString());

        Bbsex1Exciter exciter = (Bbsex1Exciter) builder.getDStabNetwork()
                .getMachine("Bus1-mach1").getExciter();
        assertNotNull(exciter);
        Bbsex1Data data = exciter.getData();
        assertEquals(.04, data.getTf(), TOL);
        assertEquals(40, data.getK(), TOL);
        assertEquals(4, data.getT1(), TOL);
        assertEquals(1.2, data.getT2(), TOL);
        assertEquals(.02, data.getT3(), TOL);
        assertEquals(.1, data.getT4(), TOL);
        assertEquals(6.1, data.getVrmax(), TOL);
        assertEquals(-6.1, data.getVrmin(), TOL);
        assertEquals(5.3, data.getEfdmax(), TOL);
        assertEquals(-4.1, data.getEfdmin(), TOL);
        assertEquals(1, data.getSwitchLocation());
        assertTrue(parser.getLastImportReport().isStrictlyComplete());
        assertEquals(DynamicModelSupportStatus.LOADABLE,
                DynamicModelCatalog.find("BBSEX1").orElseThrow().supportStatus());
        assertTrue(WeccApprovedDynamicModelCatalog.findExciter("EXBBC")
                .orElseThrow().isImplementedExactly());
        assertFalse(DynamicModelCatalog.find("EXBBC").isPresent());
    }

    @Test
    void threeStateTrajectoryMatchesIndependentDiagramOracle() throws Exception {
        Fixture fixture = fixture(dynamicData());
        Bbsex1Exciter exciter = fixture.exciter;
        double vt = fixture.machine.getDStabBus().getVoltageMag();
        exciter.setRefPoint(exciter.getRefPoint() + .02);
        double[] state = {vt, 1.2 / 10.0, (.4 / .1 - 1.0) / 10.0 * 1.2};
        double dt = .0002;
        double maxError = 0.0;
        for (int i = 0; i < 1000; i++) {
            double[] first = derivatives(state, vt, .02);
            double[] predicted = add(state, first, dt);
            double[] second = derivatives(predicted, vt, .02);
            for (int j = 0; j < state.length; j++) {
                state[j] += .5 * (first[j] + second[j]) * dt;
            }
            step(exciter, fixture.machine, dt);
            double[] actual = {exciter.getSensedVoltage(), exciter.getLeadLagState(),
                    exciter.getInverseFeedbackState()};
            for (int j = 0; j < state.length; j++) {
                maxError = Math.max(maxError, Math.abs(state[j] - actual[j]));
            }
        }
        assertTrue(maxError < 1.0e-9, "BBSEX1 three-state max error=" + maxError);
    }

    @Test
    void switchRoutesSupplementOnlyToPublishedSummation() throws Exception {
        Bbsex1Data atErrorData = algebraicData();
        atErrorData.setSwitchLocation(Bbsex1Exciter.SUPPLEMENT_AT_ERROR);
        Fixture atError = fixture(atErrorData);
        double initialError = atError.exciter.getVoltageError();
        new FixedOutputStabilizer(atError.machine, .1);
        assertEquals(initialError + .1, atError.exciter.getVoltageError(), TOL);
        assertEquals(atError.exciter.getRegulatorOutput(),
                atError.exciter.getBeforeFieldLimit(), TOL);

        Bbsex1Data atOutputData = algebraicData();
        atOutputData.setSwitchLocation(Bbsex1Exciter.SUPPLEMENT_AT_OUTPUT);
        Fixture atOutput = fixture(atOutputData);
        double outputPathError = atOutput.exciter.getVoltageError();
        new FixedOutputStabilizer(atOutput.machine, .1);
        assertEquals(outputPathError, atOutput.exciter.getVoltageError(), TOL);
        assertEquals(atOutput.exciter.getRegulatorOutput() + .1,
                atOutput.exciter.getBeforeFieldLimit(), TOL);
    }

    @Test
    void limitedVrDrivesThePublishedInverseTimingFeedback() throws Exception {
        Bbsex1Data data = dynamicData();
        data.setVrmax(1.21);
        Fixture fixture = fixture(data);
        double initial = fixture.exciter.getInverseFeedbackState();
        fixture.exciter.setRefPoint(fixture.exciter.getRefPoint() + .2);
        step(fixture.exciter, fixture.machine, .01);
        assertEquals(1.21, fixture.exciter.getRegulatorOutput(), TOL);
        assertTrue(fixture.exciter.getInverseFeedbackState() > initial);
    }

    @Test
    void finalFieldLimitsScaleWithTerminalVoltage() throws Exception {
        Fixture fixture = fixture(algebraicData());
        fixture.exciter.efdmax = .5;
        fixture.exciter.efdmin = -.5;
        fixture.exciter.setRefPoint(fixture.exciter.getRefPoint() + 10.0);
        step(fixture.exciter, fixture.machine, .001);
        double vt = fixture.machine.getDStabBus().getVoltageMag();
        assertEquals(.5 * vt, fixture.exciter.getOutput(fixture.machine), TOL);
        assertEquals(.5 * vt, fixture.exciter.getDynamicFieldUpperLimit(), TOL);
    }

    @Test
    void correctsTimesSwapsLimitsExpandsVrAndParticipatesInSolver() throws Exception {
        Bbsex1Data data = dynamicData();
        data.setTf(.01);
        data.setT1(.01);
        data.setT2(.01);
        data.setT3(.01);
        data.setT4(.01);
        data.setVrmax(-2.0);
        data.setVrmin(-3.0);
        data.setEfdmax(-4.0);
        data.setEfdmin(5.0);
        DStabNetworkBuilder builder = DStabBuilderTestFixture.createWithMachine();
        Bbsex1Exciter exciter = builder.addExcBbsex1("Bus1", "1", data);
        Machine machine = builder.getDStabNetwork().getMachine("Bus1-mach1");
        machine.setEfd(1.2);
        exciter.configureIntegrationStep(.01, 2.0);
        assertTrue(exciter.initStates(machine.getDStabBus(), machine));
        assertEquals(.02, exciter.tf, TOL);
        assertEquals(.02, exciter.t1, TOL);
        assertEquals(.02, exciter.t2, TOL);
        assertEquals(.02, exciter.t3, TOL);
        assertEquals(.02, exciter.t4, TOL);
        assertTrue(exciter.vrmax >= exciter.getRegulatorOutput());
        assertEquals(5.0, exciter.efdmax, TOL);
        assertEquals(-4.0, exciter.efdmin, TOL);

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

    private static Fixture fixture(Bbsex1Data data) throws Exception {
        DStabNetworkBuilder builder = DStabBuilderTestFixture.createWithMachine();
        Bbsex1Exciter exciter = builder.addExcBbsex1("Bus1", "1", data);
        Machine machine = builder.getDStabNetwork().getMachine("Bus1-mach1");
        machine.setEfd(1.2);
        assertTrue(exciter.initStates(machine.getDStabBus(), machine));
        return new Fixture(machine, exciter);
    }

    private static Bbsex1Data algebraicData() {
        Bbsex1Data data = new Bbsex1Data();
        data.setTf(0.0);
        data.setK(10.0);
        data.setT1(.4);
        data.setT2(.1);
        data.setT3(0.0);
        data.setT4(.2);
        data.setVrmax(100.0);
        data.setVrmin(-100.0);
        data.setEfdmax(100.0);
        data.setEfdmin(-100.0);
        return data;
    }

    private static Bbsex1Data dynamicData() {
        Bbsex1Data data = algebraicData();
        data.setTf(.1);
        data.setT3(.05);
        return data;
    }

    private static double[] derivatives(double[] state, double vt, double referenceStep) {
        double error = vt + 1.2 / 10.0 + referenceStep - state[0];
        double leadLag = .05 / .2 * error + (1.0 - .05 / .2) * state[1];
        double unlimited = 10.0 * .1 / .4 * (leadLag + state[2]);
        double feedbackGain = (.4 / .1 - 1.0) / 10.0;
        return new double[] {(vt - state[0]) / .1, (error - state[1]) / .2,
                (feedbackGain * unlimited - state[2]) / .1};
    }

    private static double[] add(double[] state, double[] derivative, double dt) {
        double[] result = new double[state.length];
        for (int i = 0; i < state.length; i++) result[i] = state[i] + derivative[i] * dt;
        return result;
    }

    private static void step(Bbsex1Exciter exciter, Machine machine, double dt) {
        assertTrue(exciter.nextStep(dt, DynamicSimuMethod.MODIFIED_EULER, machine, 0));
        assertTrue(exciter.nextStep(dt, DynamicSimuMethod.MODIFIED_EULER, machine, 1));
    }

    private record Fixture(Machine machine, Bbsex1Exciter exciter) { }

    private static final class FixedOutputStabilizer extends AbstractStabilizer {
        private final double output;
        FixedOutputStabilizer(Machine machine, double output) {
            super("fixed-pss", "Fixed PSS", "test");
            this.output = output;
            setMachine(machine);
        }
        @Override public double getOutput(Machine machine) { return output; }
    }
}
