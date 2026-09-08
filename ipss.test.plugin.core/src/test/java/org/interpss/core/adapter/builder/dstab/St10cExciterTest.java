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
import org.interpss.dstab.control.exc.psse.st10c.St10cData;
import org.interpss.dstab.control.exc.psse.st10c.St10cExciter;
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
import com.interpss.dstab.mach.MachineIfdBase;

/** Native schema, equations, selector logic and solver tests for PSS/E ST10C. */
public class St10cExciterTest extends CorePluginTestSetup {
    private static final double TOL = 1e-9;

    @Test
    void parsesExactPsse36SchemaAndRejectsNonPsseAlias(@TempDir Path dir) throws Exception {
        Path dyr = dir.resolve("st10c.dyr");
        String parameters = "2 3 2 1 2 .01 500 1.5 12.5 .1 .1 1.6 13 .2 .3 1.7 14 .4 .5 5 -5 10 -8.7 .004 1 .01 0 0 0 1.5";
        Files.writeString(dyr, "1 'ST10C' 1 " + parameters + " /\n");
        DStabNetworkBuilder builder = DStabBuilderTestFixture.createWithMachine();
        PSSEDStabDirectParser parser = new PSSEDStabDirectParser(builder).setStrictImport(true);
        parser.parseDynFile(dyr.toString());
        St10cExciter exciter = (St10cExciter) builder.getDStabNetwork()
                .getMachine("Bus1-mach1").getExciter();
        assertNotNull(exciter);
        St10cData data = exciter.getData();
        assertEquals(2, data.getPss()); assertEquals(3, data.getOel());
        assertEquals(2, data.getUel()); assertEquals(1, data.getScl());
        assertEquals(2, data.getSw1()); assertEquals(.01, data.getTr(), TOL);
        assertEquals(500, data.getKr(), TOL); assertEquals(1.5, data.getTc1(), TOL);
        assertEquals(12.5, data.getTb1(), TOL); assertEquals(.1, data.getTc2(), TOL);
        assertEquals(.1, data.getTb2(), TOL); assertEquals(1.6, data.getTuc1(), TOL);
        assertEquals(13, data.getTub1(), TOL); assertEquals(1.7, data.getToc1(), TOL);
        assertEquals(14, data.getTob1(), TOL); assertEquals(5, data.getVrsmax(), TOL);
        assertEquals(-8.7, data.getVrmin(), TOL); assertEquals(.004, data.getT1(), TOL);
        assertEquals(1, data.getKp(), TOL); assertEquals(.01, data.getKc(), TOL);
        assertEquals(1.5, data.getVbmax(), TOL);
        assertTrue(parser.getLastImportReport().isStrictlyComplete());
        var descriptor = DynamicModelCatalog.find("ST10C").orElseThrow();
        assertEquals(30, descriptor.parameterCount());
        assertEquals(DynamicModelSupportStatus.LOADABLE, descriptor.supportStatus());
        assertTrue(WeccApprovedDynamicModelCatalog.findExciter("ST10C")
                .orElseThrow().isImplementedExactly());

        Files.writeString(dyr, "1 'ESST10C' 1 " + parameters + " /\n");
        builder = DStabBuilderTestFixture.createWithMachine();
        PSSEDStabDirectParser strict = new PSSEDStabDirectParser(builder).setStrictImport(true);
        assertThrows(Exception.class, () -> strict.parseDynFile(dyr.toString()));
    }

