package org.interpss.dstab.control.oel.psse.oel2c;

/** OEL2C record in published ICON/CON order. */
public record Oel2cData(int inputMode, int rampMode,
        double tc1, double tb1, double tc2, double tb2,
        double kp, double ki, double kd, double td,
        double pidMax, double pidMin, double leadLag1Max, double leadLag1Min,
        double outputMax, double outputMin, double resetReference,
        double activationDelay, double resetDelay, double resetThreshold,
        double inputScale, double inputFilterTime, double actualScale,
        double inverseReference, double instantaneousLimit, double thermalLimit,
        double referenceFilterTime, double inverseExponent1, double inverseGain1,
        double inverseExponent2, double inverseGain2, double inverseMax,
        double inverseMin, double fixedRampUp, double fixedRampDown,
        double timerReference, double timerMax, double timerMin,
        double timerFeedback, double rampDown, double rampUp,
        double releaseThreshold, double ratedFieldCurrent) {

    public Oel2cData {
        double[] values = {tc1,tb1,tc2,tb2,kp,ki,kd,td,pidMax,pidMin,
                leadLag1Max,leadLag1Min,outputMax,outputMin,resetReference,
                activationDelay,resetDelay,resetThreshold,inputScale,inputFilterTime,
                actualScale,inverseReference,instantaneousLimit,thermalLimit,
                referenceFilterTime,inverseExponent1,inverseGain1,inverseExponent2,
                inverseGain2,inverseMax,inverseMin,fixedRampUp,fixedRampDown,
                timerReference,timerMax,timerMin,timerFeedback,rampDown,rampUp,
                releaseThreshold,ratedFieldCurrent};
        for (double value : values) if (!Double.isFinite(value))
            throw new IllegalArgumentException("OEL2C parameters must be finite");
        if (inputMode < 1 || inputMode > 3 || (rampMode != 1 && rampMode != 2)
                || tb1 < 0 || tb2 < 0 || td < 0 || activationDelay < 0
                || resetDelay < 0 || inputFilterTime < 0 || referenceFilterTime < 0
                || pidMax < pidMin || leadLag1Max < leadLag1Min
                || outputMax < outputMin || inverseMax < inverseMin
                || timerMax < timerMin || instantaneousLimit < thermalLimit
                || inverseReference <= 0 || ratedFieldCurrent <= 0) {
            throw new IllegalArgumentException("OEL2C modes, limits, or time constants are invalid");
        }
    }
}
