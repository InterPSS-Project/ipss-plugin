package org.interpss.core.adapter.builder.dstab;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.commons.math3.complex.Complex;
import org.interpss.CorePluginTestSetup;
import org.interpss.dstab.control.exc.psse.esst2a.Esst2aData;
import org.interpss.dstab.control.exc.psse.esst2a.Esst2aExciter;
import org.interpss.fadapter.builder.DStabNetworkBuilder;
import org.interpss.fadapter.psse.PSSEDStabDirectParser;
import org.interpss.fadapter.psse.dyr.DynamicModelCatalog;
import org.interpss.fadapter.psse.dyr.DynamicModelSupportStatus;
import org.interpss.fadapter.psse.dyr.PsseDyrRecordReader;
import org.interpss.fadapter.psse.dyr.WeccApprovedDynamicModelCatalog;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.interpss.dstab.DStabObjectFactory;
import com.interpss.dstab.algo.DynamicSimuAlgorithm;
import com.interpss.dstab.algo.DynamicSimuMethod;
import com.interpss.dstab.cache.StateMonitor;
import com.interpss.dstab.mach.Machine;

/** Import, equation, and integration tests for PSS/E ESST2A. */
public class Esst2aExciterTest extends CorePluginTestSetup {
    private static final double TOL = 1.0e-9;
    private static final Path CORPUS_ROOT = Path.of(System.getProperty("psse.testcases.root",
            Path.of(System.getProperty("user.home"), "OneDrive", "Documents", "qiuhua",
                    "private_cases").toString()));

    @Test
    void parsesRealThirteenParameterRecordAndCatalogsExactSupport(@TempDir Path dir)
            throws Exception {
        DStabNetworkBuilder builder = DStabBuilderTestFixture.createWithMachine();
        Path dyr = dir.resolve("esst2a.dyr");
        Files.writeString(dyr, "1 'ESST2A' 1 0 50 .2 1.5 0 1 .25 .015 .2 3.1 4.5 1.4 3.7 /\n");
        PSSEDStabDirectParser parser = new PSSEDStabDirectParser(builder).setStrictImport(true);
        parser.parseDynFile(dyr.toString());

        Machine machine = builder.getDStabNetwork().getMachine("Bus1-mach1");
        Esst2aExciter exciter = (Esst2aExciter) machine.getExciter();
        assertNotNull(exciter);
        Esst2aData data = exciter.getData();
        assertEquals(0.0, data.getTr(), TOL); assertEquals(50.0, data.getKa(), TOL);
        assertEquals(0.2, data.getTa(), TOL); assertEquals(1.5, data.getVrmax(), TOL);
        assertEquals(0.0, data.getVrmin(), TOL); assertEquals(1.0, data.getKe(), TOL);
        assertEquals(0.25, data.getTe(), TOL); assertEquals(0.015, data.getKf(), TOL);
        assertEquals(0.2, data.getTf(), TOL); assertEquals(3.1, data.getKp(), TOL);
        assertEquals(4.5, data.getKi(), TOL); assertEquals(1.4, data.getKc(), TOL);
        assertEquals(3.7, data.getEfdmax(), TOL);
        assertTrue(parser.getLastImportReport().isStrictlyComplete());
        machine.setEfd(1.2);
        assertTrue(exciter.initStates(machine.getDStabBus(), machine));
        for (int i = 0; i < 1000; i++) step(exciter, machine, 0.0001);
        assertEquals(1.2, exciter.getOutput(machine), 1.0e-8);

        var descriptor = DynamicModelCatalog.find("ESST2A").orElseThrow();
        assertEquals(13, descriptor.parameterCount());
        assertEquals(DynamicModelSupportStatus.LOADABLE, descriptor.supportStatus());
        assertTrue(WeccApprovedDynamicModelCatalog.findExciter("ESST2A")
                .orElseThrow().isImplementedExactly());
        assertEquals("ESST2A", WeccApprovedDynamicModelCatalog.findExciter("EXST2A")
                .orElseThrow().interpssModel());
        assertTrue(WeccApprovedDynamicModelCatalog.findExciter("EXST2A")
                .orElseThrow().isImplementedExactly());
        assertTrue(DynamicModelCatalog.find("EXST2A").isEmpty(),
                "EXST2A is a cross-catalog row, not a native PSS/E model name");
    }