    @Test
    void tenStatesMatchIndependentModifiedEulerOracle() throws Exception {
        Fixture fixture = fixture(baseData(), 0);
        double[] expected = fixture.exciter.getStateSnapshot();
        fixture.exciter.setRefPoint(fixture.exciter.getRefPoint() + .1);
        double dt = .0001, maxError = 0;
        for (int n = 0; n < 2000; n++) {
            double[] first = oracleDerivatives(expected, .1);
            double[] predicted = add(expected, first, dt);
            double[] second = oracleDerivatives(predicted, .1);
            for (int i = 0; i < expected.length; i++)
                expected[i] += .5 * (first[i] + second[i]) * dt;
            step(fixture.exciter, fixture.machine, dt);
            double[] actual = fixture.exciter.getStateSnapshot();
            for (int i = 0; i < expected.length; i++)
                maxError = Math.max(maxError, Math.abs(expected[i] - actual[i]));
        }
        assertTrue(maxError < 1e-10, "ST10C ten-state max error=" + maxError);
    }

    @Test
    void coordinatesTakeoverPathsAndMovesPssWithoutSclFalseActivation() throws Exception {
        St10cData data = baseData(); data.setPss(2); data.setOel(2);
        data.setUel(2); data.setScl(2);
        Fixture fixture = fixture(data, .05);
        fixture.exciter.setVsclUel(1.0); fixture.exciter.setVsclOel(.9);
        assertFalse(fixture.exciter.isUelTakeoverActive());
        assertFalse(fixture.exciter.isOelTakeoverActive());
        assertEquals(.05, fixture.exciter.getVs1(), TOL);
        assertEquals(0, fixture.exciter.getVs2(), TOL);

        fixture.exciter.setVuel(.8);
        assertTrue(fixture.exciter.isUelTakeoverActive());
        assertEquals(1.0, fixture.exciter.getFirstHighGate(), TOL);
        assertEquals(0, fixture.exciter.getVs1(), TOL);
        assertEquals(.05, fixture.exciter.getVs2(), TOL);
        step(fixture.exciter, fixture.machine, .001);
        assertEquals(fixture.exciter.getUelPathOutput(),
                fixture.exciter.getSelectedPathOutput(), TOL);
        assertTrue(fixture.exciter.getPssRegulatorOutput() > 0);

        fixture.exciter.setVoel(.7);
        assertTrue(fixture.exciter.isOelTakeoverActive());
        assertEquals(.7, fixture.exciter.getFirstGatedSignal(), TOL);
        step(fixture.exciter, fixture.machine, .001);
        assertEquals(fixture.exciter.getOelPathOutput(),
                fixture.exciter.getSelectedPathOutput(), TOL);
    }

    @Test
    void implementsAllDirectAndSecondGateLimiterLocations() throws Exception {
        St10cData data = baseData(); data.setOel(1); data.setUel(1); data.setScl(1);
        Fixture direct = fixture(data, 0);
        direct.exciter.setVuel(.02); direct.exciter.setVoel(.03); direct.exciter.setVsclSum(.04);
        assertEquals(.09 + .6, direct.exciter.getSummedError(), TOL);

        data = baseData(); data.setOel(3); data.setUel(3); data.setScl(3);
        Fixture finalGate = fixture(data, 0);
        finalGate.exciter.setVuel(1.4); finalGate.exciter.setVsclUel(1.5);
        finalGate.exciter.setVoel(1.35); finalGate.exciter.setVsclOel(1.3);
        assertEquals(1.3, finalGate.exciter.getSecondGatedSignal(), TOL);
    }

