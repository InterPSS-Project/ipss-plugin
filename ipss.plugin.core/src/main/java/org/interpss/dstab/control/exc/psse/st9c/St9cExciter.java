package org.interpss.dstab.control.exc.psse.st9c;

import java.lang.reflect.Field;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

import org.apache.commons.math3.complex.Complex;
import org.interpss.dstab.control.exc.psse.exac1.Exac1Exciter;
import org.interpss.dstab.control.util.IntegrationStepAware;

import com.interpss.dstab.BaseDStabBus;
import com.interpss.dstab.algo.DynamicSimuMethod;
import com.interpss.dstab.controller.cml.ICMLMachineVoltageProvider;
import com.interpss.dstab.controller.cml.annotate.AnController;
import com.interpss.dstab.controller.cml.annotate.AnnotateExciter;
import com.interpss.dstab.mach.Machine;
import com.interpss.dstab.mach.MachineIfdBase;

/** Native PSS/E implementation of the IEEE 421.5-2016 ST9C exciter. */
@AnController(input = "mach.vt", output = "this.outputSignal",
        refPoint = "this.reference", display = {})
public final class St9cExciter extends AnnotateExciter implements IntegrationStepAware {
    public static final int INPUT_SUMMATION = 1;
    public static final int INPUT_TAKEOVER = 2;
    public static final int SWITCH_A = 1;
    public static final int SWITCH_B = 2;

    private static final double EPS = 1e-12;
    private static final int SENSED = 0;
    private static final int DIFFERENTIAL = 1;
    private static final int INTEGRAL = 2;
    private static final int CONVERTER = 3;

    private final St9cData data;
    private final double[] state = new double[4];
    private final double[] trial = new double[4];
    private final double[] oldDerivative = new double[4];
    private double[] active = state;
    private boolean initialized;
    private boolean hasVuel, hasVoel, hasVsclSum, hasVsclUel, hasVsclOel;
    private double vuel, voel, vsclSum, vsclUel, vsclOel;
    private double integrationStep, minimumTimeConstantMultiplier = 1;
    private double previousSensedInput, algebraicSensedRate;

    public int oel, uel, scl, sw1;
    public double tr, tcd, tbd, za, ka, ku, ta, tauel;
    public double vrmax, vrmin, kas, tas, kp, thetaP, ki, xl, kc, vbmax;
    public double reference, outputSignal;

    public St9cExciter(String id, St9cData data, Machine machine) {
        super(id, "ST9C", "PSS/E");
        this.data = data;
        this._data = data;
        setMachine(machine);
    }

    public St9cData getData() { return data; }

    @Override
    public void configureIntegrationStep(double seconds) {
        configureIntegrationStep(seconds, 1);
    }

    public void configureIntegrationStep(double seconds, double multiplier) {
        integrationStep = seconds;
        minimumTimeConstantMultiplier = multiplier;
    }

    @Override
    public boolean initStates(BaseDStabBus<?, ?> bus, Machine machine) {
        loadAndCorrect();
        if (!validSelector(oel) || !validSelector(uel) || !validSelector(scl)
                || (sw1 != SWITCH_A && sw1 != SWITCH_B) || tr < 0 || tcd < 0
                || tbd < 0 || za < 0 || ka <= EPS || ku < 0 || ta <= EPS
                || tauel <= EPS || kas <= EPS || tas < 0 || kc < 0 || vbmax < 0
                || !finiteParameters()) {
            return false;
        }
        double sensed = sensingVoltage(machine);
        double available = availableExciterVoltage(machine);
        double efd0 = machine.getEfd();
        if (!Double.isFinite(sensed) || sensed <= EPS || available <= EPS
                || !Double.isFinite(efd0)) {
            return false;
        }
        double converter0 = efd0 / available;
        double vr0 = converter0 / kas;
        vrmax = Math.max(vrmax, vr0);
        vrmin = Math.min(vrmin, vr0);
        state[SENSED] = sensed;
        state[DIFFERENTIAL] = sensed;
        state[INTEGRAL] = vr0;
        state[CONVERTER] = converter0;
        previousSensedInput = sensed;
        algebraicSensedRate = 0;
        reference = sensed - stabilizerSignal(machine) - directLimiterInput();
        System.arraycopy(state, 0, trial, 0, state.length);
        active = state;
        outputSignal = efd0;
        initialized = true;
        return true;
    }

