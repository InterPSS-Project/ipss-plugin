package org.interpss.core.dstab;

import static org.junit.jupiter.api.Assertions.*;
import java.nio.file.*;
import java.util.*;
import org.apache.commons.math3.complex.Complex;
import org.interpss.CorePluginTestSetup;
import org.interpss.core.dstab.reference.GenqecKwNativeReference;
import com.interpss.dstab.mach.model.GenqecMachine;
import org.interpss.fadapter.psse.PSSEMultiFileLoader;
import org.interpss.fadapter.psse.PSSEDStabDirectParser;
import org.interpss.fadapter.builder.DStabNetworkBuilder;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import com.interpss.dstab.algo.DynamicSimuMethod;
import com.interpss.dstab.cache.StateMonitor;
import com.interpss.dstab.mach.MachineIfdBase;
import com.interpss.dstab.DStabObjectFactory;
import com.interpss.core.acsc.fault.SimpleFaultCode;

/** Exact field-compensation inputs and product limits, separate from full-network parity. */
public class GenqecKwNativeConformanceTest extends CorePluginTestSetup {
    private static double kw(String name){return name.startsWith("kw-zero")?0:name.startsWith("kw-one")?1:1.5;}
    private static com.interpss.simu.SimuContext load(GenqecKwNativeReference.Profile p,Path dir,boolean unsaturated)throws Exception{
        return load(p,dir,unsaturated,false);
    }
    private static com.interpss.simu.SimuContext load(GenqecKwNativeReference.Profile p,Path dir,boolean unsaturated,boolean fault)throws Exception{
        Files.createDirectories(dir);Files.writeString(dir.resolve("network.raw"),p.network());String dynamic=p.input();
        if(unsaturated){assertTrue(dynamic.contains("0.05 0.24"));dynamic=dynamic.replace("0.05 0.24","0.0 0.0");}Files.writeString(dir.resolve("dynamic.dyr"),dynamic);
        var context=new PSSEMultiFileLoader().loadDStab(dir.resolve("network.raw").toString());var parser=new PSSEDStabDirectParser(new DStabNetworkBuilder(context.getDStabilityNet())).setStrictImport(true);parser.parseDynFile(dir.resolve("dynamic.dyr").toString());assertTrue(parser.getLastImportReport().isStrictlyComplete());var algorithm=context.getDynSimuAlgorithm();algorithm.getAclfAlgorithm().setTolerance(1e-10);assertTrue(algorithm.getAclfAlgorithm().loadflow());algorithm.setSimuMethod(DynamicSimuMethod.MODIFIED_EULER);algorithm.setSimuStepSec(p.step());algorithm.setTotalSimuTimeSec(.3);algorithm.setSimuOutputHandler(new StateMonitor());
        if(fault)context.getDStabilityNet().addDynamicEvent(DStabObjectFactory.createBusFaultEvent("Bus1",context.getDStabilityNet(),SimpleFaultCode.GROUND_3P,new Complex(0,.2),null,.05,.05),"compensation-three-cycle");
        assertTrue(algorithm.initialization());
        var machine=(GenqecMachine)context.getDStabilityNet().getMachine("Bus1-mach1");assertEquals(kw(p.name()),machine.getGenqecData().kw());return context;
    }
    @Test void everyRawProfileHasElectricalReplay(@TempDir Path dir)throws Exception{
        int count=0;
        var maxima=new HashMap<String,double[]>();
        for(var p:GenqecKwNativeReference.profiles()){
            var rows=HplntdSwitchingNativeSmibTest.rows(p.body());assertEquals(p.samples(),rows.size());var context=load(p,dir.resolve(p.name()),false);
            var machine=(GenqecMachine)context.getDStabilityNet().getMachine("Bus1-mach1");var solver=GenqecSalientNativeConformanceTest.electricalSolver(machine);var previous=rows.getFirst();double last=previous.get("time_s");
            double[] maximum=new double[7],at=new double[7];String[] fields={"EP_Q","EP_D","PSIP_D","PSIP_Q","INTERNAL_VAR","P_PU","Q_PU"};var trace=new StringBuilder("time_s");for(String field:fields)trace.append(",IPSS_").append(field).append(",NATIVE_").append(field);trace.append('\n');
            for(var row:rows){double time=row.get("time_s");if(time>last){GenqecSalientNativeConformanceTest.boundary(machine,previous);assertTrue(solver.nextStepElectrical(p.step(),DynamicSimuMethod.MODIFIED_EULER,context.getDStabilityNet(),0));GenqecSalientNativeConformanceTest.boundary(machine,row);assertTrue(solver.nextStepElectrical(p.step(),DynamicSimuMethod.MODIFIED_EULER,context.getDStabilityNet(),1));}else GenqecSalientNativeConformanceTest.boundary(machine,row);
                var states=machine.getNamedStates();Complex v=machine.getDStabBus().getVoltage(),i=machine.getIgen().subtract(v.multiply(machine.getYgen())),power=v.multiply(i.conjugate());double[] actual={states.get("E'q"),states.get("E'd"),states.get("Psi'd"),states.get("Psi'q"),machine.getEffectiveSaturationFactor(),power.getReal(),power.getImaginary()};
                trace.append(time);for(int k=0;k<actual.length;k++){double expected=row.get(fields[k]),error=Math.abs(actual[k]-expected);assertTrue(Double.isFinite(actual[k]));if(error>maximum[k]){maximum[k]=error;at[k]=time;}trace.append(',').append(actual[k]).append(',').append(expected);}trace.append('\n');previous=row;last=time;
            }
            Path output=Path.of("target/genqec-kw-replay",p.name()+".csv");Files.createDirectories(output.getParent());Files.writeString(output,trace);System.out.println("Field-compensation replay "+p.name()+" errors="+Arrays.toString(maximum)+" at="+Arrays.toString(at));count++;
            for(int k=0;k<maximum.length;k++)assertTrue(maximum[k]<p.replayBounds()[k],p.name()+" "+fields[k]+" error="+maximum[k]+" at="+at[k]);maxima.put(p.name(),maximum);
        }
        assertEquals(6,count);
        for(String name:List.of("kw-zero","kw-one","kw-high"))for(int k=0;k<7;k++)assertTrue(maxima.get(name+"-fine")[k]<maxima.get(name+"-coarse")[k],name+" nonconvergent replay channel "+k);
    }

