package org.interpss.core.adapter.builder.dstab;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;

import org.interpss.IpssCorePlugin;
import com.interpss.dstab.mach.model.Wt3e1Model;
import com.interpss.dstab.mach.model.Wt3g2Data;
import com.interpss.dstab.mach.model.Wt3g2Model;
import org.interpss.fadapter.psse.PSSEMultiFileLoader;
import org.interpss.fadapter.psse.dyr.DynamicModelCatalog;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.interpss.dstab.DStabGen;
import com.interpss.dstab.algo.DynamicSimuMethod;
import com.interpss.dstab.cache.StateMonitor;

/** Exact schema, composition, limits, and named-state checks for WT3G2. */
public class PsseWt3g2ModelTest {
    private static final Path CASE = Path.of("testData", "adpter", "psse", "v33", "SMIB");

    @Test
    void exactNativeRecordAttachesWithWt3e1AndInitializes() throws Exception {
        Fixture fixture = load();
        Wt3g2Model model = fixture.model;
        assertEquals(new Wt3g2Data(50, .018, .023, 20, .55, .12, 2,
                .48, .88, 1.15, 1.18, 2.10, 4.70, .025), model.getData());
        assertEquals(14, DynamicModelCatalog.find("WT3G2").orElseThrow().parameterCount());
        assertEquals(5, model.getNamedStates().size());
        assertInstanceOf(Wt3e1Model.class, model.getElectricalController());
        assertEquals(fixture.network.getBus("Bus1").getVoltage().getArgument(),
                model.getPllAngleState(), 1.0e-12);
        assertEquals(.5, model.getP(), 2.0e-6);
        assertEquals(100.0, model.getAggregateRatedMw(), 1.0e-12);
    }

    @Test
    void unequalMachineBasePreservesTerminalPowerAndNortonBalance() throws Exception {
        var builder=DStabBuilderTestFixture.createBuilder();
        var bus=builder.getDStabNetwork().getDStabBus("Bus1");
        var gen=(DStabGen)bus.getContributeGen("1");
        gen.setMvaBase(21);gen.setGen(new org.apache.commons.math3.complex.Complex(.107,-.03215));
        gen.setZMultiFactor(1); // first device init precedes matrix formation
        gen.setPosGenZ(new org.apache.commons.math3.complex.Complex(0,.8));
        bus.setVoltage(org.apache.commons.math3.complex.Complex.ONE);
        var model=builder.addWt3g2("Bus1","1",new Wt3g2Data(1,.02,.02,0,0,.1,21,.5,.9,1.22,1.2,1.3,10,.02));
        assertTrue(model.initStates(bus));
        assertEquals(21,model.getDeviceBaseMva(),0);
        assertEquals(.107*100/21,model.getP(),1e-12);
        assertEquals(1+.8*(-.03215*100/21),model.getInternalVoltageState(),1e-12);
        gen.setZMultiFactor(100.0/21.0); // matrix formation refreshes this cache
        assertTrue(model.initStates(bus));
        assertEquals(1+.8*(-.03215*100/21),model.getInternalVoltageState(),1e-12,
                "reinitialization must not change the state base");
        var source=(org.apache.commons.math3.complex.Complex)model.getOutputObject();
        var physical=source.subtract(bus.getVoltage().divide(gen.getPosGenZ().multiply(gen.getZMultiFactor())));
        assertEquals(0,bus.getVoltage().multiply(physical.conjugate()).subtract(gen.getGen()).abs(),1e-12);
        assertTrue(model.updateAttributes(false));
        assertEquals(-.03215*100/21,model.getQ(),1e-12);
        double ip=model.getActiveCurrentState(),eq=model.getInternalVoltageState();
        for(double v:new double[]{.6,1,1.2,1.3,2}) {
            var voltage=new org.apache.commons.math3.complex.Complex(Math.cos(.3)*v,Math.sin(.3)*v);
            bus.setVoltage(voltage);
            var norton=(org.apache.commons.math3.complex.Complex)model.getOutputObject();
            var actual=norton.subtract(voltage.divide(gen.getPosGenZ().multiply(gen.getZMultiFactor())));
            var expected=new org.apache.commons.math3.complex.Complex(ip*Wt3g2Model.fixedLowVoltageActiveGain(v),
                    (v-eq)/.8+Wt3g2Model.highVoltageCorrection(v,1.2,1.3))
                    .multiply(new org.apache.commons.math3.complex.Complex(Math.cos(.3),Math.sin(.3))).multiply(.21);
            assertEquals(0,actual.subtract(expected).abs(),1e-12,"Norton boundary must preserve physical current across voltage regions");
        }
    }

