package org.interpss.core.dstab.mach;

import static org.junit.jupiter.api.Assertions.*;
import java.util.*;
import org.interpss.IpssCorePlugin;
import org.interpss.fadapter.psse.PSSEMultiFileLoader;
import com.interpss.dstab.controller.runtime.StaggeredMachineElectricalParticipant;
import com.interpss.dstab.mach.impl.DynamicMachineImpl;
import com.interpss.dstab.algo.DynamicSimuMethod;
import com.interpss.dstab.cache.StateMonitor;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/** Custom electrical coordinates retain their own equations and native staged history. */
class CustomMachineStaggeredStateTest {
    @ParameterizedTest @ValueSource(strings={"genqecu","gentpj1","genqec_independent"})
    void repeatedRhsAndStatePublicationLeaveNativePredictorHistoryUntouched(String family)throws Exception {
        var m=load(family);var p=(StaggeredMachineElectricalParticipant)m;
        m.getDStabBus().setVoltage(m.getDStabBus().getVoltage().multiply(.97));m.getIgen();
        var history=scratch(m);var mechanical=m.captureMechanicalIntegrationState();
        double[] x=new double[4],r=new double[4],again=new double[4],after=new double[4];p.readStaggeredState(x,0);
        p.evaluateStaggered(.001,r,0);p.evaluateStaggered(.001,again,0);p.readStaggeredState(after,0);
        assertArrayEquals(x,after,1e-12);assertArrayEquals(r,again,1e-10);
        var trial=x.clone();trial[0]+=.001;p.applyStaggeredState(trial,0,.001);p.applyStaggeredState(x,0,.001);
        assertEquals(history,scratch(m));assertEquals(mechanical,m.captureMechanicalIntegrationState());
        var invalid=x.clone();invalid[3]=Double.NaN;assertThrows(IllegalArgumentException.class,()->p.applyStaggeredState(invalid,0,.001));
        p.readStaggeredState(after,0);assertArrayEquals(x,after,1e-12);
    }
    @ParameterizedTest @ValueSource(strings={"genqecu","gentpj1","genqec_independent"})
    void explicitRatesAgreeWithTheExistingElectricalEulerPredictor(String family)throws Exception {
        var m=load(family);var n=load(family);double h=1e-7;
        for(var machine:List.of(m,n)){machine.getDStabBus().setVoltage(machine.getDStabBus().getVoltage().multiply(.97));machine.getIgen();}
        var p=(StaggeredMachineElectricalParticipant)m;var q=(StaggeredMachineElectricalParticipant)n;
        double[] x=new double[4],r=new double[4],actual=new double[4];p.readStaggeredState(x,0);p.evaluateStaggered(h,r,0);
        for(int i=0;i<4;i++)x[i]+=h*r[i];p.applyStaggeredState(x,0,h);
        assertTrue(n.nextStepElectrical(h,DynamicSimuMethod.MODIFIED_EULER,n.getDStabBus().getNetwork(),0));
        q.readStaggeredState(actual,0);p.readStaggeredState(x,0);assertArrayEquals(actual,x,1e-8);
    }
    @ParameterizedTest @ValueSource(strings={"genqecu","gentpj1","genqec_independent"})
    void fastElectricalPoleUsesLocalResolutionAndPreservesNativeHistory(String family)throws Exception {
        double h=1./240;
        var a=loadAlgorithm(family,true,h,true);var m=(DynamicMachineImpl)a.getNetwork().getMachine("Bus1-mach1");
        var solver=(org.interpss.dstab.algo.StaggeredEulerSolver)a.getSolver();
        int count=solver.getResolvedMachineElectricalSubsteps().get(m.getId());
        assertTrue(count>1,"Fast winding/stator feedback must use local electrical steps");
        var p=(StaggeredMachineElectricalParticipant)m;double[] initial=new double[4],after=new double[4];p.readStaggeredState(initial,0);
        var history=scratch(m);var mechanical=m.captureMechanicalIntegrationState();double tq=((com.interpss.dstab.mach.SalientPoleMachine)m).getTq011();
        assertEquals(count,com.interpss.dstab.controller.runtime.StaggeredArrayState.recommendedLocalSteps(p,h));
        p.readStaggeredState(after,0);assertArrayEquals(initial,after,1e-12);
        assertEquals(history,scratch(m));assertEquals(mechanical,m.captureMechanicalIntegrationState());assertEquals(tq,((com.interpss.dstab.mach.SalientPoleMachine)m).getTq011());
        // A small electrical-state perturbation excites the otherwise invisible
        // fast mode. An unresolved AB2 winding diverges within these intervals.
        solver.notifyAcceptedEndpointDiscontinuity();var trial=initial.clone();trial[1]+=1e-5;p.applyStaggeredState(trial,0,h);
        double largest=0.;
        for(int step=0;step<50;step++) {
            assertTrue(a.solveDEqnStep(true),"Network must converge with the fast electrical participant");p.readStaggeredState(after,0);
            for(int i=0;i<after.length;i++){assertTrue(Double.isFinite(after[i]));largest=Math.max(largest,Math.abs(after[i]-initial[i]));}
        }
        assertTrue(largest<.01,"Fast-mode perturbation must remain bounded: "+largest);
        assertEquals(count,solver.getResolvedMachineElectricalSubsteps().get(m.getId()));
    }
    private Map<String,Object> scratch(DynamicMachineImpl m)throws Exception {
        var field=com.interpss.dstab.mach.impl.MachineImpl.class.getDeclaredField("_dEqnSolver");field.setAccessible(true);Object solver=field.get(m);
        var result=new TreeMap<String,Object>();
        for(var f:solver.getClass().getDeclaredFields())if(f.getName().startsWith("old")||f.getName().startsWith("dEq")
                ||f.getName().startsWith("dEd")||f.getName().startsWith("dPsik")||f.getName().startsWith("pending")){
            f.setAccessible(true);result.put(f.getName(),f.get(solver));}
        assertFalse(result.isEmpty());return result;
    }
    private DynamicMachineImpl load(String family)throws Exception {
        return (DynamicMachineImpl)loadAlgorithm(family,false,.001,false).getNetwork().getMachine("Bus1-mach1");
    }
    private com.interpss.dstab.algo.DynamicSimuAlgorithm loadAlgorithm(String family,boolean fast,double h,boolean staggered)throws Exception {
        IpssCorePlugin.init();IpssCorePlugin.configureSparseSolver("java-klu");String base="testData/adpter/psse/v33/SMIB/";
        var a=new PSSEMultiFileLoader().loadDStab(base+"SMIB_v33.raw",base+"SMIB_v33_"+family+".dyr").getDynSimuAlgorithm();
        if(fast)((com.interpss.dstab.mach.SalientPoleMachine)a.getNetwork().getMachine("Bus1-mach1")).setTq011(.001);
        assertTrue(a.getAclfAlgorithm().loadflow());a.setSimuStepSec(h);a.setSimuOutputHandler(new StateMonitor());
        if(staggered)a.setSolver(new org.interpss.dstab.algo.StaggeredEulerSolver(a));
        assertTrue(a.initialization());return a;
    }
}