    @Test
    void appliesDerivedBoundsPublishedCorrectionsAndLimitExpansion() throws Exception {
        St10cData data = baseData(); data.setTr(.008); data.setT1(.015);
        data.setVrmax(-2); data.setVrmin(-3); data.setVrsmax(-1); data.setVrsmin(-2);
        DStabNetworkBuilder builder = DStabBuilderTestFixture.createWithMachine();
        Machine machine = builder.getDStabNetwork().getMachine("Bus1-mach1");
        St10cExciter exciter = builder.addExcSt10c("Bus1", "1", data);
        machine.setEfd(1.2); exciter.configureIntegrationStep(.01, 2);
        assertTrue(exciter.initStates(machine.getDStabBus(), machine));
        assertEquals(.01, exciter.tr, TOL); assertEquals(.02, exciter.t1, TOL);
        assertTrue(exciter.vrmax >= 1.2); assertTrue(exciter.vrmin <= 1.2);
        assertEquals((exciter.vrmax - exciter.vrmin) * exciter.tb1
                / (exciter.kr * exciter.tc1), exciter.max1, TOL);
        assertEquals((exciter.vrsmax - exciter.vrsmin) * exciter.tb1
                / (exciter.kr * exciter.tc1), exciter.max4, TOL);

        data = baseData(); data.setTr(.004); data.setT1(.009); data.setKr(0);
        builder = DStabBuilderTestFixture.createWithMachine(); machine = builder.getDStabNetwork().getMachine("Bus1-mach1");
        exciter = builder.addExcSt10c("Bus1", "1", data); machine.setEfd(1.2);
        exciter.configureIntegrationStep(.01, 2); assertTrue(exciter.initStates(machine.getDStabBus(), machine));
        assertEquals(0, exciter.tr, TOL); assertEquals(0, exciter.t1, TOL);
        assertEquals(.02, exciter.kr, TOL);
    }

    @Test
    void implementsCompoundSupplyAndLoadedRectifier() throws Exception {
        St10cData data = baseData(); data.setSw1(1); data.setKp(.9);
        data.setKi(.15); data.setXl(.07); data.setThetaP(20); data.setKc(.04);
        Fixture fixture = fixture(data, 0);
        Complex angle = new Complex(Math.cos(Math.toRadians(20)), Math.sin(Math.toRadians(20)));
        Complex kp = angle.multiply(.9), vt = fixture.machine.getDStabBus().getVoltage();
        Complex it = fixture.machine.getIxy().divide(fixture.machine.getIMultiFactor());
        double source = kp.multiply(vt)
                .add(Complex.I.multiply(new Complex(.15, 0).add(kp.multiply(.07))).multiply(it)).abs();
        double expected = source * Exac1Exciter.rectifierFactor(Math.max(0,
                Math.min(1, .04 * ifd(fixture.machine) / source)));
        assertEquals(source, fixture.exciter.getCompoundSource(), TOL);
        assertEquals(expected, fixture.exciter.getAvailableExciterVoltage(), TOL);
    }

    @Test
    void validatesSelectorsAndHoldsPositionCEquilibrium() throws Exception {
        St10cData invalidData = baseData(); invalidData.setPss(4);
        DStabNetworkBuilder builder = DStabBuilderTestFixture.createWithMachine();
        Machine machine = builder.getDStabNetwork().getMachine("Bus1-mach1");
        machine.setEfd(1.2);
        St10cExciter invalid = builder.addExcSt10c("Bus1", "1", invalidData);
        invalid.configureIntegrationStep(.001);
        assertFalse(invalid.initStates(machine.getDStabBus(), machine));

        St10cData data = baseData(); data.setPss(3);
        Fixture fixture = fixture(data, .05);
        assertEquals(.1, fixture.exciter.getPssRegulatorOutput(), TOL);
        double initial = fixture.exciter.getOutput(fixture.machine);
        for (int i = 0; i < 1000; i++) step(fixture.exciter, fixture.machine, .0001);
        assertEquals(initial, fixture.exciter.getOutput(fixture.machine), 1e-9);
    }

    @Test
    void participatesInFullSimulation() throws Exception {
        DStabNetworkBuilder builder = DStabBuilderTestFixture.createWithMachine();
        assertNotNull(builder.addExcSt10c("Bus1", "1", baseData()));
        DynamicSimuAlgorithm algorithm = DStabObjectFactory
                .createDynamicSimuAlgorithm(builder.getDStabNetwork());
        algorithm.setSimuMethod(DynamicSimuMethod.MODIFIED_EULER);
        algorithm.setSimuStepSec(.005); algorithm.setTotalSimuTimeSec(.02);
        algorithm.setSimuOutputHandler(new StateMonitor());
        assertTrue(algorithm.getAclfAlgorithm().loadflow());
        assertTrue(algorithm.initialization());
        assertTrue(algorithm.performSimulation());
    }

