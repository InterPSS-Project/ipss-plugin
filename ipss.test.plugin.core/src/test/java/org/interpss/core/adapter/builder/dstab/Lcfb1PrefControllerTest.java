package org.interpss.core.adapter.builder.dstab;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;

import org.interpss.CorePluginTestSetup;
import org.interpss.dstab.control.gov.psse.lcfb1.Lcfb1Data;
import org.interpss.dstab.control.gov.psse.lcfb1.Lcfb1PrefController;
import org.interpss.fadapter.builder.DStabNetworkBuilder;
import org.interpss.fadapter.psse.PSSEDStabDirectParser;
import org.interpss.fadapter.psse.dyr.DynamicModelCatalog;
import org.interpss.fadapter.psse.dyr.DynamicModelImportStatus;
import org.interpss.fadapter.psse.dyr.DynamicModelSupportStatus;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.interpss.dstab.algo.DynamicSimuMethod;
import com.interpss.dstab.algo.DynamicSimuAlgorithm;
import com.interpss.dstab.DStabObjectFactory;
import com.interpss.dstab.cache.StateMonitor;
import com.interpss.dstab.mach.Machine;

public class Lcfb1PrefControllerTest extends CorePluginTestSetup {
    private static final double TOL = 1.0e-9;

    @Test
    void mapsNativeRecordAfterGovernorRegardlessOfRecordOrder(@TempDir Path tempDir)
            throws Exception {
        DStabNetworkBuilder builder = DStabBuilderTestFixture.createWithMachine();
        Path dyr = tempDir.resolve("lcfb1.dyr");
        Files.writeString(dyr,
                "1 'LCFB1' 1 0 1 0.0 1.0 0.0 0.1 0.0 .00015 .025 /\n"
                + "1 'TGOV1' 1 .05 .1 1.0 0.0 .2 .5 0.0 /\n");

        PSSEDStabDirectParser parser = new PSSEDStabDirectParser(builder).setStrictImport(true);
        parser.parseDynFile(dyr.toString());
        Machine machine = builder.getDStabNetwork().getMachine("Bus1-mach1");
        Lcfb1PrefController controller = find(builder);

        assertNotNull(controller);
        assertSame(machine.getGovernor(), controller.getGovernor());
        assertEquals(0, controller.getData().frequencyBiasFlag());
        assertEquals(1, controller.getData().powerControlFlag());
        assertEquals(1.0, controller.getData().powerTransducerTime(), TOL);
        assertEquals(.00015, controller.getData().integralGain(), TOL);
        assertEquals(.025, controller.getData().maximumReferenceBias(), TOL);
        assertEquals(2, parser.getLastImportReport().count(DynamicModelImportStatus.ATTACHED));
        assertTrue(parser.getLastImportReport().isStrictlyComplete());
    }

    @Test
    void catalogDefinesNativeSchemaAndPowerWorldAlias() {
        var descriptor = DynamicModelCatalog.find("LCFB1_PTI").orElseThrow();
        assertEquals("LCFB1", descriptor.canonicalName());
        assertEquals(9, descriptor.parameterCount());
        assertEquals(DynamicModelSupportStatus.LOADABLE, descriptor.supportStatus());
    }

    @Test
    void initializesAtSolvedPowerWithoutReferenceStep() throws Exception {
        Fixture f = fixture(new Lcfb1Data(1, 1, 10, .1, 0, .1, 1, 1, .05));
        assertEquals(.4, f.controller.getPowerSetpoint(), TOL);
        assertEquals(.4, f.controller.getSensedPower(), TOL);
        assertEquals(0.0, f.controller.getReferenceBias(), TOL);
        assertEquals(.4, f.controller.getGovernorReference(), TOL);
        for (int i = 0; i < 100; i++) step(f.controller, .01);
        assertEquals(0.0, f.controller.getReferenceBias(), TOL);
    }

    @Test
    void powerDeficitRaisesReferenceAndHonorsBiasLimit() throws Exception {
        Fixture f = fixture(new Lcfb1Data(0, 1, 0, 0, 0, 1, 2, 1, .05));
        f.machine.setPe(.3);
        for (int i = 0; i < 100; i++) step(f.controller, .01);
        assertEquals(.05, f.controller.getReferenceBias(), TOL);
        assertEquals(.45, f.controller.getGovernorReference(), TOL);
    }

