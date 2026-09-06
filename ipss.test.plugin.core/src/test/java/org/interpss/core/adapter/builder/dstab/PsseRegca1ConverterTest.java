package org.interpss.core.adapter.builder.dstab;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;

import org.apache.commons.math3.complex.Complex;
import org.interpss.CorePluginTestSetup;
import org.interpss.dstab.renewable.Regca1Data;
import org.interpss.dstab.renewable.Regca1Model;
import org.interpss.dstab.renewable.RenewableElectricalController;
import org.interpss.dstab.renewable.Repca1Model;
import org.interpss.fadapter.builder.DStabNetworkBuilder;
import org.interpss.fadapter.psse.PSSEDStabDirectParser;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.interpss.dstab.DStabGen;
import com.interpss.dstab.algo.DynamicSimuMethod;

public class PsseRegca1ConverterTest extends CorePluginTestSetup {
    private static final double TOL = 1.0e-10;

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
        return new Regca1Data(lvplsw, .1, rrpwr, .9, .4, 1.22, 1.2, .8,
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
}
