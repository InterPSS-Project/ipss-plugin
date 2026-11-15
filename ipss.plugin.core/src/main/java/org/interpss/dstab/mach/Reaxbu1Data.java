package org.interpss.dstab.mach;

/** One attachment ICON and seven constants for the REAXB auxiliary control. */
public record Reaxbu1Data(int plantBus, double measurementTime,
        double reactiveGain, double activeGain, double reactiveMaximum,
        double reactiveMinimum, double activeMaximum, double activeMinimum) {
    public Reaxbu1Data {
        // A zero participation weight disconnects that incremental plant
        // command. The runtime retains the initialized host output instead of
        // dividing by zero or replacing the entered weight with an epsilon.
        if (!finite(measurementTime, reactiveGain, activeGain, reactiveMaximum,
                reactiveMinimum, activeMaximum, activeMinimum)
                || measurementTime < 0.0
                || reactiveMaximum < reactiveMinimum || activeMaximum < activeMinimum) {
            throw new IllegalArgumentException("invalid REAXB constants");
        }
    }
    private static boolean finite(double... values) {
        for (double value : values) if (!Double.isFinite(value)) return false;
        return true;
    }
}
