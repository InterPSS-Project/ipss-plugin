package org.interpss.dstab.control.util;

import java.util.Map;

/** Read-only access to integration coordinates and the last evaluated rates. */
public interface IntegrationStateAccess {
    /** Current stored coordinates, which may differ from published block outputs. */
    Map<String, Double> getIntegrationStates();

    /**
     * Detached snapshot of the last rate evaluation, including its input states.
     * It is not necessarily evaluated at the current corrected state. Empty rates
     * mean no evaluation since initialization, never a claim of equilibrium.
     * TWO_STEP_EVENT retains current pre-step coordinates with rates averaged
     * across the event; those rates are not an instantaneous RHS at those states.
     */
    IntegrationStateMonitor.Snapshot getStateDerivativeSnapshot();
}
