package org.interpss.core.adapter.builder.dstab;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.file.Path;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.apache.commons.math3.complex.Complex;
import org.interpss.CorePluginTestSetup;
import com.interpss.dstab.svc.Svsmo3t2Model;
import org.interpss.fadapter.psse.PSSEMultiFileLoader;
import org.interpss.fadapter.psse.dyr.DynamicModelCatalog;
import org.interpss.fadapter.psse.dyr.DynamicModelSupportStatus;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.interpss.dstab.StaticLoadModel;
import com.interpss.dstab.algo.DynamicSimuMethod;
import com.interpss.dstab.cache.StateMonitor;

/** Exact ownership, initialization and physical-current replacement contracts. */
public class PsseSvsmo3t2ModelTest extends CorePluginTestSetup {
    private static final Path FIXTURE = Path.of("testData/psse/v33/SVSMO3T2");

    @Test void malformedRecordsAndMissingMssNeverAttachSilently(@TempDir Path temporary) throws Exception {
        String source = Files.readString(FIXTURE.resolve("smib.dyr"));
        var record = org.interpss.fadapter.psse.dyr.PsseDyrRecordReader.read(
                new java.io.StringReader(source), "synthetic.dyr").get(2);
        for (String problem : List.of("short", "long", "nan", "flag", "mss", "empty-mss", "missing", "remote")) {
            var fields = new ArrayList<>(record.fields());
            switch (problem) {
                case "short" -> fields.remove(fields.size() - 1);
                case "long" -> fields.add("0");
                case "nan" -> fields.set(22, "NaN");
                case "flag" -> fields.set(4, "2");
                case "mss" -> { fields.set(3, "1"); fields.set(6, "3"); fields.set(7, "'MISSING'"); }
                case "empty-mss" -> fields.set(3, "1");
                case "missing" -> fields.set(0, "MISSING");
                case "remote" -> fields.set(2, "99");
            }
            fields.set(0, "'" + fields.get(0) + "'");
            fields.set(1, "'SVSMO3T2'");
            Path variant = temporary.resolve(problem + ".dyr");
            Files.writeString(variant, "1 'GENCLS' '1' 1000000 0 /\n2 'GENCLS' '1' 3.5 0 /\n"
                    + String.join(" ", fields) + " /\n");
            var loader = new PSSEMultiFileLoader();
            var network = loader.loadDStab(FIXTURE.resolve("smib.raw").toString(), variant.toString()).getDStabilityNet();
            assertTrue(network.getDStabBus("Bus3").getDynamicBusDeviceList().stream()
                    .noneMatch(Svsmo3t2Model.class::isInstance), problem);
            var entry = loader.getLastDynamicImportReport().entries().get(2);
            assertNotEquals(org.interpss.fadapter.psse.dyr.DynamicModelImportStatus.ATTACHED, entry.status(), problem);
            assertFalse(loader.getLastDynamicImportReport().isStrictlyComplete());
        }
    }

    @Test void repeatedNamedRecordCannotAttachTwoControllers(@TempDir Path temporary) throws Exception {
        Path repeated = temporary.resolve("repeated.dyr");
        String source = Files.readString(FIXTURE.resolve("smib.dyr"));
        Files.writeString(repeated, source + source.substring(source.indexOf("'STATCOM A'")));
        var loader = new PSSEMultiFileLoader();
        var network = loader.loadDStab(FIXTURE.resolve("smib.raw").toString(), repeated.toString()).getDStabilityNet();
        assertEquals(1, network.getDStabBus("Bus3").getDynamicBusDeviceList().stream()
                .filter(Svsmo3t2Model.class::isInstance).count());
        assertEquals(org.interpss.fadapter.psse.dyr.DynamicModelImportStatus.ERROR,
                loader.getLastDynamicImportReport().entries().get(3).status());
        assertTrue(loader.getLastDynamicImportReport().entries().get(3).message().contains("Duplicate FACTS controller"));
    }

