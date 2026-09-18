package org.interpss.dstab.mach;

import org.apache.commons.math3.complex.Complex;

import com.interpss.dstab.controller.cml.ICMLMachineVoltageProvider;
import com.interpss.dstab.mach.Machine;

/** Machine boundary that can supply the algebraic PSS/E IEEEVC signal. */
public interface IeeeVoltageCompensatedMachine extends Machine, ICMLMachineVoltageProvider {
    IeeeVcData getIeeeVcData();

    void setIeeeVcData(IeeeVcData data);

    /** Published IEEEVC signal using generator current out of the machine. */
    default double getIeeeVcVoltage() {
        Complex terminalVoltage = getDStabBus().getVoltage();
        Complex generatorCurrent = getIgen().subtract(terminalVoltage.multiply(getYgen()));
        IeeeVcData data = getIeeeVcData();
        Complex impedanceOnSystemBase = new Complex(data.rc(), data.xc())
                .multiply(getZMultiFactor());
        return terminalVoltage.add(impedanceOnSystemBase.multiply(generatorCurrent))
                .abs() / getVMultiFactor();
    }

    @Override
    default double getCmlMachineVoltage() {
        return getIeeeVcData() == null
                ? getDStabBus().getVoltage().abs() / getVMultiFactor()
                : getIeeeVcVoltage();
    }

    /** Shared boundary for equation-based exciters that do not use CML expressions. */
    static double sensedVoltage(Machine machine) {
        if (machine instanceof ICMLMachineVoltageProvider provider) {
            double value = provider.getCmlMachineVoltage();
            if (Double.isFinite(value)) return value;
        }
        return machine.getDStabBus().getVoltage().abs() / machine.getVMultiFactor();
    }
}
