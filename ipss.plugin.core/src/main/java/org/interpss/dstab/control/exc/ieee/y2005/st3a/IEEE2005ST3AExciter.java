package org.interpss.dstab.control.exc.ieee.y2005.st3a;

import java.lang.reflect.Field;
import java.util.Arrays;

import org.apache.commons.math3.complex.Complex;
import org.interpss.dstab.control.exc.psse.esst2a.Esst2aExciter;
import org.interpss.dstab.control.util.IntegrationStepAware;

import com.interpss.dstab.BaseDStabBus;
import com.interpss.dstab.algo.DynamicSimuMethod;
import com.interpss.dstab.controller.cml.annotate.AnController;
import com.interpss.dstab.controller.cml.annotate.AnnotateExciter;
import com.interpss.dstab.mach.Machine;
import com.interpss.dstab.mach.MachineIfdBase;

/** IEEE/PSS/E ESST3A static excitation system. */
@AnController(input = "mach.vt", output = "this.outputSignal",
        refPoint = "this.reference", display = {})
public class IEEE2005ST3AExciter extends AnnotateExciter implements IntegrationStepAware {
    private static final double EPS = 1.0e-12;
    private static final int VM = 0, VSENSE = 1, VR = 2, LEAD_LAG = 3;

    private final double[] state = new double[4];
    private final double[] trial = new double[4];
    private final double[] oldDerivative = new double[4];
    private double[] active = state;
    private boolean initialized, hasVuel;
    private double vuel, integrationStep, minimumTimeConstantMultiplier = 1.0;

    public double tr, vimax, vimin, km, tc, tb, ka, ta, vrmax, vrmin;
    public double kg, kp, ki, vbmax, kc, xl, vgmax, angKp_deg, tm, vmmax, vmmin;
    public double reference, outputSignal;

    public IEEE2005ST3AExciter() {
        this("id", "IEEE2005ST3A", "IEEE");
    }

    public IEEE2005ST3AExciter(String id, String name, String category) {
        super(id, name, category);
        _data = new IEEE2005ST3AExciterData();
    }

    public IEEE2005ST3AExciterData getData() {
        return (IEEE2005ST3AExciterData) _data;
    }

    public void setVuel(double value) { vuel = value; hasVuel = true; }
    public void clearVuel() { hasVuel = false; }
    public double getVuel() { return vuel; }

    @Override
    public void configureIntegrationStep(double value) {
        configureIntegrationStep(value, 1.0);
    }

    public void configureIntegrationStep(double value, double multiplier) {
        integrationStep = value;
        minimumTimeConstantMultiplier = multiplier;
    }

    @Override
    public boolean initStates(BaseDStabBus<?, ?> bus, Machine machine) {
        loadAndCorrectParameters();
        if (tr < 0.0 || tb < 0.0 || ta < 0.0 || tm < 0.0
                || Math.abs(ka) <= EPS || Math.abs(km) <= EPS) return false;
        double efd0 = machine.getEfd();
        double vt0 = machine.getDStabBus().getVoltageMag();
        double vb0 = bridgeVoltage(machine);
        if (!Double.isFinite(efd0) || !Double.isFinite(vt0) || vb0 <= EPS) return false;

        double vm0 = efd0 / vb0;
        double vg0 = Math.min(vgmax, kg * efd0);
        double vr0 = vm0 / km + vg0;
        double vi0 = vr0 / ka;
        vmmax = Math.max(vmmax, vm0); vmmin = Math.min(vmmin, vm0);
        vrmax = Math.max(vrmax, vr0); vrmin = Math.min(vrmin, vr0);
        vimax = Math.max(vimax, vi0); vimin = Math.min(vimin, vi0);
        state[VM] = vm0; state[VSENSE] = vt0; state[VR] = vr0; state[LEAD_LAG] = vi0;
        System.arraycopy(state, 0, trial, 0, state.length);
        active = state;
        reference = vi0 + vt0 - stabilizerSignal(machine);
        outputSignal = efd0;
        initialized = true;
        return true;
    }

