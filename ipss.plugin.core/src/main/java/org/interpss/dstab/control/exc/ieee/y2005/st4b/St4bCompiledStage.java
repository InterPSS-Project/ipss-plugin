package org.interpss.dstab.control.exc.ieee.y2005.st4b;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;

import com.interpss.dstab.controller.cml.ICMLMachineVoltageProvider;
import com.interpss.dstab.controller.cml.field.ICMLLimitBlock;
import com.interpss.dstab.controller.cml.field.ICMLStaticBlock;
import com.interpss.dstab.controller.cml.field.adapt.CMLControlBlockAdapter;
import com.interpss.dstab.controller.cml.field.block.DelayControlBlock;
import com.interpss.dstab.controller.cml.field.block.GainBlock;
import com.interpss.dstab.controller.cml.field.func.LowValueFunction;
import com.interpss.dstab.controller.cml.runtime.CmlBlockKernel;
import com.interpss.dstab.controller.cml.runtime.CmlCompiledStage;
import com.interpss.dstab.controller.cml.wrapper.*;
import com.interpss.dstab.datatype.ExpCalculator;

/** Typed fixed wiring, retaining the existing feedback sweeps and block equations. */
final class St4bCompiledStage implements CmlCompiledStage {
    private final IEEE2005ST4BExciter model;
    private final Node[] nodes = new Node[6];
    private final Node[] ordered;
    private final Node[] feedback;
    private final Node[] captured = new Node[4];
    private final double[] inputs = new double[4], states = new double[4], rates = new double[4];
    private final double[] gateInputs = new double[2];
    private final LowValueFunction gateFunction;
    private int capturedCount;
    private final boolean endpointPreparation;
    private static final String[] NAMES = {"trDelayBlock", "vrPIBlock", "taDelayBlock", "kgGainBlock", "vmPIBlock", "customBlock"};
    // BEGIN GENERATED SIGNATURES
    private static final String[] INPUTS = {"+mach.vt", "+this.refPoint-this.trDelayBlock.y+pss.vs+this.vuel", "+this.vrPIBlock.y", "+this.customBlock.y", "+this.taDelayBlock.y-this.kgGainBlock.y", "+this.vmPIBlock.y-this.vmPIBlock.y+this.voelGate.y"};
    private static final String[] GATE_INPUTS = {"this.vmPIBlock.y", "this.voel"};
    // END GENERATED SIGNATURES
    private static final String[] EXPECTED = Arrays.stream(INPUTS).map(St4bCompiledStage::mappedSignature).toArray(String[]::new);

    static St4bCompiledStage tryCreate(IEEE2005ST4BExciter model) {
        if (model.getClass() != IEEE2005ST4BExciter.class) return null;
        try { return new St4bCompiledStage(model); }
        catch (IllegalArgumentException | IllegalStateException unsupported) { return null; }
    }

    private St4bCompiledStage(IEEE2005ST4BExciter model) {
        this.model = model;
        if (!model.hasStandardRectifier() || model.getFieldWrapperList().size() != 7)
            throw new IllegalArgumentException("Changed controller graph");
        var order = new ArrayList<Node>();
        var feedbackOrder = new ArrayList<Node>();
        boolean gateFound = false;
        LowValueFunction boundGate = null;
        for (var field : model.getFieldWrapperList()) {
            if (field.getFieldName().equals("voelGate")) {
                if (field.getClass() != FunctionFieldAnWrapper.class
                        || field.getField().getClass() != LowValueFunction.class
                        || !Arrays.equals(((FunctionFieldAnWrapper<?>)field).getInputs(), GATE_INPUTS))
                    throw new IllegalArgumentException("Changed gate");
                boundGate = (LowValueFunction)field.getField();
                var records = boundGate.getVarRecList();
                if (records.size() != GATE_INPUTS.length) throw new IllegalArgumentException("Changed gate inputs");
                for (int i=0; i<records.size(); i++) {
                    var expected = mapped(GATE_INPUTS[i]).getRecList()[0];
                    if (records.get(i).getSymbolType() != expected.getSymbolType()
                            || !records.get(i).getSymbolStr().equals(expected.getSymbolStr()))
                        throw new IllegalArgumentException("Changed gate binding");
                }
                gateFound = true;
                continue;
            }
            int id = Arrays.asList(NAMES).indexOf(field.getFieldName());
            if (id < 0 || !(field instanceof BlockFieldAnWrapper<?> wrapper))
                throw new IllegalArgumentException("Unexpected field");
            var node = new Node(id, wrapper);
            nodes[id] = node;
            order.add(node);
            if (!node.control && wrapper.isFeedback()) feedbackOrder.add(node);
        }
        if (!gateFound) throw new IllegalArgumentException("Missing gate");
        gateFunction = boundGate;
        for (var node : nodes) if (node == null) throw new IllegalArgumentException("Incomplete graph");
        order.sort(Comparator.comparingInt(n -> n.order));
        ordered = order.toArray(Node[]::new);
        feedback = feedbackOrder.toArray(Node[]::new);
        var unique = java.util.Collections.newSetFromMap(new java.util.IdentityHashMap<Object,Boolean>());
        boolean independent = signature(model.getOutputExp()).equals(mappedSignature("this.customBlock.y"));
        for (var node : nodes) independent &= unique.add(node.block);
        endpointPreparation = independent;
    }

