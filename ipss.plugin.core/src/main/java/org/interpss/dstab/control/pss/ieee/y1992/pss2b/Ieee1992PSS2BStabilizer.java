package org.interpss.dstab.control.pss.ieee.y1992.pss2b;

import java.lang.reflect.Field;

import org.interpss.dstab.control.pss.ieee.y1992.pss2a.Pss2aLeadLagBlock;
import org.interpss.dstab.control.pss.ieee.y1992.pss2a.Pss2aWashoutBlock;
import org.interpss.dstab.control.util.IntegrationStepAware;

import com.interpss.dstab.BaseDStabBus;
import com.interpss.dstab.algo.DynamicSimuMethod;
import com.interpss.dstab.controller.cml.annotate.AbstractChildAnnotateController;
import com.interpss.dstab.controller.cml.annotate.AnController;
import com.interpss.dstab.controller.cml.annotate.AnControllerField;
import com.interpss.dstab.controller.cml.annotate.AnnotateStabilizer;
import com.interpss.dstab.controller.cml.field.block.DelayControlBlock;
import com.interpss.dstab.controller.cml.field.block.FilterControlBlock;
import com.interpss.dstab.controller.cml.field.block.FilterNthOrderBlock;
import com.interpss.dstab.controller.cml.field.block.WashoutControlBlock;
import com.interpss.dstab.datatype.CMLFieldEnum;
import com.interpss.dstab.mach.Machine;

