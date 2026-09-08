package org.interpss.dstab.control.exc.psse.exeli;

import java.lang.reflect.Field;
import java.util.Arrays;

import org.interpss.dstab.control.util.IntegrationStepAware;

import com.interpss.dstab.BaseDStabBus;
import com.interpss.dstab.algo.DynamicSimuMethod;
import com.interpss.dstab.controller.cml.ICMLMachineVoltageProvider;
import com.interpss.dstab.controller.cml.annotate.AnController;
import com.interpss.dstab.controller.cml.annotate.AnnotateExciter;
import com.interpss.dstab.mach.Machine;
import com.interpss.dstab.mach.MachineIfdBase;

/** Native PSS/E implementation of the EXELI excitation system. */
@AnController(input = "mach.vt", output = "this.outputSignal",
        refPoint = "this.reference", display = {})
public final class ExeliExciter extends AnnotateExciter implements IntegrationStepAware {
    private static final double EPS = 1.0e-12;
    private static final int WASHOUT1 = 0;
    private static final int STABILIZER_LAG = 1;
    private static final int NEGATIVE_WASHOUT = 2;
    private static final int SENSED_VOLTAGE = 3;
    private static final int SENSED_CURRENT = 4;
    private static final int CONTROLLED_VOLTAGE = 5;
    private static final int WASHOUT2 = 6;
    private static final int WASHOUT3 = 7;

    private final ExeliData data;
    private final double[] state = new double[8];
    private final double[] trial = new double[8];
    private final double[] oldDerivative = new double[8];
    private double[] active = state;
    private boolean initialized;
    private double integrationStep;
    private double minimumTimeConstantMultiplier = 1.0;

    public double tfv, tfi, tnu, vpu, vpi, vpnf, dpnf, efdmin, efdmax;
    public double xe, tw, ks1, ks2, ts1, ts2, smax, reference, outputSignal;

    public ExeliExciter(String id, ExeliData data, Machine machine) {
        super(id, "EXELI", "PSS/E");
        this.data = data;
        this._data = data;
        setMachine(machine);
    }

    public ExeliData getData() { return data; }

    @Override
    public void configureIntegrationStep(double stepSeconds) {
        configureIntegrationStep(stepSeconds, 1.0);
    }

    public void configureIntegrationStep(double stepSeconds, double multiplier) {
        integrationStep = stepSeconds;
        minimumTimeConstantMultiplier = multiplier;
    }

    @Override
    public boolean initStates(BaseDStabBus<?, ?> bus, Machine machine) {
        loadAndCorrectParameters();
        if (tfv < 0.0 || tfi < 0.0 || tnu <= 0.0 || vpnf < 0.0 || dpnf < 0.0
                || xe < 0.0 || tw < 0.0 || ts1 < 0.0 || ts2 < 0.0
                || smax <= 0.0 || Math.abs(vpi) <= EPS) {
            return false;
        }

        double terminalVoltage = sensingVoltage(machine);
        double fieldCurrent = exciterIfd(machine);
        double initialEfd = machine.getEfd();
        double initialPower = machine.getPe();
        if (!Double.isFinite(terminalVoltage) || !Double.isFinite(fieldCurrent)
                || !Double.isFinite(initialEfd) || !Double.isFinite(initialPower)) {
            return false;
        }

        efdmax = Math.max(efdmax, initialEfd);
        efdmin = Math.min(efdmin, initialEfd);

        state[WASHOUT1] = initialPower;
        state[STABILIZER_LAG] = 0.0;
        state[NEGATIVE_WASHOUT] = 0.0;
        state[SENSED_VOLTAGE] = terminalVoltage;
        state[SENSED_CURRENT] = fieldCurrent;
        state[WASHOUT2] = 0.0;
        state[WASHOUT3] = 0.0;

        double target = (initialEfd + xe * fieldCurrent) / vpi;
        Equilibrium equilibrium = solveEquilibrium(target);
        if (equilibrium == null
                || (Math.abs(equilibrium.proportionalOutput) > EPS && Math.abs(vpu) <= EPS)) {
            return false;
        }
        state[CONTROLLED_VOLTAGE] = fieldCurrent + equilibrium.controlledOffset;
        double initialError = Math.abs(vpu) > EPS ? equilibrium.proportionalOutput / vpu : 0.0;
        reference = terminalVoltage + initialError;

        System.arraycopy(state, 0, trial, 0, state.length);
        active = state;
        outputSignal = initialEfd;
        initialized = true;
        return true;
    }

