package org.interpss.core.dstab.mach;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
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

/** Full-solver WT3E1 trajectory contract against independent numerical checkpoints. */
public class Wt3e1PsseSmibConformanceTest {
    private static final double STEP=.0005;
    private static final Path CASE=Path.of("testData","adpter","psse","v33","SMIB");
    private static final String[] CHANNELS={"VREG_FILTER","VREG_INTEGRAL","TORQUE_FILTER","TORQUE_INTEGRAL",
            "VOLTAGE_SENSOR","POWER_FILTER","Q_VREF_INTEGRAL","VERROR_EQ_INTEGRAL",
            "WINDVAR_LAG","PELEC_FILTER","V_BUS1","V_BUS2","P_PU","Q_PU","A_BUS1","A_BUS2"};
    private static final double[] CEILING={.14,.016,.00085,.00012,.011,.00030,.0125,.036,
            .16,.014,.011,.0095,.042,.072,.85,1.10};
    /** Sparse independent checkpoints; no generated trajectory artifact is required. */
    private static final double[][] CHECKPOINTS={
            {0,2.70316377282e-07,2.70316377282e-07,.766871154308,.639059245586,
                    .998746037483,.200000047684,.998746037483,.998746156693,0,.5,
                    .998746097088,1,.5,2.70083546638e-07,-.160413935781,-3.03000020981},
            {.0750000625849,.0593677610159,.00628604926169,.768702089787,.639074087143,
                    .644434690475,.197700113058,.996687948704,1.43514072895,1.36034154892,
                    .419611722231,.517389416695,.835880756378,.322432875633,1.04063928127,
                    .382907956839,-3.89635705948},
            {.125000163913,.192478597164,.00617648148909,.775609970093,.639165043831,
                    1.02218723297,.195936664939,.990053892136,1.65063130856,2.77065873146,
                    .497963100672,1.20823514462,1.0709887743,.620310544968,1.67297887802,
                    .131443694234,-2.62109422684},
            {.249998554587,-.082359328866,-.0217100568116,.778980493546,.639418303967,
                    1.05691242218,.196061655879,.980479061604,1.06477963924,-1.14409899712,
                    .508875370026,1.03599715233,1.01265823841,.496865332127,.252355754375,
                    -.308915883303,-3.02201581001},
            {.499995350838,-.0404727645218,-.0134735321626,.778995752335,.639895737171,
                    .97876393795,.196300238371,.97651976347,.930996596813,.228456482291,
                    .505587279797,.978021800518,.992949545383,.506778776646,-.132806241512,
                    -.0297479219735,-3.02099514008},
            {.750007033348,.261595129967,.00725517142564,.77885890007,.640343308449,
                    .979923248291,.19652441144,.983547329903,.942699193954,.298358798027,
                    .508234381676,.980511903763,.993794381618,.508407652378,-.116914466023,
                    -.0243699308485,-3.01519918442},
            {.999518692493,.242514163256,.0217787101865,.778730869293,.640762269497,
                    .988668262959,.196734815836,.992426872253,.971169650555,.177974507213,
                    .508499741554,.989368021488,.996804773808,.50847274065,-.060407590121,
                    -.0553191453218,-3.01078104973}
    };

    @Test void electricalControllerMatchesIndependentFaultTrajectory() throws Exception {
        IpssCorePlugin.init();
        var context=new PSSEMultiFileLoader().loadDStab(
                CASE.resolve("SMIB_v33_wt2g1.raw").toString(),
                CASE.resolve("SMIB_v33_wt3e1.dyr").toString());
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
        double[] maximum=new double[CHANNELS.length],times=new double[CHANNELS.length];
        for(double[] expected:CHECKPOINTS){double time=expected[0];double[] row=interpolate(actual,time);
            for(int c=0;c<CHANNELS.length;c++){double e=Math.abs(row[c+1]-expected[c+1]);
                if(e>maximum[c]){maximum[c]=e;times[c]=time;}}}
        System.out.println("WT3E1 checkpoint max errors: "+Arrays.toString(maximum));
        System.out.println("WT3E1 checkpoint max-error times: "+Arrays.toString(times));
        for(int c=0;c<maximum.length;c++)assertTrue(maximum[c]<=CEILING[c],CHANNELS[c]);
    }

    private static void record(List<double[]> rows,double t,com.interpss.dstab.BaseDStabNetwork<?,?> net,Wt3g1Model gen,Wt3e1Model model){
        Map<String,Double>s=model.getNamedStates();rows.add(new double[]{t,
                s.get("Voltage regulator filter"),s.get("Voltage regulator integrator"),s.get("Torque regulator filter"),s.get("Torque regulator integrator"),
                s.get("Voltage sensor"),s.get("Power filter"),s.get("MVAR/Vref integrator"),s.get("Voltage-error/internal-voltage integrator"),s.get("WindVar lag"),s.get("Fast-PF electrical-power filter"),
                net.getBus("Bus1").getVoltageMag(),net.getBus("Bus2").getVoltageMag(),gen.getP(),gen.getQ(),Math.toDegrees(net.getBus("Bus1").getVoltage().getArgument()),Math.toDegrees(net.getBus("Bus2").getVoltage().getArgument())});}
    private static double[] interpolate(List<double[]>r,double t){for(int i=0;i<r.size();i++){double[]a=r.get(i);if(Math.abs(a[0]-t)<1e-8)return a;if(i+1<r.size()&&r.get(i+1)[0]>t){double[]b=r.get(i+1),z=new double[a.length];double f=(t-a[0])/(b[0]-a[0]);z[0]=t;for(int j=1;j<z.length;j++)z[j]=a[j]+f*(b[j]-a[j]);return z;}}return r.get(r.size()-1);}
}
