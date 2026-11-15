package org.interpss.core.adapter.builder.dstab;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;

import org.interpss.IpssCorePlugin;
import org.interpss.dstab.mach.Wt1g1Data;
import org.interpss.dstab.mach.Wt1g1Machine;
import org.interpss.fadapter.psse.PSSEMultiFileLoader;
import org.interpss.fadapter.psse.dyr.DynamicModelCatalog;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.interpss.dstab.algo.DynamicSimuMethod;
import com.interpss.dstab.cache.StateMonitor;

/** Exact parser, validation, initialization, and named-state checks for WT1G1. */
public class PsseWt1g1ModelTest {
    private static final Path CASE = Path.of("testData", "adpter", "psse", "v33", "SMIB");

    @Test
    void driveTrainUsesPresentVoltageBeforeMechanicalPredictor() throws Exception {
        var builder=DStabBuilderTestFixture.createBuilder();
        var net=builder.getDStabNetwork();net.setFrequency(60);
        var bus=net.getDStabBus("Bus1");
        var gen=(com.interpss.dstab.DStabGen)bus.getContributeGen("1");
        gen.setGen(new org.apache.commons.math3.complex.Complex(.5,0));
        var data=new Wt1g1Data(.81,.035,3.6,.21,.15,.1,1,.025,1.2,.14);
        var machine=builder.addWt1g1("Bus1","1",100,16.5,data);
        var mechanical=new org.interpss.dstab.mach.Wt12t1Data(1.,2.,.92,5.,1.);
        var drive=new org.interpss.dstab.mach.Wt12t1Model(mechanical);
        machine.setDriveTrain(drive);assertTrue(machine.initStates(bus));
        var expected=new org.interpss.dstab.mach.Wt12t1Model(mechanical);
        expected.initialize(machine.getPe(),machine.getSpeed(),60);
        double previousPower=machine.getPe();
        bus.setVoltage(.8,.1);
        // This machine's Norton evaluation is algebraic and pure. It supplies
        // an independent terminal-power boundary without refreshing cached Pe.
        double presentPower=bus.getVoltage().multiply(machine.getIgen()
                .subtract(machine.getYgen().multiply(bus.getVoltage())).conjugate())
                .getReal()/machine.getRating();
        assertTrue(Math.abs(presentPower-previousPower)>.05);
        expected.stepCoupled(.0033333,presentPower,bus.getVoltageMag(),0);
        assertTrue(machine.nextStepMechanical(.0033333,DynamicSimuMethod.MODIFIED_EULER,net,0));
        assertEquals(expected.getGeneratorSpeed(),machine.getSpeed(),1e-12,
                "A fault-side voltage changes algebraic Pe before the first mechanical derivative");
        assertEquals(expected.getTurbineSpeed(),drive.getTurbineSpeed(),1e-12);
    }

    @Test
    void saturatedSlipInitializationUsesActualTerminalVoltage() throws Exception {
        for(double v:new double[]{.9,1.1}) {
            var builder=DStabBuilderTestFixture.createBuilder();
            // The independent fixture is 60 Hz; the generic builder defaults to 50 Hz.
            builder.getDStabNetwork().setFrequency(60);
            var bus=builder.getDStabNetwork().getDStabBus("Bus1");
            var gen=(com.interpss.dstab.DStabGen)bus.getContributeGen("1");
            gen.setGen(new org.apache.commons.math3.complex.Complex(.5,.1));
            bus.setVoltage(new org.apache.commons.math3.complex.Complex(v*Math.cos(.2),v*Math.sin(.2)));
            var data=new Wt1g1Data(.81,.035,3.6,.21,.15,.1,1,.025,1.2,.14);
            var machine=builder.addWt1g1("Bus1","1",100,16.5,data);assertTrue(machine.initStates(bus));
            assertEquals(.5,machine.getPe(),2e-9,"Saturated slip must balance P at actual voltage");
            var initial=machine.getNamedStates();
            for(int i=0;i<20;i++)for(int stage=0;stage<2;stage++)
                assertTrue(machine.nextStepElectrical(.0005,DynamicSimuMethod.MODIFIED_EULER,builder.getDStabNetwork(),stage));
            assertEquals(.5,machine.getPe(),2e-8);
            for(var state:initial.entrySet())assertEquals(state.getValue(),machine.getNamedStates().get(state.getKey()),2e-8);
        }
    }

    @Test
    void nonUnitVoltageSlipMatchesIndependentInitialization() throws Exception {
        // Original channel samples on the public two-bus fixture, with the
        // external grid at .9/1.1 pu. Match the measured terminal voltage;
        // wind-generator VS alone does not control voltage in fixed-Q mode.
        double[][] profiles={{.8982771039009094,.004416251555085182},
                {1.099058985710144,.0029455332551151514}};
        for(var expected:profiles) {
            var builder=DStabBuilderTestFixture.createBuilder();
            // The independent fixture is 60 Hz; the generic builder defaults to 50 Hz.
            builder.getDStabNetwork().setFrequency(60);
            var bus=builder.getDStabNetwork().getDStabBus("Bus1");
            var gen=(com.interpss.dstab.DStabGen)bus.getContributeGen("1");
            gen.setGen(new org.apache.commons.math3.complex.Complex(.5,0));
            bus.setVoltage(new org.apache.commons.math3.complex.Complex(expected[0],0));
            var data=new Wt1g1Data(.81,.035,3.6,.21,.15,.1,1,.025,1.2,.14);
            var machine=builder.addWt1g1("Bus1","1",100,16.5,data);assertTrue(machine.initStates(bus));
            assertEquals(expected[1],machine.getSpeed()-1,2e-7,"Independent initialized rotor-speed deviation");
            assertEquals(.5,machine.getPe(),2e-9);
        }
    }

