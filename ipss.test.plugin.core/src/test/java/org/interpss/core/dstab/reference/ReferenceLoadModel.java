package org.interpss.core.dstab.reference;

import org.interpss.fadapter.psse.PSSEMultiFileLoader;

/**
 * Static-load representation of the archived SMIB reference runs.
 *
 * <p>The PowerWorld, ANDES, PSS/E and independent-integrator SMIB references ran
 * the bus-2 static load as constant impedance for both P and Q. The PSS/E importer now
 * defaults to constant-current P and constant-admittance Q, so comparisons with
 * those references declare the experiment's load model explicitly instead of
 * relying on the importer default.
 */
public final class ReferenceLoadModel {
    public static final String CONSTANT_IMPEDANCE_JSON =
            "{\"activeStaticLoadModel\":\"CONST_Z\",\"reactiveStaticLoadModel\":\"CONST_Z\"}";

    private ReferenceLoadModel() {}

    /** A loader that represents static P and Q as constant impedance. */
    public static PSSEMultiFileLoader constantImpedanceLoader() {
        return new PSSEMultiFileLoader().setDynamicRunConfigJson(CONSTANT_IMPEDANCE_JSON);
    }
}
