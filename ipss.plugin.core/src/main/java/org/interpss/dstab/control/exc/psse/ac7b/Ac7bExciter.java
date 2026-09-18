package org.interpss.dstab.control.exc.psse.ac7b;

import java.lang.reflect.Field;
import java.util.Arrays;

import org.interpss.dstab.control.exc.psse.exac1.Exac1Exciter;
import org.interpss.dstab.control.util.IntegrationStepAware;
import org.interpss.dstab.control.exc.UnderExcitationLimiterTarget;

import com.interpss.dstab.BaseDStabBus;
import com.interpss.dstab.algo.DynamicSimuMethod;
import com.interpss.dstab.controller.cml.annotate.AnController;
import com.interpss.dstab.controller.cml.annotate.AnnotateExciter;
import com.interpss.dstab.mach.Machine;
import com.interpss.dstab.mach.MachineIfdBase;

/**
 * IEEE 421.5-2005 AC7B / PSS/E ESAC7B rotating excitation system.
 *
 * <p>The signal ordering follows the PowerWorld AC7B diagram and Dynawo's
 * open-source {@code BaseAc7}/{@code AcRotatingExciter} equations. The model
 * contains the cascaded limited PID and PI regulators, potential-source
 * multiplier, algebraic field-current limiter, three feedback paths, rotating
 * rectifier characteristic, saturation, and dynamic field limits.</p>
 */
@AnController(input="mach.vt", output="this.outputSignal",
        refPoint="this.reference", display={})
