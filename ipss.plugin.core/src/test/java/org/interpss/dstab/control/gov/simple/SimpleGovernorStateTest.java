package org.interpss.dstab.control.gov.simple;

import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Test;
import com.google.gson.Gson;
import org.apache.commons.math3.complex.Complex;
import com.interpss.dstab.*;
import com.interpss.dstab.mach.*;
import com.interpss.dstab.algo.DynamicSimuMethod;
import com.interpss.dstab.controller.cml.field.block.DelayControlBlock;
import com.interpss.dstab.controller.cml.field.block.GainBlock;
import com.interpss.state.dstab.DStabNetworkState;
import org.interpss.dstab.control.exc.simple.SimpleExciter;

class SimpleGovernorStateTest {
    private DStabilityNetwork network()throws Exception {
        var net=DStabObjectFactory.createDStabilityNetwork();net.setBaseKva(100000);net.setFrequency(60);
        var bus=(DStabBus)DStabObjectFactory.createDStabBus("B",net).orElseThrow();bus.setBaseVoltage(1000);bus.setVoltage(Complex.ONE);
        var gen=DStabObjectFactory.createDStabGen("G");bus.getContributeGenList().add(gen);
        gen.setPosGenZ(new Complex(0,.2));gen.setNegGenZ(new Complex(0,.2));gen.setZeroGenZ(new Complex(0,.2));
        var mach=DStabObjectFactory.createMachine("M","machine",MachineModelType.ECONSTANT,net,"B","G");
        mach.setRating(100,org.interpss.numeric.datatype.Unit.UnitType.mVA,net.getBaseKva());mach.setRatedVoltage(1000);mach.calMultiFactors();
        mach.setEfd(1);mach.setSpeed(1);mach.setPm(.8);
        var governor=new SimpleGovernor("GV","Governor","test");governor.setMachine(mach);
        governor.getData().setK(12);governor.getData().setT1(.2);governor.getData().setPmax(1.1);governor.getData().setPmin(.2);
        return net;
    }
    private Object block(SimpleGovernor gov,String name) {
        return gov.getFieldWrapperList().stream().filter(w->w.getFieldName().equals(name)).findFirst().orElseThrow().getField();
    }
    private DStabilityNetwork copy(String frozen)throws Exception {
        return DStabNetworkState.create(new Gson().fromJson(frozen,DStabNetworkState.class));
    }
    @Test void configuredGovernorAndExciterAttachToCopiedMachine()throws Exception {
        var source=network();var mach=source.getMachine("M");var gov=(SimpleGovernor)mach.getGovernor();
        var exc=new SimpleExciter("EX","Exciter","test");exc.setMachine(mach);
        gov.setStatus(false);gov.setDesc("original governor");gov.setRefPoint(.9);
        var frozen=new Gson().toJson(new DStabNetworkState(source));gov.getData().setK(999);gov.setRefPoint(99);
        var restored=copy(frozen);var target=(SimpleGovernor)restored.getMachine("M").getGovernor();
        assertNotSame(gov,target);assertNotSame(gov.getData(),target.getData());
        assertSame(restored.getMachine("M"),target.getMachine());assertSame(restored.getBus("B"),target.getDStabBus());
        assertSame(restored.getMachine("M"),restored.getMachine("M").getExciter().getMachine());
        assertEquals(2,restored.getMachine("M").getControllerList().size());
        assertEquals(12,target.getData().getK());assertEquals(.9,target.getRefPoint());assertFalse(target.isStatus());
        assertEquals("original governor",target.getDesc());assertNull(target.getFieldWrapperList());
    }
    @Test void initializedGovernorContinuesMidStepAndKeepsRunningParametersAndAliases()throws Exception {
        var source=network();var mach=source.getMachine("M");var gov=(SimpleGovernor)mach.getGovernor();
        assertTrue(gov.initStates(mach.getDStabBus(),mach));
        gov.delayBlock=(DelayControlBlock)block(gov,"delayBlock");gov.gainBlock=(GainBlock)block(gov,"gainBlock");
        gov.getData().setK(13);gov.ka=14;mach.setSpeed(1.03);
        assertTrue(gov.nextStep(.01,DynamicSimuMethod.MODIFIED_EULER,mach,0));
        var frozen=new Gson().toJson(new DStabNetworkState(source));var restored=copy(frozen);
        var copiedMach=restored.getMachine("M");var target=(SimpleGovernor)copiedMach.getGovernor();
        assertNotSame(gov.delayBlock,target.delayBlock);assertNotSame(gov.gainBlock,target.gainBlock);
        assertNotSame(gov.gainBlock.getLimit(),target.gainBlock.getLimit());
        assertSame(target.delayBlock,block(target,"delayBlock"));assertSame(target.gainBlock,block(target,"gainBlock"));
        assertSame(target.delayBlock,target.getNamedStateBlocks().get("delayBlock"));
        assertEquals(13,target.getData().getK());assertEquals(14,target.ka);assertEquals(12,target.delayBlock.getK());
        assertTrue(gov.nextStep(.01,DynamicSimuMethod.MODIFIED_EULER,mach,1));
        assertTrue(target.nextStep(.01,DynamicSimuMethod.MODIFIED_EULER,copiedMach,1));
        assertEquals(gov.getOutput(),target.getOutput(),1e-14);
        boolean upper=false,lower=false;
        for(int i=0;i<150;i++) {
            double speed=1+.3*Math.sin(i*.1);mach.setSpeed(speed);copiedMach.setSpeed(speed);
            for(int flag=0;flag<2;flag++) {
                assertTrue(gov.nextStep(.01,DynamicSimuMethod.MODIFIED_EULER,mach,flag));
                assertTrue(target.nextStep(.01,DynamicSimuMethod.MODIFIED_EULER,copiedMach,flag));
            }
            assertEquals(gov.getOutput(),target.getOutput(),1e-13);
            upper|=target.getOutput()==1.1;lower|=target.getOutput()==.2;
        }
        assertTrue(upper&&lower,"Trajectory must exercise both governor power limits");
        mach.setSpeed(2);gov.setRefPoint(50);
        double x=target.delayBlock.getStateX(),u=copiedMach.getSpeed()-1;
        double dx1=(12*u-x)/.2,dx2=(12*u-(x+.01*dx1))/.2;
        double expected=target.gainBlock.getLimit().limit(target.getRefPoint()-(x+.005*(dx1+dx2)));
        target.nextStep(.01,DynamicSimuMethod.MODIFIED_EULER,copiedMach,0);
        target.nextStep(.01,DynamicSimuMethod.MODIFIED_EULER,copiedMach,1);
        assertEquals(expected,target.getOutput(),1e-13);
    }
    @Test void unsupportedSubclassAndUnboundDeclaredBlockAreRejected()throws Exception {
        var net=network();var mach=net.getMachine("M");mach.getControllerList().clear();new SimpleGovernor(){}.setMachine(mach);
        assertFalse(DStabNetworkState.supports(net));assertThrows(IllegalArgumentException.class,()->new DStabNetworkState(net));
        var unbound=new SimpleGovernor();unbound.delayBlock=new DelayControlBlock(1,.1);
        assertThrows(IllegalArgumentException.class,()->new SimpleGovernorState(unbound));
    }
}
