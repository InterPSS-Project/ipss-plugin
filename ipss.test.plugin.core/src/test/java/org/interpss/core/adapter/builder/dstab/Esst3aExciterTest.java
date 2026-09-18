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
import org.interpss.dstab.control.exc.ieee.y2005.st3a.IEEE2005ST3AExciter;
import org.interpss.dstab.control.exc.ieee.y2005.st3a.IEEE2005ST3AExciterData;
import org.interpss.fadapter.builder.DStabNetworkBuilder;
import org.interpss.fadapter.psse.PSSEDStabDirectParser;
import org.interpss.fadapter.psse.dyr.DynamicModelCatalog;
import org.interpss.fadapter.psse.dyr.WeccApprovedDynamicModelCatalog;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.interpss.dstab.DStabObjectFactory;
import com.interpss.dstab.algo.DynamicSimuAlgorithm;
import com.interpss.dstab.algo.DynamicSimuMethod;
import com.interpss.dstab.cache.StateMonitor;
import com.interpss.dstab.mach.Machine;

/** Exact PSS/E schema and PowerWorld/ANDES equation coverage for ESST3A. */
public class Esst3aExciterTest extends CorePluginTestSetup {
    private static final double TOL = 1.0e-10;

    @Test
    void parsesExactNativeRecordAndKeepsExst3aOutOfTheDyrNamespace(@TempDir Path dir)
            throws Exception {
        DStabNetworkBuilder builder = DStabBuilderTestFixture.createWithMachine();
        Path dyr = dir.resolve("esst3a.dyr");
        Files.writeString(dyr, "1 'ESST3A' '1' .02 .3 -.2 8 1 5 20 0 99 -99 "
                + "1 3.67 .435 6.48 .01 .0098 4.86 3.33 .4 99 0 /\n");
        PSSEDStabDirectParser parser = new PSSEDStabDirectParser(builder).setStrictImport(true);
        parser.parseDynFile(dyr.toString());
        IEEE2005ST3AExciter exciter = (IEEE2005ST3AExciter) builder.getDStabNetwork()
                .getMachine("Bus1-mach1").getExciter();
        assertNotNull(exciter);
        IEEE2005ST3AExciterData data = exciter.getData();
        assertEquals(.02, data.getTr(), TOL); assertEquals(.3, data.getVimax(), TOL);
        assertEquals(-.2, data.getVimin(), TOL); assertEquals(8, data.getKm(), TOL);
        assertEquals(1, data.getTc(), TOL); assertEquals(5, data.getTb(), TOL);
        assertEquals(20, data.getKa(), TOL); assertEquals(0, data.getTa(), TOL);
        assertEquals(99, data.getVrmax(), TOL); assertEquals(-99, data.getVrmin(), TOL);
        assertEquals(1, data.getKg(), TOL); assertEquals(3.67, data.getKp(), TOL);
        assertEquals(.435, data.getKi(), TOL); assertEquals(6.48, data.getVbmax(), TOL);
        assertEquals(.01, data.getKc(), TOL); assertEquals(.0098, data.getXl(), TOL);
        assertEquals(4.86, data.getVgmax(), TOL); assertEquals(3.33, data.getAngKp(), TOL);
        assertEquals(.4, data.getTm(), TOL); assertEquals(99, data.getVmmax(), TOL);
        assertEquals(0, data.getVmmin(), TOL);
        assertTrue(parser.getLastImportReport().isStrictlyComplete());
        assertEquals(21, DynamicModelCatalog.find("ESST3A").orElseThrow().parameterCount());
        assertTrue(WeccApprovedDynamicModelCatalog.findExciter("EXST3A")
                .orElseThrow().isImplementedExactly());
        assertFalse(DynamicModelCatalog.find("EXST3A").isPresent());

        Path alias = dir.resolve("exst3a.dyr");
        Files.writeString(alias, "1 'EXST3A' '1' .02 .3 -.2 8 1 5 20 0 99 -99 "
                + "1 3.67 .435 6.48 .01 .0098 4.86 3.33 .4 99 0 /\n");
        assertThrows(Exception.class, () -> new PSSEDStabDirectParser(
                DStabBuilderTestFixture.createWithMachine()).setStrictImport(true)
                .parseDynFile(alias.toString()));
    }

