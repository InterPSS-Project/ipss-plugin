package org.interpss.dstab.mach;

import com.interpss.dstab.mach.impl.EConstMachineImpl;
import com.interpss.dstab.BaseDStabBus;
import com.interpss.dstab.algo.DynamicSimuMethod;

/** Classical machine with an optional IEEEVC sensing boundary. */
public final class IeeeVcEConstMachine extends EConstMachineImpl
        implements IeeeVoltageCompensatedMachine {
    static { IeeeVcMachineState.register(IeeeVcEConstMachine.class,IeeeVcEConstMachine::new); }
    private IeeeVcData ieeeVcData;
    @Override public boolean supportsDeferredCurrentPublication(){return true;}

    /** This fixed-emf preparation is qualified without attached active controls. */
    public boolean supportsInitialOperatingPointBalance() {
        return (getGovernor() == null || !getGovernor().isActive())
                && (getExciter() == null || !getExciter().isActive())
                && (getStabilizer() == null || !getStabilizer().isActive());
    }

    /** Set shaft power from the converged initial electrical boundary, retaining
     * internal emf, angle and speed. Never call this for a running-network event. */
    public void reconcileInitialOperatingPointBalance() {
        if (!supportsInitialOperatingPointBalance())
            throw new IllegalStateException("Initial source balance with active classical-machine controls is not qualified");
        getIgen(); // Publish electrical air-gap power using the final initial voltage.
        if (!Double.isFinite(getPe()))throw new IllegalStateException("Nonfinite initial electrical power");
        setPm(getPe());
    }

    @Override
    public boolean initStates(BaseDStabBus<?, ?> bus) {
        if (!super.initStates(bus)) return false;
        // The core classical machine omits the DynamicMachine controller
        // lifecycle. GENCLS can have a governor in native PSS/E; do not accept
        // an attached but uninitialized/non-advancing turbine controller.
        return !hasGovernor() || !getGovernor().isActive()
                || getGovernor().initStates(bus, this);
    }

    @Override
    public boolean nextStep(double dt, DynamicSimuMethod method, int flag) {
        boolean governed = hasGovernor() && getGovernor().isActive();
        if (governed && !getGovernor().nextStep(dt, method, this, flag)) return false;
        if (!super.nextStep(dt, method, flag)) return false;
        // Match the existing DynamicMachine staging: publish corrected Pm
        // after the mechanical stage, not a different integration realization.
        if (governed) {
            double output = getGovernor().getOutput(this);
            if (!Double.isFinite(output)) return false;
            setPm(output);
        }
        return true;
    }

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
