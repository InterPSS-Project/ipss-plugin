package org.interpss.dstab.control.exc.psse.st10c;

import java.lang.reflect.Field;
import java.util.Arrays;

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

/** Native PSS/E implementation of the IEEE 421.5-2016 ST10C exciter. */
@AnController(input = "mach.vt", output = "this.outputSignal",
        refPoint = "this.reference", display = {})
public final class St10cExciter extends AnnotateExciter implements IntegrationStepAware {
    private static final double EPS = 1e-12;
    private static final int SENSED = 0, NORMAL2 = 1, NORMAL1 = 2;
    private static final int UEL2 = 3, UEL1 = 4, OEL2 = 5, OEL1 = 6;
    private static final int PSS2 = 7, PSS1 = 8, VAS = 9;

    private final St10cData data;
    private final double[] state = new double[10], trial = new double[10];
    private final double[] oldDerivative = new double[10];
    private double[] active = state;
    private boolean initialized;
    private boolean hasVuel, hasVoel, hasVsclSum, hasVsclUel, hasVsclOel;
    private double vuel, voel, vsclSum, vsclUel, vsclOel;
    private double integrationStep, minimumTimeConstantMultiplier = 1;

    public int pss, oel, uel, scl, sw1;
    public double tr, kr, tc1, tb1, tc2, tb2;
    public double tuc1, tub1, tuc2, tub2, toc1, tob1, toc2, tob2;
    public double vrsmax, vrsmin, vrmax, vrmin, t1;
    public double kp, kc, ki, xl, thetaP, vbmax;
    public double max1, max2, max3, max4;
    public double reference, outputSignal;

    public St10cExciter(String id, St10cData data, Machine machine) {
        super(id, "ST10C", "PSS/E");
        this.data = data;
        this._data = data;
        setMachine(machine);
    }

