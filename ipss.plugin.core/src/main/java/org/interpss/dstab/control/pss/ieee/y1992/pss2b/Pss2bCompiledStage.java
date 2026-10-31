package org.interpss.dstab.control.pss.ieee.y1992.pss2b;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Set;
import java.util.Collections;

import org.interpss.dstab.control.pss.ieee.y1992.pss2a.Pss2aLeadLagBlock;
import org.interpss.dstab.control.pss.ieee.y1992.pss2a.Pss2aWashoutBlock;

import com.interpss.dstab.controller.cml.annotate.AbstractAnnotateController;
import com.interpss.dstab.controller.cml.field.ICMLLimitBlock;
import com.interpss.dstab.controller.cml.field.adapt.CMLControlBlockAdapter;
import com.interpss.dstab.controller.cml.field.block.DelayControlBlock;
import com.interpss.dstab.controller.cml.field.block.FilterControlBlock;
import com.interpss.dstab.controller.cml.field.block.FilterNthOrderBlock;
import com.interpss.dstab.controller.cml.field.block.WashoutControlBlock;
import com.interpss.dstab.controller.cml.runtime.CmlBlockKernel;
import com.interpss.dstab.controller.cml.runtime.CmlCompiledStage;
import com.interpss.dstab.controller.cml.wrapper.BlockFieldAnWrapper;
import com.interpss.dstab.controller.cml.wrapper.ControlBlockFieldAnWrapper;
import com.interpss.dstab.controller.cml.wrapper.ControllerFieldAnWrapper;
import com.interpss.dstab.datatype.ExpCalculator;

/** Typed wiring for the standard graph; existing kernels own numerical states and history. */
final class Pss2bCompiledStage implements CmlCompiledStage {
    private record Node(int id, CMLControlBlockAdapter block, CmlBlockKernel kernel) { }
    private record Entry(BlockFieldAnWrapper<?> wrapper, int child, Node node) { }
    private final Ieee1992PSS2BStabilizer model;
    private final Node[] nodes = new Node[11];
    private final Node[][] children = new Node[2][];
    private final Entry[] entries;
    private final Node[] captured = new Node[11];
    private final double[] inputs = new double[11], states = new double[11], rates = new double[11];
    private int size;

    Pss2bCompiledStage(Ieee1992PSS2BStabilizer model) {
        this.model = model;
        if (!model.eAdapters().isEmpty() || !model.inputPath1.belongsTo(model) || !model.inputPath2.belongsTo(model))
            throw unsupported();
        Set<Object> owned = Collections.newSetFromMap(new IdentityHashMap<>());
        children[0] = bindChild(model.inputPath1, 0, owned);
        children[1] = bindChild(model.inputPath2, 3, owned);
        List<Entry> order = new ArrayList<>();
        for (var field : model.getFieldWrapperList()) {
            if (!(field instanceof BlockFieldAnWrapper<?> wrapper)) throw unsupported();
            if (wrapper.isDebug()) throw unsupported();
            String name = wrapper.getFieldName();
            if (name.equals("inputPath1") || name.equals("inputPath2")) {
                int child = name.equals("inputPath1") ? 0 : 1;
                if (wrapper.getClass() != ControllerFieldAnWrapper.class
                        || wrapper.getField() != (child == 0 ? model.inputPath1 : model.inputPath2))
                    throw unsupported();
                expression(wrapper, new String[]{child == 0 ? "this.input1Signal" : "this.input2Signal"},
                        new ExpCalculator.Opt[]{ExpCalculator.Opt.Add});
                order.add(new Entry(wrapper, child, null));
                continue;
            }
            int id = switch (name) {
                case "rampFilter" -> 6;
                case "leadLag1" -> 7;
                case "leadLag2" -> 8;
                case "leadLag3" -> 9;
                case "outputBlock" -> 10;
                default -> throw unsupported();
            };
            Class<?> type = id == 6 ? FilterNthOrderBlock.class
                    : id == 10 ? Pss2aLeadLagBlock.class : FilterControlBlock.class;
            Node node = bind(wrapper, id, type, owned);
            if (id == 6) expression(wrapper,
                    new String[]{"this.inputPath1.y", "this.ks3", "this.inputPath2.y"},
                    new ExpCalculator.Opt[]{ExpCalculator.Opt.Add, ExpCalculator.Opt.Add, ExpCalculator.Opt.Mul});
            else if (id == 7) expression(wrapper,
                    new String[]{"this.refPoint", "this.rampFilter.y", "this.ks4", "this.inputPath2.y"},
                    new ExpCalculator.Opt[]{ExpCalculator.Opt.Add, ExpCalculator.Opt.Add, ExpCalculator.Opt.Sub, ExpCalculator.Opt.Mul});
            else expression(wrapper, new String[]{"this." + (id == 8 ? "leadLag1" : id == 9 ? "leadLag2" : "leadLag3") + ".y"},
                    new ExpCalculator.Opt[]{ExpCalculator.Opt.Add});
            order.add(new Entry(wrapper, -1, node));
        }
        if (order.size() != 7) throw unsupported();
        for (Node node : nodes) if (node == null) throw unsupported();
        order.sort(Comparator.comparingInt(entry -> entry.wrapper().getInitOrder()));
        entries = order.toArray(Entry[]::new);
    }