    private static Fixture fixture(St10cData data, double pss) throws Exception {
        DStabNetworkBuilder builder = DStabBuilderTestFixture.createWithMachine();
        Machine machine = builder.getDStabNetwork().getMachine("Bus1-mach1");
        if (pss != 0) new FixedOutputStabilizer(machine, pss);
        St10cExciter exciter = builder.addExcSt10c("Bus1", "1", data);
        machine.setEfd(1.2); exciter.configureIntegrationStep(.001);
        assertNotNull(exciter); assertTrue(exciter.initStates(machine.getDStabBus(), machine));
        return new Fixture(machine, exciter);
    }

    private static St10cData baseData() {
        St10cData data = new St10cData();
        data.setPss(1); data.setOel(1); data.setUel(1); data.setScl(1); data.setSw1(2);
        data.setTr(.1); data.setKr(2); data.setTc1(.02); data.setTb1(.1);
        data.setTc2(.03); data.setTb2(.15); data.setTuc1(.025); data.setTub1(.12);
        data.setTuc2(.035); data.setTub2(.16); data.setToc1(.03); data.setTob1(.13);
        data.setToc2(.04); data.setTob2(.17); data.setVrsmax(99); data.setVrsmin(-99);
        data.setVrmax(99); data.setVrmin(-99); data.setT1(.2); data.setKp(1);
        data.setKc(0); data.setKi(0); data.setXl(0); data.setThetaP(0); data.setVbmax(99);
        return data;
    }

    private static double[] oracleDerivatives(double[] x, double referenceStep) {
        double mainInput = .6 + referenceStep;
        double n2 = leadLag(mainInput, x[1], .03, .15);
        double n1 = leadLag(n2, x[2], .02, .1);
        double u2 = leadLag(mainInput, x[3], .035, .16);
        double u1 = leadLag(u2, x[4], .025, .12);
        double o2 = leadLag(mainInput, x[5], .04, .17);
        double o1 = leadLag(o2, x[6], .03, .13);
        double p2 = leadLag(0, x[7], .03, .15);
        double p1 = leadLag(p2, x[8], .02, .1);
        return new double[] {0, (mainInput - x[1]) / .15, (n2 - x[2]) / .1,
                (mainInput - x[3]) / .16, (u2 - x[4]) / .12,
                (mainInput - x[5]) / .17, (o2 - x[6]) / .13,
                -x[7] / .15, (p2 - x[8]) / .1, (2 * n1 + 2 * p1 - x[9]) / .2};
    }

    private static double leadLag(double input, double state, double lead, double lag) {
        return lead / lag * input + (1 - lead / lag) * state;
    }

    private static double[] add(double[] x, double[] d, double dt) {
        double[] result = new double[x.length];
        for (int i = 0; i < x.length; i++) result[i] = x[i] + d[i] * dt;
        return result;
    }

    private static double ifd(Machine machine) {
        double value = machine.calculateIfd(MachineIfdBase.EXCITER);
        return Double.isFinite(value) ? value : 0;
    }

    private static void step(St10cExciter exciter, Machine machine, double dt) {
        assertTrue(exciter.nextStep(dt, DynamicSimuMethod.MODIFIED_EULER, machine, 0));
        assertTrue(exciter.nextStep(dt, DynamicSimuMethod.MODIFIED_EULER, machine, 1));
    }

    private record Fixture(Machine machine, St10cExciter exciter) { }

    private static final class FixedOutputStabilizer extends AbstractStabilizer {
        private final double output;
        FixedOutputStabilizer(Machine machine, double output) {
            super("fixed-pss", "Fixed PSS", "test"); this.output = output; setMachine(machine);
        }
        @Override public double getOutput(Machine machine) { return output; }
    }
}
