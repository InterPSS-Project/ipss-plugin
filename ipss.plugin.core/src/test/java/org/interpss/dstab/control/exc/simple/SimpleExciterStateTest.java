package org.interpss.dstab.control.exc.simple;

import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Test;
import com.google.gson.Gson;
import org.apache.commons.math3.complex.Complex;
import com.interpss.dstab.*;
import com.interpss.dstab.mach.*;
import com.interpss.dstab.algo.DynamicSimuMethod;
import com.interpss.state.dstab.*;

class SimpleExciterStateTest {
    private DStabilityNetwork network() throws Exception {
        var net=DStabObjectFactory.createDStabilityNetwork();net.setBaseKva(100000);net.setFrequency(60);
        var bus=(DStabBus)DStabObjectFactory.createDStabBus("B",net).orElseThrow();
        bus.setBaseVoltage(1000);bus.setVoltage(Complex.ONE);
        var gen=DStabObjectFactory.createDStabGen("G");bus.getContributeGenList().add(gen);
        var machine=DStabObjectFactory.createMachine("M","machine",MachineModelType.ECONSTANT,net,"B","G");
        machine.setRating(100,org.interpss.numeric.datatype.Unit.UnitType.mVA,net.getBaseKva());
        machine.setRatedVoltage(1000);machine.calMultiFactors();
        gen.setPosGenZ(new Complex(0,.2));gen.setNegGenZ(new Complex(0,.2));gen.setZeroGenZ(new Complex(0,.2));
        machine.setEfd(1);machine.setSpeed(1);
        var exc=new SimpleExciter("EX","exciter","test");exc.setMachine(machine);
        exc.getData().setKa(7);exc.getData().setTa(.15);exc.getData().setVrmax(5);exc.getData().setVrmin(-2);
        return net;
    }
    private DStabilityNetwork copy(String frozen) throws Exception {
        return DStabNetworkState.create(new Gson().fromJson(frozen,DStabNetworkState.class));
    }
    private com.interpss.dstab.controller.cml.field.block.DelayControlBlock block(SimpleExciter exc) {
        return (com.interpss.dstab.controller.cml.field.block.DelayControlBlock)exc.getFieldWrapperList().get(0).getField();
    }
    @Test void configuredControllerAndMetadataRestoreIndependently() throws Exception {
        var source=network();var machine=source.getMachine("M");var exc=(SimpleExciter)machine.getExciter();
        exc.setDesc("original controller");exc.setStatus(false);exc.setRefPoint(1.25);
        assertTrue(DStabNetworkState.supports(source));
        var frozen=new Gson().toJson(new DStabNetworkState(source));
        exc.getData().setKa(999);exc.setRefPoint(9);exc.setStatus(true);
        var restored=copy(frozen);var target=(SimpleExciter)restored.getMachine("M").getExciter();
        assertNotSame(exc,target);assertNotSame(exc.getData(),target.getData());
        assertSame(restored.getMachine("M"),target.getMachine());
        assertSame(restored.getBus("B"),target.getDStabBus());
        assertEquals(7,target.getData().getKa());assertEquals(1.25,target.getRefPoint());
        assertFalse(target.isStatus());assertEquals("original controller",target.getDesc());
        assertNull(target.delayBlock);assertEquals("test",target.getCategory());
        assertEquals(1,restored.getMachine("M").getControllerList().size());
    }
    @Test void initializedControllerContinuesAcrossPredictorCorrectorWithReboundSymbols() throws Exception {
        var source=network();var machine=source.getMachine("M");var exc=(SimpleExciter)machine.getExciter();
        assertTrue(exc.initStates(machine.getDStabBus(),machine));
        // Configuration edits after initialization do not replace the running block parameters.
        exc.getData().setKa(8);exc.k=9;
        source.getBus("B").setVoltage(new Complex(.9,0));
        assertTrue(exc.nextStep(.01,DynamicSimuMethod.MODIFIED_EULER,machine,0));
        new SimpleExciterState(exc);
        var frozen=new Gson().toJson(new DStabNetworkState(source));var restored=copy(frozen);
        var copiedMachine=restored.getMachine("M");var target=(SimpleExciter)copiedMachine.getExciter();
        assertNotSame(block(exc),block(target));assertNotSame(exc.getFieldWrapperList(),target.getFieldWrapperList());
        assertSame(block(target),target.getNamedStateBlocks().get("delayBlock"));
        assertEquals(block(exc).getDX_dt(),block(target).getDX_dt());
        assertEquals(8,target.getData().getKa());assertEquals(9,target.k);assertEquals(7,block(target).getK());
        assertEquals(((com.interpss.dstab.controller.cml.wrapper.BlockFieldAnWrapper<?>)exc.getFieldWrapperList().get(0)).isInitialized(),
                ((com.interpss.dstab.controller.cml.wrapper.BlockFieldAnWrapper<?>)target.getFieldWrapperList().get(0)).isInitialized());
        assertTrue(exc.nextStep(.01,DynamicSimuMethod.MODIFIED_EULER,machine,1));
        assertTrue(target.nextStep(.01,DynamicSimuMethod.MODIFIED_EULER,copiedMachine,1));
        assertEquals(exc.getOutput(),target.getOutput(),1e-14);
        for(int i=0;i<100;i++) {
            double voltage=1+.1*Math.sin(i*.1);
            source.getBus("B").setVoltage(new Complex(voltage,0));restored.getBus("B").setVoltage(new Complex(voltage,0));
            for(int flag=0;flag<2;flag++) {
                assertTrue(exc.nextStep(.01,DynamicSimuMethod.MODIFIED_EULER,machine,flag));
                assertTrue(target.nextStep(.01,DynamicSimuMethod.MODIFIED_EULER,copiedMachine,flag));
            }
            assertEquals(exc.getOutput(),target.getOutput(),1e-13);
        }
        // Source voltage and controller edits must never leak into copied expression bindings.
        source.getBus("B").setVoltage(new Complex(.2,0));exc.setRefPoint(20);exc.getData().setKa(999);
        double x=block(target).getStateX(), input=target.getRefPoint()-restored.getBus("B").getVoltageMag()/copiedMachine.getVMultiFactor();
        double dx1=(block(target).getK()*input-x)/block(target).getT();
        double dx2=(block(target).getK()*input-(x+dx1*.01))/block(target).getT();
        double expected=block(target).getLimit().limit(x+.005*(dx1+dx2));
        target.nextStep(.01,DynamicSimuMethod.MODIFIED_EULER,copiedMachine,0);
        target.nextStep(.01,DynamicSimuMethod.MODIFIED_EULER,copiedMachine,1);
        assertEquals(expected,target.getOutput(),1e-13);
        assertSame(copiedMachine,target.getMachine());assertSame(restored.getBus("B"),target.getDStabBus());
        var independent=copy(frozen);var independentMachine=independent.getMachine("M");
        var independentExc=(SimpleExciter)independentMachine.getExciter();
        var another=copy(frozen);var anotherMachine=another.getMachine("M");
        independentExc.nextStep(.01,DynamicSimuMethod.MODIFIED_EULER,independentMachine,1);
        ((SimpleExciter)anotherMachine.getExciter()).nextStep(.01,DynamicSimuMethod.MODIFIED_EULER,anotherMachine,1);
        assertEquals(independentExc.getOutput(),anotherMachine.getExciter().getOutput(),1e-14);
        assertEquals(8,independentExc.getData().getKa());
    }
    @Test void unsupportedControllerSubclassRemainsRejected() throws Exception {
        var net=network();var machine=net.getMachine("M");machine.getControllerList().clear();
        new SimpleExciter(){}.setMachine(machine);
        assertFalse(DStabNetworkState.supports(net));
        assertThrows(IllegalArgumentException.class,()->new DStabNetworkState(net));
    }
}
