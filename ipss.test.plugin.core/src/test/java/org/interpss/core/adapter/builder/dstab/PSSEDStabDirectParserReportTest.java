package org.interpss.core.adapter.builder.dstab;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;

import org.apache.commons.math3.complex.Complex;
import org.interpss.CorePluginTestSetup;
import org.interpss.dstab.control.exc.psse.ieeex1.Ieeex1Exciter;
import org.interpss.dstab.relay.Lds3blRelayModel;
import org.interpss.dstab.relay.Lvs3blRelayModel;
import org.interpss.fadapter.builder.AclfNetworkBuilder;
import org.interpss.fadapter.builder.DStabNetworkBuilder;
import org.interpss.fadapter.psse.PSSEDStabDirectParser;
import org.interpss.fadapter.psse.dyr.DynamicModelImportStatus;
import org.interpss.dstab.relay.AbstractGeneratorTripRelayModel;
import org.interpss.dstab.relay.FrqtpatRelayModel;
import org.interpss.dstab.relay.VtgtpatRelayModel;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.interpss.common.exp.InterpssException;
import com.google.gson.JsonParser;

public class PSSEDStabDirectParserReportTest extends CorePluginTestSetup {
    @TempDir
    Path tempDir;

    @Test
    void directPssEDynamicParserRejectsPslfDydInput() throws Exception {
        DStabNetworkBuilder builder = DStabBuilderTestFixture.createBuilder();
        Path dyd = tempDir.resolve("wrong-format.dyd");
        Files.writeString(dyd, "wtgt_a 1 \"BUS 1\" 230.00 \"1\" : #9 0 4 1 .2 3.2 1\n");

        InterpssException error = assertThrows(InterpssException.class,
                () -> new PSSEDStabDirectParser(builder).parseDynFile(dyd.toString()));

        assertTrue(error.getMessage().contains("GE PSLF .dyd is not PSS/E DYR input"));
        assertNull(builder.getDStabNetwork().getMachine("Bus1-mach1"));
    }

    @Test
    void strictImportPassesWhenEverySourceRecordIsAttached() throws Exception {
        DStabNetworkBuilder builder = DStabBuilderTestFixture.createBuilder();
        Path dyr = tempDir.resolve("complete.dyr");
        Files.writeString(dyr, "1 'GENCLS' '1' 3.0 0.0 /\n");
        PSSEDStabDirectParser parser = new PSSEDStabDirectParser(builder).setStrictImport(true);

        parser.parseDynFile(dyr.toString());

        assertTrue(parser.getLastImportReport().isStrictlyComplete());
        assertEquals(1, parser.getLastImportReport().totalRecordCount());
        assertEquals(1, parser.getLastImportReport().count(DynamicModelImportStatus.ATTACHED));
        assertTrue(parser.getLastImportReport().entries().get(0).source().endsWith("complete.dyr"));
        assertEquals(1, parser.getLastImportReport().entries().get(0).startLine());
    }

    @Test
    void strictImportRejectsInvalidSupportedRecordAndPreservesReport() throws Exception {
        DStabNetworkBuilder builder = DStabBuilderTestFixture.createBuilder();
        Path dyr = tempDir.resolve("unsupported.dyr");
        Files.writeString(dyr, "1 'GGOV1' '1' 0 /\n");
        PSSEDStabDirectParser parser = new PSSEDStabDirectParser(builder).setStrictImport(true);

        InterpssException error = assertThrows(InterpssException.class,
                () -> parser.parseDynFile(dyr.toString()));

        assertTrue(error.getMessage().contains("Strict DYR import failed"));
        assertEquals(1, parser.getLastImportReport().count(DynamicModelImportStatus.REJECTED));
        assertEquals("GGOV1", parser.getLastImportReport().failures().get(0).canonicalModelName());
    }

