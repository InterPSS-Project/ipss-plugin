package org.interpss.dstab.control.exc.psse.ac8b;

import java.lang.reflect.Field;
import java.util.Arrays;

import org.interpss.dstab.control.exc.psse.exac1.Exac1Exciter;
import org.interpss.dstab.control.util.IntegrationStepAware;

import com.interpss.dstab.BaseDStabBus;
import com.interpss.dstab.algo.DynamicSimuMethod;
import com.interpss.dstab.controller.cml.annotate.AnController;
import com.interpss.dstab.controller.cml.annotate.AnnotateExciter;
import com.interpss.dstab.mach.Machine;
import com.interpss.dstab.mach.MachineIfdBase;

/**
 * IEEE 421.5-2005 / PSS/E AC8B rotating exciter.
 *
 * <p>The five states and signal ordering follow the published PowerWorld
 * diagram.  The PID tracking anti-windup equations match the open-source
 * ANDES {@code PIDTrackAW} realization; the field integrator additionally
 * enforces the diagram's {@code VFEMAX}/{@code VEMIN} dynamic limits.</p>
 */
@AnController(input="mach.vt", output="this.outputSignal",
        refPoint="this.reference", display={})
public class Ac8bExciter extends AnnotateExciter implements IntegrationStepAware {
    private static final double EPS = 1.0e-12;
    private static final double PID_TRACKING_GAIN = 2.0;
    private static final int VE = 0;
    private static final int VSENSE = 1;
    private static final int PID_INTEGRAL = 2;
    private static final int PID_DERIVATIVE_LAG = 3;
    private static final int VR = 4;

    private final Ac8bData data;
    private final double[] state = new double[5];
    private final double[] trial = new double[5];
    private final double[] oldDerivative = new double[5];
    private double[] active = state;
    private boolean initialized;
    private double integrationStep;
    private double minimumTimeConstantMultiplier = 1.0;
    private double vuel;
    private double voel;

    public double tr, kpr, kir, kdr, tdr, vpidmax, vpidmin;
    public double vrmax, vrmin, vfemax, vemin, ta, ka, te, kc, kd, ke;
    public double e1, se1, e2, se2;
    public double reference;
    public double outputSignal;

    public Ac8bExciter(String id, Ac8bData data, Machine machine) {
        this(id, "AC8B", data, machine);
    }

    protected Ac8bExciter(String id, String modelName, Ac8bData data, Machine machine) {
        super(id, modelName, "PSS/E");
        this.data = data;
        this._data = data;
        setMachine(machine);
    }

    public Ac8bData getData() { return data; }
    public void setVuel(double value) { vuel = value; }
    public double getVuel() { return vuel; }
    public void setVoel(double value) { voel = value; }
    public double getVoel() { return voel; }

    @Override public void configureIntegrationStep(double timeStepSec) {
        configureIntegrationStep(timeStepSec, 1.0);
    }

    public void configureIntegrationStep(double timeStepSec, double multiplier) {
        integrationStep = timeStepSec;
        minimumTimeConstantMultiplier = multiplier;
    }

    @Override public boolean initStates(BaseDStabBus<?, ?> bus, Machine machine) {
        loadAndCorrectParameters();
        if (tr < 0.0 || tdr < 0.0 || ta < 0.0 || te < 0.0 || Math.abs(ka) <= EPS
                || (data.isEsac8bPti() && te <= EPS)) {
            return false;
        }

        double ifd = exciterIfd(machine);
        double ve0 = Exac1Exciter.solveInternalVoltage(machine.getEfd(), kc * ifd);
        if (!Double.isFinite(ve0)) return false;
        double vfe0 = fieldFeedback(ve0, ifd);
        double pid0 = vfe0 / ka;
        if (pid0 > vpidmax + EPS || pid0 < vpidmin - EPS) return false;

        // PowerWorld expands only the amplifier limits during initialization.
        vrmax = Math.max(vrmax, vfe0);
        vrmin = Math.min(vrmin, vfe0);
        state[VE] = ve0;
        state[VSENSE] = machine.getDStabBus().getVoltageMag();
        state[PID_INTEGRAL] = pid0;
        state[PID_DERIVATIVE_LAG] = 0.0;
        state[VR] = vfe0;
        System.arraycopy(state, 0, trial, 0, state.length);
        active = state;
        reference = state[VSENSE] - vuel - voel - stabilizerSignal(machine);
        outputSignal = rectifierOutput(ve0, ifd);
        initialized = true;
        return true;
    }

