package org.interpss.core.dstab;

import static org.junit.jupiter.api.Assertions.*;
import java.nio.file.*;
import java.util.*;
import org.interpss.IpssCorePlugin;
import org.interpss.fadapter.psse.PSSEMultiFileLoader;
import org.interpss.dstab.algo.PsseStaggeredEulerSolver;
import com.interpss.dstab.mach.model.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.api.Test;

/** Type-1 electrical, shaft and aerodynamic coordinates share one physical interval. */
class StaggeredWt1SupportTest {
    private com.interpss.dstab.algo.DynamicSimuAlgorithm load(String family,boolean fault,Path dir)throws Exception {
        IpssCorePlugin.init();IpssCorePlugin.configureSparseSolver("java-klu");var root=Path.of("testData/adpter/psse/v33/SMIB");
        var dyr=root.resolve("SMIB_v33_"+family+".dyr");
        if(family.equals("ramp")) {
            dyr=dir.resolve("ramp.dyr");Files.writeString(dyr,Files.readString(root.resolve("SMIB_v33_wt12t1.dyr"))+"\n1 'WT12A1B' '1' .02 .4 -.6 .2 .1 .3 .3 .3 .6 .2 .8 .1 .95 .05 /\n");
        }
        var c=new PSSEMultiFileLoader().loadDStab(root.resolve("SMIB_v33_wt1g1.raw").toString(),dyr.toString());
        var a=c.getDynSimuAlgorithm();assertTrue(a.getAclfAlgorithm().loadflow());a.setSimuStepSec(1./240);
        a.setSimuOutputHandler(new com.interpss.dstab.cache.StateMonitor());
        if(fault)a.getNetwork().addDynamicEvent(com.interpss.dstab.DStabObjectFactory.createBusFaultEvent("Bus1",a.getNetwork(),
                com.interpss.core.acsc.fault.SimpleFaultCode.GROUND_3P,new org.apache.commons.math3.complex.Complex(0,.1),null,.1,.05),"wind1-fault");
        a.setSolver(new PsseStaggeredEulerSolver(a));assertTrue(a.initialization());return a;
    }
    private void history(Object model,String prefix,Map<String,Object> result)throws Exception {
        if(model==null)return;
        for(var f:model.getClass().getDeclaredFields())if(Set.of("oldState","oldDerivative","oldRates","pendingStep","oldRotorFlux1","oldRotorFlux2","derivative1","derivative2").contains(f.getName())){
            f.setAccessible(true);result.put(prefix+f.getName(),f.get(model));
        }
        if(model instanceof Wt1g1Machine m){var f=Wt1g1Machine.class.getDeclaredField("solver");f.setAccessible(true);history(f.get(model),"flux/",result);history(m.getDriveTrain(),"shaft/",result);}
        if(model instanceof Wt12t1Model m)history(m.getAerodynamicController(),"aero/",result);
    }
    @ParameterizedTest @ValueSource(strings={"wt1g1","wt12t1","wt12a1","ramp"})
    void heldPhysicalInputsAndPureRatesPreserveEveryNativeHistory(String family,@TempDir Path dir)throws Exception {
        var a=load(family,false,dir);var p=(Wt1g1Machine)a.getNetwork().getMachine("Bus1-mach1");
        var bus=p.getDStabBus();double v=bus.getVoltageMag(),angle=bus.getVoltageAng();bus.setVoltage(v-.01,angle);p.beginStaggeredStep(1./240);
        double[] x=new double[p.staggeredStateCount()],r=x.clone(),observed=x.clone();p.readStaggeredState(x,0);
        var before=new TreeMap<String,Object>();var after=new TreeMap<String,Object>();history(p,"",before);p.evaluateStaggered(1./240,r,0);
        bus.setVoltage(v-.03,angle);p.evaluateStaggered(1./240,observed,0);assertArrayEquals(r,observed,0.);history(p,"",after);assertEquals(before,after);
        p.readStaggeredState(observed,0);assertArrayEquals(x,observed,0.);
        var invalid=x.clone();invalid[invalid.length-1]=Double.NaN;assertThrows(IllegalArgumentException.class,()->p.applyStaggeredState(invalid,0,1./240));
        p.readStaggeredState(observed,0);assertArrayEquals(x,observed,0.);
    }
    @ParameterizedTest @ValueSource(strings={"wt1g1","wt12t1","wt12a1","ramp"})
    void completeGroupsRunAnActualQuarterCycleFault(String family,@TempDir Path dir)throws Exception {
        var a=load(family,true,dir);var p=(Wt1g1Machine)a.getNetwork().getMachine("Bus1-mach1");
        double initialV=p.getDStabBus().getVoltageMag(),minimum=initialV,speed=p.getSpeed();
        for(int i=0;i<240;i++){assertTrue(a.solveDEqnStep(true));minimum=Math.min(minimum,p.getDStabBus().getVoltageMag());}
        assertTrue(minimum<initialV-.05);assertTrue(p.isStaggeredOperational());
        if(p.getDriveTrain()==null)assertEquals(speed,p.getSpeed(),0.);
        else assertEquals(p.getDriveTrain().getGeneratorSpeed(),p.getSpeed(),0.);
        double[] x=new double[p.staggeredStateCount()];p.readStaggeredState(x,0);for(double value:x)assertTrue(Double.isFinite(value));
    }
    @Test void aerodynamicRampClockAdvancesOncePerPhysicalInterval() {
        var model=new Wt12a1bModel(new Wt12a1bData(0,0,2,-1,.2,.2,.5,.4,.3,.6,.2,.8,.1,.95,.05));
        model.initialize(.8,0.,.8,1.);model.setStaggeredInputs(.8,0.,.5);model.beginStaggeredStep(.01);
        double before=model.getRemainingTime();assertTrue(before>0.);double[] rates=new double[3];
        for(int i=0;i<20;i++)model.evaluateStaggered(.0025,rates,0);
        assertEquals(before,model.getRemainingTime(),0.);model.completeStaggeredStep(.01);
        assertEquals(before-.01,model.getRemainingTime(),1e-12);
    }
}
