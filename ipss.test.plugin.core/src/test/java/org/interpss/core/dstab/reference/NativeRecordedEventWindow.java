package org.interpss.core.dstab.reference;

import java.util.List;

/** One-step transition windows in a reference stream's recorded binary32 clock. */
public final class NativeRecordedEventWindow {
    private NativeRecordedEventWindow() { }

    public static double[] boundaries(List<double[]> rows, double step, double... scheduled) {
        double[] result = new double[scheduled.length];
        for (int event = 0; event < scheduled.length; event++) {
            double best = Double.POSITIVE_INFINITY;
            double found = Double.NaN;
            for (int i = 1; i < rows.size(); i++) {
                double time = rows.get(i)[0];
                if (time == rows.get(i - 1)[0] && Math.abs(time - scheduled[event]) < best) {
                    best = Math.abs(time - scheduled[event]);
                    found = time;
                }
            }
            if (!Double.isFinite(found) || best > step / 2) {
                throw new IllegalArgumentException("Missing recorded event boundary near " + scheduled[event]);
            }
            result[event] = found;
        }
        return result;
    }

    public static boolean contains(double time, double[] boundaries, double step) {
        for (double event : boundaries) {
            // The source clock accumulates in binary32. Center on its actual
            // duplicate event timestamp; tolerate only representation rounding
            // of the two clock values and the declared one-step interval.
            double rounding = 2.0 * (Math.ulp((float) time) + Math.ulp((float) event)
                    + Math.ulp((float) step));
            if (Math.abs(time - event) <= step + rounding) return true;
        }
        return false;
    }
}
