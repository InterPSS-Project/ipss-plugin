package org.interpss.plugin.aclf.ecf;

import static org.junit.jupiter.api.Assertions.*;
import java.util.concurrent.CompletableFuture;
import org.apache.commons.math3.complex.Complex;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import com.interpss.core.CoreObjectFactory;
import com.interpss.core.LoadflowAlgoObjectFactory;
import com.interpss.core.aclf.*;
import org.interpss.plugin.aclf.ecf.*;
import com.interpss.simu.util.sample.SampleTestingCases;

class EcfPowerFlowTest {
    static AclfNetwork twoBus(Complex expected) throws Exception {
        var network = CoreObjectFactory.createAclfNetwork(); network.setBaseKva(100000);
        var swing = CoreObjectFactory.createAclfBus("reference-101", network).orElseThrow();
        swing.setBaseVoltage(230000); swing.setGenCode(AclfGenCode.SWING);
        swing.setDesiredVoltMag(1); swing.setDesiredVoltAng(0);
        swing.setVoltage(.97, .12); swing.setSortNumber(42);
        swing.setLoadCode(AclfLoadCode.CONST_P); swing.setLoadP(.1); swing.setLoadQ(.03);
        var load = CoreObjectFactory.createAclfBus("load-507", network).orElseThrow();
        load.setBaseVoltage(230000); load.setGenCode(AclfGenCode.NON_GEN); load.setLoadCode(AclfLoadCode.CONST_P);
        var branch = CoreObjectFactory.createAclfBranch(); branch.setBranchCode(AclfBranchCode.LINE);
        branch.setZ(new Complex(.02, .15)); network.addBranch(branch, swing.getId(), load.getId(), "1");
        Complex current = expected.subtract(Complex.ONE).divide(branch.getZ());
        var demand = expected.multiply(current.conjugate()).negate();
        load.setLoadP(demand.getReal()); load.setLoadQ(demand.getImaginary());
        load.setVoltage(.93, .07); load.setSortNumber(9);
        network.setBusNumberArranged(false); network.setLfConverged(false);
        return network;
    }
    @ParameterizedTest @EnumSource(EcfPowerFlowConfig.Backend.class)
    void recoversAnalyticalTwoBusPointWithBothBackendsWithoutMutatingSource(EcfPowerFlowConfig.Backend backend) throws Exception {
        var expected = new Complex(.96, -.055); var network = twoBus(expected);
        var initial = network.getBus("load-507").getVoltage();
        var config = new EcfPowerFlowConfig(1e-10, 50, .2, 20, EcfPowerFlowConfig.Initialization.FLAT, backend, false);
        var result = new EcfPowerFlowAlgorithm().solve(network, config);
        assertTrue(result.converged(), result.toString());
        assertEquals(0, result.voltages().get("load-507").subtract(expected).abs(), 1e-10);
        var line = network.getBranchList().get(0); var reference = result.generation().get("reference-101");
        var supplied = Complex.ONE.subtract(expected).divide(line.getZ()).conjugate().add(new Complex(.1,.03));
        assertEquals(0, reference.subtract(supplied).abs(), 1e-10);
        assertEquals(initial, network.getBus("load-507").getVoltage());
        assertEquals(42, network.getBus("reference-101").getSortNumber());
        assertEquals(9, network.getBus("load-507").getSortNumber());
        assertFalse(network.isBusNumberArranged()); assertFalse(network.isLfConverged());
        assertThrows(UnsupportedOperationException.class, () -> result.voltages().clear());
    }
    @Test void pvAndPqVoltagesAndGenerationAgreeWithLegacyNewton() throws Exception {
        var network = CoreObjectFactory.createAclfNetwork(); SampleTestingCases.load_LF_5BusSystem(network);
        network.getBusLoadLowVoltConfig().setApplyVoltAdjust(false);
        var result = new EcfPowerFlowAlgorithm().solve(network);
        assertTrue(result.converged(), result.toString());
        var legacy = LoadflowAlgoObjectFactory.createLoadflowAlgorithm(network);
        legacy.setTolerance(1e-10); legacy.getLfAdjAlgo().setApplyAdjustAlgo(false);
        assertTrue(legacy.loadflow());
        for (var bus : network.getBusList()) {
            assertEquals(0, bus.getVoltage().subtract(result.voltages().get(bus.getId())).abs(), 1e-8, bus.getId());
            assertEquals(0, bus.calNetGenResults().subtract(result.generation().get(bus.getId())).abs(), 1e-7, bus.getId());
        }
    }
    @Test void noLoadAndIndependentSuppliedIslandsConverge() throws Exception {
        var network = twoBus(Complex.ONE);
        network.getBus("reference-101").setLoadP(0); network.getBus("reference-101").setLoadQ(0);
        var island = CoreObjectFactory.createAclfBus("island", network).orElseThrow();
        island.setBaseVoltage(230000); island.setGenCode(AclfGenCode.SWING);
        island.setDesiredVoltMag(1.03); island.setDesiredVoltAng(.2);
        var result = new EcfPowerFlowAlgorithm().solve(network);
        assertTrue(result.converged(), result.detail());
        assertEquals(0, result.generation().get("island").abs(), 0);
        assertEquals(1.03, result.voltages().get("island").abs(), 1e-14);
        assertEquals(0, result.maximumCurrentMismatch(), 1e-14);
    }
    @Test void detectsMissingReferenceAndDoesNotMislabelIterationFailureAsInfeasibility() throws Exception {
        var network = twoBus(new Complex(.96,-.055));
        var config = new EcfPowerFlowConfig(1e-10, 0, .2, 20, EcfPowerFlowConfig.Initialization.FLAT, EcfPowerFlowConfig.Backend.KLU, false);
        var result = new EcfPowerFlowAlgorithm().solve(network, config);
        assertEquals(EcfPowerFlowResult.Status.NO_CONVERGENCE, result.status());
        network.getBus("reference-101").setGenCode(AclfGenCode.NON_GEN);
        assertEquals(EcfPowerFlowResult.Status.UNSUPPLIED_ISLAND, new EcfPowerFlowAlgorithm().solve(network).status());
        network.getBus("reference-101").setGenCode(AclfGenCode.GEN_PV);
        assertEquals(EcfPowerFlowResult.Status.INVALID_INPUT, new EcfPowerFlowAlgorithm().solve(network).status());
    }
    @Test void suppliedZeroVoltageIsInvalidAndCannotChangePriorResult() throws Exception {
        var network = twoBus(new Complex(.96,-.055)); var algorithm = new EcfPowerFlowAlgorithm();
        var accepted = algorithm.solve(network); var saved = accepted.voltages().get("load-507");
        network.getBus("load-507").setVoltage(0,0);
        var config = new EcfPowerFlowConfig(1e-10, 50, .2, 20, EcfPowerFlowConfig.Initialization.SUPPLIED, EcfPowerFlowConfig.Backend.KLU, false);
        assertEquals(EcfPowerFlowResult.Status.INVALID_INPUT, algorithm.solve(network,config).status());
        assertEquals(saved, accepted.voltages().get("load-507"));
    }
    @Test void snapshotsAndFactorsAreIndependentAcrossConcurrentSolves() throws Exception {
        var model = EcfNetworkModel.compile(twoBus(new Complex(.96,-.055)), false);
        var algorithm = new EcfPowerFlowAlgorithm();
        var a = CompletableFuture.supplyAsync(() -> algorithm.solve(model,EcfPowerFlowConfig.defaults()));
        var b = CompletableFuture.supplyAsync(() -> algorithm.solve(model,EcfPowerFlowConfig.defaults()));
        assertTrue(a.get().converged()); assertEquals(a.get().voltages(),b.get().voltages());
    }
}
