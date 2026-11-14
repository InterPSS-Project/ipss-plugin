package org.interpss.fadapter.psse;

import static org.junit.jupiter.api.Assertions.*;
import java.util.Set;
import org.junit.jupiter.api.Test;
import com.interpss.dstab.DStabObjectFactory;
import com.interpss.dstab.cache.StateMonitor;
import com.interpss.dstab.device.impl.DynamicBusDeviceImpl;
import com.interpss.dstab.mach.MachineModelType;
import com.interpss.dstab.controller.pss.ieeest.*;

class BusFrequencySelectionTest {
    private com.interpss.dstab.algo.DynamicSimuAlgorithm fixture() throws Exception {
        var network=DStabObjectFactory.createDStabilityNetwork();
        for(String id:new String[]{"Bus1","Bus2","Bus3"})DStabObjectFactory.createDStabBus(id,network).orElseThrow();
        var bus=network.getDStabBus("Bus1");bus.getContributeGenList().add(DStabObjectFactory.createDStabGen("1"));
        DStabObjectFactory.createMachine("M","M",MachineModelType.ECONSTANT,network,"Bus1","1");
        var algorithm=DStabObjectFactory.createDynamicSimuAlgorithm(network);
        algorithm.setSimuOutputHandler(new StateMonitor());return algorithm;
    }
    @Test void passiveBusesAreOmittedAndRequestedOutputIsRetained() throws Exception {
        var a=fixture();assertEquals(Set.of("Bus1"),BusFrequencySelection.select(a).required());
        ((StateMonitor)a.getSimuOutputHandler()).addBusStdMonitor(new String[]{"Bus3"});
        assertEquals(Set.of("Bus1","Bus3"),BusFrequencySelection.select(a).required());
        assertEquals(PsseDStabSolver.BusFrequencyMode.REQUIRED,new PsseDStabSolver(a).getBusFrequencyMode());
    }
    @Test void remoteFrequencyIsIncludedButVoltageOnlyRemoteInputDoesNotRequireIt() throws Exception {
        for(int mode:new int[]{2,5,6}) {
            var a=fixture();var machine=a.getNetwork().getMachine("M");
            var data=new IeeestData(mode,2,.061,.0017,.02,0,0,0,.1,.1,.1,.1,.1,.1,1,.1,-.1,0,0);
            var pss=new IeeestStabilizer("S",data,machine);
            assertTrue(pss.initStates(a.getNetwork().getDStabBus("Bus1"),machine));
            assertEquals(mode==2?Set.of("Bus1","Bus2"):Set.of("Bus1"),BusFrequencySelection.select(a).required());
        }
    }
    @Test void unknownCallbacksDevicesAndCustomMeasurementsRetainAllBuses() throws Exception {
        var a=fixture();a.setSimuOutputHandler(new StateMonitor(){});
        assertNull(BusFrequencySelection.select(a).required());
        a.setSimuOutputHandler(new StateMonitor());
        var bus=a.getNetwork().getDStabBus("Bus3");
        bus.getDynamicBusDeviceList().add(new DynamicBusDeviceImpl(){});
        assertNull(BusFrequencySelection.select(a).required());
        bus.getDynamicBusDeviceList().clear();
        bus.setBusFreqMeasureBlock(new com.interpss.dstab.controller.deqn.measure.BusFreqMeasurementImpl(){});
        assertNull(BusFrequencySelection.select(a).required());
    }
    @Test void customCallbackCanExplicitlyDeclareAdditionalFrequencyReads() throws Exception {
        var a=fixture();a.setSimuOutputHandler(new StateMonitor(){
            @Override public Set<String> requiredBusFrequencyIds(){return Set.of("Bus2");}
        });
        assertEquals(Set.of("Bus1","Bus2"),BusFrequencySelection.select(a).required());
    }
    @Test void observersAndCustomCurrentCallbacksRetainAllMeasurements() throws Exception {
        var a=fixture();var bus=a.getNetwork().getDStabBus("Bus3");
        bus.eAdapters().add(new org.eclipse.emf.common.notify.impl.AdapterImpl());
        assertNull(BusFrequencySelection.select(a).required());bus.eAdapters().clear();
        a.getNetwork().setCustomBusCurrInjHashtable(new java.util.Hashtable<>(){});
        assertNull(BusFrequencySelection.select(a).required());
    }
    @Test void constantConverterCommandsAndConfiguredRemoteSignalBusesAreKnown() throws Exception {
        var a=fixture();var bus=a.getNetwork().getDStabBus("Bus1");
        var gen=(com.interpss.dstab.DStabGen)bus.getContributeGen("1");
        var data=new com.interpss.dstab.renewable.Regca1Data(1,.02,10.,.9,.4,1.2,1.2,.8,.4,-1.3,.02,0.,999.,-999.,.3);
        var converter=new com.interpss.dstab.renewable.Regca1Model(gen,bus,"1",data);
        converter.setActiveElectricalController(new com.interpss.dstab.renewable.ConverterCurrentCommands());
        gen.setDynamicGenDevice(converter);
        assertEquals(Set.of("Bus1"),BusFrequencySelection.select(a).required());
        var pss=new com.interpss.dstab.controller.pss.ieee1992pss2a.Ieee1992PSS2AStabilizer();
        pss.setInputSignalBuses(a.getNetwork().getDStabBus("Bus2"),a.getNetwork().getDStabBus("Bus3"));
        assertEquals(Set.of("Bus2","Bus3"),pss.requiredBusFrequencyIds());
    }
    @Test void replacingAStandardModelsBlockRetainsAllMeasurements() throws Exception {
        var a=fixture();var machine=a.getNetwork().getMachine("M");
        var data=new IeeestData(2,2,.061,.0017,.02,0,0,0,.1,.1,.1,.1,.1,.1,1,.1,-.1,0,0);
        var pss=new IeeestStabilizer("S",data,machine);
        pss.busMeasurement=new com.interpss.dstab.controller.cml.field.block.GainBlock(){};
        assertNull(BusFrequencySelection.select(a).required());
    }

