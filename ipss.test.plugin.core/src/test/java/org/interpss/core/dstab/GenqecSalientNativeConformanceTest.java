package org.interpss.core.dstab;

import static org.junit.jupiter.api.Assertions.*;
import java.nio.file.*;
import java.util.*;
import org.apache.commons.math3.complex.Complex;
import org.interpss.CorePluginTestSetup;
import com.interpss.dstab.mach.model.GenqecMachine;
import org.interpss.core.dstab.reference.GenqecSalientNativeReference;
import org.interpss.fadapter.psse.PSSEMultiFileLoader;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import com.interpss.dstab.algo.*;
import com.interpss.dstab.cache.StateMonitor;
import com.interpss.dstab.mach.impl.MachineImpl;
import com.interpss.dstab.DStabObjectFactory;
import com.interpss.core.acsc.fault.SimpleFaultCode;

/** Recorded phasor/speed/field-voltage replay, separate from coupled acceptance. */
public class GenqecSalientNativeConformanceTest extends CorePluginTestSetup {
    static void boundary(GenqecMachine machine,Map<String,Double> row){
        double angle=Math.toRadians(row.get("A_BUS1"));
        machine.getDStabBus().setVoltage(new Complex(Math.cos(angle),Math.sin(angle)).multiply(row.get("V_BUS1")));
        machine.setAngle(row.get("ANGLE"));machine.setSpeed(1+row.get("SPEED_DEV"));machine.setEfd(row.get("EFD"));
    }
    static IDynamicSimulation electricalSolver(GenqecMachine machine)throws Exception{
        var field=MachineImpl.class.getDeclaredField("_dEqnSolver");field.setAccessible(true);return (IDynamicSimulation)field.get(machine);
    }
    private static com.interpss.simu.SimuContext load(GenqecSalientNativeReference.Profile profile,Path dir,String dynamic)throws Exception{
        Files.createDirectories(dir);Files.writeString(dir.resolve("network.raw"),profile.network());Files.writeString(dir.resolve("dynamic.dyr"),dynamic);
        var context=new PSSEMultiFileLoader().loadDStab(dir.resolve("network.raw").toString(),dir.resolve("dynamic.dyr").toString());
        var algorithm=context.getDynSimuAlgorithm();algorithm.getAclfAlgorithm().setTolerance(1e-10);assertTrue(algorithm.getAclfAlgorithm().loadflow());algorithm.setSimuOutputHandler(new StateMonitor());algorithm.setSimuMethod(DynamicSimuMethod.MODIFIED_EULER);algorithm.setSimuStepSec(profile.step());algorithm.setTotalSimuTimeSec(.3);
        return context;
    }
    @Test void everyRecordedPhasorSampleHasBoundedElectricalReplay(@TempDir Path temp)throws Exception{
        int count=0;
        var maxima=new HashMap<String,double[]>();
        for(var profile:GenqecSalientNativeReference.profiles()){
            String name=profile.name();Path input=temp.resolve(name);Files.createDirectories(input);
            Files.writeString(input.resolve("network.raw"),profile.network());Files.writeString(input.resolve("dynamic.dyr"),profile.input());
            var rows=HplntdSwitchingNativeSmibTest.rows(profile.body());assertEquals(profile.samples(),rows.size());
            var context=new PSSEMultiFileLoader().loadDStab(input.resolve("network.raw").toString(),input.resolve("dynamic.dyr").toString());
            var algorithm=context.getDynSimuAlgorithm();algorithm.getAclfAlgorithm().setTolerance(1e-10);assertTrue(algorithm.getAclfAlgorithm().loadflow());algorithm.setSimuOutputHandler(new StateMonitor());assertTrue(algorithm.initialization());
            var machine=(GenqecMachine)context.getDStabilityNet().getMachine("Bus1-mach1");var solver=electricalSolver(machine);double step=profile.step();
            assertEquals(0,machine.getTq01());assertEquals(machine.getXq(),machine.getXq1());
            double previous=rows.getFirst().get("time_s");Map<String,Double> old=rows.getFirst();double[] maximum=new double[7],at=new double[7];
            String[] labels={"E'q","E'd","Psi'd","Psi'q"};String[] nativeLabels={"EP_Q","EP_D","PSIP_D","PSIP_Q","INTERNAL_VAR","P_PU","Q_PU"};
            var trace=new StringBuilder("time_s");for(String label:nativeLabels)trace.append(",IPSS_").append(label).append(",NATIVE_").append(label);trace.append('\n');
            for(var row:rows){double time=row.get("time_s");
                if(time>previous){boundary(machine,old);assertTrue(solver.nextStepElectrical(step,DynamicSimuMethod.MODIFIED_EULER,context.getDStabilityNet(),0));boundary(machine,row);assertTrue(solver.nextStepElectrical(step,DynamicSimuMethod.MODIFIED_EULER,context.getDStabilityNet(),1));}
                else boundary(machine,row);
                var states=machine.getNamedStates();Complex v=machine.getDStabBus().getVoltage(),i=machine.getIgen().subtract(v.multiply(machine.getYgen())),power=v.multiply(i.conjugate());
                double[] actual={states.get(labels[0]),states.get(labels[1]),states.get(labels[2]),states.get(labels[3]),machine.getEffectiveSaturationFactor(),power.getReal(),power.getImaginary()};
                trace.append(time);for(int k=0;k<actual.length;k++){double expected=row.get(nativeLabels[k]),error=Math.abs(actual[k]-expected);assertTrue(Double.isFinite(actual[k]));if(error>maximum[k]){maximum[k]=error;at[k]=time;}trace.append(',').append(actual[k]).append(',').append(expected);}trace.append('\n');previous=time;old=row;
            }
            Path output=Path.of("target/genqec-salient-replay",name+".csv");Files.createDirectories(output.getParent());Files.writeString(output,trace);
            System.out.println("Salient electrical replay "+name+" errors="+Arrays.toString(maximum)+" at="+Arrays.toString(at));count++;
            for(int k=0;k<maximum.length;k++)assertTrue(maximum[k]<profile.replayBounds()[k],name+" "+nativeLabels[k]+" error="+maximum[k]+" at="+at[k]);
            assertEquals(0,maximum[1],0,"The removed q-transient state must be exactly zero");
            assertTrue(Math.abs(at[3]-(.05+step))<step*.1,"The largest q-state residual is the first post-fault callback, not a hidden later drift");maxima.put(name,maximum);
        }
        assertEquals(8,count);
        for(String name:List.of("genqec-0","genqec-1","genqej-0","genqej-1"))for(int k:new int[]{0,2,3,4,5,6})assertTrue(maxima.get(name+"-fine")[k]<maxima.get(name+"-coarse")[k],name+" nonconvergent replay channel "+k);
    }

