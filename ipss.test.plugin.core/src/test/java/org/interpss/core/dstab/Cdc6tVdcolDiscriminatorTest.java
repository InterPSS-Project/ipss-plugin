package org.interpss.core.dstab;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.file.Files;
import java.nio.file.Path;
import org.apache.commons.math3.complex.Complex;
import org.interpss.CorePluginTestSetup;
import org.interpss.dstab.hvdc.Cdc6tParameters;
import org.interpss.dstab.hvdc.Cdc6tVoltageCurrentLimit;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import com.interpss.core.acsc.fault.SimpleFaultCode;
import com.interpss.dstab.DStabObjectFactory;
import com.interpss.dstab.algo.DynamicSimuMethod;
import com.interpss.dstab.cache.StateMonitor;
import org.interpss.dstab.algo.DefaultDynamicSimulationSolver;
import org.interpss.dstab.algo.StaggeredEulerSolver;

/** Small-network stimulus for the still-unqualified DC current-limit voltage selector. */
public class Cdc6tVdcolDiscriminatorTest extends CorePluginTestSetup {
    @TempDir Path folder;

    @ParameterizedTest @ValueSource(booleans={false,true})
    void rectifierFaultCanSeparateBothMeasuredVoltageCandidates(boolean staggered) throws Exception {
        var raw=Files.readAllLines(Path.of("testData/adpter/psse/v33/Kundur_2area_LCC_HVDC_PsetOnInv.raw"));
        var curve=new Cdc6tVoltageCurrentLimit(450,400,490,1000,550,1200);
        double[] constants={5,20,.05,.05,.2,.3,.02,.2,.3,.02,
                250,500,99,99,350,450,400,490,1000,550,1200,
                .02,-999,999,-999,0,0,999,999,0,999,.005};
        var fixture=Cdc6tCoupledNetworkTest.load(folder,raw,new Cdc6tParameters("1",constants));
        var algorithm=fixture.context().getDynSimuAlgorithm();var network=fixture.context().getDStabilityNet();
        algorithm.setSimuMethod(DynamicSimuMethod.MODIFIED_EULER);
        algorithm.setSimuStepSec(.0005);algorithm.setTotalSimuTimeSec(.15);
        algorithm.setSimuOutputHandler(new StateMonitor());
        algorithm.setSolver(staggered?new StaggeredEulerSolver(algorithm):new DefaultDynamicSimulationSolver(algorithm));
        network.addDynamicEvent(DStabObjectFactory.createBusFaultEvent("Bus7",network,
                SimpleFaultCode.GROUND_3P,new Complex(0,.2),null,.05,.05),"rectifier-fault");
        assertTrue(algorithm.initialization());algorithm.setSimuTime(-.001);
        double[] states=new double[3],boundary=new double[8];
        double widestDifference=0,selectedDifference=0,lowestGap=Double.POSITIVE_INFINITY;
        double lowestAc=Double.POSITIVE_INFINITY,lowestDc=Double.POSITIVE_INFINITY;
        while(algorithm.getSimuTime()<.15-.00025){
            assertTrue(algorithm.solveDEqnStep(true));
            fixture.runtime().readStaggeredState(states,0);
            fixture.runtime().readBoundary(boundary,0);
            lowestAc=Math.min(lowestAc,network.getBus("Bus7").getVoltageMag());
            lowestDc=Math.min(lowestDc,boundary[6]);
            double gap=states[2]-states[0];
            lowestGap=Math.min(lowestGap,gap);
            if(gap<0){
                double inverterCeiling=curve.ceilingAmperes(states[0]);
                double rectifierCeiling=curve.ceilingAmperes(states[2]);
                widestDifference=Math.max(widestDifference,inverterCeiling-rectifierCeiling);
                double desired=500e6/states[0];
                selectedDifference=Math.max(selectedDifference,
                        curve.limit(desired,states[0])-curve.limit(desired,states[2]));
            }
        }
        System.out.println("CDC6T VDCOL selector stimulus staggered="+staggered+
                " minimum rectifier-minus-inverter measurement="+lowestGap+
                " maximum candidate-current difference="+widestDifference+
                " maximum selected-current difference="+selectedDifference+
                " minimum rectifier AC="+lowestAc+" minimum rectifier DC="+lowestDc);
        assertTrue(lowestGap<0,"Stimulus must reverse the measured DC voltages");
        assertTrue(widestDifference>1,"Current-limit ceilings must be distinguishable");
        assertTrue(selectedDifference>1,"The lower-voltage ceiling must actively limit the power order");
    }
}
