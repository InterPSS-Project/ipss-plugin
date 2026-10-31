package org.interpss.dstab.control.pss.ieee.y1992.pss2b;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import org.interpss.IpssCorePlugin;
import org.interpss.dstab.control.pss.ieee.y1992.pss2a.Pss2aLeadLagBlock;
import org.interpss.fadapter.psse.PSSEMultiFileLoader;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import com.interpss.dstab.controller.cml.annotate.AbstractAnnotateController;
import com.interpss.dstab.controller.cml.field.adapt.CMLControlBlockAdapter;
import com.interpss.dstab.controller.cml.field.adapt.CMLControlBlock1stOrderAdapter;
import com.interpss.dstab.controller.cml.field.block.DelayControlBlock;
import com.interpss.dstab.controller.cml.field.block.FilterNthOrderBlock;
import com.interpss.dstab.controller.cml.wrapper.ControlBlockFieldAnWrapper;

/** Typed stages must preserve generic block storage, live parameters and pending history. */
class Pss2bCompiledStageTest {
    private static final Path FIXTURE = Path.of("testData/adpter/psse/v33/HspModels");

    @BeforeAll static void setup() { IpssCorePlugin.init(); }

    static Ieee1992PSS2BStabilizer model(boolean bypass, int m, int n) throws Exception {
        var context = new PSSEMultiFileLoader().loadDStab(
                FIXTURE.resolve("network.raw").toString(), FIXTURE.resolve("pss2b.dyr").toString());
        var algorithm = context.getDynSimuAlgorithm();
        assertTrue(algorithm.getAclfAlgorithm().loadflow());
        var machine = context.getDStabilityNet().getMachine("Bus1-mach1");
        var pss = (Ieee1992PSS2BStabilizer) machine.getStabilizer();
        var data = pss.getData();
        data.setM(m); data.setN(n);
        data.setTw2(bypass ? 0. : .3); data.setTw4(bypass ? 0. : .4);
        data.setT6(bypass ? 0. : .07); data.setT7(bypass ? 0. : .09);
        data.setT2(bypass ? 0. : .03); data.setT4(bypass ? 0. : .04); data.setT11(bypass ? 0. : .05);
        if (bypass) { data.setT1(0.); data.setT3(0.); data.setT10(0.); }
        data.setT8(.04); data.setT9(.1);
        data.setA(1.); data.setTa(bypass ? 0. : .02); data.setTb(bypass ? 0. : .06);
        data.setVstmax(.02); data.setVstmin(-.03);
        assertTrue(pss.initStates(machine.getDStabBus(), machine));
        pss.invalidateRuntimePlan();
        return pss;
    }

    @Test void everyStageAndObservationMatchesGenericStorageAcrossBypassesAndFilterOrders() throws Exception {
        for (boolean bypass : new boolean[]{false, true})
                for (int[] order : new int[][]{{0, 0}, {2, 0}, {1, 2}, {2, 2}, {5, 1}}) {
            var generic = model(bypass, order[0], order[1]);
            var typed = model(bypass, order[0], order[1]);
            typed.compileRuntimePlan();
            assertTrue(typed.hasCompiledStageEvaluator(), "Positive typed-path coverage");
            for (int step = 0; step < 180; step++) for (boolean predictor : new boolean[]{true, false}) {
                for (var pss : List.of(generic, typed)) {
                    pss.input1Signal = .03 * Math.sin(step * .23);
                    pss.input2Signal = .04 * Math.cos(step * .19);
                    pss.ks3 = .2 + (step % 9) * .13;
                    pss.ks4 = .5 + (step % 7) * .07;
                    pss.setRefPoint((step % 5 - 2) * .001);
                    pss.inputPath1.setRefPoint((step % 3) * .002);
                    pss.inputPath2.setRefPoint(-(step % 4) * .003);
                    assertTrue(pss.eulerStep(predictor, .0005, pss.getMachine()));
                }
                assertEquals(snapshot(generic), snapshot(typed), "Step " + step + ", predictor=" + predictor);
                for (int observation = 0; observation < 3; observation++) {
                    var before = snapshot(typed);
                    assertEquals(Double.doubleToLongBits(generic.getOutput(generic.getMachine())),
                            Double.doubleToLongBits(typed.getOutput(typed.getMachine())));
                    assertEquals(before, snapshot(typed), "Observation must preserve pending predictor history");
                    assertEquals(snapshot(generic), snapshot(typed));
                }
            }
            assertEquals(360, typed.getCompiledStageEvaluationCount());
        }
    }

    @Test void customBlockAfterInvalidationRetainsGenericDispatch() throws Exception {
        var pss = model(false, 2, 1);
        pss.compileRuntimePlan(); assertTrue(pss.hasCompiledStageEvaluator());
        pss.invalidateRuntimePlan(); assertFalse(pss.hasCompiledStageEvaluator());
        var wrapper = (ControlBlockFieldAnWrapper) pss.inputPath1.getFieldWrapperList().stream()
                .filter(field -> field.getFieldName().equals("delayBlock")).findFirst().orElseThrow();
        int[] reads = {0};
        var custom = new DelayControlBlock(1., .07) {
            @Override public double getY() { reads[0]++; return super.getY(); }
        };
        assertTrue(custom.initStateY0(0.));
        wrapper.setField(custom);
        pss.compileRuntimePlan(); assertFalse(pss.hasCompiledStageEvaluator());
        assertTrue(pss.eulerStep(true, .0005, pss.getMachine()));
        assertTrue(reads[0] > 0);
    }

