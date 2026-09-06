package org.interpss.dstab.control.exc.psse.ieeex1;

import java.lang.reflect.Field;

import org.interpss.dstab.control.exc.ieee.y1981.dc1.IEEE1981DC1Exciter;
import org.interpss.dstab.control.exc.psse.exac1.Exac1Exciter;

import com.interpss.dstab.BaseDStabBus;
import com.interpss.dstab.algo.DynamicSimuMethod;
import com.interpss.dstab.controller.cml.annotate.AnController;
import com.interpss.dstab.mach.Machine;

/**
 * PSS/E IEEEX1 (WECC EXDC1) excitation system.
 *
 * <p>The class deliberately extends the legacy IEEE DC1 implementation so
 * existing callers that use {@link IEEE1981DC1Exciter} remain source and
 * binary compatible.  Its state equations are evaluated directly because
 * IEEEX1 permits an algebraic {@code TE=0} field block, which the original
 * CML integration block cannot represent.</p>
 */
@AnController(input="mach.vt", output="this.outputSignal",
        refPoint="this.reference", display={})
public class Ieeex1Exciter extends IEEE1981DC1Exciter {
    private static final double EPS = 1.0e-12;
    private static final int VSENSE = 0;
    private static final int LEAD_LAG = 1;
    private static final int REGULATOR = 2;
    private static final int FIELD = 3;
    private static final int WASHOUT_LAG = 4;

    private final double[] state = new double[5];
    private final double[] trial = new double[5];
    private final double[] oldDerivative = new double[5];
    private double[] active = state;
    private double integrationStep;
    private double minimumTimeConstantMultiplier = 1.0;
    private double switchValue;
    private double vuel;
    private double voel;
    private boolean initialized;
    public double reference;
    public double outputSignal;

    public Ieeex1Exciter(String id, Machine machine) {
        this(id, "IEEEX1", machine);
    }

    protected Ieeex1Exciter(String id, String name, Machine machine) {
        super(id, name, "PSS/E");
        configureAsPsseIeeex1();
        setMachine(machine);
    }

    public void setSwitchValue(double value) { switchValue = value; }
    public double getSwitchValue() { return switchValue; }
    public void setVuel(double value) { vuel = value; }
    public double getVuel() { return vuel; }
    public void setVoel(double value) { voel = value; }
    public double getVoel() { return voel; }
    public double getSensedVoltage() { return algebraics(active, getMachine()).sensedVoltage; }
    public double getRegulatorOutput() { return algebraics(active, getMachine()).regulator; }

    @Override
    public void configureIntegrationStep(double timeStepSec) {
        configureIntegrationStep(timeStepSec, 1.0);
    }

    @Override
    public void configureIntegrationStep(double timeStepSec, double multiplier) {
        super.configureIntegrationStep(timeStepSec, multiplier);
        integrationStep = timeStepSec;
        minimumTimeConstantMultiplier = multiplier;
    }

    @Override
    public boolean initStates(BaseDStabBus<?, ?> bus, Machine machine) {
        loadAndCorrectParameters(machine);
        double efd0 = initialInternalField(machine);
        double vr0 = fieldFeedback(efd0);
        double voltageScale = regulatorLimitScale(machine);
        vrmax = Math.max(vrmax, vr0 / voltageScale);
        vrmin = Math.min(vrmin, vr0 / voltageScale);
        double leadLag0 = Math.abs(ka) > EPS ? vr0 / ka : 0.0;
        double pss0 = stabilizerSignal(machine);

        state[VSENSE] = machine.getDStabBus().getVoltageMag();
        state[LEAD_LAG] = leadLag0;
        state[REGULATOR] = vr0;
        state[FIELD] = efd0;
        state[WASHOUT_LAG] = efd0;
        System.arraycopy(state, 0, trial, 0, state.length);
        active = state;
        reference = leadLag0 + state[VSENSE] - vuel - voel - pss0;
        outputSignal = machineOutput(efd0, machine);
        initialized = true;
        return true;
    }

    private void loadAndCorrectParameters(Machine machine) {
        ka = getData().getKa();
        ta = correctedTa(getData().getTa());
        tb = correctedTb(getData().getTb());
        tc = getData().getTc();
        vrmax = Math.max(getData().getVrmax(), getData().getVrmin());
        vrmin = Math.min(getData().getVrmax(), getData().getVrmin());
        ke = getData().getKe();
        te = correctedTe(getData().getTe());
        kf = getData().getKf();
        tf = correctedTf(getData().getTf());
        e1 = getData().getE1();
        se_e1 = getData().getSe_e1();
        e2 = getData().getE2();
        se_e2 = getData().getSe_e2();
        tr = correctedTr(getSourceTransducerTimeConstant());
        kint = te > EPS ? 1.0 / te : 0.0;
        k = tf > EPS ? kf / tf : 0.0;
    }

    protected double minimumResolvedTimeConstant() {
        return minimumTimeConstantMultiplier * integrationStep;
    }

    protected double correctedTr(double value) { return correctedBypass(value); }
    protected double correctedTa(double value) { return correctedBypass(value); }
    protected double correctedTb(double value) { return correctedBypass(value); }
    protected double correctedTe(double value) { return correctedMinimum(value); }
    protected double correctedTf(double value) { return correctedMinimum(value); }

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

