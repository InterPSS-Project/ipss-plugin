package org.interpss.dstab.mach;

import com.interpss.dstab.mach.impl.EConstMachineImpl;

/** Classical machine with an optional IEEEVC sensing boundary. */
public final class IeeeVcEConstMachine extends EConstMachineImpl
        implements IeeeVoltageCompensatedMachine {
    static { IeeeVcMachineState.register(IeeeVcEConstMachine.class,IeeeVcEConstMachine::new); }
    private IeeeVcData ieeeVcData;

    @Override
    protected double cal_dW_dt(double speed, double baseFrequency) {
        // GENCLS stores electrical POWER (E * conjugate(I)), unlike the
        // GENROU Flux0 torque coordinate. Both powers become shaft torque.
        double damping = getD() * .01 * baseFrequency;
        return (getPm() - getPe() - getPe_NegSeq() - damping * (speed - 1))
                / (2 * getH() * speed);
    }

    @Override
    public IeeeVcData getIeeeVcData() {
        return ieeeVcData;
    }

    @Override
    public void setIeeeVcData(IeeeVcData data) {
        ieeeVcData = data;
    }
}