    private void loadAndCorrectParameters() {
        tfv = correctedBypass(data.getTfv());
        tfi = correctedBypass(data.getTfi());
        tnu = correctedNonzero(data.getTnu());
        // VPU and VPI are gains in both the PSS/E parameter table and diagram.
        // Preserve them; the PowerWorld page's timestep assignment for positive
        // "Vpu/Vpi" is inconsistent with that topology and appears to be a typo.
        vpu = data.getVpu();
        vpi = data.getVpi();
        vpnf = data.getVpnf();
        dpnf = data.getDpnf();
        efdmax = Math.max(data.getEfdmax(), data.getEfdmin());
        efdmin = Math.min(data.getEfdmax(), data.getEfdmin());
        xe = data.getXe();
        tw = correctedBypass(data.getTw());
        ks1 = data.getKs1();
        ks2 = data.getKs2();
        ts1 = correctedBypass(data.getTs1());
        ts2 = correctedBypass(data.getTs2());
        smax = data.getSmax();
    }

    private double correctedBypass(double value) {
        double minimum = minimumTimeConstantMultiplier * integrationStep;
        if (value > 0.0 && value < 0.5 * minimum) return 0.0;
        if (value >= 0.5 * minimum && value < minimum) return minimum;
        return value;
    }

    private double correctedNonzero(double value) {
        double minimum = minimumTimeConstantMultiplier * integrationStep;
        return value > 0.0 && value < minimum ? minimum : value;
    }

    @Override
    public boolean nextStep(double dt, DynamicSimuMethod method, Machine machine, int flag) {
        if (!initialized || dt < 0.0) return false;
        if (dt == 0.0) return true;
        int stage = method == DynamicSimuMethod.MODIFIED_EULER ? flag : 2;
        if (stage == 0) {
            derivatives(state, oldDerivative, machine);
            add(state, oldDerivative, dt, trial);
            sanitize(trial);
            active = trial;
        } else if (stage == 1) {
            double[] correctedDerivative = new double[state.length];
            derivatives(trial, correctedDerivative, machine);
            for (int i = 0; i < state.length; i++) {
                state[i] += 0.5 * (oldDerivative[i] + correctedDerivative[i]) * dt;
            }
            sanitize(state);
            active = state;
        } else {
            double[] derivative = new double[state.length];
            derivatives(state, derivative, machine);
            add(state, derivative, dt, state);
            sanitize(state);
            active = state;
        }
        outputSignal = algebraics(active, machine).efd;
        return true;
    }

    private void derivatives(double[] x, double[] dx, Machine machine) {
        Arrays.fill(dx, 0.0);
        double power = machine.getPe();
        double firstWashout = washoutOutput(power, x[WASHOUT1], tw);
        double secondWashout = washoutOutput(firstWashout, x[WASHOUT2], tw);
        double thirdWashout = washoutOutput(secondWashout, x[WASHOUT3], tw);
        double stabilizerSum = ks1 * thirdWashout
                + lagOutput(ks2 * thirdWashout, x[STABILIZER_LAG], ts1);

        dx[WASHOUT1] = lagDerivative(power, x[WASHOUT1], tw);
        dx[WASHOUT2] = lagDerivative(firstWashout, x[WASHOUT2], tw);
        dx[WASHOUT3] = lagDerivative(secondWashout, x[WASHOUT3], tw);
        dx[STABILIZER_LAG] = lagDerivative(ks2 * thirdWashout,
                x[STABILIZER_LAG], ts1);
        dx[NEGATIVE_WASHOUT] = lagDerivative(stabilizerSum,
                x[NEGATIVE_WASHOUT], ts2);

        double terminalVoltage = sensingVoltage(machine);
        double fieldCurrent = exciterIfd(machine);
        dx[SENSED_VOLTAGE] = lagDerivative(terminalVoltage, x[SENSED_VOLTAGE], tfv);
        dx[SENSED_CURRENT] = lagDerivative(fieldCurrent, x[SENSED_CURRENT], tfi);

        Algebraic algebraic = algebraics(x, machine);
        dx[CONTROLLED_VOLTAGE] = (algebraic.proportionalOutput
                + vpnf * deadband(algebraic.filteredCurrent - x[CONTROLLED_VOLTAGE], dpnf))
                / tnu;
    }

