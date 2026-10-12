package org.interpss.threePhase.state;

import com.google.gson.Gson;
import com.interpss.core.aclf.AclfLoadCode;
import com.interpss.core.acsc.PhaseCode;
import com.interpss.dstab.GeneratorType;
import org.apache.commons.math3.complex.Complex;
import org.interpss.numeric.datatype.Complex3x1;
import org.interpss.numeric.datatype.Unit.UnitType;
import org.interpss.threePhase.basic.dstab.impl.DStab1PLoadImpl;
import org.interpss.threePhase.util.ThreePhaseObjectFactory;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class DStab3PBusStateTest {
    @Test void warmFrequencyBlockIsCopiedWithoutReinitialization(){
        var net=ThreePhaseObjectFactory.create3PhaseDStabNetwork();
        var bus=ThreePhaseObjectFactory.create3PDStabBus("B",net);bus.setBaseVoltage(12470);
        var block=new com.interpss.dstab.controller.deqn.measure.BusFreqMeasurementImpl(.15,.04);
        block.setParameter(com.interpss.common.datatype.Constants.Token_NetBaseFreq,50);
        bus.setBusFreqMeasureBlock(block);block.initStateU0(.1);block.eulerStep1(.103,.005);
        var gson=new Gson();var json=gson.toJson(new DStab3PBusState(bus));
        var copy=DStab3PBusState.create(gson.fromJson(json,DStab3PBusState.class));
        var restored=(com.interpss.dstab.controller.deqn.measure.BusFreqMeasurementImpl)copy.getBusFreqMeasureBlock();
        assertNotSame(block,restored);assertEquals(.15,restored.getTf());assertEquals(block.getY(),restored.getY(),1e-12);
        block.eulerStep2(.104,.005);restored.eulerStep2(.104,.005);assertEquals(block.getY(),restored.getY(),1e-12);
        for(int i=0;i<100;i++){double angle=.104+i*.002;block.eulerStep1(angle,.005);restored.eulerStep1(angle,.005);
            block.eulerStep2(angle,.005);restored.eulerStep2(angle,.005);assertEquals(block.getY(),restored.getY(),1e-12);}
        bus.setBusFreqMeasureBlock(null);assertNull(DStab3PBusState.create(new DStab3PBusState(bus)).getBusFreqMeasureBlock());
        bus.setBusFreqMeasureBlock(new com.interpss.dstab.controller.deqn.measure.BusFreqMeasurementImpl(){});
        assertThrows(IllegalArgumentException.class,()->new DStab3PBusState(bus));
    }

    @Test @SuppressWarnings({"rawtypes","unchecked"})
    void jsonCopyPreservesSharedDeviceIdentityAndElectricalResponse(){
        var net=ThreePhaseObjectFactory.create3PhaseDStabNetwork();
        var bus=ThreePhaseObjectFactory.create3PDStabBus("B1",net);
        bus.setBaseVoltage(12470);bus.setFreq(1.01);
        var volts=new Complex3x1(new Complex(.95,0),new Complex(-.475,-.8227),new Complex(-.475,.8227));
        bus.set3PhaseVotlages(volts);bus.setThreePhaseInitVoltage(volts);
        bus.set3PhaseNetLoadResults(new Complex3x1(new Complex(.1,.02),new Complex(.2,.03),new Complex(.3,.04)));
        var single=new DStab1PLoadImpl();single.setId("S1");single.setPhaseCode(PhaseCode.A);single.setCode(AclfLoadCode.CONST_P);single.setLoadCP(new Complex(.15,.03));single.setParentBus(bus);
        bus.getSinglePhaseLoadList().add(single);((java.util.List)bus.getContributeLoadList()).add(single);
        var load=ThreePhaseObjectFactory.create3PLoad("L1");load.setCode(AclfLoadCode.CONST_P);load.set3PhaseLoad(new Complex3x1(new Complex(.2,.03),new Complex(.3,.04),new Complex(.4,.05)));load.setParentBus(bus);
        bus.getThreePhaseLoadList().add(load);bus.getContributeLoadList().add(load);
        var gen=ThreePhaseObjectFactory.create3PGenerator("G1");gen.setGenType(GeneratorType.SYNCH_MACHINE);gen.setParentBus(bus);
        gen.setPosGenZ(new Complex(.01,.2));gen.setNegGenZ(new Complex(.02,.25));gen.setZeroGenZ(new Complex(.03,.3));
        gen.setPower3Phase(new Complex3x1(new Complex(.1,.02),new Complex(.2,.03),new Complex(.3,.04)),UnitType.PU);
        bus.getThreePhaseGenList().add(gen);bus.getContributeGenList().add(gen);
        var loadResponse=load.getEquivCurrInj(volts);var genResponse=gen.getPowerflowEquivCurrInj();
        var gson=new Gson();var json=gson.toJson(new DStab3PBusState(bus));
        volts.a_0=new Complex(99,99);single.setLoadCP(new Complex(99,99));gen.getPower3Phase(UnitType.PU).a_0=new Complex(99,99);
        var state=gson.fromJson(json,DStab3PBusState.class);var copied=DStab3PBusState.create(state);
        assertNotSame(bus,copied);assertEquals(12470,copied.getBaseVoltage());assertEquals(1.01,copied.getFreq());
        assertEquals(new Complex(.95,0),copied.get3PhaseVotlages().a_0);assertEquals(new Complex(.95,0),copied.get3PhaseInitVoltage().a_0);
        assertEquals(new Complex(.3,.04),copied.get3PhaseNetLoadResults().c_2);
        var copiedLoad=copied.getThreePhaseLoadList().getFirst();var copiedGen=copied.getThreePhaseGenList().getFirst();
        assertSame(copiedLoad,copied.getContributeLoad("L1"));assertSame(copiedGen,copied.getContributeGen("G1"));
        assertSame(copied.getSinglePhaseLoadList().getFirst(),copied.getContributeLoad("S1"));
        assertSame(copied,copiedLoad.getParentBus());assertSame(copied,copiedGen.getParentBus());
        assertEquals(new Complex(.15,.03),copied.getSinglePhaseLoadList().getFirst().getLoadCP());
        assertEquals(0,loadResponse.a_0.subtract(copiedLoad.getEquivCurrInj(copied.get3PhaseVotlages()).a_0).abs(),1e-12);
        assertEquals(0,genResponse.a_0.subtract(copiedGen.getPowerflowEquivCurrInj().a_0).abs(),1e-12);
        copied.get3PhaseVotlages().a_0=Complex.ZERO;
        assertEquals(new Complex(.95,0),DStab3PBusState.create(state).get3PhaseVotlages().a_0);
    }
    @Test void refusesUncopiedMotorState(){
        var bus=ThreePhaseObjectFactory.create3PDStabBus("B1",ThreePhaseObjectFactory.create3PhaseDStabNetwork());
        bus.getPhaseADynLoadList().add(new org.interpss.threePhase.dynamic.model.impl.SinglePhaseACMotor(bus,"M1"));
        assertThrows(IllegalArgumentException.class,()->new DStab3PBusState(bus));
    }
}
