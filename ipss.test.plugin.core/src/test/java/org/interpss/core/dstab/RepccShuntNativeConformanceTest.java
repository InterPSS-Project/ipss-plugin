package org.interpss.core.dstab;

import static org.junit.jupiter.api.Assertions.*;
import java.nio.file.*;
import java.util.*;
import org.interpss.core.dstab.reference.RepccShuntNativeReference;
import org.interpss.dstab.renewable.*;
import org.junit.jupiter.api.Test;

/** Native switch-event/timer evidence; discharge counterexamples are not accepted as parity. */
public class RepccShuntNativeConformanceTest {
    private record Event(String id,boolean active,double time) {}
    private static List<Map<String,Double>> rows(String text){
        var lines=text.lines().toList();String[] headers=lines.getFirst().split(",");var result=new ArrayList<Map<String,Double>>();
        for(String line:lines.subList(1,lines.size())){String[] fields=line.split(",");assertEquals(headers.length,fields.length);var row=new HashMap<String,Double>();
            for(int i=0;i<headers.length;i++){double v=Double.parseDouble(fields[i]);assertTrue(Double.isFinite(v));row.put(headers[i],v);}result.add(row);}
        return result;
    }
    private static RepccShuntController initialized(RepccShuntNativeReference.Profile p,List<Map<String,Double>> status){
        var devices=new ArrayList<RepccShuntController.Device>();var data=p.parameters();
        for(var key:data.shunts())if(key.bus()!=0)devices.add(new RepccShuntController.Device(key,status.getFirst().get(key.id()+"_B_NOM")/50,
                status.getFirst().get(key.id()+"_STATUS")==1));
        var controller=new RepccShuntController(data);controller.initialize(devices,true);return controller;
    }
    private static List<Event> events(RepccData data,List<Map<String,Double>> status){
        var events=new ArrayList<Event>();for(int i=1;i<status.size();i++)for(var key:data.shunts()){
            double before=status.get(i-1).get(key.id()+"_STATUS"),after=status.get(i).get(key.id()+"_STATUS");
            if(before!=after)events.add(new Event(key.id(),after==1,status.get(i).get("time_s")));
        }return events;
    }
    @Test void everyThresholdAndBreakerTimerAndAllPhysicalStatusesMatchEightProfiles() throws Exception {
        int profiles=0;
        for(var p:RepccShuntNativeReference.profiles()){
            if(p.lockoutDiscrepancy())continue;profiles++;
            var raw=rows(p.body());var status=rows(p.status());var data=p.parameters();var controller=initialized(p,status);
            var actual=new ArrayList<RepccShuntController.Action>();var snapshots=new HashMap<Integer,List<RepccShuntController.Slot>>();
            double[] max=new double[12];int history=0,duplicates=0;double previous=-Double.MAX_VALUE;
            StringBuilder trace=new StringBuilder("time_s,Q_MACHINE,IPSS_SLOW,NATIVE_SLOW,IPSS_FAST,NATIVE_FAST");
            for(int i=0;i<10;i++)trace.append(",IPSS_BREAKER").append(i).append(",NATIVE_BREAKER").append(i);
            for(int i=0;i<10;i++)trace.append(",IPSS_STATUS").append(i);trace.append('\n');
            for(var row:raw){double time=row.get("time_s");if(time<0)history++;else actual.addAll(controller.advance(time,row.get("PLANT_VAR13")/50));
                if(time==previous)duplicates++;previous=time;var state=controller.getNamedStates();double[] timers=new double[12];
                timers[0]=state.get("slowStart");timers[1]=state.get("fastStart");for(int i=0;i<10;i++)timers[i+2]=controller.slots().get(i).breakerStart();
                for(int i=0;i<12;i++)max[i]=Math.max(max[i],Math.abs(timers[i]-row.get("PLANT_VAR"+(14+i))));
                if(time>=0)snapshots.put((int)Math.round(time/p.step()),controller.slots());
                trace.append(time).append(',').append(row.get("PLANT_VAR13")/50);
                for(int i=0;i<12;i++)trace.append(',').append(timers[i]).append(',').append(row.get("PLANT_VAR"+(14+i)));
                for(var slot:controller.slots())trace.append(',').append(slot.inService()?1:0);trace.append('\n');
            }
            Path path=Path.of("target/repcc-shunt-replay",p.name()+".csv");Files.createDirectories(path.getParent());Files.writeString(path,trace.toString());
            assertEquals(p.samples(),raw.size());assertEquals(p.statusSamples(),status.size());assertEquals(2,history);assertEquals(status.size()-2,duplicates);
            assertEquals(32,controller.getNamedStates().size());for(double value:max)assertTrue(value<1e-10,p.name()+" timer maxima="+Arrays.toString(max));
            for(int tick=0;tick<status.size();tick++)for(int i=0;i<10;i++)assertEquals(status.get(tick).get(data.shunts().get(i).id()+"_STATUS")==1,snapshots.get(tick).get(i).inService(),p.name()+" tick="+tick+" slot="+i);
            var expected=events(data,status);assertEquals(5,expected.size());assertEquals(expected.size(),actual.size());
            for(int i=0;i<actual.size();i++){assertEquals(expected.get(i).id(),actual.get(i).key().id());assertEquals(expected.get(i).active(),actual.get(i).inService());assertEquals(expected.get(i).time(),actual.get(i).completedAt(),1e-10);}
            System.out.println("REPCC shunts "+p.name()+" timers="+Arrays.toString(max)+" events="+actual);
        }
        assertEquals(8,profiles);
    }
    @Test void nativeDischargeViolationIsPreservedWithoutChangingThePublishedLockout(){
        int profiles=0;
        for(var p:RepccShuntNativeReference.profiles()){
            if(!p.lockoutDiscrepancy())continue;profiles++;
            var raw=rows(p.body());var status=rows(p.status());var d=p.parameters();var controller=initialized(p,status);var actual=new ArrayList<RepccShuntController.Action>();
            for(var row:raw)if(row.get("time_s")>=0)actual.addAll(controller.advance(row.get("time_s"),row.get("PLANT_VAR13")/50));
            var expected=events(d,status);assertEquals(expected.size(),actual.size());int mismatches=0;
            for(int i=0;i<expected.size();i++){
                var nativeEvent=expected.get(i);var own=actual.get(i);assertEquals(nativeEvent.id(),own.key().id());assertEquals(nativeEvent.active(),own.inService());
                if(own.key().id().startsWith("C")&&own.inService()){
                    mismatches++;double opened=actual.stream().filter(a->a.key().equals(own.key())&&!a.inService()&&a.completedAt()<own.completedAt()).findFirst().orElseThrow().completedAt();
                    assertTrue(nativeEvent.time()-opened<.04,"Native reclose violates declared 150-ms discharge time");
                    assertTrue(own.requestedAt()-opened>=d.value(RepccData.Parameter.TOUT));
                    assertTrue(own.completedAt()-nativeEvent.time()>.1,"Do not hide the substantive gap with a step-size tolerance");
                }else assertEquals(nativeEvent.time(),own.completedAt(),1e-10);
            }
            assertEquals(5,mismatches);assertEquals(.15,d.value(RepccData.Parameter.TOUT));
        }
        assertEquals(2,profiles);
    }
}
