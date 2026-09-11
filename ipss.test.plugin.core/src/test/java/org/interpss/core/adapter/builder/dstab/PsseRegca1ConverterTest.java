package org.interpss.core.adapter.builder.dstab;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.commons.math3.complex.Complex;
import org.interpss.CorePluginTestSetup;
import org.interpss.dstab.renewable.Regca1Data;
import org.interpss.dstab.renewable.Regca1Model;
import org.interpss.dstab.renewable.RenewableElectricalController;
import org.interpss.dstab.renewable.Repca1Model;
import org.interpss.fadapter.builder.DStabNetworkBuilder;
import org.interpss.fadapter.psse.PSSEDStabDirectParser;
import org.interpss.fadapter.psse.dyr.PsseDyrRecordReader;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.interpss.dstab.DStabGen;
import com.interpss.dstab.algo.DynamicSimuMethod;

public class PsseRegca1ConverterTest extends CorePluginTestSetup {
    private static final double TOL = 1.0e-10;
    private static final Path TEXAS_ROOT = Path.of(System.getProperty("texas2k.case.root",
            Path.of(System.getProperty("user.home"), "OneDrive", "Documents", "qiuhua",
                    "private_cases", "Texas2k_series24_cases_with_dynamics",
                    "Texas2k_series24_cases_with_dynamics").toString()));
    private static final List<String> TEXAS_DYR = List.of(
            "Texas2k_series24_case1_2016summerpeak/dynamic_models_case1.dyr",
            "Texas2k_series24_case2_2016lowload/dynamic_models_case2.dyr",
            "Texas2k_series24_case3_2024summerpeak/dynamic_models_case3.dyr",
            "Texas2k_series24_case4_2024lowload/dynamic_models_case4.dyr",
            "Texas2k_series24_case5_2024highrenewables/dynamic_models_case5.dyr",
            "Texas2k_series24_case6_2024lowloadwithgfm/dynamic_models_case6.dyr");

    @Test
    void directParserRetainsAllFifteenPsseParameters(@TempDir Path tempDir) throws Exception {
        DStabNetworkBuilder builder = DStabBuilderTestFixture.createBuilder();
        Path dyr = tempDir.resolve("regca1.dyr");
        Files.writeString(dyr,
                "1 'REGCA1' 1 1 .02 10 .9 .4 1.22 1.2 .9 .5 -1.3 .03 .7 .8 -.6 .4 /\n");

        PSSEDStabDirectParser parser = new PSSEDStabDirectParser(builder).setStrictImport(true);
        parser.parseDynFile(dyr.toString());
        DStabGen gen = (DStabGen) builder.getDStabNetwork().getDStabBus("Bus1")
                .getContributeGen("1");
        Regca1Model model = assertInstanceOf(Regca1Model.class, gen.getDynamicGenDevice());

        assertEquals(new Regca1Data(1, .02, 10, .9, .4, 1.22, 1.2, .9,
                .5, -1.3, .03, .7, .8, -.6, .4), model.getData());
        assertTrue(parser.getLastImportReport().isStrictlyComplete());
    }

    @Test
    void bothReviewedTexas2kProfilesAreCompleteAndHaveDistinctTgResponses() throws Exception {
        Map<String, Integer> counts = new HashMap<>();
        for (String relative : TEXAS_DYR) {
            Path dyr = TEXAS_ROOT.resolve(relative);
            assumeTrue(Files.isRegularFile(dyr), "Missing private Texas2k DYR: " + dyr);
            PsseDyrRecordReader.read(dyr).stream()
                    .filter(record -> record.canonicalModelName().equals("REGCA1"))
                    .map(record -> String.join(" ", record.parameters()))
                    .forEach(profile -> counts.merge(profile, 1, Integer::sum));
        }
        assertEquals(Map.of(
                "1 0.01 10 0.9 0.5 1.22 1.2 0.8 0.4 -1.3 0.02 0.7 0 0 0.8", 656,
                "1 0.02 10 0.9 0.5 1.22 1.2 0.8 0.4 -1.3 0.02 0.7 0 0 0.8", 762),
                counts);

        for (double tg : new double[] {.01, .02}) {
            Fixture fixture = fixture(data(1, tg, 10.0, 0.0, 0.0));
            double initial = fixture.model.getIpRegulatorState();
            double commandChange = .05;
            double dt = .005;
            fixture.controller.ipcmd = initial + commandChange;
            step(fixture.model, dt);

            double d0 = commandChange / tg;
            double d1 = (commandChange - dt * d0) / tg;
            assertEquals(initial + .5 * dt * (d0 + d1),
                    fixture.model.getIpRegulatorState(), TOL, "Tg=" + tg);
        }
    }