    @Test void observersChangedEquationsAndAliasedBlocksRejectTheTypedStage() throws Exception {
        for (String change : List.of("parent-observer", "child-observer", "expression", "alias")) {
            var pss = model(false, 2, 1);
            switch (change) {
                case "parent-observer" -> pss.eAdapters().add(new org.eclipse.emf.common.notify.impl.AdapterImpl());
                case "child-observer" -> pss.inputPath2.eAdapters().add(new org.eclipse.emf.common.notify.impl.AdapterImpl());
                case "expression" -> {
                    var wrapper = (com.interpss.dstab.controller.cml.wrapper.BlockFieldAnWrapper<?>) pss
                            .getFieldWrapperList().stream().filter(w -> w.getFieldName().equals("rampFilter")).findFirst().orElseThrow();
                    var expression = new com.interpss.dstab.datatype.ExpCalculator("this.inputPath1.y - this.ks3*this.inputPath2.y");
                    com.interpss.dstab.controller.cml.annotate.util.CMLSymbolMapper.mapExpressionSymbolStr2Type(expression.getRecList());
                    wrapper.setInputExp(expression);
                }
                case "alias" -> {
                    var first = (ControlBlockFieldAnWrapper) pss.getFieldWrapperList().stream()
                            .filter(w -> w.getFieldName().equals("leadLag1")).findFirst().orElseThrow();
                    var second = (ControlBlockFieldAnWrapper) pss.getFieldWrapperList().stream()
                            .filter(w -> w.getFieldName().equals("leadLag2")).findFirst().orElseThrow();
                    second.setField(first.getField());
                }
                default -> fail(change);
            }
            pss.compileRuntimePlan();
            assertFalse(pss.hasCompiledStageEvaluator(), change);
        }
    }

    @Test void invalidTypedParametersFailBeforePreparingNumericalStorage() throws Exception {
        for (String invalid : List.of("nan", "infinite", "improper", "selector", "negative-time")) {
            var pss = model(false, 2, 1);
            switch (invalid) {
                case "nan" -> pss.getData().setKs1(Double.NaN);
                case "infinite" -> pss.getData().setVsi1max(Double.POSITIVE_INFINITY);
                case "improper" -> pss.getData().setM(0);
                case "selector" -> pss.getData().setIcs2(0);
                case "negative-time" -> pss.getData().setTw3(-1.);
                default -> fail(invalid);
            }
            assertFalse(pss.initStates(pss.getMachine().getDStabBus(), pss.getMachine()), invalid);
        }
    }

    @Test void acceptedVoltageMemoryIsNamedAndAlgebraicEventsDoNotAdvanceIt() throws Exception {
        var pss = model(false, 2, 1);
        pss.getData().setIcs1(6); pss.getData().setIcs2(6);
        var bus = pss.getMachine().getDStabBus();
        bus.setVoltage(new org.apache.commons.math3.complex.Complex(1., 0.));
        assertTrue(pss.initStates(bus, pss.getMachine())); pss.compileRuntimePlan();
        var initial = pss.getNamedStates();
        bus.setVoltage(new org.apache.commons.math3.complex.Complex(.99, 0.));
        pss.acceptVoltageDerivativeEndpoint(.001, .001);
        var accepted = pss.getNamedStates();
        assertEquals(.99, accepted.get("input1VoltageMemory"));
        assertEquals(-2., accepted.get("input1VoltageDerivative"), 1e-12);
        assertEquals(accepted.get("input1VoltageDerivative"), accepted.get("input2VoltageDerivative"));
        assertEquals(0., initial.get("input1VoltageDerivative"), "Named states are immutable snapshots");
        assertThrows(UnsupportedOperationException.class, () -> accepted.put("input1VoltageMemory", 2.));
        bus.setVoltage(new org.apache.commons.math3.complex.Complex(.8, 0.));
        pss.synchronizeVoltageDerivativeMemory();
        assertEquals(.8, pss.getNamedState("input1VoltageMemory"));
        assertEquals(-2., pss.getNamedState("input1VoltageDerivative"), 1e-12);
        var before = snapshot(pss);
        pss.nextStep(0., com.interpss.dstab.algo.DynamicSimuMethod.MODIFIED_EULER, pss.getMachine(), 0);
        pss.nextStep(0., com.interpss.dstab.algo.DynamicSimuMethod.MODIFIED_EULER, pss.getMachine(), 1);
        assertEquals(-2., pss.getInput1VoltageDerivative(), 1e-12);
        assertEquals(.8, pss.getInput2VoltageMemory());
        // Output refresh can change algebraic inputs, but an observation cannot alter states.
        before = snapshot(pss); pss.getOutput(pss.getMachine()); assertEquals(before, snapshot(pss));
    }

    private static List<Long> snapshot(AbstractAnnotateController controller) {
        List<Long> values = new ArrayList<>();
        append(controller, values);
        return values;
    }

    private static void append(AbstractAnnotateController controller, List<Long> values) {
        for (var wrapper : controller.getFieldWrapperList()) {
            if (wrapper.getField() instanceof AbstractAnnotateController child) append(child, values);
            else if (wrapper.getField() instanceof CMLControlBlockAdapter block) {
                appendBlock(block, values);
                if (block instanceof FilterNthOrderBlock compound)
                    for (var inner : compound.getIntegrationBlocks()) appendBlock(inner, values);
            }
        }
    }

    private static void appendBlock(CMLControlBlockAdapter block, List<Long> values) {
        for (double value : new double[]{block.getU(), block.getStateX(), block.getY()})
            values.add(Double.doubleToLongBits(value));
        if (block instanceof CMLControlBlock1stOrderAdapter first)
            values.add(Double.doubleToLongBits(first.getDX_dt()));
        if (block instanceof Pss2aLeadLagBlock terminal)
            values.add(Double.doubleToLongBits(terminal.getStateDerivative()));
    }
}
