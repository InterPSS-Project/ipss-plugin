package org.interpss.core.adapter.builder.dstab;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.apache.commons.math3.complex.Complex;
import org.interpss.CorePluginTestSetup;
import org.interpss.core.dstab.reference.EmbeddedNativeTrajectoryValues;
import org.interpss.dstab.dynLoad.impl.IeelLoadModel;
import org.interpss.fadapter.builder.AclfNetworkBuilder;
import org.interpss.fadapter.builder.DStabNetworkBuilder;
import org.interpss.fadapter.psse.PSSEDStabDirectParser;
import org.interpss.fadapter.psse.PSSEMultiFileLoader;
import org.interpss.fadapter.psse.dyr.DynamicModelImportStatus;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.interpss.dstab.algo.DynamicSimuMethod;
import com.interpss.dstab.cache.StateMonitor;

/** Equation, scope, schema, and lifecycle tests for native PSS/E IEEL loads. */
public class IeelLoadModelTest extends CorePluginTestSetup {
    @Test
    void staggeredIntegrationKeepsTheAlgebraicLoadCurrentLive() throws Exception {
        Path data=Path.of("testData/adpter/psse/v33/HspModels");
        var context=new PSSEMultiFileLoader().loadDStab(data.resolve("network.raw").toString(),data.resolve("tiocr1-0.dyr").toString());
        var a=context.getDynSimuAlgorithm();var net=a.getNetwork();
        for(var b:net.getBusList())for(var d:b.getDynamicBusDeviceList())d.setStatus(false);
        var bus=net.getDStabBus("Bus2");
        var model=new IeelLoadModel("IEELBL",bus,bus.getContributeLoad("1"),
                new org.interpss.dstab.dynLoad.IeelLoadData(.23,.31,.46,.37,.22,.41,1.37,-.63,.8,1.7,2.4,.6,1.4,2.2));
        assertTrue(a.getAclfAlgorithm().loadflow());
        a.setSolver(new org.interpss.dstab.algo.StaggeredEulerSolver(a));
        a.setSimuStepSec(.001);a.setTotalSimuTimeSec(.01);a.setSimuOutputHandler(new StateMonitor());
        assertTrue(a.initialization());assertEquals(0,model.staggeredStateCount());
        for(int i=0;i<10;i++)assertTrue(a.solveDEqnStep(true));
        double initial=model.effectivePower().getReal();
        bus.setVoltage(bus.getVoltage().multiply(.8));bus.setFreq(.99);
        assertTrue(Math.abs(model.effectivePower().getReal()-initial)>1e-3);
        var measured=bus.getVoltage().multiply(model.getEquivY().multiply(bus.getVoltage()).subtract(model.getNortonCurInj()).conjugate());
        assertEquals(0.,measured.subtract(model.effectivePower()).abs(),1e-12);
    }

