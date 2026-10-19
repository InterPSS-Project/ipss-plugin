package org.interpss.dstab.renewable;

import static org.junit.jupiter.api.Assertions.*;
import static org.interpss.dstab.renewable.HplntdData.Parameter.*;
import java.util.*;
import org.junit.jupiter.api.Test;

class HplntdSwitchingKernelTest {
    private static double[] constants(){return new double[]{.02,1,5,0,.05,.7,0,.05,.02,1,-1,0,0,1,-1,1,.1,.02,-.0006,.0006,99,-99,2,-1,.1,20,20,1.08,.95,1,-1,99,-99,99,-99,99,-99,.8,-.8,99,-99,1,-1,.02,-5,-10,5,10,.01,.003,.004,.03,.01,.02,.8,.015,-.015,0,.01,-.02,1.1};}
    private static List<RepccShuntController.Device> devices(){
        var result=new ArrayList<RepccShuntController.Device>();
        for(int i=0;i<10;i++)result.add(new RepccShuntController.Device(new RepccData.Shunt(2,(i<5?"C":"R")+(i%5+1)),i<5?.0001:-.0001,i>=5));
        return result;
    }
    private static HplntdData data(double[] c,int flag,List<RepccShuntController.Device> devices){
        var keys=new ArrayList<RepccData.Shunt>();devices.forEach(d->keys.add(d.key()));while(keys.size()<10)keys.add(new RepccData.Shunt(0,"0"));
        return new HplntdData(2,new RepccData.Branch(2,3,"1"),1,0,1,1,1,flag,new RepccData.Branch(2,3,"1"),keys,2,1,c);
    }
    private static RepccShuntController initialized(double[] c,List<RepccShuntController.Device> devices){var k=new RepccShuntController(data(c,1,devices));k.initialize(devices,true);return k;}
    @Test void physicalMvarThresholdsAreIndependentOfPlantBase(){
        Map<String,Double> previous=null;
        for(double base:new double[]{0,50,150,200}){
            var c=constants();c[PLANT_MVA.ordinal()]=base;var k=initialized(c,devices());k.advance(0,14);k.advance(.003,14);
            assertEquals(.003,k.slots().get(5).breakerStart(),0);
            if(previous!=null)assertEquals(previous,k.getNamedStates());previous=k.getNamedStates();
            var wrong=initialized(c,devices());wrong.advance(0,14/150.);wrong.advance(.1,14/150.);
            assertTrue(wrong.slots().stream().noneMatch(s->s.breakerStart()>=0),"A plant-pu conversion would incorrectly suppress the physical Mvar trigger");
        }
    }
    @Test void allTenSlotsRespectRemovalPriorityAndInputOrder(){
        var k=initialized(constants(),devices());var actions=new ArrayList<RepccShuntController.Action>();k.advance(0,14);
        for(int tick=1;tick<=200;tick++)actions.addAll(k.advance(tick*.0005,14));
        assertEquals(List.of("R1","R2","R3","R4","R5","C1","C2","C3","C4","C5"),actions.stream().map(a->a.key().id()).toList());
        for(int i=0;i<10;i++)assertEquals(i>=5,actions.get(i).inService());
        assertEquals(32,k.getNamedStates().size());assertEquals(10,k.slots().size());
    }
    @Test void negativeDirectionRemovesCapacitorsBeforeInsertingReactors(){
        var reversed=devices().stream().map(d->new RepccShuntController.Device(d.key(),d.susceptance(),!d.inService())).toList();
        var k=initialized(constants(),reversed);var actions=new ArrayList<RepccShuntController.Action>();k.advance(0,-14);
        for(int tick=1;tick<=200;tick++)actions.addAll(k.advance(tick*.0005,-14));
        assertEquals(List.of("C1","C2","C3","C4","C5","R1","R2","R3","R4","R5"),actions.stream().map(a->a.key().id()).toList());
    }
    @Test void disabledInvalidAndMissingBranchPathsDoNotSwitchOrCorrectData(){
        for(int kind=0;kind<3;kind++){
            var c=constants();if(kind==1)c[QDN2.ordinal()]=-1;var d=data(c,kind==0?0:1,devices());
            assertEquals(kind!=1,d.switchingThresholdsValid());var k=new RepccShuntController(d);k.initialize(devices(),kind!=2);
            assertFalse(k.isEnabled());k.advance(0,14);assertTrue(k.advance(1,14).isEmpty());
            if(kind==1)assertEquals(-1,d.value(QDN2),0);
            for(int i=0;i<10;i++)assertEquals(devices().get(i).inService(),k.slots().get(i).inService());
        }
    }
    @Test void capacitorDischargePreventsPrematureRecloseAfterDirectionReversal(){
        var one=List.of(new RepccShuntController.Device(new RepccData.Shunt(2,"C1"),.01,true));var k=initialized(constants(),one);
        k.advance(0,-14);k.advance(.003,-14);assertFalse(k.advance(.007001,-14).getFirst().inService());
        k.advance(.008,14);assertTrue(k.advance(.012,14).isEmpty());assertEquals(-1,k.slots().getFirst().breakerStart(),0);
        k.advance(.03701,14);assertEquals(.03701,k.slots().getFirst().breakerStart(),0);assertTrue(k.advance(.04102,14).getFirst().inService());
    }
    @Test void equalTimeIsIdempotentAndPendingRequestsStayLatched(){
        var k=initialized(constants(),devices());k.advance(0,14);k.advance(.003,14);var before=k.getNamedStates();
        assertTrue(k.advance(.003,-14).isEmpty());assertEquals(before,k.getNamedStates());
        assertFalse(k.advance(.007001,0).getFirst().inService());
        assertThrows(IllegalArgumentException.class,()->k.advance(.006,0));assertThrows(IllegalArgumentException.class,()->k.advance(.008,Double.NaN));
    }
    @Test void zeroDelayStillIssuesAtMostOneRequestPerDistinctAcceptedTime(){
        var c=constants();c[TDELAY1.ordinal()]=c[TDELAY2.ordinal()]=c[TMSSBRK.ordinal()]=0;var k=initialized(c,devices());
        assertEquals(1,k.advance(0,14).size());assertTrue(k.advance(0,14).isEmpty());assertEquals(1,k.advance(.0005,14).size());
    }
    @Test void reinitializationRestoresStatusesAndClearsAllTimerHistory(){
        var k=initialized(constants(),devices());k.advance(0,14);k.advance(.003,14);k.advance(.01,14);
        k.initialize(devices(),true);assertEquals(0,k.time(),0);assertEquals(-1,k.getNamedStates().get("fastStart"));
        for(int i=0;i<10;i++){assertEquals(devices().get(i).inService(),k.slots().get(i).inService());assertEquals(-1,k.slots().get(i).breakerStart(),0);}
        assertThrows(UnsupportedOperationException.class,()->k.getNamedStates().clear());assertThrows(UnsupportedOperationException.class,()->k.slots().clear());
        var bad=new RepccShuntController(data(constants(),1,devices()));assertThrows(IllegalArgumentException.class,()->bad.initialize(List.of(),true));
    }
}
