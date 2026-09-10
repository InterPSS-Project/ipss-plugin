package org.interpss.core.dstab;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Set;

import org.apache.commons.math3.complex.Complex;
import org.interpss.IpssCorePlugin;
import org.interpss.core.dstab.reference.PowerWorldCsvReference;
import org.interpss.dstab.control.exc.psse.dc4b.Dc4bExciter;
import org.interpss.fadapter.psse.PSSEMultiFileLoader;
import org.junit.jupiter.api.Test;

import com.interpss.core.acsc.fault.SimpleFaultCode;
import com.interpss.dstab.DStabObjectFactory;
import com.interpss.dstab.algo.DynamicSimuMethod;
import com.interpss.dstab.cache.StateMonitor;
import com.interpss.dstab.mach.Machine;
import com.interpss.dstab.mach.RoundRotorMachine;

/** Full-solver native PSS/E DC4B comparison against PowerWorld. */
public class Dc4bPowerWorldSmibConformanceTest {
    private static final double STEP=0.0005;
    private static final Path CASE=Path.of("testData","adpter","psse","v33","SMIB");

    @Test
    void threeCycleFaultMatchesPowerWorldBoundaryMachineAndExciterStates()throws Exception{
        IpssCorePlugin.init();var context=new PSSEMultiFileLoader().loadDStab(
                CASE.resolve("SMIB_v33.raw").toString(),CASE.resolve("SMIB_v33_genrou_dc4b.dyr").toString());
        var network=context.getDStabilityNet();var algorithm=context.getDynSimuAlgorithm();
        assertTrue(algorithm.getAclfAlgorithm().loadflow(),"GENROU + DC4B SMIB load flow");
        algorithm.setSimuMethod(DynamicSimuMethod.MODIFIED_EULER);algorithm.setSimuStepSec(STEP);
        algorithm.setTotalSimuTimeSec(1);algorithm.setSimuOutputHandler(new StateMonitor());
        network.addDynamicEvent(DStabObjectFactory.createBusFaultEvent("Bus1",network,
                SimpleFaultCode.GROUND_3P,new Complex(0,.2),null,.05,.05),"SmibFault");
        assertTrue(algorithm.initialization(),"GENROU + DC4B SMIB initialization");

        RoundRotorMachine machine=(RoundRotorMachine)network.getMachine("Bus1-mach1");
        Machine referenceMachine=network.getMachine("Bus2-mach1");
        Dc4bExciter exciter=(Dc4bExciter)machine.getExciter();
        assertEquals(Set.of("EField","Sensed Vt","PI","Derivative","Vr","Feedback"),exciter.getNamedStates().keySet());
        double initialAngle=machine.getAngle()-referenceMachine.getAngle();List<double[]> actual=new ArrayList<>();
        record(actual,algorithm.getSimuTime(),network,machine,referenceMachine,exciter,initialAngle);
        while(algorithm.getSimuTime()<1-STEP/2){assertTrue(algorithm.solveDEqnStep(true));
            record(actual,algorithm.getSimuTime(),network,machine,referenceMachine,exciter,initialAngle);}

        PowerWorldCsvReference reference=PowerWorldCsvReference.read(Path.of("testData","reference","powerworld",
                "smib-genrou-dc4b","powerworld.csv"));
        assertEquals(2003,reference.samples().size());assertEquals(2001,reference.postEventSamples().size());
        int[] field={reference.fieldIndex("Bus","1","TSVpu"),reference.fieldIndex("Bus","2","TSVpu"),
                reference.fieldIndex("Generator","1 1","TSMW"),reference.fieldIndex("Generator","1 1","TSMvar"),
                reference.fieldIndex("Generator","1 1","TSRotorAngle"),reference.fieldIndex("Generator","1 1","TSSpeed"),
                reference.fieldIndex("Generator","1 1","TSMachineState:3"),reference.fieldIndex("Generator","1 1","TSMachineState:4"),
                reference.fieldIndex("Generator","1 1","TSMachineState:5"),reference.fieldIndex("Generator","1 1","TSMachineState:6"),
                reference.fieldIndex("Generator","1 1","TSExciterState:1"),reference.fieldIndex("Generator","1 1","TSExciterState:2"),
                reference.fieldIndex("Generator","1 1","TSExciterState:3"),reference.fieldIndex("Generator","1 1","TSExciterState:4"),
                reference.fieldIndex("Generator","1 1","TSExciterState:5"),reference.fieldIndex("Generator","1 1","TSExciterState:6")};
        int refAngle=reference.fieldIndex("Generator","2 1","TSRotorAngle");
        int refSpeed=reference.fieldIndex("Generator","2 1","TSSpeed");
        var initial=reference.postEventSamples().get(0);double initialPwAngle=initial.value(field[4])-initial.value(refAngle);
        double[] maximum=new double[field.length],maximumTime=new double[field.length];
        for(var expected:reference.postEventSamples()){
            if(Math.abs(expected.time()-.05)<STEP||Math.abs(expected.time()-.10)<STEP)continue;
            double[] row=interpolate(actual,expected.time()),pw=new double[field.length];
            for(int i=0;i<field.length;i++)pw[i]=expected.value(field[i]);
            pw[4]-=expected.value(refAngle)+initialPwAngle;pw[5]-=expected.value(refSpeed);
            for(int i=0;i<field.length;i++){double error=Math.abs(row[i+1]-pw[i]);
                if(error>maximum[i]){maximum[i]=error;maximumTime[i]=expected.time();}}
        }
        System.out.println("DC4B PowerWorld max errors: "+Arrays.toString(maximum));
        System.out.println("DC4B PowerWorld max-error times: "+Arrays.toString(maximumTime));
        String[] label={"Bus1 V","Bus2 V","P MW","Q Mvar","relative angle","relative speed",
                "Eqp","PsiDp","PsiQpp","Edp","EField","sensed Vt","PI","Derivative","Vr","Feedback"};
        double[] tolerance={3.4e-4,1.2e-4,.08,.23,.016,6.3e-6,8e-5,1.55e-4,
                1.4e-4,9.5e-5,.0024,3.4e-4,.009,.135,.04,2.4e-4};
        for(int i=0;i<maximum.length;i++)assertTrue(maximum[i]<tolerance[i],String.format(Locale.ROOT,
                "%s max error %.9g at %.9g exceeds %.9g",label[i],maximum[i],maximumTime[i],tolerance[i]));
    }