    @Override
    public boolean nextStep(double dt, DynamicSimuMethod method, Machine machine, int flag) {
        if (!initialized || dt < 0.0) return false;
        if (dt == 0.0) return true;
        int stage = method == DynamicSimuMethod.MODIFIED_EULER ? flag : 2;
        if (stage == 0) {
            derivatives(state, oldDerivative, machine);
            for (int i = 0; i < state.length; i++) {
                trial[i] = state[i] + oldDerivative[i] * dt;
            }
            active = trial;
        } else if (stage == 1) {
            double[] corrected = new double[state.length];
            derivatives(trial, corrected, machine);
            for (int i = 0; i < state.length; i++) {
                state[i] += 0.5 * (oldDerivative[i] + corrected[i]) * dt;
            }
            active = state;
        } else {
            double[] derivative = new double[state.length];
            derivatives(state, derivative, machine);
            for (int i = 0; i < state.length; i++) state[i] += derivative[i] * dt;
            active = state;
        }
        outputSignal = machineOutput(algebraics(active, machine).field, machine);
        return true;
    }

    private void derivatives(double[] x, double[] dx, Machine machine) {
        Algebraic a = algebraics(x, machine);
        double vt = machine.getDStabBus().getVoltageMag();
        dx[VSENSE] = lagDerivative(vt, x[VSENSE], tr);
        dx[LEAD_LAG] = lagDerivative(a.error, x[LEAD_LAG], tb);

        double targetVr = ka * a.leadLag;
        double rawRegDerivative = lagDerivative(targetVr, x[REGULATOR], ta);
        double upper = regulatorUpper(machine);
        double lower = regulatorLower(machine);
        boolean atUpperAndRising = x[REGULATOR] >= upper && rawRegDerivative > 0.0;
        boolean atLowerAndFalling = x[REGULATOR] <= lower && rawRegDerivative < 0.0;
        dx[REGULATOR] = atUpperAndRising || atLowerAndFalling ? 0.0 : rawRegDerivative;
        dx[FIELD] = te > EPS
                ? (a.regulator - fieldFeedback(x[FIELD])) / te : 0.0;
        dx[WASHOUT_LAG] = lagDerivative(a.field, x[WASHOUT_LAG], tf);
    }

    private Algebraic algebraics(double[] x, Machine machine) {
        double vt = machine.getDStabBus().getVoltageMag();
        double sensed = tr > EPS ? x[VSENSE] : vt;
        if (te > EPS) return algebraicsForField(x, machine, sensed, x[FIELD]);
        double field = solveAlgebraicField(x, machine, sensed);
        return algebraicsForField(x, machine, sensed, field);
    }

    private Algebraic algebraicsForField(double[] x, Machine machine,
            double sensed, double field) {
        double washout = tf > EPS ? kf * (field - x[WASHOUT_LAG]) / tf : 0.0;
        double error = reference - sensed + vuel + voel
                + stabilizerSignal(machine) - washout;
        double leadLag = tb > EPS
                ? (tc / tb) * error + (1.0 - tc / tb) * x[LEAD_LAG]
                : error;
        double upper = regulatorUpper(machine);
        double lower = regulatorLower(machine);
        double regulator = ta > EPS ? clamp(x[REGULATOR], upper, lower)
                : clamp(ka * leadLag, upper, lower);
        return new Algebraic(sensed, error, leadLag, regulator, field);
    }

    private double solveAlgebraicField(double[] x, Machine machine, double sensed) {
        double value = Double.isFinite(x[FIELD]) ? x[FIELD] : 0.0;
        for (int iteration = 0; iteration < 30; iteration++) {
            double residual = algebraicFieldResidual(value, x, machine, sensed);
            if (Math.abs(residual) < 1.0e-11) return value;
            double h = 1.0e-6 * Math.max(1.0, Math.abs(value));
            double slope = (algebraicFieldResidual(value + h, x, machine, sensed)
                    - algebraicFieldResidual(value - h, x, machine, sensed)) / (2.0 * h);
            if (!Double.isFinite(slope) || Math.abs(slope) < EPS) break;
            double next = value - residual / slope;
            if (!Double.isFinite(next)) break;
            if (Math.abs(next - value) < 1.0e-11) return next;
            value = next;
        }
        return value;
    }

    private double algebraicFieldResidual(double field, double[] x,
            Machine machine, double sensed) {
        Algebraic a = algebraicsForField(x, machine, sensed, field);
        return fieldFeedback(field) - a.regulator;
    }

    private double fieldFeedback(double field) {
        double se = Exac1Exciter.saturation(field, e1, se_e1, e2, se_e2);
        return (ke + se) * field;
    }

    private static double lagDerivative(double input, double value, double timeConstant) {
        return timeConstant > EPS ? (input - value) / timeConstant : 0.0;
    }

    private static double clamp(double value, double max, double min) {
        return Math.max(min, Math.min(max, value));
    }

    private static double stabilizerSignal(Machine machine) {
        return machine.getStabilizer() == null ? 0.0
                : machine.getStabilizer().getOutput(machine);
    }

    @Override public double getOutput(Machine machine) {
        outputSignal = machineOutput(algebraics(active, machine).field, machine);
        return outputSignal;
    }

    protected double regulatorLimitScale(Machine machine) { return 1.0; }
    protected double regulatorUpper(Machine machine) {
        return vrmax * regulatorLimitScale(machine);
    }
    protected double regulatorLower(Machine machine) {
        return vrmin * regulatorLimitScale(machine);
    }
    protected double initialInternalField(Machine machine) { return machine.getEfd(); }
    protected double machineOutput(double internalField, Machine machine) {
        return internalField;
    }

    @Override public void setRefPoint(double value) { reference = value; }
    @Override public double getRefPoint() { return reference; }

    private record Algebraic(double sensedVoltage, double error,
            double leadLag, double regulator, double field) {}

    @Override public AnController getAnController() {
        return getClass().getAnnotation(AnController.class);
    }
    @Override public Field getField(String name) throws Exception {
        return getClass().getField(name);
    }
    @Override public Object getFieldObject(Field field) throws Exception {
        return field.get(this);
    }
}
