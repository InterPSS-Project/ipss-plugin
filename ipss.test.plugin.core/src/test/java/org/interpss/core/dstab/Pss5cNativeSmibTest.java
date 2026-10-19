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
import com.interpss.core.acsc.fault.SimpleFaultCode;
import com.interpss.dstab.DStabObjectFactory;
import com.interpss.dstab.algo.DynamicSimuMethod;
import com.interpss.dstab.cache.StateMonitor;
import com.interpss.dstab.mach.RoundRotorMachine;

/** Native-schema solver checks and unbounded diagnostic export, not trajectory acceptance. */
public class Pss5cNativeSmibTest {
    @ParameterizedTest
    @CsvSource({"false,.0005", "true,.0005", "false,.00025", "true,.00025"})
    void importedNativeModelHasAStationaryFlatAndAThreeCycleFaultResponse(boolean fault,double step)
            throws Exception {
        IpssCorePlugin.init();var folder=Path.of("testData/adpter/psse/v33/SMIB");
        var context=new PSSEMultiFileLoader().loadDStab(folder.resolve("SMIB_v33.raw").toString(),
                folder.resolve("SMIB_v33_genrou_esst1a_pss5c_native.dyr").toString());
        var net=context.getDStabilityNet();var alg=context.getDynSimuAlgorithm();
        assertTrue(alg.getAclfAlgorithm().loadflow());alg.setSimuMethod(DynamicSimuMethod.MODIFIED_EULER);
        alg.setSimuStepSec(step);alg.setTotalSimuTimeSec(.5);alg.setSimuOutputHandler(new StateMonitor());
        if(fault)net.addDynamicEvent(DStabObjectFactory.createBusFaultEvent("Bus1",net,
                SimpleFaultCode.GROUND_3P,new Complex(0,.2),null,.05,.05),"Pss5cFault");
        assertTrue(alg.initialization());var machine=assertInstanceOf(RoundRotorMachine.class,net.getMachine("Bus1-mach1"));
        var model=assertInstanceOf(Pss5cNativeStabilizer.class,machine.getStabilizer());
        double initialV=net.getBus("Bus1").getVoltageMag(),maximum=0;
        StringBuilder csv=new StringBuilder("time_s,low_signal,high_signal,VVL,VL,VI,VH,Vstab,speed_deviation,V_BUS1\n");
        do {
            double value=model.getOutput(machine);maximum=Math.max(maximum,Math.abs(value));
            assertTrue(Math.abs(value)<=.15+1e-14);
            assertTrue(model.getNamedStates().values().stream().allMatch(Double::isFinite));
            csv.append(String.format(Locale.ROOT,"%.12g,%.12g,%.12g,%.12g,%.12g,%.12g,%.12g,%.12g,%.12g,%.12g%n",
                alg.getSimuTime(),model.getLowIntermediateInput(),model.getHighInput(),model.getBandOutput(0),
                model.getBandOutput(1),model.getBandOutput(2),model.getBandOutput(3),value,machine.getSpeed()-1,
                net.getBus("Bus1").getVoltageMag()));
            if(alg.getSimuTime()>=.5-step/2)break;
            assertTrue(alg.solveDEqnStep(true));
        } while(true);
        if(fault)assertTrue(maximum>1e-8,"Disturbance activates the controller; not an oracle tolerance");
        else {assertTrue(maximum<1e-9);assertEquals(initialV,net.getBus("Bus1").getVoltageMag(),1e-9);}
        Path output=Path.of("target/pss5c-native-coupled");Files.createDirectories(output);
        Files.writeString(output.resolve((fault?"fault":"flat")+"-"+step+".csv"),csv.toString());
    }
}