public final class Ac7bExciter extends AnnotateExciter
        implements IntegrationStepAware, UnderExcitationLimiterTarget {
    private static final double EPS = 1.0e-12;
    private static final double ANTI_WINDUP_GAIN = 2.0;
    private static final int VE = 0;
    private static final int VSENSE = 1;
    private static final int PID_INTEGRAL = 2;
    private static final int PID_DERIVATIVE_LAG = 3;
    private static final int PI_INTEGRAL = 4;
    private static final int RATE_LAG = 5;

    private final Ac7bData data;
    private final String modelName;
    private final double[] state = new double[6];
    private final double[] trial = new double[6];
    private final double[] oldDerivative = new double[6];
    private double[] active = state;
    private boolean initialized;
    private double integrationStep;
    private double minimumTimeConstantMultiplier = 1.0;
    private double vuel;

    public double tr, kpr, kir, kdr, tdr, vrmax, vrmin;
    public double kpa, kia, vamax, vamin, kp, kl;
    public double kf1, kf2, kf3, tf, kc, kd, ke, te;
    public double vfemax, vemin, e1, se1, e2, se2, spdmlt;
    public double reference;
    public double outputSignal;

    public Ac7bExciter(String id, String modelName, Ac7bData data, Machine machine) {
        super(id, modelName, "PSS/E");
        this.modelName = modelName;
        this.data = data;
        this._data = data;
        setMachine(machine);
    }

    public Ac7bData getData() { return data; }
    public String getModelName() { return modelName; }
    public void setVuel(double value) { vuel = value; }
    public double getVuel() { return vuel; }

    @Override public void configureIntegrationStep(double timeStepSec) {
        configureIntegrationStep(timeStepSec, 1.0);
    }

    public void configureIntegrationStep(double timeStepSec, double multiplier) {
        integrationStep = timeStepSec;
        minimumTimeConstantMultiplier = multiplier;
    }

    @Override public boolean initStates(BaseDStabBus<?, ?> bus, Machine machine) {
        loadAndCorrectParameters();
        if (tr < 0.0 || tdr < 0.0 || tf < 0.0 || te < 0.0
                || Math.abs(kp) <= EPS) {
            return false;
        }

        double ifd = exciterIfd(machine);
        double ve0 = Exac1Exciter.solveInternalVoltage(machine.getEfd(), kc * ifd);
        if (!Double.isFinite(ve0)) return false;
        double rawEfd0 = rectifierOutput(ve0, ifd);
        double vfe0 = fieldFeedback(ve0, ifd);
        double vb0 = kp * machine.getDStabBus().getVoltageMag();
        if (Math.abs(vb0) <= EPS) return false;
        double va0 = vfe0 / vb0;
        double vr0 = kf1 * rawEfd0 + kf2 * vfe0;

        // PowerWorld expands all three limiter pairs to admit the solved point.
        vrmax = Math.max(vrmax, vr0);
        vrmin = Math.min(vrmin, vr0);
        vamax = Math.max(vamax, va0);
        vamin = Math.min(vamin, va0);
        vfemax = Math.max(vfemax, vfe0);
        vemin = Math.min(vemin, ve0);

        state[VE] = ve0;
        state[VSENSE] = machine.getDStabBus().getVoltageMag();
        state[PID_INTEGRAL] = vr0;
        state[PID_DERIVATIVE_LAG] = 0.0;
        state[PI_INTEGRAL] = va0;
        state[RATE_LAG] = vfe0;
        System.arraycopy(state, 0, trial, 0, state.length);
        active = state;
        reference = state[VSENSE] - vuel - stabilizerSignal(machine);
        outputSignal = speedAdjusted(rawEfd0, machine);
        initialized = true;
        return true;
    }

    private void loadAndCorrectParameters() {
        tr = correctedTransducer(data.getTr());
        kpr = data.getKpr(); kir = data.getKir(); kdr = data.getKdr();
        tdr = correctedBypass(data.getTdr());
        vrmax = Math.max(data.getVrmax(), data.getVrmin());
        vrmin = Math.min(data.getVrmax(), data.getVrmin());
        kpa = data.getKpa(); kia = data.getKia();
        vamax = Math.max(data.getVamax(), data.getVamin());
        vamin = Math.min(data.getVamax(), data.getVamin());
        kp = data.getKp(); kl = data.getKl();
        kf1 = data.getKf1(); kf2 = data.getKf2(); kf3 = data.getKf3();
        tf = correctedMinimum(data.getTf());
        kc = data.getKc(); kd = data.getKd(); ke = data.getKe();
        te = correctedMinimum(data.getTe());
        vfemax = data.getVfemax(); vemin = data.getVemin();
        e1 = data.getE1(); se1 = data.getSe1(); e2 = data.getE2(); se2 = data.getSe2();
        spdmlt = data.getSpdmlt();
    }

    private double minimumResolvedTimeConstant() {
        return minimumTimeConstantMultiplier * integrationStep;
    }

    private double correctedTransducer(double value) {
        double minimum = minimumResolvedTimeConstant();
        if (value > 0.0 && value < 0.25 * minimum) return 0.0;
        if (value > 0.25 * minimum && value < 0.5 * minimum) return 0.5 * minimum;
        return value;
    }

    private double correctedBypass(double value) {
        double minimum = minimumResolvedTimeConstant();
        if (value > 0.0 && value < 0.5 * minimum) return 0.0;
        if (value > 0.5 * minimum && value < minimum) return minimum;
        return value;
    }

    private double correctedMinimum(double value) {
        double minimum = minimumResolvedTimeConstant();
        return value > 0.0 && value < minimum ? minimum : value;
    }

    @Override public boolean nextStep(double dt, DynamicSimuMethod method,
            Machine machine, int flag) {
        if (!initialized || dt < 0.0) return false;
        if (dt == 0.0) return true;
        int stage = method == DynamicSimuMethod.MODIFIED_EULER ? flag : 2;
        if (stage == 0) {
            derivatives(state, oldDerivative, machine);
            for (int i = 0; i < state.length; i++) trial[i] = state[i] + oldDerivative[i] * dt;
            constrainDynamicStates(trial, machine);
            active = trial;
        } else if (stage == 1) {
            double[] corrected = new double[state.length];
            derivatives(trial, corrected, machine);
            for (int i = 0; i < state.length; i++) {
                state[i] += 0.5 * (oldDerivative[i] + corrected[i]) * dt;
            }
            constrainDynamicStates(state, machine);
            active = state;
        } else {
            double[] derivative = new double[state.length];
            derivatives(state, derivative, machine);
            for (int i = 0; i < state.length; i++) state[i] += derivative[i] * dt;
            constrainDynamicStates(state, machine);
            active = state;
        }
        outputSignal = speedAdjusted(rawOutput(active, machine), machine);
        return true;
    }

    private void derivatives(double[] x, double[] dx, Machine machine) {
        Arrays.fill(dx, 0.0);
        Algebraic a = algebraics(x, machine);
        double vt = machine.getDStabBus().getVoltageMag();
        dx[VSENSE] = lagDerivative(vt, x[VSENSE], tr);
        dx[PID_DERIVATIVE_LAG] = lagDerivative(a.pidError, x[PID_DERIVATIVE_LAG], tdr);
        dx[PID_INTEGRAL] = kir * (a.pidError
                - ANTI_WINDUP_GAIN * (a.pidUnlimited - a.pidOutput));
        dx[PI_INTEGRAL] = kia * (a.piError
                - ANTI_WINDUP_GAIN * (a.piUnlimited - a.piOutput));
        dx[RATE_LAG] = lagDerivative(a.vfe, x[RATE_LAG], tf);

        double fieldRate = te > EPS ? (a.regulator - a.vfe) / te : 0.0;
        double upper = fieldUpperLimit(x[VE], exciterIfd(machine));
        if ((x[VE] >= upper && fieldRate > 0.0) || (x[VE] <= vemin && fieldRate < 0.0)) {
            fieldRate = 0.0;
        }
        dx[VE] = fieldRate;
    }

    private Algebraic algebraics(double[] x, Machine machine) {
        double vt = machine.getDStabBus().getVoltageMag();
        double ifd = exciterIfd(machine);
        double sensed = tr > EPS ? x[VSENSE] : vt;
        double vfe = fieldFeedback(x[VE], ifd);
        double rawEfd = rectifierOutput(x[VE], ifd);
        double rateFeedback = tf > EPS ? kf3 * (vfe - x[RATE_LAG]) / tf : 0.0;
        double pidError = reference - sensed + vuel + stabilizerSignal(machine) - rateFeedback;
        double derivative = tdr > EPS
                ? kdr * (pidError - x[PID_DERIVATIVE_LAG]) / tdr : 0.0;
        double pidUnlimited = kpr * pidError + x[PID_INTEGRAL] + derivative;
        double pidOutput = clamp(pidUnlimited, vrmin, vrmax);
        double combinedFeedback = kf1 * rawEfd + kf2 * vfe;
        double piError = pidOutput - combinedFeedback;
        double piUnlimited = kpa * piError + x[PI_INTEGRAL];
        double piOutput = clamp(piUnlimited, vamin, vamax);
        double regulator = Math.max(kp * vt * piOutput, -kl * vfe);
        regulator = Math.min(999.0, regulator);
        double field = te > EPS ? limitedField(x[VE], machine)
                : solveAlgebraicField(regulator, x[VE], machine);
        return new Algebraic(sensed, vfe, rateFeedback, pidError, pidUnlimited,
                pidOutput, combinedFeedback, piError, piUnlimited, piOutput,
                regulator, field);
    }

    private double solveAlgebraicField(double regulator, double initial, Machine machine) {
        double ifd = exciterIfd(machine);
        double value = Double.isFinite(initial) ? initial : 0.0;
        for (int iteration = 0; iteration < 30; iteration++) {
            value = clamp(value, vemin, fieldUpperLimit(value, ifd));
            double residual = regulator - fieldFeedback(value, ifd);
            if (Math.abs(residual) < 1.0e-11) break;
            double h = 1.0e-6 * Math.max(1.0, Math.abs(value));
            double slope = (fieldFeedback(value + h, ifd)
                    - fieldFeedback(value - h, ifd)) / (2.0 * h);
            if (!Double.isFinite(slope) || Math.abs(slope) <= EPS) break;
            double next = value + residual / slope;
            if (!Double.isFinite(next)) break;
            value = next;
        }
        return clamp(value, vemin, fieldUpperLimit(value, ifd));
    }

    private void constrainDynamicStates(double[] x, Machine machine) {
        if (te > EPS) {
            double ifd = exciterIfd(machine);
            x[VE] = clamp(x[VE], vemin, fieldUpperLimit(x[VE], ifd));
        }
    }

    private double limitedField(double value, Machine machine) {
        double ifd = exciterIfd(machine);
        return clamp(value, vemin, fieldUpperLimit(value, ifd));
    }

    private double fieldUpperLimit(double field, double ifd) {
        double denominator = ke
                + Exac1Exciter.saturation(Math.max(field, 0.0), e1, se1, e2, se2);
        if (denominator <= EPS) return Double.POSITIVE_INFINITY;
        return (vfemax - kd * ifd) / denominator;
    }

    private double fieldFeedback(double field, double ifd) {
        return field * (ke + Exac1Exciter.saturation(field, e1, se1, e2, se2))
                + kd * ifd;
    }

    private double rectifierOutput(double field, double ifd) {
        if (Math.abs(field) <= EPS) return 0.0;
        return field * Exac1Exciter.rectifierFactor(kc * ifd / field);
    }

    private double rawOutput(double[] x, Machine machine) {
        Algebraic a = algebraics(x, machine);
        return rectifierOutput(a.field, exciterIfd(machine));
    }

    private double speedAdjusted(double rawEfd, Machine machine) {
        return spdmlt != 0.0 ? rawEfd * machine.getSpeed() : rawEfd;
    }

    private static double lagDerivative(double input, double stateValue, double timeConstant) {
        return timeConstant > EPS ? (input - stateValue) / timeConstant : 0.0;
    }

    private static double clamp(double value, double low, double high) {
        return Math.max(low, Math.min(high, value));
    }

    private static double stabilizerSignal(Machine machine) {
        return machine.getStabilizer() == null ? 0.0 : machine.getStabilizer().getOutput(machine);
    }

    private static double exciterIfd(Machine machine) {
        double ifd = machine.calculateIfd(MachineIfdBase.EXCITER);
        return Double.isFinite(ifd) ? ifd : 0.0;
    }

    public double getSensedVoltage() { return algebraics(active, getMachine()).sensedVoltage; }
    public double getPidIntegralState() { return active[PID_INTEGRAL]; }
    public double getPidDerivativeOutput() {
        Algebraic a = algebraics(active, getMachine());
        return tdr > EPS ? kdr * (a.pidError - active[PID_DERIVATIVE_LAG]) / tdr : 0.0;
    }
    public double getFieldCurrentSignal() { return algebraics(active, getMachine()).vfe; }
    public double getRateFeedback() { return algebraics(active, getMachine()).rateFeedback; }
    public double getPidOutput() { return algebraics(active, getMachine()).pidOutput; }
    public double getPiOutput() { return algebraics(active, getMachine()).piOutput; }
    public double getRegulatorOutput() { return algebraics(active, getMachine()).regulator; }
    public double getInternalFieldVoltage() { return algebraics(active, getMachine()).field; }

    @Override public double getOutput(Machine machine) {
        outputSignal = speedAdjusted(rawOutput(active, machine), machine);
        return outputSignal;
    }
    @Override public void setRefPoint(double value) { reference = value; }
    @Override public double getRefPoint() { return reference; }

    private record Algebraic(double sensedVoltage, double vfe, double rateFeedback,
            double pidError, double pidUnlimited, double pidOutput,
            double combinedFeedback, double piError, double piUnlimited,
            double piOutput, double regulator, double field) { }

    @Override public AnController getAnController() { return getClass().getAnnotation(AnController.class); }
    @Override public Field getField(String name) throws Exception { return getClass().getField(name); }
    @Override public Object getFieldObject(Field field) throws Exception { return field.get(this); }
}
