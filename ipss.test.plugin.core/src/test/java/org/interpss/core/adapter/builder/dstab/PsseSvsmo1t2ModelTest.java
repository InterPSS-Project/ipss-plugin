package org.interpss.core.adapter.builder.dstab;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;

import org.apache.commons.math3.complex.Complex;
import org.interpss.CorePluginTestSetup;
import com.interpss.dstab.svc.Svsmo1t2Model;
import org.interpss.fadapter.psse.PSSEMultiFileLoader;
import org.interpss.fadapter.psse.dyr.DynamicModelCatalog;
import org.junit.jupiter.api.Test;

import com.interpss.dstab.algo.DynamicSimuMethod;
import com.interpss.dstab.cache.StateMonitor;

/** Parser, initialization, and boundary tests for the switched-shunt target. */
public class PsseSvsmo1t2ModelTest extends CorePluginTestSetup {
    private static final Path RAW = Path.of("testData", "psse", "v35",
            "PSSE_5Bus_svsmo1t2_synthetic.raw");
    private static final Path DYR = Path.of("testData", "psse", "v35",
            "PSSE_5Bus_svsmo1t2_synthetic.dyr");

    @Test void zeroProtectionDelayRequiresPickupAndReinitializationClearsTheTrip(@org.junit.jupiter.api.io.TempDir Path temp) throws Exception {
        var source=new StringBuilder();
        for(var record:org.interpss.fadapter.psse.dyr.PsseDyrRecordReader.read(DYR)) {
            var fields=new java.util.ArrayList<>(record.fields());
            if(record.canonicalModelName().equals("SVSMO1T2"))for(int index:new int[]{27,28,29,30})fields.set(index,"0");
            source.append(String.join(" ",fields)).append(" /\n");
        }
        Path dyr=temp.resolve("zero-delay.dyr");Files.writeString(dyr,source);
        var context=new PSSEMultiFileLoader().loadDStab(RAW.toString(),dyr.toString());var algorithm=context.getDynSimuAlgorithm();
        assertTrue(algorithm.getAclfAlgorithm().loadflow());algorithm.setSimuOutputHandler(new StateMonitor());assertTrue(algorithm.initialization());
        var bus=context.getDStabilityNet().getDStabBus("Bus4");
        var model=(Svsmo1t2Model)bus.getDynamicBusDeviceList().stream().filter(Svsmo1t2Model.class::isInstance).findFirst().orElseThrow();
        double initial=model.getSusceptance(),voltage=model.getRemoteBus().getVoltageMag();assertTrue(Math.abs(initial)>1e-6);
        for(int i=0;i<20;i++)step(model,.0005);
        assertEquals(initial,model.getSusceptance(),1e-10);
        model.getRemoteBus().setVoltage(new Complex(2.1,0));step(model,.0005);assertEquals(0,model.getSusceptance(),0);
        model.getRemoteBus().setVoltage(new Complex(voltage,0));assertTrue(model.initStates(bus));step(model,.0005);
        assertEquals(initial,model.getSusceptance(),1e-10);
    }

    @Test
    void parserRetainsExactT2SchemaAndTargetsSwitchedShunt() throws Exception {
        var context = new PSSEMultiFileLoader().loadDStab(RAW.toString(), DYR.toString());
        var bus = context.getDStabilityNet().getDStabBus("Bus4");
        Svsmo1t2Model model = bus.getDynamicBusDeviceList().stream()
                .filter(Svsmo1t2Model.class::isInstance)
                .map(Svsmo1t2Model.class::cast).findFirst().orElseThrow();

        assertEquals("1", model.getSwitchedShunt().getId());
        assertEquals("Bus4", model.getRemoteBus().getId());
        assertEquals(8, model.getData().mssDevices().size());
        assertEquals(.18, model.getData().uvSbMax(), 1.0e-12);
        assertEquals(1.80, model.getData().ov1(), 1.0e-12);
        assertEquals(42.0, model.getData().kpv(), 1.0e-12);
        assertEquals(280.0, model.getData().capacitorDischargeTime(), 1.0e-12);
        assertEquals(65, DynamicModelCatalog.find("SVSMO1T2").orElseThrow()
                .parameterCount());
        assertEquals("SVSMO1T2", DynamicModelCatalog.find("SVSMO1T3").orElseThrow()
                .canonicalName());
    }

    @Test
    void t3AliasUsesItsExplicitSwitchedShuntIdentifier() throws Exception {
        Path variant = Files.createTempFile("svsmo1t3-synthetic-", ".dyr");
        try {
            String t3 = Files.readString(DYR)
                    .replace("'SVSMO1T2' 4", "'SVSMO1T3' '1' 4");
            Files.writeString(variant, t3);
            var context = new PSSEMultiFileLoader().loadDStab(
                    RAW.toString(), variant.toString());
            Svsmo1t2Model model = context.getDStabilityNet().getDStabBus("Bus4")
                    .getDynamicBusDeviceList().stream()
                    .filter(Svsmo1t2Model.class::isInstance)
                    .map(Svsmo1t2Model.class::cast).findFirst().orElseThrow();
            assertEquals("1", model.getSwitchedShunt().getId());
            assertEquals("Bus4", model.getRemoteBus().getId());
        } finally {
            Files.deleteIfExists(variant);
        }
    }