    private Algebraic algebraics(double[] x, Machine machine) {
        double terminalVoltage = sensingVoltage(machine);
        double fieldCurrent = exciterIfd(machine);
        double filteredVoltage = tfv > EPS ? x[SENSED_VOLTAGE] : terminalVoltage;
        double filteredCurrent = tfi > EPS ? x[SENSED_CURRENT] : fieldCurrent;

        double firstWashout = washoutOutput(machine.getPe(), x[WASHOUT1], tw);
        double secondWashout = washoutOutput(firstWashout, x[WASHOUT2], tw);
        double thirdWashout = washoutOutput(secondWashout, x[WASHOUT3], tw);
        double stabilizerSum = ks1 * thirdWashout
                + lagOutput(ks2 * thirdWashout, x[STABILIZER_LAG], ts1);
        double stabilizer = clamp(-washoutOutput(stabilizerSum,
                x[NEGATIVE_WASHOUT], ts2), -smax, smax);

        double error = reference - filteredVoltage - stabilizer;
        double proportionalOutput = vpu * error;
        double currentError = x[CONTROLLED_VOLTAGE] + proportionalOutput - filteredCurrent;
        double unlimitedEfd = vpi * currentError - xe * fieldCurrent;
        double efd = clamp(unlimitedEfd, efdmin, efdmax);
        return new Algebraic(filteredVoltage, filteredCurrent, stabilizer, error,
                proportionalOutput, currentError, unlimitedEfd, efd);
    }

    private Equilibrium solveEquilibrium(double target) {
        if (vpnf <= EPS || dpnf < 0.0) return new Equilibrium(target, 0.0);
        if (Math.abs(target) <= dpnf) return new Equilibrium(target, 0.0);
        double offset = target > dpnf
                ? (target + vpnf * dpnf) / (1.0 + vpnf)
                : (target - vpnf * dpnf) / (1.0 + vpnf);
        return new Equilibrium(offset, target - offset);
    }

    private static double washoutOutput(double input, double lagState, double timeConstant) {
        return timeConstant > EPS ? input - lagState : 0.0;
    }

    private static double lagOutput(double input, double lagState, double timeConstant) {
        return timeConstant > EPS ? lagState : input;
    }

    private static double lagDerivative(double input, double lagState, double timeConstant) {
        return timeConstant > EPS ? (input - lagState) / timeConstant : 0.0;
    }

    private static double deadband(double value, double width) {
        if (value > width) return value - width;
        if (value < -width) return value + width;
        return 0.0;
    }

    private static double sensingVoltage(Machine machine) {
        if (machine instanceof ICMLMachineVoltageProvider provider) {
            double value = provider.getCmlMachineVoltage();
            if (Double.isFinite(value)) return value;
        }
        return machine.getDStabBus().getVoltageMag();
    }

    private static double exciterIfd(Machine machine) {
        double value = machine.calculateIfd(MachineIfdBase.EXCITER);
        return Double.isFinite(value) ? value : 0.0;
    }

    private static double clamp(double value, double lower, double upper) {
        return Math.max(lower, Math.min(upper, value));
    }

    private static void add(double[] x, double[] derivative, double dt, double[] result) {
        for (int i = 0; i < x.length; i++) result[i] = x[i] + derivative[i] * dt;
    }

    private static void sanitize(double[] x) {
        for (int i = 0; i < x.length; i++) if (!Double.isFinite(x[i])) x[i] = 0.0;
    }

    public double getSensedVoltage() { return algebraics(active, getMachine()).filteredVoltage; }
    public double getSensedFieldCurrent() { return algebraics(active, getMachine()).filteredCurrent; }
    public double getStabilizerSignal() { return algebraics(active, getMachine()).stabilizer; }
    public double getVoltageError() { return algebraics(active, getMachine()).error; }
    public double getVoltageControllerOutput() { return algebraics(active, getMachine()).proportionalOutput; }
    public double getControlledVoltage() { return active[CONTROLLED_VOLTAGE]; }
    public double getCurrentControllerError() { return algebraics(active, getMachine()).currentError; }
    public double getUnlimitedOutput() { return algebraics(active, getMachine()).unlimitedEfd; }
    /** Copy of the eight PSS/E state variables in documented state order. */
    public double[] getStateSnapshot() { return active.clone(); }

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

    private record Algebraic(double filteredVoltage, double filteredCurrent,
            double stabilizer, double error, double proportionalOutput,
            double currentError, double unlimitedEfd, double efd) { }
    private record Equilibrium(double controlledOffset, double proportionalOutput) { }
}