    @Test
    void strictImportRejectsTruncatedAndUnattachedMachineRecords() throws Exception {
        DStabNetworkBuilder builder = DStabBuilderTestFixture.createBuilder();
        Path dyr = tempDir.resolve("invalid-machines.dyr");
        Files.writeString(dyr, "1 'GENROU' '1' 8.0 /\n"
                + "2 'GENROU' '1' 8.0 0.03 0.4 0.05 5.0 3.0 "
                + "1.8 1.7 0.3 0.55 0.25 0.15 0.10 0.20 /\n");
        PSSEDStabDirectParser parser = new PSSEDStabDirectParser(builder).setStrictImport(true);

        assertThrows(InterpssException.class, () -> parser.parseDynFile(dyr.toString()));

        assertEquals(1, parser.getLastImportReport().count(DynamicModelImportStatus.REJECTED));
        assertEquals(1, parser.getLastImportReport().count(DynamicModelImportStatus.MISSING_TARGET));
        assertTrue(parser.getLastImportReport().failures().get(0).message()
                .contains("expected 14 parameters"));
        assertTrue(parser.getLastImportReport().failures().get(1).message()
                .contains("target bus Bus2 does not exist"));
    }

    @Test
    void unsupportedGentpjIsNeverSilentlyAttachedAsGenrou() throws Exception {
        DStabNetworkBuilder builder = DStabBuilderTestFixture.createBuilder();
        Path dyr = tempDir.resolve("gentpj.dyr");
        Files.writeString(dyr, "1 'GENTPJ' '1' 8.0 0.03 0.4 0.05 5.0 3.0 "
                + "1.8 1.7 0.3 0.55 0.25 0.15 0.10 0.20 /\n");
        PSSEDStabDirectParser parser = new PSSEDStabDirectParser(builder)
                .setStrictImport(true);

        assertThrows(InterpssException.class, () -> parser.parseDynFile(dyr.toString()));

        assertEquals(1, parser.getLastImportReport()
                .count(DynamicModelImportStatus.UNSUPPORTED));
        assertEquals("GENTPJ", parser.getLastImportReport().failures().get(0)
                .canonicalModelName());
        assertTrue(parser.getLastImportReport().failures().get(0).message()
                .contains("not implemented"));
        assertNull(builder.getDStabNetwork().getMachine("Bus1-mach1"));
    }

    @Test
    void malformedNumericParameterIsReportedInsteadOfDefaulted() throws Exception {
        DStabNetworkBuilder builder = DStabBuilderTestFixture.createBuilder();
        Path dyr = tempDir.resolve("malformed-gencls.dyr");
        Files.writeString(dyr, "1 'GENCLS' '1' NOT_A_NUMBER 0.0 /\n");
        PSSEDStabDirectParser parser = new PSSEDStabDirectParser(builder)
                .setStrictImport(true);

        assertThrows(InterpssException.class, () -> parser.parseDynFile(dyr.toString()));

        assertEquals(1, parser.getLastImportReport().count(DynamicModelImportStatus.ERROR));
        assertTrue(parser.getLastImportReport().failures().get(0).message()
                .contains("Invalid floating-point DYR field 4"));
        assertNull(builder.getDStabNetwork().getMachine("Bus1-mach1"));
    }

    @Test
    void exactIeeex1PathLoadsInStrictModeAndPreservesSwitch() throws Exception {
        DStabNetworkBuilder builder = DStabBuilderTestFixture.createBuilder();
        Path dyr = tempDir.resolve("partial-ieeex1.dyr");
        Files.writeString(dyr, "1 'GENCLS' '1' 3.0 0.0 /\n"
                + "1 'IEEEX1' '1' 0.02 40.0 0.02 0.0 0.0 5.0 -5.0 "
                + "1.0 0.6 0.03 0.35 7 2.8 0.1 3.7 0.33 /\n");
        PSSEDStabDirectParser parser = new PSSEDStabDirectParser(builder)
                .setStrictImport(true);

        parser.parseDynFile(dyr.toString());

        assertEquals(2, parser.getLastImportReport().count(DynamicModelImportStatus.ATTACHED));
        assertEquals(0, parser.getLastImportReport().count(DynamicModelImportStatus.FALLBACK));
        Ieeex1Exciter exciter = (Ieeex1Exciter) builder.getDStabNetwork()
                .getMachine("Bus1-mach1").getExciter();
        assertEquals(0.02, exciter.tr, 1.0e-12);
        assertEquals(7.0, exciter.getSwitchValue(), 1.0e-12);
        assertEquals("IEEEX1", exciter.getName());
    }

