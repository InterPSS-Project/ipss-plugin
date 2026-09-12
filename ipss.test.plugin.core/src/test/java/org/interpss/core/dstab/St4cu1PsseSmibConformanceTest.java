package org.interpss.core.dstab;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import org.apache.commons.math3.complex.Complex;
import org.interpss.IpssCorePlugin;
import org.interpss.dstab.control.exc.psse.st4c.St4cExciter;
import org.interpss.fadapter.psse.PSSEMultiFileLoader;
import org.junit.jupiter.api.Test;

import com.interpss.core.acsc.fault.SimpleFaultCode;
import com.interpss.dstab.DStabObjectFactory;
import com.interpss.dstab.algo.DynamicSimuMethod;
import com.interpss.dstab.cache.StateMonitor;
import com.interpss.dstab.mach.Machine;
import com.interpss.dstab.mach.RoundRotorMachine;

/** Full-solver contract for ST4CU1 against the equivalent published ST4C equation. */
public class St4cu1PsseSmibConformanceTest {
    private static final double STEP=0.00025;
    private static final Path CASE=Path.of("testData","adpter","psse","v33","SMIB");
    private static final Path REFERENCE=Path.of("testData","reference","psse","smib-st4cu1","psse.csv");

    @Test
    void threeCycleFaultMatchesBoundaryOutputAndAllFivePublishedStates()throws Exception{
        IpssCorePlugin.init();String hash=HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                .digest(Files.readAllBytes(REFERENCE)));
        assertTrue(Files.readString(REFERENCE.resolveSibling("manifest.json")).contains(hash));
        var context=new PSSEMultiFileLoader().loadDStab(CASE.resolve("SMIB_v33.raw").toString(),
                CASE.resolve("SMIB_v33_genrou_st4cu1.dyr").toString());
        var network=context.getDStabilityNet();var algorithm=context.getDynSimuAlgorithm();
        assertTrue(algorithm.getAclfAlgorithm().loadflow());algorithm.setSimuMethod(DynamicSimuMethod.MODIFIED_EULER);
        algorithm.setSimuStepSec(STEP);algorithm.setTotalSimuTimeSec(1);algorithm.setSimuOutputHandler(new StateMonitor());
        network.addDynamicEvent(DStabObjectFactory.createBusFaultEvent("Bus1",network,SimpleFaultCode.GROUND_3P,
                new Complex(0,.2),null,.05,.05),"SmibFault");assertTrue(algorithm.initialization());
        RoundRotorMachine machine=(RoundRotorMachine)network.getMachine("Bus1-mach1");
        Machine referenceMachine=network.getMachine("Bus2-mach1");
        St4cExciter exciter=(St4cExciter)machine.getExciter();assertEquals(5,exciter.getNamedStates().size());
        double initialRelativeAngle=machine.getAngle()-referenceMachine.getAngle();List<double[]> actual=new ArrayList<>();
        record(actual,algorithm.getSimuTime(),network,machine,referenceMachine,exciter,initialRelativeAngle);
        while(algorithm.getSimuTime()<1-STEP/2){assertTrue(algorithm.solveDEqnStep(true));
            record(actual,algorithm.getSimuTime(),network,machine,referenceMachine,exciter,initialRelativeAngle);}
        Csv reference=read(REFERENCE);assertEquals(2005,reference.rows().size());
        double[] initial=reference.rows().stream().filter(row->row[0]>=-1e-9).findFirst().orElseThrow();
        double initialNativeAngle=value(initial,reference,"MACH_ANGLE")-value(initial,reference,"REF_ANGLE");
        double[] maximum=new double[12],maximumTime=new double[12];
        for(double[] expected:reference.rows()){
            double time=expected[0];if(time < -1e-9||time>1+1e-8||Math.abs(time-.05)<STEP||Math.abs(time-.10)<STEP)continue;
            double[] row=interpolate(actual,time);double[] nativeRow={value(expected,reference,"V_BUS1"),
                    value(expected,reference,"V_BUS2"),value(expected,reference,"P_PU"),value(expected,reference,"Q_PU"),
                    value(expected,reference,"MACH_ANGLE")-value(expected,reference,"REF_ANGLE")-initialNativeAngle,
                    value(expected,reference,"MACH_SPEED")-value(expected,reference,"REF_SPEED"),
                    value(expected,reference,"MACHINE_EFD"),value(expected,reference,"SENSED_VT"),
                    value(expected,reference,"VR_INTEGRATOR"),value(expected,reference,"VM_INTEGRATOR"),
                    value(expected,reference,"VG"),value(expected,reference,"VA")};
            for(int column=0;column<maximum.length;column++){double error=Math.abs(row[column+1]-nativeRow[column]);
                if(error>maximum[column]){maximum[column]=error;maximumTime[column]=time;}}
        }
        System.out.println("ST4CU1 native max errors: "+Arrays.toString(maximum));
        System.out.println("ST4CU1 native max-error times: "+Arrays.toString(maximumTime));
        double[] tolerances={0.00183,0.00405,0.0252,0.00983,0.197,9.10e-5,0.00299,
                9.56e-4,0.311,0.00242,4.75e-4,0.00299};
        String[] labels={"Bus1 V","Bus2 V","P","Q","relative angle","relative speed","machine Efd",
                "Sensed Vt","VR Integrator","VM Integrator","VG","VA"};
        for(int column=0;column<maximum.length;column++)assertTrue(maximum[column]<=tolerances[column],
                String.format(Locale.ROOT,"%s max error %.9g at %.9g exceeds %.9g",labels[column],maximum[column],maximumTime[column],tolerances[column]));
    }

    private static void record(List<double[]> rows,double time,com.interpss.dstab.BaseDStabNetwork<?,?> network,
            RoundRotorMachine machine,Machine referenceMachine,St4cExciter exciter,double initialAngle){
        Complex voltage=network.getBus("Bus1").getVoltage();Complex current=machine.getIgen().subtract(voltage.multiply(machine.getYgen()));
        Complex power=voltage.multiply(current.conjugate());Map<String,Double> state=exciter.getNamedStates();
        rows.add(new double[]{time,network.getBus("Bus1").getVoltageMag(),network.getBus("Bus2").getVoltageMag(),
                power.getReal(),power.getImaginary(),Math.toDegrees(machine.getAngle()-referenceMachine.getAngle()-initialAngle),
                machine.getSpeed()-referenceMachine.getSpeed(),exciter.getOutput(machine),state.get("Sensed Vt"),state.get("VR"),
                state.get("VM"),state.get("VG"),state.get("VA")});
    }
    private static Csv read(Path path)throws Exception{List<String> lines=Files.readAllLines(path);String[] headings=lines.get(0).split(",");
        Map<String,Integer> columns=new LinkedHashMap<>();for(int index=0;index<headings.length;index++)columns.put(headings[index],index);
        return new Csv(columns,lines.stream().skip(1).map(line->Arrays.stream(line.split(",")).mapToDouble(Double::parseDouble).toArray()).toList());}
    private static double value(double[] row,Csv csv,String name){return row[csv.columns().get(name)];}
    private static double[] interpolate(List<double[]> rows,double target){for(int index=0;index<rows.size();index++){double[] lower=rows.get(index);
        if(Math.abs(lower[0]-target)<1e-8)return lower;if(index+1<rows.size()&&rows.get(index+1)[0]>target){double[] upper=rows.get(index+1),result=new double[lower.length];
            double fraction=(target-lower[0])/(upper[0]-lower[0]);result[0]=target;for(int column=1;column<result.length;column++)
                result[column]=lower[column]+fraction*(upper[column]-lower[column]);return result;}}return rows.get(rows.size()-1);}
    private record Csv(Map<String,Integer> columns,List<double[]> rows){}
}