    @Test
    void composedControllerCanSupplyACommandAtBothModifiedEulerStages() throws Exception {
        DStabNetworkBuilder builder = DStabBuilderTestFixture.createBuilder();
        Regca1Model model = builder.addRegca1("Bus1", "1", data(0, .1, 10.0, 0.0, 0.0));
        StageCommandController controller = new StageCommandController();
        model.setActiveElectricalController(controller);
        assertTrue(model.initStates(model.getDStabBus()));

        double initial = model.getIpRegulatorState();
        controller.predictorCommand = initial + .05;
        controller.correctorCommand = initial + .10;
        double dt = .01;

        step(model, dt);

        double d0 = (controller.predictorCommand - initial) / .1;
        double predicted = initial + dt * d0;
        double d1 = (controller.correctorCommand - predicted) / .1;
        assertEquals(initial + .5 * dt * (d0 + d1),
                model.getIpRegulatorState(), TOL);
        assertEquals(List.of(0, 1), controller.stages);
    }

    @Test
    void activeLagUsesRecoveryRateLimitBeforeAlgebraicLowVoltageGain() throws Exception {
        Fixture fixture = fixture(data(0.2, 0.8, -0.6));
        double initialState = fixture.model.getIpRegulatorState();
        fixture.controller.ipcmd = initialState + 1.0;

        step(fixture.model, .01);
        assertEquals(initialState + .002, fixture.model.getIpRegulatorState(), TOL);

        fixture.model.getDStabBus().setVoltage(new Complex(.6, 0.0));
        assertEquals(.5 * fixture.model.getIpRegulatorState(), fixture.model.getIp(), TOL);
        assertEquals(initialState + .002, fixture.model.getIpRegulatorState(), TOL);
    }

    @Test
    void enabledLvplClampsTheRegulatorStateUsingFilteredVoltage() throws Exception {
        Fixture fixture = fixture(data(1, 10.0, .8, -.6));
        fixture.controller.ipcmd = fixture.model.getIpRegulatorState() + 1.0;
        fixture.model.getDStabBus().setVoltage(new Complex(.4, 0.0));

        for (int i = 0; i < 3; i++) step(fixture.model, .01);

        double filtered = fixture.model.getFilteredVoltage();
        double lvpl = filtered <= .4 ? 0.0 : (filtered - .4) * 1.22 / (.9 - .4);
        assertEquals(lvpl, fixture.model.getIpRegulatorState(), TOL);
        assertEquals(0.0, fixture.model.getIp(), TOL);
    }

    @Test
    void reactiveRecoveryRatePrecedesAlgebraicHighVoltageManagement() throws Exception {
        Fixture fixture = fixture(data(10.0, 0.1, -0.1));
        double initialState = fixture.model.getIqRegulatorState();
        fixture.controller.iqcmd = -(initialState + 1.0);

        step(fixture.model, .01);
        assertEquals(initialState + .001, fixture.model.getIqRegulatorState(), TOL);

        fixture.model.getDStabBus().setVoltage(new Complex(1.3, 0.0));
        double expected = Math.max(-1.3,
                fixture.model.getIqRegulatorState() - .7 * (1.3 - 1.2));
        assertEquals(expected, fixture.model.getIq(), TOL);
        assertEquals(initialState + .001, fixture.model.getIqRegulatorState(), TOL);
    }

