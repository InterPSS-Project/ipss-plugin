package org.interpss.core.dstab;

import static org.junit.jupiter.api.Assertions.*;
import java.nio.file.*;
import java.util.*;
import org.apache.commons.math3.complex.Complex;
import org.interpss.CorePluginTestSetup;
import org.interpss.core.dstab.reference.Wt12a1bNativeReference;
import com.interpss.dstab.mach.model.*;
import org.interpss.fadapter.builder.DStabNetworkBuilder;
import org.interpss.fadapter.psse.*;
import com.interpss.core.acsc.fault.SimpleFaultCode;
import com.interpss.dstab.DStabObjectFactory;
import com.interpss.dstab.algo.*;
import com.interpss.dstab.cache.StateMonitor;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Bounded three-state replay; zero-time and full-network residuals are explicit diagnostics. */
public class Wt12a1bNativeConformanceTest extends CorePluginTestSetup {
    private static final String[] STATES={"Terminal voltage measurement filter","Mechanical power ramp integrator","Mechanical power output lag"};
    private static List<Map<String,Double>> rows(String text){return HplntdSwitchingNativeSmibTest.rows(text);}
    @Test void everyRawControllerSampleHasBoundedReplayOrAnExplicitZeroTimeDiagnostic()throws Exception{
        int accepted=0,diagnostics=0;
        for(var p:Wt12a1bNativeReference.profiles()){
            var raw=rows(p.body());assertEquals(p.samples(),raw.size());var first=raw.getFirst();var model=new Wt12a1bModel(p.data(),Wt12a1bModel.Specification.NATIVE_STANDARD);
            model.initialize(first.get("AERO_VAR0"),0,first.get("AERO_STATE1"),first.get("AERO_STATE0"));double previous=first.get("time_s"),voltage=first.get("V1");double[] maximum=new double[3],at=new double[3];
            var trace=new StringBuilder("time_s,V1,IPSS_V,NATIVE_V,IPSS_RAMP,NATIVE_RAMP,IPSS_OUT,NATIVE_OUT,remaining_s\n");
            for(var row:raw){double time=row.get("time_s");if(time>previous){assertEquals(p.step(),time-previous,p.step()*.05);model.step(p.step(),0,0,voltage,0);model.step(p.step(),0,0,row.get("V1"),1);}else if(p.data().voltageTime()==0)model.step(0,0,0,row.get("V1"),0);
                var states=model.getNamedStates();trace.append(time).append(',').append(row.get("V1"));for(int i=0;i<3;i++){double value=states.get(STATES[i]),nativeValue=row.get("AERO_STATE"+i),error=Math.abs(value-nativeValue);assertTrue(Double.isFinite(value));if(error>maximum[i]){maximum[i]=error;at[i]=time;}trace.append(',').append(value).append(',').append(nativeValue);}trace.append(',').append(model.getRemainingTime()).append('\n');previous=time;voltage=row.get("V1");}
            Path output=Path.of("target/wt12a1b-controller-replay",p.name()+".csv");Files.createDirectories(output.getParent());Files.writeString(output,trace);
            System.out.println("Active-stall replay "+p.name()+" errors="+Arrays.toString(maximum)+" times="+Arrays.toString(at));
            if(p.acceptedReplay()){for(int i=0;i<3;i++)assertTrue(maximum[i]<p.bounds()[i],p.name()+" "+STATES[i]+" error="+maximum[i]+" at="+at[i]);accepted++;}
            else{assertTrue(maximum[0]>.1,"Native zero-TR event/callback behavior is not algebraic-input parity");diagnostics++;}
        }
        assertEquals(10,accepted);assertEquals(2,diagnostics);
    }

