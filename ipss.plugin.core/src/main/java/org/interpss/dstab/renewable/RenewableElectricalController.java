package org.interpss.dstab.renewable;

import com.interpss.dstab.controller.runtime.IntegrationStepAware;

/** Common signal contract between a REGC_A converter and REEC_A/REEC_B controls. */
public interface RenewableElectricalController extends IntegrationStepAware {
    /** Converts a current command to the voltage command for an explicit reactance.
     * Fixed-command providers retain their initialized voltage reference instead. */
    default double getReactiveVoltageCommand(double terminalVoltage, double reactance, double currentCommand) {
        return terminalVoltage - reactance * currentCommand;
    }
    default ActivePowerResponseLimits getActivePowerResponseLimits() {
        return ActivePowerResponseLimits.NORMAL;
    }
    /** Configure before initialization; unsupported controllers reject constrained response. */
    default void setActivePowerResponseLimits(ActivePowerResponseLimits limits) {
        if (java.util.Objects.requireNonNull(limits) != ActivePowerResponseLimits.NORMAL)
            throw new UnsupportedOperationException("Active-power response limits are unsupported by "
                    + getClass().getSimpleName());
    }
    @Override default void configureIntegrationStep(double timeStepSec) { }
    void initialize(double p, double q, double v);
    void step(double dt, double p, double q, double v, double frequency);
    /**
     * Advances one predictor/corrector stage. Existing single-stage controllers
     * retain their former behavior until they implement a stage-aware corrector.
     */
    default void step(double dt, double p, double q, double v, double frequency, int flag) {
        if (flag == 0) step(dt, p, q, v, frequency);
    }
    double getIpcmd();
    double getIqcmd();
    /** Explicit hardware blocking, distinct from a zero current command. */
    default boolean isConverterBlocked() { return false; }
    Repca1Model getPlantController();
    void setPlantController(Repca1Model plantController);
    default WindControlStack getWindControlStack() { return null; }
    default void setWindControlStack(WindControlStack stack) {
        throw new UnsupportedOperationException("This controller has no wind-control input");
    }
    default RepccModel getRepccController() { return null; }
    default HplntdCoordinator.Port getHybridController(){return null;}
    default org.interpss.dstab.mach.Reaxbu1Model getAuxiliaryController(){return null;}
    default void setAuxiliaryController(org.interpss.dstab.mach.Reaxbu1Model controller){
        throw new UnsupportedOperationException("This electrical controller has no auxiliary reference port");
    }
    default void setRepccController(RepccModel plantController) {
        throw new UnsupportedOperationException("This electrical controller has no REPCC input port");
    }
    /** Optional weak-grid limiter on the absolute plant power reference. */
    default WtgwgoaModel getWeakGridController() { return null; }
    /** Optional inertia-response power increment, distinct from weak-grid Pref. */
    default WtgibffraModel getInertialResponseController() { return null; }
    default void setWeakGridController(WtgwgoaModel controller) {
        throw new UnsupportedOperationException("This electrical controller has no weak-grid reference input");
    }
    default double getPlantPowerReference(double initialPower) {
        if(getAuxiliaryController()!=null)return getAuxiliaryController().getActiveOutput();
        if(getHybridController()!=null)return initialPower+getHybridController().activeIncrement();
        if (getRepccController() != null) return initialPower + getRepccController().activeIncrement();
        return initialPower + (getPlantController() == null ? 0.0 : getPlantController().getPref());
    }
    default double getSelectedPowerReference(double initialPower) {
        return getWeakGridController() == null ? getPlantPowerReference(initialPower)
                : getWeakGridController().getReference();
    }
}
