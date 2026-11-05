package org.interpss.dstab.hvdc;

/** Current-order converter API backed by the shared prepared conducting-bridge equations. */
public final class Cdc6tConverterBoundary {
    private final LccConverterBoundary equations;
    public Cdc6tConverterBoundary(boolean rectifier,int bridges,double acBaseVolts,double transformerRatio,
            double tap,double reactanceOhms,double networkBaseVA) {
        equations=new LccConverterBoundary(rectifier,bridges,acBaseVolts,transformerRatio,tap,reactanceOhms,networkBaseVA);
    }
    /** Finite nonnegative commutating resistance in ohms per bridge. */
    public Cdc6tConverterBoundary(boolean rectifier,int bridges,double acBaseVolts,double transformerRatio,
            double tap,double resistanceOhms,double reactanceOhms,double networkBaseVA) {
        equations=new LccConverterBoundary(rectifier,bridges,acBaseVolts,transformerRatio,tap,resistanceOhms,reactanceOhms,networkBaseVA);
    }
    /** Maximum conducting DC voltage at the supplied minimum angle, with taps held fixed. */
    public double maximumDcVoltage(double acVoltagePu,double amperes,double minimumAngleRadians) {
        return equations.maximumDcVoltage(acVoltagePu,amperes,minimumAngleRadians);
    }
    /** Writes AC P/Q (pu) and firing/extinction angle (radians) from positive terminal DC voltage.
     * Invalid trials leave the caller's output buffer unchanged. */
    public void evaluate(double acVoltagePu,double dcVolts,double amperes,double[] output,int offset) {
        equations.evaluateDcVoltage(acVoltagePu,dcVolts,amperes,output,offset);
    }
}
