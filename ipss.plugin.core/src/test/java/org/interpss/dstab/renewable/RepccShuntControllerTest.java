package org.interpss.dstab.renewable;

import static org.junit.jupiter.api.Assertions.*;
import static org.interpss.dstab.renewable.RepccData.Parameter.*;
import java.util.*;
import org.junit.jupiter.api.Test;

class RepccShuntControllerTest {
    private static final List<RepccShuntController.Device> DEVICES=List.of(
            new RepccShuntController.Device(new RepccData.Shunt(2,"R1"),-.01,false),
            new RepccShuntController.Device(new RepccData.Shunt(2,"C1"),.01,true),
            new RepccShuntController.Device(new RepccData.Shunt(2,"R2"),-.01,true),
            new RepccShuntController.Device(new RepccData.Shunt(2,"C2"),.01,false));
    private static double[] constants(){return new double[]{.02,1,5,0,.05,.7,0,.05,.02,1,-1,0,0,1,-1,1,.1,.02,-.0006,.0006,
            99,-99,2,-1,.1,20,20,1.08,.95,1,-1,99,-99,99,-99,99,-99,.8,-.8,1,-1,1,-1,.02,-.15,-.3,.15,.3,.01,.003,.004,.03,.04,.02,.8,.015,-.015};}
    private static RepccData data(double[] c,int flag,List<RepccShuntController.Device> devices){
        var slots=new ArrayList<RepccData.Shunt>();for(var device:devices)slots.add(device.key());
        while(slots.size()<10)slots.add(new RepccData.Shunt(0,"0"));
        return new RepccData(3,new RepccData.Branch(2,3,"1"),1,0,1,1,1,flag,new RepccData.Branch(3,2,"1"),slots,3,1,c);
    }
    private static RepccShuntController controller(double[] c,List<RepccShuntController.Device> devices){
        var k=new RepccShuntController(data(c,1,devices));k.initialize(devices,true);return k;
    }
    @Test void removalPriorityPrecedesEarlierInsertionAndBreakersOverlap(){
        var k=controller(constants(),DEVICES);k.advance(0,-.2);assertTrue(k.advance(.01,-.2).isEmpty());
        assertEquals(.01,k.slots().get(1).breakerStart());assertTrue(k.slots().get(1).inService());
        k.advance(.0105,-.2);assertEquals(.0105,k.slots().get(0).breakerStart());
        var cap=k.advance(.014001,-.2);assertEquals(1,cap.size());assertEquals("C1",cap.getFirst().key().id());assertFalse(cap.getFirst().inService());
        var reactor=k.advance(.014501,-.2);assertEquals(1,reactor.size());assertEquals("R1",reactor.getFirst().key().id());assertTrue(reactor.getFirst().inService());
        assertEquals(0,k.getNamedStates().get("slowStart"));
    }
    @Test void positiveDirectionRemovesReactorBeforeInsertingCapacitor(){
        var k=controller(constants(),DEVICES);k.advance(0,.2);k.advance(.01,.2);
        assertEquals(.01,k.slots().get(2).breakerStart());k.advance(.0105,.2);
        assertEquals(.0105,k.slots().get(3).breakerStart());
        var out=k.advance(.014501,.2);assertEquals(2,out.size());assertEquals("R2",out.get(0).key().id());assertFalse(out.get(0).inService());
        assertEquals("C2",out.get(1).key().id());assertTrue(out.get(1).inService());
    }
    @Test void fastBandUsesTheSecondDelayAndThresholdsAreStrict(){
        var k=controller(constants(),DEVICES);k.advance(0,-.4);k.advance(.0029,-.4);assertEquals(-1,k.slots().get(1).breakerStart());
        k.advance(.003,-.4);assertEquals(.003,k.slots().get(1).breakerStart());
        assertEquals(-1,k.getNamedStates().get("slowStart"));assertEquals(0,k.getNamedStates().get("fastStart"));
        k=controller(constants(),DEVICES);k.advance(0,-.15);k.advance(.1,.15);assertEquals(-1,k.getNamedStates().get("slowStart"));
        k.advance(.2,-.3);assertEquals(-1,k.getNamedStates().get("fastStart"));assertEquals(.2,k.getNamedStates().get("slowStart"));
    }
    @Test void pendingActionsRemainLatchedAfterBandRecovery(){
        var k=controller(constants(),DEVICES);k.advance(0,-.2);k.advance(.01,-.2);k.advance(.012,0);
        var actions=k.advance(.014001,0);assertEquals(1,actions.size());assertFalse(actions.getFirst().inService());assertEquals(-1,k.getNamedStates().get("slowStart"));
    }
    @Test void capacitorDischargeStartsAtActualOpeningAndBlocksReclosure(){
        double[] c=constants();c[TDELAY1.ordinal()]=.125;c[TDELAY2.ordinal()]=.0625;c[TMSSBRK.ordinal()]=.03125;c[TOUT.ordinal()]=.25;
        var device=List.of(DEVICES.get(1));var k=controller(c,device);k.advance(0,-.2);k.advance(.125,-.2);
        k.advance(.15625,-.2);assertEquals(.15625,k.slots().getFirst().dischargeStart());
        k.advance(.1875,.2);k.advance(.3125,.2);assertEquals(-1,k.slots().getFirst().breakerStart());
        k.advance(.40625,.2);assertEquals(.40625,k.slots().getFirst().breakerStart());
        var close=k.advance(.4375,.2);assertEquals(1,close.size());assertTrue(close.getFirst().inService());assertEquals(-1,k.slots().getFirst().dischargeStart());
    }
    @Test void alreadyOffCapacitorIsInitiallyAvailableAndUnavailableSlotsAreSkipped(){
        var device=List.of(DEVICES.get(3));var k=controller(constants(),device);k.advance(0,.2);k.advance(.01,.2);
        assertEquals(.01,k.slots().getFirst().breakerStart());assertEquals(10,k.slots().size());
        assertTrue(k.advance(.014001,.2).getFirst().inService());
    }
    @Test void directionChangeRestartsDurationAndRepeatedTimeIsIdempotent(){
        var k=controller(constants(),DEVICES);k.advance(0,-.2);k.advance(.005,.2);k.advance(.01,.2);
        assertEquals(-1,k.slots().get(2).breakerStart());k.advance(.015001,.2);assertTrue(k.slots().get(2).breakerStart()>=0);
        var before=k.getNamedStates();assertTrue(k.advance(.015001,-.4).isEmpty());assertEquals(before,k.getNamedStates());
        assertThrows(UnsupportedOperationException.class,()->before.clear());assertThrows(UnsupportedOperationException.class,()->k.slots().clear());
    }
    @Test void disabledAndMissingMeasurementBranchesDoNotSwitch(){
        for(boolean branch:new boolean[]{false,true})for(int flag:new int[]{0,1}){
            if(branch&&flag==1)continue;
            var k=new RepccShuntController(data(constants(),flag,DEVICES));k.initialize(DEVICES,branch);
            assertFalse(k.isEnabled());k.advance(0,-.4);assertTrue(k.advance(1,-.4).isEmpty());
            for(int i=0;i<4;i++)assertEquals(DEVICES.get(i).inService(),k.slots().get(i).inService());
        }
        double[] c=constants();c[QDN2.ordinal()]=-.1;var k=controller(c,DEVICES);assertFalse(k.isEnabled());
        var missing=new RepccShuntController(data(constants(),1,DEVICES));missing.initialize(List.of(),false);
        assertFalse(missing.isEnabled());assertTrue(missing.advance(1,-.4).isEmpty());
    }
    @Test void initializationRejectsMissingOrDuplicateConfiguredDevices(){
        var k=new RepccShuntController(data(constants(),1,DEVICES));
        assertThrows(IllegalArgumentException.class,()->k.initialize(List.of(),true));
        var duplicate=new ArrayList<>(DEVICES);duplicate.add(DEVICES.getFirst());assertThrows(IllegalArgumentException.class,()->k.initialize(duplicate,true));
        assertThrows(IllegalArgumentException.class,()->new RepccShuntController.Device(new RepccData.Shunt(2,"1"),Double.NaN,false));
    }
    @Test void timeAndInputValidationDoNotSilentlyRewindTheRelay(){
        var k=controller(constants(),DEVICES);k.advance(.1,0);
        assertThrows(IllegalArgumentException.class,()->k.advance(.09,0));
        assertThrows(IllegalArgumentException.class,()->k.advance(.2,Double.NaN));
        assertThrows(IllegalStateException.class,()->new RepccShuntController(data(constants(),1,DEVICES)).advance(0,0));
    }
}
