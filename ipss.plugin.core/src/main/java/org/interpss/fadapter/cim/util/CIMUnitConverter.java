/*
 * CIMUnitConverter.java
 *
 * Unit conversion utilities for CIM physical units to per-unit.
 */

package org.interpss.fadapter.cim.util;

/**
 * Converts CIM physical units (Ohms, Siemens, W/var, kV) to per-unit values.
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

    /** CIM ActivePower / ReactivePower are SI (W / var). */
    private static final double W_PER_MW = 1_000_000.0;

    /**
     * Convert CIM ActivePower (Watts) or ReactivePower (var) to MW / MVAr.
     */
    public static double siPowerToMVA(double wattsOrVars) {
        return wattsOrVars / W_PER_MW;
    }

    /**
     * Convert CIM ActivePower (Watts) to per-unit on {@code baseMVA}.
     * Ppu = (P_W / 1e6) / baseMVA
     */
    public static double pToPU(double watts, double baseMVA) {
        return siPowerToMVA(watts) / baseMVA;
    }

    /**
     * Convert CIM ReactivePower (var) to per-unit on {@code baseMVA}.
     * Qpu = (Q_var / 1e6) / baseMVA
     */
    public static double qToPU(double vars, double baseMVA) {
        return siPowerToMVA(vars) / baseMVA;
    }

    /**
     * Normalize CIM voltage to kV.
     * Spec uses volts; some exports (ENTSO-E) already use kV.
     */
    public static double toKV(double voltage) {
        return voltage > 1000.0 ? voltage / 1000.0 : voltage;
    }

    /**
     * Normalize CIM ApparentPower to MVA.
     * Spec uses VA; values already in MVA are left unchanged.
     */
    public static double apparentPowerToMVA(double apparentPower) {
        return Math.abs(apparentPower) >= 1e6 ? apparentPower / 1e6 : apparentPower;
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
