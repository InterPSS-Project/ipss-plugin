package org.interpss.core.adapter.builder.dstab;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;

import org.apache.commons.math3.complex.Complex;
import org.interpss.CorePluginTestSetup;
import org.interpss.dstab.control.exc.psse.exac1.Exac1Exciter;
import org.interpss.dstab.control.exc.psse.st9c.St9cData;
import org.interpss.dstab.control.exc.psse.st9c.St9cExciter;
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
import com.interpss.dstab.mach.Machine;
import com.interpss.dstab.mach.MachineIfdBase;

/** Official-schema, equation, limiter, correction and solver tests for PSS/E ST9C. */
public class St9cExciterTest extends CorePluginTestSetup {
    private static final double TOL = 1e-9;

    @Test
    void parsesExactNativeSchemaAndRejectsPslfName(@TempDir Path dir) throws Exception {
        Path dyr = dir.resolve("st9c.dyr");
        String parameters = "1 2 1 2 .1 .06 .03 .036 12 100 2 3 1 -1 6 .01 1 15 .2 .05 .04 1.2";
        Files.writeString(dyr, "1 'ST9C' 1 " + parameters + " /\n");
        DStabNetworkBuilder builder = DStabBuilderTestFixture.createWithMachine();
        PSSEDStabDirectParser parser = new PSSEDStabDirectParser(builder).setStrictImport(true);
        parser.parseDynFile(dyr.toString());
        St9cExciter exciter = (St9cExciter) builder.getDStabNetwork()
                .getMachine("Bus1-mach1").getExciter();
        assertNotNull(exciter);
        St9cData data = exciter.getData();
        assertEquals(1, data.getOel()); assertEquals(2, data.getUel());
        assertEquals(1, data.getScl()); assertEquals(2, data.getSw1());
        assertEquals(.1, data.getTr(), TOL); assertEquals(.06, data.getTcd(), TOL);
        assertEquals(.03, data.getTbd(), TOL); assertEquals(.036, data.getZa(), TOL);
        assertEquals(12, data.getKa(), TOL); assertEquals(100, data.getKu(), TOL);
        assertEquals(2, data.getTa(), TOL); assertEquals(3, data.getTauel(), TOL);
        assertEquals(6, data.getKas(), TOL); assertEquals(.01, data.getTas(), TOL);
        assertEquals(15, data.getThetaP(), TOL); assertEquals(.04, data.getKc(), TOL);
        assertEquals(1.2, data.getVbmax(), TOL);
        assertTrue(parser.getLastImportReport().isStrictlyComplete());
        var descriptor = DynamicModelCatalog.find("ST9C").orElseThrow();
        assertEquals(22, descriptor.parameterCount());
        assertEquals(DynamicModelSupportStatus.LOADABLE, descriptor.supportStatus());
        assertTrue(WeccApprovedDynamicModelCatalog.findExciter("ESST9C")
                .orElseThrow().isImplementedExactly());
        assertTrue(DynamicModelCatalog.find("ESST9C").isEmpty());

        Files.writeString(dyr, "1 'ESST9C' 1 " + parameters + " /\n");
        builder = DStabBuilderTestFixture.createWithMachine();
        parser = new PSSEDStabDirectParser(builder).setStrictImport(true);
        PSSEDStabDirectParser strictParser = parser;
        assertThrows(Exception.class, () -> strictParser.parseDynFile(dyr.toString()));
    }

    @Test
    void fourStatesMatchIndependentDynawoModifiedEulerOracle() throws Exception {
        Fixture fixture = fixture(baseData());
        double[] expected = fixture.exciter.getStateSnapshot();
        fixture.exciter.setRefPoint(fixture.exciter.getRefPoint() + .1);
        double dt = .0001;
        double maxError = 0;
        for (int n = 0; n < 2000; n++) {
            double[] first = oracleDerivatives(expected, .1);
            double[] predicted = add(expected, first, dt);
            double[] second = oracleDerivatives(predicted, .1);
            for (int i = 0; i < expected.length; i++) {
                expected[i] += .5 * (first[i] + second[i]) * dt;
            }
            step(fixture.exciter, fixture.machine, dt);
            double[] actual = fixture.exciter.getStateSnapshot();
            for (int i = 0; i < expected.length; i++) {
                maxError = Math.max(maxError, Math.abs(expected[i] - actual[i]));
            }
        }
        assertTrue(maxError < 1e-10, "ST9C four-state max error=" + maxError);
    }

