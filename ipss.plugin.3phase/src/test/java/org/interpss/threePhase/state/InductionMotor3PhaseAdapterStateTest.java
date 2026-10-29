package org.interpss.threePhase.state;

import static org.junit.jupiter.api.Assertions.*;
import com.google.gson.Gson;
import com.interpss.core.aclf.AclfLoadCode;
import com.interpss.dstab.algo.DynamicSimuMethod;
import org.apache.commons.math3.complex.Complex;
import org.interpss.numeric.datatype.Complex3x1;
import org.interpss.threePhase.basic.dstab.DStab3PBus;
import org.interpss.threePhase.dynamic.DStabNetwork3Phase;
import org.interpss.threePhase.dynamic.model.InductionMotor3PhaseAdapter;
import org.interpss.dstab.dynLoad.impl.InductionMotorImpl;
import org.interpss.threePhase.util.ThreePhaseObjectFactory;
import org.junit.jupiter.api.Test;

class InductionMotor3PhaseAdapterStateTest {
    private DStab3PBus bus()throws Exception {
        var net=ThreePhaseObjectFactory.create3PhaseDStabNetwork();net.setBaseKva(100000);net.setFrequency(60);
        var bus=ThreePhaseObjectFactory.create3PDStabBus("B",net);bus.setBaseVoltage(1000);bus.setFreq(1);
        bus.setLoadCode(AclfLoadCode.CONST_P);bus.setInitLoad(new Complex(.6,.2));voltage(bus,1);
        return bus;
    }
    private void voltage(DStab3PBus bus,double magnitude){
        bus.setVoltage(new Complex(magnitude,0));bus.set3PhaseVotlages(new Complex3x1(new Complex(magnitude,0),
            new Complex(-magnitude/2,-magnitude*Math.sqrt(3)/2),new Complex(-magnitude/2,magnitude*Math.sqrt(3)/2)));
    }
    private InductionMotorImpl motor(DStab3PBus bus){
        var motor=new InductionMotorImpl(bus,"M");motor.setXm(3);motor.setXl(.07);motor.setRa(.032);
        motor.setXr1(.2);motor.setRr1(.01);motor.setXr2(0);motor.setRr2(0);motor.setTwoAxisModel(false);
        motor.setMvaBase(50);motor.setH(1);motor.setLoadPercent(50);motor.setRatedVoltage(1000);
        motor.setInitLoadPQ(new Complex(.3,.12));return motor;
    }
    private DStabNetwork3Phase copy(DStabNetwork3Phase source)throws Exception {
        var gson=new Gson();return DStab3PNetworkState.create(gson.fromJson(gson.toJson(new DStab3PNetworkState(source)),DStab3PNetworkState.class));
    }
    @Test void phaseOnlyAdapterPreservesSharedMotorAndWarmTrajectory()throws Exception {verify(false);}
    @Test void genericAdapterBeforeMotorPreservesSharedMotorAndWarmTrajectory()throws Exception {verify(true);}
    private void verify(boolean generic)throws Exception {
        var sourceBus=bus();InductionMotor3PhaseAdapter adapter=null;
        if(generic){adapter=new InductionMotor3PhaseAdapter();adapter.setDStabBus(sourceBus);}
        var motor=motor(sourceBus);assertTrue(motor.initStates());
        if(generic)adapter.setInductionMotor(motor);else adapter=new InductionMotor3PhaseAdapter(motor);
        adapter.setId("adapter");adapter.setDesc("shared motor adapter");adapter.setMVABase(73);
        adapter.setInitLoadPQ3Phase(new Complex3x1(new Complex(.3,.12),new Complex(.3,.12),new Complex(.3,.12)));
        sourceBus.getThreePhaseDynLoadList().add(adapter);sourceBus.getThreePhaseDynLoadList().add(adapter);
        adapter.getYabc(true);adapter.getEquivYabc();adapter.nextStep(.001,DynamicSimuMethod.MODIFIED_EULER,0);
        var source=(DStabNetwork3Phase)sourceBus.getNetwork();assertTrue(DStab3PNetworkState.supports(source));
        var target=copy(source);var targetBus=target.getBus("B");
        var copied=(InductionMotor3PhaseAdapter)targetBus.getThreePhaseDynLoadList().get(0);
        assertSame(copied,targetBus.getThreePhaseDynLoadList().get(1));assertNotSame(adapter,copied);
        assertSame(targetBus,copied.getInductionMotor().getDStabBus());assertSame(copied.getInductionMotor(),targetBus.getDynLoadModelList().get(0));
        assertEquals(generic,targetBus.getDynamicBusDeviceList().contains(copied));
        assertEquals(generic,targetBus==copied.getDStabBus());assertSame(targetBus,copied.getParentBus());
        assertEquals("shared motor adapter",copied.getDesc());
        var originalState=new InductionMotor3PhaseAdapter.ModelState(adapter);var copiedState=new InductionMotor3PhaseAdapter.ModelState(copied);
        assertEquals(73,copiedState.baseMva);assertEquals(originalState.loadPercentage,copiedState.loadPercentage);
        for(int i=0;i<120;i++){
            double v=i>20&&i<55?.75:1;voltage(sourceBus,v);voltage(targetBus,v);
            if(i>0){adapter.nextStep(.001,DynamicSimuMethod.MODIFIED_EULER,0);copied.nextStep(.001,DynamicSimuMethod.MODIFIED_EULER,0);}
            adapter.nextStep(.001,DynamicSimuMethod.MODIFIED_EULER,1);copied.nextStep(.001,DynamicSimuMethod.MODIFIED_EULER,1);
            adapter.afterStep(.001);copied.afterStep(.001);
            assertEquals(motor.getSlip(),copied.getInductionMotor().getSlip(),1e-12);
            assertEquals(0,adapter.getISource3Phase().subtract(copied.getISource3Phase()).absMax(),1e-12);
            assertEquals(0,adapter.getIinj2Network3Phase().subtract(copied.getIinj2Network3Phase()).absMax(),1e-12);
        }
        var again=copy(target);assertNotSame(copied,again.getBus("B").getThreePhaseDynLoadList().get(0));
        copied.getYabc(true).aa=Complex.ZERO;assertNotEquals(Complex.ZERO,adapter.getYabc(true).aa);
        copied.getInitLoadPQ3Phase().a_0=Complex.ZERO;assertNotEquals(Complex.ZERO,adapter.getInitLoadPQ3Phase().a_0);
        source.removeBus("B");assertSame(targetBus,target.getBus("B"));
    }
    @Test void normalizationIsIdempotentAndKeepsEachPhysicalMotorOnce()throws Exception {
        var bus=bus();var first=motor(bus);var second=motor(bus);second.setId("M2");
        var a=new InductionMotor3PhaseAdapter(first);var b=new InductionMotor3PhaseAdapter(second);
        a.setDStabBus(bus);bus.getThreePhaseDynLoadList().add(b);bus.getThreePhaseDynLoadList().add(b);
        org.interpss.threePhase.util.ThreePhaseUtilFunction.normalizeInductionMotorAdapters(bus);
        org.interpss.threePhase.util.ThreePhaseUtilFunction.normalizeInductionMotorAdapters(bus);
        assertEquals(2,bus.getDynamicBusDeviceList().size());assertTrue(bus.getDynamicBusDeviceList().contains(first));
        assertTrue(bus.getDynamicBusDeviceList().contains(second));assertFalse(bus.getDynamicBusDeviceList().contains(a));
        assertSame(a,org.interpss.threePhase.util.ThreePhaseUtilFunction.threePhaseInductionMotorAptr.apply(first));
        assertSame(b,org.interpss.threePhase.util.ThreePhaseUtilFunction.threePhaseInductionMotorAptr.apply(second));
        assertSame(bus,a.getParentBus());assertNull(a.getDStabBus());
    }
    @Test void conflictingExecutionViewsRejectBeforeContainmentMutation()throws Exception {
        var bus=bus();var motor=motor(bus);var first=new InductionMotor3PhaseAdapter(motor);
        var second=new InductionMotor3PhaseAdapter(motor);first.setDStabBus(bus);
        bus.getThreePhaseDynLoadList().add(second);
        assertThrows(IllegalArgumentException.class,()->org.interpss.threePhase.util.ThreePhaseUtilFunction.normalizeInductionMotorAdapters(bus));
        assertSame(bus,first.getDStabBus());assertTrue(bus.getDynamicBusDeviceList().contains(first));
        bus.getThreePhaseDynLoadList().clear();motor.setStatus(false);first.setStatus(false);
        org.interpss.threePhase.util.ThreePhaseUtilFunction.normalizeInductionMotorAdapters(bus);
        assertFalse(org.interpss.threePhase.util.ThreePhaseUtilFunction.threePhaseInductionMotorAptr.apply(motor).isActive());
        var bare=motor(bus);bare.setStatus(false);assertFalse(new InductionMotor3PhaseAdapter(bare).isActive());
    }
    @Test void conflictingOwnersAndUnsupportedAdaptersReject()throws Exception {
        var a=bus();var motor=motor(a);var adapter=new InductionMotor3PhaseAdapter(motor);adapter.setDStabBus(bus());
        assertThrows(IllegalArgumentException.class,()->new InductionMotor3PhaseAdapter.ModelState(adapter));
        assertThrows(IllegalArgumentException.class,()->new InductionMotor3PhaseAdapter.ModelState(new InductionMotor3PhaseAdapter(motor){}));
    }
}
