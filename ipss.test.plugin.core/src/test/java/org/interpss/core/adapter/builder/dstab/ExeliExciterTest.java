package org.interpss.core.adapter.builder.dstab;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;

import org.interpss.CorePluginTestSetup;
import org.interpss.dstab.control.exc.psse.exeli.ExeliData;
import org.interpss.dstab.control.exc.psse.exeli.ExeliExciter;
import org.interpss.fadapter.builder.DStabNetworkBuilder;
import org.interpss.fadapter.psse.PSSEDStabDirectParser;
import org.interpss.fadapter.psse.dyr.DynamicModelCatalog;
import org.interpss.fadapter.psse.dyr.DynamicModelImportStatus;
import org.interpss.fadapter.psse.dyr.DynamicModelSupportStatus;
import org.interpss.fadapter.psse.dyr.WeccApprovedDynamicModelCatalog;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.interpss.dstab.DStabObjectFactory;
import com.interpss.dstab.algo.DynamicSimuAlgorithm;
import com.interpss.dstab.algo.DynamicSimuMethod;
import com.interpss.dstab.cache.StateMonitor;
import com.interpss.dstab.mach.Machine;

/** PSS/E schema, equation, correction, limiter and solver tests for EXELI. */
public class ExeliExciterTest extends CorePluginTestSetup {
    private static final double TOL = 1.0e-9;

    @Test
    void parsesExactOfficialSixteenParameterRecord(@TempDir Path dir) throws Exception {
        Path dyr = dir.resolve("exeli.dyr");
        Files.writeString(dyr,
                "1 'EXELI' '1' .05 .04 .2 2 3 .4 .02 -4 5 .1 .3 1.2 .5 .2 .15 .25 /\n");
        DStabNetworkBuilder builder = DStabBuilderTestFixture.createWithMachine();
        PSSEDStabDirectParser parser = new PSSEDStabDirectParser(builder).setStrictImport(true);
        parser.parseDynFile(dyr.toString());

        ExeliExciter exciter = (ExeliExciter) builder.getDStabNetwork()
                .getMachine("Bus1-mach1").getExciter();
        assertNotNull(exciter);
        ExeliData data = exciter.getData();
        assertEquals(.05, data.getTfv(), TOL); assertEquals(.04, data.getTfi(), TOL);
        assertEquals(.2, data.getTnu(), TOL); assertEquals(2, data.getVpu(), TOL);
        assertEquals(3, data.getVpi(), TOL); assertEquals(.4, data.getVpnf(), TOL);
        assertEquals(.02, data.getDpnf(), TOL); assertEquals(-4, data.getEfdmin(), TOL);
        assertEquals(5, data.getEfdmax(), TOL); assertEquals(.1, data.getXe(), TOL);
        assertEquals(.3, data.getTw(), TOL); assertEquals(1.2, data.getKs1(), TOL);
        assertEquals(.5, data.getKs2(), TOL); assertEquals(.2, data.getTs1(), TOL);
        assertEquals(.15, data.getTs2(), TOL); assertEquals(.25, data.getSmax(), TOL);
        assertTrue(parser.getLastImportReport().isStrictlyComplete());

        var descriptor = DynamicModelCatalog.find("EXELI").orElseThrow();
        assertEquals(16, descriptor.parameterCount());
        assertEquals(DynamicModelSupportStatus.LOADABLE, descriptor.supportStatus());
        assertTrue(WeccApprovedDynamicModelCatalog.findExciter("EXELI")
                .orElseThrow().isImplementedExactly());
    }

    @Test
    void rejectsWrongRecordLengthInStrictMode(@TempDir Path dir) throws Exception {
        Path dyr = dir.resolve("short-exeli.dyr");
        Files.writeString(dyr,
                "1 'EXELI' '1' .05 .04 .2 2 3 .4 .02 -4 5 .1 .3 1.2 .5 .2 .15 /\n");
        DStabNetworkBuilder builder = DStabBuilderTestFixture.createWithMachine();
        PSSEDStabDirectParser parser = new PSSEDStabDirectParser(builder).setStrictImport(false);
        parser.parseDynFile(dyr.toString());
        assertFalse(parser.getLastImportReport().isStrictlyComplete());
        assertEquals(1, parser.getLastImportReport().count(DynamicModelImportStatus.REJECTED));
    }

    @Test
    void initializesAllEightStatesAtAnExactStationaryEquilibrium() throws Exception {
        Fixture fixture = fixture(baseData());
        double initial = fixture.exciter.getOutput(fixture.machine);
        double[] initialStates = fixture.exciter.getStateSnapshot();
        for (int i = 0; i < 2000; i++) step(fixture.exciter, fixture.machine, .0001);
        assertEquals(initial, fixture.exciter.getOutput(fixture.machine), 1.0e-10);
        assertArrayClose(initialStates, fixture.exciter.getStateSnapshot(), 1.0e-10);
        assertEquals(0.0, fixture.exciter.getStabilizerSignal(), 1.0e-12);
        assertEquals(initial, fixture.exciter.getUnlimitedOutput(), 1.0e-10);
    }

