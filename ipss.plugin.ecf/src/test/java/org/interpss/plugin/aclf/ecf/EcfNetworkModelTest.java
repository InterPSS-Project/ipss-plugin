package org.interpss.plugin.aclf.ecf;

import static org.junit.jupiter.api.Assertions.*;
import org.apache.commons.math3.complex.Complex;
import org.junit.jupiter.api.Test;
import com.interpss.core.CoreObjectFactory;
import com.interpss.core.aclf.*;
import org.interpss.plugin.aclf.ecf.*;

class EcfNetworkModelTest {
    @Test void transformerTerminalOperatorsMatchIndependentComplexTapEquations() throws Exception {
        var network = EcfPowerFlowTest.twoBus(new Complex(.96,-.055));
        var branch = network.getBranchList().get(0);
        branch.setBranchCode(AclfBranchCode.PS_XFORMER); branch.setFromTurnRatio(1.08); branch.setToTurnRatio(.99);
        branch.setFromPSXfrAngle(.12); branch.setToPSXfrAngle(-.03);
        var sf = new Complex(.001,.014); var st = new Complex(.002,.008);
        branch.setFromShuntY(sf); branch.setToShuntY(st);
        var shunt = new Complex(.003,-.025); network.getBus("load-507").setShuntY(shunt);
        var model = EcfNetworkModel.compile(network,false); var line = model.branches().getFirst();
        var y = Complex.ONE.divide(branch.getZ());
        var tf = new Complex(1.08*Math.cos(.12),1.08*Math.sin(.12));
        var tt = new Complex(.99*Math.cos(-.03),.99*Math.sin(-.03));
        assertEquals(0,line.yff().subtract(y.add(sf).divide(1.08*1.08)).abs(),1e-12);
        assertEquals(0,line.yft().subtract(y.negate().divide(tt.multiply(tf.conjugate()))).abs(),1e-12);
        assertEquals(0,line.ytf().subtract(y.negate().divide(tf.multiply(tt.conjugate()))).abs(),1e-12);
        var v = new Complex[]{new Complex(1.01,.04),new Complex(.95,-.08)};
        var current = model.currents(v);
        assertEquals(0,current[0].subtract(line.fromCurrent(v[0],v[1])).abs(),1e-12);
        assertEquals(0,current[1].subtract(line.toCurrent(v[0],v[1]).add(shunt.multiply(v[1]))).abs(),1e-12);
        branch.setZ(new Complex(.1,.3)); network.getBus("load-507").setShuntY(Complex.ZERO);
        assertArrayEquals(current,model.currents(v),"Source edits must not affect an existing electrical snapshot");
    }
    @Test void lineChargingParallelCircuitsAndOfflineEquipmentAreStampedExactlyOnce() throws Exception {
        var network = EcfPowerFlowTest.twoBus(Complex.ONE); var first = network.getBranchList().getFirst();
        first.setHShuntY(new Complex(0,.02));
        var second = CoreObjectFactory.createAclfBranch(); second.setBranchCode(AclfBranchCode.LINE); second.setZ(first.getZ());
        network.addBranch(second,"reference-101","load-507","2");
        var offline = CoreObjectFactory.createAclfBranch(); offline.setZ(new Complex(.01,.01));
        network.addBranch(offline,"reference-101","load-507","3"); offline.setStatus(false);
        var model = EcfNetworkModel.compile(network,false);
        var currents = model.currents(new Complex[]{Complex.ONE,Complex.ONE});
        assertEquals(2,model.branches().size());
        assertEquals(0,currents[0].subtract(new Complex(0,.02)).abs(),1e-12);
        assertEquals(0,currents[1].subtract(new Complex(0,.02)).abs(),1e-12);
    }
    @Test void contributedLoadAndGenerationAreReadWithoutOverwritingStaleAggregateFields() throws Exception {
        var network = EcfPowerFlowTest.twoBus(Complex.ONE); network.getBusLoadLowVoltConfig().setApplyVoltAdjust(false);
        var bus = network.getBus("load-507"); bus.setGenCode(AclfGenCode.GEN_PQ);
        bus.setGenP(99); bus.setGenQ(98); bus.setLoadP(97); bus.setLoadQ(96);
        for (int i=0;i<2;i++) {
            var generator=CoreObjectFactory.createAclfGen("g"+i); generator.setGen(new Complex(.1,.02)); bus.getContributeGenList().add(generator);
            var load=CoreObjectFactory.createAclfLoad("l"+i); load.setCode(AclfLoadCode.CONST_P); load.setLoadCP(new Complex(.2,.03)); bus.getContributeLoadList().add(load);
        }
        var model=EcfNetworkModel.compile(network,false);
        assertEquals(new Complex(.2,.04),model.bus(1).generation()); assertEquals(.4,model.bus(1).load().p());
        assertEquals(99,bus.getGenP()); assertEquals(97,bus.getLoadP());
    }
    @Test void rejectsModelsThatWouldOtherwiseBeSilentlyDropped() throws Exception {
        var network = EcfPowerFlowTest.twoBus(Complex.ONE); network.getBus("load-507").setLoadCode(AclfLoadCode.ZIP);
        assertEquals(EcfPowerFlowResult.Status.UNSUPPORTED_MODEL,new EcfPowerFlowAlgorithm().solve(network).status());
        network.getBus("load-507").setLoadCode(AclfLoadCode.CONST_P); network.getBranchList().getFirst().setZ(Complex.ZERO);
        assertEquals(EcfPowerFlowResult.Status.UNSUPPORTED_MODEL,new EcfPowerFlowAlgorithm().solve(network).status());
    }
    @Test void generatorVoltageTargetsAreCapturedWithoutConsolidationAndConflictsAreRejected() throws Exception {
        var network=EcfPowerFlowTest.twoBus(Complex.ONE);var bus=network.getBus("reference-101");
        var first=CoreObjectFactory.createAclfGen("one");first.setDesiredVoltMag(1.06);bus.getContributeGenList().add(first);
        assertEquals(1.06,EcfNetworkModel.compile(network,false).bus(0).desiredMagnitude());
        assertEquals(1,bus.getDesiredVoltMag());
        var second=CoreObjectFactory.createAclfGen("two");second.setDesiredVoltMag(1.04);bus.getContributeGenList().add(second);
        assertEquals(EcfPowerFlowResult.Status.INVALID_INPUT,new EcfPowerFlowAlgorithm().solve(network).status());
        first.setStatus(false);second.setStatus(false);
        assertEquals(EcfPowerFlowResult.Status.INVALID_INPUT,new EcfPowerFlowAlgorithm().solve(network).status());
    }
    @Test void frozenShuntsRemainPhysicalAndAutomaticControlsNeedExplicitPermission() throws Exception {
        var network=EcfPowerFlowTest.twoBus(Complex.ONE);var bus=network.getBus("load-507");
        var shunt=com.interpss.core.AclfAdjustObjectFactory.createSwitchedShunt(bus);
        shunt.setStatus(true);shunt.setControlStatus(true);shunt.setBActual(.04);shunt.setBInit(.04);
        assertEquals(EcfPowerFlowResult.Status.UNSUPPORTED_MODEL,new EcfPowerFlowAlgorithm().solve(network).status());
        var model=EcfNetworkModel.compile(network,true);
        assertEquals(.04,model.currents(new Complex[]{Complex.ONE,Complex.ONE})[1].getImaginary(),1e-12);
        shunt.setControlStatus(false);var fixed=EcfNetworkModel.compile(network,false);
        assertEquals(model.row(1),fixed.row(1));
    }
}
