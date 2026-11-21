package org.interpss.core.dstab.reference;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.Supplier;

import com.interpss.dstab.cache.StateMonitor;
import com.interpss.dstab.common.DStabOutSymbol;
import com.interpss.dstab.datatype.DStabSimuEvent;

/**
 * Captures custom states synchronously with a reference bus's output callback.
 * Retains the solver's raw callback time alongside the physical sample time.
 * A legacy fixed-grid callback labels the integrated endpoint with the step
 * start. The exact solver labels the physical endpoint directly and emits
 * both sides of an event at the same time. Requires output on every step.
 */
public final class EndpointAlignedStateMonitor extends StateMonitor {
    public enum SampleKind { INITIAL, EVENT_PRE, STEP_END, EVENT_POST }

    public record Frame(double time, double solverTime, SampleKind kind, double[] states) {
        public Frame { states = states.clone(); }
        @Override public double[] states() { return states.clone(); }
    }

    private record Pending(double solverTime, double[] states) { }
    private final String referenceBus;
    private final List<Frame> frames = new ArrayList<>();
    private final List<Pending> pending = new ArrayList<>();
    private Boolean exactLabels;
    private Supplier<double[]> capture;
    private int width;
    private double start;
    private double step;
    private boolean stepping;
    private java.util.Set<String> additionalFrequencyInputs;

    public EndpointAlignedStateMonitor(String referenceBus) {
        this.referenceBus = Objects.requireNonNull(referenceBus);
    }

    /** Declare all extra frequency reads made by the custom capture, before initialization. */
    public void declareAdditionalFrequencyInputs(java.util.Set<String> busIds) {
        if(capture!=null)throw new IllegalStateException("Declare frequency inputs before capture initialization");
        additionalFrequencyInputs=java.util.Set.copyOf(busIds);
    }

    @Override public java.util.Set<String> requiredBusFrequencyIds() {
        if(additionalFrequencyInputs==null)return null;
        var ids=new java.util.HashSet<>(additionalFrequencyInputs);ids.addAll(getOutputVarIdList());
        return java.util.Set.copyOf(ids);
    }

    /** Call after successful simulation initialization, before the first step. */
    public void initializeStateCapture(double time, Supplier<double[]> capture) {
        if (this.capture != null || !Double.isFinite(time))
            throw new IllegalStateException("Capture must be initialized exactly once at a finite time");
        var records = getBusVoltTable().get(referenceBus);
        if (records == null || records.size() != 1 || !sameTime(records.get(0).t, time))
            throw new IllegalStateException("Expected exactly one reference-bus initialization sample");
        this.capture = Objects.requireNonNull(capture);
        double[] values = capture.get().clone();
        width = values.length;
        requireFinite(values);
        frames.add(new Frame(time, time, SampleKind.INITIAL, values));
    }

    public void beginStep(double time, double step) {
        if (capture == null || stepping || !Double.isFinite(time)
                || !Double.isFinite(step) || step <= 0 || !Double.isFinite(time + step)
                || !sameTime(frames.get(frames.size()-1).time(), time))
            throw new IllegalStateException("Invalid or noncontiguous observation step");
        start = time;
        this.step = step;
        stepping = true;
        pending.clear();
    }

    @Override public boolean onSimuEvent(DStabSimuEvent event) {
        boolean accepted = super.onSimuEvent(event);
        if (capture != null && event.getType() == DStabSimuEvent.PlotStepBusStates
                && referenceBus.equals(event.getHashtableData().get(DStabOutSymbol.OUT_SYMBOL_BUS_ID))) {
            if (!accepted || !stepping)
                throw new IllegalStateException("Unexpected reference-bus callback outside an active step");
            double time = ((Number)event.getHashtableData().get(DStabOutSymbol.OUT_SYMBOL_TIME)).doubleValue();
            if (!Double.isFinite(time) || time < start - 1e-9 || time > start + step + 1e-9
                    || pending.size() >= 4
                    || (!pending.isEmpty() && time < pending.getLast().solverTime() - 1e-9))
                throw new IllegalStateException("Unsupported solver output timestamp or callback count");
            double[] values = capture.get().clone();
            if (values.length != width) throw new IllegalStateException("State channel count changed");
            requireFinite(values);
            pending.add(new Pending(time, values));
        }
        return accepted;
    }

    /** Complete only after solveDEqnStep(true) returns successfully. */
    public void endStep(double time) {
        if (!stepping || !Double.isFinite(time) || time <= start
                || time > start + step + 1e-9 || pending.isEmpty())
            throw new IllegalStateException("Missing endpoint output or unexpected final simulation clock");
        Pending last = pending.getLast();
        // The final callback always belongs to the integrated endpoint. Its
        // raw label distinguishes exact and legacy solvers on the first call.
        boolean exact = sameTime(last.solverTime(), time)
                && (!sameTime(last.solverTime(), start) || last.solverTime() == time);
        if (exactLabels != null && exact != exactLabels)
            throw new IllegalStateException("Solver callback clock changed during the run");
        if (exact) {
            int atStart = 0;
            while (atStart < pending.size() && sameTime(pending.get(atStart).solverTime(), start)
                    && pending.get(atStart).solverTime() != time) atStart++;
            int atEnd = pending.size() - atStart;
            if ((atStart != 0 && atStart != 2) || (atEnd != 1 && atEnd != 2)
                    || pending.subList(atStart, pending.size()).stream()
                            .anyMatch(sample -> !sameTime(sample.solverTime(), time)))
                throw new IllegalStateException("Expected an endpoint or a paired exact event boundary");
            if (atStart == 2) appendEventPair(start, 0);
            if (atEnd == 2) appendEventPair(time, atStart);
            else frames.add(new Frame(time, last.solverTime(), SampleKind.STEP_END, last.states()));
        } else {
            if (!sameTime(time, start + step) || pending.size() > 2
                    || pending.stream().anyMatch(sample -> !sameTime(sample.solverTime(), start)))
                throw new IllegalStateException("Expected a fixed-grid endpoint with legacy step-start labels");
            if (pending.size() == 2) {
                Pending before = pending.getFirst();
                frames.add(new Frame(start, before.solverTime(), SampleKind.EVENT_PRE, before.states()));
            }
            frames.add(new Frame(time, last.solverTime(), SampleKind.STEP_END, last.states()));
        }
        exactLabels = exact;
        if (getBusVoltTable().get(referenceBus).size() != frames.size())
            throw new IllegalStateException("Reference-bus and custom-state samples are not one-to-one");
        stepping = false;
        pending.clear();
    }

    private void appendEventPair(double time, int first) {
        Pending pre = pending.get(first);
        Pending post = pending.get(first + 1);
        frames.add(new Frame(time, pre.solverTime(), SampleKind.EVENT_PRE, pre.states()));
        frames.add(new Frame(time, post.solverTime(), SampleKind.EVENT_POST, post.states()));
    }

    public List<Frame> frames() {
        if (stepping) throw new IllegalStateException("Cannot export an incomplete step");
        return List.copyOf(frames);
    }

    private static boolean sameTime(double a, double b) { return Math.abs(a-b) <= 1.e-9; }
    private static void requireFinite(double[] values) {
        for (double value : values)
            if (!Double.isFinite(value)) throw new IllegalStateException("Nonfinite captured state");
    }
}
