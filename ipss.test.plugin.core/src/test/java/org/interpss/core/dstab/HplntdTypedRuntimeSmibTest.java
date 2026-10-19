package org.interpss.core.dstab;

import static org.junit.jupiter.api.Assertions.*;
import java.nio.file.*;
import java.util.*;
import org.apache.commons.math3.complex.Complex;
import org.interpss.CorePluginTestSetup;
import org.interpss.core.dstab.reference.HplntdAggregationNativeReference;
import org.interpss.dstab.renewable.*;
import org.interpss.fadapter.builder.DStabNetworkBuilder;
import org.interpss.fadapter.psse.*;
import com.interpss.dstab.*;
import com.interpss.dstab.algo.*;
import com.interpss.dstab.cache.StateMonitor;
import com.interpss.core.acsc.fault.SimpleFaultCode;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Typed shared-controller lifecycle on a deterministic two-unit testbed. */
public class HplntdTypedRuntimeSmibTest extends CorePluginTestSetup {
    record Fixture(DynamicSimuAlgorithm algorithm, Regca1Model first, Regca1Model second,
                   HplntdCoordinator group) {}

    static Path writeNetwork(Path dir)throws Exception {
        Files.createDirectories(dir);
        String raw=Files.readString(Path.of("testData/adpter/psse/v33/renewable/regfma1_native.raw"));
        var lines=raw.lines().toList();
        String bus=lines.stream().filter(s->s.stripLeading().startsWith("1,'BUS1")).findFirst().orElseThrow();
        raw=raw.replace("0 / END OF BUS DATA",bus.replace("1,'BUS1","4,'BUS4")+"\n0 / END OF BUS DATA");
        String gen=lines.stream().filter(s->s.stripLeading().startsWith("1,'1 ")).findFirst().orElseThrow();
        var fields=gen.split(",",-1);fields[0]="4";fields[2]="30.0";fields[8]="100.0";
        raw=raw.replace("0 / END OF GENERATOR DATA",String.join(",",fields)+"\n0 / END OF GENERATOR DATA");
        String branch=lines.stream().filter(s->s.contains("2,'1 '")&&s.contains("1.00000E-1")).findFirst().orElseThrow();
        raw=raw.replace("0 / END OF BRANCH DATA",branch.replace("     1,     2,","     4,     2,")+"\n0 / END OF BRANCH DATA");
        Path network=dir.resolve("network.raw");Files.writeString(network,raw);
        return network;
    }
    Fixture fixture(Path dir,boolean reverse,double step)throws Exception{return fixture(dir,reverse,step,false);}
    Fixture fixture(Path dir, boolean reverse, double step,boolean imported) throws Exception {
        return fixture(dir,reverse,step,imported,HplntdAggregationNativeReference.profiles().getFirst());
    }
    Fixture fixture(Path dir,boolean reverse,double step,boolean imported,HplntdAggregationNativeReference.Profile profile)throws Exception {
        Path network=writeNetwork(dir);
        String dynamic=profile.input().lines().filter(s->imported||!s.contains("HPLNTDU")&&!s.contains("REAXDU"))
                .reduce("",(a,b)->a+b+"\n");
        if(imported&&reverse){var records=new ArrayList<>(dynamic.lines().toList());Collections.reverse(records);dynamic=String.join("\n",records)+"\n";}
        Path dyr=dir.resolve("network.dyr");Files.writeString(dyr,dynamic);
        var ctx=new PSSEMultiFileLoader().loadDStab(network.toString());
        var net=ctx.getDStabilityNet();var algo=ctx.getDynSimuAlgorithm();
        var parser=new PSSEDStabDirectParser(new DStabNetworkBuilder(net)).setStrictImport(true);
        parser.parseDynFile(dyr.toString());assertTrue(parser.getLastImportReport().isStrictlyComplete());
        net.setAllowGenWithoutMach(true);algo.getAclfAlgorithm().getDataCheckConfig().setAllowGenWithoutMachine(true);
        if(reverse){
            var reordered=new ArrayList<>(net.getBusList());Collections.reverse(reordered);
            for(int i=0;i<reordered.size();i++)net.getBusList().move(i,net.getBusList().indexOf(reordered.get(i)));
        }
        algo.getAclfAlgorithm().setTolerance(1e-10);assertTrue(algo.getAclfAlgorithm().loadflow());
        var first=(Regca1Model)((DStabGen)net.getBus("Bus1").getContributeGen("1")).getDynamicGenDevice();
        var second=(Regca1Model)((DStabGen)net.getBus("Bus4").getContributeGen("1")).getDynamicGenDevice();
        var bindings=new ArrayList<>(List.of(new HplntdCoordinator.Binding(first,profile.downstream(1)),
                new HplntdCoordinator.Binding(second,profile.downstream(4))));
        if(reverse)Collections.reverse(bindings);
        var group=imported?first.getReeca1Controller().getHybridController().owner():HplntdCoordinator.attach(profile.plant(),net.getBus("Bus2"),bindings);
        algo.setSimuMethod(DynamicSimuMethod.MODIFIED_EULER);algo.setSimuStepSec(step);algo.setTotalSimuTimeSec(.2);
        algo.setSimuOutputHandler(new StateMonitor());assertTrue(algo.initialization());
        return new Fixture(algo,first,second,group);
    }

