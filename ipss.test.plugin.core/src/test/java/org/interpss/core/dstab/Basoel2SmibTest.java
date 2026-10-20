package org.interpss.core.dstab;

import static org.junit.jupiter.api.Assertions.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.stream.Stream;
import org.apache.commons.math3.complex.Complex;
import org.interpss.IpssCorePlugin;
import org.interpss.dstab.control.exc.psse.ac1c.Ac1cExciter;
import org.interpss.dstab.control.oel.psse.basoel2.*;
import org.interpss.fadapter.builder.DStabNetworkBuilder;
import org.interpss.fadapter.psse.PSSEMultiFileLoader;
import org.interpss.fadapter.psse.PsseDStabSolver;
import org.interpss.core.dstab.reference.BenchmarkOutputGuard;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import com.interpss.core.acsc.fault.SimpleFaultCode;
import com.interpss.dstab.DStabObjectFactory;
import com.interpss.dstab.algo.DynamicSimuMethod;
import com.interpss.dstab.algo.DynamicSimuAlgorithm;
import com.interpss.dstab.cache.StateMonitor;
import com.interpss.dstab.common.DStabSimuException;
import com.interpss.dstab.mach.MachineIfdBase;

/** Typed published-dialect flat/fault coupling; not exact native trajectory acceptance. */
public class Basoel2SmibTest {
    private static final String HEADER="event_side,time_s,V_BUS1,V_BUS2,SPEED_DEV,REF_SPEED_DEV,RELATIVE_ANGLE,EFD,IFD_EXC,VFE,VREF,BUS_FREQUENCY,BAS_INPUT,BAS_INTEGRAL,BAS_FREQUENCY,BAS_REFERENCE,BAS_ERROR,BAS_PROPORTIONAL,BAS_PI,BAS_OEL_OUTPUT,BAS_VHZ_OUTPUT,BAS_OUTPUT,P_PU,Q_PU,EXC_FIELD,EXC_SENSE,EXC_REGULATOR,EXC_LEAD_LAG,EXC_RATE_FEEDBACK";
    private static final String TIMER_HEADER=",BAS_T1_ACTIVE,BAS_T1_ABOVE,BAS_T1_TIME,BAS_T2_ACTIVE,BAS_T2_ABOVE,BAS_T2_TIME,BAS_T3_ACTIVE,BAS_T3_ABOVE,BAS_T3_TIME";
    private static Path output;
    private static synchronized Path outputDirectory()throws Exception{
        if(output==null){
            Path classes=Path.of(Basoel2SmibTest.class.getProtectionDomain().getCodeSource().getLocation().toURI());
            output=Files.createTempDirectory(classes.getParent(),"basoel2-typed-coupled-");
        }
        return output;
    }
    static Stream<Arguments> profiles(){
        return Stream.of("flat","flat-vhz","efd-fault","ifd-fault","vfe-fault","vhz-fault","disabled-fault","ifd-zero-filter")
            .flatMap(profile->Stream.of(.0005,.00025).flatMap(step->Stream.of(Basoel2Kernel.TimerSemantics.values())
                .map(policy->Arguments.of(profile,step,policy))));
    }
    private static Basoel2Model limiter(DynamicSimuAlgorithm algorithm){
        return (Basoel2Model)algorithm.getNetwork().getBus("Bus1").getDynamicBusDeviceList().stream()
            .filter(Basoel2Model.class::isInstance).findFirst().orElseThrow();
    }
    private static double[] snapshot(DynamicSimuAlgorithm algorithm,double time,boolean timers){
        var net=algorithm.getNetwork();var m=net.getMachine("Bus1-mach1");var ref=net.getMachine("Bus2-mach1");
        var exc=(Ac1cExciter)m.getExciter();var oel=limiter(algorithm);var s=oel.getSignals();
        Complex v=m.getDStabBus().getVoltage();Complex power=v.multiply(m.getIgen().subtract(v.multiply(m.getYgen())).conjugate());
        double[] base=new double[]{time,net.getBus("Bus1").getVoltageMag(),net.getBus("Bus2").getVoltageMag(),
            m.getSpeed()-1,ref.getSpeed()-1,m.getAngle()-ref.getAngle(),m.getEfd(),m.calculateIfd(MachineIfdBase.EXCITER),
            exc.getFieldFeedback(),exc.reference,m.getDStabBus().getFreq(),s.sensedInput(),
            oel.getPublishedStates().get("PI integral"),s.sensedFrequency(),s.reference(),s.error(),s.proportional(),s.pi(),
            s.oelOutput(),s.vhzOutput(),oel.getOutput(),power.getReal(),power.getImaginary(),exc.getInternalFieldVoltage(),
            exc.getSensedVoltage(),exc.getRegulatorOutput(),exc.getLeadLagOutput(),exc.getRateFeedback()};
        if(!timers)return base;
        double[] values=java.util.Arrays.copyOf(base,base.length+9);int offset=base.length;
        for(var timer:oel.getTimers()){
            values[offset++]=timer.active()?1:0;values[offset++]=timer.above()?1:0;values[offset++]=timer.elapsed();
        }
        return values;
    }
    private static String line(String side,double[] values,boolean timers){
        assertEquals((HEADER+(timers?TIMER_HEADER:"")).split(",").length-1,values.length);
        for(double value:values)assertTrue(Double.isFinite(value));
        return side+","+java.util.Arrays.stream(values).mapToObj(Double::toString).collect(java.util.stream.Collectors.joining(","));
    }
    private static final class ObservedSolver extends PsseDStabSolver {
        private final DynamicSimuAlgorithm algorithm;private final double step;private final boolean timers;
        private final Map<Integer,double[]> post=new HashMap<>();
        private final Map<Integer,Map<String,Double>> preState=new HashMap<>();
        ObservedSolver(DynamicSimuAlgorithm algorithm,double step,boolean timers){super(algorithm);this.algorithm=algorithm;this.step=step;this.timers=timers;}
        @Override public void diffEqnIntegrationStep(double time,double dt,DynamicSimuMethod method,int flag)throws DStabSimuException{
            if(flag==0&&dt>0&&(Math.abs(time-.05)<1e-12||Math.abs(time-.1)<1e-12)){
                int tick=(int)Math.round(time/step);assertNull(post.put(tick,snapshot(algorithm,time,timers)));
                assertEquals(preState.get(tick),limiter(algorithm).getNamedStates(),"Continuous state changed at zero-time event");
            }
            super.diffEqnIntegrationStep(time,dt,method,flag);
        }
    }
    @ParameterizedTest @MethodSource("profiles")
    void explicitPublishedPoliciesRunOnTheCoreRoundRotorMachine(String profile,double h,Basoel2Kernel.TimerSemantics policy)throws Exception{
        run(profile,h,policy,false,outputDirectory());
    }
    static void run(String profile,double h,Basoel2Kernel.TimerSemantics policy,boolean impedanceLoad,Path destination)throws Exception{
        run(profile,h,policy,impedanceLoad,destination,false);
    }
    static void run(String profile,double h,Basoel2Kernel.TimerSemantics policy,boolean impedanceLoad,Path destination,boolean importRecord)throws Exception{
        IpssCorePlugin.init();Path work=Files.createTempDirectory(destination,"case-");Path dyr=work.resolve("model.dyr");
        Files.writeString(dyr,"1 'GENROU' '1' 6 .033 .54 .078 6.4 0 .8958 .8645 .1189 .1969 .089 .0521 0 0 /\n"
            +"2 'GENCLS' '1' 99999 0 /\n1 'AC1C' '1' 1 0 0 .2 .05 10 .3 20 -20 .4 .2 .5 .2 .1 1.1 .75 0 1 0 20 -20 99 0 /\n");
        var context=new PSSEMultiFileLoader().loadDStab("testData/adpter/psse/v33/SMIB/SMIB_v33.raw",dyr.toString());
        var net=context.getDStabilityNet();var alg=context.getDynSimuAlgorithm();boolean fault=!profile.startsWith("flat");
        int selector=profile.equals("efd-fault")?0:profile.equals("vfe-fault")?2:1;
        int enabled=profile.equals("disabled-fault")||profile.equals("vhz-fault")||profile.equals("flat-vhz")?0:1;
        int vhz=profile.startsWith("flat")||profile.equals("vhz-fault")?1:0;
        boolean efd=impedanceLoad&&profile.equals("efd-fault"),vfe=impedanceLoad&&profile.equals("vfe-fault");
        double level=!fault?4:efd?1.3:vfe?1.7:1.8;
        var data=new Basoel2Data(enabled,vhz,selector,profile.equals("ifd-zero-filter")?0:.02,.15,.25,
            level,efd?0:.01,!fault?3.5:efd?1.2:vfe?1.6:1.5,efd?.01:.02,!fault?2.5:efd?1.15:vfe?1.5:1.2,efd?.01:.03,.02,
            profile.equals("flat-vhz")?.9:profile.equals("vhz-fault")?1.0001:1.05,.1);
        Basoel2Model oel;
        if(importRecord){
            assertEquals(org.interpss.fadapter.psse.PSSEDStabDirectParser.BASOEL2_TIMER_POLICY,policy);
            String record="1 'BASOEL2' '1' "+data.oelFlag()+" "+data.vhzFlag()+" "+data.inputFlag()+" "+
                data.inputFilterTime()+" "+data.proportionalGain()+" "+data.integralGain()+" "+data.shortLimit()+" "+
                data.shortDelay()+" "+data.mediumLimit()+" "+data.mediumDelay()+" "+data.continuousLimit()+" "+
                data.continuousDelay()+" "+data.speedFilterTime()+" "+data.vhzGain()+" "+data.minimumVhzInput()+" /\n";
            Path imported=work.resolve("limiter.dyr");Files.writeString(imported,record);
            new org.interpss.fadapter.psse.PSSEDStabDirectParser(new DStabNetworkBuilder(net)).setStrictImport(true).parseDynFile(imported.toString());
            Files.writeString(dyr,Files.readString(dyr)+record);
            oel=limiter(alg);assertEquals(data,oel.getData());
        }else oel=new DStabNetworkBuilder(net).addBasoel2("Bus1","1",data,policy);
        assertNotNull(oel);
        net.setStaticLoadIncludedInYMatrix(impedanceLoad);
        var loadModel=impedanceLoad?com.interpss.dstab.StaticLoadModel.CONST_Z:com.interpss.dstab.StaticLoadModel.CONST_P;
        net.setStaticLoadModel(loadModel);net.setReactiveStaticLoadModel(loadModel);
        assertTrue(alg.getAclfAlgorithm().loadflow());alg.setSimuMethod(DynamicSimuMethod.MODIFIED_EULER);
        alg.setSimuStepSec(h);alg.setTotalSimuTimeSec(.5);alg.setSimuOutputHandler(new StateMonitor());
        var solver=new ObservedSolver(alg,h,impedanceLoad);alg.setSolver(solver);
        if(fault)net.addDynamicEvent(DStabObjectFactory.createBusFaultEvent("Bus1",net,
            SimpleFaultCode.GROUND_3P,new Complex(0,.2),null,.05,.05),"Basoel2Fault");
        assertTrue(alg.initialization());assertTrue(net.getMachine("Bus1-mach1") instanceof com.interpss.dstab.mach.RoundRotorMachine);
        assertTrue(oel.isInitialized());double initial=net.getBus("Bus1").getVoltageMag(),drift=0,lowest=0;
        double initialOutput=oel.getOutput();if(profile.equals("flat-vhz"))assertTrue(initialOutput<-.05);
        Path file=destination.resolve(profile+"-"+policy+"-"+h+".csv");
        Files.copy(Path.of("testData/adpter/psse/v33/SMIB/SMIB_v33.raw"),work.resolve("network.raw"));
        try(var config=Files.newBufferedWriter(work.resolve("case.json"))){
            new com.google.gson.GsonBuilder().setPrettyPrinting().create().toJson(Map.of(
                "profile",profile,"step_s",h,"timer_policy",policy.name(),"impedance_load",impedanceLoad,
                "base_mva",net.getBaseKva()/1000.,"data",data,"network","network.raw","dynamic","model.dyr",
                "trace_file","../"+file.getFileName()),config);
        }
        try(var writer=BenchmarkOutputGuard.newWriter(file,BenchmarkOutputGuard.estimateCsvBytes(Math.round(.5/h)+3,impedanceLoad?37:28))){
            writer.write(HEADER+(impedanceLoad?TIMER_HEADER:""));writer.newLine();int samples=0;
            while(true){
                double time=alg.getSimuTime();int tick=(int)Math.round(time/h);double[] before=snapshot(alg,time,impedanceLoad);
                writer.write(line(fault&&(Math.abs(time-.05)<1e-12||Math.abs(time-.1)<1e-12)?"pre":"regular",before,impedanceLoad));writer.newLine();samples++;
                drift=Math.max(drift,Math.abs(before[1]-initial));lowest=Math.min(lowest,oel.getOutput());
                assertTrue(oel.getSignals().oelOutput()<=0);assertTrue(oel.getSignals().vhzOutput()<=0);
                assertTrue(oel.getPublishedStates().get("PI integral")>=0);
                if(!fault)assertEquals(initialOutput,oel.getOutput(),1e-7);
                if(profile.equals("disabled-fault"))assertEquals(0,oel.getOutput());
                if(time>=.5-h/2)break;
                if(Math.abs(time-.05)<1e-12||Math.abs(time-.1)<1e-12)solver.preState.put(tick,oel.getNamedStates());
                if(profile.equals("vhz-fault")&&Math.abs(time-.05)<1e-12)((Ac1cExciter)net.getMachine("Bus1-mach1").getExciter()).reference+=.2;
                assertTrue(alg.solveDEqnStep(true));
                double[] post=solver.post.get(tick);
                if(fault&&post!=null){writer.write(line("post",post,impedanceLoad));writer.newLine();}
            }
            assertEquals(Math.round(.5/h)+1,samples);
        }
        if(!fault)assertTrue(drift<1e-7,"Flat drift="+drift);
        if(fault)assertEquals(2,solver.post.size());
        System.out.println("BASOEL2 typed "+profile+" "+policy+" h="+h+" drift="+drift+" lowestOutput="+lowest);
    }
}