    public St10cData getData() { return data; }

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
        if (!validLocation(pss) || !validLocation(oel) || !validLocation(uel)
                || !validLocation(scl) || (sw1 != 1 && sw1 != 2)
                || tr < 0 || kr <= EPS || anyNegativeTime() || kc < 0 || vbmax < 0
                || !finiteParameters()) return false;
        double sensed = sensingVoltage(machine);
        double available = availableExciterVoltage(machine);
        double efd0 = machine.getEfd();
        if (!Double.isFinite(sensed) || sensed <= EPS || available <= EPS
                || !Double.isFinite(efd0)) return false;
        double vas0 = efd0 / available;
        vrmax = Math.max(vrmax, vas0); vrmin = Math.min(vrmin, vas0);
        double stabilizer = stabilizerSignal(machine);
        double pssInput0 = pss == 3 ? stabilizer : 0;
        double pssReg0 = pss == 3 ? clamp(kr * pssInput0, vrsmin, vrsmax) : 0;
        vrsmax = Math.max(vrsmax, pssReg0); vrsmin = Math.min(vrsmin, pssReg0);
        calculateIntermediateLimits();
        double main0 = (vas0 - pssReg0) / kr;
        state[SENSED] = sensed;
        for (int index = NORMAL2; index <= OEL1; index++) state[index] = main0;
        state[PSS2] = pssInput0; state[PSS1] = pssInput0; state[VAS] = vas0;
        double directPss = pss == 1 || pss == 2 ? stabilizer : 0;
        reference = main0 + sensed - directLimiterInput() - directPss;
        System.arraycopy(state, 0, trial, 0, state.length);
        active = state; outputSignal = efd0; initialized = true;
        return true;
    }

    private void loadAndCorrect() {
        pss = normalizeLocation(data.getPss()); oel = normalizeLocation(data.getOel());
        uel = normalizeLocation(data.getUel()); scl = normalizeLocation(data.getScl());
        sw1 = data.getSw1(); tr = correctedTransducer(data.getTr());
        kr = data.getKr() <= 0 ? minimumTime() : data.getKr();
        tc1 = data.getTc1(); tb1 = data.getTb1(); tc2 = data.getTc2(); tb2 = data.getTb2();
        tuc1 = data.getTuc1(); tub1 = data.getTub1(); tuc2 = data.getTuc2(); tub2 = data.getTub2();
        toc1 = data.getToc1(); tob1 = data.getTob1(); toc2 = data.getToc2(); tob2 = data.getTob2();
        vrsmax = Math.max(data.getVrsmax(), data.getVrsmin());
        vrsmin = Math.min(data.getVrsmax(), data.getVrsmin());
        vrmax = Math.max(data.getVrmax(), data.getVrmin());
        vrmin = Math.min(data.getVrmax(), data.getVrmin());
        t1 = correctedBypass(data.getT1()); kp = data.getKp(); kc = data.getKc();
        ki = data.getKi(); xl = data.getXl(); thetaP = data.getThetaP(); vbmax = data.getVbmax();
    }

    private double minimumTime() { return minimumTimeConstantMultiplier * integrationStep; }

    private double correctedTransducer(double value) {
        double minimum = minimumTime();
        if (value > 0 && value < .25 * minimum) return 0;
        if (value > .25 * minimum && value < .5 * minimum) return .5 * minimum;
        return value;
    }

    private double correctedBypass(double value) {
        double minimum = minimumTime();
        if (value > 0 && value < .5 * minimum) return 0;
        if (value > .5 * minimum && value < minimum) return minimum;
        return value;
    }

    private boolean anyNegativeTime() {
        return tc1 < 0 || tb1 < 0 || tc2 < 0 || tb2 < 0 || tuc1 < 0 || tub1 < 0
                || tuc2 < 0 || tub2 < 0 || toc1 < 0 || tob1 < 0 || toc2 < 0
                || tob2 < 0 || t1 < 0;
    }

    private boolean finiteParameters() {
        double[] values = {tr, kr, tc1, tb1, tc2, tb2, tuc1, tub1, tuc2, tub2,
                toc1, tob1, toc2, tob2, vrsmax, vrsmin, vrmax, vrmin, t1,
                kp, kc, ki, xl, thetaP, vbmax};
        for (double value : values) if (!Double.isFinite(value)) return false;
        return true;
    }

    private void calculateIntermediateLimits() {
        max1 = intermediateLimit(vrmax - vrmin, tb1, tc1);
        max2 = intermediateLimit(vrmax - vrmin, tub1, tuc1);
        max3 = intermediateLimit(vrmax - vrmin, tob1, toc1);
        max4 = intermediateLimit(vrsmax - vrsmin, tb1, tc1);
    }

    private double intermediateLimit(double span, double lag, double lead) {
        return lead > EPS ? Math.max(0, span * lag / (kr * lead)) : Double.POSITIVE_INFINITY;
    }

    @Override
    public boolean nextStep(double dt, DynamicSimuMethod method, Machine machine, int flag) {
        if (!initialized || dt < 0) return false;
        if (dt == 0) { outputSignal = algebraics(active, machine).efd; return true; }
        int stage = method == DynamicSimuMethod.MODIFIED_EULER ? flag : 2;
        if (stage == 0) {
            derivatives(state, oldDerivative, machine);
            add(state, oldDerivative, dt, trial); constrain(trial); active = trial;
        } else if (stage == 1) {
            double[] corrected = new double[state.length]; derivatives(trial, corrected, machine);
            for (int i = 0; i < state.length; i++) state[i] += .5 * (oldDerivative[i] + corrected[i]) * dt;
            constrain(state); active = state;
        } else {
            double[] derivative = new double[state.length]; derivatives(state, derivative, machine);
            add(state, derivative, dt, state); constrain(state); active = state;
        }
        outputSignal = algebraics(active, machine).efd;
        return true;
    }

    private void derivatives(double[] x, double[] derivative, Machine machine) {
        Arrays.fill(derivative, 0); Algebraic a = algebraics(x, machine);
        derivative[SENSED] = lagDerivative(sensingVoltage(machine), x[SENSED], tr);
        leadLagDerivative(x, derivative, NORMAL2, a.mainInput, tc2, tb2, -max1, max1);
        leadLagDerivative(x, derivative, NORMAL1, a.normal2, tc1, tb1, vrmin / kr, vrmax / kr);
        leadLagDerivative(x, derivative, UEL2, a.mainInput, tuc2, tub2, -max2, max2);
        leadLagDerivative(x, derivative, UEL1, a.uel2, tuc1, tub1, vrmin / kr, vrmax / kr);
        leadLagDerivative(x, derivative, OEL2, a.mainInput, toc2, tob2, -max3, max3);
        leadLagDerivative(x, derivative, OEL1, a.oel2, toc1, tob1, vrmin / kr, vrmax / kr);
        leadLagDerivative(x, derivative, PSS2, a.pssInput, tc2, tb2, -max4, max4);
        leadLagDerivative(x, derivative, PSS1, a.pss2, tc1, tb1, vrsmin / kr, vrsmax / kr);
        double rate = lagDerivative(a.finalInput, x[VAS], t1);
        if ((x[VAS] >= vrmax - EPS && rate > 0) || (x[VAS] <= vrmin + EPS && rate < 0)) rate = 0;
        derivative[VAS] = rate;
    }

    private void leadLagDerivative(double[] x, double[] derivative, int index,
            double input, double lead, double lag, double lower, double upper) {
        if (lag <= EPS) return;
        double rate = (input - x[index]) / lag;
        double output = leadLagOutput(input, x[index], lead, lag);
        double stateGain = 1 - lead / lag;
        if ((output >= upper - EPS && stateGain * rate > 0)
                || (output <= lower + EPS && stateGain * rate < 0)) rate = 0;
        derivative[index] = rate;
    }

    private Algebraic algebraics(double[] x, Machine machine) {
        double sensed = tr > EPS ? x[SENSED] : sensingVoltage(machine);
        double stabilizer = stabilizerSignal(machine);
        double summed = reference - sensed + directLimiterInput() + (pss == 1 ? stabilizer : 0);

        double hvWithoutScl = uel == 2 && hasVuel ? Math.max(summed, vuel) : summed;
        boolean uelActive = uel == 2 && hasVuel && vuel > summed;
        double hv1 = hvWithoutScl;
        if (scl == 2 && hasVsclUel) hv1 = Math.max(hv1, vsclUel);
        boolean oelActive = oel == 2 && hasVoel && voel < hvWithoutScl;
        double gated1 = oel == 2 && hasVoel ? Math.min(hv1, voel) : hv1;
        if (scl == 2 && hasVsclOel) gated1 = Math.min(gated1, vsclOel);

        boolean takeoverActive = uelActive || oelActive;
        double vs1 = pss == 2 && !takeoverActive ? stabilizer : 0;
        double vs2 = pss == 2 && takeoverActive ? stabilizer : 0;
        double mainInput = gated1 + vs1;
        double normal2 = limitedLeadLag(mainInput, x[NORMAL2], tc2, tb2, -max1, max1);
        double normal1 = limitedLeadLag(normal2, x[NORMAL1], tc1, tb1, vrmin / kr, vrmax / kr);
        double uel2 = limitedLeadLag(mainInput, x[UEL2], tuc2, tub2, -max2, max2);
        double uel1 = limitedLeadLag(uel2, x[UEL1], tuc1, tub1, vrmin / kr, vrmax / kr);
        double oel2 = limitedLeadLag(mainInput, x[OEL2], toc2, tob2, -max3, max3);
        double oel1 = limitedLeadLag(oel2, x[OEL1], toc1, tob1, vrmin / kr, vrmax / kr);
        double selected = oelActive ? oel1 : uelActive ? uel1 : normal1;
        double regulator = clamp(kr * selected, vrmin, vrmax);

        double hv2 = uel == 3 && hasVuel ? Math.max(regulator, vuel) : regulator;
        if (scl == 3 && hasVsclUel) hv2 = Math.max(hv2, vsclUel);
        double gated2 = oel == 3 && hasVoel ? Math.min(hv2, voel) : hv2;
        if (scl == 3 && hasVsclOel) gated2 = Math.min(gated2, vsclOel);

        double pssInput = vs2 + (pss == 3 ? stabilizer : 0);
        double pss2 = limitedLeadLag(pssInput, x[PSS2], tc2, tb2, -max4, max4);
        double pss1 = limitedLeadLag(pss2, x[PSS1], tc1, tb1, vrsmin / kr, vrsmax / kr);
        double pssRegulator = clamp(kr * pss1, vrsmin, vrsmax);
        double finalInput = gated2 + pssRegulator;
        double vas = t1 > EPS ? x[VAS] : clamp(finalInput, vrmin, vrmax);
        double available = availableExciterVoltage(machine);
        double efd = vas * available;
        return new Algebraic(sensed, summed, hv1, gated1, uelActive, oelActive,
                takeoverActive, vs1, vs2, mainInput, normal2, normal1, uel2,
                uel1, oel2, oel1, selected, regulator, hv2, gated2, pssInput,
                pss2, pss1, pssRegulator, finalInput, vas, available, efd);
    }

    private double directLimiterInput() {
        double value = 0;
        if (uel == 1 && hasVuel) value += vuel;
        if (oel == 1 && hasVoel) value += voel;
        if (scl == 1 && hasVsclSum) value += vsclSum;
        return value;
    }

    private double limitedLeadLag(double input, double stateValue, double lead,
            double lag, double lower, double upper) {
        return clamp(leadLagOutput(input, stateValue, lead, lag), lower, upper);
    }

    private static double leadLagOutput(double input, double stateValue, double lead, double lag) {
        return lag > EPS ? (lead / lag) * input + (1 - lead / lag) * stateValue : input;
    }

    private Complex machineBaseCurrent(Machine machine) {
        return machine.getIxy().divide(machine.getIMultiFactor());
    }

    private double compoundSource(Machine machine) {
        Complex angle = new Complex(Math.cos(Math.toRadians(thetaP)), Math.sin(Math.toRadians(thetaP)));
        Complex kpPhasor = angle.multiply(kp);
        Complex terminalVoltage = machine.getDStabBus().getVoltage();
        Complex terminalCurrent = machineBaseCurrent(machine);
        return kpPhasor.multiply(terminalVoltage)
                .add(Complex.I.multiply(new Complex(ki, 0).add(kpPhasor.multiply(xl)))
                        .multiply(terminalCurrent)).abs();
    }

    private double selectedSource(Machine machine) { return sw1 == 1 ? compoundSource(machine) : kp; }

    private double availableExciterVoltage(Machine machine) {
        double source = Math.max(0, selectedSource(machine));
        if (source <= EPS) return 0;
        double ratio = clamp(kc * fieldCurrent(machine) / source, 0, 1);
        return clamp(source * Exac1Exciter.rectifierFactor(ratio), 0, vbmax);
    }

    private void constrain(double[] values) {
        values[VAS] = clamp(values[VAS], vrmin, vrmax);
        for (int i = 0; i < values.length; i++) if (!Double.isFinite(values[i])) values[i] = 0;
    }

    private static double lagDerivative(double input, double value, double time) {
        return time > EPS ? (input - value) / time : 0;
    }

    private static void add(double[] x, double[] derivative, double dt, double[] result) {
        for (int i = 0; i < x.length; i++) result[i] = x[i] + derivative[i] * dt;
    }

    private static double clamp(double value, double lower, double upper) {
        return Math.max(lower, Math.min(upper, value));
    }

    private static int normalizeLocation(int value) { return value == 0 ? 1 : value; }
    private static boolean validLocation(int value) { return value >= 1 && value <= 3; }

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

    private static double fieldCurrent(Machine machine) {
        double value = machine.calculateIfd(MachineIfdBase.EXCITER);
        return Double.isFinite(value) ? value : 0;
    }

    public void setVuel(double value) { vuel = value; hasVuel = true; }
    public void setVoel(double value) { voel = value; hasVoel = true; }
    public void setVsclSum(double value) { vsclSum = value; hasVsclSum = true; }
    public void setVsclUel(double value) { vsclUel = value; hasVsclUel = true; }
    public void setVsclOel(double value) { vsclOel = value; hasVsclOel = true; }
    public double getSensedVoltage() { return algebraics(active, getMachine()).sensed; }
    public double getSummedError() { return algebraics(active, getMachine()).summed; }
    public double getFirstHighGate() { return algebraics(active, getMachine()).hv1; }
    public double getFirstGatedSignal() { return algebraics(active, getMachine()).gated1; }
    public boolean isUelTakeoverActive() { return algebraics(active, getMachine()).uelActive; }
    public boolean isOelTakeoverActive() { return algebraics(active, getMachine()).oelActive; }
    public double getVs1() { return algebraics(active, getMachine()).vs1; }
    public double getVs2() { return algebraics(active, getMachine()).vs2; }
    public double getNormalPathOutput() { return algebraics(active, getMachine()).normal1; }
    public double getUelPathOutput() { return algebraics(active, getMachine()).uel1; }
    public double getOelPathOutput() { return algebraics(active, getMachine()).oel1; }
    public double getSelectedPathOutput() { return algebraics(active, getMachine()).selected; }
    public double getRegulatorOutput() { return algebraics(active, getMachine()).regulator; }
    public double getSecondGatedSignal() { return algebraics(active, getMachine()).gated2; }
    public double getPssRegulatorOutput() { return algebraics(active, getMachine()).pssRegulator; }
    public double getFinalLagInput() { return algebraics(active, getMachine()).finalInput; }
    public double getCompoundSource() { return compoundSource(getMachine()); }
    public double getAvailableExciterVoltage() { return algebraics(active, getMachine()).available; }
    public double[] getStateSnapshot() { return active.clone(); }

    @Override public double getOutput(Machine machine) { outputSignal = algebraics(active, machine).efd; return outputSignal; }
    @Override public void setRefPoint(double value) { reference = value; }
    @Override public double getRefPoint() { return reference; }

    private record Algebraic(double sensed, double summed, double hv1, double gated1,
            boolean uelActive, boolean oelActive, boolean takeoverActive,
            double vs1, double vs2, double mainInput, double normal2, double normal1,
            double uel2, double uel1, double oel2, double oel1, double selected,
            double regulator, double hv2, double gated2, double pssInput,
            double pss2, double pss1, double pssRegulator, double finalInput,
            double vas, double available, double efd) { }

    @Override public AnController getAnController() { return getClass().getAnnotation(AnController.class); }
    @Override public Field getField(String name) throws Exception { return getClass().getField(name); }
    @Override public Object getFieldObject(Field field) throws Exception { return field.get(this); }
}