    private static void referenceStep(HplntdCoordinator group){
        var r=group.kernel().references();group.kernel().setReferences(new HplntdKernel.References(
                r.voltage(),r.reactive()+.02,r.frequency(),r.active()+.03,r.powerFactor(),r.voltageAux(),r.activeAux()));
    }

    @Test void coupledFlatRunAdvancesSharedGroupExactlyOnceAtBothSteps(@TempDir Path dir)throws Exception {
        for(double h:new double[]{.0005,.00025}){
            var f=fixture(dir.resolve("h"+h),false,h);var g=f.group();
            double[] initial=g.kernel().states();double voltage=f.first().getDStabBus().getVoltageMag();
            assertEquals(150,g.snapshot().plantMva(),0);assertEquals(17,g.getNamedStates().size());
            assertThrows(UnsupportedOperationException.class,()->g.getNamedStates().clear());
            for(int tick=0;tick<200;tick++){
                for(var port:g.ports())port.step(0,0);
                assertEquals(tick,g.predictorCount());assertTrue(f.algorithm().solveDEqnStep(true));
                assertEquals(tick+1,g.predictorCount());assertEquals(tick+1,g.correctorCount());
                assertEquals((tick+1)*h,g.kernel().time(),1e-13);
            }
            assertArrayEquals(initial,g.kernel().states(),1e-7);
            assertEquals(voltage,f.first().getDStabBus().getVoltageMag(),1e-7);
            for(var p:g.ports()){assertEquals(0,p.activeIncrement(),1e-7);assertEquals(0,p.reactiveIncrement(),1e-7);}
        }
    }

    @Test void traversalAndBindingOrderDoNotChangeCoupledCommands(@TempDir Path dir)throws Exception {
        var a=fixture(dir.resolve("forward"),false,.0005);var b=fixture(dir.resolve("reverse"),true,.0005);
        referenceStep(a.group());referenceStep(b.group());
        for(int tick=0;tick<200;tick++){
            assertTrue(a.algorithm().solveDEqnStep(true));assertTrue(b.algorithm().solveDEqnStep(true));
            assertArrayEquals(a.group().kernel().states(),b.group().kernel().states(),1e-10,"tick "+tick);
            for(int i=0;i<2;i++){
                assertArrayEquals(a.group().ports().get(i).states(),b.group().ports().get(i).states(),1e-10);
                assertEquals(a.group().ports().get(i).activeIncrement(),b.group().ports().get(i).activeIncrement(),1e-10);
            }
        }
        assertTrue(Math.abs(a.group().ports().getFirst().activeIncrement())>1e-4);
        assertTrue(Math.abs(a.group().ports().getFirst().reactiveIncrement())>1e-4);
    }

    @Test void callbackContractRejectsDuplicatesAndIncompleteStages(@TempDir Path dir)throws Exception {
        var f=fixture(dir,false,.0005);var g=f.group();var a=g.ports().getFirst();var b=g.ports().getLast();
        assertThrows(IllegalStateException.class,()->a.step(.0005,1));
        a.step(.0005,0);double[] predicted=g.kernel().states();
        assertThrows(IllegalStateException.class,()->a.step(.0005,0));assertArrayEquals(predicted,g.kernel().states());
        assertThrows(IllegalStateException.class,()->a.step(.0005,1));
        b.step(.0005,0);b.step(.0005,1);
        assertThrows(IllegalStateException.class,()->b.step(.0005,1));
        assertThrows(IllegalStateException.class,()->a.step(.0005,0));
        a.step(.0005,1);assertEquals(1,g.correctorCount());
        assertThrows(IllegalArgumentException.class,()->a.step(Double.NaN,0));
        assertThrows(IllegalStateException.class,()->f.first().getReeca1Controller().setHybridController(null));
    }

