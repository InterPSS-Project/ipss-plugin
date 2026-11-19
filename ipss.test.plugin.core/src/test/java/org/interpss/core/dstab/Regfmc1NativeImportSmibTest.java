package org.interpss.core.dstab;

import org.interpss.core.dstab.reference.EventLeftLimits;
import static org.junit.jupiter.api.Assertions.*;
import java.util.*;
import org.apache.commons.math3.complex.Complex;
import org.interpss.CorePluginTestSetup;
import com.interpss.dstab.renewable.Regfmc1Model;
import org.interpss.fadapter.psse.PSSEMultiFileLoader;
import com.interpss.dstab.*;
import com.interpss.dstab.algo.DynamicSimuMethod;
import com.interpss.dstab.cache.StateMonitor;
import com.interpss.core.acsc.fault.SimpleFaultCode;
import org.junit.jupiter.api.Test;

public class Regfmc1NativeImportSmibTest extends CorePluginTestSetup {
    @Test void nativeFlatFaultAndTripAtBothSteps() throws Exception {
        String base="testData/adpter/psse/v33/renewable/";
        for(double h:new double[]{.0005,.00025})for(String profile:new String[]{"flat","fault","trip"}) {
            var ctx=new PSSEMultiFileLoader().loadDStab(base+"regfma1_native.raw",base+"regfmc1u_native.dyr");
            var net=ctx.getDStabilityNet();net.setAllowGenWithoutMach(true);var algo=ctx.getDynSimuAlgorithm();
            algo.getAclfAlgorithm().getDataCheckConfig().setAllowGenWithoutMachine(true);assertTrue(algo.getAclfAlgorithm().loadflow());
            var model=assertInstanceOf(Regfmc1Model.class,((DStabGen)net.getBus("Bus1").getContributeGen("1")).getDynamicGenDevice());
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
            var path=java.nio.file.Path.of("target","regfmc-import-traces",profile+"-"+h+".csv");java.nio.file.Files.createDirectories(path.getParent());java.nio.file.Files.write(path,trace);
            System.out.println("REGFMC1U imported "+profile+" h="+h+" drift="+drift+" minV2="+minV);
            if(profile.equals("flat"))assertTrue(drift<1e-7,"flat drift "+drift);
            if(profile.equals("fault"))assertTrue(minV<.98);
            compareNativeBoundary(trace,h,profile);
            if(profile.equals("trip"))assertNotNull(stopped);
        }
    }
    private static void compareNativeBoundary(List<String> trace,double h,String profile) {
        var profiles=profile.equals("trip")?org.interpss.core.dstab.reference.Regfmc1LifecycleNativeReference.profiles()
                :org.interpss.core.dstab.reference.Regfmc1NativeContractReference.profiles();
        var reference=profiles.stream()
                .filter(p->p.name().startsWith(profile)&&p.step()==h).findFirst().orElseThrow();
        var lines=reference.body().lines().toList();var headers=Arrays.asList(lines.getFirst().split(","));
        String[] names={"V1","V2","V3","P_PU","Q_PU","SPEED_DEV"};
        double[] max=new double[6],at=new double[6];int matched=0,history=0,posts=0;String previous="";
        for(String line:lines.subList(1,lines.size())) {
            String[] raw=line.split(",");double time=Double.parseDouble(raw[0]);
            if(time<0){history++;previous=raw[0];continue;}
            if(raw[0].equals(previous)){posts++;continue;}previous=raw[0];
            String[] actual=trace.get(1+(int)Math.round(time/h)).split(",");matched++;
            assertEquals(time,Double.parseDouble(actual[0]),1e-5,"Declared native float clock to nearest fixed tick, no fitted shift");
            for(int i=0;i<6;i++){double error=Math.abs(Double.parseDouble(actual[i+1])-Double.parseDouble(raw[headers.indexOf(names[i])]));if(error>max[i]){max[i]=error;at[i]=time;}}
        }
        assertEquals(2,history);assertEquals(profile.equals("trip")?3:2,posts);assertEquals(1+(int)Math.round(.3/h),matched);
        double[] bounds=profile.equals("flat")?new double[]{3.4e-7,3.7e-6,8e-8,2.8e-7,4.1e-5,1e-12}
                :profile.equals("trip")?new double[]{7.8e-6,7.8e-6,4.1e-7,2.8e-7,4.1e-5,1e-12}
                :h==.0005?new double[]{5.56e-6,6.39e-6,3.07e-7,7.51e-5,6.98e-5,1.12e-6}
                :new double[]{3.46e-6,4.94e-6,1.70e-7,6.61e-5,5.54e-5,1.12e-6};
        System.out.println("REGFMC1U coupled native "+profile+" h="+h+" boundary errors="+Arrays.toString(max)+" times="+Arrays.toString(at));
        for(int i=0;i<6;i++)assertTrue(max[i]<bounds[i],names[i]+" native boundary error "+max[i]);
    }
    @Test void nativeTripRetainsInactiveHistoryButIsNotAnActiveControllerReplay() {
        for(var profile:org.interpss.core.dstab.reference.Regfmc1LifecycleNativeReference.profiles()) {
            var lines=profile.body().lines().toList();var header=Arrays.asList(lines.getFirst().split(","));assertEquals(profile.samples(),lines.size()-1);assertEquals(49,header.size());
            String[] first=lines.get(1).split(",");int inactive=0,history=0,duplicates=0;String previous="";
            for(String line:lines.subList(1,lines.size())) {
                String[] r=line.split(",");double t=Double.parseDouble(r[0]);if(t<0)history++;if(r[0].equals(previous))duplicates++;previous=r[0];
                if(t>.21){for(int i=0;i<11;i++)assertEquals(first[header.indexOf("STATE"+i)],r[header.indexOf("STATE"+i)]);
                    assertEquals(0,Double.parseDouble(r[header.indexOf("P_PU")]));assertEquals(0,Double.parseDouble(r[header.indexOf("Q_PU")]));
                    assertTrue(Math.abs(Double.parseDouble(r[header.indexOf("V1")])-Double.parseDouble(first[header.indexOf("V1")]))>.02);inactive++;}
            }
            assertEquals(2,history);assertEquals(3,duplicates);assertTrue(inactive>100);
        }
    }
}
