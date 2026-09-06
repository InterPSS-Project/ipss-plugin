package org.interpss.dstab.control.util;

/** Receives the simulation integration step before dynamic-state initialization. */
public interface IntegrationStepAware {
    void configureIntegrationStep(double timeStepSec);
}
