package org.interpss.core.dstab;

import static org.junit.jupiter.api.Assertions.*;
import java.nio.file.*;
import java.util.*;
import org.apache.commons.math3.complex.Complex;
import org.interpss.CorePluginTestSetup;
import org.interpss.core.dstab.reference.*;
import org.interpss.dstab.renewable.*;
import com.interpss.dstab.renewable.*;
import org.interpss.fadapter.builder.*;
import com.interpss.dstab.svc.*;
import org.interpss.fadapter.psse.*;
import com.interpss.dstab.algo.dynamic.*;
import com.interpss.dstab.*;
import com.interpss.dstab.algo.*;
import com.interpss.dstab.cache.StateMonitor;
import com.interpss.dstab.common.DStabSimuException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Physical network/import evidence with explicit clock and origin diagnostics. */
public class HplntdSwitchingNativeSmibTest extends CorePluginTestSetup {
    static List<Map<String,Double>> rows(String text){
        var lines=text.lines().toList();var header=lines.getFirst().split(",");var result=new ArrayList<Map<String,Double>>();
        for(String line:lines.subList(1,lines.size())){var values=line.split(",");assertEquals(header.length,values.length);var row=new LinkedHashMap<String,Double>();for(int i=0;i<header.length;i++)row.put(header[i],Double.parseDouble(values[i]));result.add(row);}return result;
    }
    record Fixture(DynamicSimuAlgorithm algo,HplntdCoordinator group){}
    static Fixture load(HplntdSwitchingNativeReference.Profile p,Path dir,boolean initialize)throws Exception{
        return load(p,dir,initialize,true);
    }
    private static Fixture load(HplntdSwitchingNativeReference.Profile p,Path dir,boolean initialize,boolean attachGroup)throws Exception{
        Files.createDirectories(dir);Path raw=dir.resolve("network.raw");Files.writeString(raw,p.network());
        Path dyr=dir.resolve("network.dyr");Files.writeString(dyr,attachGroup?p.input():p.input().lines().filter(s->!s.contains("HPLNTDU")&&!s.contains("REAXDU")).reduce("",(a,b)->a+b+"\n"));
        var ctx=new PSSEMultiFileLoader().loadDStab(raw.toString());var net=ctx.getDStabilityNet();var algo=ctx.getDynSimuAlgorithm();
        var parser=new PSSEDStabDirectParser(new DStabNetworkBuilder(net)).setStrictImport(true);parser.parseDynFile(dyr.toString());assertTrue(parser.getLastImportReport().isStrictlyComplete());
        net.setAllowGenWithoutMach(true);algo.getAclfAlgorithm().getDataCheckConfig().setAllowGenWithoutMachine(true);algo.getAclfAlgorithm().setTolerance(1e-10);assertTrue(algo.getAclfAlgorithm().loadflow());
        var group=attachGroup?unit(algo,1).getReeca1Controller().getHybridController().owner():null;if(attachGroup)assertSame(group,unit(algo,4).getReeca1Controller().getHybridController().owner());
        algo.setSimuMethod(DynamicSimuMethod.MODIFIED_EULER);algo.setSimuStepSec(p.step());algo.setTotalSimuTimeSec(.1);algo.setSimuOutputHandler(new StateMonitor());
        if(initialize)assertTrue(algo.initialization());return new Fixture(algo,group);
    }
    private static Regca1Model unit(DynamicSimuAlgorithm algo,int bus){return (Regca1Model)((DStabGen)algo.getNetwork().getBus("Bus"+bus).getContributeGen("1")).getDynamicGenDevice();}
    private static double[] snapshot(Fixture f){
        double[] result=new double[26];for(int i=0;i<4;i++)result[i]=f.algo().getNetwork().getBus("Bus"+(i+1)).getVoltageMag();
        for(int i=0;i<2;i++){var c=unit(f.algo(),i==0?1:4);double scale=c.getParentGen().getMvaBase()/100;result[4+2*i]=c.getDStabBus().getVoltageMag()*c.getIp()*scale;result[5+2*i]=c.getDStabBus().getVoltageMag()*c.getIq()*scale;System.arraycopy(f.group().ports().get(i).states(),0,result,21+2*i,2);}
        System.arraycopy(f.group().kernel().states(),0,result,8,13);result[25]=f.algo().getNetwork().getBus("Bus2").getFreq()-1;return result;
    }
    private static final class ObservedSolver extends PsseDStabSolver {
        final Fixture f;final double step;final Map<Integer,double[]> start=new HashMap<>();
        ObservedSolver(Fixture f,double step){super(f.algo());this.f=f;this.step=step;}
        @Override public void diffEqnIntegrationStep(double time,double dt,DynamicSimuMethod method,int flag)throws DStabSimuException{
            if(flag==0&&dt>0)start.put((int)Math.round(time/step),snapshot(f));super.diffEqnIntegrationStep(time,dt,method,flag);
        }
    }
    private static RepccShuntController schedule(HplntdSwitchingNativeReference.Profile p,Map<String,Double> initial){
        var devices=new ArrayList<RepccShuntController.Device>();for(var key:p.parameters().shunts())devices.add(new RepccShuntController.Device(key,initial.get(key.id()+"_B_NOM")/100,initial.get(key.id()+"_STATUS")==1));
        var result=new RepccShuntController(p.parameters());result.initialize(devices,!p.name().contains("missing-branch"));return result;
    }
    @Test void importedSwitchingProfilesRetainEveryNativeSampleAndPhysicalStatus(@TempDir Path dir)throws Exception{
        int profiles=0,accepted=0,clockDiagnostics=0;
        for(var p:HplntdSwitchingNativeReference.profiles()){
            var f=load(p,dir.resolve(p.name()),false);var algo=f.algo();var net=algo.getNetwork();var g=f.group();var solver=new ObservedSolver(f,p.step());algo.setSolver(solver);assertTrue(algo.initialization());
            assertTrue(g.acceptedEndpoint().isEmpty());var status=rows(p.status());int ticks=(int)Math.round(.1/p.step());var actual=new ArrayList<double[]>();var physicalStatuses=new ArrayList<boolean[]>();var clocks=new ArrayList<Double>();int mismatches=0;double firstMismatch=-1;
            var statuses=new StringBuilder("native_time_s,ipss_time_s");for(var key:p.parameters().shunts())statuses.append(",IPSS_").append(key.id()).append(",NATIVE_").append(key.id());statuses.append('\n');
            for(int tick=0;tick<=ticks;tick++){
                actual.add(snapshot(f));clocks.add(g.kernel().time());var physical=new boolean[10];int slot=0;statuses.append(status.get(tick).get("time_s")).append(',').append(g.kernel().time());
                for(var key:p.parameters().shunts()){
                    boolean value=net.getBus("Bus"+key.bus()).getCompensator(key.id()).isActive(),expected=status.get(tick).get(key.id()+"_STATUS")==1;
                    physical[slot++]=value;
                    statuses.append(',').append(value?1:0).append(',').append(expected?1:0);if(value!=expected){mismatches++;if(firstMismatch<0)firstMismatch=tick*p.step();}
                }
                physicalStatuses.add(physical);statuses.append('\n');if(tick<ticks)assertTrue(algo.solveDEqnStep(true));
            }
            String[] channels=new String[26];for(int i=0;i<4;i++)channels[i]="V"+(i+1);System.arraycopy(new String[]{"UNIT1_P_PU","UNIT1_Q_PU","UNIT4_P_PU","UNIT4_Q_PU"},0,channels,4,4);
            for(int i=0;i<13;i++)channels[8+i]="PLANT_STATE"+i;for(int i=0;i<2;i++){channels[21+i]="UNIT1_STATE"+i;channels[23+i]="UNIT4_STATE"+i;}channels[25]="FREQ2";
            double[] max=new double[26],at=new double[26];int history=0,duplicates=0;Double previous=null;var trace=new StringBuilder("native_time_s,ipss_time_s,side");
            var nativeClock=schedule(p,status.getFirst());var solverClock=schedule(p,status.getFirst());
            for(String channel:channels)trace.append(",IPSS_").append(channel).append(",NATIVE_").append(channel);trace.append('\n');
            for(var row:rows(p.body())){
                double time=row.get("time_s");int tick=Math.max(0,(int)Math.round(time/p.step()));boolean duplicate=previous!=null&&time==previous;previous=time;
                double[] values=duplicate&&tick>0?solver.start.get(tick):actual.get(tick);assertNotNull(values);if(time<0)history++;if(duplicate)duplicates++;
                trace.append(time).append(',').append(tick*p.step()).append(',').append(time<0?-1:duplicate?1:0);
                for(int i=0;i<26;i++){assertTrue(Double.isFinite(values[i]));double reference=row.get(channels[i]),error=Math.abs(values[i]-reference);if(error>max[i]){max[i]=error;at[i]=time;}trace.append(',').append(values[i]).append(',').append(reference);}trace.append('\n');
                if(time>=0){
                    nativeClock.advance(time,row.get("PLANT_VAR13"));solverClock.advance(clocks.get(tick),row.get("PLANT_VAR13"));
                    for(int i=0;i<10;i++){
                        if(tick>0||!p.zeroDelay())assertEquals(status.get(tick).get(p.parameters().shunts().get(i).id()+"_STATUS")==1,nativeClock.slots().get(i).inService(),p.name()+" recorded-clock tick="+tick+" slot="+i);
                        assertEquals(physicalStatuses.get(tick)[i],solverClock.slots().get(i).inService(),p.name()+" identical-input solver-clock tick="+tick+" slot="+i);
                    }
                }
            }
            assertEquals(2,history);assertEquals(ticks-1+(p.zeroDelay()?1:0),duplicates);
            Path output=Path.of("target/hplntd-switching-coupled",p.name());Files.createDirectories(output.getParent());Files.writeString(output.resolveSibling(p.name()+"-paired.csv"),trace);Files.writeString(output.resolveSibling(p.name()+"-statuses.csv"),statuses);
            System.out.println("Hybrid switching coupled "+p.name()+" mismatches="+mismatches+" first="+firstMismatch+" errors="+Arrays.toString(max)+" times="+Arrays.toString(at));profiles++;
            if(p.coupledAccepted()){
                assertEquals(0,mismatches,p.name());double[] bounds=p.coupledBounds();assertEquals(26,bounds.length);
                for(int i=0;i<26;i++)assertTrue(max[i]<bounds[i],p.name()+" "+channels[i]+" error="+max[i]+" at="+at[i]);accepted++;
            }else{assertTrue(mismatches>0,"Clock/origin discriminator must remain explicit");clockDiagnostics++;}
        }
        assertEquals(16,profiles);assertEquals(8,accepted);assertEquals(8,clockDiagnostics);
    }
    @Test void pendingSharedStagesCannotSwitchAndRepeatedAcceptedCallbacksAreIdempotent(@TempDir Path dir)throws Exception{
        var p=HplntdSwitchingNativeReference.profiles().getFirst();var f=load(p,dir,true);var a=f.group().ports().getFirst();var b=f.group().ports().getLast();
        a.step(p.step(),0);assertThrows(IllegalStateException.class,()->f.group().acceptedEndpoint());b.step(p.step(),0);a.step(p.step(),1);assertThrows(IllegalStateException.class,()->f.group().acceptedEndpoint());
        b.step(p.step(),1);f.group().acceptedEndpoint();var state=f.group().getNamedStates();assertTrue(f.group().acceptedEndpoint().isEmpty());assertEquals(state,f.group().getNamedStates());
        assertEquals(49,state.size());assertThrows(UnsupportedOperationException.class,state::clear);assertEquals(32,unit(f.algo(),1).getNamedStates().keySet().stream().filter(n->n.startsWith("HPLNTDU.switching.")).count());
    }
    @Test void sharedPhysicalBoundaryUsesExactConductanceAndPersistsThroughRebuild(@TempDir Path dir)throws Exception{
        var p=HplntdSwitchingNativeReference.profiles().getFirst();var raw=HplntdTypedRuntimeSmibTest.writeNetwork(dir);var net=new PSSEMultiFileLoader().loadDStab(raw.toString()).getDStabilityNet();
        var data=p.parameters();var topology=new AclfNetworkBuilder(net);var nominal=new HashMap<RepccData.Shunt,Complex>();
        for(int i=0;i<10;i++){var key=data.shunts().get(i);double b=i<5?.0001:-.0001,g=.00002*(i+1);topology.addFixedShunt("Bus2",key.id(),i>=5,g,b,null);nominal.put(key,new Complex(g,b));}
        var boundary=new PlantShuntNetworkBoundary(data,net,true);assertThrows(IllegalArgumentException.class,()->boundary.initialize(Map.of()));boundary.initialize(nominal);
        assertThrows(IllegalStateException.class,()->boundary.advance(0,14));net.formYMatrix4DStab();int index=net.getBus("Bus2").getSortNumber();var initial=net.getYMatrix().getA(index,index);var conductance=net.getBus("Bus2").getShuntY();
        boundary.advance(0,14);boundary.advance(.0031,14);var actions=boundary.advance(.0072,14);assertEquals(1,actions.size());var key=data.shunts().get(5);assertEquals(key,actions.getFirst().key());var delta=nominal.get(key).negate();
        assertTrue(net.getYMatrix().getA(index,index).subtract(initial.add(delta)).abs()<1e-12);assertEquals(conductance.add(new Complex(delta.getReal(),0)),net.getBus("Bus2").getShuntY());
        assertTrue(boundary.advance(.0072,14).isEmpty());net.formYMatrix4DStab();assertTrue(net.getYMatrix().getA(index,index).subtract(initial.add(delta)).abs()<1e-12);
        net.getBus("Bus2").getCompensator(key.id()).setStatus(true);assertThrows(IllegalStateException.class,()->boundary.advance(.008,14));assertEquals(.0072,boundary.controller().time(),0);
    }
    @Test void zeroDelayOriginSwitchesOnceAfterCompensationAndPreservesCallerCurrents(@TempDir Path dir)throws Exception{
        var profiles=HplntdSwitchingNativeReference.profiles();var disabled=profiles.stream().filter(p->p.name().equals("switching-disabled-coarse")).findFirst().orElseThrow();
        var zero=profiles.stream().filter(p->p.name().equals("switching-zero-delay-coarse")).findFirst().orElseThrow();var baseline=load(disabled,dir.resolve("baseline"),true);
        var net0=baseline.algo().getNetwork();int index=net0.getBus("Bus2").getSortNumber();var y0=net0.getYMatrix().getA(index,index);var initial=baseline.group().kernel().states();var currents=new Hashtable<>(net0.getCustomBusCurrInjHashtable());
        for(boolean supplied:new boolean[]{false,true}){
            var f=load(zero,dir.resolve("supplied"+supplied),false);var net=f.algo().getNetwork();var provided=new Hashtable<>(currents);if(supplied)net.setCustomBusCurrInjHashtable(provided);
            assertTrue(f.algo().initialization());assertFalse(net.getBus("Bus2").getCompensator("R1").isActive());assertTrue(net.getBus("Bus2").getCompensator("R2").isActive(),"The second owner's callback cannot issue another origin action");
            assertTrue(net.getYMatrix().getA(index,index).subtract(y0.add(new Complex(0,.0001))).abs()<1e-12);assertEquals(currents,net.getCustomBusCurrInjHashtable());if(supplied)assertSame(provided,net.getCustomBusCurrInjHashtable());
            assertArrayEquals(initial,f.group().kernel().states(),1e-14);assertEquals(0,f.group().kernel().time());assertTrue(f.group().acceptedEndpoint().isEmpty());
            for(int bus:new int[]{1,4})unit(f.algo(),bus).setStatus(false);var before=f.group().shuntController().getNamedStates();assertTrue(f.group().acceptedEndpoint().isEmpty());assertEquals(before,f.group().shuntController().getNamedStates());
        }
    }
    @Test void missingIndividualNominalMetadataRejectsBeforeAnyPortOrShuntMutation(@TempDir Path dir)throws Exception{
        var p=HplntdSwitchingNativeReference.profiles().getFirst();var f=load(p,dir,false,false);var net=f.algo().getNetwork();var shunt=net.getBus("Bus2").getCompensator("R5");
        shunt.eAdapters().removeIf(a->a instanceof FixedShuntAdmittance);assertTrue(FixedShuntAdmittance.find(shunt).isEmpty());
        var links=new HplntdAggregationNativeReference.Profile(p.name(),p.step(),p.samples(),p.csvSha256(),p.inputSha256());
        var units=List.of(new DStabNetworkBuilder.HybridUnit("Bus1","1",links.downstream(1)),new DStabNetworkBuilder.HybridUnit("Bus4","1",links.downstream(4)));
        assertThrows(IllegalArgumentException.class,()->new DStabNetworkBuilder(net).addHplntd("Bus2",p.parameters(),units));
        assertNull(unit(f.algo(),1).getReeca1Controller().getHybridController());assertNull(unit(f.algo(),4).getReeca1Controller().getHybridController());
        for(int i=0;i<10;i++)assertEquals(i>=5,net.getBus("Bus2").getCompensator(p.parameters().shunts().get(i).id()).isActive());
    }
    @Test void enabledGroupReinitializationSeedsActualStatusesAndClearsTimers(@TempDir Path dir)throws Exception{
        var p=HplntdSwitchingNativeReference.profiles().getFirst();var f=load(p,dir,true);for(int i=0;i<20;i++)assertTrue(f.algo().solveDEqnStep(true));
        assertFalse(f.algo().getNetwork().getBus("Bus2").getCompensator("R1").isActive());
        f.group().ports().getFirst().initialize(.4,.15);assertThrows(IllegalStateException.class,()->f.group().acceptedEndpoint());f.group().ports().getLast().initialize(.3,.08);
        assertEquals(0,f.group().kernel().time());assertEquals(0,f.group().shuntController().time());
        assertEquals(-1,f.group().shuntController().getNamedStates().get("fastStart"));
        for(var slot:f.group().shuntController().slots()){
            assertEquals(f.algo().getNetwork().getBus("Bus"+slot.key().bus()).getCompensator(slot.key().id()).isActive(),slot.inService());
            assertEquals(-1,slot.breakerStart(),0);assertEquals(-1,slot.dischargeStart(),0);
        }
    }
}
