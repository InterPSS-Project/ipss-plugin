package org.interpss.dstab.renewable;

/** Operating limits applied to the active-power controller, before current limiting. */
public enum ActivePowerResponseLimits {
    NORMAL, DOWN_ONLY, FIXED;

    public double upper(double modelLimit, double initialValue) {
        return this == NORMAL ? modelLimit : initialValue;
    }

    public double lower(double modelLimit, double initialValue) {
        return this == FIXED ? initialValue : modelLimit;
    }

    public static ActivePowerResponseLimits fromCode(int code) {
        return switch (code) {
            case 0 -> NORMAL;
            case 1 -> DOWN_ONLY;
            case 2 -> FIXED;
            default -> throw new IllegalArgumentException("Response limit code must be 0, 1 or 2");
        };
    }
}
