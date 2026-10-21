package org.interpss.core.dstab;

import static org.junit.jupiter.api.Assertions.*;

import java.util.ArrayList;
import java.util.List;

import org.interpss.IpssCorePlugin;
import org.interpss.dstab.algo.PsseStaggeredEulerSolver;
import org.interpss.dstab.control.exc.ieee.y1981.st1.IEEE1981ST1Exciter;
import org.interpss.fadapter.psse.PSSEMultiFileLoader;
import org.junit.jupiter.api.Test;

import com.interpss.dstab.algo.DynamicSimuAlgorithm;
import com.interpss.dstab.cache.StateMonitor;
import com.interpss.dstab.controller.cml.field.adapt.CMLControlBlock1stOrderAdapter;
import com.interpss.dstab.controller.cml.field.adapt.CMLControlBlockAdapter;
import com.interpss.dstab.mach.Machine;

/** Stage, lifecycle and fallback regressions for the compiled local recurrence. */
class St1CompiledLocalPlanTest {
    @Test
    void compiledChildOutputReuseKeepsCoupledStatesAcrossDiscontinuities() throws Exception {
        var ordinary=load("esst1a_pss2a",true,true);
        var reused=load("esst1a_pss2a",true,true);
        assertThrows(IllegalStateException.class,()->reused.solver.setPreparedControllerOutputReuseEnabled(false));
        var first=(com.interpss.dstab.controller.cml.annotate.AbstractAnnotateController)ordinary.machine.getStabilizer();
        var second=(com.interpss.dstab.controller.cml.annotate.AbstractAnnotateController)reused.machine.getStabilizer();
        first.setPreparedOutputReuseEnabled(false);
        long firstCount=first.getPreparedOutputReuseCount(), secondCount=second.getPreparedOutputReuseCount();
        for(int step=0;step<240;step++) {
            for(var fixture:List.of(ordinary,reused)) {
                if(step%19==0) {
                    fixture.exciter.setRefPoint(fixture.exciter.getRefPoint()+(step%3-1)*.01);
                    fixture.solver.notifyDiscontinuity();
                }
                fixture.machine.getStabilizer().setStatus(step%37!=4);
                assertTrue(fixture.algorithm.solveDEqnStep(true));
            }
            assertEquals(snapshot(ordinary),snapshot(reused),"step "+step);
        }
        assertEquals(firstCount,first.getPreparedOutputReuseCount());
        assertTrue(second.getPreparedOutputReuseCount()>secondCount+100);
    }

    @Test
    void fullStateAndHistoryMatchOrdinaryEvaluationAcrossEventsAndLiveParameters() throws Exception {
        for(String model:List.of("esst1a_pss2a","esst1a_ieeest","esst1a","exst1")) {
            var ordinary=load(model,false);
            var compiled=load(model,true);
            double reference=ordinary.exciter.getRefPoint();
            for(int step=0;step<120;step++) {
                for(var fixture:List.of(ordinary,compiled)) {
                    var exciter=fixture.exciter;
                    if(step%13==0) {
                        exciter.setRefPoint(reference+(step%3-1)*.03);
                        fixture.solver.notifyDiscontinuity();
                    }
                    exciter.uel=step%4; exciter.setVuel(.1); exciter.setVoel(8.);
                    exciter.vosError=step%2; exciter.vosOutput=1-exciter.vosError;
                    exciter.kc=step%7*.01; exciter.klr=step%3*.05;
                    exciter.setStatus(step%29!=5);
                    assertTrue(fixture.algorithm.solveDEqnStep(true));
                }
                assertEquals(snapshot(ordinary),snapshot(compiled),model+" step "+step);
            }
            assertEquals(0,ordinary.exciter.getCompiledLocalAdvanceCount());
            if(model.equals("esst1a_ieeest"))assertEquals(0,compiled.exciter.getCompiledLocalAdvanceCount());
            else assertTrue(compiled.exciter.getCompiledLocalAdvanceCount()>100,model+" must exercise compiled dispatch");
        }
    }

    @Test
    void existingCompiledGraphModeKeepsCoupledTrajectories() throws Exception {
        for(String model:List.of("esst1a_pss2a","esst1a","exst1")) {
            var ordinary=load(model,true,false);
            var compiled=load(model,true,true);
            for(int step=0;step<40;step++) {
                for(var fixture:List.of(ordinary,compiled)) {
                    if(step%11==0) {
                        fixture.exciter.setRefPoint(fixture.exciter.getRefPoint()+.001);
                        fixture.solver.notifyDiscontinuity();
                    }
                    assertTrue(fixture.algorithm.solveDEqnStep(true));
                }
                assertEquals(snapshot(ordinary),snapshot(compiled),model+" compiled graph step "+step);
            }
        }
    }