    private void loadAndCorrect() {
        oel = normalizeSelector(data.getOel());
        uel = normalizeSelector(data.getUel());
        scl = normalizeSelector(data.getScl());
        sw1 = data.getSw1();
        tr = correctedBypass(data.getTr());
        tcd = data.getTcd();
        tbd = correctedBypass(data.getTbd());
        za = data.getZa();
        ka = data.getKa();
        if (Math.abs(ka) <= EPS) ka = minimumTime();
        ku = data.getKu();
        ta = data.getTa();
        if (Math.abs(ta) <= EPS) ta = minimumTime();
        tauel = data.getTauel();
        if (Math.abs(tauel) <= EPS) tauel = minimumTime();
        vrmax = Math.max(data.getVrmax(), data.getVrmin());
        vrmin = Math.min(data.getVrmax(), data.getVrmin());
        kas = data.getKas();
        tas = correctedBypass(data.getTas());
        kp = data.getKp();
        thetaP = data.getThetaP();
        ki = data.getKi();
        xl = data.getXl();
        kc = data.getKc();
        vbmax = data.getVbmax();
    }

    private double minimumTime() {
        return minimumTimeConstantMultiplier * integrationStep;
    }

    private double correctedBypass(double value) {
        double minimum = minimumTime();
        if (value > 0 && value < .5 * minimum) return 0;
        if (value > .5 * minimum && value < minimum) return minimum;
        return value;
    }

    private boolean finiteParameters() {
        double[] parameters = {tr, tcd, tbd, za, ka, ku, ta, tauel, vrmax,
                vrmin, kas, tas, kp, thetaP, ki, xl, kc, vbmax};
        for (double value : parameters) if (!Double.isFinite(value)) return false;
        return true;
    }

    @Override
    public boolean nextStep(double dt, DynamicSimuMethod method, Machine machine, int flag) {
        if (!initialized || dt < 0) return false;
        if (dt == 0) {
            outputSignal = algebraics(active, machine).efd;
            return true;
        }
        int stage = method == DynamicSimuMethod.MODIFIED_EULER ? flag : 2;
        if (stage == 0) {
            prepareAlgebraicSensedRate(machine, dt);
            derivatives(state, oldDerivative, machine);
            add(state, oldDerivative, dt, trial);
            constrain(trial);
            active = trial;
        } else if (stage == 1) {
            prepareAlgebraicSensedRate(machine, dt);
            double[] corrected = new double[state.length];
            derivatives(trial, corrected, machine);
            for (int i = 0; i < state.length; i++) {
                state[i] += .5 * (oldDerivative[i] + corrected[i]) * dt;
            }
            constrain(state);
            active = state;
            previousSensedInput = sensingVoltage(machine);
        } else {
            prepareAlgebraicSensedRate(machine, dt);
            double[] derivative = new double[state.length];
            derivatives(state, derivative, machine);
            add(state, derivative, dt, state);
            constrain(state);
            active = state;
            previousSensedInput = sensingVoltage(machine);
        }
        outputSignal = algebraics(active, machine).efd;
        return true;
    }

    private void derivatives(double[] x, double[] derivative, Machine machine) {
        Arrays.fill(derivative, 0);
        Algebraic algebraic = algebraics(x, machine);
        derivative[SENSED] = lagDerivative(sensingVoltage(machine), x[SENSED], tr);
        derivative[DIFFERENTIAL] = lagDerivative(algebraic.sensed, x[DIFFERENTIAL], tbd);
        derivative[INTEGRAL] = (algebraic.regulatorOutput - x[INTEGRAL])
                * algebraic.integralRate;
        derivative[CONVERTER] = lagDerivative(kas * algebraic.regulatorOutput,
                x[CONVERTER], tas);
    }