    private Node[] bindChild(AbstractAnnotateController child, int base, Set<Object> owned) {
        if (child.getClass() != Ieee1992PSS2BStabilizer.InputPath.class
                || !child.eAdapters().isEmpty() || !owned.add(child)) throw unsupported();
        List<BlockFieldAnWrapper<?>> order = new ArrayList<>();
        for (var field : child.getFieldWrapperList()) {
            if (!(field instanceof BlockFieldAnWrapper<?> wrapper)) throw unsupported();
            int local = switch (wrapper.getFieldName()) {
                case "firstWashout" -> 0;
                case "secondWashout" -> 1;
                case "delayBlock" -> 2;
                default -> throw unsupported();
            };
            bind(wrapper, base + local, local == 0 ? WashoutControlBlock.class
                    : local == 1 ? Pss2aWashoutBlock.class : DelayControlBlock.class, owned);
            if (local == 0) expression(wrapper, new String[]{"this.input", "this.refPoint"},
                    new ExpCalculator.Opt[]{ExpCalculator.Opt.Add, ExpCalculator.Opt.Sub});
            else expression(wrapper, new String[]{local == 1 ? "this.firstWashout.y" : "this.secondWashout.y"},
                    new ExpCalculator.Opt[]{ExpCalculator.Opt.Add});
            order.add(wrapper);
        }
        if (order.size() != 3) throw unsupported();
        order.sort(Comparator.comparingInt(BlockFieldAnWrapper::getInitOrder));
        return order.stream().map(wrapper -> nodes[base + switch (wrapper.getFieldName()) {
            case "firstWashout" -> 0; case "secondWashout" -> 1; default -> 2;
        }]).toArray(Node[]::new);
    }

    private Node bind(BlockFieldAnWrapper<?> wrapper, int id, Class<?> type, Set<Object> owned) {
        if (wrapper.getClass() != ControlBlockFieldAnWrapper.class || wrapper.isFeedback() || wrapper.isDebug()
                || wrapper.getField().getClass() != type || !owned.add(wrapper.getField()) || nodes[id] != null)
            throw unsupported();
        var block = (CMLControlBlockAdapter) wrapper.getField();
        if (block instanceof ICMLLimitBlock limited && limited.getLimit() != null
                && (limited.getLimit().getClass() != com.interpss.dstab.datatype.LimitExpression.class
                    || limited.getLimit().getMaxExp() != null || limited.getLimit().getMinExp() != null))
            throw unsupported();
        return nodes[id] = new Node(id, block, CmlBlockKernel.create(block));
    }

    private static void expression(BlockFieldAnWrapper<?> wrapper, String[] symbols, ExpCalculator.Opt[] operations) {
        var records = wrapper.getInputExp().getRecList();
        if (records.length != symbols.length) throw unsupported();
        for (int i = 0; i < records.length; i++)
            if (!symbols[i].equals(records[i].getSymbolStr()) || records[i].opt != operations[i]) throw unsupported();
    }

    private static IllegalArgumentException unsupported() { return new IllegalArgumentException("Nonstandard PSS2B graph"); }
    private double y(int id) { return nodes[id].kernel().getAsDouble(); }
    double signal(int id) { return y(id); }

    private double input(int id) {
        if (id < 6) {
            var child = id < 3 ? model.inputPath1 : model.inputPath2;
            return id % 3 == 0 ? (0. + child.getRuntimeInputValue()) + (-child.getRefPoint()) : 0. + y(id - 1);
        }
        if (id == 6) {
            double first = model.inputPath1.getRuntimeOutputValue(), gain = model.ks3,
                    second = model.inputPath2.getRuntimeOutputValue();
            double sum = 0.; sum += first; sum += 0.; sum += gain * second; return sum;
        }
        if (id == 7) {
            double reference = model.getRefPoint(), filtered = y(6), gain = model.ks4,
                    second = model.inputPath2.getRuntimeOutputValue();
            double sum = 0.; sum += reference; sum += filtered; sum += 0.; sum += (-gain) * second; return sum;
        }
        return 0. + y(id - 1);
    }

    private void prepare(Node node, boolean capture) {
        double input = input(node.id());
        node.block().setStageInput(input);
        if (capture) { captured[size] = node; inputs[size++] = input; }
    }

    @Override public void prepare(boolean predictor, double dt, boolean capture) {
        if (capture) size = 0;
        for (Entry entry : entries) {
            if (entry.child() < 0) prepare(entry.node(), capture);
            else {
                var child = entry.child() == 0 ? model.inputPath1 : model.inputPath2;
                child.setRuntimeInputValue(0. + (entry.child() == 0 ? model.input1Signal : model.input2Signal));
                for (Node node : children[entry.child()]) prepare(node, capture);
                child.setRuntimeOutputValue(child.preparedRuntimeOutput(model.getMachine()));
            }
        }
    }

    @Override public void advance(boolean predictor, double dt) {
        for (int i = 0; i < size; i++) captured[i].kernel().advance(predictor, inputs[i], dt, null, null, states, rates, i);
    }

    @Override public void release() { size = 0; }
    @Override public boolean supportsObservationPreparation() { return true; }
}