    @Test
    void lateMachineObserverFallsBackAndCanBeRemoved() throws Exception {
        var f=load("esst1a",true);
        assertTrue(f.algorithm.solveDEqnStep(true));
        long first=f.exciter.getCompiledLocalAdvanceCount();
        assertTrue(first>0);
        var observer=new org.eclipse.emf.common.notify.impl.AdapterImpl();
        f.machine.eAdapters().add(observer);
        assertTrue(f.algorithm.solveDEqnStep(true));
        assertEquals(first,f.exciter.getCompiledLocalAdvanceCount());
        f.machine.eAdapters().remove(observer);
        assertTrue(f.algorithm.solveDEqnStep(true));
        assertEquals(first+1,f.exciter.getCompiledLocalAdvanceCount());
    }

    @Test
    void malformedOutputBufferAndCustomLimitLeaveIntegrationStateUntouched() throws Exception {
        var f=load("esst1a",true);
        int count=f.exciter.getNamedStateBlocks().size();
        var before=snapshot(f);
        assertThrows(IllegalArgumentException.class,()->f.exciter.tryCompiledLocalAdvance(1./240.,false,
                true,false,new double[count],new double[count],new double[count],new double[0]));
        assertEquals(before,snapshot(f));
        var regulator=(com.interpss.dstab.controller.cml.field.block.DelayControlBlock)
                f.exciter.getNamedStateBlocks().get("kaDelayBlock");
        var original=regulator.getLimit();
        var limitField=regulator.getClass().getDeclaredField("limit");limitField.setAccessible(true);
        limitField.set(regulator,new com.interpss.dstab.datatype.LimitExpression(original.getMax(),original.getMin()) {});
        assertFalse(f.exciter.tryCompiledLocalAdvance(1./240.,false,true,false,
                new double[count],new double[count],new double[count],new double[1]));
        assertEquals(before,snapshot(f));
        limitField.set(regulator,original);
        assertTrue(f.algorithm.solveDEqnStep(true));
        assertEquals(1,f.exciter.getCompiledLocalAdvanceCount());
    }

    @Test
    void structuralInvalidationRequiresSolverReinitialization() throws Exception {
        var f=load("exst1",true);
        assertTrue(f.algorithm.solveDEqnStep(true));
        f.exciter.invalidateRuntimePlan();
        assertFalse(f.algorithm.solveDEqnStep(true),"solver reports structural invalidation as a failed step");
        f.algorithm.getNetwork().setDStabNetInitialized(false);
        assertTrue(f.algorithm.getAclfAlgorithm().loadflow());
        assertTrue(f.algorithm.initialization());
        assertEquals(0,f.exciter.getCompiledLocalAdvanceCount());
        assertTrue(f.algorithm.solveDEqnStep(true));
        assertEquals(1,f.exciter.getCompiledLocalAdvanceCount());
        assertThrows(IllegalStateException.class,()->f.solver.setCompiledLocalSt1Enabled(false));
    }

    private static List<Long> snapshot(Fixture f) throws Exception {
        var result=new ArrayList<Long>();
        add(result,f.machine.getAngle(),f.machine.getSpeed(),f.machine.getEfd(),f.exciter.getOutput(f.machine));
        for(var wrapper:f.exciter.getFieldWrapperList())
            if(wrapper.getField() instanceof CMLControlBlockAdapter block) {
                add(result,block.getU(),block.getStateX(),block.getY());
                if(block instanceof CMLControlBlock1stOrderAdapter first)add(result,first.getDX_dt());
            }
        var locals=PsseStaggeredEulerSolver.class.getDeclaredField("localSt1Controllers");
        locals.setAccessible(true);
        for(Object local:(List<?>)locals.get(f.solver)) {
            var history=local.getClass().getDeclaredField("previous");history.setAccessible(true);
            add(result,(double[])history.get(local));
        }
        return result;
    }

    private static void add(List<Long> values,double... samples) {
        for(double sample:samples) {
            assertTrue(Double.isFinite(sample));
            values.add(Double.doubleToRawLongBits(sample));
        }
    }

    private static Fixture load(String model,boolean compiled) throws Exception {
        return load(model,compiled,false);
    }

    private static Fixture load(String model,boolean compiled,boolean compiledGraph) throws Exception {
        IpssCorePlugin.init();
        String base="testData/adpter/psse/v33/SMIB/";
        var context=new PSSEMultiFileLoader().loadDStab(base+"SMIB_v33.raw",
                base+"SMIB_v33_genrou_"+model+".dyr");
        var algorithm=context.getDynSimuAlgorithm();
        assertTrue(algorithm.getAclfAlgorithm().loadflow());
        var solver=new PsseStaggeredEulerSolver(algorithm);
        solver.setCompiledLocalSt1Enabled(compiled);
        solver.setCompiledControllerGraphsEnabled(compiledGraph);
        algorithm.setSolver(solver);algorithm.setSimuStepSec(1./240.);
        algorithm.setTotalSimuTimeSec(1.);algorithm.setSimuOutputHandler(new StateMonitor());
        assertTrue(algorithm.initialization());
        var machine=context.getDStabilityNet().getMachine("Bus1-mach1");
        return new Fixture(algorithm,solver,machine,(IEEE1981ST1Exciter)machine.getExciter());
    }

    private record Fixture(DynamicSimuAlgorithm algorithm,PsseStaggeredEulerSolver solver,
            Machine machine,IEEE1981ST1Exciter exciter) {}
}
