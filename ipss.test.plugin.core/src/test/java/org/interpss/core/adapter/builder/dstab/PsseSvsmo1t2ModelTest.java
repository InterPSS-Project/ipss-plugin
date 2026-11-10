package org.interpss.core.adapter.builder.dstab;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;

import org.apache.commons.math3.complex.Complex;
import org.interpss.CorePluginTestSetup;
import org.interpss.dstab.svc.Svsmo1t2Model;
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

    private static void step(Svsmo1t2Model model, double dt) {
        assertTrue(model.nextStep(dt, DynamicSimuMethod.MODIFIED_EULER, 0));
        assertTrue(model.nextStep(dt, DynamicSimuMethod.MODIFIED_EULER, 1));
    }
}