    private static final String SYNTHETIC_PARAMETERS =
            "0.23 0.31 0.46 0.37 0.22 0.41 1.37 -0.63 "
                    + "0.8 1.7 2.4 0.6 1.4 2.2";
    @Test
    void ieelblReplacesAllStaticComponentsWithPublishedAlgebraicEquation(
            @TempDir Path directory) throws Exception {
        DStabNetworkBuilder builder = DStabBuilderTestFixture.createBuilder();
        new AclfNetworkBuilder(builder.getDStabNetwork()).addContributeLoad("Bus1", "L", true,
                new Complex(0.8, 0.3), null, null, null, false);
        Path dyr = directory.resolve("ieelbl.dyr");
        Files.writeString(dyr, "1 'IEELBL' 'L' " + SYNTHETIC_PARAMETERS + " /\n");
        PSSEDStabDirectParser parser = new PSSEDStabDirectParser(builder).setStrictImport(true);
        parser.parseDynFile(dyr.toString());

        var bus = builder.getDStabNetwork().getDStabBus("Bus1");
        IeelLoadModel model = assertInstanceOf(IeelLoadModel.class,
                bus.getDynLoadModelList().getFirst());
        assertTrue(model.initStates());
        assertEquals(0.8, model.getInitLoadPQ().getReal(), 1.0e-12);
        assertEquals(0.3, model.getInitLoadPQ().getImaginary(), 1.0e-12);

        double voltage = 0.82;
        double frequency = 0.987;
        bus.setVoltage(new Complex(voltage * Math.cos(0.17), voltage * Math.sin(0.17)));
        bus.setFreq(frequency);
        double expectedP = model.getActivePowerAtOnePu()
                * (0.23 * Math.pow(voltage, 0.8) + 0.31 * Math.pow(voltage, 1.7)
                        + 0.46 * Math.pow(voltage, 2.4))
                * (1.0 + 1.37 * (frequency - 1.0));
        double expectedQ = model.getReactivePowerAtOnePu()
                * (0.37 * Math.pow(voltage, 0.6) + 0.22 * Math.pow(voltage, 1.4)
                        + 0.41 * Math.pow(voltage, 2.2))
                * (1.0 - 0.63 * (frequency - 1.0));
        assertEquals(expectedP, model.effectivePower().getReal(), 1.0e-12);
        assertEquals(expectedQ, model.effectivePower().getImaginary(), 1.0e-12);

        Complex netLoadCurrent = model.getEquivY().multiply(bus.getVoltage())
                .subtract(model.getNortonCurInj());
        Complex reconstructedPower = bus.getVoltage().multiply(netLoadCurrent.conjugate());
        assertEquals(expectedP, reconstructedPower.getReal(), 1.0e-12);
        assertEquals(expectedQ, reconstructedPower.getImaginary(), 1.0e-12);
        assertEquals(1, parser.getLastImportReport().count(DynamicModelImportStatus.ATTACHED));
    }

    @Test
    void followsMeasuredLowVoltagePowerWithTwoIndependentLoadRecords() throws Exception {
        DStabNetworkBuilder builder = DStabBuilderTestFixture.createBuilder();
        var bus = builder.getDStabNetwork().getDStabBus("Bus1");
        var topology = new AclfNetworkBuilder(builder.getDStabNetwork());
        topology.addContributeLoad("Bus1", "A", true,
                new Complex(0.7, 0.32), null, null, null, false);
        topology.addContributeLoad("Bus1", "B", true,
                new Complex(0.3, 0.18), null, null, null, false);
        double initialVoltage = 0.9588847160339355;
        // Recorded voltage, frequency deviation and load A P/Q on the system base.
        double[][] checkpoints = {
            {0.0, 0.39457109570503235, 0.024999840185046196, 0.6404011249542236, 0.05282893404364586},
            {0.0, 0.18308784067630768, 0.02499990165233612, 0.25322845578193665, 0.0113747026771307},
            {0.5, 0.1835470199584961, 0.024999873712658882, 0.1566893458366394, 0.01143183745443821},
            {1.0, 0.3963426947593689, 0.024999873712658882, 0.28693118691444397, 0.053304433822631836},
            {1.0, 0.12351011484861374, 0.02499987930059433, 0.047712452709674835, 0.005176386330276728},
        };
        for (double[] row : checkpoints) {
            bus.getDynLoadModelList().clear();
            bus.setVoltage(new Complex(initialVoltage));
            bus.setFreq(1.0);
            var data = new org.interpss.dstab.dynLoad.IeelLoadData(
                    1, 0, 0, 1, 0, 0, 1, -1, row[0], 1, 0, 2, 1, 0);
            var first = new IeelLoadModel("IEELAR", bus, bus.getContributeLoad("A"), data);
            var second = new IeelLoadModel("IEELAR", bus, bus.getContributeLoad("B"), data);
            assertTrue(first.initStates());
            assertTrue(second.initStates());
            assertEquals(0.7, first.getInitLoadPQ().getReal(), 1.0e-12);
            assertEquals(0.3, second.getInitLoadPQ().getReal(), 1.0e-12);
            bus.setVoltage(new Complex(row[1]));
            bus.setFreq(1.0 + row[2]);
            assertEquals(row[3], first.effectivePower().getReal(), 2.0e-6);
            assertEquals(row[4], first.effectivePower().getImaginary(), 2.0e-6);
            assertEquals(row[3] * 0.3 / 0.7, second.effectivePower().getReal(), 2.0e-6);
            assertEquals(row[4] * 0.18 / 0.32, second.effectivePower().getImaginary(), 2.0e-6);
            Complex measured = bus.getVoltage().multiply(first.getEquivY().multiply(bus.getVoltage())
                    .subtract(first.getNortonCurInj()).conjugate());
            assertEquals(row[3], measured.getReal(), 2.0e-6);
            assertEquals(row[4], measured.getImaginary(), 2.0e-6);
            bus.setVoltage(Complex.ZERO);
            assertEquals(Complex.ZERO, first.effectivePower());
            assertEquals(Complex.ZERO, first.getNortonCurInj());
        }
    }

