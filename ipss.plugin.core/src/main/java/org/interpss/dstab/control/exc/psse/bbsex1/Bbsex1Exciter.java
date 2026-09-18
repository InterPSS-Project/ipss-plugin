package org.interpss.dstab.control.exc.psse.bbsex1;

import java.lang.reflect.Field;

import org.interpss.dstab.control.util.IntegrationStepAware;

import com.interpss.dstab.BaseDStabBus;
import com.interpss.dstab.algo.DynamicSimuMethod;
import com.interpss.dstab.controller.cml.annotate.AnController;
import com.interpss.dstab.controller.cml.annotate.AnnotateExciter;
import com.interpss.dstab.mach.Machine;

/** PowerWorld/PSS/E BBSEX1 transformer-fed static excitation system. */
@AnController(input = "mach.vt", output = "this.outputSignal",
        refPoint = "this.reference", display = {})
public final class Bbsex1Exciter extends AnnotateExciter implements IntegrationStepAware {
    public static final int SUPPLEMENT_AT_ERROR = 0;
    public static final int SUPPLEMENT_AT_OUTPUT = 1;
    private static final double EPS = 1.0e-12;
    private static final int SENSED = 0;
    private static final int LEAD_LAG = 1;
    private static final int INVERSE_FEEDBACK = 2;

    private final Bbsex1Data data;
    private final double[] state = new double[3];
    private final double[] trial = new double[3];
    private final double[] oldDerivative = new double[3];
    private double[] active = state;
    private double integrationStep;
    private double minimumTimeConstantMultiplier = 1.0;
    private boolean initialized;
    private double vuel;
    private double voel;
    private boolean hasVuel;
    private boolean hasVoel;

    public double tf, k, t1, t2, t3, t4;
    public double vrmax, vrmin, efdmax, efdmin;
    public int switchLocation;
    public double reference, outputSignal;

    public Bbsex1Exciter(String id, Bbsex1Data data, Machine machine) {
        super(id, "BBSEX1", "PSS/E");
        this.data = data;
        this._data = data;
        setMachine(machine);
    }

    public Bbsex1Data getData() { return data; }
    public void setVuel(double value) { vuel = value; hasVuel = true; }
    public void setVoel(double value) { voel = value; hasVoel = true; }

    @Override
    public void configureIntegrationStep(double seconds) {
        configureIntegrationStep(seconds, 1.0);
    }

    public void configureIntegrationStep(double seconds, double multiplier) {
        integrationStep = seconds;
        minimumTimeConstantMultiplier = multiplier;
    }

    @Override
    public boolean initStates(BaseDStabBus<?, ?> bus, Machine machine) {
        loadAndCorrect();
        if (tf < 0.0 || k <= 0.0 || t1 <= 0.0 || t2 <= 0.0 || t3 < 0.0
                || t4 <= 0.0 || (switchLocation != SUPPLEMENT_AT_ERROR
                        && switchLocation != SUPPLEMENT_AT_OUTPUT)) {
            return false;
        }
        double vt = bus.getVoltageMag();
        double pss = stabilizerSignal(machine);
        double efd = machine.getEfd();
        double lower = vt * efdmin;
        double upper = vt * efdmax;
        if (!Double.isFinite(vt) || vt <= EPS || !Double.isFinite(efd)
                || efd < lower - 1.0e-9 || efd > upper + 1.0e-9) {
            return false;
        }
        double vr = efd - (switchLocation == SUPPLEMENT_AT_OUTPUT ? pss : 0.0);
        vrmax = Math.max(vrmax, vr);
        vrmin = Math.min(vrmin, vr);
        double regulatorInput = vr / k;
        state[SENSED] = vt;
        state[LEAD_LAG] = regulatorInput;
        state[INVERSE_FEEDBACK] = inverseFeedbackGain() * vr;
        System.arraycopy(state, 0, trial, 0, state.length);
        active = state;
        reference = vt + regulatorInput - limiterInput()
                - (switchLocation == SUPPLEMENT_AT_ERROR ? pss : 0.0);
        outputSignal = efd;
        initialized = true;
        return true;
    }

    private void loadAndCorrect() {
        tf = correctedTime(data.getTf());
        k = data.getK();
        t1 = correctedTime(data.getT1());
        t2 = correctedTime(data.getT2());
        t3 = correctedTime(data.getT3());
        t4 = correctedTime(data.getT4());
        vrmax = Math.max(data.getVrmax(), data.getVrmin());
        vrmin = Math.min(data.getVrmax(), data.getVrmin());
        efdmax = Math.max(data.getEfdmax(), data.getEfdmin());
        efdmin = Math.min(data.getEfdmax(), data.getEfdmin());
        switchLocation = data.getSwitchLocation();
    }

    private double correctedTime(double value) {
        double minimum = minimumTimeConstantMultiplier * integrationStep;
        return value > 0.0 && value < minimum ? minimum : value;
    }

