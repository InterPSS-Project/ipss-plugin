package org.interpss.dstab.control.gov.simple;

import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Test;
import com.google.gson.Gson;
import com.interpss.dstab.*;
import com.interpss.dstab.devent.*;
import com.interpss.dstab.mach.*;
import com.interpss.state.dstab.*;
import org.interpss.dstab.control.exc.simple.SimpleExciter;

class SetPointChangeEventStateTest {
    private DStabilityNetwork network()throws Exception {
        var net=DStabObjectFactory.createDStabilityNetwork();net.setBaseKva(100000);
        var bus=(DStabBus)DStabObjectFactory.createDStabBus("B",net).orElseThrow();
        var gen=DStabObjectFactory.createDStabGen("G");bus.getContributeGenList().add(gen);
        gen.setPosGenZ(new org.apache.commons.math3.complex.Complex(0,.2));
        gen.setNegGenZ(new org.apache.commons.math3.complex.Complex(0,.2));
        gen.setZeroGenZ(new org.apache.commons.math3.complex.Complex(0,.2));
        var mach=DStabObjectFactory.createMachine("M","Machine",MachineModelType.ECONSTANT,net,"B","G");
        mach.setEfd(1);mach.setPm(.8);
        new SimpleGovernor("GV","Governor","test").setMachine(mach);
        new SimpleExciter("EX","Exciter","test").setMachine(mach);
        return net;
    }
    @Test void absoluteAndRelativeChangesBindCopiedControllersAndExecuteOnceAtTime()throws Exception {
        for(var type:new MachineControllerType[]{MachineControllerType.EXCITER,MachineControllerType.GOVERNOR})
        for(boolean absolute:new boolean[]{true,false}) {
            var net=network();var mach=net.getMachine("M");
            var event=DStabObjectFactory.createDEvent("change","Saved change",DynamicSimuEventType.SET_POINT_CHANGE,net);
            var payload=DStabObjectFactory.createSetPointChangeEvent("M",net);payload.setControllerType(type);
            payload.setBus(net.getBus("B"));payload.setChangeValue(.2);payload.setAbusoluteChange(absolute);payload.setDesc("saved payload");
            event.setBusDynamicEvent(payload);event.setStartTimeSec(.02);event.setDurationSec(1);event.setPermanent(true);
            var gson=new Gson();var frozen=gson.toJson(new DStabNetworkState(net));payload.setChangeValue(9);
            var copy=DStabNetworkState.create(gson.fromJson(frozen,DStabNetworkState.class));
            var copiedEvent=copy.getDynamicEvent("change");var copiedPayload=(SetPointChangeEvent)copiedEvent.getBusDynamicEvent();
            assertSame(copy.getBus("B"),copiedPayload.getBus());assertEquals("M",copiedPayload.getMachId());
            assertEquals(.2,copiedPayload.getChangeValue());assertEquals(absolute,copiedPayload.isAbusoluteChange());
            assertEquals("saved payload",copiedPayload.getDesc());
            var target=type==MachineControllerType.EXCITER?copy.getMachine("M").getExciter():copy.getMachine("M").getGovernor();
            var source=type==MachineControllerType.EXCITER?mach.getExciter():mach.getGovernor();
            target.setRefPoint(.5);
            assertFalse(copiedEvent.hasEventAt(.01));assertEquals(.5,((com.interpss.dstab.controller.cml.annotate.AbstractAnnotateController)target).getRefPoint());
            assertTrue(copiedEvent.hasEventAt(.02));
            double expected=absolute?.2:(type==MachineControllerType.EXCITER?1.2:1);
            assertEquals(expected,((com.interpss.dstab.controller.cml.annotate.AbstractAnnotateController)target).getRefPoint(),1e-14);
            assertFalse(copiedEvent.hasEventAt(.02));assertEquals(0,((com.interpss.dstab.controller.cml.annotate.AbstractAnnotateController)source).getRefPoint());
            // Retain the already-applied latch even if event status is independently reset.
            target.setRefPoint(3.5);copiedEvent.setStatus(false);
            var again=DStabNetworkState.create(gson.fromJson(gson.toJson(new DStabNetworkState(copy)),DStabNetworkState.class));
            assertTrue(((com.interpss.dstab.devent.impl.DynamicSimuEventImpl)again.getDynamicEvent("change")).isSetPointApplied());
            assertTrue(again.getDynamicEvent("change").hasEventAt(.02));
            var restoredTarget=type==MachineControllerType.EXCITER?again.getMachine("M").getExciter():again.getMachine("M").getGovernor();
            assertEquals(3.5,((com.interpss.dstab.controller.cml.annotate.AbstractAnnotateController)restoredTarget).getRefPoint());
        }
    }
    @Test void foreignBusMissingMachineAndMissingControllerAreRejected()throws Exception {
        var net=network();var event=DStabObjectFactory.createDEvent("change","Change",DynamicSimuEventType.SET_POINT_CHANGE,net);
        var payload=DStabObjectFactory.createSetPointChangeEvent("M",net);event.setBusDynamicEvent(payload);
        payload.setControllerType(MachineControllerType.GOVERNOR);
        var foreign=network();payload.setBus(foreign.getBus("B"));
        assertFalse(DynamicEventState.supports(event,net));
        payload.setBus(net.getBus("B"));payload.setMachId("missing");assertFalse(SetPointChangeEventState.supports(event,net));
        payload.setMachId("M");net.getMachine("M").getControllerList().clear();assertFalse(SetPointChangeEventState.supports(event,net));
        assertThrows(IllegalArgumentException.class,()->new SetPointChangeEventState(event,net));
    }
}
