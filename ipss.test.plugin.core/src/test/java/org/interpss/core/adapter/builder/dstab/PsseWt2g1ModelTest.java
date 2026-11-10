package org.interpss.core.adapter.builder.dstab;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;

import org.interpss.IpssCorePlugin;
import org.interpss.dstab.mach.Wt2g1Data;
import org.interpss.dstab.mach.Wt2g1Machine;
import org.interpss.fadapter.psse.PSSEMultiFileLoader;
import org.interpss.fadapter.psse.dyr.DynamicModelCatalog;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.interpss.dstab.algo.DynamicSimuMethod;
import com.interpss.dstab.cache.StateMonitor;

/** Exact parser, validation, initialization, and named-state checks for WT2G1. */
public class PsseWt2g1ModelTest {
    private static final Path CASE = Path.of("testData", "adpter", "psse", "v33", "SMIB");

    @Test void saturatedResistanceInitializationUsesActualTerminalVoltage() throws Exception {
        for(double v:new double[]{.9,1.1}) {
            var b=DStabBuilderTestFixture.createBuilder();var bus=b.getDStabNetwork().getDStabBus("Bus1");
            var gen=(com.interpss.dstab.DStabGen)bus.getContributeGen("1");
            gen.setGen(new org.apache.commons.math3.complex.Complex(.5,.1));
            bus.setVoltage(new org.apache.commons.math3.complex.Complex(v*Math.cos(.2),v*Math.sin(.2)));
            var d=new Wt2g1Data(.14,6.5,.2,.005,.12,1,.03,1.2,.29,
                    new double[]{0,.15,.45,.75,.95},new double[]{0,.006,.025,.055,.11});
            var m=b.addWt2g1("Bus1","1",100,16.5,d);assertTrue(m.initStates(bus));
            assertEquals(.5,m.getPe(),2e-9,"Solved rotor resistance must balance power at actual saturated flux");
            var initial=m.getNamedStates();
            for(int i=0;i<10;i++)for(int stage=0;stage<2;stage++)
                assertTrue(m.nextStepElectrical(.0001,DynamicSimuMethod.MODIFIED_EULER,b.getDStabNetwork(),stage));
            assertEquals(.5,m.getPe(),2e-8);
            for(var s:initial.entrySet())assertEquals(s.getValue(),m.getNamedStates().get(s.getKey()),2e-8);
        }
    }

    @Test
    void exactNativeRecordAttachesAndInitializes() throws Exception {
        IpssCorePlugin.init();
        var context = new PSSEMultiFileLoader().loadDStab(
                CASE.resolve("SMIB_v33_wt2g1.raw").toString(),
                CASE.resolve("SMIB_v33_wt2g1.dyr").toString());
        var algorithm = context.getDynSimuAlgorithm();
        assertTrue(algorithm.getAclfAlgorithm().loadflow());
        algorithm.setSimuMethod(DynamicSimuMethod.MODIFIED_EULER);
        algorithm.setSimuStepSec(0.0005);
        algorithm.setSimuOutputHandler(new StateMonitor());
        assertTrue(algorithm.initialization());

        Wt2g1Machine machine = assertInstanceOf(Wt2g1Machine.class,
                context.getDStabilityNet().getMachine("Bus1-mach1"));
        assertEquals(0.140, machine.getWt2g1Data().xa(), 0.0);
        assertEquals(0.3340298507462687, machine.getWt2g1Data().transientReactance(), 1e-14);
        assertEquals(19, DynamicModelCatalog.find("WT2G1").orElseThrow().parameterCount());
        assertEquals(3, machine.getNamedStates().size());
        assertEquals(0.03, machine.getSpeed() - 1.0, 1e-12);
        assertTrue(machine.getRotorResistance() > machine.getWt2g1Data().rotorResistance());
        assertTrue(machine.getRotorResistance() < machine.getWt2g1Data().maximumRotorResistance());
    }

    @Test
    void rejectsExtraNativeField(@TempDir Path tempDir) throws Exception {
        Path dyr = tempDir.resolve("extra.dyr");
        Files.writeString(dyr,
                "1 'WT2G1' '1' .14 6.5 .2 .005 .12 1.02 0 1.22 0 "
                        + "0 .15 .45 .75 .95 0 .006 .025 .055 .11 99 /\n"
                        + "2 'GENCLS' '1' 99999 0 /\n");
        var context = new PSSEMultiFileLoader().loadDStab(
                CASE.resolve("SMIB_v33_wt2g1.raw").toString(), dyr.toString());
        assertTrue(!(context.getDStabilityNet().getMachine("Bus1-mach1")
                instanceof Wt2g1Machine));
    }

    @Test
    void validatesAndInterpolatesPowerSlipCurve() {
        Wt2g1Data data = data();
        assertEquals(0.03, data.speedDeviation(0.50), 1e-12);
        assertEquals(0.0, data.speedDeviation(-1.0), 0.0);
        assertEquals(0.11, data.speedDeviation(2.0), 0.0);
        assertThrows(IllegalArgumentException.class, () -> new Wt2g1Data(
                .14, 6.5, .2, .12, .005, 1.02, 0, 1.22, 0,
                new double[] {0, .15, .45, .75, .95},
                new double[] {0, .006, .025, .055, .11}));
    }

    private static Wt2g1Data data() {
        return new Wt2g1Data(.14, 6.5, .2, .005, .12, 1.02, 0, 1.22, 0,
                new double[] {0, .15, .45, .75, .95},
                new double[] {0, .006, .025, .055, .11});
    }
}
