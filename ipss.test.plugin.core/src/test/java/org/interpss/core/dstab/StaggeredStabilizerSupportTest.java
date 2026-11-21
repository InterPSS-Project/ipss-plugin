package org.interpss.core.dstab;

import static org.junit.jupiter.api.Assertions.*;
import java.nio.file.Path;
import java.util.*;
import com.interpss.dstab.controller.cml.annotate.AbstractAnnotateController;
import com.interpss.dstab.controller.cml.field.adapt.CMLControlBlock1stOrderAdapter;
import com.interpss.dstab.controller.pss.ieee1992pss2a.Pss2aLeadLagBlock;
import com.interpss.dstab.controller.runtime.PreparedStaggeredController;
import org.interpss.IpssCorePlugin;
import org.interpss.fadapter.psse.PSSEMultiFileLoader;
import org.interpss.dstab.algo.PsseStaggeredEulerSolver;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/** Explicit stabilizer coordinates retain CML predictor and accepted measurement histories. */
class StaggeredStabilizerSupportTest {
    private com.interpss.dstab.algo.DynamicSimuAlgorithm load(String family,boolean staggered)throws Exception {
        return load(family,staggered,model->{});
    }
    private com.interpss.dstab.algo.DynamicSimuAlgorithm load(String family,boolean staggered,
            java.util.function.Consumer<AbstractAnnotateController> configure)throws Exception {
        IpssCorePlugin.init();IpssCorePlugin.configureSparseSolver("java-klu");
        Path root=Path.of("testData/adpter/psse/v33/SMIB");
        var c=new PSSEMultiFileLoader().loadDStab(root.resolve("SMIB_v33.raw").toString(),
                root.resolve("SMIB_v33_genrou_esst1a_"+family.toLowerCase(Locale.ROOT)+".dyr").toString());
        var a=c.getDynSimuAlgorithm();assertTrue(a.getAclfAlgorithm().loadflow());
        a.setSimuStepSec(1./240);a.setSimuOutputHandler(new com.interpss.dstab.cache.StateMonitor());
        configure.accept((AbstractAnnotateController)a.getNetwork().getMachine("Bus1-mach1").getStabilizer());
        if(staggered)a.setSolver(new PsseStaggeredEulerSolver(a));assertTrue(a.initialization());return a;
    }
    private void blockHistory(AbstractAnnotateController c,String prefix,Map<String,Object> result)throws Exception {
        for(var f:c.getFieldWrapperList()) {
            Object block=f.getField();String key=prefix+f.getFieldName();
            if(block instanceof AbstractAnnotateController child)blockHistory(child,key+"/",result);
            else if(block instanceof CMLControlBlock1stOrderAdapter first) {
                result.put(key+"/x",first.getStateX());result.put(key+"/dx",first.getDX_dt());
            } else if(block instanceof Pss2aLeadLagBlock lead) {
                result.put(key+"/x",lead.getStateX());result.put(key+"/dx",lead.getStateDerivative());
            }
        }
        for(var f:c.getClass().getDeclaredFields())if(f.getName().contains("PreviousVoltage")
                ||f.getName().startsWith("pgenFilter")||f.getName().startsWith("compensatedFrequency")
                ||f.getName().equals("previousCompensatedAngle")||f.getName().equals("pssActive")) {
            f.setAccessible(true);result.put(prefix+f.getName(),f.get(c));
        }
    }
    @ParameterizedTest @ValueSource(strings={"ST2CUT","PSS2C"})
    void pureRatesHoldPhysicalInputsAndPreserveAllNativeHistories(String family)throws Exception {
        var a=load(family,true);var m=a.getNetwork().getMachine("Bus1-mach1");
        var p=(PreparedStaggeredController)m.getStabilizer();var c=(AbstractAnnotateController)p;
        double speed=m.getSpeed(),pe=m.getPe();m.setSpeed(speed+.001);
        p.beginStaggeredStep(1./240);
        double[] r=new double[p.staggeredStateCount()],observed=r.clone(),x=r.clone();p.readStaggeredState(x,0);
        Map<String,Object> before=new TreeMap<>(),after=new TreeMap<>();blockHistory(c,"",before);
        p.evaluateStaggered(1./240,r,0);m.setSpeed(speed+.003);m.setPe(pe+.1);
        p.evaluateStaggered(1./240,observed,0);assertArrayEquals(r,observed,0.);
        blockHistory(c,"",after);assertEquals(before,after,"Rate probes cannot replace CML or sampled-input histories");
        p.readStaggeredState(observed,0);assertArrayEquals(x,observed,0.);
        double[] invalid=x.clone();invalid[invalid.length-1]=Double.NaN;
        assertThrows(IllegalArgumentException.class,()->p.applyStaggeredState(invalid,0,1./240));
        p.readStaggeredState(observed,0);assertArrayEquals(x,observed,0.);
        p.completeStaggeredStep(1./240);m.setSpeed(speed);m.setPe(pe);
    }
    @ParameterizedTest @ValueSource(strings={"ST2CUT","PSS2C"})
    void existingEulerPredictorAndExplicitRhsUseTheSameEquations(String family)throws Exception {
        var a=load(family,true);var b=load(family,false);
        var m=a.getNetwork().getMachine("Bus1-mach1");var n=b.getNetwork().getMachine("Bus1-mach1");
        var p=(PreparedStaggeredController)m.getStabilizer();var q=(PreparedStaggeredController)n.getStabilizer();
        m.setSpeed(m.getSpeed()+.001);n.setSpeed(n.getSpeed()+.001);
        int size=p.staggeredStateCount();double[] x=new double[size],r=x.clone(),expected=x.clone(),actual=x.clone();
        p.beginStaggeredStep(1./240);p.readStaggeredState(x,0);p.evaluateStaggered(1./240,r,0);
        double h=1e-7;for(int i=0;i<size;i++)expected[i]=x[i]+h*r[i];
        assertTrue(n.getStabilizer().nextStep(h,com.interpss.dstab.algo.DynamicSimuMethod.MODIFIED_EULER,n,0));
        q.readStaggeredState(actual,0);assertArrayEquals(expected,actual,1e-10,family+" existing differential equations");
    }
    @ParameterizedTest @ValueSource(strings={"ST2CUT","PSS2C"})
    void coupledQuarterCycleStabilizersStayActiveAndRespondToSpeedChange(String family)throws Exception {
        var a=load(family,true);var m=a.getNetwork().getMachine("Bus1-mach1");
        var p=(PreparedStaggeredController)m.getStabilizer();
        for(int i=0;i<120;i++)assertTrue(a.solveDEqnStep(true));
        double initial=m.getStabilizer().getOutput(m);m.setSpeed(m.getSpeed()+.002);
        ((PsseStaggeredEulerSolver)a.getSolver()).notifyDiscontinuity();
        double maximum=0;
        for(int i=0;i<240;i++) {
            assertTrue(a.solveDEqnStep(true));maximum=Math.max(maximum,Math.abs(m.getStabilizer().getOutput(m)-initial));
        }
        assertTrue(m.getStabilizer().isActive());assertTrue(maximum>1e-6);
        double[] x=new double[p.staggeredStateCount()];p.readStaggeredState(x,0);for(double value:x)assertTrue(Double.isFinite(value));
    }
    @org.junit.jupiter.api.Test
    void sampledVoltageDerivativeUsesOnePhysicalIntervalAndDoesNotConsumeProbes()throws Exception {
        var a=load("PSS2C",true,model->{
            var d=((com.interpss.dstab.controller.pss.ieee2016pss2c.Ieee2016PSS2CStabilizer)model).getData();
            d.setIcs1(6);d.setIcs2(1);
        });
        var m=a.getNetwork().getMachine("Bus1-mach1");
        var p=(com.interpss.dstab.controller.pss.ieee2016pss2c.Ieee2016PSS2CStabilizer)m.getStabilizer();
        var bus=m.getDStabBus();double v=bus.getVoltageMag(),angle=bus.getVoltageAng();
        bus.setVoltage(v-.005,angle);p.beginStaggeredStep(1./240);
        double[] rates=new double[p.staggeredStateCount()],again=rates.clone();
        p.evaluateStaggered(1./240,rates,0);assertEquals(-1.2,p.input1Signal,1e-10);
        p.evaluateStaggered(1./240,again,0);assertArrayEquals(rates,again,0.);
        p.completeStaggeredStep(1./240);bus.setVoltage(v-.0025,angle);
        p.evaluateStaggered(1./240,again,0);assertEquals(.6,p.input1Signal,1e-10);
        p.acceptStaggeredEndpoint(1./240,1./240);p.beginStaggeredStep(1./240);
        p.evaluateStaggered(1./240,again,0);assertEquals(.6,p.input1Signal,1e-10);
    }
    @org.junit.jupiter.api.Test
    void compensatedFrequencyAndPowerFiltersIntegrateAsCoordinatesAndGateCommitsOnce()throws Exception {
        var a=load("PSS2C",true,model->{
            var d=((com.interpss.dstab.controller.pss.ieee2016pss2c.Ieee2016PSS2CStabilizer)model).getData();
            d.setIcs1(7);d.setTcomp(.03);d.setTpgfilt(.05);d.setXcomp(.1);
        });
        var m=a.getNetwork().getMachine("Bus1-mach1");
        var p=(com.interpss.dstab.controller.pss.ieee2016pss2c.Ieee2016PSS2CStabilizer)m.getStabilizer();
        for(int i=0;i<120;i++)assertTrue(a.solveDEqnStep(true));
        double[] x=new double[p.staggeredStateCount()];p.readStaggeredState(x,0);
        for(double value:x)assertTrue(Double.isFinite(value));
        long revision=p.acceptedStaggeredDiscontinuityVersion();assertTrue(p.isPssActive());
        p.getData().setPssDeactivation(10.);p.getData().setPssActivation(11.);
        p.evaluateStaggered(1./240,x,0);assertTrue(p.isPssActive());assertEquals(revision,p.acceptedStaggeredDiscontinuityVersion());
        p.acceptStaggeredEndpoint(1./240,a.getSimuTime());assertFalse(p.isPssActive());
        assertEquals(revision+1,p.acceptedStaggeredDiscontinuityVersion());
        p.acceptStaggeredEndpoint(1./240,a.getSimuTime());assertEquals(revision+1,p.acceptedStaggeredDiscontinuityVersion());
    }
}