    private void loadAndCorrectParameters() {
        tr = data.isEsac8bPti() ? correctedBypass(data.getTr())
                : correctedTransducer(data.getTr());
        kpr = data.getKpr(); kir = data.getKir(); kdr = data.getKdr();
        if (Math.abs(kpr) <= EPS && Math.abs(kir) <= EPS) kpr = 40.0;
        tdr = correctedBypass(data.getTdr());
        vpidmax = Math.max(data.getVpidmax(), data.getVpidmin());
        vpidmin = Math.min(data.getVpidmax(), data.getVpidmin());
        vrmax = Math.max(data.getVrmax(), data.getVrmin());
        vrmin = Math.min(data.getVrmax(), data.getVrmin());
        vfemax = data.getVfemax(); vemin = data.getVemin();
        ta = correctedBypass(data.getTa()); ka = data.getKa();
        if (data.isEsac8bPti() && Math.abs(ka) <= EPS) ka = minimumResolvedTimeConstant();
        te = correctedMinimum(data.getTe()); kc = data.getKc();
        kd = data.getKd(); ke = data.getKe();
        e1 = data.getE1(); se1 = data.getSe1(); e2 = data.getE2(); se2 = data.getSe2();
    }

    private double minimumResolvedTimeConstant() {
        return minimumTimeConstantMultiplier * integrationStep;
    }

    private double correctedTransducer(double value) {
        double minimum = minimumResolvedTimeConstant();
        if (value > 0.0 && value < 0.25 * minimum) return 0.0;
        if (value >= 0.25 * minimum && value < 0.5 * minimum) return 0.5 * minimum;
        return value;
    }

    private double correctedBypass(double value) {
        double minimum = minimumResolvedTimeConstant();
        if (value > 0.0 && value < 0.5 * minimum) return 0.0;
        if (value >= 0.5 * minimum && value < minimum) return minimum;
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
        Algebraic a = algebraics(active, machine);
        outputSignal = rectifierOutput(a.field, exciterIfd(machine));
        return true;
    }

    private void derivatives(double[] x, double[] dx, Machine machine) {
        Arrays.fill(dx, 0.0);
        Algebraic a = algebraics(x, machine);
        double vt = machine.getDStabBus().getVoltageMag();
        dx[VSENSE] = lagDerivative(vt, x[VSENSE], tr);
        dx[PID_DERIVATIVE_LAG] = lagDerivative(a.error, x[PID_DERIVATIVE_LAG], tdr);
        dx[PID_INTEGRAL] = kir * (a.error
                - PID_TRACKING_GAIN * (a.pidUnlimited - a.pidOutput));

        double vrRate = ta > EPS ? (ka * a.pidOutput - x[VR]) / ta : 0.0;
        if ((x[VR] >= vrmax && vrRate > 0.0) || (x[VR] <= vrmin && vrRate < 0.0)) {
            vrRate = 0.0;
        }
        dx[VR] = vrRate;

        double ifd = exciterIfd(machine);
        double fieldRate = te > EPS ? (a.regulator - fieldFeedback(x[VE], ifd)) / te : 0.0;
        double upper = fieldUpperLimit(x[VE], ifd);
        if ((x[VE] >= upper && fieldRate > 0.0) || (x[VE] <= vemin && fieldRate < 0.0)) {
            fieldRate = 0.0;
        }
        dx[VE] = fieldRate;
    }

