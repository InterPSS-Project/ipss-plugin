package org.interpss.core.dstab;

import static org.junit.jupiter.api.Assertions.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.stream.Stream;
import org.apache.commons.math3.complex.Complex;
import org.interpss.IpssCorePlugin;
import org.interpss.core.dstab.reference.BenchmarkOutputGuard;
import com.interpss.dstab.controller.exc.ac1c.Ac1cExciter;
import org.interpss.dstab.control.oel.psse.oel4c.*;
import org.interpss.fadapter.builder.DStabNetworkBuilder;
import org.interpss.fadapter.psse.PSSEMultiFileLoader;
import org.interpss.fadapter.psse.PsseDStabSolver;
import com.interpss.core.acsc.fault.SimpleFaultCode;
import com.interpss.dstab.DStabObjectFactory;
import com.interpss.dstab.algo.DynamicSimuAlgorithm;
import com.interpss.dstab.algo.DynamicSimuMethod;
import com.interpss.dstab.cache.StateMonitor;
import com.interpss.dstab.common.DStabSimuException;
import com.interpss.dstab.mach.MachineIfdBase;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

/** Typed runtime flat/fault/base coverage; not independent coupled trajectory acceptance. */
public class Oel4cSmibTest {
    private static final String HEADER="event_side,time_s,V_BUS1,V_BUS2,SPEED_DEV,REF_SPEED_DEV,RELATIVE_ANGLE,EFD,IFD_EXC,P_PU,Q_PU,Q_MACHINE,OEL_INTEGRAL,OEL_OUTPUT,OEL_CONTROLLED_ERROR,OEL_TIMER,OEL_DELAY_GATE,EXC_FIELD,EXC_SENSE,EXC_REGULATOR,EXC_LEAD_LAG,EXC_RATE_FEEDBACK";
    private static Path output;
    private static synchronized Path directory()throws Exception {
        if(output==null){Path classes=Path.of(Oel4cSmibTest.class.getProtectionDomain().getCodeSource().getLocation().toURI());
            output=Files.createTempDirectory(classes.getParent(),"oel4c-typed-coupled-");}return output;
    }
    static Stream<Arguments> profiles(){
        var standard=Stream.of("flat","flat-initial-limit","delayed-fault","zero-delay-fault","integral-fault","lower-limit-fault","qmax-fault")
            .flatMap(p->Stream.of(.0005,.00025).map(h->Arguments.of(p,h,100.)));
        var bases=Stream.of(50.,200.).flatMap(mva->Stream.of("flat","delayed-fault")
            .flatMap(p->Stream.of(.0005,.00025).map(h->Arguments.of(p,h,mva))));
        return Stream.concat(standard,bases);
    }
    private static Oel4cModel limiter(DynamicSimuAlgorithm a){
        return (Oel4cModel)a.getNetwork().getBus("Bus1").getDynamicBusDeviceList().stream().filter(Oel4cModel.class::isInstance).findFirst().orElseThrow();
    }
    private static double[] snapshot(DynamicSimuAlgorithm a,double time){
        var net=a.getNetwork();var m=net.getMachine("Bus1-mach1");var ref=net.getMachine("Bus2-mach1");
        var exc=(Ac1cExciter)m.getExciter();var o=limiter(a);var s=o.getSignals();var v=m.getDStabBus().getVoltage();
        var power=v.multiply(m.getIgen().subtract(v.multiply(m.getYgen())).conjugate());
        return new double[]{time,net.getBus("Bus1").getVoltageMag(),net.getBus("Bus2").getVoltageMag(),
            m.getSpeed()-1,ref.getSpeed()-1,m.getAngle()-ref.getAngle(),m.getEfd(),m.calculateIfd(MachineIfdBase.EXCITER),
            power.getReal(),power.getImaginary(),o.getSampledReactivePower(),s.integral(),o.getOutput(),s.controlledError(),s.elapsed(),s.active()?1:0,
            exc.getInternalFieldVoltage(),exc.getSensedVoltage(),exc.getRegulatorOutput(),exc.getLeadLagOutput(),exc.getRateFeedback()};
    }
    private static Map<String,Double> state(Oel4cModel o){var result=new HashMap<>(o.getNamedStates());result.put("timer",o.getSignals().elapsed());return Map.copyOf(result);}
    private static final class Observer extends PsseDStabSolver {
        final DynamicSimuAlgorithm a;final double h;final boolean observeEvents;final Map<Integer,double[]> post=new HashMap<>();
        final Map<Integer,Map<String,Double>> before=new HashMap<>();
        Observer(DynamicSimuAlgorithm a,double h,boolean observeEvents){super(a);this.a=a;this.h=h;this.observeEvents=observeEvents;}
        @Override public void diffEqnIntegrationStep(double t,double dt,DynamicSimuMethod method,int flag)throws DStabSimuException {
            if(observeEvents&&flag==0&&dt>0&&(Math.abs(t-.05)<1e-12||Math.abs(t-.1)<1e-12)){
                int tick=(int)Math.round(t/h);assertNull(post.put(tick,snapshot(a,t)));
                assertEquals(before.get(tick),state(limiter(a)),"State advanced during a zero-time network event");
            }
            super.diffEqnIntegrationStep(t,dt,method,flag);
        }
    }
    private static String line(String side,double[] values){
        assertEquals(HEADER.split(",").length-1,values.length);for(double v:values)assertTrue(Double.isFinite(v));
        return side+","+java.util.Arrays.stream(values).mapToObj(Double::toString).collect(java.util.stream.Collectors.joining(","));
    }
    @ParameterizedTest @MethodSource("profiles")
    void typedLimiterRunsOnCoreGenrouWithFullEventSides(String profile,double h,double mva)throws Exception {
        IpssCorePlugin.init();Path work=Files.createTempDirectory(directory(),"case-");Path raw=work.resolve("network.raw"),dyr=work.resolve("model.dyr");
        String text=Files.readString(Path.of("testData/adpter/psse/v33/SMIB/SMIB_v33.raw"));
        String gen=text.lines().filter(s->s.startsWith("1,'1',")).findFirst().orElseThrow();String[] fields=gen.split(",");fields[8]=Double.toString(mva);
        Files.writeString(raw,text.replace(gen,String.join(",",fields)));
        Files.writeString(dyr,"1 'GENROU' '1' 6 .033 .54 .078 6.4 0 .8958 .8645 .1189 .1969 .089 .0521 0 0 /\n"
            +"2 'GENCLS' '1' 99999 0 /\n1 'AC1C' '1' 1 0 0 .2 .05 10 .3 20 -20 .4 .2 .5 .2 .1 1.1 .75 0 1 0 20 -20 99 0 /\n");
        var context=new PSSEMultiFileLoader().loadDStab(raw.toString(),dyr.toString());var net=context.getDStabilityNet();var alg=context.getDynSimuAlgorithm();
        var data=new Oel4cData(profile.equals("flat")?1:profile.equals("flat-initial-limit")?.005:profile.equals("qmax-fault")?0:.35,
            profile.equals("zero-delay-fault")||profile.equals("flat-initial-limit")?0:.01,
            profile.equals("integral-fault")?0:profile.equals("lower-limit-fault")?3:.15,
            profile.equals("flat-initial-limit")?0:profile.equals("lower-limit-fault")?4:.25,profile.equals("lower-limit-fault")?-.02:-.25);
        var o=new DStabNetworkBuilder(net).addOel4c("Bus1","1",data);assertNotNull(o);
        net.setStaticLoadIncludedInYMatrix(true);net.setStaticLoadModel(com.interpss.dstab.StaticLoadModel.CONST_Z);
        net.setReactiveStaticLoadModel(com.interpss.dstab.StaticLoadModel.CONST_Z);
        assertTrue(alg.getAclfAlgorithm().loadflow());alg.setSimuMethod(DynamicSimuMethod.MODIFIED_EULER);
        alg.setSimuStepSec(h);alg.setTotalSimuTimeSec(.5);alg.setSimuOutputHandler(new StateMonitor());
        var solver=new Observer(alg,h,!profile.startsWith("flat"));alg.setSolver(solver);boolean fault=!profile.startsWith("flat");
        if(fault)net.addDynamicEvent(DStabObjectFactory.createBusFaultEvent("Bus1",net,SimpleFaultCode.GROUND_3P,new Complex(0,.2),null,.05,.05),"Oel4cFault");
        assertTrue(alg.initialization());assertTrue(o.isInitialized());
        assertTrue(net.getMachine("Bus1-mach1") instanceof com.interpss.dstab.mach.RoundRotorMachine);
        double initial=net.getBus("Bus1").getVoltageMag(),drift=0,lowest=0,minimumVoltage=initial;
        if(profile.equals("flat-initial-limit"))assertTrue(o.getOutput()<0);
        Path trace=directory().resolve(profile+"-mva"+mva+"-"+h+".csv");
        try(var config=Files.newBufferedWriter(work.resolve("case.json"))){
            new com.google.gson.GsonBuilder().setPrettyPrinting().create().toJson(Map.of("profile",profile,"step_s",h,
                "machine_base_mva",mva,"network_base_mva",100.,"data",data,"network","network.raw","dynamic","model.dyr",
                "typed_limiter",true,"trace_file","../"+trace.getFileName()),config);
        }
        try(var writer=BenchmarkOutputGuard.newWriter(trace,BenchmarkOutputGuard.estimateCsvBytes(Math.round(.5/h)+3,21))){
            writer.write(HEADER);writer.newLine();int samples=0;
            while(true){
                double time=alg.getSimuTime();int tick=(int)Math.round(time/h);var values=snapshot(alg,time);
                boolean event=fault&&(Math.abs(time-.05)<1e-12||Math.abs(time-.1)<1e-12);
                writer.write(line(event?"pre":"regular",values));writer.newLine();samples++;
                drift=Math.max(drift,Math.abs(values[1]-initial));minimumVoltage=Math.min(minimumVoltage,values[1]);lowest=Math.min(lowest,o.getOutput());
                assertTrue(o.getOutput()<=1e-12&&o.getOutput()>=data.minimumOutput()-1e-12);
                assertEquals(values[9]*100/mva,values[10],1e-9);
                if(time>=.5-h/2)break;
                if(event)solver.before.put(tick,state(o));assertTrue(alg.solveDEqnStep(true));
                if(fault&&solver.post.containsKey(tick)){writer.write(line("post",solver.post.get(tick)));writer.newLine();}
            }
            assertEquals(Math.round(.5/h)+1,samples);if(fault){assertEquals(2,solver.post.size());assertTrue(minimumVoltage<initial-.1);assertTrue(lowest<0);}
            else assertTrue(drift<1e-7,"Flat voltage drift="+drift);
        }
        System.out.println("OEL4C typed "+profile+" base="+mva+" h="+h+" drift="+drift+" minimumOutput="+lowest);
    }
}