    @Test
    void ieelarWildcardAttachesOnlyToActiveLoadsInTheSelectedArea(
            @TempDir Path directory) throws Exception {
        DStabNetworkBuilder builder = DStabBuilderTestFixture.createBuilder();
        AclfNetworkBuilder topology = new AclfNetworkBuilder(builder.getDStabNetwork());
        topology.addArea("7", "selected", null);
        topology.addArea("8", "other", null);
        builder.getDStabNetwork().getDStabBus("Bus1")
                .setArea(builder.getDStabNetwork().getArea("7"));
        topology.addContributeLoad("Bus1", "A", true,
                new Complex(0.2, 0.05), null, null, null, false);
        topology.addBus("Bus2", "selected load", 2L, 115000.0, 1.0, 0.0,
                "7", null, null);
        topology.addContributeLoad("Bus2", "B", true,
                new Complex(0.3, 0.08), null, null, null, false);
        topology.addBus("Bus3", "other load", 3L, 115000.0, 1.0, 0.0,
                "8", null, null);
        topology.addContributeLoad("Bus3", "C", true,
                new Complex(0.4, 0.1), null, null, null, false);
        Path dyr = directory.resolve("ieelar.dyr");
        Files.writeString(dyr, "7 'IEELAR' '*' " + SYNTHETIC_PARAMETERS + " /\n");
        new PSSEDStabDirectParser(builder).setStrictImport(true).parseDynFile(dyr.toString());

        assertEquals(1, builder.getDStabNetwork().getDStabBus("Bus1")
                .getDynLoadModelList().size());
        assertEquals(1, builder.getDStabNetwork().getDStabBus("Bus2")
                .getDynLoadModelList().size());
        assertTrue(builder.getDStabNetwork().getDStabBus("Bus3")
                .getDynLoadModelList().isEmpty());
    }

    @Test
    void ieelarUsesTheLoadRecordAreaWhenItDiffersFromTheBus(
            @TempDir Path directory) throws Exception {
        Path sourceRaw = Path.of("testData", "adpter", "psse", "v33", "ieee9_v33.raw");
        String rawText = Files.readString(sourceRaw);
        String originalLoadPrefix = "5,'1 ',1,   1,   1,";
        assertTrue(rawText.contains(originalLoadPrefix));
        Path raw = directory.resolve("public-ieee9-load-area.raw");
        Files.writeString(raw, rawText.replace(originalLoadPrefix,
                "5,'1 ',1,   7,   9,"));
        Path dyr = directory.resolve("synthetic-area-load.dyr");
        Files.writeString(dyr, "7 'IEELAR' '*' " + SYNTHETIC_PARAMETERS + " /\n");

        var context = new PSSEMultiFileLoader().loadDStab(raw.toString(), dyr.toString());
        var network = context.getDStabilityNet();
        assertEquals(1, network.getDStabBus("Bus5").getArea().getNumber());
        assertEquals(1, network.getDStabBus("Bus5").getDynLoadModelList().size());
        assertTrue(network.getDStabBus("Bus6").getDynLoadModelList().isEmpty());
        assertTrue(network.getDStabBus("Bus8").getDynLoadModelList().isEmpty());
    }

    @Test
    void rejectsWrongSchemaAndInvalidInitialization(@TempDir Path directory) throws Exception {
        DStabNetworkBuilder builder = DStabBuilderTestFixture.createBuilder();
        new AclfNetworkBuilder(builder.getDStabNetwork()).addContributeLoad("Bus1", "L", true,
                new Complex(0.8, 0.3), null, null, null, false);
        Path dyr = directory.resolve("bad-ieelbl.dyr");
        Files.writeString(dyr, "1 'IEELBL' 'L' 0 0 0 0 0 0 0 0 0 0 0 0 0 /\n");
        PSSEDStabDirectParser parser = new PSSEDStabDirectParser(builder);
        parser.parseDynFile(dyr.toString());
        assertEquals(1, parser.getLastImportReport().count(DynamicModelImportStatus.REJECTED));

        IeelLoadModel model = new IeelLoadModel("IEELBL",
                builder.getDStabNetwork().getDStabBus("Bus1"),
                builder.getDStabNetwork().getDStabBus("Bus1").getContributeLoad("L"),
                new org.interpss.dstab.dynLoad.IeelLoadData(
                        0, 0, 0, 1, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0));
        assertFalse(model.initStates());
    }

