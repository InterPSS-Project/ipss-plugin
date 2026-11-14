package org.interpss.core.dstab;

import static org.junit.jupiter.api.Assertions.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.HashMap;
import org.apache.commons.math3.complex.Complex;
import org.interpss.IpssCorePlugin;
import com.interpss.dstab.controller.exc.ac1c.Ac1cExciter;
import com.interpss.dstab.controller.oel.oel3c.Oel3cModel;
import org.interpss.fadapter.psse.PSSEMultiFileLoader;
import org.interpss.fadapter.psse.PsseDStabSolver;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import com.interpss.core.acsc.fault.SimpleFaultCode;
import com.interpss.dstab.DStabObjectFactory;
import com.interpss.dstab.algo.DynamicSimuMethod;
import com.interpss.dstab.algo.DynamicSimuAlgorithm;
import com.interpss.dstab.cache.StateMonitor;
import com.interpss.dstab.common.DStabSimuException;

/** Public GENROU/AC1C/OEL3C network coupling; finite runs are not independent acceptance. */
public class Oel3cSmibTest {
    private static final String HEADER="time_s,V_BUS1,V_BUS2,EFD,MACH_SPEED,OEL_INPUT,OEL_INTEGRAL,OEL_REFERENCE,OEL_ERROR,OEL_GAINED_ERROR,OEL_PROPORTIONAL,OEL_OUTPUT,VFE,PMW,QMVAR,MACH_ANGLE,REF_SPEED,EXC_FIELD,EXC_SENSE,EXC_REGULATOR,EXC_LEAD_LAG,EXC_RATE_FEEDBACK";
    private static double[] snapshot(DynamicSimuAlgorithm algorithm,double time){
        var net=algorithm.getNetwork();var machine=net.getMachine("Bus1-mach1");
        var reference=net.getMachine("Bus2-mach1");var exc=(Ac1cExciter)machine.getExciter();
        var oel=(Oel3cModel)net.getBus("Bus1").getDynamicBusDeviceList().stream().filter(Oel3cModel.class::isInstance).findFirst().orElseThrow();
        var signals=oel.getSignals();Complex voltage=machine.getDStabBus().getVoltage();
        Complex power=voltage.multiply(machine.getIgen().subtract(voltage.multiply(machine.getYgen())).conjugate()).multiply(100);
        return new double[]{time,net.getBus("Bus1").getVoltageMag(),net.getBus("Bus2").getVoltageMag(),
            machine.getEfd(),machine.getSpeed()-1,signals.scaledInput(),oel.getPublishedStates().get("Integrator VIOEL"),
            signals.reference(),signals.error(),signals.gainedError(),signals.proportional(),oel.getOutput(),exc.getFieldFeedback(),
            power.getReal(),power.getImaginary(),Math.toDegrees(machine.getAngle()),reference.getSpeed()-1,
            exc.getInternalFieldVoltage(),exc.getSensedVoltage(),exc.getRegulatorOutput(),exc.getLeadLagOutput(),exc.getRateFeedback(),
            machine.calculateIfd(com.interpss.dstab.mach.MachineIfdBase.EXCITER),Math.toDegrees(reference.getAngle())};
    }
    private static String line(double[] values){
        return java.util.Arrays.stream(values).mapToObj(Double::toString).collect(java.util.stream.Collectors.joining(","));
    }
    private static final class ObservedSolver extends PsseDStabSolver {
        private final DynamicSimuAlgorithm algorithm;
        private final double step;
        private final Map<Integer,double[]> post=new HashMap<>();
        ObservedSolver(DynamicSimuAlgorithm algorithm,double step){super(algorithm);this.algorithm=algorithm;this.step=step;}
        @Override public void diffEqnIntegrationStep(double time,double dt,DynamicSimuMethod method,int flag)throws DStabSimuException{
            // INITIAL network convergence has completed; no positive-time state
            // integration has occurred. Observe, then delegate without changing dt.
            if(flag==0&&dt>0&&(Math.abs(time-.05)<1e-12||Math.abs(time-.1)<1e-12)){
                int tick=(int)Math.round(time/step);
                assertNull(post.put(tick,snapshot(algorithm,time)),"Duplicate event observation");
            }
            super.diffEqnIntegrationStep(time,dt,method,flag);
        }
    }
    @TempDir Path directory;
    @ParameterizedTest
    @CsvSource({"1,0,.0005,false","2,0,.0005,false","3,0,.0005,false","17,0,.0005,false",
        "1,0,.00025,false","2,0,.00025,false","3,0,.00025,false","17,0,.00025,false",
        "1,0,.0005,true","2,0,.0005,true","3,0,.0005,true",
        "1,0,.00025,true","2,0,.00025,true","3,0,.00025,true",
        "1,.02,.0005,true","3,.02,.00025,true"})
    void publicNetworkInitializesAndRunsWithoutHiddenSignalSubstitution(int selector,double tf,double h,boolean fault)throws Exception {
        run(selector,tf,h,fault,false);
    }
    @ParameterizedTest @CsvSource({"1,.0005","1,.00025","2,.0005","2,.00025","3,.0005","3,.00025"})
    void independentImpedanceLoadProfiles(int selector,double h)throws Exception {
        run(selector,0,h,true,true);
    }
    private void run(int selector,double tf,double h,boolean fault,boolean impedanceLoad)throws Exception {
        IpssCorePlugin.init();Path dyr=directory.resolve("oel3c.dyr");
        Files.writeString(dyr,"1 'GENROU' '1' 6 .033 .54 .078 6.4 0 .8958 .8645 .1189 .1969 .089 .0521 0 0 /\n"
            +"2 'GENCLS' '1' 99999 0 /\n"
            +"1 'AC1C' '1' 1 0 0 .2 .05 10 .3 20 -20 .4 .2 .5 .2 .1 1.1 .75 0 1 0 20 -20 99 0 /\n"
            +"1 'OEL3C' '1' "+selector+" 1 "+(fault?.5:4.47)+" .3333 "+tf+" .1 .1 1 0 -1 0 -1 /\n");
        var context=new PSSEMultiFileLoader().loadDStab("testData/adpter/psse/v33/SMIB/SMIB_v33.raw",dyr.toString());
        var net=context.getDStabilityNet();var alg=context.getDynSimuAlgorithm();
        // Match the reference probe's zero current/admittance load conversion.
        if(!impedanceLoad){
            net.setStaticLoadIncludedInYMatrix(false);
            net.setStaticLoadModel(com.interpss.dstab.StaticLoadModel.CONST_P);
            net.setReactiveStaticLoadModel(com.interpss.dstab.StaticLoadModel.CONST_P);
        }else{
            net.setStaticLoadIncludedInYMatrix(true);
            net.setStaticLoadModel(com.interpss.dstab.StaticLoadModel.CONST_Z);
            net.setReactiveStaticLoadModel(com.interpss.dstab.StaticLoadModel.CONST_Z);
        }
        assertTrue(alg.getAclfAlgorithm().loadflow());alg.setSimuMethod(DynamicSimuMethod.MODIFIED_EULER);
        alg.setSimuStepSec(h);alg.setTotalSimuTimeSec(.5);alg.setSimuOutputHandler(new StateMonitor());
        ObservedSolver observer=impedanceLoad?new ObservedSolver(alg,h):null;
        if(observer!=null)alg.setSolver(observer);
        if(fault)net.addDynamicEvent(DStabObjectFactory.createBusFaultEvent("Bus1",net,
            SimpleFaultCode.GROUND_3P,new Complex(0,.2),null,.05,.05),"Oel3cFault");
        assertTrue(alg.initialization());var machine=net.getMachine("Bus1-mach1");var exc=(Ac1cExciter)machine.getExciter();
        var oel=(Oel3cModel)net.getBus("Bus1").getDynamicBusDeviceList().stream().filter(Oel3cModel.class::isInstance).findFirst().orElseThrow();
        assertTrue(oel.isInitialized());assertTrue(machine instanceof com.interpss.dstab.mach.RoundRotorMachine);
        double expectedInput=selector==2?machine.calculateIfd(com.interpss.dstab.mach.MachineIfdBase.EXCITER):
            selector==3?exc.getFieldFeedback():machine.getEfd();
        assertEquals(expectedInput,oel.getSelectedInput(),1e-12);
        double initialVoltage=net.getBus("Bus1").getVoltageMag(),initialEfd=machine.getEfd();
        List<String> rows=new ArrayList<>();rows.add(HEADER);
        List<double[]> measurements=new ArrayList<>();
        double largestDrift=0,lowestOutput=0;
        while(true){
            double[] values=snapshot(alg,alg.getSimuTime());
            for(double value:values)assertTrue(Double.isFinite(value));
            assertTrue(oel.getOutput()>=-1&&oel.getOutput()<=0);assertTrue(values[6]>=-1&&values[6]<=0);
            lowestOutput=Math.min(lowestOutput,oel.getOutput());largestDrift=Math.max(largestDrift,Math.abs(values[1]-initialVoltage));
            rows.add(line(java.util.Arrays.copyOf(values,22)));measurements.add(values);
            if(alg.getSimuTime()>=.5-h/2)break;assertTrue(alg.solveDEqnStep(true));
        }
        if(!fault){assertTrue(largestDrift<1e-7,"Flat voltage drift="+largestDrift);assertEquals(initialEfd,machine.getEfd(),1e-7);}
        System.out.println("OEL3C selector="+selector+" TF="+tf+" h="+h+" fault="+fault+" maxVoltageDrift="+largestDrift+" minOutput="+lowestOutput);
        Path destination=Path.of("target","oel3c-runtime-coupled");Files.createDirectories(destination);
        String name="input"+selector+"-tf"+tf+"-h"+h+"-"+(fault?"fault":"flat")+(impedanceLoad?"-impedance":"")+".csv";
        Files.write(destination.resolve(name),rows);
        if(observer!=null){
            assertEquals(2,observer.post.size());
            List<String> enriched=new ArrayList<>();enriched.add("event_side,"+HEADER+",IFD_EXC,REF_ANGLE");
            for(int tick=0;tick<measurements.size();tick++){
                double[] before=measurements.get(tick),after=observer.post.get(tick);
                enriched.add((after==null?"regular,":"pre,")+line(before));
                if(after!=null){
                    assertEquals(before[0],after[0],1e-12);
                    for(int index:new int[]{4,6,17})assertEquals(before[index],after[index],1e-12,"Continuous state at event, column "+index);
                    if(tick==Math.round(.05/h))assertTrue(after[1]<before[1]-.05,"Fault must reduce instantaneous voltage");
                    else assertTrue(after[1]>before[1]+.05,"Clearing must restore instantaneous voltage");
                    for(double value:after)assertTrue(Double.isFinite(value));
                    enriched.add("post,"+line(after));
                }
            }
            assertEquals((int)Math.round(.5/h)+4,enriched.size());
            Path observed=Path.of("target","oel3c-event-coupled");Files.createDirectories(observed);
            Files.write(observed.resolve(name),enriched);
        }
    }
}
