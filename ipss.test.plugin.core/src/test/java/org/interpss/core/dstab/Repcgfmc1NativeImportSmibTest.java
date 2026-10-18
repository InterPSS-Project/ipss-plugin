package org.interpss.core.dstab;

import static org.junit.jupiter.api.Assertions.*;
import java.nio.file.*;
import java.util.*;
import org.apache.commons.math3.complex.Complex;
import org.interpss.CorePluginTestSetup;
import org.interpss.core.dstab.reference.Repcgfmc1NativeContractReference;
import org.interpss.core.dstab.reference.Repcgfmc1ImportNativeReference;
import org.interpss.dstab.renewable.Regfmc1Model;
import org.interpss.fadapter.psse.PSSEMultiFileLoader;
import com.interpss.dstab.*;
import com.interpss.dstab.algo.DynamicSimuMethod;
import com.interpss.dstab.cache.StateMonitor;
import com.interpss.core.acsc.fault.SimpleFaultCode;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

public class Repcgfmc1NativeImportSmibTest extends CorePluginTestSetup {
    private record Reference(String mode,double step,int samples,String body,String input) {}
    private static List<Reference> references(){
        List<Reference> refs=new ArrayList<>();
        for(var p:Repcgfmc1NativeContractReference.profiles()){
            String mode=p.name().equals("branch-flat")?"flat":p.name().startsWith("branch-fault")?"fault":p.name().equals("base-flat")?"base":null;
            if(mode!=null)refs.add(new Reference(mode,p.step(),p.samples(),p.body(),p.input()));
        }
        for(var p:Repcgfmc1ImportNativeReference.profiles()){
            String mode=p.name().startsWith("flat")?"flat":p.name().startsWith("base")?"base":p.name().startsWith("trip")?"trip":"fallback";
            refs.add(new Reference(mode,p.step(),p.samples(),p.body(),p.input()));
        }return refs;
    }
    @Test void importedFlatFaultAndTripAtBothSteps(@TempDir Path dir) throws Exception {
        var references=references();assertEquals(9,references.size());
        for(var reference:references){
            double h=reference.step();String mode=reference.mode();
            Path dyr=dir.resolve("plant.dyr");Files.writeString(dyr,reference.input());
            var ctx=new PSSEMultiFileLoader().loadDStab("testData/adpter/psse/v33/renewable/regfma1_native.raw",dyr.toString());
            var net=ctx.getDStabilityNet();net.setAllowGenWithoutMach(true);var algo=ctx.getDynSimuAlgorithm();algo.getAclfAlgorithm().getDataCheckConfig().setAllowGenWithoutMachine(true);
            assertTrue(algo.getAclfAlgorithm().loadflow());algo.setSimuMethod(DynamicSimuMethod.MODIFIED_EULER);algo.setSimuStepSec(h);algo.setTotalSimuTimeSec(.3);algo.setSimuOutputHandler(new StateMonitor());
            if(mode.equals("fault"))net.addDynamicEvent(DStabObjectFactory.createBusFaultEvent("Bus2",net,SimpleFaultCode.GROUND_3P,new Complex(0,1),null,.05,.05),"three-cycle");
            if(mode.equals("trip"))net.addDynamicEvent(DStabObjectFactory.createGeneratorTripEvent("Bus1","1",net,.2),"trip");
            assertTrue(algo.initialization());var owner=assertInstanceOf(Regfmc1Model.class,((DStabGen)net.getBus("Bus1").getContributeGen("1")).getDynamicGenDevice());
            double initial=net.getBus("Bus1").getVoltageMag(),drift=0,minV=1;Map<String,Double> stopped=null;
            StringBuilder trace=new StringBuilder("time_s,V1,V2,V3,P_PU,Q_PU,SPEED_DEV");for(int i=0;i<26;i++)trace.append(",IPSS_STATE").append(i);trace.append('\n');
            List<double[]> actual=new ArrayList<>();
            for(int tick=0;tick<=(int)Math.round(.3/h);tick++){
                double[] row=new double[33];row[0]=algo.getSimuTime();row[1]=net.getBus("Bus1").getVoltageMag();row[2]=net.getBus("Bus2").getVoltageMag();row[3]=net.getBus("Bus3").getVoltageMag();
                row[4]=owner.getActivePower()/2;row[5]=owner.getReactivePower()/2;row[6]=owner.getKernel().speedDeviation();
                System.arraycopy(owner.getKernel().stateSnapshot(),0,row,7,11);System.arraycopy(owner.getPlantController().getKernel().states(),0,row,18,15);
                for(int i=0;i<row.length;i++)trace.append(i==0?"":",").append(row[i]);trace.append('\n');actual.add(row);
                for(double value:owner.getNamedStates().values())assertTrue(Double.isFinite(value));assertTrue(algo.solveDEqnStep(true));
                drift=Math.max(drift,Math.abs(net.getBus("Bus1").getVoltageMag()-initial));minV=Math.min(minV,net.getBus("Bus2").getVoltageMag());
                if(mode.equals("trip")&&tick*h>.21){if(stopped==null)stopped=owner.getNamedStates();else assertEquals(stopped,owner.getNamedStates());assertEquals(0,owner.getActivePower());assertEquals(0,owner.getReactivePower());}
            }
            Path out=Path.of("target","repcgfmc-import-traces",mode+"-"+h+".csv");Files.createDirectories(out.getParent());Files.writeString(out,trace.toString());
            System.out.println("REPCGFMC1U imported "+mode+" h="+h+" voltage drift="+drift+" minV2="+minV);
            if(!mode.equals("fault")&&!mode.equals("trip"))assertTrue(drift<1e-7,"Flat converter voltage drift "+drift);
            if(mode.equals("fault"))assertTrue(minV<.98);
            compare(reference,actual);
        }
    }
    private static double[] bounds(String mode,double h){return switch(mode+(h==.0005?"-coarse":"-fine")){
        case "flat-coarse" -> new double[]{3.219e-07,3.539e-06,7.451e-08,2.645e-07,3.876e-05,1e-12,3.219e-07,3.219e-07,1e-12,1e-12,3.219e-07,1e-12,1e-12,1e-12,1e-12,1e-12,2.666e-07,1e-12,1e-12,3.539e-06,1e-12,1e-12,3.219e-07,3.993e-07,7.138e-05,7.138e-05,1e-12,7.571e-07,5.29e-07,0.0001546,7.752e-05,1e-12};
        case "fault-coarse" -> new double[]{9.444e-06,5.974e-06,5.19e-07,8.029e-05,9.661e-05,1.319e-06,0.0005223,3.659e-06,0.004282,1.752e-05,5.854e-06,8.337e-06,2.912e-07,0.000138,1.319e-06,3.488e-05,5.222e-05,2.343e-06,3.538e-07,0.0012,1e-12,1e-12,1.015e-05,0.0002971,0.02042,7.556e-05,0.002392,8.287e-07,5.787e-07,0.0001571,0.0001718,1e-12};
        case "fault-fine" -> new double[]{5.692e-06,4.091e-06,3.349e-07,7.988e-05,6.206e-05,1.319e-06,0.0002695,4.806e-06,0.00227,4.792e-06,2.15e-06,8.337e-06,2.912e-07,0.0001379,1.319e-06,3.482e-05,5.213e-05,2.641e-06,3.538e-07,0.0006351,1e-12,1e-12,4.219e-06,0.0001624,0.01081,7.138e-05,0.001263,7.93e-07,9.156e-07,0.0001557,0.0001247,1e-12};
        case "base-coarse" -> new double[]{3.219e-07,3.539e-06,7.451e-08,2.645e-07,3.876e-05,1e-12,3.219e-07,3.219e-07,1e-12,1e-12,3.219e-07,1e-12,1e-12,1e-12,1e-12,1e-12,2.666e-07,1e-12,1e-12,3.539e-06,1e-12,1e-12,3.219e-07,1.996e-07,3.569e-05,3.569e-05,1e-12,4.213e-07,2.645e-07,7.588e-05,3.876e-05,1e-12};
        case "flat-fine" -> new double[]{3.219e-07,3.539e-06,7.451e-08,2.645e-07,3.876e-05,1e-12,3.219e-07,3.219e-07,1e-12,1e-12,3.219e-07,1e-12,1e-12,1e-12,1e-12,1e-12,2.666e-07,1e-12,1e-12,3.539e-06,1e-12,1e-12,3.219e-07,3.993e-07,7.138e-05,7.138e-05,1e-12,7.571e-07,5.29e-07,0.0001546,7.752e-05,1e-12};
        case "base-fine" -> new double[]{3.219e-07,3.539e-06,7.451e-08,2.645e-07,3.876e-05,1e-12,3.219e-07,3.219e-07,1e-12,1e-12,3.219e-07,1e-12,1e-12,1e-12,1e-12,1e-12,2.666e-07,1e-12,1e-12,3.539e-06,1e-12,1e-12,3.219e-07,1.996e-07,3.569e-05,3.569e-05,1e-12,4.213e-07,2.645e-07,7.588e-05,3.876e-05,1e-12};
        case "trip-coarse" -> new double[]{7.702e-06,7.696e-06,3.978e-07,2.645e-07,3.876e-05,1e-12,3.219e-07,3.219e-07,1e-12,1e-12,3.219e-07,1e-12,1e-12,1e-12,1e-12,1e-12,2.666e-07,1e-12,1e-12,3.539e-06,1e-12,1e-12,3.219e-07,3.993e-07,7.138e-05,7.138e-05,1e-12,7.571e-07,5.29e-07,0.0001546,7.752e-05,1e-12};
        case "trip-fine" -> new double[]{7.702e-06,7.696e-06,3.978e-07,2.645e-07,3.876e-05,1e-12,3.219e-07,3.219e-07,1e-12,1e-12,3.219e-07,1e-12,1e-12,1e-12,1e-12,1e-12,2.666e-07,1e-12,1e-12,3.539e-06,1e-12,1e-12,3.219e-07,3.993e-07,7.138e-05,7.138e-05,1e-12,7.571e-07,5.29e-07,0.0001546,7.752e-05,1e-12};
        case "fallback-coarse" -> new double[]{3.219e-07,3.539e-06,7.451e-08,2.645e-07,3.876e-05,1e-12,3.219e-07,3.219e-07,1e-12,1e-12,3.219e-07,1e-12,1e-12,1e-12,1e-12,1e-12,2.666e-07,1e-12,1e-12,3.219e-07,1e-12,1e-12,3.219e-07,5.29e-07,7.752e-05,7.752e-05,1e-12,1e-12,5.29e-07,1e-12,7.752e-05,1e-12};
        default -> throw new IllegalArgumentException("Unreviewed imported profile "+mode+" "+h);
    };}
    private static void compare(Reference reference,List<double[]> actual){
        var lines=reference.body().lines().toList();assertEquals(reference.samples(),lines.size()-1);var headers=Arrays.asList(lines.getFirst().split(","));
        String[] channels=new String[32];System.arraycopy(new String[]{"V1","V2","V3","P_PU","Q_PU","SPEED_DEV"},0,channels,0,6);
        for(int i=0;i<26;i++)channels[i+6]=i<11?"INV_STATE"+i:"PLANT_STATE"+(i-11);
        double[] max=new double[32],at=new double[32];int history=0,duplicates=0,matched=0;String previous="";
        for(String line:lines.subList(1,lines.size())){
            String[] raw=line.split(",");double time=Double.parseDouble(raw[0]);
            if(time<0){history++;previous=raw[0];continue;}if(raw[0].equals(previous)){duplicates++;continue;}previous=raw[0];
            double[] row=actual.get((int)Math.round(time/reference.step()));assertEquals(time,row[0],1e-5);matched++;
            for(int i=0;i<32;i++){double error=Math.abs(row[i+1]-Double.parseDouble(raw[headers.indexOf(channels[i])]));if(error>max[i]){max[i]=error;at[i]=time;}}
        }
        assertEquals(2,history);assertEquals(reference.mode().equals("trip")?3:2,duplicates);assertEquals(1+(int)Math.round(.3/reference.step()),matched);
        System.out.println("REPCGFMC1U coupled "+reference.mode()+" h="+reference.step()+" errors="+Arrays.toString(max)+" times="+Arrays.toString(at));
        double[] limits=bounds(reference.mode(),reference.step());
        for(int i=0;i<max.length;i++)assertTrue(max[i]<limits[i],reference.mode()+" h="+reference.step()+" "+channels[i]+"="+max[i]+" at "+at[i]);
    }
    @Test void nativeTripRetainsBothControllersButRemovesPhysicalGeneration(){
        var profiles=Repcgfmc1ImportNativeReference.profiles();assertEquals(5,profiles.size());
        assertEquals(Set.of("flat-fine","base-fine","trip-coarse","trip-fine","fallback-circuit"),profiles.stream().map(p->p.name()).collect(java.util.stream.Collectors.toSet()));
        for(var p:profiles){
            var lines=p.body().lines().toList();var header=Arrays.asList(lines.getFirst().split(","));assertEquals(p.samples(),lines.size()-1);
            assertEquals(p.name().equals("fallback-circuit")?84:85,header.size());
            if(!p.name().startsWith("trip"))continue;
            String[] first=lines.get(1).split(",");int stopped=0;
            for(String line:lines.subList(1,lines.size())){
                String[] row=line.split(",");if(Double.parseDouble(row[0])<=.21)continue;
                for(int i=0;i<26;i++){String key=i<11?"INV_STATE"+i:"PLANT_STATE"+(i-11);assertEquals(first[header.indexOf(key)],row[header.indexOf(key)]);}
                assertEquals(0,Double.parseDouble(row[header.indexOf("P_PU")]));assertEquals(0,Double.parseDouble(row[header.indexOf("Q_PU")]));
                assertTrue(Double.parseDouble(row[header.indexOf("PLANT_VAR1")])>.39,"Retained plant references are not physical injection after trip");stopped++;
            }assertTrue(stopped>100);
        }
    }
}
