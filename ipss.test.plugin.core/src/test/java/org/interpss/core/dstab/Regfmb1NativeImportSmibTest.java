package org.interpss.core.dstab;

import static org.junit.jupiter.api.Assertions.*;
import java.util.*;
import org.apache.commons.math3.complex.Complex;
import org.interpss.CorePluginTestSetup;
import org.interpss.core.dstab.reference.EventLeftLimits;
import com.interpss.dstab.renewable.Regfmb1Model;
import org.interpss.fadapter.psse.PSSEMultiFileLoader;
import com.interpss.dstab.*;
import com.interpss.dstab.algo.DynamicSimuMethod;
import com.interpss.dstab.cache.StateMonitor;
import com.interpss.core.acsc.fault.SimpleFaultCode;
import org.junit.jupiter.api.Test;

public class Regfmb1NativeImportSmibTest extends CorePluginTestSetup {
    @Test void nativeFlatFaultAndTripAtBothSteps() throws Exception {
        String base="testData/adpter/psse/v33/renewable/";
        for(double h:new double[]{.0005,.00025})for(String profile:new String[]{"flat","fault","trip"}) {
            var ctx=new PSSEMultiFileLoader().loadDStab(base+"regfma1_native.raw",base+"regfmb1u_native.dyr");
            var net=ctx.getDStabilityNet();net.setAllowGenWithoutMach(true);var algo=ctx.getDynSimuAlgorithm();
            algo.getAclfAlgorithm().getDataCheckConfig().setAllowGenWithoutMachine(true);assertTrue(algo.getAclfAlgorithm().loadflow());
            var model=assertInstanceOf(Regfmb1Model.class,((DStabGen)net.getBus("Bus1").getContributeGen("1")).getDynamicGenDevice());
            algo.setSimuMethod(DynamicSimuMethod.MODIFIED_EULER);algo.setSimuStepSec(h);algo.setTotalSimuTimeSec(.3);algo.setSimuOutputHandler(new StateMonitor());
            if(profile.equals("fault"))net.addDynamicEvent(DStabObjectFactory.createBusFaultEvent("Bus2",net,SimpleFaultCode.GROUND_3P,new Complex(0,1),null,.05,.05),"three-cycle");
            if(profile.equals("trip"))net.addDynamicEvent(DStabObjectFactory.createGeneratorTripEvent("Bus1","1",net,.2),"trip");
            assertTrue(algo.initialization());double v0=net.getBus("Bus1").getVoltageMag(),drift=0,minV=1;Map<String,Double> stopped=null;
            List<String> trace=new ArrayList<>();trace.add("time_s,V1,V2,V3,P_PU,Q_PU,SPEED_DEV");
            java.util.function.Supplier<String> sample=()->algo.getSimuTime()+","+net.getBus("Bus1").getVoltageMag()+","+net.getBus("Bus2").getVoltageMag()+","+net.getBus("Bus3").getVoltageMag()+","+model.getActivePower()/2+","+model.getReactivePower()/2+","+model.getKernel().speedDeviation();
            // The native trace records the pre-event side first at each event time.
            var left=EventLeftLimits.capture(algo,h,sample);
            for(int tick=0;tick<=(int)Math.round(.3/h);tick++) {
                trace.add(left.at(algo.getSimuTime(),sample));
                assertTrue(algo.solveDEqnStep(true));drift=Math.max(drift,Math.abs(net.getBus("Bus1").getVoltageMag()-v0));minV=Math.min(minV,net.getBus("Bus2").getVoltageMag());
                for(double state:model.getNamedStates().values())assertTrue(Double.isFinite(state));
                if(profile.equals("trip")&&tick*h>.21){if(stopped==null)stopped=model.getNamedStates();else assertEquals(stopped,model.getNamedStates());assertEquals(0,model.getActivePower(),0);assertEquals(0,model.getReactivePower(),0);}
            }
            var path=java.nio.file.Path.of("target","regfmb-import-traces",profile+"-"+h+".csv");java.nio.file.Files.createDirectories(path.getParent());java.nio.file.Files.write(path,trace);
            System.out.println("REGFMB1U imported "+profile+" h="+h+" drift="+drift+" minV2="+minV);
            if(profile.equals("flat"))assertTrue(drift<1e-7,"flat drift "+drift);
            if(profile.equals("fault"))assertTrue(minV<.98);
            if(profile.equals("fault"))compareNativeBoundary(trace,h);
            if(profile.equals("trip"))assertNotNull(stopped);
        }
    }
    private static void compareNativeBoundary(List<String> trace,double h) {
        var reference=org.interpss.core.dstab.reference.Regfmb1NativeContractReference.profiles().stream()
                .filter(p->p.name().startsWith("fault")&&p.step()==h).findFirst().orElseThrow();
        var lines=reference.body().lines().toList();var headers=Arrays.asList(lines.getFirst().split(","));
        String[] names={"V1","V2","V3","P_PU","Q_PU","SPEED_DEV"};
        double[] max=new double[6];int matched=0,history=0,posts=0;String previous="";
        for(String line:lines.subList(1,lines.size())) {
            String[] raw=line.split(",");double time=Double.parseDouble(raw[0]);
            if(time<0){history++;previous=raw[0];continue;}
            if(raw[0].equals(previous)){posts++;continue;}previous=raw[0];
            String[] actual=trace.get(1+(int)Math.round(time/h)).split(",");matched++;
            for(int i=0;i<6;i++)max[i]=Math.max(max[i],Math.abs(Double.parseDouble(actual[i+1])-Double.parseDouble(raw[headers.indexOf(names[i])])));
        }
        assertEquals(2,history);assertEquals(2,posts);assertEquals(1+(int)Math.round(.3/h),matched);
        double[] bounds=h==.0005?new double[]{2e-6,4.4e-6,1.7e-7,1.3e-6,4.6e-5,6.1e-8}
                :new double[]{1.5e-6,3.9e-6,1.7e-7,7e-7,4.6e-5,1.6e-8};
        System.out.println("REGFMB1U coupled native h="+h+" boundary errors="+Arrays.toString(max));
        for(int i=0;i<6;i++)assertTrue(max[i]<bounds[i],names[i]+" native boundary error "+max[i]);
    }
}