    @Test
    void appliesDifferentialDeadbandAndAllLimiterRoutes() throws Exception {
        St9cData data = baseData();
        data.setTcd(1); data.setZa(.01);
        Fixture differential = fixture(data);
        differential.machine.getDStabBus().setVoltage(new Complex(.94, 0));
        assertTrue(differential.exciter.nextStep(.01, DynamicSimuMethod.MODIFIED_EULER,
                differential.machine, 0));
        double raw = differential.exciter.getDifferentialOutput();
        assertTrue(raw < -.01);
        assertEquals(-.01 - raw, differential.exciter.getDifferentialInfluence(), TOL);

        Fixture summation = fixture(baseData());
        summation.exciter.setVuel(.02); summation.exciter.setVoel(.03);
        summation.exciter.setVsclSum(.04);
        assertEquals(.01, summation.exciter.getVoltageError(), TOL);

        data = baseData(); data.setOel(2); data.setUel(2); data.setScl(2);
        Fixture takeover = fixture(data);
        takeover.exciter.setVuel(.2); takeover.exciter.setVsclUel(.25);
        takeover.exciter.setVoel(.15); takeover.exciter.setVsclOel(.1);
        assertEquals(.25, takeover.exciter.getHighGateOutput(), TOL);
        assertEquals(.1, takeover.exciter.getRegulatorOutput(), TOL);
        assertEquals(1, takeover.exciter.getTakeoverFraction(), TOL);
        assertEquals(1 / data.getTauel(), takeover.exciter.getIntegralRate(), TOL);

        data = baseData(); data.setScl(3);
        DStabNetworkBuilder builder = DStabBuilderTestFixture.createWithMachine();
        St9cExciter invalid = builder.addExcSt9c("Bus1", "1", data);
        Machine machine = builder.getDStabNetwork().getMachine("Bus1-mach1");
        machine.setEfd(1.2); invalid.configureIntegrationStep(.001);
        assertFalse(invalid.initStates(machine.getDStabBus(), machine));
    }

    @Test
    void implementsPotentialCompoundSupplyAndLoadedRectifier() throws Exception {
        St9cData data = baseData(); data.setSw1(1); data.setKp(.9);
        data.setKi(.15); data.setXl(.07); data.setThetaP(20); data.setKc(.04);
        Fixture fixture = fixture(data);
        Complex angle = new Complex(Math.cos(Math.toRadians(20)),
                Math.sin(Math.toRadians(20)));
        Complex kp = angle.multiply(.9);
        Complex vt = fixture.machine.getDStabBus().getVoltage();
        Complex it = fixture.machine.getIxy().divide(fixture.machine.getIMultiFactor());
        double source = kp.multiply(vt)
                .add(Complex.I.multiply(new Complex(.15, 0).add(kp.multiply(.07)))
                        .multiply(it)).abs();
        double ifd = ifd(fixture.machine);
        double expectedAvailable = Math.min(99,
                source * Exac1Exciter.rectifierFactor(Math.max(0, Math.min(1,
                        .04 * ifd / source))));
        assertEquals(source, fixture.exciter.getCompoundSource(), TOL);
        assertEquals(expectedAvailable, fixture.exciter.getAvailableExciterVoltage(), TOL);
        assertEquals(fixture.exciter.getConverterOutput() * expectedAvailable,
                fixture.exciter.getOutput(fixture.machine), TOL);
    }

    @Test
    void appliesPublishedCorrectionsAndInitializationLimitExpansion() throws Exception {
        St9cData data = baseData();
        data.setTr(.004); data.setTbd(.015); data.setTas(.004);
        data.setKa(0); data.setTa(0); data.setTauel(0);
        data.setVrmax(-2); data.setVrmin(-3);
        DStabNetworkBuilder builder = DStabBuilderTestFixture.createWithMachine();
        St9cExciter exciter = builder.addExcSt9c("Bus1", "1", data);
        Machine machine = builder.getDStabNetwork().getMachine("Bus1-mach1");
        machine.setEfd(1.2); exciter.configureIntegrationStep(.01, 2);
        assertTrue(exciter.initStates(machine.getDStabBus(), machine));
        assertEquals(0, exciter.tr, TOL); assertEquals(.02, exciter.tbd, TOL);
        assertEquals(0, exciter.tas, TOL); assertEquals(.02, exciter.ka, TOL);
        assertEquals(.02, exciter.ta, TOL); assertEquals(.02, exciter.tauel, TOL);
        assertTrue(exciter.vrmax >= exciter.getRegulatorOutput());
        assertTrue(exciter.vrmin <= exciter.getRegulatorOutput());
    }

