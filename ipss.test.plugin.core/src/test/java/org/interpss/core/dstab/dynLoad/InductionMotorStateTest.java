package org.interpss.core.dstab.dynLoad;

import static org.junit.jupiter.api.Assertions.*;
import java.lang.reflect.Modifier;
import org.apache.commons.math3.complex.Complex;
import org.interpss.dstab.dynLoad.impl.InductionMotorImpl;
import org.junit.jupiter.api.Test;
import com.google.gson.Gson;
import com.interpss.dstab.*;
import com.interpss.dstab.algo.DynamicSimuMethod;

class InductionMotorStateTest {
    private DStabBus bus() throws Exception {
        var net=DStabObjectFactory.createDStabilityNetwork();net.setBaseKva(100000);net.setFrequency(60);
        var bus=(DStabBus)DStabObjectFactory.createDStabBus("B",net).get();
        bus.setLoadCode(com.interpss.core.aclf.AclfLoadCode.CONST_P);bus.setBaseVoltage(1000);bus.setVoltage(Complex.ONE);bus.setInitLoad(new Complex(.6,.2));
        return bus;
    }
    private InductionMotorImpl motor(DStabBus bus,boolean twoCage){
        var motor=new InductionMotorImpl(bus,"M");motor.setXm(3);motor.setXl(.07);motor.setRa(.032);
        motor.setXr1(.2);motor.setRr1(.01);motor.setXr2(twoCage?.15:0);motor.setRr2(twoCage?.02:0);
        motor.setTwoAxisModel(twoCage);motor.setMvaBase(50);motor.setH(1);motor.setLoadPercent(50);
        motor.setRatedVoltage(1000);motor.setInitLoadPQ(new Complex(.3,.12));return motor;
    }
    private InductionMotorImpl copy(InductionMotorImpl source,DStabBus target){
        var gson=new Gson();return gson.fromJson(gson.toJson(new InductionMotorImpl.ModelState(source)),
            InductionMotorImpl.ModelState.class).create(target);
    }
    @Test void configuredCopyIsIndependent()throws Exception {
        var source=motor(bus(),false);
        var shadow=com.interpss.dstab.dynLoad.impl.DynLoadModelImpl.class.getDeclaredField("loadFactor");
        shadow.setAccessible(true);shadow.setDouble(source,.37);
        var targetBus=bus();var target=copy(source,targetBus);assertEquals(.37,shadow.getDouble(target));
        assertSame(targetBus,target.getDStabBus());assertNotSame(source.getDStabBus(),target.getDStabBus());
        assertTrue(Double.isNaN(target.getTorqueExponent()));assertTrue(source.initStates());assertTrue(target.initStates());
        assertEquals(0,source.getLoadPQ().subtract(target.getLoadPQ()).abs(),1e-12);
        target.setH(9);assertEquals(1,source.getH());
    }
    @Test void warmSingleCageContinuation()throws Exception {continuation(false);}
    @Test void warmTwoCageContinuation()throws Exception {continuation(true);}
    private void continuation(boolean twoCage)throws Exception {
        var source=motor(bus(),twoCage);
        source.setVtr1(.8);source.setTtr1(.005);source.setFtr1(.2);source.setVrc1(.9);source.setTrc1(.005);
        assertTrue(source.initStates());
        source.nextStep(.001,DynamicSimuMethod.MODIFIED_EULER,0);source.getStates(null);
        var target=copy(source,bus());boolean tripped=false;
        for(int i=0;i<150;i++){
            double voltage=i>15&&i<60?.7:1;
            source.getDStabBus().setVoltage(new Complex(voltage,0));target.getDStabBus().setVoltage(new Complex(voltage,0));
            if(i>0){source.nextStep(.001,DynamicSimuMethod.MODIFIED_EULER,0);target.nextStep(.001,DynamicSimuMethod.MODIFIED_EULER,0);}
            source.nextStep(.001,DynamicSimuMethod.MODIFIED_EULER,1);target.nextStep(.001,DynamicSimuMethod.MODIFIED_EULER,1);
            source.afterStep(.001);target.afterStep(.001);
            assertEquals(source.getSlip(),target.getSlip(),1e-12);
            assertEquals(source.getFuv(),target.getFuv(),1e-12);tripped|=source.getFuv()<.99;
            assertEquals(0,source.getLoadPQ().subtract(target.getLoadPQ()).abs(),1e-12);
            assertEquals(source.getStates(null),target.getStates(null));
            if(i==40){assertTrue(source.getFuv()<.99);var rebound=bus();rebound.setVoltage(source.getDStabBus().getVoltage());target=copy(source,rebound);assertNotSame(source,target);}
        }
        assertTrue(tripped);assertEquals(1,source.getFuv(),1e-12);
        target.getStates(null).put("isolated",true);assertFalse(source.getStates(null).containsKey("isolated"));
    }
    @Test void fullNetworkCopiesPreserveDeviceIdentityAndListMembership()throws Exception {
        var originalBus=bus();var source=motor(originalBus,false);assertTrue(source.initStates());
        var network=(DStabilityNetwork)originalBus.getNetwork();
        assertTrue(com.interpss.state.dstab.DStabNetworkState.supports(network));
        var gson=new Gson();var frozen=gson.toJson(new com.interpss.state.dstab.DStabNetworkState(network));
        var copy=com.interpss.state.dstab.DStabNetworkState.create(gson.fromJson(frozen,com.interpss.state.dstab.DStabNetworkState.class));
        var bus=copy.getBus("B");var motor=(InductionMotorImpl)bus.getDynamicBusDeviceList().get(0);
        assertSame(bus,motor.getDStabBus());assertSame(motor,bus.getDynLoadModelList().get(0));assertNotSame(source,motor);
        assertEquals(source.getSlip(),motor.getSlip(),1e-12);
        motor.setH(9);assertEquals(1,source.getH());
        var repeated=com.interpss.state.dstab.DStabNetworkState.create(gson.fromJson(gson.toJson(new com.interpss.state.dstab.DStabNetworkState(copy)),com.interpss.state.dstab.DStabNetworkState.class));
        assertNotSame(motor,repeated.getBus("B").getDynamicBusDeviceList().get(0));
        assertSame(repeated.getBus("B").getDynamicBusDeviceList().get(0),repeated.getBus("B").getDynLoadModelList().get(0));
    }
    @Test void unknownCacheObjectsAndSubclassesAreRejected()throws Exception {
        var source=motor(bus(),false);assertTrue(source.initStates());source.getStates(null).put("foreign",source.getDStabBus());
        assertThrows(IllegalArgumentException.class,()->new InductionMotorImpl.ModelState(source));
        assertThrows(IllegalArgumentException.class,()->new InductionMotorImpl.ModelState(new InductionMotorImpl(){}));
        var protectedMotor=motor(bus(),false);protectedMotor.getProtectionControlList().add(null);
        assertThrows(IllegalArgumentException.class,()->new InductionMotorImpl.ModelState(protectedMotor));
    }
    @Test void everyMotorValueHasExplicitCompanionField()throws Exception {
        for(Class<?> type=InductionMotorImpl.class;type!=null;type=type.getSuperclass()){
            if(type.getPackageName().startsWith("org.eclipse"))break;
            for(var field:type.getDeclaredFields())if(!Modifier.isStatic(field.getModifiers())&&
                (field.getType().isPrimitive()||field.getType()==Complex.class)){
                if(type.getSimpleName().equals("NameTagImpl"))continue;
                assertNotNull(InductionMotorImpl.ModelState.class.getField(field.getName()),type+"."+field.getName());
            }
        }
    }
}