    @Test
    void matchesNativePsseVoltageAndFrequencyPlaybackContract() throws Exception {
        Path data = Path.of("testData", "adpter", "psse");
        Path reference = Path.of("testData", "reference", "psse", "ieee9-ieelar", "psse.csv");
        assertTrue(!EmbeddedNativeTrajectoryValues.lines(reference).isEmpty());

        var context = new PSSEMultiFileLoader().loadDStab(
                data.resolve("v33/ieee9_v33.raw").toString(),
                data.resolve("v36/ieee9_ieelar.dyr").toString());
        assertTrue(context.getDynSimuAlgorithm().getAclfAlgorithm().loadflow());
        var bus = context.getDStabilityNet().getDStabBus("Bus5");
        IeelLoadModel model = assertInstanceOf(IeelLoadModel.class,
                bus.getDynLoadModelList().getFirst());

        List<String> lines = EmbeddedNativeTrajectoryValues.lines(reference);
        assertTrue(lines.size() > 1, "embedded IEELAR checkpoints");
        String[] headings = lines.getFirst().split(",");
        Map<String, Integer> columns = new LinkedHashMap<>();
        for (int index = 0; index < headings.length; index++) columns.put(headings[index], index);
        String[] initial = lines.get(1).split(",");
        bus.setVoltage(new Complex(value(initial, columns, "V5"), 0.0));
        bus.setFreq(1.0 + value(initial, columns, "FDEV5"));
        assertTrue(model.initStates());

        double maximumPError = 0.0;
        double maximumQError = 0.0;
        for (String line : lines.subList(1, lines.size())) {
            String[] row = line.split(",");
            bus.setVoltage(new Complex(value(row, columns, "V5"), 0.0));
            bus.setFreq(1.0 + value(row, columns, "FDEV5"));
            Complex actual = model.effectivePower();
            maximumPError = Math.max(maximumPError,
                    Math.abs(actual.getReal() - value(row, columns, "IEEL_P")));
            maximumQError = Math.max(maximumQError,
                    Math.abs(actual.getImaginary() - value(row, columns, "IEEL_Q")));
        }
        assertTrue(maximumPError < 2.0e-6, "IEEL P maximum error=" + maximumPError);
        assertTrue(maximumQError < 7.0e-7, "IEEL Q maximum error=" + maximumQError);
    }

    @Test
    void participatesInAFullDynamicSolutionWithoutDisturbingTheFlatStart() throws Exception {
        Path data = Path.of("testData", "adpter", "psse");
        var context = new PSSEMultiFileLoader().loadDStab(
                data.resolve("v33/ieee9_v33.raw").toString(),
                data.resolve("v36/ieee9_ieelar.dyr").toString());
        var algorithm = context.getDynSimuAlgorithm();
        assertTrue(algorithm.getAclfAlgorithm().loadflow());

        var bus = context.getDStabilityNet().getDStabBus("Bus5");
        assertInstanceOf(IeelLoadModel.class, bus.getDynLoadModelList().getFirst());
        double initialVoltage = bus.getVoltageMag();
        algorithm.setSimuMethod(DynamicSimuMethod.MODIFIED_EULER);
        algorithm.setSimuStepSec(0.001);
        algorithm.setTotalSimuTimeSec(0.05);
        algorithm.setOutPutPerSteps(1);
        algorithm.setSimuOutputHandler(new StateMonitor());
        assertTrue(algorithm.initialization());
        assertTrue(algorithm.performSimulation());

        assertTrue(Double.isFinite(bus.getVoltageMag()));
        assertEquals(initialVoltage, bus.getVoltageMag(), 1.0e-6);
    }

    private static double value(String[] row, Map<String, Integer> columns, String name) {
        return Double.parseDouble(row[columns.get(name)]);
    }
}