    boolean supportsElectricalEndpointPreparation() {
        if (!endpointPreparation || capturedCount != 0) return false;
        for (var node : nodes)
            if (node.block instanceof ICMLLimitBlock limited && limited.getLimit() != null
                    && (limited.getLimit().getClass() != com.interpss.dstab.datatype.LimitExpression.class
                        || limited.getLimit().getMaxExp() != null || limited.getLimit().getMinExp() != null))
                return false;
        return true;
    }

    private static ExpCalculator mapped(String expression) {
        try {
            var parsed = new ExpCalculator(expression);
            com.interpss.dstab.controller.cml.annotate.util.CMLSymbolMapper.mapExpressionSymbolStr2Type(parsed.getRecList());
            return parsed;
        } catch (com.interpss.common.exp.InterpssException error) { throw new IllegalStateException(error); }
    }
    private static String mappedSignature(String expression) { return signature(mapped(expression)); }
    private static String signature(ExpCalculator expression) {
        var result = new StringBuilder();
        for (var record : expression.getRecList()) result.append(record.opt).append(':')
                .append(record.getSymbolType()).append(':').append(record.getSymbolStr()).append(';');
        return result.toString();
    }

    @Override public void prepare(boolean predictor, double dt, boolean capture) {
        for (var node : ordered) node.prepare(predictor, capture);
        for (var node : feedback) node.prepare(predictor, false);
    }
    @Override public void advance(boolean predictor, double dt) {
        for (int i=0; i<capturedCount; i++)
            captured[i].kernel.advance(predictor, inputs[i], dt, null, null, states, rates, i);
    }
    @Override public void release() {
        Arrays.fill(captured, 0, capturedCount, null);
        capturedCount = 0;
    }
    @Override public boolean supportsObservationPreparation() { return true; }
    private double y(int id) {
        return switch (id) {
            case 0, 2 -> ((DelayControlBlock)nodes[id].block).getY();
            case 1, 4 -> ((Esst4bPIControlBlock)nodes[id].block).getY();
            case 3 -> ((GainBlock)nodes[id].block).getY();
            case 5 -> nodes[id].block.getY();
            default -> throw new IllegalArgumentException("Unknown output slot");
        };
    }
    private double voltage() {
        var machine = model.getMachine();
        return machine instanceof ICMLMachineVoltageProvider provider ? provider.getCmlMachineVoltage()
                : model.getDStabBus().getVoltage().abs()/machine.getVMultiFactor();
    }
    private double stabilizer() {
        var machine = model.getMachine();
        return machine.getStabilizer() == null ? 0. : machine.getStabilizer().getOutput(machine);
    }
    // BEGIN GENERATED ARITHMETIC
    private double input(int id) {
        switch (id) {
            case 0: {
                double v0 = voltage();
                return 0. + v0;
            }
            case 1: {
                double v0 = model.getRefPoint();
                double v1 = y(0);
                double v2 = stabilizer();
                double v3 = model.vuel;
                v1 = -v1;
                return 0. + v0 + v1 + v2 + v3;
            }
            case 2: {
                double v0 = y(1);
                return 0. + v0;
            }
            case 3: {
                double v0 = y(5);
                return 0. + v0;
            }
            case 4: {
                double v0 = y(2);
                double v1 = y(3);
                v1 = -v1;
                return 0. + v0 + v1;
            }
            case 5: {
                double v0 = y(4);
                double v1 = y(4);
                double v2 = gate();
                v1 = -v1;
                return 0. + v0 + v1 + v2;
            }
            default: throw new IllegalArgumentException("Unknown input slot");
        }
    }
    private double gate() {
        gateInputs[0] = y(4);
        gateInputs[1] = model.voel;
        return gateFunction.eval(gateInputs);
    }
    // END GENERATED ARITHMETIC

    private final class Node {
        final int id, order;
        final boolean control;
        final ICMLStaticBlock block;
        final CmlBlockKernel kernel;
        Node(int id, BlockFieldAnWrapper<?> wrapper) {
            this.id = id;
            order = wrapper.getInitOrder();
            control = id == 0 || id == 1 || id == 2 || id == 4;
            if (wrapper.getClass() != (control ? ControlBlockFieldAnWrapper.class : StaticBlockFieldAnWrapper.class)
                    || !signature(wrapper.getInputExp()).equals(EXPECTED[id]))
                throw new IllegalArgumentException("Changed wiring");
            Class<?>[] types = {DelayControlBlock.class, Esst4bPIControlBlock.class, DelayControlBlock.class,
                    GainBlock.class, Esst4bPIControlBlock.class, model.customBlock.getClass()};
            if (wrapper.getField().getClass() != types[id]) throw new IllegalArgumentException("Custom block");
            if (id == 5 && wrapper.getField() != model.customBlock)
                throw new IllegalArgumentException("Changed rectifier owner");
            block = (ICMLStaticBlock)wrapper.getField();
            kernel = CmlBlockKernel.create(block);
            if (block instanceof ICMLLimitBlock limited && limited.getLimit() != null
                    && (limited.getLimit().getClass() != com.interpss.dstab.datatype.LimitExpression.class
                    || limited.getLimit().getMaxExp() != null || limited.getLimit().getMinExp() != null))
                throw new IllegalArgumentException("Custom limit");
        }
        void prepare(boolean predictor, boolean capture) {
            double value = input(id);
            if (control) {
                ((CMLControlBlockAdapter)block).setStageInput(value);
                if (capture) { captured[capturedCount] = this; inputs[capturedCount++] = value; }
            } else if (predictor) block.eulerStep1(value, 0.);
            else block.eulerStep2(value, 0.);
        }
    }
}