    private static void record(List<double[]> rows,double time,com.interpss.dstab.BaseDStabNetwork<?,?> network,
            RoundRotorMachine machine,Machine referenceMachine,Dc4bExciter exciter,double initialAngle){
        Complex v=network.getBus("Bus1").getVoltage();Complex i=machine.getIgen().subtract(v.multiply(machine.getYgen()));
        Complex s=v.multiply(i.conjugate());rows.add(new double[]{time,network.getBus("Bus1").getVoltageMag(),
                network.getBus("Bus2").getVoltageMag(),s.getReal()*100,s.getImaginary()*100,
                Math.toDegrees(machine.getAngle()-referenceMachine.getAngle()-initialAngle),
                machine.getSpeed()-referenceMachine.getSpeed(),machine.getEq1(),machine.getPsikd(),machine.getPsikq(),machine.getEd1(),
                exciter.getNamedState("EField"),exciter.getNamedState("Sensed Vt"),exciter.getNamedState("PI"),
                exciter.getNamedState("Derivative"),exciter.getNamedState("Vr"),exciter.getNamedState("Feedback")});
    }
    private static double[] interpolate(List<double[]> rows,double target){for(int i=0;i<rows.size();i++){double[] lo=rows.get(i);
        if(Math.abs(lo[0]-target)<1e-9)return lo;if(i+1<rows.size()&&rows.get(i+1)[0]>target){double[] hi=rows.get(i+1),r=new double[lo.length];
            double f=(target-lo[0])/(hi[0]-lo[0]);r[0]=target;for(int j=1;j<r.length;j++)r[j]=lo[j]+f*(hi[j]-lo[j]);return r;}}
        return rows.get(rows.size()-1);}
}