    @Test void activeBothSignsAndInactiveProductUseThePublishedFieldDenominator(@TempDir Path dir)throws Exception{
        var p=GenqecKwNativeReference.profiles().stream().filter(v->v.name().equals("kw-high-coarse")).findFirst().orElseThrow();var context=load(p,dir,true);var machine=(GenqecMachine)context.getDStabilityNet().getMachine("Bus1-mach1");assertEquals(0,machine.getEffectiveSaturationFactor(),0);
        int positive=0,negative=0,inactive=0;
        for(double magnitude:new double[]{.1,.5,1,1.5,2})for(int degree=-180;degree<=180;degree++){
            double angle=Math.toRadians(degree);machine.getDStabBus().setVoltage(new Complex(Math.cos(angle),Math.sin(angle)).multiply(magnitude));machine.getIgen();var current=machine.getIdq();var state=machine.getNamedStates();double id=current.d;
            double error=-state.get("Psi'd")-(machine.getXd1()-machine.getXl())*id+state.get("E'q");double temp=(machine.getXd1()-machine.getXd11())/Math.pow(machine.getXd1()-machine.getXl(),2)*error;
            double multiplier=machine.getGenqecData().kw()*id,limited=Math.max(-.4,Math.min(.4,multiplier));double expected=(state.get("E'q")+(machine.getXd()-machine.getXd1())*(id+temp))/(1-limited);
            double actual=machine.calculateIfd(MachineIfdBase.MACHINE)*(machine.getXdAdjusted()-machine.getXl());assertEquals(expected,actual,1e-10);
            if(multiplier>.4)positive++;else if(multiplier<-.4)negative++;else inactive++;
        }
        assertTrue(positive>0&&negative>0&&inactive>0,"Product limiter region counts: positive="+positive+", negative="+negative+", inactive="+inactive);
    }

    @Test void nativeInitialFieldAndImportedFaultsCoverAllAcceptedMultipliers(@TempDir Path dir)throws Exception{
        int count=0;
        for(var p:GenqecKwNativeReference.profiles()){
            var context=load(p,dir.resolve(p.name()),false,true);var algorithm=context.getDynSimuAlgorithm();var net=context.getDStabilityNet();var machine=(GenqecMachine)net.getMachine("Bus1-mach1");var rows=HplntdSwitchingNativeSmibTest.rows(p.body());
            double initialFieldError=Math.abs(machine.getEfd()-rows.getFirst().get("EFD"));System.out.println("Field-compensation initialization "+p.name()+" Efd error="+initialFieldError);assertTrue(initialFieldError<1e-6);
            var initial=machine.getNamedStates();double minimum=machine.getDStabBus().getVoltageMag(),productMin=Double.POSITIVE_INFINITY,productMax=Double.NEGATIVE_INFINITY;int ticks=(int)Math.round(.3/p.step());var trace=new StringBuilder("time_s,eqp,edp,psidp,psiqp,speed,angle,v1,v2,P,Q,kw_Id\n");
            for(int tick=0;tick<=ticks;tick++){
                var states=machine.getNamedStates();for(double value:states.values())assertTrue(Double.isFinite(value));if(tick*p.step()<.05)for(String key:initial.keySet())assertEquals(initial.get(key),states.get(key),1e-7,p.name()+" initial equilibrium "+key);
                Complex v=machine.getDStabBus().getVoltage(),i=machine.getIgen().subtract(v.multiply(machine.getYgen())),power=v.multiply(i.conjugate());double product=machine.getGenqecData().kw()*machine.getIdq().d;productMin=Math.min(productMin,product);productMax=Math.max(productMax,product);minimum=Math.min(minimum,v.abs());trace.append(tick*p.step());
                for(String key:List.of("E'q","E'd","Psi'd","Psi'q","Speed deviation","Angle"))trace.append(',').append(states.get(key));trace.append(',').append(v.abs()).append(',').append(net.getBus("Bus2").getVoltageMag()).append(',').append(power.getReal()).append(',').append(power.getImaginary()).append(',').append(product).append('\n');
                if(tick<ticks)assertTrue(algorithm.solveDEqnStep(true));
            }
            assertTrue(minimum<.95);if(p.name().startsWith("kw-one"))assertTrue(productMin<-.4&&productMax>.4,"The dynamic profile must exercise both product-limit signs");
            Path output=Path.of("target/genqec-kw-coupled",p.name()+".csv");Files.createDirectories(output.getParent());Files.writeString(output,trace);count++;
        }
        assertEquals(6,count);
    }
}