    private com.interpss.dstab.controller.pss.ieee1992pss2a.Ieee1992PSS2AStabilizer
            attachPss2a(com.interpss.dstab.algo.DynamicSimuAlgorithm algorithm) {
        var machine=algorithm.getNetwork().getMachine("M");
        var pss=new com.interpss.dstab.controller.pss.ieee1992pss2a.Ieee1992PSS2AStabilizer("S","S","test");
        pss.setMachine(machine);
        pss.setInputSignalBuses(algorithm.getNetwork().getDStabBus("Bus2"),algorithm.getNetwork().getDStabBus("Bus2"));
        machine.getControllerList().add((com.interpss.dstab.mach.MachineController)(Object)pss.customBlock1);
        machine.getControllerList().add((com.interpss.dstab.mach.MachineController)(Object)pss.customBlock2);
        return pss;
    }

    @Test void ownedPss2aChildrenKeepRemoteInputsWithoutRequiringPassiveBuses() throws Exception {
        var a=fixture();var pss=attachPss2a(a);
        assertTrue(pss.isBuiltInSignalConditioner(pss.customBlock1));
        assertEquals(Set.of("Bus1","Bus2"),BusFrequencySelection.select(a).required());
        ((StateMonitor)a.getSimuOutputHandler()).addBusStdMonitor(new String[]{"Bus3"});
        assertEquals(Set.of("Bus1","Bus2","Bus3"),BusFrequencySelection.select(a).required());
    }

    @Test void foreignOrDetachedPss2aChildrenKeepConservativeCoverage() throws Exception {
        var a=fixture();var pss=attachPss2a(a);
        Object old=pss.customBlock1;
        var other=new com.interpss.dstab.controller.pss.ieee1992pss2a.Ieee1992PSS2AStabilizer("X","X","test");
        Object foreign=other.customBlock1;
        pss.getClass().getField("customBlock1").set(pss,foreign);
        assertFalse(pss.isBuiltInSignalConditioner(old));
        assertFalse(pss.isBuiltInSignalConditioner(foreign));
        assertNull(BusFrequencySelection.select(a).required());
    }

    @Test void observedOrCustomizedPss2aChildrenKeepAllMeasurements() throws Exception {
        var a=fixture();var pss=attachPss2a(a);
        var child=(com.interpss.dstab.mach.MachineController)(Object)pss.customBlock1;
        child.eAdapters().add(new org.eclipse.emf.common.notify.impl.AdapterImpl());
        assertNull(BusFrequencySelection.select(a).required());child.eAdapters().clear();
        var field=child.getClass().getDeclaredField("washoutBlock1");assertTrue(field.trySetAccessible());
        field.set(child,new com.interpss.dstab.controller.cml.field.block.WashoutControlBlock(1.,.1){});
        assertNull(BusFrequencySelection.select(a).required());
    }

    @Test void standardLocalExciterAndGovernorsDoNotRequirePassiveFrequency() throws Exception {
        var a=fixture();var machine=a.getNetwork().getMachine("M");
        new com.interpss.dstab.controller.exc.ieee1981st1.IEEE1981ST1Exciter("E","E","test").setMachine(machine);
        for(var governor:java.util.List.of(
                new com.interpss.dstab.controller.gov.tgov1.PsseTGov1SteamTurGovernor("G1","G1","test"),
                new com.interpss.dstab.controller.gov.gast.PsseGASTGasTurGovernor("G2","G2","test"),
                new com.interpss.dstab.controller.gov.ieesgo.PsseIEESGOSteamTurGovernor("G3","G3","test")))
            governor.setMachine(machine);
        assertEquals(Set.of("Bus1"),BusFrequencySelection.select(a).required());
        new com.interpss.dstab.controller.gov.tgov1.PsseTGov1SteamTurGovernor("C","C","test"){}.setMachine(machine);
        assertNull(BusFrequencySelection.select(a).required());
    }
}
