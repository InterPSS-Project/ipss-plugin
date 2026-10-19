package org.interpss.core.dstab.reference;

import static org.junit.jupiter.api.Assertions.*;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.zip.GZIPInputStream;

import org.apache.commons.math3.complex.Complex;
import org.interpss.IpssCorePlugin;
import org.interpss.dstab.renewable.Regca1Model;
import org.interpss.dstab.renewable.Regfma1Model;
import org.interpss.fadapter.psse.PSSEMultiFileLoader;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.google.gson.JsonParser;
import com.interpss.dstab.algo.DynamicSimuMethod;
import com.interpss.dstab.cache.StateMonitor;

class Texas2kFullNetworkRecorderTest {
    @TempDir Path directory;

    @Test void terminalPowerUsesSystemBaseAndPreservesDuplicateEventSamples() throws Exception {
        IpssCorePlugin.init();
        String base = "testData/adpter/psse/v33/SMIB/";
        var context = new PSSEMultiFileLoader().loadDStab(base + "SMIB_v33.raw", base + "SMIB_v33_genrou_esst4b.dyr");
        var algorithm = context.getDynSimuAlgorithm();
        assertTrue(algorithm.getAclfAlgorithm().loadflow());
        var machine = context.getDStabilityNet().getMachine("Bus1-mach1");
        machine.setRa(.01);
        algorithm.setSimuMethod(DynamicSimuMethod.MODIFIED_EULER);
        algorithm.setSimuStepSec(.0005);
        algorithm.setTotalSimuTimeSec(.01);
        algorithm.setSimuOutputHandler(new StateMonitor());
        assertTrue(algorithm.initialization());
        var net = context.getDStabilityNet();
        double angle = machine.getAngle(), speed = machine.getSpeed(), efd = machine.getEfd(), pm = machine.getPm();
        var round = (com.interpss.dstab.mach.impl.RoundRotorMachineImpl)machine;
        var storedElectricalState = round.captureRoundElectricalState();
        double storedAirgapPower = machine.getPe();
        Complex voltage = machine.getDStabBus().getVoltage().multiply(new Complex(1.01, .002));
        machine.getDStabBus().setVoltage(voltage);
        voltage = machine.getDStabBus().getVoltage();
        Complex power = voltage.multiply(machine.getIxy().conjugate()).multiply(net.getBaseMva());
        assertNotEquals(storedElectricalState, round.captureRoundElectricalState(),
                "The legacy current getter must expose its electrical-cache side effect in this fixture");
        assertNotEquals(power.getReal(), machine.getPe() * machine.getRating() * net.getBaseMva(), 1.e-5,
                "Copper loss must distinguish terminal power from air-gap power");
        round.restoreRoundElectricalState(storedElectricalState);
        machine.setPe(storedAirgapPower);
        try (var recorder = new Texas2kFullNetworkRecorder(net, directory)) {
            recorder.record(-.001, "HISTORY");
            recorder.record(0., "INITIAL");
            recorder.record(1., "STEP_END");
            machine.setStatus(false);
            recorder.record(1., "EVENT_POST");
        }
        var rows = rows(directory);
        assertEquals(4, rows.size());
        assertEquals("STEP_END", rows.get(2).get("sample_kind"));
        assertEquals("EVENT_POST", rows.get(3).get("sample_kind"));
        assertEquals(rows.get(2).get("time_s"), rows.get(3).get("time_s"));
        assertEquals(power.getReal(), number(rows.get(2), "generator_P_MW__bus_1__id_1"), 1.e-12);
        assertEquals(power.getImaginary(), number(rows.get(2), "generator_Q_Mvar__bus_1__id_1"), 1.e-12);
        assertEquals(Math.toDegrees(angle), number(rows.get(2), "machine_ANGLE_deg__bus_1__id_1"), 0.);
        assertEquals(pm * machine.getRating() * net.getBaseMva(), number(rows.get(2), "machine_PMECH_MW__bus_1__id_1"), 0.);
        assertEquals(0., number(rows.get(3), "generator_P_MW__bus_1__id_1"), 0.);
        assertEquals(0., number(rows.get(3), "machine_ANGLE_deg__bus_1__id_1"), 0.);
        assertEquals(rows.get(2).get("bus_angle_deg__bus_1"), rows.get(3).get("bus_angle_deg__bus_1"));
        assertEquals(voltage, machine.getDStabBus().getVoltage());
        assertEquals(angle, machine.getAngle(), 0.);
        assertEquals(speed, machine.getSpeed(), 0.);
        assertEquals(efd, machine.getEfd(), 0.);
        assertEquals(pm, machine.getPm(), 0.);
        assertEquals(storedAirgapPower, machine.getPe(), 0.);
        assertEquals(storedElectricalState, round.captureRoundElectricalState(),
                "Reporting after a voltage change must preserve cached currents, derivatives and power");
        var metadata = JsonParser.parseString(Files.readString(directory.resolve("full-network-channels.json"))).getAsJsonObject();
        assertEquals(4, metadata.get("samples").getAsInt());
        assertEquals(-.001, metadata.get("first_time_s").getAsDouble(), 0.);
        assertThrows(java.io.IOException.class, () -> new Texas2kFullNetworkRecorder(net, directory));
    }