    @Test
    void eightStatesMatchIndependentModifiedEulerOracle() throws Exception {
        ExeliData data = baseData();
        Fixture fixture = fixture(data);
        fixture.machine.setPe(fixture.machine.getPe() + .12);
        fixture.exciter.setRefPoint(fixture.exciter.getRefPoint() + .03);
        double[] oracle = fixture.exciter.getStateSnapshot();
        double reference = fixture.exciter.getRefPoint();
        double terminalVoltage = fixture.machine.getDStabBus().getVoltageMag();
        double fieldCurrent = fixture.exciter.getSensedFieldCurrent();
        double dt = .0001;
        double maxError = 0.0;

        for (int n = 0; n < 1500; n++) {
            double[] d0 = oracleDerivative(oracle, fixture.machine.getPe(), terminalVoltage,
                    fieldCurrent, reference, data);
            double[] predicted = add(oracle, d0, dt);
            double[] d1 = oracleDerivative(predicted, fixture.machine.getPe(), terminalVoltage,
                    fieldCurrent, reference, data);
            for (int i = 0; i < oracle.length; i++) {
                oracle[i] += .5 * (d0[i] + d1[i]) * dt;
            }
            step(fixture.exciter, fixture.machine, dt);
            double[] actual = fixture.exciter.getStateSnapshot();
            for (int i = 0; i < oracle.length; i++) {
                maxError = Math.max(maxError, Math.abs(oracle[i] - actual[i]));
            }
        }
        assertTrue(maxError < 1.0e-10, "EXELI eight-state maximum error=" + maxError);
    }

    @Test
    void appliesPublishedTimeCorrectionsAndInitializationLimitExpansion() throws Exception {
        ExeliData data = baseData();
        data.setTfv(.004); data.setTfi(.015); data.setTnu(.015);
        data.setTw(.015); data.setTs1(.004); data.setTs2(.015);
        data.setEfdmax(-2); data.setEfdmin(-3);
        DStabNetworkBuilder builder = DStabBuilderTestFixture.createWithMachine();
        ExeliExciter exciter = builder.addExcExeli("Bus1", "1", data);
        Machine machine = builder.getDStabNetwork().getMachine("Bus1-mach1");
        machine.setEfd(1.2);
        exciter.configureIntegrationStep(.01, 2.0);
        assertTrue(exciter.initStates(machine.getDStabBus(), machine));

        assertEquals(0.0, exciter.tfv, TOL); assertEquals(.02, exciter.tfi, TOL);
        assertEquals(.02, exciter.tnu, TOL); assertEquals(.02, exciter.tw, TOL);
        assertEquals(0.0, exciter.ts1, TOL); assertEquals(.02, exciter.ts2, TOL);
        assertEquals(2.0, exciter.vpu, TOL); assertEquals(3.0, exciter.vpi, TOL);
        assertTrue(exciter.efdmax >= 1.2); assertTrue(exciter.efdmin <= 1.2);
    }

    @Test
    void supportsOptionalAlgebraicBlocksAndRejectsInvalidRequiredParameters() throws Exception {
        ExeliData algebraic = baseData();
        algebraic.setTfv(0); algebraic.setTfi(0); algebraic.setTw(0);
        algebraic.setTs1(0); algebraic.setTs2(0);
        Fixture fixture = fixture(algebraic);
        double initial = fixture.exciter.getOutput(fixture.machine);
        for (int i = 0; i < 100; i++) step(fixture.exciter, fixture.machine, .001);
        assertEquals(initial, fixture.exciter.getOutput(fixture.machine), 1.0e-10);

        ExeliData invalidTnu = baseData(); invalidTnu.setTnu(0);
        assertFalse(canInitialize(invalidTnu));
        ExeliData invalidVpi = baseData(); invalidVpi.setVpi(0);
        assertFalse(canInitialize(invalidVpi));
        ExeliData invalidFollowUp = baseData(); invalidFollowUp.setVpnf(-1);
        assertFalse(canInitialize(invalidFollowUp));
        ExeliData invalidLimit = baseData(); invalidLimit.setSmax(0);
        assertFalse(canInitialize(invalidLimit));
    }

