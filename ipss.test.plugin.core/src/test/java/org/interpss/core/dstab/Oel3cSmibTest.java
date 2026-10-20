package org.interpss.core.dstab;

import static org.junit.jupiter.api.Assertions.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import org.apache.commons.math3.complex.Complex;
import org.interpss.IpssCorePlugin;
import org.interpss.dstab.control.exc.psse.ac1c.Ac1cExciter;
import org.interpss.dstab.control.oel.psse.oel3c.Oel3cModel;
import org.interpss.fadapter.psse.PSSEMultiFileLoader;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import com.interpss.core.acsc.fault.SimpleFaultCode;
import com.interpss.dstab.DStabObjectFactory;
import com.interpss.dstab.algo.DynamicSimuMethod;
import com.interpss.dstab.cache.StateMonitor;

/** Public GENROU/AC1C/OEL3C network coupling; finite runs are not independent acceptance. */
public class Oel3cSmibTest {
    @TempDir Path directory;
    @ParameterizedTest
    @CsvSource({"1,0,.0005,false","2,0,.0005,false","3,0,.0005,false","17,0,.0005,false",
        "1,0,.00025,false","2,0,.00025,false","3,0,.00025,false","17,0,.00025,false",
        "1,0,.0005,true","2,0,.0005,true","3,0,.0005,true",
        "1,0,.00025,true","2,0,.00025,true","3,0,.00025,true",
        "1,.02,.0005,true","3,.02,.00025,true"})
    void publicNetworkInitializesAndRunsWithoutHiddenSignalSubstitution(int selector,double tf,double h,boolean fault)throws Exception {
        IpssCorePlugin.init();Path dyr=directory.resolve("oel3c.dyr");
        Files.writeString(dyr,"1 'GENROU' '1' 6 .033 .54 .078 6.4 0 .8958 .8645 .1189 .1969 .089 .0521 0 0 /\n"
            +"2 'GENCLS' '1' 99999 0 /\n"
            +"1 'AC1C' '1' 1 0 0 .2 .05 10 .3 20 -20 .4 .2 .5 .2 .1 1.1 .75 0 1 0 20 -20 99 0 /\n"
            +"1 'OEL3C' '1' "+selector+" 1 "+(fault?.5:4.47)+" .3333 "+tf+" .1 .1 1 0 -1 0 -1 /\n");
        var context=new PSSEMultiFileLoader().loadDStab("testData/adpter/psse/v33/SMIB/SMIB_v33.raw",dyr.toString());
        var net=context.getDStabilityNet();var alg=context.getDynSimuAlgorithm();
        // Match the reference probe's zero current/admittance load conversion.
        net.setStaticLoadIncludedInYMatrix(false);
        net.setStaticLoadModel(com.interpss.dstab.StaticLoadModel.CONST_P);
        net.setReactiveStaticLoadModel(com.interpss.dstab.StaticLoadModel.CONST_P);
        assertTrue(alg.getAclfAlgorithm().loadflow());alg.setSimuMethod(DynamicSimuMethod.MODIFIED_EULER);
        alg.setSimuStepSec(h);alg.setTotalSimuTimeSec(.5);alg.setSimuOutputHandler(new StateMonitor());
        if(fault)net.addDynamicEvent(DStabObjectFactory.createBusFaultEvent("Bus1",net,
            SimpleFaultCode.GROUND_3P,new Complex(0,.2),null,.05,.05),"Oel3cFault");
        assertTrue(alg.initialization());var machine=net.getMachine("Bus1-mach1");var exc=(Ac1cExciter)machine.getExciter();
        var oel=(Oel3cModel)net.getBus("Bus1").getDynamicBusDeviceList().stream().filter(Oel3cModel.class::isInstance).findFirst().orElseThrow();
        assertTrue(oel.isInitialized());assertTrue(machine instanceof com.interpss.dstab.mach.RoundRotorMachine);
        double expectedInput=selector==2?machine.calculateIfd(com.interpss.dstab.mach.MachineIfdBase.EXCITER):
            selector==3?exc.getFieldFeedback():machine.getEfd();
        assertEquals(expectedInput,oel.getSelectedInput(),1e-12);
        double initialVoltage=net.getBus("Bus1").getVoltageMag(),initialEfd=machine.getEfd();
        List<String> rows=new ArrayList<>();rows.add("time_s,V_BUS1,V_BUS2,EFD,MACH_SPEED,OEL_INPUT,OEL_INTEGRAL,OEL_REFERENCE,OEL_ERROR,OEL_GAINED_ERROR,OEL_PROPORTIONAL,OEL_OUTPUT,VFE");
        double largestDrift=0,lowestOutput=0;
        while(true){
            var signals=oel.getSignals();double[] values={alg.getSimuTime(),net.getBus("Bus1").getVoltageMag(),net.getBus("Bus2").getVoltageMag(),
                machine.getEfd(),machine.getSpeed()-1,signals.scaledInput(),oel.getPublishedStates().get("Integrator VIOEL"),
                signals.reference(),signals.error(),signals.gainedError(),signals.proportional(),oel.getOutput(),exc.getFieldFeedback()};
            for(double value:values)assertTrue(Double.isFinite(value));
            assertTrue(oel.getOutput()>=-1&&oel.getOutput()<=0);assertTrue(values[6]>=-1&&values[6]<=0);
            lowestOutput=Math.min(lowestOutput,oel.getOutput());largestDrift=Math.max(largestDrift,Math.abs(values[1]-initialVoltage));
            rows.add(java.util.Arrays.stream(values).mapToObj(Double::toString).collect(java.util.stream.Collectors.joining(",")));
            if(alg.getSimuTime()>=.5-h/2)break;assertTrue(alg.solveDEqnStep(true));
        }
        if(!fault){assertTrue(largestDrift<1e-7,"Flat voltage drift="+largestDrift);assertEquals(initialEfd,machine.getEfd(),1e-7);}
        System.out.println("OEL3C selector="+selector+" TF="+tf+" h="+h+" fault="+fault+" maxVoltageDrift="+largestDrift+" minOutput="+lowestOutput);
        Path destination=Path.of("target","oel3c-runtime-coupled");Files.createDirectories(destination);
        Files.write(destination.resolve("input"+selector+"-tf"+tf+"-h"+h+"-"+(fault?"fault":"flat")+".csv"),rows);
    }
}