    private Algebraic algebraics(double[] x, Machine machine) {
        double sensed = tr > EPS ? x[SENSED] : sensingVoltage(machine);
        double sensedRate = tr > EPS
                ? lagDerivative(sensingVoltage(machine), x[SENSED], tr)
                : algebraicSensedRate;
        double differential = tbd > EPS
                ? tcd * (sensed - x[DIFFERENTIAL]) / tbd : tcd * sensedRate;
        double differentialInfluence = clamp(differential, -za, za) - differential;
        double voltageError = reference - sensed + stabilizerSignal(machine)
                + differentialInfluence + directLimiterInput();
        double proportional = ka * voltageError;
        double highGate = proportional;
        if (uel == INPUT_TAKEOVER && hasVuel) highGate = Math.max(highGate, vuel);
        if (scl == INPUT_TAKEOVER && hasVsclUel) highGate = Math.max(highGate, vsclUel);
        double regulatorRaw = highGate + x[INTEGRAL];
        double regulatorOutput = clamp(regulatorRaw, vrmin, vrmax);
        if (oel == INPUT_TAKEOVER && hasVoel) regulatorOutput = Math.min(regulatorOutput, voel);
        if (scl == INPUT_TAKEOVER && hasVsclOel) regulatorOutput = Math.min(regulatorOutput, vsclOel);
        double takeover = (uel == INPUT_TAKEOVER || scl == INPUT_TAKEOVER)
                ? clamp(ku * (highGate - proportional), 0, 1) : 0;
        double integralRate = (1 - takeover) / ta + takeover / tauel;
        double converter = tas > EPS ? x[CONVERTER] : kas * regulatorOutput;
        double available = availableExciterVoltage(machine);
        double efd = converter * available;
        return new Algebraic(sensed, differential, differentialInfluence, voltageError,
                proportional, highGate, regulatorRaw, regulatorOutput, takeover,
                integralRate, converter, available, efd);
    }

    private double directLimiterInput() {
        double value = 0;
        if (oel == INPUT_SUMMATION && hasVoel) value += voel;
        if (uel == INPUT_SUMMATION && hasVuel) value += vuel;
        if (scl == INPUT_SUMMATION && hasVsclSum) value -= vsclSum;
        return value;
    }

    private void prepareAlgebraicSensedRate(Machine machine, double dt) {
        algebraicSensedRate = tr <= EPS && tbd <= EPS && dt > EPS
                ? (sensingVoltage(machine) - previousSensedInput) / dt : 0;
    }

    private Complex machineBaseCurrent(Machine machine) {
        return machine.getIxy().divide(machine.getIMultiFactor());
    }

    private double compoundSource(Machine machine) {
        Complex angle = new Complex(Math.cos(Math.toRadians(thetaP)),
                Math.sin(Math.toRadians(thetaP)));
        Complex kpPhasor = angle.multiply(kp);
        Complex terminalVoltage = machine.getDStabBus().getVoltage();
        Complex terminalCurrent = machineBaseCurrent(machine);
        return kpPhasor.multiply(terminalVoltage)
                .add(Complex.I.multiply(new Complex(ki, 0).add(kpPhasor.multiply(xl)))
                        .multiply(terminalCurrent)).abs();
    }

    private double selectedSource(Machine machine) {
        return sw1 == SWITCH_A ? compoundSource(machine) : kp;
    }

    private double availableExciterVoltage(Machine machine) {
        double source = Math.max(0, selectedSource(machine));
        if (source <= EPS) return 0;
        double ratio = clamp(kc * exciterIfd(machine) / source, 0, 1);
        return clamp(source * Exac1Exciter.rectifierFactor(ratio), 0, vbmax);
    }

    private void constrain(double[] x) {
        for (int i = 0; i < x.length; i++) if (!Double.isFinite(x[i])) x[i] = 0;
    }

