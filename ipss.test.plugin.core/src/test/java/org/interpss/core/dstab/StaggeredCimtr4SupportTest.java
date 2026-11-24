package org.interpss.core.dstab;

import static org.junit.jupiter.api.Assertions.*;
import java.nio.file.*;
import java.util.*;
import org.interpss.IpssCorePlugin;
import org.interpss.fadapter.psse.PSSEMultiFileLoader;
import org.interpss.dstab.algo.PsseStaggeredEulerSolver;
import com.interpss.dstab.mach.model.Cimtr4Machine;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.junit.jupiter.api.io.TempDir;

/** Induction flux and mechanics share one state boundary without altering native histories. */
class StaggeredCimtr4SupportTest {
    private com.interpss.dstab.algo.DynamicSimuAlgorithm load(boolean twoCage,boolean staggered,boolean fault,Path dir)throws Exception {
        IpssCorePlugin.init();IpssCorePlugin.configureSparseSolver("java-klu");var root=Path.of("testData/adpter/psse/v33/SMIB");
        var dyr=root.resolve("SMIB_v33_cimtr4.dyr");
        if(!twoCage){var text=Files.readString(dyr).replace("0.42 0.028","0.42 0.0");dyr=dir.resolve("single-cage.dyr");Files.writeString(dyr,text);}
        var c=new PSSEMultiFileLoader().loadDStab(root.resolve("SMIB_v33_cimtr4.raw").toString(),dyr.toString());
        var a=c.getDynSimuAlgorithm();assertTrue(a.getAclfAlgorithm().loadflow());a.setSimuStepSec(1./240);
        a.setSimuOutputHandler(new com.interpss.dstab.cache.StateMonitor());
        if(fault)a.getNetwork().addDynamicEvent(com.interpss.dstab.DStabObjectFactory.createBusFaultEvent("Bus1",a.getNetwork(),
                com.interpss.core.acsc.fault.SimpleFaultCode.GROUND_3P,new org.apache.commons.math3.complex.Complex(0,.1),null,.1,.05),"induction-fault");
        if(staggered)a.setSolver(new PsseStaggeredEulerSolver(a));assertTrue(a.initialization());return a;
    }
    private Map<String,Object> history(Cimtr4Machine model)throws Exception {
        var result=new TreeMap<String,Object>();
        for(var f:Cimtr4Machine.class.getDeclaredFields())if(Set.of("oldSpeed","oldAngle","speedDerivative","angleDerivative","solver").contains(f.getName())){
            f.setAccessible(true);Object value=f.get(model);
            if(f.getName().equals("solver")) {
                for(var sf:value.getClass().getDeclaredFields())if(sf.getName().startsWith("oldRotor")||sf.getName().startsWith("derivative")) {
                    sf.setAccessible(true);result.put(sf.getName(),sf.get(value));
                }
            } else result.put(f.getName(),value);
        }return result;
    }
    @ParameterizedTest @ValueSource(booleans={false,true})
    void heldRatesPreserveFluxAndMechanicalHistories(boolean twoCage,@TempDir Path dir)throws Exception {
        var a=load(twoCage,true,false,dir);var p=(Cimtr4Machine)a.getNetwork().getMachine("Bus1-mach1");
        assertEquals(twoCage?6:4,p.staggeredStateCount());var bus=p.getDStabBus();double v=bus.getVoltageMag(),ang=bus.getVoltageAng();
        bus.setVoltage(v-.01,ang);p.beginStaggeredStep(1./240);double[] x=new double[p.staggeredStateCount()],r=x.clone(),observed=x.clone();
        p.readStaggeredState(x,0);var before=history(p);p.evaluateStaggered(1./240,r,0);
        bus.setVoltage(v-.03,ang);p.evaluateStaggered(1./240,observed,0);assertArrayEquals(r,observed,0.);assertEquals(before,history(p));
        p.readStaggeredState(observed,0);assertArrayEquals(x,observed,0.);
        var invalid=x.clone();invalid[invalid.length-1]=Double.NaN;assertThrows(IllegalArgumentException.class,()->p.applyStaggeredState(invalid,0,1./240));
        p.readStaggeredState(observed,0);assertArrayEquals(x,observed,0.);
    }
    @ParameterizedTest @ValueSource(booleans={false,true})
    void coupledQuarterCycleFaultAdvancesBothFluxAndMechanics(boolean twoCage,@TempDir Path dir)throws Exception {
        var a=load(twoCage,true,true,dir);var p=(Cimtr4Machine)a.getNetwork().getMachine("Bus1-mach1");
        double initialV=p.getDStabBus().getVoltageMag(),minimum=initialV,speed=p.getSpeed(),maximumSpeedChange=0.;
        for(int i=0;i<240;i++){
            assertTrue(a.solveDEqnStep(true));minimum=Math.min(minimum,p.getDStabBus().getVoltageMag());maximumSpeedChange=Math.max(maximumSpeedChange,Math.abs(p.getSpeed()-speed));
        }
        assertTrue(minimum<initialV-.05);assertTrue(maximumSpeedChange>1e-5);assertTrue(p.isStaggeredOperational());
        double[] x=new double[p.staggeredStateCount()];p.readStaggeredState(x,0);for(double value:x)assertTrue(Double.isFinite(value));
    }
}
