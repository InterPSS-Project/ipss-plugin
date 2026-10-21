package org.interpss.core.dstab;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import org.apache.commons.math3.complex.Complex;
import org.interpss.IpssCorePlugin;
import org.interpss.dstab.algo.PsseStaggeredEulerSolver;
import org.interpss.dstab.control.exc.ieee.y2005.st4b.IEEE2005ST4BExciter;
import org.interpss.fadapter.psse.PSSEMultiFileLoader;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.interpss.core.acsc.fault.SimpleFaultCode;
import com.interpss.dstab.DStabObjectFactory;
import com.interpss.dstab.algo.DynamicSimuAlgorithm;
import com.interpss.dstab.cache.StateMonitor;
import com.interpss.dstab.controller.cml.annotate.AbstractAnnotateController;
import com.interpss.dstab.controller.cml.field.adapt.CMLControlBlock1stOrderAdapter;
import com.interpss.dstab.controller.cml.field.adapt.CMLControlBlockAdapter;
import com.interpss.dstab.mach.impl.RoundRotorMachineImpl;

/** Fixed signal bindings retain the ordered feedback and rectifier observations. */
class St4bCompiledBindingsTest {
    @Test
    void coupledStatesAndHistoryMatchAcrossFaultsAndLiveControls(@TempDir Path temporary) throws Exception {
        for (boolean pss : new boolean[]{false,true}) {
            var ordinary=load(false,pss,temporary.resolve("ordinary-"+pss+".dyr"));
            var compiled=load(true,pss,temporary.resolve("compiled-"+pss+".dyr"));
            assertFalse(ordinary.exciter.isRuntimePlanCompiled());
            assertTrue(compiled.exciter.isRuntimePlanCompiled());
            // The rectifier reads machine current and updates its stored bridge voltage.
            // Compiling its inputs does not authorize reordering or reusing its output.
            assertFalse(compiled.exciter.isCompiledOutputSchedulingEnabled());
            assertThrows(IllegalStateException.class,()->compiled.solver.setCompiledSt4bBindingsEnabled(false));
            double reference=ordinary.exciter.getRefPoint();
            for (int step=0;step<240;step++) {
                for (var f : List.of(ordinary,compiled)) {
                    if (step%17==0) {
                        f.exciter.setRefPoint(reference+(step%3-1)*.03);
                        f.solver.notifyDiscontinuity();
                    }
                    f.exciter.setVuel(step%23==0?.015:0.);
                    f.exciter.setVoel(step%31==0?.2:Double.POSITIVE_INFINITY);
                    f.exciter.kc=.08+step%7*.01;
                    f.exciter.vbmax=8.+step%4;
                    f.exciter.setStatus(step!=73);
                    if (pss) f.machine.getStabilizer().setStatus(step!=97);
                    assertTrue(f.algorithm.solveDEqnStep(true));
                }
                assertEquals(snapshot(ordinary),snapshot(compiled),"PSS="+pss+", step="+step);
            }
        }
    }

    @Test
    void publicParameterBindingsStayLiveAndCustomProvidersRemainVirtual() throws Exception {
        var exciter=new IEEE2005ST4BExciter();
        var uel=exciter.bindCompiledParameter("vuel");
        var oel=exciter.bindCompiledParameter("voel");
        exciter.setVuel(.123); exciter.setVoel(.456);
        assertEquals(.123,uel.getAsDouble()); assertEquals(.456,oel.getAsDouble());
        exciter.setVuel(-.2);exciter.clearVoel();
        assertEquals(-.2,uel.getAsDouble());assertEquals(Double.POSITIVE_INFINITY,oel.getAsDouble());
        var custom=new IEEE2005ST4BExciter() {
            @Override public double getDoubleField(String name) throws com.interpss.common.exp.InterpssException {
                return name.equals("vuel") ? vuel+7. : super.getDoubleField(name);
            }
        };
        var binding=custom.bindCompiledParameter("vuel");custom.setVuel(.5);
        assertEquals(7.5,binding.getAsDouble());custom.setVuel(1.);
        assertEquals(8.,binding.getAsDouble());
    }

    @Test
    void invalidatingACompiledStructureRequiresSolverReinitialization(@TempDir Path temporary) throws Exception {
        var f=load(true,false,temporary.resolve("invalidated.dyr"));
        assertTrue(f.algorithm.solveDEqnStep(true));
        f.exciter.invalidateRuntimePlan();
        assertFalse(f.algorithm.solveDEqnStep(true));
        f.algorithm.getNetwork().setDStabNetInitialized(false);
        assertTrue(f.algorithm.getAclfAlgorithm().loadflow());
        assertTrue(f.algorithm.initialization());
        assertTrue(f.exciter.isRuntimePlanCompiled());
        assertTrue(f.algorithm.solveDEqnStep(true));
    }