    @Test
    void zeroUpperReactiveRecoveryRateFreezesUpwardMotionButAllowsReturn() throws Exception {
        Fixture fixture = fixture(data(10.0, 0.0, 0.0));
        double initialState = fixture.model.getIqRegulatorState();
        fixture.controller.iqcmd = -(initialState + 1.0);

        step(fixture.model, .01);
        assertEquals(initialState, fixture.model.getIqRegulatorState(), TOL,
                "Iqrmax=0 is an active zero upward rate for initially positive Q");

        fixture.controller.iqcmd = -(initialState - 1.0);
        step(fixture.model, .01);
        assertTrue(fixture.model.getIqRegulatorState() < initialState,
                "the conditional upward bound must not block downward return motion");
    }

    @Test
    void negativeReactiveRecoveryUsesTheConditionalLowerRate() throws Exception {
        DStabNetworkBuilder builder = DStabBuilderTestFixture.createBuilder();
        DStabGen gen = (DStabGen) builder.getDStabNetwork().getDStabBus("Bus1")
                .getContributeGen("1");
        gen.setGen(new Complex(gen.getGen().getReal(), -.2705));
        Regca1Model model = builder.addRegca1("Bus1", "1", data(10.0, .1, -.1));
        CommandController controller = new CommandController();
        model.setActiveElectricalController(controller);
        assertTrue(model.initStates(model.getDStabBus()));
        double initialState = model.getIqRegulatorState();
        controller.iqcmd = -(initialState - 1.0);

        step(model, .01);

        assertEquals(initialState - .001, model.getIqRegulatorState(), TOL);
    }

    @Test
    void nortonInjectionAndPowerReconstructionHonorGeneratorMvaBase() throws Exception {
        DStabNetworkBuilder builder = DStabBuilderTestFixture.createBuilder();
        DStabGen gen = (DStabGen) builder.getDStabNetwork().getDStabBus("Bus1")
                .getContributeGen("1");
        Complex sourcePower = gen.getGen();
        gen.setMvaBase(50.0);
        Regca1Model model = builder.addRegca1("Bus1", "1", data(10.0, .8, -.6));
        CommandController controller = new CommandController();
        model.setActiveElectricalController(controller);
        assertTrue(model.initStates(model.getDStabBus()));

        Complex voltage = model.getDStabBus().getVoltage();
        Complex norton = (Complex) model.getOutputObject();
        Complex z = gen.getPosGenZ().multiply(gen.getZMultiFactor());
        Complex injected = norton.subtract(voltage.divide(z));
        Complex reconstructedSystemPower = voltage.multiply(injected.conjugate());

        assertEquals(sourcePower.getReal(), reconstructedSystemPower.getReal(), TOL);
        assertEquals(sourcePower.getImaginary(), reconstructedSystemPower.getImaginary(), TOL);
        assertEquals(2.0 * sourcePower.getReal(),
                (double) model.getStates(null).get("REGCA1_P"), TOL);
        assertEquals(2.0 * sourcePower.getImaginary(),
                (double) model.getStates(null).get("REGCA1_Q"), TOL);
    }

