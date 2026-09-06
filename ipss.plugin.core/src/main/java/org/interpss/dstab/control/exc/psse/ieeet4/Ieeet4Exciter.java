package org.interpss.dstab.control.exc.psse.ieeet4;

import java.lang.reflect.Field;

import org.interpss.dstab.control.exc.psse.exac1.Exac1Exciter;

import com.interpss.dstab.BaseDStabBus;
import com.interpss.dstab.algo.DynamicSimuMethod;
import com.interpss.dstab.controller.cml.annotate.AnController;
import com.interpss.dstab.controller.cml.annotate.AnnotateExciter;
import com.interpss.dstab.mach.Machine;

/**
 * PSS/E IEEET4 / WECC EXDC4 IEEE Type 4 rotating DC excitation system.
 *
 * <p>The implementation follows the two-state topology already represented by
 * the legacy InterPSS IEEE Type 4 model, while adding the missing PSS/E
 * {@code KR} path and applying the published contact-selection equations.
 * Direct state integration avoids a circular CML initialization dependency
 * between {@code VRH} and {@code EFD}.</p>
 */
@AnController(input="mach.vt", output="this.outputSignal",
        refPoint="this.reference", display={})
public final class Ieeet4Exciter extends AnnotateExciter {
    private static final int VRH = 0;
    private static final int EFD = 1;

    private final Ieeet4Data data;
    private final double[] state = new double[2];
    private final double[] trial = new double[2];
    private final double[] oldDerivative = new double[2];
    private double[] active = state;
    private boolean initialized;
    private double integrationStep;
    private double minimumTimeConstantMultiplier = 1.0;

    public double kr, trh, kv, vrmax, vrmin, te, ke, e1, se1, e2, se2;
    public double reference;
    public double outputSignal;

    public Ieeet4Exciter(String id, String modelName, Ieeet4Data data, Machine machine) {
        super(id, modelName, "PSS/E");
        this.data = data;
        this._data = data;
        setMachine(machine);
    }

    public Ieeet4Data getData() { return data; }

    public void configureIntegrationStep(double timeStepSec) {
        configureIntegrationStep(timeStepSec, 1.0);
    }

    public void configureIntegrationStep(double timeStepSec, double multiplier) {
        integrationStep = timeStepSec;
        minimumTimeConstantMultiplier = multiplier;
    }

    @Override public boolean initStates(BaseDStabBus<?, ?> bus, Machine machine) {
        kr=data.getKr(); trh=correctedMinimum(data.getTrh()); kv=data.getKv();
        te=correctedMinimum(data.getTe()); ke=data.getKe(); e1=data.getE1(); se1=data.getSe1();
        e2=data.getE2(); se2=data.getSe2();
        if (trh <= 0.0 || te <= 0.0 || kv < 0.0) return false;

        double efd0 = machine.getEfd();
        double vr0 = fieldFeedback(efd0);
        double high = Math.max(data.getVrmax(), data.getVrmin());
        double low = Math.min(data.getVrmax(), data.getVrmin());
        vrmax = Math.max(high, vr0);
        vrmin = Math.min(low, vr0);
        state[VRH] = vr0;
        state[EFD] = efd0;
        System.arraycopy(state, 0, trial, 0, state.length);
        active = state;
        reference = machine.getDStabBus().getVoltageMag();
        outputSignal = efd0;
        initialized = true;
        return true;
    }

    private double correctedMinimum(double value) {
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
        outputSignal = active[EFD];
        return true;
    }

    private void derivatives(double[] x, double[] dx, Machine machine) {
        double voltage = machine.getDStabBus().getVoltageMag();
        double rate = rateCommand(reference, voltage) / trh;
        if ((x[VRH] >= vrmax && rate > 0.0) || (x[VRH] <= vrmin && rate < 0.0)) rate = 0.0;
        dx[VRH] = rate;
        double control = selectControl(reference, voltage, clamp(x[VRH], vrmin, vrmax));
        dx[EFD] = (control - fieldFeedback(x[EFD])) / te;
    }

    public double rateCommand(double voltageReference, double terminalVoltage) {
        return clamp(kr * (voltageReference - terminalVoltage), -1.0, 1.0);
    }

    public double selectControl(double voltageReference, double terminalVoltage,
            double rheostatOutput) {
        double error = voltageReference - terminalVoltage;
        if (error >= kv) return vrmax;
        if (error <= -kv) return vrmin;
        return rheostatOutput;
    }

    private double fieldFeedback(double field) {
        return field * (ke + Exac1Exciter.saturation(field, e1, se1, e2, se2));
    }

    public double getRheostatOutput() { return active[VRH]; }
    @Override public double getOutput(Machine machine) {
        outputSignal = active[EFD];
        return outputSignal;
    }
    @Override public void setRefPoint(double value) { reference = value; }
    @Override public double getRefPoint() { return reference; }

    private static double clamp(double value, double low, double high) {
        return Math.max(low, Math.min(high, value));
    }

    @Override public AnController getAnController() { return getClass().getAnnotation(AnController.class); }
    @Override public Field getField(String name) throws Exception { return getClass().getField(name); }
    @Override public Object getFieldObject(Field field) throws Exception { return field.get(this); }
}
