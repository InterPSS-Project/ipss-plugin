/*
 * CIMUnitConverter.java
 *
 * Unit conversion utilities for CIM physical units to per-unit.
 */

package org.interpss.fadapter.cim.util;

/**
 * Converts CIM physical units (Ohms, Siemens, MW, kV) to per-unit values.
 */
public final class CIMUnitConverter {

    private CIMUnitConverter() {}

    /**
     * Convert impedance from Ohms to per-unit.
     * Zpu = Zohm * baseMVA / (baseKV^2 * 1000)
     *
     * @param ohms impedance in Ohms
     * @param baseMVA system base power in MVA
     * @param baseKV base voltage in kV
     * @return impedance in per-unit
     */
    public static double zToPU(double ohms, double baseMVA, double baseKV) {
        double baseZ = baseKV * baseKV * 1000.0 / baseMVA;
        return ohms / baseZ;
    }

    /**
     * Convert admittance from Siemens to per-unit.
     * Ypu = Ysiemens * (baseKV^2 * 1000) / baseMVA
     *
     * @param siemens admittance in Siemens
     * @param baseMVA system base power in MVA
     * @param baseKV base voltage in kV
     * @return admittance in per-unit
     */
    public static double yToPU(double siemens, double baseMVA, double baseKV) {
        double baseY = baseMVA / (baseKV * baseKV * 1000.0);
        return siemens / baseY;
    }

    /**
     * Convert power from MW to per-unit.
     * Ppu = Pmw / baseMVA
     */
    public static double pToPU(double mw, double baseMVA) {
        return mw / baseMVA;
    }

    /**
     * Convert reactive power from MVAr to per-unit.
     * Qpu = Qmvar / baseMVA
     */
    public static double qToPU(double mvar, double baseMVA) {
        return mvar / baseMVA;
    }

    /**
     * Convert voltage from kV to per-unit.
     * Vpu = Vkv / baseKV
     */
    public static double vToPU(double kv, double baseKV) {
        return kv / baseKV;
    }

    /**
     * Convert angle from degrees to radians.
     */
    public static double degToRad(double degrees) {
        return Math.toRadians(degrees);
    }

    /**
     * Compute base impedance in Ohms.
     */
    public static double baseZ(double baseMVA, double baseKV) {
        return baseKV * baseKV * 1000.0 / baseMVA;
    }

    /**
     * Compute base admittance in Siemens.
     */
    public static double baseY(double baseMVA, double baseKV) {
        return baseMVA / (baseKV * baseKV * 1000.0);
    }
}