    @Test
    void solvedShuntIsNotDoubleCountedAndLowVoltageRaisesSusceptance() throws Exception {
        var context = new PSSEMultiFileLoader().loadDStab(RAW.toString(), DYR.toString());
        var network = context.getDStabilityNet();
        var algorithm = context.getDynSimuAlgorithm();
        assertTrue(algorithm.getAclfAlgorithm().loadflow());
        network.setLfConverged(true);
        algorithm.setSimuMethod(DynamicSimuMethod.MODIFIED_EULER);
        algorithm.setSimuStepSec(.0005);
        algorithm.setTotalSimuTimeSec(.2);
        algorithm.setSimuOutputHandler(new StateMonitor());
        assertTrue(algorithm.initialization());

        var bus = network.getDStabBus("Bus4");
        Svsmo1t2Model model = bus.getDynamicBusDeviceList().stream()
                .filter(Svsmo1t2Model.class::isInstance)
                .map(Svsmo1t2Model.class::cast).findFirst().orElseThrow();
        Complex initialIncrement = (Complex) model.getOutputObject();
        assertEquals(0.0, initialIncrement.abs(), 1.0e-10);
        double initialB = model.getSusceptance();
        model.getRemoteBus().setVoltage(new Complex(.80, 0.0));
        for (int index = 0; index < 200; index++) step(model, .0005);

        assertTrue(model.getSusceptance() > initialB);
        assertEquals(model.getSusceptance(), model.getNamedState("SVC susceptance"),
                1.0e-12);
        assertTrue(model.getSusceptance() <= model.getData().bShort() + 1.0e-12);
    }

    @Test
    void undervoltageCapLimitsFiringCommandAndHoldsThroughPllRecovery(
            @org.junit.jupiter.api.io.TempDir Path temp) throws Exception {
        var input = new StringBuilder();
        for (var record : org.interpss.fadapter.psse.dyr.PsseDyrRecordReader.read(DYR)) {
            var fields = new java.util.ArrayList<>(record.fields());
            if (record.canonicalModelName().equals("SVSMO1T2")) {
                fields.set(27, "0.005"); // Tuv1, dwell before arming PLL recovery
                fields.set(57, "0.150"); // PLL recovery hold
            }
            input.append(String.join(" ", fields)).append(" /\n");
        }
        Path dyr = Files.writeString(temp.resolve("pll-recovery.dyr"), input);
        var context = new PSSEMultiFileLoader().loadDStab(RAW.toString(), dyr.toString());
        var bus = context.getDStabilityNet().getDStabBus("Bus4");
        var model = bus.getDynamicBusDeviceList().stream().filter(Svsmo1t2Model.class::isInstance)
                .map(Svsmo1t2Model.class::cast).findFirst().orElseThrow();
        bus.setVoltage(new Complex(1.05, 0.0));
        assertTrue(model.initStates(bus));
        double cap = model.getData().uvSbMax();
        double stepSec = 0.001;

        bus.setVoltage(new Complex(0.40, 0.0));
        int pickupSteps = 0;
        while (model.getVoltageMeasurement() >= model.getData().uv1() && pickupSteps++ < 50) {
            step(model, stepSec);
        }
        assertTrue(model.getVoltageMeasurement() < model.getData().uv1());
        assertEquals(cap, model.getFastControllerOutput(), 1.0e-12);
        assertTrue(model.getSusceptance() > cap, "The firing state decays through its lag");
        for (int i = 0; i < 45; i++) step(model, stepSec);

        bus.setVoltage(new Complex(0.80, 0.0));
        for (int i = 0; i < 20; i++) step(model, stepSec);
        assertTrue(model.getVoltageMeasurement() > model.getData().uv1());
        assertEquals(cap, model.getFastControllerOutput(), 1.0e-12);
        for (int i = 0; i < 200; i++) step(model, 0.0);
        assertEquals(cap, model.getFastControllerOutput(), 1.0e-12);

        for (int i = 0; i < 110; i++) step(model, stepSec);
        assertEquals(cap, model.getFastControllerOutput(), 1.0e-12);
        for (int i = 0; i < 40; i++) step(model, stepSec);
        assertTrue(model.getFastControllerOutput() > cap);
    }