    private static double lagDerivative(double input, double stateValue, double time) {
        return time > EPS ? (input - stateValue) / time : 0;
    }

    private static void add(double[] x, double[] derivative, double dt, double[] result) {
        for (int i = 0; i < x.length; i++) result[i] = x[i] + derivative[i] * dt;
    }

    private static double clamp(double value, double lower, double upper) {
        return Math.max(lower, Math.min(upper, value));
    }

    private static int normalizeSelector(int value) {
        return value == 0 ? INPUT_SUMMATION : value;
    }

    private static boolean validSelector(int value) {
        return value == INPUT_SUMMATION || value == INPUT_TAKEOVER;
    }

    private static double sensingVoltage(Machine machine) {
        if (machine instanceof ICMLMachineVoltageProvider provider) {
            double value = provider.getCmlMachineVoltage();
            if (Double.isFinite(value)) return value;
        }
        return machine.getDStabBus().getVoltageMag();
    }

    private static double stabilizerSignal(Machine machine) {
        return machine.getStabilizer() == null ? 0 : machine.getStabilizer().getOutput(machine);
    }

    private static double exciterIfd(Machine machine) {
        double value = machine.calculateIfd(MachineIfdBase.EXCITER);
        return Double.isFinite(value) ? value : 0;
    }

    public void setVuel(double value) { vuel = value; hasVuel = true; }
    public void setVoel(double value) { voel = value; hasVoel = true; }
    public void setVsclSum(double value) { vsclSum = value; hasVsclSum = true; }
    public void setVsclUel(double value) { vsclUel = value; hasVsclUel = true; }
    public void setVsclOel(double value) { vsclOel = value; hasVsclOel = true; }
    public double getSensedVoltage() { return algebraics(active, getMachine()).sensed; }
    public double getDifferentialOutput() { return algebraics(active, getMachine()).differential; }
    public double getDifferentialInfluence() { return algebraics(active, getMachine()).differentialInfluence; }
    public double getVoltageError() { return algebraics(active, getMachine()).voltageError; }
    public double getProportionalOutput() { return algebraics(active, getMachine()).proportional; }
    public double getHighGateOutput() { return algebraics(active, getMachine()).highGate; }
    public double getRegulatorOutput() { return algebraics(active, getMachine()).regulatorOutput; }
    public double getTakeoverFraction() { return algebraics(active, getMachine()).takeover; }
    public double getIntegralRate() { return algebraics(active, getMachine()).integralRate; }
    public double getConverterOutput() { return algebraics(active, getMachine()).converter; }
    public double getCompoundSource() { return compoundSource(getMachine()); }
    public double getAvailableExciterVoltage() { return algebraics(active, getMachine()).available; }
    public double[] getStateSnapshot() { return active.clone(); }

    /** Published PSS/E ST9C states in model-library order and semantics. */
    @Override
    public Map<String, Double> getNamedStates() {
        Map<String, Double> states = new LinkedHashMap<>();
        states.put("Sensed VT", active[SENSED]);
        states.put("AVR Differential Washout", active[DIFFERENTIAL]);
        states.put("Power Converter Filter", active[CONVERTER]);
        states.put("Integrator", active[INTEGRAL]);
        return Collections.unmodifiableMap(states);
    }

    @Override
    public double getOutput(Machine machine) {
        outputSignal = algebraics(active, machine).efd;
        return outputSignal;
    }

    @Override
    public void setRefPoint(double value) { reference = value; }

    @Override
    public double getRefPoint() { return reference; }

    private record Algebraic(double sensed, double differential,
            double differentialInfluence, double voltageError, double proportional,
            double highGate, double regulatorRaw, double regulatorOutput,
            double takeover, double integralRate, double converter, double available,
            double efd) { }

    @Override
    public AnController getAnController() { return getClass().getAnnotation(AnController.class); }

    @Override
    public Field getField(String name) throws Exception { return getClass().getField(name); }

    @Override
    public Object getFieldObject(Field field) throws Exception { return field.get(this); }
}
