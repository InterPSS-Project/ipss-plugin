package org.interpss.core.dstab.mach;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.apache.commons.math3.complex.Complex;
import org.interpss.dstab.mach.GenqecData;
import org.interpss.dstab.mach.GenqecMachine;
import org.interpss.dstab.mach.GenqejData;
import org.interpss.dstab.mach.GenqejMachine;
import org.interpss.dstab.mach.Gentpj1Data;
import org.interpss.dstab.mach.Gentpj1Machine;
import org.interpss.fadapter.builder.DStabNetworkBuilder;
import org.interpss.fadapter.builder.AclfNetworkBuilder;
import org.interpss.dstab.control.exc.simple.SimpleExciter;
import org.junit.jupiter.api.Test;

import com.interpss.dstab.BaseDStabBus;
import com.interpss.dstab.BaseDStabNetwork;
import com.interpss.dstab.DStabObjectFactory;
import com.interpss.dstab.DStabilityNetwork;
import com.interpss.dstab.algo.DynamicSimuAlgorithm;
import com.interpss.dstab.algo.DynamicSimuMethod;
import com.interpss.dstab.cache.StateMonitor;
import com.interpss.dstab.controller.cml.annotate.util.CMLSymbolMapper;
import com.interpss.dstab.datatype.CMLVarEnum;
import com.interpss.dstab.mach.MachineIfdBase;
import com.interpss.dstab.util.sample.SampleDStabCase;
import com.interpss.core.net.OriginalDataFormat;

class GenqecMachineTest extends TestSetupBase {
    private static final double TOL = 1.0e-8;

    private static GenqecData benchmarkData(int saturationFunction) {
        return new GenqecData(
                3.17, 0.0, 0.003,
                2.37, 1.87, 0.32, 0.52, 0.28, 0.20, 0.19,
                6.81, 0.85, 0.02, 0.02,
                0.233, 0.797,
                0.0, 0.0, 0.0, 0.1, saturationFunction);
    }

    @Test
    void saturationFunctionsPassSpecifiedPoints() {
        for (int flag = 0; flag <= 2; flag++) {
            GenqecMachine machine = new GenqecMachine(benchmarkData(flag));
            assertEquals(0.233, machine.getSatruationFactor(1.0), TOL);
            assertEquals(0.797, machine.getSatruationFactor(1.2), TOL);
        }
        GenqecMachine disabled = new GenqecMachine(benchmarkData(-1));
        assertEquals(0.0, disabled.getSatruationFactor(1.2), TOL);
    }

    @Test
    void powerWorldAutocorrectionsAreApplied() {
        GenqecData corrected = new GenqecData(
                3.0, 0.0, 0.0,
                2.0, 1.8, 2.2, 0.0, 0.01, 1.0, 0.40,
                7.0, 0.0, 0.03, 0.05,
                0.05, 0.30,
                0.0, 0.0, 0.0, 0.0, 9);

        assertEquals(1.6, corrected.xdp(), TOL);
        assertEquals(1.8, corrected.xqp(), TOL);
        assertEquals(0.05, corrected.xdpp(), TOL);
        assertEquals(0.075, corrected.xqpp(), TOL);
        assertEquals(0.04, corrected.xl(), TOL);
        assertEquals(0, corrected.satFunc());

        GenqecData noDamperWindings = new GenqecData(
                3.0, 0.0, 0.0,
                2.0, 1.8, 0.30, 0.0, 0.0, 0.0, 0.15,
                7.0, 0.0, 0.0, 0.0,
                0.05, 0.30,
                0.0, 0.0, 0.0, 0.0, 0);
        assertEquals(noDamperWindings.xdp(), noDamperWindings.xdpp(), TOL);
        assertEquals(noDamperWindings.xq(), noDamperWindings.xqp(), TOL);
        assertEquals(noDamperWindings.xqp(), noDamperWindings.xqpp(), TOL);
    }

