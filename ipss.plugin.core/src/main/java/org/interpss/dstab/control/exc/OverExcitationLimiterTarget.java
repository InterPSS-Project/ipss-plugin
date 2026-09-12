package org.interpss.dstab.control.exc;

/** Exciter-side contract used by independently integrated OEL models. */
public interface OverExcitationLimiterTarget {
    void setVoel(double value);

    default void clearVoel() {
        setVoel(0.0);
    }
}