    @Test
    void nortonOutputReadsArePureAndPowerFeedbackUsesMachineLifecycle() throws Exception {
        Fixture fixture = fixture(data(10.0, .8, -.6));
        Regca1Model model = fixture.model;
        double initialP = (double) model.getStates(null).get("REGCA1_P");
        double initialQ = (double) model.getStates(null).get("REGCA1_Q");

        model.getDStabBus().setVoltage(new Complex(1.1, 0.0));
        Complex first = (Complex) model.getOutputObject();
        Complex second = (Complex) model.getOutputObject();

        assertEquals(first.getReal(), second.getReal(), TOL);
        assertEquals(first.getImaginary(), second.getImaginary(), TOL);
        assertEquals(initialP, (double) model.getStates(null).get("REGCA1_P"), TOL);
        assertEquals(initialQ, (double) model.getStates(null).get("REGCA1_Q"), TOL);

        assertTrue(model.updateAttributes(false));
        double sampledP = 1.1 * model.getIp();
        double sampledQ = 1.1 * model.getIq();
        assertEquals(sampledP, (double) model.getStates(null).get("REGCA1_P"), TOL);
        assertEquals(sampledQ, (double) model.getStates(null).get("REGCA1_Q"), TOL);

        model.getDStabBus().setVoltage(new Complex(.95, 0.0));
        double eventP = .95 * model.getIp();
        double eventQ = .95 * model.getIq();
        assertTrue(model.updateAttributes(true));
        assertEquals(.5 * (sampledP + eventP),
                (double) model.getStates(null).get("REGCA1_P"), TOL);
        assertEquals(.5 * (sampledQ + eventQ),
                (double) model.getStates(null).get("REGCA1_Q"), TOL);
    }

    private static Fixture fixture(Regca1Data data) throws Exception {
        DStabNetworkBuilder builder = DStabBuilderTestFixture.createBuilder();
        Regca1Model model = builder.addRegca1("Bus1", "1", data);
        CommandController controller = new CommandController();
        model.setActiveElectricalController(controller);
        assertTrue(model.initStates(model.getDStabBus()));
        return new Fixture(model, controller);
    }

    private static Regca1Data data(double rrpwr, double iqrmax, double iqrmin) {
        return data(0, rrpwr, iqrmax, iqrmin);
    }

    private static Regca1Data data(int lvplsw, double rrpwr, double iqrmax, double iqrmin) {
        return data(lvplsw, .1, rrpwr, iqrmax, iqrmin);
    }

    private static Regca1Data data(int lvplsw, double tg, double rrpwr,
            double iqrmax, double iqrmin) {
        return new Regca1Data(lvplsw, tg, rrpwr, .9, .4, 1.22, 1.2, .8,
                .4, -1.3, .02, .7, iqrmax, iqrmin, .8);
    }

    private static void step(Regca1Model model, double dt) {
        assertTrue(model.nextStep(dt, DynamicSimuMethod.MODIFIED_EULER, 0));
        assertTrue(model.nextStep(dt, DynamicSimuMethod.MODIFIED_EULER, 1));
    }

    private record Fixture(Regca1Model model, CommandController controller) { }

    private static final class CommandController implements RenewableElectricalController {
        private double ipcmd;
        private double iqcmd;
        private Repca1Model plantController;

        @Override public void initialize(double p, double q, double v) {
            ipcmd = p / v;
            iqcmd = -q / v;
        }
        @Override public void step(double dt, double p, double q, double v, double frequency) { }
        @Override public double getIpcmd() { return ipcmd; }
        @Override public double getIqcmd() { return iqcmd; }
        @Override public Repca1Model getPlantController() { return plantController; }
        @Override public void setPlantController(Repca1Model value) { plantController = value; }
    }

    private static final class StageCommandController implements RenewableElectricalController {
        private final List<Integer> stages = new java.util.ArrayList<>();
        private double predictorCommand;
        private double correctorCommand;
        private double ipcmd;

        @Override public void initialize(double p, double q, double v) {
            ipcmd = p / v;
        }
        @Override public void step(double dt, double p, double q, double v, double frequency) {
            throw new AssertionError("stage-aware path expected");
        }
        @Override public void step(double dt, double p, double q, double v,
                double frequency, int flag) {
            stages.add(flag);
            ipcmd = flag == 0 ? predictorCommand : correctorCommand;
        }
        @Override public double getIpcmd() { return ipcmd; }
        @Override public double getIqcmd() { return 0.0; }
        @Override public Repca1Model getPlantController() { return null; }
        @Override public void setPlantController(Repca1Model value) { }
    }
}
