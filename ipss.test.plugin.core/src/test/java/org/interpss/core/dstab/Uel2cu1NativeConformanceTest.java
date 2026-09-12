package org.interpss.core.dstab;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.*;

import org.apache.commons.math3.complex.Complex;
import org.interpss.IpssCorePlugin;
import org.interpss.dstab.control.uel.psse.uel2c.Uel2cUnderExcitationLimiter;
import org.interpss.fadapter.psse.PSSEMultiFileLoader;
import org.junit.jupiter.api.Test;

import com.interpss.core.acsc.fault.SimpleFaultCode;
import com.interpss.dstab.DStabObjectFactory;
import com.interpss.dstab.algo.DynamicSimuMethod;
import com.interpss.dstab.cache.StateMonitor;
import com.interpss.dstab.mach.Machine;

/** Full-solver contract against an independently generated UEL2CU1 trajectory. */
@org.junit.jupiter.api.Tag("private-reference")
public class Uel2cu1NativeConformanceTest {
    private static final double STEP=.00025;
    private static final Path CASE=Path.of("testData","adpter","psse","v33","SMIB");
    private static final Path REFERENCE=Path.of("testData","reference","psse","smib-genrou-st1c-uel2cu1","native.csv");

    @Test void matchesNetworkAndNinePublishedStates()throws Exception{
        IpssCorePlugin.init();Path manifest=REFERENCE.resolveSibling("manifest.json");
        for(Path input:List.of(REFERENCE,CASE.resolve("SMIB_v33.raw"),CASE.resolve("SMIB_v33_genrou_st1c_uel2cu1.dyr"),
                Path.of("src","test","python","psse_uel2cu1_probe.py")))assertManifestHash(manifest,input);
        var context=new PSSEMultiFileLoader().loadDStab(CASE.resolve("SMIB_v33.raw").toString(),CASE.resolve("SMIB_v33_genrou_st1c_uel2cu1.dyr").toString());
        var network=context.getDStabilityNet();var algorithm=context.getDynSimuAlgorithm();assertTrue(algorithm.getAclfAlgorithm().loadflow());
        algorithm.setSimuMethod(DynamicSimuMethod.MODIFIED_EULER);algorithm.setSimuStepSec(STEP);algorithm.setTotalSimuTimeSec(1);algorithm.setSimuOutputHandler(new StateMonitor());
        network.addDynamicEvent(DStabObjectFactory.createBusFaultEvent("Bus1",network,SimpleFaultCode.GROUND_3P,new Complex(0,.2),null,.05,.05),"SmibFault");assertTrue(algorithm.initialization());
        Machine machine=network.getMachine("Bus1-mach1"),referenceMachine=network.getMachine("Bus2-mach1");
        Uel2cUnderExcitationLimiter limiter=network.getBus("Bus1").getDynamicBusDeviceList().stream().filter(Uel2cUnderExcitationLimiter.class::isInstance).map(Uel2cUnderExcitationLimiter.class::cast).findFirst().orElseThrow();
        List<double[]> actual=new ArrayList<>();record(actual,algorithm.getSimuTime(),network,machine,referenceMachine,limiter);
        while(algorithm.getSimuTime()<1-STEP/2){assertTrue(algorithm.solveDEqnStep(true));record(actual,algorithm.getSimuTime(),network,machine,referenceMachine,limiter);}
        Csv reference=read(REFERENCE);assertEquals(2005,reference.rows.size());String[] channels={"V_BUS1","V_BUS2","EFD","MACH_SPEED","REF_SPEED","UEL_V_FILTER","UEL_P_FILTER","UEL_Q_FILTER","UEL_INTEGRATOR","UEL_FB_FILTER","UEL_LL_1","UEL_LL_2","UEL_QREF_FILTER","UEL_GAIN_FILTER"};
        double[] maximum=new double[channels.length];for(double[] expected:reference.rows){double time=expected[0];if(time<0||time>1+1e-8||Math.abs(time-.05)<STEP||Math.abs(time-.1)<STEP)continue;double[] row=interpolate(actual,time);
            for(int c=0;c<channels.length;c++)maximum[c]=Math.max(maximum[c],Math.abs(row[c+1]-value(expected,reference,channels[c])));}
        System.out.println("UEL2CU1 native max errors: "+Arrays.toString(maximum));double[] tolerance={.0018,.0039,.028,.00009,.00000005,.0029,.025,.0155,.00011,.000000000001,.00065,.00041,.000022,.0013};
        for(int i=0;i<maximum.length;i++)assertTrue(maximum[i]<=tolerance[i],"channel "+channels[i]+" max="+maximum[i]);
    }
    @Test void prescribedTerminalSignalsMatchNineControllerStates()throws Exception{
        IpssCorePlugin.init();var context=new PSSEMultiFileLoader().loadDStab(CASE.resolve("SMIB_v33.raw").toString(),CASE.resolve("SMIB_v33_genrou_st1c_uel2cu1.dyr").toString());
        assertTrue(context.getDynSimuAlgorithm().getAclfAlgorithm().loadflow());context.getDynSimuAlgorithm().setSimuMethod(DynamicSimuMethod.MODIFIED_EULER);
        context.getDynSimuAlgorithm().setSimuStepSec(.0005);context.getDynSimuAlgorithm().setSimuOutputHandler(new StateMonitor());assertTrue(context.getDynSimuAlgorithm().initialization());
        Uel2cUnderExcitationLimiter limiter=context.getDStabilityNet().getBus("Bus1").getDynamicBusDeviceList().stream().filter(Uel2cUnderExcitationLimiter.class::isInstance).map(Uel2cUnderExcitationLimiter.class::cast).findFirst().orElseThrow();
        Csv csv=read(REFERENCE);List<double[]> rows=csv.rows.stream().filter(row->row[0]>=-1e-9).toList();double[] first=rows.get(0);
        limiter.initializeWithSignals(value(first,csv,"V_BUS1"),value(first,csv,"P_PU"),value(first,csv,"Q_PU"),0,0);
        String[] channels={"UEL_V_FILTER","UEL_P_FILTER","UEL_Q_FILTER","UEL_INTEGRATOR","UEL_FB_FILTER","UEL_LL_1","UEL_LL_2","UEL_QREF_FILTER","UEL_GAIN_FILTER"};double[] maximum=new double[9];
        for(int i=1;i<rows.size();i++){double[] previous=rows.get(i-1),current=rows.get(i);double dt=current[0]-previous[0];if(dt<=0)continue;
            assertTrue(limiter.nextStepWithSignals(dt,DynamicSimuMethod.MODIFIED_EULER,0,value(previous,csv,"V_BUS1"),value(previous,csv,"P_PU"),value(previous,csv,"Q_PU"),0,0));
            assertTrue(limiter.nextStepWithSignals(dt,DynamicSimuMethod.MODIFIED_EULER,1,value(current,csv,"V_BUS1"),value(current,csv,"P_PU"),value(current,csv,"Q_PU"),0,0));
            double[] state=limiter.getStateSnapshot();for(int c=0;c<9;c++)maximum[c]=Math.max(maximum[c],Math.abs(state[c]-value(current,csv,channels[c])));
        }
        System.out.println("UEL2CU1 prescribed-input max errors: "+Arrays.toString(maximum));double[] tolerance={.0014,.00065,.0095,.00011,1e-12,.00052,.00034,.0000015,.0001};
        for(int i=0;i<9;i++)assertTrue(maximum[i]<=tolerance[i],"state "+channels[i]+" max="+maximum[i]);
    }
    private static void record(List<double[]> rows,double time,com.interpss.dstab.BaseDStabNetwork<?,?> network,Machine machine,Machine reference,Uel2cUnderExcitationLimiter limiter){double[] s=limiter.getStateSnapshot();double[] row=new double[15];
        row[0]=time;row[1]=network.getBus("Bus1").getVoltageMag();row[2]=network.getBus("Bus2").getVoltageMag();row[3]=machine.getEfd();row[4]=machine.getSpeed()-1;row[5]=reference.getSpeed()-1;System.arraycopy(s,0,row,6,9);rows.add(row);}
    private static Csv read(Path path)throws Exception{String[] names=Files.readAllLines(path).get(0).split(",");Map<String,Integer> columns=new HashMap<>();for(int i=0;i<names.length;i++)columns.put(names[i],i);
        List<double[]> rows=Files.readAllLines(path).stream().skip(1).map(line->Arrays.stream(line.split(",")).mapToDouble(Double::parseDouble).toArray()).toList();return new Csv(columns,rows);}
    private static double value(double[] row,Csv csv,String name){return row[csv.columns.get(name)];}
    private static double[] interpolate(List<double[]> rows,double target){for(int i=0;i<rows.size();i++){double[] lo=rows.get(i);if(Math.abs(lo[0]-target)<1e-8)return lo;if(i+1<rows.size()&&rows.get(i+1)[0]>target){double[] hi=rows.get(i+1),r=new double[lo.length];double f=(target-lo[0])/(hi[0]-lo[0]);r[0]=target;for(int c=1;c<r.length;c++)r[c]=lo[c]+f*(hi[c]-lo[c]);return r;}}return rows.get(rows.size()-1);}
    private static void assertManifestHash(Path manifest,Path input)throws Exception{String hash=HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(input)));assertTrue(Files.readString(manifest).contains(hash),input+" hash missing from manifest");}
    private record Csv(Map<String,Integer> columns,List<double[]> rows){}
}
