package org.interpss.core.dstab;

import static org.junit.jupiter.api.Assertions.*;
import static com.interpss.dstab.renewable.HplntdData.Parameter.*;
import java.nio.file.*;
import java.util.*;
import org.apache.commons.math3.complex.Complex;
import org.interpss.CorePluginTestSetup;
import org.interpss.core.dstab.reference.*;
import org.interpss.dstab.renewable.*;
import com.interpss.dstab.renewable.*;
import com.interpss.dstab.DStabObjectFactory;
import com.interpss.core.acsc.fault.SimpleFaultCode;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Retain exact native counterexamples; enforce the published safety contract
 * independently. No native fault-waveform or discharge parity is claimed. */
public class HplntdDischargeNativeConformanceTest extends CorePluginTestSetup {
    private static List<Map<String,Double>> rows(String text){return HplntdSwitchingNativeSmibTest.rows(text);}
    private static RepccShuntController schedule(HplntdSwitchingNativeReference.Profile p){
        var first=rows(p.status()).getFirst();var devices=new ArrayList<RepccShuntController.Device>();
        for(var key:p.parameters().shunts())devices.add(new RepccShuntController.Device(key,first.get(key.id()+"_B_NOM")/100,first.get(key.id()+"_STATUS")==1));
        var k=new RepccShuntController(p.parameters());k.initialize(devices,true);return k;
    }
    @Test void changingOnlyToutLeavesEveryRawNativeChannelAndStatusIdentical(){
        var profiles=HplntdDischargeNativeReference.profiles();assertEquals(6,profiles.size());
        for(double h:new double[]{.0005,.00025}){
            var same=profiles.stream().filter(p->p.step()==h).toList();assertEquals(3,same.size());
            var first=same.getFirst();var base=first.parameters().constants();
            for(var p:same){
                assertEquals(first.body(),p.body(),"All 89 raw channels, including history/duplicates, are unchanged");assertEquals(first.status(),p.status());assertEquals(first.network(),p.network());
                var constants=p.parameters().constants();for(int i=0;i<61;i++)if(i!=TOUT.ordinal())assertEquals(base[i],constants[i],0);
                assertEquals(p.samples(),rows(p.body()).size());assertEquals(p.statusSamples(),rows(p.status()).size());
            }
            assertEquals(Set.of(0.,.03,.15),new HashSet<>(same.stream().map(p->p.parameters().value(TOUT)).toList()));
        }
    }
    @Test void longDelayNativeCapacitorsRecloseBeforeTheirPublishedDischargeTime(){
        int profiles=0;
        for(var p:HplntdDischargeNativeReference.profiles())if(p.name().contains("long")){
            var status=rows(p.status());var previous=status.getFirst();var opened=new HashMap<String,Double>();var reclosed=new HashMap<String,Double>();
            for(var row:status){
                for(int i=1;i<=5;i++){String id="C"+i,field=id+"_STATUS";if(previous.get(field)==1&&row.get(field)==0)opened.put(id,row.get("time_s"));
                    if(previous.get(field)==0&&row.get(field)==1&&opened.containsKey(id))reclosed.put(id,row.get("time_s"));}
                previous=row;
            }
            assertEquals(5,reclosed.size());for(var id:reclosed.keySet()){
                double duration=reclosed.get(id)-opened.get(id);assertTrue(duration<p.parameters().value(TOUT),"Retain the functional counterexample, not a plotting-coordinate assumption");
                System.out.println("Hybrid native discharge "+p.name()+" "+id+" opened="+opened.get(id)+" reclosed="+reclosed.get(id)+" offDuration="+duration);
            }profiles++;
        }
        assertEquals(2,profiles);
    }
    @Test void directSignReversalRetainsNativeTimerButPublishedKernelRestartsIt(){
        int checked=0;
        for(var p:HplntdDischargeNativeReference.profiles()){
            var k=schedule(p);boolean negative=false,returned=false;
            for(var row:rows(p.body())){
                double time=row.get("time_s"),q=row.get("PLANT_VAR13");if(time<0)continue;k.advance(time,q);
                if(!negative&&q<-10){
                    assertEquals(0,row.get("PLANT_VAR15"),0);assertEquals(time,row.get("PLANT_VAR16"),0,"Native requests the first capacitor opening immediately, retaining its prior fast timer");
                    assertEquals(time,k.getNamedStates().get("fastStart"),0);assertEquals(-1,k.slots().getFirst().breakerStart(),0,"Published sign-specific persistence restarts before requesting a breaker action");negative=true;
                }else if(negative&&!returned&&q>10){
                    assertEquals(0,row.get("PLANT_VAR15"),0);assertEquals(time,k.getNamedStates().get("fastStart"),0);returned=true;
                }
            }
            assertTrue(negative&&returned);checked++;
        }
        assertEquals(6,checked);
    }
    @Test void recordedInputPublishedKernelRetainsDischargeAndBreakerDelays()throws Exception{
        int profiles=0;
        for(var p:HplntdDischargeNativeReference.profiles()){
            var k=schedule(p);var queried=rows(p.status());var opened=new HashMap<String,Double>();int reclosed=0;var trace=new StringBuilder("time_s,Q_SWITCH_MVAR,IPSS_slow,NATIVE_slow,IPSS_fast,NATIVE_fast");
            for(int i=0;i<10;i++)trace.append(",IPSS_status_").append(i).append(",NATIVE_QUERY_status_").append(i).append(",IPSS_breaker_").append(i).append(",NATIVE_breaker_").append(i).append(",IPSS_discharge_").append(i).append(",NATIVE_discharge_").append(i);trace.append('\n');
            for(var row:rows(p.body())){
                double time=row.get("time_s");var actions=time<0?List.<RepccShuntController.Action>of():k.advance(time,row.get("PLANT_VAR13"));
                for(var action:actions)if(action.key().id().startsWith("C")){
                    String id=action.key().id();if(!action.inService())opened.put(id,action.completedAt());else if(opened.containsKey(id)){
                        assertTrue(action.requestedAt()>=opened.get(id)+p.parameters().value(TOUT));assertTrue(action.completedAt()>=action.requestedAt()+p.parameters().value(TMSSBRK));reclosed++;
                    }
                }
                var states=k.getNamedStates();trace.append(time).append(',').append(row.get("PLANT_VAR13")).append(',').append(states.get("slowStart")).append(',').append(row.get("PLANT_VAR14")).append(',').append(states.get("fastStart")).append(',').append(row.get("PLANT_VAR15"));
                var status=queried.get(Math.max(0,(int)Math.round(time/p.step())));
                for(int i=0;i<10;i++){var slot=k.slots().get(i);trace.append(',').append(slot.inService()?1:0).append(',').append(status.get(slot.key().id()+"_STATUS")).append(',').append(slot.breakerStart()).append(',').append(row.get("PLANT_VAR"+(16+i))).append(',').append(slot.dischargeStart()).append(',').append(row.get("PLANT_VAR"+(26+i)));}trace.append('\n');
            }
            assertEquals(5,reclosed);Path out=Path.of("target/hplntd-discharge-kernel",p.name()+".csv");Files.createDirectories(out.getParent());Files.writeString(out,trace);profiles++;
        }
        assertEquals(6,profiles);
    }
    @Test void importedThreeCycleFaultEnforcesDischargeBeforePhysicalReclose(@TempDir Path dir)throws Exception{
        int profiles=0;
        for(var p:HplntdDischargeNativeReference.profiles()){
            var f=HplntdSwitchingNativeSmibTest.load(p,dir.resolve(p.name()),false);var algo=f.algo();var net=algo.getNetwork();var group=f.group();algo.setTotalSimuTimeSec(.3);
            net.addDynamicEvent(DStabObjectFactory.createBusFaultEvent("Bus2",net,SimpleFaultCode.GROUND_3P,new Complex(0,1),null,.03,.05),"three-cycle");assertTrue(algo.initialization());
            var previous=new HashMap<String,Boolean>();for(var key:p.parameters().shunts())previous.put(key.id(),net.getBus("Bus2").getCompensator(key.id()).isActive());
            var opened=new HashMap<String,Double>();int reclosed=0;var trace=new StringBuilder("time_s,V1,V2,V3,V4");for(var key:p.parameters().shunts())trace.append(',').append(key.id()).append("_STATUS,").append(key.id()).append("_DISCHARGE");trace.append('\n');
            for(int tick=0;tick<=Math.round(.3/p.step());tick++){
                double time=group.kernel().time();trace.append(time);for(int i=1;i<=4;i++){double voltage=net.getBus("Bus"+i).getVoltageMag();assertTrue(Double.isFinite(voltage));trace.append(',').append(voltage);}
                for(var slot:group.shuntController().slots()){
                    String id=slot.key().id();boolean physical=net.getBus("Bus2").getCompensator(id).isActive();assertEquals(slot.inService(),physical);boolean before=previous.get(id);
                    if(id.startsWith("C")){if(before&&!physical)opened.put(id,time);else if(!before&&physical&&opened.containsKey(id)){
                        assertTrue(time>=opened.get(id)+p.parameters().value(TOUT)+p.parameters().value(TMSSBRK),p.name()+" premature physical reclose "+id);reclosed++;
                    }}
                    previous.put(id,physical);trace.append(',').append(physical?1:0).append(',').append(slot.dischargeStart());
                }
                for(double state:group.kernel().states())assertTrue(Double.isFinite(state));trace.append('\n');if(tick<Math.round(.3/p.step()))assertTrue(algo.solveDEqnStep(true));
            }
            assertEquals(5,reclosed,p.name());Path out=Path.of("target/hplntd-discharge-coupled",p.name()+".csv");Files.createDirectories(out.getParent());Files.writeString(out,trace);profiles++;
        }
        assertEquals(6,profiles);
    }
}