    private Algebraic algebraics(double[] x, Machine machine) {
        double vt = machine.getDStabBus().getVoltageMag();
        double sensed = tr > EPS ? x[VSENSE] : vt;
        double error = reference - sensed + vuel + voel + stabilizerSignal(machine);
        double derivative = tdr > EPS ? kdr * (error - x[PID_DERIVATIVE_LAG]) / tdr : 0.0;
        double pidUnlimited = kpr * error + x[PID_INTEGRAL] + derivative;
        double pidOutput = clamp(pidUnlimited, vpidmin, vpidmax);
        double regulator = ta > EPS ? clamp(x[VR], vrmin, vrmax)
                : clamp(ka * pidOutput, vrmin, vrmax);
        double field = te > EPS ? limitedField(x[VE], machine)
                : solveAlgebraicField(regulator, x[VE], machine);
        return new Algebraic(sensed, error, pidUnlimited, pidOutput, regulator, field);
    }

    private double solveAlgebraicField(double regulator, double initial, Machine machine) {
        double ifd = exciterIfd(machine);
        double value = Double.isFinite(initial) ? initial : 0.0;
        for (int iteration = 0; iteration < 30; iteration++) {
            value = clamp(value, vemin, fieldUpperLimit(value, ifd));
            double residual = regulator - fieldFeedback(value, ifd);
            if (Math.abs(residual) < 1.0e-11) break;
            double h = 1.0e-6 * Math.max(1.0, Math.abs(value));
            double slope = (fieldFeedback(value + h, ifd) - fieldFeedback(value - h, ifd)) / (2.0 * h);
            if (!Double.isFinite(slope) || Math.abs(slope) <= EPS) break;
            double next = value + residual / slope;
            if (!Double.isFinite(next)) break;
            value = next;
        }
        return clamp(value, vemin, fieldUpperLimit(value, ifd));
    }

    private void constrainDynamicStates(double[] x, Machine machine) {
        if (ta > EPS) x[VR] = clamp(x[VR], vrmin, vrmax);
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
        double denominator = ke + Exac1Exciter.saturation(Math.max(field, 0.0), e1, se1, e2, se2);
        if (denominator <= EPS) return Double.POSITIVE_INFINITY;
        return (vfemax - kd * ifd) / denominator;
    }

    private double fieldFeedback(double field, double ifd) {
        return field * (ke + Exac1Exciter.saturation(field, e1, se1, e2, se2)) + kd * ifd;
    }

    private double rectifierOutput(double field, double ifd) {
        if (Math.abs(field) <= EPS) return 0.0;
        return field * Exac1Exciter.rectifierFactor(kc * ifd / field);
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
    public double getPidUnlimitedOutput() { return algebraics(active, getMachine()).pidUnlimited; }
    public double getPidOutput() { return algebraics(active, getMachine()).pidOutput; }
    public double getRegulatorOutput() { return algebraics(active, getMachine()).regulator; }
    public double getInternalFieldVoltage() { return algebraics(active, getMachine()).field; }
    public double[] getStateSnapshot() { return active.clone(); }

    @Override public double getOutput(Machine machine) {
        Algebraic a = algebraics(active, machine);
        outputSignal = rectifierOutput(a.field, exciterIfd(machine));
        return outputSignal;
    }
    @Override public void setRefPoint(double value) { reference = value; }
    @Override public double getRefPoint() { return reference; }

    private record Algebraic(double sensedVoltage, double error,
            double pidUnlimited, double pidOutput, double regulator, double field) { }

    @Override public AnController getAnController() { return getClass().getAnnotation(AnController.class); }
    @Override public Field getField(String name) throws Exception { return getClass().getField(name); }
    @Override public Object getFieldObject(Field field) throws Exception { return field.get(this); }
}