    private static List<Object> snapshot(Fixture f) throws Exception {
        var result=new ArrayList<Object>();
        result.add(f.machine.captureMechanicalIntegrationState());
        result.add(f.machine.captureRoundElectricalState());
        add(result,f.machine.getAngle(),f.machine.getSpeed(),f.machine.getEfd(),f.machine.getPe(),f.machine.getPm());
        for (var bus:f.algorithm.getNetwork().getBusList())
            add(result,bus.getVoltage().getReal(),bus.getVoltage().getImaginary());
        controllerSnapshot(result,f.exciter);
        if(f.machine.getStabilizer() instanceof AbstractAnnotateController pss)controllerSnapshot(result,pss);
        var previous=PsseStaggeredEulerSolver.class.getDeclaredField("previous");previous.setAccessible(true);
        add(result,(double[])previous.get(f.solver));
        return result;
    }

    private static void controllerSnapshot(List<Object> result,AbstractAnnotateController controller) {
        for(var wrapper:controller.getFieldWrapperList()) {
            if(wrapper.getField() instanceof AbstractAnnotateController child)controllerSnapshot(result,child);
            else if(wrapper.getField() instanceof CMLControlBlockAdapter block) {
                add(result,block.getU(),block.getStateX(),block.getY());
                if(block instanceof CMLControlBlock1stOrderAdapter first)add(result,first.getDX_dt());
                if(block instanceof com.interpss.dstab.controller.cml.field.block.FilterNthOrderBlock compound)
                    for(var inner:compound.getIntegrationBlocks())add(result,inner.getU(),inner.getStateX(),inner.getDX_dt());
                if(block instanceof org.interpss.dstab.control.pss.ieee.y1992.pss2a.Pss2aLeadLagBlock lag)
                    add(result,lag.getStateDerivative());
            }
        }
        add(result,controller.getOutput(controller.getMachine()));
    }

    private static void add(List<Object> values,double... samples) {
        for(double sample:samples) {
            assertTrue(Double.isFinite(sample));values.add(Double.doubleToRawLongBits(sample));
        }
    }

    private static Fixture load(boolean compiled,boolean pss,Path dynamicFile) throws Exception {
        IpssCorePlugin.init();Path base=Path.of("testData/adpter/psse/v33/SMIB");
        String records=Files.readString(base.resolve("SMIB_v33_genrou_esst4b.dyr"));
        if(pss) records+="\n"+Files.readAllLines(base.resolve("SMIB_v33_genrou_esst1a_pss2a.dyr")).stream()
                .filter(line->line.contains("'PSS2A'")).findFirst().orElseThrow()+"\n";
        Files.writeString(dynamicFile,records);
        var context=new PSSEMultiFileLoader().loadDStab(base.resolve("SMIB_v33.raw").toString(),dynamicFile.toString());
        var algorithm=context.getDynSimuAlgorithm();assertTrue(algorithm.getAclfAlgorithm().loadflow());
        var solver=new PsseStaggeredEulerSolver(algorithm);solver.setCompiledControllerGraphsEnabled(true);
        solver.setCompiledSt4bBindingsEnabled(compiled);
        solver.setNetworkSolutionTolerance(1.e-5);solver.setReuseNetworkEndpoint(true);
        algorithm.setSolver(solver);algorithm.setSimuStepSec(1./240.);algorithm.setTotalSimuTimeSec(1.);
        algorithm.setSimuOutputHandler(new StateMonitor());
        var net=algorithm.getNetwork();
        net.addDynamicEvent(DStabObjectFactory.createBusFaultEvent("Bus1",net,
                SimpleFaultCode.GROUND_3P,new Complex(0.,.2),null,.2,.05),"BindingFault");
        assertTrue(algorithm.initialization());
        var machine=(RoundRotorMachineImpl)net.getMachine("Bus1-mach1");
        return new Fixture(algorithm,solver,machine,(IEEE2005ST4BExciter)machine.getExciter());
    }

    private record Fixture(DynamicSimuAlgorithm algorithm,PsseStaggeredEulerSolver solver,
            RoundRotorMachineImpl machine,IEEE2005ST4BExciter exciter) { }
}
