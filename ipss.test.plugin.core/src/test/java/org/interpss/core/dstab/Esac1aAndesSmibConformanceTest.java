package org.interpss.core.dstab;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.interpss.IpssCorePlugin;
import org.interpss.dstab.control.exc.psse.esac1a.Esac1aExciter;
import org.interpss.fadapter.psse.PSSEMultiFileLoader;
import org.junit.jupiter.api.Test;

import com.interpss.dstab.DStabObjectFactory;
import com.interpss.dstab.algo.DynamicSimuMethod;
import com.interpss.dstab.cache.StateMonitor;
import com.interpss.dstab.common.DStabOutSymbol;
import com.interpss.dstab.datatype.DStabSimuEvent;
import com.interpss.dstab.devent.DynamicSimuEventType;

/** Full-stack trajectory comparison against native ANDES 2.0.0 ESAC1A. */
public class Esac1aAndesSmibConformanceTest {
    private static final double STEP=.0005;
    private static final Path CASE=Path.of("testData","adpter","psse","v33","SMIB");
    private static final Path REFERENCE=Path.of("src","test","resources","reference",
            "andes","esac1a-smib-line-trip.csv");

    @Test void lineTripTrajectoryMatchesNativeAndes() throws Exception {
        IpssCorePlugin.init();
        var context=new PSSEMultiFileLoader().loadDStab(
                CASE.resolve("SMIB_v33.raw").toString(),
                CASE.resolve("SMIB_v33_genrou_esac1a.dyr").toString());
        var network=context.getDStabilityNet();var algorithm=context.getDynSimuAlgorithm();
        assertTrue(algorithm.getAclfAlgorithm().loadflow(),"SMIB load flow");
        algorithm.setSimuMethod(DynamicSimuMethod.MODIFIED_EULER);
        algorithm.setSimuStepSec(STEP);algorithm.setTotalSimuTimeSec(5);algorithm.setOutPutPerSteps(1);
        var outage=DStabObjectFactory.createDEvent("TripLine1","TripLine1",
                DynamicSimuEventType.BRANCH_OUTAGE,network);
        outage.setStartTimeSec(1);outage.setDurationSec(999);outage.setPermanent(true);
        outage.setBranchDynamicEvent(DStabObjectFactory.createBranchOutageEvent(
                network.getBranch("Bus1","Bus2","1").getId(),network));
        network.addDynamicEvent(outage,"TripLine1");
        Esac1aExciter exciter=(Esac1aExciter)network.getMachine("Bus1-mach1").getExciter();
        InternalStateMonitor monitor=new InternalStateMonitor(exciter);
        monitor.addGeneratorStdMonitor(new String[]{"Bus1-mach1"});
        monitor.addBusStdMonitor(new String[]{"Bus1","Bus2"});algorithm.setSimuOutputHandler(monitor);
        assertTrue(algorithm.initialization(),"ESAC1A dynamic initialization");
        assertTrue(algorithm.performSimulation(),"ESAC1A line-trip simulation");

        Series speed=series(monitor.getMachSpeedTable(),"Bus1-mach1");
        Series efd=series(monitor.getMachEfdTable(),"Bus1-mach1");
        Series bus1=series(monitor.getBusVoltTable(),"Bus1");
        Series bus2=series(monitor.getBusVoltTable(),"Bus2");
        assertTrue(monitor.internal.size()==speed.time.length,"aligned internal samples");
        Series[] internal=monitor.internalSeries(speed.time);double[] max=new double[9],maxTime=new double[9];
        for(double[] row:readReference()){
            double time=row[0];double[] actual={speed.at(time),efd.at(time),bus1.at(time),bus2.at(time),
                    internal[0].at(time),internal[1].at(time),internal[2].at(time),
                    internal[3].at(time),internal[4].at(time)};
            for(int i=0;i<actual.length;i++){
                if((i==2||i==3)&&Math.abs(time-1)<=STEP/2)continue;
                double error=Math.abs(actual[i]-row[i+1]);if(error>max[i]){max[i]=error;maxTime[i]=time;}
            }
        }
        System.out.printf(java.util.Locale.ROOT,
                "ESAC1A ANDES SMIB max errors: speed=%.9g@%.4f efd=%.9g@%.4f "
                +"v1=%.9g@%.4f v2=%.9g@%.4f states=%s%n",
                max[0],maxTime[0],max[1],maxTime[1],max[2],maxTime[2],max[3],maxTime[3],
                Arrays.toString(Arrays.copyOfRange(max,4,max.length)));
        assertTrue(max[0]<2e-5,"rotor-speed parity: "+max[0]);
        assertTrue(max[1]<4e-3,"field-voltage parity: "+max[1]);
        assertTrue(max[2]<1.5e-4,"generator-bus voltage parity: "+max[2]);
        assertTrue(max[3]<3e-5,"infinite-bus voltage parity: "+max[3]);
        double[] internalTolerance={1.5e-4,8e-4,1e-2,4e-3,2e-3};
        for(int i=4;i<max.length;i++)assertTrue(max[i]<internalTolerance[i-4],
                "ESAC1A internal-state parity index "+(i-4)+": "+max[i]+" at "+maxTime[i]);
    }

    private static Series series(java.util.Hashtable<String,
            java.util.Hashtable<Integer,StateMonitor.MonitorRecord>> table,String id){
        var records=table.get(id);double[] time=new double[records.size()],value=new double[records.size()];
        for(int i=0;i<records.size();i++){time[i]=records.get(i).getTime();value[i]=records.get(i).getValue();}
        return new Series(time,value);
    }
    private static List<double[]> readReference() throws Exception {
        List<double[]> rows=new ArrayList<>();for(String line:Files.readAllLines(REFERENCE)){
            if(line.isBlank()||line.startsWith("#")||line.startsWith("time_s"))continue;
            String[] values=line.split(",");double[] row=new double[values.length];
            for(int i=0;i<values.length;i++)row[i]=Double.parseDouble(values[i]);rows.add(row);
        }return rows;
    }
    private record Series(double[] time,double[] value){double at(double target){
        int index=Arrays.binarySearch(time,target);if(index>=0)return value[index];int upper=-index-1;
        if(upper==0||upper==time.length)throw new IllegalArgumentException();int lower=upper-1;
        double fraction=(target-time[lower])/(time[upper]-time[lower]);
        return value[lower]+fraction*(value[upper]-value[lower]);}}

    private static final class InternalStateMonitor extends StateMonitor {
        private final Esac1aExciter exciter;private final List<double[]> internal=new ArrayList<>();
        private InternalStateMonitor(Esac1aExciter exciter){this.exciter=exciter;}
        @Override public boolean onSimuEvent(DStabSimuEvent event){boolean accepted=super.onSimuEvent(event);
            if(accepted&&event.getType()==DStabSimuEvent.PlotStepMachineStates
                    &&"Bus1-mach1".equals(event.getHashtableData().get(DStabOutSymbol.OUT_SYMBOL_MACH_ID)))
                {double[] state=exciter.getStateSnapshot();internal.add(new double[]{state[0],state[1],
                        state[2],state[3],exciter.getRateFeedback()});}
            return accepted;}
        private Series[] internalSeries(double[] time){Series[] result=new Series[5];
            for(int column=0;column<result.length;column++){double[] values=new double[internal.size()];
                for(int row=0;row<values.length;row++)values[row]=internal.get(row)[column];
                result[column]=new Series(time,values);}return result;}
    }
}
