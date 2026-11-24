package org.interpss.core.dstab;

import static org.junit.jupiter.api.Assertions.*;
import java.nio.file.Path;
import org.interpss.IpssCorePlugin;
import org.interpss.fadapter.psse.PSSEMultiFileLoader;
import org.interpss.dstab.algo.PsseStaggeredEulerSolver;
import com.interpss.dstab.svc.Csvgn5Model;
import org.junit.jupiter.api.Test;

/** Generator-attached SVCs retain their physical current law and state limits. */
class StaggeredCsvgn5SupportTest {
    private com.interpss.dstab.algo.DynamicSimuAlgorithm load(boolean staggered)throws Exception {
        IpssCorePlugin.init();IpssCorePlugin.configureSparseSolver("java-klu");
        var root=Path.of("testData/adpter/psse/v33/SMIB");
        var c=new PSSEMultiFileLoader().loadDStab(root.resolve("SMIB_v33_csvgn5.raw").toString(),root.resolve("SMIB_v33_csvgn5.dyr").toString());
        var a=c.getDynSimuAlgorithm();a.getAclfAlgorithm().loadflow();
        var residual=a.getNetwork().maxMismatch(com.interpss.core.algo.AclfMethodType.NR).maxMis;
        assertTrue(Math.abs(residual.getReal())<1e-5&&Math.abs(residual.getImaginary())<1e-5);a.getNetwork().setLfConverged(true);
        a.setSimuStepSec(1./240);a.setSimuOutputHandler(new com.interpss.dstab.cache.StateMonitor());
        if(staggered)a.setSolver(new PsseStaggeredEulerSolver(a));assertTrue(a.initialization());return a;
    }
    private Csvgn5Model model(com.interpss.dstab.algo.DynamicSimuAlgorithm a){
        return (Csvgn5Model)((com.interpss.dstab.DStabGen)a.getNetwork().getDStabBus("Bus1").getContributeGen("1")).getDynamicGenDevice();
    }
    @Test void ratesArePureHeldAndMatchTheExistingPredictor()throws Exception {
        var a=load(true);var b=load(false);var p=model(a);var q=model(b);
        p.setOtherSignal(.001);q.setOtherSignal(.001);p.beginStaggeredStep(1./240);
        double[] x=new double[4],r=new double[4],observed=new double[4];p.readStaggeredState(x,0);
        var start=Csvgn5Model.class.getDeclaredField("predictorStart");start.setAccessible(true);Object history=start.get(p);
        p.evaluateStaggered(1./240,r,0);double v=p.getRemoteBus().getVoltageMag(),ang=p.getRemoteBus().getVoltageAng();
        p.getRemoteBus().setVoltage(v-.01,ang);p.setOtherSignal(.003);p.evaluateStaggered(1./240,observed,0);
        assertArrayEquals(r,observed,0.);assertSame(history,start.get(p));p.readStaggeredState(observed,0);assertArrayEquals(x,observed,0.);
        double h=1e-7;assertTrue(q.nextStep(h,com.interpss.dstab.algo.DynamicSimuMethod.MODIFIED_EULER,0));q.readStaggeredState(observed,0);
        for(int i=0;i<4;i++)assertEquals(x[i]+h*r[i],observed[i],1e-10);
        double[] invalid=x.clone();invalid[3]=Double.NaN;assertThrows(IllegalArgumentException.class,()->p.applyStaggeredState(invalid,0,h));
        p.readStaggeredState(observed,0);assertArrayEquals(x,observed,0.);
    }
    @Test void coupledQuarterCycleRetainsEquilibriumAndPhysicalSusceptanceLimits()throws Exception {
        var a=load(true);var p=model(a);double initial=p.getSystemBaseSusceptance();
        for(int i=0;i<120;i++)assertTrue(a.solveDEqnStep(true));
        assertEquals(initial,p.getSystemBaseSusceptance(),1e-7);assertTrue(p.isStaggeredOperational());
        p.setOtherSignal(.01);((PsseStaggeredEulerSolver)a.getSolver()).notifyDiscontinuity();
        for(int i=0;i<240;i++)assertTrue(a.solveDEqnStep(true));
        assertTrue(p.getThyristorDelay()>=p.getData().bmin());assertTrue(p.getThyristorDelay()<=p.getData().bmax());
        assertTrue(p.getSystemBaseSusceptance()>initial);assertTrue(Double.isFinite(((org.apache.commons.math3.complex.Complex)p.getOutputObject()).abs()));
    }
}