    private void loadAndCorrectParameters() {
        IEEE2005ST3AExciterData data = getData();
        tr = correctedBypass(data.getTr());
        vimax = Math.max(data.getVimax(), data.getVimin());
        vimin = Math.min(data.getVimax(), data.getVimin());
        km = correctedGain(data.getKm()); tc = data.getTc(); tb = correctedBypass(data.getTb());
        ka = correctedGain(data.getKa()); ta = correctedBypass(data.getTa());
        vrmax = Math.max(data.getVrmax(), data.getVrmin());
        vrmin = Math.min(data.getVrmax(), data.getVrmin());
        kg = data.getKg(); kp = data.getKp(); ki = data.getKi(); vbmax = data.getVbmax();
        kc = data.getKc(); xl = data.getXl(); vgmax = data.getVgmax();
        angKp_deg = data.getAngKp(); tm = correctedBypass(data.getTm());
        vmmax = Math.max(data.getVmmax(), data.getVmmin());
        vmmin = Math.min(data.getVmmax(), data.getVmmin());
    }

    private double correctedGain(double value) {
        return Math.abs(value) <= EPS ? minimumTimeConstantMultiplier * integrationStep : value;
    }

    private double correctedBypass(double value) {
        double minimum = minimumTimeConstantMultiplier * integrationStep;
        if (value > 0.0 && value < 0.5 * minimum) return 0.0;
        if (value >= 0.5 * minimum && value < minimum) return minimum;
        return value;
    }

    @Override
    public boolean nextStep(double dt, DynamicSimuMethod method, Machine machine, int flag) {
        if (!initialized || dt < 0.0) return false;
        if (dt == 0.0) return true;
        int stage = method == DynamicSimuMethod.MODIFIED_EULER ? flag : 2;
        if (stage == 0) {
            derivatives(state, oldDerivative, machine);
            for (int i = 0; i < state.length; i++) trial[i] = state[i] + oldDerivative[i] * dt;
            constrain(trial); active = trial;
        } else if (stage == 1) {
            double[] derivative = new double[state.length];
            derivatives(trial, derivative, machine);
            for (int i = 0; i < state.length; i++)
                state[i] += 0.5 * (oldDerivative[i] + derivative[i]) * dt;
            constrain(state); active = state;
        } else {
            double[] derivative = new double[state.length];
            derivatives(state, derivative, machine);
            for (int i = 0; i < state.length; i++) state[i] += derivative[i] * dt;
            constrain(state); active = state;
        }
        outputSignal = algebraics(active, machine).efd;
        return true;
    }

    private void derivatives(double[] values, double[] derivatives, Machine machine) {
        Arrays.fill(derivatives, 0.0);
        Algebraic a = algebraics(values, machine);
        derivatives[VSENSE] = lag(machine.getDStabBus().getVoltageMag(), values[VSENSE], tr);
        derivatives[LEAD_LAG] = lag(a.gatedInput, values[LEAD_LAG], tb);
        derivatives[VR] = limitedRate(ka * a.leadLag, values[VR], ta, vrmin, vrmax);
        derivatives[VM] = limitedRate(km * a.vrs, values[VM], tm, vmmin, vmmax);
    }

    private Algebraic algebraics(double[] values, Machine machine) {
        double sensed = tr > EPS ? values[VSENSE] : machine.getDStabBus().getVoltageMag();
        double error = reference - sensed + stabilizerSignal(machine);
        double limitedInput = clamp(error, vimin, vimax);
        double gatedInput = hasVuel ? Math.max(limitedInput, vuel) : limitedInput;
        double leadLag = tb > EPS
                ? tc / tb * gatedInput + (1.0 - tc / tb) * values[LEAD_LAG]
                : gatedInput;
        double regulator = ta > EPS ? clamp(values[VR], vrmin, vrmax)
                : clamp(ka * leadLag, vrmin, vrmax);
        double vb = bridgeVoltage(machine);
        double inner = tm > EPS ? clamp(values[VM], vmmin, vmmax)
                : solveAlgebraicVm(regulator, vb, values[VM]);
        double efd = inner * vb;
        double feedback = Math.min(vgmax, kg * efd);
        return new Algebraic(sensed, error, limitedInput, gatedInput, leadLag,
                regulator, feedback, regulator - feedback, inner, vb, efd);
    }

