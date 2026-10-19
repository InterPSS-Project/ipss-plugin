package org.interpss.core.dstab;

import static org.junit.jupiter.api.Assertions.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import org.apache.commons.math3.complex.Complex;
import org.interpss.IpssCorePlugin;
import org.interpss.dstab.control.pss.psse.pss5c.Pss5cNativeStabilizer;
import org.interpss.fadapter.psse.PSSEMultiFileLoader;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.api.io.TempDir;
import com.interpss.core.acsc.fault.SimpleFaultCode;
import com.interpss.dstab.DStabObjectFactory;
import com.interpss.dstab.algo.DynamicSimuMethod;
import com.interpss.dstab.cache.StateMonitor;
import com.interpss.dstab.mach.RoundRotorMachine;

/** Native-schema solver checks and unbounded diagnostic export, not trajectory acceptance. */
public class Pss5cNativeSmibTest {
    @TempDir Path temporary;
    @ParameterizedTest
    @CsvSource({"false,.0005,false,-1", "true,.0005,false,-1", "false,.00025,false,-1", "true,.00025,false,-1",
                "false,.0005,true,-1", "true,.0005,true,-1", "false,.00025,true,-1", "true,.00025,true,-1",
                "false,.0005,false,0", "true,.0005,false,0", "false,.00025,false,0", "true,.00025,false,0",
                "false,.0005,true,0", "true,.0005,true,0", "false,.00025,true,0", "true,.00025,true,0",
                "false,.0005,false,1", "true,.0005,false,1", "false,.00025,false,1", "true,.00025,false,1",
                "false,.0005,true,1", "true,.0005,true,1", "false,.00025,true,1", "true,.00025,true,1"})
    void importedNativeModelHasAStationaryFlatAndAThreeCycleFaultResponse(boolean fault,double step,boolean firstOrder,int highMode)
            throws Exception {
        IpssCorePlugin.init();var folder=Path.of("testData/adpter/psse/v33/SMIB");
        Path dynamic=folder.resolve("SMIB_v33_genrou_esst1a_pss5c_native.dyr");
        if(firstOrder || highMode>=0) {
            String original=Files.readString(dynamic);assertEquals(1,original.split("\\.00012739",-1).length-1);
            if(firstOrder)original=original.replace(".00012739","0.0");
            if(highMode>=0) {
                assertEquals(1,original.split("66 1 1 81 80 66",-1).length-1);
                original=original.replace("66 1 1 81 80 66",highMode==0?"66 0 1 81 80 66":"66 1 0 81 80 66");
            }
            dynamic=temporary.resolve("reduced-order.dyr");Files.writeString(dynamic,original);
        }
        var context=new PSSEMultiFileLoader().loadDStab(folder.resolve("SMIB_v33.raw").toString(),dynamic.toString());
        var net=context.getDStabilityNet();var alg=context.getDynSimuAlgorithm();
        assertTrue(alg.getAclfAlgorithm().loadflow());alg.setSimuMethod(DynamicSimuMethod.MODIFIED_EULER);
        alg.setSimuStepSec(step);alg.setTotalSimuTimeSec(.5);alg.setSimuOutputHandler(new StateMonitor());
        if(fault)net.addDynamicEvent(DStabObjectFactory.createBusFaultEvent("Bus1",net,
                SimpleFaultCode.GROUND_3P,new Complex(0,.2),null,.05,.05),"Pss5cFault");
        assertTrue(alg.initialization());var machine=assertInstanceOf(RoundRotorMachine.class,net.getMachine("Bus1-mach1"));
        var model=assertInstanceOf(Pss5cNativeStabilizer.class,machine.getStabilizer());
        assertEquals(firstOrder,model.getData().hasFirstOrderLowTransducer());
        assertEquals(highMode>=0,model.getData().hasSecondOrderHighTransducer());
        double initialV=net.getBus("Bus1").getVoltageMag(),maximum=0;
        StringBuilder csv=new StringBuilder("time_s,low_signal,high_signal,VVL,VL,VI,VH,Vstab,speed_deviation,V_BUS1\n");
        do {
            double value=model.getOutput(machine);maximum=Math.max(maximum,Math.abs(value));
            assertTrue(Math.abs(value)<=.15+1e-14);
            assertTrue(model.getNamedStates().values().stream().allMatch(Double::isFinite));
            if(firstOrder)assertEquals(0,model.getNamedStates().get("lowRate"),0);
            if(highMode>=0)assertEquals(0,model.getNamedStates().get("highAcceleration"),0);
            csv.append(String.format(Locale.ROOT,"%.12g,%.12g,%.12g,%.12g,%.12g,%.12g,%.12g,%.12g,%.12g,%.12g%n",
                alg.getSimuTime(),model.getLowIntermediateInput(),model.getHighInput(),model.getBandOutput(0),
                model.getBandOutput(1),model.getBandOutput(2),model.getBandOutput(3),value,machine.getSpeed()-1,
                net.getBus("Bus1").getVoltageMag()));
            if(alg.getSimuTime()>=.5-step/2)break;
            assertTrue(alg.solveDEqnStep(true));
        } while(true);
        if(fault)assertTrue(maximum>1e-8,"Disturbance activates the controller; not an oracle tolerance");
        else {assertTrue(maximum<1e-9);assertEquals(initialV,net.getBus("Bus1").getVoltageMag(),1e-9);}
        Path output=Path.of(highMode>=0?"target/pss5c-native-high-"+highMode+"-low-"+(firstOrder?"first":"full")+"-coupled":
                (firstOrder?"target/pss5c-native-first-order-coupled":"target/pss5c-native-coupled"));Files.createDirectories(output);
        Files.writeString(output.resolve((fault?"fault":"flat")+"-"+step+".csv"),csv.toString());
    }
}
