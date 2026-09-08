package org.interpss.dstab.control.exc.psse.dc1c;

import org.interpss.dstab.control.exc.psse.esdc2a.Esdc2aExciter;

import com.interpss.dstab.BaseDStabBus;
import com.interpss.dstab.controller.cml.annotate.AnController;
import com.interpss.dstab.mach.Machine;

/** IEEE 421.5-2016 DC1C commutator exciter with constant regulator limits. */
@AnController(input="mach.vt", output="this.outputSignal",
        refPoint="this.reference", display={})
public class Dc1cExciter extends Esdc2aExciter {
    public static final int INPUT_UNUSED = 0;
    public static final int INPUT_SUMMATION = 1;
    public static final int INPUT_TAKEOVER = 2;

    private final Dc1cData dcData;
    private boolean hasVuel, hasVoel, hasVsclSum, hasVsclUel, hasVsclOel;
    private double vuel, voel, vsclSum, vsclUel, vsclOel;
    private double vemax, vemin;

    public Dc1cExciter(String id, Dc1cData data, Machine machine) {
        this(id, "DC1C", data, machine, false);
    }

    protected Dc1cExciter(String id, String modelName, Dc1cData data,
            Machine machine, boolean voltageDependentLimits) {
        super(id, modelName, data, machine, voltageDependentLimits);
        dcData = data;
    }

    @Override public Dc1cData getData() { return dcData; }

    public void setVuel(double value) { vuel = value; hasVuel = true; }
    public double getVuel() { return vuel; }
    public void setVoel(double value) { voel = value; hasVoel = true; }
    public double getVoel() { return voel; }
    public void setVsclSum(double value) { vsclSum = value; hasVsclSum = true; }
    public void setVsclUel(double value) { vsclUel = value; hasVsclUel = true; }
    public void setVsclOel(double value) { vsclOel = value; hasVsclOel = true; }

    @Override public boolean initStates(BaseDStabBus<?, ?> bus, Machine machine) {
        if (!validLocation(dcData.getOelLocation())
                || !validLocation(dcData.getUelLocation())
                || !validLocation(dcData.getSclLocation())) return false;
        vemax = Math.max(dcData.getVemax(), dcData.getVemin());
        vemin = Math.min(dcData.getVemax(), dcData.getVemin());
        return super.initStates(bus, machine);
    }

    @Override protected double summationLimiterInput() {
        double value = 0.0;
        if (dcData.getUelLocation() == INPUT_SUMMATION && hasVuel) value += vuel;
        if (dcData.getOelLocation() == INPUT_SUMMATION && hasVoel) value -= voel;
        if (dcData.getSclLocation() == INPUT_SUMMATION && hasVsclSum) value -= vsclSum;
        return value;
    }

    @Override protected double takeoverLimiterOutput(double regulator) {
        double value = regulator;
        if (dcData.getUelLocation() == INPUT_TAKEOVER && hasVuel) {
            value = Math.max(value, vuel);
        }
        if (dcData.getSclLocation() == INPUT_TAKEOVER && hasVsclUel) {
            value = Math.max(value, vsclUel);
        }
        if (dcData.getOelLocation() == INPUT_TAKEOVER && hasVoel) {
            value = Math.min(value, voel);
        }
        if (dcData.getSclLocation() == INPUT_TAKEOVER && hasVsclOel) {
            value = Math.min(value, vsclOel);
        }
        return value;
    }

    @Override protected double fieldUpperLimit() { return vemax; }
    @Override protected double fieldLowerLimit() { return vemin; }

    private static boolean validLocation(int value) {
        return value >= INPUT_UNUSED && value <= INPUT_TAKEOVER;
    }
}
