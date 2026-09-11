package org.interpss.dstab.control.exc.psse.esst2a;

import java.lang.reflect.Field;
import java.util.Arrays;
import java.util.Map;

import org.apache.commons.math3.complex.Complex;
import org.interpss.dstab.control.util.IntegrationStepAware;

import com.interpss.dstab.BaseDStabBus;
import com.interpss.dstab.algo.DynamicSimuMethod;
import com.interpss.dstab.controller.cml.annotate.AnController;
import com.interpss.dstab.controller.cml.annotate.AnnotateExciter;
import com.interpss.dstab.controller.cml.ICMLStateProvider;
import com.interpss.dstab.mach.Machine;
import com.interpss.dstab.mach.MachineIfdBase;

/** IEEE 421.5-2005 Type ST2A compound-source rectifier excitation system. */
@AnController(input = "mach.vt", output = "this.outputSignal",
        refPoint = "this.reference", display = {})
public class Esst2aExciter extends AnnotateExciter implements IntegrationStepAware, ICMLStateProvider {
    private static final double EPS = 1.0e-12;
    private static final int EFD = 0, VSENSE = 1, VR = 2, VF_FILTER = 3, LEAD_LAG = 4;

    private final Esst2aData data;
    private final double[] state = new double[5];
    private final double[] trial = new double[5];
    private final double[] oldDerivative = new double[5];
    private double[] active = state;
    private boolean initialized;
    private boolean hasVuel;
    private boolean hasVoel;
    private final boolean additiveBridge;
    private final boolean bothLimitersAtError;
    private double integrationStep;
    private double minimumTimeConstantMultiplier = 1.0;
    private double vuel;
    private double voel;

    public double tr, ka, ta, vrmax, vrmin, ke, te, kf, tf, kp, ki, kc, efdmax;
    public int uel;
    public double tb, tc, reference, outputSignal;

    public Esst2aExciter(String id, Esst2aData data, Machine machine) {
        this(id, "ESST2A", data, machine, false, false);
    }

    protected Esst2aExciter(String id, String modelName, Esst2aData data,
            Machine machine, boolean additiveBridge, boolean bothLimitersAtError) {
        super(id, modelName, "IEEE");
        this.data = data;
        this._data = data;
        this.additiveBridge = additiveBridge;
        this.bothLimitersAtError = bothLimitersAtError;
        setMachine(machine);
    }

    public Esst2aData getData() { return data; }
    public void setVuel(double value) { vuel = value; hasVuel = true; }
    public double getVuel() { return vuel; }
    public void setVoel(double value) { voel = value; hasVoel = true; }
    public double getVoel() { return voel; }

    @Override public void configureIntegrationStep(double stepSeconds) {
        configureIntegrationStep(stepSeconds, 1.0);
    }
    public void configureIntegrationStep(double stepSeconds, double multiplier) {
        integrationStep = stepSeconds;
        minimumTimeConstantMultiplier = multiplier;
    }

    @Override public boolean initStates(BaseDStabBus<?, ?> bus, Machine machine) {
        loadAndCorrectParameters();
        if (tr < 0.0 || ta < 0.0 || te < 0.0 || tf < 0.0 || tb < 0.0) return false;
        if (Math.abs(ka) <= EPS) return false;

        double efd0 = machine.getEfd();
        double vt0 = machine.getDStabBus().getVoltageMag();
        double vb0 = bridgeVoltage(machine);
        if (!Double.isFinite(efd0) || !Double.isFinite(vt0)
                || (!additiveBridge && vb0 <= EPS)) return false;
        if (efd0 < -EPS || (te <= EPS && Math.abs(ke) <= EPS)) return false;

        double vr0 = additiveBridge ? ke * efd0 - vb0 : ke * efd0 / vb0;
        double error0 = vr0 / ka;
        vrmax = Math.max(vrmax, vr0);
        vrmin = Math.min(vrmin, vr0);
        efdmax = Math.max(efdmax, efd0);

        state[EFD] = Math.max(0.0, efd0);
        state[VSENSE] = vt0;
        state[VR] = vr0;
        state[VF_FILTER] = efd0;
        state[LEAD_LAG] = error0;
        System.arraycopy(state, 0, trial, 0, state.length);
        active = state;

        double limiterAdd = bothLimitersAtError
                ? (hasVuel ? vuel : 0.0) + (hasVoel ? voel : 0.0)
                : (uel < 2 && hasVuel ? vuel : 0.0);
        reference = error0 + vt0 - stabilizerSignal(machine) - limiterAdd;
        outputSignal = efd0;
        initialized = true;
        return true;
    }

