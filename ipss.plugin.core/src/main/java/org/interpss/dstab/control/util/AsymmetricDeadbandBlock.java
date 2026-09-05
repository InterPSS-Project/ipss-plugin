package org.interpss.dstab.control.util;

import com.interpss.dstab.controller.cml.field.block.GainBlock;

/**
 * Continuous asymmetric deadband for CML controller signals.
 *
 * <p>The lower threshold must be non-positive and the upper threshold must be
 * non-negative. Outside the deadband the threshold is subtracted so the
 * output remains continuous at both edges.</p>
 */
public class AsymmetricDeadbandBlock extends GainBlock {
    private double upper;
    private double lower;

    public AsymmetricDeadbandBlock() {
        this(0.0, 0.0);
    }

    public AsymmetricDeadbandBlock(double upper, double lower) {
        setThresholds(upper, lower);
    }

    public final void setThresholds(double upper, double lower) {
        if (upper < 0.0 || lower > 0.0 || lower > upper) {
            throw new IllegalArgumentException(
                    "Asymmetric deadband requires lower <= 0 <= upper");
        }
        this.upper = upper;
        this.lower = lower;
    }

    public double getUpper() {
        return upper;
    }

    public double getLower() {
        return lower;
    }

    @Override
    public boolean initStateY0(double y0) {
        super.k = 1.0;
        return super.initStateY0(y0);
    }

    @Override
    public double getY() {
        return apply(getU(), upper, lower);
    }

    public static double apply(double value, double upper, double lower) {
        if (value > upper) return value - upper;
        if (value < lower) return value - lower;
        return 0.0;
    }
}
