package org.interpss.core.dstab;

import static org.junit.jupiter.api.Assertions.*;
import java.nio.file.*;
import java.util.*;
import org.interpss.core.dstab.reference.HplntdSelectorNativeReference;
import org.interpss.dstab.renewable.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Exact disabled-path state replay and bounded imported small-network coupling. */
public class HplntdSelectorNativeSmibTest {
    @Test void wholeGroupReinitializationReseedsDisabledHeldCommands(@TempDir Path dir)throws Exception {
        var factory=new HplntdTypedRuntimeSmibTest();
        for(var p:HplntdSelectorNativeReference.profiles())if(p.name().matches("selector-(p-disabled|q-disabled|both-disabled)-coarse")){
            var f=factory.fixture(dir.resolve(p.name()),false,p.step(),true,p);var group=f.group();
            for(int i=0;i<5;i++)assertTrue(f.algorithm().solveDEqnStep(true));
            group.ports().getLast().initialize(.3,.08);assertThrows(IllegalStateException.class,group::kernel);
            group.ports().getFirst().initialize(.4,.1);
            assertEquals(0,group.predictorCount());assertEquals(0,group.correctorCount());assertEquals(0,group.kernel().time(),0);
            for(var port:group.ports()){assertEquals(0,port.activeIncrement(),0);assertEquals(0,port.reactiveIncrement(),0);}
            var in=new HplntdKernel.Input(org.apache.commons.math3.complex.Complex.ONE,org.apache.commons.math3.complex.Complex.ZERO,0,0,0,1);
            var out=group.kernel().output(in);
            if(p.plant().frequencyFlag()==0){assertEquals(0,group.kernel().states()[6],0);assertEquals(group.snapshot().initialActive(),out.activeCommand(),0);}
            if(p.plant().reactiveFlag()==0){assertEquals(0,group.kernel().states()[12],0);assertEquals(group.snapshot().initialReactive(),out.reactiveCommand(),0);}
            assertEquals(17,group.getNamedStates().size());
        }
    }
    @Test void everyRecordedPlantStateAndCommandMatchesWithoutInjectingNativeStates()throws Exception {
        HplntdKernelNativeConformanceTest.compareProfiles(HplntdSelectorNativeReference.profiles(),
                p->HplntdSelectorNativeReference.replayBounds(p.name()));
    }
    private static List<String> channels(){
        var names=new ArrayList<>(List.of("V1","V2","V3","V4","UNIT1_P_PU","UNIT1_Q_PU","UNIT4_P_PU","UNIT4_Q_PU"));
        for(int i=0;i<13;i++)names.add("PLANT_STATE"+i);
        for(int bus:new int[]{1,4})for(int i=0;i<2;i++)names.add("UNIT"+bus+"_STATE"+i);
        return names;
    }
    private static double[] snapshot(HplntdTypedRuntimeSmibTest.Fixture f){
        double[] values=new double[25];
        for(int i=0;i<4;i++)values[i]=f.algorithm().getNetwork().getBus("Bus"+(i+1)).getVoltageMag();
        for(int i=0;i<2;i++){
            var unit=i==0?f.first():f.second();double scale=unit.getParentGen().getMvaBase()/100.,v=unit.getDStabBus().getVoltageMag();
            values[4+2*i]=v*unit.getIp()*scale;values[5+2*i]=v*unit.getIq()*scale;
            System.arraycopy(f.group().ports().get(i).states(),0,values,21+2*i,2);
        }
        System.arraycopy(f.group().kernel().states(),0,values,8,13);return values;
    }
    @Test void importedDisabledPathsKeepCommandsAndCompareAllNetworkAndStateSamples(@TempDir Path dir)throws Exception {
        compareImportedProfiles(HplntdSelectorNativeReference.profiles(),p->HplntdSelectorNativeReference.coupledBounds(p.name()),dir,"hplntd-selector-coupled");
    }
    static void compareImportedProfiles(List<org.interpss.core.dstab.reference.HplntdAggregationNativeReference.Profile> profiles,
            java.util.function.Function<org.interpss.core.dstab.reference.HplntdAggregationNativeReference.Profile,double[]> boundsFor,Path dir,String folder)throws Exception {
        compareImportedProfiles(profiles,boundsFor,dir,folder,(p,g)->{
            var r=g.kernel().references();g.kernel().setReferences(new HplntdKernel.References(r.voltage(),r.reactive()+.02,r.frequency(),r.active()+.05,r.powerFactor(),r.voltageAux(),r.activeAux()));
        });
    }
    static void compareImportedProfiles(List<org.interpss.core.dstab.reference.HplntdAggregationNativeReference.Profile> profiles,
            java.util.function.Function<org.interpss.core.dstab.reference.HplntdAggregationNativeReference.Profile,double[]> boundsFor,Path dir,String folder,
            java.util.function.BiConsumer<org.interpss.core.dstab.reference.HplntdAggregationNativeReference.Profile,HplntdCoordinator> event)throws Exception {
        var factory=new HplntdTypedRuntimeSmibTest();var names=channels();
        var failures=new ArrayList<String>();
        for(var p:profiles){
            var f=factory.fixture(dir.resolve(p.name()),true,p.step(),true,p);var data=p.plant();
            var rows=new ArrayList<double[]>();int steps=(int)Math.round(.2/p.step());
            for(int tick=0;tick<=steps;tick++){
                if(tick==(int)Math.round(.05/p.step())){
                    event.accept(p,f.group());
                }
                rows.add(snapshot(f));
                for(var port:f.group().ports()){
                    if(data.frequencyFlag()==0)assertEquals(0,port.activeIncrement(),1e-15,"Inactive active command port");
                    if(data.reactiveFlag()==0)assertEquals(0,port.reactiveIncrement(),1e-15,"Inactive reactive command port");
                }
                if(tick<steps){
                    var before=f.group().kernel().states();var reference=f.group().kernel().references();
                    assertTrue(f.algorithm().solveDEqnStep(true));var after=f.group().kernel().states();
                    if(data.frequencyFlag()==1)assertEquals(HplntdTypedRuntimeSmibTest.referenceHeun(before[10],reference.active(),p.step(),data.value(HplntdData.Parameter.DPRMIN),data.value(HplntdData.Parameter.DPRMAX)),after[10],1e-14);
                    if(data.reactiveFlag()==1&&data.referenceFlag()==0)assertEquals(HplntdTypedRuntimeSmibTest.referenceHeun(before[11],Math.max(data.value(HplntdData.Parameter.QREFMIN),Math.min(data.value(HplntdData.Parameter.QREFMAX),reference.reactive())),p.step(),data.value(HplntdData.Parameter.DQREFMIN),data.value(HplntdData.Parameter.DQREFMAX)),after[11],1e-14);
                }
            }
            assertEquals(steps,f.group().predictorCount());assertEquals(steps,f.group().correctorCount());
            assertEquals(17,f.group().getNamedStates().size());
            var lines=p.body().lines().toList();assertEquals(p.samples()+1,lines.size());var header=Arrays.asList(lines.getFirst().split(","));
            var trace=new StringBuilder("time_s");for(String name:names)trace.append(",interpss_").append(name).append(",native_").append(name);trace.append('\n');
            double[] maximum=new double[25],at=new double[25];
            for(String line:lines.subList(1,lines.size())){
                var fields=line.split(",");double time=Double.parseDouble(fields[0]);var own=rows.get(Math.max(0,(int)Math.round(time/p.step())));trace.append(time);
                for(int i=0;i<25;i++){
                    double expected=Double.parseDouble(fields[header.indexOf(names.get(i))]),error=Math.abs(own[i]-expected);
                    if(error>maximum[i]){maximum[i]=error;at[i]=time;}trace.append(',').append(own[i]).append(',').append(expected);
                }trace.append('\n');
            }
            Path output=Path.of("target",folder,p.name()+".csv");Files.createDirectories(output.getParent());Files.writeString(output,trace);
            System.out.println("Hybrid selector coupled "+p.name()+" errors="+Arrays.toString(maximum)+" at="+Arrays.toString(at));
            double[] bounds=boundsFor.apply(p);
            for(int i=0;i<25;i++)if(maximum[i]>bounds[i])failures.add(p.name()+" "+names.get(i)+"="+maximum[i]);
        }
        assertTrue(failures.isEmpty(),String.join("; ",failures));
    }
}