    @Test void converterPowerExcludesNortonAdmittanceAndCachedEventAverage() throws Exception {
        IpssCorePlugin.init();
        String base = "testData/adpter/psse/v33/renewable/regca_reeca_repca_bus1062";
        var context = new PSSEMultiFileLoader().loadDStab(base + ".raw", base + ".dyr");
        var algorithm = context.getDynSimuAlgorithm();
        var net = context.getDStabilityNet();
        var bus = net.getDStabBus("Bus1");
        var generator = (com.interpss.dstab.DStabGen)bus.getContributeGen("1");
        generator.setMvaBase(37.);
        net.setAllowGenWithoutMach(true);
        algorithm.getAclfAlgorithm().getDataCheckConfig().setAllowGenWithoutMachine(true);
        assertTrue(algorithm.getAclfAlgorithm().loadflow());
        algorithm.setSimuMethod(DynamicSimuMethod.MODIFIED_EULER);
        algorithm.setSimuStepSec(.0005);
        algorithm.setTotalSimuTimeSec(.01);
        algorithm.setSimuOutputHandler(new StateMonitor());
        assertTrue(algorithm.initialization());
        var converter = (Regca1Model)generator.getDynamicGenDevice();
        bus.setVoltage(new Complex(.6, .2));
        assertTrue(converter.updateAttributes(true));
        Complex norton = (Complex)converter.getOutputObject();
        Complex admittanceCurrent = bus.getVoltage().divide(generator.getPosGenZ().multiply(generator.getZMultiFactor()));
        Complex terminalPower = bus.getVoltage().multiply(norton.subtract(admittanceCurrent).conjugate()).multiply(net.getBaseMva());
        var before = converter.getNamedStates();
        assertNotEquals(terminalPower.getReal(), before.get("Active Power") * generator.getMvaBase(), 1.e-5);
        try (var recorder = new Texas2kFullNetworkRecorder(net, directory)) {
            recorder.record(1., "EVENT_POST");
        }
        assertEquals(before, converter.getNamedStates(), "Observation must not refresh cached powers or advance controls");
        var row = rows(directory).getFirst();
        assertEquals(terminalPower.getReal(), number(row, "generator_P_MW__bus_1__id_1"), 1.e-10);
        assertEquals(terminalPower.getImaginary(), number(row, "generator_Q_Mvar__bus_1__id_1"), 1.e-10);
        assertFalse(row.containsKey("machine_ANGLE_deg__bus_1__id_1"));
        var metadata = JsonParser.parseString(Files.readString(directory.resolve("full-network-channels.json"))).getAsJsonObject();
        assertEquals(1, metadata.get("initial_online_converter_generators").getAsInt());
        var description = metadata.getAsJsonArray("channels").asList().stream()
                .map(e -> e.getAsJsonObject()).filter(c -> c.get("name").getAsString().equals("generator_P_MW__bus_1__id_1")).findFirst().orElseThrow();
        assertEquals("MW", description.get("unit").getAsString());
    }

    @Test void recordingBindsAfterUnmodeledGenerationIsConverted() throws Exception {
        var net = (com.interpss.dstab.DStabilityNetwork)
                com.interpss.simu.util.sample.SampleDStabTestingCases.sample3BusDStab_StaticLoads();
        var lf = com.interpss.core.LoadflowAlgoObjectFactory.createLoadflowAlgorithm(net);
        lf.getLfAdjAlgo().setApplyAdjustAlgo(false);
        assertTrue(lf.loadflow());
        var bus = net.getBus("Bus-3");
        var generator = com.interpss.dstab.DStabObjectFactory.createDStabGen("unmodeled");
        generator.setGen(new Complex(.3, .2));
        generator.setStatus(true);
        generator.setMvaBase(100.);
        bus.getContributeGenList().add(generator);
        net.setAllowGenWithoutMach(true);
        lf.getDataCheckConfig().setAllowGenWithoutMachine(true);
        net.checkData(lf.getDataCheckConfig());
        net.setDStabNetInitialized(false);
        try (var recorder = new Texas2kFullNetworkRecorder(net, directory)) {
            assertThrows(IllegalStateException.class, () -> recorder.record(0., "INITIAL"));
            assertTrue(net.initDStabNet());
            assertFalse(generator.isActive());
            recorder.record(0., "INITIAL");
        }
        var row = rows(directory).getFirst();
        assertTrue(row.containsKey("bus_voltage_pu__bus_-3"));
        assertFalse(row.keySet().stream().anyMatch(name -> name.endsWith("__id_unmodeled")));
        var metadata = JsonParser.parseString(Files.readString(directory.resolve("full-network-channels.json"))).getAsJsonObject();
        assertTrue(metadata.get("inventory_bound_after_initialization").getAsBoolean());
        assertEquals(1, metadata.get("samples").getAsInt());
    }