    @Test
    void fourStatesMatchTheAndesEquationTopology() throws Exception {
        Fixture fixture = fixture(baseData());
        fixture.exciter.setRefPoint(fixture.exciter.getRefPoint() + .1);
        double[] expected = fixture.exciter.getStateSnapshot();
        double vi0 = expected[3], vb = fixture.exciter.getBridgeVoltage(), dt = .0001, max = 0.0;
        for (int n = 0; n < 2000; n++) {
            double[] d0 = derivatives(expected, vi0, vb);
            double[] prediction = add(expected, d0, dt);
            double[] d1 = derivatives(prediction, vi0, vb);
            for (int i = 0; i < expected.length; i++) expected[i] += .5 * (d0[i] + d1[i]) * dt;
            step(fixture.exciter, fixture.machine, dt);
            double[] actual = fixture.exciter.getStateSnapshot();
            for (int i = 0; i < expected.length; i++) max = Math.max(max, Math.abs(expected[i] - actual[i]));
        }
        assertTrue(max < 1.0e-11, "ESST3A four-state maximum error=" + max);
    }

    @Test
    void placesVuelAsThePublishedHighValueGateAfterTheViLimiter() throws Exception {
        Fixture fixture = fixture(baseData());
        double limited = fixture.exciter.getLimitedInput();
        fixture.exciter.setVuel(limited + .25);
        assertEquals(limited, fixture.exciter.getLimitedInput(), TOL);
        assertEquals(limited + .25, fixture.exciter.getHighValueInput(), TOL);
        fixture.exciter.clearVuel();
        assertEquals(limited, fixture.exciter.getHighValueInput(), TOL);
    }

    @Test
    void appliesPublishedCorrectionsLimitExpansionAndZeroTimeAlgebraicPaths() throws Exception {
        IEEE2005ST3AExciterData data = baseData();
        data.setTr(.004); data.setTb(.015); data.setTa(.004); data.setTm(.004);
        data.setKa(0); data.setKm(0);
        data.setVimax(-2); data.setVimin(-3); data.setVrmax(-4); data.setVrmin(-5);
        data.setVmmax(-6); data.setVmmin(-7);
        DStabNetworkBuilder builder = DStabBuilderTestFixture.createWithMachine();
        Machine machine = builder.getDStabNetwork().getMachine("Bus1-mach1");
        machine.setEfd(1.2);
        IEEE2005ST3AExciter exciter = attach(builder, data);
        exciter.configureIntegrationStep(.01, 2.0);
        assertTrue(exciter.initStates(machine.getDStabBus(), machine));
        assertEquals(0, exciter.tr, TOL); assertEquals(.02, exciter.tb, TOL);
        assertEquals(0, exciter.ta, TOL); assertEquals(0, exciter.tm, TOL);
        assertEquals(.02, exciter.ka, TOL); assertEquals(.02, exciter.km, TOL);
        assertTrue(exciter.vimax >= exciter.getLimitedInput());
        assertTrue(exciter.vimin <= exciter.getLimitedInput());
        assertTrue(exciter.vrmax >= exciter.getRegulatorOutput());
        assertTrue(exciter.vrmin <= exciter.getRegulatorOutput());
        assertTrue(exciter.vmmax >= exciter.getInnerRegulatorOutput());
        assertTrue(exciter.vmmin <= exciter.getInnerRegulatorOutput());
        exciter.setRefPoint(exciter.getRefPoint() + .01);
        assertTrue(Double.isFinite(exciter.getOutput(machine)));
    }

    @Test
    void implementsComplexCompoundSourceLoadedRectifierAndVbLimit() throws Exception {
        IEEE2005ST3AExciterData data = baseData();
        data.setKp(2); data.setKi(3); data.setXl(.2); data.setAngKp(15);
        data.setKc(0); data.setVbmax(99);
        Fixture fixture = fixture(data);
        double angle = Math.toRadians(15);
        Complex kpc = new Complex(2 * Math.cos(angle), 2 * Math.sin(angle));
        Complex expected = fixture.machine.getDStabBus().getVoltage().multiply(kpc)
                .add(Complex.I.multiply(kpc.multiply(.2).add(3)).multiply(fixture.machine.getIxy()));
        assertEquals(expected.abs(), fixture.exciter.getCompoundSourceVoltage(), TOL);
        assertEquals(expected.abs(), fixture.exciter.getBridgeVoltage(), TOL);
        fixture.exciter.vbmax = expected.abs() / 2;
        assertEquals(expected.abs() / 2, fixture.exciter.getBridgeVoltage(), TOL);
    }