    @Test
    void allSuppliedEsst2aRecordsUseReviewedSchema() throws Exception {
        List<Path> paths = List.of(
                CORPUS_ROOT.resolve("private_case_package/24HSP11p.dyr"),
                CORPUS_ROOT.resolve("24LW1a1p_package (1)/24LW1a1p_package/24LW11p.dyr"),
                CORPUS_ROOT.resolve("31hs1ap/31hs1ap_348 (1)/31hs1ap.dyr"));
        assumeTrue(paths.stream().allMatch(Files::isRegularFile),
                "Missing supplied ESST2A corpus under " + CORPUS_ROOT);
        Pattern recordPattern = Pattern.compile("(?ims)^\\s*\\d+\\s+'ESST2A'\\s+[^/]+/");
        int recordCount = 0;
        for (Path path : paths) {
            Matcher matcher = recordPattern.matcher(Files.readString(path));
            while (matcher.find()) {
                String record = matcher.group();
                assertEquals(16, PsseDyrRecordReader.tokenize(
                        record.substring(0, record.lastIndexOf('/'))).size(), path.toString());
                recordCount++;
            }
        }
        assertEquals(85, recordCount);
    }

    @Test
    void fiveStateTrajectoryMatchesPublishedEquations() throws Exception {
        Fixture fixture = fixture(baseData());
        fixture.exciter.setRefPoint(fixture.exciter.getRefPoint() + 0.1);
        double[] x = {1.2, 1.04, 1.2, 1.2, 0.24};
        double dt = 0.0001;
        double maxError = 0.0;
        for (int i = 0; i < 2000; i++) {
            double[] d0 = derivatives(x, 0.1);
            double[] prediction = add(x, d0, dt);
            double[] d1 = derivatives(prediction, 0.1);
            for (int j = 0; j < x.length; j++) x[j] += 0.5 * (d0[j] + d1[j]) * dt;
            step(fixture.exciter, fixture.machine, dt);
            double vf = 0.1 * (x[0] - x[3]) / 0.4;
            maxError = Math.max(maxError, Math.abs(x[0] - fixture.exciter.getOutput(fixture.machine)));
            maxError = Math.max(maxError, Math.abs(x[1] - fixture.exciter.getSensedVoltage()));
            maxError = Math.max(maxError, Math.abs(x[2] - fixture.exciter.getRegulatorOutput()));
            maxError = Math.max(maxError, Math.abs(vf - fixture.exciter.getFeedbackVoltage()));
        }
        assertTrue(maxError < 1.0e-10, "ESST2A five-state max error=" + maxError);
    }

    @Test
    void implementsCompoundSourceAndFiveRegionRectifier() throws Exception {
        Esst2aData data = baseData(); data.setKp(2.0); data.setKi(3.0); data.setKc(0.0);
        Fixture fixture = fixture(data);
        Complex expected = fixture.machine.getDStabBus().getVoltage().multiply(2.0)
                .add(fixture.machine.getIxy().multiply(new Complex(0.0, 3.0)));
        assertEquals(expected.abs(), fixture.exciter.getCompoundSourceVoltage(), TOL);
        assertEquals(expected.abs(), fixture.exciter.getBridgeVoltage(), TOL);
        assertEquals(1.0, Esst2aExciter.rectifierFactor(-0.1), TOL);
        assertEquals(1.0 - 0.577 * 0.2, Esst2aExciter.rectifierFactor(0.2), TOL);
        assertEquals(Math.sqrt(0.75 - 0.5 * 0.5), Esst2aExciter.rectifierFactor(0.5), TOL);
        assertEquals(1.732 * 0.1, Esst2aExciter.rectifierFactor(0.9), TOL);
        assertEquals(0.0, Esst2aExciter.rectifierFactor(1.1), TOL);
    }

    @Test
    void preservesAllThreeUelLocations() throws Exception {
        Esst2aData before = baseData(); before.setTb(0.2); before.setTc(0.1); before.setUel(2);
        Fixture gateOne = fixture(before); gateOne.exciter.setVuel(0.5);
        assertEquals(0.5, gateOne.exciter.getLeadLagInput(), TOL);
        assertEquals(0.37, gateOne.exciter.getLeadLagOutput(), TOL);

        Esst2aData after = baseData(); after.setTb(0.2); after.setTc(0.1); after.setUel(3);
        Fixture gateTwo = fixture(after); gateTwo.exciter.setVuel(0.5);
        assertEquals(0.24, gateTwo.exciter.getLeadLagInput(), TOL);
        assertEquals(0.5, gateTwo.exciter.getRegulatorInput(), TOL);

        Esst2aData summed = baseData(); summed.setUel(0);
        Fixture errorSum = fixture(summed); errorSum.exciter.setVuel(0.1);
        assertEquals(0.34, errorSum.exciter.getLeadLagInput(), TOL);
    }