    @Test
    void independentSingleAndDoubleCageSaturationBoundaries() throws Exception {
        // Columns: single cage, saturation enabled, observed terminal V,
        // observed speed deviation and stator Q, all from original samples.
        // The source X is X' for one cage and X'' for two cages. Keeping the
        // two-cage source X for a one-cage experiment changes its Norton boundary.
        double[][] points = {
            {0, 1, 1.099058985710144, .0029455332551151514, -.38352474570274353},
            {0, 0, 1.0990588665008545, .002933714771643281, -.3686924874782562},
            {1, 1, 1.0990588665008545, .005249637644737959, -.3936980068683624},
            {1, 0, 1.0990588665008545, .005227163899689913, -.3820909261703491}
        };
        for (double[] point : points) {
            boolean single = point[0] == 1, saturated = point[1] == 1;
            var builder = DStabBuilderTestFixture.createBuilder();
            builder.getDStabNetwork().setFrequency(60);
            var bus = builder.getDStabNetwork().getDStabBus("Bus1");
            var gen = (com.interpss.dstab.DStabGen) bus.getContributeGen("1");
            gen.setGen(new org.apache.commons.math3.complex.Complex(.5, 0));
            gen.setSourceZ(new org.apache.commons.math3.complex.Complex(0, single ? .21 : .15));
            bus.setVoltage(new org.apache.commons.math3.complex.Complex(point[2], 0));
            var data = new Wt1g1Data(.81, single ? 0 : .035, 3.6, .21,
                    single ? 0 : .15, .1, 1, saturated ? .025 : 0, 1.2,
                    saturated ? .14 : 0);
            var machine = builder.addWt1g1("Bus1", "1", 100, 16.5, data);
            assertTrue(machine.initStates(bus));
            assertEquals(point[3], machine.getSpeed() - 1, 5e-8);
            assertEquals(point[4], ((Number) machine.getStates(null)
                    .get("WT1G1 Machine Q")).doubleValue(), 3e-6);
            assertEquals(.5, machine.getPe(), 2e-9);
        }
    }

    @Test
    void exactNativeRecordAttachesAndInitializes() throws Exception {
        IpssCorePlugin.init();
        var context = new PSSEMultiFileLoader().loadDStab(
                CASE.resolve("SMIB_v33_wt1g1.raw").toString(),
                CASE.resolve("SMIB_v33_wt1g1.dyr").toString());
        var network = context.getDStabilityNet();
        var algorithm = context.getDynSimuAlgorithm();
        assertTrue(algorithm.getAclfAlgorithm().loadflow());
        algorithm.setSimuMethod(DynamicSimuMethod.MODIFIED_EULER);
        algorithm.setSimuStepSec(0.0005);
        algorithm.setSimuOutputHandler(new StateMonitor());
        assertTrue(algorithm.initialization());

        Wt1g1Machine machine = assertInstanceOf(Wt1g1Machine.class,
                network.getMachine("Bus1-mach1"));
        assertEquals(0.810, machine.getWt1g1Data().tp(), 0.0);
        assertEquals(0.035, machine.getWt1g1Data().tpp(), 0.0);
        assertEquals(10, DynamicModelCatalog.find("WT1G1").orElseThrow().parameterCount());
        assertEquals(4, machine.getNamedStates().size());
        assertTrue(machine.getSlip() < 0.0, "induction generator must initialize above sync speed");
        assertEquals(-0.00356336, machine.getSlip(), 2.0e-5);
    }

    @Test
    void rejectsExtraNativeField(@TempDir Path tempDir) throws Exception {
        Path dyr = tempDir.resolve("extra.dyr");
        Files.writeString(dyr,
                "1 'WT1G1' '1' .81 .035 3.6 .21 .15 .1 1 .025 1.2 .14 99 /\n"
                        + "2 'GENCLS' '1' 99999 0 /\n");
        var context = new PSSEMultiFileLoader().loadDStab(
                CASE.resolve("SMIB_v33_wt1g1.raw").toString(), dyr.toString());
        assertTrue(!(context.getDStabilityNet().getMachine("Bus1-mach1")
                instanceof Wt1g1Machine));
    }

    @Test
    void validatesSingleAndDoubleCageBoundaries() {
        Wt1g1Data single = new Wt1g1Data(.81, 0, 3.6, .21, 0, .1,
                1, .025, 1.2, .14);
        assertTrue(!single.twoCage());
        assertEquals(.21, single.xpp(), 0.0);
        assertThrows(IllegalArgumentException.class, () -> new Wt1g1Data(
                .81, .035, 3.6, .21, .25, .1, 1, .025, 1.2, .14));
        assertThrows(IllegalArgumentException.class, () -> new Wt1g1Data(
                0, 0, 3.6, .21, 0, .1, 1, .025, 1.2, .14));
    }
}
