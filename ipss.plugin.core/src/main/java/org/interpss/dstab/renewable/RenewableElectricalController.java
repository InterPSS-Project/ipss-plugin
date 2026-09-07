package org.interpss.dstab.renewable;

import org.interpss.dstab.control.util.IntegrationStepAware;

/** Common signal contract between a REGC_A converter and REEC_A/REEC_B controls. */
public interface RenewableElectricalController extends IntegrationStepAware {
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
    Repca1Model getPlantController();
    void setPlantController(Repca1Model plantController);
}
