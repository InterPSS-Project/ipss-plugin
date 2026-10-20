package org.interpss.core.dstab;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import org.apache.commons.math3.complex.Complex;
import org.interpss.IpssCorePlugin;
import org.interpss.dstab.algo.PsseStaggeredEulerSolver;
import org.interpss.dstab.control.pss.ieee.y1992.pss2a.Ieee1992PSS2AStabilizer;
import org.interpss.fadapter.psse.PSSEMultiFileLoader;
import org.junit.jupiter.api.Test;

import com.interpss.core.acsc.fault.SimpleFaultCode;
import com.interpss.dstab.DStabObjectFactory;
import com.interpss.dstab.algo.DynamicSimuAlgorithm;
import com.interpss.dstab.algo.DynamicSimuMethod;
import com.interpss.dstab.cache.StateMonitor;
import com.interpss.dstab.controller.cml.annotate.AbstractAnnotateController;
import com.interpss.dstab.mach.Machine;

class CmlSolverRuntimePlanTest {
    private static final double STEP = 1. / 240.;

    @Test void faultAndClearingMatchTheInterpreterAtEveryStateEndpoint() throws Exception {
        var baseline = run(false);
        var compiled = run(true);
        assertEquals(baseline.size(), compiled.size());
        for (int step = 0; step < baseline.size(); step++) {
            assertEquals(baseline.get(step).keySet(), compiled.get(step).keySet());
            for (var entry : baseline.get(step).entrySet())
                assertEquals(Double.doubleToLongBits(entry.getValue()),
                        Double.doubleToLongBits(compiled.get(step).get(entry.getKey())),
                        "step=" + step + ", signal=" + entry.getKey());
        }
    }

    @Test void fixedGraphsMatchLiveBindingsAcrossFaultClearingAndAllNamedStates() throws Exception {
        var baseline = run(true, false);
        var fixed = run(true, true);
        assertEquals(baseline.size(), fixed.size());
        for (int step = 0; step < baseline.size(); step++) {
            assertEquals(baseline.get(step).keySet(), fixed.get(step).keySet());
            for (var entry : baseline.get(step).entrySet())
                assertEquals(Double.doubleToLongBits(entry.getValue()),
                        Double.doubleToLongBits(fixed.get(step).get(entry.getKey())),
                        "step=" + step + ", signal=" + entry.getKey());
        }
    }

    @Test void fixedInputOutputScopeRejectsStageChangesAndReleasesCleanly() throws Exception {
        var algorithm = load(true);
        var solver = (PsseStaggeredEulerSolver)algorithm.getSolver();
        var machine = algorithm.getNetwork().getMachine("Bus1-mach1");
        var pss = (Ieee1992PSS2AStabilizer)machine.getStabilizer();
        double output = pss.getOutput(machine);
        pss.beginFixedInputObservation();
        try {
            for (int i = 0; i < 50; i++) assertEquals(output, pss.getOutput(machine), 0.);
            assertThrows(IllegalStateException.class, pss::beginFixedInputObservation);
            assertThrows(IllegalStateException.class,
                    () -> pss.nextStep(0., DynamicSimuMethod.MODIFIED_EULER, machine, 0));
        } finally { pss.endFixedInputObservation(); }
        assertTrue(pss.nextStep(0., DynamicSimuMethod.MODIFIED_EULER, machine, 0));
        assertTrue(algorithm.solveDEqnStep(true));
        assertThrows(IllegalStateException.class,
                () -> solver.setRuntimeControllerOptimizationEnabled(false));
    }

    private static DynamicSimuAlgorithm load(boolean optimized) throws Exception {
        return load(optimized, false);
    }

