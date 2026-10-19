package org.interpss.core.dstab;

import static org.junit.jupiter.api.Assertions.*;
import java.nio.file.*;
import java.util.*;
import org.apache.commons.math3.complex.Complex;
import org.interpss.CorePluginTestSetup;
import org.interpss.core.dstab.reference.PssCompensationNativeReference;
import org.interpss.dstab.control.pss.ieee.y2016.pss6c.*;
import org.interpss.dstab.control.pss.ieee.y2016.pss7c.*;
import org.interpss.fadapter.psse.PSSEMultiFileLoader;
import org.interpss.fadapter.psse.PSSEDStabDirectParser;
import org.interpss.fadapter.builder.DStabNetworkBuilder;
import org.interpss.fadapter.psse.dyr.PsseDyrRecordReader;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import com.interpss.dstab.BaseDStabBus;
import com.interpss.dstab.algo.DynamicSimuMethod;
import com.interpss.dstab.controller.cml.annotate.AnnotateStabilizer;
import com.interpss.dstab.mach.impl.RoundRotorMachineImpl;
import com.interpss.dstab.DStabObjectFactory;
import com.interpss.core.acsc.fault.SimpleFaultCode;
import com.interpss.dstab.cache.StateMonitor;

/** Recorded measurement replay; forced network boundaries are not predicted. */
public class PssCompensationNativeConformanceTest extends CorePluginTestSetup {
    private static com.interpss.simu.SimuContext load(PssCompensationNativeReference.Profile p,Path input)throws Exception {
        Files.createDirectories(input);Files.writeString(input.resolve("network.raw"),p.network());Files.writeString(input.resolve("dynamic.dyr"),p.input());
        var context=new PSSEMultiFileLoader().loadDStab(input.resolve("network.raw").toString());
        var parser=new PSSEDStabDirectParser(new DStabNetworkBuilder(context.getDStabilityNet())).setStrictImport(true);
        parser.parseDynFile(input.resolve("dynamic.dyr").toString());assertTrue(parser.getLastImportReport().isStrictlyComplete());assertEquals(4,parser.getLastImportReport().totalRecordCount());
        return context;
    }
    private static final List<String> SIX=List.of("input1Transducer1","input1Transducer2","input2Transducer","input2Washout","mainWashout","canonicalIntegrator1","canonicalIntegrator2","canonicalIntegrator3","canonicalIntegrator4","compensatedFrequencyWashout");
    private static final List<String> SEVEN=List.of("input1Washout1","input1Washout2","input1Transducer","input2Washout1","input2Washout2","input2Transducer","rampTracking1","rampTracking2","rampTracking3","rampTracking4","rampTracking5","rampTracking6","rampTracking7","rampTracking8","canonicalIntegrator1","canonicalIntegrator2","canonicalIntegrator3","canonicalIntegrator4","compensatedFrequencyWashout");

    private static final class ReplayMachine extends RoundRotorMachineImpl {
        private final BaseDStabBus<?,?> bus;
        private final double factor;
        private Complex power=Complex.ZERO;
        ReplayMachine(BaseDStabBus<?,?> bus,double factor){this.bus=bus;this.factor=factor;}
        @Override public BaseDStabBus getDStabBus(){return bus;}
        @Override public Complex getYgen(){return Complex.ZERO;}
        @Override public Complex getIgen(){return power.divide(bus.getVoltage()).conjugate();}
        @Override public double getIMultiFactor(){return factor;}
        @Override public double getVMultiFactor(){return 1;}
        @Override public double getZMultiFactor(){return 1/factor;}
        @Override public double getPe(){return power.getReal()/factor;}
        @Override public double getPm(){return .50001001358/factor;}
        void boundary(Map<String,Double> row){
            double angle=Math.toRadians(row.get("A_BUS1"));
            bus.setVoltage(new Complex(Math.cos(angle),Math.sin(angle)).multiply(row.get("V_BUS1")));
            power=new Complex(row.get("P_PU"),row.get("Q_PU"));
        }
    }

