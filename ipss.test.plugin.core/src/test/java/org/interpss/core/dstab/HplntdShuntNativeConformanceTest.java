package org.interpss.core.dstab;

import static org.junit.jupiter.api.Assertions.*;
import java.nio.file.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.interpss.core.dstab.reference.HplntdSwitchingNativeReference;
import org.interpss.dstab.renewable.*;
import com.interpss.dstab.renewable.*;

/** Shared switching-kernel evidence, not physical Y/import acceptance. */
public class HplntdShuntNativeConformanceTest {
    private static List<Map<String,Double>> rows(String text){
        var lines=text.lines().toList();String[] header=lines.getFirst().split(",");var result=new ArrayList<Map<String,Double>>();
        for(String line:lines.subList(1,lines.size())){String[] values=line.split(",");assertEquals(header.length,values.length);var row=new LinkedHashMap<String,Double>();for(int i=0;i<header.length;i++)row.put(header[i],Double.parseDouble(values[i]));result.add(row);}return result;
    }
    private record Run(RepccShuntController controller,List<RepccShuntController.Action> actions,Map<Integer,List<RepccShuntController.Slot>> slots,double[] timerErrors,double dischargeDifference) {}
    private static Run replay(HplntdSwitchingNativeReference.Profile p)throws Exception {
        var data=p.parameters();var raw=rows(p.body());var status=rows(p.status());var first=status.getFirst();var devices=new ArrayList<RepccShuntController.Device>();
        for(var key:data.shunts())devices.add(new RepccShuntController.Device(key,first.get(key.id()+"_B_NOM")/100,first.get(key.id()+"_STATUS")==1));
        var controller=new RepccShuntController(data);controller.initialize(devices,!p.name().contains("missing-branch"));
        var actions=new ArrayList<RepccShuntController.Action>();var snapshots=new HashMap<Integer,List<RepccShuntController.Slot>>();double[] maximum=new double[12];double discharge=0;
        var trace=new StringBuilder("time_s,Q_SWITCH_MVAR");for(int i=0;i<12;i++)trace.append(",kernel_timer_").append(i).append(",native_timer_").append(i);
        for(int i=0;i<10;i++)trace.append(",kernel_discharge_").append(i).append(",native_discharge_").append(i).append(",kernel_status_").append(i);trace.append('\n');
        for(var row:raw){
            double time=row.get("time_s");if(time>=0)actions.addAll(controller.advance(time,row.get("PLANT_VAR13")));var states=controller.getNamedStates();
            double[] timers=new double[12];timers[0]=states.get("slowStart");timers[1]=states.get("fastStart");for(int i=0;i<10;i++)timers[i+2]=controller.slots().get(i).breakerStart();
            trace.append(time).append(',').append(row.get("PLANT_VAR13"));
            for(int i=0;i<12;i++){double expected=row.get("PLANT_VAR"+(14+i));maximum[i]=Math.max(maximum[i],Math.abs(timers[i]-expected));trace.append(',').append(timers[i]).append(',').append(expected);}
            for(int i=0;i<10;i++){var slot=controller.slots().get(i);double expected=row.get("PLANT_VAR"+(26+i));discharge=Math.max(discharge,Math.abs(slot.dischargeStart()-expected));trace.append(',').append(slot.dischargeStart()).append(',').append(expected).append(',').append(slot.inService()?1:0);}
            trace.append('\n');if(time>=0)snapshots.put((int)Math.round(time/p.step()),controller.slots());
        }
        assertEquals(p.samples(),raw.size());assertEquals(p.statusSamples(),status.size());
        Path output=Path.of("target/hplntd-switching-kernel",p.name()+".csv");Files.createDirectories(output.getParent());Files.writeString(output,trace);
        return new Run(controller,List.copyOf(actions),snapshots,maximum,discharge);
    }
    @Test void thresholdBreakerTimersAndAllTenStatusesMatchFourteenProfiles()throws Exception {
        int profiles=0;
        for(var p:HplntdSwitchingNativeReference.profiles())if(!p.zeroDelay()){
            var r=replay(p);var d=p.parameters();var status=rows(p.status());
            for(double error:r.timerErrors())assertTrue(error<1e-10,p.name()+" threshold/breaker errors="+Arrays.toString(r.timerErrors()));
            for(int tick=0;tick<status.size();tick++)for(int i=0;i<10;i++)assertEquals(status.get(tick).get(d.shunts().get(i).id()+"_STATUS")==1,r.slots().get(tick).get(i).inService(),p.name()+" tick="+tick+" slot="+i);
            int expected=p.name().contains("positive")||p.name().contains("negative")?10:0;assertEquals(expected,r.actions().size());
            var order=new ArrayList<String>();for(int type=0;type<2;type++)for(int i=1;i<=5;i++)order.add((p.name().contains("negative")== (type==0)?"C":"R")+i);
            if(expected>0)assertEquals(order,r.actions().stream().map(a->a.key().id()).toList());
            System.out.println("Hybrid switching "+p.name()+" timerErrors="+Arrays.toString(r.timerErrors())+" retainedDischargeDifference="+r.dischargeDifference()+" actions="+r.actions());profiles++;
        }
        assertEquals(14,profiles);
    }
    @Test void zeroDelayKeepsBothOriginObservationSidesAndDoesNotPretendTimerParity()throws Exception {
        int profiles=0;
        for(var p:HplntdSwitchingNativeReference.profiles())if(p.zeroDelay()){
            var r=replay(p);var status=rows(p.status());var d=p.parameters();assertEquals(10,r.actions().size());
            assertEquals(0,r.actions().getFirst().completedAt(),0,"Kernel origin action is explicit");
            assertTrue(status.getFirst().get("time_s")<0,"Initial device query retains its actual prehistory clock");
            for(int i=0;i<10;i++)assertEquals(i>=5,status.getFirst().get(d.shunts().get(i).id()+"_STATUS")==1,"Initial query is before first model evaluation");
            assertFalse(r.slots().get(0).get(5).inService(),"Kernel's post-evaluation origin is separately retained");
            for(int tick=1;tick<status.size();tick++)for(int i=0;i<10;i++)assertEquals(status.get(tick).get(d.shunts().get(i).id()+"_STATUS")==1,r.slots().get(tick).get(i).inService(),p.name()+" positive tick="+tick);
            assertTrue(r.timerErrors()[1]>=1,"Native zero-delay timer remains unused; no state is fitted to make it look equal");profiles++;
        }
        assertEquals(2,profiles);
    }
    @Test void physicalMvarInputMustNotBeDividedByLivePlantMva()throws Exception {
        int checked=0;
        for(var p:HplntdSwitchingNativeReference.profiles())if(p.name().contains("positive-fast")){
            var d=p.parameters();var first=rows(p.status()).getFirst();var devices=new ArrayList<RepccShuntController.Device>();
            for(var key:d.shunts())devices.add(new RepccShuntController.Device(key,first.get(key.id()+"_B_NOM")/100,first.get(key.id()+"_STATUS")==1));
            var wrong=new RepccShuntController(d);wrong.initialize(devices,true);var events=new ArrayList<RepccShuntController.Action>();
            for(var row:rows(p.body()))if(row.get("time_s")>=0)events.addAll(wrong.advance(row.get("time_s"),row.get("PLANT_VAR13")/row.get("PLANT_VAR6")));
            assertTrue(events.isEmpty(),"Wrong pu conversion misses all ten native actions");checked++;
        }
        assertEquals(2,checked);
    }
}