    private static DynamicSimuAlgorithm load(boolean optimized, boolean fixed) throws Exception {
        IpssCorePlugin.init();
        Path path = Path.of("testData", "adpter", "psse", "v33", "SMIB");
        var context = new PSSEMultiFileLoader().loadDStab(
                path.resolve("SMIB_v33.raw").toString(),
                path.resolve("SMIB_v33_genrou_esst1a_pss2a.dyr").toString());
        var algorithm = context.getDynSimuAlgorithm();
        var network = context.getDStabilityNet();
        assertTrue(algorithm.getAclfAlgorithm().loadflow());
        var solver = new PsseStaggeredEulerSolver(algorithm);
        solver.setRuntimeControllerOptimizationEnabled(optimized);
        solver.setCompiledControllerGraphsEnabled(fixed);
        algorithm.setSolver(solver);
        algorithm.setSimuMethod(DynamicSimuMethod.MODIFIED_EULER);
        algorithm.setSimuStepSec(STEP);
        algorithm.setTotalSimuTimeSec(.5);
        algorithm.setSimuOutputHandler(new StateMonitor());
        algorithm.setOutPutPerSteps(1000);
        network.addDynamicEvent(DStabObjectFactory.createBusFaultEvent(
                "Bus1", network, SimpleFaultCode.GROUND_3P,
                new Complex(0., .2), null, .1, .05), "RuntimePlanFault");
        assertTrue(algorithm.initialization());
        if (fixed) {
            var machine = network.getMachine("Bus1-mach1");
            assertTrue(((AbstractAnnotateController)machine.getExciter()).isRuntimePlanCompiled());
            assertTrue(((AbstractAnnotateController)machine.getStabilizer()).isRuntimePlanCompiled());
        }
        return algorithm;
    }

    private static List<Map<String, Double>> run(boolean optimized) throws Exception {
        return run(optimized, false);
    }

    private static List<Map<String, Double>> run(boolean optimized, boolean fixed) throws Exception {
        var algorithm = load(optimized, fixed);
        var diagnostics = new ArrayList<com.interpss.dstab.controller.cml.runtime.CmlRuntimeDiagnostics>();
        var frames = new ArrayList<com.interpss.dstab.controller.cml.runtime.CmlRuntimeDiagnostics.Frame>();
        var machine = algorithm.getNetwork().getMachine("Bus1-mach1");
        for (var controller : machine.getControllerList()) if (controller instanceof AbstractAnnotateController c) {
            var plan = new com.interpss.dstab.controller.cml.runtime.CmlRuntimeDiagnostics(c);
            assertTrue(plan.unsupportedBlocks().isEmpty(), plan.unsupportedBlocks().toString());
            diagnostics.add(plan); frames.add(plan.newFrame());
        }
        var result = new ArrayList<Map<String, Double>>();
        result.add(recordWithHistory(algorithm, diagnostics, frames));
        for (int step = 0; step < 120; step++) {
            assertTrue(algorithm.solveDEqnStep(true));
            result.add(recordWithHistory(algorithm, diagnostics, frames));
        }
        return result;
    }

    private static Map<String, Double> recordWithHistory(DynamicSimuAlgorithm algorithm,
            List<com.interpss.dstab.controller.cml.runtime.CmlRuntimeDiagnostics> diagnostics,
            List<com.interpss.dstab.controller.cml.runtime.CmlRuntimeDiagnostics.Frame> frames) {
        var values = record(algorithm);
        for (int c=0;c<diagnostics.size();c++) {
            var plan=diagnostics.get(c); var frame=frames.get(c); plan.sample(frame);
            for (int i=0;i<plan.names().size();i++) {
                String key="diagnostic/"+c+"/"+plan.names().get(i);
                assertTrue(Double.isFinite(frame.states[i]),key);
                assertTrue(Double.isFinite(frame.storedDerivatives[i]),key);
                values.put(key+"/state",frame.states[i]);
                values.put(key+"/storedDerivative",frame.storedDerivatives[i]);
                values.put(key+"/equationResidual",frame.equationResiduals[i]);
            }
        }
        return values;
    }

    private static Map<String, Double> record(DynamicSimuAlgorithm algorithm) {
        var values = new TreeMap<String, Double>();
        for (var bus : algorithm.getNetwork().getBusList()) {
            values.put(bus.getId() + "/vr", bus.getVoltage().getReal());
            values.put(bus.getId() + "/vi", bus.getVoltage().getImaginary());
            for (var generator : bus.getContributeGenList()) {
                if (!(generator.getDynamicGenDevice() instanceof Machine machine)) continue;
                String prefix = machine.getId() + "/";
                values.put(prefix + "angle", machine.getAngle());
                values.put(prefix + "speed", machine.getSpeed());
                values.put(prefix + "efd", machine.getEfd());
                values.put(prefix + "pm", machine.getPm());
                values.put(prefix + "pe", machine.getPe());
                values.put(prefix + "q", machine.getQGen());
                for (var controller : machine.getControllerList())
                    if (controller instanceof AbstractAnnotateController c)
                        c.getNamedStates().forEach((name, value) ->
                                values.put(prefix + c.getClass().getSimpleName() + "/" + name, value));
            }
        }
        return values;
    }
}
