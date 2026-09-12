package org.interpss.dstab.control.exc;

/** Exciter-side contract used by independently integrated UEL models. */
public interface UnderExcitationLimiterTarget {
    void setVuel(double value);

    default void clearVuel() {
        setVuel(0.0);
    }

    /** Excitation-system stabilizing signal supplied to a UEL, when available. */
    default double getUelStabilizingSignal() {
        return 0.0;
    }

    /** ST7C reference-feedback signal used only by UEL2C's KFB path. */
    default double getUelReferenceFeedbackSignal() {
        return 0.0;
    }
}
