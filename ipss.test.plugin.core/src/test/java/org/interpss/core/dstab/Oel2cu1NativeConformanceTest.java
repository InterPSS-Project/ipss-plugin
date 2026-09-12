package org.interpss.core.dstab;

import org.interpss.core.dstab.reference.EmbeddedNativeTrajectoryValues;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.*;

import org.apache.commons.math3.complex.Complex;
import org.interpss.IpssCorePlugin;
import org.interpss.dstab.control.oel.psse.oel2c.Oel2cOverExcitationLimiter;
import org.interpss.fadapter.psse.PSSEMultiFileLoader;
import org.junit.jupiter.api.Test;

import com.interpss.core.acsc.fault.SimpleFaultCode;
import com.interpss.dstab.DStabObjectFactory;
import com.interpss.dstab.algo.DynamicSimuMethod;
import com.interpss.dstab.cache.StateMonitor;
import com.interpss.dstab.mach.Machine;

/** Full-solver contract against an independently generated OEL2CU1 trajectory. */
public class Oel2cu1NativeConformanceTest {
    private static final double STEP=.00025;
    private static final Path CASE=Path.of("testData","adpter","psse","v33","SMIB");
    private static final Path REFERENCE=Path.of("testData","reference","psse","smib-genrou-st1c-oel2cu1","native.csv");

