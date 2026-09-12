package org.interpss.core.dstab.mach;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.apache.commons.math3.complex.Complex;
import org.interpss.IpssCorePlugin;
import org.interpss.dstab.mach.Wt3e1Model;
import org.interpss.dstab.mach.Wt3g1Model;
import org.interpss.fadapter.psse.PSSEMultiFileLoader;
import org.interpss.fadapter.psse.dyr.DynamicModelCatalog;
import org.junit.jupiter.api.Test;

import com.interpss.core.acsc.fault.SimpleFaultCode;
import com.interpss.dstab.DStabGen;
import com.interpss.dstab.DStabObjectFactory;
import com.interpss.dstab.algo.DynamicSimuMethod;
import com.interpss.dstab.cache.StateMonitor;

/** Full-solver trajectory contract against the independent native WT3E1 run. */
public class Wt3e1PsseSmibConformanceTest {
    private static final double STEP=.0005;
    private static final Path CASE=Path.of("testData","adpter","psse","v33","SMIB");
    private static final Path REFERENCE=Path.of("testData","reference","psse","smib-wt3e1","psse.csv");

    @Test void electricalControllerMatchesIndependentFaultTrajectory() throws Exception {
        IpssCorePlugin.init();
        var context=new PSSEMultiFileLoader().loadDStab(
                CASE.resolve("SMIB_v33_wt2g1_psse36.raw").toString(),
                CASE.resolve("SMIB_v33_wt3e1_psse36.dyr").toString());
        var network=context.getDStabilityNet();var algorithm=context.getDynSimuAlgorithm();
        algorithm.getAclfAlgorithm().getDataCheckConfig().setAllowGenWithoutMachine(true);
        assertTrue(algorithm.getAclfAlgorithm().loadflow());
        algorithm.setSimuMethod(DynamicSimuMethod.MODIFIED_EULER);algorithm.setSimuStepSec(STEP);
        algorithm.setTotalSimuTimeSec(1);algorithm.setSimuOutputHandler(new StateMonitor());
        network.addDynamicEvent(DStabObjectFactory.createBusFaultEvent("Bus1",network,
                SimpleFaultCode.GROUND_3P,new Complex(0,.1),null,.05,.05),"WindFault");
        assertTrue(algorithm.initialization());
        DStabGen gen=(DStabGen)network.getBus("Bus1").getContributeGen("1");
        Wt3g1Model generator=(Wt3g1Model)gen.getDynamicGenDevice();
        Wt3e1Model model=generator.getElectricalController();
        assertEquals(10,model.getNamedStates().size());
        assertEquals(37,DynamicModelCatalog.find("WT3E1").orElseThrow().parameterCount());
        assertEquals(1,model.getData().varFlag());assertEquals(1,model.getData().voltageLimitFlag());
        assertEquals(.76687,model.getNamedStates().get("Torque regulator filter"),2e-5);
        assertEquals(.63906,model.getNamedStates().get("Torque regulator integrator"),2e-5);
        List<double[]> actual=new ArrayList<>();record(actual,algorithm.getSimuTime(),network,generator,model);
        while(algorithm.getSimuTime()<1-STEP/2){assertTrue(algorithm.solveDEqnStep(true));record(actual,algorithm.getSimuTime(),network,generator,model);}
        Csv reference=read(REFERENCE);assertEquals(2005,reference.rows.size());
        String[] names={"VREG_FILTER","VREG_INTEGRAL","TORQUE_FILTER","TORQUE_INTEGRAL",
                "VOLTAGE_SENSOR","POWER_FILTER","Q_VREF_INTEGRAL","VERROR_EQ_INTEGRAL",
                "WINDVAR_LAG","PELEC_FILTER","V_BUS1","V_BUS2","P_PU","Q_PU","A_BUS1","A_BUS2"};
        double[] maximum=new double[names.length],times=new double[names.length];
        for(double[] expected:reference.rows){double time=expected[0];if(time<0||time>1.0000001||Math.abs(time-.05)<STEP||Math.abs(time-.10)<STEP)continue;
            double[] row=interpolate(actual,time);for(int c=0;c<names.length;c++){double e=Math.abs(row[c+1]-expected[reference.columns.get(names[c])]);if(e>maximum[c]){maximum[c]=e;times[c]=time;}}}
        System.out.println("WT3E1 native max errors: "+Arrays.toString(maximum));
        System.out.println("WT3E1 native max-error times: "+Arrays.toString(times));
        double[] ceiling={.14,.016,.00085,.00012,.011,.00030,.0125,.036,
                .16,.014,.011,.0095,.042,.072,.85,1.10};
        for(int c=0;c<maximum.length;c++)assertTrue(maximum[c]<=ceiling[c],names[c]);
    }

    private static void record(List<double[]> rows,double t,com.interpss.dstab.BaseDStabNetwork<?,?> net,Wt3g1Model gen,Wt3e1Model model){
        Map<String,Double>s=model.getNamedStates();rows.add(new double[]{t,
                s.get("Voltage regulator filter"),s.get("Voltage regulator integrator"),s.get("Torque regulator filter"),s.get("Torque regulator integrator"),
                s.get("Voltage sensor"),s.get("Power filter"),s.get("MVAR/Vref integrator"),s.get("Voltage-error/internal-voltage integrator"),s.get("WindVar lag"),s.get("Fast-PF electrical-power filter"),
                net.getBus("Bus1").getVoltageMag(),net.getBus("Bus2").getVoltageMag(),gen.getP(),gen.getQ(),Math.toDegrees(net.getBus("Bus1").getVoltage().getArgument()),Math.toDegrees(net.getBus("Bus2").getVoltage().getArgument())});}
    private static Csv read(Path p)throws Exception{List<String>lines=Files.readAllLines(p);String[]h=lines.get(0).split(",");Map<String,Integer>c=new LinkedHashMap<>();for(int i=0;i<h.length;i++)c.put(h[i],i);return new Csv(c,lines.stream().skip(1).map(x->Arrays.stream(x.split(",")).mapToDouble(Double::parseDouble).toArray()).toList());}
    private static double[] interpolate(List<double[]>r,double t){for(int i=0;i<r.size();i++){double[]a=r.get(i);if(Math.abs(a[0]-t)<1e-8)return a;if(i+1<r.size()&&r.get(i+1)[0]>t){double[]b=r.get(i+1),z=new double[a.length];double f=(t-a[0])/(b[0]-a[0]);z[0]=t;for(int j=1;j<z.length;j++)z[j]=a[j]+f*(b[j]-a[j]);return z;}}return r.get(r.size()-1);}
    private record Csv(Map<String,Integer>columns,List<double[]>rows){}
}
