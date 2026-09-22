package org.interpss.core.adapter.builder.dstab;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;

import org.apache.commons.math3.complex.Complex;
import org.interpss.IpssCorePlugin;
import org.interpss.dstab.mach.Wt3g1Data;
import org.interpss.dstab.mach.Wt3g1Model;
import org.interpss.fadapter.psse.PSSEMultiFileLoader;
import org.interpss.fadapter.psse.dyr.DynamicModelCatalog;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.interpss.dstab.DStabGen;
import com.interpss.dstab.algo.DynamicSimuMethod;
import com.interpss.dstab.cache.StateMonitor;

/** Exact import, initialization, equation, and named-state checks for WT3G1. */
public class PsseWt3g1ModelTest {
    private static final Path CASE = Path.of("testData", "adpter", "psse", "v33", "SMIB");

    @Test
    void exactNativeRecordAttachesAndInitializes() throws Exception {
        Fixture fixture = load();
        Wt3g1Model model = fixture.model;
        assertEquals(new Wt3g1Data(40, .33403, 24.0, .60, .120, 1.630), model.getData());
        assertEquals(6, DynamicModelCatalog.find("WT3G1").orElseThrow().parameterCount());
        assertEquals(4, model.getNamedStates().size());
        assertEquals(fixture.network.getBus("Bus1").getVoltage().getArgument(),
                model.getPllAngleState(), 1.0e-12);
        assertEquals(0.0, model.getPllIntegralState(), 1.0e-12);
        assertEquals(.5, model.getP(), 2.0e-6);
        assertEquals(65.2, model.getData().aggregateRatedMw(), 1.0e-12);
    }

    @Test
    void modifiedEulerUsesFixedConverterLagsAndPublishedPllCoordinates() throws Exception {
        Fixture fixture = load();
        Wt3g1Model model = fixture.model;
        double initialIp = model.getActiveCurrentState();
        double initialEq = model.getInternalVoltageState();
        double initialAngle = model.getPllAngleState();
        model.setCommands(initialIp + .08, initialEq - .04);
        fixture.network.getBus("Bus1").setVoltage(new Complex(
                .96 * Math.cos(initialAngle - .012),
                .96 * Math.sin(initialAngle - .012)));

        double dt = .001;
        double omega0 = 2.0 * Math.PI * fixture.network.getFrequency();
        double vy0 = .96 * Math.sin(-.012);
        double proportional0 = model.getData().pllGain() * vy0 / omega0;
        double pllRate0 = model.getData().pllIntegratorGain() * proportional0;
        double angleRate0 = omega0 * proportional0;
        double predictedAngle = initialAngle + dt * angleRate0;
        double predictedIntegral = dt * pllRate0;
        double vy1 = .96 * Math.sin(initialAngle - .012 - predictedAngle);
        double proportional1 = model.getData().pllGain() * vy1 / omega0;
        double pllRate1 = model.getData().pllIntegratorGain() * proportional1;
        double angleRate1 = omega0 * (proportional1 + predictedIntegral);

        assertTrue(model.nextStep(dt, DynamicSimuMethod.MODIFIED_EULER, 0));
        assertTrue(model.nextStep(dt, DynamicSimuMethod.MODIFIED_EULER, 1));

        double dip0 = .08 / .02;
        double predictedIp = initialIp + dt * dip0;
        double dip1 = (initialIp + .08 - predictedIp) / .02;
        double deq0 = -.04 / .02;
        double predictedEq = initialEq + dt * deq0;
        double deq1 = (initialEq - .04 - predictedEq) / .02;
        assertEquals(initialIp + .5 * dt * (dip0 + dip1),
                model.getActiveCurrentState(), 1.0e-12);
        assertEquals(initialEq + .5 * dt * (deq0 + deq1),
                model.getInternalVoltageState(), 1.0e-12);
        assertEquals(.5 * dt * (pllRate0 + pllRate1),
                model.getPllIntegralState(), 1.0e-12);
        assertEquals(initialAngle + .5 * dt * (angleRate0 + angleRate1),
                model.getPllAngleState(), 1.0e-12);
    }

    @Test
    void zeroPllLimitFreezesBothPllStates() throws Exception {
        var builder = DStabBuilderTestFixture.createBuilder();
        Wt3g1Model model = builder.addWt3g1("Bus1", "1",
                new Wt3g1Data(12, .31, 31.0, .75, 0.0, 1.71));
        assertTrue(model.initStates(model.getDStabBus()));
        double angle = model.getPllAngleState();
        model.getDStabBus().setVoltage(new Complex(
                .9 * Math.cos(angle + .1), .9 * Math.sin(angle + .1)));
        assertTrue(model.nextStep(.001, DynamicSimuMethod.MODIFIED_EULER, 0));
        assertTrue(model.nextStep(.001, DynamicSimuMethod.MODIFIED_EULER, 1));
        assertEquals(0.0, model.getPllIntegralState(), 0.0);
        assertEquals(angle, model.getPllAngleState(), 0.0);
    }

    @Test
    void rejectsExtraNativeFieldAndInvalidParameters(@TempDir Path tempDir) throws Exception {
        Path dyr = tempDir.resolve("extra.dyr");
        Files.writeString(dyr,
                "1 'WT3G1' '1' 40 .33403 24 .60 .12 1.63 99 /\n"
                        + "2 'GENCLS' '1' 99999 0 /\n");
        var context = new PSSEMultiFileLoader().loadDStab(
                CASE.resolve("SMIB_v33_wt2g1.raw").toString(), dyr.toString());
        DStabGen gen = (DStabGen) context.getDStabilityNet().getBus("Bus1")
                .getContributeGen("1");
        assertTrue(!(gen.getDynamicGenDevice() instanceof Wt3g1Model));
        assertThrows(IllegalArgumentException.class,
                () -> new Wt3g1Data(0, .3, 20, .5, .1, 1.5));
        assertThrows(IllegalArgumentException.class,
                () -> new Wt3g1Data(20, 0, 20, .5, .1, 1.5));
    }

    private static Fixture load() throws Exception {
        IpssCorePlugin.init();
        var context = new PSSEMultiFileLoader().loadDStab(
                CASE.resolve("SMIB_v33_wt2g1.raw").toString(),
                CASE.resolve("SMIB_v33_wt3g1.dyr").toString());
        var algorithm = context.getDynSimuAlgorithm();
        var network = context.getDStabilityNet();
        DStabGen attachedGen = (DStabGen) network.getBus("Bus1").getContributeGen("1");
        Wt3g1Model attachedModel = assertInstanceOf(Wt3g1Model.class,
                attachedGen.getDynamicGenDevice());
        // WT3G1 is a converter device, not a synchronous Machine instance.
        algorithm.getAclfAlgorithm().getDataCheckConfig().setAllowGenWithoutMachine(true);
        assertTrue(algorithm.getAclfAlgorithm().loadflow());
        algorithm.setSimuMethod(DynamicSimuMethod.MODIFIED_EULER);
        algorithm.setSimuStepSec(.0005);
        algorithm.setSimuOutputHandler(new StateMonitor());
        assertTrue(algorithm.initialization());
        return new Fixture(network, attachedModel);
    }

    private record Fixture(com.interpss.dstab.BaseDStabNetwork<?, ?> network,
            Wt3g1Model model) { }
}
