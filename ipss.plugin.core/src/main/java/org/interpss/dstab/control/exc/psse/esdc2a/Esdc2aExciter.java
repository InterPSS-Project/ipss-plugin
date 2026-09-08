package org.interpss.dstab.control.exc.psse.esdc2a;

import java.lang.reflect.Field;

import org.interpss.dstab.control.exc.psse.exac1.Exac1Exciter;
import org.interpss.dstab.control.util.IntegrationStepAware;

import com.interpss.dstab.BaseDStabBus;
import com.interpss.dstab.algo.DynamicSimuMethod;
import com.interpss.dstab.controller.cml.annotate.AnController;
import com.interpss.dstab.controller.cml.annotate.AnnotateExciter;
import com.interpss.dstab.controller.cml.field.ICMLFunction;
import com.interpss.dstab.controller.cml.field.adapt.CMLFunctionAdapter;
import com.interpss.dstab.mach.Machine;

/**
 * Native PSS/E ESDC2A (IEEE DC2A) excitation system.
 *
 * <p>The five published storage variables are evaluated directly so the model
 * can represent the algebraic {@code TE=0}, {@code TA=0}, and {@code TF1=0}
 * forms without asking a CML dynamic block to accept a zero time constant.
 * ESDC1A reuses this engine and overrides only the regulator-limit scale.</p>
 */
@AnController(input="mach.vt", output="this.outputSignal",
        refPoint="this.reference", display={})
public class Esdc2aExciter extends AnnotateExciter implements IntegrationStepAware {
    private static final double EPS = 1.0e-12;
    private static final int FIELD = 0;
    private static final int SENSED = 1;
    private static final int REGULATOR = 2;
    private static final int WASHOUT_LAG = 3;
    private static final int LEAD_LAG = 4;

    private final Esdc2aData data;
    private final boolean voltageDependentLimits;
    private final double[] state = new double[5];
    private final double[] trial = new double[5];
    private final double[] oldDerivative = new double[5];
    private double[] active = state;
    private double integrationStep;
    private double minimumTimeConstantMultiplier = 1.0;
    private boolean initialized;

    public double tr, ka, ta, tb, tc, vrmaxVt, vrminVt;
    public double ke, te, kf, tf, e1, se1, e2, se2, switchValue;
    public double spdmlt;
    public double reference, outputSignal;

    /** Public saturation-factor function retained for diagnostic compatibility. */
    public final ICMLFunction saturation = new CMLFunctionAdapter() {
        @Override public double eval(double[] values) {
            return saturationFactor(values[0]);
        }
    };

    public Esdc2aExciter(String id, Esdc2aData data, Machine machine) {
        this(id, "ESDC2A", data, machine, true);
    }

    protected Esdc2aExciter(String id, String name, Esdc2aData data,
            Machine machine, boolean voltageDependentLimits) {
        super(id, name, "PSS/E");
        this.data = data;
        this.voltageDependentLimits = voltageDependentLimits;
        this._data = data;
        setMachine(machine);
    }

    public Esdc2aData getData() { return data; }

    @Override public void configureIntegrationStep(double seconds) {
        configureIntegrationStep(seconds, 1.0);
    }

    public void configureIntegrationStep(double seconds, double multiplier) {
        integrationStep = seconds;
        minimumTimeConstantMultiplier = multiplier;
    }

    @Override
    public boolean initStates(BaseDStabBus<?, ?> bus, Machine machine) {
        loadAndCorrect();
        double vt = bus.getVoltageMag();
        if (!Double.isFinite(vt) || vt <= EPS || !Double.isFinite(machine.getEfd())
                || Math.abs(ka) <= EPS || tr < 0.0 || ta < 0.0 || tb < 0.0
                || te < 0.0 || tf < 0.0) {
            return false;
        }
        double outputSpeedScale = outputSpeedScale(machine);
        if (Math.abs(outputSpeedScale) <= EPS) return false;
        double field = machine.getEfd() / outputSpeedScale;
        double vr = fieldFeedback(field);
        double scale = regulatorLimitScale(machine);
        if (!Double.isFinite(vr) || !Double.isFinite(scale) || scale <= EPS) return false;
        vrmaxVt = Math.max(vrmaxVt, vr / scale);
        vrminVt = Math.min(vrminVt, vr / scale);
        double input = vr / ka;

        state[FIELD] = field;
        state[SENSED] = vt;
        state[REGULATOR] = vr;
        state[WASHOUT_LAG] = field;
        state[LEAD_LAG] = input;
        System.arraycopy(state, 0, trial, 0, state.length);
        active = state;
        reference = vt + input - stabilizerSignal(machine);
        outputSignal = field;
        initialized = true;
        return true;
    }