    @Test void zeroTransientBranchUsesThePublishedPredictorAndCorrector(@TempDir Path dir)throws Exception{
        var p=GenqecSalientNativeReference.profiles().stream().filter(v->v.name().equals("genqec-0-coarse")).findFirst().orElseThrow();
        String needle="0.05 0.24 0.035";assertTrue(p.input().contains(needle));
        var context=load(p,dir,p.input().replace(needle,"0.0 0.0 0.035"));assertTrue(context.getDynSimuAlgorithm().initialization());
        var machine=(GenqecMachine)context.getDStabilityNet().getMachine("Bus1-mach1");var solver=electricalSolver(machine);assertEquals(0,machine.getEffectiveSaturationFactor(),0);
        double original=machine.getNamedStates().get("Psi'q"),step=p.step();machine.getDStabBus().setVoltage(machine.getDStabBus().getVoltage().multiply(.8));machine.getIgen();
        double first=((machine.getXq()-machine.getXl())*machine.getIdq().q-original)/machine.getTq011();
        assertTrue(solver.nextStepElectrical(step,DynamicSimuMethod.MODIFIED_EULER,context.getDStabilityNet(),0));
        double predicted=machine.getNamedStates().get("Psi'q");assertEquals(original+step*first,predicted,1e-12);assertEquals(0,machine.getEd1(),0);
        machine.getIgen();double second=((machine.getXq()-machine.getXl())*machine.getIdq().q-predicted)/machine.getTq011();
        assertTrue(solver.nextStepElectrical(step,DynamicSimuMethod.MODIFIED_EULER,context.getDStabilityNet(),1));
        assertEquals(original+.5*step*(first+second),machine.getNamedStates().get("Psi'q"),1e-12);assertEquals(0,machine.getEd1(),0);
        var states=machine.getNamedStates();assertThrows(UnsupportedOperationException.class,()->states.clear());
        assertTrue(solver.nextStepElectrical(0,DynamicSimuMethod.MODIFIED_EULER,context.getDStabilityNet(),0));assertTrue(solver.nextStepElectrical(0,DynamicSimuMethod.MODIFIED_EULER,context.getDStabilityNet(),1));assertEquals(states,machine.getNamedStates());
    }

    @Test void allImportedSalientProfilesRunThreeCycleFaultsWithTheRemovedStateZero(@TempDir Path dir)throws Exception{
        int count=0;
        for(var p:GenqecSalientNativeReference.profiles()){
            var context=load(p,dir.resolve(p.name()),p.input());var algorithm=context.getDynSimuAlgorithm();var net=context.getDStabilityNet();
            net.addDynamicEvent(DStabObjectFactory.createBusFaultEvent("Bus1",net,SimpleFaultCode.GROUND_3P,new Complex(0,.2),null,.05,.05),"salient-three-cycle");assertTrue(algorithm.initialization());
            var machine=(GenqecMachine)net.getMachine("Bus1-mach1");var initial=machine.getNamedStates();double minimum=machine.getDStabBus().getVoltageMag();
            var trace=new StringBuilder("time_s,eqp,edp,psidp,psiqp,speed,angle,v1,v2,P,Q,saturation\n");int ticks=(int)Math.round(.3/p.step());
            for(int tick=0;tick<=ticks;tick++){
                var states=machine.getNamedStates();assertEquals(0,states.get("E'd"),0);for(double value:states.values())assertTrue(Double.isFinite(value));
                if(tick*p.step()<.05)for(String key:initial.keySet())assertEquals(initial.get(key),states.get(key),1e-7,"Salient pre-fault equilibrium "+p.name()+" "+key);
                Complex v=machine.getDStabBus().getVoltage(),i=machine.getIgen().subtract(v.multiply(machine.getYgen())),power=v.multiply(i.conjugate());minimum=Math.min(minimum,v.abs());
                trace.append(tick*p.step());for(String key:List.of("E'q","E'd","Psi'd","Psi'q","Speed deviation","Angle"))trace.append(',').append(states.get(key));trace.append(',').append(v.abs()).append(',').append(net.getBus("Bus2").getVoltageMag()).append(',').append(power.getReal()).append(',').append(power.getImaginary()).append(',').append(machine.getEffectiveSaturationFactor()).append('\n');
                if(tick<ticks)assertTrue(algorithm.solveDEqnStep(true));
            }
            assertTrue(minimum<.95,"Fault must activate the electrical branch");Path output=Path.of("target/genqec-salient-coupled",p.name()+".csv");Files.createDirectories(output.getParent());Files.writeString(output,trace);count++;
        }
        assertEquals(8,count);
    }
}