    @Test
    void ieeex1ZeroTeLoadsAsAlgebraicFieldBlock() throws Exception {
        DStabNetworkBuilder builder = DStabBuilderTestFixture.createBuilder();
        Path dyr = tempDir.resolve("zero-te-ieeex1.dyr");
        Files.writeString(dyr, "1 'GENCLS' '1' 3.0 0.0 /\n"
                + "1 'IEEEX1' '1' 0.02 40.0 0.02 0.0 0.0 5.0 -5.0 "
                + "1.0 0.0 0.03 0.35 0 2.8 0.1 3.7 0.33 /\n");
        PSSEDStabDirectParser parser = new PSSEDStabDirectParser(builder)
                .setStrictImport(true);

        parser.parseDynFile(dyr.toString());

        assertEquals(2, parser.getLastImportReport().count(DynamicModelImportStatus.ATTACHED));
        assertTrue(builder.getDStabNetwork().getMachine("Bus1-mach1").getExciter()
                instanceof Ieeex1Exciter);
    }

    @Test
    void nativeGeneratorTripRelaysUseTheirIconTargetsAndAttachInStrictMode() throws Exception {
        DStabNetworkBuilder builder = DStabBuilderTestFixture.createBuilder();
        builder.getDStabNetwork().setFrequency(60.0);
        Path dyr = tempDir.resolve("generator-trip-relays.dyr");
        Files.writeString(dyr, "1 'GENCLS' '1' 3.0 0.0 /\n"
                + "901 'FRQTPAT' 1 1 '1' 59.71 60.29 0.031 0.017 /\n"
                + "902 'VTGTPAT' 1 1 '1' 0.817 1.183 0.029 0.023 /\n");
        PSSEDStabDirectParser parser = new PSSEDStabDirectParser(builder).setStrictImport(true);

        parser.parseDynFile(dyr.toString());

        assertEquals(3, parser.getLastImportReport().count(DynamicModelImportStatus.ATTACHED));
        var devices = builder.getDStabNetwork().getDStabBus("Bus1").getDynamicBusDeviceList();
        FrqtpatRelayModel frequency = assertInstanceOf(FrqtpatRelayModel.class,
                devices.stream().filter(FrqtpatRelayModel.class::isInstance).findFirst().orElseThrow());
        VtgtpatRelayModel voltage = assertInstanceOf(VtgtpatRelayModel.class,
                devices.stream().filter(VtgtpatRelayModel.class::isInstance).findFirst().orElseThrow());
        assertEquals(59.71, frequency.getData().lowerThreshold(), 1.0e-12);
        assertEquals(1.183, voltage.getData().upperThreshold(), 1.0e-12);
        assertEquals("1", frequency.getTargetGenerator().getId());
        assertEquals("Bus1", frequency.getTargetBus().getId());
        assertEquals("Bus1", frequency.getMonitoredBus().getId());
        assertEquals(60.0, frequency.getMonitoredBus().getNetwork().getFrequency(), 1.0e-12);
    }

