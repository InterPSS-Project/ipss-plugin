package org.interpss.core.dstab;

import static org.junit.jupiter.api.Assertions.*;
import java.nio.file.Path;
import org.interpss.IpssCorePlugin;
import org.interpss.fadapter.psse.PSSEMultiFileLoader;
import org.interpss.dstab.algo.PsseStaggeredEulerSolver;
import com.interpss.dstab.renewable.Dera1Model;
import org.junit.jupiter.api.Test;

/** Aggregate converter coordinates and accepted protection histories have separate owners. */
class StaggeredDera1SupportTest {
    private com.interpss.dstab.algo.DynamicSimuAlgorithm load(boolean staggered)throws Exception {
        IpssCorePlugin.init();IpssCorePlugin.configureSparseSolver("java-klu");
        var root=Path.of("testData/adpter/psse/v33/SMIB");
        var c=new PSSEMultiFileLoader().loadDStab(root.resolve("SMIB_v33_wt3g2.raw").toString(),root.resolve("SMIB_v33_dera1.dyr").toString());
        var a=c.getDynSimuAlgorithm();a.getAclfAlgorithm().getDataCheckConfig().setAllowGenWithoutMachine(true);
        assertTrue(a.getAclfAlgorithm().loadflow());a.setSimuStepSec(1./240);
        a.setSimuOutputHandler(new com.interpss.dstab.cache.StateMonitor());
        if(staggered)a.setSolver(new PsseStaggeredEulerSolver(a));assertTrue(a.initialization());return a;
    }
    private Dera1Model model(com.interpss.dstab.algo.DynamicSimuAlgorithm a){
        return (Dera1Model)((com.interpss.dstab.DStabGen)a.getNetwork().getDStabBus("Bus1").getContributeGen("1")).getDynamicGenDevice();
    }
    @Test void ratesHoldSignalsPreservePredictorAndUseTheExistingEquations()throws Exception {
        var a=load(true);var b=load(false);var p=model(a);var q=model(b);
        var bus=p.getDStabBus();double v=bus.getVoltageMag(),ang=bus.getVoltageAng();bus.setVoltage(v-.01,ang);
        q.getDStabBus().setVoltage(v-.01,ang);p.beginStaggeredStep(1./240);
        double[] x=new double[10],r=new double[10],observed=new double[10];p.readStaggeredState(x,0);
        var field=Dera1Model.class.getDeclaredField("oldDerivative");field.setAccessible(true);double[] before=((double[])field.get(p)).clone();
        p.evaluateStaggered(1./240,r,0);bus.setVoltage(v-.02,ang);p.evaluateStaggered(1./240,observed,0);
        assertArrayEquals(r,observed,0.);assertArrayEquals(before,(double[])field.get(p),0.);
        p.readStaggeredState(observed,0);assertArrayEquals(x,observed,0.);
        double h=1e-7;assertTrue(q.nextStep(h,com.interpss.dstab.algo.DynamicSimuMethod.MODIFIED_EULER,0));
        observed=q.getStateSnapshot();for(int i=0;i<10;i++)assertEquals(x[i]+h*r[i],observed[i],1e-10);
        double[] invalid=x.clone();invalid[9]=Double.NaN;assertThrows(IllegalArgumentException.class,()->p.applyStaggeredState(invalid,0,h));
        p.readStaggeredState(observed,0);assertArrayEquals(x,observed,0.);
    }
    @Test void protectionTimersAdvanceOnlyAtTheAcceptedCallback()throws Exception {
        var a=load(true);var p=model(a);double[] x=new double[10],r=new double[10];p.readStaggeredState(x,0);
        x[0]=p.getData().vl0()-.01;p.applyStaggeredState(x,0,1./240);
        var timer=Dera1Model.class.getDeclaredField("lowTimer");timer.setAccessible(true);
        var latch=Dera1Model.class.getDeclaredField("lowTripLatched");latch.setAccessible(true);
        for(int i=0;i<20;i++)p.evaluateStaggered(1./240,r,0);
        assertEquals(0.,timer.getDouble(p),0.);assertFalse(latch.getBoolean(p));
        double half=p.getData().tvl0()/2.;assertTrue(half>0.);assertTrue(p.afterStep(half));
        assertEquals(half,timer.getDouble(p),1e-12);assertFalse(latch.getBoolean(p));
        for(int i=0;i<20;i++)p.evaluateStaggered(1./240,r,0);assertEquals(half,timer.getDouble(p),0.);
        assertTrue(p.afterStep(half));assertTrue(latch.getBoolean(p));
    }
    @Test void coupledQuarterCycleDeviceRetainsEquilibriumAndRespondsToAFault()throws Exception {
        var a=load(true);var p=model(a);double[] before=p.getStateSnapshot();
        for(int i=0;i<120;i++)assertTrue(a.solveDEqnStep(true));
        assertArrayEquals(before,p.getStateSnapshot(),1e-7);assertTrue(p.isStaggeredOperational());
        var net=a.getNetwork();net.addDynamicEvent(com.interpss.dstab.DStabObjectFactory.createBusFaultEvent(
                "Bus1",net,com.interpss.core.acsc.fault.SimpleFaultCode.GROUND_3P,
                new org.apache.commons.math3.complex.Complex(0.,.1),null,.55,.05),"aggregate-fault");
        double minimumVoltage=p.getDStabBus().getVoltageMag();
        for(int i=0;i<240;i++) {
            assertTrue(a.solveDEqnStep(true));minimumVoltage=Math.min(minimumVoltage,p.getDStabBus().getVoltageMag());
        }
        assertTrue(minimumVoltage<before[0]-.05,"The coupled test must actually execute the scheduled fault");
        for(double value:p.getStateSnapshot())assertTrue(Double.isFinite(value));
        assertTrue(Double.isFinite(p.getP()));assertTrue(Double.isFinite(p.getQ()));
    }
}