    @Test void gridFormingPowerIsReadOnlyAcrossVoltageChangeCurrentLimitAndTrip() throws Exception {
        IpssCorePlugin.init();
        var profile = Regfma1NativeReferenceCheckpoints.get("flat11");
        Path dyr = directory.resolve("grid-forming.dyr");
        Files.writeString(dyr, "1 'REGFMA1' '1' 1 1 "
                + String.join(" ", profile.constants().stream().map(Object::toString).toList())
                + " /\n3 'GENCLS' '1' 9999999 0 /\n");
        var context = new PSSEMultiFileLoader().loadDStab(
                "testData/adpter/psse/v33/renewable/regfma1_native.raw", dyr.toString());
        var net = context.getDStabilityNet();
        var algorithm = context.getDynSimuAlgorithm();
        net.setAllowGenWithoutMach(true);
        algorithm.getAclfAlgorithm().getDataCheckConfig().setAllowGenWithoutMachine(true);
        assertTrue(algorithm.getAclfAlgorithm().loadflow());
        algorithm.setSimuMethod(DynamicSimuMethod.MODIFIED_EULER);
        algorithm.setSimuStepSec(.0005);
        algorithm.setTotalSimuTimeSec(.01);
        algorithm.setSimuOutputHandler(new StateMonitor());
        var bus = net.getDStabBus("Bus1");
        var generator = (com.interpss.dstab.DStabGen)bus.getContributeGen("1");
        var converter = (Regfma1Model)generator.getDynamicGenDevice();
        var expected = new java.util.ArrayList<Complex>();
        try (var recorder = new Texas2kFullNetworkRecorder(net, directory)) {
            assertTrue(algorithm.initialization());
            for (Complex voltage : List.of(new Complex(1.01, .002), new Complex(.2, .05))) {
                bus.setVoltage(voltage);
                var states = converter.getNamedStates();
                var rates = converter.getStateDerivativeSnapshot();
                boolean limited = converter.isCurrentLimited();
                recorder.record(1., expected.isEmpty() ? "STEP_END" : "EVENT_POST");
                assertEquals(states, converter.getNamedStates());
                assertEquals(rates, converter.getStateDerivativeSnapshot());
                assertEquals(limited, converter.isCurrentLimited());
                Complex norton = (Complex)converter.getOutputObject();
                Complex current = norton.subtract(voltage.divide(generator.getPosGenZ().multiply(generator.getZMultiFactor())));
                expected.add(voltage.multiply(current.conjugate()).multiply(net.getBaseMva()));
            }
            assertTrue(converter.isCurrentLimited(), "The second observation exercises the current-limited output");
            converter.setStatus(false);
            recorder.record(1.1, "STEP_END");
        }
        var samples = rows(directory);
        assertEquals(3, samples.size());
        for (int i = 0; i < 2; i++) {
            assertEquals(expected.get(i).getReal(), number(samples.get(i), "generator_P_MW__bus_1__id_1"), 1.e-10);
            assertEquals(expected.get(i).getImaginary(), number(samples.get(i), "generator_Q_Mvar__bus_1__id_1"), 1.e-10);
            assertFalse(samples.get(i).containsKey("machine_ANGLE_deg__bus_1__id_1"));
        }
        assertEquals(0., number(samples.get(2), "generator_P_MW__bus_1__id_1"), 0.);
        assertEquals(0., number(samples.get(2), "generator_Q_Mvar__bus_1__id_1"), 0.);
        var metadata = JsonParser.parseString(Files.readString(directory.resolve("full-network-channels.json"))).getAsJsonObject();
        assertEquals(1, metadata.get("initial_online_grid_forming_generators").getAsInt());
    }

    private static double number(Map<String, String> row, String name) {
        return Double.parseDouble(row.get(name));
    }

    private static List<Map<String, String>> rows(Path directory) throws Exception {
        try (var reader = new BufferedReader(new InputStreamReader(new GZIPInputStream(
                Files.newInputStream(directory.resolve("full-network.csv.gz"))), StandardCharsets.UTF_8))) {
            String[] header = reader.readLine().split(",", -1);
            return reader.lines().map(line -> {
                String[] fields = line.split(",", -1);
                assertEquals(header.length, fields.length);
                Map<String, String> values = new LinkedHashMap<>();
                for (int i = 0; i < header.length; i++) assertNull(values.put(header[i], fields[i]));
                return values;
            }).toList();
        }
    }
}