    private void loadAndCorrectParameters() {
        tr = correctedBypass(data.getTr());
        if (additiveBridge) tr = Math.min(tr, 0.5);
        ka = data.getKa() == 0.0 ? minimumTimeConstantMultiplier * integrationStep : data.getKa();
        ta = correctedBypass(data.getTa());
        vrmax = Math.max(data.getVrmax(), data.getVrmin());
        vrmin = Math.min(data.getVrmax(), data.getVrmin());
        ke = data.getKe(); te = additiveBridge
                ? correctedRequired(data.getTe()) : correctedBypass(data.getTe());
        kf = data.getKf(); tf = additiveBridge
                ? correctedRequired(data.getTf()) : correctedBypass(data.getTf());
        kp = data.getKp(); ki = data.getKi(); kc = data.getKc();
        efdmax = data.getEfdmax(); uel = data.getUel();
        tb = correctedBypass(data.getTb()); tc = data.getTc();
    }

    private double correctedBypass(double value) {
        double minimum = minimumTimeConstantMultiplier * integrationStep;
        if (value > 0.0 && value < 0.5 * minimum) return 0.0;
        if (value > 0.5 * minimum && value < minimum) return minimum;
        return value;
    }

    private double correctedRequired(double value) {
        double minimum = minimumTimeConstantMultiplier * integrationStep;
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
            constrain(trial);
            active = trial;
        } else if (stage == 1) {
            double[] derivative = new double[state.length];
            derivatives(trial, derivative, machine);
            for (int i = 0; i < state.length; i++) {
                state[i] += 0.5 * (oldDerivative[i] + derivative[i]) * dt;
            }
            constrain(state);
            active = state;
        } else {
            double[] derivative = new double[state.length];
            derivatives(state, derivative, machine);
            for (int i = 0; i < state.length; i++) state[i] += derivative[i] * dt;
            constrain(state);
            active = state;
        }
        outputSignal = algebraics(active, machine).efd;
        return true;
    }

    private void derivatives(double[] x, double[] derivative, Machine machine) {
        Arrays.fill(derivative, 0.0);
        Algebraic a = algebraics(x, machine);
        derivative[VSENSE] = lagDerivative(machine.getDStabBus().getVoltageMag(), x[VSENSE], tr);
        derivative[LEAD_LAG] = lagDerivative(a.leadLagInput, x[LEAD_LAG], tb);
        derivative[VF_FILTER] = lagDerivative(a.efd, x[VF_FILTER], tf);

        double vrRate = ta > EPS ? (ka * a.regulatorInput - x[VR]) / ta : 0.0;
        if ((x[VR] >= vrmax - EPS && vrRate > 0.0)
                || (x[VR] <= vrmin + EPS && vrRate < 0.0)) vrRate = 0.0;
        derivative[VR] = vrRate;

        double drive = additiveBridge ? a.vr + a.vb : a.vr * a.vb;
        double efdRate = te > EPS ? (drive - ke * x[EFD]) / te : 0.0;
        if ((x[EFD] >= efdmax - EPS && efdRate > 0.0)
                || (x[EFD] <= EPS && efdRate < 0.0)) efdRate = 0.0;
        derivative[EFD] = efdRate;
    }

    private Algebraic algebraics(double[] x, Machine machine) {
        double vt = machine.getDStabBus().getVoltageMag();
        double sensed = tr > EPS ? x[VSENSE] : vt;
        double efd = te > EPS ? x[EFD] : algebraicEfd(x[VR], machine);
        double vf = tf > EPS ? kf * (efd - x[VF_FILTER]) / tf : 0.0;
        double leadLagInput = reference - sensed + stabilizerSignal(machine) - vf;
        if (bothLimitersAtError) {
            leadLagInput += (hasVuel ? vuel : 0.0) + (hasVoel ? voel : 0.0);
        } else {
            leadLagInput += uel < 2 && hasVuel ? vuel : 0.0;
            if (uel == 2 && hasVuel) leadLagInput = Math.max(leadLagInput, vuel);
        }
        double leadLagOutput = tb > EPS
                ? (tc / tb) * leadLagInput + (1.0 - tc / tb) * x[LEAD_LAG]
                : leadLagInput;
        double regulatorInput = !bothLimitersAtError && uel == 3 && hasVuel
                ? Math.max(leadLagOutput, vuel) : leadLagOutput;
        double vr = ta > EPS ? x[VR] : clamp(ka * regulatorInput, vrmin, vrmax);
        double vb = bridgeVoltage(machine);
        return new Algebraic(sensed, vf, leadLagInput, leadLagOutput,
                regulatorInput, vr, vb, efd);
    }

    private double algebraicEfd(double vr, Machine machine) {
        if (Math.abs(ke) <= EPS) return 0.0;
        double vb = bridgeVoltage(machine);
        double drive = additiveBridge ? vr + vb : vr * vb;
        return clamp(drive / ke, 0.0, efdmax);
    }

    private void constrain(double[] values) {
        values[VR] = clamp(values[VR], vrmin, vrmax);
        values[EFD] = clamp(values[EFD], 0.0, efdmax);
        for (int i = 0; i < values.length; i++) if (!Double.isFinite(values[i])) values[i] = 0.0;
    }

    public double getCompoundSourceVoltage() { return compoundSourceVoltage(getMachine()); }
    public double getBridgeVoltage() { return bridgeVoltage(getMachine()); }
    private double compoundSourceVoltage(Machine machine) {
        Complex terminalVoltage = machine.getParentGen().getParentBus().getVoltage();
        Complex terminalCurrent = machine.getIxy();
        return terminalVoltage.multiply(kp)
                .add(terminalCurrent.multiply(new Complex(0.0, ki))).abs();
    }
    private double bridgeVoltage(Machine machine) {
        if (Math.abs(kp) <= EPS && Math.abs(ki) <= EPS) return 1.0;
        double ve = compoundSourceVoltage(machine);
        if (ve <= EPS) return 0.0;
        double ifd = machine.calculateIfd(MachineIfdBase.EXCITER);
        if (!Double.isFinite(ifd)) ifd = 0.0;
        return Math.max(0.0, ve * rectifierFactor(kc * ifd / ve));
    }

    public static double rectifierFactor(double in) {
        if (in <= 0.0) return 1.0;
        if (in <= 0.433) return 1.0 - 0.577 * in;
        if (in < 0.75) return Math.sqrt(Math.max(0.0, 0.75 - in * in));
        if (in <= 1.0) return 1.732 * (1.0 - in);
        return 0.0;
    }

    private static double lagDerivative(double input, double value, double timeConstant) {
        return timeConstant > EPS ? (input - value) / timeConstant : 0.0;
    }
    private static double stabilizerSignal(Machine machine) {
        return machine.getStabilizer() == null ? 0.0 : machine.getStabilizer().getOutput(machine);
    }
    private static double clamp(double value, double lower, double upper) {
        return Math.max(lower, Math.min(upper, value));
    }

    public double getSensedVoltage(){return algebraics(active,getMachine()).sensed;}
    public double getFeedbackVoltage(){return algebraics(active,getMachine()).vf;}
    public double getLeadLagInput(){return algebraics(active,getMachine()).leadLagInput;}
    public double getLeadLagOutput(){return algebraics(active,getMachine()).leadLagOutput;}
    public double getRegulatorInput(){return algebraics(active,getMachine()).regulatorInput;}
    public double getRegulatorOutput(){return algebraics(active,getMachine()).vr;}
    protected double getRateFeedbackLagState(){return active[VF_FILTER];}
    @Override public Map<String,Double> getNamedStates(){return Map.of(
            "EFD",active[EFD],"Sensed Vt",getSensedVoltage(),
            "VR",getRegulatorOutput(),"VF",getFeedbackVoltage(),
            "LL",getLeadLagOutput());}
    @Override public double getOutput(Machine machine){outputSignal=algebraics(active,machine).efd;return outputSignal;}
    @Override public void setRefPoint(double value){reference=value;}
    @Override public double getRefPoint(){return reference;}

    private record Algebraic(double sensed, double vf, double leadLagInput,
            double leadLagOutput, double regulatorInput, double vr, double vb, double efd) { }

    @Override public AnController getAnController(){return getClass().getAnnotation(AnController.class);}
    @Override public Field getField(String name)throws Exception{return getClass().getField(name);}
    @Override public Object getFieldObject(Field field)throws Exception{return field.get(this);}
}