    @Test void membershipChangesReweightButHoldInactivePublishedCommands(@TempDir Path dir)throws Exception {
        var f=fixture(dir,false,.0005);var g=f.group();referenceStep(g);
        var a=g.ports().getFirst();var b=g.ports().getLast();
        for(int i=0;i<20;i++){a.step(.0005,0);b.step(.0005,0);a.step(.0005,1);b.step(.0005,1);}
        double initial=g.snapshot().initialActive(),held=b.activeIncrement();double[] old=b.states();
        f.second().getParentGen().setStatus(false);
        for(int i=0;i<20;i++){a.step(.0005,0);a.step(.0005,1);}
        assertEquals(50,g.snapshot().plantMva(),0);assertEquals(initial,g.snapshot().initialActive(),0);
        assertEquals(held,b.activeIncrement(),0);assertFalse(Arrays.equals(old,b.states()));
        f.second().getParentGen().setStatus(true);
        a.step(.0005,0);b.step(.0005,0);b.step(.0005,1);a.step(.0005,1);
        assertEquals(150,g.snapshot().plantMva(),0);assertEquals(initial,g.snapshot().initialActive(),0);
        assertEquals(41,g.predictorCount());assertEquals(41,g.correctorCount());
    }

    @Test void occupiedPortsAndIncompatibleIntegrationCannotReplaceSharedOwnership(@TempDir Path dir)throws Exception {
        var f=fixture(dir,false,.0005);var profile=HplntdAggregationNativeReference.profiles().getFirst();
        var a=f.first().getReeca1Controller().getHybridController();var b=f.second().getReeca1Controller().getHybridController();
        assertThrows(IllegalArgumentException.class,()->HplntdCoordinator.attach(profile.plant(),
                f.algorithm().getNetwork().getBus("Bus2"),List.of(new HplntdCoordinator.Binding(f.first(),profile.downstream(1)),
                        new HplntdCoordinator.Binding(f.second(),profile.downstream(4)))));
        assertSame(a,f.first().getReeca1Controller().getHybridController());assertSame(b,f.second().getReeca1Controller().getHybridController());
        assertThrows(IllegalArgumentException.class,()->f.first().setIntegrationScheme(Regca1Model.IntegrationScheme.TWO_STEP));
        assertSame(f.first().getReeca1Controller(),f.first().getActiveElectricalController());
    }

    @Test void wholeGroupReinitializationReseedsEveryLagAndResetsStageHistory(@TempDir Path dir)throws Exception {
        var f=fixture(dir,false,.0005);var g=f.group();referenceStep(g);
        for(int i=0;i<20;i++)assertTrue(f.algorithm().solveDEqnStep(true));
        double[] seeded=new double[]{.41,.12,.31,.08};
        g.ports().getLast().initialize(seeded[2],seeded[3]);
        assertThrows(IllegalStateException.class,g::kernel);
        g.ports().getFirst().step(0,0);
        g.ports().getFirst().initialize(seeded[0],seeded[1]);
        assertEquals(0,g.kernel().time(),0);assertEquals(0,g.predictorCount());assertEquals(0,g.correctorCount());
        assertEquals((50*.41+100*.31)/150,g.snapshot().initialActive(),1e-15);
        for(var p:g.ports()){assertEquals(0,p.activeIncrement(),0);assertEquals(0,p.reactiveIncrement(),0);}
        assertEquals(17,g.getNamedStates().size());
    }

    private static double[] snapshot(Fixture f){
        double[] result=new double[25];
        for(int i=0;i<4;i++)result[i]=f.algorithm().getNetwork().getBus("Bus"+(i+1)).getVoltageMag();
        for(int i=0;i<2;i++){
            var c=i==0?f.first():f.second();double v=c.getDStabBus().getVoltageMag(),scale=c.getParentGen().getMvaBase()/100;
            result[4+2*i]=v*c.getIp()*scale;result[5+2*i]=v*c.getIq()*scale;
        }
        System.arraycopy(f.group().kernel().states(),0,result,8,13);
        for(int i=0;i<2;i++)System.arraycopy(f.group().ports().get(i).states(),0,result,21+2*i,2);
        return result;
    }

    static double referenceHeun(double previous,double reference,double h,double lo,double hi){
        double first=Math.max(lo,Math.min(hi,(reference-previous)/(2*h)));
        double predicted=previous+h*first;
        double second=Math.max(lo,Math.min(hi,(reference-predicted)/(2*h)));
        return previous+.5*h*(first+second);
    }