    @Test
    void lds3blUsesFiveNativeStagesAndStartsTransferTripAtPickup() throws Exception {
        DStabNetworkBuilder builder = DStabBuilderTestFixture.createBuilder();
        new AclfNetworkBuilder(builder.getDStabNetwork()).addContributeLoad("Bus1", "L", true,
                new Complex(0.8, 0.3), null, null, null, false);
        builder.getDStabNetwork().setFrequency(60.0);
        Path dyr = tempDir.resolve("lds3bl.dyr");
        Files.writeString(dyr, "1 'GENCLS' '1' 3.0 0.0 /\n"
                + "1 'LDS3BL' 'L' 1 '1' 0 "
                + "59.47 0.031 0.019 0.23 "
                + "0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0.023 /\n");
        new PSSEDStabDirectParser(builder).setStrictImport(true).parseDynFile(dyr.toString());
        var bus = builder.getDStabNetwork().getDStabBus("Bus1");
        Lds3blRelayModel relay = (Lds3blRelayModel) bus.getDynamicBusDeviceList().stream()
                .filter(Lds3blRelayModel.class::isInstance).findFirst().orElseThrow();
        builder.getDStabNetwork().formYMatrix4DStab();
        assertTrue(relay.initStates(bus));
        bus.setFreq(0.98);
        for (int i = 0; i < 50; i++) assertTrue(relay.afterStep(0.001));
        assertEquals(0.23, relay.getShedFraction(), 1.0e-12);
        assertTrue(relay.isStageOperated(0));
        assertFalse(relay.isTransferOperated());
        for (int i = 0; i < 4; i++) assertTrue(relay.afterStep(0.001));
        assertTrue(relay.isTransferOperated());
        assertFalse(bus.getContributeGen("1").isActive());
        assertTrue(relay.getNamedStates().containsKey("Stage 5 timer"));
    }

    @Test
    void lvs3blUsesVoltagePickupWithoutFabricatingTransferBranches() throws Exception {
        DStabNetworkBuilder builder = DStabBuilderTestFixture.createBuilder();
        new AclfNetworkBuilder(builder.getDStabNetwork()).addContributeLoad("Bus1", "L", true,
                new Complex(0.8, 0.3), null, null, null, false);
        Path dyr = tempDir.resolve("lvs3bl.dyr");
        Files.writeString(dyr, "1 'GENCLS' '1' 3.0 0.0 /\n"
                + "1 'LVS3BL' 'L' 0 0 '0' 0 0 '0' 0 "
                + "0.83 0.017 0.013 0.19 "
                + "0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0.021 0.025 /\n");
        new PSSEDStabDirectParser(builder).setStrictImport(true).parseDynFile(dyr.toString());
        var bus = builder.getDStabNetwork().getDStabBus("Bus1");
        Lvs3blRelayModel relay = (Lvs3blRelayModel) bus.getDynamicBusDeviceList().stream()
                .filter(Lvs3blRelayModel.class::isInstance).findFirst().orElseThrow();
        builder.getDStabNetwork().formYMatrix4DStab();
        assertTrue(relay.initStates(bus));
        bus.setVoltageMag(0.80);
        for (int i = 0; i < 30; i++) assertTrue(relay.afterStep(0.001));
        assertEquals(0.19, relay.getShedFraction(), 1.0e-12);
        assertTrue(relay.isStageOperated(0));
        assertFalse(relay.isTransferOperated(0));
        assertFalse(relay.isTransferOperated(1));
    }

    @Test
    void loadSheddingRelayDurationsMatchNativePsseTimerChannels() throws Exception {
        var manifest = JsonParser.parseString(Files.readString(Path.of("testData", "reference",
                "psse", "ieee9-load-shedding-relays", "manifest.json"))).getAsJsonObject();
        var simulation = manifest.getAsJsonObject("simulation");
        var pickups = simulation.getAsJsonObject("observed_pickup_start_times_s");
        var sheds = simulation.getAsJsonObject("observed_shed_times_s");
        assertEquals(0.031 + 0.019,
                sheds.get("LDS3BL").getAsDouble() - pickups.get("LDS3BL").getAsDouble(), 1.0e-7);
        assertEquals(0.047 + 0.029,
                sheds.get("LVS3BL").getAsDouble() - pickups.get("LVS3BL").getAsDouble(), 1.0e-7);
    }