    @Override
    public boolean nextStep(double dt, DynamicSimuMethod method, Machine machine, int flag) {
        if (!initialized || dt < 0.0) return false;
        if (dt == 0.0) {
            outputSignal = algebraics(active, machine).efd;
            return true;
        }
        int stage = method == DynamicSimuMethod.MODIFIED_EULER ? flag : 2;
        if (stage == 0) {
            derivatives(state, oldDerivative, machine);
            add(state, oldDerivative, dt, trial);
            active = trial;
        } else if (stage == 1) {
            double[] derivative = new double[3];
            derivatives(trial, derivative, machine);
            for (int i = 0; i < state.length; i++) {
                state[i] += 0.5 * (oldDerivative[i] + derivative[i]) * dt;
            }
            active = state;
        } else {
            double[] derivative = new double[3];
            derivatives(state, derivative, machine);
            add(state, derivative, dt, state);
            active = state;
        }
        for (int i = 0; i < active.length; i++) {
            if (!Double.isFinite(active[i])) return false;
        }
        outputSignal = algebraics(active, machine).efd;
        return Double.isFinite(outputSignal);
    }

    private void derivatives(double[] values, double[] derivative, Machine machine) {
        Algebraic algebraic = algebraics(values, machine);
        double vt = machine.getDStabBus().getVoltageMag();
        derivative[SENSED] = tf > EPS ? (vt - values[SENSED]) / tf : 0.0;
        derivative[LEAD_LAG] = (algebraic.error - values[LEAD_LAG]) / t4;
        derivative[INVERSE_FEEDBACK] =
                (inverseFeedbackGain() * algebraic.vr - values[INVERSE_FEEDBACK]) / t2;
    }

    private Algebraic algebraics(double[] values, Machine machine) {
        double vt = machine.getDStabBus().getVoltageMag();
        double sensed = tf > EPS ? values[SENSED] : vt;
        double pss = stabilizerSignal(machine);
        double error = reference - sensed + limiterInput()
                + (switchLocation == SUPPLEMENT_AT_ERROR ? pss : 0.0);
        double ratio = t3 / t4;
        double leadLag = ratio * error + (1.0 - ratio) * values[LEAD_LAG];
        double unlimited = k * t2 / t1 * (leadLag + values[INVERSE_FEEDBACK]);
        double vr = clamp(unlimited, vrmin, vrmax);
        double beforeFieldLimit = vr
                + (switchLocation == SUPPLEMENT_AT_OUTPUT ? pss : 0.0);
        double efd = clamp(beforeFieldLimit, vt * efdmin, vt * efdmax);
        return new Algebraic(sensed, error, leadLag, unlimited, vr, beforeFieldLimit, efd);
    }

    private double inverseFeedbackGain() {
        return (t1 / t2 - 1.0) / k;
    }

    private double limiterInput() {
        return (hasVuel ? vuel : 0.0) + (hasVoel ? voel : 0.0);
    }

    private static double stabilizerSignal(Machine machine) {
        return machine.getStabilizer() == null ? 0.0 : machine.getStabilizer().getOutput(machine);
    }

    private static double clamp(double value, double lower, double upper) {
        return Math.max(lower, Math.min(upper, value));
    }

    private static void add(double[] values, double[] derivative, double dt, double[] result) {
        for (int i = 0; i < values.length; i++) result[i] = values[i] + derivative[i] * dt;
    }

    public double getSensedVoltage() { return algebraics(active, getMachine()).sensed; }
    public double getVoltageError() { return algebraics(active, getMachine()).error; }
    public double getLeadLagState() { return active[LEAD_LAG]; }
    public double getLeadLagOutput() { return algebraics(active, getMachine()).leadLag; }
    public double getInverseFeedbackState() { return active[INVERSE_FEEDBACK]; }
    public double getUnlimitedRegulatorOutput() { return algebraics(active, getMachine()).unlimited; }
    public double getRegulatorOutput() { return algebraics(active, getMachine()).vr; }
    public double getBeforeFieldLimit() { return algebraics(active, getMachine()).beforeFieldLimit; }
    public double getDynamicFieldUpperLimit() {
        return getMachine().getDStabBus().getVoltageMag() * efdmax;
    }
    public double getDynamicFieldLowerLimit() {
        return getMachine().getDStabBus().getVoltageMag() * efdmin;
    }

    @Override
    public double getOutput(Machine machine) {
        outputSignal = algebraics(active, machine).efd;
        return outputSignal;
    }

    @Override public void setRefPoint(double value) { reference = value; }
    @Override public double getRefPoint() { return reference; }
    @Override public AnController getAnController() { return getClass().getAnnotation(AnController.class); }
    @Override public Field getField(String name) throws Exception { return getClass().getField(name); }
    @Override public Object getFieldObject(Field field) throws Exception { return field.get(this); }

    private record Algebraic(double sensed, double error, double leadLag,
            double unlimited, double vr, double beforeFieldLimit, double efd) { }
}