/** IEEE dual-input PSS2B stabilizer with the PowerWorld extension fields. */
@AnController(input="mach.speed", output="this.outputBlock.y", refPoint="0.0", display={})
public class Ieee1992PSS2BStabilizer extends AnnotateStabilizer
        implements IntegrationStepAware, org.interpss.dstab.control.util.AcceptedVoltageMeasurement,
        org.interpss.dstab.control.util.StaggeredEulerParticipant {
    private transient Pss2bCompiledStage compiledSignals;
    private transient Pss2bCompiledStage sharedStage;
    private boolean initialized, sharedStageInvalidated, sharedEvaluationActive, predictorPending;
    private long staggeredEvaluations;
    private BaseDStabBus<?, ?> initializedInput1Bus, initializedInput2Bus;
    private int initializedIcs1, initializedIcs2;

    @Override
    protected com.interpss.dstab.controller.cml.runtime.CmlCompiledStage compileStageEvaluator() {
        compiledSignals = null;
        if (getClass() != Ieee1992PSS2BStabilizer.class) return null;
        try { return compiledSignals = new Pss2bCompiledStage(this); }
        catch (IllegalArgumentException unsupportedGraph) { return null; }
    }

    @Override public void invalidateRuntimePlan() {
        super.invalidateRuntimePlan();
        compiledSignals = null;
        if (sharedStage != null) sharedStageInvalidated = true;
    }

    @Override public void setRuntimeExpressionBindingsEnabled(boolean enabled) {
        super.setRuntimeExpressionBindingsEnabled(enabled);
        if (sharedStage != null) sharedStageInvalidated = true;
    }

    @Override public java.util.function.DoubleSupplier bindCompiledParameter(String name)
            throws com.interpss.common.exp.InterpssException {
        if (getClass() != Ieee1992PSS2BStabilizer.class) return super.bindCompiledParameter(name);
        return switch (name) {
            case "input1Signal" -> () -> input1Signal;
            case "input2Signal" -> () -> input2Signal;
            case "ks3" -> () -> ks3;
            case "ks4" -> () -> ks4;
            default -> super.bindCompiledParameter(name);
        };
    }

    public double tw1, tw2, t6;
    @AnControllerField(type=CMLFieldEnum.Controller, input="this.input1Signal",
            y0="0.0", initOrderNumber=-2)
    public InputPath inputPath1 = new InputPath(tw1, tw2, 1.0, t6);

    public double tw3, tw4, t7, ks2;
    @AnControllerField(type=CMLFieldEnum.Controller, input="this.input2Signal",
            y0="0.0", initOrderNumber=-3)
    public InputPath inputPath2 = new InputPath(tw3, tw4, ks2, t7);

    public double t8, t9, ks3;
    public int m, n;
    @AnControllerField(type=CMLFieldEnum.ControlBlock,
            input="this.inputPath1.y + this.ks3*this.inputPath2.y",
            parameter={"this.t8", "this.t9", "this.m", "this.n"},
            y0="this.leadLag1.u0 - this.refPoint + this.inputPath2.y")
    FilterNthOrderBlock rampFilter;

    public double ks1, t1, t2;
    @AnControllerField(type=CMLFieldEnum.ControlBlock,
            input="this.refPoint + this.rampFilter.y - this.ks4*this.inputPath2.y",
            parameter={"type.NoLimit", "this.ks1", "this.t1", "this.t2"},
            y0="this.leadLag2.u0")
    FilterControlBlock leadLag1;

    public double one = 1.0, t3, t4;
    @AnControllerField(type=CMLFieldEnum.ControlBlock, input="this.leadLag1.y",
            parameter={"type.NoLimit", "this.one", "this.t3", "this.t4"},
            y0="this.leadLag3.u0")
    FilterControlBlock leadLag2;

    public double t10, t11;
    @AnControllerField(type=CMLFieldEnum.ControlBlock, input="this.leadLag2.y",
            parameter={"type.NoLimit", "this.one", "this.t10", "this.t11"},
            y0="this.outputBlock.u0")
    FilterControlBlock leadLag3;

    public double a, ta, tb, ks4, vstmax, vstmin;
    @AnControllerField(type=CMLFieldEnum.ControlBlock, input="this.leadLag3.y",
            parameter={"this.a", "this.ta", "this.tb", "this.vstmax", "this.vstmin"},
            y0="pss.vs")
    Pss2aLeadLagBlock outputBlock;

    public double input1Signal;
    public double input2Signal;
    public double vsi1max;
    public double vsi1min;
    public double vsi2max;
    public double vsi2min;

    private BaseDStabBus<?, ?> input1Bus;
    private BaseDStabBus<?, ?> input2Bus;

    /** Include both configured buses so changing a live selector cannot omit frequency history. */
    public java.util.Set<String> requiredBusFrequencyIds() {
        var ids = new java.util.HashSet<String>();
        if (input1Bus != null) ids.add(input1Bus.getId());
        if (input2Bus != null) ids.add(input2Bus.getId());
        return java.util.Set.copyOf(ids);
    }

    /** Standard child paths consume scalar inputs supplied by this controller. */
    public final boolean isBuiltInSignalConditioner(Object controller) {
        return getClass() == Ieee1992PSS2BStabilizer.class && controller != null
                && controller.getClass() == InputPath.class
                && (controller == inputPath1 || controller == inputPath2)
                && ((InputPath) controller).belongsTo(this);
    }

    public void setInputSignalBuses(BaseDStabBus<?, ?> first, BaseDStabBus<?, ?> second) {
        input1Bus = java.util.Objects.requireNonNull(first);
        input2Bus = java.util.Objects.requireNonNull(second);
    }

    private final org.interpss.dstab.control.pss.psse.ieeest.IeeestVoltageDerivative derivative1 =
            new org.interpss.dstab.control.pss.psse.ieeest.IeeestVoltageDerivative();
    private final org.interpss.dstab.control.pss.psse.ieeest.IeeestVoltageDerivative derivative2 =
            new org.interpss.dstab.control.pss.psse.ieeest.IeeestVoltageDerivative();
    private double integrationStep;
    private double minimumTimeConstantMultiplier = 1.0;

    public Ieee1992PSS2BStabilizer() {
        this("id", "PSS2B", "IEEE-1992");
    }

    public Ieee1992PSS2BStabilizer(String id, String name, String category) {
        super(id, name, category);
        _data = new Ieee1992PSS2BStabilizerData();
    }

    public Ieee1992PSS2BStabilizerData getData() {
        return (Ieee1992PSS2BStabilizerData) _data;
    }

    /** Diagram signals corresponding to PowerWorld PSS2B states 1-8 and 19-20. */
    public double getInput1Washout1Output() {
        return observedChildSignal(0, inputPath1, "this.firstWashout.y");
    }

    public double getInput1Washout2Output() {
        return observedChildSignal(1, inputPath1, "this.secondWashout.y");
    }

    public double getInput1TransducerOutput() {
        return observedChildSignal(2, inputPath1, "this.delayBlock.y");
    }

    public double getInput2Washout1Output() {
        return observedChildSignal(3, inputPath2, "this.firstWashout.y");
    }

    public double getInput2Washout2Output() {
        return observedChildSignal(4, inputPath2, "this.secondWashout.y");
    }

    public double getInput2TransducerOutput() {
        return observedChildSignal(5, inputPath2, "this.delayBlock.y");
    }

    public double getLeadLag1Output() { return observedSignal(7, "this.leadLag1.y"); }
    public double getLeadLag2Output() { return observedSignal(8, "this.leadLag2.y"); }
    public double getRampTrackingOutput() { return observedSignal(6, "this.rampFilter.y"); }
    public double getGeLeadLagOutput() { return observedSignal(10, "this.outputBlock.y"); }
    public double getLeadLag3Output() { return observedSignal(9, "this.leadLag3.y"); }

    private double observedSignal(int id, String name) {
        var shared = sharedObservation();
        if (shared != null) return shared.signal(id);
        return compiledSignals == null ? signal(name) : compiledSignals.signal(id);
    }

    private double observedChildSignal(int id, InputPath owner, String name) {
        var shared = sharedObservation();
        if (shared != null) return shared.signal(id);
        return compiledSignals == null ? childSignal(owner, name) : compiledSignals.signal(id);
    }

    private Pss2bCompiledStage sharedObservation() {
        if (!sharedEvaluationActive) return null;
        var plan = sharedStage();
        // Accepted measurement callbacks and live mixing/setpoints can change
        // feedthrough after the last rate evaluation. Refresh equations only.
        plan.refreshSharedAlgebraic();
        return plan;
    }

    private double signal(String fieldName) {
        try {
            return getFieldVaule(fieldName);
        } catch (Exception ex) {
            throw new IllegalStateException("Cannot read PSS2B signal " + fieldName, ex);
        }
    }

    private double childSignal(InputPath child, String fieldName) {
        try {
            return child.getFieldVaule(fieldName);
        } catch (Exception ex) {
            throw new IllegalStateException(
                    "Cannot read PSS2B child signal " + fieldName, ex);
        }
    }

    @Override
    public void configureIntegrationStep(double timeStepSec) {
        configureIntegrationStep(timeStepSec, 1.0);
    }

    public void configureIntegrationStep(double timeStepSec, double multiplier) {
        if (!Double.isFinite(timeStepSec) || timeStepSec < 0.0
                || !Double.isFinite(multiplier) || multiplier < 0.0) {
            throw new IllegalArgumentException("PSS2B integration-step settings must be finite and non-negative");
        }
        integrationStep = timeStepSec;
        minimumTimeConstantMultiplier = multiplier;
    }

    @Override
    public boolean initStates(BaseDStabBus<?, ?> bus, Machine machine) {
        initialized = false; sharedStage = null; sharedStageInvalidated = sharedEvaluationActive = predictorPending = false;
        staggeredEvaluations = 0;
        compiledSignals = null;
        Ieee1992PSS2BStabilizerData d = getData();
        tw1 = d.getTw1(); tw2 = d.getTw2(); t6 = d.getT6();
        tw3 = d.getTw3(); tw4 = d.getTw4(); t7 = d.getT7();
        ks2 = d.getKs2(); ks3 = d.getKs3(); t8 = d.getT8(); t9 = d.getT9();
        ks1 = d.getKs1(); t1 = d.getT1(); t2 = d.getT2();
        t3 = d.getT3(); t4 = d.getT4(); t10 = d.getT10(); t11 = d.getT11();
        vsi1max = d.getVsi1max(); vsi1min = d.getVsi1min();
        vsi2max = d.getVsi2max(); vsi2min = d.getVsi2min();
        vstmax = d.getVstmax(); vstmin = d.getVstmin();
        a = d.getA(); ta = d.getTa(); tb = d.getTb(); ks4 = d.getKs4();
        m = d.getM(); n = d.getN();
        if (d.getIcs1() < 1 || d.getIcs1() > 6 || d.getIcs2() < 1 || d.getIcs2() > 6
                || m < 0 || n < 0 || (m == 0 && n > 0)
                || java.util.stream.DoubleStream.of(tw1,tw2,t6,tw3,tw4,t7,ks2,ks3,t8,t9,ks1,
                        t1,t2,t3,t4,t10,t11,vsi1max,vsi1min,vsi2max,vsi2min,vstmax,vstmin,a,ta,tb,ks4)
                        .anyMatch(value -> !Double.isFinite(value))
                || java.util.stream.DoubleStream.of(tw1,tw2,t6,tw3,tw4,t7,t8,t9,t2,t4,t11,tb)
                        .anyMatch(value -> value < 0.)
                || (n > 0 && t9 == 0. && t8 != 0.) || (tb == 0. && ta != 0.)) return false;
        applyPowerWorldCorrections();

        if (input1Bus == null) input1Bus = bus;
        if (input2Bus == null) input2Bus = bus;
        if (!input1Bus.isActive() || !input2Bus.isActive()) return false;
        input1Signal = limitedInput(d.getIcs1(), input1Bus, machine, vsi1max, vsi1min);
        input2Signal = limitedInput(d.getIcs2(), input2Bus, machine, vsi2max, vsi2min);
        derivative1.initialize(input1Bus.getVoltageMag());
        derivative2.initialize(input2Bus.getVoltageMag());
        initializedInput1Bus = input1Bus; initializedInput2Bus = input2Bus;
        initializedIcs1 = d.getIcs1(); initializedIcs2 = d.getIcs2();
        inputPath1 = new InputPath(tw1, tw2, 1.0, t6);
        inputPath2 = new InputPath(tw3, tw4, ks2, t7);
        initialized = super.initStates(bus, machine);
        return initialized;
    }

    @Override
    public boolean nextStep(double dt, DynamicSimuMethod method, Machine machine, int flag) {
        refreshInputSignals(machine);
        return super.nextStep(dt, method, machine, flag);
    }

    private void refreshInputSignals(Machine machine) {
        Ieee1992PSS2BStabilizerData d = getData();
        input1Signal = d.getIcs1() == 6
                ? clamp(derivative1.getDerivative(), vsi1max, vsi1min)
                : limitedInput(d.getIcs1(), input1Bus, machine, vsi1max, vsi1min);
        input2Signal = d.getIcs2() == 6
                ? clamp(derivative2.getDerivative(), vsi2max, vsi2min)
                : limitedInput(d.getIcs2(), input2Bus, machine, vsi2max, vsi2min);
    }

    @Override public boolean eulerStep(boolean predictor, double dt, Machine machine) {
        sharedEvaluationActive = false;
        boolean result = super.eulerStep(predictor, dt, machine);
        if (result) acceptCompatibleStage(predictor, dt);
        return result;
    }

    /** Both ordinary calls and prepared-stage dispatch share the same pending-history guard. */
    void acceptCompatibleStage(boolean predictor, double dt) {
        sharedEvaluationActive = false;
        if (dt > 0) predictorPending = predictor;
    }

    private Pss2bCompiledStage sharedStage() {
        if (getClass() != Ieee1992PSS2BStabilizer.class || !initialized || predictorPending || sharedStageInvalidated
                || getRuntimePhase() != RuntimePhase.IDLE)
            throw new IllegalStateException("Initialize PSS2B and finish any predictor before shared state access; reinitialize after structural changes");
        if (input1Bus != initializedInput1Bus || input2Bus != initializedInput2Bus
                || getData().getIcs1() != initializedIcs1 && (getData().getIcs1() == 6 || initializedIcs1 == 6)
                || getData().getIcs2() != initializedIcs2 && (getData().getIcs2() == 6 || initializedIcs2 == 6))
            throw new IllegalStateException("Reinitialize PSS2B after changing signal buses or sampled-derivative selectors");
        if (sharedStage == null) sharedStage = compiledSignals != null ? compiledSignals : new Pss2bCompiledStage(this);
        sharedStage.checkSharedOwners();
        return sharedStage;
    }

    @Override public int staggeredStateCount() { return sharedStage().sharedStateCount(); }
    @Override public void readStaggeredState(double[] values, int offset) { sharedStage().readSharedState(values, offset); }
    @Override public void evaluateStaggered(double stepSec, double[] derivatives, int offset) {
        requireSharedStep(stepSec);
        var plan = sharedStage();
        refreshInputSignals(getMachine());
        plan.evaluateShared(derivatives, offset);
        sharedEvaluationActive = true;
        staggeredEvaluations++;
    }
    @Override public void applyStaggeredState(double[] values, int offset, double stepSec) {
        requireSharedStep(stepSec);
        sharedStage().applySharedState(values, offset);
        sharedEvaluationActive = true;
    }
    @Override public boolean averageStaggeredRatesAtDiscontinuity() {
        return getData().getIcs1() != 6 && getData().getIcs2() != 6;
    }
    @Override public boolean averageStaggeredRateAtDiscontinuity(int stateIndex) {
        java.util.Objects.checkIndex(stateIndex, staggeredStateCount());
        // Input conditioners retain their own measurement history. A sampled
        // derivative in one path cannot suppress the other path's event handling.
        if (stateIndex < 3) return getData().getIcs1() != 6;
        if (stateIndex < 6) return getData().getIcs2() != 6;
        return averageStaggeredRatesAtDiscontinuity();
    }
    private static void requireSharedStep(double stepSec) {
        if (!Double.isFinite(stepSec) || stepSec < 0) throw new IllegalArgumentException("Finite nonnegative PSS2B interval required");
    }
    public long getStaggeredEvaluationCount() { return staggeredEvaluations; }
    @Override public double getOutput(Machine machine) {
        var shared = sharedObservation();
        return shared == null ? super.getOutput(machine) : shared.signal(10);
    }

    @Override public void acceptVoltageDerivativeEndpoint(double time, double step) {
        if (getData().getIcs1() == 6) {
            derivative1.accept(time, input1Bus.getVoltageMag(), step);
            input1Signal = clamp(derivative1.getDerivative(), vsi1max, vsi1min);
        }
        if (getData().getIcs2() == 6) {
            derivative2.accept(time, input2Bus.getVoltageMag(), step);
            input2Signal = clamp(derivative2.getDerivative(), vsi2max, vsi2min);
        }
    }

    @Override public void synchronizeVoltageDerivativeMemory() {
        if (getData().getIcs1() == 6) derivative1.synchronizeVoltage(input1Bus.getVoltageMag());
        if (getData().getIcs2() == 6) derivative2.synchronizeVoltage(input2Bus.getVoltageMag());
    }

    public double getInput1VoltageMemory() { return derivative1.getVoltage(); }
    public double getInput2VoltageMemory() { return derivative2.getVoltage(); }
    public double getInput1VoltageDerivative() { return derivative1.getDerivative(); }
    public double getInput2VoltageDerivative() { return derivative2.getDerivative(); }

    @Override public java.util.Map<String, Double> getNamedStates() {
        var states = new java.util.LinkedHashMap<>(super.getNamedStates());
        states.put("input1VoltageMemory", derivative1.getVoltage());
        states.put("input1VoltageDerivative", derivative1.getDerivative());
        states.put("input2VoltageMemory", derivative2.getVoltage());
        states.put("input2VoltageDerivative", derivative2.getDerivative());
        return java.util.Collections.unmodifiableMap(states);
    }

    private void applyPowerWorldCorrections() {
        double minimum = minimumTimeConstantMultiplier * integrationStep;
        tw1 = minimumPositive(tw1, minimum);
        tw3 = minimumPositive(tw3, minimum);
        tw2 = halfStepBypass(tw2, minimum);
        tw4 = halfStepBypass(tw4, minimum);
        t6 = halfStepBypass(t6, minimum);
        t7 = halfStepBypass(t7, minimum);
        t9 = halfStepBypass(t9, minimum);
        tb = halfStepBypass(tb, minimum);
        t2 = quarterStepBypass(t2, minimum);
        t4 = quarterStepBypass(t4, minimum);
        t11 = quarterStepBypass(t11, minimum);
        double[] outputLimits = signedOutputLimits(vstmax, vstmin);
        vstmax = outputLimits[0]; vstmin = outputLimits[1];
        double[] input1Limits = orderedLimits(vsi1max, vsi1min);
        vsi1max = input1Limits[0]; vsi1min = input1Limits[1];
        double[] input2Limits = orderedLimits(vsi2max, vsi2min);
        vsi2max = input2Limits[0]; vsi2min = input2Limits[1];
    }

    private static double limitedInput(int code, BaseDStabBus<?, ?> bus, Machine machine,
            double max, double min) {
        double value = switch (code) {
            case 1 -> machine.getSpeed() - 1.0;
            case 2 -> bus.getFreq() - 1.0;
            case 3 -> machine.getPe();
            case 4 -> machine.getPm() - machine.getPe();
            case 5 -> bus.getVoltageMag();
            case 6 -> 0.0;
            default -> throw new IllegalArgumentException("Unsupported PSS2B input code: " + code);
        };
        return clamp(value, max, min);
    }

    private static double clamp(double value, double max, double min) {
        return Math.max(min, Math.min(max, value));
    }

    private static double minimumPositive(double value, double minimum) {
        return value > 0.0 && value < minimum ? minimum : value;
    }

    private static double halfStepBypass(double value, double minimum) {
        if (value > 0.0 && value < 0.5 * minimum) return 0.0;
        if (value > 0.5 * minimum && value < minimum) return minimum;
        return value;
    }

    private static double quarterStepBypass(double value, double minimum) {
        if (value > 0.0 && value < 0.125 * minimum) return 0.0;
        if (value > 0.125 * minimum && value < 0.25 * minimum) return 0.25 * minimum;
        return value;
    }

    private static double[] orderedLimits(double max, double min) {
        return max >= min ? new double[] {max, min} : new double[] {min, max};
    }

    private static double[] signedOutputLimits(double max, double min) {
        double[] ordered = orderedLimits(max, min);
        return new double[] {Math.abs(ordered[0]), -Math.abs(ordered[1])};
    }

    @AnController(output="this.delayBlock.y", refPoint="0.0")
    public class InputPath extends AbstractChildAnnotateController {
        boolean belongsTo(Ieee1992PSS2BStabilizer owner) { return Ieee1992PSS2BStabilizer.this == owner; }
        @Override public boolean supportsCompiledOutputScheduling() {
            return getClass() == InputPath.class
                    && Ieee1992PSS2BStabilizer.this.getClass() == Ieee1992PSS2BStabilizer.class
                    && eAdapters().isEmpty() && Ieee1992PSS2BStabilizer.this.eAdapters().isEmpty();
        }

        public double one = 1.0, firstWashoutTime;
        @AnControllerField(type=CMLFieldEnum.ControlBlock,
                input="this.input - this.refPoint",
                parameter={"type.NoLimit", "this.one", "this.firstWashoutTime"},
                y0="this.secondWashout.u0")
        WashoutControlBlock firstWashout;

        public double secondWashoutTime;
        @AnControllerField(type=CMLFieldEnum.ControlBlock, input="this.firstWashout.y",
                parameter={"this.one", "this.secondWashoutTime"}, y0="this.delayBlock.u0")
        Pss2aWashoutBlock secondWashout;

        public double gain, transducerTime;
        @AnControllerField(type=CMLFieldEnum.ControlBlock, input="this.secondWashout.y",
                parameter={"type.NoLimit", "this.gain", "this.transducerTime"}, y0="this.output")
        DelayControlBlock delayBlock;

        InputPath(double firstWashoutTime, double secondWashoutTime,
                double gain, double transducerTime) {
            this.firstWashoutTime = firstWashoutTime;
            this.secondWashoutTime = secondWashoutTime;
            this.gain = gain;
            this.transducerTime = transducerTime;
        }

        @Override public AnController getAnController() { return getClass().getAnnotation(AnController.class); }
        @Override public Field getField(String name) throws Exception { return getClass().getField(name); }
        @Override public Object getFieldObject(Field field) throws Exception { return field.get(this); }
    }

    @Override public AnController getAnController() { return getClass().getAnnotation(AnController.class); }
    @Override public Field getField(String name) throws Exception { return getClass().getField(name); }
    @Override public Object getFieldObject(Field field) throws Exception { return field.get(this); }
}