    @Test
    void clampsCurrentControllerOutputAndRunsThroughTheSolver() throws Exception {
        ExeliData limitedData = baseData();
        limitedData.setEfdmin(-.5); limitedData.setEfdmax(.5);
        Fixture limited = fixture(limitedData);
        limited.exciter.setRefPoint(limited.exciter.getRefPoint() + 10.0);
        assertEquals(1.2, limited.exciter.getOutput(limited.machine), TOL);
        assertTrue(limited.exciter.getUnlimitedOutput() > limited.exciter.efdmax);

        DStabNetworkBuilder builder = DStabBuilderTestFixture.createWithMachine();
        assertNotNull(builder.addExcExeli("Bus1", "1", baseData()));
        DynamicSimuAlgorithm algorithm = DStabObjectFactory
                .createDynamicSimuAlgorithm(builder.getDStabNetwork());
        algorithm.setSimuMethod(DynamicSimuMethod.MODIFIED_EULER);
        algorithm.setSimuStepSec(.005); algorithm.setTotalSimuTimeSec(.02);
        algorithm.setSimuOutputHandler(new StateMonitor());
        assertTrue(algorithm.getAclfAlgorithm().loadflow());
        assertTrue(algorithm.initialization());
        assertTrue(algorithm.performSimulation());
    }

    private static Fixture fixture(ExeliData data) throws Exception {
        DStabNetworkBuilder builder = DStabBuilderTestFixture.createWithMachine();
        ExeliExciter exciter = builder.addExcExeli("Bus1", "1", data);
        Machine machine = builder.getDStabNetwork().getMachine("Bus1-mach1");
        machine.setEfd(1.2);
        assertNotNull(exciter);
        assertTrue(exciter.initStates(machine.getDStabBus(), machine));
        return new Fixture(machine, exciter);
    }

    private static boolean canInitialize(ExeliData data) throws Exception {
        DStabNetworkBuilder builder = DStabBuilderTestFixture.createWithMachine();
        ExeliExciter exciter = builder.addExcExeli("Bus1", "1", data);
        Machine machine = builder.getDStabNetwork().getMachine("Bus1-mach1");
        machine.setEfd(1.2);
        return exciter.initStates(machine.getDStabBus(), machine);
    }

    private static ExeliData baseData() {
        ExeliData data = new ExeliData();
        data.setTfv(.05); data.setTfi(.04); data.setTnu(.2);
        data.setVpu(2); data.setVpi(3); data.setVpnf(.4); data.setDpnf(.02);
        data.setEfdmin(-4); data.setEfdmax(5); data.setXe(.1);
        data.setTw(.3); data.setKs1(1.2); data.setKs2(.5);
        data.setTs1(.2); data.setTs2(.15); data.setSmax(.25);
        return data;
    }

    private static double[] oracleDerivative(double[] x, double power,
            double terminalVoltage, double fieldCurrent, double reference, ExeliData d) {
        double y1 = power - x[0];
        double y2 = y1 - x[6];
        double y3 = y2 - x[7];
        double lag = x[1];
        double sum = d.getKs1() * y3 + lag;
        double stabilizer = clamp(-(sum - x[2]), -d.getSmax(), d.getSmax());
        double error = reference - x[3] - stabilizer;
        double proportional = d.getVpu() * error;
        double followUp = deadband(x[4] - x[5], d.getDpnf());
        return new double[] {
                (power - x[0]) / d.getTw(),
                (d.getKs2() * y3 - x[1]) / d.getTs1(),
                (sum - x[2]) / d.getTs2(),
                (terminalVoltage - x[3]) / d.getTfv(),
                (fieldCurrent - x[4]) / d.getTfi(),
                (proportional + d.getVpnf() * followUp) / d.getTnu(),
                (y1 - x[6]) / d.getTw(),
                (y2 - x[7]) / d.getTw()
        };
    }

    private static double deadband(double value, double width) {
        if (value > width) return value - width;
        if (value < -width) return value + width;
        return 0.0;
    }

    private static double clamp(double value, double lower, double upper) {
        return Math.max(lower, Math.min(upper, value));
    }

    private static double[] add(double[] x, double[] derivative, double dt) {
        double[] result = new double[x.length];
        for (int i = 0; i < x.length; i++) result[i] = x[i] + derivative[i] * dt;
        return result;
    }

    private static void assertArrayClose(double[] expected, double[] actual, double tolerance) {
        assertEquals(expected.length, actual.length);
        for (int i = 0; i < expected.length; i++) assertEquals(expected[i], actual[i], tolerance);
    }

    private static void step(ExeliExciter exciter, Machine machine, double dt) {
        assertTrue(exciter.nextStep(dt, DynamicSimuMethod.MODIFIED_EULER, machine, 0));
        assertTrue(exciter.nextStep(dt, DynamicSimuMethod.MODIFIED_EULER, machine, 1));
    }

    private record Fixture(Machine machine, ExeliExciter exciter) { }
}