    @Test
    void initializesAtLoadFlowAndRemainsAtSteadyState() throws Exception {
        BaseDStabNetwork<?, ?> network = SampleDStabCase.createDStabTestNet();
        GenqecMachine machine = new DStabNetworkBuilder(network).addGenqec(
                "Gen", "G1", 100.0, 1.0, benchmarkData(1));
        BaseDStabBus<?, ?> bus = network.getDStabBus("Gen");
        bus.initStates();
        assertTrue(machine.initStates(bus));

        Complex expectedCurrent = network.getDStabBus("Gen").getContributeGen("G1").getGen()
                .divide(bus.getVoltage()).conjugate();
        Complex actualCurrent = machine.getIgen().subtract(bus.getVoltage().multiply(machine.getYgen()));
        assertEquals(expectedCurrent.getReal(), actualCurrent.getReal(), 1.0e-7);
        assertEquals(expectedCurrent.getImaginary(), actualCurrent.getImaginary(), 1.0e-7);

        var idq = machine.getIdq();
        var vdq = machine.getVdq();
        assertEquals(-machine.getPsiq11() * machine.getSpeed() - machine.getRa() * idq.d
                        + machine.getXqppSaturated() * idq.q,
                vdq.d, 1.0e-7);
        assertEquals(machine.getPsid11() * machine.getSpeed() - machine.getXdppSaturated() * idq.d
                        - machine.getRa() * idq.q,
                vdq.q, 1.0e-7);
        assertTrue(Double.isFinite(machine.calculateIfd(MachineIfdBase.MACHINE)));

        double angle0 = machine.getAngle();
        double eq10 = machine.getEq1();
        double ed10 = machine.getEd1();
        for (int i = 0; i < 5; i++) {
            machine.getIgen();
            assertTrue(machine.nextStep(0.005, DynamicSimuMethod.MODIFIED_EULER, 0));
            machine.getIgen();
            assertTrue(machine.nextStep(0.005, DynamicSimuMethod.MODIFIED_EULER, 1));
        }
        assertEquals(angle0, machine.getAngle(), 2.0e-6);
        assertEquals(eq10, machine.getEq1(), 2.0e-6);
        assertEquals(ed10, machine.getEd1(), 2.0e-6);
        assertTrue(Double.isFinite(machine.getCompensatedVoltage()));
    }

    @Test
    void cmlMachVtUsesGenqecCompensatedVoltage() throws Exception {
        BaseDStabNetwork<?, ?> network = SampleDStabCase.createDStabTestNet();
        GenqecData compensatedData = new GenqecData(
                3.17, 0.0, 0.003,
                2.37, 1.87, 0.32, 0.52, 0.28, 0.20, 0.19,
                6.81, 0.85, 0.02, 0.02,
                0.233, 0.797,
                0.02, 0.10, 0.0, 0.1, 1);
        GenqecMachine machine = new DStabNetworkBuilder(network).addGenqec(
                "Gen", "G1", 100.0, 1.0, compensatedData);
        BaseDStabBus<?, ?> bus = network.getDStabBus("Gen");
        bus.initStates();
        assertTrue(machine.initStates(bus));

        SimpleExciter legacyCmlExciter = new SimpleExciter();
        legacyCmlExciter.setMachine(machine);
        double cmlMachVt = CMLSymbolMapper.getValue(
                CMLVarEnum.MachVt, "mach.vt", legacyCmlExciter);
        double rawTerminalVoltage = bus.getVoltage().abs() / machine.getVMultiFactor();

        assertEquals(machine.getCompensatedVoltage(), cmlMachVt, TOL);
        assertTrue(Math.abs(cmlMachVt - rawTerminalVoltage) > 1.0e-5);
        assertEquals(bus.getVoltageMag(), CMLSymbolMapper.getValue(
                CMLVarEnum.BusVMag, "bus.vmag", legacyCmlExciter), TOL);
    }

    @Test
    void genqejAddsKisCurrentMagnitudeToTheSharedSaturationInput() throws Exception {
        BaseDStabNetwork<?, ?> qecNetwork = SampleDStabCase.createDStabTestNet();
        GenqecMachine qec = new DStabNetworkBuilder(qecNetwork).addGenqec(
                "Gen", "G1", 100.0, 1.0, benchmarkData(1));
        BaseDStabBus<?, ?> qecBus = qecNetwork.getDStabBus("Gen");
        qecBus.initStates();
        assertTrue(qec.initStates(qecBus));

        BaseDStabNetwork<?, ?> qejNetwork = SampleDStabCase.createDStabTestNet();
        GenqecData qecData = benchmarkData(1);
        GenqejData qejData = new GenqejData(
                qecData.h(), qecData.d(), qecData.ra(), qecData.xd(), qecData.xq(),
                qecData.xdp(), qecData.xqp(), qecData.xdpp(), qecData.xqpp(), qecData.xl(),
                qecData.tdop(), qecData.tqop(), qecData.tdopp(), qecData.tqopp(),
                qecData.s1(), qecData.s12(), qecData.rcomp(), qecData.xcomp(),
                qecData.accel(), 0.15, qecData.satFunc());
        GenqejMachine qej = new DStabNetworkBuilder(qejNetwork).addGenqej(
                "Gen", "G1", 100.0, 1.0, qejData);
        BaseDStabBus<?, ?> qejBus = qejNetwork.getDStabBus("Gen");
        qejBus.initStates();
        assertTrue(qej.initStates(qejBus));

        assertTrue(qej.getEffectiveSaturationFactor() > qec.getEffectiveSaturationFactor());
        var qejIdq = qej.getIdq();
        var qejVdq = qej.getVdq();
        double airGapFlux = Math.hypot(
                qejVdq.q + qej.getRa() * qejIdq.q + qej.getXl() * qejIdq.d,
                qejVdq.d + qej.getRa() * qejIdq.d - qej.getXl() * qejIdq.q);
        double expectedSaturation = qej.getSatruationFactor(
                airGapFlux + qejData.kis() * Math.hypot(qejIdq.d, qejIdq.q));
        assertEquals(expectedSaturation, qej.getEffectiveSaturationFactor(), TOL);
        assertTrue(Double.isFinite(qej.calculateIfd(MachineIfdBase.MACHINE)));

        double angle0 = qej.getAngle();
        for (int i = 0; i < 5; i++) {
            qej.getIgen();
            assertTrue(qej.nextStep(0.005, DynamicSimuMethod.MODIFIED_EULER, 0));
            qej.getIgen();
            assertTrue(qej.nextStep(0.005, DynamicSimuMethod.MODIFIED_EULER, 1));
        }
        assertEquals(angle0, qej.getAngle(), 2.0e-6);
    }

