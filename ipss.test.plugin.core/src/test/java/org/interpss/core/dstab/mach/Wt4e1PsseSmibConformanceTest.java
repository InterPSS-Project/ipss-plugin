package org.interpss.core.dstab.mach;

import static org.junit.jupiter.api.Assertions.*;
import java.nio.file.Path; import java.util.*;
import org.apache.commons.math3.complex.Complex;
import org.interpss.IpssCorePlugin; import org.interpss.dstab.mach.*;
import org.interpss.fadapter.psse.PSSEMultiFileLoader; import org.junit.jupiter.api.Test;
import com.interpss.core.acsc.fault.SimpleFaultCode; import com.interpss.dstab.*;
import com.interpss.dstab.algo.DynamicSimuMethod; import com.interpss.dstab.cache.StateMonitor;

/** Full-loop WT4E1 trajectory contract against independent numerical checkpoints. */
public class Wt4e1PsseSmibConformanceTest {
    private static final double STEP=.0005;
    private static final Path CASE=Path.of("testData","adpter","psse","v33","SMIB");
    private static final String[] CHANNELS={"GEN_IP","GEN_EQ","GEN_LVPL","VREG_FILTER","VREG_INTEGRAL","POWER_INTEGRAL","POWER_FEEDBACK","VOLTAGE_SENSOR","POWER_FILTER","Q_VREF_INTEGRAL","VERROR_INTERNAL_INTEGRAL","WINDVAR_LAG","PELEC_FILTER","V_BUS1","V_BUS2","P_PU","Q_PU","A_BUS1","A_BUS2"};
    private static final double[] CEILING={.0057,.0026,.0023,.0049,.00056,.000050,.00055,.0028,
            .0015,.00013,.0039,.016,.0015,.0024,.0026,.0057,.0028,.28,.28};
    /** Sparse independent checkpoints; no generated trajectory artifact is required. */
    private static final double[][] CHECKPOINTS={
            {0,.500627756119,-4.19621359882e-09,.998746037483,-4.19095158577e-09,
                    -4.19095158577e-09,0,0,.998746037483,.5,.998746037483,
                    -4.19621359882e-09,0,.5,.998746037483,.999999940395,.5,
                    -4.19095158577e-09,-.160414204001,-3.03000068665},
            {.0750000625849,.542703807354,.0340748541057,.926934719086,.0270681008697,
                    .00560506945476,-8.28129559522e-05,-8.21524590719e-05,.906303048134,
                    .482162386179,.998738229275,.0975016355515,.352735489607,.482162386179,
                    .871444344521,.956718206406,.472915142775,.0296930205077,.0797434300184,
                    -3.17212510109},
            {.125000163913,.510745584965,.151207074523,.965425908566,.170093983412,
                    .00723526487127,-.000223584283958,-.000157992981258,.980863511562,
                    .506547272205,.998766064644,.174902662635,.824656367302,.506547272205,
                    1.0215536356,1.00773024559,.521743655205,.154463097453,-.053833451122,
                    -2.95902347565},
            {.249998554587,.491509258747,.110190466046,1.01696038246,.0995371639729,
                    -.000983371166512,-4.08292617067e-05,2.33179471252e-05,1.01710569859,
                    .500862419605,.999428331852,.0957423001528,-.190675839782,.500862419605,
                    1.01551687717,1.00569951534,.499147385359,.111902855337,-.223163381219,
                    -3.02445983887},
            {.499995350838,.498408615589,.0258767772466,1.00379574299,-.128983423114,
                    -.0114372074604,-5.77770624659e-05,-2.09426652873e-05,1.00337648392,
                    .499697595835,.996059596539,.0200906731188,-.0992570221424,.499697595835,
                    1.00268566608,1.00133895874,.499751836061,.0259465184063,-.175800055265,
                    -3.02883195877},
            {.750007033348,.501533389091,-.0129475770518,.997321665287,-.0461440570652,
                    -.012576428242,-7.24677374819e-05,-2.3305274226e-05,.99711483717,
                    .499889135361,.99367660284,-.0157366972417,.00944744236767,.499889135361,
                    .996776819229,.999330818653,.499919116497,-.0129059022292,-.154460057616,
                    -3.03116822243},
            {.999518692493,.502799272537,-.027877965942,.994644999504,.0245172735304,
                    -.00941885914654,-7.57938250899e-05,-2.19522808038e-05,.994585037231,
                    .500015556812,.994041740894,-.0284476485103,.057631932199,.500015556812,
                    .994503736496,.998558223248,.500036239624,-.0277247652411,-.145713701844,
                    -3.03192234039}
    };
    @Test void electricalControllerMatchesIndependentFaultTrajectory() throws Exception {
        IpssCorePlugin.init(); var context=new PSSEMultiFileLoader().loadDStab(
                CASE.resolve("SMIB_v33_wt3g2.raw").toString(),CASE.resolve("SMIB_v33_wt4e1.dyr").toString());
        var net=context.getDStabilityNet(); var alg=context.getDynSimuAlgorithm();
        alg.getAclfAlgorithm().getDataCheckConfig().setAllowGenWithoutMachine(true); assertTrue(alg.getAclfAlgorithm().loadflow());
        alg.setSimuMethod(DynamicSimuMethod.MODIFIED_EULER); alg.setSimuStepSec(STEP); alg.setTotalSimuTimeSec(1); alg.setSimuOutputHandler(new StateMonitor());
        net.addDynamicEvent(DStabObjectFactory.createBusFaultEvent("Bus1",net,SimpleFaultCode.GROUND_3P,new Complex(0,1.0),null,.05,.05),"WindFault"); assertTrue(alg.initialization());
        DStabGen dg=(DStabGen)net.getBus("Bus1").getContributeGen("1"); Wt4g1Model gen=(Wt4g1Model)dg.getDynamicGenDevice(); Wt4e1Model model=gen.getElectricalController();
        List<double[]> actual=new ArrayList<>(); record(actual,alg.getSimuTime(),net,gen,model);
        while(alg.getSimuTime()<1-STEP/2){assertTrue(alg.solveDEqnStep(true));record(actual,alg.getSimuTime(),net,gen,model);}
        double[] max=new double[CHANNELS.length];
        for(double[] expected:CHECKPOINTS){double t=expected[0];double[] row=interpolate(actual,t);for(int c=0;c<CHANNELS.length;c++)max[c]=Math.max(max[c],Math.abs(row[c+1]-expected[c+1]));}
        System.out.println("WT4E1 checkpoint max errors: "+Arrays.toString(max));
        for(int c=0;c<max.length;c++)assertTrue(max[c]<=CEILING[c],CHANNELS[c]);
    }
    private static void record(List<double[]>r,double t,com.interpss.dstab.BaseDStabNetwork<?,?>n,Wt4g1Model g,Wt4e1Model m){Map<String,Double>s=m.getNamedStates();r.add(new double[]{t,g.getActiveCurrentState(),g.getReactiveCurrentState(),g.getFilteredVoltageState(),s.get("Voltage regulator filter"),s.get("Voltage regulator integrator"),s.get("Active-power regulator integrator"),s.get("Active-power regulator feedback"),s.get("Voltage sensor"),s.get("Power filter"),s.get("MVAR/Vref integrator"),s.get("Voltage-error/internal-voltage integrator"),s.get("WindVar lag"),s.get("Fast-PF electrical-power filter"),n.getBus("Bus1").getVoltageMag(),n.getBus("Bus2").getVoltageMag(),g.getP(),g.getQ(),Math.toDegrees(n.getBus("Bus1").getVoltage().getArgument()),Math.toDegrees(n.getBus("Bus2").getVoltage().getArgument())});}
    private static double[] interpolate(List<double[]>r,double t){for(int i=0;i<r.size();i++){double[]a=r.get(i);if(Math.abs(a[0]-t)<1e-8)return a;if(i+1<r.size()&&r.get(i+1)[0]>t){double[]b=r.get(i+1),z=new double[a.length];double f=(t-a[0])/(b[0]-a[0]);z[0]=t;for(int j=1;j<z.length;j++)z[j]=a[j]+f*(b[j]-a[j]);return z;}}return r.get(r.size()-1);}
}
