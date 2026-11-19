package org.interpss.core.dstab;

import static org.junit.jupiter.api.Assertions.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import org.apache.commons.math3.complex.Complex;
import org.interpss.IpssCorePlugin;
import com.interpss.dstab.controller.exc.ieee1981st1.IEEE1981ST1Exciter;
import com.interpss.dstab.controller.exc.ieee2005st4b.IEEE2005ST4BExciter;
import com.interpss.dstab.controller.pss.ieee1992pss2a.Ieee1992PSS2AStabilizer;
import com.interpss.dstab.controller.pss.ieee1992pss2b.Ieee1992PSS2BStabilizer;
import org.interpss.fadapter.psse.PSSEMultiFileLoader;
import org.interpss.dstab.algo.DefaultDynamicSimulationSolver;
import com.interpss.dstab.DStabObjectFactory;
import com.interpss.dstab.algo.DynamicSimuAlgorithm;
import com.interpss.dstab.algo.DynamicSimuMethod;
import com.interpss.dstab.cache.StateMonitor;
import com.interpss.dstab.controller.cml.annotate.AbstractAnnotateController;
import com.interpss.dstab.controller.cml.wrapper.ControlBlockFieldAnWrapper;
import com.interpss.dstab.controller.cml.field.ICMLControlBlock;
import com.interpss.dstab.mach.Machine;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Automatic controller preparation preserves ordinary integration histories. */
class ModifiedEulerControllerPreparationTest {
    record Fixture(DynamicSimuAlgorithm algorithm,DefaultDynamicSimulationSolver solver,Machine machine) { }
    private Fixture load(String family,boolean optimized,Path file,boolean initialize) throws Exception {
        IpssCorePlugin.init();IpssCorePlugin.configureSparseSolver("java-klu");
        Path base=Path.of("testData/adpter/psse/v33/SMIB");
        String dyr=Files.readString(base.resolve("SMIB_v33_genrou_"+(family.endsWith("pss2b")?"esst1a_pss2a":family)+".dyr"));
        if(family.endsWith("pss2b")) {
            String record=Files.readAllLines(Path.of("testData/adpter/psse/v33/HspModels/pss2b.dyr"))
                .stream().filter(line->line.contains("'PSS2B'")).findFirst().orElseThrow();
            dyr=dyr.replaceAll("(?m)^.*'PSS2A'.*$",java.util.regex.Matcher.quoteReplacement(record));
        }
        if(!dyr.contains("'PSS2A'")&&!dyr.contains("'PSS2B'")&&!dyr.contains("'IEEEST'"))dyr+="\n"+Files.readAllLines(base.resolve("SMIB_v33_genrou_esst1a_pss2a.dyr"))
            .stream().filter(line->line.contains("'PSS2A'")).findFirst().orElseThrow()+"\n";
        Files.writeString(file,dyr);
        var context=new PSSEMultiFileLoader().loadDStab(base.resolve("SMIB_v33.raw").toString(),file.toString());
        var algorithm=context.getDynSimuAlgorithm();var solver=assertInstanceOf(DefaultDynamicSimulationSolver.class,algorithm.getSolver());
        assertTrue(solver.isControllerRuntimePreparationEnabled());
        solver.setControllerRuntimePreparationEnabled(optimized);
        algorithm.setSimuMethod(DynamicSimuMethod.MODIFIED_EULER);algorithm.setSimuStepSec(1./240.);
        algorithm.setSimuOutputHandler(new StateMonitor());
        assertTrue(algorithm.getAclfAlgorithm().loadflow());
        var network=algorithm.getNetwork();
        network.addDynamicEvent(DStabObjectFactory.createBusFaultEvent("Bus1",network,
            com.interpss.core.acsc.fault.SimpleFaultCode.GROUND_3P,new Complex(0.,.2),null,.2,.05),"fault");
        if(initialize)assertTrue(algorithm.initialization());
        return new Fixture(algorithm,solver,network.getMachine("Bus1-mach1"));
    }
    private List<Long> snapshot(Fixture fixture) {
        var values=new ArrayList<Long>();var m=fixture.machine;
        for(double v:new double[]{m.getAngle(),m.getSpeed(),m.getPe(),m.getPm(),m.getEfd()})values.add(Double.doubleToRawLongBits(v));
        for(var bus:fixture.algorithm.getNetwork().getBusList()) {
            values.add(Double.doubleToRawLongBits(bus.getVoltage().getReal()));
            values.add(Double.doubleToRawLongBits(bus.getVoltage().getImaginary()));
            values.add(Double.doubleToRawLongBits(bus.getFreq()));
        }
        for(var controller:List.of(m.getExciter(),m.getStabilizer()))
            capture(assertInstanceOf(AbstractAnnotateController.class,controller),values);
        if(m.getStabilizer() instanceof Ieee1992PSS2BStabilizer pss)
            for(double v:new double[]{pss.getInput1VoltageMemory(),pss.getInput2VoltageMemory(),
                    pss.getInput1VoltageDerivative(),pss.getInput2VoltageDerivative()})values.add(Double.doubleToRawLongBits(v));
        return values;
    }
    private void capture(AbstractAnnotateController model,List<Long> values) {
        for(var wrapper:model.getFieldWrapperList()) {
            var field=wrapper.getField();
            if(field instanceof AbstractAnnotateController child)capture(child,values);
            else if(wrapper instanceof ControlBlockFieldAnWrapper&&field instanceof ICMLControlBlock block) {
                if(block instanceof com.interpss.dstab.controller.cml.field.block.FilterNthOrderBlock compound)
                    for(var part:compound.getIntegrationBlocks())captureBlock(part,values);
                else captureBlock(block,values);
            }
        }
    }
    private void captureBlock(ICMLControlBlock block,List<Long> values) {
        values.add(Double.doubleToRawLongBits(block.getStateX()));
        if(block instanceof com.interpss.dstab.controller.cml.field.adapt.CMLControlBlock1stOrderAdapter first)
            values.add(Double.doubleToRawLongBits(first.getDX_dt()));
        else if(block instanceof com.interpss.dstab.controller.pss.ieee1992pss2a.Pss2aLeadLagBlock lead)
            values.add(Double.doubleToRawLongBits(lead.getStateDerivative()));
        values.add(Double.doubleToRawLongBits(block.getU()));
    }
    @Test void faultLiveParametersAndActivationPreservePredictorCorrectorStates(@TempDir Path folder) throws Exception {
        for(String family:List.of("exst1","esst1a_pss2a","esst4b","esst1a_ieeest","esst1a_pss2b")) {
            var ordinary=load(family,false,folder.resolve(family+"-ordinary.dyr"),true);
            var fast=load(family,true,folder.resolve(family+"-fast.dyr"),true);
            var report=fast.solver.getControllerPreparationReport();
            assertEquals(2,report.compiledControllers(),report.toString());
            assertEquals(family.endsWith("ieeest")||family.endsWith("pss2b")?0:1,report.pss2aControllers());
            assertEquals(family.endsWith("ieeest")?1:0,report.ieeestControllers());
            assertEquals(family.equals("esst4b")?0:1,report.st1Controllers());
            assertEquals(snapshot(ordinary),snapshot(fast),"initial "+family);
            if(fast.machine.getStabilizer() instanceof Ieee1992PSS2BStabilizer pss)
                assertTrue(pss.hasCompiledStageEvaluator(),"Automatic PSS2B typed-stage coverage");
            double reference=((AbstractAnnotateController)ordinary.machine.getExciter()).getRefPoint();
            for(int tick=0;tick<240;tick++) {
                for(var fixture:List.of(ordinary,fast)) {
                    var exciter=fixture.machine.getExciter();
                    if(tick%29==0)exciter.setRefPoint(reference+(tick%3-1)*.01);
                    exciter.setStatus(tick!=71);fixture.machine.getStabilizer().setStatus(tick!=93);
                    if(exciter instanceof IEEE1981ST1Exciter st1){st1.vuel=tick%17==0?.001:0.;st1.kc=.02;}
                    if(exciter instanceof IEEE2005ST4BExciter st4){st4.setVuel(tick%17==0?.001:0.);st4.kc=.02;}
                    assertTrue(fixture.algorithm.solveDEqnStep(true));
                }
                assertEquals(snapshot(ordinary),snapshot(fast),family+" step="+tick);
            }
            if(fast.machine.getExciter() instanceof IEEE1981ST1Exciter st1)assertTrue(st1.getModifiedEulerStageCount()>400);
        }
    }
    @Test void derivativeInputsRetainAcceptedMemoryAcrossTheCoupledFault(@TempDir Path folder) throws Exception {
        for(int slot:new int[]{1,2}) {
            var ordinary=load("esst1a_pss2b",false,folder.resolve("ordinary-"+slot+".dyr"),false);
            var fast=load("esst1a_pss2b",true,folder.resolve("fast-"+slot+".dyr"),false);
            for(var fixture:List.of(ordinary,fast)) {
                var pss=(Ieee1992PSS2BStabilizer)fixture.machine.getStabilizer();
                pss.getData().setIcs1(slot==1?6:2);pss.getData().setIcs2(slot==2?6:2);
                pss.setInputSignalBuses(fixture.machine.getDStabBus(),fixture.machine.getDStabBus());
                assertTrue(fixture.algorithm.initialization());
            }
            var pss=(Ieee1992PSS2BStabilizer)fast.machine.getStabilizer();
            assertTrue(pss.hasCompiledStageEvaluator());
            boolean nonzero=false;
            for(int tick=0;tick<120;tick++) {
                assertTrue(ordinary.algorithm.solveDEqnStep(true));assertTrue(fast.algorithm.solveDEqnStep(true));
                assertEquals(snapshot(ordinary),snapshot(fast),"Derivative slot="+slot+" step="+tick);
                nonzero|=Math.abs(slot==1?pss.getInput1VoltageDerivative():pss.getInput2VoltageDerivative())>1e-6;
            }
            assertTrue(nonzero,"The fault must exercise derivative memory");
            assertTrue(pss.getCompiledStageEvaluationCount()>200);
        }
    }
    @Test void customBlockOrObservedMachineKeepsCompatibleDispatch(@TempDir Path folder) throws Exception {
        for(boolean observer:new boolean[]{false,true}) {
            var fixture=load("esst1a_pss2a",true,folder.resolve("custom-"+observer+".dyr"),false);
            if(observer)fixture.machine.eAdapters().add(new org.eclipse.emf.common.notify.impl.AdapterImpl());
            else {
                var pss=(Ieee1992PSS2AStabilizer)fixture.machine.getStabilizer();
                var field=pss.getClass().getDeclaredField("filterBlock1");field.setAccessible(true);
                field.set(pss,new com.interpss.dstab.controller.cml.field.block.FilterControlBlock(1.,.1,.1){});
            }
            assertTrue(fixture.algorithm.initialization());
            assertEquals(0,fixture.solver.getControllerPreparationReport().compiledControllers());
            assertFalse(((AbstractAnnotateController)fixture.machine.getStabilizer()).isRuntimePlanCompiled());
            assertTrue(fixture.algorithm.solveDEqnStep(true));
        }
    }
    @Test void reinitializationCanReturnToCompatibleControllerExecution(@TempDir Path folder) throws Exception {
        var fixture=load("esst1a_pss2a",true,folder.resolve("reinitialize.dyr"),true);
        var st1=(IEEE1981ST1Exciter)fixture.machine.getExciter();
        assertTrue(fixture.algorithm.solveDEqnStep(true));assertTrue(st1.getModifiedEulerStageCount()>0);
        fixture.solver.setControllerRuntimePreparationEnabled(false);
        assertTrue(fixture.algorithm.initialization());
        assertNull(fixture.solver.getControllerPreparationReport());assertFalse(st1.isRuntimePlanCompiled());
        assertEquals(0,st1.getModifiedEulerStageCount());assertTrue(fixture.algorithm.solveDEqnStep(true));
    }
    @Test void structuralInvalidationReleasesTheDirectEquationBindings(@TempDir Path folder) throws Exception {
        for(String family:List.of("esst1a_pss2a","esst4b")) {
            var ordinary=load(family,false,folder.resolve(family+"-a.dyr"),true);
            var fast=load(family,true,folder.resolve(family+"-b.dyr"),true);
            for(var fixture:List.of(ordinary,fast)) {
                var controller=(AbstractAnnotateController)fixture.machine.getExciter();
                controller.invalidateRuntimePlan();
                var expression=new com.interpss.dstab.datatype.ExpCalculator("mach.vt * 0.999");
                com.interpss.dstab.controller.cml.annotate.util.CMLSymbolMapper.mapExpressionSymbolStr2Type(expression.getRecList());
                var wrapper=(com.interpss.dstab.controller.cml.wrapper.BlockFieldAnWrapper<?>)controller.getFieldWrapperList()
                    .stream().filter(w->w.getFieldName().equals("trDelayBlock")).findFirst().orElseThrow();
                wrapper.setInputExp(expression);controller.compileRuntimePlan();
                ((AbstractAnnotateController)fixture.machine.getStabilizer()).invalidateRuntimePlan();
            }
            for(int tick=0;tick<30;tick++) {
                assertTrue(ordinary.algorithm.solveDEqnStep(true));assertTrue(fast.algorithm.solveDEqnStep(true));
                assertEquals(snapshot(ordinary),snapshot(fast),family+" invalidated step="+tick);
            }
            if(fast.machine.getExciter() instanceof IEEE1981ST1Exciter st1)assertEquals(0,st1.getModifiedEulerStageCount());
        }
    }
}