    @Test
    void overVoltageForcesTheCommandWhileTheFiringStateDecays() throws Exception {
        var context = new PSSEMultiFileLoader().loadDStab(RAW.toString(), DYR.toString());
        var algorithm = context.getDynSimuAlgorithm();
        assertTrue(algorithm.getAclfAlgorithm().loadflow());
        algorithm.setSimuMethod(DynamicSimuMethod.MODIFIED_EULER);
        algorithm.setSimuStepSec(0.0005);
        algorithm.setSimuOutputHandler(new StateMonitor());
        assertTrue(algorithm.initialization());
        var model = context.getDStabilityNet().getDStabBus("Bus4").getDynamicBusDeviceList()
                .stream().filter(Svsmo1t2Model.class::isInstance)
                .map(Svsmo1t2Model.class::cast).findFirst().orElseThrow();
        double initial = model.getSusceptance();
        double minimum = model.getData().bMin();
        model.getRemoteBus().setVoltage(new Complex(1.9, 0.));
        step(model, 0.0005);
        assertEquals(minimum, model.getFastControllerOutput(), 0.0);
        assertTrue(model.getSusceptance() > minimum);
        assertTrue(model.getSusceptance() < initial);
        assertTrue(!model.isTripped());
    }

    @Test
    void mechanicalBankUsesAcceptedStepsAndStampsItsIndividualAdmittance(
            @org.junit.jupiter.api.io.TempDir Path temp) throws Exception {
        String rawText = Files.readString(RAW).replace(
                "0 / END OF FIXED SHUNT DATA", "4,'CP',0,2.0,12.0\n0 / END OF FIXED SHUNT DATA");
        Path raw = Files.writeString(temp.resolve("bank.raw"), rawText);
        var input = new StringBuilder();
        for (var record : org.interpss.fadapter.psse.dyr.PsseDyrRecordReader.read(DYR)) {
            var fields = new java.util.ArrayList<>(record.fields());
            if (record.canonicalModelName().equals("SVSMO1T2")) {
                fields.set(3, "1");
                fields.set(5, "4");
                fields.set(6, "'CP'");
                fields.set(61, "0");
                fields.set(62, "0.04");
                fields.set(64, "0.02");
            }
            input.append(String.join(" ", fields)).append(" /\n");
        }
        Path dyr = Files.writeString(temp.resolve("bank.dyr"), input);
        var context = new PSSEMultiFileLoader().loadDStab(raw.toString(), dyr.toString());
        var network = context.getDStabilityNet();
        var algorithm = context.getDynSimuAlgorithm();
        assertTrue(algorithm.getAclfAlgorithm().loadflow());
        algorithm.setSimuMethod(DynamicSimuMethod.MODIFIED_EULER);
        algorithm.setSimuStepSec(0.001);
        algorithm.setSimuOutputHandler(new StateMonitor());
        assertTrue(algorithm.initialization());

        var bus = network.getDStabBus("Bus4");
        var bank = bus.getCompensatorList().stream().filter(x -> "CP".equals(x.getId())).findFirst().orElseThrow();
        var model = bus.getDynamicBusDeviceList().stream().filter(Svsmo1t2Model.class::isInstance)
                .map(Svsmo1t2Model.class::cast).findFirst().orElseThrow();
        int index = bus.getSortNumber();
        var diagonal = network.getYMatrix().getA(index, index);
        assertEquals(0.0, model.getSwitchingNamedStates().get("slot.1.inService"), 0.0);
        assertTrue(model.getSusceptance() > model.getData().bSmallCapMvar() / network.getBaseMva());
        assertTrue(model.afterStep(0.001));
        assertTrue(model.afterStep(0.020));
        assertTrue(model.afterStep(0.0));
        assertEquals(0.0, model.getSwitchingNamedStates().get("operations"), 0.0);
        assertTrue(model.afterStep(0.040));
        assertTrue(bank.isActive());
        assertEquals(1.0, model.getSwitchingNamedStates().get("operations"), 0.0);
        var applied = network.getYMatrix().getA(index, index).subtract(diagonal);
        assertEquals(2.0 / network.getBaseMva(), applied.getReal(), 1e-10);
        assertEquals(12.0 / network.getBaseMva(), applied.getImaginary(), 1e-10);

        bus.setVoltage(new Complex(1.15, 0.));
        for (int i = 0; i < 400; i++) {
            step(model, 0.001);
            assertTrue(model.afterStep(0.001));
        }
        assertEquals(0.0, model.getSwitchingNamedStates().get("slot.1.inService"), 0.0);
        assertEquals(2.0, model.getSwitchingNamedStates().get("operations"), 0.0);
        assertEquals(0.0, network.getYMatrix().getA(index, index).subtract(diagonal).abs(), 1e-10);
        bus.setVoltage(new Complex(0.85, 0.));
        for (int i = 0; i < 400; i++) {
            step(model, 0.001);
            assertTrue(model.afterStep(0.001));
        }
        assertEquals(0.0, model.getSwitchingNamedStates().get("slot.1.inService"), 0.0);
        assertEquals(2.0, model.getSwitchingNamedStates().get("operations"), 0.0);
    }

    private static void step(Svsmo1t2Model model, double dt) {
        assertTrue(model.nextStep(dt, DynamicSimuMethod.MODIFIED_EULER, 0));
        assertTrue(model.nextStep(dt, DynamicSimuMethod.MODIFIED_EULER, 1));
    }
}
