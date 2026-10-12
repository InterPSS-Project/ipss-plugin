package org.interpss.dstab.control.pss.simple;
import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Test;
import com.google.gson.Gson;
import org.apache.commons.math3.complex.Complex;
import com.interpss.dstab.*;
import com.interpss.dstab.mach.*;
import com.interpss.dstab.algo.DynamicSimuMethod;
import com.interpss.dstab.controller.cml.field.block.FilterControlBlock;
import com.interpss.state.dstab.DStabNetworkState;
import org.interpss.dstab.control.exc.simple.SimpleExciter;
import org.interpss.dstab.control.gov.simple.SimpleGovernor;
class SimpleStabilizerStateTest {
    private DStabilityNetwork network()throws Exception {
        var net=DStabObjectFactory.createDStabilityNetwork();net.setBaseKva(100000);net.setFrequency(60);
        var bus=(DStabBus)DStabObjectFactory.createDStabBus("B",net).orElseThrow();bus.setBaseVoltage(1000);bus.setVoltage(Complex.ONE);
        var gen=DStabObjectFactory.createDStabGen("G");bus.getContributeGenList().add(gen);
        gen.setPosGenZ(new Complex(0,.2));gen.setNegGenZ(new Complex(0,.2));gen.setZeroGenZ(new Complex(0,.2));
        var mach=DStabObjectFactory.createMachine("M","machine",MachineModelType.ECONSTANT,net,"B","G");
        mach.setRating(100,org.interpss.numeric.datatype.Unit.UnitType.mVA,net.getBaseKva());mach.setRatedVoltage(1000);mach.calMultiFactors();
        mach.setEfd(1);mach.setSpeed(1);mach.setPm(.8);
        var stabilizer=new SimpleStabilizer("PS","Stabilizer","test");stabilizer.setMachine(mach);
        stabilizer.getData().setKs(8);stabilizer.getData().setT1(.03);stabilizer.getData().setT2(.2);
        stabilizer.getData().setT3(.04);stabilizer.getData().setT4(.15);
        stabilizer.getData().setVsmax(.12);stabilizer.getData().setVsmin(-.1);
        return net;
    }

    private FilterControlBlock block(SimpleStabilizer controller,String name) {
        return (FilterControlBlock)controller.getFieldWrapperList().stream().filter(w->w.getFieldName().equals(name)).findFirst().orElseThrow().getField();
    }
    private DStabilityNetwork copy(String frozen)throws Exception {
        return DStabNetworkState.create(new Gson().fromJson(frozen,DStabNetworkState.class));
    }
    @Test void configuredControllersRebindToCopiedMachine()throws Exception {
        var source=network();var mach=source.getMachine("M");var pss=(SimpleStabilizer)mach.getStabilizer();
        new SimpleExciter("EX","Exciter","test").setMachine(mach);
        new SimpleGovernor("GV","Governor","test").setMachine(mach);
        pss.setStatus(false);pss.setDesc("saved stabilizer");pss.setRefPoint(1.01);
        var frozen=new Gson().toJson(new DStabNetworkState(source));pss.getData().setKs(999);pss.setRefPoint(99);
        var restored=copy(frozen);var target=(SimpleStabilizer)restored.getMachine("M").getStabilizer();
        assertNotSame(pss,target);assertNotSame(pss.getData(),target.getData());
        assertSame(restored.getMachine("M"),target.getMachine());assertSame(restored.getBus("B"),target.getDStabBus());
        assertEquals(3,restored.getMachine("M").getControllerList().size());
        assertEquals(8,target.getData().getKs());assertEquals(1.01,target.getRefPoint());assertFalse(target.isStatus());
        assertEquals("saved stabilizer",target.getDesc());assertNull(target.getFieldWrapperList());
    }
    @Test void initializedStabilizerContinuesBothStagesAndKeepsDistinctParameters()throws Exception {
        var source=network();var mach=source.getMachine("M");var pss=(SimpleStabilizer)mach.getStabilizer();
        assertTrue(pss.initStates(mach.getDStabBus(),mach));
        pss.filterBlock1=block(pss,"filterBlock1");pss.filterBlock2=block(pss,"filterBlock2");
        pss.getData().setKs(9);pss.k1=10;mach.setSpeed(1.03);
        assertTrue(pss.nextStep(.005,DynamicSimuMethod.MODIFIED_EULER,mach,0));
        var frozen=new Gson().toJson(new DStabNetworkState(source));var restored=copy(frozen);
        var copiedMach=restored.getMachine("M");var target=(SimpleStabilizer)copiedMach.getStabilizer();
        assertNotSame(pss.filterBlock1,target.filterBlock1);assertNotSame(pss.filterBlock2,target.filterBlock2);
        assertNotSame(pss.filterBlock2.getLimit(),target.filterBlock2.getLimit());
        assertSame(target.filterBlock1,block(target,"filterBlock1"));assertSame(target.filterBlock2,block(target,"filterBlock2"));
        assertSame(target.filterBlock1,target.getNamedStateBlocks().get("filterBlock1"));
        assertEquals(9,target.getData().getKs());assertEquals(10,target.k1);assertEquals(8,target.filterBlock1.getK());
        assertTrue(pss.nextStep(.005,DynamicSimuMethod.MODIFIED_EULER,mach,1));
        assertTrue(target.nextStep(.005,DynamicSimuMethod.MODIFIED_EULER,copiedMach,1));
        assertEquals(pss.getOutput(),target.getOutput(),1e-14);
        boolean upper=false,lower=false;
        for(int i=0;i<200;i++) {
            double speed=1+.2*Math.sin(i*.1);mach.setSpeed(speed);copiedMach.setSpeed(speed);
            for(int flag=0;flag<2;flag++) {
                assertTrue(pss.nextStep(.005,DynamicSimuMethod.MODIFIED_EULER,mach,flag));
                assertTrue(target.nextStep(.005,DynamicSimuMethod.MODIFIED_EULER,copiedMach,flag));
            }
            assertEquals(pss.getOutput(),target.getOutput(),1e-13);
            upper|=target.getOutput()==.12;lower|=target.getOutput()==-.1;
        }
        assertTrue(upper&&lower,"Trajectory must exercise both stabilizer limits");
        double x=target.filterBlock1.getStateX();pss.filterBlock1.restoreIntegrationState(50,50,50);
        assertEquals(x,target.filterBlock1.getStateX());assertNotSame(target,copy(frozen).getMachine("M").getStabilizer());
    }
    @Test void unknownSubclassAndUnboundDeclaredBlockAreRejected()throws Exception {
        var net=network();var mach=net.getMachine("M");mach.getControllerList().clear();new SimpleStabilizer(){}.setMachine(mach);
        assertFalse(DStabNetworkState.supports(net));assertThrows(IllegalArgumentException.class,()->new DStabNetworkState(net));
        var unbound=new SimpleStabilizer();unbound.filterBlock1=new FilterControlBlock(1,.1,.2);
        assertThrows(IllegalArgumentException.class,()->new SimpleStabilizerState(unbound));
    }
}
