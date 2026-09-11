package org.interpss.dstab.control.pss.psse.psssb;

import java.lang.reflect.Field;
import java.util.LinkedHashMap;
import java.util.Map;

import org.interpss.dstab.control.pss.ieee.y1992.pss2a.Ieee1992PSS2AStabilizer;

import com.interpss.dstab.BaseDStabBus;
import com.interpss.dstab.algo.DynamicSimuMethod;
import com.interpss.dstab.controller.cml.annotate.AnController;
import com.interpss.dstab.mach.Machine;

/**
 * PSLF/PowerWorld PSSSB: an IEEE PSS2A with a manually switched transient
 * voltage-boost branch and terminal-voltage-deviation cutout.
 */
@AnController(input="mach.speed", output="this.outputBlock.y", refPoint="0.0", display={})
public final class PsssbStabilizer extends Ieee1992PSS2AStabilizer {
    private static final double EPS = 1.0e-12;

    private int boostSwitch;
    private double td1;
    private double td2;
    private double vtl;
    private double vk;
    private double vcutoff;
    private double initialTerminalVoltage;

    // Canonical denominator memories for 1/(1+sTd1) and 1/(1+sTd2).
    private double boostLagState;
    private double boostWashoutLagState;
    private double predictorLagState;
    private double predictorWashoutState;
    private double predictorLagDerivative;
    private double predictorWashoutDerivative;

    public PsssbStabilizer() {
        this("id", "PSSSB", "WECC");
    }

    public PsssbStabilizer(String id, String name, String category) {
        super(id, name, category);
        _data = new PsssbStabilizerData();
    }

    @Override
    public PsssbStabilizerData getData() {
        return (PsssbStabilizerData) _data;
    }

    public void setData(PsssbStabilizerData data) {
        if (data == null) throw new IllegalArgumentException("PSSSB data is required");
        _data = data;
    }

    @Override
    public boolean initStates(BaseDStabBus<?, ?> bus, Machine machine) {
        PsssbStabilizerData data = getData();
        if (data.getSw1() != 0 && data.getSw1() != 1) return false;
        if (data.getTd1() < 0.0 || data.getTd2() < 0.0) return false;
        boostSwitch = data.getSw1();
        td1 = data.getTd1();
        td2 = data.getTd2();
        vtl = Math.abs(data.getVtl());
        vk = data.getVk();
        vcutoff = data.getVcutoff();
        initialTerminalVoltage = bus.getVoltageMag();
        // Initialize the inherited PSS2A at its own zero-output equilibrium
        // before applying the independent start-of-simulation boost step.
        if (!super.initStates(bus, machine)) return false;
        double command = boostCommand();
        boostLagState = command;
        // The published model applies Vk as a start-of-simulation step.  Its
        // washout denominator memory therefore starts at zero, producing the
        // documented initial WOTEB output Vk when Sw1 is enabled.
        boostWashoutLagState = 0.0;
        predictorLagState = command;
        predictorWashoutState = 0.0;
        predictorLagDerivative = 0.0;
        predictorWashoutDerivative = 0.0;
        return true;
    }

    /** Changes the published manual selector (1 = Vk branch, 0 = zero). */
    public void setBoostSwitch(int value) {
        if (value != 0 && value != 1) {
            throw new IllegalArgumentException("PSSSB Sw1 must be 0 or 1");
        }
        boostSwitch = value;
    }

    public int getBoostSwitch() { return boostSwitch; }

    @Override
    public boolean nextStep(double dt, DynamicSimuMethod method, Machine machine, int flag) {
        boolean result = super.nextStep(dt, method, machine, flag);
        if (!result) return false;
        advanceBoost(dt, flag);
        return true;
    }

    private void advanceBoost(double dt, int flag) {
        double command = boostCommand();
        if (flag == 0) {
            predictorLagState = boostLagState;
            predictorWashoutState = boostWashoutLagState;
            predictorLagDerivative = lagDerivative(command, boostLagState, td1);
            predictorWashoutDerivative = lagDerivative(
                    boostLagState, boostWashoutLagState, td2);
            boostLagState = td1 > EPS
                    ? boostLagState + predictorLagDerivative * dt : command;
            boostWashoutLagState = td2 > EPS
                    ? boostWashoutLagState + predictorWashoutDerivative * dt
                    : boostLagState;
        }
        else {
            double lagDerivative = lagDerivative(command, boostLagState, td1);
            double washoutDerivative = lagDerivative(
                    boostLagState, boostWashoutLagState, td2);
            boostLagState = td1 > EPS
                    ? predictorLagState
                            + 0.5 * (predictorLagDerivative + lagDerivative) * dt
                    : command;
            boostWashoutLagState = td2 > EPS
                    ? predictorWashoutState
                            + 0.5 * (predictorWashoutDerivative + washoutDerivative) * dt
                    : boostLagState;
        }
    }

    private static double lagDerivative(double input, double state, double timeConstant) {
        return timeConstant > EPS ? (input - state) / timeConstant : 0.0;
    }

    private double boostCommand() {
        return boostSwitch == 1 ? vk : 0.0;
    }

    /** Output of the published sTd2/(1+sTd2) transient-boost path. */
    public double getTransientBoostOutput() {
        double output = getTransientBoostWashoutOutput();
        return vtl > EPS ? Math.max(-vtl, Math.min(vtl, output)) : output;
    }

    /** Published PowerWorld state 20 (WOTEB); this is an output coordinate. */
    public double getTransientBoostWashoutOutput() {
        return td2 > EPS ? boostLagState - boostWashoutLagState : 0.0;
    }

    public double getTransientBoostLagState() { return boostLagState; }
    public double getTransientBoostWashoutLagState() { return boostWashoutLagState; }

    public double getTerminalVoltageDeviation(Machine machine) {
        return initialTerminalVoltage - machine.getDStabBus().getVoltageMag();
    }

    public boolean isVoltageCutoutActive(Machine machine) {
        return getTerminalVoltageDeviation(machine) > vcutoff;
    }

    @Override
    public double getOutput(Machine machine) {
        if (isVoltageCutoutActive(machine)) return 0.0;
        return super.getOutput(machine) + getTransientBoostOutput();
    }

    @Override
    public Map<String, Double> getNamedStates() {
        Map<String, Double> states = new LinkedHashMap<>(super.getNamedStates());
        states.put("Transient boost lag", boostLagState);
        states.put("Transient boost washout memory", boostWashoutLagState);
        return Map.copyOf(states);
    }

    @Override
    public Field getField(String name) throws Exception {
        Class<?> type = getClass();
        while (type != null) {
            try {
                Field field = type.getDeclaredField(name);
                field.setAccessible(true);
                return field;
            }
            catch (NoSuchFieldException ignored) {
                type = type.getSuperclass();
            }
        }
        throw new NoSuchFieldException(name);
    }
}
