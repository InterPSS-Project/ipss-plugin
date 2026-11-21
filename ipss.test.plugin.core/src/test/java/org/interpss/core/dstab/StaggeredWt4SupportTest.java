package org.interpss.core.dstab;

import static org.junit.jupiter.api.Assertions.*;
import java.nio.file.Path;
import java.util.*;
import org.interpss.IpssCorePlugin;
import org.interpss.fadapter.psse.PSSEMultiFileLoader;
import org.interpss.dstab.algo.PsseStaggeredEulerSolver;
import com.interpss.dstab.mach.model.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/** Type-4 generator/controller groups preserve their original state and input boundaries. */
class StaggeredWt4SupportTest {
    private com.interpss.dstab.algo.DynamicSimuAlgorithm load(boolean controller,boolean staggered,boolean fault)throws Exception {
        IpssCorePlugin.init();IpssCorePlugin.configureSparseSolver("java-klu");var root=Path.of("testData/adpter/psse/v33/SMIB");
        var c=new PSSEMultiFileLoader().loadDStab(root.resolve("SMIB_v33_wt3g2.raw").toString(),root.resolve(controller?"SMIB_v33_wt4e1.dyr":"SMIB_v33_wt4g1.dyr").toString());
        var a=c.getDynSimuAlgorithm();a.getAclfAlgorithm().getDataCheckConfig().setAllowGenWithoutMachine(true);assertTrue(a.getAclfAlgorithm().loadflow());
        a.setSimuStepSec(1./240);a.setSimuOutputHandler(new com.interpss.dstab.cache.StateMonitor());
        if(fault)a.getNetwork().addDynamicEvent(com.interpss.dstab.DStabObjectFactory.createBusFaultEvent("Bus1",a.getNetwork(),
                com.interpss.core.acsc.fault.SimpleFaultCode.GROUND_3P,new org.apache.commons.math3.complex.Complex(0,1.),null,.1,.05),"type4-fault");
        if(staggered)a.setSolver(new PsseStaggeredEulerSolver(a));assertTrue(a.initialization());return a;
    }
    private Wt4g1Model model(com.interpss.dstab.algo.DynamicSimuAlgorithm a){return (Wt4g1Model)((com.interpss.dstab.DStabGen)a.getNetwork().getDStabBus("Bus1").getContributeGen("1")).getDynamicGenDevice();}
    private Map<String,Object> history(Object model)throws Exception {
        var result=new TreeMap<String,Object>();for(var f:model.getClass().getDeclaredFields())if(Set.of("oldState","predictor").contains(f.getName())){
            f.setAccessible(true);result.put(f.getName(),f.get(model));
        }return result;
    }
    @ParameterizedTest @ValueSource(booleans={false,true})
    void ratesHoldPhysicalInputsAndPreserveBothPredictorHistories(boolean controller)throws Exception {
        var a=load(controller,true,false);var p=model(a);assertEquals(controller?13:3,p.staggeredStateCount());
        var bus=p.getDStabBus();double v=bus.getVoltageMag(),angle=bus.getVoltageAng();bus.setVoltage(v-.01,angle);p.beginStaggeredStep(1./240);
        double[] x=new double[p.staggeredStateCount()],r=x.clone(),observed=x.clone();p.readStaggeredState(x,0);
        var gHistory=history(p);var eHistory=controller?history(p.getElectricalController()):Map.of();
        p.evaluateStaggered(1./240,r,0);bus.setVoltage(v-.04,angle);p.evaluateStaggered(1./240,observed,0);
        assertArrayEquals(r,observed,0.);assertEquals(gHistory,history(p));if(controller)assertEquals(eHistory,history(p.getElectricalController()));
        p.readStaggeredState(observed,0);assertArrayEquals(x,observed,0.);
        var invalid=x.clone();invalid[invalid.length-1]=Double.NaN;assertThrows(IllegalArgumentException.class,()->p.applyStaggeredState(invalid,0,1./240));
        p.readStaggeredState(observed,0);assertArrayEquals(x,observed,0.);
    }
    @ParameterizedTest @ValueSource(booleans={false,true})
    void coupledQuarterCycleFaultActuallyExecutesAndAllCoordinatesStayFinite(boolean controller)throws Exception {
        var a=load(controller,true,true);var p=model(a);double initialV=p.getDStabBus().getVoltageMag(),minimum=initialV;
        for(int i=0;i<240;i++){
            assertTrue(a.solveDEqnStep(true));minimum=Math.min(minimum,p.getDStabBus().getVoltageMag());
            var terminal=p.observeTerminalPower(p.getDStabBus().getVoltage());
            double system=p.getDStabBus().getNetwork().getBaseMva();
            double device=p.getParentGen().getMvaBase()>0?p.getParentGen().getMvaBase():system;
            assertEquals(terminal.getReal()*system/device,p.getP(),1e-12,"Fresh terminal MW at each accepted endpoint");
            assertEquals(terminal.getImaginary()*system/device,p.getQ(),1e-12,"Fresh terminal Mvar at each accepted endpoint");
        }
        assertTrue(minimum<initialV-.05);assertTrue(p.isStaggeredOperational());
        double[] x=new double[p.staggeredStateCount()];p.readStaggeredState(x,0);for(double value:x)assertTrue(Double.isFinite(value));
        assertTrue(p.getActiveCurrentState()<=p.lowVoltagePowerLimit(p.getFilteredVoltageState())+1e-12);
    }
}