    @Test
    void gentpj1UsesQuadraticCurrentDependentSaturationAndHoldsEquilibrium() throws Exception {
        BaseDStabNetwork<?, ?> network = SampleDStabCase.createDStabTestNet();
        Gentpj1Data data = new Gentpj1Data(
                7.25, 0.035, 0.72, 0.045, 4.15, 0.0,
                2.05, 1.91, 0.36, 0.59, 0.27, 0.23, 0.16,
                0.075, 0.31, 0.22);
        Gentpj1Machine machine = new DStabNetworkBuilder(network).addGentpj1(
                "Gen", "G1", 100.0, 1.0, data);
        BaseDStabBus<?, ?> bus = network.getDStabBus("Gen");
        bus.initStates();
        assertTrue(machine.initStates(bus));

        assertEquals(2, machine.getGenqecData().satFunc());
        var idq = machine.getIdq();
        var vdq = machine.getVdq();
        double airGapFlux = Math.hypot(
                vdq.q + machine.getRa() * idq.q + machine.getXl() * idq.d,
                vdq.d + machine.getRa() * idq.d - machine.getXl() * idq.q);
        assertEquals(machine.getSatruationFactor(
                        airGapFlux + data.kis() * Math.hypot(idq.d, idq.q)),
                machine.getEffectiveSaturationFactor(), TOL);

        double angle0 = machine.getAngle();
        for (int i = 0; i < 5; i++) {
            machine.getIgen();
            assertTrue(machine.nextStep(0.0025, DynamicSimuMethod.MODIFIED_EULER, 0));
            machine.getIgen();
            assertTrue(machine.nextStep(0.0025, DynamicSimuMethod.MODIFIED_EULER, 1));
        }
        assertEquals(angle0, machine.getAngle(), 2.0e-6);
    }

    @Test
    void runsThroughInterpssNetworkDynamicSolver() throws Exception {
        DStabilityNetwork network = DStabObjectFactory.createDStabilityNetwork();
        network.setBaseKva(100000.0);
        AclfNetworkBuilder topology = new AclfNetworkBuilder(network);
        topology.setNetworkInfo("genqec-smoke", "genqec-smoke", 100000.0, OriginalDataFormat.PSSE);
        topology.addBus("Bus1", "GENQEC", 1L, 16500.0, 1.0, 0.0, null, null, null);
        topology.setSwingBus("Bus1", 1.0, 0.0);
        topology.addContributeGen("Bus1", "1", true, 0.0, 0.0, 100.0, 1.0,
                0.0, 0.0, 0.0, 0.0, new Complex(0.0, 0.20), null, 0.0, null, 0.0, 0.0);
        GenqecMachine machine = new DStabNetworkBuilder(network).addGenqec(
                "Bus1", "1", 100.0, 16.5, benchmarkData(1));
        DynamicSimuAlgorithm algorithm = DStabObjectFactory.createDynamicSimuAlgorithm(network);
        algorithm.setSimuMethod(DynamicSimuMethod.MODIFIED_EULER);
        algorithm.setSimuStepSec(0.005);
        algorithm.setTotalSimuTimeSec(0.05);
        algorithm.setSimuOutputHandler(new StateMonitor());

        assertTrue(algorithm.getAclfAlgorithm().loadflow());
        assertTrue(algorithm.initialization());
        assertTrue(algorithm.performSimulation());
        assertTrue(Double.isFinite(machine.getAngle()));
        assertTrue(Double.isFinite(machine.getSpeed()));
        assertTrue(Double.isFinite(machine.getPe()));
    }
}