    private void loadAndCorrect() {
        tr = correctedTransducer(data.getTr());
        ka = data.getKa();
        ta = correctedMinimum(data.getTa());
        tb = data.getTb();
        tc = data.getTc();
        double rawMax = data.getVrmax();
        double rawMin = data.getVrmin();
        vrmaxVt = Math.max(rawMax, rawMin);
        vrminVt = Math.min(rawMax, rawMin);
        if (vrmaxVt == 0.0) vrmaxVt = 999.0;
        ke = data.getKe();
        te = correctedMinimum(data.getTe());
        kf = data.getKf();
        tf = correctedFeedback(data.getTf());
        switchValue = data.getSwitchValue();
        spdmlt = data.getSpdmlt();
        e1 = data.getE1(); se1 = data.getSe1();
        e2 = data.getE2(); se2 = data.getSe2();
    }

    private double minimumTimeConstant() {
        return minimumTimeConstantMultiplier * integrationStep;
    }

    private double correctedMinimum(double value) {
        double minimum = minimumTimeConstant();
        return value > 0.0 && value < minimum ? minimum : value;
    }

    private double correctedFeedback(double value) {
        return value <= 0.0 ? 0.0 : correctedMinimum(value);
    }

    private double correctedTransducer(double value) {
        double minimum = minimumTimeConstant();
        if (value > 0.0 && value < 0.25 * minimum) return 0.0;
        if (value > 0.25 * minimum && value < 0.5 * minimum) return 0.5 * minimum;
        return value;
    }

    @Override
    public boolean nextStep(double dt, DynamicSimuMethod method, Machine machine, int flag) {
        if (!initialized || dt < 0.0) return false;
        if (dt == 0.0) {
            outputSignal = algebraics(active, machine).field;
            return true;
        }
        int stage = method == DynamicSimuMethod.MODIFIED_EULER ? flag : 2;
        if (stage == 0) {
            derivatives(state, oldDerivative, machine);
            add(state, oldDerivative, dt, trial);
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
            add(state, derivative, dt, state);
            active = state;
        }
        for (double value : active) if (!Double.isFinite(value)) return false;
        outputSignal = algebraics(active, machine).field;
        return Double.isFinite(outputSignal);
    }

    private void derivatives(double[] values, double[] derivative, Machine machine) {
        Algebraic a = algebraics(values, machine);
        double vt = machine.getDStabBus().getVoltageMag();
        derivative[SENSED] = lagDerivative(vt, values[SENSED], tr);
        derivative[LEAD_LAG] = lagDerivative(a.error, values[LEAD_LAG], tb);

        double rawVrDerivative = lagDerivative(ka * a.leadLag,
                values[REGULATOR], ta);
        double upper = regulatorUpper(machine);
        double lower = regulatorLower(machine);
        boolean upperAndRising = values[REGULATOR] >= upper && rawVrDerivative > 0.0;
        boolean lowerAndFalling = values[REGULATOR] <= lower && rawVrDerivative < 0.0;
        derivative[REGULATOR] = ta <= EPS || upperAndRising || lowerAndFalling
                ? 0.0 : rawVrDerivative;
        derivative[FIELD] = te > EPS
                ? (a.regulator - fieldFeedback(values[FIELD])) / te : 0.0;
        derivative[WASHOUT_LAG] = lagDerivative(a.field,
                values[WASHOUT_LAG], tf);
    }

    private Algebraic algebraics(double[] values, Machine machine) {
        double vt = machine.getDStabBus().getVoltageMag();
        double sensed = tr > EPS ? values[SENSED] : vt;
        if (te > EPS) return algebraicsForField(values, machine, sensed, values[FIELD]);
        double field = solveAlgebraicField(values, machine, sensed);
        return algebraicsForField(values, machine, sensed, field);
    }

