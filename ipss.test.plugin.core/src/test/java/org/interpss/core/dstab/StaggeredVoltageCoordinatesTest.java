package org.interpss.core.dstab;

import static org.junit.jupiter.api.Assertions.*;
import org.interpss.IpssCorePlugin;
import org.interpss.dstab.algo.PsseStaggeredEulerSolver;
import org.interpss.fadapter.psse.PSSEMultiFileLoader;
import org.junit.jupiter.api.Test;
import com.interpss.dstab.algo.DynamicSimuMethod;
import com.interpss.dstab.cache.StateMonitor;
import com.interpss.dstab.impl.DStabilityNetworkImpl;

/** Coordinate representation and lazy publication must preserve the coupled model. */
class StaggeredVoltageCoordinatesTest {
    @Test void roundAndSalientRotorTrajectoriesRetainTheSamePhysicalSolution() throws Exception {
        for(String model:new String[]{"genrou","gensal"}) {
            double[][] polar=trajectory(model,false,false);
            double[][] rectangular=trajectory(model,true,false);
            double[][] deferred=trajectory(model,true,true);
            for(int i=0;i<polar.length;i++) {
                assertArrayEquals(rectangular[i],deferred[i],0.,"Publication must preserve every state at step "+i);
                assertArrayEquals(polar[i],rectangular[i],1e-9,"Coordinate round trips at step "+i);
            }
        }
    }

    private double[][] trajectory(String model,boolean rectangular,boolean efficient) throws Exception {
        IpssCorePlugin.init();String base="testData/adpter/psse/v33/SMIB/";
        var context=new PSSEMultiFileLoader().loadDStab(base+"SMIB_v33.raw",base+"SMIB_v33_"+model+".dyr");
        var algorithm=context.getDynSimuAlgorithm();
        assertTrue(algorithm.getAclfAlgorithm().loadflow());
        var network=(DStabilityNetworkImpl)context.getDStabilityNet();
        network.setEfficientVoltagePublicationEnabled(efficient);
        var solver=new PsseStaggeredEulerSolver(algorithm);
        assertFalse(solver.isUseRectangularNetworkVoltage());
        solver.setUseRectangularNetworkVoltage(rectangular);
        solver.setNetworkSolutionTolerance(1e-5);algorithm.setSolver(solver);
        algorithm.setSimuMethod(DynamicSimuMethod.MODIFIED_EULER);algorithm.setSimuStepSec(1./240);
        algorithm.setTotalSimuTimeSec(.5);algorithm.setSimuOutputHandler(new StateMonitor());
        assertTrue(algorithm.initialization());assertEquals(!rectangular,network.isPolarCoordinate());
        assertThrows(IllegalStateException.class,()->solver.setUseRectangularNetworkVoltage(!rectangular));
        var machine=network.getMachine("Bus1-mach1");double power=machine.getPm();
        double[][] trace=new double[100][];
        for(int i=0;i<trace.length;i++) {
            if(i==10 || i==40) {machine.setPm(i==10?power+.1:power);solver.notifyDiscontinuity();}
            assertTrue(algorithm.solveDEqnStep(true));
            double[] row=new double[4+network.getBusList().size()*4];
            row[0]=machine.getAngle();row[1]=machine.getSpeed();row[2]=machine.getPe();row[3]=machine.getEfd();
            int j=4;for(var bus:network.getBusList()) {
                var voltage=bus.getVoltage();row[j++]=voltage.getReal();row[j++]=voltage.getImaginary();
                row[j++]=bus.getVoltageMag();row[j++]=bus.getVoltageAng();
            }
            trace[i]=row;
        }
        assertEquals(efficient && rectangular,network.getEfficientVoltagePublicationCount()>0);
        return trace;
    }
}