    @Test
    void retainsIdealDifferentialPathWhenBothFiltersAreBypassed() throws Exception {
        St9cData data = baseData(); data.setTr(0); data.setTbd(0);
        data.setTcd(.06); data.setZa(.01);
        Fixture fixture = fixture(data);
        fixture.machine.getDStabBus().setVoltage(new Complex(1.03, 0));
        assertTrue(fixture.exciter.nextStep(.01, DynamicSimuMethod.MODIFIED_EULER,
                fixture.machine, 0));
        assertEquals(-.06, fixture.exciter.getDifferentialOutput(), TOL);
        assertEquals(.05, fixture.exciter.getDifferentialInfluence(), TOL);
    }

    @Test
    void holdsEquilibriumAndParticipatesInFullSimulation() throws Exception {
        Fixture fixture = fixture(baseData());
        double initial = fixture.exciter.getOutput(fixture.machine);
        for (int i = 0; i < 1000; i++) step(fixture.exciter, fixture.machine, .0001);
        assertEquals(initial, fixture.exciter.getOutput(fixture.machine), 1e-9);

        DStabNetworkBuilder builder = DStabBuilderTestFixture.createWithMachine();
        assertNotNull(builder.addExcSt9c("Bus1", "1", baseData()));
        DynamicSimuAlgorithm algorithm = DStabObjectFactory
                .createDynamicSimuAlgorithm(builder.getDStabNetwork());
        algorithm.setSimuMethod(DynamicSimuMethod.MODIFIED_EULER);
        algorithm.setSimuStepSec(.005); algorithm.setTotalSimuTimeSec(.02);
        algorithm.setSimuOutputHandler(new StateMonitor());
        assertTrue(algorithm.getAclfAlgorithm().loadflow());
        assertTrue(algorithm.initialization());
        assertTrue(algorithm.performSimulation());
    }

    private static Fixture fixture(St9cData data) throws Exception {
        DStabNetworkBuilder builder = DStabBuilderTestFixture.createWithMachine();
        Machine machine = builder.getDStabNetwork().getMachine("Bus1-mach1");
        St9cExciter exciter = builder.addExcSt9c("Bus1", "1", data);
        machine.setEfd(1.2); exciter.configureIntegrationStep(.001);
        assertNotNull(exciter); assertTrue(exciter.initStates(machine.getDStabBus(), machine));
        return new Fixture(machine, exciter);
    }

    private static St9cData baseData() {
        St9cData data = new St9cData();
        data.setOel(1); data.setUel(1); data.setScl(1); data.setSw1(2);
        data.setTr(.1); data.setTcd(.06); data.setTbd(.03); data.setZa(.036);
        data.setKa(2); data.setKu(10); data.setTa(.2); data.setTauel(.05);
        data.setVrmax(99); data.setVrmin(-99); data.setKas(1.5); data.setTas(.1);
        data.setKp(1); data.setThetaP(0); data.setKi(0); data.setXl(0);
        data.setKc(0); data.setVbmax(99);
        return data;
    }

    private static double[] oracleDerivatives(double[] state, double referenceStep) {
        double differential = .06 * (state[0] - state[1]) / .03;
        double influence = Math.max(-.036, Math.min(.036, differential)) - differential;
        double proportional = 2 * (referenceStep + influence);
        double regulator = proportional + state[2];
        return new double[] {0, (state[0] - state[1]) / .03,
                (regulator - state[2]) / .2, (1.5 * regulator - state[3]) / .1};
    }

    private static double[] add(double[] state, double[] derivative, double dt) {
        double[] result = new double[state.length];
        for (int i = 0; i < state.length; i++) result[i] = state[i] + derivative[i] * dt;
        return result;
    }

    private static double ifd(Machine machine) {
        double value = machine.calculateIfd(MachineIfdBase.EXCITER);
        return Double.isFinite(value) ? value : 0;
    }

    private static void step(St9cExciter exciter, Machine machine, double dt) {
        assertTrue(exciter.nextStep(dt, DynamicSimuMethod.MODIFIED_EULER, machine, 0));
        assertTrue(exciter.nextStep(dt, DynamicSimuMethod.MODIFIED_EULER, machine, 1));
    }

    private record Fixture(Machine machine, St9cExciter exciter) { }
}