    private Algebraic algebraicsForField(double[] values, Machine machine,
            double sensed, double field) {
        double washout = tf > EPS
                ? kf * (field - values[WASHOUT_LAG]) / tf : 0.0;
        double error = reference - sensed + stabilizerSignal(machine) - washout;
        double leadLag = tb > EPS
                ? tc / tb * error + (1.0 - tc / tb) * values[LEAD_LAG]
                : error;
        double regulator = ta > EPS
                ? clamp(values[REGULATOR], regulatorLower(machine), regulatorUpper(machine))
                : clamp(ka * leadLag, regulatorLower(machine), regulatorUpper(machine));
        return new Algebraic(sensed, error, leadLag, regulator, field, washout);
    }

    private double solveAlgebraicField(double[] values, Machine machine, double sensed) {
        double field = Double.isFinite(values[FIELD]) ? values[FIELD] : 0.0;
        for (int iteration = 0; iteration < 30; iteration++) {
            double residual = fieldResidual(field, values, machine, sensed);
            if (Math.abs(residual) < 1.0e-11) return field;
            double h = 1.0e-6 * Math.max(1.0, Math.abs(field));
            double slope = (fieldResidual(field + h, values, machine, sensed)
                    - fieldResidual(field - h, values, machine, sensed)) / (2.0 * h);
            if (!Double.isFinite(slope) || Math.abs(slope) < EPS) break;
            double next = field - residual / slope;
            if (!Double.isFinite(next)) break;
            if (Math.abs(next - field) < 1.0e-11) return next;
            field = next;
        }
        return field;
    }

    private double fieldResidual(double field, double[] values,
            Machine machine, double sensed) {
        Algebraic a = algebraicsForField(values, machine, sensed, field);
        return fieldFeedback(field) - a.regulator;
    }

    private double saturationFactor(double field) {
        return Exac1Exciter.saturation(field, e1, se1, e2, se2);
    }

    private double fieldFeedback(double field) {
        return (ke + saturationFactor(field)) * field;
    }

    protected double regulatorLimitScale(Machine machine) {
        return voltageDependentLimits ? machine.getDStabBus().getVoltageMag() : 1.0;
    }

    private double regulatorUpper(Machine machine) {
        return vrmaxVt * regulatorLimitScale(machine);
    }

    private double regulatorLower(Machine machine) {
        return vrminVt * regulatorLimitScale(machine);
    }

    private static double lagDerivative(double input, double value, double timeConstant) {
        return timeConstant > EPS ? (input - value) / timeConstant : 0.0;
    }

    private static double stabilizerSignal(Machine machine) {
        return machine.getStabilizer() == null ? 0.0
                : machine.getStabilizer().getOutput(machine);
    }

    private static double clamp(double value, double lower, double upper) {
        return Math.max(lower, Math.min(upper, value));
    }

    private static void add(double[] values, double[] derivative,
            double dt, double[] result) {
        for (int i = 0; i < values.length; i++) {
            result[i] = values[i] + derivative[i] * dt;
        }
    }

    public double getSensedVoltage() { return algebraics(active, getMachine()).sensed; }
    public double getVoltageError() { return algebraics(active, getMachine()).error; }
    public double getLeadLagState() { return active[LEAD_LAG]; }
    public double getLeadLagOutput() { return algebraics(active, getMachine()).leadLag; }
    public double getRegulatorOutput() { return algebraics(active, getMachine()).regulator; }
    public double getInternalFieldVoltage() { return algebraics(active, getMachine()).field; }
    public double getWashoutLagState() { return active[WASHOUT_LAG]; }
    public double getRateFeedbackOutput() { return algebraics(active, getMachine()).washout; }
    public double getDynamicRegulatorUpperLimit() { return regulatorUpper(getMachine()); }
    public double getDynamicRegulatorLowerLimit() { return regulatorLower(getMachine()); }

    @Override public double getOutput(Machine machine) {
        outputSignal = algebraics(active, machine).field * outputSpeedScale(machine);
        return outputSignal;
    }

    private double outputSpeedScale(Machine machine) {
        return spdmlt == 0.0 ? 1.0 : machine.getSpeed();
    }

    @Override public void setRefPoint(double value) { reference = value; }
    @Override public double getRefPoint() { return reference; }
    @Override public AnController getAnController() {
        return getClass().getAnnotation(AnController.class);
    }
    @Override public Field getField(String name) throws Exception {
        return getClass().getField(name);
    }
    @Override public Object getFieldObject(Field field) throws Exception {
        return field.get(this);
    }

    private record Algebraic(double sensed, double error, double leadLag,
            double regulator, double field, double washout) { }
}