    @Test
    void appliesPowerWorldCorrectionsAndInitializationExpansion() throws Exception {
        Esst2aData data = baseData();
        data.setTr(0.004); data.setTa(0.015); data.setTe(0.015); data.setTf(0.004);
        data.setKa(0.0); data.setVrmax(-3.0); data.setVrmin(-2.0); data.setEfdmax(0.5);
        DStabNetworkBuilder builder = DStabBuilderTestFixture.createWithMachine();
        Esst2aExciter exciter = builder.addExcEsst2a("Bus1", "1", data);
        Machine machine = builder.getDStabNetwork().getMachine("Bus1-mach1");
        machine.setEfd(1.2);
        exciter.configureIntegrationStep(0.01, 2.0);
        assertTrue(exciter.initStates(machine.getDStabBus(), machine));
        assertEquals(0.0, exciter.tr, TOL); assertEquals(0.02, exciter.ta, TOL);
        assertEquals(0.02, exciter.te, TOL); assertEquals(0.0, exciter.tf, TOL);
        assertEquals(0.02, exciter.ka, TOL);
        assertTrue(exciter.vrmax >= exciter.getRegulatorOutput());
        assertTrue(exciter.vrmin <= exciter.getRegulatorOutput());
        assertTrue(exciter.efdmax >= machine.getEfd());
    }

    @Test
    void participatesInFullDynamicSolver() throws Exception {
        DStabNetworkBuilder builder = DStabBuilderTestFixture.createWithMachine();
        assertNotNull(builder.addExcEsst2a("Bus1", "1", baseData()));
        DynamicSimuAlgorithm algorithm = DStabObjectFactory
                .createDynamicSimuAlgorithm(builder.getDStabNetwork());
        algorithm.setSimuMethod(DynamicSimuMethod.MODIFIED_EULER);
        algorithm.setSimuStepSec(0.005); algorithm.setTotalSimuTimeSec(0.02);
        algorithm.setSimuOutputHandler(new StateMonitor());
        assertTrue(algorithm.getAclfAlgorithm().loadflow());
        assertTrue(algorithm.initialization());
        assertTrue(algorithm.performSimulation());
    }

    private static Fixture fixture(Esst2aData data) throws Exception {
        DStabNetworkBuilder builder = DStabBuilderTestFixture.createWithMachine();
        Esst2aExciter exciter = builder.addExcEsst2a("Bus1", "1", data);
        Machine machine = builder.getDStabNetwork().getMachine("Bus1-mach1");
        machine.setEfd(1.2);
        assertTrue(exciter.initStates(machine.getDStabBus(), machine));
        return new Fixture(machine, exciter);
    }
    private static Esst2aData baseData() {
        Esst2aData data = new Esst2aData();
        data.setTr(0.1); data.setKa(5.0); data.setTa(0.2);
        data.setVrmax(10.0); data.setVrmin(-10.0); data.setKe(1.0); data.setTe(0.3);
        data.setKf(0.1); data.setTf(0.4); data.setKp(0.0); data.setKi(0.0);
        data.setKc(0.2); data.setEfdmax(5.0);
        return data;
    }
    private static double[] derivatives(double[] x, double referenceStep) {
        double vf = 0.1 * (x[0] - x[3]) / 0.4;
        double error = 1.28 + referenceStep - x[1] - vf;
        return new double[] {(x[2] - x[0]) / 0.3, (1.04 - x[1]) / 0.1,
                (5.0 * error - x[2]) / 0.2, (x[0] - x[3]) / 0.4, 0.0};
    }
    private static double[] add(double[] x, double[] derivative, double dt) {
        double[] result = new double[x.length];
        for (int i = 0; i < x.length; i++) result[i] = x[i] + derivative[i] * dt;
        return result;
    }
    private static void step(Esst2aExciter exciter, Machine machine, double dt) {
        exciter.nextStep(dt, DynamicSimuMethod.MODIFIED_EULER, machine, 0);
        exciter.nextStep(dt, DynamicSimuMethod.MODIFIED_EULER, machine, 1);
    }
    private record Fixture(Machine machine, Esst2aExciter exciter) { }
}
