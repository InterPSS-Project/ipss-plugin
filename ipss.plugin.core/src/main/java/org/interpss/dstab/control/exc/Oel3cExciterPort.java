package org.interpss.dstab.control.exc;

/** Explicit physical input, initialization and connection contract for an auxiliary OEL3C. */
public interface Oel3cExciterPort {
    enum Connection { DISABLED, SUMMATION, TAKEOVER }
    interface Limiter {
        boolean initializeAfterExciter(double initialExciterFieldCurrent);
        double output();
        /** Frozen endpoint output, or NaN when a read-only value is unavailable. */
        default double observeOutput(){return Double.NaN;}
    }
    Connection oel3cConnection();
    boolean bindOel3c(Limiter limiter);
    /** Static exciters may expose machine field inputs without an exciter-field circuit. */
    default boolean supportsOel3cInput(int inputFlag){return true;}
    double oel3cExciterFieldCurrent();
}