    @Test void coupledCommandResponseComparedWithEveryNativeStateAndNetworkChannel(@TempDir Path dir)throws Exception {
        for(var profile:HplntdAggregationNativeReference.profiles()){
            if(!profile.name().startsWith("commands"))continue;
            var f=fixture(dir.resolve(profile.name()),false,profile.step(),true);var actual=new ArrayList<double[]>();
            int steps=(int)Math.round(.2/profile.step()),event=(int)Math.round(.05/profile.step());
            for(int tick=0;tick<=steps;tick++){
                if(tick==event){
                    var r=f.group().kernel().references();f.group().kernel().setReferences(new HplntdKernel.References(
                            r.voltage(),r.reactive()+.02,r.frequency(),r.active()+.05,r.powerFactor(),r.voltageAux(),r.activeAux()));
                }
                actual.add(snapshot(f));
                if(tick<steps){
                    double[] before=f.group().kernel().states();var r=f.group().kernel().references();
                    assertTrue(f.algorithm().solveDEqnStep(true));double[] after=f.group().kernel().states();
                    // These reference lags have T=2h and explicit slew limits.
                    // Their Heun endpoint is exact for this discrete method, not a fitted
                    // approximation to the reference tool's different STORE rule.
                    var d=profile.plant();
                    assertEquals(referenceHeun(before[10],r.active(),profile.step(),d.value(HplntdData.Parameter.DPRMIN),d.value(HplntdData.Parameter.DPRMAX)),after[10],1e-14);
                    assertEquals(referenceHeun(before[11],r.reactive(),profile.step(),d.value(HplntdData.Parameter.DQREFMIN),d.value(HplntdData.Parameter.DQREFMAX)),after[11],1e-14);
                }
            }
            var channels=new ArrayList<>(List.of("V1","V2","V3","V4","UNIT1_P_PU","UNIT1_Q_PU","UNIT4_P_PU","UNIT4_Q_PU"));
            for(int i=0;i<13;i++)channels.add("PLANT_STATE"+i);
            for(int bus:new int[]{1,4})for(int i=0;i<2;i++)channels.add("UNIT"+bus+"_STATE"+i);
            var lines=profile.body().lines().toList();var header=Arrays.asList(lines.getFirst().split(","));
            double[] maximum=new double[25],at=new double[25];
            StringBuilder trace=new StringBuilder("time_s");for(String c:channels)trace.append(",interpss_").append(c).append(",native_").append(c);trace.append('\n');
            for(String line:lines.subList(1,lines.size())){
                var fields=line.split(",");double time=Double.parseDouble(fields[0]);int tick=Math.max(0,(int)Math.round(time/profile.step()));
                var own=actual.get(tick);trace.append(time);
                for(int i=0;i<25;i++){
                    double reference=Double.parseDouble(fields[header.indexOf(channels.get(i))]),error=Math.abs(own[i]-reference);
                    if(error>maximum[i]){maximum[i]=error;at[i]=time;}
                    trace.append(',').append(own[i]).append(',').append(reference);
                }trace.append('\n');
            }
            Path out=Path.of("target/hplntd-typed-runtime-comparisons",profile.name()+".csv");Files.createDirectories(out.getParent());Files.writeString(out,trace.toString());
            System.out.println("Hybrid coupled "+profile.name()+" maxima="+Arrays.toString(maximum)+" at="+Arrays.toString(at));
            double[] bounds=profile.name().endsWith("coarse")
                    ?new double[]{2.67e-5,2.39e-5,2.68e-6,3.32e-5,1.23e-4,4.79e-6,4.96e-4,2.59e-6,1e-12,9.43e-5,4.85e-5,1.06e-4,6.42e-4,5.65e-6,3.54e-4,2.44e-6,1e-12,3.17e-7,.0056,.0023,1.05e-4,1.84e-4,1.91e-4,3.67e-4,9.30e-5}
                    :new double[]{1.27e-5,1.15e-5,1.28e-6,1.60e-5,6.10e-5,4.79e-6,2.48e-4,2.59e-6,1e-12,4.47e-5,2.69e-5,5.51e-5,3.21e-4,2.83e-6,1.78e-4,2.53e-6,1e-12,1.60e-7,.0056,.0023,5.51e-5,9.33e-5,1.01e-4,1.86e-4,4.82e-5};
            for(int i=0;i<25;i++)assertTrue(maximum[i]<bounds[i],profile.name()+" "+channels.get(i)+"="+maximum[i]);
        }
    }

    @Test void threeCycleFaultRemainsFiniteAtBothTimesteps(@TempDir Path dir)throws Exception {
        double[] coarse=null;
        for(double h:new double[]{.0005,.00025}){
            var f=fixture(dir.resolve("fault"+h),false,h);var net=f.algorithm().getNetwork();
            net.addDynamicEvent(DStabObjectFactory.createBusFaultEvent("Bus2",net,SimpleFaultCode.GROUND_3P,
                    new Complex(0,.1),null,.05,.05),"three-cycle");
            double minimum=1;int steps=(int)Math.round(.2/h);
            for(int i=0;i<steps;i++){
                assertTrue(f.algorithm().solveDEqnStep(true));minimum=Math.min(minimum,net.getBus("Bus2").getVoltageMag());
                for(double v:snapshot(f))assertTrue(Double.isFinite(v));
            }
            assertTrue(minimum<.8,"The fault must be electrically visible");
            assertEquals(steps,f.group().predictorCount());assertEquals(steps,f.group().correctorCount());
            if(coarse==null)coarse=snapshot(f);else assertArrayEquals(coarse,snapshot(f),.01,"Fault timestep sensitivity");
        }
    }
}
