package org.interpss.core.dstab;

import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.apache.commons.math3.complex.Complex;
import org.interpss.dstab.relay.*;
import com.interpss.dstab.DStabObjectFactory;
import com.interpss.dstab.algo.defaultImpl.DynamicEventProcessor;
import com.interpss.core.acsc.fault.SimpleFaultCode;

/** Protection trips must preserve the accepted load model and every still-active fault. */
class GeneratorTripRelayTopologyTest {
    @ParameterizedTest @ValueSource(doubles={1.,.7})
    void relayTripPreservesConvertedLoadAndActiveFaultAtDisturbedVoltage(double voltage)throws Exception {
        var net=com.interpss.simu.util.sample.SampleDStabTestingCases.sample3BusDStab_StaticLoads();
        var target=net.getDStabBus("Bus-3");var monitored=net.getDStabBus("Bus-1");
        target.setLoadCode(com.interpss.core.aclf.AclfLoadCode.CONST_P);target.setLoadP(.4);target.setLoadQ(.1);
        target.setShuntY(new Complex(.02,.08));
        assertTrue(com.interpss.core.LoadflowAlgoObjectFactory.createLoadflowAlgorithm(net).loadflow());
        net.setStaticLoadIncludedInYMatrix(true);
        var fault=DStabObjectFactory.createBusFaultEvent("Bus-1",net,SimpleFaultCode.GROUND_3P,
            new Complex(0.,.2),null,.1,.4);
        assertTrue(fault.hasEventAt(.1));
        new DynamicEventProcessor().rebuildCurrentTopology(net);
        var faultDiagonal=net.getYMatrix().getA(monitored.getSortNumber(),monitored.getSortNumber());
        var before=net.getYMatrix().getA(target.getSortNumber(),target.getSortNumber());
        var generator=(com.interpss.dstab.DStabGen)target.getContributeGen("G1");var machine=generator.getMach();
        var expected=before.subtract(machine.getYgen()).add(Complex.ONE.divide(org.interpss.numeric.NumericConstant.LargeBusZ));
        target.setVoltage(voltage,.2);
        var relay=new VtgtpatRelayModel(901,monitored,target,generator,new GeneratorTripRelayData(.8,1.2,.02,0.));
        assertTrue(relay.initStates(monitored));assertTrue(relay.action(.138));
        assertTrue(relay.isTripped());assertTrue(relay.didDisconnectTarget());assertEquals(.138,relay.getActionTime(),1e-14);
        assertFalse(generator.isActive());assertFalse(machine.isActive());assertTrue(fault.isActive());
        assertEquals(0.,net.getYMatrix().getA(monitored.getSortNumber(),monitored.getSortNumber()).subtract(faultDiagonal).abs(),1e-12,
            "An endogenous trip cannot clear an unrelated active fault");
        assertEquals(0.,net.getYMatrix().getA(target.getSortNumber(),target.getSortNumber()).subtract(expected).abs(),1e-12,
            "Trip changes only the generator stamp, never reconverts colocated load at disturbed voltage");
    }

    @Test void anotherRelayOnAnAlreadyTrippedTargetDoesNotRebuildOrRepeatThePhysicalAction()throws Exception {
        var net=com.interpss.simu.util.sample.SampleDStabTestingCases.sample3BusDStab_StaticLoads();
        assertTrue(com.interpss.core.LoadflowAlgoObjectFactory.createLoadflowAlgorithm(net).loadflow());
        net.setStaticLoadIncludedInYMatrix(true);net.formYMatrix4DStab();
        var bus=net.getDStabBus("Bus-3");var generator=(com.interpss.dstab.DStabGen)bus.getContributeGen("G1");
        var first=new VtgtpatRelayModel(902,bus,bus,generator,new GeneratorTripRelayData(.8,1.2,.02,0.));
        var second=new VtgtpatRelayModel(903,bus,bus,generator,new GeneratorTripRelayData(.7,1.3,.03,0.));
        assertTrue(first.initStates(bus));assertTrue(second.initStates(bus));assertTrue(first.action(.1));
        var acceptedMatrix=net.getYMatrix();
        assertTrue(second.action(.12));assertTrue(second.isTripped());assertFalse(second.didDisconnectTarget());assertTrue(first.didDisconnectTarget());assertEquals(.12,second.getActionTime(),1e-14);
        assertSame(acceptedMatrix,net.getYMatrix(),"Each relay keeps its own operation history; target disconnection occurs once");
        assertTrue(first.action(.2));assertSame(acceptedMatrix,net.getYMatrix());assertEquals(.1,first.getActionTime(),1e-14);
    }
}