    @Test
    void generatorTripRelayDelaysMatchTheNativePsseContract() throws Exception {
        DStabNetworkBuilder builder = DStabBuilderTestFixture.createBuilder();
        builder.getDStabNetwork().setFrequency(50.0);
        Path dyr = tempDir.resolve("generator-trip-relay-timing.dyr");
        Files.writeString(dyr, "1 'GENCLS' '1' 3.0 0.0 /\n"
                + "711 'FRQTPAT' 1 1 '1' 49.71 50.29 0.031 0.017 /\n"
                + "712 'VTGTPAT' 1 1 '1' 0.817 1.183 0.029 0.023 /\n");
        new PSSEDStabDirectParser(builder).setStrictImport(true).parseDynFile(dyr.toString());
        var bus = builder.getDStabNetwork().getDStabBus("Bus1");
        FrqtpatRelayModel frequency = (FrqtpatRelayModel) bus.getDynamicBusDeviceList().stream()
                .filter(FrqtpatRelayModel.class::isInstance).findFirst().orElseThrow();
        VtgtpatRelayModel voltage = (VtgtpatRelayModel) bus.getDynamicBusDeviceList().stream()
                .filter(VtgtpatRelayModel.class::isInstance).findFirst().orElseThrow();
        assertTrue(frequency.initStates(bus));
        assertTrue(voltage.initStates(bus));
        bus.setFreq(0.98);
        bus.setVoltageMag(0.70);
        for (int step = 0; step < 52; step++) {
            assertTrue(frequency.afterStep(0.001));
            assertTrue(voltage.afterStep(0.001));
        }

        var manifest = JsonParser.parseString(Files.readString(Path.of("testData", "reference",
                "psse", "ieee9-generator-trip-relays", "manifest.json"))).getAsJsonObject();
        var simulation = manifest.getAsJsonObject("simulation");
        double nativeStart = simulation.get("psse_relay_initialization_start_s").getAsDouble();
        var nativeTrips = simulation.getAsJsonObject("observed_trip_times_s");
        assertEquals(nativeTrips.get("FRQTPAT_bus2_s").getAsDouble() - nativeStart,
                frequency.getActionTime(), 5.0e-8);
        assertEquals(nativeTrips.get("VTGTPAT_bus3_s").getAsDouble() - nativeStart,
                voltage.getActionTime(), 5.0e-8);
        assertEquals(0.048, frequency.getActionTime(), 1.0e-12);
        assertEquals(0.052, voltage.getActionTime(), 1.0e-12);
    }

    @Test
    void voltageTripRelayResetsBeforePickupThenLatchesThroughBreakerDelay() throws Exception {
        DStabNetworkBuilder builder = DStabBuilderTestFixture.createBuilder();
        Path dyr = tempDir.resolve("vtgtpat-lifecycle.dyr");
        Files.writeString(dyr, "1 'GENCLS' '1' 3.0 0.0 /\n"
                + "903 'VTGTPAT' 1 1 '1' 0.82 1.18 0.03 0.02 /\n");
        PSSEDStabDirectParser parser = new PSSEDStabDirectParser(builder).setStrictImport(true);
        parser.parseDynFile(dyr.toString());
        var bus = builder.getDStabNetwork().getDStabBus("Bus1");
        VtgtpatRelayModel relay = (VtgtpatRelayModel) bus.getDynamicBusDeviceList().stream()
                .filter(VtgtpatRelayModel.class::isInstance).findFirst().orElseThrow();
        assertTrue(relay.initStates(bus));
        assertEquals(AbstractGeneratorTripRelayModel.STATE_TIMER_MEMORY,
                relay.getNamedStates().keySet().iterator().next());

        bus.setVoltageMag(0.70);
        assertTrue(relay.afterStep(0.01));
        assertEquals(0.01, relay.getTimerMemory(), 1.0e-12);
        bus.setVoltageMag(1.00);
        assertTrue(relay.afterStep(0.01));
        assertEquals(0.0, relay.getTimerMemory(), 1.0e-12);

        bus.setVoltageMag(0.70);
        assertTrue(relay.afterStep(0.01));
        assertTrue(relay.afterStep(0.01));
        assertTrue(relay.afterStep(0.01));
        assertTrue(relay.isPickedUp());
        bus.setVoltageMag(1.00);
        assertTrue(relay.afterStep(0.01));
        assertTrue(relay.afterStep(0.01));

        assertTrue(relay.isTripped());
        assertEquals(0.07, relay.getActionTime(), 1.0e-12);
        assertFalse(relay.getTargetGenerator().isActive());
        assertFalse(relay.getTargetGenerator().getDynamicGenDevice().isActive());
    }
}