    @Test void sourcePreparationAndReportedCoordinatesPreserveEnteredParameters(@TempDir Path temporary) throws Exception {
        String source = Files.readString(FIXTURE.resolve("smib.dyr"));
        var original = org.interpss.fadapter.psse.dyr.PsseDyrRecordReader.read(
                new java.io.StringReader(source), "synthetic.dyr").get(2);
        double[][] gains = {{.001, .001}, {.04, 0}, {0, .04}, {0, 0}};
        for (int i = 0; i < gains.length; i++) {
            var fields = new ArrayList<>(original.fields());
            fields.set(0, "'STATCOM A'"); fields.set(1, "'SVSMO3T2'");
            fields.set(5, "1");
            fields.set(34, Double.toString(gains[i][0]));
            fields.set(35, Double.toString(gains[i][1]));
            fields.set(55, ".03"); fields.set(56, ".05");
            fields.set(23, ".02");
            Path variant = temporary.resolve("preparation-" + i + ".dyr");
            Files.writeString(variant, source.substring(0, source.indexOf("'STATCOM A'"))
                    + String.join(" ", fields) + " /\n");
            var context = new PSSEMultiFileLoader().loadDStab(FIXTURE.resolve("smib.raw").toString(), variant.toString());
            var algorithm = context.getDynSimuAlgorithm();
            algorithm.getAclfAlgorithm().setTolerance(1e-10);
            algorithm.getAclfAlgorithm().getLfAdjAlgo().setLocalContinuousSvcAsPv(true);
            algorithm.getAclfAlgorithm().getLfAdjAlgo().getVoltAdjConfig().setAdjTolerance(1e-9);
            assertTrue(algorithm.getAclfAlgorithm().loadflow());
            algorithm.setSimuStepSec(.0005);
            algorithm.setSimuOutputHandler(new StateMonitor());
            assertTrue(algorithm.initialization());
            var model = context.getDStabilityNet().getDStabBus("Bus3").getDynamicBusDeviceList().stream()
                    .filter(Svsmo3t2Model.class::isInstance).map(Svsmo3t2Model.class::cast).findFirst().orElseThrow();
            assertEquals(1, model.getData().nonlinearDroopFlag());
            assertEquals(gains[i][0], model.getData().kpr());
            assertEquals(gains[i][1], model.getData().kir());
            assertEquals(i == 3, model.isNonlinearDroopActive());
            assertEquals(i == 3 ? 1.00913500786 : 1.00380623341, model.getVoltageReference(), 2e-7);
            assertEquals(.6, model.getNamedState("Voltage measurement filter"), 1e-12);
            assertEquals(1, model.getIntegrationStates().get("Voltage measurement filter"), 1e-12);
            assertEquals(0, model.getStateDerivativeSnapshot().rates().get("Voltage measurement filter"), 1e-12);
        }
    }

