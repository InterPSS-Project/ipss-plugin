package org.interpss.threePhase.state;
import static org.junit.jupiter.api.Assertions.*;
import java.util.List;
import com.google.gson.Gson;
import com.interpss.core.aclf.AclfLoadCode;
import com.interpss.core.acsc.PhaseCode;
import com.interpss.dstab.algo.DynamicSimuMethod;
import org.apache.commons.math3.complex.Complex;
import org.interpss.numeric.datatype.Complex3x1;
import org.interpss.threePhase.basic.dstab.DStab3PBus;
import org.interpss.threePhase.dynamic.model.impl.SinglePhaseACMotor;
import org.interpss.threePhase.util.ThreePhaseObjectFactory;
import org.junit.jupiter.api.Test;
class SinglePhaseACMotorStateTest {
    private DStab3PBus bus()throws Exception {
        var net=ThreePhaseObjectFactory.create3PhaseDStabNetwork();net.setBaseKva(100000);net.setFrequency(60);
        var bus=ThreePhaseObjectFactory.create3PDStabBus("B",net);bus.setBaseVoltage(12470);bus.setFreq(1);
        voltage(bus,1);bus.setThreePhaseInitVoltage(bus.get3PhaseVotlages());
        var load=ThreePhaseObjectFactory.create3PLoad("L");bus.setLoadCode(AclfLoadCode.CONST_P);load.set3PhaseLoad(new Complex3x1(new Complex(1,.2),new Complex(1,.2),new Complex(1,.2)));bus.getThreePhaseLoadList().add(load);
        return bus;
    }
    private void voltage(DStab3PBus bus,double magnitude){bus.set3PhaseVotlages(new Complex3x1(new Complex(magnitude,0),new Complex(-magnitude/2,-magnitude*Math.sqrt(3)/2),new Complex(-magnitude/2,magnitude*Math.sqrt(3)/2)));}
    private SinglePhaseACMotor configured(DStab3PBus bus,PhaseCode phase) {
        var motor=new SinglePhaseACMotor(bus,"M");motor.setPhase(phase);motor.setLoadPercent(50);motor.setMvaBase(20);
        motor.setFrst(.4);motor.setTrst(.05);motor.setTstall(.02);return motor;
    }
    private SinglePhaseACMotor copy(SinglePhaseACMotor source,DStab3PBus target) {
        var gson=new Gson();return gson.fromJson(gson.toJson(new SinglePhaseACMotor.ModelState(source)),SinglePhaseACMotor.ModelState.class).create(target);
    }
    @Test void configuredCopyRebindsIdentityAndIndependentParameters()throws Exception {
        var original=bus();var source=configured(original,PhaseCode.B);source.setDesc("original motor");
        var targetBus=bus();var target=copy(source,targetBus);
        assertNotSame(source,target);assertSame(targetBus,target.getDStabBus());assertSame(targetBus,target.getParentBus());
        assertEquals(PhaseCode.B,target.getPhase());assertEquals(50,target.getLoadPercent());assertEquals("original motor",target.getDesc());
        source.setLoadPercent(99);assertEquals(50,target.getLoadPercent());assertTrue(target.initStates());
        assertEquals(.5,target.getInitLoadPQ().getReal(),1e-12);assertTrue(target.getPac()>0);assertEquals(0,source.getPac());
    }
    @Test void predictorCopiesRetainProtectionTimersAndMotorTrajectoryForEveryPhase()throws Exception {
        for(var phase:List.of(PhaseCode.A,PhaseCode.B,PhaseCode.C)) {
            var sourceBus=bus();var targetBus=bus();var source=configured(sourceBus,phase);assertTrue(source.initStates());
            source.getStates(null);voltage(sourceBus,.45);source.nextStep(.005,DynamicSimuMethod.MODIFIED_EULER,0);
            var target=copy(source,targetBus);voltage(targetBus,.45);
            source.nextStep(.005,DynamicSimuMethod.MODIFIED_EULER,1);target.nextStep(.005,DynamicSimuMethod.MODIFIED_EULER,1);
            source.afterStep(.005);target.afterStep(.005);
            boolean stalled=false,restarted=false;
            for(int i=0;i<160;i++) {
                double v=i<40?.45:1;voltage(sourceBus,v);voltage(targetBus,v);
                for(int flag=0;flag<2;flag++){source.nextStep(.005,DynamicSimuMethod.MODIFIED_EULER,flag);target.nextStep(.005,DynamicSimuMethod.MODIFIED_EULER,flag);}
                source.afterStep(.005);target.afterStep(.005);
                assertEquals(source.getStage(),target.getStage());assertEquals(source.getPac(),target.getPac(),1e-12);assertEquals(source.getQac(),target.getQac(),1e-12);
                assertEquals(0,source.getNortonCurInj().subtract(target.getNortonCurInj()).abs(),1e-12);
                assertEquals(source.getStates(null),target.getStates(null));stalled|=target.getStage()==0;restarted|=target.getStage()==2;
                if(i==30)target=copy(target,targetBus); // preserve an already stalled motor and its timers
            }
            assertTrue(stalled,"Voltage dip must stall the motor");assertTrue(restarted,"Recovery must restart the configured motor fraction");
            var frozen=new SinglePhaseACMotor.ModelState(target);source.changeLoad(-.2);assertEquals(frozen.pac,new SinglePhaseACMotor.ModelState(target).pac);
            var again=frozen.create(bus());assertNotSame(target.getStates(null),again.getStates(null));
        }
    }
    @Test void companionCoversEveryConcreteAndInheritedMotorValueField()throws Exception {
        for(var model:List.of(SinglePhaseACMotor.class,org.interpss.threePhase.dynamic.model.DynLoadModel1Phase.class)) {
            for(var field:model.getDeclaredFields()) {
                if(java.lang.reflect.Modifier.isStatic(field.getModifiers()))continue;
                if(field.getType().isPrimitive()||field.getType()==Complex.class)
                    assertNotNull(SinglePhaseACMotor.ModelState.class.getField(field.getName()),"Missing copied motor value: "+field.getName());
                else assertTrue(List.of("bus","states").contains(field.getName()),"New motor reference requires explicit copy handling: "+field.getName());
            }
        }
    }
    @Test void fullNetworkCopyPreservesThreePhaseMotorIdentitiesAndIndependentGraphs()throws Exception {
        var sourceBus=bus();
        for(var phase:List.of(PhaseCode.A,PhaseCode.B,PhaseCode.C)) {
            var motor=configured(sourceBus,phase);
            switch(phase){case A->sourceBus.getPhaseADynLoadList().add(motor);case B->sourceBus.getPhaseBDynLoadList().add(motor);case C->sourceBus.getPhaseCDynLoadList().add(motor);default->throw new AssertionError();}
            assertTrue(motor.initStates());
        }
        var source=(org.interpss.threePhase.dynamic.DStabNetwork3Phase)sourceBus.getNetwork();
        var gson=new Gson();var json=gson.toJson(new DStab3PNetworkState(source));
        var restored=DStab3PNetworkState.create(gson.fromJson(json,DStab3PNetworkState.class));var targetBus=restored.getBus("B");
        assertEquals(3,targetBus.getDynamicBusDeviceList().size());
        var sourceMotors=List.of(sourceBus.getPhaseADynLoadList().getFirst(),sourceBus.getPhaseBDynLoadList().getFirst(),sourceBus.getPhaseCDynLoadList().getFirst());
        var targetMotors=List.of(targetBus.getPhaseADynLoadList().getFirst(),targetBus.getPhaseBDynLoadList().getFirst(),targetBus.getPhaseCDynLoadList().getFirst());
        for(int i=0;i<3;i++) {
            assertNotSame(sourceMotors.get(i),targetMotors.get(i));assertSame(targetBus,targetMotors.get(i).getDStabBus());
            assertSame(targetMotors.get(i),targetBus.getDynamicBusDeviceList().get(i));assertEquals("M",targetMotors.get(i).getId());
        }
        for(int i=0;i<120;i++) {
            double v=i<40?.45:1;voltage(sourceBus,v);voltage(targetBus,v);
            for(int m=0;m<3;m++) {
                var original=(SinglePhaseACMotor)sourceMotors.get(m);var copied=(SinglePhaseACMotor)targetMotors.get(m);
                for(int flag=0;flag<2;flag++){original.nextStep(.005,DynamicSimuMethod.MODIFIED_EULER,flag);copied.nextStep(.005,DynamicSimuMethod.MODIFIED_EULER,flag);}
                original.afterStep(.005);copied.afterStep(.005);
                assertEquals(original.getStage(),copied.getStage());assertEquals(original.getPac(),copied.getPac(),1e-12);
                assertEquals(0,original.getNortonCurInj().subtract(copied.getNortonCurInj()).abs(),1e-12);
            }
        }
        source.removeBus("B");assertSame(targetBus,restored.getBus("B"));
        var again=DStab3PNetworkState.create(gson.fromJson(json,DStab3PNetworkState.class));
        assertEquals(1,again.getNoBus());assertNotSame(targetMotors.getFirst(),again.getBus("B").getPhaseADynLoadList().getFirst());
        assertEquals(1,((SinglePhaseACMotor)again.getBus("B").getPhaseADynLoadList().getFirst()).getStage());
    }
    @Test void incorrectPhaseMembershipAndUnknownMotorImplementationsAreRejected()throws Exception {
        var bus=bus();var motor=configured(bus,PhaseCode.B);bus.getPhaseADynLoadList().add(motor);
        assertThrows(IllegalArgumentException.class,()->new DStab3PBusState(bus));
        bus.getPhaseADynLoadList().clear();bus.getDynamicBusDeviceList().clear();
        var unknown=new SinglePhaseACMotor(bus,"unsupported"){};unknown.setPhase(PhaseCode.A);bus.getPhaseADynLoadList().add(unknown);
        assertFalse(DStab3PNetworkState.supports((org.interpss.threePhase.dynamic.DStabNetwork3Phase)bus.getNetwork()));
    }
    @Test void unknownSubclassAndMutableOutputCacheAreRejected()throws Exception {
        assertThrows(IllegalArgumentException.class,()->new SinglePhaseACMotor.ModelState(new SinglePhaseACMotor(){}));
        var motor=configured(bus(),PhaseCode.A);assertTrue(motor.initStates());motor.getStates(null).put("unsafe",new Object());
        assertThrows(IllegalArgumentException.class,()->new SinglePhaseACMotor.ModelState(motor));
    }
}
