package org.interpss.dstab.control.pss.psse.pss5c;

import java.lang.reflect.Field;
import java.util.LinkedHashMap;
import java.util.Map;

import com.interpss.dstab.BaseDStabBus;
import com.interpss.dstab.algo.DynamicSimuMethod;
import com.interpss.dstab.controller.cml.annotate.AnController;
import com.interpss.dstab.controller.cml.annotate.AnnotateStabilizer;
import com.interpss.dstab.mach.Machine;

/** Native PSS5C full-order transducers and four bounded parallel bands.
 * The thirteen companion/lag coordinates are semantic solver states, not
 * unproven aliases for the reference simulator's internal state coordinates.
 */
@AnController(input="mach.speed", output="this.outputSignal", refPoint="0.0", display={})
public final class Pss5cNativeStabilizer extends AnnotateStabilizer {
    private final Pss5cNativeData data;
    private final double[] state = new double[13];
    private final double[] oldState = new double[13];
    private final double[] oldRate = new double[13];
    private boolean bypassed;
    public double outputSignal;

    public Pss5cNativeStabilizer(String id, Pss5cNativeData data, Machine machine) {
        super(id, "PSS5C", "IEEE-2016");
        this.data = data;
        setMachine(machine);
    }

    public Pss5cNativeData getData() { return data; }
    public boolean isBypassed() { return bypassed; }

    @Override public boolean initStates(BaseDStabBus<?, ?> bus, Machine machine) {
        if (!data.isBypassed() && !data.hasFullOrderTransducers()) return false;
        java.util.Arrays.fill(state, 0);
        bypassed = data.isBypassed();
        if (!bypassed) {
            double input = machine.getSpeed() - 1;
            state[0] = input;
            state[2] = input;
            for (int band = 0; band < 3; band++) {
                state[5 + 2 * band] = state[6 + 2 * band] = p(3) * input;
            }
        }
        System.arraycopy(state, 0, oldState, 0, 13);
        java.util.Arrays.fill(oldRate, 0);
        outputSignal = calculateOutput(state);
        return true;
    }

    @Override public boolean nextStep(double dt, DynamicSimuMethod method, Machine machine, int flag) {
        if (!Double.isFinite(dt) || dt < 0) return false;
        if (dt == 0 || bypassed) return true;
        double input = machine.getSpeed() - 1;
        if (method == DynamicSimuMethod.MODIFIED_EULER && flag == 0) {
            System.arraycopy(state, 0, oldState, 0, 13);
            rates(input, state, oldRate);
            for (int i = 0; i < 13; i++) state[i] = oldState[i] + dt * oldRate[i];
        } else {
            double[] rate = new double[13];
            rates(input, state, rate); // All slopes use the same complete endpoint.
            if (method == DynamicSimuMethod.MODIFIED_EULER) {
                for (int i = 0; i < 13; i++) state[i] = oldState[i] + .5 * dt * (oldRate[i] + rate[i]);
            } else {
                for (int i = 0; i < 13; i++) state[i] += dt * rate[i];
            }
        }
        outputSignal = calculateOutput(state);
        return true;
    }

    private void rates(double input, double[] x, double[] dx) {
        dx[0] = x[1];
        dx[1] = (input - x[0] - p(2) * x[1]) / p(1);
        dx[2] = x[3];
        dx[3] = x[4];
        double a1 = p(4) + p(6) / p(7);
        double a2 = (p(5) + p(4) * p(6)) / p(7);
        double a3 = p(4) * p(5) / p(7);
        dx[4] = (input - x[2] - a1 * x[3] - a2 * x[4]) / a3;
        double low = p(3) * lowSignal(x);
        double high = p(8) * highSignal(x);
        for (int band = 0; band < 4; band++) {
            int offset = 9 + 4 * band;
            double frequency = p(offset + 1);
            double signal = band == 3 ? high : low;
            int upper = 5 + 2 * band;
            if (frequency > 0) {
                dx[upper] = (signal - x[upper]) * p(26) * frequency;
                dx[upper + 1] = (signal - x[upper + 1]) * p(25) * frequency;
            } else dx[upper] = dx[upper + 1] = 0;
        }
    }

    private double lowSignal(double[] x) { return x[0] + p(0) * x[1]; }
    private double highSignal(double[] x) { return x[4]; }
    public double getLowIntermediateInput() { return bypassed ? 0 : lowSignal(state); }
    public double getHighInput() { return bypassed ? 0 : highSignal(state); }

    public double getBandOutput(int band) {
        if (band < 0 || band >= 4) throw new IllegalArgumentException("PSS5C band index must be 0..3");
        return bandOutput(state, band);
    }

    private double bandOutput(double[] x, int band) {
        if (bypassed) return 0;
        int offset = 9 + 4 * band;
        if (p(offset + 1) <= 0) return 0;
        double input = band == 3 ? p(8) * highSignal(x) : p(3) * lowSignal(x);
        int upper = 5 + 2 * band;
        double upperOutput = x[upper] + (p(26) / p(27)) * (input - x[upper]);
        double lowerOutput = x[upper + 1] + (p(25) / p(26)) * (input - x[upper + 1]);
        return clip(p(offset) * (upperOutput - lowerOutput), data.lower(offset + 2), data.upper(offset + 2));
    }

    private double calculateOutput(double[] x) {
        if (bypassed) return 0;
        double sum = 0;
        for (int band = 0; band < 4; band++) sum += bandOutput(x, band);
        return clip(sum, data.lower(28), data.upper(28));
    }

    private static double clip(double value, double low, double high) { return Math.max(low, Math.min(high, value)); }
    private double p(int index) { return data.parameter(index); }
    @Override public double getOutput(Machine machine) { return calculateOutput(state); }

    @Override public Map<String, Double> getNamedStates() {
        var result = new LinkedHashMap<String, Double>();
        String[] names = {"lowPosition", "lowRate", "highPosition", "highRate", "highAcceleration",
                "veryLowUpperLag", "veryLowLowerLag", "lowUpperLag", "lowLowerLag",
                "intermediateUpperLag", "intermediateLowerLag", "highUpperLag", "highLowerLag"};
        for (int i = 0; i < names.length; i++) result.put(names[i], state[i]);
        return java.util.Collections.unmodifiableMap(result);
    }

    @Override public AnController getAnController() { return getClass().getAnnotation(AnController.class); }
    @Override public Field getField(String name) throws Exception { return getClass().getField(name); }
    @Override public Object getFieldObject(Field field) throws Exception { return field.get(this); }
}