    @Test
    void underfrequencyBiasRaisesGovernorReference() throws Exception {
        Fixture f = fixture(new Lcfb1Data(1, 0, 10, 0, 0, 1, .5, 0, .2));
        f.machine.setSpeed(.99);
        step(f.controller, .01);
        assertEquals(.05, f.controller.getReferenceBias(), 1.0e-12);
    }

    @Test
    void continuousDeadbandAndErrorClampPrecedePi() throws Exception {
        Fixture f = fixture(new Lcfb1Data(0, 1, 0, 0, .02, .03, 2, 0, 1));
        f.machine.setPe(.30);
        step(f.controller, .01);
        assertEquals(.06, f.controller.getReferenceBias(), TOL);
    }

    @Test
    void powerTransducerMatchesFirstOrderResponse() throws Exception {
        Fixture f = fixture(new Lcfb1Data(0, 1, 0, .5, 0, 1, 0, 0, 1));
        f.machine.setPe(.3);
        double dt = .0005;
        for (int i = 0; i < 2000; i++) step(f.controller, dt);
        double expected = .3 + .1 * Math.exp(-1.0 / .5);
        assertEquals(expected, f.controller.getSensedPower(), 2.0e-8);
    }

    @Test
    void participatesInFullDynamicAlgorithmWithoutReplacingGovernor() throws Exception {
        DStabNetworkBuilder builder = DStabBuilderTestFixture.createWithMachine();
        Machine machine = builder.getDStabNetwork().getMachine("Bus1-mach1");
        var governor = builder.addGovTgov1("Bus1", "1", .05, .1, 1, 0, .2, .5, 0);
        Lcfb1PrefController controller = builder.addLcfb1("Bus1", "1",
                new Lcfb1Data(1, 1, 10, .1, 0, .1, 1, 1, .05));
        DynamicSimuAlgorithm algorithm = DStabObjectFactory
                .createDynamicSimuAlgorithm(builder.getDStabNetwork());
        algorithm.setSimuMethod(DynamicSimuMethod.MODIFIED_EULER);
        algorithm.setSimuStepSec(.005);
        algorithm.setTotalSimuTimeSec(.02);
        algorithm.setSimuOutputHandler(new StateMonitor());

        assertTrue(algorithm.getAclfAlgorithm().loadflow());
        assertTrue(algorithm.initialization());
        assertTrue(algorithm.performSimulation());
        assertSame(governor, machine.getGovernor());
        assertTrue(Math.abs(controller.getReferenceBias()) <= .05 + TOL);
        assertTrue(Double.isFinite(controller.getGovernorReference()));
    }

    private static Fixture fixture(Lcfb1Data data) throws Exception {
        DStabNetworkBuilder builder = DStabBuilderTestFixture.createWithMachine();
        Machine machine = builder.getDStabNetwork().getMachine("Bus1-mach1");
        machine.setPm(.4); machine.setPe(.4); machine.setSpeed(1.0);
        assertNotNull(builder.addGovTgov1("Bus1", "1", .05, .1, 1, 0, .2, .5, 0));
        Lcfb1PrefController controller = builder.addLcfb1("Bus1", "1", data);
        assertNotNull(controller);
        assertTrue(controller.initStates(machine.getDStabBus()));
        return new Fixture(machine, controller);
    }

    private static Lcfb1PrefController find(DStabNetworkBuilder builder) {
        return builder.getDStabNetwork().getDStabBus("Bus1").getDynamicBusDeviceList()
                .stream().filter(Lcfb1PrefController.class::isInstance)
                .map(Lcfb1PrefController.class::cast).findFirst().orElse(null);
    }

    private static void step(Lcfb1PrefController controller, double dt) {
        controller.nextStep(dt, DynamicSimuMethod.MODIFIED_EULER, 0);
        controller.nextStep(dt, DynamicSimuMethod.MODIFIED_EULER, 1);
    }

    private record Fixture(Machine machine, Lcfb1PrefController controller) { }
}