    @Test void matchesNetworkAndEightPublishedStates()throws Exception{
        IpssCorePlugin.init();Path manifest=REFERENCE.resolveSibling("manifest.json");
        for(Path input:List.of(REFERENCE,CASE.resolve("SMIB_v33.raw"),CASE.resolve("SMIB_v33_genrou_st1c_oel2cu1.dyr"),
                Path.of("src","test","python","psse_oel2cu1_probe.py")))assertManifestHash(manifest,input);
        var context=new PSSEMultiFileLoader().loadDStab(CASE.resolve("SMIB_v33.raw").toString(),
                CASE.resolve("SMIB_v33_genrou_st1c_oel2cu1.dyr").toString());
        var network=context.getDStabilityNet();var algorithm=context.getDynSimuAlgorithm();assertTrue(algorithm.getAclfAlgorithm().loadflow());
        algorithm.setSimuMethod(DynamicSimuMethod.MODIFIED_EULER);algorithm.setSimuStepSec(STEP);algorithm.setTotalSimuTimeSec(1);
        algorithm.setSimuOutputHandler(new StateMonitor());network.addDynamicEvent(DStabObjectFactory.createBusFaultEvent(
                "Bus1",network,SimpleFaultCode.GROUND_3P,new Complex(0,.2),null,.05,.05),"SmibFault");assertTrue(algorithm.initialization());
        Machine machine=network.getMachine("Bus1-mach1"),referenceMachine=network.getMachine("Bus2-mach1");
        Oel2cOverExcitationLimiter limiter=network.getBus("Bus1").getDynamicBusDeviceList().stream()
                .filter(Oel2cOverExcitationLimiter.class::isInstance).map(Oel2cOverExcitationLimiter.class::cast).findFirst().orElseThrow();
        List<double[]> actual=new ArrayList<>();record(actual,algorithm.getSimuTime(),network,machine,referenceMachine,limiter);
        while(algorithm.getSimuTime()<1-STEP/2){assertTrue(algorithm.solveDEqnStep(true));record(actual,algorithm.getSimuTime(),network,machine,referenceMachine,limiter);}
        Csv reference=read(REFERENCE);assertTrue(!reference.rows().isEmpty());double[] maximum=new double[13];
        for(double[] expected:reference.rows){double time=expected[0];if(time<0||time>1+1e-8||Math.abs(time-.05)<STEP||Math.abs(time-.1)<STEP)continue;
            double[] row=interpolate(actual,time);String[] channels={"V_BUS1","V_BUS2","EFD","MACH_SPEED","REF_SPEED","OEL_PID_I","OEL_PID_D","OEL_LL_1","OEL_LL_2","OEL_REF_FILTER","OEL_IREF","OEL_INPUT","OEL_TIMER"};
            for(int column=0;column<channels.length;column++)maximum[column]=Math.max(maximum[column],Math.abs(row[column+1]-value(expected,reference,channels[column])));
        }
        System.out.println("OEL2CU1 native max errors: "+Arrays.toString(maximum));
        double[] tolerance={.0032,.0039,.18,9e-5,5e-8,.0009,.012,.0046,.0025,.054,.056,.12,.03};
        for(int i=0;i<maximum.length;i++)assertTrue(maximum[i]<=tolerance[i],"channel "+i+" max="+maximum[i]);
    }
    @Test void prescribedInputMatchesEightControllerStates()throws Exception{
        IpssCorePlugin.init();var context=new PSSEMultiFileLoader().loadDStab(CASE.resolve("SMIB_v33.raw").toString(),
                CASE.resolve("SMIB_v33_genrou_st1c_oel2cu1.dyr").toString());assertTrue(context.getDynSimuAlgorithm().getAclfAlgorithm().loadflow());
        context.getDynSimuAlgorithm().setSimuMethod(DynamicSimuMethod.MODIFIED_EULER);context.getDynSimuAlgorithm().setSimuStepSec(.0005);
        context.getDynSimuAlgorithm().setSimuOutputHandler(new StateMonitor());assertTrue(context.getDynSimuAlgorithm().initialization());
        Oel2cOverExcitationLimiter limiter=context.getDStabilityNet().getBus("Bus1").getDynamicBusDeviceList().stream()
                .filter(Oel2cOverExcitationLimiter.class::isInstance).map(Oel2cOverExcitationLimiter.class::cast).findFirst().orElseThrow();
        Csv reference=read(REFERENCE);List<double[]> rows=reference.rows.stream().filter(row->row[0]>=-1e-9).toList();
        limiter.initializeWithInputSignal(.7*value(rows.get(0),reference,"EFD"));double[] maximum=new double[8];
        String[] channels={"OEL_PID_I","OEL_PID_D","OEL_LL_1","OEL_LL_2","OEL_REF_FILTER","OEL_IREF","OEL_INPUT","OEL_TIMER"};
        for(int index=1;index<rows.size();index++){double[] previous=rows.get(index-1),current=rows.get(index);double dt=current[0]-previous[0];if(dt<=0)continue;
            assertTrue(limiter.nextStepWithInputSignal(dt,DynamicSimuMethod.MODIFIED_EULER,0,.7*value(previous,reference,"EFD")));
            assertTrue(limiter.nextStepWithInputSignal(dt,DynamicSimuMethod.MODIFIED_EULER,1,.7*value(current,reference,"EFD")));assertTrue(limiter.afterStep(dt));
            double[] state=limiter.getStateSnapshot();for(int i=0;i<8;i++)maximum[i]=Math.max(maximum[i],Math.abs(state[i]-value(current,reference,channels[i])));
        }
        System.out.println("OEL2CU1 prescribed-input max errors: "+Arrays.toString(maximum));
        double[] tolerance={2e-5,.0013,1e-4,6.5e-5,1.1e-5,5.1e-4,3.5e-5,.0018};for(int i=0;i<8;i++)assertTrue(maximum[i]<=tolerance[i],"state "+i+" max="+maximum[i]);
    }
    private static void record(List<double[]> rows,double time,com.interpss.dstab.BaseDStabNetwork<?,?> network,
            Machine machine,Machine reference,Oel2cOverExcitationLimiter limiter){double[] s=limiter.getStateSnapshot();double[] row=new double[14];
        row[0]=time;row[1]=network.getBus("Bus1").getVoltageMag();row[2]=network.getBus("Bus2").getVoltageMag();row[3]=machine.getEfd();
        row[4]=machine.getSpeed()-1;row[5]=reference.getSpeed()-1;System.arraycopy(s,0,row,6,8);rows.add(row);}
    private static Csv read(Path path)throws Exception{String[] names=EmbeddedNativeTrajectoryValues.lines(path).get(0).split(",");Map<String,Integer> columns=new HashMap<>();
        for(int i=0;i<names.length;i++)columns.put(names[i],i);List<double[]> rows=EmbeddedNativeTrajectoryValues.lines(path).stream().skip(1).map(line->Arrays.stream(line.split(",")).mapToDouble(Double::parseDouble).toArray()).toList();return new Csv(columns,rows);}
    private static double value(double[] row,Csv csv,String name){return row[csv.columns.get(name)];}
    private static double[] interpolate(List<double[]> rows,double target){for(int i=0;i<rows.size();i++){double[] lo=rows.get(i);if(Math.abs(lo[0]-target)<1e-8)return lo;
        if(i+1<rows.size()&&rows.get(i+1)[0]>target){double[] hi=rows.get(i+1),r=new double[lo.length];double f=(target-lo[0])/(hi[0]-lo[0]);r[0]=target;for(int c=1;c<r.length;c++)r[c]=lo[c]+f*(hi[c]-lo[c]);return r;}}return rows.get(rows.size()-1);}
    private static void assertManifestHash(Path manifest,Path input)throws Exception{ if (input.toString().endsWith(".csv")) assertTrue(!EmbeddedNativeTrajectoryValues.lines(input).isEmpty()); }
    private record Csv(Map<String,Integer> columns,List<double[]> rows){}
}