    @Test
    void initializesStationaryAndParticipatesInTheFullSolver() throws Exception {
        Fixture fixture = fixture(baseData());
        for (int n = 0; n < 1000; n++) step(fixture.exciter, fixture.machine, .0001);
        assertEquals(1.2, fixture.exciter.getOutput(fixture.machine), 1.0e-9);

        DStabNetworkBuilder builder = DStabBuilderTestFixture.createWithMachine();
        assertNotNull(attach(builder, baseData()));
        DynamicSimuAlgorithm algorithm = DStabObjectFactory
                .createDynamicSimuAlgorithm(builder.getDStabNetwork());
        algorithm.setSimuMethod(DynamicSimuMethod.MODIFIED_EULER);
        algorithm.setSimuStepSec(.005); algorithm.setTotalSimuTimeSec(.02);
        algorithm.setSimuOutputHandler(new StateMonitor());
        assertTrue(algorithm.getAclfAlgorithm().loadflow());
        assertTrue(algorithm.initialization());
        assertTrue(algorithm.performSimulation());
    }

    private static Fixture fixture(IEEE2005ST3AExciterData data) throws Exception {
        DStabNetworkBuilder builder = DStabBuilderTestFixture.createWithMachine();
        Machine machine = builder.getDStabNetwork().getMachine("Bus1-mach1");
        machine.setEfd(1.2);
        IEEE2005ST3AExciter exciter = attach(builder, data);
        assertTrue(exciter.initStates(machine.getDStabBus(), machine));
        return new Fixture(machine, exciter);
    }

    private static IEEE2005ST3AExciter attach(DStabNetworkBuilder builder,
            IEEE2005ST3AExciterData data) {
        return builder.addExcEsst3a("Bus1", "1", data.getTr(), data.getVimax(), data.getVimin(),
                data.getKm(), data.getTc(), data.getTb(), data.getKa(), data.getTa(),
                data.getVrmax(), data.getVrmin(), data.getKg(), data.getKp(), data.getKi(),
                data.getVbmax(), data.getKc(), data.getXl(), data.getVgmax(), data.getAngKp(),
                data.getTm(), data.getVmmax(), data.getVmmin());
    }

    private static IEEE2005ST3AExciterData baseData() {
        IEEE2005ST3AExciterData data = new IEEE2005ST3AExciterData();
        data.setTr(.1); data.setVimax(99); data.setVimin(-99);
        data.setKm(3); data.setTc(.2); data.setTb(.4); data.setKa(4); data.setTa(.3);
        data.setVrmax(99); data.setVrmin(-99); data.setKg(.1);
        data.setKp(1); data.setKi(0); data.setVbmax(99); data.setKc(0); data.setXl(0);
        data.setVgmax(99); data.setAngKp(0); data.setTm(.5);
        data.setVmmax(99); data.setVmmin(-99);
        return data;
    }

    private static double[] derivatives(double[] x, double vi0, double vb) {
        double error = vi0 + .1 + 1.04 - x[1];
        double leadLag = .5 * error + .5 * x[3];
        double efd = x[0] * vb;
        double feedback = .1 * efd;
        return new double[] {(3 * (x[2] - feedback) - x[0]) / .5,
                (1.04 - x[1]) / .1, (4 * leadLag - x[2]) / .3,
                (error - x[3]) / .4};
    }

    private static double[] add(double[] x, double[] derivative, double dt) {
        double[] result = x.clone();
        for (int i = 0; i < result.length; i++) result[i] += derivative[i] * dt;
        return result;
    }

    private static void step(IEEE2005ST3AExciter exciter, Machine machine, double dt) {
        assertTrue(exciter.nextStep(dt, DynamicSimuMethod.MODIFIED_EULER, machine, 0));
        assertTrue(exciter.nextStep(dt, DynamicSimuMethod.MODIFIED_EULER, machine, 1));
    }

    private record Fixture(Machine machine, IEEE2005ST3AExciter exciter) {}
}