    private record Fixture(DynamicSimuAlgorithm algorithm,Wt1g1Machine machine,Wt12a1bModel controller){}
    private static Fixture load(Wt12a1bNativeReference.Profile p,Path dir,boolean wrapper)throws Exception{
        Files.createDirectories(dir);Path raw=dir.resolve("network.raw"),dyr=dir.resolve("dynamic.dyr");Files.writeString(raw,p.network());String dynamic=p.input();
        if(wrapper)dynamic=dynamic.replace("1 'WT12A1B' '1' ","1 'USRMDL' '1' 'WT12A1U_B' 105 0 1 14 3 2 0 ");Files.writeString(dyr,dynamic);
        var ctx=new PSSEMultiFileLoader().loadDStab(raw.toString());var net=ctx.getDStabilityNet();var algorithm=ctx.getDynSimuAlgorithm();
        var parser=new PSSEDStabDirectParser(new DStabNetworkBuilder(net)).setStrictImport(true);parser.parseDynFile(dyr.toString());assertTrue(parser.getLastImportReport().isStrictlyComplete());
        algorithm.getAclfAlgorithm().setTolerance(1e-10);assertTrue(algorithm.getAclfAlgorithm().loadflow());algorithm.setSimuMethod(DynamicSimuMethod.MODIFIED_EULER);algorithm.setSimuStepSec(p.step());algorithm.setTotalSimuTimeSec(1);algorithm.setSimuOutputHandler(new StateMonitor());
        if(!p.name().startsWith("flat")){
            net.addDynamicEvent(DStabObjectFactory.createBusFaultEvent("Bus1",net,SimpleFaultCode.GROUND_3P,new Complex(0,.1),null,.05,.05),"first-three-cycle");
            if(p.name().startsWith("repeat"))net.addDynamicEvent(DStabObjectFactory.createBusFaultEvent("Bus1",net,SimpleFaultCode.GROUND_3P,new Complex(0,.1),null,.35,.05),"second-three-cycle");
        }
        assertTrue(algorithm.initialization());var machine=(Wt1g1Machine)net.getMachine("Bus1-mach1");var controller=(Wt12a1bModel)machine.getDriveTrain().getAerodynamicController();assertEquals(Wt12a1bModel.Specification.NATIVE_STANDARD,controller.getSpecification());return new Fixture(algorithm,machine,controller);
    }

    @Test void standardAndExplicitIdWrapperHaveIdenticalCoupledFaultResponses(@TempDir Path dir)throws Exception{
        for(var p:Wt12a1bNativeReference.profiles())if(p.name().startsWith("fault")){
            var a=load(p,dir.resolve("standard-"+p.name()),false);var b=load(p,dir.resolve("wrapper-"+p.name()),true);
            for(int tick=0;tick<Math.round(.4/p.step());tick++){
                assertTrue(a.algorithm().solveDEqnStep(true));assertTrue(b.algorithm().solveDEqnStep(true));
                for(String name:STATES)assertEquals(a.controller().getNamedStates().get(name),b.controller().getNamedStates().get(name),1e-12);
                assertEquals(a.machine().getSpeed(),b.machine().getSpeed(),1e-12);assertEquals(a.machine().getDStabBus().getVoltageMag(),b.machine().getDStabBus().getVoltageMag(),1e-12);
            }
        }
    }

    @Test void allImportedProfilesRunFlatOrThreeCycleFaultsAtBothSteps(@TempDir Path dir)throws Exception{
        int count=0;
        for(var p:Wt12a1bNativeReference.profiles()){
            var f=load(p,dir.resolve(p.name()),false);var trace=new StringBuilder("time_s,V1,V2,speed,mechanical_power,filtered_v,ramp,out\n");double initial=f.controller().getOutput(),minimum=initial;
            for(int tick=0;tick<=Math.round(1/p.step());tick++){
                var states=f.controller().getNamedStates();double time=tick*p.step();trace.append(time).append(',').append(f.machine().getDStabBus().getVoltageMag()).append(',').append(f.algorithm().getNetwork().getBus("Bus2").getVoltageMag()).append(',').append(f.machine().getSpeed()).append(',').append(f.machine().getDriveTrain().getAerodynamicPower());
                for(String name:STATES){double value=states.get(name);assertTrue(Double.isFinite(value));trace.append(',').append(value);}trace.append('\n');minimum=Math.min(minimum,f.controller().getOutput());
                assertTrue(Double.isFinite(f.machine().getSpeed()));if(tick<Math.round(1/p.step()))assertTrue(f.algorithm().solveDEqnStep(true));
            }
            if(!p.name().startsWith("flat")&&!p.name().startsWith("below"))assertTrue(minimum<initial-.001,p.name()+" must activate reduction");
            else assertTrue(minimum>initial-.001,p.name()+" inactive response must remain near initial power");
            Path output=Path.of("target/wt12a1b-coupled-diagnostics",p.name()+".csv");Files.createDirectories(output.getParent());Files.writeString(output,trace);count++;
        }
        assertEquals(12,count);
    }
}