    @Test
    void curhvrcrCapsNortonReactiveCurrentWithoutClippingTheEqState() throws Exception {
        var builder = DStabBuilderTestFixture.createBuilder();
        var bus = builder.getDStabNetwork().getDStabBus("Bus1");
        var gen = (DStabGen) bus.getContributeGen("1");
        gen.setMvaBase(64.2);
        gen.setGen(new org.apache.commons.math3.complex.Complex(.30, -.08304));
        gen.setPosGenZ(new org.apache.commons.math3.complex.Complex(0, .8));
        gen.setZMultiFactor(100.0 / 64.2);
        bus.setVoltage(1.0, 0.0);
        var model = builder.addWt3g2("Bus1", "1",
                new Wt3g2Data(1, .02, .02, 0, 0, .1, 64.2, .5, .9, 1.22, 1.2, 1.3, 5, .02));
        assertTrue(model.initStates(bus));
        double voltage = .65;
        bus.setVoltage(voltage, .3);
        model.setCommands(model.getActiveCurrentState(), 1.45);
        for (int i = 0; i < 80; i++) {
            assertTrue(model.nextStep(.001, DynamicSimuMethod.MODIFIED_EULER, 0));
            assertTrue(model.nextStep(.001, DynamicSimuMethod.MODIFIED_EULER, 1));
        }
        double eq = model.getInternalVoltageState();
        assertTrue(eq > .8 * 1.3);
        var terminal = bus.getVoltage();
        var source = (org.apache.commons.math3.complex.Complex) model.getOutputObject();
        var current = source.subtract(terminal.divide(gen.getPosGenZ().multiply(gen.getZMultiFactor())));
        assertEquals(voltage * .642 * (1.3 - voltage / .8),
                terminal.multiply(current.conjugate()).getImaginary(), 1e-12);
        assertEquals(eq, model.getInternalVoltageState(), 0.0);
    }

    @Test
    void lvplAndHvrcUsePublishedPiecewiseBoundaries() throws Exception {
        Fixture fixture = load();
        Wt3g2Model model = fixture.model;
        assertEquals(0.0, model.lowVoltagePowerLimit(.47), 0.0);
        assertEquals(1.15 * .50, model.lowVoltagePowerLimit(.68), 1.0e-12);
        assertEquals(1.15, model.lowVoltagePowerLimit(.95), 1.0e-12);
        assertEquals(0.0, Wt3g2Model.fixedLowVoltageActiveGain(.4), 0.0);
        assertEquals(.5, Wt3g2Model.fixedLowVoltageActiveGain(.6), 1.0e-12);
        assertEquals(1.0, Wt3g2Model.fixedLowVoltageActiveGain(.8), 0.0);
        assertEquals(0.0, Wt3g2Model.highVoltageCorrection(1.17, 1.18, 2.1), 0.0);
        assertEquals(.084, Wt3g2Model.highVoltageCorrection(1.22, 1.18, 2.1), 1.0e-12);
        assertEquals(4.7, Wt3g2Model.directionalRecoveryRate(.2, 10.0, 4.7), 0.0);
        assertEquals(-10.0, Wt3g2Model.directionalRecoveryRate(.2, -10.0, 4.7), 0.0);
        assertEquals(10.0, Wt3g2Model.directionalRecoveryRate(-.2, 10.0, 4.7), 0.0);
        assertEquals(-4.7, Wt3g2Model.directionalRecoveryRate(-.2, -10.0, 4.7), 0.0);

        double initialIp = model.getActiveCurrentState();
        model.setCommands(initialIp + 1.0, model.getInternalVoltageState());
        assertTrue(model.nextStep(.001, DynamicSimuMethod.MODIFIED_EULER, 0));
        assertTrue(model.nextStep(.001, DynamicSimuMethod.MODIFIED_EULER, 1));
        assertTrue(model.getActiveCurrentState() - initialIp <= .0047 + 1.0e-12,
                "RIp_LVPL is the upward recovery-rate boundary");
    }

    @Test
    void rejectsExtraFieldAndInvalidBoundaries(@TempDir Path tempDir) throws Exception {
        Path dyr = tempDir.resolve("extra.dyr");
        Files.writeString(dyr,
                "1 'WT3G2' '1' 50 .018 .023 20 .55 .12 2 .48 .88 1.15 1.18 2.1 4.7 .025 99 /\n"
                        + "2 'GENCLS' '1' 99999 0 /\n");
        var context = new PSSEMultiFileLoader().loadDStab(
                CASE.resolve("SMIB_v33_wt3g2.raw").toString(), dyr.toString());
        DStabGen gen = (DStabGen) context.getDStabilityNet().getBus("Bus1")
                .getContributeGen("1");
        assertTrue(!(gen.getDynamicGenDevice() instanceof Wt3g2Model));
        assertThrows(IllegalArgumentException.class, () -> new Wt3g2Data(
                0, .02, .02, 1, 1, .1, 2, .4, .9, 2, 1.2, 2, 5, .02));
        assertThrows(IllegalArgumentException.class, () -> new Wt3g2Data(
                10, .02, .02, 1, 1, .1, 2, .9, .4, 2, 1.2, 2, 5, .02));
    }

    private static Fixture load() throws Exception {
        IpssCorePlugin.init();
        var context = new PSSEMultiFileLoader().loadDStab(
                CASE.resolve("SMIB_v33_wt3g2.raw").toString(),
                CASE.resolve("SMIB_v33_wt3g2.dyr").toString());
        var network = context.getDStabilityNet();
        var algorithm = context.getDynSimuAlgorithm();
        DStabGen gen = (DStabGen) network.getBus("Bus1").getContributeGen("1");
        Wt3g2Model model = assertInstanceOf(Wt3g2Model.class, gen.getDynamicGenDevice());
        algorithm.getAclfAlgorithm().getDataCheckConfig().setAllowGenWithoutMachine(true);
        assertTrue(algorithm.getAclfAlgorithm().loadflow());
        algorithm.setSimuMethod(DynamicSimuMethod.MODIFIED_EULER);
        algorithm.setSimuStepSec(.0005);
        algorithm.setSimuOutputHandler(new StateMonitor());
        assertTrue(algorithm.initialization());
        return new Fixture(network, model);
    }

    private record Fixture(com.interpss.dstab.BaseDStabNetwork<?, ?> network,
            Wt3g2Model model) { }
}
