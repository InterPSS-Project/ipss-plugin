package org.interpss.dstab.control.pss.ieee.y2016.pss4c;

import java.util.Arrays;

import org.interpss.dstab.control.pss.ieee.y2005.pss4b.Ieee2005PSS4BStabilizerData;
import org.interpss.dstab.control.pss.ieee.y2005.pss4b.Ieee2005PSS4BStabilizerData.BandData;

/** IEEE 421.5-2016 PSS4C data in the 94-value PowerWorld parameter order. */
public record Ieee2016PSS4CStabilizerData(
        Ieee2005PSS4BStabilizerData threeBandData,
        BandData veryLowBand) {

    public static final int PARAMETER_COUNT = 94;

    public Ieee2016PSS4CStabilizerData {
        if (threeBandData == null || veryLowBand == null) {
            throw new IllegalArgumentException("PSS4C data groups must not be null");
        }
    }

    /**
     * Parse the order published by PowerWorld: the 75 PSS4B-compatible values
     * (including VSTMAX/VSTMIN), followed by the 19 very-low-band values.
     */
    public static Ieee2016PSS4CStabilizerData fromPowerWorldParameters(double[] p) {
        if (p == null || p.length != PARAMETER_COUNT) {
            throw new IllegalArgumentException("PSS4C requires exactly 94 parameters");
        }
        return new Ieee2016PSS4CStabilizerData(
                Ieee2005PSS4BStabilizerData.fromParameters(Arrays.copyOf(p, 75)),
                BandData.fromParameters(p, 75));
    }
}