    private double solveAlgebraicVm(double regulator, double vb, double initial) {
        double value = clamp(initial, vmmin, vmmax);
        for (int n = 0; n < 30; n++) {
            double residual = innerResidual(value, regulator, vb);
            if (Math.abs(residual) < 1.0e-11) break;
            double h = 1.0e-6 * Math.max(1.0, Math.abs(value));
            double slope = (innerResidual(value + h, regulator, vb)
                    - innerResidual(value - h, regulator, vb)) / (2.0 * h);
            if (!Double.isFinite(slope) || Math.abs(slope) <= EPS) break;
            double next = clamp(value - residual / slope, vmmin, vmmax);
            if (!Double.isFinite(next)) break;
            value = next;
        }
        return value;
    }

    private double innerResidual(double inner, double regulator, double vb) {
        double feedback = Math.min(vgmax, kg * inner * vb);
        return inner - clamp(km * (regulator - feedback), vmmin, vmmax);
    }

    private void constrain(double[] values) {
        if (ta > EPS) values[VR] = clamp(values[VR], vrmin, vrmax);
        if (tm > EPS) values[VM] = clamp(values[VM], vmmin, vmmax);
        for (int i = 0; i < values.length; i++) if (!Double.isFinite(values[i])) values[i] = 0.0;
    }

    public double getCompoundSourceVoltage() { return compoundSourceVoltage(getMachine()); }
    public double getBridgeVoltage() { return bridgeVoltage(getMachine()); }

    private double compoundSourceVoltage(Machine machine) {
        double angle = Math.toRadians(angKp_deg);
        Complex kpComplex = new Complex(kp * Math.cos(angle), kp * Math.sin(angle));
        Complex vt = machine.getParentGen().getParentBus().getVoltage();
        Complex it = machine.getIxy();
        return vt.multiply(kpComplex).add(Complex.I
                .multiply(kpComplex.multiply(xl).add(ki)).multiply(it)).abs();
    }

    private double bridgeVoltage(Machine machine) {
        double ve = compoundSourceVoltage(machine);
        if (ve <= EPS) return 0.0;
        double ifd = machine.calculateIfd(MachineIfdBase.EXCITER);
        if (!Double.isFinite(ifd)) ifd = 0.0;
        return Math.max(0.0, Math.min(vbmax,
                ve * Esst2aExciter.rectifierFactor(kc * ifd / ve)));
    }

    private static double limitedRate(double command, double value, double time,
            double lower, double upper) {
        if (time <= EPS) return 0.0;
        double rate = (command - value) / time;
        if ((value >= upper - EPS && rate > 0.0) || (value <= lower + EPS && rate < 0.0)) return 0.0;
        return rate;
    }

    private static double lag(double input, double value, double time) {
        return time > EPS ? (input - value) / time : 0.0;
    }

    private static double clamp(double value, double lower, double upper) {
        return Math.max(lower, Math.min(upper, value));
    }

    private static double stabilizerSignal(Machine machine) {
        return machine.getStabilizer() == null ? 0.0 : machine.getStabilizer().getOutput(machine);
    }

    public double getSensedVoltage() { return algebraics(active, getMachine()).sensed; }
    public double getLimitedInput() { return algebraics(active, getMachine()).limitedInput; }
    public double getHighValueInput() { return algebraics(active, getMachine()).gatedInput; }
    public double getLeadLagOutput() { return algebraics(active, getMachine()).leadLag; }
    public double getRegulatorOutput() { return algebraics(active, getMachine()).regulator; }
    public double getFeedbackVoltage() { return algebraics(active, getMachine()).feedback; }
    public double getInnerRegulatorOutput() { return algebraics(active, getMachine()).inner; }
    public double[] getStateSnapshot() { return active.clone(); }

    @Override public double getOutput(Machine machine) {
        outputSignal = algebraics(active, machine).efd;
        return outputSignal;
    }
    @Override public void setRefPoint(double value) { reference = value; }
    @Override public double getRefPoint() { return reference; }
    @Override public AnController getAnController() { return getClass().getAnnotation(AnController.class); }
    @Override public Field getField(String name) throws Exception { return getClass().getField(name); }
    @Override public Object getFieldObject(Field field) throws Exception { return field.get(this); }

    private record Algebraic(double sensed, double error, double limitedInput,
            double gatedInput, double leadLag, double regulator, double feedback,
            double vrs, double inner, double vb, double efd) {}
}
