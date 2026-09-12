package org.interpss.core.dstab.mach;

import static org.junit.jupiter.api.Assertions.*;
import java.nio.file.*; import java.util.*;
import org.apache.commons.math3.complex.Complex;
import org.interpss.IpssCorePlugin; import org.interpss.dstab.mach.*;
import org.interpss.fadapter.psse.PSSEMultiFileLoader; import org.junit.jupiter.api.Test;
import com.interpss.core.acsc.fault.SimpleFaultCode; import com.interpss.dstab.*;
import com.interpss.dstab.algo.DynamicSimuMethod; import com.interpss.dstab.cache.StateMonitor;

/** Full-loop trajectory comparison against the independent native WT4E1 run. */
@org.junit.jupiter.api.Tag("private-reference")
public class Wt4e1PsseSmibConformanceTest {
    private static final double STEP=.0005;
    private static final Path CASE=Path.of("testData","adpter","psse","v33","SMIB");
    private static final Path REF=Path.of("testData","reference","psse","smib-wt4e1","psse.csv");
    @Test void electricalControllerMatchesIndependentFaultTrajectory() throws Exception {
        IpssCorePlugin.init(); var context=new PSSEMultiFileLoader().loadDStab(
                CASE.resolve("SMIB_v33_wt3g2_psse36.raw").toString(),CASE.resolve("SMIB_v33_wt4e1_psse36.dyr").toString());
        var net=context.getDStabilityNet(); var alg=context.getDynSimuAlgorithm();
        alg.getAclfAlgorithm().getDataCheckConfig().setAllowGenWithoutMachine(true); assertTrue(alg.getAclfAlgorithm().loadflow());
        alg.setSimuMethod(DynamicSimuMethod.MODIFIED_EULER); alg.setSimuStepSec(STEP); alg.setTotalSimuTimeSec(1); alg.setSimuOutputHandler(new StateMonitor());
        net.addDynamicEvent(DStabObjectFactory.createBusFaultEvent("Bus1",net,SimpleFaultCode.GROUND_3P,new Complex(0,1.0),null,.05,.05),"WindFault"); assertTrue(alg.initialization());
        DStabGen dg=(DStabGen)net.getBus("Bus1").getContributeGen("1"); Wt4g1Model gen=(Wt4g1Model)dg.getDynamicGenDevice(); Wt4e1Model model=gen.getElectricalController();
        List<double[]> actual=new ArrayList<>(); record(actual,alg.getSimuTime(),net,gen,model);
        while(alg.getSimuTime()<1-STEP/2){assertTrue(alg.solveDEqnStep(true));record(actual,alg.getSimuTime(),net,gen,model);}
        Csv ref=read(REF); assertEquals(2005,ref.rows.size());
        String[] names={"GEN_IP","GEN_EQ","GEN_LVPL","VREG_FILTER","VREG_INTEGRAL","POWER_INTEGRAL","POWER_FEEDBACK","VOLTAGE_SENSOR","POWER_FILTER","Q_VREF_INTEGRAL","VERROR_INTERNAL_INTEGRAL","WINDVAR_LAG","PELEC_FILTER","V_BUS1","V_BUS2","P_PU","Q_PU","A_BUS1","A_BUS2"};
        double[] max=new double[names.length];
        for(double[] expected:ref.rows){double t=expected[0];if(t<0||t>1.0000001||Math.abs(t-.05)<STEP||Math.abs(t-.10)<STEP)continue;double[] row=interpolate(actual,t);for(int c=0;c<names.length;c++)max[c]=Math.max(max[c],Math.abs(row[c+1]-expected[ref.columns.get(names[c]) ]));}
        System.out.println("WT4E1 native max errors: "+Arrays.toString(max));
        double[] ceiling={.0057,.0026,.0023,.0049,.00056,.000050,.00055,.0028,
                .0015,.00013,.0039,.016,.0015,.0024,.0026,.0057,.0028,.28,.28};
        for(int c=0;c<max.length;c++)assertTrue(max[c]<=ceiling[c],names[c]);
    }
    private static void record(List<double[]>r,double t,com.interpss.dstab.BaseDStabNetwork<?,?>n,Wt4g1Model g,Wt4e1Model m){Map<String,Double>s=m.getNamedStates();r.add(new double[]{t,g.getActiveCurrentState(),g.getReactiveCurrentState(),g.getFilteredVoltageState(),s.get("Voltage regulator filter"),s.get("Voltage regulator integrator"),s.get("Active-power regulator integrator"),s.get("Active-power regulator feedback"),s.get("Voltage sensor"),s.get("Power filter"),s.get("MVAR/Vref integrator"),s.get("Voltage-error/internal-voltage integrator"),s.get("WindVar lag"),s.get("Fast-PF electrical-power filter"),n.getBus("Bus1").getVoltageMag(),n.getBus("Bus2").getVoltageMag(),g.getP(),g.getQ(),Math.toDegrees(n.getBus("Bus1").getVoltage().getArgument()),Math.toDegrees(n.getBus("Bus2").getVoltage().getArgument())});}
    private static Csv read(Path p)throws Exception{List<String>l=Files.readAllLines(p);String[]h=l.get(0).split(",");Map<String,Integer>c=new LinkedHashMap<>();for(int i=0;i<h.length;i++)c.put(h[i],i);return new Csv(c,l.stream().skip(1).map(x->Arrays.stream(x.split(",")).mapToDouble(Double::parseDouble).toArray()).toList());}
    private static double[] interpolate(List<double[]>r,double t){for(int i=0;i<r.size();i++){double[]a=r.get(i);if(Math.abs(a[0]-t)<1e-8)return a;if(i+1<r.size()&&r.get(i+1)[0]>t){double[]b=r.get(i+1),z=new double[a.length];double f=(t-a[0])/(b[0]-a[0]);z[0]=t;for(int j=1;j<z.length;j++)z[j]=a[j]+f*(b[j]-a[j]);return z;}}return r.get(r.size()-1);}
    private record Csv(Map<String,Integer>columns,List<double[]>rows){}
}
