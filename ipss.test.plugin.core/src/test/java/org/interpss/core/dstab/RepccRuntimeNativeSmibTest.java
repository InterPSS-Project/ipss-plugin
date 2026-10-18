package org.interpss.core.dstab;

import static org.junit.jupiter.api.Assertions.*;
import java.nio.file.*;
import java.util.*;
import org.apache.commons.math3.complex.Complex;
import org.interpss.CorePluginTestSetup;
import org.interpss.core.dstab.reference.RepccRuntimeNativeReference;
import org.interpss.dstab.renewable.*;
import org.interpss.fadapter.builder.DStabNetworkBuilder;
import org.interpss.fadapter.psse.*;
import com.interpss.dstab.*;
import com.interpss.dstab.algo.*;
import com.interpss.dstab.cache.StateMonitor;
import com.interpss.dstab.common.DStabSimuException;
import com.interpss.core.acsc.fault.SimpleFaultCode;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Exact imported plant benchmark. Event sides and initialized history are explicit. */
public class RepccRuntimeNativeSmibTest extends CorePluginTestSetup {
    private static double[] snapshot(DynamicSimuAlgorithm algorithm){
        var net=algorithm.getNetwork();var converter=(Regca1Model)((DStabGen)net.getBus("Bus1").getContributeGen("1")).getDynamicGenDevice();
        var plant=converter.getActiveElectricalController().getRepccController();double[] result=new double[18];
        for(int i=0;i<3;i++)result[i]=net.getBus("Bus"+(i+1)).getVoltageMag();
        result[3]=result[0]*converter.getIp()/2;result[4]=result[0]*converter.getIq()/2;
        System.arraycopy(plant.getKernel().states(),0,result,5,13);return result;
    }
    private static final class ObservedSolver extends PsseDStabSolver {
        final DynamicSimuAlgorithm algorithm;final double step;final Map<Integer,double[]> start=new HashMap<>();
        ObservedSolver(DynamicSimuAlgorithm algorithm,double step){super(algorithm);this.algorithm=algorithm;this.step=step;}
        @Override public void diffEqnIntegrationStep(double time,double dt,DynamicSimuMethod method,int flag)throws DStabSimuException{
            if(flag==0&&dt>0)start.put((int)Math.round(time/step),snapshot(algorithm));
            super.diffEqnIntegrationStep(time,dt,method,flag);
        }
    }
    private static double[] bounds(String profile){
        if(profile.startsWith("flat"))return new double[]{2.84e-7,9.94e-8,6.56e-8,2.33e-7,1.302e-6,1.102e-7,1e-12,2.604e-6,2.604e-6,4.984e-7,3.279e-8,4.656e-7,6.56e-8,1e-12,4.67e-12,4.984e-7,1e-12,2.604e-6};
        return switch(profile){
            case "fault-coarse" -> new double[]{6.246e-6,3.206e-6,3.538e-7,1.073e-4,1.921e-6,5.3451e-4,1e-12,4.596e-6,1.637e-5,5.679e-4,1.278e-6,1.003e-4,5.939e-5,1e-12,2.701e-6,4.984e-7,1e-12,6.022e-5};
            case "fault-fine" -> new double[]{7.324e-6,3.606e-6,3.732e-7,5.44e-5,2.027e-6,2.743e-4,1e-12,2.604e-6,4.118e-6,2.891e-4,6.248e-7,5.024e-5,3.058e-5,1e-12,2.742e-6,4.984e-7,1e-12,3.713e-5};
            default -> throw new IllegalArgumentException("Unreviewed REPCC profile "+profile);
        };
    }
    @Test void strictImportedFlatAndThreeCycleFaultCompareEveryRawRowAtBothSteps(@TempDir Path dir)throws Exception{
        int profiles=0;
        for(var profile:RepccRuntimeNativeReference.profiles()){
            Path file=dir.resolve(profile.name()+".dyr");Files.writeString(file,profile.input());
            var ctx=new PSSEMultiFileLoader().loadDStab("testData/adpter/psse/v33/renewable/regfma1_native.raw");
            var net=ctx.getDStabilityNet();var algo=ctx.getDynSimuAlgorithm();var parser=new PSSEDStabDirectParser(new DStabNetworkBuilder(net)).setStrictImport(true);
            parser.parseDynFile(file.toString());assertTrue(parser.getLastImportReport().isStrictlyComplete());net.setAllowGenWithoutMach(true);
            algo.getAclfAlgorithm().getDataCheckConfig().setAllowGenWithoutMachine(true);algo.getAclfAlgorithm().setTolerance(1e-10);assertTrue(algo.getAclfAlgorithm().loadflow());
            algo.setSimuMethod(DynamicSimuMethod.MODIFIED_EULER);algo.setSimuStepSec(profile.step());algo.setTotalSimuTimeSec(.3);algo.setSimuOutputHandler(new StateMonitor());
            var solver=new ObservedSolver(algo,profile.step());algo.setSolver(solver);
            boolean fault=profile.name().startsWith("fault");
            if(fault)net.addDynamicEvent(DStabObjectFactory.createBusFaultEvent("Bus2",net,SimpleFaultCode.GROUND_3P,new Complex(0,1),null,.05,.05),"three-cycle");
            assertTrue(algo.initialization());int ticks=(int)Math.round(.3/profile.step());var actual=new ArrayList<double[]>();double drift=0,initial=net.getBus("Bus1").getVoltageMag(),minimum=1;
            StringBuilder trace=new StringBuilder("time_s,V1,V2,V3,P_SYSTEM,Q_SYSTEM");for(int i=0;i<13;i++)trace.append(",STATE").append(i);trace.append('\n');
            for(int tick=0;tick<=ticks;tick++){
                double[] row=snapshot(algo);actual.add(row);trace.append(algo.getSimuTime());for(double value:row){assertTrue(Double.isFinite(value));trace.append(',').append(value);}trace.append('\n');
                drift=Math.max(drift,Math.abs(row[0]-initial));minimum=Math.min(minimum,row[1]);if(tick<ticks)assertTrue(algo.solveDEqnStep(true));
            }
            Path out=Path.of("target/repcc-runtime-traces",profile.name()+".csv");Files.createDirectories(out.getParent());Files.writeString(out,trace.toString());
            var lines=profile.body().lines().toList();assertEquals(profile.samples(),lines.size()-1);var headers=Arrays.asList(lines.getFirst().split(","));
            String[] channels=new String[18];System.arraycopy(new String[]{"V1","V2","V3","P_PU","Q_PU"},0,channels,0,5);for(int i=0;i<13;i++)channels[5+i]="PLANT_STATE"+i;
            double[] maximum=new double[18],at=new double[18];int history=0,duplicates=0;String previous="";
            StringBuilder paired=new StringBuilder("native_time_s,ipss_time_s,side");
            for(String channel:channels)paired.append(",IPSS_").append(channel).append(",NATIVE_").append(channel);paired.append('\n');
            for(String line:lines.subList(1,lines.size())){
                String[] fields=line.split(",");double time=Double.parseDouble(fields[0]);int tick=Math.max(0,(int)Math.round(time/profile.step()));
                boolean duplicate=fields[0].equals(previous);previous=fields[0];if(time<0)history++;if(duplicate)duplicates++;
                double[] row=duplicate?solver.start.get(tick):actual.get(tick);assertNotNull(row,"Missing event-post snapshot at "+time);
                if(time>=0)assertEquals(time,tick*profile.step(),3e-6);
                paired.append(time).append(',').append(tick*profile.step()).append(',').append(time<0?-1:duplicate?1:0);
                for(int i=0;i<18;i++){double reference=Double.parseDouble(fields[headers.indexOf(channels[i])]);
                    double error=Math.abs(row[i]-reference);if(error>maximum[i]){maximum[i]=error;at[i]=time;}
                    paired.append(',').append(row[i]).append(',').append(reference);}
                paired.append('\n');
            }
            Path comparison=Path.of("target/repcc-runtime-comparisons",profile.name()+".csv");Files.createDirectories(comparison.getParent());Files.writeString(comparison,paired.toString());
            assertEquals(2,history);assertEquals(2,duplicates);
            System.out.println("REPCC imported coupled "+profile.name()+" errors="+Arrays.toString(maximum)+" times="+Arrays.toString(at)+" drift="+drift+" minimumV2="+minimum);
            if(!fault)assertTrue(drift<1e-7);else assertTrue(minimum<.98);
            double[] limits=bounds(profile.name());for(int i=0;i<maximum.length;i++)assertTrue(maximum[i]<limits[i],profile.name()+" "+channels[i]+" error="+maximum[i]+" at="+at[i]);
            profiles++;
        }
        assertEquals(4,profiles);
    }
}