    @Test void namedFactsControllerUsesSolvedCurrentAndExplicitBase() throws Exception {
        var context = new PSSEMultiFileLoader().loadDStab(
                FIXTURE.resolve("smib.raw").toString(), FIXTURE.resolve("smib.dyr").toString());
        var network = context.getDStabilityNet();
        var algorithm = context.getDynSimuAlgorithm();
        network.setStaticLoadIncludedInYMatrix(true);
        network.setStaticLoadModel(StaticLoadModel.CONST_Z);
        network.setReactiveStaticLoadModel(StaticLoadModel.CONST_Z);
        algorithm.getAclfAlgorithm().setTolerance(1e-10);
        algorithm.getAclfAlgorithm().getLfAdjAlgo().setLocalContinuousSvcAsPv(true);
        algorithm.getAclfAlgorithm().getLfAdjAlgo().getVoltAdjConfig().setAdjTolerance(1e-9);
        assertTrue(algorithm.getAclfAlgorithm().loadflow());
        algorithm.setSimuMethod(DynamicSimuMethod.MODIFIED_EULER);
        algorithm.setSimuStepSec(.0005);
        algorithm.setSimuOutputHandler(new StateMonitor());
        assertTrue(algorithm.initialization());
        var bus = network.getDStabBus("Bus3");
        var model = bus.getDynamicBusDeviceList().stream().filter(Svsmo3t2Model.class::isInstance)
                .map(Svsmo3t2Model.class::cast).findFirst().orElseThrow();
        assertEquals("STATCOM A", model.getId());
        assertNull(bus.getBusFreqMeasureBlock(), "Voltage-only STATCOM must not retain unused frequency measurement");
        assertSame(bus, model.getRemoteBus());
        assertEquals(100, model.getData().statcomBaseMva());
        assertEquals(64, DynamicModelCatalog.find("SVSMO3T2").orElseThrow().parameterCount());
        assertEquals(DynamicModelSupportStatus.PARTIAL,
                DynamicModelCatalog.find("SVSMO3T2").orElseThrow().supportStatus());
        assertEquals(1, bus.getVoltageMag(), 1e-8);
        assertEquals(.304501, model.getCurrent(), 2e-7);
        assertEquals(1.00380626, model.getVoltageReference(), 2e-7);
        assertEquals(0, ((Complex) model.getOutputObject()).abs(), 1e-12);
        Map<String, Double> initial = model.getNamedStates();
        for (double rate : model.getStateDerivativeSnapshot().rates().values()) assertEquals(0, rate, 1e-11);
        for (int i = 0; i < 20; i++) assertTrue(algorithm.solveDEqnStep(true));
        for (var state : initial.entrySet()) assertEquals(state.getValue(), model.getNamedState(state.getKey()), 1e-10);
        assertThrows(UnsupportedOperationException.class, () -> initial.put("arbitrary", 0.));

        if (java.lang.management.ManagementFactory.getThreadMXBean()
                instanceof com.sun.management.ThreadMXBean allocation && allocation.isThreadAllocatedMemorySupported()) {
            allocation.setThreadAllocatedMemoryEnabled(true);
            for (int i = 0; i < 20000; i++) step(model);
            long thread = Thread.currentThread().threadId();
            long before = allocation.getThreadAllocatedBytes(thread);
            for (int i = 0; i < 100000; i++) step(model);
            long bytes = allocation.getThreadAllocatedBytes(thread) - before;
            System.out.println("SVSMO3T2 warmed predictor/corrector allocation bytes=" + bytes);
            assertTrue(bytes < 16384, "Direct stages must reuse numerical/diagnostic buffers");
        }

        assertTrue(Double.isFinite(model.getStaticDevice().getPendingShuntCurrentLimitPu()));
        assertTrue(Double.isNaN(model.getStaticDevice().getShuntCurrentLimitPu()));
        double stampedB = model.getStaticDevice().getBActual();
        Complex perturbed = bus.getVoltage().multiply(.7);
        bus.setVoltage(perturbed);
        network.formYMatrix4DStab();
        assertEquals(stampedB, model.getStaticDevice().getBActual(), 1e-12);
        Complex increment = (Complex) model.getOutputObject();
        Complex total = increment.add(perturbed.multiply(new Complex(0, -model.getStaticDevice().getBActual())));
        assertEquals(model.getCurrent(), total.abs(), 1e-12,
                "The total physical current remains constant as voltage changes");
        assertEquals(0, perturbed.multiply(total.conjugate()).getReal(), 1e-12);
        var beforeEvent = model.getNamedStates();
        var beforeSnapshot = model.getStateDerivativeSnapshot();
        assertTrue(model.nextStep(0, DynamicSimuMethod.MODIFIED_EULER, 0));
        assertTrue(model.nextStep(0, DynamicSimuMethod.MODIFIED_EULER, 1));
        assertEquals(beforeEvent, model.getNamedStates());
        assertEquals(beforeSnapshot, model.getStateDerivativeSnapshot());
        bus.setVoltage(new Complex(2, 0));
        for (int i = 0; i < 300 && !model.isTripped(); i++) step(model);
        assertTrue(model.isTripped());
        assertEquals(0, model.getCurrent());
        assertFalse(model.isNetworkDetached(), "Trip command precedes the next electrical-network event");
        assertEquals(0, model.getAvailableCurrentRating());
        model.acceptNetworkEvent();
        assertTrue(model.isNetworkDetached());
        var tripped = model.getNamedStates();
        bus.setVoltage(Complex.ONE);
        for (int i = 0; i < 20; i++) step(model);
        assertEquals(tripped, model.getNamedStates(), "A tripped FACTS controller freezes its continuous state");
        assertTrue(model.initStates(bus));
        assertFalse(model.isTripped());
        assertFalse(model.isNetworkDetached());
        assertEquals(0., model.getIntegrationHistories().get("Overvoltage elapsed seconds"));
    }

    private static void step(Svsmo3t2Model model) {
        assertTrue(model.nextStep(.0005, DynamicSimuMethod.MODIFIED_EULER, 0));
        assertTrue(model.nextStep(.0005, DynamicSimuMethod.MODIFIED_EULER, 1));
    }
}