    @Test void replayEveryRawRowAtBothStepsAndMachineBases(@TempDir Path dir)throws Exception {
        var allMaxima=new LinkedHashMap<String,double[]>();
        for(var p:PssCompensationNativeReference.profiles()){
            Path input=dir.resolve(p.name());var context=load(p,input);
            assertTrue(context.getDynSimuAlgorithm().getAclfAlgorithm().loadflow());
            var machine=new ReplayMachine(context.getDStabilityNet().getBus("Bus1"),p.mva()/100);
            var rows=HplntdSwitchingNativeSmibTest.rows(p.body());assertEquals(p.samples(),rows.size());machine.boundary(rows.getFirst());
            var record=PsseDyrRecordReader.read(input.resolve("dynamic.dyr")).stream().filter(r->r.canonicalModelName().equals(p.model())).findFirst().orElseThrow();
            double[] values=new double[record.parameterCount()];for(int i=0;i<values.length;i++)values[i]=record.doubleParameter(i);
            AnnotateStabilizer pss=p.model().equals("PSS6C")?new Ieee2016PSS6CStabilizer("replay",Ieee2016PSS6CStabilizerData.fromPsseParameters(values),machine):new Ieee2016PSS7CStabilizer("replay",Ieee2016PSS7CStabilizerData.fromPsseParameters(values),machine);
            assertTrue(pss.initStates(machine.getDStabBus(),machine));
            var names=p.model().equals("PSS6C")?SIX:SEVEN;double[] maximum=new double[names.size()+1],at=new double[maximum.length];
            var trace=new StringBuilder("time_s");for(String name:names)trace.append(",IPSS_").append(name).append(",NATIVE_").append(name);trace.append(",IPSS_output,NATIVE_output\n");
            var old=rows.getFirst();double previous=old.get("time_s");
            for(var row:rows){double time=row.get("time_s");
                if(time>previous){machine.boundary(old);assertTrue(pss.nextStep(p.step(),DynamicSimuMethod.MODIFIED_EULER,machine,0));machine.boundary(row);assertTrue(pss.nextStep(p.step(),DynamicSimuMethod.MODIFIED_EULER,machine,1));}else machine.boundary(row);
                var states=pss.getNamedStates();trace.append(time);
                for(int k=0;k<maximum.length;k++){
                    double actual=k<names.size()?states.get(names.get(k)):pss.getOutput(machine);
                    double expected=k<names.size()?row.get(String.format(java.util.Locale.ROOT,"PSS_STATE_%02d",k+1)):row.get("VOTHSG");
                    assertTrue(Double.isFinite(actual));double error=Math.abs(actual-expected);if(error>maximum[k]){maximum[k]=error;at[k]=time;}trace.append(',').append(actual).append(',').append(expected);
                }
                trace.append('\n');old=row;previous=time;
            }
            Path output=Path.of("target/pss-comp-replay",p.name()+".csv");Files.createDirectories(output.getParent());Files.writeString(output,trace);
            System.out.println("Compensation replay "+p.name()+" maxima="+Arrays.toString(maximum)+" at="+Arrays.toString(at));allMaxima.put(p.name(),maximum);
            for(int k=0;k<maximum.length;k++)assertTrue(maximum[k]<p.replayBounds()[k],p.name()+" channel "+k+" error="+maximum[k]+" at="+at[k]);
        }
        assertEquals(8,allMaxima.size());
        for(String model:List.of("pss6c","pss7c"))for(String profile:List.of("mva50-positive","mva200-negative")) {
            double[] coarse=allMaxima.get(model+"-"+profile+"-coarse"),fine=allMaxima.get(model+"-"+profile+"-fine");
            for(int k:new int[]{coarse.length-2,coarse.length-1})assertTrue(fine[k]<.55*coarse[k],model+" "+profile+" must refine the compensation state and output");
        }
    }

    @Test void exactImportedProfilesInitializeAndRunThreeCycleFaults(@TempDir Path dir)throws Exception {
        int count=0;
        for(var p:PssCompensationNativeReference.profiles()) {
            Path input=dir.resolve(p.name());var context=load(p,input);
            var network=context.getDStabilityNet();var algorithm=context.getDynSimuAlgorithm();algorithm.getAclfAlgorithm().setTolerance(1e-10);
            assertTrue(algorithm.getAclfAlgorithm().loadflow());algorithm.setSimuMethod(DynamicSimuMethod.MODIFIED_EULER);algorithm.setSimuStepSec(p.step());algorithm.setTotalSimuTimeSec(.3);algorithm.setSimuOutputHandler(new StateMonitor());
            network.addDynamicEvent(DStabObjectFactory.createBusFaultEvent("Bus1",network,SimpleFaultCode.GROUND_3P,new Complex(0,.2),null,.05,.05),"compensation-three-cycle");assertTrue(algorithm.initialization());
            var machine=network.getMachine("Bus1-mach1");var pss=(AnnotateStabilizer)machine.getStabilizer();var initial=pss.getNamedStates();
            var first=HplntdSwitchingNativeSmibTest.rows(p.body()).getFirst();int states=p.model().equals("PSS6C")?10:19;
            assertEquals(first.get(String.format(java.util.Locale.ROOT,"PSS_STATE_%02d",states)),initial.get("compensatedFrequencyWashout"),8e-6,p.name()+" initialization");
            double minimum=machine.getDStabBus().getVoltageMag();var trace=new StringBuilder("nominal_time_s,v1,v2,speed,angle,efd,output");var names=p.model().equals("PSS6C")?SIX:SEVEN;for(String name:names)trace.append(',').append(name);trace.append('\n');
            int ticks=(int)Math.round(.3/p.step());
            for(int tick=0;tick<=ticks;tick++) {
                var named=pss.getNamedStates();for(double value:named.values())assertTrue(Double.isFinite(value));assertTrue(Double.isFinite(machine.getSpeed()));assertTrue(Double.isFinite(machine.getEfd()));
                if(tick*p.step()<.05)for(String key:initial.keySet())assertEquals(initial.get(key),named.get(key),1e-7,p.name()+" prefault "+key);
                double voltage=network.getBus("Bus1").getVoltageMag();minimum=Math.min(minimum,voltage);
                trace.append(tick*p.step()).append(',').append(voltage).append(',').append(network.getBus("Bus2").getVoltageMag()).append(',').append(machine.getSpeed()).append(',').append(machine.getAngle()).append(',').append(machine.getEfd()).append(',').append(pss.getOutput(machine));for(String name:names)trace.append(',').append(named.get(name));trace.append('\n');
                if(tick<ticks)assertTrue(algorithm.solveDEqnStep(true));
            }
            assertTrue(minimum<.95,"The fault must activate the model");Path output=Path.of("target/pss-comp-coupled",p.name()+".csv");Files.createDirectories(output.getParent());Files.writeString(output,trace);count++;
        }
        assertEquals(8,count);
    }
}
