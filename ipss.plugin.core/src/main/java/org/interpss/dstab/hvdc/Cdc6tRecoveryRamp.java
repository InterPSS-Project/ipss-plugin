package org.interpss.dstab.hvdc;

/** Prepared recovery factors; the event owner supplies elapsed time since accepted restart. */
public final class Cdc6tRecoveryRamp {
    private final double initialVoltage,initialCurrent,voltageRate,currentRate;

    public Cdc6tRecoveryRamp(double restartVolts,double scheduledVolts,double restartAmperes,
            double scheduledAmperes,double voltageRatePerSecond,double currentRatePerSecond) {
        if(!Double.isFinite(scheduledVolts)||scheduledVolts<=0
                ||!Double.isFinite(scheduledAmperes)||scheduledAmperes<=0)
            throw new IllegalArgumentException("Positive finite recovery bases required");
        nonnegative(restartVolts);nonnegative(restartAmperes);
        nonnegative(voltageRatePerSecond);nonnegative(currentRatePerSecond);
        initialVoltage=Math.min(1,restartVolts/scheduledVolts);
        initialCurrent=Math.min(1,restartAmperes/scheduledAmperes);
        voltageRate=voltageRatePerSecond;currentRate=currentRatePerSecond;
    }

    /** Stateless evaluation is safe for repeated network trials and either integration method. */
    public double voltageFactor(double elapsedSeconds) {
        nonnegative(elapsedSeconds);
        return Math.min(1,initialVoltage+voltageRate*elapsedSeconds);
    }

    /** Apply before the voltage-dependent current ceiling, which remains effective during recovery. */
    public double currentFactor(double elapsedSeconds) {
        nonnegative(elapsedSeconds);
        return Math.min(1,initialCurrent+currentRate*elapsedSeconds);
    }

    private static void nonnegative(double value) {
        if(!Double.isFinite(value)||value<0)throw new IllegalArgumentException("Nonnegative finite recovery input required");
    }
}
